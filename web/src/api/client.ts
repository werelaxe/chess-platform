import { getAuthToken, useAuthStore } from "../store/auth";
import type {
  ApiErrorBody,
  AuthResponse,
  CreateGameRequest,
  DrawAction,
  GameDto,
  GameFilter,
  GameKind,
  GameListResponse,
  GameMove,
  GameSummary,
  MoveResponse,
  UserRef,
} from "./types";

/** A failed API call. `status` is 0 when the server could not be reached at all. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

/** Message suitable for showing to the user for any thrown value. */
export function errorMessage(error: unknown): string {
  if (isApiError(error)) return error.message;
  if (error instanceof Error) return error.message;
  return "Something went wrong";
}

type Method = "GET" | "POST";

const STATUS_MESSAGES: Record<number, string> = {
  400: "The request was rejected",
  401: "You need to sign in",
  403: "You are not allowed to do that",
  404: "Not found",
  409: "The game is not in the right state for that",
  500: "The server failed to handle the request",
  502: "The server is unavailable right now",
  503: "The server is unavailable right now",
  504: "The server took too long to respond",
};

async function request<T>(method: Method, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const token = getAuthToken();
  if (token) headers.Authorization = `Bearer ${token}`;

  let response: Response;
  try {
    response = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, "network", "Cannot reach the server. Check your connection and try again.");
  }

  const text = await response.text();
  let payload: unknown = null;
  if (text) {
    try {
      payload = JSON.parse(text);
    } catch {
      payload = null;
    }
  }

  if (!response.ok) {
    const errorBody = (payload ?? {}) as Partial<ApiErrorBody>;
    const code = typeof errorBody.error === "string" ? errorBody.error : `http_${response.status}`;
    const message =
      typeof errorBody.message === "string" && errorBody.message
        ? errorBody.message
        : (STATUS_MESSAGES[response.status] ?? `Request failed with status ${response.status}`);
    // A rejected token means the stored session is stale: drop it so the UI asks to sign in again.
    if (response.status === 401 && token && !path.startsWith("/api/auth/")) {
      useAuthStore.getState().clearSession();
    }
    throw new ApiError(response.status, code, message);
  }

  return payload as T;
}

export interface ListGamesParams {
  filter: GameFilter;
  kind?: GameKind;
  limit?: number;
  offset?: number;
}

export const api = {
  register(username: string, password: string): Promise<AuthResponse> {
    return request("POST", "/api/auth/register", { username, password });
  },
  login(username: string, password: string): Promise<AuthResponse> {
    return request("POST", "/api/auth/login", { username, password });
  },
  me(): Promise<UserRef> {
    return request("GET", "/api/auth/me");
  },
  createGame(body: CreateGameRequest): Promise<GameDto> {
    return request("POST", "/api/games", body);
  },
  async listGames(params: ListGamesParams): Promise<GameSummary[]> {
    const query = new URLSearchParams({ filter: params.filter });
    if (params.kind) query.set("kind", params.kind);
    if (params.limit !== undefined) query.set("limit", String(params.limit));
    if (params.offset !== undefined) query.set("offset", String(params.offset));
    const response = await request<GameListResponse>("GET", `/api/games?${query.toString()}`);
    return response.games ?? [];
  },
  async myGames(): Promise<GameSummary[]> {
    const response = await request<GameListResponse>("GET", "/api/games/mine");
    return response.games ?? [];
  },
  getGame(id: string): Promise<GameDto> {
    return request("GET", `/api/games/${encodeURIComponent(id)}`);
  },
  joinGame(id: string): Promise<GameDto> {
    return request("POST", `/api/games/${encodeURIComponent(id)}/join`);
  },
  sendMove(id: string, move: GameMove): Promise<MoveResponse> {
    return request("POST", `/api/games/${encodeURIComponent(id)}/moves`, { move });
  },
  resign(id: string): Promise<GameDto> {
    return request("POST", `/api/games/${encodeURIComponent(id)}/resign`);
  },
  draw(id: string, action: DrawAction): Promise<GameDto> {
    return request("POST", `/api/games/${encodeURIComponent(id)}/draw`, { action });
  },
};
