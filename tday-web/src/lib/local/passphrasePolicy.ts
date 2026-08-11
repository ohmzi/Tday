/**
 * The rules a Local Mode passphrase has to meet.
 *
 * Lives on its own because two screens ask for one — the gate, when a workspace
 * is first created, and Settings, when an unencrypted workspace is upgraded —
 * and a security minimum kept in two places is a security minimum that drifts.
 */

/**
 * An attacker working on the stored ciphertext is offline: nothing rate-limits
 * the guesses, and there is no account to lock. Server passwords get 8; this
 * gets more.
 */
export const MIN_PASSPHRASE_LENGTH = 10;

export type PassphraseProblem = "too-short" | "mismatch";

/** The reason [passphrase] and [confirmation] are unusable, or `null` if they are. */
export function validatePassphrase(
  passphrase: string,
  confirmation: string,
): PassphraseProblem | null {
  if (passphrase.length < MIN_PASSPHRASE_LENGTH) return "too-short";
  if (passphrase !== confirmation) return "mismatch";
  return null;
}
