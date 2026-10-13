package ru.werelaxe.chess.server.service

import org.slf4j.LoggerFactory
import ru.werelaxe.chess.engine.EngineLevel
import ru.werelaxe.chess.server.model.User
import ru.werelaxe.chess.server.repository.UserRepository
import java.time.Clock

/**
 * A bot defined in code: the account it plays from and how strongly it plays. A [legacy] bot is
 * kept for the games it already has but is neither offered to players nor used by the matchmaker.
 */
data class BotSpec(val username: String, val displayName: String, val level: EngineLevel, val legacy: Boolean = false)

/**
 * The bot accounts: a registry loaded from the repository at startup and refreshed whenever a
 * bot is created or changed through the admin API, so that the game logic recognises a bot
 * seat by id without touching the database and new bots play without a restart. The roster is
 * every bot but the legacy ones, the code-defined bots first in their order and the ones
 * created through the API after them by creation; the legacy bots follow in [all].
 */
class Bots(private val users: UserRepository) {
    private class Snapshot(val roster: List<User>, val legacy: List<User>) {
        val all: List<User> = roster + legacy
        val byId: Map<Long, User> = all.associateBy { it.id }
    }

    @Volatile
    private var snapshot = Snapshot(emptyList(), emptyList())

    val roster: List<User>
        get() = snapshot.roster

    val legacy: List<User>
        get() = snapshot.legacy

    val all: List<User>
        get() = snapshot.all

    val ids: Set<Long>
        get() = snapshot.byId.keys

    operator fun get(id: Long): User? = snapshot.byId[id]

    fun isBot(id: Long?): Boolean = id != null && id in snapshot.byId

    fun principal(bot: User) = UserPrincipal(bot.id, bot.username, guest = false)

    /** Reloads the registry from the repository. */
    suspend fun refresh() {
        val bots = users.listBots().sortedWith(compareBy({ BotRoster.position(it.username) }, { it.id }))
        val (legacy, roster) = bots.partition { BotRoster.isLegacy(it.username) }
        snapshot = Snapshot(roster, legacy)
    }
}

/**
 * The bots defined in code (docs/ARCHITECTURE.md section 2.10): two per level except one
 * NOVICE and one EXPERT, plus the `computer` account of the earlier versions as a legacy
 * MEDIUM bot. They have no password, so nobody can log in as them, and their names cannot be
 * registered because the rows exist.
 */
object BotRoster {
    const val COMPUTER = "computer"

    val specs: List<BotSpec> = listOf(
        BotSpec("rookie_rook", "Rookie Rook", EngineLevel.NOVICE),
        BotSpec("pawn_pal", "Pawn Pal", EngineLevel.EASY),
        BotSpec("bishop_bud", "Bishop Bud", EngineLevel.EASY),
        BotSpec("knight_nora", "Knight Nora", EngineLevel.MEDIUM),
        BotSpec("castle_carl", "Castle Carl", EngineLevel.MEDIUM),
        BotSpec("queen_quinn", "Queen Quinn", EngineLevel.HARD),
        BotSpec("check_chuck", "Check Chuck", EngineLevel.HARD),
        BotSpec("grandmaster_gus", "Grandmaster Gus", EngineLevel.EXPERT),
        BotSpec(COMPUTER, "Computer", EngineLevel.MEDIUM, legacy = true),
    )

    private val byName = specs.associateBy { it.username.lowercase() }

    private val log = LoggerFactory.getLogger(BotRoster::class.java)

    fun isLegacy(username: String): Boolean = byName[username.lowercase()]?.legacy ?: false

    /** Where a bot goes in listings: the code-defined bots in their order, the others after them. */
    internal fun position(username: String): Int = byName[username.lowercase()]?.let(specs::indexOf) ?: specs.size

    /**
     * Creates the accounts that are missing and loads the registry. An existing bot row is left
     * as it is, so a level or name changed through the admin API survives a restart. A name a
     * person registered before it was reserved is logged and its bot left out until the account
     * is renamed: the server starts without it rather than not at all.
     */
    suspend fun ensure(users: UserRepository, clock: Clock): Bots {
        val now = clock.instant()
        for (spec in specs) {
            val existing = users.findByUsername(spec.username)
            when {
                existing == null -> {
                    // Another instance may have created the row meanwhile; its row is as good as ours.
                    if (users.createBot(spec.username, spec.displayName, spec.level, now) == null) {
                        log.warn("The bot '{}' was created concurrently; using the existing row", spec.username)
                    }
                }
                !existing.isBot -> log.error(
                    "A regular account holds the username '{}'; the bot is skipped until that account is renamed",
                    spec.username,
                )
            }
        }
        return Bots(users).also { it.refresh() }
    }
}
