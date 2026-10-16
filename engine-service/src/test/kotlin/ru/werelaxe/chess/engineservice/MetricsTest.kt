package ru.werelaxe.chess.engineservice

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MetricsTest {
    @Test
    fun exposesThePrometheusTextFormat() = serviceTest {
        // A completed request, so that the request timer has a sample.
        health()
        val response = client.get("/metrics")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.contentType()?.match(ContentType.Text.Plain) == true, "${response.contentType()}")
        val body = response.bodyAsText()
        // The searcher of serviceTest has two threads and nothing to do.
        assertContains(body, "chess_engine_search_threads 2.0")
        assertContains(body, "chess_engine_searches_in_flight 0.0")
        assertContains(body, "chess_engine_requests_queued 0.0")
        assertContains(body, "chess_engine_requests_queued_bots 0.0")
        assertContains(body, "chess_engine_helpers_busy 0.0")
        // The JVM binders and the request timer.
        assertContains(body, "jvm_memory_used_bytes")
        assertContains(body, "ktor_http_server_requests_seconds_bucket")
        assertContains(body, "route=\"/health\"")
    }
}
