package webserver

import core.AdditionalStepInfo
import core.Coordinate
import kotlinx.serialization.Serializable

@Serializable
data class StepSchema(
    val gameId: Int,
    val from: Coordinate,
    val to: Coordinate,
    val additionalStepInfo: AdditionalStepInfo
)
