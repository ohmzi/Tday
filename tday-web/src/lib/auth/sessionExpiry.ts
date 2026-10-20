// Lightweight pub/sub used to bridge a confirmed authentication failure (HTTP 401)
// detected deep in the API client up to the AuthProvider, which owns session state.
//
// The API client cannot import the AuthProvider (it would create a cycle and the
// provider isn't available outside React), so a 401 on any data request fires
// `notifySessionExpired()` and the AuthProvider subscribes via `onSessionExpired()`
// to clear the session and redirect to login.

type Listener = () => void;

const listeners = new Set<Listener>();

export function onSessionExpired(listener: Listener): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function notifySessionExpired(): void {
  // Copy before iterating so a listener that unsubscribes itself can't mutate
  // the set mid-iteration.
  for (const listener of [...listeners]) {
    listener();
  }
}
