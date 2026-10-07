import { i18n } from "../i18n";
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
  UpdateProfileRequest,
  UserProfile,
} from "./types";

/**
 * A failed API call. `status` is 0 when the server could not be reached at all; `message` is
 * the server's own wording, kept for logging, while `errorMessage` gives the translated one.
 */
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

/** Error codes of the server (ARCHITECTURE.md 2.3) plus the client's own `network`, each with a translation. */
const ERROR_CODES = [
  "network",
  "validation",
  "unauthorized",
  "not_found",
  "username_taken",
  "invalid_credentials",
  "rate_limited",
  "own_game",
  "game_full",
  "not_a_player",
  "not_your_turn",
  "illegal_move",
  "game_not_started",
  "game_finished",
  "draw_pending",
  "no_draw_offer",
  "draw_not_available",
  "internal",
] as const;

const STATUS_CODES = [400, 401, 403, 404, 409, 429, 500, 502, 503, 504] as const;

function isKnownCode(code: string): code is (typeof ERROR_CODES)[number] {
  return (ERROR_CODES as readonly string[]).includes(code);
}

function isKnownStatus(status: number): status is (typeof STATUS_CODES)[number] {
  return (STATUS_CODES as readonly number[]).includes(status);
}

/** Message in the UI language for any thrown value: API errors are mapped by code, then by HTTP status. */
export function errorMessage(error: unknown): string {
  if (isApiError(error)) {
    if (isKnownCode(error.code)) return i18n.t(`errors.code.${error.code}`);
    if (isKnownStatus(error.status)) return i18n.t(`errors.status.${error.status}`);
    return i18n.t("errors.status.other", { status: error.status });
  }
  if (error instanceof Error && error.message) return error.message;
  return i18n.t("errors.unexpected");
}

type Method = "GET" | "POST" | "PATCH";

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
    throw new ApiError(0, "network", "Cannot reach the server");
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
      typeof errorBody.message === "string" && errorBody.message ? errorBody.message : `Request failed with status ${response.status}`;
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
  /** Creates a fresh guest account (`guest-NNNNNN`) and signs it in. */
  guest(): Promise<AuthResponse> {
    return request("POST", "/api/auth/guest");
  },
  me(): Promise<UserProfile> {
    return request("GET", "/api/auth/me");
  },
  /** Saves the UI language on the account; returns the updated profile. */
  updateProfile(body: UpdateProfileRequest): Promise<UserProfile> {
    return request("PATCH", "/api/auth/me", body);
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
