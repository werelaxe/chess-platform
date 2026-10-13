package ru.werelaxe.chess.admin.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.enum
import ru.werelaxe.chess.admin.client.BotLevel
import ru.werelaxe.chess.admin.client.SpawnBotRequest
import ru.werelaxe.chess.admin.client.UpdateBotRequest
import ru.werelaxe.chess.admin.output.Render

class BotsCommand : CliktCommand(name = "bots") {
    init {
        subcommands(BotsListCommand(), BotsShowCommand(), BotsSpawnCommand(), BotsSetCommand())
    }

    override fun help(context: Context) = "List, inspect, create and change the bots."

    override fun run() = Unit
}

class BotsListCommand : CliktCommand(name = "list") {
    private val session by requireObject<Session>()

    override fun help(context: Context) = "The roster with levels, ratings and game counts."

    override fun run() {
        echo(session.show(session.exchange { bots() }) { Render.bots(it.bots) })
    }
}

class BotsShowCommand : CliktCommand(name = "show") {
    private val session by requireObject<Session>()
    private val username by argument(help = "The bot's username")

    override fun help(context: Context) = "One bot with its ratings, active games and recent games."

    override fun run() {
        echo(session.show(session.exchange { bot(username) }, Render::bot))
    }
}

class BotsSpawnCommand : CliktCommand(name = "spawn") {
    private val session by requireObject<Session>()
    private val username by option("--username", help = "Username of the new bot").required()
    private val name by option("--name", help = "Display name of the new bot").required()
    private val level by option("--level", help = "Strength of the new bot").enum<BotLevel>().required()

    override fun help(context: Context) = "Create a bot; it joins the roster at once."

    override fun run() {
        val reply = session.exchange { spawnBot(SpawnBotRequest(username, name, level)) }
        echo(session.show(reply) { Render.bots(listOf(it)) })
    }
}

class BotsSetCommand : CliktCommand(name = "set") {
    private val session by requireObject<Session>()
    private val username by argument(help = "The bot's username")
    private val level by option("--level", help = "New strength").enum<BotLevel>()
    private val name by option("--name", help = "New display name")

    override fun help(context: Context) = "Change a bot's level and/or display name."

    override fun run() {
        if (level == null && name == null) throw UsageError("Nothing to change: pass --level and/or --name")
        val reply = session.exchange { updateBot(username, UpdateBotRequest(level, name)) }
        echo(session.show(reply) { Render.bots(listOf(it)) })
    }
}
