import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { api } from "../api/client";
import type { GameLifecycle } from "../api/types";
import { BRAND_NAME } from "../brand";
import { GameList } from "../components/GameList";
import { ErrorNotice, Loading, Username } from "../components/ui";
import { useAsync } from "../hooks/useAsync";
import { useDocumentTitle } from "../hooks/useDocumentTitle";
import { useAuthStore } from "../store/auth";

type Filter = "ALL" | GameLifecycle;

const FILTERS: Filter[] = ["ALL", "ACTIVE", "WAITING", "FINISHED"];

export function MyGamesPage() {
  const { t } = useTranslation();
  const user = useAuthStore((state) => state.user);
  const [filter, setFilter] = useState<Filter>("ALL");
  const { data, error, loading, reload } = useAsync(() => api.myGames(), []);
  useDocumentTitle(t("titles.myGames", { brand: BRAND_NAME }));

  const games = useMemo(() => {
    if (!data) return [];
    return filter === "ALL" ? data : data.filter((game) => game.status === filter);
  }, [data, filter]);

  return (
    <div className="page">
      <div className="page__header reveal">
        <div className="page__eyebrow">{user ? <Username user={user} /> : null}</div>
        <h1 className="page__title">{t("myGames.title")}</h1>
        <p className="page__lede">{t("myGames.lede")}</p>
      </div>
      <div className="row row--between reveal" style={{ "--i": 1, marginBottom: 16 } as React.CSSProperties}>
        <div className="tabs" style={{ marginBottom: 0, borderBottom: 0 }}>
          {FILTERS.map((entry) => (
            <button
              key={entry}
              type="button"
              className={`tab${entry === filter ? " is-active" : ""}`}
              onClick={() => setFilter(entry)}
            >
              {t(`myGames.filters.${entry}`)}
              {data ? (
                <span className="tab__count">{entry === "ALL" ? data.length : data.filter((game) => game.status === entry).length}</span>
              ) : null}
            </button>
          ))}
        </div>
        <button type="button" className="btn btn--ghost btn--small" onClick={() => reload(true)} disabled={loading}>
          {t("common.refresh")}
        </button>
      </div>
      {error ? <ErrorNotice error={error} onRetry={() => reload()} /> : null}
      {loading && !data ? <Loading label={t("myGames.loading")} /> : null}
      {data ? <GameList games={games} viewer={user} emptyText={t("myGames.empty")} /> : null}
    </div>
  );
}
