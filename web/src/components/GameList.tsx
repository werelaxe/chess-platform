import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import type { BotLevel, GameSummary, UserRef } from "../api/types";
import { formatResult } from "../core/notation";
import { absoluteTime, relativeTime } from "../core/time";
import { currentLocale } from "../i18n";
import { EmptyState, KindPill, LifecyclePill, Username, VisibilityPill } from "./ui";

function Seat({
  player,
  color,
  viewer,
  botLevel,
}: {
  player: UserRef | null;
  color: "white" | "black";
  viewer: UserRef | null;
  botLevel?: BotLevel | null;
}) {
  const { t } = useTranslation();
  const you = player !== null && viewer !== null && player.id === viewer.id;
  return (
    <span className={`game-row__seat${player ? "" : " game-row__seat--open"}`}>
      <span className={`seat-dot seat-dot--${color}`} aria-hidden="true" />
      {player ? (
        <>
          <Username user={player} botLevel={botLevel} />
          {you ? <span className="faint"> {t("common.you")}</span> : null}
        </>
      ) : (
        t("list.openSeat")
      )}
    </span>
  );
}

export function GameList({ games, viewer, emptyText }: { games: GameSummary[]; viewer: UserRef | null; emptyText: string }) {
  const { t } = useTranslation();
  const locale = currentLocale();
  if (games.length === 0) return <EmptyState>{emptyText}</EmptyState>;
  return (
    <div className="game-list">
      {games.map((game, index) => (
        <Link key={game.id} to={`/games/${game.id}`} className="game-row reveal" style={{ "--i": Math.min(index, 8) } as React.CSSProperties}>
          <KindPill kind={game.kind} />
          <span className="game-row__players">
            <Seat player={game.white} color="white" viewer={viewer} botLevel={game.botLevel} />
            <span className="game-row__vs">{t("list.vs")}</span>
            <Seat player={game.black} color="black" viewer={viewer} botLevel={game.botLevel} />
          </span>
          <span className="game-row__meta">
            <span>{t("counts.plies", { count: game.moveCount })}</span>
            <span title={absoluteTime(game.updatedAt, locale)}>{relativeTime(game.updatedAt, locale)}</span>
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
