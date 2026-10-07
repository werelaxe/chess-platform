package ru.werelaxe.chess.core

import kotlinx.serialization.Serializable

@Serializable
enum class GameKind {
    CLASSIC,
    QUANTUM,
}
