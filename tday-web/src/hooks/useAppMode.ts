import { useSyncExternalStore } from "react";
import {
  getAppMode,
  isLocalMode,
  subscribeToAppMode,
  type AppMode,
} from "@/lib/local/appMode";

/**
 * The workspace this browser is using. Re-renders when the mode changes (leaving
 * the local workspace, picking a setup in the onboarding wizard).
 */
export function useAppMode(): AppMode | null {
  return useSyncExternalStore(subscribeToAppMode, getAppMode, () => null);
}

/** True while the no-login, browser-storage workspace is active. */
export function useIsLocalMode(): boolean {
  return useSyncExternalStore(subscribeToAppMode, isLocalMode, () => false);
}
