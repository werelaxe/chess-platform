package core

data class Figure <Type, PlayerType> (
    val owner: PlayerType,
    val type: Type
)
