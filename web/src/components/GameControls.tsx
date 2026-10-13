import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import type { Color, DrawAction, GameSummary, UserRef } from "../api/types";
import { opponentOf } from "../core/players";

/** How the viewer relates to a WAITING invitation. */
export type InvitationRole = "creator" | "invitee" | "outsider";

export function GameControls({
  game,
  viewer,
  viewerColor,
  invitationRole,
  busy,
  shareUrl,
  onJoin,
  onJoinAsGuest,
  onDecline,
  onResign,
  onDraw,
}: {
  game: GameSummary;
  viewer: UserRef | null;
  viewerColor: Color | null;
  /** Null for an open game. */
  invitationRole: InvitationRole | null;
  busy: boolean;
  shareUrl: string;
  onJoin: () => void;
  onJoinAsGuest: () => void;
  onDecline: () => void;
  onResign: () => void;
  onDraw: (action: DrawAction) => void;
}) {
  const { t } = useTranslation();
  const [copied, setCopied] = useState(false);
  const isCreator = viewer !== null && viewer.id === game.creator.id;
  const isPlayer = viewerColor !== null;
  // A bot never answers a draw offer, so none is offered against one.
  const againstBot = viewerColor !== null && opponentOf(game, viewerColor)?.bot === true;

  async function copyLink() {
    try {
      await navigator.clipboard.writeText(shareUrl);
      setCopied(true);
      setTimeout(() => setCopied(false), 1800);
    } catch {
      // Clipboard access can be denied; the input stays selectable for manual copying.
    }
  }

  function confirmResign() {
    const question = game.status === "WAITING" ? t("controls.confirmCancel") : t("controls.confirmResign");
    if (window.confirm(question)) onResign();
  }

  let buttons: React.ReactNode = null;
  if (game.status === "WAITING") {
    if (isCreator) {
      buttons = (
        <button type="button" className="btn btn--danger" disabled={busy} onClick={confirmResign}>
          {invitationRole ? t("controls.cancelInvitation") : t("controls.cancelGame")}
        </button>
      );
    } else if (invitationRole === "invitee") {
      buttons = (
        <>
          <button type="button" className="btn btn--primary" disabled={busy} onClick={onJoin}>
            {t("controls.acceptInvitation")}
          </button>
          <button type="button" className="btn" disabled={busy} onClick={onDecline}>
            {t("controls.declineInvitation")}
          </button>
        </>
      );
    } else if (invitationRole === "outsider") {
      // The invited player may well have followed the shared link while signed out.
      buttons = (
        <>
          {viewer ? null : (
            <Link to="/login" state={{ from: `/games/${game.id}` }} className="btn btn--primary">
              {t("controls.signInToAccept")}
            </Link>
          )}
          <span className="faint small">{t("controls.invitationOnly")}</span>
        </>
      );
    } else if (viewer) {
      buttons = (
        <button type="button" className="btn btn--primary" disabled={busy} onClick={onJoin}>
          {t("controls.join")}
        </button>
      );
    } else {
      buttons = (
        <>
          <Link to="/login" state={{ from: `/games/${game.id}` }} className="btn btn--primary">
            {t("controls.signInToJoin")}
          </Link>
          <button type="button" className="btn" disabled={busy} onClick={onJoinAsGuest}>
            {t("controls.continueAsGuest")}
          </button>
        </>
      );
    }
  } else if (game.status === "ACTIVE" && isPlayer) {
    const offer = game.drawOfferedBy;
    buttons = (
      <>
        {againstBot ? null : offer === null ? (
          <button type="button" className="btn" disabled={busy} onClick={() => onDraw("OFFER")}>
            {t("controls.offerDraw")}
          </button>
        ) : offer === viewerColor ? (
          <button type="button" className="btn" disabled={busy} onClick={() => onDraw("WITHDRAW")}>
            {t("controls.withdrawDraw")}
          </button>
        ) : (
          <>
            <button type="button" className="btn btn--primary" disabled={busy} onClick={() => onDraw("ACCEPT")}>
              {t("controls.acceptDraw")}
            </button>
            <button type="button" className="btn" disabled={busy} onClick={() => onDraw("DECLINE")}>
              {t("controls.declineDraw")}
            </button>
          </>
        )}
        <button type="button" className="btn btn--danger" disabled={busy} onClick={confirmResign}>
          {t("controls.resign")}
        </button>
      </>
    );
  } else if (game.status === "FINISHED") {
    buttons = (
      <Link to="/" className="btn">
        {t("controls.backToLobby")}
      </Link>
    );
  }

  const showShare = game.visibility === "PRIVATE";
  if (!buttons && !showShare) return null;

  return (
    <section className="panel">
      {buttons ? <div className="actions">{buttons}</div> : null}
      {showShare ? (
        <>
          <div className="panel__title" style={{ marginTop: buttons ? 14 : 0 }}>
            {t("controls.shareLink")}
          </div>
          <div className="share" style={{ marginTop: 0 }}>
            <input
              className="share__input"
              readOnly
              value={shareUrl}
              onFocus={(event) => event.currentTarget.select()}
              aria-label={t("controls.gameLink")}
            />
            <button type="button" className="btn btn--small" onClick={() => void copyLink()}>
              {copied ? t("controls.copied") : t("controls.copy")}
            </button>
          </div>
          <p className="faint small" style={{ marginTop: 8 }}>
            {t("controls.privateGame")}
          </p>
        </>
      ) : null}
    </section>
  );
}
