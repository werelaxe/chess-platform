import { useMemo, useState } from "react";
import { api } from "../api/client";
import type { GameLifecycle } from "../api/types";
import { GameList } from "../components/GameList";
import { ErrorNotice, Loading, Username } from "../components/ui";
import { useAsync } from "../hooks/useAsync";
import { useAuthStore } from "../store/auth";

type Filter = "ALL" | GameLifecycle;

const FILTERS: { value: Filter; label: string }[] = [
  { value: "ALL", label: "All" },
  { value: "ACTIVE", label: "Live" },
  { value: "WAITING", label: "Open" },
  { value: "FINISHED", label: "Finished" },
];

export function MyGamesPage() {
  const user = useAuthStore((state) => state.user);
  const [filter, setFilter] = useState<Filter>("ALL");
  const { data, error, loading, reload } = useAsync(() => api.myGames(), []);

  const games = useMemo(() => {
    if (!data) return [];
    return filter === "ALL" ? data : data.filter((game) => game.status === filter);
  }, [data, filter]);

  return (
    <div className="page">
      <div className="page__header reveal">
        <div className="page__eyebrow">{user ? <Username user={user} /> : null}</div>
        <h1 className="page__title">My games</h1>
        <p className="page__lede">Every game you created or joined, newest first.</p>
      </div>
      <div className="row row--between reveal" style={{ "--i": 1, marginBottom: 16 } as React.CSSProperties}>
        <div className="tabs" style={{ marginBottom: 0, borderBottom: 0 }}>
          {FILTERS.map((entry) => (
            <button
              key={entry.value}
              type="button"
              className={`tab${entry.value === filter ? " is-active" : ""}`}
              onClick={() => setFilter(entry.value)}
            >
              {entry.label}
              {data ? (
                <span className="tab__count">
                  {entry.value === "ALL" ? data.length : data.filter((game) => game.status === entry.value).length}
                </span>
              ) : null}
            </button>
          ))}
        </div>
        <button type="button" className="btn btn--ghost btn--small" onClick={() => reload(true)} disabled={loading}>
          Refresh
        </button>
      </div>
      {error ? <ErrorNotice error={error} onRetry={() => reload()} /> : null}
      {loading && !data ? <Loading label="Loading your games" /> : null}
      {data ? <GameList games={games} viewer={user} emptyText="Nothing here yet. Create a game from the lobby." /> : null}
    </div>
  );
}
