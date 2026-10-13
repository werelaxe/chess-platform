import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { errorMessage } from "../api/client";
import type { GameKind, GameLifecycle, UserRef, Visibility } from "../api/types";
import { botLevelLabel, displayName, hasProfile } from "../core/players";

export function profilePath(username: string): string {
  return `/players/${encodeURIComponent(username)}`;
}

/** The small tag after a name: the level of a bot, or "guest"; nothing for registered people. */
export function UserTag({ user }: { user: UserRef }) {
  const { t } = useTranslation();
  if (user.bot) return <span className="bot-tag">{user.botLevel ? botLevelLabel(user.botLevel) : t("user.botTag")}</span>;
  if (user.guest) return <span className="guest-tag">{t("user.guestTag")}</span>;
  return null;
}

/**
 * A user's display name linked to their profile (guests have none, so theirs is plain text),
 * followed by the level of a bot or the guest tag so they are recognisable but not loud.
 */
export function Username({ user, link = true }: { user: UserRef; link?: boolean }) {
  const name = displayName(user);
  return (
    <>
      {link && hasProfile(user) ? (
        <Link to={profilePath(user.username)} className="user-link" title={user.username}>
          {name}
        </Link>
      ) : (
        <span title={user.username}>{name}</span>
      )}
      <UserTag user={user} />
    </>
  );
}

/** A rating figure in the tabular style used in lists and cards. */
export function Rating({ value, title }: { value: number | null | undefined; title?: string }) {
  const { t } = useTranslation();
  if (value === null || value === undefined) return <span className="rating rating--none">{t("ratings.none")}</span>;
  return (
    <span className="rating" title={title}>
      {value}
    </span>
  );
}

export function RatedPill({ rated }: { rated: boolean }) {
  const { t } = useTranslation();
  return <span className={`pill pill--${rated ? "rated" : "casual"}`}>{rated ? t("rated.rated") : t("rated.casual")}</span>;
}

/** A labelled on/off switch. */
export function Toggle({
  checked,
  onChange,
  label,
  hint,
  disabled,
}: {
  checked: boolean;
  onChange: (checked: boolean) => void;
  label: string;
  hint?: string;
  disabled?: boolean;
}) {
  return (
    <label className={`toggle${disabled ? " is-disabled" : ""}`}>
      <input type="checkbox" className="toggle__input" checked={checked} disabled={disabled} onChange={(event) => onChange(event.target.checked)} />
      <span className="toggle__track" aria-hidden="true">
        <span className="toggle__thumb" />
      </span>
      <span className="toggle__text">
        <span className="toggle__label">{label}</span>
        {hint ? <span className="toggle__hint">{hint}</span> : null}
      </span>
    </label>
  );
}

export function KindPill({ kind }: { kind: GameKind }) {
  const { t } = useTranslation();
  return <span className={`pill pill--${kind === "QUANTUM" ? "quantum" : "classic"}`}>{t(`kinds.${kind}`)}</span>;
}

export function LifecyclePill({ status }: { status: GameLifecycle }) {
  const { t } = useTranslation();
  return (
    <span className={`pill pill--${status.toLowerCase()}`}>
      <span className="pill__dot" />
      {t(`lifecycle.${status}`)}
    </span>
  );
}

export function VisibilityPill({ visibility }: { visibility: Visibility }) {
  const { t } = useTranslation();
  if (visibility === "PUBLIC") return null;
  return <span className="pill pill--private">{t("visibility.private")}</span>;
}

export function Notice({
  kind = "info",
  children,
  action,
}: {
  kind?: "info" | "error" | "success";
  children: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div className={`notice notice--${kind}`} role={kind === "error" ? "alert" : "status"}>
      <div className="notice__body">{children}</div>
      {action ? <div className="notice__action">{action}</div> : null}
    </div>
  );
}

export function ErrorNotice({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const { t } = useTranslation();
  return (
    <Notice
      kind="error"
      action={
        onRetry ? (
          <button type="button" className="btn btn--small" onClick={onRetry}>
            {t("common.retry")}
          </button>
        ) : undefined
      }
    >
      {errorMessage(error)}
    </Notice>
  );
}

export function Loading({ label }: { label?: string }) {
  const { t } = useTranslation();
  return (
    <div className="loading" role="status">
      <span className="spinner" aria-hidden="true" />
      {label ?? t("common.loading")}
    </div>
  );
}

export function EmptyState({ children }: { children: ReactNode }) {
  return <div className="empty">{children}</div>;
}

export function Choice<T extends string>({
  value,
  options,
  onChange,
  name,
  disabled,
}: {
  value: T;
  options: { value: T; label: string; quantum?: boolean }[];
  onChange: (value: T) => void;
  name: string;
  disabled?: boolean;
}) {
  return (
    <div className="choice" role="radiogroup" aria-label={name}>
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          role="radio"
          aria-checked={option.value === value}
          disabled={disabled}
          className={`choice__option${option.value === value ? " is-selected" : ""}${option.quantum ? " is-quantum" : ""}`}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
