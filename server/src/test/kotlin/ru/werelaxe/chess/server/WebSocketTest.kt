package ru.werelaxe.chess.server

import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withTimeout
import ru.werelaxe.chess.core.ChessJson
import ru.werelaxe.chess.core.Color
import ru.werelaxe.chess.core.EndReason
import ru.werelaxe.chess.core.GameStatus
import ru.werelaxe.chess.server.dto.DrawAction
import ru.werelaxe.chess.server.model.GamePhase
import ru.werelaxe.chess.server.model.GameResult
import ru.werelaxe.chess.server.ws.ServerEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class WebSocketTest {
    private suspend fun ReceiveChannel<Frame>.nextEvent(): ServerEvent = withTimeout(5_000) {
        val frame = assertIs<Frame.Text>(receive())
        ChessJson.json.decodeFromString(ServerEvent.serializer(), frame.readText())
    }

    @Test
    fun gameEventOnConnectAndMoveEventAfterMove() = serverTest {
        val alice = token("alice")
        val bob = token("bob")
        val game = activeGame(alice, bob)

        client.webSocket("/api/games/${game.id}/ws") {
            val connected = assertIs<ServerEvent.GameUpdated>(incoming.nextEvent())
            assertEquals(game.id, connected.game.id)
            assertEquals(GamePhase.ACTIVE, connected.game.status)

            send(Frame.Text("""{"type":"ping"}"""))
            assertIs<ServerEvent.Pong>(incoming.nextEvent())

            move(alice, game.id, "e2e4")
            val event = assertIs<ServerEvent.Move>(incoming.nextEvent())
            assertEquals(0, event.ply)
            assertEquals(normal("e2e4"), event.move)
            assertEquals(GameStatus.Ongoing, event.status)
        }
    }

    @Test
    fun spectatorsReceiveLifecycleEvents() = serverTest {
        val alice = token("alice")
        val bob = token("bob")
        val waiting = createGame(alice)

        // No token: a spectator connection.
        client.webSocket("/api/games/${waiting.id}/ws") {
            assertEquals(GamePhase.WAITING, assertIs<ServerEvent.GameUpdated>(incoming.nextEvent()).game.status)

            join(bob, waiting.id)
            val joined = assertIs<ServerEvent.GameUpdated>(incoming.nextEvent())
            assertEquals(GamePhase.ACTIVE, joined.game.status)
            assertEquals("bob", joined.game.black?.username)

            resign(alice, waiting.id)
            val finished = assertIs<ServerEvent.GameUpdated>(incoming.nextEvent())
            assertEquals(GamePhase.FINISHED, finished.game.status)
        }
    }

    @Test
    fun cancellingAWaitingGameIsPublishedAsAbandonment() = serverTest {
        val alice = token("alice")
        val waiting = createGame(alice)

        client.webSocket("/api/games/${waiting.id}/ws") {
            assertEquals(GamePhase.WAITING, assertIs<ServerEvent.GameUpdated>(incoming.nextEvent()).game.status)

            resign(alice, waiting.id)
            val gone = assertIs<ServerEvent.GameUpdated>(incoming.nextEvent())
            assertEquals(GamePhase.FINISHED, gone.game.status)
            assertEquals(GameResult(null, EndReason.ABANDONMENT), gone.game.result)
        }
    }

    @Test
    fun drawOfferEventsFollowTheMoves() = serverTest {
        val alice = token("alice")
        val bob = token("bob")
        val game = activeGame(alice, bob)

        client.webSocket("/api/games/${game.id}/ws") {
            assertIs<ServerEvent.GameUpdated>(incoming.nextEvent())

            draw(alice, game.id, DrawAction.OFFER)
            assertEquals(Color.WHITE, assertIs<ServerEvent.GameUpdated>(incoming.nextEvent()).game.drawOfferedBy)
            // The offerer's own move keeps the offer, so only a `move` event is published...
            move(alice, game.id, "e2e4")
            assertIs<ServerEvent.Move>(incoming.nextEvent())
            draw(bob, game.id, DrawAction.DECLINE)
            // ...and the next `game` event is the decline.
            assertNull(assertIs<ServerEvent.GameUpdated>(incoming.nextEvent()).game.drawOfferedBy)

            draw(alice, game.id, DrawAction.OFFER)
            assertEquals(Color.WHITE, assertIs<ServerEvent.GameUpdated>(incoming.nextEvent()).game.drawOfferedBy)
            // The opponent's move clears the offer and says so with a `game` event.
            move(bob, game.id, "e7e5")
            assertIs<ServerEvent.Move>(incoming.nextEvent())
            assertNull(assertIs<ServerEvent.GameUpdated>(incoming.nextEvent()).game.drawOfferedBy)
        }
    }

    @Test
    fun unknownGameClosesTheSocket() = serverTest {
        client.webSocket("/api/games/nope/ws") {
            assertEquals(CloseReason.Codes.VIOLATED_POLICY.code, closeReason.await()?.code)
        }
        client.webSocket("/api/games/missing12345/ws") {
            assertEquals(CloseReason.Codes.VIOLATED_POLICY.code, closeReason.await()?.code)
        }
    }
}
