package other

import core.Game
import dialects.GameCollection
import dialects.GameKind
import kotlinx.serialization.Serializable
import kotlin.random.Random.Default.nextInt


@Serializable
data class GameInfo(val id: String, val kind: GameKind)


class GameManager {
    private val stringId2IntId = mutableMapOf<String, Int>()

    fun create(kind: GameKind): GameInfo {
        GameCollection.create(kind).apply {
            val id = games.size
            val stringId = genStringId()

            stringId2IntId[stringId] = id
            games[id] = this

            GameInfo(stringId, kind).let {
                infos[id] = it
                return it
            }
        }
    }

    private val alpha = ('a'..'z') + ('A'..'Z') + ('0'..'9')

    private fun genStringId(length: Int = 20): String {
        while (true) {
            val id = (1..length)
                .map { nextInt(0, alpha.size) }
                .map(alpha::get)
                .joinToString("")
            if (id !in stringId2IntId) {
                return id
            }
        }
    }

    fun get(id: String) = games[stringId2IntId[id]]
    fun info(id: String) = infos[stringId2IntId[id]]

    private val games = mutableMapOf<Int, Game<*, *, *>>()
    private val infos = mutableMapOf<Int, GameInfo>()
}
