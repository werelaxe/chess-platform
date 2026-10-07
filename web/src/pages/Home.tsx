import { useEffect, useState } from "react";
import { Trans, useTranslation } from "react-i18next";
import { api } from "../api/client";
import type { GameFilter, GameSummary } from "../api/types";
import { BRAND_NAME } from "../brand";
import { CreateGameForm } from "../components/CreateGameForm";
import { GameList } from "../components/GameList";
import { ErrorNotice, Loading } from "../components/ui";
import { useAsync } from "../hooks/useAsync";
import { useDocumentTitle } from "../hooks/useDocumentTitle";
import { useAuthStore } from "../store/auth";

const REFRESH_MS = 15_000;
/** The lobby shows at most this many games; a full list is counted as "50+". */
const LOBBY_LIMIT = 50;

function countLabel(games: GameSummary[]): string {
  return games.length >= LOBBY_LIMIT ? `${LOBBY_LIMIT}+` : String(games.length);
}

const TABS: GameFilter[] = ["open", "active"];

export function HomePage() {
  const { t } = useTranslation();
  const user = useAuthStore((state) => state.user);
  const [filter, setFilter] = useState<GameFilter>("open");
  const { data, error, loading, reload } = useAsync(() => api.listGames({ filter, limit: LOBBY_LIMIT }), [filter]);
  useDocumentTitle(t("titles.lobby", { brand: BRAND_NAME }));

  useEffect(() => {
    const timer = setInterval(() => reload(true), REFRESH_MS);
    return () => clearInterval(timer);
  }, [reload]);

  const tabLabel = (entry: GameFilter) => (entry === "active" ? t("lobby.tabs.active") : t("lobby.tabs.open"));
  const emptyText = filter === "active" ? t("lobby.empty.active") : t("lobby.empty.open");

  return (
    <div className="page">
      <section className="hero">
        <div className="reveal">
          <h1 className="hero__title">
            <Trans i18nKey="lobby.heroTitle" components={{ em: <em /> }} />
          </h1>
          <p className="hero__lede">{t("lobby.heroLede")}</p>
        </div>
        <div className="hero__aside reveal" style={{ "--i": 2 } as React.CSSProperties}>
          <div className="hero__stat">
            <span className="hero__stat-value">{data ? countLabel(data) : "–"}</span>
            <span>{tabLabel(filter).toLocaleLowerCase()}</span>
          </div>
          <div className="hero__stat">
            <span className="hero__stat-value">2</span>
            <span>{t("lobby.variants")}</span>
          </div>
        </div>
      </section>
      <div className="lobby">
        <div className="lobby__main reveal" style={{ "--i": 3 } as React.CSSProperties}>
          <div className="row row--between">
            <div className="tabs" style={{ marginBottom: 0, borderBottom: 0 }}>
              {TABS.map((entry) => (
                <button
                  key={entry}
                  type="button"
                  className={`tab${entry === filter ? " is-active" : ""}`}
                  onClick={() => setFilter(entry)}
                >
                  {tabLabel(entry)}
                  {entry === filter && data ? <span className="tab__count">{countLabel(data)}</span> : null}
                </button>
              ))}
            </div>
            <button type="button" className="btn btn--ghost btn--small" onClick={() => reload(true)} disabled={loading}>
              {t("common.refresh")}
            </button>
          </div>
          <div style={{ borderTop: "1px solid var(--line)", marginBottom: 16 }} />
          {error ? <ErrorNotice error={error} onRetry={() => reload()} /> : null}
          {loading && !data ? <Loading label={t("lobby.loading")} /> : null}
          {data ? <GameList games={data} viewer={user} emptyText={emptyText} /> : null}
        </div>
        <aside className="lobby__aside reveal" style={{ "--i": 4 } as React.CSSProperties}>
          <CreateGameForm />
        </aside>
      </div>
    </div>
  );
}
