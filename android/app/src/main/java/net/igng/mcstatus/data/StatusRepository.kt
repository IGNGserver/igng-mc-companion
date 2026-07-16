package net.igng.mcstatus.data

import java.io.IOException
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class StatusRepository(
    private val baseUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    },
) {
    suspend fun fetchServers(): List<ServerSummary> =
        getJson("$baseUrl/api/status/list")

    suspend fun fetchNodes(): List<NodeSummary> =
        getJson("$baseUrl/api/status/nodes")

    suspend fun fetchOverview(): List<OverviewServerSnapshot> =
        getJson<OverviewResponse>("$baseUrl/api/status/overview").data.map { payload ->
            OverviewServerSnapshot(
                server = payload.server,
                latestPerf = payload.latestPerf,
                bestLatency = payload.bestLatency
            )
        }

    suspend fun fetchTimeline(
        serverIds: List<Int>,
        preset: RangePreset,
    ): TimelineResponse {
        if (serverIds.isEmpty()) {
            return TimelineResponse()
        }

        val (start, end) = buildRange(preset)
        val url = "$baseUrl/api/status/timeline".toHttpUrl().newBuilder()
            .addQueryParameter("serverIds", serverIds.joinToString(","))
            .addQueryParameter("start", start)
            .addQueryParameter("end", end)
            .build()

        return getJson(url.toString())
    }

    suspend fun fetchServerDetail(
        serverId: Int,
        preset: RangePreset,
    ): ServerDetailResponse {
        val (start, end) = buildRange(preset)
        val url = "$baseUrl/api/status/server/$serverId".toHttpUrl().newBuilder()
            .addQueryParameter("start", start)
            .addQueryParameter("end", end)
            .build()

        return getJson(url.toString())
    }

    private suspend inline fun <reified T> getJson(
        url: String,
    ): T = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Request failed: ${response.code} $url")
            }

            val body = response.body?.string()
                ?: throw IOException("Empty response body: $url")
            json.decodeFromString(body)
        }
    }

    private fun buildRange(preset: RangePreset): Pair<String, String> {
        val end = Instant.now()
        val start = end.minus(preset.hours, ChronoUnit.HOURS)
        return start.toString() to end.toString()
    }
}
