import { getPriorityFlag } from "@/lib/priority";
import type { FloaterItemType } from "@/types";

export type FloaterSectionId = "urgent" | "important" | "normal";

export type FloaterSection = {
  id: FloaterSectionId;
  labelKey: "urgent" | "important" | "normal";
  items: FloaterItemType[];
};

const sectionOrder: FloaterSectionId[] = ["urgent", "important", "normal"];

function sectionIdForPriority(priority: FloaterItemType["priority"]): FloaterSectionId {
  const flag = getPriorityFlag(priority);
  if (flag?.label === "Urgent") return "urgent";
  if (flag?.label === "Important") return "important";
  return "normal";
}

function compareFloaters(a: FloaterItemType, b: FloaterItemType) {
  if (a.pinned !== b.pinned) return a.pinned ? -1 : 1;
  const orderDiff = a.order - b.order;
  if (orderDiff !== 0) return orderDiff;
  return a.title.localeCompare(b.title, undefined, { sensitivity: "base" });
}

function priorityRank(priority: FloaterItemType["priority"]): number {
  const flag = getPriorityFlag(priority);
  if (flag?.label === "Urgent") return 0;
  if (flag?.label === "Important") return 1;
  return 2;
}

function compareByPriorityThenCreated(a: FloaterItemType, b: FloaterItemType) {
  if (a.pinned !== b.pinned) return a.pinned ? -1 : 1;
  const pDiff = priorityRank(a.priority) - priorityRank(b.priority);
  if (pDiff !== 0) return pDiff;
  // createdAt descending (newest first) for same priority
  const aTime = new Date(a.createdAt ?? 0).getTime();
  const bTime = new Date(b.createdAt ?? 0).getTime();
  if (aTime !== bTime) return bTime - aTime;
  return a.title.localeCompare(b.title, undefined, { sensitivity: "base" });
}

/** Flat list sorted by priority (urgent → important → normal), then created date. */
export function sortFloatersByPriority(floaters: FloaterItemType[]): FloaterItemType[] {
  return floaters.filter((f) => !f.completed).sort(compareByPriorityThenCreated);
}

export function buildFloaterSections(floaters: FloaterItemType[]): FloaterSection[] {
  const grouped: Record<FloaterSectionId, FloaterItemType[]> = {
    urgent: [],
    important: [],
    normal: [],
  };

  for (const floater of floaters) {
    if (floater.completed) continue;
    grouped[sectionIdForPriority(floater.priority)].push(floater);
  }

  return sectionOrder
    .map((id) => ({
      id,
      labelKey: id,
      items: grouped[id].sort(compareFloaters),
    }))
    .filter((section) => section.items.length > 0);
}
