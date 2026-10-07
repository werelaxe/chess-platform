import type { ReactNode } from "react";
import { errorMessage } from "../api/client";
import type { GameKind, GameLifecycle, UserRef, Visibility } from "../api/types";

/** A user's name; guest accounts carry a small tag so they are recognisable but not loud. */
export function Username({ user }: { user: UserRef }) {
  return (
    <>
      {user.username}
      {user.guest ? <span className="guest-tag">guest</span> : null}
    </>
  );
}

export function KindPill({ kind }: { kind: GameKind }) {
  return <span className={`pill pill--${kind === "QUANTUM" ? "quantum" : "classic"}`}>{kind === "QUANTUM" ? "Quantum" : "Classic"}</span>;
}

const LIFECYCLE_LABELS: Record<GameLifecycle, string> = {
  WAITING: "Open",
  ACTIVE: "Live",
  FINISHED: "Finished",
};

export function LifecyclePill({ status }: { status: GameLifecycle }) {
  return (
    <span className={`pill pill--${status.toLowerCase()}`}>
      <span className="pill__dot" />
      {LIFECYCLE_LABELS[status]}
    </span>
  );
}

export function VisibilityPill({ visibility }: { visibility: Visibility }) {
  if (visibility === "PUBLIC") return null;
  return <span className="pill pill--private">Private</span>;
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
  return (
    <Notice
      kind="error"
      action={
        onRetry ? (
          <button type="button" className="btn btn--small" onClick={onRetry}>
            Retry
          </button>
        ) : undefined
      }
    >
      {errorMessage(error)}
    </Notice>
  );
}

export function Loading({ label = "Loading" }: { label?: string }) {
  return (
    <div className="loading" role="status">
      <span className="spinner" aria-hidden="true" />
      {label}
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
