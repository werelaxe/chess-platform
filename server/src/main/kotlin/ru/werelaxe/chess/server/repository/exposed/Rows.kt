package ru.werelaxe.chess.server.repository.exposed

import org.jetbrains.exposed.v1.core.ResultRow
import ru.werelaxe.chess.server.db.RatingChanges
import ru.werelaxe.chess.server.db.Ratings
import ru.werelaxe.chess.server.db.Users
import ru.werelaxe.chess.server.model.Rating
import ru.werelaxe.chess.server.model.RatingChange
import ru.werelaxe.chess.server.model.User

/** Row mappings shared by the Exposed repositories (a leaderboard row carries a user and a rating). */
internal fun ResultRow.toUser() = User(
    id = this[Users.id],
    username = this[Users.username],
    passwordHash = this[Users.passwordHash],
    isGuest = this[Users.isGuest],
    isBot = this[Users.isBot],
    createdAt = this[Users.createdAt].toInstant(),
    locale = this[Users.locale],
    displayName = this[Users.displayName],
    botLevel = this[Users.botLevel],
    lastSeenAt = this[Users.lastSeenAt]?.toInstant(),
)

internal fun ResultRow.toRating() = Rating(
    userId = this[Ratings.userId],
    kind = this[Ratings.kind],
    rating = this[Ratings.rating],
    games = this[Ratings.games],
    wins = this[Ratings.wins],
    draws = this[Ratings.draws],
    losses = this[Ratings.losses],
    peak = this[Ratings.peak],
    updatedAt = this[Ratings.updatedAt].toInstant(),
)

internal fun ResultRow.toRatingChange() = RatingChange(
    gameId = this[RatingChanges.gameId],
    userId = this[RatingChanges.userId],
    kind = this[RatingChanges.kind],
    before = this[RatingChanges.ratingBefore],
    after = this[RatingChanges.ratingAfter],
    createdAt = this[RatingChanges.createdAt].toInstant(),
)
