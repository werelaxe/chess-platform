package ru.werelaxe.chess.engineservice.routes

import io.ktor.http.ContentType
import io.ktor.http.withCharset
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

/** The content type of the Prometheus text exposition format. */
private val PROMETHEUS_TEXT: ContentType =
    ContentType.Text.Plain.withParameter("version", "0.0.4").withCharset(Charsets.UTF_8)

/**
 * `GET /metrics`: the Prometheus exposition of [registry] (docs/ARCHITECTURE.md section 4.1).
 * Like `/think` and `/health` it needs no authentication: the service is only reachable from
 * inside the cluster, where Prometheus scrapes every replica of it.
 */
fun Route.metricsRoutes(registry: PrometheusMeterRegistry) {
    get("/metrics") {
        call.respondText(registry.scrape(), PROMETHEUS_TEXT)
    }
}
