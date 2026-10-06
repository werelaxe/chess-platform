import { useEffect, useRef } from "react";
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
  const firstOption = useRef<HTMLButtonElement>(null);

  // Move focus into the dialog and hand it back to the square that opened it when it closes.
  useEffect(() => {
    const previous = document.activeElement;
    firstOption.current?.focus();
    return () => {
      if (previous instanceof HTMLElement && previous.isConnected) previous.focus();
    };
  }, []);

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.key !== "Escape") return;
      event.preventDefault();
      onCancel();
    }
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [onCancel]);

  return (
    <div className="promo" role="dialog" aria-modal="true" aria-label="Choose a promotion piece">
      <div className="promo__dialog">
        <div className="promo__title">Promote to</div>
        <div className="promo__list">
          {PROMOTIONS.map((type, position) => (
            <button
              key={type}
              ref={position === 0 ? firstOption : undefined}
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
