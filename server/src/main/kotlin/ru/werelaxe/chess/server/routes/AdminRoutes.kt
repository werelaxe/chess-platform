package ru.werelaxe.chess.server.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.json.JsonNull
import ru.werelaxe.chess.server.dto.AdminBotsResponse
import ru.werelaxe.chess.server.dto.CreateBotRequest
import ru.werelaxe.chess.server.dto.GamesResponse
import ru.werelaxe.chess.server.dto.SettingUpdateRequest
import ru.werelaxe.chess.server.dto.SettingsResponse
import ru.werelaxe.chess.server.dto.UpdateBotRequest
import ru.werelaxe.chess.server.model.GamePhase
import ru.werelaxe.chess.server.plugins.AdminGuard
import ru.werelaxe.chess.server.service.AdminService
import ru.werelaxe.chess.server.service.ApiException
import ru.werelaxe.chess.server.service.Settings

private val ApplicationCall.botUsername: String
    get() = parameters["username"] ?: throw ApiException.notFound("Bot")

private val ApplicationCall.settingKey: String
    get() = parameters["key"] ?: throw ApiException.validation("A setting key is required")

/** The `status` query parameter of the game listing, when present. */
private fun ApplicationCall.statusParameter(): GamePhase? = request.queryParameters["status"]?.let { value ->
    GamePhase.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        ?: throw ApiException.validation("Unknown status '$value'; expected WAITING, ACTIVE or FINISHED")
}

/**
 * The admin API (docs/ARCHITECTURE.md section 2.11), guarded by [AdminGuard]. Without a
 * configured token nothing is registered, so the whole subtree is a 404 like any unknown path.
 */
fun Route.adminRoutes(admin: AdminService, settings: Settings, adminToken: String?) {
    if (adminToken == null) return
    route("/api/admin") {
        install(AdminGuard) { token = adminToken }
        get("/overview") {
            call.respond(admin.overview())
        }
        route("/bots") {
            get {
                call.respond(AdminBotsResponse(admin.listBots()))
            }
            post {
                val request = call.receive<CreateBotRequest>()
                call.respond(HttpStatusCode.Created, admin.createBot(request))
            }
            get("/{username}") {
                call.respond(admin.bot(call.botUsername))
            }
            patch("/{username}") {
                val request = call.receive<UpdateBotRequest>()
                call.respond(admin.updateBot(call.botUsername, request))
            }
        }
        route("/games") {
            get {
                val (limit, offset) = call.paging()
                call.respond(GamesResponse(admin.listGames(call.statusParameter(), limit, offset)))
            }
            delete("/{id}") {
                admin.deleteGame(call.gameId)
                call.respond(HttpStatusCode.NoContent)
            }
            post("/{id}/abort") {
                call.respond(admin.abortGame(call.gameId))
            }
        }
        route("/settings") {
            get {
                call.respond(SettingsResponse(settings.list()))
            }
            put("/{key}") {
                val request = call.receive<SettingUpdateRequest>()
                if (request.value is JsonNull) throw ApiException.validation("value must be a string")
                call.respond(settings.set(call.settingKey, request.value.content))
            }
            delete("/{key}") {
                call.respond(settings.reset(call.settingKey))
            }
        }
    }
}
