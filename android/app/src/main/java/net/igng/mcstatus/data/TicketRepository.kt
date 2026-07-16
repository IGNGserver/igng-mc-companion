package net.igng.mcstatus.data

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class TicketRepository(private val mcBaseUrl: String, private val ssoBaseUrl: String, private val client: OkHttpClient = OkHttpClient()) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }
    suspend fun captcha(): MathCaptcha = get("$ssoBaseUrl/api/auth/math-captcha")
    suspend fun login(identifier: String, password: String, duration: String, captcha: MathCaptcha, answer: String): LoginResponse = post("$ssoBaseUrl/api/mobile/auth/login", buildJsonObject { put("identifier", identifier); put("password", password); put("duration", duration); put("deviceName", "IGNGmc Android"); put("mathCaptchaToken", captcha.token); put("mathCaptchaAnswer", answer) })
    suspend fun me(token: String): MobileMeResponse = get("$ssoBaseUrl/api/mobile/auth/me", token)
    suspend fun logout(token: String) { post<LogoutResponse>("$ssoBaseUrl/api/mobile/auth/logout", buildJsonObject {}, token) }
    suspend fun tickets(token: String, participant: String, status: String): TicketsResponse = get("$mcBaseUrl/api/mobile/reports?participant=$participant&status=$status", token)
    suspend fun ticket(token: String, id: Int): TicketDetailResponse = get("$mcBaseUrl/api/mobile/reports/$id", token)
    suspend fun action(token: String, id: Int, body: kotlinx.serialization.json.JsonObject) { post<ActionResponse>("$mcBaseUrl/api/mobile/reports/$id", body, token) }
    suspend fun create(token: String, title: String, content: String, visibility: String, targets: List<String>, serverId: Int): Int {
        val response: CreateResponse = post("$mcBaseUrl/api/mobile/reports", buildJsonObject { put("title", title); put("content", content); put("visibility", visibility); put("serverId", serverId); put("targets", buildJsonArray { targets.forEach { add(JsonPrimitive(it)) } }) }, token)
        return response.id
    }
    @kotlinx.serialization.Serializable private data class CreateResponse(val ok: Boolean, val id: Int)
    @kotlinx.serialization.Serializable private data class ActionResponse(val ok: Boolean)
    @kotlinx.serialization.Serializable private data class LogoutResponse(val success: Boolean)
    @kotlinx.serialization.Serializable data class MobileMeResponse(val success: Boolean, val user: LoginUser)
    private suspend inline fun <reified T> get(url: String, token: String? = null): T = withContext(Dispatchers.IO) { request(url, null, token) }
    private suspend inline fun <reified T> post(url: String, body: kotlinx.serialization.json.JsonObject, token: String? = null): T = withContext(Dispatchers.IO) { request(url, body.toString().toRequestBody("application/json".toMediaType()), token) }
    private inline fun <reified T> request(url: String, body: okhttp3.RequestBody?, token: String?): T {
        val request = Request.Builder().url(url).apply { if (token != null) header("Authorization", "Bearer $token") }.method(if (body == null) "GET" else "POST", body).build()
        client.newCall(request).execute().use { response -> val text = response.body?.string().orEmpty(); if (!response.isSuccessful) throw IOException(runCatching { json.parseToJsonElement(text).jsonObject["error"]?.toString()?.trim('"') }.getOrDefault("请求失败 ${response.code}")); return json.decodeFromString(text) }
    }
}
