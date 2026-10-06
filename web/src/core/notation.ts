import type { Color, EndReason, GameMove, GameStatus, Piece, PieceType } from "../api/types";

const PROMOTION_LETTERS: Record<PieceType, string> = {
  PAWN: "P",
  KNIGHT: "N",
  BISHOP: "B",
  ROOK: "R",
  QUEEN: "Q",
  KING: "K",
};

const PIECE_NAMES: Record<PieceType, string> = {
  PAWN: "pawn",
  KNIGHT: "knight",
  BISHOP: "bishop",
  ROOK: "rook",
  QUEEN: "queen",
  KING: "king",
};

export function colorName(color: Color): string {
  return color === "WHITE" ? "white" : "black";
}

export function pieceName(piece: Piece): string {
  return `${colorName(piece.color)} ${PIECE_NAMES[piece.type]}`;
}

export function pieceTypeName(type: PieceType): string {
  return PIECE_NAMES[type];
}

/** Human-readable move: "e2-e4", "e7-e8=Q", "g1->f3|h3", "e2->e4|stay", "observe e4 = white pawn". */
export function formatMove(move: GameMove): string {
  switch (move.type) {
    case "normal": {
      const promotion = move.promotion ? `=${PROMOTION_LETTERS[move.promotion]}` : "";
      return `${move.from}-${move.to}${promotion}`;
    }
    case "split": {
      const second = move.second === move.from ? "stay" : move.second;
      const promotion = move.promotion ? `=${PROMOTION_LETTERS[move.promotion]}` : "";
      return `${move.from}->${move.first}|${second}${promotion}`;
    }
    case "observe": {
      if (move.outcome === undefined || move.outcome === null) return `observe ${move.square}`;
      const result = move.outcome.piece ? pieceName(move.outcome.piece) : "empty";
      return `observe ${move.square} = ${result}`;
    }
  }
}

const END_REASONS: Record<EndReason, string> = {
  CHECKMATE: "checkmate",
  STALEMATE: "stalemate",
  INSUFFICIENT_MATERIAL: "insufficient material",
  FIFTY_MOVE_RULE: "fifty-move rule",
  THREEFOLD_REPETITION: "threefold repetition",
  KING_CAPTURED: "king captured",
  RESIGNATION: "resignation",
  DRAW_AGREEMENT: "agreement",
  ABANDONMENT: "abandonment",
};

export function endReasonName(reason: EndReason): string {
  return END_REASONS[reason];
}

/** Result line such as "White wins by checkmate" or "Draw by stalemate". */
export function formatResult(winner: Color | null, reason: EndReason): string {
  const why = END_REASONS[reason];
  if (winner === null) return `Draw by ${why}`;
  return `${winner === "WHITE" ? "White" : "Black"} wins by ${why}`;
}

export function formatStatus(status: GameStatus): string {
  return status.type === "ongoing" ? "In progress" : formatResult(status.winner, status.reason);
}

export function formatProbability(probability: number): string {
  const percent = probability * 100;
  if (percent >= 99.5 && percent < 100) return "99%";
  if (percent > 0 && percent < 0.5) return "<1%";
  return `${Math.round(percent)}%`;
}

export function movesEqual(a: GameMove, b: GameMove): boolean {
  if (a.type !== b.type) return false;
  switch (a.type) {
    case "normal": {
      const other = b as typeof a;
      return a.from === other.from && a.to === other.to && (a.promotion ?? null) === (other.promotion ?? null);
    }
    case "split": {
      const other = b as typeof a;
      return (
        a.from === other.from &&
        a.first === other.first &&
        a.second === other.second &&
        (a.promotion ?? null) === (other.promotion ?? null)
      );
    }
    case "observe": {
      const other = b as typeof a;
      if (a.square !== other.square) return false;
      const left = a.outcome?.piece ?? null;
      const right = other.outcome?.piece ?? null;
      if (left === null || right === null) return left === right;
      return left.color === right.color && left.type === right.type;
    }
  }
}
