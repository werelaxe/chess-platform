package ru.werelaxe.chess.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.jvm.JvmInline

/**
 * A board square. [index] = rank * 8 + file, so a1 = 0, h1 = 7, a8 = 56, h8 = 63.
 * Serialized as its algebraic name ("e4").
 */
@Serializable(with = SquareSerializer::class)
@JvmInline
value class Square(val index: Int) {
    init {
        require(index in 0 until 64) { "Square index out of range: $index" }
    }

    /** File index: 0 = a, 7 = h. */
    val file: Int
        get() = index and 7

    /** Rank index: 0 = first rank, 7 = eighth rank. */
    val rank: Int
        get() = index shr 3

    val name: String
        get() = "${'a' + file}${'1' + rank}"

    /** Square shifted by the given file/rank deltas, or null when it would leave the board. */
    fun offset(fileDelta: Int, rankDelta: Int): Square? {
        val f = file + fileDelta
        val r = rank + rankDelta
        return if (f in 0..7 && r in 0..7) of(f, r) else null
    }

    override fun toString(): String = name

    companion object {
        fun of(file: Int, rank: Int): Square {
            require(file in 0..7 && rank in 0..7) { "Square out of range: file=$file rank=$rank" }
            return Square(rank * 8 + file)
        }

        fun parse(name: String): Square {
            require(name.length == 2) { "Invalid square name: '$name'" }
            val file = name[0] - 'a'
            val rank = name[1] - '1'
            require(file in 0..7 && rank in 0..7) { "Invalid square name: '$name'" }
            return of(file, rank)
        }

        val ALL: List<Square> = List(64) { Square(it) }

        val A1 = parse("a1")
        val C1 = parse("c1")
        val D1 = parse("d1")
        val E1 = parse("e1")
        val F1 = parse("f1")
        val G1 = parse("g1")
        val H1 = parse("h1")
        val A8 = parse("a8")
        val C8 = parse("c8")
        val D8 = parse("d8")
        val E8 = parse("e8")
        val F8 = parse("f8")
        val G8 = parse("g8")
        val H8 = parse("h8")
    }
}

object SquareSerializer : KSerializer<Square> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Square", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Square) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): Square = Square.parse(decoder.decodeString())
}
