import { afterEach, describe, expect, it } from "vitest";
import type { PlayerSummary, UserRef } from "../api/types";
import { i18n } from "../i18n";
import ru from "../i18n/ru.json";
import { BOT_LEVELS, botLevelLabel, displayName, findByUsername, hasProfile, isBotSeat, isOwnUsername, opponentOf, seat } from "./players";

const ALICE: UserRef = { id: 1, username: "alice", name: "Alice Smith" };
const BOB: UserRef = { id: 2, username: "bob", name: null };
const GUEST: UserRef = { id: 3, username: "guest-123456", guest: true };
const BOT: UserRef = { id: 4, username: "fianchetto", name: "Fianchetto", bot: true, botLevel: "MEDIUM" };

afterEach(() => i18n.changeLanguage("en"));

describe("displayName", () => {
  it("prefers the display name and falls back to the username", () => {
    expect(displayName(ALICE)).toBe("Alice Smith");
    expect(displayName(BOB)).toBe("bob");
    expect(displayName(GUEST)).toBe("guest-123456");
    expect(displayName({ username: "carol", name: "   " })).toBe("carol");
  });
});

describe("hasProfile", () => {
  it("is false only for guests", () => {
    expect(hasProfile(ALICE)).toBe(true);
    expect(hasProfile(BOT)).toBe(true);
    expect(hasProfile(GUEST)).toBe(false);
  });
});

describe("seats", () => {
  const game = { white: ALICE, black: BOT };

  it("finds players and opponents by color", () => {
    expect(seat(game, "WHITE")).toBe(ALICE);
    expect(seat(game, "BLACK")).toBe(BOT);
    expect(opponentOf(game, "WHITE")).toBe(BOT);
    expect(opponentOf(game, "BLACK")).toBe(ALICE);
    expect(opponentOf({ white: ALICE, black: null }, "WHITE")).toBeNull();
  });

  it("tells bot seats apart from people and empty seats", () => {
    expect(isBotSeat(game, "BLACK")).toBe(true);
    expect(isBotSeat(game, "WHITE")).toBe(false);
    expect(isBotSeat({ white: null, black: GUEST }, "WHITE")).toBe(false);
    expect(isBotSeat({ white: null, black: GUEST }, "BLACK")).toBe(false);
  });
});

describe("level labels", () => {
  it("exist for every level, in order of strength", () => {
    expect(BOT_LEVELS).toStrictEqual(["NOVICE", "EASY", "MEDIUM", "HARD", "EXPERT"]);
    for (const level of BOT_LEVELS) expect(botLevelLabel(level)).toMatch(/^[A-Z][a-z]+$/);
  });

  it("follow the UI language", async () => {
    expect(botLevelLabel("MEDIUM")).toBe("Medium");
    await i18n.changeLanguage("ru");
    expect(botLevelLabel("MEDIUM")).toBe(ru.levels.MEDIUM);
  });
});

describe("findByUsername", () => {
  const found: PlayerSummary[] = [
    { user: ALICE, ratings: { CLASSIC: null, QUANTUM: null } },
    { user: BOT, ratings: { CLASSIC: null, QUANTUM: null } },
  ];

  it("matches a username in full, whatever the case and the surrounding spaces", () => {
    expect(findByUsername("alice", found)?.user).toBe(ALICE);
    expect(findByUsername(" Alice ", found)?.user).toBe(ALICE);
    expect(findByUsername("FIANCHETTO", found)?.user).toBe(BOT);
  });

  it("ignores prefixes, display names and an empty text", () => {
    expect(findByUsername("ali", found)).toBeNull();
    expect(findByUsername("Alice Smith", found)).toBeNull();
    expect(findByUsername("", found)).toBeNull();
    expect(findByUsername("   ", found)).toBeNull();
    expect(findByUsername("alice", [])).toBeNull();
  });
});

describe("isOwnUsername", () => {
  it("recognises the viewer's username regardless of case, and nobody's without a viewer", () => {
    expect(isOwnUsername("alice", ALICE)).toBe(true);
    expect(isOwnUsername(" ALICE ", ALICE)).toBe(true);
    expect(isOwnUsername("Alice Smith", ALICE)).toBe(false);
    expect(isOwnUsername("bob", ALICE)).toBe(false);
    expect(isOwnUsername("alice", null)).toBe(false);
    expect(isOwnUsername("alice", undefined)).toBe(false);
  });
});
