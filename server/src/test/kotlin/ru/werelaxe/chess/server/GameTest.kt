package ru.werelaxe.chess.server

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import ru.werelaxe.chess.core.Color
import ru.werelaxe.chess.core.EndReason
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.core.GameMove
import ru.werelaxe.chess.core.GameStatus
import ru.werelaxe.chess.core.Piece
import ru.werelaxe.chess.core.PieceType
import ru.werelaxe.chess.core.Square
import ru.werelaxe.chess.server.dto.ColorChoice
import ru.werelaxe.chess.server.dto.DrawAction
import ru.werelaxe.chess.server.dto.GameDto
import ru.werelaxe.chess.server.dto.GamesResponse
import ru.werelaxe.chess.server.model.GamePhase
import ru.werelaxe.chess.server.model.GameResult
import ru.werelaxe.chess.server.model.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameTest {
    @Test
    fun createAndJoin() = serverTest {
        val alice = token("alice")
        val bob = token("bob")

        val created = createGame(alice, kind = GameKind.QUANTUM, color = ColorChoice.BLACK)
        assertEquals(GamePhase.WAITING, created.status)
        assertEquals(GameKind.QUANTUM, created.kind)
        assertEquals("alice", created.black?.username)
        assertNull(created.white)
        assertEquals("alice", created.creator.username)
        assertEquals(12, created.id.length)
        assertTrue(created.id.all { it.isLetterOrDigit() })
        assertEquals("2026-10-07T10:00:00Z", created.createdAt)

        joinResponse(alice, created.id).assertError(HttpStatusCode.BadRequest, "own_game")

        val joined = join(bob, created.id)
        assertEquals(GamePhase.ACTIVE, joined.status)
        assertEquals("bob", joined.white?.username)
        assertEquals("alice", joined.black?.username)

        val carol = token("carol")
        joinResponse(carol, created.id).assertError(HttpStatusCode.Conflict, "game_full")
        joinResponse(carol, "missing12345").assertError(HttpStatusCode.NotFound, "not_found")
        createGameResponse("bad-token").assertError(HttpStatusCode.Unauthorized, "unauthorized")
    }

    @Test
    fun randomColorAssignsBothColors() = serverTest {
        val alice = token("alice")
        val colors = (1..12).map { createGame(alice, color = ColorChoice.RANDOM) }.map { game ->
            assertTrue((game.white == null) != (game.black == null), "exactly one seat is taken")
            if (game.white != null) Color.WHITE else Color.BLACK
        }.toSet()
        assertEquals(setOf(Color.WHITE, Color.BLACK), colors)
    }

    @Test
    fun publicListingFilters() = serverTest {
        val alice = token("alice")
        val bob = token("bob")
        val open = createGame(alice, kind = GameKind.CLASSIC)
        val openQuantum = createGame(alice, kind = GameKind.QUANTUM)
        val private = createGame(alice, visibility = Visibility.PRIVATE)
        val active = activeGame(alice, bob)
        val finished = activeGame(alice, bob)
        assertEquals(HttpStatusCode.OK, resign(alice, finished.id).status)

        suspend fun ids(query: String): List<String> =
            client.get("/api/games$query").body<GamesResponse>().games.map { it.id }

        assertEquals(setOf(open.id, openQuantum.id), ids("?filter=open").toSet())
        assertEquals(listOf(openQuantum.id), ids("?filter=open&kind=QUANTUM"))
        assertEquals(listOf(active.id), ids("?filter=active"))
        assertEquals(listOf(finished.id), ids("?filter=finished"))
        assertEquals(setOf(open.id, openQuantum.id, active.id, finished.id), ids("").toSet())
        assertTrue(private.id !in ids(""))
        assertEquals(1, ids("?limit=1").size)
        assertEquals(1, ids("?limit=1&offset=1").size)
        assertEquals(ids("")[1], ids("?limit=1&offset=1").single())
        client.get("/api/games?filter=bogus").assertError(HttpStatusCode.BadRequest, "validation")

        val mine = client.get("/api/games/mine") { bearerAuth(bob) }.body<GamesResponse>().games.map { it.id }
        assertEquals(setOf(active.id, finished.id), mine.toSet())
        val aliceMine = client.get("/api/games/mine") { bearerAuth(alice) }.body<GamesResponse>().games
        assertEquals(5, aliceMine.size)
        assertTrue(private.id in aliceMine.map { it.id })
    }

    @Test
    fun classicGameToCheckmate() = serverTest {
        val alice = token("alice")
        val bob = token("bob")
        val carol = token("carol")
        val game = activeGame(alice, bob)

        moveResponse(bob, game.id, normal("e7e5")).assertError(HttpStatusCode.Forbidden, "not_your_turn")
        moveResponse(carol, game.id, normal("e2e4")).assertError(HttpStatusCode.Forbidden, "not_a_player")
        moveResponse(alice, game.id, normal("e2e5")).assertError(HttpStatusCode.BadRequest, "illegal_move")
        moveResponse(alice, game.id, split("e2", "e3", "e4")).assertError(HttpStatusCode.BadRequest, "illegal_move")

        val first = move(alice, game.id, "f2f3")
        assertEquals(0, first.ply)
        assertEquals(normal("f2f3"), first.move)
        assertEquals(GameStatus.Ongoing, first.status)
        move(bob, game.id, "e7e5")
        move(alice, game.id, "g2g4")
        val mate = move(bob, game.id, "d8h4")
        assertEquals(3, mate.ply)
        assertEquals(GameStatus.Finished(Color.BLACK, EndReason.CHECKMATE), mate.status)

        val finished = game(game.id)
        assertEquals(GamePhase.FINISHED, finished.status)
        assertEquals(GameResult(Color.BLACK, EndReason.CHECKMATE), finished.result)
        assertEquals(4, finished.moveCount)
        assertEquals(listOf(normal("f2f3"), normal("e7e5"), normal("g2g4"), normal("d8h4")), finished.moves)

        moveResponse(alice, game.id, normal("a2a3")).assertError(HttpStatusCode.Conflict, "game_finished")
        resign(alice, game.id).assertError(HttpStatusCode.Conflict, "game_finished")
    }

    @Test
    fun promotionIsAccepted() = serverTest {
        val alice = token("alice")
        val bob = token("bob")
        val game = activeGame(alice, bob)
        // 1. a4 b5 2. axb5 a6 3. bxa6 Nc6 4. a7 Nb8 5. axb8=Q
        val prelude = listOf("a2a4", "b7b5", "a4b5", "a7a6", "b5a6", "b8c6", "a6a7", "c6b8")
        prelude.forEachIndexed { index, uci -> move(if (index % 2 == 0) alice else bob, game.id, uci) }
        moveResponse(alice, game.id, normal("a7b8")).assertError(HttpStatusCode.BadRequest, "illegal_move")
        val promoted = move(alice, game.id, normal("a7b8", PieceType.QUEEN))
        assertEquals(GameMove.Normal(Square.parse("a7"), Square.parse("b8"), PieceType.QUEEN), promoted.move)
    }

    @Test
    fun quantumSplitAndObserve() = serverTest {
        val alice = token("alice")
        val bob = token("bob")
        val game = activeGame(alice, bob, kind = GameKind.QUANTUM)

        val splitMove = move(alice, game.id, split("e2", "e3", "e4"))
        assertEquals(split("e2", "e3", "e4"), splitMove.move)

        val observed = move(bob, game.id, observe("e4"))
        val resolved = assertIs<GameMove.Observe>(observed.move)
        val outcome = assertNotNull(resolved.outcome, "the server fills in the observation outcome")
        val piece = outcome.piece
        assertTrue(piece == null || piece == Piece(Color.WHITE, PieceType.PAWN))
        assertEquals(1, observed.ply)

        // Observing a certain square is illegal.
        moveResponse(alice, game.id, observe("a1")).assertError(HttpStatusCode.BadRequest, "illegal_move")

        val stored = game(game.id)
        assertEquals(2, stored.moveCount)
        assertEquals(resolved, stored.moves[1])
    }

    @Test
    fun resignActiveGameAndDeleteWaitingGame() = serverTest {
        val alice = token("alice")
        val bob = token("bob")
        val active = activeGame(alice, bob)
        move(alice, active.id, "e2e4")

        resign(token("carol"), active.id).assertError(HttpStatusCode.Forbidden, "not_a_player")
        val resigned = resign(bob, active.id)
        assertEquals(HttpStatusCode.OK, resigned.status)
        val dto = resigned.body<GameDto>()
        assertEquals(GamePhase.FINISHED, dto.status)
        assertEquals(GameResult(Color.WHITE, EndReason.RESIGNATION), dto.result)
        assertEquals(1, dto.moves.size)

        val waiting = createGame(alice)
        resign(bob, waiting.id).assertError(HttpStatusCode.Forbidden, "not_a_player")
        assertEquals(HttpStatusCode.OK, resign(alice, waiting.id).status)
        getGame(waiting.id).assertError(HttpStatusCode.NotFound, "not_found")
    }

    @Test
    fun drawOfferAndAccept() = serverTest {
        val alice = token("alice")
        val bob = token("bob")
        val game = activeGame(alice, bob)

        draw(alice, game.id, DrawAction.ACCEPT).assertError(HttpStatusCode.Conflict, "no_draw_offer")
        val offered = draw(alice, game.id, DrawAction.OFFER)
        assertEquals(HttpStatusCode.OK, offered.status)
        assertEquals(Color.WHITE, offered.body<GameDto>().drawOfferedBy)
        draw(bob, game.id, DrawAction.OFFER).assertError(HttpStatusCode.Conflict, "draw_pending")
        draw(alice, game.id, DrawAction.ACCEPT).assertError(HttpStatusCode.Conflict, "no_draw_offer")

        val declined = draw(bob, game.id, DrawAction.DECLINE).body<GameDto>()
        assertNull(declined.drawOfferedBy)

        draw(bob, game.id, DrawAction.OFFER)
        // A move by the opponent clears the pending offer.
        move(alice, game.id, "e2e4")
        assertNull(game(game.id).drawOfferedBy)

        draw(bob, game.id, DrawAction.OFFER)
        draw(bob, game.id, DrawAction.WITHDRAW)
        assertNull(game(game.id).drawOfferedBy)

        draw(alice, game.id, DrawAction.OFFER)
        val accepted = draw(bob, game.id, DrawAction.ACCEPT).body<GameDto>()
        assertEquals(GamePhase.FINISHED, accepted.status)
        assertEquals(GameResult(null, EndReason.DRAW_AGREEMENT), accepted.result)
        assertNull(accepted.drawOfferedBy)
        draw(alice, game.id, DrawAction.OFFER).assertError(HttpStatusCode.Conflict, "game_finished")
    }

    @Test
    fun movesRequireAnOpponent() = serverTest {
        val alice = token("alice")
        val waiting = createGame(alice)
        moveResponse(alice, waiting.id, normal("e2e4")).assertError(HttpStatusCode.Conflict, "game_not_started")
    }
}
