package ru.werelaxe.chess.server.routes

import io.ktor.server.application.ApplicationCall

private const val DEFAULT_LIMIT = 50
private const val MAX_LIMIT = 200

internal data class Paging(val limit: Int, val offset: Int)

/** `limit` (default [DEFAULT_LIMIT], clamped to 1..[MAX_LIMIT]) and `offset` (at least 0) of a listing. */
internal fun ApplicationCall.paging(): Paging {
    val query = request.queryParameters
    val limit = (query["limit"]?.toIntOrNull() ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
    val offset = (query["offset"]?.toIntOrNull() ?: 0).coerceAtLeast(0)
    return Paging(limit, offset)
}
