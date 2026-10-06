import { memo } from "react";
import type { CellEntry, CellView, Piece } from "../api/types";
import { formatProbability } from "../core/notation";
import { FILES, fileOf, isLightSquare, rankOf } from "../core/squares";
import { PieceImage } from "./Piece";

export interface CellProps {
  index: number;
  cell: CellView;
  showFile: boolean;
  showRank: boolean;
  selected: boolean;
  lastMove: boolean;
  target: boolean;
  first: boolean;
  stay: boolean;
  observable: boolean;
  inspected: boolean;
  clickable: boolean;
  /** Opacity of the red check tint, 0..1. */
  checkAlpha: number;
  onClick: (index: number) => void;
  onHover: (index: number | null) => void;
}

interface PieceEntry {
  piece: Piece;
  probability: number;
}

const RING_RADIUS = 46;
const RING_LENGTH = 2 * Math.PI * RING_RADIUS;

function pieceEntries(entries: CellEntry[]): PieceEntry[] {
  const result: PieceEntry[] = [];
  for (const entry of entries) if (entry.piece) result.push({ piece: entry.piece, probability: entry.probability });
  return result;
}

function SinglePiece({ entry }: { entry: PieceEntry }) {
  const certain = entry.probability >= 1;
  const opacity = certain ? 1 : 0.4 + 0.6 * entry.probability;
  return (
    <div className="piece">
      <PieceImage piece={entry.piece} className="piece__img" style={certain ? undefined : { opacity }} />
      {certain ? null : (
        <>
          <svg className="piece__ring" viewBox="0 0 100 100" aria-hidden="true">
            <circle className="piece__ring-track" cx="50" cy="50" r={RING_RADIUS} />
            <circle
              className="piece__ring-fill"
              cx="50"
              cy="50"
              r={RING_RADIUS}
              strokeDasharray={RING_LENGTH}
              strokeDashoffset={RING_LENGTH * (1 - entry.probability)}
            />
          </svg>
          <span className="piece__badge">{formatProbability(entry.probability)}</span>
        </>
      )}
    </div>
  );
}

function MiniGrid({ entries }: { entries: PieceEntry[] }) {
  const shown = entries.length > 4 ? entries.slice(0, 3) : entries.slice(0, 4);
  const hidden = entries.length - shown.length;
  return (
    <div className="cell__mini">
      {shown.map((entry) => (
        <div className="mini" key={`${entry.piece.color}-${entry.piece.type}`}>
          <PieceImage piece={entry.piece} className="mini__img" style={{ opacity: 0.4 + 0.6 * entry.probability }} />
          <span className="mini__badge">{formatProbability(entry.probability)}</span>
        </div>
      ))}
      {hidden > 0 ? <div className="mini mini--more">+{hidden}</div> : null}
    </div>
  );
}

function CellComponent(props: CellProps) {
  const { index, cell, onClick, onHover } = props;
  const pieces = pieceEntries(cell.entries);
  const occupied = pieces.length > 0;

  const classes = ["cell", isLightSquare(index) ? "cell--light" : "cell--dark"];
  if (props.clickable) classes.push("cell--clickable");
  if (props.selected) classes.push("cell--selected");
  if (props.lastMove) classes.push("cell--last");
  if (props.first) classes.push("cell--first");
  if (props.target) {
    classes.push("cell--target");
    if (occupied) classes.push("cell--capture");
  }
  if (props.stay) classes.push("cell--stay");
  if (props.observable) classes.push("cell--observable");
  if (props.inspected) classes.push("cell--inspected");

  return (
    <div
      className={classes.join(" ")}
      data-square={cell.square}
      role={props.clickable ? "button" : undefined}
      tabIndex={props.clickable ? 0 : undefined}
      aria-label={cell.square}
      onClick={() => onClick(index)}
      onKeyDown={(event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          onClick(index);
        }
      }}
      onMouseEnter={() => onHover(index)}
      onMouseLeave={() => onHover(null)}
    >
      {props.checkAlpha > 0 ? (
        <div className="cell__layer cell__check" style={{ "--check": props.checkAlpha } as React.CSSProperties} />
      ) : null}
      {props.showRank ? <span className="cell__coord cell__coord--rank">{rankOf(index) + 1}</span> : null}
      {props.showFile ? <span className="cell__coord cell__coord--file">{FILES[fileOf(index)]}</span> : null}
      {pieces.length === 1 && pieces[0] ? <SinglePiece entry={pieces[0]} /> : null}
      {pieces.length > 1 ? <MiniGrid entries={pieces} /> : null}
    </div>
  );
}

export const Cell = memo(CellComponent);
