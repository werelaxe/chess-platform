package ru.werelaxe.chess.server

import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.withTimeout
import ru.werelaxe.chess.core.ChessJson
import ru.werelaxe.chess.core.EndReason
import ru.werelaxe.chess.engine.EngineLevel
import ru.werelaxe.chess.server.dto.ColorChoice
import ru.werelaxe.chess.server.dto.GameDto
import ru.werelaxe.chess.server.dto.GamesResponse
import ru.werelaxe.chess.server.dto.Opponent
import ru.werelaxe.chess.server.model.GamePhase
import ru.werelaxe.chess.server.model.GameResult
import ru.werelaxe.chess.server.model.Visibility
import ru.werelaxe.chess.server.service.ApiException
import ru.werelaxe.chess.server.service.UserPrincipal
import ru.werelaxe.chess.server.ws.ServerEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InvitationTest {
    private suspend fun ReceiveChannel<Frame>.nextEvent(): ServerEvent = withTimeout(5_000) {
        val frame = assertIs<Frame.Text>(receive())
        ChessJson.json.decodeFromString(ServerEvent.serializer(), frame.readText())
    }

    @Test
    fun humanAcceptsAnInvitation() = serverTest {
        val alice = token("alice")
        val bob = token("bob")
        val carol = token("carol")
        val created = invite(alice, "Bob", color = ColorChoice.BLACK)
        assertEquals(GamePhase.WAITING, created.status)
        assertEquals("bob", created.invitee?.username)
        assertNull(created.white)
        assertTrue(created.rated)

        joinResponse(carol, created.id).assertError(HttpStatusCode.Forbidden, "not_invited")
        joinResponse(alice, created.id).assertError(HttpStatusCode.BadRequest, "own_game")
        assertEquals(listOf(created.id), invitations(bob).incoming.map { it.id })
        assertTrue(invitations(bob).outgoing.isEmpty())
        assertEquals(listOf(created.id), invitations(alice).outgoing.map { it.id })
        assertTrue(invitations(alice).incoming.isEmpty())
        assertTrue(invitations(carol).incoming.isEmpty())

        val joined = join(bob, created.id)
        assertEquals(GamePhase.ACTIVE, joined.status)
        assertEquals("bob", joined.white?.username)
        assertEquals("bob", joined.invitee?.username, "the invitee is kept for the record")
        assertTrue(invitations(bob).incoming.isEmpty())
        assertTrue(invitations(alice).outgoing.isEmpty())
        joinResponse(carol, created.id).assertError(HttpStatusCode.Conflict, "game_full")
    }

    @Test
    fun inviteeDeclinesAndTheGameIsGone() = serverTest {
        val alice = token("alice")
        val bob = token("bob")
        val carol = token("carol")
        val created = invite(alice, "bob")

        decline(carol, created.id).assertError(HttpStatusCode.Forbidden, "not_invited")
        decline(alice, created.id).assertError(HttpStatusCode.Forbidden, "not_invited")
        decline(bob, "missing12345").assertError(HttpStatusCode.NotFound, "not_found")

        client.webSocket("/api/games/${created.id}/ws") {
            assertEquals(GamePhase.WAITING, assertIs<ServerEvent.GameUpdated>(incoming.nextEvent()).game.status)
            assertEquals(HttpStatusCode.NoContent, decline(bob, created.id).status)
            val gone = assertIs<ServerEvent.GameUpdated>(incoming.nextEvent())
            assertEquals(GamePhase.FINISHED, gone.game.status)
            assertEquals(GameResult(null, EndReason.ABANDONMENT), gone.game.result)
            assertTrue(gone.game.declined, "the creator's page can tell a decline from a cancellation")
        }
        getGame(created.id).assertError(HttpStatusCode.NotFound, "not_found")
        assertTrue(invitations(bob).incoming.isEmpty())
        assertTrue(invitations(alice).outgoing.isEmpty())
        assertTrue(myGames(alice).isEmpty())

        val active = activeGame(alice, bob)
        decline(bob, active.id).assertError(HttpStatusCode.Conflict, "game_full")
    }

    @Test
    fun creatorCancelsAnInvitation() = serverTest {
        val alice = token("alice")
        val bob = token("bob")
        val created = invite(alice, "bob")
        resign(bob, created.id).assertError(HttpStatusCode.Forbidden, "not_a_player")
        val response = resign(alice, created.id)
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsString())
        val json = response.bodyAsString()
        assertFalse("declined" in json, "a cancellation is not a decline: $json")
        val cancelled = ChessJson.json.decodeFromString(GameDto.serializer(), json)
        assertEquals(GameResult(null, EndReason.ABANDONMENT), cancelled.result)
        assertFalse(cancelled.declined)
        assertTrue(invitations(bob).incoming.isEmpty())
        getGame(created.id).assertError(HttpStatusCode.NotFound, "not_found")
    }

    @Test
    fun cancelInvitationNeverTouchesAGameThatStarted() = serverTest {
        val alice = token("alice")
        val bob = token("bob")
        val creator = UserPrincipal(me(alice).id, "alice", guest = false)
        val invitee = UserPrincipal(me(bob).id, "bob", guest = false)
        val service = services.games

        val open = createGame(alice)
        assertEquals("game_full", assertFailsWith<ApiException> { service.cancelInvitation(creator, open.id) }.code, "an open game is not an invitation")
        val invitation = invite(alice, "bob")
        assertEquals("not_a_player", assertFailsWith<ApiException> { service.cancelInvitation(invitee, invitation.id) }.code)

        // The invitee accepts between a stale listing and the cancellation: the game stays as it is.
        join(bob, invitation.id)
        assertEquals("game_full", assertFailsWith<ApiException> { service.cancelInvitation(creator, invitation.id) }.code)
        val untouched = game(invitation.id)
        assertEquals(GamePhase.ACTIVE, untouched.status)
        assertNull(untouched.result)

        val second = invite(alice, "bob")
        val cancelled = service.cancelInvitation(creator, second.id)
        assertEquals(GameResult(null, EndReason.ABANDONMENT), cancelled.result)
        assertFalse(cancelled.declined)
        getGame(second.id).assertError(HttpStatusCode.NotFound, "not_found")
        assertEquals("not_found", assertFailsWith<ApiException> { service.cancelInvitation(creator, second.id) }.code)
    }

    @Test
    fun invitationsAreValidated() = serverTest {
        val alice = token("alice")
        token("bob")
        val guest = guest()
        createGameResponse(alice, opponent = Opponent.USER).assertError(HttpStatusCode.BadRequest, "validation")
        createGameResponse(alice, opponent = Opponent.USER, opponentUsername = " ").assertError(HttpStatusCode.BadRequest, "validation")
        createGameResponse(alice, opponent = Opponent.USER, opponentUsername = "nobody").assertError(HttpStatusCode.NotFound, "not_found")
        createGameResponse(alice, opponent = Opponent.USER, opponentUsername = "alice").assertError(HttpStatusCode.BadRequest, "validation")
        createGameResponse(alice, opponent = Opponent.USER, opponentUsername = guest.user.username)
            .assertError(HttpStatusCode.NotFound, "not_found")
        assertTrue(myGames(alice).isEmpty())
        // The username is ignored for an open game.
        assertNull(createGame(alice, opponent = Opponent.OPEN, opponentUsername = "bob").invitee)
    }

    @Test
    fun pendingInvitationsAreNotPublic() = serverTest {
        val alice = token("alice")
        val bob = token("bob")
        val invitation = invite(alice, "bob", visibility = Visibility.PUBLIC)
        val open = createGame(alice)

        suspend fun ids(query: String): List<String> =
            client.get("/api/games$query").body<GamesResponse>().games.map { it.id }
        assertEquals(listOf(open.id), ids(""))
        assertEquals(listOf(open.id), ids("?filter=open"))

        join(bob, invitation.id)
        assertEquals(listOf(invitation.id), ids("?filter=active"))
        assertEquals(setOf(open.id, invitation.id), ids("").toSet())
    }

    @Test
    fun botInviteeJoinsAtOnce() = serverTest {
        val alice = token("alice")
        val created = invite(alice, botNamed(EngineLevel.NOVICE), color = ColorChoice.BLACK)
        assertEquals(GamePhase.ACTIVE, created.status)
        assertEquals(EngineLevel.NOVICE, created.white?.botLevel)
        assertEquals("alice", created.black?.username)
        assertTrue(invitations(alice).outgoing.isEmpty())
        assertEquals(1, awaitMoves(alice, created.id, 1).moveCount, "the bot opens the game")

        val record = assertNotNull(repositories.games.findById(created.id))
        assertEquals(1500, record.whiteRatingBefore)
        assertEquals(1500, record.blackRatingBefore)
    }

    @Test
    fun guestInvitationsAreUnrated() = serverTest {
        val guest = guest()
        val bob = token("bob")
        val toBot = invite(guest.token, botNamed(EngineLevel.EASY))
        assertEquals(GamePhase.ACTIVE, toBot.status)
        assertFalse(toBot.rated)

        val toHuman = invite(guest.token, "bob")
        assertFalse(toHuman.rated)
        assertEquals(listOf(toHuman.id), invitations(bob).incoming.map { it.id })
        assertTrue(invitations(bob).incoming.single().creator.guest)
        assertFalse(join(bob, toHuman.id).rated)
    }
}
