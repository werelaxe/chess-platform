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
 * Whether a finished game deserves the note that it was rated but changed no rating: its
 * result did not count because it ended before both sides had moved (ARCHITECTURE.md 2.8).
 * An invitation cancelled or declined before anyone moved was never played and gets no note.
 */
export function endedWithoutRatingChange(game: Pick<GameDto, "status" | "rated" | "ratingChanges" | "result" | "moveCount">): boolean {
  if (game.status !== "FINISHED" || !game.rated || game.ratingChanges) return false;
  return !(game.result?.reason === "ABANDONMENT" && game.moveCount === 0);
}
