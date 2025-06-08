package core

import kotlinx.serialization.Serializable


@Serializable
data class AdditionalStepInfo(
    val records: Map<String, String>
)
