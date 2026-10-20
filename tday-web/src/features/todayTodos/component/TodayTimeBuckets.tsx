import {
  TODAY_BUCKETS,
  TodayBucketDndContext,
  TodayBucketDroppable,
  DraggableTodayTask,
} from "@/components/todo/dnd/TodayBucketDnd";
import type { TodoItemType } from "@/types";

type TodayBucket = {
  label: "Morning" | "Afternoon" | "Tonight";
  todos: TodoItemType[];
};

/**
 * Today's Morning/Afternoon/Tonight drop targets. Unchanged by this PR —
 * pulled out verbatim to keep the container's own complexity down. The three
 * buckets stay visible (even empty ones) as long as the day holds at least
 * one task, so they read as live drop targets alongside the others; the
 * caller only renders this at all once `todayBuckets` holds something (see
 * `AllTasksTimelineContainer`'s own note on that memo).
 */
export default function TodayTimeBuckets({
  todayBuckets,
  timeZone,
  focusedTaskId,
}: {
  todayBuckets: TodayBucket[];
  timeZone?: string;
  focusedTaskId: string | null;
}) {
  return (
    <TodayBucketDndContext timeZone={timeZone}>
      {todayBuckets.map((bucket, index) => (
        <TodayBucketDroppable
          key={bucket.label}
          bucket={bucket.label}
          targetHour={TODAY_BUCKETS.find((b) => b.label === bucket.label)?.targetHour ?? 9}
          isFirst={index === 0}
        >
          {bucket.todos.map((todo) => (
            <DraggableTodayTask
              key={todo.id}
              todo={todo}
              currentBucket={bucket.label}
              highlighted={focusedTaskId === todo.id}
            />
          ))}
        </TodayBucketDroppable>
      ))}
    </TodayBucketDndContext>
  );
}
