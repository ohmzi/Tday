export function canonicalTodoId(id: string): string {
  const [todoId] = id.split(":");
  return todoId ?? id;
}

/**
 * True when a row is the task a focus param (`focusTask`) names. Rows from the
 * timeline, list and calendar caches can carry a composite `${id}:${ms}` key,
 * while the param is always the bare id, so both sides are canonicalised.
 */
export function isFocusedTodoRow(focusedTaskId: string | null | undefined, rowId: string): boolean {
  return Boolean(focusedTaskId) && canonicalTodoId(rowId) === focusedTaskId;
}

export function todoInstanceTimestampFromId(id: string): number | null {
  const [, instanceTimestamp] = id.split(":");
  const parsedTimestamp = Number(instanceTimestamp);

  return Number.isFinite(parsedTimestamp) ? parsedTimestamp : null;
}
