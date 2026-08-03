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
// Enter (no modifiers, not mid-IME) on a desktop pointer, the keyboard
// equivalent of tapping the header ✓. On touch, Enter keeps the native
// behaviour of dismissing the on-screen keyboard, so callers blur instead.
export function isSubmitEnter(event: ReactKeyboardEvent): boolean {
  if (event.key !== "Enter") return false
  if (event.shiftKey || event.ctrlKey || event.metaKey || event.altKey) return false
  if (event.nativeEvent.isComposing || event.keyCode === 229) return false
  return isDesktopPointer()
}
