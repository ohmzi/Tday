import {
  loadWorkspace,
  newLocalId,
  updateWorkspace,
  type LocalTodoRow,
  type LocalWorkspace,
  LOCAL_USER_ID,
} from "@/lib/local/localDb";
import { localBadRequest, localNotFound } from "@/lib/local/localError";
import {
  epochMs,
  nowApiDateTime,
  parseDueMinute,
  sameInstant,
  toApiDateTime,
  wholeDaysBetween,
} from "@/lib/local/localTime";

/**
 * Scheduled-task handlers for the local workspace — the browser-side twin of
 * `TodoService`/`TodoRoutes`. Response shapes are the `TodoDto` the app already
 * parses, including the deliberate omission of `exdates`/`instances` (the server
 * doesn't send them either, and clients expand recurrences from `rrule`).
 */

const PRIORITIES = new Set(["Low", "Medium", "High"]);

export type TodoDto = {
  id: string;
  title: string;
  description: string | null;
  pinned: boolean;
  priority: string;
  due: string;
  rrule: string | null;
  timeZone: string | null;
  instanceDate: string | null;
  completed: boolean;
  order: number;
  listID: string | null;
  userID: string;
  updatedAt: string;
  createdAt: string;
};

function toTodoDto(row: LocalTodoRow): TodoDto {
  return {
    id: row.id,
    title: row.title,
    description: row.description,
    pinned: row.pinned,
    priority: row.priority,
    due: row.due,
    rrule: row.rrule,
    timeZone: row.timeZone,
    instanceDate: null,
    completed: row.completed,
    order: row.order,
    listID: row.listID,
    userID: LOCAL_USER_ID,
    updatedAt: row.updatedAt,
    createdAt: row.createdAt,
  };
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

function findTodo(workspace: LocalWorkspace, id: string): LocalTodoRow | undefined {
  return workspace.todos.find((todo) => todo.id === id);
}

export function createTodo(body: Record<string, unknown>) {
  const title = typeof body.title === "string" ? body.title.trim() : "";
  if (title.length === 0) throw localBadRequest("title cannot be left empty", "title");
  const due = parseDueMinute(typeof body.due === "string" ? body.due : null);
  if (!due) throw localBadRequest("due must be a valid ISO-8601 datetime", "due");
  const priority = requirePriority(body.priority, "Low");
  const rrule = normalizeId(body.rrule);
  const listID = normalizeId(body.listID);
  const now = nowApiDateTime();

  const row: LocalTodoRow = {
    id: newLocalId(),
    title,
    description: typeof body.description === "string" ? body.description : null,
    pinned: false,
    priority,
    due,
    rrule,
    timeZone: "UTC",
    completed: false,
    order: 0,
    listID,
    createdAt: now,
    updatedAt: now,
    exdates: [],
  };

  updateWorkspace((workspace) => {
    if (listID && !workspace.lists.some((list) => list.id === listID)) {
      throw localBadRequest("list not found", "listID");
    }
    workspace.todos.push(row);
  });

  return { message: "todo created", todo: toTodoDto(row) };
}

/**
 * `GET /api/todo`. One-off todos are filtered to the requested window; recurring
 * templates are always returned in full so the client can expand occurrences
 * itself (exactly what `TodoService.getByDateRange`/`getTimeline` do).
 */
export function listTodos(params: URLSearchParams) {
  const workspace = loadWorkspace();
  const pending = workspace.todos.filter((todo) => !todo.completed);
  const recurring = pending.filter((todo) => todo.rrule != null);
  const oneOff = pending.filter((todo) => todo.rrule == null);

  if (params.get("timeline") === "true") {
    const sorted = [...oneOff].sort((a, b) => {
      const dueDelta = (epochMs(a.due) ?? 0) - (epochMs(b.due) ?? 0);
      return dueDelta !== 0 ? dueDelta : a.order - b.order;
    });
    return { todos: [...sorted, ...recurring].map(toTodoDto) };
  }

  const start = Number(params.get("start"));
  const end = Number(params.get("end"));
  if (!Number.isFinite(start)) throw localBadRequest("date range start not specified");
  if (!Number.isFinite(end)) throw localBadRequest("date range end not specified");

  const inRange = oneOff
    .filter((todo) => {
      const dueMs = epochMs(todo.due);
      return dueMs !== null && dueMs >= start && dueMs <= end;
    })
    .sort((a, b) => (epochMs(b.createdAt) ?? 0) - (epochMs(a.createdAt) ?? 0));

  return { todos: [...inRange, ...recurring].map(toTodoDto) };
}

export function updateTodo(body: Record<string, unknown>) {
  const id = normalizeId(body.id);
  if (!id) throw localBadRequest("todo id is required", "id");

  updateWorkspace((workspace) => {
    const todo = findTodo(workspace, id);
    if (!todo) return;

    if (typeof body.title === "string") todo.title = body.title;
    if (typeof body.description === "string") todo.description = body.description;
    if (body.priority != null) todo.priority = requirePriority(body.priority);
    if (typeof body.pinned === "boolean") todo.pinned = body.pinned;
    if (typeof body.completed === "boolean") todo.completed = body.completed;

    const dueProvided = typeof body.due === "string" && body.due.trim().length > 0;
    if (body.dateChanged === true && !dueProvided) {
      throw localBadRequest("due is required", "due");
    }
    if (dueProvided) {
      const due = parseDueMinute(String(body.due));
      if (!due) throw localBadRequest("due must be a valid ISO-8601 datetime", "due");
      todo.due = due;
    }
    if (body.rruleChanged === true || body.rrule != null) {
      todo.rrule = normalizeId(body.rrule);
    }
    if (body.listID != null) {
      const listID = normalizeId(body.listID);
      if (listID && !workspace.lists.some((list) => list.id === listID)) {
        throw localBadRequest("list not found", "listID");
      }
      todo.listID = listID;
    }
    todo.updatedAt = nowApiDateTime();
  });

  return { message: "Todo updated" };
}

export function deleteTodo(body: Record<string, unknown>) {
  const id = normalizeId(body.id);
  if (!id) throw localBadRequest("todo id is required", "id");

  const removed = updateWorkspace((workspace) => {
    const index = workspace.todos.findIndex((todo) => todo.id === id);
    if (index < 0) return false;
    workspace.todos.splice(index, 1);
    workspace.todoInstances = workspace.todoInstances.filter(
      (instance) => instance.todoId !== id,
    );
    workspace.taskSteps = workspace.taskSteps.filter((step) => step.todoID !== id);
    return true;
  });

  return { message: removed ? "todo deleted" : "todo already deleted" };
}

export function completeTodo(body: Record<string, unknown>) {
  const id = normalizeId(body.id);
  if (!id) throw localBadRequest("todo id is required", "id");
  const instanceDate = parseInstanceDate(body.instanceDate);

  updateWorkspace((workspace) => {
    const todo = findTodo(workspace, id);
    if (!todo) return;

    const now = nowApiDateTime();
    const todoDue = parseDueMinute(todo.due) ?? todo.due;
    const list = todo.listID
      ? workspace.lists.find((entry) => entry.id === todo.listID)
      : undefined;

    const alreadyCleared = workspace.completedTodos.some(
      (entry) =>
        entry.originalTodoID === id &&
        (instanceDate
          ? sameInstant(entry.instanceDate, instanceDate)
          : entry.instanceDate == null),
    );

    if (!alreadyCleared) {
      const steps = workspace.taskSteps
        .filter((step) => step.todoID === id)
        .sort((a, b) => a.position - b.position);
      workspace.completedTodos.push({
        id: newLocalId(),
        originalTodoID: id,
        title: todo.title,
        description: todo.description,
        priority: todo.priority,
        due: todoDue,
        completedAt: now,
        // Minute granularity: clearing it within the due minute counts as on time.
        completedOnTime: (epochMs(parseDueMinute(now)) ?? 0) <= (epochMs(todoDue) ?? 0),
        daysToComplete: wholeDaysBetween(todo.createdAt, now),
        rrule: todo.rrule,
        instanceDate,
        listID: todo.listID,
        listName: list?.name ?? null,
        listColor: list?.color ?? null,
        steps: steps.length > 0 ? steps.map((step) => ({ ...step })) : null,
      });
    }

    if (todo.rrule == null) {
      todo.completed = true;
      todo.updatedAt = now;
    } else if (instanceDate) {
      const existing = workspace.todoInstances.find(
        (instance) =>
          instance.todoId === id && sameInstant(instance.instanceDate, instanceDate),
      );
      if (existing) {
        existing.completedAt = now;
      } else {
        workspace.todoInstances.push({
          id: newLocalId(),
          todoId: id,
          recurId: instanceDate,
          instanceDate,
          overriddenTitle: null,
          overriddenDescription: null,
          overriddenPriority: null,
          overriddenDue: null,
          completedAt: now,
        });
      }
    }
  });

  return { message: "todo completed" };
}

export function uncompleteTodo(body: Record<string, unknown>) {
  const id = normalizeId(body.id);
  if (!id) throw localBadRequest("todo id is required", "id");
  const instanceDate = parseInstanceDate(body.instanceDate);

  updateWorkspace((workspace) => {
    const todo = findTodo(workspace, id);
    if (!todo) return;

    if (instanceDate) {
      const instance = workspace.todoInstances.find(
        (entry) => entry.todoId === id && sameInstant(entry.instanceDate, instanceDate),
      );
      if (instance) instance.completedAt = null;
    } else {
      todo.completed = false;
      todo.updatedAt = nowApiDateTime();
    }

    workspace.completedTodos = workspace.completedTodos.filter(
      (entry) =>
        !(
          entry.originalTodoID === id &&
          (instanceDate
            ? sameInstant(entry.instanceDate, instanceDate)
            : entry.instanceDate == null)
        ),
    );
  });

  return { message: "todo uncompleted" };
}

export function prioritizeTodo(body: Record<string, unknown>) {
  const id = normalizeId(body.id);
  if (!id) throw localBadRequest("todo id is required", "id");
  const priority = requirePriority(body.priority);

  updateWorkspace((workspace) => {
    const todo = findTodo(workspace, id);
    if (!todo) return;
    todo.priority = priority;
    todo.updatedAt = nowApiDateTime();
  });

  return { message: "priority updated" };
}

export function reorderTodo(body: Record<string, unknown>) {
  const id = normalizeId(body.id);
  if (!id) throw localBadRequest("todo id is required", "id");
  const order = Number(body.order);
  if (!Number.isFinite(order)) throw localBadRequest("order is required", "order");

  updateWorkspace((workspace) => {
    const todo = findTodo(workspace, id);
    if (!todo) return;
    todo.order = order;
    todo.updatedAt = nowApiDateTime();
  });

  return { message: "order updated" };
}

export function patchTodoInstance(body: Record<string, unknown>) {
  const todoId = normalizeId(body.todoId);
  if (!todoId) throw localBadRequest("todo id is required", "todoId");
  const instanceDate = parseInstanceDate(body.instanceDate);
  if (!instanceDate) {
    throw localBadRequest("instanceDate must be a valid ISO-8601 datetime", "instanceDate");
  }

  updateWorkspace((workspace) => {
    if (!findTodo(workspace, todoId)) return;

    const overriddenDue =
      body.due == null ? undefined : parseDueMinute(String(body.due));
    if (body.due != null && !overriddenDue) {
      throw localBadRequest("due must be a valid ISO-8601 datetime", "due");
    }

    const existing = workspace.todoInstances.find(
      (instance) =>
        instance.todoId === todoId && sameInstant(instance.instanceDate, instanceDate),
    );
    const target = existing ?? {
      id: newLocalId(),
      todoId,
      recurId: instanceDate,
      instanceDate,
      overriddenTitle: null,
      overriddenDescription: null,
      overriddenPriority: null,
      overriddenDue: null,
      completedAt: null,
    };

    if (typeof body.title === "string") target.overriddenTitle = body.title;
    if (typeof body.description === "string") {
      target.overriddenDescription = body.description;
    }
    if (body.priority != null) target.overriddenPriority = requirePriority(body.priority);
    if (overriddenDue) target.overriddenDue = overriddenDue;

    if (!existing) workspace.todoInstances.push(target);
  });

  return { message: "instance updated" };
}

export function deleteTodoInstance(body: Record<string, unknown>) {
  const todoId = normalizeId(body.todoId);
  if (!todoId) throw localBadRequest("todo id is required", "todoId");
  const instanceDate = parseInstanceDate(body.instanceDate);
  if (!instanceDate) {
    throw localBadRequest("instanceDate must be a valid ISO-8601 datetime", "instanceDate");
  }

  updateWorkspace((workspace) => {
    const todo = findTodo(workspace, todoId);
    if (!todo) return;
    workspace.todoInstances = workspace.todoInstances.filter(
      (instance) =>
        !(instance.todoId === todoId && sameInstant(instance.instanceDate, instanceDate)),
    );
    if (!todo.exdates.some((exdate) => sameInstant(exdate, instanceDate))) {
      todo.exdates.push(instanceDate);
    }
  });

  return { message: "instance deleted" };
}

/** Lets a stale todo float: the todo row is consumed and a floater takes its place. */
export function demoteTodo(todoId: string) {
  const id = normalizeId(todoId);
  if (!id) throw localBadRequest("todo id is required", "id");

  const floater = updateWorkspace((workspace) => {
    const todo = findTodo(workspace, id);
    if (!todo) throw localNotFound("todo not found");
    if (todo.rrule != null) {
      throw localBadRequest("recurring tasks cannot be demoted to floaters", "id");
    }

    const now = nowApiDateTime();
    const row = {
      id: newLocalId(),
      title: todo.title,
      description: todo.description,
      pinned: todo.pinned,
      priority: todo.priority,
      completed: false,
      order: 0,
      // Todo lists and floater lists are separate types; membership stays behind.
      listID: null,
      createdAt: todo.createdAt,
      updatedAt: now,
    };
    workspace.floaters.push(row);
    workspace.todos = workspace.todos.filter((entry) => entry.id !== id);
    workspace.taskSteps = workspace.taskSteps.filter((step) => step.todoID !== id);
    return row;
  });

  return {
    message: "todo demoted",
    floater: { ...floater, userID: LOCAL_USER_ID },
  };
}

function parseInstanceDate(value: unknown): string | null {
  if (value == null || value === "") return null;
  if (typeof value === "number") return parseDueMinute(toApiDateTime(new Date(value)));
  const parsed = parseDueMinute(String(value));
  if (!parsed) {
    throw localBadRequest("instanceDate must be a valid ISO-8601 datetime", "instanceDate");
  }
  return parsed;
}
