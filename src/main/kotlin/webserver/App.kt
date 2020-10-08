package webserver

import core.Coordinate
import core.Game
import dialects.GameKind
import dialects.StateSerializer
import freemarker.cache.ClassTemplateLoader
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.freemarker.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.pipeline.*
import other.GameManager
import java.io.File


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
    install(FreeMarker) {
        templateLoader = ClassTemplateLoader(this::class.java.classLoader, "templates")
    }
}


suspend fun <T> PipelineContext<Unit, ApplicationCall>.ensureQueryParam(name: String, parse: (String) -> T): T? {
    call.request.queryParameters[name]?.let { value ->
        return try {
            parse(value)
        } catch (e: Throwable) {
            call.respondText("Invalid value '${value}' for parameter '${name}'")
            call.response.status(HttpStatusCode.BadRequest)
            null
        }
    }
    call.respondText("Paramater '${name}' is required")
    call.response.status(HttpStatusCode.BadRequest)
    return null
}


suspend fun PipelineContext<Unit, ApplicationCall>.ensureIntQueryParam(name: String) =
        ensureQueryParam(name) {it.toInt()}


suspend fun PipelineContext<Unit, ApplicationCall>.ensureGame(gameManager: GameManager, id: Int): Game<*, *>? {
    return gameManager.get(id) ?: run {
        call.respondText("Game with id=${id} does not exist")
        call.response.status(HttpStatusCode.NotFound)
        null
    }
}


fun main(args: Array<String>) {
    val gameManager = GameManager()
    gameManager.create(GameKind.SIMPLE)

    val server = embeddedServer(Netty, port = 8080) {
        basicAuthApplication()
        routing {
//            authenticate("auth") {
            post("/game/create") {
                try {
                    val newGameSchema = call.receive<NewGameSchema>()
                    val newGame = gameManager.create(newGameSchema.kind)
                    call.respond(newGame)
                } catch (e: Throwable) {
                    println(e)
                }
            }
            get("/game/info") {
                val id = ensureIntQueryParam("id") ?: return@get
                val info = gameManager.info(id)
                if (info == null) {
                    call.respondText("Game with id=${id} does not exist")
                    call.response.status(HttpStatusCode.NotFound)
                    return@get
                }
                call.respond(info)
            }
            get("/game/state") {
                val id = ensureIntQueryParam("id") ?: return@get
                val game = ensureGame(gameManager, id) ?: return@get
                call.respond(SerializableState(StateSerializer.serialize(game), game.state.currentPlayer))
            }
            post("/game/step") {
                val step = call.receive<StepSchema>()
                val game = ensureGame(gameManager, step.gameId) ?: return@post
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
            static("static") {
                staticRootFolder = File("src/main/resources/static")
                files("js")
                files("css")
            }
            get("/") {
                call.respond(FreeMarkerContent("index.ftl", emptyMap<String, Any>()))
            }
            get("/create_page") {
                call.respond(FreeMarkerContent("createPage.ftl", emptyMap<String, Any>()))
            }
            get("/game/play") {
                val id = ensureIntQueryParam("id") ?: return@get
                val game = ensureGame(gameManager, id) ?: return@get
                call.respond(FreeMarkerContent("playPage.ftl", mapOf("id" to id, "kind" to game.kind)))
            }
        }
    }
    server.start(wait = true)
}
