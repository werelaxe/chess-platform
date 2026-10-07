import { useEffect, useState } from "react";
import { Link, NavLink, Outlet, useLocation, useNavigate } from "react-router";
import { api, isApiError } from "../api/client";
import { BRAND_NAME, BRAND_TAGLINE } from "../brand";
import { useAuthStore } from "../store/auth";
import { Username } from "./ui";

function navClass({ isActive }: { isActive: boolean }): string {
  return `nav__link${isActive ? " is-active" : ""}`;
}

export function Layout() {
  const { token, user, setSession, clearSession } = useAuthStore();
  const [open, setOpen] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();

  // Close the mobile menu whenever the route changes.
  useEffect(() => setOpen(false), [location.pathname]);

  // Validate a persisted session once per page load; a stale token is dropped by the client.
  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    api
      .me()
      .then((me) => {
        if (!cancelled) setSession(token, me);
      })
      .catch((error: unknown) => {
        if (!cancelled && isApiError(error) && error.status === 401) clearSession();
      });
    return () => {
      cancelled = true;
    };
    // The token is the only input that should re-run this check.
  }, [token]);

  function logout() {
    clearSession();
    navigate("/");
  }

  return (
    <div className="app">
      <header className={`nav${open ? " nav--open" : ""}`}>
        <div className="nav__inner">
          <Link to="/" className="nav__brand" aria-label={`${BRAND_NAME} home`}>
            <img className="nav__brand-mark" src="/pieces/Chess_nlt45.svg" alt="" aria-hidden="true" />
            <span>{BRAND_NAME}</span>
            <span className="nav__brand-sub">{BRAND_TAGLINE}</span>
          </Link>
          <div className="nav__menu">
            <nav className="nav__links" aria-label="Main">
              <NavLink to="/" end className={navClass}>
                Lobby
              </NavLink>
              {user ? (
                <NavLink to="/games/mine" className={navClass}>
                  My games
                </NavLink>
              ) : null}
              <NavLink to="/rules" className={navClass}>
                Rules
              </NavLink>
              <NavLink to="/about" className={navClass}>
                About
              </NavLink>
            </nav>
            <div className="nav__spacer" />
            <div className="nav__user">
              {user ? (
                <>
                  <span className="nav__username">
                    <Username user={user} />
                  </span>
                  {user.guest ? (
                    <Link to="/register" className="nav__hint">
                      <span className="nav__hint-long">Register to keep your games</span>
                      <span className="nav__hint-short">Register</span>
                    </Link>
                  ) : null}
                  <button type="button" className="btn btn--ghost btn--small" onClick={logout}>
                    Log out
                  </button>
                </>
              ) : (
                <>
                  <Link to="/login" className="btn btn--ghost btn--small">
                    Log in
                  </Link>
                  <Link to="/register" className="btn btn--primary btn--small">
                    Register
                  </Link>
                </>
              )}
            </div>
          </div>
          <button
            type="button"
            className="nav__burger"
            aria-label={open ? "Close menu" : "Open menu"}
            aria-expanded={open}
            onClick={() => setOpen((value) => !value)}
          >
            {open ? "✕" : "☰"}
          </button>
        </div>
      </header>
      <main className="app__main">
        <Outlet />
      </main>
      <footer className="footer">
        <div className="footer__inner">
          <span>
            {BRAND_NAME} &middot; {BRAND_TAGLINE}
          </span>
          <span>
            Chess pieces by Colin M.L. Burnett, CC BY-SA 3.0 &middot; <Link to="/about">About</Link>
          </span>
        </div>
      </footer>
    </div>
  );
}
