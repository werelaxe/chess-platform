package dialects

import kotlinx.serialization.Serializable


@Serializable
data class SerializableFigure(
    val type: Int,
    val owner: Int
)