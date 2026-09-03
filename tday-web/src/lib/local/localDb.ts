/**
 * The local-mode workspace: every row the no-login browser workspace owns, held
 * in one `localStorage` document.
 *
 * Rows mirror the backend tables field-for-field (see `docs/DATA_MODEL.md`) so
 * `localApi` can answer with the exact DTO shapes the app already parses. Times
 * are stored the way the API sends them — a UTC wall clock with no offset, e.g.
 * `2026-08-04T09:30:00.000` — which is what `parseApiDateTime` expects.
 *
 * By default that document is encrypted at rest with a passphrase only the user
 * knows (see `localCrypto`), so a browser profile someone else gets hold of
 * yields nothing but ciphertext. The derived key lives in this module's memory
 * for the session and is never written anywhere — losing the passphrase loses
 * the workspace.
 *
 * A user can decline the passphrase, in which case the same rows are stored in
 * the clear inside an open document. Everything below is written so the two
 * differ only in the form the bytes take on the way to storage: one cache, one
 * write queue, one cancellation guard. `protection` says which form is in play.
 *
 * Clearing the browser's cookies/site data drops this document too. That is the
 * documented contract of Local Mode on the web, not a failure mode.
 */

import {
  deriveVaultKey,
  envelopeSalt,
  isLegacyPlaintextWorkspace,
  isOpenWorkspaceDocument,
  isVaultCryptoAvailable,
  isVaultEnvelope,
  LocalVaultError,
  openVault,
  openWorkspaceDocumentJson,
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

export type LocalFloaterListRow = LocalListRow & {
  reusable: boolean;
  /**
   * Set only on a list this workspace recreated from `uncompleteFloater`'s
   * find-or-create step (see localFloaters.ts) — names the id of the
   * original (deleted) list. Never surfaced in a FloaterListDto response;
   * purely the local twin of the backend's `FloaterLists.recreatedFromListID`.
   */
  recreatedFromListID?: string | null;
};

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
  /**
   * Snapshot of `listID` at completion time, kept even after `listID` is
   * cleared by a list deletion — the local twin of the backend's
   * `CompletedFloaters.originalListID`. Never sent in a DTO response; it is
   * only what `uncompleteFloater`'s find-or-create reads to recreate the
   * list. See docs/design/completed-floaters-durability.md.
   */
  originalListID?: string | null;
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
  // "scheduled" or "floater" — which root feed opens on a fresh cold launch.
  defaultHomeScreen: string;
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
      defaultHomeScreen: "scheduled",
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
 * - `unsupported`: no `crypto.subtle` on this origin (plain http) and nothing
 *                  readable stored — the user is offered an open workspace,
 *                  because encryption is the one thing that can't be had here.
 * - `empty`:       no workspace yet; the user picks a passphrase or declines one.
 * - `legacy`:      a plaintext workspace from a build before encryption existed;
 *                  the user is offered the passphrase, and may decline it.
 * - `locked`:      an encrypted workspace waiting for its passphrase.
 * - `open`:        stored in the clear by choice — readable with no key, but not
 *                  yet held by this session. Distinct from `unlocked` because
 *                  `inspectStorage` is a pure probe: nothing is cached yet, so
 *                  `loadWorkspace` would still throw.
 * - `unlocked`:    the workspace is in memory and `loadWorkspace` will answer,
 *                  whichever protection it uses.
 */
export type LocalVaultState =
  | "unsupported"
  | "empty"
  | "legacy"
  | "locked"
  | "open"
  | "unlocked";

/** How the document this session holds is stored. */
export type LocalProtection = "passphrase" | "none";

// The workspace this session holds, and how it is written back. `protection` is
// the single "something is held" flag; the key and salt exist only alongside
// `"passphrase"`. All memory-only: the key never reaches localStorage or
// sessionStorage, and everything here is dropped on lock.
let cached: LocalWorkspace | null = null;
let protection: LocalProtection | null = null;
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

/** What the browser holds right now, ignoring what this session already has. */
function inspectStorage(): LocalVaultState {
  const stored = readStoredDocument();
  // Probed before the crypto check on purpose: a plain-http origin has no
  // `crypto.subtle`, but an open document needs none to be read. Refusing here
  // would lock the user out of rows sitting in front of us in the clear.
  if (isOpenWorkspaceDocument(stored)) return "open";
  if (!isVaultCryptoAvailable()) return "unsupported";
  if (isVaultEnvelope(stored)) return "locked";
  if (isLegacyPlaintextWorkspace(stored)) return "legacy";
  return "empty";
}

/** Drives the unlock gate; see [LocalVaultState]. */
export function getLocalVaultState(): LocalVaultState {
  if (cached && protection) return "unlocked";
  return inspectStorage();
}

/** How the held workspace is stored, or `null` when this session holds none. */
export function getLocalProtection(): LocalProtection | null {
  return cached ? protection : null;
}

function requireUnlocked(): LocalWorkspace {
  if (!cached || !protection) {
    throw new LocalVaultError("locked", "The local workspace is not open in this tab.");
  }
  return cached;
}

export function loadWorkspace(): LocalWorkspace {
  return requireUnlocked();
}

export function saveWorkspace(workspace: LocalWorkspace): void {
  // Captured synchronously: a lock (or an upgrade to a passphrase) between here
  // and the queued task must not change which form this particular write takes.
  const mode = protection;
  const key = vaultKey;
  const salt = vaultSalt;
  if (!mode || (mode === "passphrase" && (!key || !salt))) {
    throw new LocalVaultError("locked", "The local workspace is not open in this tab.");
  }
  cached = workspace;
  // Serialised now, not inside the queued task: `updateWorkspace` hands back the
  // same object it mutated, so a later edit would otherwise leak into this write.
  const plaintext = JSON.stringify(workspace);
  const generation = writeGeneration;
  // The open form needs no await, but it still goes through the queue: that keeps
  // write ordering against a later upgrade, and one cancellation guard rather
  // than two.
  writeQueue = writeQueue.then(async () => {
    if (generation !== writeGeneration) return;
    try {
      const document =
        key && salt
          ? JSON.stringify(await sealVault(key, salt, plaintext))
          : openWorkspaceDocumentJson(plaintext);
      if (generation !== writeGeneration) return;
      window.localStorage.setItem(LOCAL_WORKSPACE_STORAGE_KEY, document);
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
  protection = "passphrase";
  vaultKey = key;
  vaultSalt = salt;
  cached = workspace;
}

/**
 * The open-document counterpart of [openWith]: writes [workspace] in the clear
 * and adopts it. Synchronous, because there is no key to derive — an async
 * version would add a paint where the gate is neither open nor closed.
 *
 * Writes loudly for the same reason [openWith] does: if this failed quietly the
 * gate would open the app over a document that isn't there.
 */
function openInTheClear(workspace: LocalWorkspace): void {
  const document = openWorkspaceDocumentJson(JSON.stringify(workspace));
  try {
    // Cancels anything a previous session still had queued, so a stale write
    // can't land on top of the document we just wrote.
    writeGeneration += 1;
    window.localStorage.setItem(LOCAL_WORKSPACE_STORAGE_KEY, document);
  } catch (error) {
    console.warn("Local workspace could not be stored", error);
    throw new LocalVaultError(
      "storage",
      "This browser wouldn't save the workspace. Free up site storage and try again.",
    );
  }
  protection = "none";
  vaultKey = null;
  vaultSalt = null;
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
 * First-time setup for a user who declined the passphrase: a brand-new empty
 * workspace, stored in the clear. Anyone with this browser profile can read it.
 */
export function createOpenLocalWorkspace(): void {
  openInTheClear(emptyWorkspace());
}

/** Adopts the open document already in storage into this session. */
export function openLocalWorkspace(): void {
  const stored = readStoredDocument();
  if (!isOpenWorkspaceDocument(stored)) {
    throw new LocalVaultError("corrupt", "This browser has no unencrypted workspace.");
  }
  writeGeneration += 1;
  protection = "none";
  vaultKey = null;
  vaultSalt = null;
  cached = coerceWorkspace(stored.workspace);
}

/**
 * The "skip" answer to the migration prompt: wraps the plaintext rows an older
 * build left behind into an open document, recording that staying unencrypted
 * was a choice. Without the wrapper the prompt would fire again on every load.
 */
export function keepLegacyWorkspaceOpen(): void {
  const legacy = readStoredDocument();
  if (!isLegacyPlaintextWorkspace(legacy)) {
    throw new LocalVaultError("corrupt", "There is no unprotected workspace here.");
  }
  openInTheClear(coerceWorkspace(legacy));
}

/**
 * Seals whatever unencrypted rows this browser holds — a pre-encryption legacy
 * document, or one stored in the clear by choice — under [passphrase], in place.
 * The rows survive; only the storage form changes.
 *
 * One-way: there is no downgrade and no rekey.
 */
export async function protectPlaintextWorkspace(passphrase: string): Promise<void> {
  // Load-bearing: `openWith` bumps `writeGeneration` before its write, so a
  // queued open write left in flight here would be cancelled while the session
  // carried on — an edit made moments ago would reach neither document.
  await flushWorkspaceWrites();
  // The in-memory rows win: sealing the stored copy would drop newer edits.
  if (cached && protection === "none") return openWith(passphrase, cached);
  const stored = readStoredDocument();
  if (isOpenWorkspaceDocument(stored)) {
    return openWith(passphrase, coerceWorkspace(stored.workspace));
  }
  if (isLegacyPlaintextWorkspace(stored)) {
    return openWith(passphrase, coerceWorkspace(stored));
  }
  throw new LocalVaultError("corrupt", "There is no unprotected workspace to protect.");
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
  protection = "passphrase";
  vaultKey = key;
  vaultSalt = salt;
  cached = coerceWorkspace(parsed);
}

/**
 * Explicit lock / sign-out: drops this session's hold on the workspace and
 * cancels anything still queued, so nothing is written after this point.
 *
 * For an encrypted workspace that is a real lock — the derived key goes with the
 * rows. For one stored in the clear there is no key to drop, so it is only the
 * cache: the document stays where it is and the next load adopts it again
 * without asking. That is what the user chose.
 */
export function lockLocalVault(): void {
  writeGeneration += 1;
  protection = null;
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
  // The session keeps its hold: the user is still here, and the next edit simply
  // writes a fresh empty document in whatever form this workspace uses.
  cached = protection ? emptyWorkspace() : null;
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
