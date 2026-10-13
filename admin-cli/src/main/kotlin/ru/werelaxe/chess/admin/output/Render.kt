package ru.werelaxe.chess.admin.output

import kotlinx.serialization.json.JsonPrimitive
import ru.werelaxe.chess.admin.client.AdminBot
import ru.werelaxe.chess.admin.client.AdminBotDetails
import ru.werelaxe.chess.admin.client.GameSummary
import ru.werelaxe.chess.admin.client.Overview
import ru.werelaxe.chess.admin.client.RatingSummary
import ru.werelaxe.chess.admin.client.Setting
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Turns the admin API's responses into the text the tool prints without `--json`. */
object Render {
    private const val NONE = "-"
    private val timestamps = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC)

    fun overview(overview: Overview): String {
        val counts = Table.details(
            listOf(
                "Users" to overview.users.toString(),
                "Guests" to overview.guests.toString(),
                "Bots" to overview.bots.toString(),
                "Waiting games" to overview.games.waiting.toString(),
                "Active games" to overview.games.active.toString(),
                "Finished games" to overview.games.finished.toString(),
                "Active bot games" to overview.activeBotGames.toString(),
            ),
        )
        if (overview.settings.isEmpty()) return counts
        val settings = overview.settings.entries.sortedBy { it.key }.map { (key, value) ->
            listOf(key, (value as? JsonPrimitive)?.content ?: value.toString())
        }
        return counts + "\n\n" + Table.render(listOf("SETTING", "VALUE"), settings)
    }

    fun bots(bots: List<AdminBot>): String = Table.render(
        listOf("USERNAME", "NAME", "LEVEL", "CLASSIC", "QUANTUM", "ACTIVE", "GAMES", "LEGACY"),
        bots.map { bot ->
            listOf(
                bot.user.username,
                bot.user.name ?: NONE,
                bot.level,
                rating(bot.ratings.classic),
                rating(bot.ratings.quantum),
                bot.activeGames.toString(),
                bot.totalGames.toString(),
                if (bot.legacy) "yes" else "",
            )
        },
    )

    fun bot(bot: AdminBotDetails): String {
        val details = Table.details(
            listOf(
                "Username" to bot.user.username,
                "Name" to (bot.user.name ?: NONE),
                "Level" to bot.level,
                "Legacy" to if (bot.legacy) "yes" else "no",
                "Active games" to bot.activeGames.toString(),
                "Total games" to bot.totalGames.toString(),
                "Classic" to ratingDetails(bot.ratings.classic),
                "Quantum" to ratingDetails(bot.ratings.quantum),
            ),
        )
        return listOf(
            details,
            "Active games (${bot.activeGameList.size})\n" + games(bot.activeGameList),
            "Recent games (${bot.recentGames.size})\n" + games(bot.recentGames),
        ).joinToString("\n\n")
    }

    fun games(games: List<GameSummary>): String = Table.render(
        listOf("ID", "KIND", "STATUS", "WHITE", "BLACK", "MOVES", "RATED", "RESULT", "UPDATED"),
        games.map { game ->
            listOf(
                game.id,
                game.kind,
                game.status,
                game.white?.username ?: NONE,
                game.black?.username ?: NONE,
                game.moveCount.toString(),
                if (game.rated) "yes" else "no",
                result(game),
                timestamp(game.updatedAt),
            )
        },
    )

    /** One line with a game's id, status and result: `aZ3kq9bX7LmN: FINISHED 1/2-1/2 ABANDONMENT`. */
    fun status(game: GameSummary): String = "${game.id}: ${game.status} ${result(game)}"

    fun settings(settings: List<Setting>): String = Table.render(
        listOf("KEY", "VALUE", "DEFAULT", "SOURCE", "DESCRIPTION"),
        settings.map { listOf(it.key, it.value, it.defaultValue, it.source, it.description) },
    )

    /** `yyyy-MM-dd HH:mm` in UTC; anything that is not an ISO instant is shown as it is. */
    fun timestamp(iso: String): String =
        runCatching { timestamps.format(Instant.parse(iso)) }.getOrDefault(iso)

    private fun result(game: GameSummary) = game.result?.let { "${score(it.winner)} ${it.reason}" } ?: NONE

    private fun rating(summary: RatingSummary?) = summary?.rating?.toString() ?: NONE

    private fun ratingDetails(summary: RatingSummary?) = summary?.let {
        "${it.rating} (${it.games} rated, ${it.wins} wins, ${it.draws} draws, ${it.losses} losses, peak ${it.peak})"
    } ?: "unrated"

    private fun score(winner: String?) = when (winner) {
        "WHITE" -> "1-0"
        "BLACK" -> "0-1"
        else -> "1/2-1/2"
    }
}
