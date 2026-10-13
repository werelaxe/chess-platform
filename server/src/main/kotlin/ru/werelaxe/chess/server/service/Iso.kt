package ru.werelaxe.chess.server.service

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Timestamps in the API: ISO instants with millisecond precision. */
internal fun Instant.toIso(): String = DateTimeFormatter.ISO_INSTANT.format(truncatedTo(ChronoUnit.MILLIS))
