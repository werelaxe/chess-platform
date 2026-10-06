import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { api, errorMessage } from "../api/client";
import type { Color, DrawAction, GameDto, GameMove, PieceType, ServerEvent } from "../api/types";
import { Board, type BoardHighlights, type BoardMode } from "../components/Board";
import { DistributionPanel } from "../components/DistributionPanel";
import { GameControls } from "../components/GameControls";
import { GameHeader } from "../components/GameHeader";
import { ModeBar } from "../components/ModeBar";
import { MoveList } from "../components/MoveList";
import { PromotionDialog } from "../components/PromotionDialog";
import { ErrorNotice, Loading } from "../components/ui";
import { IllegalMoveError, LocalGame, type GameSnapshot } from "../core/game";
import { movesEqual } from "../core/notation";
import { squareIndex, squareName } from "../core/squares";
import { useGameSocket } from "../hooks/useGameSocket";
import { useAuthStore } from "../store/auth";

interface Selection {
  from: number;
  targets: number[];
}

interface SplitFirst {
  first: number;
  targets: number[];
}

interface PendingPromotion {
  from: number;
  to: number;
  /** Present for a split: the second target (may equal `from` for "stay"). */
  second?: number;
}

interface Toast {
  kind: "error" | "info";
  text: string;
}

const EMPTY_SET: ReadonlySet<number> = new Set<number>();
const TOAST_MS = 4_500;
/** How long to wait for the server's event after an observation before resyncing. */
const OBSERVE_FALLBACK_MS = 3_000;

function lastMoveSquares(move: GameMove | null): ReadonlySet<number> {
  if (!move) return EMPTY_SET;
  const squares = new Set<number>();
  switch (move.type) {
    case "normal":
      squares.add(squareIndex(move.from));
      squares.add(squareIndex(move.to));
      break;
    case "split":
      squares.add(squareIndex(move.from));
      squares.add(squareIndex(move.first));
      squares.add(squareIndex(move.second));
      break;
    case "observe":
      squares.add(squareIndex(move.square));
      break;
  }
  return squares;
}

export function GamePage() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const user = useAuthStore((state) => state.user);

  const localRef = useRef<LocalGame | null>(null);
  const loadSequence = useRef(0);
  /** Highest move count reported by the socket, tracked even while no local game is ready to apply events. */
  const serverMoveCount = useRef(0);
  /** Server count a follow-up load was already started for, so a persistent mismatch cannot loop. */
  const followUpTarget = useRef(0);
  const busyRef = useRef(false);
  const observeTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const [game, setGame] = useState<GameDto | null>(null);
  const [snapshot, setSnapshot] = useState<GameSnapshot | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<unknown>(null);
  const [mode, setMode] = useState<BoardMode>("normal");
  const [selection, setSelection] = useState<Selection | null>(null);
  const [splitFirst, setSplitFirst] = useState<SplitFirst | null>(null);
  const [promotion, setPromotion] = useState<PendingPromotion | null>(null);
  const [hovered, setHovered] = useState<number | null>(null);
  const [pinned, setPinned] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [awaitingObservation, setAwaitingObservation] = useState(false);
  const [toast, setToast] = useState<Toast | null>(null);

  const showToast = useCallback((kind: Toast["kind"], text: string) => setToast({ kind, text }), []);

  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(null), TOAST_MS);
    return () => clearTimeout(timer);
  }, [toast]);

  const clearObserveTimer = useCallback(() => {
    if (observeTimer.current !== null) {
      clearTimeout(observeTimer.current);
      observeTimer.current = null;
    }
  }, []);

  const clearSelection = useCallback(() => {
    setSelection(null);
    setSplitFirst(null);
    setPromotion(null);
  }, []);

  /** Fetches the game and rebuilds the local state by replaying its moves. */
  const load = useCallback(
    async (initial: boolean) => {
      loadSequence.current += 1;
      const sequence = loadSequence.current;
      if (initial) setLoading(true);
      try {
        const dto = await api.getGame(id);
        if (sequence !== loadSequence.current) return;
        const local = LocalGame.replay(dto.kind, dto.moves);
        localRef.current = local;
        setGame(dto);
        setSnapshot(local.snapshot());
        setLoadError(null);
        setAwaitingObservation(false);
        clearObserveTimer();
        clearSelection();
        // A move committed while the fetch was in flight is missing from the response, and its
        // event was ignored because no local game was ready: fetch again when the socket saw more.
        if (local.moveCount() < serverMoveCount.current && followUpTarget.current !== serverMoveCount.current) {
          followUpTarget.current = serverMoveCount.current;
          void load(false);
        }
      } catch (error) {
        if (sequence !== loadSequence.current) return;
        if (initial) setLoadError(error);
        else showToast("error", `Could not sync the game: ${errorMessage(error)}`);
      } finally {
        if (sequence === loadSequence.current && initial) setLoading(false);
      }
    },
    [id, clearObserveTimer, clearSelection, showToast],
  );

  const resync = useCallback(() => void load(false), [load]);

  useEffect(() => {
    localRef.current = null;
    serverMoveCount.current = 0;
    followUpTarget.current = 0;
    setGame(null);
    setSnapshot(null);
    setMode("normal");
    setPinned(null);
    void load(true);
    return () => {
      loadSequence.current += 1;
      clearObserveTimer();
    };
  }, [load, clearObserveTimer]);

  const onEvent = useCallback(
    (event: ServerEvent) => {
      const local = localRef.current;
      if (event.type === "game") {
        const serverCount = event.game.moves.length;
        serverMoveCount.current = Math.max(serverMoveCount.current, serverCount);
        setGame(event.game);
        if (!local) return;
        const localCount = local.moveCount();
        // The server being ahead means we missed something; being behind while idle means our
        // optimistic state is stale (for example the game was reset or replayed differently).
        if (serverCount > localCount || (serverCount < localCount && !busyRef.current)) resync();
        return;
      }
      if (event.type !== "move") return;
      serverMoveCount.current = Math.max(serverMoveCount.current, event.ply + 1);
      if (!local) return;
      const count = local.moveCount();
      if (event.ply === count) {
        try {
          local.apply(event.move);
        } catch {
          resync();
          return;
        }
        setSnapshot(local.snapshot());
        setAwaitingObservation(false);
        clearObserveTimer();
        clearSelection();
        setGame((current) => (current ? { ...current, moveCount: event.ply + 1, moves: [...current.moves, event.move] } : current));
      } else if (event.ply < count && movesEqual(local.history()[event.ply]!, event.move)) {
        // Echo of a move we already applied optimistically.
      } else {
        resync();
      }
    },
    [resync, clearObserveTimer, clearSelection],
  );

  // No feed while the page shows the load error: an unknown or deleted game would otherwise be retried forever.
  const socketState = useGameSocket(loadError || !id ? undefined : id, onEvent);

  const viewerColor = useMemo<Color | null>(() => {
    if (!game || !user) return null;
    if (game.white?.id === user.id) return "WHITE";
    if (game.black?.id === user.id) return "BLACK";
    return null;
  }, [game, user]);

  const quantum = game?.kind === "QUANTUM";
  const effectiveMode: BoardMode = quantum ? mode : "normal";
  const canAct =
    game !== null &&
    snapshot !== null &&
    viewerColor !== null &&
    game.status === "ACTIVE" &&
    snapshot.status.type === "ongoing" &&
    snapshot.sideToMove === viewerColor;
  const interactive = canAct && !busy && !awaitingObservation;

  useEffect(() => {
    if (!interactive) clearSelection();
  }, [interactive, clearSelection]);

  useEffect(() => {
    const previous = document.title;
    if (game) {
      const players = `${game.white?.username ?? "?"} vs ${game.black?.username ?? "?"}`;
      document.title = `${players} - ${game.kind === "QUANTUM" ? "Quantum" : "Classic"} chess`;
    }
    return () => {
      document.title = previous;
    };
  }, [game]);

  const observable = useMemo<ReadonlySet<number>>(() => {
    const local = localRef.current;
    if (!quantum || effectiveMode !== "observe" || !interactive || !local || !snapshot) return EMPTY_SET;
    const squares = new Set<number>();
    for (let index = 0; index < 64; index += 1) if (local.canObserve(index)) squares.add(index);
    return squares;
  }, [quantum, effectiveMode, interactive, snapshot]);

  const highlights = useMemo<BoardHighlights>(() => {
    let targets: ReadonlySet<number> = EMPTY_SET;
    let stay: number | null = null;
    if (selection && splitFirst) {
      targets = new Set(splitFirst.targets.filter((square) => square !== selection.from));
      if (splitFirst.targets.includes(selection.from)) stay = selection.from;
    } else if (selection) {
      targets = new Set(selection.targets);
    }
    return {
      selected: selection?.from ?? null,
      targets,
      first: splitFirst?.first ?? null,
      stay,
      observable,
      lastMove: lastMoveSquares(snapshot?.view.lastMove ?? null),
      inspected: hovered ?? pinned,
      pinned,
    };
  }, [selection, splitFirst, observable, snapshot, hovered, pinned]);

  async function submit(move: GameMove, optimistic: boolean) {
    const local = localRef.current;
    if (!local || !game) return;
    const countBefore = local.moveCount();
    if (optimistic) {
      try {
        local.apply(move);
      } catch (error) {
        showToast("error", error instanceof IllegalMoveError ? `Illegal move: ${error.message}` : errorMessage(error));
        clearSelection();
        return;
      }
      setSnapshot(local.snapshot());
    }
    clearSelection();
    setBusy(true);
    busyRef.current = true;
    try {
      const response = await api.sendMove(game.id, move);
      setGame((current) => (current ? { ...current, moveCount: Math.max(current.moveCount, response.ply + 1) } : current));
      if (!optimistic && localRef.current === local && local.moveCount() === countBefore) {
        // The outcome is random: wait for the server's move event, resync if it never comes.
        setAwaitingObservation(true);
        clearObserveTimer();
        observeTimer.current = setTimeout(() => {
          observeTimer.current = null;
          if (localRef.current === local && local.moveCount() === countBefore) resync();
        }, OBSERVE_FALLBACK_MS);
      }
    } catch (error) {
      showToast("error", `The server rejected the move: ${errorMessage(error)}`);
      await load(false);
    } finally {
      setBusy(false);
      busyRef.current = false;
    }
  }

  function select(index: number) {
    const local = localRef.current;
    if (!local || !snapshot || !viewerColor) return;
    const cell = snapshot.view.cells[index];
    const mine = cell?.entries.some((entry) => entry.piece?.color === viewerColor) ?? false;
    if (!mine) {
      clearSelection();
      setPinned(index);
      return;
    }
    const targets = local.legalTargets(index);
    if (targets.length === 0) {
      clearSelection();
      setPinned(index);
      return;
    }
    setSelection({ from: index, targets });
    setSplitFirst(null);
    setPromotion(null);
    setPinned(null);
  }

  function handleCellClick(index: number) {
    const local = localRef.current;
    if (!local || !snapshot || !game) return;
    if (!interactive) {
      setPinned((current) => (current === index ? null : index));
      return;
    }

    if (effectiveMode === "observe") {
      if (local.canObserve(index)) {
        void submit({ type: "observe", square: squareName(index) }, false);
      } else {
        showToast("info", "Pick an outlined square: only uncertain squares can be observed.");
        setPinned(index);
      }
      return;
    }

    if (selection && splitFirst) {
      if (splitFirst.targets.includes(index)) {
        const from = selection.from;
        const first = splitFirst.first;
        const second = index;
        const needsPromotion = local.requiresPromotion(from, first) || (second !== from && local.requiresPromotion(from, second));
        if (needsPromotion) {
          setPromotion({ from, to: first, second });
        } else {
          void submit({ type: "split", from: squareName(from), first: squareName(first), second: squareName(second) }, true);
        }
        return;
      }
      if (index === splitFirst.first) {
        setSplitFirst(null);
        return;
      }
      select(index);
      return;
    }

    if (selection) {
      if (selection.targets.includes(index)) {
        if (effectiveMode === "split") {
          setSplitFirst({ first: index, targets: local.splitSecondTargets(selection.from, index) });
        } else if (local.requiresPromotion(selection.from, index)) {
          setPromotion({ from: selection.from, to: index });
        } else {
          void submit({ type: "normal", from: squareName(selection.from), to: squareName(index) }, true);
        }
        return;
      }
      if (index === selection.from) {
        clearSelection();
        return;
      }
    }
    select(index);
  }

  function pickPromotion(type: PieceType) {
    if (!promotion) return;
    const { from, to, second } = promotion;
    setPromotion(null);
    if (second !== undefined) {
      void submit({ type: "split", from: squareName(from), first: squareName(to), second: squareName(second), promotion: type }, true);
    } else {
      void submit({ type: "normal", from: squareName(from), to: squareName(to), promotion: type }, true);
    }
  }

  function changeMode(next: BoardMode) {
    setMode(next);
    clearSelection();
  }

  async function runAction(label: string, action: () => Promise<GameDto>, after?: (dto: GameDto) => void) {
    setBusy(true);
    busyRef.current = true;
    try {
      const dto = await action();
      setGame(dto);
      after?.(dto);
    } catch (error) {
      showToast("error", `${label} failed: ${errorMessage(error)}`);
    } finally {
      setBusy(false);
      busyRef.current = false;
    }
  }

  function onJoin() {
    void runAction("Joining", () => api.joinGame(id), () => resync());
  }

  function onResign() {
    if (game?.status === "WAITING") {
      // Leaving a game nobody joined deletes it.
      void runAction("Cancelling", () => api.resign(id), () => navigate("/"));
    } else {
      void runAction("Resigning", () => api.resign(id));
    }
  }

  function onDraw(action: DrawAction) {
    void runAction("Draw request", () => api.draw(id, action));
  }

  if (loading) {
    return (
      <div className="page">
        <Loading label="Loading game" />
      </div>
    );
  }

  if (loadError || !game || !snapshot) {
    return (
      <div className="page page--narrow">
        <h1 className="page__title" style={{ marginBottom: 12 }}>
          Game unavailable
        </h1>
        <ErrorNotice error={loadError ?? new Error("The game could not be loaded.")} onRetry={() => void load(true)} />
        <Link to="/" className="btn" style={{ marginTop: 8 }}>
          Back to the lobby
        </Link>
      </div>
    );
  }

  const inspectedIndex = hovered ?? pinned;
  const inspectedCell = inspectedIndex !== null ? (snapshot.view.cells[inspectedIndex] ?? null) : null;
  const shareUrl = `${window.location.origin}/games/${game.id}`;
  const showModeBar = quantum && viewerColor !== null && game.status === "ACTIVE" && snapshot.status.type === "ongoing";

  let hint = "";
  if (awaitingObservation) hint = "Measuring the square, waiting for the outcome.";
  else if (busy) hint = "Sending your move.";
  else if (!canAct) hint = viewerColor ? "Waiting for your opponent." : "";
  else if (effectiveMode === "observe") hint = "Click an outlined square to measure it. The outcome is random and costs your turn.";
  else if (effectiveMode === "split") {
    hint = !selection
      ? "Select a piece to split."
      : !splitFirst
        ? "Choose the first destination."
        : "Choose the second destination, or the origin square to stay.";
  } else hint = selection ? "Choose a destination." : "Select a piece to move.";

  let banner: React.ReactNode = null;
  if (game.status === "WAITING") {
    banner = (
      <div className="banner">
        {viewerColor
          ? game.visibility === "PRIVATE"
            ? "Waiting for an opponent. Share the link from the panel to invite someone."
            : "Waiting for an opponent to join from the lobby."
          : "This game is waiting for a second player."}
      </div>
    );
  } else if (awaitingObservation) {
    banner = <div className="banner">Observing, waiting for the outcome from the server.</div>;
  } else if (canAct && !busy) {
    banner = <div className="banner">Your move{snapshot.view.checkProbability > 0 ? ", you are in check" : ""}.</div>;
  }

  return (
    <div className="page">
      <div className="game">
        <div className="game__stage reveal">
          {banner}
          <Board
            view={snapshot.view}
            flipped={viewerColor === "BLACK"}
            mode={effectiveMode}
            highlights={highlights}
            interactive={interactive}
            busy={busy}
            inert={promotion !== null}
            onCellClick={handleCellClick}
            onCellHover={setHovered}
          >
            {promotion && viewerColor ? (
              <PromotionDialog color={viewerColor} onPick={pickPromotion} onCancel={() => setPromotion(null)} />
            ) : null}
          </Board>
        </div>
        <div className="game__side reveal" style={{ "--i": 1 } as React.CSSProperties}>
          <GameHeader game={game} snapshot={snapshot} viewer={user} socketState={socketState} />
          {showModeBar ? <ModeBar mode={mode} onChange={changeMode} disabled={!interactive} hint={hint} /> : null}
          <GameControls
            game={game}
            viewer={user}
            viewerColor={viewerColor}
            busy={busy}
            shareUrl={shareUrl}
            onJoin={onJoin}
            onResign={onResign}
            onDraw={onDraw}
          />
          <DistributionPanel cell={inspectedCell} quantum={quantum} pinned={hovered === null && pinned !== null} />
          <MoveList moves={snapshot.history} />
        </div>
      </div>
      {toast ? (
        <div className={`toast${toast.kind === "info" ? " toast--info" : ""}`} role="status">
          {toast.text}
        </div>
      ) : null}
    </div>
  );
}
