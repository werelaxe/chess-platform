package ru.werelaxe.chess.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A move in any supported variant. Classic chess accepts only [Normal]; quantum chess
 * accepts all three. Serialized polymorphically with a "type" discriminator.
 */
@Serializable
sealed interface GameMove {
    /** A regular move of a piece from one square to another. */
    @Serializable
    @SerialName("normal")
    data class Normal(
        val from: Square,
        val to: Square,
        val promotion: PieceType? = null,
    ) : GameMove {
        fun toMove(): Move = Move(from, to, promotion)
    }

    /**
     * A quantum split: the piece on [from] goes to [first] in half of the universes and to
     * [second] in the other half. [second] may equal [from], meaning the piece stays in place.
     * [promotion] applies to whichever target is a promotion square for a pawn.
     */
    @Serializable
    @SerialName("split")
    data class Split(
        val from: Square,
        val first: Square,
        val second: Square,
        val promotion: PieceType? = null,
    ) : GameMove

    /**
     * Measurement of a square. The server decides the [outcome] at random according to the
     * current distribution; a client proposes the move with [outcome] = null.
     */
    @Serializable
    @SerialName("observe")
    data class Observe(
        val square: Square,
        val outcome: Observation? = null,
    ) : GameMove
}

/** The observed content of a square: a piece, or null for an empty square. */
@Serializable
data class Observation(val piece: Piece?)
