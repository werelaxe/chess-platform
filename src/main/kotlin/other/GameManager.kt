package other

import core.Game
import dialects.GameCollection
import dialects.GameKind
import kotlinx.serialization.Serializable


@Serializable
data class GameInfo(val id: Int, val kind: GameKind)


class GameManager {
    fun create(kind: GameKind): GameInfo {
        GameCollection.create(kind).apply {
            val id = games.size
            games[id] = this
            GameInfo(id, kind).let {
                infos[id] = it
                return it
            }
        }
    }

    fun get(id: Int) = games[id]
    fun info(id: Int) = infos[id]

    private val games = mutableMapOf<Int, Game<*, *, *>>()
    private val infos = mutableMapOf<Int, GameInfo>()
}
