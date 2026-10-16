// Elo ratings as reported by the server (ARCHITECTURE.md 2.8 and 2.9).

import type { GameDto, GameKind, PlayerRatings, RatingChange, RatingSummary } from "../api/types";

/** The rating in one variant, or null while the player has none there. */
export function ratingIn(ratings: PlayerRatings | null | undefined, kind: GameKind): RatingSummary | null {
  return ratings?.[kind] ?? null;
}

/** "+16", "-12" or "0": the signed difference of a rating change. */
export function formatRatingDelta(change: RatingChange): string {
  const delta = change.after - change.before;
  return delta > 0 ? `+${delta}` : String(delta);
}

/** "1512 → 1528 (+16)": a player's rating before and after a rated game. */
export function formatRatingChange(change: RatingChange): string {
  return `${change.before} → ${change.after} (${formatRatingDelta(change)})`;
}

/** "12 / 3 / 5": wins, draws and losses in that order. */
export function formatRecord(summary: Pick<RatingSummary, "wins" | "draws" | "losses">): string {
  return `${summary.wins} / ${summary.draws} / ${summary.losses}`;
}

/**
 * Whether a finished game deserves the note that it was rated but changed no rating because
 * it ended before both sides had moved (ARCHITECTURE.md 2.8). A game ended by abandonment
 * without a winner (an invitation cancelled or declined, an admin abort, a stale game with a
 * bot on turn) was never decided and changes no rating whatever its length, so the note, which
 * would explain the missing change by the move count, is not shown for it. A forfeit by
 * abandonment has a winner and gets the note like a resignation when the result did not count.
 */
export function endedWithoutRatingChange(game: Pick<GameDto, "status" | "rated" | "ratingChanges" | "result">): boolean {
  if (game.status !== "FINISHED" || !game.rated || game.ratingChanges) return false;
  return !(game.result?.winner === null && game.result.reason === "ABANDONMENT");
}
