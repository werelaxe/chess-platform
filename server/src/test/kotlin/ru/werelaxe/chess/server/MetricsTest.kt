package ru.werelaxe.chess.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import ru.werelaxe.chess.core.GameKind
import ru.werelaxe.chess.server.service.Presence
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class MetricsTest {
    @Test
    fun exposesThePrometheusTextFormat() = serverTest {
        val response = client.get("/metrics")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.contentType()?.match(ContentType.Text.Plain) == true, "${response.contentType()}")
        val body = response.bodyAsText()
        assertContains(body, "chess_games_active")
        assertContains(body, "chess_players_online")
        // The JVM binders and the request timer (the health check of serverTest was timed).
        assertContains(body, "jvm_memory_used_bytes")
        assertContains(body, "ktor_http_server_requests_seconds_bucket")
        assertContains(body, "route=\"/api/health\"")
    }

    @Test
    fun countsGamesAndPlayers() = serverTest {
        val alice = token("alice")
        val bob = token("bob")
        activeGame(alice, bob)
        createGame(alice, kind = GameKind.QUANTUM)
        botGame(bob, kind = GameKind.QUANTUM)
        services.metrics.refresh()

        val body = client.get("/metrics").bodyAsText()
        assertEquals(1.0, body.gauge("chess_games_active{kind=\"CLASSIC\"}"))
        assertEquals(1.0, body.gauge("chess_games_active{kind=\"QUANTUM\"}"))
        assertEquals(1.0, body.gauge("chess_games_waiting"))
        assertEquals(0.0, body.gauge("chess_games_active_bot_versus_bot"))
        assertEquals(2.0, body.gauge("chess_games_active_with_humans"))
        assertEquals(2.0, body.gauge("chess_human_games_recent"))
        assertEquals(services.settings.humanGamesLimit.toDouble(), body.gauge("chess_human_games_limit"))
        assertEquals(2.0, body.gauge("chess_players_online{kind=\"human\"}"))
        assertEquals(1.0, body.gauge("chess_players_online{kind=\"bot\"}"))
        assertEquals(0.0, body.gauge("chess_ws_connections"))

        // Nobody made a request for longer than the window: no person is online any more.
        clock.advance(Presence.ONLINE_WINDOW)
        services.metrics.refresh()
        assertEquals(0.0, client.get("/metrics").bodyAsText().gauge("chess_players_online{kind=\"human\"}"))
    }

    /** The value of the sample [series] (name with its labels) in the exposition text. */
    private fun String.gauge(series: String): Double =
        lines().firstOrNull { it.startsWith("$series ") }?.substringAfterLast(' ')?.toDouble()
            ?: fail("No sample $series in:\n$this")
}
