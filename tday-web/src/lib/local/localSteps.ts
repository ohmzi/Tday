import {
  loadWorkspace,
  newLocalId,
  updateWorkspace,
  type LocalTaskStepRow,
} from "@/lib/local/localDb";
import { localBadRequest, localNotFound } from "@/lib/local/localError";
import { nowApiDateTime } from "@/lib/local/localTime";

/**
 * Flat checklist steps inside a todo — the local twin of `TaskStepService`.
 * Steps are owned by their todo and disappear with it.
 */

function orderedSteps(steps: LocalTaskStepRow[], todoId: string): LocalTaskStepRow[] {
  return steps
    .filter((step) => step.todoID === todoId)
    .sort((a, b) =>
      a.position !== b.position
        ? a.position - b.position
        : a.createdAt.localeCompare(b.createdAt),
    );
}

export function listTaskSteps(todoId: string) {
  const workspace = loadWorkspace();
  if (!workspace.todos.some((todo) => todo.id === todoId)) {
    throw localNotFound("todo not found");
  }
  return { steps: orderedSteps(workspace.taskSteps, todoId) };
}

export function createTaskStep(body: Record<string, unknown>) {
  const todoId = typeof body.todoId === "string" ? body.todoId.trim() : "";
  const title = typeof body.title === "string" ? body.title.trim() : "";
  if (title.length === 0) throw localBadRequest("step title is required", "title");

  const step = updateWorkspace((workspace) => {
    if (!workspace.todos.some((todo) => todo.id === todoId)) {
      throw localNotFound("todo not found");
    }
    const nextPosition =
      workspace.taskSteps
        .filter((entry) => entry.todoID === todoId)
        .reduce((max, entry) => Math.max(max, entry.position), -1) + 1;
    const row: LocalTaskStepRow = {
      id: newLocalId(),
      todoID: todoId,
      title,
      completed: false,
      position: nextPosition,
      createdAt: nowApiDateTime(),
    };
    workspace.taskSteps.push(row);
    return row;
  });

  return { message: "step created", step };
}

export function toggleTaskStep(body: Record<string, unknown>) {
  const id = typeof body.id === "string" ? body.id : "";
  const completed = body.completed === true;

  const step = updateWorkspace((workspace) => {
    const row = workspace.taskSteps.find((entry) => entry.id === id);
    if (!row) throw localNotFound("step not found");
    row.completed = completed;
    return { ...row };
  });

  return { message: "step toggled", step };
}

export function deleteTaskStep(body: Record<string, unknown>) {
  const id = typeof body.id === "string" ? body.id : "";

  updateWorkspace((workspace) => {
    const index = workspace.taskSteps.findIndex((entry) => entry.id === id);
    if (index < 0) throw localNotFound("step not found");
    workspace.taskSteps.splice(index, 1);
  });

  return { message: "step deleted" };
}

export function reorderTaskSteps(body: Record<string, unknown>) {
  const todoId = typeof body.todoId === "string" ? body.todoId : "";
  const orderedIds = Array.isArray(body.orderedIds)
    ? body.orderedIds.filter((id): id is string => typeof id === "string")
    : [];

  updateWorkspace((workspace) => {
    if (!workspace.todos.some((todo) => todo.id === todoId)) {
      throw localNotFound("todo not found");
    }
    // Only reorder steps that actually belong to this todo; ignore stragglers.
    orderedIds
      .map((id) => workspace.taskSteps.find((step) => step.id === id))
      .filter((step): step is LocalTaskStepRow => step?.todoID === todoId)
      .forEach((step, index) => {
        step.position = index;
      });
  });

  return { message: "steps reordered" };
}
