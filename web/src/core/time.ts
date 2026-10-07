import type { Locale } from "../i18n/locale";

const MINUTE = 60;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

/** "5 min. ago", "yesterday" and the like in the given language; empty for an unparsable date. */
export function relativeTime(iso: string, locale: Locale, now: number = Date.now()): string {
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return "";
  const format = new Intl.RelativeTimeFormat(locale, { numeric: "auto", style: "short" });
  const seconds = Math.max(0, Math.round((now - then) / 1000));
  if (seconds < MINUTE) return format.format(0, "second");
  if (seconds < HOUR) return format.format(-Math.round(seconds / MINUTE), "minute");
  if (seconds < DAY) return format.format(-Math.round(seconds / HOUR), "hour");
  return format.format(-Math.round(seconds / DAY), "day");
}

/** The full date and time in the given language, for a tooltip next to the relative form. */
export function absoluteTime(iso: string, locale: Locale): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "";
  return new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeStyle: "short" }).format(date);
}
