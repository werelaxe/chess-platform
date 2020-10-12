package webserver

import core.Coordinate
import kotlinx.serialization.Serializable

@Serializable
data class SerializableState(
    val board: List<List<Int?>>,
    val currentPlayer: Int
)


@Serializable
data class SerializableQuantumState(
    val board: List<List<Int?>>,
    val currentPlayer: Int,
    val postQuantum: Coordinate?
)
