export function canonicalTodoId(id: string): string {
  const [todoId] = id.split(":");
  return todoId ?? id;
}

export function todoInstanceTimestampFromId(id: string): number | null {
  const [, instanceTimestamp] = id.split(":");
  const parsedTimestamp = Number(instanceTimestamp);

  return Number.isFinite(parsedTimestamp) ? parsedTimestamp : null;
}
