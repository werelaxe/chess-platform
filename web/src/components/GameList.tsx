import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import type { GameSummary, UserRef } from "../api/types";
import { formatResult } from "../core/notation";
import { absoluteTime, relativeTime } from "../core/time";
import { currentLocale } from "../i18n";
import { EmptyState, KindPill, LifecyclePill, Username, VisibilityPill } from "./ui";

function Seat({ player, color, viewer }: { player: UserRef | null; color: "white" | "black"; viewer: UserRef | null }) {
  const { t } = useTranslation();
  const you = player !== null && viewer !== null && player.id === viewer.id;
  return (
    <span className={`game-row__seat${player ? "" : " game-row__seat--open"}`}>
      <span className={`seat-dot seat-dot--${color}`} aria-hidden="true" />
      {player ? (
        <>
          <Username user={player} />
          {typeof player.rating === "number" ? <span className="game-row__rating">{player.rating}</span> : null}
          {you ? <span className="faint"> {t("common.you")}</span> : null}
        </>
      ) : (
        t("list.openSeat")
      )}
    </span>
  );
}

/**
 * One row per game. The whole row opens the game through a link stretched over it (see
 * `.game-row__link`), while the names stay separate links to the players' profiles.
 */
export function GameList({ games, viewer, emptyText }: { games: GameSummary[]; viewer: UserRef | null; emptyText: string }) {
  const { t } = useTranslation();
  const locale = currentLocale();
  if (games.length === 0) return <EmptyState>{emptyText}</EmptyState>;
  return (
    <div className="game-list">
      {games.map((game, index) => (
        <div key={game.id} className="game-row reveal" style={{ "--i": Math.min(index, 8) } as React.CSSProperties}>
          <Link to={`/games/${game.id}`} className="game-row__link" aria-label={t("list.openGame")}>
            <KindPill kind={game.kind} />
          </Link>
          <span className="game-row__players">
            <Seat player={game.white} color="white" viewer={viewer} />
            <span className="game-row__vs">{t("list.vs")}</span>
            <Seat player={game.black} color="black" viewer={viewer} />
          </span>
          <span className="game-row__meta">
            {game.rated ? <span className="game-row__rated">{t("rated.short")}</span> : null}
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
        </div>
      ))}
    </div>
  );
}
