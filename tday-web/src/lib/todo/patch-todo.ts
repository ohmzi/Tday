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
  instanceDate?: Date | number | null;
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
    console.warn(parsedObj.error.errors[0]);
    return;
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
