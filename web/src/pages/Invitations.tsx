import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router";
import { api, errorMessage } from "../api/client";
import type { GameSummary } from "../api/types";
import { BRAND_NAME } from "../brand";
import { EmptyState, ErrorNotice, KindPill, Loading, Notice, RatedPill, Username } from "../components/ui";
import { absoluteTime, relativeTime } from "../core/time";
import { currentLocale } from "../i18n";
import { useDocumentTitle } from "../hooks/useDocumentTitle";
import { useInvitationsStore } from "../store/invitations";

/** The page refreshes faster than the badge: someone waiting here expects answers to show up. */
const REFRESH_MS = 10_000;

function Row({ game, direction, children }: { game: GameSummary; direction: "incoming" | "outgoing"; children: React.ReactNode }) {
  const { t } = useTranslation();
  const locale = currentLocale();
  // The creator holds one seat and the invitee gets the other; the DTO may leave the invitee unnamed.
  const other = direction === "incoming" ? game.creator : (game.invitee ?? null);
  const yourColor = (direction === "incoming") === (game.white === null) ? "WHITE" : "BLACK";
  return (
    <div className="invite-row">
      <div className="invite-row__who">
        <KindPill kind={game.kind} />
        <RatedPill rated={game.rated} />
        <span className="invite-row__name">
          {t(direction === "incoming" ? "invitations.from" : "invitations.to")} {other ? <Username user={other} /> : t("common.unknownPlayer")}
        </span>
        <span className="faint small">{yourColor === "WHITE" ? t("invitations.youPlayWhite") : t("invitations.youPlayBlack")}</span>
      </div>
      <span className="invite-row__when" title={absoluteTime(game.createdAt, locale)}>
        {relativeTime(game.createdAt, locale)}
      </span>
      <div className="invite-row__actions">
        <Link to={`/games/${game.id}`} className="btn btn--ghost btn--small">
          {t("invitations.view")}
        </Link>
        {children}
      </div>
    </div>
  );
}

export function InvitationsPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { incoming, outgoing, loaded, error, refresh } = useInvitationsStore();
  const [busyId, setBusyId] = useState<string | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  useDocumentTitle(t("titles.invitations", { brand: BRAND_NAME }));

  useEffect(() => {
    void refresh();
    const timer = setInterval(() => void refresh(), REFRESH_MS);
    return () => clearInterval(timer);
  }, [refresh]);

  async function act(id: string, action: () => Promise<unknown>, after?: () => void) {
    setBusyId(id);
    setFailure(null);
    try {
      await action();
      await refresh();
      after?.();
    } catch (cause) {
      setFailure(errorMessage(cause));
    } finally {
      setBusyId(null);
    }
  }

  function accept(game: GameSummary) {
    void act(game.id, () => api.joinGame(game.id), () => navigate(`/games/${game.id}`));
  }

  function decline(game: GameSummary) {
    void act(game.id, () => api.decline(game.id));
  }

  function cancel(game: GameSummary) {
    if (!window.confirm(t("controls.confirmCancel"))) return;
    void act(game.id, () => api.resign(game.id));
  }

  return (
    <div className="page">
      <div className="page__header reveal">
        <div className="page__eyebrow">{t("invitations.eyebrow")}</div>
        <h1 className="page__title">{t("invitations.title")}</h1>
        <p className="page__lede">{t("invitations.lede")}</p>
      </div>
      {error && !loaded ? <ErrorNotice error={error} onRetry={() => void refresh()} /> : null}
      {failure ? <Notice kind="error">{failure}</Notice> : null}
      {!loaded && !error ? <Loading label={t("invitations.loading")} /> : null}
      {loaded ? (
        <>
          <section className="reveal" style={{ "--i": 1 } as React.CSSProperties}>
            <h2 className="section__title">
              {t("invitations.incoming")}
              {incoming.length > 0 ? <span className="tab__count">{incoming.length}</span> : null}
            </h2>
            {incoming.length === 0 ? (
              <EmptyState>{t("invitations.noIncoming")}</EmptyState>
            ) : (
              <div className="invite-list">
                {incoming.map((game) => (
                  <Row key={game.id} game={game} direction="incoming">
                    <button type="button" className="btn btn--primary btn--small" disabled={busyId !== null} onClick={() => accept(game)}>
                      {t("controls.acceptInvitation")}
                    </button>
                    <button type="button" className="btn btn--small" disabled={busyId !== null} onClick={() => decline(game)}>
                      {t("controls.declineInvitation")}
                    </button>
                  </Row>
                ))}
              </div>
            )}
          </section>
          <section className="reveal" style={{ "--i": 2, marginTop: 32 } as React.CSSProperties}>
            <h2 className="section__title">
              {t("invitations.outgoing")}
              {outgoing.length > 0 ? <span className="tab__count">{outgoing.length}</span> : null}
            </h2>
            {outgoing.length === 0 ? (
              <EmptyState>{t("invitations.noOutgoing")}</EmptyState>
            ) : (
              <div className="invite-list">
                {outgoing.map((game) => (
                  <Row key={game.id} game={game} direction="outgoing">
                    <button type="button" className="btn btn--danger btn--small" disabled={busyId !== null} onClick={() => cancel(game)}>
                      {t("controls.cancelInvitation")}
                    </button>
                  </Row>
                ))}
              </div>
            )}
          </section>
        </>
      ) : null}
    </div>
  );
}
