package ru.werelaxe.chess.server

import ru.werelaxe.chess.server.ws.GameHub
import ru.werelaxe.chess.server.ws.ServerEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameHubTest {
    @Test
    fun capsSubscribersPerGame() {
        val hub = GameHub(maxSubscribersPerGame = 2)
        val first = assertNotNull(hub.subscribe("game"))
        assertNotNull(hub.subscribe("game"))
        assertNull(hub.subscribe("game"))
        assertEquals(2, hub.subscriberCount("game"))
        // The cap is per game.
        assertNotNull(hub.subscribe("other"))

        hub.unsubscribe(first)
        assertNotNull(hub.subscribe("game"))
    }

    @Test
    fun publishReachesCurrentSubscribersOnly() {
        val hub = GameHub()
        val subscription = assertNotNull(hub.subscribe("game"))
        hub.publish("game", ServerEvent.Pong)
        hub.publish("other", ServerEvent.Pong)
        assertEquals(hub.encode(ServerEvent.Pong), subscription.outbound.tryReceive().getOrNull())
        assertNull(subscription.outbound.tryReceive().getOrNull())

        hub.unsubscribe(subscription)
        assertEquals(0, hub.subscriberCount("game"))
        assertTrue(subscription.outbound.tryReceive().isClosed)

        // A subscriber arriving after the last one left is registered in a live set.
        val late = assertNotNull(hub.subscribe("game"))
        hub.publish("game", ServerEvent.Pong)
        assertNotNull(late.outbound.tryReceive().getOrNull())
    }
}
