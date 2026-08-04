import resolveTimezone from "@/lib/date/resolveTimezone";
import {
  clearWorkspace,
  loadWorkspace,
  updateWorkspace,
  LOCAL_USER_ID,
} from "@/lib/local/localDb";
import { LocalApiError, localNotFound } from "@/lib/local/localError";
import {
  listCompletedFloaters,
  listCompletedTodos,
  deleteCompletedFloaters,
  deleteCompletedTodos,
  updateCompletedFloater,
  updateCompletedTodo,
} from "@/lib/local/localCompleted";
import {
  completeFloater,
  createFloater,
  deleteFloater,
  listFloaters,
  prioritizeFloater,
  promoteFloater,
  reorderFloater,
  uncompleteFloater,
  updateFloater,
} from "@/lib/local/localFloaters";
import {
  createFloaterList,
  createList,
  deleteFloaterLists,
  deleteLists,
  getFloaterListDetail,
  getListDetail,
  listFloaterLists,
  listLists,
  resetFloaterList,
  updateFloaterList,
  updateList,
} from "@/lib/local/localLists";
import {
  createTaskStep,
  deleteTaskStep,
  listTaskSteps,
  reorderTaskSteps,
  toggleTaskStep,
} from "@/lib/local/localSteps";
import { brainDumpLocal, summarizeLocal } from "@/lib/local/localSummary";
import {
  completeTodo,
  createTodo,
  deleteTodo,
  deleteTodoInstance,
  demoteTodo,
  listTodos,
  patchTodoInstance,
  prioritizeTodo,
  reorderTodo,
  uncompleteTodo,
  updateTodo,
} from "@/lib/local/localTodos";
import { exportWorkspace, importWorkspace } from "@/lib/local/localTransfer";

/**
 * Request router for the local (no-login) workspace.
 *
 * `api-client` hands every `/api/*` call here while Local Mode is active, and
 * this answers with the same JSON the Ktor backend would — so React Query,
 * the feature hooks and the optimistic-update paths are untouched by the mode.
 *
 * Routes that only make sense against a real server (accounts, sharing, admin,
 * push, webhooks, API keys, calendar feed) deliberately have no handler; their
 * UI is hidden in Local Mode and a stray call fails as a plain 404 instead of
 * silently pretending to succeed.
 */

export type LocalRequest = {
  method: string;
  url: string;
  body?: string | FormData;
};

/** The synthetic session Local Mode signs the browser in as. */
function localSession() {
  return {
    user: {
      id: LOCAL_USER_ID,
      name: null,
      username: null,
      role: "USER",
      approvalStatus: "APPROVED",
      timeZone: resolveTimezone(),
      requirePasswordChange: false,
      requireSecurityQuestions: false,
    },
  };
}

function parseBody(body: string | FormData | undefined): Record<string, unknown> {
  if (typeof body !== "string" || body.trim().length === 0) return {};
  try {
    const parsed: unknown = JSON.parse(body);
    return parsed && typeof parsed === "object"
      ? (parsed as Record<string, unknown>)
      : {};
  } catch {
    throw new LocalApiError("Malformed request body", 400, "bad_request");
  }
}

function readPreferences() {
  const { preferences } = loadWorkspace();
  return { ...preferences, userPreferences: null };
}

function patchPreferences(body: Record<string, unknown>) {
  updateWorkspace((workspace) => {
    if (typeof body.sortBy === "string") workspace.preferences.sortBy = body.sortBy;
    if (body.sortBy === null) workspace.preferences.sortBy = null;
    if (typeof body.groupBy === "string") workspace.preferences.groupBy = body.groupBy;
    if (body.groupBy === null) workspace.preferences.groupBy = null;
    if (typeof body.direction === "string") {
      workspace.preferences.direction = body.direction;
    }
    if (body.direction === null) workspace.preferences.direction = null;
    if (typeof body.aiSummaryEnabled === "boolean") {
      workspace.preferences.aiSummaryEnabled = body.aiSummaryEnabled;
    }
  });
  return { message: "preferences updated" };
}

/**
 * Handles one `/api/*` request against browser storage. Returns the response
 * body (or `null` for a 204-equivalent); throws [LocalApiError] on failure.
 */
export function handleLocalRequest(request: LocalRequest): unknown {
  const method = request.method.toUpperCase();
  const [rawPath, rawQuery = ""] = request.url.split("?");
  const path = rawPath.replace(/\/+$/, "");
  const params = new URLSearchParams(rawQuery);
  const body = () => parseBody(request.body);
  const route = `${method} ${path}`;

  switch (route) {
    // ---- session ----
    case "GET /api/auth/session":
      return localSession();
    case "POST /api/auth/logout":
      return { message: "logged out" };

    // ---- workspace-wide settings ----
    case "GET /api/preferences":
      return readPreferences();
    case "PATCH /api/preferences":
      return patchPreferences(body());
    case "GET /api/app-settings":
      // A browser workspace can't reach a model, so the summary source label is
      // hidden and every summary comes from the deterministic engine.
      return { aiSummaryConfigured: false, aiSummaryHealthy: false, updatedAt: null };

    // ---- scheduled tasks ----
    case "GET /api/todo":
      return listTodos(params);
    case "POST /api/todo":
      return createTodo(body());
    case "PATCH /api/todo":
      return updateTodo(body());
    case "DELETE /api/todo":
      return deleteTodo(body());
    case "PATCH /api/todo/complete":
      return completeTodo(body());
    case "PATCH /api/todo/uncomplete":
      return uncompleteTodo(body());
    case "PATCH /api/todo/prioritize":
      return prioritizeTodo(body());
    case "PATCH /api/todo/reorder":
      return reorderTodo(body());
    case "PATCH /api/todo/instance":
      return patchTodoInstance(body());
    case "DELETE /api/todo/instance":
      return deleteTodoInstance(body());
    case "POST /api/todo/summary":
      return summarizeLocal(body());
    case "POST /api/todo/brain-dump":
      return brainDumpLocal(body());

    // ---- task steps ----
    case "POST /api/todo/steps":
      return createTaskStep(body());
    case "POST /api/todo/steps/toggle":
      return toggleTaskStep(body());
    case "POST /api/todo/steps/delete":
      return deleteTaskStep(body());
    case "POST /api/todo/steps/reorder":
      return reorderTaskSteps(body());

    // ---- anytime tasks ----
    case "GET /api/floater":
      return listFloaters();
    case "POST /api/floater":
      return createFloater(body());
    case "PATCH /api/floater":
      return updateFloater(body());
    case "DELETE /api/floater":
      return deleteFloater(body());
    case "PATCH /api/floater/complete":
      return completeFloater(body());
    case "PATCH /api/floater/uncomplete":
      return uncompleteFloater(body());
    case "PATCH /api/floater/prioritize":
      return prioritizeFloater(body());
    case "PATCH /api/floater/reorder":
      return reorderFloater(body());

    // ---- lists ----
    case "GET /api/list":
      return listLists();
    case "POST /api/list":
      return createList(body());
    case "PATCH /api/list":
      return updateList(body());
    case "DELETE /api/list":
      return deleteLists(body());
    case "GET /api/floaterList":
      return listFloaterLists();
    case "POST /api/floaterList":
      return createFloaterList(body());
    case "PATCH /api/floaterList":
      return updateFloaterList(body());
    case "DELETE /api/floaterList":
      return deleteFloaterLists(body());

    // ---- completion history ----
    case "GET /api/completedTodo":
      return listCompletedTodos();
    case "DELETE /api/completedTodo":
      return deleteCompletedTodos(body());
    case "PATCH /api/completedTodo":
      return updateCompletedTodo(body());
    case "GET /api/completedFloater":
      return listCompletedFloaters();
    case "DELETE /api/completedFloater":
      return deleteCompletedFloaters(body());
    case "PATCH /api/completedFloater":
      return updateCompletedFloater(body());

    // ---- portable data ----
    case "GET /api/export":
      return exportWorkspace();
    case "POST /api/import":
      return importWorkspace(body());

    default:
      break;
  }

  // Parameterised routes.
  const stepsMatch = /^\/api\/todo\/([^/]+)\/steps$/.exec(path);
  if (stepsMatch && method === "GET") {
    return listTaskSteps(decodeURIComponent(stepsMatch[1]));
  }

  const demoteMatch = /^\/api\/todo\/([^/]+)\/demote$/.exec(path);
  if (demoteMatch && method === "POST") {
    return demoteTodo(decodeURIComponent(demoteMatch[1]));
  }

  const promoteMatch = /^\/api\/floater\/([^/]+)\/promote$/.exec(path);
  if (promoteMatch && method === "POST") {
    return promoteFloater(decodeURIComponent(promoteMatch[1]), body());
  }

  const listResetMatch = /^\/api\/floaterList\/([^/]+)\/reset$/.exec(path);
  if (listResetMatch && method === "POST") {
    return resetFloaterList(decodeURIComponent(listResetMatch[1]));
  }

  const listDetailMatch = /^\/api\/list\/([^/]+)$/.exec(path);
  if (listDetailMatch && method === "GET") {
    return getListDetail(decodeURIComponent(listDetailMatch[1]));
  }

  const floaterListDetailMatch = /^\/api\/floaterList\/([^/]+)$/.exec(path);
  if (floaterListDetailMatch && method === "GET") {
    return getFloaterListDetail(decodeURIComponent(floaterListDetailMatch[1]));
  }

  throw localNotFound("This feature needs a T'Day account. Sign in to use it.");
}

/** Settings → "Delete local data". Everything in this browser workspace goes. */
export function deleteLocalWorkspace(): void {
  clearWorkspace();
}
