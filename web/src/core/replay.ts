import type { GameKind, GameMove } from "../api/types";
import { LocalGame, type GameSnapshot } from "./game";

/** A step through the move history, as offered by the buttons next to the move list. */
export type ReplayStep = "first" | "previous" | "next" | "last";

/**
 * Position shown on the board: the number of plies applied (0 is the starting position), or
 * `null` for the live position, which keeps following the game as moves arrive.
 */
export type ViewedPly = number | null;

/** Keeps a ply within the positions of a game with `total` moves: 0 to `total` inclusive. */
export function clampPly(ply: number, total: number): number {
  const last = Math.max(0, total);
  if (!Number.isFinite(ply)) return last;
  return Math.min(Math.max(0, Math.trunc(ply)), last);
}

/** The ply actually shown for a stored position, once the move list has `total` moves. */
export function resolvePly(viewed: ViewedPly, total: number): number {
  return viewed === null ? Math.max(0, total) : clampPly(viewed, total);
}

/** The position to store for a chosen ply: the last ply is the live position, not a frozen one. */
export function selectPly(ply: number, total: number): ViewedPly {
  const clamped = clampPly(ply, total);
  return clamped >= total ? null : clamped;
}

/** The position reached from `viewed` with a navigation step. */
export function stepPly(viewed: ViewedPly, total: number, step: ReplayStep): ViewedPly {
  const current = resolvePly(viewed, total);
  switch (step) {
    case "first":
      return selectPly(0, total);
    case "previous":
      return selectPly(current - 1, total);
    case "next":
      return selectPly(current + 1, total);
    case "last":
      return null;
  }
}

/** The navigation step bound to a keyboard key on the game page, if any. */
export function replayStepForKey(key: string): ReplayStep | null {
  switch (key) {
    case "ArrowLeft":
      return "previous";
    case "ArrowRight":
      return "next";
    case "Home":
      return "first";
    case "End":
      return "last";
    default:
      return null;
  }
}

/**
 * Positions of a game at every ply, replayed on demand from its move list and kept for later
 * visits. Build a new cache whenever the move list changes.
 */
export class PositionCache {
  private readonly snapshots = new Map<number, GameSnapshot>();

  constructor(
    private readonly kind: GameKind,
    private readonly moves: readonly GameMove[],
  ) {}

  get total(): number {
    return this.moves.length;
  }

  /** Snapshot after the first `ply` moves; throws IllegalMoveError when that prefix does not replay. */
  at(ply: number): GameSnapshot {
    const count = clampPly(ply, this.moves.length);
    let snapshot = this.snapshots.get(count);
    if (!snapshot) {
      snapshot = LocalGame.replay(this.kind, this.moves.slice(0, count)).snapshot();
      this.snapshots.set(count, snapshot);
    }
    return snapshot;
  }
}
