import { useSyncExternalStore } from "react";
import {
  isOnboardingTelemetryHeld,
  subscribeToOnboardingTelemetryHold,
} from "@/lib/privacy/onboardingTelemetryHold";

/**
 * Whether the onboarding wizard is holding the screen to ask the admin about error reports.
 * The auth guards read it so they do not navigate away from that step.
 */
export function useOnboardingTelemetryHold(): boolean {
  return useSyncExternalStore(
    subscribeToOnboardingTelemetryHold,
    isOnboardingTelemetryHeld,
    () => false,
  );
}
