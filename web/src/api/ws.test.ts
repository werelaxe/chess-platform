import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { GameSocket, gameSocketUrl, type SocketState } from "./ws";

/** Minimal stand-in for the browser's WebSocket: the test drives open, messages and closes. */
class FakeWebSocket {
  static readonly CONNECTING = 0;
  static readonly OPEN = 1;
  static readonly CLOSING = 2;
  static readonly CLOSED = 3;
  static instances: FakeWebSocket[] = [];

  readyState = FakeWebSocket.CONNECTING;
  sent: string[] = [];
  closeCalls = 0;
  onopen: (() => void) | null = null;
  onmessage: ((message: { data: unknown }) => void) | null = null;
  onclose: ((event: { code: number }) => void) | null = null;
  onerror: (() => void) | null = null;

  constructor(readonly url: string) {
    FakeWebSocket.instances.push(this);
  }

  open(): void {
    this.readyState = FakeWebSocket.OPEN;
    this.onopen?.();
  }

  receive(payload: unknown): void {
    this.onmessage?.({ data: JSON.stringify(payload) });
  }

  serverClose(code: number): void {
    this.readyState = FakeWebSocket.CLOSED;
    this.onclose?.({ code });
  }

  send(data: string): void {
    this.sent.push(data);
  }

  close(): void {
    this.closeCalls += 1;
    this.readyState = FakeWebSocket.CLOSED;
  }
}

const PING = JSON.stringify({ type: "ping" });
/** Longest first reconnect delay: base 1 s plus up to 300 ms of jitter. */
const FIRST_RECONNECT_MS = 1_500;

function openSocket() {
  const states: SocketState[] = [];
  const events: unknown[] = [];
  const socket = new GameSocket("game-1", {
    onEvent: (event) => events.push(event),
    onStateChange: (state) => states.push(state),
  });
  socket.connect();
  const ws = FakeWebSocket.instances[0]!;
  ws.open();
  return { socket, ws, states, events };
}

describe("gameSocketUrl", () => {
  it("derives the scheme from the page and sends no credentials", () => {
    expect(gameSocketUrl("abc", { protocol: "http:", host: "localhost:5173" } as Location)).toBe("ws://localhost:5173/api/games/abc/ws");
    expect(gameSocketUrl("a/b", { protocol: "https:", host: "example.org" } as Location)).toBe("wss://example.org/api/games/a%2Fb/ws");
  });
});

describe("GameSocket", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    FakeWebSocket.instances = [];
    vi.stubGlobal("WebSocket", FakeWebSocket);
    vi.stubGlobal("window", { location: { protocol: "http:", host: "localhost" } });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it("forwards events and swallows pongs", () => {
    const { ws, states, events } = openSocket();
    expect(states).toEqual(["open"]);
    ws.receive({ type: "pong" });
    ws.receive({ type: "move", ply: 0, move: { type: "normal", from: "e2", to: "e4" }, status: { type: "ongoing" } });
    expect(events).toHaveLength(1);
  });

  it("drops a connection whose ping is not answered and reconnects", () => {
    const { ws, states } = openSocket();
    vi.advanceTimersByTime(30_000);
    expect(ws.sent).toEqual([PING]);
    vi.advanceTimersByTime(9_999);
    expect(ws.closeCalls).toBe(0);
    vi.advanceTimersByTime(1);
    expect(ws.closeCalls).toBe(1);
    expect(states.at(-1)).toBe("reconnecting");
    vi.advanceTimersByTime(FIRST_RECONNECT_MS);
    expect(FakeWebSocket.instances).toHaveLength(2);
    FakeWebSocket.instances[1]!.open();
    expect(states.at(-1)).toBe("open");
  });

  it("keeps a connection whose pings are answered", () => {
    const { ws, states } = openSocket();
    for (let round = 0; round < 3; round += 1) {
      vi.advanceTimersByTime(30_000);
      ws.receive({ type: "pong" });
    }
    vi.advanceTimersByTime(10_000);
    expect(ws.sent).toHaveLength(3);
    expect(ws.closeCalls).toBe(0);
    expect(states).toEqual(["open"]);
    expect(FakeWebSocket.instances).toHaveLength(1);
  });

  it("reconnects with backoff after an abnormal close", () => {
    const { ws, states } = openSocket();
    ws.serverClose(1006);
    expect(states.at(-1)).toBe("reconnecting");
    vi.advanceTimersByTime(FIRST_RECONNECT_MS);
    expect(FakeWebSocket.instances).toHaveLength(2);
  });

  it.each([1008, 4000, 4999])("stops for good after close code %i", (code) => {
    const { ws, states } = openSocket();
    ws.serverClose(code);
    expect(states.at(-1)).toBe("closed");
    vi.advanceTimersByTime(60_000);
    expect(FakeWebSocket.instances).toHaveLength(1);
  });

  it("close() ends the subscription", () => {
    const { socket, ws, states } = openSocket();
    socket.close();
    expect(ws.closeCalls).toBe(1);
    expect(states.at(-1)).toBe("closed");
    vi.advanceTimersByTime(60_000);
    expect(ws.sent).toEqual([]);
    expect(FakeWebSocket.instances).toHaveLength(1);
  });
});
