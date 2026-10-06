import { useEffect, useRef } from "react";
import type { GameMove } from "../api/types";
import { formatMove } from "../core/notation";

function plyClass(move: GameMove, current: boolean): string {
  const classes = ["moves__ply"];
  if (move.type === "split") classes.push("moves__ply--split");
  if (move.type === "observe") classes.push("moves__ply--observe");
  if (current) classes.push("is-current");
  return classes.join(" ");
}

export function MoveList({ moves }: { moves: GameMove[] }) {
  const listRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const list = listRef.current;
    if (list) list.scrollTop = list.scrollHeight;
  }, [moves.length]);

  const rows: { number: number; white: GameMove | undefined; black: GameMove | undefined; whitePly: number }[] = [];
  for (let ply = 0; ply < moves.length; ply += 2) {
    rows.push({ number: ply / 2 + 1, white: moves[ply], black: moves[ply + 1], whitePly: ply });
  }

  return (
    <section className="panel moves">
      <div className="panel__title">Moves</div>
      {moves.length === 0 ? (
        <p className="moves__empty">No moves yet.</p>
      ) : (
        <div className="moves__list" ref={listRef}>
          {rows.map((row) => (
            <div key={row.number} style={{ display: "contents" }}>
              <span className="moves__num">{row.number}.</span>
              {row.white ? (
                <span className={plyClass(row.white, row.whitePly === moves.length - 1)} title={formatMove(row.white)}>
                  {formatMove(row.white)}
                </span>
              ) : (
                <span />
              )}
              {row.black ? (
                <span className={plyClass(row.black, row.whitePly + 1 === moves.length - 1)} title={formatMove(row.black)}>
                  {formatMove(row.black)}
                </span>
              ) : (
                <span />
              )}
            </div>
          ))}
        </div>
      )}
    </section>
  );
}
