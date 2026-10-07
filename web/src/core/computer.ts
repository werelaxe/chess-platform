// Games against the built-in engine (ARCHITECTURE.md 2.3): the system user "computer" takes the
// other seat as soon as the game is created, and the game is unlisted.

import type { BotLevel, Color, ColorChoice, CreateGameRequest, GameKind, GameSummary, Opponent, UserRef, Visibility } from "../api/types";

/** Engine levels in order of strength. */
export const BOT_LEVELS: readonly BotLevel[] = ["EASY", "MEDIUM", "HARD"];

export const BOT_LEVEL_LABELS: Record<BotLevel, string> = {
  EASY: "Easy",
  MEDIUM: "Medium",
  HARD: "Hard",
};

/** One line per level for the level picker. */
export const BOT_LEVEL_HINTS: Record<BotLevel, string> = {
  EASY: "A shallow search with some randomness: forgiving, good for learning the moves.",
  MEDIUM: "Thinks a few moves ahead.",
  HARD: "Takes its full thinking time on every move.",
};

/** Display name of a player: the username, or "Computer · Medium" for the bot (plain "Computer" without a level). */
export function playerLabel(user: UserRef, botLevel?: BotLevel | null): string {
  if (!user.bot) return user.username;
  return botLevel ? `Computer · ${BOT_LEVEL_LABELS[botLevel]}` : "Computer";
}

/** The color played by the computer, or null for a game between people. */
export function botColor(game: Pick<GameSummary, "white" | "black">): Color | null {
  if (game.white?.bot) return "WHITE";
  if (game.black?.bot) return "BLACK";
  return null;
}

export interface NewGameOptions {
  kind: GameKind;
  visibility: Visibility;
  color: ColorChoice;
  opponent: Opponent;
  level: BotLevel;
}

/**
 * The body of POST /api/games. Computer games are always unlisted, so the visibility choice is
 * replaced by PRIVATE and the level is sent; the level is left out of games between people.
 */
export function createGameRequest({ kind, visibility, color, opponent, level }: NewGameOptions): CreateGameRequest {
  if (opponent === "COMPUTER") return { kind, visibility: "PRIVATE", color, opponent, level };
  return { kind, visibility, color, opponent };
}
