import { useTranslation } from "react-i18next";
import type { Color, GameDto, RatingChange, UserRef } from "../api/types";
import type { SocketState } from "../api/ws";
import type { GameSnapshot } from "../core/game";
import { displayName } from "../core/players";
import { formatResult } from "../core/notation";
import { endedWithoutRatingChange, formatRatingChange, formatRatingDelta } from "../core/ratings";
import { KindPill, LifecyclePill, RatedPill, Rating, Username, VisibilityPill } from "./ui";

function PlayerCard({
  color,
  player,
  viewer,
  toMove,
  creator,
  change,
}: {
  color: Color;
  player: UserRef | null;
  viewer: UserRef | null;
  toMove: boolean;
  creator: UserRef;
  /** The rating change of this seat once a rated game is over. */
  change: RatingChange | null;
}) {
  const { t } = useTranslation();
  const isViewer = player !== null && viewer !== null && player.id === viewer.id;
  const showUsername = player !== null && displayName(player) !== player.username;
  const delta = change ? change.after - change.before : 0;
  return (
    <div className={`player player--${color.toLowerCase()}${toMove ? " player--to-move" : ""}`}>
      <span className={`player__dot player__dot--${color.toLowerCase()}`} aria-hidden="true" />
      {player ? (
        <span className="player__body">
          <span className="player__name">
            <Username user={player} />
            {isViewer ? <span className="faint"> {t("common.you")}</span> : null}
          </span>
          <span className="player__sub">
            {showUsername ? <span className="player__username">{player.username}</span> : null}
            {change ? (
              <span className={`player__change${delta > 0 ? " is-up" : delta < 0 ? " is-down" : ""}`} title={t("ratings.changeTitle")}>
                {formatRatingChange(change)}
              </span>
            ) : player.guest ? null : (
              <Rating value={player.rating} title={t("ratings.ratingTitle")} />
            )}
          </span>
        </span>
      ) : (
        <span className="player__name player__name--empty">{t("header.waitingPlayer")}</span>
      )}
      <span className="player__tag">
        {toMove ? t("header.toMoveTag") : player && player.id === creator.id ? t("header.creatorTag") : ""}
      </span>
    </div>
  );
}

export function GameHeader({
  game,
  snapshot,
  viewer,
  socketState,
}: {
  game: GameDto;
  snapshot: GameSnapshot | null;
  viewer: UserRef | null;
  socketState: SocketState;
}) {
  const { t } = useTranslation();
  const finished = game.status === "FINISHED" || snapshot?.status.type === "finished";
  const result =
    game.status === "FINISHED" && game.result
      ? game.result
      : snapshot?.status.type === "finished"
        ? { winner: snapshot.status.winner, reason: snapshot.status.reason }
        : null;
  const sideToMove = snapshot?.sideToMove ?? "WHITE";
  const live = game.status === "ACTIVE" && !finished;
  const waiting = game.status === "WAITING";
  const invitee = game.invitee ?? null;
  const changes = finished ? game.ratingChanges : null;
  // The creator's cancel and the invitee's refusal end an invitation the same way; only the server tells them apart.
  const declined = result?.reason === "ABANDONMENT" && game.declined === true && invitee !== null;

  return (
    <section className="panel gh">
      <div className="gh__meta">
        <KindPill kind={game.kind} />
        <LifecyclePill status={finished ? "FINISHED" : game.status} />
        <RatedPill rated={game.rated} />
        <VisibilityPill visibility={game.visibility} />
        <span className={`conn conn--${socketState}`} title={t("header.socketTitle")}>
          {t(`header.socket.${socketState}`)}
        </span>
      </div>
      <div className="players">
        <PlayerCard
          color="WHITE"
          player={game.white}
          viewer={viewer}
          toMove={live && sideToMove === "WHITE"}
          creator={game.creator}
          change={changes?.white ?? null}
        />
        <PlayerCard
          color="BLACK"
          player={game.black}
          viewer={viewer}
          toMove={live && sideToMove === "BLACK"}
          creator={game.creator}
          change={changes?.black ?? null}
        />
      </div>
      <div className="gh__status">
        {result ? (
          <div className={`gh__result${result.winner === null ? " gh__result--draw" : ""}`}>
            {declined ? t("header.invitationDeclined", { name: displayName(invitee) }) : formatResult(result.winner, result.reason)}
          </div>
        ) : waiting ? (
          <div className="gh__line">
            <span>{invitee ? t("header.waitingInvitee", { name: displayName(invitee) }) : t("header.waitingOpponent")}</span>
          </div>
        ) : (
          <div className="gh__line">
            <span>{sideToMove === "WHITE" ? t("header.whiteToMove") : t("header.blackToMove")}</span>
            {snapshot && snapshot.view.checkProbability > 0 ? (
              <span style={{ color: "var(--danger)" }}>
                {game.kind === "QUANTUM" && snapshot.view.checkProbability < 1
                  ? t("header.checkProbability", { percent: Math.round(snapshot.view.checkProbability * 100) })
                  : t("header.check")}
              </span>
            ) : null}
          </div>
        )}
        {changes ? (
          <div className="gh__line gh__changes">
            <span>{t("header.ratingChanges")}</span>
            <span className="mono">
              {t("header.whiteShort")} {formatRatingDelta(changes.white)} · {t("header.blackShort")} {formatRatingDelta(changes.black)}
            </span>
          </div>
        ) : endedWithoutRatingChange(game) ? (
          <div className="gh__line" style={{ marginTop: 8 }}>
            <span className="faint small">{t("header.unratedEnd")}</span>
          </div>
        ) : null}
        {game.kind === "QUANTUM" && snapshot ? (
          <div className="gh__line" style={{ marginTop: 8 }}>
            <span className="gh__universes">
              <strong>{snapshot.universeCount}</strong>
              {t("counts.universes", { count: snapshot.universeCount })}
            </span>
            <span className="faint small mono">{t("counts.plies", { count: snapshot.moveCount })}</span>
          </div>
        ) : snapshot ? (
          <div className="gh__line" style={{ marginTop: 8 }}>
            <span className="faint small mono">{t("counts.plies", { count: snapshot.moveCount })}</span>
          </div>
        ) : null}
        {game.drawOfferedBy && !finished ? (
          <div className="gh__line" style={{ marginTop: 8, color: "var(--accent-bright)" }}>
            <span>{game.drawOfferedBy === "WHITE" ? t("header.whiteOffersDraw") : t("header.blackOffersDraw")}</span>
          </div>
        ) : null}
      </div>
    </section>
  );
}
