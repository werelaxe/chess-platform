import { describe, expect, it } from "vitest";
import type { UserRef } from "../api/types";
import { BOT_LEVELS, BOT_LEVEL_HINTS, BOT_LEVEL_LABELS, botColor, createGameRequest, playerLabel } from "./computer";

const ALICE: UserRef = { id: 1, username: "alice" };
const GUEST: UserRef = { id: 2, username: "guest-123456", guest: true };
const COMPUTER: UserRef = { id: 3, username: "computer", bot: true };

describe("createGameRequest", () => {
  it("keeps the visibility choice and sends no level for a game between people", () => {
    expect(
      createGameRequest({ kind: "QUANTUM", visibility: "PUBLIC", color: "RANDOM", opponent: "HUMAN", level: "MEDIUM" }),
    ).toStrictEqual({ kind: "QUANTUM", visibility: "PUBLIC", color: "RANDOM", opponent: "HUMAN" });
  });

  it("forces computer games to be private and carries the level and the color choice", () => {
    expect(
      createGameRequest({ kind: "CLASSIC", visibility: "PUBLIC", color: "BLACK", opponent: "COMPUTER", level: "HARD" }),
    ).toStrictEqual({ kind: "CLASSIC", visibility: "PRIVATE", color: "BLACK", opponent: "COMPUTER", level: "HARD" });
  });
});

describe("playerLabel", () => {
  it("shows people by their username", () => {
    expect(playerLabel(ALICE)).toBe("alice");
    expect(playerLabel(GUEST, "HARD")).toBe("guest-123456");
  });

  it("names the computer with the level of the game when known", () => {
    expect(playerLabel(COMPUTER, "MEDIUM")).toBe("Computer · Medium");
    expect(playerLabel(COMPUTER, "EASY")).toBe("Computer · Easy");
    expect(playerLabel(COMPUTER)).toBe("Computer");
    expect(playerLabel(COMPUTER, null)).toBe("Computer");
  });
});

describe("botColor", () => {
  it("finds the seat taken by the computer", () => {
    expect(botColor({ white: COMPUTER, black: ALICE })).toBe("WHITE");
    expect(botColor({ white: ALICE, black: COMPUTER })).toBe("BLACK");
    expect(botColor({ white: ALICE, black: GUEST })).toBeNull();
    expect(botColor({ white: ALICE, black: null })).toBeNull();
  });
});

describe("level metadata", () => {
  it("has a label and a one-line hint for every level", () => {
    expect(BOT_LEVELS).toStrictEqual(["EASY", "MEDIUM", "HARD"]);
    for (const level of BOT_LEVELS) {
      expect(BOT_LEVEL_LABELS[level]).toMatch(/^[A-Z][a-z]+$/);
      expect(BOT_LEVEL_HINTS[level]).not.toContain("\n");
    }
  });
});
