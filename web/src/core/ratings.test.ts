import { describe, expect, it } from "vitest";
import type { PlayerRatings } from "../api/types";
import { endedWithoutRatingChange, formatRatingChange, formatRatingDelta, formatRecord, ratingIn } from "./ratings";

describe("formatRatingDelta", () => {
  it("signs gains, keeps the minus of losses and shows a flat change as zero", () => {
    expect(formatRatingDelta({ before: 1512, after: 1528 })).toBe("+16");
    expect(formatRatingDelta({ before: 1512, after: 1500 })).toBe("-12");
    expect(formatRatingDelta({ before: 1500, after: 1500 })).toBe("0");
  });
});

describe("formatRatingChange", () => {
  it("shows before, after and the delta", () => {
    expect(formatRatingChange({ before: 1512, after: 1528 })).toBe("1512 → 1528 (+16)");
    expect(formatRatingChange({ before: 1600, after: 1587 })).toBe("1600 → 1587 (-13)");
  });
});

describe("formatRecord", () => {
  it("lists wins, draws and losses", () => {
    expect(formatRecord({ wins: 12, draws: 3, losses: 5 })).toBe("12 / 3 / 5");
  });
});

describe("ratingIn", () => {
  const ratings: PlayerRatings = {
    CLASSIC: { rating: 1540, games: 4, wins: 3, draws: 0, losses: 1, peak: 1540 },
    QUANTUM: null,
  };

  it("picks the variant and tolerates missing data", () => {
    expect(ratingIn(ratings, "CLASSIC")?.rating).toBe(1540);
    expect(ratingIn(ratings, "QUANTUM")).toBeNull();
    expect(ratingIn(undefined, "CLASSIC")).toBeNull();
    expect(ratingIn(null, "QUANTUM")).toBeNull();
  });
});

describe("endedWithoutRatingChange", () => {
  const resignedAtOnce = { status: "FINISHED", rated: true, ratingChanges: null, result: { winner: "BLACK", reason: "RESIGNATION" } } as const;

  it("notes a rated game whose result did not count", () => {
    expect(endedWithoutRatingChange(resignedAtOnce)).toBe(true);
    expect(endedWithoutRatingChange({ ...resignedAtOnce, result: { winner: null, reason: "DRAW_AGREEMENT" } })).toBe(true);
  });

  it("notes a forfeit by abandonment like a resignation", () => {
    expect(endedWithoutRatingChange({ ...resignedAtOnce, result: { winner: "BLACK", reason: "ABANDONMENT" } })).toBe(true);
    expect(endedWithoutRatingChange({ ...resignedAtOnce, result: { winner: "WHITE", reason: "ABANDONMENT" } })).toBe(true);
  });

  it("stays quiet for casual games, counted results and games still going", () => {
    expect(endedWithoutRatingChange({ ...resignedAtOnce, rated: false })).toBe(false);
    expect(endedWithoutRatingChange({ ...resignedAtOnce, ratingChanges: { white: { before: 1500, after: 1484 }, black: { before: 1500, after: 1516 } } })).toBe(false);
    expect(endedWithoutRatingChange({ ...resignedAtOnce, status: "ACTIVE", result: null })).toBe(false);
  });

  it("stays quiet for a game abandoned without a winner: cancelled, aborted, or stale with a bot on turn", () => {
    expect(endedWithoutRatingChange({ ...resignedAtOnce, result: { winner: null, reason: "ABANDONMENT" } })).toBe(false);
  });
});
