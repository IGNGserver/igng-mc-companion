@file:OptIn(ExperimentalMaterial3Api::class)
package net.igng.mcstatus.ui

import androidx.compose.animation.AnimatedContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import net.igng.mcstatus.data.*

data class TicketsUiState(val loading: Boolean = true, val data: TicketsResponse? = null, val error: String? = null)
class TicketsViewModel(private val repository: TicketRepository, private val token: String) : ViewModel() {
    private val _state = mutableStateOf(TicketsUiState())
    val state: State<TicketsUiState> = _state
    var participant by mutableStateOf("involved")
    var status by mutableStateOf("pending")
    init { refresh() }
    fun refresh() = viewModelScope.launch { _state.value = _state.value.copy(loading = true, error = null); runCatching { repository.tickets(token, participant, status) }.onSuccess { _state.value = TicketsUiState(false, it) }.onFailure { _state.value = _state.value.copy(loading = false, error = it.message ?: "加载失败") } }
    fun detail(id: Int, success: (TicketDetailResponse) -> Unit, fail: (String) -> Unit) = viewModelScope.launch { runCatching { repository.ticket(token, id) }.onSuccess(success).onFailure { fail(it.message ?: "详情加载失败") } }
    fun action(id: Int, body: JsonObject, success: () -> Unit, fail: (String) -> Unit) = viewModelScope.launch { runCatching { repository.action(token, id, body) }.onSuccess { success() }.onFailure { fail(it.message ?: "操作失败") } }
    fun create(title: String, content: String, visibility: String, targets: List<String>, serverId: Int, done: () -> Unit) = viewModelScope.launch { runCatching { repository.create(token, title, content, visibility, targets, serverId) }.onSuccess { refresh(); done() }.onFailure { _state.value = _state.value.copy(error = it.message ?: "提交失败") } }
}
class TicketsViewModelFactory(private val repository: TicketRepository, private val token: String) : androidx.lifecycle.ViewModelProvider.Factory { override fun <T : ViewModel> create(modelClass: Class<T>): T { @Suppress("UNCHECKED_CAST") return TicketsViewModel(repository, token) as T } }

@Composable fun TicketsScreen(repository: TicketRepository, settings: AppSettings) {
    val token = settings.sessionToken ?: return Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { Text("请先在设置中登录 IGNG 账号") }
    val vm: TicketsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(key = "tickets-$token", factory = remember(token) { TicketsViewModelFactory(repository, token) })
    val state by vm.state; val haptic = LocalHapticFeedback.current
    var detailId by remember(token) { mutableStateOf<Int?>(null) }; var detail by remember(token) { mutableStateOf<TicketDetailResponse?>(null) }; var creating by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }; var actionPending by remember { mutableStateOf(false) }
    BackHandler(enabled = detailId != null || creating) { detailId = null; detail = null; creating = false; error = null; actionPending = false }
    fun reload() { val id = detailId ?: return; vm.detail(id, { detail = it; actionPending = false }, { error = it; actionPending = false }) }
    AnimatedContent(detailId != null || creating, label = "ticket-page") { subpage ->
        when {
            detailId != null && detail == null -> TicketLoadingPage(detailId!!, settings, haptic, error, onBack = { detailId = null; error = null }, onRetry = ::reload)
            detail != null -> TicketDetailPage(detail!!, settings, haptic, actionPending, onBack = { detail = null; detailId = null }, onReload = ::reload, onAction = { body -> if (!actionPending) { actionPending = true; vm.action(detail!!.report.id, body, ::reload, { error = it; actionPending = false }) } }, error = error)
            creating && state.data != null -> CreateTicketPage(state.data!!, vm, settings, haptic) { creating = false }
            else -> TicketListPage(state, vm, settings, haptic, onOpen = { id -> error = null; detail = null; detailId = id; reload() }, onCreate = { creating = true })
        }
    }
}

@Composable private fun TicketListPage(state: TicketsUiState, vm: TicketsViewModel, settings: AppSettings, haptic: HapticFeedback, onOpen: (Int) -> Unit, onCreate: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Column { Text("工单"); Text("${settings.accountUsername ?: settings.accountName ?: "未登录"} · ${roleText(state.data?.adminRole)}", style = MaterialTheme.typography.labelMedium) } }, actions = { IconButton({ buzz(haptic, settings); vm.refresh() }, enabled = !state.loading) { if (state.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.Refresh, "刷新") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { FilterRow(listOf("involved" to "全部参与", "mine" to "我发起", "received" to "我收到"), vm.participant, haptic, settings) { vm.participant = it; vm.refresh() }; if (state.data?.adminRole != null) FilterChip(vm.participant == "admin", { buzz(haptic, settings); vm.participant = "admin"; vm.refresh() }, { Text("管理范围") }) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(vm.status == "pending", { buzz(haptic, settings); vm.status = "pending"; vm.refresh() }, { Text("正在处理") }); FilterChip(vm.status == "resolved", { buzz(haptic, settings); vm.status = "resolved"; vm.refresh() }, { Text("已完成") }); Spacer(Modifier.weight(1f)); Button({ buzz(haptic, settings); onCreate() }, enabled = state.data != null) { Text("创建") } } }
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }; state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            items(state.data?.reports.orEmpty(), key = { it.id }) { report -> Card(Modifier.fillMaxWidth().clickable { buzz(haptic, settings); onOpen(report.id) }) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Row { Text("#${report.id} ${report.title}", Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); AssistChip({}, { Text(if (report.status == "PENDING") "处理中" else "已完成") }) }; Text("${report.reporter_name} · ${report.source_server_name ?: "未指定服务器"}", style = MaterialTheme.typography.bodySmall); Text("涉事账号：${report.targets.joinToString { it.mc_username }}", style = MaterialTheme.typography.bodySmall) } } }
        }
    }
}

@Composable private fun TicketLoadingPage(id: Int, settings: AppSettings, haptic: HapticFeedback, error: String?, onBack: () -> Unit, onRetry: () -> Unit) { Scaffold(topBar = { TopAppBar(title = { Text("工单 #$id") }, navigationIcon = { IconButton({ buzz(haptic, settings); onBack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回工单列表") } }) }) { padding -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) { if (error == null) CircularProgressIndicator() else Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(error, color = MaterialTheme.colorScheme.error); Button({ buzz(haptic, settings); onRetry() }) { Text("重试") } } } } }
@Composable private fun TicketDetailPage(data: TicketDetailResponse, settings: AppSettings, haptic: HapticFeedback, pending: Boolean, onBack: () -> Unit, onReload: () -> Unit, onAction: (JsonObject) -> Unit, error: String?) {
    val report = data.report; var reply by remember(report.id) { mutableStateOf("") }; var edit by remember(report.id) { mutableStateOf(false) }; var resolve by remember(report.id) { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Column { Text("工单 #${report.id}"); Text("${settings.accountUsername ?: settings.accountName ?: "未登录"} · ${roleText(data.adminRole)}", style = MaterialTheme.typography.labelMedium) } }, navigationIcon = { IconButton({ buzz(haptic, settings); onBack() }, enabled = !pending) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回工单列表") } }, actions = { IconButton({ buzz(haptic, settings); onReload() }, enabled = !pending) { if (pending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.Refresh, "刷新") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(report.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
            item { Text("${roleText(data.adminRole)}\n发起人：${report.reporter_name}\n属地服务器：${report.source_server_name ?: "未指定"}\n涉事账号：${report.targets.joinToString { it.mc_username }}\n提交时间：${report.created_at}\n涉事玩家可见：${if (report.target_visibility == "PUBLIC") "已开放" else "未开放"}") }
            item { ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text("原始举报", fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text(report.content) } } }
            if (report.permissions.canManage) item { AdminActions(report, settings, haptic, pending, { edit = !edit }, { resolve = true }, onAction) }
            if (edit) item { EditTicket(report, data.servers, settings, haptic, pending, onAction) }
            item { Text("工单回复（${report.replies.size}）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(report.replies, key = { it.id }) { item -> ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { Row { Text(item.author_name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(item.authorRoleLabel ?: "参与用户", style = MaterialTheme.typography.labelSmall) }; Text(if (item.deleted_at != null) "该回复已被超级管理员删除。" else item.content); if (data.adminRole == "SUPERADMIN" && item.deleted_at == null) TextButton({ buzz(haptic, settings); onAction(buildJsonObject { put("action", "deleteReply"); put("replyId", item.id) }) }, enabled = !pending) { Text(if (pending) "处理中..." else "删除回复") } } } }
            if (report.permissions.canReply) item { OutlinedTextField(reply, { reply = it }, Modifier.fillMaxWidth(), label = { Text("继续回复") }, minLines = 4, enabled = !pending); Button({ buzz(haptic, settings); onAction(buildJsonObject { put("action", "reply"); put("content", reply) }); reply = "" }, enabled = reply.isNotBlank() && !pending, modifier = Modifier.fillMaxWidth()) { if (pending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("发送回复") } } else item { Text("当前身份只有查看权限", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        }
    }
    if (resolve) ResolveDialog(report, settings, haptic, pending, { resolve = false }, { body -> onAction(body); resolve = false })
}

@Composable private fun AdminActions(report: TicketReport, settings: AppSettings, haptic: HapticFeedback, pending: Boolean, onEdit: () -> Unit, onResolve: () -> Unit, onAction: (JsonObject) -> Unit) { ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("管理操作", fontWeight = FontWeight.Bold); Button({ buzz(haptic, settings); onEdit() }, Modifier.fillMaxWidth(), enabled = !pending) { Text("编辑工单信息") }; Button({ buzz(haptic, settings); onAction(buildJsonObject { put("action", "visibility"); put("targetVisibility", if (report.target_visibility == "PUBLIC") "PRIVATE" else "PUBLIC") }) }, Modifier.fillMaxWidth(), enabled = !pending) { Text(if (pending) "处理中..." else if (report.target_visibility == "PUBLIC") "关闭涉事玩家查看" else "向涉事玩家开放") }; if (report.status == "PENDING") Button({ buzz(haptic, settings); onResolve() }, Modifier.fillMaxWidth(), enabled = !pending) { Text("完成工单") } else OutlinedButton({ buzz(haptic, settings); onAction(buildJsonObject { put("action", "reopen") }) }, Modifier.fillMaxWidth(), enabled = !pending) { Text(if (pending) "处理中..." else "改回待处理") } } } }
@Composable private fun EditTicket(report: TicketReport, servers: List<ReportServer>, settings: AppSettings, haptic: HapticFeedback, pending: Boolean, onAction: (JsonObject) -> Unit) { var title by remember(report.id) { mutableStateOf(report.title) }; var targets by remember(report.id) { mutableStateOf(report.targets.joinToString(",") { it.mc_username }) }; var server by remember(report.id) { mutableStateOf(report.source_server_id ?: 0) }; ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(title, { title = it }, label = { Text("工单标题") }, modifier = Modifier.fillMaxWidth(), enabled = !pending); OutlinedTextField(targets, { targets = it }, label = { Text("涉事账号，用逗号分隔") }, modifier = Modifier.fillMaxWidth(), enabled = !pending); servers.forEach { FilterChip(server == it.id, { buzz(haptic, settings); server = it.id }, { Text(it.name) }, enabled = !pending) }; Button({ buzz(haptic, settings); onAction(updateBody(title, targets, server)) }, Modifier.fillMaxWidth(), enabled = !pending) { Text(if (pending) "保存中..." else "保存修改") } } } }

private data class PunishmentDraft(val player: String = "", val type: String = "BAN", val amount: String = "1", val unit: String = "days", val reason: String = "")
@Composable private fun ResolveDialog(report: TicketReport, settings: AppSettings, haptic: HapticFeedback, pending: Boolean, onDismiss: () -> Unit, onConfirm: (JsonObject) -> Unit) { var punishment by remember { mutableStateOf(false) }; var drafts by remember { mutableStateOf(listOf(PunishmentDraft(player = report.targets.firstOrNull()?.mc_username.orEmpty()))) }; AlertDialog(onDismissRequest = { if (!pending) onDismiss() }, title = { Text("完成工单 #${report.id}") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(!punishment, { punishment = false }, { Text("普通处理") }, enabled = !pending); FilterChip(punishment, { punishment = true }, { Text("处罚处理") }, enabled = !pending); if (punishment) { drafts.forEachIndexed { index, draft -> PunishmentEditor(draft, { next -> drafts = drafts.toMutableList().also { it[index] = next } }, drafts.size > 1, { drafts = drafts.filterIndexed { i, _ -> i != index } }) }; if (drafts.size < 10) OutlinedButton({ drafts = drafts + PunishmentDraft() }, enabled = !pending) { Text("添加处罚") } } } }, confirmButton = { Button({ buzz(haptic, settings); onConfirm(resolveBody(punishment, drafts)) }, enabled = !pending && (!punishment || drafts.all { it.player.isNotBlank() && it.reason.isNotBlank() && (it.amount.toIntOrNull() ?: 0) > 0 })) { if (pending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("确认完成") } }, dismissButton = { TextButton(onDismiss, enabled = !pending) { Text("取消") } }) }
@Composable private fun PunishmentEditor(draft: PunishmentDraft, update: (PunishmentDraft) -> Unit, removable: Boolean, remove: () -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { OutlinedTextField(draft.player, { update(draft.copy(player = it)) }, label = { Text("玩家") }); Row { FilterChip(draft.type == "BAN", { update(draft.copy(type = "BAN")) }, { Text("封禁") }); FilterChip(draft.type == "MUTE", { update(draft.copy(type = "MUTE")) }, { Text("禁言") }) }; OutlinedTextField(draft.amount, { update(draft.copy(amount = it)) }, label = { Text("时长") }); Row { listOf("minutes" to "分钟", "hours" to "小时", "days" to "天", "weeks" to "周").forEach { (unit, label) -> FilterChip(draft.unit == unit, { update(draft.copy(unit = unit)) }, { Text(label) }) } }; OutlinedTextField(draft.reason, { update(draft.copy(reason = it)) }, label = { Text("处罚理由") }); if (removable) TextButton(remove) { Text("删除此项") } } }

@Composable private fun CreateTicketPage(data: TicketsResponse, vm: TicketsViewModel, settings: AppSettings, haptic: HapticFeedback, done: () -> Unit) { var title by remember { mutableStateOf("") }; var content by remember { mutableStateOf("") }; var targets by remember { mutableStateOf("") }; var server by remember { mutableStateOf(data.servers.firstOrNull()?.id ?: 0) }; var visibility by remember { mutableStateOf("ADMIN") }; Scaffold(topBar = { TopAppBar(title = { Text("新建工单") }, navigationIcon = { IconButton(done) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } }) }) { padding -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Text("今日剩余 ${data.quota.remaining}/${data.quota.limit} 次") }; item { OutlinedTextField(title, { title = it }, label = { Text("工单标题") }, modifier = Modifier.fillMaxWidth()) }; item { OutlinedTextField(content, { content = it }, label = { Text("举报内容") }, modifier = Modifier.fillMaxWidth(), minLines = 6) }; item { OutlinedTextField(targets, { targets = it }, label = { Text("涉事游戏账号，用逗号分隔") }, modifier = Modifier.fillMaxWidth()) }; item { data.servers.forEach { FilterChip(server == it.id, { buzz(haptic, settings); server = it.id }, { Text(it.name) }) } }; if (data.adminRole == "SUPERADMIN") item { FilterChip(visibility == "ADMIN", { visibility = "ADMIN" }, { Text("普通管理员") }); FilterChip(visibility == "SUPERADMIN", { visibility = "SUPERADMIN" }, { Text("仅超级管理员") }) }; item { Button({ buzz(haptic, settings); vm.create(title, content, visibility, targets.split(',').map { it.trim() }.filter { it.isNotEmpty() }, server, done) }, Modifier.fillMaxWidth(), enabled = title.isNotBlank() && content.isNotBlank() && targets.isNotBlank() && server > 0) { Text("提交工单") } } } } }
@Composable private fun FilterRow(options: List<Pair<String, String>>, selected: String, haptic: HapticFeedback, settings: AppSettings, change: (String) -> Unit) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { options.forEach { (value, label) -> FilterChip(selected == value, { buzz(haptic, settings); change(value) }, { Text(label) }) } } }
private fun updateBody(title: String, targets: String, server: Int) = buildJsonObject { put("action", "update"); put("title", title); put("serverId", server); put("targets", buildJsonArray { targets.split(',').map { it.trim() }.filter { it.isNotBlank() }.forEach { add(JsonPrimitive(it)) } }) }
private fun resolveBody(punishment: Boolean, drafts: List<PunishmentDraft>) = buildJsonObject { put("action", "resolve"); put("kind", if (punishment) "PUNISHMENT" else "NORMAL"); put("punishments", buildJsonArray { if (punishment) drafts.forEach { d -> add(buildJsonObject { put("player", d.player); put("type", d.type); put("amount", d.amount.toIntOrNull() ?: 0); put("unit", d.unit); put("reason", d.reason) }) } }) }
private fun roleText(role: String?) = when (role) { "SUPERADMIN" -> "当前身份：超级管理员"; "ADMIN" -> "当前身份：管理员"; else -> "当前身份：用户" }
private fun buzz(haptic: HapticFeedback, settings: AppSettings) { if (settings.vibrationEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
