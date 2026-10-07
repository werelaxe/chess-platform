// The languages the client is translated into and how the one to show is chosen. Pure, so that
// it can be tested without a browser; the browser-bound parts live in index.ts.

export const LOCALES = ["en", "ru"] as const;

export type Locale = (typeof LOCALES)[number];

export const DEFAULT_LOCALE: Locale = "en";

export function isLocale(value: unknown): value is Locale {
  return typeof value === "string" && (LOCALES as readonly string[]).includes(value);
}

/** The UI language for a BCP 47 tag: Russian in any region or script, English for everything else. */
export function localeForLanguage(tag: string | null | undefined): Locale {
  return tag && /^ru(?:[-_]|$)/i.test(tag) ? "ru" : DEFAULT_LOCALE;
}

/**
 * The language to start with, in order of preference: the one saved on the account of the
 * signed-in user, the one picked earlier in this browser, then the browser's own language.
 */
export function detectLocale(
  account: string | null | undefined,
  stored: string | null | undefined,
  browserLanguage: string | null | undefined,
): Locale {
  if (isLocale(account)) return account;
  if (isLocale(stored)) return stored;
  return localeForLanguage(browserLanguage);
}
