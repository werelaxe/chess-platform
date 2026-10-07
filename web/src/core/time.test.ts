import { describe, expect, it } from "vitest";
import { absoluteTime, relativeTime } from "./time";

const NOW = Date.parse("2026-10-07T12:00:00Z");

function ago(seconds: number): string {
  return new Date(NOW - seconds * 1000).toISOString();
}

describe("relativeTime", () => {
  it("rounds to the largest unit and speaks the given language", () => {
    expect(relativeTime(ago(10), "en", NOW)).toBe("now");
    expect(relativeTime(ago(5 * 60), "en", NOW)).toBe("5 min. ago");
    expect(relativeTime(ago(2 * 3600), "en", NOW)).toBe("2 hr. ago");
    expect(relativeTime(ago(24 * 3600), "en", NOW)).toBe("yesterday");
    expect(relativeTime(ago(3 * 24 * 3600), "en", NOW)).toBe("3 days ago");
    expect(relativeTime(ago(5 * 60), "ru", NOW)).not.toBe(relativeTime(ago(5 * 60), "en", NOW));
  });

  it("treats a future or invalid date gracefully", () => {
    expect(relativeTime(new Date(NOW + 60_000).toISOString(), "en", NOW)).toBe("now");
    expect(relativeTime("not a date", "en", NOW)).toBe("");
  });
});

describe("absoluteTime", () => {
  it("formats the date in the given language and is empty for an invalid one", () => {
    expect(absoluteTime("2026-10-07T12:00:00Z", "en")).toContain("2026");
    expect(absoluteTime("2026-10-07T12:00:00Z", "ru")).toContain("2026");
    expect(absoluteTime("nope", "en")).toBe("");
  });
});
