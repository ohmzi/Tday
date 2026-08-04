/**
 * The local-mode workspace: every row the no-login browser workspace owns, held
 * in one `localStorage` document.
 *
 * Rows mirror the backend tables field-for-field (see `docs/DATA_MODEL.md`) so
 * `localApi` can answer with the exact DTO shapes the app already parses. Times
 * are stored the way the API sends them — a UTC wall clock with no offset, e.g.
 * `2026-08-04T09:30:00.000` — which is what `parseApiDateTime` expects.
 *
 * Clearing the browser's cookies/site data drops this document. That is the
 * documented contract of Local Mode on the web, not a failure mode.
 */

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

// Read once per session and kept in memory; every mutation writes straight back
// so a crash or a closed tab never loses more than the in-flight change.
let cached: LocalWorkspace | null = null;

export function loadWorkspace(): LocalWorkspace {
  if (cached) return cached;
  let parsed: unknown = null;
  try {
    const raw = window.localStorage.getItem(LOCAL_WORKSPACE_STORAGE_KEY);
    parsed = raw ? JSON.parse(raw) : null;
  } catch {
    // Unreadable or corrupt document — fall through to a fresh workspace.
    parsed = null;
  }
  cached = coerceWorkspace(parsed);
  return cached;
}

export function saveWorkspace(workspace: LocalWorkspace): void {
  cached = workspace;
  try {
    window.localStorage.setItem(
      LOCAL_WORKSPACE_STORAGE_KEY,
      JSON.stringify(workspace),
    );
  } catch (error) {
    // Quota or a storage-blocked context: the in-memory copy stays authoritative
    // for this session so the user's work isn't lost mid-edit.
    console.warn("Local workspace could not be persisted", error);
  }
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

/** Wipes the stored workspace (Settings → "Delete local data"). */
export function clearWorkspace(): void {
  cached = null;
  try {
    window.localStorage.removeItem(LOCAL_WORKSPACE_STORAGE_KEY);
  } catch {
    // Ignore storage failures — the reset below still clears this session.
  }
  cached = emptyWorkspace();
}

/** Drops the in-memory copy so the next read comes from storage (tests). */
export function resetWorkspaceCache(): void {
  cached = null;
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
