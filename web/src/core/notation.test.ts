import { afterEach, describe, expect, it } from "vitest";
import type { Piece } from "../api/types";
import { i18n } from "../i18n";
import ru from "../i18n/ru.json";
import { cellDistribution, describeCell, formatMove, formatProbability, formatResult, formatStatus, movesEqual } from "./notation";

const WHITE_PAWN: Piece = { color: "WHITE", type: "PAWN" };
const BLACK_KNIGHT: Piece = { color: "BLACK", type: "KNIGHT" };

// The expectations below are the English resources; the default language in tests is English.
afterEach(() => i18n.changeLanguage("en"));

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

  it("uses the words of the UI language", async () => {
    await i18n.changeLanguage("ru");
    expect(formatMove({ type: "normal", from: "e2", to: "e4" })).toBe("e2-e4");
    expect(formatMove({ type: "split", from: "e2", first: "e4", second: "e2" })).toBe(`e2->e4|${ru.notation.stay}`);
    expect(formatMove({ type: "observe", square: "e4", outcome: { piece: WHITE_PAWN } })).toBe(
      `${ru.notation.observe} e4 = ${ru.pieces.WHITE.PAWN}`,
    );
    expect(formatMove({ type: "observe", square: "e4", outcome: { piece: null } })).toBe(
      `${ru.notation.observe} e4 = ${ru.notation.empty}`,
    );
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

  it("follows the UI language", async () => {
    await i18n.changeLanguage("ru");
    expect(formatResult(null, "STALEMATE")).toBe(ru.notation.draw.replace("{{reason}}", ru.notation.reasons.STALEMATE));
    expect(formatResult("WHITE", "CHECKMATE")).toBe(ru.notation.whiteWins.replace("{{reason}}", ru.notation.reasons.CHECKMATE));
    expect(formatStatus({ type: "ongoing" })).toBe(ru.notation.inProgress);
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

describe("cellDistribution", () => {
  it("adds the implicit empty share only when the entries do not sum to one", () => {
    expect(cellDistribution({ square: "e4", entries: [] })).toEqual([{ piece: null, probability: 1 }]);
    expect(cellDistribution({ square: "e4", entries: [{ piece: WHITE_PAWN, probability: 1 }] })).toEqual([
      { piece: WHITE_PAWN, probability: 1 },
    ]);
    expect(cellDistribution({ square: "e4", entries: [{ piece: WHITE_PAWN, probability: 0.25 }] })).toEqual([
      { piece: WHITE_PAWN, probability: 0.25 },
      { piece: null, probability: 0.75 },
    ]);
    const explicit = [
      { piece: WHITE_PAWN, probability: 0.5 },
      { piece: null, probability: 0.5 },
    ];
    expect(cellDistribution({ square: "e4", entries: explicit })).toBe(explicit);
  });
});

describe("describeCell", () => {
  it("names the square and every possible content with its probability", () => {
    expect(describeCell({ square: "e4", entries: [] })).toBe("e4: empty");
    expect(describeCell({ square: "e4", entries: [{ piece: WHITE_PAWN, probability: 1 }] })).toBe("e4: white pawn");
    expect(
      describeCell({
        square: "d5",
        entries: [
          { piece: WHITE_PAWN, probability: 0.5 },
          { piece: BLACK_KNIGHT, probability: 0.25 },
        ],
      }),
    ).toBe("d5: white pawn 50%, black knight 25%, empty 25%");
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
