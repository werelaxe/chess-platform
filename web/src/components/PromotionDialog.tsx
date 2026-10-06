import type { Color, PieceType } from "../api/types";
import { pieceTypeName } from "../core/notation";
import { PieceImage } from "./Piece";

const PROMOTIONS: PieceType[] = ["QUEEN", "ROOK", "BISHOP", "KNIGHT"];

export function PromotionDialog({
  color,
  onPick,
  onCancel,
}: {
  color: Color;
  onPick: (type: PieceType) => void;
  onCancel: () => void;
}) {
  return (
    <div className="promo" role="dialog" aria-modal="true" aria-label="Choose a promotion piece">
      <div className="promo__dialog">
        <div className="promo__title">Promote to</div>
        <div className="promo__list">
          {PROMOTIONS.map((type) => (
            <button
              key={type}
              type="button"
              className="promo__option"
              onClick={() => onPick(type)}
              aria-label={pieceTypeName(type)}
              title={pieceTypeName(type)}
            >
              <PieceImage piece={{ color, type }} />
            </button>
          ))}
        </div>
        <button type="button" className="btn btn--ghost btn--small promo__cancel" onClick={onCancel}>
          Cancel
        </button>
      </div>
    </div>
  );
}
