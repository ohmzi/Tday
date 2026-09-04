import {
  loadWorkspace,
  updateWorkspace,
  type LocalCompletedTodoRow,
  LOCAL_USER_ID,
} from "@/lib/local/localDb";
import { localBadRequest } from "@/lib/local/localError";
import { epochMs, parseDueMinute } from "@/lib/local/localTime";

/**
 * Completion history for the local workspace — the local twin of
 * `CompletedTodoService` / `CompletedFloaterService`.
 */

const PRIORITIES = new Set(["Low", "Medium", "High"]);

function optionalPriority(value: unknown): string | undefined {
  if (value == null) return undefined;
  const priority = String(value);
  if (!PRIORITIES.has(priority)) {
    throw localBadRequest("priority must be one of: Low, Medium, High", "priority");
  }
  return priority;
}

function normalize(value: unknown): string | null {
  const trimmed = typeof value === "string" ? value.trim() : "";
  return trimmed.length > 0 ? trimmed : null;
}

function newestFirst<T extends { completedAt: string }>(rows: T[]): T[] {
  return [...rows].sort(
    (a, b) => (epochMs(b.completedAt) ?? 0) - (epochMs(a.completedAt) ?? 0),
  );
}

/** The API's CompletedTodoDto. `steps` stays behind — it is a local-only snapshot. */
export function toCompletedTodoDto(row: LocalCompletedTodoRow) {
  return {
    id: row.id,
    originalTodoID: row.originalTodoID,
    title: row.title,
    description: row.description,
    priority: row.priority,
    due: row.due,
    completedAt: row.completedAt,
    completedOnTime: row.completedOnTime,
    daysToComplete: row.daysToComplete,
    rrule: row.rrule,
    instanceDate: row.instanceDate,
    listID: row.listID,
    listName: row.listName,
    listColor: row.listColor,
    userID: LOCAL_USER_ID,
  };
}

export function listCompletedTodos() {
  const workspace = loadWorkspace();
  return {
    completedTodos: newestFirst(workspace.completedTodos).map(toCompletedTodoDto),
  };
}

export function deleteCompletedTodos(body: Record<string, unknown> | null) {
  const id = normalize(body?.id);
  if (!id) {
    updateWorkspace((workspace) => {
      workspace.completedTodos = [];
    });
    return { message: "completed todos cleared" };
  }

  const removed = updateWorkspace((workspace) => {
    const before = workspace.completedTodos.length;
    workspace.completedTodos = workspace.completedTodos.filter(
      (entry) => entry.id !== id,
    );
    return before !== workspace.completedTodos.length;
  });

  return {
    message: removed ? "completed todo removed" : "completed todo already removed",
  };
}

export function updateCompletedTodo(body: Record<string, unknown>) {
  const id = normalize(body.id);
  if (!id) throw localBadRequest("completed todo id is required", "id");
  const priority = optionalPriority(body.priority);

  const due =
    body.due == null ? undefined : parseDueMinute(String(body.due));
  if (body.due != null && !due) {
    throw localBadRequest("due must be a valid ISO-8601 datetime", "due");
  }

  const touchesAnything =
    typeof body.title === "string" ||
    typeof body.description === "string" ||
    priority !== undefined ||
    due !== undefined ||
    body.rrule != null ||
    body.listID != null;

  // An empty patch means "remove it from history" — same contract as the server.
  if (!touchesAnything) return deleteCompletedTodos({ id });

  const updated = updateWorkspace((workspace) => {
    const row = workspace.completedTodos.find((entry) => entry.id === id);
    if (!row) return false;

    if (typeof body.title === "string") row.title = body.title;
    if (typeof body.description === "string") row.description = body.description;
    if (priority !== undefined) row.priority = priority;
    if (due !== undefined && due) row.due = due;
    if (body.rrule != null) row.rrule = normalize(body.rrule);
    if (body.listID != null) {
      const listID = normalize(body.listID);
      const list = listID
        ? workspace.lists.find((entry) => entry.id === listID)
        : undefined;
      if (listID && !list) throw localBadRequest("list not found", "listID");
      row.listID = listID;
      row.listName = list?.name ?? null;
      row.listColor = list?.color ?? null;
    }
    return true;
  });

  return {
    message: updated ? "completed todo updated" : "completed todo already removed",
  };
}

export function listCompletedFloaters() {
  const workspace = loadWorkspace();
  return {
    completedFloaters: newestFirst(workspace.completedFloaters).map((row) => ({
      id: row.id,
      originalFloaterID: row.originalFloaterID,
      title: row.title,
      description: row.description,
      priority: row.priority,
      completedAt: row.completedAt,
      daysToComplete: row.daysToComplete,
      listID: row.listID,
      listName: row.listName,
      listColor: row.listColor,
      // True only when this item had a list at completion (listName set) and
      // that list is now gone — the same rule as the backend's
      // CompletedFloaterDto.listDeleted. originalListID itself never leaves
      // this module — it is a local-only correlation key.
      listDeleted: row.originalListID != null && row.listID == null,
      userID: LOCAL_USER_ID,
    })),
  };
}

export function deleteCompletedFloaters(body: Record<string, unknown> | null) {
  const id = normalize(body?.id);
  if (!id) {
    // Clear-everything is left as-is, same as the backend's deleteAll(): it
    // has the same latent orphan below, not fixed here either — see the
    // single-id branch's comment.
    updateWorkspace((workspace) => {
      workspace.completedFloaters = [];
    });
    return { message: "completed floaters cleared" };
  }

  const removed = updateWorkspace((workspace) => {
    const entry = workspace.completedFloaters.find((row) => row.id === id);
    if (!entry) return false;
    // Adjacent-bug fix (matches the backend's CompletedFloaterService.deleteById):
    // this used to remove only the history row, leaving the completed
    // Floaters row it pointed at behind forever — invisible (listFloaters
    // filters completed=false) but never cleaned up. Remove both, same as
    // the real permanent-delete path (deleteFloater above).
    if (entry.originalFloaterID) {
      workspace.floaters = workspace.floaters.filter(
        (floater) => floater.id !== entry.originalFloaterID,
      );
    }
    workspace.completedFloaters = workspace.completedFloaters.filter(
      (row) => row.id !== id,
    );
    return true;
  });

  return {
    message: removed
      ? "completed floater removed"
      : "completed floater already removed",
  };
}

export function updateCompletedFloater(body: Record<string, unknown>) {
  const id = normalize(body.id);
  if (!id) throw localBadRequest("completed floater id is required", "id");
  const priority = optionalPriority(body.priority);

  const touchesAnything =
    typeof body.title === "string" ||
    typeof body.description === "string" ||
    priority !== undefined ||
    body.listID != null;

  if (!touchesAnything) return deleteCompletedFloaters({ id });

  const updated = updateWorkspace((workspace) => {
    const row = workspace.completedFloaters.find((entry) => entry.id === id);
    if (!row) return false;

    if (typeof body.title === "string") row.title = body.title;
    if (typeof body.description === "string") row.description = body.description;
    if (priority !== undefined) row.priority = priority;
    if (body.listID != null) {
      const listID = normalize(body.listID);
      const list = listID
        ? workspace.floaterLists.find((entry) => entry.id === listID)
        : undefined;
      if (listID && !list) throw localBadRequest("floater list not found", "listID");
      row.listID = listID;
      row.listName = list?.name ?? null;
      row.listColor = list?.color ?? null;
    }
    return true;
  });

  return {
    message: updated
      ? "completed floater updated"
      : "completed floater already removed",
  };
}
