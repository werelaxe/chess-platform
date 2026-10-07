import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";
import type { UserRef } from "../api/types";

interface AuthState {
  token: string | null;
  user: UserRef | null;
  setSession: (token: string, user: UserRef) => void;
  clearSession: () => void;
}

/** Logged-in session, persisted in localStorage so a reload keeps the user signed in. */
export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      user: null,
      setSession: (token, user) => set({ token, user }),
      clearSession: () => set({ token: null, user: null }),
    }),
    {
      name: "chess-platform.auth",
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({ token: state.token, user: state.user }),
    },
  ),
);

export function getAuthToken(): string | null {
  return useAuthStore.getState().token;
}
