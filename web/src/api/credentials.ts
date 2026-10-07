// Client-side mirror of the server's credential rules (ARCHITECTURE.md 2.2), so that obvious
// mistakes are reported before a request is made. The server remains the source of truth. The
// validators return a problem code; the form turns it into a message in the UI language.

const USERNAME_PATTERN = /^[A-Za-z0-9_]{3,20}$/;
export const PASSWORD_MIN = 8;
/** The server limits the UTF-8 encoding of the password (bcrypt input), not its character count. */
export const PASSWORD_MAX_BYTES = 72;
/** Guest accounts are named `guest-NNNNNN`; the prefix cannot be registered (case-insensitive). */
export const GUEST_PREFIX = "guest-";

export type UsernameProblem = "empty" | "reserved" | "length" | "characters";

export type PasswordProblem = "empty" | "short" | "long";

export function hasGuestPrefix(username: string): boolean {
  return username.toLowerCase().startsWith(GUEST_PREFIX);
}

/**
 * Returns the problem with a username, or null when it is acceptable. Registration additionally
 * refuses the reserved guest prefix, which the server rejects as well.
 */
export function validateUsername(username: string, mode: "login" | "register"): UsernameProblem | null {
  if (username.length === 0) return "empty";
  if (mode === "register" && hasGuestPrefix(username)) return "reserved";
  if (username.length < 3 || username.length > 20) return "length";
  if (!USERNAME_PATTERN.test(username)) return "characters";
  return null;
}

export function validatePassword(password: string): PasswordProblem | null {
  if (password.length === 0) return "empty";
  if (password.length < PASSWORD_MIN) return "short";
  if (new TextEncoder().encode(password).length > PASSWORD_MAX_BYTES) return "long";
  return null;
}
