package webserver

import core.Coordinate
import kotlinx.serialization.Serializable

@Serializable
data class StepSchema(
    val gameId: Int,
    val from: Coordinate,
    val to: Coordinate
)
