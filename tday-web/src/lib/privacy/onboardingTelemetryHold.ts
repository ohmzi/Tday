/**
 * A one-way latch the onboarding wizard raises while the admin's error-report question is on
 * screen.
 *
 * The question is asked after the admin has signed in — setting it needs admin rights — and signing
 * in is exactly what makes the auth guards hand the browser to the app. Without this latch
 * `AuthLayout` and `LandingPage` would navigate away mid-question and take the wizard's last step
 * with them. The wizard raises it before the sign-in request leaves and lowers it when the step is
 * done, so no other visit is affected.
 *
 * Deliberately module state rather than storage: it describes the wizard's current mount, and a
 * reload (or a crash) starting fresh means the app opens normally instead of holding a question
 * nobody is there to answer.
 */

type Listener = () => void;

const listeners = new Set<Listener>();

let held = false;

export function isOnboardingTelemetryHeld(): boolean {
  return held;
}

export function setOnboardingTelemetryHold(next: boolean): void {
  if (held === next) return;
  held = next;
  for (const listener of listeners) listener();
}

export function subscribeToOnboardingTelemetryHold(listener: Listener): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}
