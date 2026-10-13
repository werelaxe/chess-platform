import { describe, expect, it } from "vitest";
import { createGameRequest } from "./newGame";

describe("createGameRequest", () => {
  it("keeps the visibility choice for an open game", () => {
    expect(
      createGameRequest({ kind: "QUANTUM", visibility: "PUBLIC", color: "RANDOM", rated: true, opponentUsername: null, guest: false }),
    ).toStrictEqual({ kind: "QUANTUM", color: "RANDOM", rated: true, visibility: "PUBLIC", opponent: "OPEN" });
    expect(
      createGameRequest({ kind: "CLASSIC", visibility: "PRIVATE", color: "WHITE", rated: false, opponentUsername: "", guest: false }),
    ).toStrictEqual({ kind: "CLASSIC", color: "WHITE", rated: false, visibility: "PRIVATE", opponent: "OPEN" });
  });

  it("keeps the visibility choice for an invitation and names the invitee", () => {
    expect(
      createGameRequest({ kind: "CLASSIC", visibility: "PUBLIC", color: "BLACK", rated: true, opponentUsername: " bob ", guest: false }),
    ).toStrictEqual({ kind: "CLASSIC", color: "BLACK", rated: true, visibility: "PUBLIC", opponent: "USER", opponentUsername: "bob" });
    expect(
      createGameRequest({ kind: "QUANTUM", visibility: "PRIVATE", color: "WHITE", rated: true, opponentUsername: "fianchetto", guest: false }),
    ).toStrictEqual({ kind: "QUANTUM", color: "WHITE", rated: true, visibility: "PRIVATE", opponent: "USER", opponentUsername: "fianchetto" });
  });

  it("never asks for a rated game on behalf of a guest", () => {
    expect(
      createGameRequest({ kind: "QUANTUM", visibility: "PUBLIC", color: "RANDOM", rated: true, opponentUsername: null, guest: true }),
    ).toMatchObject({ rated: false });
    expect(
      createGameRequest({ kind: "QUANTUM", visibility: "PUBLIC", color: "RANDOM", rated: true, opponentUsername: "bot", guest: true }),
    ).toMatchObject({ rated: false, opponent: "USER" });
  });
});
