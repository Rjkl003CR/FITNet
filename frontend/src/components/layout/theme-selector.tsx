"use client";

import { Moon, Sun } from "lucide-react";
import { useSyncExternalStore } from "react";

type Theme = "light" | "dark";

export function ThemeSelector() {
  const theme = useSyncExternalStore(subscribeTheme, getThemeSnapshot, getServerThemeSnapshot);

  const toggleTheme = () => {
    const next: Theme = theme === "light" ? "dark" : "light";
    const dark = next === "dark";
    document.documentElement.classList.toggle("dark", dark);
    document.documentElement.style.colorScheme = dark ? "dark" : "light";
    localStorage.setItem("fitnet-theme", next);
    window.dispatchEvent(new Event("fitnet-theme-change"));
  };

  return (
    <button
      type="button"
      onClick={toggleTheme}
      aria-label={theme === "light" ? "Switch to dark mode" : "Switch to light mode"}
      title={theme === "light" ? "Dark mode" : "Light mode"}
      className="theme-surface theme-text-secondary theme-border theme-hover flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border transition-colors duration-200"
    >
      {theme === "light" ? <Moon size={16} /> : <Sun size={16} />}
    </button>
  );
}

function getThemeSnapshot(): Theme {
  const saved = localStorage.getItem("fitnet-theme");
  if (saved === "light" || saved === "dark") return saved;
  return document.documentElement.classList.contains("dark") ? "dark" : "light";
}

function getServerThemeSnapshot(): Theme {
  return "light";
}

function subscribeTheme(onStoreChange: () => void) {
  const media = window.matchMedia("(prefers-color-scheme: dark)");
  const applySystemTheme = () => {
    if (!localStorage.getItem("fitnet-theme")) {
      document.documentElement.classList.toggle("dark", media.matches);
      document.documentElement.style.colorScheme = media.matches ? "dark" : "light";
      onStoreChange();
    }
  };
  window.addEventListener("fitnet-theme-change", onStoreChange);
  window.addEventListener("storage", onStoreChange);
  media.addEventListener("change", applySystemTheme);
  return () => {
    window.removeEventListener("fitnet-theme-change", onStoreChange);
    window.removeEventListener("storage", onStoreChange);
    media.removeEventListener("change", applySystemTheme);
  };
}
