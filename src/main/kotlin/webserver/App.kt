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
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.pipeline.*
import io.ktor.websocket.*
import other.GameManager
import java.io.File
import java.util.*
import kotlin.collections.LinkedHashSet


fun Application.installFeatures() {
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
    install(WebSockets)
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
    call.respondText("Parameter '${name}' is required")
    call.response.status(HttpStatusCode.BadRequest)
    return null
}


suspend fun <T> PipelineContext<Unit, ApplicationCall>.ensureQueryParamList(name: String, parse: (String) -> T): List<T>? {
    call.request.queryParameters.getAll(name)?.let { value ->
        return try {
            value.map(parse)
        } catch (e: Throwable) {
            call.respondText("Invalid value '${value}' for parameter '${name}'")
            call.response.status(HttpStatusCode.BadRequest)
            null
        }
    }
    call.respondText("Parameter '${name}' is required")
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
    gameManager.create(GameKind.CLASSIC_CHESS)

    val server = embeddedServer(Netty, port = 8080) {
        installFeatures()
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
                try {
                    val id = ensureIntQueryParam("id") ?: return@get
                    val game = ensureGame(gameManager, id) ?: return@get
                    val state = StateSerializer.serialize(game)
                    call.respond(SerializableState(state, game.state.currentPlayer))
                } catch (e: Throwable) {
                    println(e)
                    println(e.stackTrace)
                }
            }
            get("/game/figures") {
                try {
                    val id = ensureIntQueryParam("id") ?: return@get
                    val game = ensureGame(gameManager, id) ?: return@get
                    val ids = ensureQueryParamList("ids") { it.toInt() } ?: return@get
                    call.respond(StateSerializer.figures(game.kind, ids))
                } catch (e: Throwable) {
                    println(e)
                    println(e.stackTrace)
                }
            }
            post("/game/step") {
                val step = call.receive<StepSchema>()
                val game = ensureGame(gameManager, step.gameId) ?: return@post
                if (!game.canMove(step.from, step.to)) {
                    call.response.status(HttpStatusCode.BadRequest)
                    call.respondText("Can not make step due to rules")
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
                files("images")
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
            get("/game/suggest") {
                val id = ensureIntQueryParam("id") ?: return@get
                val game = ensureGame(gameManager, id) ?: return@get
                val fromNums = ensureQueryParamList("from") { it.toInt() } ?: return@get
                call.respond(game.possibleSteps(Coordinate(fromNums)))
            }

            val connections = Collections.synchronizedMap(mutableMapOf<Int, MutableSet<DefaultWebSocketSession>>())

            webSocket("/game/notify") {
                while (true) {
                    val frame = incoming.receive()
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        if (text.startsWith("new:")) {
                            text.substring(4, text.length).toIntOrNull()?.let { gameId ->
                                if (gameId !in connections) {
                                    connections[gameId] = mutableSetOf()
                                }
                                connections[gameId]!!.add(this)
                            }
                        } else if (text.startsWith("step:")) {
                            text.substring(5, text.length).toIntOrNull()?.let { gameId ->
                                connections[gameId]?.let { conns ->
                                    val toRemove = mutableSetOf<DefaultWebSocketSession>()

                                    for (conn in conns) {
                                        try {
                                            conn.outgoing.send(Frame.Text(""))
                                        } catch (e: Throwable) {
                                            toRemove.add(conn)
                                        }
                                    }

                                    conns.removeAll(toRemove)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    server.start(wait = true)
}