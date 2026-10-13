import { useEffect } from "react";
import { create } from "zustand";
import { api } from "../api/client";
import type { GameSummary } from "../api/types";
import { useAuthStore } from "./auth";

/** How often the navigation badge asks for the pending invitations while signed in. */
export const INVITATIONS_POLL_MS = 30_000;

interface InvitationsState {
  incoming: GameSummary[];
  outgoing: GameSummary[];
  /** False until the first successful fetch of the current session. */
  loaded: boolean;
  error: unknown;
  refresh: () => Promise<void>;
  clear: () => void;
}

let inFlight: Promise<void> | null = null;

/**
 * The caller's pending invitations (ARCHITECTURE.md 2.10), shared by the navigation badge, the
 * invitations page and the game page. Concurrent refreshes are folded into one request.
 */
export const useInvitationsStore = create<InvitationsState>()((set) => ({
  incoming: [],
  outgoing: [],
  loaded: false,
  error: null,
  refresh: () => {
    if (inFlight) return inFlight;
    const token = useAuthStore.getState().token;
    if (!token) {
      set({ incoming: [], outgoing: [], loaded: false, error: null });
      return Promise.resolve();
    }
    inFlight = api
      .invitations()
      .then((response) => {
        // A session that ended meanwhile must not get the old account's invitations back.
        if (useAuthStore.getState().token !== token) return;
        set({ incoming: response.incoming, outgoing: response.outgoing, loaded: true, error: null });
      })
      .catch((error: unknown) => set({ error }))
      .finally(() => {
        inFlight = null;
      });
    return inFlight;
  },
  clear: () => set({ incoming: [], outgoing: [], loaded: false, error: null }),
}));

/** Keeps the store fresh every `intervalMs` while a session exists; clears it when the session ends. */
export function useInvitationsPolling(intervalMs: number = INVITATIONS_POLL_MS): void {
  const token = useAuthStore((state) => state.token);
  const refresh = useInvitationsStore((state) => state.refresh);
  const clear = useInvitationsStore((state) => state.clear);
  useEffect(() => {
    if (!token) {
      clear();
      return;
    }
    void refresh();
    const timer = setInterval(() => void refresh(), intervalMs);
    return () => clearInterval(timer);
  }, [token, intervalMs, refresh, clear]);
}
