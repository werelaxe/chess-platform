// People and bots as they appear in game DTOs (ARCHITECTURE.md 2.3 and 2.10): a bot is an
// ordinary account with a level that may sit on either side of any game.

import type { BotLevel, Color, GameSummary, PlayerSummary, UserRef } from "../api/types";
import { i18n } from "../i18n";

/** Bot levels in order of strength. */
export const BOT_LEVELS: readonly BotLevel[] = ["NOVICE", "EASY", "MEDIUM", "HARD", "EXPERT"];

export function botLevelLabel(level: BotLevel): string {
  return i18n.t(`levels.${level}`);
}

/** The name to show for a player: the display name when set, the username otherwise. */
export function displayName(user: Pick<UserRef, "username" | "name">): string {
  const name = user.name?.trim();
  return name ? name : user.username;
}

/** Guests have no profile page; everyone else is linked to theirs. */
export function hasProfile(user: Pick<UserRef, "guest">): boolean {
  return !user.guest;
}

export function seat(game: Pick<GameSummary, "white" | "black">, color: Color): UserRef | null {
  return color === "WHITE" ? game.white : game.black;
}

export function opponentOf(game: Pick<GameSummary, "white" | "black">, color: Color): UserRef | null {
  return seat(game, color === "WHITE" ? "BLACK" : "WHITE");
}

/** Whether the seat of the given color is taken by a bot. */
export function isBotSeat(game: Pick<GameSummary, "white" | "black">, color: Color): boolean {
  return seat(game, color)?.bot === true;
}

/**
 * The player among `players` whose username is `typed`, or null. Usernames are unique whatever
 * the case, and an invitation goes to a username: a name typed by hand only counts once the
 * search has shown the player with that exact username.
 */
export function findByUsername(typed: string, players: readonly PlayerSummary[]): PlayerSummary | null {
  const wanted = typed.trim().toLowerCase();
  if (!wanted) return null;
  return players.find((player) => player.user.username.toLowerCase() === wanted) ?? null;
}

/** Whether `typed` is the viewer's own username: one cannot invite oneself. */
export function isOwnUsername(typed: string, viewer: Pick<UserRef, "username"> | null | undefined): boolean {
  return viewer != null && typed.trim().toLowerCase() === viewer.username.toLowerCase();
}
