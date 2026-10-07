import { describe, expect, it } from "vitest";
import { RESERVED_PREFIX_MESSAGE, hasGuestPrefix, validatePassword, validateUsername } from "./credentials";

describe("validateUsername", () => {
  it("accepts ordinary names in both modes", () => {
    expect(validateUsername("alice", "login")).toBeNull();
    expect(validateUsername("alice", "register")).toBeNull();
    expect(validateUsername("Guest_42", "register")).toBeNull();
    expect(validateUsername("guestly", "register")).toBeNull();
  });

  it("rejects the reserved guest prefix on registration, case-insensitively", () => {
    expect(validateUsername("guest-123456", "register")).toBe(RESERVED_PREFIX_MESSAGE);
    expect(validateUsername("Guest-abc", "register")).toBe(RESERVED_PREFIX_MESSAGE);
    expect(validateUsername("GUEST-", "register")).toBe(RESERVED_PREFIX_MESSAGE);
    expect(RESERVED_PREFIX_MESSAGE).toBe("Names starting with guest- are reserved");
  });

  it("reports the generic character rule for the prefix on login", () => {
    // Guests cannot log in with a password anyway; the dash already fails the character rule.
    expect(validateUsername("guest-123456", "login")).toBe("Only letters, digits and underscores are allowed.");
  });

  it("checks the length and the allowed characters", () => {
    expect(validateUsername("", "register")).toBe("Enter a username.");
    expect(validateUsername("ab", "register")).toBe("Username must be 3 to 20 characters long.");
    expect(validateUsername("a".repeat(21), "register")).toBe("Username must be 3 to 20 characters long.");
    expect(validateUsername("bad name", "register")).toBe("Only letters, digits and underscores are allowed.");
  });
});

describe("hasGuestPrefix", () => {
  it("matches the prefix regardless of case", () => {
    expect(hasGuestPrefix("guest-000001")).toBe(true);
    expect(hasGuestPrefix("GUEST-x")).toBe(true);
    expect(hasGuestPrefix("guest_1")).toBe(false);
    expect(hasGuestPrefix("alice")).toBe(false);
  });
});

describe("validatePassword", () => {
  it("enforces the minimum length and the byte limit", () => {
    expect(validatePassword("")).toBe("Enter a password.");
    expect(validatePassword("short")).toBe("Password must be at least 8 characters long.");
    expect(validatePassword("longenough")).toBeNull();
    expect(validatePassword("a".repeat(72))).toBeNull();
    expect(validatePassword("a".repeat(73))).not.toBeNull();
    // 36 two-byte characters are 72 bytes; one more crosses the limit while staying under 73 characters.
    expect(validatePassword("é".repeat(36))).toBeNull();
    expect(validatePassword("é".repeat(37))).not.toBeNull();
  });
});
