import { describe, expect, it } from "vitest";
import { hasGuestPrefix, validatePassword, validateUsername } from "./credentials";

describe("validateUsername", () => {
  it("accepts ordinary names in both modes", () => {
    expect(validateUsername("alice", "login")).toBeNull();
    expect(validateUsername("alice", "register")).toBeNull();
    expect(validateUsername("Guest_42", "register")).toBeNull();
    expect(validateUsername("guestly", "register")).toBeNull();
  });

  it("rejects the reserved guest prefix on registration, case-insensitively", () => {
    expect(validateUsername("guest-123456", "register")).toBe("reserved");
    expect(validateUsername("Guest-abc", "register")).toBe("reserved");
    expect(validateUsername("GUEST-", "register")).toBe("reserved");
  });

  it("reports the generic character rule for the prefix on login", () => {
    // Guests cannot log in with a password anyway; the dash already fails the character rule.
    expect(validateUsername("guest-123456", "login")).toBe("characters");
  });

  it("checks the length and the allowed characters", () => {
    expect(validateUsername("", "register")).toBe("empty");
    expect(validateUsername("ab", "register")).toBe("length");
    expect(validateUsername("a".repeat(21), "register")).toBe("length");
    expect(validateUsername("bad name", "register")).toBe("characters");
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
    expect(validatePassword("")).toBe("empty");
    expect(validatePassword("short")).toBe("short");
    expect(validatePassword("longenough")).toBeNull();
    expect(validatePassword("a".repeat(72))).toBeNull();
    expect(validatePassword("a".repeat(73))).toBe("long");
    // 36 two-byte characters are 72 bytes; one more crosses the limit while staying under 73 characters.
    expect(validatePassword("é".repeat(36))).toBeNull();
    expect(validatePassword("é".repeat(37))).toBe("long");
  });
});
