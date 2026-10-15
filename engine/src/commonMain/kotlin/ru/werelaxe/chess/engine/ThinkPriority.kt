package ru.werelaxe.chess.engine

import kotlinx.serialization.Serializable

/**
 * Who waits for a computer move: [HUMAN] when a person is at the board (a human-versus-bot
 * game) and [BOT] when both seats are bots. The engine service serves humans first and keeps
 * half of its queue for them (docs/ARCHITECTURE.md section 2.7), and the API lets a
 * bot-versus-bot game wait rather than search elsewhere when the service is busy.
 */
@Serializable
enum class ThinkPriority {
    HUMAN,
    BOT,
}
