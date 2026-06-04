import parseApiDateTime from "@/lib/date/parseApiDateTime";
import type { FloaterItemType } from "@/types";

type FloaterApiItem = Omit<FloaterItemType, "createdAt" | "updatedAt" | "order"> & {
  createdAt?: string | Date | null;
  updatedAt?: string | Date | null;
  order?: number | null;
  priority?: string | null;
};

function parseOptionalDate(value: string | Date | null | undefined) {
  if (!value) return null;
  if (value instanceof Date) return value;
  return parseApiDateTime(value);
}

function normalizePriority(value: string | null | undefined): FloaterItemType["priority"] {
  if (value === "High" || value === "Medium" || value === "Low") return value;
  return "Low";
}

export function normalizeFloater(floater: FloaterApiItem): FloaterItemType {
  return {
    ...floater,
    description: floater.description ?? null,
    pinned: floater.pinned ?? false,
    completed: floater.completed ?? false,
    priority: normalizePriority(floater.priority),
    order: floater.order ?? Number.MAX_SAFE_INTEGER,
    listID: floater.listID ?? null,
    createdAt: parseOptionalDate(floater.createdAt),
    updatedAt: parseOptionalDate(floater.updatedAt),
  };
}

export function normalizeFloaters(floaters: FloaterApiItem[] = []) {
  return floaters.map(normalizeFloater);
}

export function floaterListQueryKey(listID?: string | null) {
  return listID ? ["floaterList", listID] : ["floaterList"];
}
