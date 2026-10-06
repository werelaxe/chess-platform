package ru.werelaxe.chess.server.repository

data class Repositories(
    val users: UserRepository,
    val games: GameRepository,
)
