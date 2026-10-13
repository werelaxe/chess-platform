package ru.werelaxe.chess.admin

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.werelaxe.chess.admin.client.AdminClient

/** The JSON the admin API sends, shaped like the server's DTOs including the fields the tool ignores. */
object Fixtures {
    const val NICK_USER = """{"id":7,"username":"novice-nick","name":"Nick the Novice","bot":true,"botLevel":"NOVICE","rating":null}"""
    const val ALICE_USER = """{"id":2,"username":"alice","name":null,"botLevel":null,"rating":1512}"""

    const val NICK = """{"user":$NICK_USER,""" +
        """"ratings":{"CLASSIC":{"rating":1480,"games":25,"wins":10,"draws":5,"losses":10,"peak":1520},"QUANTUM":null},""" +
        """"level":"NOVICE","legacy":false,"activeGames":1,"totalGames":25}"""

    const val COMPUTER = """{"user":{"id":1,"username":"computer","name":"Computer","bot":true,"botLevel":"MEDIUM","rating":null},""" +
        """"ratings":{"CLASSIC":null,"QUANTUM":null},"level":"MEDIUM","legacy":true,"activeGames":0,"totalGames":100}"""

    const val BOTS = """{"bots":[$NICK,$COMPUTER]}"""

    const val ACTIVE_GAME = """{"id":"aZ3kq9bX7LmN","kind":"QUANTUM","visibility":"PUBLIC","status":"ACTIVE","rated":true,""" +
        """"white":$NICK_USER,"black":$ALICE_USER,"creator":$ALICE_USER,"invitee":$NICK_USER,"moveCount":12,"result":null,""" +
        """"drawOfferedBy":null,"createdAt":"2026-10-07T10:00:00Z","updatedAt":"2026-10-07T10:30:00Z"}"""

    const val FINISHED_GAME = """{"id":"bY4lr0cY8MnO","kind":"CLASSIC","visibility":"PUBLIC","status":"FINISHED","rated":false,""" +
        """"white":$ALICE_USER,"black":$NICK_USER,"creator":$NICK_USER,"invitee":null,"moveCount":41,""" +
        """"result":{"winner":"BLACK","reason":"CHECKMATE"},"drawOfferedBy":null,""" +
        """"createdAt":"2026-10-06T09:00:00Z","updatedAt":"2026-10-06T09:45:00Z"}"""

    const val WAITING_GAME = """{"id":"cX5ms1dZ9NoP","kind":"CLASSIC","visibility":"PUBLIC","status":"WAITING","rated":true,""" +
        """"white":$ALICE_USER,"black":null,"creator":$ALICE_USER,"invitee":null,"moveCount":0,"result":null,""" +
        """"drawOfferedBy":null,"createdAt":"2026-10-07T11:00:00Z","updatedAt":"2026-10-07T11:00:00Z"}"""

    /** Every game the fake knows, newest first. */
    val GAMES = listOf(WAITING_GAME, ACTIVE_GAME, FINISHED_GAME)

    /** [ACTIVE_GAME] after `POST /api/admin/games/{id}/abort`. */
    val ABORTED_GAME = ACTIVE_GAME
        .replace(""""status":"ACTIVE"""", """"status":"FINISHED"""")
        .replace(""""result":null""", """"result":{"winner":null,"reason":"ABANDONMENT"}""")
        .replace("2026-10-07T10:30:00Z", "2026-10-07T10:31:00Z")

    const val NICK_DETAILS = """{"user":$NICK_USER,""" +
        """"ratings":{"CLASSIC":{"rating":1480,"games":25,"wins":10,"draws":5,"losses":10,"peak":1520},"QUANTUM":null},""" +
        """"level":"NOVICE","legacy":false,"activeGames":1,"totalGames":25,""" +
        """"activeGameList":[$ACTIVE_GAME],"recentGames":[$ACTIVE_GAME,$FINISHED_GAME]}"""

    const val OVERVIEW = """{"users":123,"guests":45,"bots":8,"games":{"waiting":2,"active":5,"finished":1000},"activeBotGames":3,""" +
        """"settings":{"bot.open_games_target":"2","bot.matchmaking":"true"}}"""

    const val MATCHMAKING_SETTING = """{"key":"bot.matchmaking","value":"true","default":"true","source":"default",""" +
        """"description":"the matchmaker loop acts on its ticks"}"""
    const val OPEN_GAMES_SETTING = """{"key":"bot.open_games_target","value":"3","default":"2","source":"db",""" +
        """"description":"public open games kept waiting by bots"}"""
    const val SETTINGS = """{"settings":[$MATCHMAKING_SETTING,$OPEN_GAMES_SETTING]}"""

    val KNOWN_SETTINGS = mapOf(
        "bot.matchmaking" to "true",
        "bot.open_games_target" to "2",
        "bot.live_games_target" to "1",
        "bot.reply_pause_millis" to "700",
        "bot.vs_bot_pause_millis" to "0",
    )
}

/** A mock engine that plays the admin API: records every request and answers like the server would. */
class FakeAdminApi {
    data class Recorded(val method: HttpMethod, val url: String, val token: String?, val contentType: String?, val body: String)

    val requests = mutableListOf<Recorded>()

    fun engine() = MockEngine { request ->
        val body = request.body.toByteArray().decodeToString()
        requests += Recorded(request.method, request.url.toString(), request.headers[AdminClient.TOKEN_HEADER], request.body.contentType?.toString(), body)
        if (request.headers[AdminClient.TOKEN_HEADER] != TOKEN) {
            error(HttpStatusCode.Unauthorized, "unauthorized", "Bad admin token")
        } else {
            route(request.method, request.url.encodedPath, request.url.parameters, body)
        }
    }

    private fun MockRequestHandleScope.route(method: HttpMethod, path: String, query: Parameters, body: String): HttpResponseData {
        val bot = path.removePrefix("/api/admin/bots/").takeIf { path.startsWith("/api/admin/bots/") }
        val game = path.removePrefix("/api/admin/games/").takeIf { path.startsWith("/api/admin/games/") }
        val setting = path.removePrefix("/api/admin/settings/").takeIf { path.startsWith("/api/admin/settings/") }
        return when {
            method == HttpMethod.Get && path == "/api/admin/overview" -> json(Fixtures.OVERVIEW)
            method == HttpMethod.Get && path == "/api/admin/bots" -> json(Fixtures.BOTS)
            method == HttpMethod.Post && path == "/api/admin/bots" -> {
                val fields = Json.parseToJsonElement(body).jsonObject
                json(
                    """{"user":{"id":9,"username":${fields["username"]},"name":${fields["displayName"]},"bot":true,""" +
                        """"botLevel":${fields["level"]},"rating":null},"ratings":{"CLASSIC":null,"QUANTUM":null},""" +
                        """"level":${fields["level"]},"legacy":false,"activeGames":0,"totalGames":0}""",
                    HttpStatusCode.Created,
                )
            }
            bot == "novice-nick" && method == HttpMethod.Get -> json(Fixtures.NICK_DETAILS)
            bot == "novice-nick" && method == HttpMethod.Patch -> {
                val fields = Json.parseToJsonElement(body).jsonObject
                val level = fields["level"]?.jsonPrimitive?.content ?: "NOVICE"
                val name = fields["displayName"]?.jsonPrimitive?.content ?: "Nick the Novice"
                json(Fixtures.NICK.replace("\"NOVICE\"", "\"$level\"").replace("Nick the Novice", name))
            }
            bot != null && (method == HttpMethod.Get || method == HttpMethod.Patch) ->
                error(HttpStatusCode.NotFound, "not_found", "Unknown bot '$bot'")
            method == HttpMethod.Get && path == "/api/admin/games" -> {
                val status = query["status"]
                if (status != null && status !in STATUSES) return error(HttpStatusCode.BadRequest, "validation", "Unknown status '$status'")
                val limit = query["limit"]?.let { it.toIntOrNull()?.takeIf { n -> n > 0 } ?: return error(HttpStatusCode.BadRequest, "validation", "limit must be positive") }
                val games = Fixtures.GAMES.filter { status == null || """"status":"$status"""" in it }.take(limit ?: Fixtures.GAMES.size)
                json("""{"games":[${games.joinToString(",")}]}""")
            }
            game != null && method == HttpMethod.Delete -> when (game) {
                in GAME_IDS -> respond("", HttpStatusCode.NoContent)
                else -> error(HttpStatusCode.NotFound, "not_found", "Unknown game '$game'")
            }
            game != null && game.endsWith("/abort") && method == HttpMethod.Post -> when (val id = game.removeSuffix("/abort")) {
                "aZ3kq9bX7LmN", "cX5ms1dZ9NoP" -> json(Fixtures.ABORTED_GAME.replace("aZ3kq9bX7LmN", id))
                "bY4lr0cY8MnO" -> error(HttpStatusCode.Conflict, "game_over", "The game is already over")
                else -> error(HttpStatusCode.NotFound, "not_found", "Unknown game '$id'")
            }
            method == HttpMethod.Get && path == "/api/admin/settings" -> json(Fixtures.SETTINGS)
            setting != null && method == HttpMethod.Put -> {
                val default = Fixtures.KNOWN_SETTINGS[setting]
                    ?: return error(HttpStatusCode.BadRequest, "validation", "Unknown setting '$setting'")
                val value = Json.parseToJsonElement(body).jsonObject.getValue("value").jsonPrimitive.content
                json("""{"key":"$setting","value":"$value","default":"$default","source":"db","description":"a setting"}""")
            }
            setting != null && method == HttpMethod.Delete -> {
                val default = Fixtures.KNOWN_SETTINGS[setting]
                    ?: return error(HttpStatusCode.BadRequest, "validation", "Unknown setting '$setting'")
                json("""{"key":"$setting","value":"$default","default":"$default","source":"default","description":"a setting"}""")
            }
            else -> error(HttpStatusCode.NotFound, "not_found", "No such endpoint")
        }
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

    private fun MockRequestHandleScope.error(status: HttpStatusCode, code: String, message: String) =
        json("""{"error":"$code","message":"$message"}""", status)

    companion object {
        const val TOKEN = "secret-admin-token-0123"
        private val STATUSES = setOf("WAITING", "ACTIVE", "FINISHED")
        private val GAME_IDS = setOf("cX5ms1dZ9NoP", "aZ3kq9bX7LmN", "bY4lr0cY8MnO")
    }
}
