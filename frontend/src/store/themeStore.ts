import { create } from "zustand";
import { persist } from "zustand/middleware";

type Theme = "light" | "dark";

interface ThemeState {
  theme: Theme;
  toggle: () => void;
}

function applyToDocument(theme: Theme) {
  document.documentElement.classList.toggle("dark", theme === "dark");
}

const prefersDark = typeof window !== "undefined" && window.matchMedia("(prefers-color-scheme: dark)").matches;

export const useThemeStore = create<ThemeState>()(
  persist(
    (set, get) => ({
      theme: prefersDark ? "dark" : "light",
      toggle: () => {
        const next: Theme = get().theme === "dark" ? "light" : "dark";
        applyToDocument(next);
        set({ theme: next });
      },
    }),
    {
      name: "collabflow-theme",
      onRehydrateStorage: () => (state) => {
        if (state) applyToDocument(state.theme);
      },
    },
  ),
);

// Apply immediately on module load too (before React mounts / before persist rehydrates on a
// fresh visit with no stored preference yet), so there's no flash of the wrong theme.
applyToDocument(useThemeStore.getState().theme);
