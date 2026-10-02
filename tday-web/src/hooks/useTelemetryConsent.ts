import { useSyncExternalStore } from "react";
import {
  getTelemetryConsent,
  subscribeToTelemetryConsent,
  type TelemetryConsent,
} from "@/lib/privacy/telemetryConsent";

/** This browser's answer about crash and problem reports; re-renders when it changes. */
export function useTelemetryConsent(): TelemetryConsent {
  return useSyncExternalStore(
    subscribeToTelemetryConsent,
    getTelemetryConsent,
    () => "unanswered",
  );
}
