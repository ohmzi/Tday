import { api } from "@/lib/api-client";
import { todoSchema } from "@/schema";
import type { TodoItemType } from "@/types";

import { canonicalTodoId } from "./todo-id";

export interface TodoItemTypeWithDateChecksum extends TodoItemType {
  dateRangeChecksum: string;
  rruleChecksum: string | null;
}

export interface TodoItemPatchInput extends TodoItemType {
  dateRangeChecksum?: string | null;
  rruleChecksum?: string | null;
}

type TodoPatchOptions = {
  // Date only — the backend parses instanceDate as ISO-8601, and epoch millis
  // decode into a digit string it can't parse.
  instanceDate?: Date | null;
  listID?: string | null;
};

export async function patchTodo(
  todo: TodoItemPatchInput,
  options: TodoPatchOptions = {},
) {
  if (!todo.id) {
    throw new Error("this todo is missing");
  }

  const listID = "listID" in options ? options.listID : todo.listID;
  const parsedObj = todoSchema.safeParse({
    title: todo.title,
    description: todo.description,
    priority: todo.priority,
    due: todo.due,
    rrule: todo.rrule,
    listID,
  });

  if (!parsedObj.success) {
    // Throw, never return quietly. A silent return reads as success to every
    // caller: `runBulkFanOut` counts a rejection and nothing else, so a bulk
    // move over rows this schema rejects used to report `{failed: 0}` having
    // issued zero requests, and the only trace was a console warning. A bulk
    // action must never claim to have done something it did not send.
    const issue = parsedObj.error.errors[0];
    throw new Error(`todo patch rejected: ${issue.path.join(".")} ${issue.message}`);
  }

  const dateChanged = todo.dateRangeChecksum !== todo.due.toISOString();
  const rruleChanged = todo.rruleChecksum !== todo.rrule;
  const instanceDate =
    "instanceDate" in options ? options.instanceDate : todo.instanceDate;

  await api.PATCH({
    url: "/api/todo",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      ...parsedObj.data,
      id: canonicalTodoId(todo.id),
      instanceDate,
      dateChanged,
      rruleChanged,
      listID,
    }),
  });
}
