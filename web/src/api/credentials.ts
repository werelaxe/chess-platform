// Client-side mirror of the server's credential rules (ARCHITECTURE.md 2.2), so that obvious
// mistakes are reported before a request is made. The server remains the source of truth.

const USERNAME_PATTERN = /^[A-Za-z0-9_]{3,20}$/;
const PASSWORD_MIN = 8;
/** The server limits the UTF-8 encoding of the password (bcrypt input), not its character count. */
export const PASSWORD_MAX_BYTES = 72;
/** Guest accounts are named `guest-NNNNNN`; the prefix cannot be registered (case-insensitive). */
export const GUEST_PREFIX = "guest-";
export const RESERVED_PREFIX_MESSAGE = "Names starting with guest- are reserved";

export function hasGuestPrefix(username: string): boolean {
  return username.toLowerCase().startsWith(GUEST_PREFIX);
}

/**
 * Returns the problem with a username, or null when it is acceptable. Registration additionally
 * refuses the reserved guest prefix with the server's own wording.
 */
export function validateUsername(username: string, mode: "login" | "register"): string | null {
  if (username.length === 0) return "Enter a username.";
  if (mode === "register" && hasGuestPrefix(username)) return RESERVED_PREFIX_MESSAGE;
  if (username.length < 3 || username.length > 20) return "Username must be 3 to 20 characters long.";
  if (!USERNAME_PATTERN.test(username)) return "Only letters, digits and underscores are allowed.";
  return null;
}

export function validatePassword(password: string): string | null {
  if (password.length === 0) return "Enter a password.";
  if (password.length < PASSWORD_MIN) return `Password must be at least ${PASSWORD_MIN} characters long.`;
  if (new TextEncoder().encode(password).length > PASSWORD_MAX_BYTES) {
    return `Password must be at most ${PASSWORD_MAX_BYTES} bytes long; a character outside ASCII takes 2 to 4 bytes.`;
  }
  return null;
}
