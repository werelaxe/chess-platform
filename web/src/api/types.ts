// TypeScript mirrors of the JSON formats produced by the core library (ARCHITECTURE.md 1.3)
// and by the server (ARCHITECTURE.md 2.3 and 2.5).

import type { Locale } from "../i18n/locale";

export type Color = "WHITE" | "BLACK";

export type PieceType = "PAWN" | "KNIGHT" | "BISHOP" | "ROOK" | "QUEEN" | "KING";

export interface Piece {
  color: Color;
  type: PieceType;
}

export type GameKind = "CLASSIC" | "QUANTUM";

export type Visibility = "PUBLIC" | "PRIVATE";

/** Lifecycle of a game record on the server (`games.status`). */
export type GameLifecycle = "WAITING" | "ACTIVE" | "FINISHED";

export type ColorChoice = Color | "RANDOM";

/**
 * Who may take the other seat of a new game: anyone (`OPEN`) or only the named account
 * (`USER`, an invitation; a bot invitee accepts at once).
 */
export type Opponent = "OPEN" | "USER";

/** Strength of a bot, in order of play. */
export type BotLevel = "NOVICE" | "EASY" | "MEDIUM" | "HARD" | "EXPERT";

export interface NormalMove {
  type: "normal";
  from: string;
  to: string;
  promotion?: PieceType;
}

export interface SplitMove {
  type: "split";
  from: string;
  first: string;
  /** Equal to `from` means "stay". */
  second: string;
  promotion?: PieceType;
}

/** The observed content of a square; `piece` is null for an empty square. */
export interface Observation {
  piece: Piece | null;
}

export interface ObserveMove {
  type: "observe";
  square: string;
  /** Absent when proposed by a client; filled in by the server. */
  outcome?: Observation | null;
}

export type GameMove = NormalMove | SplitMove | ObserveMove;

export type EndReason =
  | "CHECKMATE"
  | "STALEMATE"
  | "INSUFFICIENT_MATERIAL"
  | "FIFTY_MOVE_RULE"
  | "THREEFOLD_REPETITION"
  | "KING_CAPTURED"
  | "RESIGNATION"
  | "DRAW_AGREEMENT"
  | "ABANDONMENT";

export interface OngoingStatus {
  type: "ongoing";
}

export interface FinishedStatus {
  type: "finished";
  /** null means a draw. */
  winner: Color | null;
  reason: EndReason;
}

export type GameStatus = OngoingStatus | FinishedStatus;

/** One possible content of a square; `piece` null is the probability of being empty. */
export interface CellEntry {
  piece: Piece | null;
  probability: number;
}

export interface CellView {
  square: string;
  /** Sorted by decreasing probability; empty list means a certainly empty square. */
  entries: CellEntry[];
}

export interface BoardView {
  sideToMove: Color;
  cells: CellView[];
  universeCount: number;
  checkProbability: number;
  lastMove: GameMove | null;
}

export interface UserRef {
  id: number;
  username: string;
  /** The display name chosen in the settings; null (or absent) when the username is all there is. */
  name?: string | null;
  /** True for guest accounts (`guest-NNNNNN`, no password); omitted or false for registered users. */
  guest?: boolean;
  /** True for the bots of the roster (ARCHITECTURE.md 2.10); omitted or false otherwise. */
  bot?: boolean;
  /** The level of a bot; null or absent for people. */
  botLevel?: BotLevel | null;
  /** Inside a game: the player's rating in the game's variant; null for guests. */
  rating?: number | null;
}

/** A player's standing in one variant (ARCHITECTURE.md 2.9). */
export interface RatingSummary {
  rating: number;
  /** Rated games played in the variant. */
  games: number;
  wins: number;
  draws: number;
  losses: number;
  peak: number;
}

/** One entry per variant; null while the player has no rating there yet. */
export type PlayerRatings = Record<GameKind, RatingSummary | null>;

/**
 * The caller's own account as described by the auth endpoints: a `UserRef` plus the settings
 * only the owner sees. `locale` is the UI language saved on the account, null until chosen.
 * `ratings` is absent in sessions stored before the field existed, until the next revalidation.
 */
export interface UserProfile extends UserRef {
  locale: Locale | null;
  ratings?: PlayerRatings;
}

export interface PlayerSummary {
  user: UserRef;
  ratings: PlayerRatings;
}

export interface LeaderboardEntry extends PlayerSummary {
  rank: number;
  /** The rating in the variant the leaderboard was asked for. */
  rating: number;
}

export interface PlayerProfile extends PlayerSummary {
  memberSince: string;
  /** Null for bots. */
  lastSeen: string | null;
  /** Up to ten games, newest first. */
  recentGames: GameSummary[];
}

/** A player's rating before and after a rated game. */
export interface RatingChange {
  before: number;
  after: number;
}

export interface RatingChanges {
  white: RatingChange;
  black: RatingChange;
}

export interface GameResult {
  winner: Color | null;
  reason: EndReason;
}

export interface GameSummary {
  id: string;
  kind: GameKind;
  visibility: Visibility;
  status: GameLifecycle;
  white: UserRef | null;
  black: UserRef | null;
  creator: UserRef;
  moveCount: number;
  result: GameResult | null;
  drawOfferedBy: Color | null;
  /** Whether the result changes the ratings (ARCHITECTURE.md 2.8). */
  rated: boolean;
  /**
   * The account invited to take the other seat of a game created with `opponent: USER`; null or
   * absent for an open game. Only the invitee may join such a WAITING game.
   */
  invitee?: UserRef | null;
  /**
   * True on the `game` event that ends an invitation because the invitee turned it down; false
   * or absent when the creator cancelled it (or it expired), which ends it the same way.
   */
  declined?: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface GameDto extends GameSummary {
  moves: GameMove[];
  /** Set once a rated game has finished with a rating change; null otherwise (ARCHITECTURE.md 2.8). */
  ratingChanges: RatingChanges | null;
}

export interface AuthResponse {
  token: string;
  user: UserProfile;
}

/** Body of PATCH /api/auth/me: only the fields present change; null clears a field. */
export interface UpdateProfileRequest {
  locale?: Locale | null;
  displayName?: string | null;
}

export interface MoveResponse {
  ply: number;
  move: GameMove;
  status: GameStatus;
}

export interface GameListResponse {
  games: GameSummary[];
}

export type GameFilter = "open" | "active" | "finished";

export type DrawAction = "OFFER" | "ACCEPT" | "DECLINE" | "WITHDRAW";

export interface CreateGameRequest {
  kind: GameKind;
  visibility: Visibility;
  color: ColorChoice;
  /** Ignored for guests, whose games are never rated. */
  rated: boolean;
  opponent: Opponent;
  /** The invitee, required when `opponent` is USER. */
  opponentUsername?: string;
}

export interface InvitationsResponse {
  /** WAITING games others invited the caller to. */
  incoming: GameSummary[];
  /** WAITING games the caller created by invitation. */
  outgoing: GameSummary[];
}

export interface PlayerListResponse {
  players: PlayerSummary[];
}

export interface LeaderboardResponse {
  entries: LeaderboardEntry[];
}

export interface BotListResponse {
  bots: PlayerSummary[];
}

export interface ApiErrorBody {
  error: string;
  message: string;
}

export interface MoveEvent {
  type: "move";
  ply: number;
  move: GameMove;
  status: GameStatus;
}

export interface GameEvent {
  type: "game";
  game: GameDto;
}

export interface PongEvent {
  type: "pong";
}

export type ServerEvent = MoveEvent | GameEvent | PongEvent;
