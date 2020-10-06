package webserver

import kotlinx.serialization.Serializable

@Serializable
data class SerializableState(
    val board: List<List<Int?>>,
    val currentPlayer: Int
)
