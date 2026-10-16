package ru.werelaxe.chess.engine

import ru.werelaxe.chess.core.GameState
import ru.werelaxe.chess.core.QuantumState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The memory guard of the quantum search. [QuantumSearch] materialises the state after every
 * candidate move at the root, a few hundred states of as many boards as the position has
 * universes, before any setting of the level is consulted, so a fog of many thousands of
 * universes costs hundreds of megabytes to gigabytes at `NOVICE` exactly as at `HARD`, which
 * is how an engine once exhausted its heap. A position with more universes than
 * [maxUniverses] is therefore never searched as it stands: [restrict] keeps its most probable
 * universes, at most [SHALLOW_UNIVERSES] and never more than the ceiling, and the callers
 * search that restriction at [SHALLOW_LEVEL] within [SHALLOW_BUDGET], so the search allocates
 * a few hundred states of a few hundred boards at most and answers within a fraction of a
 * second, whatever the fog. Every move legal in the restriction is legal in the whole position
 * ([QuantumState.mostProbable]), so the game goes on with a move chosen on the likeliest part
 * of the fog rather than with no engine at all. A ceiling of 0 means none.
 */
class UniverseCeiling(val maxUniverses: Int) {
    init {
        require(maxUniverses >= 0) { "maxUniverses must not be negative" }
    }

    /**
     * What the shallow search sees of [state] when it is above the ceiling; null when [state]
     * is within it, or classic, and is searched as it stands at the requested level.
     */
    fun restrict(state: GameState): QuantumState? {
        if (maxUniverses == 0 || state !is QuantumState || state.universeCount <= maxUniverses) return null
        return state.mostProbable(minOf(SHALLOW_UNIVERSES, maxUniverses))
    }

    companion object {
        /** The ceiling the engine service and the API apply unless configured otherwise. */
        const val DEFAULT_MAX_UNIVERSES = 4096

        /** The most universes a shallow search sees. */
        const val SHALLOW_UNIVERSES = 256

        /** The level and the budget of a shallow search: four candidates two plies deep, within a tenth of a second. */
        val SHALLOW_LEVEL: EngineLevel = EngineLevel.NOVICE
        val SHALLOW_BUDGET: Duration = 100.milliseconds
    }
}
