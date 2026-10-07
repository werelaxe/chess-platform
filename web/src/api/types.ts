// TypeScript mirrors of the JSON formats produced by the core library (ARCHITECTURE.md 1.3)
// and by the server (ARCHITECTURE.md 2.3 and 2.5).

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

/** Who the creator plays against: another person or the built-in engine. */
export type Opponent = "HUMAN" | "COMPUTER";

/** Strength of the built-in engine in a computer game. */
export type BotLevel = "EASY" | "MEDIUM" | "HARD";

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
  /** True for guest accounts (`guest-NNNNNN`, no password); omitted or false for registered users. */
  guest?: boolean;
  /** True for the system "computer" user that plays computer games; omitted or false otherwise. */
  bot?: boolean;
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
  /** Engine strength of a computer game; null or absent for a game between people. */
  botLevel?: BotLevel | null;
  createdAt: string;
  updatedAt: string;
}

export interface GameDto extends GameSummary {
  moves: GameMove[];
}

export interface AuthResponse {
  token: string;
  user: UserRef;
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
  /** Defaults to HUMAN on the server. */
  opponent?: Opponent;
  /** Required when `opponent` is COMPUTER. */
  level?: BotLevel;
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
