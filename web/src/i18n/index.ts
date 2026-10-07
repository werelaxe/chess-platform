import i18next from "i18next";
import { initReactI18next } from "react-i18next";
import { useAuthStore } from "../store/auth";
import en from "./en.json";
import { DEFAULT_LOCALE, LOCALES, detectLocale, isLocale, type Locale } from "./locale";
import ru from "./ru.json";

export { DEFAULT_LOCALE, LOCALES, detectLocale, isLocale, localeForLanguage, type Locale } from "./locale";

/** Where the language picked in this browser is kept; the account's choice takes precedence once signed in. */
export const LOCALE_STORAGE_KEY = "chess-platform.locale";

export const resources = {
  en: { translation: en },
  ru: { translation: ru },
} as const;

function storedLocale(): string | null {
  try {
    return localStorage.getItem(LOCALE_STORAGE_KEY);
  } catch {
    return null;
  }
}

function rememberLocale(locale: Locale): void {
  try {
    localStorage.setItem(LOCALE_STORAGE_KEY, locale);
  } catch {
    // Storage can be unavailable (private mode, quota): the choice then lasts for the page.
  }
}

function browserLanguage(): string | null {
  return typeof navigator === "undefined" ? null : navigator.language;
}

function syncDocumentLanguage(language: string): void {
  if (typeof document !== "undefined") document.documentElement.lang = language;
}

/**
 * The shared i18next instance. The resources are bundled, so it is ready synchronously as soon
 * as this module is imported; modules outside React use `i18n.t` directly.
 */
export const i18n = i18next.use(initReactI18next);

void i18n.init({
  resources,
  lng: detectLocale(useAuthStore.getState().user?.locale, storedLocale(), browserLanguage()),
  fallbackLng: DEFAULT_LOCALE,
  supportedLngs: [...LOCALES],
  interpolation: { escapeValue: false },
});

i18n.on("languageChanged", syncDocumentLanguage);
syncDocumentLanguage(i18n.language);

export function currentLocale(): Locale {
  return isLocale(i18n.language) ? i18n.language : DEFAULT_LOCALE;
}

/** Switches the UI to `locale` and remembers it in this browser. */
export async function applyLocale(locale: Locale): Promise<void> {
  rememberLocale(locale);
  if (i18n.language !== locale) await i18n.changeLanguage(locale);
}
