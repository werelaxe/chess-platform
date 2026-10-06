import { Link } from "react-router";
import type { GameSummary, UserRef } from "../api/types";
import { formatResult } from "../core/notation";
import { EmptyState, KindPill, LifecyclePill, VisibilityPill } from "./ui";

function Seat({ player, color, viewer }: { player: UserRef | null; color: "white" | "black"; viewer: UserRef | null }) {
  const you = player !== null && viewer !== null && player.id === viewer.id;
  return (
    <span className={`game-row__seat${player ? "" : " game-row__seat--open"}`}>
      <span className={`seat-dot seat-dot--${color}`} aria-hidden="true" />
      {player ? (
        <>
          {player.username}
          {you ? <span className="faint"> (you)</span> : null}
        </>
      ) : (
        "open seat"
      )}
    </span>
  );
}

function relativeTime(iso: string): string {
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return "";
  const seconds = Math.max(0, Math.round((Date.now() - then) / 1000));
  if (seconds < 60) return "just now";
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} h ago`;
  const days = Math.round(hours / 24);
  return `${days} d ago`;
}

export function GameList({ games, viewer, emptyText }: { games: GameSummary[]; viewer: UserRef | null; emptyText: string }) {
  if (games.length === 0) return <EmptyState>{emptyText}</EmptyState>;
  return (
    <div className="game-list">
      {games.map((game, index) => (
        <Link key={game.id} to={`/games/${game.id}`} className="game-row reveal" style={{ "--i": Math.min(index, 8) } as React.CSSProperties}>
          <KindPill kind={game.kind} />
          <span className="game-row__players">
            <Seat player={game.white} color="white" viewer={viewer} />
            <span className="game-row__vs">vs</span>
            <Seat player={game.black} color="black" viewer={viewer} />
          </span>
          <span className="game-row__meta">
            <span>{game.moveCount} plies</span>
            <span>{relativeTime(game.updatedAt)}</span>
            <VisibilityPill visibility={game.visibility} />
          </span>
          {game.status === "FINISHED" && game.result ? (
            <span className="game-row__result">{formatResult(game.result.winner, game.result.reason)}</span>
          ) : (
            <span className="game-row__status">
              <LifecyclePill status={game.status} />
            </span>
          )}
        </Link>
      ))}
    </div>
  );
}
