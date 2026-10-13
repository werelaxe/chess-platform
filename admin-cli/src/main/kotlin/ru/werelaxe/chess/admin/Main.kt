package ru.werelaxe.chess.admin

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.obj
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import ru.werelaxe.chess.admin.client.AdminClient
import ru.werelaxe.chess.admin.commands.BotsCommand
import ru.werelaxe.chess.admin.commands.GamesCommand
import ru.werelaxe.chess.admin.commands.OverviewCommand
import ru.werelaxe.chess.admin.commands.Session
import ru.werelaxe.chess.admin.commands.SettingsCommand

const val API_ENV = "CHESS_API_URL"
const val TOKEN_ENV = "CHESS_ADMIN_TOKEN"
const val DEFAULT_API = "http://localhost:8080"

fun main(args: Array<String>) = ChessAdmin().main(args)

/**
 * The root command: `chess-admin [--api URL] [--token TOKEN] [--json] <command>`
 * (docs/ARCHITECTURE.md section 2.11). [engine] builds the HTTP engine the commands talk
 * through; tests pass a mock.
 */
class ChessAdmin(private val engine: () -> HttpClientEngine = { CIO.create() }) : CliktCommand(name = "chess-admin") {
    private val api by option("--api", metavar = "URL", envvar = API_ENV, help = "Base URL of the API server (env $API_ENV)")
        .default(DEFAULT_API)
    private val token by option("--token", metavar = "TOKEN", envvar = TOKEN_ENV, help = "The server's ADMIN_TOKEN (env $TOKEN_ENV)")
    private val json by option("--json", help = "Print the API response as JSON instead of a table").flag()

    init {
        subcommands(OverviewCommand(), BotsCommand(), GamesCommand(), SettingsCommand())
    }

    override fun help(context: Context) = "Administers a chess platform server through its admin API."

    override fun run() {
        val token = token?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw UsageError("Missing admin token: pass --token or set $TOKEN_ENV to the server's ADMIN_TOKEN")
        val client = AdminClient(engine(), api, token)
        currentContext.callOnClose { client.close() }
        currentContext.obj = Session(client, api, json)
    }
}
