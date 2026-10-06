import { JsGame, replayGame } from "chess-platform-core";
import type { BoardView, Color, GameKind, GameMove, GameStatus } from "../api/types";

/** Thrown when the core rejects a move or a history. */
export class IllegalMoveError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "IllegalMoveError";
  }
}

function rethrow(error: unknown): never {
  const message = error instanceof Error ? error.message : String(error);
  throw new IllegalMoveError(message);
}

/** An immutable picture of the local game after a given number of plies. */
export interface GameSnapshot {
  kind: GameKind;
  moveCount: number;
  sideToMove: Color;
  universeCount: number;
  status: GameStatus;
  view: BoardView;
  history: GameMove[];
}

/**
 * Typed wrapper over the core's JavaScript facade. The facade exchanges JSON strings; this
 * class parses them into the types of `src/api/types.ts` and converts square arrays.
 */
export class LocalGame {
  private constructor(private readonly js: JsGame) {}

  static create(kind: GameKind): LocalGame {
    return new LocalGame(new JsGame(kind));
  }

  /** Rebuilds a game from its move list; throws IllegalMoveError when the history is invalid. */
  static replay(kind: GameKind, moves: readonly GameMove[]): LocalGame {
    try {
      return new LocalGame(replayGame(kind, JSON.stringify(moves)));
    } catch (error) {
      rethrow(error);
    }
  }

  get kind(): GameKind {
    return this.js.kind as GameKind;
  }

  sideToMove(): Color {
    return this.js.sideToMove() as Color;
  }

  moveCount(): number {
    return this.js.moveCount();
  }

  universeCount(): number {
    return this.js.universeCount();
  }

  isOver(): boolean {
    return this.js.isOver();
  }

  status(): GameStatus {
    return JSON.parse(this.js.statusJson()) as GameStatus;
  }

  view(): BoardView {
    return JSON.parse(this.js.viewJson()) as BoardView;
  }

  history(): GameMove[] {
    return JSON.parse(this.js.historyJson()) as GameMove[];
  }

  /** Square indices the piece on `from` may move to with a normal move. */
  legalTargets(from: number): number[] {
    return Array.from(this.js.legalTargets(from));
  }

  /** Second targets of a split once `first` is chosen; includes `from` itself ("stay"). */
  splitSecondTargets(from: number, first: number): number[] {
    return Array.from(this.js.splitSecondTargets(from, first));
  }

  requiresPromotion(from: number, to: number): boolean {
    return this.js.requiresPromotion(from, to);
  }

  canObserve(square: number): boolean {
    return this.js.canObserve(square);
  }

  isLegal(move: GameMove): boolean {
    return this.js.isLegalJson(JSON.stringify(move));
  }

  /** Applies a move; throws IllegalMoveError when the core rejects it. */
  apply(move: GameMove): void {
    try {
      this.js.applyJson(JSON.stringify(move));
    } catch (error) {
      rethrow(error);
    }
  }

  snapshot(): GameSnapshot {
    return {
      kind: this.kind,
      moveCount: this.moveCount(),
      sideToMove: this.sideToMove(),
      universeCount: this.universeCount(),
      status: this.status(),
      view: this.view(),
      history: this.history(),
    };
  }
}
