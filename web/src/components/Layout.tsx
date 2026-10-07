import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, NavLink, Outlet, useLocation, useNavigate } from "react-router";
import { api, isApiError } from "../api/client";
import { BRAND_NAME } from "../brand";
import { LOCALES, currentLocale } from "../i18n";
import { useAuthStore } from "../store/auth";
import { changeLocale, startSession } from "../store/session";
import { Username } from "./ui";

function navClass({ isActive }: { isActive: boolean }): string {
  return `nav__link${isActive ? " is-active" : ""}`;
}

/** EN / RU toggle; the choice applies at once and is saved in the browser and on the account. */
function LanguageSwitcher() {
  const { t } = useTranslation();
  const active = currentLocale();
  return (
    <div className="nav__lang" role="group" aria-label={t("nav.language")}>
      {LOCALES.map((locale) => (
        <button
          key={locale}
          type="button"
          lang={locale}
          className={`nav__lang-btn${locale === active ? " is-active" : ""}`}
          aria-pressed={locale === active}
          onClick={() => void changeLocale(locale)}
        >
          {locale.toUpperCase()}
        </button>
      ))}
    </div>
  );
}

export function Layout() {
  const { t } = useTranslation();
  const { token, user, clearSession } = useAuthStore();
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
        if (!cancelled) startSession(token, me);
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
          <Link to="/" className="nav__brand" aria-label={t("nav.home", { brand: BRAND_NAME })}>
            <img className="nav__brand-mark" src="/pieces/Chess_nlt45.svg" alt="" aria-hidden="true" />
            <span>{BRAND_NAME}</span>
            <span className="nav__brand-sub">{t("brand.tagline")}</span>
          </Link>
          <div className="nav__menu">
            <nav className="nav__links" aria-label={t("nav.main")}>
              <NavLink to="/" end className={navClass}>
                {t("nav.lobby")}
              </NavLink>
              {user ? (
                <NavLink to="/games/mine" className={navClass}>
                  {t("nav.myGames")}
                </NavLink>
              ) : null}
              <NavLink to="/rules" className={navClass}>
                {t("nav.rules")}
              </NavLink>
              <NavLink to="/about" className={navClass}>
                {t("nav.about")}
              </NavLink>
            </nav>
            <div className="nav__spacer" />
            <LanguageSwitcher />
            <div className="nav__user">
              {user ? (
                <>
                  <span className="nav__username">
                    <Username user={user} />
                  </span>
                  {user.guest ? (
                    <Link to="/register" className="nav__hint">
                      <span className="nav__hint-long">{t("nav.registerToKeep")}</span>
                      <span className="nav__hint-short">{t("nav.register")}</span>
                    </Link>
                  ) : null}
                  <button type="button" className="btn btn--ghost btn--small" onClick={logout}>
                    {t("nav.logOut")}
                  </button>
                </>
              ) : (
                <>
                  <Link to="/login" className="btn btn--ghost btn--small">
                    {t("nav.logIn")}
                  </Link>
                  <Link to="/register" className="btn btn--primary btn--small">
                    {t("nav.register")}
                  </Link>
                </>
              )}
            </div>
          </div>
          <button
            type="button"
            className="nav__burger"
            aria-label={open ? t("nav.closeMenu") : t("nav.openMenu")}
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
            {BRAND_NAME} &middot; {t("brand.tagline")}
          </span>
          <span>
            {t("footer.credits")} &middot; <Link to="/about">{t("footer.about")}</Link>
          </span>
        </div>
      </footer>
    </div>
  );
}
