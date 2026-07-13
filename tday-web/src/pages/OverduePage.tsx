import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Sunrise } from "lucide-react";
import AllTasksTimelineContainer from "@/features/todayTodos/component/AllTasksTimelineContainer";
import { MorningSweepSheet } from "@/features/sweep/MorningSweepSheet";
import { useTodoTimeline } from "@/features/todayTodos/query/get-todo-timeline";

export default function OverduePage() {
  const { t: appDict } = useTranslation("app");
  const { todos } = useTodoTimeline();
  const [sweepOpen, setSweepOpen] = useState(false);

  // Sweep triages carried-over one-off tasks; recurring occurrences
  // reschedule via the per-instance edit flow, so they stay in the list.
  const sweepTodos = useMemo(
    () =>
      todos.filter(
        (todo) => !todo.completed && !todo.rrule && todo.due < new Date(),
      ),
    [todos],
  );

  return (
    <div className="select-none bg-inherit">
      <AllTasksTimelineContainer scope="overdue" />
      {sweepTodos.length > 0 && (
        <button
          type="button"
          onClick={() => setSweepOpen(true)}
          className="fixed bottom-24 right-5 z-20 inline-flex items-center gap-2 rounded-full bg-accent px-4 py-2.5 text-sm font-bold text-accent-foreground shadow-lg transition-opacity hover:opacity-90 sm:bottom-8"
        >
          <Sunrise className="h-4 w-4" aria-hidden="true" />
          {appDict("sweep")}
        </button>
      )}
      <MorningSweepSheet
        open={sweepOpen}
        onOpenChange={setSweepOpen}
        todos={sweepTodos}
      />
    </div>
  );
}
