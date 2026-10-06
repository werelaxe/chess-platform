package ru.werelaxe.chess.server.service

/** The authenticated caller, extracted from the JWT. */
data class UserPrincipal(val id: Long, val username: String)
