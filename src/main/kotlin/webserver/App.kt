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


suspend fun PipelineContext<Unit, ApplicationCall>.ensureGame(gameManager: GameManager, id: Int): Game<*, *, *>? {
    return gameManager.get(id) ?: run {
        call.respondText("Game with id=${id} does not exist")
        call.response.status(HttpStatusCode.NotFound)
        null
    }
}


suspend fun MutableSet<DefaultWebSocketSession>.removeIfInvalid(block: suspend (DefaultWebSocketSession) -> Unit) {
    val toRemove = mutableSetOf<DefaultWebSocketSession>()
    for (conn in this) {
        try {
            block(conn)
        } catch (e: Throwable) {
            toRemove.add(conn)
        }
    }
    this.removeAll(toRemove)
}


fun main(args: Array<String>) {
    val config = Config.readOrDefault("config")
    println("Starting server with $config")

    val gameManager = GameManager()
    gameManager.create(GameKind.CLASSIC_CHESS)
    gameManager.create(GameKind.QUANTUM_CHESS)

    val server = embeddedServer(Netty, host = config.host, port = config.port) {
        installFeatures()
        routing {
            val connections = Collections.synchronizedMap(mutableMapOf<Int, MutableSet<DefaultWebSocketSession>>())

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
                    call.respond(state)
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
                try {
                    val step = call.receive<StepSchema>()
                    val game = ensureGame(gameManager, step.gameId) ?: return@post
                    if (!game.canMove(step.from, step.to, step.additionalStepInfo)) {
                        call.response.status(HttpStatusCode.BadRequest)
                        call.respondText("Can not make step due to rules")
                        return@post
                    }
                    game.step(step.from, step.to, step.additionalStepInfo)
                    if (game.isOver()) {
                        connections[step.gameId]!!.removeIfInvalid { conn ->
                            conn.outgoing.send(Frame.Text("winner:${game.result().winners.toList()[0]}"))
                        }
                    }
                    call.response.status(HttpStatusCode.OK)
                } catch (e: Throwable) {
                    println(e)
                }
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
                call.respond(FreeMarkerContent("playPage.ftl",
                    mapOf(
                        "id" to id,
                        "kind" to game.kind,
                        "port" to config.port,
                        "home_url" to config.homeUrl
                    )
                ))
            }
            get("/game/suggest") {
                try {
                    val id = ensureIntQueryParam("id") ?: return@get
                    val game = ensureGame(gameManager, id) ?: return@get
                    val fromNums = ensureQueryParamList("from") { it.toInt() } ?: return@get
                    call.respond(game.possibleSteps(Coordinate(fromNums)))
                } catch (e: Throwable) {
                    println(e)
                    println(e.stackTrace)
                }
            }

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
                                    conns.removeIfInvalid { conn ->
                                        conn.outgoing.send(Frame.Text(""))
                                    }
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
