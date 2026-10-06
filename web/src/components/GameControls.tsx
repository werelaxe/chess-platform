import { useState } from "react";
import { Link } from "react-router";
import type { Color, DrawAction, GameSummary, UserRef } from "../api/types";

export function GameControls({
  game,
  viewer,
  viewerColor,
  busy,
  shareUrl,
  onJoin,
  onResign,
  onDraw,
}: {
  game: GameSummary;
  viewer: UserRef | null;
  viewerColor: Color | null;
  busy: boolean;
  shareUrl: string;
  onJoin: () => void;
  onResign: () => void;
  onDraw: (action: DrawAction) => void;
}) {
  const [copied, setCopied] = useState(false);
  const isCreator = viewer !== null && viewer.id === game.creator.id;
  const isPlayer = viewerColor !== null;

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
    const question = game.status === "WAITING" ? "Cancel this game?" : "Resign this game?";
    if (window.confirm(question)) onResign();
  }

  let buttons: React.ReactNode = null;
  if (game.status === "WAITING") {
    if (isCreator) {
      buttons = (
        <button type="button" className="btn btn--danger" disabled={busy} onClick={confirmResign}>
          Cancel game
        </button>
      );
    } else if (viewer) {
      buttons = (
        <button type="button" className="btn btn--primary" disabled={busy} onClick={onJoin}>
          Join game
        </button>
      );
    } else {
      buttons = (
        <Link to="/login" state={{ from: `/games/${game.id}` }} className="btn btn--primary">
          Sign in to join
        </Link>
      );
    }
  } else if (game.status === "ACTIVE" && isPlayer) {
    const offer = game.drawOfferedBy;
    buttons = (
      <>
        {offer === null ? (
          <button type="button" className="btn" disabled={busy} onClick={() => onDraw("OFFER")}>
            Offer draw
          </button>
        ) : offer === viewerColor ? (
          <button type="button" className="btn" disabled={busy} onClick={() => onDraw("WITHDRAW")}>
            Withdraw draw offer
          </button>
        ) : (
          <>
            <button type="button" className="btn btn--primary" disabled={busy} onClick={() => onDraw("ACCEPT")}>
              Accept draw
            </button>
            <button type="button" className="btn" disabled={busy} onClick={() => onDraw("DECLINE")}>
              Decline
            </button>
          </>
        )}
        <button type="button" className="btn btn--danger" disabled={busy} onClick={confirmResign}>
          Resign
        </button>
      </>
    );
  } else if (game.status === "FINISHED") {
    buttons = (
      <Link to="/" className="btn">
        Back to lobby
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
            Share link
          </div>
          <div className="share" style={{ marginTop: 0 }}>
            <input className="share__input" readOnly value={shareUrl} onFocus={(event) => event.currentTarget.select()} aria-label="Game link" />
            <button type="button" className="btn btn--small" onClick={() => void copyLink()}>
              {copied ? "Copied" : "Copy"}
            </button>
          </div>
          <p className="faint small" style={{ marginTop: 8 }}>
            This game is private: only people with the link can find it.
          </p>
        </>
      ) : null}
    </section>
  );
}
