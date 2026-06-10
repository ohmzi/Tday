import { clsx, type ClassValue } from "clsx"
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
