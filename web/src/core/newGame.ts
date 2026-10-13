// The body of POST /api/games (ARCHITECTURE.md 2.3) for the choices made in the lobby form and
// on a player's profile.

import type { ColorChoice, CreateGameRequest, GameKind, Visibility } from "../api/types";

/** How the lobby form offers the other seat: to anyone, to a bot of the roster or to a named player. */
export type OpponentMode = "OPEN" | "BOT" | "PLAYER";

/**
 * Statuses whose answer to a creation names the problem with the invitee (400 for oneself or a
 * missing name, 404 for an unknown player): the server's wording is shown as it is.
 */
export const INVITEE_ERROR_STATUSES: readonly number[] = [400, 404];

export interface NewGameOptions {
  kind: GameKind;
  visibility: Visibility;
  color: ColorChoice;
  rated: boolean;
  /** Null for an open game; the username of the bot or player to invite otherwise. */
  opponentUsername: string | null;
  /** A guest's games are never rated, whatever the toggle says. */
  guest: boolean;
}

/**
 * The chosen visibility is sent in every mode: a public invitation is left out of the lobby
 * only while it waits for an answer and is listed among the live games once the game starts.
 */
export function createGameRequest({ kind, visibility, color, rated, opponentUsername, guest }: NewGameOptions): CreateGameRequest {
  const base = { kind, visibility, color, rated: rated && !guest };
  const username = opponentUsername?.trim();
  if (username) return { ...base, opponent: "USER", opponentUsername: username };
  return { ...base, opponent: "OPEN" };
}
