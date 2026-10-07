import { api } from "../api/client";
import type { UserProfile } from "../api/types";
import { applyLocale, currentLocale, type Locale } from "../i18n";
import { useAuthStore } from "./auth";

/**
 * Stores a session, fresh from login, registration or guest creation or re-validated on page
 * load, and aligns the UI language with the account: the account's saved choice wins, and an
 * account without one takes the language in use.
 */
export function startSession(token: string, user: UserProfile): void {
  useAuthStore.getState().setSession(token, user);
  if (user.locale) void applyLocale(user.locale);
  else void saveAccountLocale(currentLocale());
}

/**
 * Saves the UI language on the signed-in account. Best effort: the choice is already kept in
 * this browser, so a failure costs nothing but the sync to other devices.
 */
export async function saveAccountLocale(locale: Locale): Promise<void> {
  const { token } = useAuthStore.getState();
  if (!token) return;
  try {
    const profile = await api.updateProfile({ locale });
    // Only refresh a session that is still the same one; a logout meanwhile must stick.
    const state = useAuthStore.getState();
    if (state.token === token) state.setSession(token, profile);
  } catch {
    // Ignored, see above.
  }
}

/** The language switcher: applies at once, remembers in this browser and on the account when signed in. */
export async function changeLocale(locale: Locale): Promise<void> {
  await applyLocale(locale);
  await saveAccountLocale(locale);
}
