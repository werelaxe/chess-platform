import { useState, type FormEvent } from "react";
import { Trans, useTranslation } from "react-i18next";
import { Link, useLocation, useNavigate } from "react-router";
import { api, errorMessage } from "../api/client";
import { PASSWORD_MAX_BYTES, PASSWORD_MIN, validatePassword, validateUsername } from "../api/credentials";
import { BRAND_NAME } from "../brand";
import { Notice, Username } from "../components/ui";
import { useDocumentTitle } from "../hooks/useDocumentTitle";
import { useAuthStore } from "../store/auth";
import { startSession } from "../store/session";

interface LocationState {
  from?: string;
}

export function AuthForm({ mode }: { mode: "login" | "register" }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const user = useAuthStore((state) => state.user);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [touched, setTouched] = useState({ username: false, password: false });
  const [submitting, setSubmitting] = useState(false);
  const [serverError, setServerError] = useState<unknown>(null);

  const usernameProblem = validateUsername(username, mode);
  const passwordProblem = validatePassword(password);
  const isRegister = mode === "register";
  const from = (location.state as LocationState | null)?.from ?? "/";
  useDocumentTitle(t(isRegister ? "titles.register" : "titles.login", { brand: BRAND_NAME }));

  async function submit(event: FormEvent) {
    event.preventDefault();
    setTouched({ username: true, password: true });
    if (usernameProblem || passwordProblem) return;
    setSubmitting(true);
    setServerError(null);
    try {
      const response = isRegister ? await api.register(username, password) : await api.login(username, password);
      startSession(response.token, response.user);
      navigate(from, { replace: true });
    } catch (error) {
      setServerError(error);
      setSubmitting(false);
    }
  }

  async function playAsGuest() {
    setSubmitting(true);
    setServerError(null);
    try {
      const response = await api.guest();
      startSession(response.token, response.user);
      navigate(from, { replace: true });
    } catch (error) {
      setServerError(error);
      setSubmitting(false);
    }
  }

  return (
    <div className="page page--narrow">
      <div className="page__header reveal">
        <div className="page__eyebrow">{t(`auth.${mode}.eyebrow`)}</div>
        <h1 className="page__title">{t(`auth.${mode}.title`)}</h1>
        <p className="page__lede">{t(`auth.${mode}.lede`)}</p>
      </div>
      {isRegister && user?.guest ? (
        <Notice kind="info">
          <Trans i18nKey="auth.guestNotice" components={{ user: <Username user={user} /> }} />
        </Notice>
      ) : null}
      <form className="card reveal" style={{ "--i": 1 } as React.CSSProperties} onSubmit={submit} noValidate>
        <div className="field">
          <label className="field__label" htmlFor="username">
            {t("auth.username")}
          </label>
          <input
            id="username"
            className="field__input"
            value={username}
            autoComplete="username"
            autoCapitalize="none"
            spellCheck={false}
            maxLength={20}
            aria-invalid={touched.username && usernameProblem !== null}
            onChange={(event) => setUsername(event.target.value)}
            onBlur={() => setTouched((state) => ({ ...state, username: true }))}
          />
          <div className="field__meta">
            {touched.username && usernameProblem ? (
              <span className="field__error">{t(`auth.usernameProblems.${usernameProblem}`)}</span>
            ) : isRegister ? (
              <span className="field__hint">{t("auth.usernameHint")}</span>
            ) : null}
          </div>
        </div>
        <div className="field">
          <label className="field__label" htmlFor="password">
            {t("auth.password")}
          </label>
          <input
            id="password"
            className="field__input"
            type="password"
            value={password}
            autoComplete={isRegister ? "new-password" : "current-password"}
            maxLength={PASSWORD_MAX_BYTES}
            aria-invalid={touched.password && passwordProblem !== null}
            onChange={(event) => setPassword(event.target.value)}
            onBlur={() => setTouched((state) => ({ ...state, password: true }))}
          />
          <div className="field__meta">
            {touched.password && passwordProblem ? (
              <span className="field__error">
                {t(`auth.passwordProblems.${passwordProblem}`, { min: PASSWORD_MIN, max: PASSWORD_MAX_BYTES })}
              </span>
            ) : isRegister ? (
              <span className="field__hint">{t("auth.passwordHint")}</span>
            ) : null}
          </div>
        </div>
        {serverError ? <Notice kind="error">{errorMessage(serverError)}</Notice> : null}
        <button type="submit" className="btn btn--primary btn--block" disabled={submitting}>
          {submitting ? t("auth.pleaseWait") : t(`auth.${mode}.submit`)}
        </button>
        <p className="muted small" style={{ marginTop: 14, textAlign: "center" }}>
          <Trans i18nKey={`auth.${mode}.switch`} components={{ link: <Link to={isRegister ? "/login" : "/register"} state={{ from }} /> }} />
        </p>
      </form>
      {user ? null : (
        <div className="guest-entry reveal" style={{ "--i": 2 } as React.CSSProperties}>
          <div className="guest-entry__rule">
            <span>{t("auth.or")}</span>
          </div>
          <button type="button" className="btn btn--block" disabled={submitting} onClick={() => void playAsGuest()}>
            {t("auth.playAsGuest")}
          </button>
          <p className="faint small guest-entry__hint">{t("auth.guestHint")}</p>
        </div>
      )}
    </div>
  );
}
