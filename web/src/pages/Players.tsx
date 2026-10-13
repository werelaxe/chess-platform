import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { api } from "../api/client";
import type { GameKind, LeaderboardEntry, PlayerSummary } from "../api/types";
import { BRAND_NAME } from "../brand";
import { EmptyState, ErrorNotice, Loading, Rating, UserTag, profilePath } from "../components/ui";
import { displayName } from "../core/players";
import { formatRecord, ratingIn } from "../core/ratings";
import { useAsync } from "../hooks/useAsync";
import { useDocumentTitle } from "../hooks/useDocumentTitle";

const KINDS: GameKind[] = ["CLASSIC", "QUANTUM"];
const LEADERBOARD_LIMIT = 100;
const SEARCH_DEBOUNCE_MS = 250;

function useDebounced(value: string, delay: number): string {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);
  return debounced;
}

function NameCell({ player }: { player: PlayerSummary }) {
  const name = displayName(player.user);
  return (
    <Link to={profilePath(player.user.username)} className="table__link">
      <span className="table__name">
        {name}
        <UserTag user={player.user} />
      </span>
      {name !== player.user.username ? <span className="table__sub">{player.user.username}</span> : null}
    </Link>
  );
}

function Leaderboard({ entries, kind }: { entries: LeaderboardEntry[]; kind: GameKind }) {
  const { t } = useTranslation();
  if (entries.length === 0) return <EmptyState>{t("players.emptyLeaderboard")}</EmptyState>;
  return (
    <table className="table">
      <thead>
        <tr>
          <th className="table__num">{t("players.rank")}</th>
          <th>{t("players.player")}</th>
          <th className="table__num">{t("players.rating")}</th>
          <th className="table__num">{t("players.games")}</th>
          <th className="table__num" title={t("players.recordTitle")}>
            {t("players.record")}
          </th>
        </tr>
      </thead>
      <tbody>
        {entries.map((entry) => {
          const summary = ratingIn(entry.ratings, kind);
          return (
            <tr key={entry.user.id}>
              <td className="table__num mono faint">{entry.rank}</td>
              <td>
                <NameCell player={entry} />
              </td>
              <td className="table__num">
                <Rating value={entry.rating} />
              </td>
              <td className="table__num mono">{summary?.games ?? 0}</td>
              <td className="table__num mono">{summary ? formatRecord(summary) : "–"}</td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}

function SearchResults({ players }: { players: PlayerSummary[] }) {
  const { t } = useTranslation();
  if (players.length === 0) return <EmptyState>{t("players.emptySearch")}</EmptyState>;
  return (
    <table className="table">
      <thead>
        <tr>
          <th>{t("players.player")}</th>
          <th className="table__num">{t("kinds.CLASSIC")}</th>
          <th className="table__num">{t("kinds.QUANTUM")}</th>
        </tr>
      </thead>
      <tbody>
        {players.map((player) => (
          <tr key={player.user.id}>
            <td>
              <NameCell player={player} />
            </td>
            <td className="table__num">
              <Rating value={ratingIn(player.ratings, "CLASSIC")?.rating} />
            </td>
            <td className="table__num">
              <Rating value={ratingIn(player.ratings, "QUANTUM")?.rating} />
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export function PlayersPage() {
  const { t } = useTranslation();
  const [kind, setKind] = useState<GameKind>("QUANTUM");
  const [query, setQuery] = useState("");
  const search = useDebounced(query.trim(), SEARCH_DEBOUNCE_MS);
  useDocumentTitle(t("titles.players", { brand: BRAND_NAME }));

  // The leaderboard is kept while a search is shown, so clearing the box brings it back at once.
  const leaderboard = useAsync(() => api.leaderboard(kind, LEADERBOARD_LIMIT), [kind]);
  const results = useAsync(() => (search ? api.searchPlayers(search) : Promise.resolve<PlayerSummary[]>([])), [search]);
  const searching = search !== "";

  return (
    <div className="page">
      <div className="page__header reveal">
        <div className="page__eyebrow">{t("players.eyebrow")}</div>
        <h1 className="page__title">{t("players.title")}</h1>
        <p className="page__lede">{t("players.lede")}</p>
      </div>
      <div className="row row--between reveal" style={{ "--i": 1, marginBottom: 16 } as React.CSSProperties}>
        <div className="tabs" style={{ marginBottom: 0, borderBottom: 0 }}>
          {KINDS.map((entry) => (
            <button
              key={entry}
              type="button"
              className={`tab${entry === kind && !searching ? " is-active" : ""}`}
              disabled={searching}
              onClick={() => setKind(entry)}
            >
              {t(`kinds.${entry}`)}
            </button>
          ))}
        </div>
        <input
          className="field__input search"
          type="search"
          value={query}
          placeholder={t("players.searchPlaceholder")}
          aria-label={t("players.search")}
          autoComplete="off"
          autoCapitalize="none"
          spellCheck={false}
          maxLength={40}
          onChange={(event) => setQuery(event.target.value)}
        />
      </div>
      {searching ? (
        <>
          {results.error ? <ErrorNotice error={results.error} onRetry={() => results.reload()} /> : null}
          {results.loading && !results.data ? <Loading label={t("players.searching")} /> : null}
          {results.data ? <SearchResults players={results.data} /> : null}
        </>
      ) : (
        <>
          {leaderboard.error ? <ErrorNotice error={leaderboard.error} onRetry={() => leaderboard.reload()} /> : null}
          {leaderboard.loading && !leaderboard.data ? <Loading label={t("players.loading")} /> : null}
          {leaderboard.data ? <Leaderboard entries={leaderboard.data} kind={kind} /> : null}
        </>
      )}
    </div>
  );
}
