import { describe, expect, it } from "vitest";
import { detectLocale, isLocale, localeForLanguage } from "./locale";

describe("isLocale", () => {
  it("accepts only the translated languages", () => {
    expect(isLocale("en")).toBe(true);
    expect(isLocale("ru")).toBe(true);
    expect(isLocale("de")).toBe(false);
    expect(isLocale("EN")).toBe(false);
    expect(isLocale(null)).toBe(false);
    expect(isLocale(undefined)).toBe(false);
  });
});

describe("localeForLanguage", () => {
  it("maps any Russian tag to ru and everything else to en", () => {
    expect(localeForLanguage("ru")).toBe("ru");
    expect(localeForLanguage("ru-RU")).toBe("ru");
    expect(localeForLanguage("RU")).toBe("ru");
    expect(localeForLanguage("ru_BY")).toBe("ru");
    expect(localeForLanguage("en-US")).toBe("en");
    expect(localeForLanguage("de")).toBe("en");
    expect(localeForLanguage("rum")).toBe("en");
    expect(localeForLanguage("")).toBe("en");
    expect(localeForLanguage(null)).toBe("en");
    expect(localeForLanguage(undefined)).toBe("en");
  });
});

describe("detectLocale", () => {
  it("prefers the account, then the browser's stored choice, then the browser language", () => {
    expect(detectLocale("ru", "en", "en-US")).toBe("ru");
    expect(detectLocale(null, "ru", "en-US")).toBe("ru");
    expect(detectLocale(null, null, "ru-RU")).toBe("ru");
    expect(detectLocale(null, null, "fr")).toBe("en");
    expect(detectLocale(undefined, undefined, undefined)).toBe("en");
  });

  it("skips values that are not a translated language", () => {
    expect(detectLocale("de", "ru", "en")).toBe("ru");
    expect(detectLocale(null, "xx", "ru")).toBe("ru");
  });
});
