package net.igng.mcstatus.data

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ChatRepository(
    private val mcBaseUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    suspend fun bootstrap(): ChatBootstrap = withContext(Dispatchers.IO) {
        val servers = runCatching { fetchServersFromStatus() }.getOrDefault(emptyList())
        val qqGroups = runCatching { fetchQqGroupsFromPage() }.getOrDefault(emptyList())
        ChatBootstrap(
            servers = servers.ifEmpty {
                runCatching { fetchServersFromPage() }.getOrDefault(emptyList())
            },
            qqGroups = qqGroups,
        )
    }

    suspend fun chatlogs(
        source: String,
        serverId: String? = null,
        groupId: String? = null,
        limit: Int = 100,
        start: String? = null,
        end: String? = null,
        senderId: String? = null,
        atAll: Boolean = false,
        messageId: String? = null,
        beforeId: String? = null,
    ): ChatLogsResponse = withContext(Dispatchers.IO) {
        val url = "$mcBaseUrl/api/chatlogs".toHttpUrl().newBuilder()
            .addQueryParameter("source", source)
            .addQueryParameter("limit", limit.coerceIn(1, 200).toString())
            .apply {
                if (source == "qq") {
                    if (!groupId.isNullOrBlank()) addQueryParameter("groupId", groupId)
                } else {
                    if (!serverId.isNullOrBlank()) addQueryParameter("serverId", serverId)
                }
                if (!start.isNullOrBlank()) addQueryParameter("start", start)
                if (!end.isNullOrBlank()) addQueryParameter("end", end)
                if (!senderId.isNullOrBlank()) addQueryParameter("senderId", senderId)
                if (source == "qq" && atAll) addQueryParameter("atAll", "1")
                if (!messageId.isNullOrBlank()) addQueryParameter("messageId", messageId)
                if (!beforeId.isNullOrBlank()) addQueryParameter("beforeId", beforeId)
            }
            .build()
        get(url.toString())
    }

    suspend fun report(token: String, messageId: String, reason: String): ChatReportResponse =
        post(
            "$mcBaseUrl/api/chatlogs/report",
            buildJsonObject {
                put("messageId", messageId)
                put("reason", reason)
            },
            token,
        )

    suspend fun reportQuota(token: String): ChatReportQuotaResponse =
        get("$mcBaseUrl/api/reports/quota", token)

    suspend fun resolvePlayers(names: List<String>): Map<String, String> = withContext(Dispatchers.IO) {
        if (names.isEmpty()) return@withContext emptyMap()
        val url = "$mcBaseUrl/api/profile/mc".toHttpUrl().newBuilder().apply {
            names.distinct().forEach { addQueryParameter("name", it) }
        }.build()
        runCatching {
            val response: McProfileResponse = get(url.toString())
            response.players
        }.getOrDefault(emptyMap())
    }

    private fun fetchServersFromStatus(): List<ChatServerOption> {
        val request = Request.Builder().url("$mcBaseUrl/api/status/list").get().build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("请求失败 ${response.code}")
            val list = json.decodeFromString<List<ServerSummary>>(text)
            return list.map { ChatServerOption(id = it.server_id, name = it.server_name, address = it.address) }
        }
    }

    private fun fetchServersFromPage(): List<ChatServerOption> {
        val payload = chatlogsPagePayload()
        val match = Regex(""""servers"\s*:\s*(\[[\s\S]*?\])\s*,\s*"qqGroups"""").find(payload)
            ?: return emptyList()
        return json.decodeFromString(match.groupValues[1])
    }

    private fun fetchQqGroupsFromPage(): List<ChatQqGroupOption> {
        val payload = chatlogsPagePayload()
        val match = Regex(""""qqGroups"\s*:\s*(\[[\s\S]*?\])\s*,\s*"initialMessageId"""").find(payload)
            ?: return emptyList()
        return json.decodeFromString(match.groupValues[1])
    }

    private fun chatlogsPagePayload(): String {
        val request = Request.Builder().url("$mcBaseUrl/chatlogs").get().build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("请求失败 ${response.code}")
            return text
        }
    }

    private suspend inline fun <reified T> get(url: String, token: String? = null): T =
        withContext(Dispatchers.IO) { request(url, null, token) }

    private suspend inline fun <reified T> post(
        url: String,
        body: kotlinx.serialization.json.JsonObject,
        token: String? = null,
    ): T = withContext(Dispatchers.IO) {
        request(url, body.toString().toRequestBody("application/json".toMediaType()), token)
    }

    private inline fun <reified T> request(url: String, body: okhttp3.RequestBody?, token: String?): T {
        val request = Request.Builder()
            .url(url)
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .method(if (body == null) "GET" else "POST", body)
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val error = runCatching {
                    json.parseToJsonElement(text).jsonObject["error"]?.toString()?.trim('"')
                }.getOrNull()
                throw IOException(error ?: "请求失败 ${response.code}")
            }
            return json.decodeFromString(text)
        }
    }
}

@Serializable
data class ChatBootstrap(
    val servers: List<ChatServerOption> = emptyList(),
    val qqGroups: List<ChatQqGroupOption> = emptyList(),
)

@Serializable
data class ChatServerOption(
    val id: Int,
    val name: String,
    val address: String? = null,
)

@Serializable
data class ChatQqGroupOption(
    val id: String,
    val name: String,
    val count: Int? = null,
)

@Serializable
data class ChatLogsResponse(
    val messages: List<ChatMessage> = emptyList(),
    val hasMore: Boolean = false,
    val focusedId: String? = null,
)

@Serializable
data class ChatMessage(
    val id: String,
    val source: String,
    val source_id: Long? = null,
    val server_id: Int? = null,
    val group_id: String? = null,
    val player_name: String,
    val sender_id: String? = null,
    val content: String,
    val sent_at: String,
    val moderation: String? = null,
    val is_at_all: Boolean = false,
)

@Serializable
data class ChatReportResponse(
    val ok: Boolean = false,
    val reportId: Int? = null,
    val error: String? = null,
)

@Serializable
data class ChatReportQuotaResponse(
    val ok: Boolean = false,
    val limit: Int = 0,
    val remaining: Int = 0,
    val used: Int = 0,
)

@Serializable
private data class McProfileResponse(
    val players: Map<String, String> = emptyMap(),
)
