package ru.werelaxe.chess.engine

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Playing strength of the built-in engine. */
@Serializable
enum class EngineLevel(
    /** Full-width search depth in plies for classic chess. */
    val classicDepth: Int,
    /** Whether captures are searched beyond the nominal depth. */
    val quiescence: Boolean,
    /** Wall-clock budget for one move. */
    val timeBudget: Duration,
    /** Candidate moves kept for the deeper refinement in quantum chess. */
    val quantumCandidates: Int,
    /** Deepest lookahead in plies for quantum chess (an even number); reached only when the budget allows. */
    val quantumDepth: Int,
    /** Moves scoring within this many centipawns of the best are chosen at random. */
    val randomness: Int,
    /**
     * What a split costs in quantum chess, in centipawns, on top of what it leads to. The levels
     * that pick at random within a margin need it: a split is seldom worse than the best move
     * at a glance, so without it they split on almost every move and bury the game in a fog of
     * hundreds of universes that nobody resolves. The levels that always take the best move
     * split only when it pays, and pay nothing.
     */
    val splitCost: Int,
) {
    NOVICE(classicDepth = 1, quiescence = false, timeBudget = 300.milliseconds, quantumCandidates = 4, quantumDepth = 2, randomness = 200, splitCost = 40),
    EASY(classicDepth = 2, quiescence = false, timeBudget = 700.milliseconds, quantumCandidates = 6, quantumDepth = 2, randomness = 90, splitCost = 40),
    MEDIUM(classicDepth = 3, quiescence = true, timeBudget = 1500.milliseconds, quantumCandidates = 12, quantumDepth = 4, randomness = 0, splitCost = 0),
    HARD(classicDepth = 6, quiescence = true, timeBudget = 3500.milliseconds, quantumCandidates = 24, quantumDepth = 4, randomness = 0, splitCost = 0),
    EXPERT(classicDepth = 7, quiescence = true, timeBudget = 6.seconds, quantumCandidates = 32, quantumDepth = 4, randomness = 0, splitCost = 0),
}
