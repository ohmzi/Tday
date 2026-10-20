import { useSyncExternalStore } from "react";
import { isLocalMode, subscribeToAppMode } from "@/lib/local/appMode";

/** True while the no-login, browser-storage workspace is active. */
export function useIsLocalMode(): boolean {
  return useSyncExternalStore(subscribeToAppMode, isLocalMode, () => false);
}
