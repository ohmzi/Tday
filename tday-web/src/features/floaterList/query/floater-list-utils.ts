import parseApiDateTime from "@/lib/date/parseApiDateTime";
import type { FloaterListItemMetaType } from "@/types";

type FloaterListApiItem = Omit<
  FloaterListItemMetaType,
  "createdAt" | "updatedAt"
> & {
  createdAt?: string | Date | null;
  updatedAt?: string | Date | null;
};

function parseOptionalDate(value: string | Date | null | undefined) {
  if (!value) return null;
  if (value instanceof Date) return value;
  return parseApiDateTime(value);
}

export function normalizeFloaterList(
  list: FloaterListApiItem,
): FloaterListItemMetaType {
  return {
    ...list,
    color: list.color,
    iconKey: list.iconKey ?? null,
    todoCount: list.todoCount ?? 0,
    userID: list.userID ?? null,
    createdAt: parseOptionalDate(list.createdAt),
    updatedAt: parseOptionalDate(list.updatedAt),
  };
}

export function normalizeFloaterLists(lists: FloaterListApiItem[] = []) {
  return lists.map(normalizeFloaterList);
}
