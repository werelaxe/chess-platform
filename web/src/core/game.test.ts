import { describe, expect, it } from "vitest";
import type { GameMove } from "../api/types";
import { IllegalMoveError, LocalGame } from "./game";
import { squareIndex, squareName } from "./squares";

const sq = squareIndex;

function names(indices: number[]): string[] {
  return indices.map(squareName).sort();
}

describe("LocalGame (classic)", () => {
  it("starts with white to move and no promotion from e2", () => {
    const game = LocalGame.create("CLASSIC");
    expect(game.kind).toBe("CLASSIC");
    expect(game.sideToMove()).toBe("WHITE");
    expect(game.moveCount()).toBe(0);
    expect(game.universeCount()).toBe(1);
    expect(game.isOver()).toBe(false);
    expect(game.status()).toEqual({ type: "ongoing" });
    expect(game.requiresPromotion(sq("e2"), sq("e4"))).toBe(false);
  });

  it("lists the legal targets of e2", () => {
    const game = LocalGame.create("CLASSIC");
    expect(names(game.legalTargets(sq("e2")))).toEqual(["e3", "e4"]);
    expect(game.legalTargets(sq("e1"))).toEqual([]);
    expect(game.legalTargets(sq("e7"))).toEqual([]);
  });

  it("replays a few normal moves", () => {
    const moves: GameMove[] = [
      { type: "normal", from: "e2", to: "e4" },
      { type: "normal", from: "e7", to: "e5" },
      { type: "normal", from: "g1", to: "f3" },
    ];
    const game = LocalGame.replay("CLASSIC", moves);
    expect(game.moveCount()).toBe(3);
    expect(game.sideToMove()).toBe("BLACK");
    expect(game.history()).toEqual(moves);

    const view = game.view();
    expect(view.sideToMove).toBe("BLACK");
    expect(view.universeCount).toBe(1);
    expect(view.checkProbability).toBe(0);
    expect(view.lastMove).toEqual(moves[2]);
    expect(view.cells).toHaveLength(64);
    expect(view.cells[sq("e4")]?.entries).toEqual([{ piece: { color: "WHITE", type: "PAWN" }, probability: 1 }]);
    expect(view.cells[sq("e2")]?.entries).toEqual([]);
    expect(view.cells[sq("f3")]?.entries[0]?.piece).toEqual({ color: "WHITE", type: "KNIGHT" });
  });

  it("rejects illegal moves and histories", () => {
    const game = LocalGame.create("CLASSIC");
    expect(game.isLegal({ type: "normal", from: "e2", to: "e5" })).toBe(false);
    expect(() => game.apply({ type: "normal", from: "e2", to: "e5" })).toThrow(IllegalMoveError);
    expect(game.moveCount()).toBe(0);
    expect(() => LocalGame.replay("CLASSIC", [{ type: "normal", from: "e2", to: "e5" }])).toThrow(IllegalMoveError);
    expect(() => LocalGame.replay("CLASSIC", [{ type: "split", from: "g1", first: "f3", second: "h3" }])).toThrow(
      IllegalMoveError,
    );
  });

  it("detects promotion and applies it", () => {
    const game = LocalGame.replay("CLASSIC", [
      { type: "normal", from: "a2", to: "a4" },
      { type: "normal", from: "b7", to: "b5" },
      { type: "normal", from: "a4", to: "b5" },
      { type: "normal", from: "b8", to: "c6" },
      { type: "normal", from: "b5", to: "b6" },
      { type: "normal", from: "c6", to: "d4" },
      { type: "normal", from: "b6", to: "b7" },
      { type: "normal", from: "d4", to: "e6" },
    ]);
    expect(game.requiresPromotion(sq("b7"), sq("a8"))).toBe(true);
    expect(game.requiresPromotion(sq("b7"), sq("c8"))).toBe(true);
    expect(names(game.legalTargets(sq("b7")))).toEqual(["a8", "b8", "c8"]);
    game.apply({ type: "normal", from: "b7", to: "a8", promotion: "QUEEN" });
    expect(game.view().cells[sq("a8")]?.entries[0]?.piece).toEqual({ color: "WHITE", type: "QUEEN" });
  });
});

describe("LocalGame (quantum)", () => {
  it("offers split second targets including staying", () => {
    const game = LocalGame.create("QUANTUM");
    expect(names(game.legalTargets(sq("g1")))).toEqual(["f3", "h3"]);
    expect(names(game.splitSecondTargets(sq("g1"), sq("f3")))).toEqual(["g1", "h3"]);
    expect(names(game.splitSecondTargets(sq("e2"), sq("e4")))).toEqual(["e2", "e3"]);
    // An illegal first target yields no second targets.
    expect(game.splitSecondTargets(sq("g1"), sq("g3"))).toEqual([]);
    expect(game.canObserve(sq("e2"))).toBe(false);
  });

  it("replays split and observe moves", () => {
    const moves: GameMove[] = [
      { type: "split", from: "g1", first: "f3", second: "h3" },
      { type: "normal", from: "e7", to: "e5" },
      { type: "split", from: "e2", first: "e4", second: "e2" },
    ];
    const game = LocalGame.replay("QUANTUM", moves);
    expect(game.kind).toBe("QUANTUM");
    expect(game.moveCount()).toBe(3);
    expect(game.universeCount()).toBe(4);
    expect(game.sideToMove()).toBe("BLACK");

    const view = game.view();
    const f3 = view.cells[sq("f3")]?.entries ?? [];
    expect(f3).toHaveLength(2);
    expect(f3[0]).toEqual({ piece: { color: "WHITE", type: "KNIGHT" }, probability: 0.5 });
    expect(f3[1]).toEqual({ piece: null, probability: 0.5 });
    expect(view.cells[sq("e2")]?.entries[0]?.probability).toBe(0.5);
    expect(game.canObserve(sq("f3"))).toBe(true);
    expect(game.canObserve(sq("e2"))).toBe(true);

    // A proposed observation without an outcome is not applicable locally.
    expect(game.isLegal({ type: "observe", square: "f3" })).toBe(false);
    expect(() => game.apply({ type: "observe", square: "f3" })).toThrow(IllegalMoveError);

    const observed: GameMove = {
      type: "observe",
      square: "f3",
      outcome: { piece: { color: "WHITE", type: "KNIGHT" } },
    };
    expect(game.isLegal(observed)).toBe(true);
    game.apply(observed);
    expect(game.universeCount()).toBe(2);
    expect(game.sideToMove()).toBe("WHITE");
    expect(game.view().cells[sq("f3")]?.entries).toEqual([{ piece: { color: "WHITE", type: "KNIGHT" }, probability: 1 }]);
    expect(game.view().cells[sq("h3")]?.entries).toEqual([]);
    expect(game.history()).toHaveLength(4);
    expect(game.history()[3]).toEqual(observed);
  });

  it("exposes a consistent snapshot", () => {
    const game = LocalGame.replay("QUANTUM", [{ type: "split", from: "b1", first: "a3", second: "c3" }]);
    const snapshot = game.snapshot();
    expect(snapshot.kind).toBe("QUANTUM");
    expect(snapshot.moveCount).toBe(1);
    expect(snapshot.sideToMove).toBe("BLACK");
    expect(snapshot.universeCount).toBe(2);
    expect(snapshot.status.type).toBe("ongoing");
    expect(snapshot.view.lastMove).toEqual({ type: "split", from: "b1", first: "a3", second: "c3" });
    expect(snapshot.history).toHaveLength(1);
  });
});
