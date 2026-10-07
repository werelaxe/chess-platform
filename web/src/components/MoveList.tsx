import { useEffect, useRef } from "react";
import { useTranslation } from "react-i18next";
import type { GameMove } from "../api/types";
import { formatMove } from "../core/notation";
import type { ReplayStep } from "../core/replay";

/** `key` is the keyboard shortcut shown in the tooltip: key cap names, not translated. */
const STEPS: { step: ReplayStep; glyph: string; key: string }[] = [
  { step: "first", glyph: "«", key: "Home" },
  { step: "previous", glyph: "‹", key: "←" },
  { step: "next", glyph: "›", key: "→" },
  { step: "last", glyph: "»", key: "End" },
];

function plyClass(move: GameMove, current: boolean): string {
  const classes = ["moves__ply"];
  if (move.type === "split") classes.push("moves__ply--split");
  if (move.type === "observe") classes.push("moves__ply--observe");
  if (current) classes.push("is-current");
  return classes.join(" ");
}

/** Keeps `element` visible inside the scrolling `list` without moving the rest of the page. */
function revealInList(list: HTMLElement, element: HTMLElement | null) {
  if (!element) {
    list.scrollTop = 0;
    return;
  }
  const bounds = list.getBoundingClientRect();
  const rect = element.getBoundingClientRect();
  if (rect.top < bounds.top) list.scrollTop += rect.top - bounds.top;
  else if (rect.bottom > bounds.bottom) list.scrollTop += rect.bottom - bounds.bottom;
}

/**
 * The move list with history navigation. `shownPly` is the number of plies of the position on
 * the board (0 is the starting position, `moves.length` the live one); the move leading to it is
 * highlighted, and clicking a move shows the position after it.
 */
export function MoveList({
  moves,
  shownPly,
  onStep,
  onSelect,
}: {
  moves: GameMove[];
  shownPly: number;
  onStep: (step: ReplayStep) => void;
  /** Called with the number of plies to show, that is the index of the clicked move plus one. */
  onSelect: (ply: number) => void;
}) {
  const { t } = useTranslation();
  const listRef = useRef<HTMLDivElement>(null);
  const currentRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const list = listRef.current;
    if (list) revealInList(list, currentRef.current);
  }, [shownPly, moves.length]);

  const rows: { number: number; white: GameMove | undefined; black: GameMove | undefined; whitePly: number }[] = [];
  for (let ply = 0; ply < moves.length; ply += 2) {
    rows.push({ number: ply / 2 + 1, white: moves[ply], black: moves[ply + 1], whitePly: ply });
  }

  const atStart = shownPly <= 0;
  const atEnd = shownPly >= moves.length;

  function renderPly(move: GameMove, index: number) {
    const current = index === shownPly - 1;
    return (
      <button
        type="button"
        ref={current ? currentRef : undefined}
        className={plyClass(move, current)}
        title={formatMove(move)}
        aria-current={current ? "true" : undefined}
        onClick={() => onSelect(index + 1)}
      >
        {formatMove(move)}
      </button>
    );
  }

  return (
    <section className="panel moves">
      <div className="moves__head">
        <div className="panel__title">{t("moves.title")}</div>
        <div className="moves__nav" role="group" aria-label={t("moves.navigation")}>
          {STEPS.map((entry) => {
            const disabled = entry.step === "first" || entry.step === "previous" ? atStart : atEnd;
            const label = t(`moves.${entry.step}`);
            return (
              <button
                key={entry.step}
                type="button"
                className="btn btn--small moves__nav-btn"
                aria-label={label}
                title={`${label} (${entry.key})`}
                disabled={disabled}
                onClick={() => onStep(entry.step)}
              >
                {entry.glyph}
              </button>
            );
          })}
        </div>
      </div>
      {moves.length === 0 ? (
        <p className="moves__empty">{t("moves.empty")}</p>
      ) : (
        <div className="moves__list" ref={listRef}>
          {rows.map((row) => (
            <div key={row.number} style={{ display: "contents" }}>
              <span className="moves__num">{row.number}.</span>
              {row.white ? renderPly(row.white, row.whitePly) : <span />}
              {row.black ? renderPly(row.black, row.whitePly + 1) : <span />}
            </div>
          ))}
        </div>
      )}
    </section>
  );
}
