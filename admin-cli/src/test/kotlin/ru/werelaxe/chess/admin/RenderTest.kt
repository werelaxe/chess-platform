package ru.werelaxe.chess.admin

import ru.werelaxe.chess.admin.client.AdminBotDetails
import ru.werelaxe.chess.admin.client.AdminJson
import ru.werelaxe.chess.admin.client.BotList
import ru.werelaxe.chess.admin.client.GameSummary
import ru.werelaxe.chess.admin.client.Overview
import ru.werelaxe.chess.admin.client.SettingList
import ru.werelaxe.chess.admin.output.Render
import kotlin.test.Test
import kotlin.test.assertEquals

class RenderTest {
    @Test
    fun overviewListsTheCountsAndTheSettings() {
        val overview = AdminJson.decodeFromString<Overview>(Fixtures.OVERVIEW)
        assertEquals(
            """
            Users:             123
            Guests:            45
            Bots:              8
            Waiting games:     2
            Active games:      5
            Finished games:    1000
            Active bot games:  3

            SETTING                VALUE
            bot.matchmaking        true
            bot.open_games_target  2
            """.trimIndent(),
            Render.overview(overview),
        )
    }

    @Test
    fun overviewWithoutSettingsIsTheCountsAlone() {
        val overview = AdminJson.decodeFromString<Overview>("""{"users":1,"guests":0,"bots":8,"games":{"waiting":0,"active":0,"finished":0},"activeBotGames":0}""")
        assertEquals(7, Render.overview(overview).lines().size)
    }

    @Test
    fun botsTableShowsRatingsLevelsAndTheLegacyFlag() {
        val bots = AdminJson.decodeFromString<BotList>(Fixtures.BOTS).bots
        assertEquals(
            """
            USERNAME     NAME             LEVEL   CLASSIC  QUANTUM  ACTIVE  GAMES  LEGACY
            novice-nick  Nick the Novice  NOVICE  1480     -        1       25
            computer     Computer         MEDIUM  -        -        0       100    yes
            """.trimIndent(),
            Render.bots(bots),
        )
    }

    @Test
    fun botDetailsHaveTheRatingsAndBothGameLists() {
        val bot = AdminJson.decodeFromString<AdminBotDetails>(Fixtures.NICK_DETAILS)
        assertEquals(
            """
            Username:      novice-nick
            Name:          Nick the Novice
            Level:         NOVICE
            Legacy:        no
            Active games:  1
            Total games:   25
            Classic:       1480 (25 rated, 10 wins, 5 draws, 10 losses, peak 1520)
            Quantum:       unrated

            Active games (1)
            ID            KIND     STATUS  WHITE        BLACK  MOVES  RATED  RESULT  UPDATED
            aZ3kq9bX7LmN  QUANTUM  ACTIVE  novice-nick  alice  12     yes    -       2026-10-07 10:30

            Recent games (2)
            ID            KIND     STATUS    WHITE        BLACK        MOVES  RATED  RESULT         UPDATED
            aZ3kq9bX7LmN  QUANTUM  ACTIVE    novice-nick  alice        12     yes    -              2026-10-07 10:30
            bY4lr0cY8MnO  CLASSIC  FINISHED  alice        novice-nick  41     no     0-1 CHECKMATE  2026-10-06 09:45
            """.trimIndent(),
            Render.bot(bot),
        )
    }

    @Test
    fun gameResultsAreScores() {
        fun game(result: String?, black: String = Fixtures.ALICE_USER) = AdminJson.decodeFromString<GameSummary>(
            """{"id":"g","kind":"CLASSIC","status":"FINISHED","white":${Fixtures.NICK_USER},"black":$black,""" +
                """"moveCount":3,"result":$result,"createdAt":"2026-10-07T10:00:00Z","updatedAt":"2026-10-07T10:00:00Z"}""",
        )
        val rows = Render.games(
            listOf(
                game("""{"winner":"WHITE","reason":"CHECKMATE"}"""),
                game("""{"winner":null,"reason":"STALEMATE"}"""),
                game("""{"winner":null,"reason":"ABANDONMENT"}""", black = "null"),
            ),
        ).lines().drop(1)
        assertEquals(listOf("1-0 CHECKMATE", "1/2-1/2 STALEMATE", "1/2-1/2 ABANDONMENT"), rows.map { it.substringAfter("yes    ").substringBefore("  2026").trim() })
        assertEquals("-", rows[2].split(Regex("  +"))[4])
    }

    @Test
    fun settingsTableShowsValueDefaultAndSource() {
        val settings = AdminJson.decodeFromString<SettingList>(Fixtures.SETTINGS).settings
        assertEquals(
            """
            KEY                    VALUE  DEFAULT  SOURCE   DESCRIPTION
            bot.matchmaking        true   true     default  the matchmaker loop acts on its ticks
            bot.open_games_target  3      2        db       public open games kept waiting by bots
            """.trimIndent(),
            Render.settings(settings),
        )
    }

    @Test
    fun timestampsAreMinutesInUtc() {
        assertEquals("2026-10-07 10:30", Render.timestamp("2026-10-07T10:30:15.123Z"))
        assertEquals("2026-10-07 08:00", Render.timestamp("2026-10-07T10:00:00+02:00"))
        assertEquals("yesterday", Render.timestamp("yesterday"))
    }
}
