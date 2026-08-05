/**
 * The local-mode workspace: every row the no-login browser workspace owns, held
 * in one `localStorage` document.
 *
 * Rows mirror the backend tables field-for-field (see `docs/DATA_MODEL.md`) so
 * `localApi` can answer with the exact DTO shapes the app already parses. Times
 * are stored the way the API sends them — a UTC wall clock with no offset, e.g.
 * `2026-08-04T09:30:00.000` — which is what `parseApiDateTime` expects.
 *
 * That document is encrypted at rest with a passphrase only the user knows (see
 * `localCrypto`), so a browser profile someone else gets hold of yields nothing
 * but ciphertext. The derived key lives in this module's memory for the session
 * and is never written anywhere — losing the passphrase loses the workspace.
 *
 * Clearing the browser's cookies/site data drops this document too. That is the
 * documented contract of Local Mode on the web, not a failure mode.
 */

import {
  deriveVaultKey,
  envelopeSalt,
  isLegacyPlaintextWorkspace,
  isVaultCryptoAvailable,
  isVaultEnvelope,
  LocalVaultError,
  openVault,
  randomVaultSalt,
  sealVault,
  type VaultBytes,
} from "@/lib/local/localCrypto";

export type LocalTodoRow = {
  id: string;
  title: string;
  description: string | null;
  pinned: boolean;
  priority: string;
  due: string;
  rrule: string | null;
  timeZone: string | null;
  completed: boolean;
  order: number;
  listID: string | null;
  createdAt: string;
  updatedAt: string;
  exdates: string[];
};

export type LocalTodoInstanceRow = {
  id: string;
  todoId: string;
  recurId: string;
  instanceDate: string;
  overriddenTitle: string | null;
  overriddenDescription: string | null;
  overriddenPriority: string | null;
  overriddenDue: string | null;
  completedAt: string | null;
};

export type LocalFloaterRow = {
  id: string;
  title: string;
  description: string | null;
  pinned: boolean;
  priority: string;
  completed: boolean;
  order: number;
  listID: string | null;
  createdAt: string;
  updatedAt: string;
};

export type LocalListRow = {
  id: string;
  name: string;
  color: string | null;
  iconKey: string | null;
  createdAt: string;
  updatedAt: string;
};

export type LocalFloaterListRow = LocalListRow & { reusable: boolean };

export type LocalCompletedTodoRow = {
  id: string;
  originalTodoID: string | null;
  title: string;
  description: string | null;
  priority: string;
  due: string;
  completedAt: string;
  completedOnTime: boolean;
  daysToComplete: number;
  rrule: string | null;
  instanceDate: string | null;
  listID: string | null;
  listName: string | null;
  listColor: string | null;
  steps: LocalTaskStepRow[] | null;
};

export type LocalCompletedFloaterRow = {
  id: string;
  originalFloaterID: string | null;
  title: string;
  description: string | null;
  priority: string;
  completedAt: string;
  daysToComplete: number;
  listID: string | null;
  listName: string | null;
  listColor: string | null;
};

export type LocalTaskStepRow = {
  id: string;
  todoID: string;
  title: string;
  completed: boolean;
  position: number;
  createdAt: string;
};

export type LocalPreferencesRow = {
  sortBy: string | null;
  groupBy: string | null;
  direction: string | null;
  aiSummaryEnabled: boolean;
};

export type LocalWorkspace = {
  schemaVersion: number;
  todos: LocalTodoRow[];
  todoInstances: LocalTodoInstanceRow[];
  floaters: LocalFloaterRow[];
  lists: LocalListRow[];
  floaterLists: LocalFloaterListRow[];
  completedTodos: LocalCompletedTodoRow[];
  completedFloaters: LocalCompletedFloaterRow[];
  taskSteps: LocalTaskStepRow[];
  preferences: LocalPreferencesRow;
};

export const LOCAL_WORKSPACE_STORAGE_KEY = "tday.local.workspace.v1";

/** Bump only when a migration is needed; unknown future versions are discarded. */
const LOCAL_WORKSPACE_SCHEMA_VERSION = 1;

/** The single synthetic account Local Mode signs the browser in as. */
export const LOCAL_USER_ID = "local";

export function emptyWorkspace(): LocalWorkspace {
  return {
    schemaVersion: LOCAL_WORKSPACE_SCHEMA_VERSION,
    todos: [],
    todoInstances: [],
    floaters: [],
    lists: [],
    floaterLists: [],
    completedTodos: [],
    completedFloaters: [],
    taskSteps: [],
    preferences: {
      sortBy: null,
      groupBy: null,
      direction: null,
      aiSummaryEnabled: true,
    },
  };
}

function coerceArray<T>(value: unknown): T[] {
  return Array.isArray(value) ? (value as T[]) : [];
}

function coerceWorkspace(parsed: unknown): LocalWorkspace {
  if (!parsed || typeof parsed !== "object") return emptyWorkspace();
  const raw = parsed as Partial<LocalWorkspace>;
  if (typeof raw.schemaVersion === "number" && raw.schemaVersion > LOCAL_WORKSPACE_SCHEMA_VERSION) {
    // Written by a newer build in another tab/profile — start clean rather than
    // half-reading a shape this build doesn't understand.
    return emptyWorkspace();
  }
  const base = emptyWorkspace();
  return {
    schemaVersion: LOCAL_WORKSPACE_SCHEMA_VERSION,
    todos: coerceArray<LocalTodoRow>(raw.todos),
    todoInstances: coerceArray<LocalTodoInstanceRow>(raw.todoInstances),
    floaters: coerceArray<LocalFloaterRow>(raw.floaters),
    lists: coerceArray<LocalListRow>(raw.lists),
    floaterLists: coerceArray<LocalFloaterListRow>(raw.floaterLists),
    completedTodos: coerceArray<LocalCompletedTodoRow>(raw.completedTodos),
    completedFloaters: coerceArray<LocalCompletedFloaterRow>(raw.completedFloaters),
    taskSteps: coerceArray<LocalTaskStepRow>(raw.taskSteps),
    preferences: { ...base.preferences, ...(raw.preferences ?? {}) },
  };
}

/**
 * What the gate has to ask the user for before the workspace can be read.
 *
 * - `unsupported`: no `crypto.subtle` on this origin (plain http) — nothing can
 *   be encrypted here, so nothing is opened.
 * - `empty`:       no workspace yet; the user picks a passphrase.
 * - `legacy`:      a plaintext workspace from a build before encryption; it is
 *                  migrated in place once the user picks a passphrase.
 * - `locked`:      an encrypted workspace waiting for its passphrase.
 * - `unlocked`:    the key is in memory and `loadWorkspace` will answer.
 */
export type LocalVaultState =
  | "unsupported"
  | "empty"
  | "legacy"
  | "locked"
  | "unlocked";

// The decrypted workspace and the key that seals it. Both are memory-only: the
// key never reaches localStorage/sessionStorage, and both are dropped on lock.
let cached: LocalWorkspace | null = null;
let vaultKey: CryptoKey | null = null;
let vaultSalt: VaultBytes | null = null;

// Writes are queued because encryption is async while the callers that mutate
// the workspace are not. Each enqueued write carries the generation it was made
// in, so a wipe or a lock cancels whatever was still in flight behind it.
let writeQueue: Promise<void> = Promise.resolve();
let writeGeneration = 0;

function readStoredDocument(): unknown {
  try {
    const raw = window.localStorage.getItem(LOCAL_WORKSPACE_STORAGE_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    // Unreadable, blocked or corrupt — treated as "nothing stored".
    return null;
  }
}

/** What the browser holds right now, ignoring the in-memory key. */
function inspectStorage(): LocalVaultState {
  if (!isVaultCryptoAvailable()) return "unsupported";
  const stored = readStoredDocument();
  if (isVaultEnvelope(stored)) return "locked";
  if (isLegacyPlaintextWorkspace(stored)) return "legacy";
  return "empty";
}

/** Drives the unlock gate; see [LocalVaultState]. */
export function getLocalVaultState(): LocalVaultState {
  if (vaultKey && cached) return "unlocked";
  return inspectStorage();
}

function requireUnlocked(): LocalWorkspace {
  if (!cached || !vaultKey) {
    throw new LocalVaultError("locked", "The local workspace is locked.");
  }
  return cached;
}

export function loadWorkspace(): LocalWorkspace {
  return requireUnlocked();
}

export function saveWorkspace(workspace: LocalWorkspace): void {
  const key = vaultKey;
  const salt = vaultSalt;
  if (!key || !salt) {
    throw new LocalVaultError("locked", "The local workspace is locked.");
  }
  cached = workspace;
  // Serialised now, not inside the queued task: `updateWorkspace` hands back the
  // same object it mutated, so a later edit would otherwise leak into this write.
  const plaintext = JSON.stringify(workspace);
  const generation = writeGeneration;
  writeQueue = writeQueue.then(async () => {
    if (generation !== writeGeneration) return;
    try {
      const envelope = await sealVault(key, salt, plaintext);
      if (generation !== writeGeneration) return;
      window.localStorage.setItem(
        LOCAL_WORKSPACE_STORAGE_KEY,
        JSON.stringify(envelope),
      );
    } catch (error) {
      // Quota, a storage-blocked context or a crypto failure: the in-memory copy
      // stays authoritative for this session so the user's work isn't lost mid-edit.
      console.warn("Local workspace could not be persisted", error);
    }
  });
}

/** Resolves once every queued encrypt-and-write has settled. */
export function flushWorkspaceWrites(): Promise<void> {
  return writeQueue;
}

/** Applies [mutate] to the workspace and persists the result. */
export function updateWorkspace<T>(
  mutate: (workspace: LocalWorkspace) => T,
): T {
  const workspace = loadWorkspace();
  const result = mutate(workspace);
  saveWorkspace(workspace);
  return result;
}

async function openWith(passphrase: string, workspace: LocalWorkspace): Promise<void> {
  const salt = randomVaultSalt();
  const key = await deriveVaultKey(passphrase, salt);
  const envelope = await sealVault(key, salt, JSON.stringify(workspace));
  // Written here rather than through `saveWorkspace`, whose queued write swallows
  // storage failures: this is the one write that must not fail quietly. If it did,
  // migration would leave the *plaintext* legacy document on disk while the gate
  // opened the app and told the user their tasks were encrypted.
  try {
    // Cancels anything a previous key still had queued, so a stale write can't
    // land on top of the envelope we just sealed.
    writeGeneration += 1;
    window.localStorage.setItem(
      LOCAL_WORKSPACE_STORAGE_KEY,
      JSON.stringify(envelope),
    );
  } catch (error) {
    console.warn("Local workspace could not be protected", error);
    throw new LocalVaultError(
      "storage",
      "This browser wouldn't save the encrypted workspace. Free up site storage and try again.",
    );
  }
  vaultKey = key;
  vaultSalt = salt;
  cached = workspace;
}

/**
 * First-time setup: seals a brand-new empty workspace under [passphrase].
 * There is no recovery path — nothing but this passphrase can open it again.
 */
export function createLocalVault(passphrase: string): Promise<void> {
  return openWith(passphrase, emptyWorkspace());
}

/**
 * Migration: takes the plaintext workspace written by an older build and seals
 * it in place under [passphrase]. The rows survive; only the storage form changes.
 */
export function protectLegacyWorkspace(passphrase: string): Promise<void> {
  const legacy = readStoredDocument();
  if (!isLegacyPlaintextWorkspace(legacy)) {
    throw new LocalVaultError("corrupt", "There is no unprotected workspace to protect.");
  }
  return openWith(passphrase, coerceWorkspace(legacy));
}

/**
 * Re-derives the key from [passphrase] and decrypts the stored workspace.
 * Throws a `wrong-passphrase` [LocalVaultError] when the AES-GCM tag fails.
 */
export async function unlockLocalVault(passphrase: string): Promise<void> {
  const stored = readStoredDocument();
  if (!isVaultEnvelope(stored)) {
    throw new LocalVaultError("corrupt", "This browser has no protected workspace.");
  }
  const salt = envelopeSalt(stored);
  const key = await deriveVaultKey(passphrase, salt);
  const plaintext = await openVault(key, stored);
  let parsed: unknown = null;
  try {
    parsed = JSON.parse(plaintext);
  } catch {
    throw new LocalVaultError("corrupt", "This local workspace is unreadable.");
  }
  vaultKey = key;
  vaultSalt = salt;
  cached = coerceWorkspace(parsed);
}

/**
 * Explicit lock / sign-out: drops the derived key and the decrypted rows, and
 * cancels anything still queued so nothing is written after the lock.
 */
export function lockLocalVault(): void {
  writeGeneration += 1;
  vaultKey = null;
  vaultSalt = null;
  cached = null;
}

/** Wipes the stored workspace (Settings → "Delete local data"). */
export function clearWorkspace(): void {
  // Bumped first so an in-flight write can't resurrect the document we remove.
  writeGeneration += 1;
  try {
    window.localStorage.removeItem(LOCAL_WORKSPACE_STORAGE_KEY);
  } catch {
    // Ignore storage failures — the reset below still clears this session.
  }
  // The key stays held: the user is still in this session, and the next edit
  // simply seals a fresh empty document under it.
  cached = vaultKey ? emptyWorkspace() : null;
}

/** Drops the in-memory copy and key so the next read must unlock again (tests). */
export function resetWorkspaceCache(): void {
  lockLocalVault();
}

/**
 * Opaque row id. `crypto.randomUUID` needs a secure context, which a self-hosted
 * LAN deployment over plain http does not have, so fall back to `getRandomValues`
 * and finally to `Math.random` rather than throwing.
 */
export function newLocalId(): string {
  const cryptoApi = typeof crypto !== "undefined" ? crypto : undefined;
  if (typeof cryptoApi?.randomUUID === "function") {
    return cryptoApi.randomUUID();
  }
  if (typeof cryptoApi?.getRandomValues === "function") {
    const bytes = cryptoApi.getRandomValues(new Uint8Array(16));
    return Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
  }
  return `l${Date.now().toString(36)}${Math.random().toString(36).slice(2, 10)}`;
}
