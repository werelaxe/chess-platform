import type { ServerEvent } from "./types";

export type SocketState = "connecting" | "open" | "reconnecting" | "closed";

export interface GameSocketHandlers {
  onEvent: (event: ServerEvent) => void;
  onStateChange?: (state: SocketState) => void;
}

const PING_INTERVAL_MS = 30_000;
const RECONNECT_BASE_MS = 1_000;
const RECONNECT_MAX_MS = 15_000;

export function gameSocketUrl(gameId: string, token: string | null, location: Location = window.location): string {
  const protocol = location.protocol === "https:" ? "wss:" : "ws:";
  const url = new URL(`${protocol}//${location.host}/api/games/${encodeURIComponent(gameId)}/ws`);
  if (token) url.searchParams.set("token", token);
  return url.toString();
}

/**
 * WebSocket subscription to one game. Reconnects with exponential backoff after an unexpected
 * close and pings the server every 30 seconds while open. `close()` stops everything for good.
 */
export class GameSocket {
  private socket: WebSocket | null = null;
  private attempts = 0;
  private pingTimer: ReturnType<typeof setInterval> | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private closed = false;
  private currentState: SocketState = "connecting";

  constructor(
    private readonly gameId: string,
    private readonly token: string | null,
    private readonly handlers: GameSocketHandlers,
  ) {}

  get state(): SocketState {
    return this.currentState;
  }

  connect(): void {
    if (this.closed) return;
    this.clearTimers();
    this.setState(this.attempts === 0 ? "connecting" : "reconnecting");

    let socket: WebSocket;
    try {
      socket = new WebSocket(gameSocketUrl(this.gameId, this.token));
    } catch {
      this.scheduleReconnect();
      return;
    }
    this.socket = socket;

    socket.onopen = () => {
      if (socket !== this.socket) return;
      this.attempts = 0;
      this.setState("open");
      this.pingTimer = setInterval(() => {
        if (socket.readyState === WebSocket.OPEN) socket.send(JSON.stringify({ type: "ping" }));
      }, PING_INTERVAL_MS);
    };

    socket.onmessage = (message: MessageEvent) => {
      if (socket !== this.socket) return;
      if (typeof message.data !== "string") return;
      let event: ServerEvent;
      try {
        event = JSON.parse(message.data) as ServerEvent;
      } catch {
        return;
      }
      if (!event || typeof event !== "object" || typeof event.type !== "string") return;
      if (event.type === "pong") return;
      this.handlers.onEvent(event);
    };

    socket.onclose = () => {
      if (socket !== this.socket) return;
      this.socket = null;
      this.clearTimers();
      if (!this.closed) this.scheduleReconnect();
    };

    socket.onerror = () => {
      // The close event that follows handles reconnection.
    };
  }

  close(): void {
    this.closed = true;
    this.clearTimers();
    const socket = this.socket;
    this.socket = null;
    if (socket) {
      socket.onopen = null;
      socket.onmessage = null;
      socket.onclose = null;
      socket.onerror = null;
      socket.close();
    }
    this.setState("closed");
  }

  private scheduleReconnect(): void {
    this.setState("reconnecting");
    const delay = Math.min(RECONNECT_BASE_MS * 2 ** this.attempts, RECONNECT_MAX_MS);
    const jitter = Math.random() * 300;
    this.attempts += 1;
    this.reconnectTimer = setTimeout(() => this.connect(), delay + jitter);
  }

  private clearTimers(): void {
    if (this.pingTimer !== null) {
      clearInterval(this.pingTimer);
      this.pingTimer = null;
    }
    if (this.reconnectTimer !== null) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  private setState(state: SocketState): void {
    if (this.currentState === state) return;
    this.currentState = state;
    this.handlers.onStateChange?.(state);
  }
}
