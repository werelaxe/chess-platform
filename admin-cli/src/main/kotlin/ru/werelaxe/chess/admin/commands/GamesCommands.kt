package ru.werelaxe.chess.admin.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.check
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.int
import ru.werelaxe.chess.admin.client.GameStatus
import ru.werelaxe.chess.admin.output.Render

class GamesCommand : CliktCommand(name = "games") {
    init {
        subcommands(GamesListCommand(), GamesDropCommand(), GamesAbortCommand())
    }

    override fun help(context: Context) = "List, abort and delete games."

    override fun run() = Unit
}

class GamesListCommand : CliktCommand(name = "list") {
    private val session by requireObject<Session>()
    private val status by option("--status", help = "Only games in this phase").enum<GameStatus>()
    private val limit by option("--limit", metavar = "N", help = "At most this many games, newest first")
        .int()
        .check("must be positive") { it > 0 }

    override fun help(context: Context) = "The games, newest first, with their players, move counts and results."

    override fun run() {
        echo(session.show(session.exchange { games(status, limit) }) { Render.games(it.games) })
    }
}

class GamesDropCommand : CliktCommand(name = "drop") {
    private val session by requireObject<Session>()
    private val id by argument(help = "The game's id")

    override fun help(context: Context) = "Delete a game outright, as if it had never been created."

    override fun run() {
        val text = session.show(session.exchange { dropGame(id) }) { "dropped $id" }
        if (text.isNotEmpty()) echo(text)
    }
}

class GamesAbortCommand : CliktCommand(name = "abort") {
    private val session by requireObject<Session>()
    private val id by argument(help = "The game's id")

    override fun help(context: Context) = "End a game without a winner; it stays in the records."

    override fun run() {
        echo(session.show(session.exchange { abortGame(id) }) { "aborted ${Render.status(it)}" })
    }
}
