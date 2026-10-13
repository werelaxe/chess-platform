import { useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate, useParams } from "react-router";
import { api, detailedErrorMessage, isApiError } from "../api/client";
import type { ColorChoice, GameKind, PlayerProfile, RatingSummary, UserRef, Visibility } from "../api/types";
import { BRAND_NAME } from "../brand";
import { GameList } from "../components/GameList";
import { Choice, EmptyState, ErrorNotice, Loading, Notice, Toggle, UserTag } from "../components/ui";
import { INVITEE_ERROR_STATUSES, createGameRequest } from "../core/newGame";
import { displayName } from "../core/players";
import { formatRecord } from "../core/ratings";
import { absoluteTime, relativeTime } from "../core/time";
import { currentLocale } from "../i18n";
import { useAsync } from "../hooks/useAsync";
import { useDocumentTitle } from "../hooks/useDocumentTitle";
import { useAuthStore } from "../store/auth";

const KINDS: GameKind[] = ["CLASSIC", "QUANTUM"];

function RatingCard({ kind, summary }: { kind: GameKind; summary: RatingSummary | null }) {
  const { t } = useTranslation();
  return (
    <div className={`stat stat--${kind.toLowerCase()}`}>
      <div className="stat__kind">{t(`variants.${kind}`)}</div>
      {summary ? (
        <>
          <div className="stat__rating">{summary.rating}</div>
          <dl className="stat__facts">
            <div>
              <dt>{t("profile.games")}</dt>
              <dd>{summary.games}</dd>
            </div>
            <div>
              <dt>{t("profile.record")}</dt>
              <dd>{formatRecord(summary)}</dd>
            </div>
            <div>
              <dt>{t("profile.peak")}</dt>
              <dd>{summary.peak}</dd>
            </div>
          </dl>
        </>
      ) : (
        <div className="stat__none">{t("profile.unrated")}</div>
      )}
    </div>
  );
}

/** "Invite to a game": a short form that creates an invitation to this player and opens the game. */
function InviteForm({ invitee, viewer }: { invitee: UserRef; viewer: UserRef }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [kind, setKind] = useState<GameKind>("QUANTUM");
  const [color, setColor] = useState<ColorChoice>("RANDOM");
  const [rated, setRated] = useState(true);
  const [visibility, setVisibility] = useState<Visibility>("PUBLIC");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const guest = viewer.guest === true;

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const game = await api.createGame(
        createGameRequest({ kind, visibility, color, rated, opponentUsername: invitee.username, guest }),
      );
      navigate(`/games/${game.id}`);
    } catch (cause) {
      setError(cause);
      setSubmitting(false);
    }
  }

  return (
    <form className="card" onSubmit={submit}>
      <h2 className="card__title">{t("profile.invite.title")}</h2>
      <p className="card__subtitle">{t(invitee.bot ? "profile.invite.ledeBot" : "profile.invite.lede", { name: displayName(invitee) })}</p>
      <div className="field">
        <span className="field__label">{t("create.variant")}</span>
        <Choice
          name={t("create.variant")}
          value={kind}
          onChange={setKind}
          disabled={submitting}
          options={[
            { value: "CLASSIC", label: t("kinds.CLASSIC") },
            { value: "QUANTUM", label: t("kinds.QUANTUM"), quantum: true },
          ]}
        />
      </div>
      <div className="field">
        <span className="field__label">{t("create.yourColor")}</span>
        <Choice
          name={t("create.color")}
          value={color}
          onChange={setColor}
          disabled={submitting}
          options={[
            { value: "WHITE", label: t("create.white") },
            { value: "RANDOM", label: t("create.random") },
            { value: "BLACK", label: t("create.black") },
          ]}
        />
      </div>
      <div className="field">
        <Toggle
          checked={rated && !guest}
          onChange={setRated}
          disabled={submitting || guest}
          label={t("create.rated")}
          hint={guest ? t("create.ratedGuestHint") : rated ? t("create.ratedHint") : t("create.casualHint")}
        />
      </div>
      <div className="field">
        <span className="field__label">{t("create.visibility")}</span>
        <Choice
          name={t("create.visibility")}
          value={visibility}
          onChange={setVisibility}
          disabled={submitting}
          options={[
            { value: "PUBLIC", label: t("create.public") },
            { value: "PRIVATE", label: t("create.private") },
          ]}
        />
        <span className="field__hint">{visibility === "PUBLIC" ? t("create.publicStartHint") : t("create.privateStartHint")}</span>
      </div>
      {error ? <Notice kind="error">{detailedErrorMessage(error, INVITEE_ERROR_STATUSES)}</Notice> : null}
      <button type="submit" className="btn btn--primary btn--block" disabled={submitting}>
        {submitting ? t("create.creating") : t("profile.invite.submit")}
      </button>
    </form>
  );
}

function Profile({ profile }: { profile: PlayerProfile }) {
  const { t } = useTranslation();
  const viewer = useAuthStore((state) => state.user);
  const locale = currentLocale();
  const user = profile.user;
  const name = displayName(user);
  const own = viewer !== null && viewer.id === user.id;

  return (
    <div className="profile">
      <div className="profile__main">
        <div className="page__header reveal">
          <div className="page__eyebrow">{t(user.bot ? "profile.eyebrowBot" : "profile.eyebrow")}</div>
          <h1 className="page__title">
            {name}
            <UserTag user={user} />
          </h1>
          <p className="page__lede">
            {name !== user.username ? <span className="mono">{user.username}</span> : null}
            {name !== user.username ? " · " : null}
            <span title={absoluteTime(profile.memberSince, locale)}>
              {t("profile.memberSince", { date: new Intl.DateTimeFormat(locale, { dateStyle: "long" }).format(new Date(profile.memberSince)) })}
            </span>
            {profile.lastSeen ? (
              <>
                {" · "}
                <span title={absoluteTime(profile.lastSeen, locale)}>{t("profile.lastSeen", { when: relativeTime(profile.lastSeen, locale) })}</span>
              </>
            ) : null}
          </p>
        </div>
        <div className="stats reveal" style={{ "--i": 1 } as React.CSSProperties}>
          {KINDS.map((kind) => (
            <RatingCard key={kind} kind={kind} summary={profile.ratings[kind] ?? null} />
          ))}
        </div>
        <h2 className="section__title reveal" style={{ "--i": 2 } as React.CSSProperties}>
          {t("profile.recentGames")}
        </h2>
        <div className="reveal" style={{ "--i": 3 } as React.CSSProperties}>
          <GameList games={profile.recentGames} viewer={viewer} emptyText={t("profile.noGames")} />
        </div>
      </div>
      <aside className="profile__aside reveal" style={{ "--i": 2 } as React.CSSProperties}>
        {own ? (
          <div className="card">
            <h2 className="card__title">{t("profile.yours.title")}</h2>
            <p className="card__subtitle">{t("profile.yours.lede")}</p>
            <Link to="/settings" className="btn btn--block">
              {t("nav.settings")}
            </Link>
          </div>
        ) : viewer ? (
          <InviteForm invitee={user} viewer={viewer} />
        ) : (
          <div className="card">
            <h2 className="card__title">{t("profile.invite.title")}</h2>
            <p className="card__subtitle">{t("profile.invite.signIn")}</p>
            <Link to="/login" state={{ from: `/players/${encodeURIComponent(user.username)}` }} className="btn btn--primary btn--block">
              {t("nav.logIn")}
            </Link>
          </div>
        )}
      </aside>
    </div>
  );
}

export function PlayerProfilePage() {
  const { t } = useTranslation();
  const { username = "" } = useParams();
  const { data, error, loading, reload } = useAsync(() => api.player(username), [username]);
  useDocumentTitle(data ? t("titles.profile", { name: displayName(data.user), brand: BRAND_NAME }) : t("titles.profileLoading", { brand: BRAND_NAME }));

  if (loading && !data) {
    return (
      <div className="page">
        <Loading label={t("profile.loading")} />
      </div>
    );
  }
  if (error || !data) {
    const missing = isApiError(error) && error.status === 404;
    return (
      <div className="page page--narrow">
        <h1 className="page__title" style={{ marginBottom: 12 }}>
          {missing ? t("profile.notFound") : t("profile.unavailable")}
        </h1>
        {missing ? <EmptyState>{t("profile.notFoundLede", { username })}</EmptyState> : <ErrorNotice error={error} onRetry={() => reload()} />}
        <Link to="/players" className="btn" style={{ marginTop: 8 }}>
          {t("profile.backToPlayers")}
        </Link>
      </div>
    );
  }
  return (
    <div className="page">
      <Profile profile={data} />
    </div>
  );
}
