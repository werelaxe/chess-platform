package ru.werelaxe.chess.server

import io.ktor.http.HttpStatusCode
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.engine.EngineLevel
import ru.werelaxe.chess.engine.ThinkPriority
import ru.werelaxe.chess.server.dto.ColorChoice
import ru.werelaxe.chess.server.dto.CreateGameRequest
import ru.werelaxe.chess.server.service.HumanLoad
import ru.werelaxe.chess.server.service.MoveProvider
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HumanLoadTest {
    /** Bots that never move, so that the games stay as the test left them. */
    private val idleBots = object : MoveProvider {
        override suspend fun chooseMove(
            gameId: String,
            kind: GameKind,
            level: EngineLevel,
            moves: List<GameMove>,
            priority: ThinkPriority,
        ): GameMove? = null
    }

    @Test
    fun countsTheGamesWithAHumanSeat() = serverTest(moveProvider = idleBots) {
        val alice = token("alice")
        val bob = token("bob")
        val load = services.humanLoad
        assertEquals(0, load.refresh())

        botGame(alice, color = ColorChoice.WHITE)
        activeGame(alice, bob)
        invite(guest().token, botNamed(EngineLevel.HARD), color = ColorChoice.WHITE)
        createGame(bob)
        val (white, black) = services.bots.roster.takeLast(2)
        val live = services.games.create(services.bots.principal(white), CreateGameRequest(GameKind.CLASSIC, color = ColorChoice.WHITE))
        services.games.join(services.bots.principal(black), live.id)
        assertEquals(3, load.refresh(), "a bot game, a game between people and a guest's game; not a lobby, not a game between bots")

        val finished = activeGame(alice, bob)
        assertEquals(4, load.refresh())
        assertEquals(HttpStatusCode.OK, resign(alice, finished.id).status)
        assertEquals(3, load.refresh(), "a finished game is over")
    }

    @Test
    fun reusesTheCountForTenSecondsAndJudgesTheLimitAgainstIt() = serverTest(
        config = TEST_CONFIG.copy(botHumanGamesLimit = 2),
        moveProvider = idleBots,
    ) {
        val alice = token("alice")
        val load = services.humanLoad
        assertEquals(0, load.humanGames())
        assertFalse(load.high())

        botGame(alice, color = ColorChoice.WHITE)
        botGame(alice, color = ColorChoice.WHITE)
        assertEquals(0, load.humanGames(), "counted again only once the cache expires")
        assertFalse(load.high())
        clock.advance(HumanLoad.CACHE_TTL.minus(Duration.ofSeconds(1)))
        assertEquals(0, load.humanGames())
        clock.advance(Duration.ofSeconds(1))
        assertEquals(2, load.humanGames())
        assertTrue(load.high(), "two games with a human reach the limit of two")

        botGame(alice, color = ColorChoice.WHITE)
        assertEquals(2, load.humanGames())
        assertEquals(3, load.refresh(), "a refresh counts at once")
        assertEquals(3, load.humanGames())

        services.settings.set("bot.human_games_limit", "4")
        assertFalse(load.high(), "the limit is read from the settings every time")
        services.settings.set("bot.human_games_limit", "0")
        assertFalse(load.high(), "zero switches the gate off")
    }
}
