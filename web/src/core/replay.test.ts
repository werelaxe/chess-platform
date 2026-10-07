import { describe, expect, it } from "vitest";
import type { GameMove } from "../api/types";
import { IllegalMoveError, LocalGame } from "./game";
import { PositionCache, clampPly, replayStepForKey, resolvePly, selectPly, stepPly } from "./replay";

describe("clampPly", () => {
  it("keeps a ply between the starting and the live position", () => {
    expect(clampPly(-1, 5)).toBe(0);
    expect(clampPly(0, 5)).toBe(0);
    expect(clampPly(3, 5)).toBe(3);
    expect(clampPly(5, 5)).toBe(5);
    expect(clampPly(9, 5)).toBe(5);
  });

  it("handles an empty game and odd numbers", () => {
    expect(clampPly(3, 0)).toBe(0);
    expect(clampPly(2.7, 5)).toBe(2);
    expect(clampPly(Number.NaN, 5)).toBe(5);
    expect(clampPly(4, -2)).toBe(0);
  });
});

describe("resolvePly", () => {
  it("shows the last ply for the live position", () => {
    expect(resolvePly(null, 0)).toBe(0);
    expect(resolvePly(null, 7)).toBe(7);
  });

  it("clamps a stored ply once the move list shrank", () => {
    expect(resolvePly(3, 7)).toBe(3);
    expect(resolvePly(9, 7)).toBe(7);
  });
});

describe("selectPly", () => {
  it("stores an earlier ply and turns the last one into the live position", () => {
    expect(selectPly(0, 4)).toBe(0);
    expect(selectPly(3, 4)).toBe(3);
    expect(selectPly(4, 4)).toBeNull();
    expect(selectPly(12, 4)).toBeNull();
    expect(selectPly(-3, 4)).toBe(0);
  });

  it("is always live for a game without moves", () => {
    expect(selectPly(0, 0)).toBeNull();
  });
});

describe("stepPly", () => {
  it("steps backwards from the live position", () => {
    expect(stepPly(null, 4, "previous")).toBe(3);
    expect(stepPly(3, 4, "previous")).toBe(2);
    expect(stepPly(0, 4, "previous")).toBe(0);
  });

  it("steps forwards and returns to the live position at the end", () => {
    expect(stepPly(0, 4, "next")).toBe(1);
    expect(stepPly(3, 4, "next")).toBeNull();
    expect(stepPly(null, 4, "next")).toBeNull();
  });

  it("jumps to both ends", () => {
    expect(stepPly(null, 4, "first")).toBe(0);
    expect(stepPly(2, 4, "first")).toBe(0);
    expect(stepPly(2, 4, "last")).toBeNull();
    expect(stepPly(null, 4, "last")).toBeNull();
  });

  it("keeps a stored ply while the move list grows", () => {
    expect(resolvePly(2, 4)).toBe(2);
    expect(resolvePly(2, 6)).toBe(2);
    expect(stepPly(2, 6, "next")).toBe(3);
    expect(stepPly(2, 6, "last")).toBeNull();
  });

  it("stays live in a game without moves", () => {
    expect(stepPly(null, 0, "first")).toBeNull();
    expect(stepPly(null, 0, "previous")).toBeNull();
    expect(stepPly(null, 0, "next")).toBeNull();
  });
});

describe("replayStepForKey", () => {
  it("maps the navigation keys and ignores the rest", () => {
    expect(replayStepForKey("ArrowLeft")).toBe("previous");
    expect(replayStepForKey("ArrowRight")).toBe("next");
    expect(replayStepForKey("Home")).toBe("first");
    expect(replayStepForKey("End")).toBe("last");
    expect(replayStepForKey("ArrowUp")).toBeNull();
    expect(replayStepForKey("a")).toBeNull();
    expect(replayStepForKey("Enter")).toBeNull();
  });
});

describe("PositionCache", () => {
  const moves: GameMove[] = [
    { type: "normal", from: "e2", to: "e4" },
    { type: "normal", from: "e7", to: "e5" },
    { type: "normal", from: "g1", to: "f3" },
  ];

  it("replays the position after a given number of plies", () => {
    const cache = new PositionCache("CLASSIC", moves);
    expect(cache.total).toBe(3);
    expect(cache.at(0).moveCount).toBe(0);
    expect(cache.at(0).view.lastMove).toBeNull();
    expect(cache.at(0).sideToMove).toBe("WHITE");
    const after = cache.at(2);
    expect(after.moveCount).toBe(2);
    expect(after.history).toEqual(moves.slice(0, 2));
    expect(after.view.lastMove).toEqual(moves[1]);
    expect(after.sideToMove).toBe("WHITE");
    expect(after.view).toEqual(LocalGame.replay("CLASSIC", moves.slice(0, 2)).view());
  });

  it("returns the same snapshot on a second visit and clamps the ply", () => {
    const cache = new PositionCache("CLASSIC", moves);
    expect(cache.at(1)).toBe(cache.at(1));
    expect(cache.at(7)).toBe(cache.at(3));
    expect(cache.at(-1)).toBe(cache.at(0));
    expect(cache.at(3).history).toEqual(moves);
  });

  it("replays quantum histories including observation outcomes", () => {
    const quantum: GameMove[] = [
      { type: "split", from: "g1", first: "f3", second: "h3" },
      { type: "normal", from: "e7", to: "e5" },
    ];
    const cache = new PositionCache("QUANTUM", quantum);
    expect(cache.at(1).universeCount).toBe(2);
    expect(cache.at(2).universeCount).toBe(2);
    expect(cache.at(0).universeCount).toBe(1);
  });

  it("rejects a history that does not replay", () => {
    const cache = new PositionCache("CLASSIC", [{ type: "normal", from: "e2", to: "e5" }]);
    expect(() => cache.at(1)).toThrow(IllegalMoveError);
    expect(cache.at(0).moveCount).toBe(0);
  });
});
