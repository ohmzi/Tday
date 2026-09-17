import type { FloaterItemType } from "@/types";
import { compareFloaters, priorityRank, type TaskSortKey } from "@/lib/taskSort";
import { floaterUpdatedEpochMs } from "@/lib/floaterResting";

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

/**
 * The flat Anytime order (pinned, priority, modified desc, id).
 *
 * One order for both Anytime surfaces. The list screen used to bucket these
 * into "Urgent" / "Important" / "Normal" sections with a heading over each; the
 * heading restated what the row's own priority `Flag` already carries (see
 * `src/lib/priority.ts`), put a word between the reader and the tasks, and
 * named the *absence* of the thing the heading was for whenever it read
 * "Normal". It also disagreed with the Anytime root feed, which has always been
 * flat — flag a task on either screen and it lifts, the same way.
 */
export function sortFloatersByPriority(floaters: FloaterItemType[]): FloaterItemType[] {
  return floaters.filter((f) => !f.completed).sort(compareFloaterItems);
}
