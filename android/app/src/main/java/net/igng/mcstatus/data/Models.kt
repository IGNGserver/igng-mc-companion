package net.igng.mcstatus.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable

@Serializable
data class ServerSummary(
    val server_id: Int,
    val server_name: String,
    val address: String? = null,
)

@Serializable
data class NodeSummary(
    val node_id: Int,
    val node_name: String,
)

@Serializable
data class LatencyRecord(
    val server_id: Int? = null,
    val node_id: Int,
    val node_name: String? = null,
    val avg_latency_ms: Double? = null,
    val max_latency_ms: Double? = null,
    val min_latency_ms: Double? = null,
    val packet_loss_pct: Double? = null,
    val timestamp_utc: String,
)

@Serializable
data class PerformanceSample(
    val server_id: Int? = null,
    val avg_tps: Double? = null,
    val avg_mspt: Double? = null,
    val cpu_usage: Double? = null,
    val memory_usage_mb: Double? = null,
    val online_players: Int? = null,
    val recorded_at: String,
)

@Serializable
data class TimelineServerPayload(
    val latencies: List<LatencyRecord> = emptyList(),
    val perf: List<PerformanceSample> = emptyList(),
)

@Serializable
data class TimelineResponse(
    val data: Map<String, TimelineServerPayload> = emptyMap(),
    val nodeNames: Map<String, String> = emptyMap(),
)

@Serializable
data class OverviewServerPayload(
    val server: ServerSummary,
    val latestPerf: PerformanceSample? = null,
    val bestLatency: LatencyRecord? = null,
)

@Serializable
data class OverviewResponse(
    val data: List<OverviewServerPayload> = emptyList(),
)

@Serializable
data class ServerDetailResponse(
    val server: ServerSummary,
    val latencies: List<LatencyRecord> = emptyList(),
    val perfLogs: List<PerformanceSample> = emptyList(),
)

enum class RangePreset(val id: String, val label: String, val hours: Long) {
    HOUR_1("1h", "1 小时", 1),
    DAY_1("24h", "24 小时", 24),
    DAY_3("3d", "3 天", 72),
    DAY_7("7d", "7 天", 168),
    DAY_30("30d", "30 天", 720),
}

data class OverviewMetrics(
    val serverCount: Int,
    val onlineCount: Int,
    val totalPlayers: Int,
    val avgTps: Double,
)

data class ServerCardState(
    val server: ServerSummary,
    val latestPerf: PerformanceSample?,
    val bestLatency: LatencyRecord?,
    val isOnline: Boolean,
)

data class OverviewServerSnapshot(
    val server: ServerSummary,
    val latestPerf: PerformanceSample?,
    val bestLatency: LatencyRecord?,
)

enum class DetailSection(val id: String) {
    OVERVIEW("overview"),
    TPS("tps"),
    MSPT("mspt"),
    MEMORY("memory"),
    CPU("cpu"),
    PLAYERS("players"),
    LATENCY("latency"),
    LATENCY_MAX("latency-max"),
    LATENCY_MIN("latency-min"),
    PACKET_LOSS("packet-loss");

    companion object {
        fun fromId(id: String?): DetailSection =
            entries.firstOrNull { it.id == id } ?: OVERVIEW
    }
}

enum class ThemeAccent(val id: String, val label: String) {
    TEAL("teal", "青绿"),
    BLUE("blue", "蔚蓝"),
    ORANGE("orange", "橙色"),
    ROSE("rose", "玫红"),
}

data class AppSettings(
    val vibrationEnabled: Boolean = true,
    val useSystemAccent: Boolean = true,
    val accent: ThemeAccent = ThemeAccent.TEAL,
    val sessionToken: String? = null,
    val accountName: String? = null,
    val accountUsername: String? = null,
    val accounts: List<SavedAccount> = emptyList(),
)

data class SavedAccount(
    val userId: Int,
    val username: String,
    val nickname: String? = null,
    val token: String,
) {
    val displayName: String get() = nickname?.takeIf { it.isNotBlank() } ?: username
}

@Serializable data class MathCaptcha(val a: Int, val b: Int, val token: String)
@Serializable data class LoginUser(val id: Int, val username: String, val nickname: String? = null)
@Serializable data class LoginResponse(val success: Boolean, val sessionToken: String, val expiresAt: String? = null, val user: LoginUser)
@Serializable data class ReportServer(val id: Int, val name: String, val address: String? = null)
@Serializable data class ReportTarget(val mc_username: String)
@Serializable data class ReportReply(val id: Int = 0, val author_name: String, val content: String, val created_at: String, val deleted_at: String? = null, val authorRoleLabel: String? = null)
@Serializable data class ReportPermissions(val canReply: Boolean = false, val canManage: Boolean = false)
@Serializable data class TicketReport(
    val id: Int, val title: String, val content: String, val status: String,
    val reporter_user_id: Int, val reporter_name: String, val source_server_id: Int? = null, val source_server_name: String? = null,
    val visibility: String, val target_visibility: String, val created_at: String,
    val targets: List<ReportTarget> = emptyList(), val replies: List<ReportReply> = emptyList(),
    val permissions: ReportPermissions = ReportPermissions(),
)
@Serializable data class TicketQuota(val limit: Int, val remaining: Int)
@Serializable data class TicketsResponse(val ok: Boolean, val adminRole: String? = null, val reports: List<TicketReport> = emptyList(), val servers: List<ReportServer> = emptyList(), val quota: TicketQuota)
@Serializable data class TicketDetailResponse(val ok: Boolean, val report: TicketReport, val adminRole: String? = null, val servers: List<ReportServer> = emptyList())

private val displayFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    .withZone(ZoneId.systemDefault())

fun String.toDisplayTime(): String = runCatching {
    displayFormatter.format(Instant.parse(this))
}.getOrDefault(this)
