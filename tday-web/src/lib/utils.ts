import { clsx, type ClassValue } from "clsx"
import type { KeyboardEvent as ReactKeyboardEvent } from "react"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

// True on devices with a mouse/trackpad (desktop); false on touch-only devices
// and in non-browser environments (SSR, tests without matchMedia).
export function isDesktopPointer(): boolean {
  if (typeof window === "undefined" || typeof window.matchMedia !== "function") {
    return false
  }
  return window.matchMedia("(hover: hover) and (pointer: fine)").matches
}

// True when a keydown in a sheet text field means "confirm this sheet" — plain
// Enter with no modifiers and not mid-IME composition. Every sheet treats it as
// a press of the header ✓, on desktop keyboards and on the mobile "done" key
// alike. Callers fall back to blurring when there is nothing to save, so the
// key still dismisses the on-screen keyboard.
export function isSubmitEnter(event: ReactKeyboardEvent): boolean {
  if (event.key !== "Enter") return false
  if (event.shiftKey || event.ctrlKey || event.metaKey || event.altKey) return false
  return !(event.nativeEvent.isComposing || event.keyCode === 229)
}
