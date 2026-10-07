import { useTranslation } from "react-i18next";
import type { BotLevel, Color, GameSummary, UserRef } from "../api/types";
import type { SocketState } from "../api/ws";
import type { GameSnapshot } from "../core/game";
import { botColor } from "../core/computer";
import { formatResult } from "../core/notation";
import { KindPill, LifecyclePill, Username, VisibilityPill } from "./ui";

function PlayerRow({
  color,
  player,
  viewer,
  toMove,
  creator,
  botLevel,
}: {
  color: Color;
  player: UserRef | null;
  viewer: UserRef | null;
  toMove: boolean;
  creator: UserRef;
  botLevel?: BotLevel | null;
}) {
  const { t } = useTranslation();
  const isViewer = player !== null && viewer !== null && player.id === viewer.id;
  return (
    <div className={`player player--${color.toLowerCase()}${toMove ? " player--to-move" : ""}`}>
      <span className={`player__dot player__dot--${color.toLowerCase()}`} aria-hidden="true" />
      {player ? (
        <span className="player__name">
          <Username user={player} botLevel={botLevel} />
          {isViewer ? <span className="faint"> {t("common.you")}</span> : null}
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
  game: GameSummary;
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
  // Computer games start active with both seats taken, so they never wait for an opponent.
  const waiting = game.status === "WAITING" && botColor(game) === null;

  return (
    <section className="panel gh">
      <div className="gh__meta">
        <KindPill kind={game.kind} />
        <LifecyclePill status={finished ? "FINISHED" : game.status} />
        <VisibilityPill visibility={game.visibility} />
        <span className={`conn conn--${socketState}`} title={t("header.socketTitle")}>
          {t(`header.socket.${socketState}`)}
        </span>
      </div>
      <div className="players">
        <PlayerRow
          color="WHITE"
          player={game.white}
          viewer={viewer}
          toMove={live && sideToMove === "WHITE"}
          creator={game.creator}
          botLevel={game.botLevel}
        />
        <PlayerRow
          color="BLACK"
          player={game.black}
          viewer={viewer}
          toMove={live && sideToMove === "BLACK"}
          creator={game.creator}
          botLevel={game.botLevel}
        />
      </div>
      <div className="gh__status">
        {result ? (
          <div className={`gh__result${result.winner === null ? " gh__result--draw" : ""}`}>
            {formatResult(result.winner, result.reason)}
          </div>
        ) : waiting ? (
          <div className="gh__line">
            <span>{t("header.waitingOpponent")}</span>
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
