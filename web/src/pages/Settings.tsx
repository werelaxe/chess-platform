import { useEffect, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { api, errorMessage } from "../api/client";
import { BRAND_NAME } from "../brand";
import { Choice, Notice, UserTag } from "../components/ui";
import { LOCALES, currentLocale, type Locale } from "../i18n";
import { useDocumentTitle } from "../hooks/useDocumentTitle";
import { useAuthStore } from "../store/auth";
import { changeLocale } from "../store/session";

export const DISPLAY_NAME_MAX = 40;

export function SettingsPage() {
  const { t } = useTranslation();
  const { token, user, setSession } = useAuthStore();
  const [name, setName] = useState(user?.name ?? "");
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const locale = currentLocale();
  useDocumentTitle(t("titles.settings", { brand: BRAND_NAME }));

  // The session is revalidated on page load; take the name from the fresh profile when it arrives.
  useEffect(() => setName(user?.name ?? ""), [user?.name]);

  const trimmed = name.trim();
  const unchanged = trimmed === (user?.name ?? "");

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!token || !user || unchanged) return;
    setSaving(true);
    setSaved(false);
    setError(null);
    try {
      const profile = await api.updateProfile({ displayName: trimmed || null });
      setSession(token, profile);
      setSaved(true);
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="page page--narrow">
      <div className="page__header reveal">
        <div className="page__eyebrow">
          {user ? (
            <>
              {user.username}
              <UserTag user={user} />
            </>
          ) : null}
        </div>
        <h1 className="page__title">{t("settings.title")}</h1>
        <p className="page__lede">{t("settings.lede")}</p>
      </div>
      <form className="card reveal" style={{ "--i": 1 } as React.CSSProperties} onSubmit={submit} noValidate>
        <h2 className="card__title">{t("settings.displayName.title")}</h2>
        <p className="card__subtitle">{t("settings.displayName.lede")}</p>
        <div className="field">
          <label className="field__label" htmlFor="displayName">
            {t("settings.displayName.label")}
          </label>
          <input
            id="displayName"
            className="field__input"
            value={name}
            maxLength={DISPLAY_NAME_MAX}
            autoComplete="nickname"
            placeholder={user?.username}
            onChange={(event) => {
              setName(event.target.value);
              setSaved(false);
            }}
          />
          <span className="field__hint">{t("settings.displayName.hint", { max: DISPLAY_NAME_MAX })}</span>
        </div>
        {error ? <Notice kind="error">{error}</Notice> : null}
        {saved ? <Notice kind="success">{t("settings.displayName.saved")}</Notice> : null}
        <div className="actions">
          <button type="submit" className="btn btn--primary" disabled={saving || unchanged}>
            {saving ? t("settings.saving") : t("settings.save")}
          </button>
          {user?.name ? (
            <button type="button" className="btn btn--ghost" disabled={saving} onClick={() => setName("")}>
              {t("settings.displayName.clear")}
            </button>
          ) : null}
        </div>
      </form>
      <div className="card reveal" style={{ "--i": 2, marginTop: 16 } as React.CSSProperties}>
        <h2 className="card__title">{t("settings.language.title")}</h2>
        <p className="card__subtitle">{t("settings.language.lede")}</p>
        <div className="field" style={{ marginBottom: 0 }}>
          <Choice<Locale>
            name={t("nav.language")}
            value={locale}
            onChange={(next) => void changeLocale(next)}
            options={LOCALES.map((entry) => ({ value: entry, label: t(`settings.language.names.${entry}`) }))}
          />
        </div>
      </div>
    </div>
  );
}
