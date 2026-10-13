import { afterEach, describe, expect, it } from "vitest";
import { i18n } from "../i18n";
import en from "../i18n/en.json";
import ru from "../i18n/ru.json";
import { ApiError, detailedErrorMessage, errorMessage } from "./client";

const INVITEE_STATUSES = [400, 404];

afterEach(() => i18n.changeLanguage("en"));

describe("errorMessage", () => {
  it("translates known codes, then known statuses, then falls back to the status number", () => {
    expect(errorMessage(new ApiError(403, "not_invited", "This invitation is for somebody else"))).toBe(en.errors.code.not_invited);
    expect(errorMessage(new ApiError(403, "mystery", "whatever"))).toBe(en.errors.status["403"]);
    expect(errorMessage(new ApiError(418, "mystery", "whatever"))).toBe("Request failed with status 418");
  });

  it("follows the UI language", async () => {
    await i18n.changeLanguage("ru");
    expect(errorMessage(new ApiError(403, "not_invited", "x"))).toBe(ru.errors.code.not_invited);
  });
});

describe("detailedErrorMessage", () => {
  it("shows the server's wording for the listed statuses only", () => {
    expect(detailedErrorMessage(new ApiError(400, "validation", "You cannot invite yourself"), INVITEE_STATUSES)).toBe("You cannot invite yourself");
    expect(detailedErrorMessage(new ApiError(404, "not_found", "Player not found"), INVITEE_STATUSES)).toBe("Player not found");
    expect(detailedErrorMessage(new ApiError(403, "not_invited", "x"), INVITEE_STATUSES)).toBe(en.errors.code.not_invited);
    expect(detailedErrorMessage(new ApiError(0, "network", "Cannot reach the server"), INVITEE_STATUSES)).toBe(en.errors.code.network);
  });

  it("treats other errors like errorMessage", () => {
    expect(detailedErrorMessage(new Error("boom"), INVITEE_STATUSES)).toBe("boom");
    expect(detailedErrorMessage(null, INVITEE_STATUSES)).toBe(en.errors.unexpected);
  });
});
