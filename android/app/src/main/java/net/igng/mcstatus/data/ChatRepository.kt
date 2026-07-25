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
        val pagePayload = runCatching { chatlogsPagePayload() }.getOrDefault("")
        val qqGroups = if (pagePayload.isBlank()) {
            emptyList()
        } else {
            runCatching { parseQqGroups(pagePayload) }.getOrDefault(emptyList())
        }
        ChatBootstrap(
            servers = servers.ifEmpty {
                if (pagePayload.isBlank()) emptyList()
                else runCatching { parseServers(pagePayload) }.getOrDefault(emptyList())
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

    private fun parseServers(payload: String): List<ChatServerOption> {
        val raw = extractJsonArray(payload, "servers") ?: return emptyList()
        return json.decodeFromString(raw)
    }

    private fun parseQqGroups(payload: String): List<ChatQqGroupOption> {
        val raw = extractJsonArray(payload, "qqGroups") ?: return emptyList()
        return json.decodeFromString(raw)
    }

    /**
     * Chatlogs HTML is Next.js RSC. Fields may appear plain ("qqGroups":[...])
     * or escaped inside flight payloads (\"qqGroups\":[...]).
     */
    private fun extractJsonArray(payload: String, key: String): String? {
        val markers = listOf(
            "\"$key\"" to false,
            "\\\"$key\\\"" to true,
        )
        for ((marker, escapedQuotes) in markers) {
            var searchFrom = 0
            while (true) {
                val keyIndex = payload.indexOf(marker, searchFrom)
                if (keyIndex < 0) break
                val colon = payload.indexOf(':', keyIndex + marker.length)
                if (colon < 0) {
                    searchFrom = keyIndex + marker.length
                    continue
                }
                val bracket = payload.indexOf('[', colon)
                // Require the array to start immediately after the colon (allow whitespace).
                if (bracket < 0 || payload.substring(colon + 1, bracket).any { !it.isWhitespace() }) {
                    searchFrom = keyIndex + marker.length
                    continue
                }
                val raw = sliceBalancedArray(payload, bracket, escapedQuotes)
                if (raw == null) {
                    searchFrom = keyIndex + marker.length
                    continue
                }
                val normalized = if (escapedQuotes) unescapeJsonFragment(raw) else raw
                if (normalized.startsWith("[") && normalized.endsWith("]") && isJsonArray(normalized)) {
                    return normalized
                }
                searchFrom = keyIndex + marker.length
            }
        }
        return null
    }

    private fun isJsonArray(raw: String): Boolean =
        runCatching {
            val el = json.parseToJsonElement(raw)
            el is kotlinx.serialization.json.JsonArray
        }.getOrDefault(false)

    private fun unescapeJsonFragment(raw: String): String {
        // Only unescape when the fragment itself is RSC-escaped.
        if (!raw.contains("\\\"")) return raw
        return buildString(raw.length) {
            var i = 0
            while (i < raw.length) {
                val ch = raw[i]
                if (ch == '\\' && i + 1 < raw.length) {
                    when (raw[i + 1]) {
                        '"' -> { append('"'); i += 2 }
                        '\\' -> { append('\\'); i += 2 }
                        '/' -> { append('/'); i += 2 }
                        'n' -> { append('\n'); i += 2 }
                        'r' -> { append('\r'); i += 2 }
                        't' -> { append('\t'); i += 2 }
                        else -> { append(ch); i++ }
                    }
                } else {
                    append(ch)
                    i++
                }
            }
        }
    }

    /**
     * Slice a JSON array starting at [start].
     * When [escapedQuotes] is true, string delimiters are the two-char sequence \"
     * (RSC flight embedding); bare [ ] still form array structure.
     */
    private fun sliceBalancedArray(
        payload: String,
        start: Int,
        escapedQuotes: Boolean = false,
    ): String? {
        if (start < 0 || start >= payload.length || payload[start] != '[') return null
        var depth = 0
        var inString = false
        var escaped = false
        var i = start
        while (i < payload.length) {
            val ch = payload[i]
            if (escapedQuotes) {
                if (inString) {
                    if (ch == '\\' && i + 1 < payload.length) {
                        val next = payload[i + 1]
                        // \" ends/toggles the RSC-escaped JSON string.
                        if (next == '"') {
                            inString = false
                            i += 2
                            continue
                        }
                        // \\ or \n / \t / \/ etc. inside the string.
                        i += 2
                        continue
                    }
                    i++
                    continue
                }
                if (ch == '\\' && i + 1 < payload.length && payload[i + 1] == '"') {
                    inString = true
                    i += 2
                    continue
                }
                when (ch) {
                    '[' -> depth++
                    ']' -> {
                        depth--
                        if (depth == 0) return payload.substring(start, i + 1)
                    }
                }
                i++
                continue
            }

            // Plain JSON string / array balancer.
            if (inString) {
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == '"' -> inString = false
                }
                i++
                continue
            }
            when (ch) {
                '"' -> inString = true
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return payload.substring(start, i + 1)
                }
            }
            i++
        }
        return null
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
