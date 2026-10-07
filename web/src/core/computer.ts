// Games against the built-in engine (ARCHITECTURE.md 2.3): the system user "computer" takes the
// other seat as soon as the game is created, and the game is unlisted.

import type { BotLevel, Color, ColorChoice, CreateGameRequest, GameKind, GameSummary, Opponent, UserRef, Visibility } from "../api/types";
import { i18n } from "../i18n";

/** Engine levels in order of strength. */
export const BOT_LEVELS: readonly BotLevel[] = ["EASY", "MEDIUM", "HARD"];

export function botLevelLabel(level: BotLevel): string {
  return i18n.t(`levels.${level}`);
}

/** One line per level for the level picker. */
export function botLevelHint(level: BotLevel): string {
  return i18n.t(`levelHints.${level}`);
}

/** Display name of a player: the username, or "Computer · Medium" for the bot (plain "Computer" without a level). */
export function playerLabel(user: UserRef, botLevel?: BotLevel | null): string {
  if (!user.bot) return user.username;
  return botLevel ? i18n.t("user.computerWithLevel", { level: botLevelLabel(botLevel) }) : i18n.t("user.computer");
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
