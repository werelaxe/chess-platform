import { useEffect, useState } from "react";
import { api } from "../api/client";
import type { GameFilter, GameSummary } from "../api/types";
import { CreateGameForm } from "../components/CreateGameForm";
import { GameList } from "../components/GameList";
import { ErrorNotice, Loading } from "../components/ui";
import { useAsync } from "../hooks/useAsync";
import { useAuthStore } from "../store/auth";

const REFRESH_MS = 15_000;
/** The lobby shows at most this many games; a full list is counted as "50+". */
const LOBBY_LIMIT = 50;

function countLabel(games: GameSummary[]): string {
  return games.length >= LOBBY_LIMIT ? `${LOBBY_LIMIT}+` : String(games.length);
}

const TABS: { filter: GameFilter; label: string; empty: string }[] = [
  { filter: "open", label: "Open games", empty: "No open games right now. Create one and share the link, or wait a moment." },
  { filter: "active", label: "Live games", empty: "Nobody is playing in public at the moment." },
];

export function HomePage() {
  const user = useAuthStore((state) => state.user);
  const [filter, setFilter] = useState<GameFilter>("open");
  const { data, error, loading, reload } = useAsync(() => api.listGames({ filter, limit: LOBBY_LIMIT }), [filter]);

  useEffect(() => {
    const timer = setInterval(() => reload(true), REFRESH_MS);
    return () => clearInterval(timer);
  }, [reload]);

  const tab = TABS.find((entry) => entry.filter === filter) ?? TABS[0]!;

  return (
    <div className="page">
      <section className="hero">
        <div className="reveal">
          <h1 className="hero__title">
            Chess, <em>in every universe</em> at once.
          </h1>
          <p className="hero__lede">
            Play classic chess, or quantum chess where a single piece can stand on two squares until someone looks.
            Pick an open game below or create your own.
          </p>
        </div>
        <div className="hero__aside reveal" style={{ "--i": 2 } as React.CSSProperties}>
          <div className="hero__stat">
            <span className="hero__stat-value">{data ? countLabel(data) : "–"}</span>
            <span>{tab.label.toLowerCase()}</span>
          </div>
          <div className="hero__stat">
            <span className="hero__stat-value">2</span>
            <span>variants</span>
          </div>
        </div>
      </section>
      <div className="lobby">
        <div className="lobby__main reveal" style={{ "--i": 3 } as React.CSSProperties}>
          <div className="row row--between">
            <div className="tabs" style={{ marginBottom: 0, borderBottom: 0 }}>
              {TABS.map((entry) => (
                <button
                  key={entry.filter}
                  type="button"
                  className={`tab${entry.filter === filter ? " is-active" : ""}`}
                  onClick={() => setFilter(entry.filter)}
                >
                  {entry.label}
                  {entry.filter === filter && data ? <span className="tab__count">{countLabel(data)}</span> : null}
                </button>
              ))}
            </div>
            <button type="button" className="btn btn--ghost btn--small" onClick={() => reload(true)} disabled={loading}>
              Refresh
            </button>
          </div>
          <div style={{ borderTop: "1px solid var(--line)", marginBottom: 16 }} />
          {error ? <ErrorNotice error={error} onRetry={() => reload()} /> : null}
          {loading && !data ? <Loading label="Loading games" /> : null}
          {data ? <GameList games={data} viewer={user} emptyText={tab.empty} /> : null}
        </div>
        <aside className="lobby__aside reveal" style={{ "--i": 4 } as React.CSSProperties}>
          <CreateGameForm />
        </aside>
      </div>
    </div>
  );
}
