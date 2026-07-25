@file:OptIn(ExperimentalMaterial3Api::class)
package net.igng.mcstatus.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.flow.distinctUntilChanged
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

    val hasAdvancedFilters: Boolean
        get() = senderId.isNotBlank() ||
            limit != 100 ||
            startLocal.isNotBlank() ||
            endLocal.isNotBlank() ||
            atAllOnly ||
            !_state.value.focusedId.isNullOrBlank() ||
            focusInput.isNotBlank()

    val currentTargetLabel: String
        get() {
            val bootstrap = _state.value.bootstrap ?: return if (source == "qq") "QQ群" else "服务器"
            return if (source == "qq") {
                bootstrap.qqGroups.firstOrNull { it.id == groupId }?.name
                    ?: groupId.takeIf { it.isNotBlank() }
                    ?: "选择QQ群"
            } else {
                bootstrap.servers.firstOrNull { it.id.toString() == serverId }?.name
                    ?: "选择服务器"
            }
        }

    init { bootstrap() }

    fun retryBootstrap() = bootstrap()

    private fun bootstrap() {
        viewModelScope.launch {
            _state.value = _state.value.copy(bootstrapping = true, error = null)
            runCatching { repository.bootstrap() }
                .onSuccess { data ->
                    serverId = data.servers.firstOrNull()?.id?.toString().orEmpty()
                    groupId = data.qqGroups.firstOrNull()?.id.orEmpty()
                    _state.value = _state.value.copy(bootstrapping = false, bootstrap = data)
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
        if (serverId == value) return
        serverId = value
        _state.value = _state.value.copy(messages = emptyList(), focusedId = null, error = null)
        focusInput = ""
        refresh()
        restartAutoRefresh()
    }

    fun selectGroupId(value: String) {
        if (groupId == value) return
        groupId = value
        _state.value = _state.value.copy(messages = emptyList(), focusedId = null, error = null)
        focusInput = ""
        refresh()
        restartAutoRefresh()
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

    fun clearAdvancedFilters() {
        playerInput = ""
        senderId = ""
        limit = 100
        startLocal = ""
        endLocal = ""
        atAllOnly = false
        focusInput = ""
        _state.value = _state.value.copy(focusedId = null)
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

    /** Message id that should stay on screen after older messages are prepended. */
    var scrollAnchorMessageId by mutableStateOf<String?>(null)
        private set

    fun consumeScrollAnchor() {
        scrollAnchorMessageId = null
    }

    fun loadOlder() {
        val firstId = _state.value.messages.firstOrNull()?.id ?: return
        if (_state.value.loadingOlder || _state.value.loading) return
        scrollAnchorMessageId = firstId
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
                if (response.messages.isEmpty()) {
                    scrollAnchorMessageId = null
                    _state.value = _state.value.copy(
                        loadingOlder = false,
                        hasMore = false,
                    )
                    return@onSuccess
                }
                val merged = response.messages + _state.value.messages
                _state.value = _state.value.copy(
                    loadingOlder = false,
                    messages = merged.distinctBy { it.id },
                    hasMore = response.hasMore,
                )
                resolveProfiles(response.messages)
            }.onFailure {
                scrollAnchorMessageId = null
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
                if (focus.isNotBlank()) {
                    val focused = response.messages.firstOrNull { it.id == (response.focusedId ?: focus) }
                    if (focused != null) {
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
    var showFilterSheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var initialScrollDone by remember { mutableStateOf(false) }

    LaunchedEffect(state.messages.isEmpty(), vm.source, vm.serverId, vm.groupId, state.loading) {
        if (state.messages.isEmpty() || state.loading) {
            initialScrollDone = false
        }
    }

    LaunchedEffect(
        state.messages,
        state.focusedId,
        state.loading,
        state.loadingOlder,
        state.hasMore,
        state.error,
        vm.scrollAnchorMessageId,
    ) {
        if (state.messages.isEmpty()) return@LaunchedEffect
        val showOlderHeader = state.hasMore || state.loadingOlder
        val headerCount = (if (state.error != null) 1 else 0) + (if (showOlderHeader) 1 else 0)

        // Keep viewport stable after prepending older messages.
        val anchorId = vm.scrollAnchorMessageId
        if (!state.loadingOlder && anchorId != null) {
            val anchorIndex = state.messages.indexOfFirst { it.id == anchorId }
            if (anchorIndex >= 0) {
                listState.scrollToItem(anchorIndex + headerCount)
            }
            vm.consumeScrollAnchor()
            initialScrollDone = true
            return@LaunchedEffect
        }

        if (state.loadingOlder || state.loading) return@LaunchedEffect

        val focusIndex = state.focusedId?.let { id -> state.messages.indexOfFirst { it.id == id } } ?: -1
        if (focusIndex >= 0) {
            listState.animateScrollToItem(focusIndex + headerCount)
            initialScrollDone = true
            return@LaunchedEffect
        }

        if (!initialScrollDone) {
            listState.scrollToItem(state.messages.lastIndex + headerCount)
            initialScrollDone = true
        }
    }

    // Auto-load older messages when user reaches the top (after initial bottom scroll).
    LaunchedEffect(
        listState,
        state.hasMore,
        state.loadingOlder,
        state.loading,
        state.messages.size,
        initialScrollDone,
    ) {
        if (!initialScrollDone) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                if (
                    index <= 0 &&
                    state.hasMore &&
                    !state.loadingOlder &&
                    !state.loading &&
                    state.messages.isNotEmpty()
                ) {
                    vm.loadOlder()
                }
            }
    }
    Scaffold(
        floatingActionButton = {
            if (state.bootstrap != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmallFloatingActionButton(
                        onClick = {
                            buzzChat(haptic, settings)
                            vm.refresh()
                        },
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
                    }
                    ExtendedFloatingActionButton(
                        onClick = {
                            buzzChat(haptic, settings)
                            showFilterSheet = true
                        },
                        icon = {
                            BadgedBox(
                                badge = { if (vm.hasAdvancedFilters) Badge() },
                            ) {
                                Icon(Icons.Rounded.FilterList, contentDescription = null)
                            }
                        },
                        text = {
                            Text(
                                text = vm.currentTargetLabel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
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
                        Button(onClick = { vm.retryBootstrap() }) { Text("重试") }
                    }
                }
            }

            else -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    when {
                        state.loading && state.messages.isEmpty() -> {
                            CircularProgressIndicator(Modifier.align(Alignment.Center))
                        }

                        state.messages.isEmpty() -> {
                            Column(
                                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = state.error ?: "这个范围内还没有聊天记录。",
                                    color = if (state.error != null) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                OutlinedButton(onClick = {
                                    buzzChat(haptic, settings)
                                    showFilterSheet = true
                                }) {
                                    Icon(Icons.Rounded.FilterList, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("打开筛选")
                                }
                            }
                        }

                        else -> {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(
                                    start = 12.dp,
                                    end = 12.dp,
                                    top = 8.dp,
                                    bottom = 96.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                if (state.error != null) {
                                    item(key = "error") {
                                        Text(
                                            text = state.error!!,
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                                if (state.hasMore || state.loadingOlder) {
                                    item(key = "older") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (state.loadingOlder) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(28.dp),
                                                    strokeWidth = 2.dp,
                                                )
                                            } else {
                                                Text(
                                                    text = "继续上滑加载更早消息",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
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
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 12.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
        }
    }

    if (showFilterSheet && state.bootstrap != null) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = filterSheetState,
        ) {
            ChatFilterSheet(
                vm = vm,
                bootstrap = state.bootstrap!!,
                settings = settings,
                token = token,
                quotaText = state.quota?.let { "今日还可创建 ${it.remaining}/${it.limit} 条工单" },
                haptic = haptic,
                onApply = {
                    buzzChat(haptic, settings)
                    vm.applyFilters()
                    showFilterSheet = false
                },
                onFocus = {
                    buzzChat(haptic, settings)
                    vm.focusMessage()
                    showFilterSheet = false
                },
                onClear = {
                    buzzChat(haptic, settings)
                    vm.clearAdvancedFilters()
                },
                onClose = { showFilterSheet = false },
            )
        }
    }

    reportTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!reportSubmitting) reportTarget = null },
            title = { Text("举报消息") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("将为 #${target.id} 创建工单")
                    Text(
                        text = "${target.player_name}: ${target.content}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    OutlinedTextField(
                        value = reportReason,
                        onValueChange = { reportReason = it },
                        label = { Text("举报原因") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                    )
                    state.quota?.let {
                        Text(
                            text = "今日剩余 ${it.remaining}/${it.limit}",
                            style = MaterialTheme.typography.bodySmall,
                        )
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
private fun ChatFilterSheet(
    vm: ChatLogsViewModel,
    bootstrap: ChatBootstrap,
    settings: AppSettings,
    token: String?,
    quotaText: String?,
    haptic: HapticFeedback,
    onApply: () -> Unit,
    onFocus: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.72f)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
    ) {
        Text(
            text = "筛选与范围",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "选择服务器/QQ群，并配置消息范围。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("消息来源", style = MaterialTheme.typography.labelLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                FilterChip(
                    selected = vm.source == "server",
                    onClick = { buzzChat(haptic, settings); vm.selectSource("server") },
                    label = { Text("服务器") },
                )
                FilterChip(
                    selected = vm.source == "qq",
                    onClick = { buzzChat(haptic, settings); vm.selectSource("qq") },
                    label = { Text("QQ群") },
                )
            }

            if (vm.source == "qq") {
                Text("QQ群", style = MaterialTheme.typography.labelLarge)
                if (bootstrap.qqGroups.isEmpty()) {
                    Text(
                        text = "未获取到QQ群列表，请下拉关闭后重试。",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        bootstrap.qqGroups.forEach { group ->
                            val label = buildString {
                                append(group.name)
                                group.count?.let { append(" · "); append(it) }
                            }
                            FilterChip(
                                selected = vm.groupId == group.id,
                                onClick = {
                                    buzzChat(haptic, settings)
                                    vm.selectGroupId(group.id)
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            } else {
                Text("服务器", style = MaterialTheme.typography.labelLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    bootstrap.servers.forEach { server ->
                        FilterChip(
                            selected = vm.serverId == server.id.toString(),
                            onClick = {
                                buzzChat(haptic, settings)
                                vm.selectServerId(server.id.toString())
                            },
                            label = { Text(server.name) },
                        )
                    }
                }
            }

            Text("消息范围", style = MaterialTheme.typography.labelLarge)
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
                label = { Text("最新消息数量（1-200）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            if (vm.source == "qq") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = vm.atAllOnly,
                        onCheckedChange = {
                            buzzChat(haptic, settings)
                            vm.atAllOnly = it
                        },
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
                Button(
                    onClick = onFocus,
                    enabled = vm.focusInput.isNotBlank(),
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("定位")
                }
            }

            if (!token.isNullOrBlank()) {
                val account = settings.accountName ?: settings.accountUsername ?: "已登录"
                Text(
                    text = buildString {
                        append("已登录：")
                        append(account)
                        if (!quotaText.isNullOrBlank()) {
                            append("，")
                            append(quotaText)
                        }
                    },
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

        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                Text("清除范围")
            }
            OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) {
                Text("关闭")
            }
            Button(onClick = onApply, modifier = Modifier.weight(1f)) {
                Text("应用")
            }
        }
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
                TextButton(
                    onClick = onFocus,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
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
