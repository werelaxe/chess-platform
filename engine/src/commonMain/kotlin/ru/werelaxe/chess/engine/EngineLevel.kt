package ru.werelaxe.chess.engine

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Playing strength of the built-in engine. */
@Serializable
enum class EngineLevel(
    /** Full-width search depth in plies for classic chess. */
    val classicDepth: Int,
    /** Whether captures are searched beyond the nominal depth. */
    val quiescence: Boolean,
    /** Wall-clock budget for one move. */
    val timeBudget: Duration,
    /** Candidate moves kept for the opponent-reply refinement in quantum chess. */
    val quantumCandidates: Int,
    /** Moves scoring within this many centipawns of the best are chosen at random. */
    val randomness: Int,
) {
    EASY(classicDepth = 2, quiescence = false, timeBudget = 700.milliseconds, quantumCandidates = 6, randomness = 90),
    MEDIUM(classicDepth = 3, quiescence = true, timeBudget = 1500.milliseconds, quantumCandidates = 12, randomness = 0),
    HARD(classicDepth = 6, quiescence = true, timeBudget = 3500.milliseconds, quantumCandidates = 24, randomness = 0),
}
