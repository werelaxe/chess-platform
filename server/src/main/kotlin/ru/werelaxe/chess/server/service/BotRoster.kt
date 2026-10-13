package ru.werelaxe.chess.server.service

import org.slf4j.LoggerFactory
import ru.werelaxe.chess.engine.EngineLevel
import ru.werelaxe.chess.server.model.User
import ru.werelaxe.chess.server.repository.UserRepository
import java.time.Clock

/**
 * A bot of the roster: the account it plays from and how strongly it plays. A [legacy] bot is
 * kept for the games it already has but is neither offered to players nor used by the matchmaker.
 */
data class BotSpec(val username: String, val displayName: String, val level: EngineLevel, val legacy: Boolean = false)

/**
 * The bot accounts as ensured at startup. Bot rows never change while the server runs, so the
 * game logic recognises a bot seat by id without touching the database.
 */
class Bots(val roster: List<User>, legacy: List<User>) {
    val all: List<User> = roster + legacy
    private val byId = all.associateBy { it.id }

    val ids: Set<Long>
        get() = byId.keys

    operator fun get(id: Long): User? = byId[id]

    fun isBot(id: Long?): Boolean = id != null && id in byId

    fun principal(bot: User) = UserPrincipal(bot.id, bot.username, guest = false)
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

    private val log = LoggerFactory.getLogger(BotRoster::class.java)

    /**
     * Creates the accounts that are missing and brings the others up to date. A name a person
     * registered before it was reserved is logged and its bot left out of the roster until the
     * account is renamed: the server starts without it rather than not at all.
     */
    suspend fun ensure(users: UserRepository, clock: Clock): Bots {
        val now = clock.instant()
        val accounts = HashMap<BotSpec, User>()
        for (spec in specs) {
            val account = users.upsertBot(spec.username, spec.displayName, spec.level, now)
            if (account == null) {
                log.error("A regular account holds the username '{}'; the bot is skipped until that account is renamed", spec.username)
            } else {
                accounts[spec] = account
            }
        }
        return Bots(
            roster = specs.filter { !it.legacy }.mapNotNull { accounts[it] },
            legacy = specs.filter { it.legacy }.mapNotNull { accounts[it] },
        )
    }
}
