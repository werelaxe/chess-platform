import type { ServerEvent } from "./types";

export type SocketState = "connecting" | "open" | "reconnecting" | "closed";

export interface GameSocketHandlers {
  onEvent: (event: ServerEvent) => void;
  onStateChange?: (state: SocketState) => void;
}

const PING_INTERVAL_MS = 30_000;
/** A ping left unanswered for this long means the connection is dead even if the browser has not noticed. */
const PONG_TIMEOUT_MS = 10_000;
const RECONNECT_BASE_MS = 1_000;
const RECONNECT_MAX_MS = 15_000;

/** Close codes after which reconnecting is pointless: 1008 (policy, e.g. unknown game) and application codes. */
function isTerminalClose(code: number): boolean {
  return code === 1008 || (code >= 4000 && code <= 4999);
}

/** The feed is anonymous: spectators and players receive the same events, so no token is sent. */
export function gameSocketUrl(gameId: string, location: Location = window.location): string {
  const protocol = location.protocol === "https:" ? "wss:" : "ws:";
  return `${protocol}//${location.host}/api/games/${encodeURIComponent(gameId)}/ws`;
}

/**
 * WebSocket subscription to one game. Reconnects with exponential backoff after an unexpected
 * close, pings the server every 30 seconds while open and drops a connection whose pong does not
 * arrive in time so that the reconnect path runs. A close with a terminal code (the game does
 * not exist) stops the socket for good, as does `close()`.
 */
export class GameSocket {
  private socket: WebSocket | null = null;
  private attempts = 0;
  private pingTimer: ReturnType<typeof setInterval> | null = null;
  private pongTimer: ReturnType<typeof setTimeout> | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private closed = false;
  private currentState: SocketState = "connecting";

  constructor(
    private readonly gameId: string,
    private readonly handlers: GameSocketHandlers,
  ) {}

  connect(): void {
    if (this.closed) return;
    this.clearTimers();
    this.setState(this.attempts === 0 ? "connecting" : "reconnecting");

    let socket: WebSocket;
    try {
      socket = new WebSocket(gameSocketUrl(this.gameId));
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
        if (socket.readyState !== WebSocket.OPEN) return;
        socket.send(JSON.stringify({ type: "ping" }));
        if (this.pongTimer === null) {
          this.pongTimer = setTimeout(() => {
            this.pongTimer = null;
            this.dropSocket(socket);
          }, PONG_TIMEOUT_MS);
        }
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
      if (event.type === "pong") {
        this.clearPongTimer();
        return;
      }
      this.handlers.onEvent(event);
    };

    socket.onclose = (event: CloseEvent) => {
      if (socket !== this.socket) return;
      this.socket = null;
      this.clearTimers();
      if (this.closed) return;
      if (isTerminalClose(event.code)) {
        this.closed = true;
        this.setState("closed");
      } else {
        this.scheduleReconnect();
      }
    };

    socket.onerror = () => {
      // The close event that follows handles reconnection.
    };
  }

  close(): void {
    this.closed = true;
    this.clearTimers();
    this.detach();
    this.setState("closed");
  }

  /** Abandons a socket that stopped answering and reconnects without waiting for the browser's close event. */
  private dropSocket(socket: WebSocket): void {
    if (socket !== this.socket || this.closed) return;
    this.clearTimers();
    this.detach();
    this.scheduleReconnect();
  }

  private detach(): void {
    const socket = this.socket;
    this.socket = null;
    if (!socket) return;
    socket.onopen = null;
    socket.onmessage = null;
    socket.onclose = null;
    socket.onerror = null;
    socket.close();
  }

  private scheduleReconnect(): void {
    this.setState("reconnecting");
    const delay = Math.min(RECONNECT_BASE_MS * 2 ** this.attempts, RECONNECT_MAX_MS);
    const jitter = Math.random() * 300;
    this.attempts += 1;
    this.reconnectTimer = setTimeout(() => this.connect(), delay + jitter);
  }

  private clearPongTimer(): void {
    if (this.pongTimer !== null) {
      clearTimeout(this.pongTimer);
      this.pongTimer = null;
    }
  }

  private clearTimers(): void {
    if (this.pingTimer !== null) {
      clearInterval(this.pingTimer);
      this.pingTimer = null;
    }
    this.clearPongTimer();
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
