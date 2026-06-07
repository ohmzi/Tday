// Persists a "waiting for admin approval" marker so the holding screen survives a page
// reload (the web analogue of the native secure pending marker). Only the username is
// stored — never the password. Cleared once the account is approved (authenticated) or
// the user abandons the pending account.
const KEY = "tday.pendingApprovalUsername";

export function setPendingApproval(username: string): void {
  try {
    localStorage.setItem(KEY, username);
  } catch {
    /* storage unavailable (private mode / SSR) — degrade gracefully */
  }
}

export function getPendingApproval(): string | null {
  try {
    return localStorage.getItem(KEY);
  } catch {
    return null;
  }
}

export function clearPendingApproval(): void {
  try {
    localStorage.removeItem(KEY);
  } catch {
    /* ignore */
  }
}
