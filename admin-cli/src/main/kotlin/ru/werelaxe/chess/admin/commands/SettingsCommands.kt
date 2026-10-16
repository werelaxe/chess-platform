package ru.werelaxe.chess.admin.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.check
import com.github.ajalt.clikt.parameters.options.check
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import ru.werelaxe.chess.admin.output.Render

const val OPEN_GAMES_KEY = "bot.open_games_target"
const val LIVE_GAMES_KEY = "bot.live_games_target"
const val HUMAN_GAMES_LIMIT_KEY = "bot.human_games_limit"
const val REPLY_PAUSE_KEY = "bot.reply_pause_millis"
const val VS_BOT_PAUSE_KEY = "bot.vs_bot_pause_millis"

class SettingsCommand : CliktCommand(name = "settings") {
    init {
        subcommands(
            SettingsListCommand(),
            SettingsSetCommand(),
            SettingsResetCommand(),
            SettingsOpenGamesCommand(),
            SettingsLiveGamesCommand(),
            SettingsHumanGamesLimitCommand(),
            SettingsPausesCommand(),
        )
    }

    override fun help(context: Context) =
        "Runtime settings (of the bots, and the age at which an untouched game is finished); they live in the database and apply without a restart."

    override fun run() = Unit
}

class SettingsListCommand : CliktCommand(name = "list") {
    private val session by requireObject<Session>()

    override fun help(context: Context) = "Every setting with its value, default and where the value comes from."

    override fun run() {
        echo(session.show(session.exchange { settings() }) { Render.settings(it.settings) })
    }
}

class SettingsSetCommand : CliktCommand(name = "set") {
    private val session by requireObject<Session>()
    private val key by argument(help = "Setting key, for example bot.matchmaking")
    private val value by argument(help = "New value; the server validates it")

    override fun help(context: Context) = "Set a setting."

    override fun run() {
        echo(session.show(session.exchange { setSetting(key, value) }) { Render.settings(listOf(it)) })
    }
}

class SettingsResetCommand : CliktCommand(name = "reset") {
    private val session by requireObject<Session>()
    private val key by argument(help = "Setting key")

    override fun help(context: Context) = "Reset a setting to its default."

    override fun run() {
        echo(session.show(session.exchange { resetSetting(key) }) { Render.settings(listOf(it)) })
    }
}

class SettingsOpenGamesCommand : CliktCommand(name = "open-games") {
    private val session by requireObject<Session>()
    private val target by argument(name = "n", help = "Public open games the bots keep waiting; 0 switches this off")
        .int()
        .check("must not be negative") { it >= 0 }

    override fun help(context: Context) = "Set how many open games the bots keep in the lobby ($OPEN_GAMES_KEY)."

    override fun run() {
        val reply = session.exchange { setSetting(OPEN_GAMES_KEY, target.toString()) }
        echo(session.show(reply) { Render.settings(listOf(it)) })
    }
}

class SettingsLiveGamesCommand : CliktCommand(name = "live-games") {
    private val session by requireObject<Session>()
    private val target by argument(name = "n", help = "Bot-versus-bot games kept in progress for spectators; 0 switches this off")
        .int()
        .check("must not be negative") { it >= 0 }

    override fun help(context: Context) = "Set how many live bot-versus-bot games the matchmaker keeps going ($LIVE_GAMES_KEY)."

    override fun run() {
        val reply = session.exchange { setSetting(LIVE_GAMES_KEY, target.toString()) }
        echo(session.show(reply) { Render.settings(listOf(it)) })
    }
}

class SettingsHumanGamesLimitCommand : CliktCommand(name = "human-games-limit") {
    private val session by requireObject<Session>()
    private val limit by argument(
        name = "n",
        help = "Games with a human played within the last 15 minutes at or above which bots start no games with each other; 0 switches this off",
    )
        .int()
        .check("must not be negative") { it >= 0 }

    override fun help(context: Context) = "Set the human load above which the bots stop playing each other ($HUMAN_GAMES_LIMIT_KEY)."

    override fun run() {
        val reply = session.exchange { setSetting(HUMAN_GAMES_LIMIT_KEY, limit.toString()) }
        echo(session.show(reply) { Render.settings(listOf(it)) })
    }
}

class SettingsPausesCommand : CliktCommand(name = "pauses") {
    private val session by requireObject<Session>()
    private val reply by option("--reply", metavar = "ms", help = "Shortest pause before a bot answers a human ($REPLY_PAUSE_KEY)")
        .int()
        .check("must not be negative") { it >= 0 }
    private val bots by option("--bots", metavar = "ms", help = "Shortest pause between the moves of two bots ($VS_BOT_PAUSE_KEY)")
        .int()
        .check("must not be negative") { it >= 0 }

    override fun help(context: Context) = "Set the pauses of the bots, in milliseconds."

    override fun run() {
        if (reply == null && bots == null) throw UsageError("Nothing to change: pass --reply and/or --bots")
        val replies = session.exchange {
            listOfNotNull(
                reply?.let { setSetting(REPLY_PAUSE_KEY, it.toString()) },
                bots?.let { setSetting(VS_BOT_PAUSE_KEY, it.toString()) },
            )
        }
        echo(session.show(replies, Render::settings))
    }
}
