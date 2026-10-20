import { useEffect } from "react";
import { useTheme } from "next-themes";

/** Light and dark values for `<meta name="theme-color">`. */
const THEME_COLORS = {
  light: "#EEF1F6", // --background light: hsl(222.86 46.67% 97.06%)
  dark: "#080809",  // --background dark:  hsl(240 16.67% 2.35%)
} as const;

/**
 * Keeps `<meta name="theme-color">` in sync with the active theme so the
 * browser chrome / status bar matches the app background.
 */
export function useThemeColor() {
  const { resolvedTheme } = useTheme();

  useEffect(() => {
    const meta = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]');
    if (!meta) return;
    const color = resolvedTheme === "dark" ? THEME_COLORS.dark : THEME_COLORS.light;
    meta.setAttribute("content", color);
  }, [resolvedTheme]);
}
