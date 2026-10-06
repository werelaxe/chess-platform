import { describe, expect, it } from "vitest";
import { formatMove, formatProbability, formatResult, formatStatus, movesEqual } from "./notation";

describe("formatMove", () => {
  it("formats normal moves", () => {
    expect(formatMove({ type: "normal", from: "e2", to: "e4" })).toBe("e2-e4");
    expect(formatMove({ type: "normal", from: "e7", to: "e8", promotion: "QUEEN" })).toBe("e7-e8=Q");
    expect(formatMove({ type: "normal", from: "b2", to: "a1", promotion: "KNIGHT" })).toBe("b2-a1=N");
  });

  it("formats split moves", () => {
    expect(formatMove({ type: "split", from: "g1", first: "f3", second: "h3" })).toBe("g1->f3|h3");
    expect(formatMove({ type: "split", from: "e2", first: "e4", second: "e2" })).toBe("e2->e4|stay");
    expect(formatMove({ type: "split", from: "a7", first: "a8", second: "a7", promotion: "ROOK" })).toBe(
      "a7->a8|stay=R",
    );
  });

  it("formats observations", () => {
    expect(formatMove({ type: "observe", square: "e4" })).toBe("observe e4");
    expect(formatMove({ type: "observe", square: "e4", outcome: null })).toBe("observe e4");
    expect(formatMove({ type: "observe", square: "e4", outcome: { piece: { color: "WHITE", type: "PAWN" } } })).toBe(
      "observe e4 = white pawn",
    );
    expect(formatMove({ type: "observe", square: "d5", outcome: { piece: { color: "BLACK", type: "KNIGHT" } } })).toBe(
      "observe d5 = black knight",
    );
    expect(formatMove({ type: "observe", square: "e4", outcome: { piece: null } })).toBe("observe e4 = empty");
  });
});

describe("formatResult and formatStatus", () => {
  it("describes results", () => {
    expect(formatResult("WHITE", "CHECKMATE")).toBe("White wins by checkmate");
    expect(formatResult("BLACK", "KING_CAPTURED")).toBe("Black wins by king captured");
    expect(formatResult(null, "STALEMATE")).toBe("Draw by stalemate");
    expect(formatResult(null, "DRAW_AGREEMENT")).toBe("Draw by agreement");
  });

  it("describes statuses", () => {
    expect(formatStatus({ type: "ongoing" })).toBe("In progress");
    expect(formatStatus({ type: "finished", winner: "BLACK", reason: "RESIGNATION" })).toBe("Black wins by resignation");
  });
});

describe("formatProbability", () => {
  it("rounds to whole percents without hiding near-certain values", () => {
    expect(formatProbability(1)).toBe("100%");
    expect(formatProbability(0.5)).toBe("50%");
    expect(formatProbability(0.333)).toBe("33%");
    expect(formatProbability(0.999)).toBe("99%");
    expect(formatProbability(0.001)).toBe("<1%");
  });
});

describe("movesEqual", () => {
  it("compares moves structurally", () => {
    expect(movesEqual({ type: "normal", from: "e2", to: "e4" }, { type: "normal", from: "e2", to: "e4" })).toBe(true);
    expect(movesEqual({ type: "normal", from: "e2", to: "e4" }, { type: "normal", from: "e2", to: "e3" })).toBe(false);
    expect(
      movesEqual(
        { type: "normal", from: "e7", to: "e8", promotion: "QUEEN" },
        { type: "normal", from: "e7", to: "e8", promotion: "ROOK" },
      ),
    ).toBe(false);
    expect(
      movesEqual({ type: "split", from: "g1", first: "f3", second: "h3" }, { type: "split", from: "g1", first: "f3", second: "h3" }),
    ).toBe(true);
    expect(movesEqual({ type: "split", from: "g1", first: "f3", second: "h3" }, { type: "normal", from: "g1", to: "f3" })).toBe(
      false,
    );
    expect(
      movesEqual(
        { type: "observe", square: "e4", outcome: { piece: { color: "WHITE", type: "PAWN" } } },
        { type: "observe", square: "e4", outcome: { piece: { color: "WHITE", type: "PAWN" } } },
      ),
    ).toBe(true);
    expect(
      movesEqual({ type: "observe", square: "e4", outcome: { piece: null } }, { type: "observe", square: "e4" }),
    ).toBe(true);
    expect(
      movesEqual(
        { type: "observe", square: "e4", outcome: { piece: null } },
        { type: "observe", square: "e4", outcome: { piece: { color: "WHITE", type: "PAWN" } } },
      ),
    ).toBe(false);
  });
});
