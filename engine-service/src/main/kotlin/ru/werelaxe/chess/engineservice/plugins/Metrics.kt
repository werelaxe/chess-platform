package ru.werelaxe.chess.engineservice.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.time.Duration

/** The histogram buckets of the request timer, from 5 ms to 30 s (a search may take seconds). */
private val LATENCY_BUCKETS: DoubleArray =
    longArrayOf(5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000, 30000)
        .map { Duration.ofMillis(it).toNanos().toDouble() }
        .toDoubleArray()

/**
 * Times every request as `ktor_http_server_requests_seconds`, tagged with the route, the
 * method and the status, with histogram buckets ([LATENCY_BUCKETS]) rather than client-side
 * percentiles, so that Prometheus can compute a p95 over any set of routes; and registers the
 * JVM binders the plugin offers by default (memory, GC, threads, class loading, CPU, file
 * descriptors) with [registry]. The registry is served by `GET /metrics` (docs/ARCHITECTURE.md
 * sections 2.7 and 4.1).
 */
fun Application.configureMetrics(registry: PrometheusMeterRegistry) {
    install(MicrometerMetrics) {
        this.registry = registry
        distributionStatisticConfig = DistributionStatisticConfig.Builder()
            .serviceLevelObjectives(*LATENCY_BUCKETS)
            .build()
    }
}
