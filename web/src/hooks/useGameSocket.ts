import { useEffect, useRef, useState } from "react";
import type { ServerEvent } from "../api/types";
import { GameSocket, type SocketState } from "../api/ws";

/** Keeps one reconnecting socket open for the game and forwards its events to the latest handler. */
export function useGameSocket(gameId: string | undefined, token: string | null, onEvent: (event: ServerEvent) => void) {
  const [state, setState] = useState<SocketState>("connecting");
  const handlerRef = useRef(onEvent);
  handlerRef.current = onEvent;

  useEffect(() => {
    if (!gameId) return;
    const socket = new GameSocket(gameId, token, {
      onEvent: (event) => handlerRef.current(event),
      onStateChange: setState,
    });
    socket.connect();
    return () => socket.close();
  }, [gameId, token]);

  return state;
}
