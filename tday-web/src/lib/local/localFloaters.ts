import {
  loadWorkspace,
  newLocalId,
  updateWorkspace,
  type LocalFloaterListRow,
  type LocalFloaterRow,
  LOCAL_USER_ID,
} from "@/lib/local/localDb";
import { localBadRequest, localNotFound } from "@/lib/local/localError";
import {
  nowApiDateTime,
  parseDueMinute,
  wholeDaysBetween,
} from "@/lib/local/localTime";

/**
 * Anytime-task handlers for the local workspace — the browser-side twin of
 * `FloaterService`/`FloaterRoutes`.
 */

const PRIORITIES = new Set(["Low", "Medium", "High"]);
const PRIORITY_RANK: Record<string, number> = { High: 3, Medium: 2, Low: 1 };

export function toFloaterDto(row: LocalFloaterRow) {
  return { ...row, userID: LOCAL_USER_ID };
}

function requirePriority(value: unknown, fallback?: string): string {
  if (value == null) {
    if (fallback) return fallback;
    throw localBadRequest("priority is required", "priority");
  }
  const priority = String(value);
  if (!PRIORITIES.has(priority)) {
    throw localBadRequest("priority must be one of: Low, Medium, High", "priority");
  }
  return priority;
}

function normalizeId(value: unknown): string | null {
  const trimmed = typeof value === "string" ? value.trim() : "";
  return trimmed.length > 0 ? trimmed : null;
}

/** Priority desc, pinned first, then manual order — mirrors `FloaterService.getAll`. */
export function sortFloaters(rows: LocalFloaterRow[]): LocalFloaterRow[] {
  return [...rows].sort((a, b) => {
    const priorityDelta =
      (PRIORITY_RANK[b.priority] ?? 0) - (PRIORITY_RANK[a.priority] ?? 0);
    if (priorityDelta !== 0) return priorityDelta;
    if (a.pinned !== b.pinned) return a.pinned ? -1 : 1;
    return a.order - b.order;
  });
}

export function listFloaters() {
  const workspace = loadWorkspace();
  const pending = workspace.floaters.filter((floater) => !floater.completed);
  return { floaters: sortFloaters(pending).map(toFloaterDto) };
}

export function createFloater(body: Record<string, unknown>) {
  const title = typeof body.title === "string" ? body.title.trim() : "";
  if (title.length === 0) throw localBadRequest("title cannot be left empty", "title");
  const priority = requirePriority(body.priority, "Low");
  const listID = normalizeId(body.listID);
  const now = nowApiDateTime();

  const row: LocalFloaterRow = {
    id: newLocalId(),
    title,
    description: typeof body.description === "string" ? body.description : null,
    pinned: false,
    priority,
    completed: false,
    order: 0,
    listID,
    createdAt: now,
    updatedAt: now,
  };

  updateWorkspace((workspace) => {
    if (listID && !workspace.floaterLists.some((list) => list.id === listID)) {
      throw localBadRequest("floater list not found", "listID");
    }
    workspace.floaters.push(row);
  });

  return { message: "floater created", floater: toFloaterDto(row) };
}

export function updateFloater(body: Record<string, unknown>) {
  const id = normalizeId(body.id);
  if (!id) throw localBadRequest("floater id is required", "id");

  updateWorkspace((workspace) => {
    const floater = workspace.floaters.find((entry) => entry.id === id);
    if (!floater) return;

    if (typeof body.title === "string") floater.title = body.title;
    if (typeof body.description === "string") floater.description = body.description;
    if (body.priority != null) floater.priority = requirePriority(body.priority);
    if (typeof body.pinned === "boolean") floater.pinned = body.pinned;
    if (typeof body.completed === "boolean") floater.completed = body.completed;
    if (body.listID != null) {
      const listID = normalizeId(body.listID);
      if (listID && !workspace.floaterLists.some((list) => list.id === listID)) {
        throw localBadRequest("floater list not found", "listID");
      }
      floater.listID = listID;
    }
    floater.updatedAt = nowApiDateTime();
  });

  return { message: "floater updated" };
}

export function deleteFloater(body: Record<string, unknown>) {
  const id = normalizeId(body.id);
  if (!id) throw localBadRequest("floater id is required", "id");

  const removed = updateWorkspace((workspace) => {
    const index = workspace.floaters.findIndex((entry) => entry.id === id);
    workspace.completedFloaters = workspace.completedFloaters.filter(
      (entry) => entry.originalFloaterID !== id,
    );
    if (index < 0) return false;
    workspace.floaters.splice(index, 1);
    return true;
  });

  return { message: removed ? "floater deleted" : "floater already deleted" };
}

export function completeFloater(body: Record<string, unknown>) {
  const id = normalizeId(body.id);
  if (!id) throw localBadRequest("floater id is required", "id");

  updateWorkspace((workspace) => {
    const floater = workspace.floaters.find((entry) => entry.id === id);
    if (!floater) return;

    const now = nowApiDateTime();
    const list = floater.listID
      ? workspace.floaterLists.find((entry) => entry.id === floater.listID)
      : undefined;

    const alreadyCleared = workspace.completedFloaters.some(
      (entry) => entry.originalFloaterID === id,
    );
    if (!alreadyCleared) {
      workspace.completedFloaters.push({
        id: newLocalId(),
        originalFloaterID: id,
        title: floater.title,
        description: floater.description,
        priority: floater.priority,
        completedAt: now,
        daysToComplete: wholeDaysBetween(floater.createdAt, now),
        listID: floater.listID,
        listName: list?.name ?? null,
        listColor: list?.color ?? null,
        // The correlation key that survives the list being deleted (its
        // listID above gets cleared then; this does not) — see
        // uncompleteFloater below and docs/design/completed-floaters-durability.md.
        originalListID: floater.listID,
      });
    }

    floater.completed = true;
    floater.updatedAt = now;
  });

  return { message: "floater completed" };
}

/**
 * The local twin of `FloaterService.uncompleteFloater()` — see
 * docs/design/completed-floaters-durability.md §6. Two cases:
 *
 * (a) the Floaters row still exists (its list was never deleted): flip
 *     `completed` back, consume the history row, done — the old behavior.
 * (b) the Floaters row is gone (only reachable via a list deletion, since
 *     `deleteFloaterLists` below removes both pending and completed rows for
 *     the deleted list): find-or-create the list from the history row's
 *     `originalListID`, insert a fresh Floaters row from the snapshot, point
 *     it at the (re)created list.
 */
export function uncompleteFloater(body: Record<string, unknown>) {
  const id = normalizeId(body.id);
  if (!id) throw localBadRequest("floater id is required", "id");

  return updateWorkspace((workspace) => {
    const floater = workspace.floaters.find((entry) => entry.id === id);
    if (floater) {
      floater.completed = false;
      floater.updatedAt = nowApiDateTime();
      workspace.completedFloaters = workspace.completedFloaters.filter(
        (entry) => entry.originalFloaterID !== id,
      );
      const list = floater.listID
        ? workspace.floaterLists.find((entry) => entry.id === floater.listID)
        : undefined;
      return {
        message: "floater uncompleted",
        floater: toFloaterDto(floater),
        listRecreated: false,
        listID: floater.listID ?? null,
        listName: list?.name ?? null,
        listColor: list?.color ?? null,
      };
    }

    const historyEntry = workspace.completedFloaters.find(
      (entry) => entry.originalFloaterID === id,
    );
    if (!historyEntry) throw localNotFound("floater not found");

    let landedList: LocalFloaterListRow | undefined;
    let listRecreated = false;
    const originalListID = historyEntry.originalListID;
    if (originalListID) {
      // Defensive, mirrors the backend: a live list at the original id would
      // mean this floater was reachable directly above, so this is
      // unreachable in practice — kept for parity with the documented
      // find-or-create order.
      landedList = workspace.floaterLists.find((entry) => entry.id === originalListID);
      if (!landedList) {
        landedList = workspace.floaterLists.find(
          (entry) => entry.recreatedFromListID === originalListID,
        );
      }
      if (!landedList) {
        const now = nowApiDateTime();
        landedList = {
          id: newLocalId(),
          name: historyEntry.listName ?? "List",
          color: historyEntry.listColor,
          iconKey: null,
          reusable: false,
          recreatedFromListID: originalListID,
          createdAt: now,
          updatedAt: now,
        };
        workspace.floaterLists.push(landedList);
      }
      listRecreated = true;
    }

    const now = nowApiDateTime();
    const restored: LocalFloaterRow = {
      id: newLocalId(),
      title: historyEntry.title,
      description: historyEntry.description,
      pinned: false,
      priority: historyEntry.priority,
      completed: false,
      order: 0,
      listID: landedList?.id ?? null,
      createdAt: now,
      updatedAt: now,
    };
    workspace.floaters.push(restored);
    workspace.completedFloaters = workspace.completedFloaters.filter(
      (entry) => entry.id !== historyEntry.id,
    );

    return {
      message: "floater uncompleted",
      floater: toFloaterDto(restored),
      listRecreated,
      listID: landedList?.id ?? null,
      listName: landedList?.name ?? null,
      listColor: landedList?.color ?? null,
    };
  });
}

export function prioritizeFloater(body: Record<string, unknown>) {
  const id = normalizeId(body.id);
  if (!id) throw localBadRequest("floater id is required", "id");
  const priority = requirePriority(body.priority);

  updateWorkspace((workspace) => {
    const floater = workspace.floaters.find((entry) => entry.id === id);
    if (!floater) return;
    floater.priority = priority;
    floater.updatedAt = nowApiDateTime();
  });

  return { message: "priority updated" };
}

export function reorderFloater(body: Record<string, unknown>) {
  const id = normalizeId(body.id);
  if (!id) throw localBadRequest("floater id is required", "id");
  const order = Number(body.order);
  if (!Number.isFinite(order)) throw localBadRequest("order is required", "order");

  updateWorkspace((workspace) => {
    const floater = workspace.floaters.find((entry) => entry.id === id);
    if (!floater) return;
    floater.order = order;
    floater.updatedAt = nowApiDateTime();
  });

  return { message: "order updated" };
}

/** Schedules a floater into a real todo; the floater row is consumed. */
export function promoteFloater(floaterId: string, body: Record<string, unknown>) {
  const id = normalizeId(floaterId);
  if (!id) throw localBadRequest("floater id is required", "id");
  const due = parseDueMinute(typeof body.due === "string" ? body.due : null);
  if (!due) throw localBadRequest("due must be a valid ISO-8601 datetime", "due");
  const rrule = normalizeId(body.rrule);

  const todo = updateWorkspace((workspace) => {
    const floater = workspace.floaters.find((entry) => entry.id === id);
    if (!floater) throw localNotFound("floater not found");

    const now = nowApiDateTime();
    const row = {
      id: newLocalId(),
      title: floater.title,
      description: floater.description,
      pinned: floater.pinned,
      priority: floater.priority,
      due,
      rrule,
      timeZone: "UTC",
      completed: false,
      order: 0,
      // Floater lists and todo lists are separate types; membership stays behind.
      listID: null,
      createdAt: floater.createdAt,
      updatedAt: now,
      exdates: [],
    };
    workspace.todos.push(row);
    workspace.floaters = workspace.floaters.filter((entry) => entry.id !== id);
    workspace.completedFloaters = workspace.completedFloaters.filter(
      (entry) => entry.originalFloaterID !== id,
    );
    return row;
  });

  // Built field-by-field: `exdates` is workspace-internal and the API's TodoDto
  // never carries it.
  return {
    message: "floater promoted",
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
      createdAt: todo.createdAt,
      updatedAt: todo.updatedAt,
    },
  };
}
