// Per-title dismissal for the "Make this repeat?" suggestion chip, so a title the
// user declined once doesn't keep nagging. Capped to the most recent entries.
// Mirrors the small localStorage helpers elsewhere (e.g. pendingApproval.ts).
//
// Titles are never stored. The feature only ever asks "have I dismissed this one
// before?", which is an equality test, so what lands on disk is a keyed digest:
// HMAC-SHA-256(salt, normalizedTitle), truncated. The salt is 128 random bits
// minted per browser workspace and kept in the same document — it must not be a
// constant in the bundle, or one precomputed table would unmask every install.
// This is what keeps a stolen browser profile from yielding real task titles even
// in Local Mode, where the workspace itself is sealed by `localCrypto`.
//
// HMAC comes from @noble/hashes rather than `crypto.subtle` for two reasons: the
// dismissal check runs synchronously during render, and `crypto.subtle` does not
// exist on a plain-http LAN deployment (see `isVaultCryptoAvailable`).

import { hmac } from "@noble/hashes/hmac";
import { sha256 } from "@noble/hashes/sha256";
import { bytesToHex, hexToBytes, randomBytes } from "@noble/hashes/utils";

const KEY = "tday.repeatSuggestion.dismissed";
const MAX_ENTRIES = 200;

/** v1 was a bare array of plaintext titles; v2 is this salted-digest document. */
const DOCUMENT_VERSION = 2;
const SALT_BYTES = 16;
/** 128 bits of HMAC output — collision-free at 200 entries, half the storage. */
const DIGEST_BYTES = 16;

type DismissalDocument = {
  version: number;
  /** hex, per-workspace, generated once and never derived from anything guessable. */
  salt: string;
  /** hex digests of normalized titles, oldest first. */
  digests: string[];
};

/** Length alone isn't enough: `hexToBytes` throws on non-hex, and a salt that
 *  always throws would silently disable dismissal forever (see `digestOf`). */
const SALT_PATTERN = new RegExp(`^[0-9a-f]{${SALT_BYTES * 2}}$`);

function isDismissalDocument(value: unknown): value is DismissalDocument {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const candidate = value as Partial<DismissalDocument>;
  return (
    candidate.version === DOCUMENT_VERSION &&
    typeof candidate.salt === "string" &&
    SALT_PATTERN.test(candidate.salt) &&
    Array.isArray(candidate.digests)
  );
}

function digestOf(saltHex: string, normalizedTitle: string): string | null {
  try {
    const mac = hmac(sha256, hexToBytes(saltHex), new TextEncoder().encode(normalizedTitle));
    return bytesToHex(mac.subarray(0, DIGEST_BYTES));
  } catch {
    // A corrupt salt would otherwise take the whole chip down mid-render.
    return null;
  }
}

function persist(doc: DismissalDocument): boolean {
  try {
    localStorage.setItem(KEY, JSON.stringify(doc));
    return true;
  } catch {
    // Quota/serialization failure — dismissal is best-effort, but the caller
    // still needs to know, because a failed migration must not leave plaintext.
    return false;
  }
}

function discard(): void {
  try {
    localStorage.removeItem(KEY);
  } catch {
    // Ignore storage failures.
  }
}

function newDocument(): DismissalDocument | null {
  try {
    return { version: DOCUMENT_VERSION, salt: bytesToHex(randomBytes(SALT_BYTES)), digests: [] };
  } catch {
    // No CSPRNG on this page. Better to lose the dismissal feature than to fall
    // back to a guessable salt or to plaintext.
    return null;
  }
}

/**
 * Reads the stored document, upgrading a v1 plaintext list in place.
 *
 * The migration runs on read rather than on the next dismissal, because a user
 * who never dismisses another suggestion must still not be left with their old
 * titles sitting in the clear. Digests are carried over, so the dismissals the
 * user already made keep working; if this browser can't mint a salt the old
 * plaintext is deleted outright rather than kept.
 */
function loadDocument(): DismissalDocument | null {
  let parsed: unknown = null;
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return null;
    parsed = JSON.parse(raw);
  } catch {
    // Unparseable — a half-written legacy array would still be readable titles,
    // so drop it rather than leave it sitting there.
    discard();
    return null;
  }

  if (isDismissalDocument(parsed)) {
    return {
      ...parsed,
      digests: parsed.digests.filter((d): d is string => typeof d === "string"),
    };
  }

  if (Array.isArray(parsed)) {
    const legacyTitles = parsed.filter((v): v is string => typeof v === "string");
    const migrated = newDocument();
    if (!migrated) {
      discard();
      return null;
    }
    for (const title of legacyTitles.slice(-MAX_ENTRIES)) {
      const digest = digestOf(migrated.salt, title);
      if (digest) migrated.digests.push(digest);
    }
    if (!persist(migrated)) {
      // The upgrade could not be written; remove the plaintext anyway. Losing a
      // few dismissals costs a chip reappearing once, and nothing more.
      discard();
      return null;
    }
    return migrated;
  }

  // Corrupt, or written by a newer build — drop it and start clean.
  discard();
  return null;
}

export function isRepeatSuggestionDismissed(normalizedTitle: string): boolean {
  if (typeof window === "undefined" || !normalizedTitle) return false;
  const doc = loadDocument();
  if (!doc) return false;
  const digest = digestOf(doc.salt, normalizedTitle);
  return digest != null && doc.digests.includes(digest);
}

export function dismissRepeatSuggestion(normalizedTitle: string): void {
  if (typeof window === "undefined" || !normalizedTitle) return;
  const doc = loadDocument() ?? newDocument();
  if (!doc) return;
  const digest = digestOf(doc.salt, normalizedTitle);
  if (!digest) return;
  const digests = doc.digests.filter((d) => d !== digest);
  digests.push(digest);
  persist({ ...doc, digests: digests.slice(-MAX_ENTRIES) });
}
