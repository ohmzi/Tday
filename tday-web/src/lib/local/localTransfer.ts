import { CURRENT_APP_VERSION } from "@/features/release/lib/release";
import { toCompletedTodoDto } from "@/lib/local/localCompleted";
import {
  loadWorkspace,
  newLocalId,
  updateWorkspace,
  type LocalCompletedFloaterRow,
  type LocalCompletedTodoRow,
  type LocalFloaterListRow,
  type LocalFloaterRow,
  type LocalListRow,
  type LocalTodoInstanceRow,
  type LocalTodoRow,
  LOCAL_USER_ID,
} from "@/lib/local/localDb";
import { localBadRequest } from "@/lib/local/localError";
import { nowApiDateTime, parseDueMinute } from "@/lib/local/localTime";

/**
 * Portable export/import for the local workspace — the `GET /api/export` and
 * `POST /api/import` contract (`TdayExport`), answered from browser storage.
 *
 * Import is additive: any id that already exists is minted fresh and every
 * reference rewritten, so a bundle is restored *alongside* what's already here
 * and never overwrites it. Same rule as the server's `ExportRemap`.
 */

const CURRENT_SCHEMA_VERSION = 1;

type ImportCounts = {
  lists: number;
  floaterLists: number;
  todos: number;
  floaters: number;
  todoInstances: number;
  completedTodos: number;
  completedFloaters: number;
  remappedIds: number;
  preferencesApplied: boolean;
};

function record(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" ? (value as Record<string, unknown>) : {};
}

function rows(value: unknown): Record<string, unknown>[] {
  return Array.isArray(value) ? value.map(record) : [];
}

function text(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function optionalText(value: unknown): string | null {
  return typeof value === "string" && value.length > 0 ? value : null;
}

export function exportWorkspace() {
  const workspace = loadWorkspace();
  const instancesByTodo = new Map<string, LocalTodoInstanceRow[]>();
  for (const instance of workspace.todoInstances) {
    const bucket = instancesByTodo.get(instance.todoId) ?? [];
    bucket.push(instance);
    instancesByTodo.set(instance.todoId, bucket);
  }

  return {
    schemaVersion: CURRENT_SCHEMA_VERSION,
    exportedAt: new Date().toISOString(),
    appVersion: CURRENT_APP_VERSION,
    source: "local-web",
    lists: workspace.lists.map((list) => ({
      id: list.id,
      name: list.name,
      color: list.color,
      todoCount: 0,
      iconKey: list.iconKey,
      userID: LOCAL_USER_ID,
      createdAt: list.createdAt,
      updatedAt: list.updatedAt,
      myRole: "OWNER",
      isShared: false,
      memberCount: 0,
      ownerUsername: null,
    })),
    floaterLists: workspace.floaterLists.map((list) => ({
      id: list.id,
      name: list.name,
      color: list.color,
      todoCount: 0,
      iconKey: list.iconKey,
      userID: LOCAL_USER_ID,
      createdAt: list.createdAt,
      updatedAt: list.updatedAt,
      reusable: list.reusable,
      myRole: "OWNER",
      isShared: false,
      memberCount: 0,
      ownerUsername: null,
    })),
    todos: workspace.todos.map((todo) => ({
      todo: {
        id: todo.id,
        title: todo.title,
        description: todo.description,
        pinned: todo.pinned,
        priority: todo.priority,
        due: todo.due,
        rrule: todo.rrule,
        timeZone: todo.timeZone,
        instanceDate: null,
        completed: todo.completed,
        order: todo.order,
        listID: todo.listID,
        userID: LOCAL_USER_ID,
        updatedAt: todo.updatedAt,
        createdAt: todo.createdAt,
      },
      exdates: todo.exdates,
      instances: (instancesByTodo.get(todo.id) ?? []).map((instance) => ({
        id: instance.id,
        recurId: instance.recurId,
        instanceDate: instance.instanceDate,
        overriddenTitle: instance.overriddenTitle,
        overriddenDescription: instance.overriddenDescription,
        overriddenPriority: instance.overriddenPriority,
        overriddenDue: instance.overriddenDue,
        completedAt: instance.completedAt,
      })),
    })),
    floaters: workspace.floaters.map((floater) => ({
      ...floater,
      userID: LOCAL_USER_ID,
    })),
    completedTodos: workspace.completedTodos.map(toCompletedTodoDto),
    completedFloaters: workspace.completedFloaters.map((entry) => ({
      ...entry,
      userID: LOCAL_USER_ID,
    })),
    preferences: workspace.preferences,
  };
}

export function importWorkspace(body: Record<string, unknown>) {
  const bundle = record(body.export);
  const dryRun = body.dryRun === true;
  const includeCompleted = body.includeCompleted !== false;
  const includePreferences = body.includePreferences !== false;

  const schemaVersion = Number(bundle.schemaVersion ?? CURRENT_SCHEMA_VERSION);
  if (Number.isFinite(schemaVersion) && schemaVersion > CURRENT_SCHEMA_VERSION) {
    throw localBadRequest(
      "This backup was made by a newer version of T'Day. Update before importing it.",
      "schemaVersion",
    );
  }

  const workspace = loadWorkspace();
  const taken = new Set<string>([
    ...workspace.lists.map((row) => row.id),
    ...workspace.floaterLists.map((row) => row.id),
    ...workspace.todos.map((row) => row.id),
    ...workspace.todoInstances.map((row) => row.id),
    ...workspace.floaters.map((row) => row.id),
    ...workspace.completedTodos.map((row) => row.id),
    ...workspace.completedFloaters.map((row) => row.id),
  ]);

  const idMap = new Map<string, string>();
  let remappedIds = 0;

  // Mints a unique id for one primary-key slot; collisions (with the workspace or
  // with an id already claimed this run) get a fresh one and references follow.
  const assign = (oldId: string): string => {
    const collides = oldId.length === 0 || taken.has(oldId);
    let finalId = oldId;
    if (collides) {
      finalId = newLocalId();
      while (taken.has(finalId)) finalId = newLocalId();
      remappedIds += 1;
    }
    taken.add(finalId);
    if (!idMap.has(oldId)) idMap.set(oldId, finalId);
    return finalId;
  };
  const remapRef = (value: unknown): string | null => {
    const id = optionalText(value);
    if (id === null) return null;
    return idMap.get(id) ?? id;
  };

  const now = nowApiDateTime();

  // Lists first so tasks and history can rewrite their list references.
  const lists: LocalListRow[] = rows(bundle.lists).map((row) => ({
    id: assign(text(row.id)),
    name: text(row.name) || "Untitled list",
    color: optionalText(row.color),
    iconKey: optionalText(row.iconKey),
    createdAt: optionalText(row.createdAt) ?? now,
    updatedAt: optionalText(row.updatedAt) ?? now,
  }));

  const floaterLists: LocalFloaterListRow[] = rows(bundle.floaterLists).map((row) => ({
    id: assign(text(row.id)),
    name: text(row.name) || "Untitled list",
    color: optionalText(row.color),
    iconKey: optionalText(row.iconKey),
    reusable: row.reusable === true,
    createdAt: optionalText(row.createdAt) ?? now,
    updatedAt: optionalText(row.updatedAt) ?? now,
  }));

  const todos: LocalTodoRow[] = [];
  const todoInstances: LocalTodoInstanceRow[] = [];
  for (const entry of rows(bundle.todos)) {
    const todo = record(entry.todo);
    const due = parseDueMinute(text(todo.due));
    if (!due) continue;
    const id = assign(text(todo.id));
    todos.push({
      id,
      title: text(todo.title),
      description: optionalText(todo.description),
      pinned: todo.pinned === true,
      priority: text(todo.priority) || "Low",
      due,
      rrule: optionalText(todo.rrule),
      timeZone: optionalText(todo.timeZone) ?? "UTC",
      completed: todo.completed === true,
      order: Number.isFinite(Number(todo.order)) ? Number(todo.order) : 0,
      listID: remapRef(todo.listID),
      createdAt: optionalText(todo.createdAt) ?? now,
      updatedAt: optionalText(todo.updatedAt) ?? now,
      exdates: Array.isArray(entry.exdates)
        ? entry.exdates
            .map((value) => parseDueMinute(text(value)))
            .filter((value): value is string => value !== null)
        : [],
    });
    for (const rawInstance of rows(entry.instances)) {
      const instanceDate = parseDueMinute(text(rawInstance.instanceDate));
      if (!instanceDate) continue;
      todoInstances.push({
        id: assign(text(rawInstance.id)),
        todoId: id,
        recurId: text(rawInstance.recurId) || instanceDate,
        instanceDate,
        overriddenTitle: optionalText(rawInstance.overriddenTitle),
        overriddenDescription: optionalText(rawInstance.overriddenDescription),
        overriddenPriority: optionalText(rawInstance.overriddenPriority),
        overriddenDue: parseDueMinute(text(rawInstance.overriddenDue)),
        completedAt: optionalText(rawInstance.completedAt),
      });
    }
  }

  const floaters: LocalFloaterRow[] = rows(bundle.floaters).map((row) => ({
    id: assign(text(row.id)),
    title: text(row.title),
    description: optionalText(row.description),
    pinned: row.pinned === true,
    priority: text(row.priority) || "Low",
    completed: row.completed === true,
    order: Number.isFinite(Number(row.order)) ? Number(row.order) : 0,
    listID: remapRef(row.listID),
    createdAt: optionalText(row.createdAt) ?? now,
    updatedAt: optionalText(row.updatedAt) ?? now,
  }));

  const completedTodos: LocalCompletedTodoRow[] = includeCompleted
    ? rows(bundle.completedTodos).map((row) => ({
        id: assign(text(row.id)),
        originalTodoID: remapRef(row.originalTodoID),
        title: text(row.title),
        description: optionalText(row.description),
        priority: text(row.priority) || "Low",
        due: parseDueMinute(text(row.due)) ?? now,
        completedAt: optionalText(row.completedAt) ?? now,
        completedOnTime: row.completedOnTime === true,
        daysToComplete: Number.isFinite(Number(row.daysToComplete))
          ? Number(row.daysToComplete)
          : 0,
        rrule: optionalText(row.rrule),
        instanceDate: parseDueMinute(text(row.instanceDate)),
        listID: remapRef(row.listID),
        listName: optionalText(row.listName),
        listColor: optionalText(row.listColor),
        steps: null,
      }))
    : [];

  const completedFloaters: LocalCompletedFloaterRow[] = includeCompleted
    ? rows(bundle.completedFloaters).map((row) => ({
        id: assign(text(row.id)),
        originalFloaterID: remapRef(row.originalFloaterID),
        title: text(row.title),
        description: optionalText(row.description),
        priority: text(row.priority) || "Low",
        completedAt: optionalText(row.completedAt) ?? now,
        daysToComplete: Number.isFinite(Number(row.daysToComplete))
          ? Number(row.daysToComplete)
          : 0,
        listID: remapRef(row.listID),
        listName: optionalText(row.listName),
        listColor: optionalText(row.listColor),
      }))
    : [];

  const preferences = includePreferences ? record(bundle.preferences) : null;
  const preferencesApplied = preferences !== null && Object.keys(preferences).length > 0;

  const imported: ImportCounts = {
    lists: lists.length,
    floaterLists: floaterLists.length,
    todos: todos.length,
    floaters: floaters.length,
    todoInstances: todoInstances.length,
    completedTodos: completedTodos.length,
    completedFloaters: completedFloaters.length,
    remappedIds,
    preferencesApplied,
  };

  if (dryRun) {
    return { dryRun: true, imported, message: "import preview" };
  }

  updateWorkspace((current) => {
    current.lists.push(...lists);
    current.floaterLists.push(...floaterLists);
    current.todos.push(...todos);
    current.todoInstances.push(...todoInstances);
    current.floaters.push(...floaters);
    current.completedTodos.push(...completedTodos);
    current.completedFloaters.push(...completedFloaters);
    if (preferences) {
      const { sortBy, groupBy, direction, aiSummaryEnabled } = preferences;
      if (typeof sortBy === "string") current.preferences.sortBy = sortBy;
      if (typeof groupBy === "string") current.preferences.groupBy = groupBy;
      if (typeof direction === "string") current.preferences.direction = direction;
      if (typeof aiSummaryEnabled === "boolean") {
        current.preferences.aiSummaryEnabled = aiSummaryEnabled;
      }
    }
  });

  return { dryRun: false, imported, message: "import complete" };
}
