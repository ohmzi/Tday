/**
 * The shapes a Local Mode workspace takes on disk, and the passphrase
 * encryption behind the protected one.
 *
 * Everything here is pure — it takes a passphrase and a string and hands back an
 * envelope — so it can be unit-tested without touching storage or React.
 * `localDb` owns where the document lives and when the derived key is dropped.
 *
 * Encrypted format v1: PBKDF2-SHA256 (310,000 rounds, matching the backend's
 * password policy) → AES-GCM-256, with a fresh random 96-bit IV on every write.
 * The version pins those parameters, so the only things that ever reach storage
 * are `{version, salt, iv, ciphertext}` — never the passphrase, never the key.
 *
 * Encryption is the default but not compulsory: a user who declines it gets the
 * open document below instead. Three shapes share one storage key, and the
 * predicates here are what keep them apart.
 */

export const LOCAL_VAULT_VERSION = 1;

/** Pins the open document's wrapper, as [LOCAL_VAULT_VERSION] pins the envelope. */
export const LOCAL_OPEN_VERSION = 1;

/** Matches the backend's PBKDF2 policy; raising it requires a new version. */
export const LOCAL_VAULT_ITERATIONS = 310_000;

const SALT_BYTES = 16;
/** 96 bits — the IV length AES-GCM is specified for. */
const IV_BYTES = 12;

/**
 * Bytes backed by a plain `ArrayBuffer` — the only shape WebCrypto's
 * `BufferSource` accepts now that TypeScript's typed arrays are generic.
 */
export type VaultBytes = Uint8Array<ArrayBuffer>;

export type LocalVaultEnvelope = {
  version: number;
  /** base64 */
  salt: string;
  /** base64, regenerated per write — an AES-GCM IV must never repeat under one key. */
  iv: string;
  /** base64 ciphertext with the GCM tag appended (WebCrypto's own layout). */
  ciphertext: string;
};

/**
 * A workspace the user chose not to encrypt.
 *
 * The wrapper earns its place as a discriminator: a bare workspace object on
 * disk is indistinguishable from the pre-encryption legacy document, so without
 * it every load of an intentionally open workspace would re-fire the "Protect
 * your tasks" migration prompt. `protection` states the choice explicitly, in
 * the document itself rather than a side flag that could drift out of step with
 * what is actually stored.
 */
export type LocalOpenWorkspaceDocument = {
  protection: "none";
  version: number;
  /** A `LocalWorkspace`, coerced by `localDb` on read like any stored shape. */
  workspace: unknown;
};

export type LocalVaultErrorCode =
  /** This origin has no `crypto.subtle` (a page served over plain http). */
  | "unsupported"
  /** The AES-GCM tag did not verify — wrong passphrase, or a tampered blob. */
  | "wrong-passphrase"
  /** Asked to read or write while no key is held in memory. */
  | "locked"
  /** Stored document is not an envelope this build can read. */
  | "corrupt"
  /** The browser refused to persist the envelope (quota, blocked storage). */
  | "storage";

export class LocalVaultError extends Error {
  constructor(
    public code: LocalVaultErrorCode,
    message: string,
  ) {
    super(message);
    this.name = "LocalVaultError";
  }
}

/**
 * `crypto.subtle` only exists in a secure context, which a self-hosted LAN
 * deployment over plain http is not. Callers surface that as its own state
 * rather than quietly falling back to storing tasks in the clear.
 */
export function isVaultCryptoAvailable(): boolean {
  return typeof crypto !== "undefined" && typeof crypto.subtle?.importKey === "function";
}

function requireSubtle(): SubtleCrypto {
  if (!isVaultCryptoAvailable()) {
    throw new LocalVaultError(
      "unsupported",
      "This browser page can't encrypt local data. Open T'Day over https:// or on localhost.",
    );
  }
  return crypto.subtle;
}

function toBase64(bytes: VaultBytes): string {
  let binary = "";
  // Built one char at a time: `String.fromCharCode(...bytes)` blows the argument
  // limit once a workspace grows past a few tens of kilobytes.
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

function fromBase64(value: string): VaultBytes {
  const binary = atob(value);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  return bytes;
}

/** True when [value] is a stored envelope this build knows how to open. */
export function isVaultEnvelope(value: unknown): value is LocalVaultEnvelope {
  if (!value || typeof value !== "object") return false;
  const candidate = value as Partial<LocalVaultEnvelope>;
  return (
    candidate.version === LOCAL_VAULT_VERSION &&
    typeof candidate.salt === "string" &&
    typeof candidate.iv === "string" &&
    typeof candidate.ciphertext === "string"
  );
}

/** True when [value] is an open document this build knows how to read. */
export function isOpenWorkspaceDocument(
  value: unknown,
): value is LocalOpenWorkspaceDocument {
  if (!value || typeof value !== "object") return false;
  const candidate = value as Partial<LocalOpenWorkspaceDocument>;
  return (
    candidate.protection === "none" &&
    // A future wrapper is not adopted, exactly as a future envelope is not.
    candidate.version === LOCAL_OPEN_VERSION &&
    typeof candidate.workspace === "object" &&
    candidate.workspace !== null
  );
}

/**
 * Wraps an already-serialised workspace as an open document.
 *
 * Takes the JSON *text* rather than the object so `saveWorkspace` can serialise
 * eagerly, before any await — the same rule the sealed path follows, so a later
 * edit can't leak into a write that is already in flight.
 */
export function openWorkspaceDocumentJson(workspaceJson: string): string {
  return `{"protection":"none","version":${LOCAL_OPEN_VERSION},"workspace":${workspaceJson}}`;
}

/**
 * True for the pre-encryption plaintext document (a workspace written by a build
 * before this one). Detected by shape, because the storage key is unchanged.
 *
 * The other two shapes are ruled out first: an open document also holds rows in
 * the clear, and mistaking one for a legacy document would send the user back to
 * the migration prompt they already declined.
 */
export function isLegacyPlaintextWorkspace(value: unknown): boolean {
  if (!value || typeof value !== "object") return false;
  if (isVaultEnvelope(value) || isOpenWorkspaceDocument(value)) return false;
  const candidate = value as Record<string, unknown>;
  return typeof candidate.schemaVersion === "number" || Array.isArray(candidate.todos);
}

export function randomVaultSalt(): VaultBytes {
  return crypto.getRandomValues(new Uint8Array(SALT_BYTES));
}

/**
 * Stretches [passphrase] into the AES key. The key is non-extractable, so even
 * a script running on the page can't read the bytes back out of it.
 */
export async function deriveVaultKey(
  passphrase: string,
  salt: VaultBytes,
): Promise<CryptoKey> {
  const subtle = requireSubtle();
  const material = await subtle.importKey(
    "raw",
    new TextEncoder().encode(passphrase),
    "PBKDF2",
    false,
    ["deriveKey"],
  );
  return subtle.deriveKey(
    { name: "PBKDF2", salt, iterations: LOCAL_VAULT_ITERATIONS, hash: "SHA-256" },
    material,
    { name: "AES-GCM", length: 256 },
    false,
    ["encrypt", "decrypt"],
  );
}

/** Encrypts [plaintext] under [key], minting a fresh IV for this write. */
export async function sealVault(
  key: CryptoKey,
  salt: VaultBytes,
  plaintext: string,
): Promise<LocalVaultEnvelope> {
  const subtle = requireSubtle();
  const iv = crypto.getRandomValues(new Uint8Array(IV_BYTES));
  const ciphertext = await subtle.encrypt(
    { name: "AES-GCM", iv },
    key,
    new TextEncoder().encode(plaintext),
  );
  return {
    version: LOCAL_VAULT_VERSION,
    salt: toBase64(salt),
    iv: toBase64(iv),
    ciphertext: toBase64(new Uint8Array(ciphertext)),
  };
}

/**
 * Decrypts [envelope] with [key]. A wrong passphrase fails the GCM tag check,
 * which WebCrypto reports as a bare `OperationError` — translated here into a
 * `wrong-passphrase` [LocalVaultError] so callers can say something useful.
 */
export async function openVault(
  key: CryptoKey,
  envelope: LocalVaultEnvelope,
): Promise<string> {
  const subtle = requireSubtle();
  let plaintext: ArrayBuffer;
  try {
    plaintext = await subtle.decrypt(
      { name: "AES-GCM", iv: fromBase64(envelope.iv) },
      key,
      fromBase64(envelope.ciphertext),
    );
  } catch {
    throw new LocalVaultError(
      "wrong-passphrase",
      "That passphrase doesn't unlock this workspace.",
    );
  }
  return new TextDecoder().decode(plaintext);
}

/** Reads the salt an existing envelope was sealed with, so unlock can re-derive. */
export function envelopeSalt(envelope: LocalVaultEnvelope): VaultBytes {
  try {
    return fromBase64(envelope.salt);
  } catch {
    throw new LocalVaultError("corrupt", "This local workspace is unreadable.");
  }
}
