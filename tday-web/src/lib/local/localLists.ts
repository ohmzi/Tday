import {
  loadWorkspace,
  newLocalId,
  updateWorkspace,
  type LocalFloaterListRow,
  type LocalListRow,
  type LocalWorkspace,
  LOCAL_USER_ID,
} from "@/lib/local/localDb";
import { localBadRequest, localNotFound } from "@/lib/local/localError";
import { sortFloaters } from "@/lib/local/localFloaters";
import { epochMs, nowApiDateTime } from "@/lib/local/localTime";

/**
 * Scheduled-list and floater-list handlers — the local twin of `ListService` and
 * `FloaterListService`. Sharing has no meaning in a single-browser workspace, so
 * every list reports `myRole: "OWNER"` with no members.
 */

const LIST_COLORS = new Set([
  "RED",
  "ORANGE",
  "YELLOW",
  "LIME",
  "BLUE",
  "PURPLE",
  "PINK",
  "TEAL",
  "CORAL",
  "GOLD",
  "DEEP_BLUE",
  "ROSE",
  "LIGHT_RED",
  "BRICK",
  "SLATE",
]);

const OWNED = {
  myRole: "OWNER",
  isShared: false,
  memberCount: 0,
  ownerUsername: null,
} as const;

function normalize(value: unknown): string | null {
  const trimmed = typeof value === "string" ? value.trim() : "";
  return trimmed.length > 0 ? trimmed : null;
}

function optionalColor(value: unknown): string | null | undefined {
  if (value == null) return undefined;
  const color = String(value);
  if (!LIST_COLORS.has(color)) throw localBadRequest("color is invalid", "color");
  return color;
}

function newestFirst<T extends { createdAt: string }>(rows: T[]): T[] {
  return [...rows].sort((a, b) => (epochMs(b.createdAt) ?? 0) - (epochMs(a.createdAt) ?? 0));
}

/** Ids from a delete body, which accepts either `id` or `ids`. */
function deleteIds(body: Record<string, unknown>, label: string): string[] {
  const ids = [
    normalize(body.id),
    ...(Array.isArray(body.ids) ? body.ids.map(normalize) : []),
  ].filter((id): id is string => id !== null);
  const distinct = [...new Set(ids)];
  if (distinct.length === 0) {
    throw localBadRequest(`at least one ${label} id is required`);
  }
  return distinct;
}

// ---- scheduled lists ----

function toListDto(row: LocalListRow, todoCount: number) {
  return {
    id: row.id,
    name: row.name,
    color: row.color,
    todoCount,
    iconKey: row.iconKey,
    userID: LOCAL_USER_ID,
    createdAt: row.createdAt,
    updatedAt: row.updatedAt,
    ...OWNED,
  };
}

function pendingTodoCount(workspace: LocalWorkspace, listId: string): number {
  return workspace.todos.filter((todo) => todo.listID === listId && !todo.completed)
    .length;
}

export function listLists() {
  const workspace = loadWorkspace();
  return {
    lists: newestFirst(workspace.lists).map((row) =>
      toListDto(row, pendingTodoCount(workspace, row.id)),
    ),
  };
}

export function createList(body: Record<string, unknown>) {
  const name = normalize(body.name);
  if (!name) throw localBadRequest("title cannot be left empty", "name");
  const now = nowApiDateTime();
  const row: LocalListRow = {
    id: newLocalId(),
    name,
    color: optionalColor(body.color) ?? null,
    iconKey: normalize(body.iconKey),
    createdAt: now,
    updatedAt: now,
  };
  updateWorkspace((workspace) => workspace.lists.push(row));
  return { message: "list created", list: toListDto(row, 0) };
}

export function updateList(body: Record<string, unknown>) {
  const id = normalize(body.id);
  if (!id) throw localBadRequest("list id is required", "id");
  const color = optionalColor(body.color);

  updateWorkspace((workspace) => {
    const row = workspace.lists.find((list) => list.id === id);
    if (!row) throw localNotFound("list not found");
    const name = normalize(body.name);
    if (name) row.name = name;
    if (color !== undefined) row.color = color;
    const iconKey = normalize(body.iconKey);
    if (iconKey) row.iconKey = iconKey;
    row.updatedAt = nowApiDateTime();
  });

  return { message: "list updated" };
}

export function deleteLists(body: Record<string, unknown>) {
  const ids = deleteIds(body, "list");

  const deletedIds = updateWorkspace((workspace) => {
    const existing = workspace.lists
      .filter((list) => ids.includes(list.id))
      .map((list) => list.id);
    if (existing.length === 0) return [];

    // Deleting a list takes its tasks and their history with it.
    const todoIds = workspace.todos
      .filter((todo) => todo.listID != null && existing.includes(todo.listID))
      .map((todo) => todo.id);
    workspace.todos = workspace.todos.filter((todo) => !todoIds.includes(todo.id));
    workspace.todoInstances = workspace.todoInstances.filter(
      (instance) => !todoIds.includes(instance.todoId),
    );
    workspace.taskSteps = workspace.taskSteps.filter(
      (step) => !todoIds.includes(step.todoID),
    );
    workspace.completedTodos = workspace.completedTodos.filter(
      (entry) =>
        !(entry.listID != null && existing.includes(entry.listID)) &&
        !(entry.originalTodoID != null && todoIds.includes(entry.originalTodoID)),
    );
    workspace.lists = workspace.lists.filter((list) => !existing.includes(list.id));
    return existing;
  });

  return {
    message:
      ids.length === 1
        ? deletedIds.length === 0
          ? "list already deleted"
          : "list deleted"
        : `${deletedIds.length} lists deleted`,
    deletedIds,
  };
}

export function getListDetail(listId: string) {
  const workspace = loadWorkspace();
  const row = workspace.lists.find((list) => list.id === listId);
  if (!row) throw localNotFound("list not found");

  const todos = workspace.todos
    .filter((todo) => todo.listID === listId && !todo.completed)
    .sort((a, b) => a.order - b.order)
    .map((todo) => ({
      id: todo.id,
      title: todo.title,
      priority: todo.priority,
      due: todo.due,
      completed: todo.completed,
      order: todo.order,
    }));

  return { list: toListDto(row, pendingTodoCount(workspace, listId)), todos };
}

// ---- floater lists ----

function toFloaterListDto(row: LocalFloaterListRow, todoCount: number) {
  return {
    id: row.id,
    name: row.name,
    color: row.color,
    todoCount,
    iconKey: row.iconKey,
    userID: LOCAL_USER_ID,
    createdAt: row.createdAt,
    updatedAt: row.updatedAt,
    reusable: row.reusable,
    ...OWNED,
  };
}

function pendingFloaterCount(workspace: LocalWorkspace, listId: string): number {
  return workspace.floaters.filter(
    (floater) => floater.listID === listId && !floater.completed,
  ).length;
}

export function listFloaterLists() {
  const workspace = loadWorkspace();
  return {
    lists: newestFirst(workspace.floaterLists).map((row) =>
      toFloaterListDto(row, pendingFloaterCount(workspace, row.id)),
    ),
  };
}

export function createFloaterList(body: Record<string, unknown>) {
  const name = normalize(body.name);
  if (!name) throw localBadRequest("title cannot be left empty", "name");
  const now = nowApiDateTime();
  const row: LocalFloaterListRow = {
    id: newLocalId(),
    name,
    color: optionalColor(body.color) ?? null,
    iconKey: normalize(body.iconKey),
    reusable: body.reusable === true,
    createdAt: now,
    updatedAt: now,
  };
  updateWorkspace((workspace) => workspace.floaterLists.push(row));
  return { message: "floater list created", list: toFloaterListDto(row, 0) };
}

export function updateFloaterList(body: Record<string, unknown>) {
  const id = normalize(body.id);
  if (!id) throw localBadRequest("floater list id is required", "id");
  const color = optionalColor(body.color);

  updateWorkspace((workspace) => {
    const row = workspace.floaterLists.find((list) => list.id === id);
    if (!row) throw localNotFound("floater list not found");
    const name = normalize(body.name);
    if (name) row.name = name;
    if (color !== undefined) row.color = color;
    const iconKey = normalize(body.iconKey);
    if (iconKey) row.iconKey = iconKey;
    if (typeof body.reusable === "boolean") row.reusable = body.reusable;
    row.updatedAt = nowApiDateTime();
  });

  return { message: "floater list updated" };
}

export function deleteFloaterLists(body: Record<string, unknown>) {
  const ids = deleteIds(body, "floater list");

  const deletedIds = updateWorkspace((workspace) => {
    const existing = workspace.floaterLists
      .filter((list) => ids.includes(list.id))
      .map((list) => list.id);
    if (existing.length === 0) return [];

    const floaterIds = workspace.floaters
      .filter((floater) => floater.listID != null && existing.includes(floater.listID))
      .map((floater) => floater.id);
    workspace.floaters = workspace.floaters.filter(
      (floater) => !floaterIds.includes(floater.id),
    );
    // Floaters-only fix: durable completion history survives a deleted list
    // (the identical bug for scheduled Todos/CompletedTodos is intentionally
    // left as-is — see docs/design/completed-floaters-durability.md). Detach
    // rather than delete: drop the live listID (the backend's ON DELETE SET
    // NULL), keep originalListID so uncompleteFloater can still recreate the
    // list. listName/listColor stay as the denormalized snapshot they always
    // were.
    for (const entry of workspace.completedFloaters) {
      if (entry.listID != null && existing.includes(entry.listID)) {
        entry.listID = null;
      }
    }
    workspace.floaterLists = workspace.floaterLists.filter(
      (list) => !existing.includes(list.id),
    );
    return existing;
  });

  return {
    message:
      ids.length === 1
        ? deletedIds.length === 0
          ? "floater list already deleted"
          : "floater list deleted"
        : `${deletedIds.length} floater lists deleted`,
    deletedIds,
  };
}

export function getFloaterListDetail(listId: string) {
  const workspace = loadWorkspace();
  const row = workspace.floaterLists.find((list) => list.id === listId);
  if (!row) throw localNotFound("floater list not found");

  const floaters = sortFloaters(
    workspace.floaters.filter(
      (floater) => floater.listID === listId && !floater.completed,
    ),
  ).map((floater) => ({
    id: floater.id,
    title: floater.title,
    priority: floater.priority,
    completed: floater.completed,
    order: floater.order,
  }));

  return {
    list: toFloaterListDto(row, pendingFloaterCount(workspace, listId)),
    floaters,
  };
}

/** Reusable-list Reset: un-complete every floater in the list. */
export function resetFloaterList(listId: string) {
  const resetCount = updateWorkspace((workspace) => {
    const row = workspace.floaterLists.find((list) => list.id === listId);
    if (!row) throw localNotFound("floater list not found");

    const cleared = workspace.floaters.filter(
      (floater) => floater.listID === listId && floater.completed,
    );
    if (cleared.length === 0) return 0;

    const now = nowApiDateTime();
    for (const floater of workspace.floaters) {
      if (floater.listID !== listId) continue;
      floater.completed = false;
      floater.updatedAt = now;
    }
    workspace.completedFloaters = workspace.completedFloaters.filter(
      (entry) => entry.listID !== listId,
    );
    return cleared.length;
  });

  return { message: "floater list reset", resetCount: String(resetCount) };
}
