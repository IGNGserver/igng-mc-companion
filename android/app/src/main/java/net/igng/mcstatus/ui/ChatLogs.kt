@file:OptIn(ExperimentalMaterial3Api::class)
package net.igng.mcstatus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.igng.mcstatus.data.AppSettings
import net.igng.mcstatus.data.ChatBootstrap
import net.igng.mcstatus.data.ChatMessage
import net.igng.mcstatus.data.ChatReportQuotaResponse
import net.igng.mcstatus.data.ChatRepository

data class ChatLogsUiState(
    val bootstrapping: Boolean = true,
    val loading: Boolean = false,
    val loadingOlder: Boolean = false,
    val bootstrap: ChatBootstrap? = null,
    val messages: List<ChatMessage> = emptyList(),
    val hasMore: Boolean = false,
    val focusedId: String? = null,
    val error: String? = null,
    val playerProfiles: Map<String, String> = emptyMap(),
    val quota: ChatReportQuotaResponse? = null,
)

class ChatLogsViewModel(
    private val repository: ChatRepository,
    private val token: String?,
) : ViewModel() {
    private val _state = mutableStateOf(ChatLogsUiState())
    val state: State<ChatLogsUiState> = _state

    var source by mutableStateOf("server")
        private set
    var serverId by mutableStateOf("")
        private set
    var groupId by mutableStateOf("")
        private set
    var playerInput by mutableStateOf("")
    var senderId by mutableStateOf("")
        private set
    var limit by mutableStateOf(100)
    var startLocal by mutableStateOf("")
    var endLocal by mutableStateOf("")
    var atAllOnly by mutableStateOf(false)
    var focusInput by mutableStateOf("")

    private var autoRefreshJob: Job? = null

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(bootstrapping = true, error = null)
            runCatching { repository.bootstrap() }
                .onSuccess { bootstrap ->
                    serverId = bootstrap.servers.firstOrNull()?.id?.toString().orEmpty()
                    groupId = bootstrap.qqGroups.firstOrNull()?.id.orEmpty()
                    _state.value = _state.value.copy(bootstrapping = false, bootstrap = bootstrap)
                    refresh()
                    refreshQuota()
                    restartAutoRefresh()
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        bootstrapping = false,
                        error = it.message ?: "初始化聊天记录失败",
                    )
                }
        }
    }

    fun selectSource(value: String) {
        if (source == value) return
        source = value
        _state.value = _state.value.copy(messages = emptyList(), focusedId = null, error = null)
        focusInput = ""
        val bootstrap = _state.value.bootstrap
        if (value == "qq" && groupId.isBlank()) {
            groupId = bootstrap?.qqGroups?.firstOrNull()?.id.orEmpty()
        }
        if (value == "server" && serverId.isBlank()) {
            serverId = bootstrap?.servers?.firstOrNull()?.id?.toString().orEmpty()
        }
        refresh()
        restartAutoRefresh()
    }

    fun selectServerId(value: String) {
        serverId = value
        _state.value = _state.value.copy(focusedId = null)
    }

    fun selectGroupId(value: String) {
        groupId = value
        _state.value = _state.value.copy(focusedId = null)
    }

    fun commitSenderFromInput() {
        senderId = playerInput.trim()
    }

    fun applyFilters() {
        commitSenderFromInput()
        _state.value = _state.value.copy(focusedId = null)
        focusInput = ""
        refresh()
        restartAutoRefresh()
    }

    fun focusMessage(id: String = focusInput.trim()) {
        val target = id.trim()
        if (target.isBlank()) return
        focusInput = target
        refresh(focus = target)
        restartAutoRefresh()
    }

    fun loadOlder() {
        val firstId = _state.value.messages.firstOrNull()?.id ?: return
        if (_state.value.loadingOlder || _state.value.loading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingOlder = true, error = null)
            runCatching {
                repository.chatlogs(
                    source = source,
                    serverId = serverId.takeIf { source == "server" },
                    groupId = groupId.takeIf { source == "qq" },
                    limit = limit,
                    start = parseLocalToIso(startLocal),
                    end = parseLocalToIso(endLocal),
                    senderId = senderId.takeIf { it.isNotBlank() },
                    atAll = source == "qq" && atAllOnly,
                    beforeId = firstId,
                )
            }.onSuccess { response ->
                val merged = response.messages + _state.value.messages
                _state.value = _state.value.copy(
                    loadingOlder = false,
                    messages = merged.distinctBy { it.id },
                    hasMore = response.hasMore,
                )
                resolveProfiles(response.messages)
            }.onFailure {
                _state.value = _state.value.copy(
                    loadingOlder = false,
                    error = it.message ?: "加载聊天记录失败",
                )
            }
        }
    }

    fun refresh(focus: String = _state.value.focusedId.orEmpty(), silent: Boolean = false) {
        val targetId = if (source == "qq") groupId else serverId
        if (targetId.isBlank() && focus.isBlank()) return
        viewModelScope.launch {
            if (!silent) {
                _state.value = _state.value.copy(loading = true, error = null)
            }
            runCatching {
                repository.chatlogs(
                    source = source,
                    serverId = serverId.takeIf { source == "server" },
                    groupId = groupId.takeIf { source == "qq" },
                    limit = limit,
                    start = parseLocalToIso(startLocal),
                    end = parseLocalToIso(endLocal),
                    senderId = senderId.takeIf { it.isNotBlank() },
                    atAll = source == "qq" && atAllOnly,
                    messageId = focus.takeIf { it.isNotBlank() },
                )
            }.onSuccess { response ->
                if (response.focusedId != null) {
                    response.messages.firstOrNull { it.id == response.focusedId }?.let { focused ->
                        if (focused.source == "qq" && !focused.group_id.isNullOrBlank()) {
                            groupId = focused.group_id
                            source = "qq"
                        } else if (focused.server_id != null) {
                            serverId = focused.server_id.toString()
                            source = "server"
                        }
                    }
                }
                _state.value = _state.value.copy(
                    loading = false,
                    messages = response.messages,
                    hasMore = response.hasMore,
                    focusedId = response.focusedId ?: focus.takeIf { it.isNotBlank() },
                    error = if (silent) _state.value.error else null,
                )
                resolveProfiles(response.messages)
            }.onFailure {
                if (!silent) {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = it.message ?: "加载聊天记录失败",
                    )
                } else {
                    _state.value = _state.value.copy(loading = false)
                }
            }
        }
    }

    fun report(message: ChatMessage, reason: String, onDone: (Result<Int>) -> Unit) {
        val session = token
        if (session.isNullOrBlank()) {
            onDone(Result.failure(IllegalStateException("请先登录后再举报")))
            return
        }
        viewModelScope.launch {
            runCatching { repository.report(session, message.id, reason) }
                .onSuccess { response ->
                    if (!response.ok || response.reportId == null) {
                        onDone(Result.failure(IllegalStateException(response.error ?: "创建失败")))
                    } else {
                        val quota = _state.value.quota
                        if (quota != null) {
                            _state.value = _state.value.copy(
                                quota = quota.copy(
                                    remaining = (quota.remaining - 1).coerceAtLeast(0),
                                    used = quota.used + 1,
                                ),
                            )
                        }
                        onDone(Result.success(response.reportId))
                    }
                }
                .onFailure { onDone(Result.failure(it)) }
        }
    }

    private fun refreshQuota() {
        val session = token ?: return
        viewModelScope.launch {
            runCatching { repository.reportQuota(session) }
                .onSuccess { if (it.ok) _state.value = _state.value.copy(quota = it) }
        }
    }

    private fun resolveProfiles(messages: List<ChatMessage>) {
        val names = messages
            .filter { it.source == "server" }
            .map { it.player_name }
            .filter { it.isNotBlank() }
        if (names.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.resolvePlayers(names) }
                .onSuccess { players ->
                    if (players.isNotEmpty()) {
                        _state.value = _state.value.copy(
                            playerProfiles = _state.value.playerProfiles + players,
                        )
                    }
                }
        }
    }

    private fun restartAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(10_000)
                if (canAutoRefresh()) {
                    refresh(silent = true)
                }
            }
        }
    }

    private fun canAutoRefresh(): Boolean {
        return startLocal.isBlank() &&
            endLocal.isBlank() &&
            _state.value.focusedId.isNullOrBlank() &&
            senderId.isBlank() &&
            !(source == "qq" && atAllOnly)
    }

    private fun parseLocalToIso(value: String): String? {
        val text = value.trim()
        if (text.isEmpty()) return null
        return runCatching {
            if (text.endsWith("Z") || text.contains('+') || text.count { it == '-' } >= 3) {
                Instant.parse(text).toString()
            } else {
                val local = LocalDateTime.parse(text, LOCAL_INPUT)
                local.atZone(ZoneId.systemDefault()).toInstant().toString()
            }
        }.getOrNull() ?: text
    }

    private companion object {
        val LOCAL_INPUT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    }
}

class ChatLogsViewModelFactory(
    private val repository: ChatRepository,
    private val token: String?,
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatLogsViewModel(repository, token) as T
    }
}

@Composable
fun ChatLogsScreen(
    repository: ChatRepository,
    settings: AppSettings,
) {
    val token = settings.sessionToken
    val vm: ChatLogsViewModel = viewModel(
        key = "chatlogs-${token.orEmpty()}",
        factory = remember(token) { ChatLogsViewModelFactory(repository, token) },
    )
    val state by vm.state
    val haptic = LocalHapticFeedback.current
    var reportTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var reportReason by remember { mutableStateOf("") }
    var reportError by remember { mutableStateOf<String?>(null) }
    var reportSubmitting by remember { mutableStateOf(false) }
    var reportSuccessId by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages, state.focusedId, state.loading) {
        if (state.loading || state.messages.isEmpty()) return@LaunchedEffect
        val focusIndex = state.focusedId?.let { id -> state.messages.indexOfFirst { it.id == id } } ?: -1
        if (focusIndex >= 0) {
            listState.animateScrollToItem(focusIndex + if (state.hasMore) 1 else 0)
        } else if (!state.loadingOlder) {
            listState.scrollToItem(state.messages.lastIndex + if (state.hasMore) 1 else 0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("聊天消息")
                        Text(
                            text = "查看服务器和QQ群消息，支持按用户索引最新记录。",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        buzzChat(haptic, settings)
                        vm.refresh()
                    }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.bootstrapping -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }

            state.bootstrap == null -> {
                Box(
                    Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error ?: "初始化失败")
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { vm.applyFilters() }) { Text("重试") }
                    }
                }
            }

            else -> {
                val bootstrap = state.bootstrap!!
                val title = if (vm.source == "qq") {
                    bootstrap.qqGroups.firstOrNull { it.id == vm.groupId }?.name ?: "QQ群聊天记录"
                } else {
                    bootstrap.servers.firstOrNull { it.id.toString() == vm.serverId }?.name ?: "服务器聊天记录"
                }
                Column(Modifier.fillMaxSize().padding(padding)) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = vm.source == "server",
                                onClick = { buzzChat(haptic, settings); vm.selectSource("server") },
                                label = { Text("服务器消息") },
                            )
                            FilterChip(
                                selected = vm.source == "qq",
                                onClick = { buzzChat(haptic, settings); vm.selectSource("qq") },
                                label = { Text("QQ群消息") },
                            )
                        }

                        if (vm.source == "qq") {
                            Text("QQ群", style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                bootstrap.qqGroups.forEach { group ->
                                    FilterChip(
                                        selected = vm.groupId == group.id,
                                        onClick = { buzzChat(haptic, settings); vm.selectGroupId(group.id) },
                                        label = { Text(group.name) },
                                    )
                                }
                            }
                        } else {
                            Text("服务器", style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                bootstrap.servers.forEach { server ->
                                    FilterChip(
                                        selected = vm.serverId == server.id.toString(),
                                        onClick = { buzzChat(haptic, settings); vm.selectServerId(server.id.toString()) },
                                        label = { Text(server.name) },
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = vm.playerInput,
                            onValueChange = { vm.playerInput = it },
                            label = { Text(if (vm.source == "qq") "用户 QQ 号" else "玩家名") },
                            placeholder = { Text(if (vm.source == "qq") "输入 QQ 号" else "输入玩家名") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = vm.limit.toString(),
                            onValueChange = { text ->
                                vm.limit = text.toIntOrNull()?.coerceIn(1, 200) ?: vm.limit
                            },
                            label = { Text("最新消息数量") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        if (vm.source == "qq") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = vm.atAllOnly,
                                    onCheckedChange = { buzzChat(haptic, settings); vm.atAllOnly = it },
                                )
                                Text("只看 @全体成员消息")
                            }
                        }
                        OutlinedTextField(
                            value = vm.startLocal,
                            onValueChange = { vm.startLocal = it },
                            label = { Text("开始时间") },
                            placeholder = { Text("yyyy-MM-dd'T'HH:mm") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = vm.endLocal,
                            onValueChange = { vm.endLocal = it },
                            label = { Text("结束时间") },
                            placeholder = { Text("yyyy-MM-dd'T'HH:mm") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        OutlinedButton(
                            onClick = { buzzChat(haptic, settings); vm.applyFilters() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("应用筛选")
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedTextField(
                                value = vm.focusInput,
                                onValueChange = { vm.focusInput = it },
                                label = { Text("消息 ID") },
                                placeholder = { Text(if (vm.source == "qq") "如 qq-17400" else "输入消息 ID") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                            Button(onClick = { buzzChat(haptic, settings); vm.focusMessage() }) {
                                Icon(Icons.Rounded.Search, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("定位")
                            }
                        }

                        if (!token.isNullOrBlank()) {
                            val account = settings.accountName ?: settings.accountUsername ?: "已登录"
                            val quotaText = state.quota?.let {
                                "，今日还可创建 ${it.remaining}/${it.limit} 条工单"
                            }.orEmpty()
                            Text(
                                text = "已登录：$account$quotaText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = "登录后可举报服务器消息（设置页登录 IGNG 账号）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ) {
                        Column(Modifier.fillMaxSize()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (vm.senderId.isNotBlank()) {
                                        "当前显示 ${vm.senderId} 的最新 ${vm.limit} 条消息。"
                                    } else {
                                        "自动刷新仅在查看最新消息时启用；单次最多显示 ${vm.limit} 条。"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (state.error != null) {
                                Text(
                                    text = state.error!!,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                            Box(Modifier.fillMaxSize()) {
                                when {
                                    state.loading && state.messages.isEmpty() -> {
                                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                                    }
                                    state.messages.isEmpty() -> {
                                        Text(
                                            text = "这个范围内还没有聊天记录。",
                                            modifier = Modifier.align(Alignment.Center),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    else -> {
                                        LazyColumn(
                                            state = listState,
                                            contentPadding = PaddingValues(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.fillMaxSize(),
                                        ) {
                                            if (state.hasMore) {
                                                item(key = "older") {
                                                    OutlinedButton(
                                                        onClick = {
                                                            buzzChat(haptic, settings)
                                                            vm.loadOlder()
                                                        },
                                                        enabled = !state.loadingOlder,
                                                        modifier = Modifier.fillMaxWidth(),
                                                    ) {
                                                        Text(if (state.loadingOlder) "加载中..." else "查看更多较早消息")
                                                    }
                                                }
                                            }
                                            items(state.messages, key = { it.id }) { message ->
                                                ChatMessageCard(
                                                    message = message,
                                                    focused = message.id == state.focusedId,
                                                    canReport = !token.isNullOrBlank() &&
                                                        message.source == "server" &&
                                                        message.moderation.isNullOrBlank(),
                                                    onReport = {
                                                        buzzChat(haptic, settings)
                                                        reportTarget = message
                                                        reportReason = ""
                                                        reportError = null
                                                    },
                                                    onFocus = {
                                                        buzzChat(haptic, settings)
                                                        vm.focusMessage(message.id)
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                                if (state.loading && state.messages.isNotEmpty()) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val target = reportTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { if (!reportSubmitting) reportTarget = null },
            title = { Text("举报 ${target.player_name} 的消息") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("#${target.id}：${target.content}")
                    OutlinedTextField(
                        value = reportReason,
                        onValueChange = { if (it.length <= 1000) reportReason = it },
                        label = { Text("举报理由") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                    )
                    state.quota?.let {
                        Text("提交后今日剩余 ${it.remaining} 次。", style = MaterialTheme.typography.bodySmall)
                    }
                    reportError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !reportSubmitting && reportReason.isNotBlank(),
                    onClick = {
                        reportSubmitting = true
                        reportError = null
                        vm.report(target, reportReason.trim()) { result ->
                            reportSubmitting = false
                            result.onSuccess {
                                reportTarget = null
                                reportSuccessId = it
                            }.onFailure {
                                reportError = it.message ?: "创建失败"
                            }
                        }
                    },
                ) {
                    Text(if (reportSubmitting) "创建中..." else "创建工单")
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!reportSubmitting) reportTarget = null }) {
                    Text("取消")
                }
            },
        )
    }

    reportSuccessId?.let { id ->
        AlertDialog(
            onDismissRequest = { reportSuccessId = null },
            title = { Text("工单已创建") },
            text = { Text("已创建工单 #$id。可在“工单”页查看进度。") },
            confirmButton = {
                TextButton(onClick = { reportSuccessId = null }) { Text("知道了") }
            },
        )
    }
}

@Composable
private fun ChatMessageCard(
    message: ChatMessage,
    focused: Boolean,
    canReport: Boolean,
    onReport: () -> Unit,
    onFocus: () -> Unit,
) {
    val container = when {
        focused -> MaterialTheme.colorScheme.primaryContainer
        !message.moderation.isNullOrBlank() -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = message.player_name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = message.sent_at.toDisplayTimeDetailed(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onFocus, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Text("#${message.id}")
                }
                if (message.is_at_all) {
                    Text(
                        text = "@全体成员",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (canReport) {
                    IconButton(onClick = onReport) {
                        Icon(Icons.Rounded.Flag, contentDescription = "举报此消息")
                    }
                }
            }
            Text(message.content)
            if (!message.moderation.isNullOrBlank()) {
                Text(
                    text = "已处理：${message.moderation}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun String.toDisplayTimeDetailed(): String = runCatching {
    val instant = Instant.parse(this)
    DateTimeFormatter.ofPattern("yyyy/M/d HH:mm:ss")
        .withZone(ZoneId.systemDefault())
        .format(instant)
}.getOrDefault(if (isBlank()) "时间未知" else this)

private fun buzzChat(haptic: HapticFeedback, settings: AppSettings) {
    if (settings.vibrationEnabled) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}
