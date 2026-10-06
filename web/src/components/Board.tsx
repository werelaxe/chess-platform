import { useMemo, type ReactNode } from "react";
import type { BoardView, CellView } from "../api/types";
import { squareAt } from "../core/squares";
import { Cell } from "./Cell";

export type BoardMode = "normal" | "split" | "observe";

export interface BoardHighlights {
  selected: number | null;
  targets: ReadonlySet<number>;
  /** Chosen first target of a split. */
  first: number | null;
  /** Origin square offered as the "stay" option of a split. */
  stay: number | null;
  observable: ReadonlySet<number>;
  lastMove: ReadonlySet<number>;
  inspected: number | null;
}

export interface BoardProps {
  view: BoardView;
  flipped: boolean;
  mode: BoardMode;
  highlights: BoardHighlights;
  interactive: boolean;
  busy: boolean;
  onCellClick: (index: number) => void;
  onCellHover: (index: number | null) => void;
  /** Overlays such as the promotion picker. */
  children?: ReactNode;
}

function hasKingOf(cell: CellView, color: BoardView["sideToMove"]): boolean {
  return cell.entries.some((entry) => entry.piece?.type === "KING" && entry.piece.color === color);
}

export function Board(props: BoardProps) {
  const { view, flipped, highlights } = props;

  // Render order: white at the bottom unless flipped.
  const order = useMemo(() => {
    const indices: number[] = [];
    for (let row = 0; row < 8; row += 1) {
      const rank = flipped ? row : 7 - row;
      for (let column = 0; column < 8; column += 1) {
        const file = flipped ? 7 - column : column;
        indices.push(squareAt(file, rank));
      }
    }
    return indices;
  }, [flipped]);

  const bottomRank = flipped ? 7 : 0;
  const leftFile = flipped ? 7 : 0;
  const checkAlpha = Math.min(1, Math.max(0, view.checkProbability));

  const classes = ["board", `board--${props.mode}`];
  if (props.busy) classes.push("board--busy");

  return (
    <div className="board-wrap">
      <div className={classes.join(" ")} role="grid" aria-label="Chess board">
        {order.map((index) => {
          const cell = view.cells[index];
          if (!cell) return null;
          return (
            <Cell
              key={index}
              index={index}
              cell={cell}
              showFile={(index >> 3) === bottomRank}
              showRank={(index & 7) === leftFile}
              selected={highlights.selected === index}
              lastMove={highlights.lastMove.has(index)}
              target={highlights.targets.has(index)}
              first={highlights.first === index}
              stay={highlights.stay === index}
              observable={highlights.observable.has(index)}
              inspected={highlights.inspected === index}
              clickable={props.interactive}
              checkAlpha={checkAlpha > 0 && hasKingOf(cell, view.sideToMove) ? checkAlpha : 0}
              onClick={props.onCellClick}
              onHover={props.onCellHover}
            />
          );
        })}
      </div>
      {props.children}
    </div>
  );
}
