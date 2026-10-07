import type { Piece, PieceType } from "../api/types";
import { pieceName } from "../core/notation";

const SYMBOLS: Record<PieceType, string> = {
  PAWN: "p",
  KNIGHT: "n",
  BISHOP: "b",
  ROOK: "r",
  QUEEN: "q",
  KING: "k",
};

/** URL of the cburnett SVG for a piece: "l" (light) is white, "d" (dark) is black. */
function pieceUrl(piece: Piece): string {
  return `/pieces/Chess_${SYMBOLS[piece.type]}${piece.color === "WHITE" ? "l" : "d"}t45.svg`;
}

export function PieceImage({ piece, className, style }: { piece: Piece; className?: string; style?: React.CSSProperties }) {
  return <img className={className} style={style} src={pieceUrl(piece)} alt={pieceName(piece)} draggable={false} />;
}
