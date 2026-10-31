export function canonicalTodoId(id: string): string {
  const [todoId] = id.split(":");
  return todoId ?? id;
}

/**
 * Resolves a focus param to the exact row id to highlight. The param names a
 * bare task id, optionally with the occurrence's ms (`focusInstance`); rows from
 * the timeline and list caches carry composite `${id}:${ms}` keys, one per
 * occurrence. The occurrence that matches wins; when it is not among the rows,
 * the first occurrence of the task is used. Returns null when no row matches.
 *
 * Callers highlight by exact equality against the returned id, so one row lights
 * up and only one row is scrolled to.
 */
export function resolveFocusedRowId(
  rowIds: readonly string[],
  focusTask: string | null | undefined,
  focusInstance?: string | null,
): string | null {
  if (!focusTask) return null;
  const taskId = canonicalTodoId(focusTask);
  const instance = focusInstance || String(todoInstanceTimestampFromId(focusTask) ?? "");

  let firstOccurrence: string | null = null;
  for (const rowId of rowIds) {
    if (canonicalTodoId(rowId) !== taskId) continue;
    const rowInstance = todoInstanceTimestampFromId(rowId);
    if (instance && rowInstance !== null && String(rowInstance) === instance) return rowId;
    if (firstOccurrence === null) firstOccurrence = rowId;
  }
  return firstOccurrence;
}

export function todoInstanceTimestampFromId(id: string): number | null {
  const [, instanceTimestamp] = id.split(":");
  const parsedTimestamp = Number(instanceTimestamp);

  return Number.isFinite(parsedTimestamp) ? parsedTimestamp : null;
}
