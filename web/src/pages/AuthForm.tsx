import { useState, type FormEvent } from "react";
import { Link, useLocation, useNavigate } from "react-router";
import { api, errorMessage, isApiError } from "../api/client";
import { useAuthStore } from "../store/auth";
import { Notice } from "../components/ui";

const USERNAME_PATTERN = /^[A-Za-z0-9_]{3,20}$/;
const PASSWORD_MIN = 8;
const PASSWORD_MAX = 72;

export function validateUsername(username: string): string | null {
  if (username.length === 0) return "Enter a username.";
  if (username.length < 3 || username.length > 20) return "Username must be 3 to 20 characters long.";
  if (!USERNAME_PATTERN.test(username)) return "Only letters, digits and underscores are allowed.";
  return null;
}

export function validatePassword(password: string): string | null {
  if (password.length === 0) return "Enter a password.";
  if (password.length < PASSWORD_MIN) return `Password must be at least ${PASSWORD_MIN} characters long.`;
  if (password.length > PASSWORD_MAX) return `Password must be at most ${PASSWORD_MAX} characters long.`;
  return null;
}

interface LocationState {
  from?: string;
}

export function AuthForm({ mode }: { mode: "login" | "register" }) {
  const navigate = useNavigate();
  const location = useLocation();
  const setSession = useAuthStore((state) => state.setSession);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [touched, setTouched] = useState({ username: false, password: false });
  const [submitting, setSubmitting] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);

  const usernameError = validateUsername(username);
  const passwordError = validatePassword(password);
  const isRegister = mode === "register";
  const from = (location.state as LocationState | null)?.from ?? "/";

  async function submit(event: FormEvent) {
    event.preventDefault();
    setTouched({ username: true, password: true });
    if (usernameError || passwordError) return;
    setSubmitting(true);
    setServerError(null);
    try {
      const response = isRegister ? await api.register(username, password) : await api.login(username, password);
      setSession(response.token, response.user);
      navigate(from, { replace: true });
    } catch (error) {
      if (isApiError(error) && error.status === 401 && !isRegister) setServerError("Wrong username or password.");
      else if (isApiError(error) && (error.code === "username_taken" || (error.status === 409 && isRegister))) setServerError("That username is already taken.");
      else setServerError(errorMessage(error));
      setSubmitting(false);
    }
  }

  return (
    <div className="page page--narrow">
      <div className="page__header reveal">
        <div className="page__eyebrow">{isRegister ? "New account" : "Welcome back"}</div>
        <h1 className="page__title">{isRegister ? "Register" : "Log in"}</h1>
        <p className="page__lede">
          {isRegister ? "Pick a name other players will see." : "Sign in to create games, join open ones and make moves."}
        </p>
      </div>
      <form className="card reveal" style={{ "--i": 1 } as React.CSSProperties} onSubmit={submit} noValidate>
        <div className="field">
          <label className="field__label" htmlFor="username">
            Username
          </label>
          <input
            id="username"
            className="field__input"
            value={username}
            autoComplete="username"
            autoCapitalize="none"
            spellCheck={false}
            maxLength={20}
            aria-invalid={touched.username && usernameError !== null}
            onChange={(event) => setUsername(event.target.value)}
            onBlur={() => setTouched((state) => ({ ...state, username: true }))}
          />
          <div className="field__meta">
            {touched.username && usernameError ? (
              <span className="field__error">{usernameError}</span>
            ) : isRegister ? (
              <span className="field__hint">3 to 20 characters: letters, digits and underscores.</span>
            ) : null}
          </div>
        </div>
        <div className="field">
          <label className="field__label" htmlFor="password">
            Password
          </label>
          <input
            id="password"
            className="field__input"
            type="password"
            value={password}
            autoComplete={isRegister ? "new-password" : "current-password"}
            maxLength={72}
            aria-invalid={touched.password && passwordError !== null}
            onChange={(event) => setPassword(event.target.value)}
            onBlur={() => setTouched((state) => ({ ...state, password: true }))}
          />
          <div className="field__meta">
            {touched.password && passwordError ? (
              <span className="field__error">{passwordError}</span>
            ) : isRegister ? (
              <span className="field__hint">8 to 72 characters.</span>
            ) : null}
          </div>
        </div>
        {serverError ? <Notice kind="error">{serverError}</Notice> : null}
        <button type="submit" className="btn btn--primary btn--block" disabled={submitting}>
          {submitting ? "Please wait" : isRegister ? "Create account" : "Log in"}
        </button>
        <p className="muted small" style={{ marginTop: 14, textAlign: "center" }}>
          {isRegister ? (
            <>
              Already have an account? <Link to="/login" state={{ from }}>Log in</Link>
            </>
          ) : (
            <>
              No account yet? <Link to="/register" state={{ from }}>Register</Link>
            </>
          )}
        </p>
      </form>
    </div>
  );
}
