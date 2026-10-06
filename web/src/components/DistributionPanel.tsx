import type { CellView } from "../api/types";
import { cellDistribution, formatProbability, pieceName } from "../core/notation";
import { PieceImage } from "./Piece";

/**
 * Full distribution of the inspected square, including the chance that it is empty. The region is
 * live only while a square is pinned: hover-driven updates would be announced for every square passed.
 */
export function DistributionPanel({ cell, quantum, pinned }: { cell: CellView | null; quantum: boolean; pinned: boolean }) {
  return (
    <section className="panel dist" aria-live={pinned ? "polite" : undefined}>
      <div className="panel__title">Square</div>
      {cell ? (
        <>
          <div className="dist__square mono">{cell.square}</div>
          <div className="dist__rows">
            {cellDistribution(cell).map((entry) => (
              <div className="dist__row" key={entry.piece ? `${entry.piece.color}-${entry.piece.type}` : "empty"}>
                <div className="dist__icon">
                  {entry.piece ? <PieceImage piece={entry.piece} /> : <span className="dist__icon--empty" aria-hidden="true" />}
                </div>
                <div>
                  <div className="dist__label">{entry.piece ? pieceName(entry.piece) : "empty"}</div>
                  <div className="dist__bar">
                    <div
                      className={`dist__bar-fill${entry.piece ? "" : " dist__bar-fill--empty"}`}
                      style={{ width: `${Math.max(1, entry.probability * 100)}%` }}
                    />
                  </div>
                </div>
                <div className={`dist__pct${entry.piece ? "" : " dist__pct--empty"}`}>{formatProbability(entry.probability)}</div>
              </div>
            ))}
          </div>
        </>
      ) : (
        <p className="dist__placeholder">
          {quantum
            ? "Hover or tap a square to see every possible content with its probability."
            : "Hover or tap a square to see what stands on it."}
        </p>
      )}
    </section>
  );
}
