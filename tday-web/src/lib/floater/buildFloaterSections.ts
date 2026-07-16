import { getPriorityFlag } from "@/lib/priority";
import type { FloaterItemType } from "@/types";
import { compareFloaters, priorityRank, type TaskSortKey } from "@/lib/taskSort";
import { floaterUpdatedEpochMs } from "@/lib/floaterResting";

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

const floaterSortKey = (floater: FloaterItemType): TaskSortKey => ({
  id: floater.id,
  pinned: floater.pinned,
  dueEpochMs: null, // floaters are undated
  priorityRank: priorityRank(floater.priority),
  updatedAtEpochMs: floaterUpdatedEpochMs(floater),
});

// The FIXED floater order (see src/lib/taskSort.ts / the shared Kotlin
// TaskSortEngine): pinned first, priority High→Low, modified desc, id.
const compareFloaterItems = (a: FloaterItemType, b: FloaterItemType): number =>
  compareFloaters(floaterSortKey(a), floaterSortKey(b));

/** Flat list in the fixed floater order (pinned, priority, modified desc, id). */
export function sortFloatersByPriority(floaters: FloaterItemType[]): FloaterItemType[] {
  return floaters.filter((f) => !f.completed).sort(compareFloaterItems);
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
      items: grouped[id].sort(compareFloaterItems),
    }))
    .filter((section) => section.items.length > 0);
}
