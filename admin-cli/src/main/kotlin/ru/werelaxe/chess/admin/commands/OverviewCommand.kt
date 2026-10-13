package ru.werelaxe.chess.admin.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import ru.werelaxe.chess.admin.output.Render

class OverviewCommand : CliktCommand(name = "overview") {
    private val session by requireObject<Session>()

    override fun help(context: Context) = "Counts of users, games and bots, and the current settings."

    override fun run() {
        echo(session.show(session.exchange { overview() }, Render::overview))
    }
}
