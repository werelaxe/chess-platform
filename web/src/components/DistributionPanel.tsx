import type { CellView } from "../api/types";
import { formatProbability, pieceName } from "../core/notation";
import { PieceImage } from "./Piece";

interface Row {
  key: string;
  label: string;
  probability: number;
  piece: CellView["entries"][number]["piece"];
}

function rows(cell: CellView): Row[] {
  const result: Row[] = [];
  let emptyShown = false;
  for (const entry of cell.entries) {
    if (entry.piece) {
      result.push({
        key: `${entry.piece.color}-${entry.piece.type}`,
        label: pieceName(entry.piece),
        probability: entry.probability,
        piece: entry.piece,
      });
    } else {
      emptyShown = true;
      result.push({ key: "empty", label: "empty", probability: entry.probability, piece: null });
    }
  }
  if (cell.entries.length === 0) result.push({ key: "empty", label: "empty", probability: 1, piece: null });
  else if (!emptyShown) {
    const total = cell.entries.reduce((sum, entry) => sum + entry.probability, 0);
    if (total < 0.9995) result.push({ key: "empty", label: "empty", probability: 1 - total, piece: null });
  }
  return result;
}

/** Full distribution of the hovered or tapped square, including the chance that it is empty. */
export function DistributionPanel({ cell, quantum }: { cell: CellView | null; quantum: boolean }) {
  return (
    <section className="panel dist" aria-live="polite">
      <div className="panel__title">Square</div>
      {cell ? (
        <>
          <div className="dist__square mono">{cell.square}</div>
          <div className="dist__rows">
            {rows(cell).map((row) => (
              <div className="dist__row" key={row.key}>
                <div className="dist__icon">
                  {row.piece ? <PieceImage piece={row.piece} /> : <span className="dist__icon--empty" aria-hidden="true" />}
                </div>
                <div>
                  <div className="dist__label">{row.label}</div>
                  <div className="dist__bar">
                    <div
                      className={`dist__bar-fill${row.piece ? "" : " dist__bar-fill--empty"}`}
                      style={{ width: `${Math.max(1, row.probability * 100)}%` }}
                    />
                  </div>
                </div>
                <div className={`dist__pct${row.piece ? "" : " dist__pct--empty"}`}>{formatProbability(row.probability)}</div>
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
