package core

import kotlinx.serialization.Serializable


typealias Player = String
typealias FigureType = String


interface Figure {
    val owner: Player
    val type: FigureType
}
