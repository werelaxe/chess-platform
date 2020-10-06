package webserver

import core.Coordinate
import dialects.GameKind
import dialects.StateSerializer
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.pipeline.*
import other.GameManager


fun Application.basicAuthApplication() {
    install(Authentication) {
        basic(name = "myauth1") {
            realm = "Ktor Server"
            validate { credentials ->
                if (credentials.name == credentials.password) {
                    UserIdPrincipal(credentials.name)
                } else {
                    null
                }
            }
        }
    }
    install(ContentNegotiation) {
        json()
    }
}


suspend fun PipelineContext<Unit, ApplicationCall>.ensureQueryParam(name: String, errorMessage: String): String? {
    call.request.queryParameters[name]?.let { value ->
        return value
    }
    call.respondText(errorMessage)
    call.response.status(HttpStatusCode.BadRequest)
    return null
}


fun main(args: Array<String>) {
    val gameManager = GameManager()
    gameManager.create(GameKind.SIMPLE)

    val server = embeddedServer(Netty, port = 8080) {
        basicAuthApplication()
        routing {
//            authenticate("auth") {
            post("/game/create") {
                val newGameSchema = call.receive<NewGameSchema>()
                val newGame = gameManager.create(newGameSchema.kind)
                call.respond(newGame)
            }
            get("/game/info") {
                val rawId = ensureQueryParam("id", "Parameter 'id' is required") ?: return@get
                val id = rawId.toIntOrNull() ?: run {
                    call.response.status(HttpStatusCode.BadRequest)
                    return@get
                }
                val info = gameManager.info(id)
                if (info == null) {
                    call.respondText("Game with id=${id} does not exist")
                    call.response.status(HttpStatusCode.NotFound)
                    return@get
                }
                call.respond(info)
            }
            get("/game/state") {
                val rawId = ensureQueryParam("id", "Parameter 'id' is required") ?: return@get
                val id = rawId.toIntOrNull() ?: run {
                    call.response.status(HttpStatusCode.BadRequest)
                    return@get
                }
                val game = gameManager.get(id) ?: run {
                    call.respondText("Game with id=${id} does not exist")
                    call.response.status(HttpStatusCode.NotFound)
                    return@get
                }
                call.respond(SerializableState(StateSerializer.serialize(game), game.state.currentPlayer))
            }
            post("/game/step") {
                val step = call.receive<StepSchema>()
                val game = gameManager.get(step.gameId) ?: run {
                    call.respondText("Game with id=${step.gameId} does not exist")
                    call.response.status(HttpStatusCode.NotFound)
                    return@post
                }
                if (!game.canMove(step.from, step.to)) {
                    call.respondText("Can not make step due to rules")
                    call.response.status(HttpStatusCode.NotFound)
                    return@post
                }
                game.step(step.from, step.to)
                call.response.status(HttpStatusCode.OK)
            }
            get("/sandbox") {
                val x = StepSchema(123, Coordinate.of(1, 2), Coordinate.of(5, 6))
                call.respond(x)
            }
        }
    }
    server.start(wait = true)
}
