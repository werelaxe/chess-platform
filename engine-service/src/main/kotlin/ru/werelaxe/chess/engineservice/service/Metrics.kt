package ru.werelaxe.chess.engineservice.service

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry

/**
 * The gauges of the searcher on `GET /metrics` (docs/ARCHITECTURE.md section 4.1): what
 * `/health` reports, read live at every scrape, as none of it costs more than a lock.
 */
fun Searcher.registerMetrics(registry: MeterRegistry) {
    gauge(registry, "chess_engine_search_threads", "Search threads (the parallelism)") { parallelism }
    gauge(registry, "chess_engine_searches_in_flight", "Searches running right now") { inFlight }
    gauge(registry, "chess_engine_requests_queued", "Requests waiting for a search thread, of either priority") { queued }
    gauge(registry, "chess_engine_requests_queued_bots", "Bot-versus-bot requests waiting for a search thread") { queuedBots }
    gauge(registry, "chess_engine_helpers", "Helper threads") { helpers.size }
    gauge(registry, "chess_engine_helpers_busy", "Helper threads lent to running searches") { helpers.busy }
}

private fun Searcher.gauge(registry: MeterRegistry, name: String, description: String, read: Searcher.() -> Int) {
    Gauge.builder(name, this) { it.read().toDouble() }.description(description).register(registry)
}
