package ru.werelaxe.chess.admin.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.serialization.encodeToString
import java.io.Closeable

/** The admin API refused the request; [toString] of the message is `<status> <code>: <message>`. */
class AdminApiException(val status: Int, val code: String, val detail: String) : RuntimeException("$status $code: $detail")

/** A response of the admin API: the body as the server sent it, decoded on first use. */
class Reply<T>(val raw: String, decode: (String) -> T) {
    val value: T by lazy { decode(raw) }
}

/**
 * Calls the admin API (docs/ARCHITECTURE.md section 2.11) at [baseUrl] with [token] in
 * `X-Admin-Token`. Any answer outside 2xx becomes an [AdminApiException]; transport failures
 * surface as the engine's exceptions. The client owns [engine] and closes it with itself.
 */
class AdminClient(
    private val engine: HttpClientEngine,
    baseUrl: String,
    private val token: String,
) : Closeable {
    private val base = baseUrl.trimEnd('/')
    private val client = HttpClient(engine) {
        expectSuccess = false
        install(HttpTimeout) { requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS }
    }

    suspend fun overview(): Reply<Overview> = exchange(HttpMethod.Get, "/api/admin/overview")

    suspend fun bots(): Reply<BotList> = exchange(HttpMethod.Get, "/api/admin/bots")

    suspend fun bot(username: String): Reply<AdminBotDetails> =
        exchange(HttpMethod.Get, "/api/admin/bots/${username.encodeURLPathPart()}")

    suspend fun spawnBot(request: SpawnBotRequest): Reply<AdminBot> =
        exchange(HttpMethod.Post, "/api/admin/bots", AdminJson.encodeToString(request))

    suspend fun updateBot(username: String, request: UpdateBotRequest): Reply<AdminBot> =
        exchange(HttpMethod.Patch, "/api/admin/bots/${username.encodeURLPathPart()}", AdminJson.encodeToString(request))

    suspend fun settings(): Reply<SettingList> = exchange(HttpMethod.Get, "/api/admin/settings")

    suspend fun setSetting(key: String, value: String): Reply<Setting> =
        exchange(HttpMethod.Put, "/api/admin/settings/${key.encodeURLPathPart()}", AdminJson.encodeToString(SettingValue(value)))

    suspend fun resetSetting(key: String): Reply<Setting> =
        exchange(HttpMethod.Delete, "/api/admin/settings/${key.encodeURLPathPart()}")

    private suspend inline fun <reified T> exchange(method: HttpMethod, path: String, body: String? = null): Reply<T> {
        val response = client.request(base + path) {
            this.method = method
            header(TOKEN_HEADER, token)
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw describe(response.status.value, response.status.description, text)
        }
        return Reply(text) { AdminJson.decodeFromString<T>(it) }
    }

    override fun close() {
        client.close()
        engine.close()
    }

    companion object {
        const val TOKEN_HEADER = "X-Admin-Token"
        private const val REQUEST_TIMEOUT_MILLIS = 30_000L
        private const val MAX_QUOTED_BODY = 200

        /** The server's error code and message when the body is one of its error responses; the reason phrase and the body's start otherwise. */
        internal fun describe(status: Int, reason: String, body: String): AdminApiException {
            val error = runCatching { AdminJson.decodeFromString<ErrorResponse>(body) }.getOrNull()
            return if (error != null) {
                AdminApiException(status, error.error, error.message)
            } else {
                AdminApiException(status, reason, body.trim().take(MAX_QUOTED_BODY).ifEmpty { "(empty body)" })
            }
        }
    }
}
