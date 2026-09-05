import { create } from "zustand";
import { persist } from "zustand/middleware";

interface AuthState {
  userId: string | null;
  accessToken: string | null;
  refreshToken: string | null;
  setSession: (session: { userId: string; accessToken: string; refreshToken: string }) => void;
  setAccessToken: (accessToken: string) => void;
  clear: () => void;
  isAuthenticated: () => boolean;
}

// Persisted to localStorage deliberately (not sessionStorage / in-memory only): a page refresh
// should not force a re-login, matching how a real user expects a web app to behave. The
// refresh token living in localStorage (rather than an httpOnly cookie) is a real, documented
// trade-off - see ADR-008's Javadoc on SecurityConfig for why this backend chose header-based
// bearer auth over cookies in the first place, which is what makes localStorage the only place
// a browser client *can* keep it.
export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      userId: null,
      accessToken: null,
      refreshToken: null,
      setSession: ({ userId, accessToken, refreshToken }) => set({ userId, accessToken, refreshToken }),
      setAccessToken: (accessToken) => set({ accessToken }),
      clear: () => set({ userId: null, accessToken: null, refreshToken: null }),
      isAuthenticated: () => get().accessToken !== null,
    }),
    { name: "collabflow-auth" },
  ),
);
