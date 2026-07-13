import React, { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import { AlarmClock, ArrowRight, CalendarDays, Flag, Trash, Waves } from "lucide-react";
import {
  Drawer,
  DrawerContent,
  DrawerHeader,
  DrawerTitle,
} from "@/components/ui/drawer";
import { useEditTodo } from "@/features/todayTodos/query/update-todo";
import { useDeleteTodo } from "@/features/todayTodos/query/delete-todo";
import { useDemoteTodo } from "@/features/todayTodos/query/demote-todo";
import { useUndoableDelete } from "@/hooks/use-undoable-delete";
import { patchTodo } from "@/lib/todo/patch-todo";
import { getPriorityFlag } from "@/lib/priority";
import { useLocale } from "@/lib/navigation";
import type { TodoItemType } from "@/types";

/** The old due's time-of-day carried onto [targetDay]. */
function movedDuePreservingTime(due: Date, dayOffset: number): Date {
  const target = new Date();
  target.setDate(target.getDate() + dayOffset);
  target.setHours(due.getHours(), due.getMinutes(), 0, 0);
  return target;
}

function duePreservingTimeOn(due: Date, day: Date): Date {
  const target = new Date(day);
  target.setHours(due.getHours(), due.getMinutes(), 0, 0);
  return target;
}

/**
 * Morning Sweep: guided one-card-at-a-time triage of carried-over tasks.
 * Each decision is the same mutation the edit sheet would produce; "Sweep all
 * to today" batches the rest behind one undoable toast. Recurring todos are
 * excluded — their occurrences reschedule via the per-instance edit flow.
 */
export function MorningSweepSheet({
  open,
  onOpenChange,
  todos,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  todos: TodoItemType[];
}) {
  const { t: appDict } = useTranslation("app");
  const locale = useLocale();
  const queryClient = useQueryClient();
  const { editTodoMutateFn } = useEditTodo();
  const { deleteMutateFn } = useDeleteTodo();
  const { demoteMutateFn } = useDemoteTodo();
  const showUndoableDelete = useUndoableDelete();

  const [skippedIds, setSkippedIds] = useState<Set<string>>(new Set());
  const [pickingDate, setPickingDate] = useState(false);

  const remaining = useMemo(
    () => todos.filter((todo) => !skippedIds.has(todo.id)),
    [todos, skippedIds],
  );
  const card = remaining[0] ?? null;

  const move = (todo: TodoItemType, due: Date) => {
    setPickingDate(false);
    editTodoMutateFn({
      ...todo,
      due,
      dateRangeChecksum: todo.due.toISOString(),
      rruleChecksum: todo.rrule ?? null,
    });
  };

  const sweepAllToToday = () => {
    const swept = remaining;
    if (swept.length === 0) return;
    onOpenChange(false);
    // Delayed commit: nothing changes until the toast survives its undo window.
    showUndoableDelete({
      message: appDict("sweptToast", { count: swept.length }),
      commit: () => {
        void Promise.allSettled(
          swept.map((todo) =>
            patchTodo({
              ...todo,
              due: movedDuePreservingTime(todo.due, 0),
              dateRangeChecksum: todo.due.toISOString(),
              rruleChecksum: todo.rrule ?? null,
            }),
          ),
        ).then(() => {
          queryClient.invalidateQueries({ queryKey: ["todo"] });
          queryClient.invalidateQueries({ queryKey: ["todoTimeline"] });
          queryClient.invalidateQueries({ queryKey: ["calendarTodo"] });
        });
      },
      undo: () => {},
    });
  };

  const actionClass =
    "flex w-full items-center gap-3 rounded-xl border border-border bg-card px-4 py-3 text-left text-sm font-bold text-card-foreground transition-colors hover:bg-muted";

  return (
    <Drawer open={open} onOpenChange={onOpenChange}>
      <DrawerContent aria-describedby={undefined}>
        <DrawerHeader>
          <DrawerTitle>{appDict("sweepTitle")}</DrawerTitle>
        </DrawerHeader>

        {card ? (
          <div className="mx-auto flex w-full max-w-md flex-col gap-4 px-4 pb-8">
            {/* The card under triage */}
            <div className="rounded-2xl border border-border bg-card px-5 py-4">
              <div className="flex items-center gap-2">
                <p className="min-w-0 flex-1 text-base font-black text-card-foreground">
                  {card.title}
                </p>
                {getPriorityFlag(card.priority) && (
                  <Flag className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
                )}
              </div>
              <p className="mt-1 text-xs font-bold text-muted-foreground">
                {new Intl.DateTimeFormat(locale, {
                  weekday: "short",
                  day: "numeric",
                  month: "short",
                }).format(card.due)}
                {remaining.length > 1 ? ` · ${remaining.length}` : ""}
              </p>
            </div>

            <div className="flex flex-col gap-2">
              <button type="button" className={actionClass} onClick={() => move(card, movedDuePreservingTime(card.due, 0))}>
                <AlarmClock className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
                {appDict("sweepToday")}
              </button>
              <button type="button" className={actionClass} onClick={() => move(card, movedDuePreservingTime(card.due, 1))}>
                <ArrowRight className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
                {appDict("sweepTomorrow")}
              </button>
              {pickingDate ? (
                <input
                  type="date"
                  autoFocus
                  className="w-full rounded-xl border border-border bg-card px-4 py-3 text-sm font-bold text-card-foreground"
                  onChange={(event) => {
                    const picked = event.target.valueAsDate;
                    if (picked) move(card, duePreservingTimeOn(card.due, picked));
                  }}
                />
              ) : (
                <button type="button" className={actionClass} onClick={() => setPickingDate(true)}>
                  <CalendarDays className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
                  {appDict("sweepPickDate")}
                </button>
              )}
              <button type="button" className={actionClass} onClick={() => { setPickingDate(false); demoteMutateFn(card); }}>
                <Waves className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
                {appDict("sweepFloat")}
              </button>
              <button type="button" className={actionClass} onClick={() => { setPickingDate(false); deleteMutateFn(card); }}>
                <Trash className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
                {appDict("sweepLetGo")}
              </button>
            </div>

            <div className="flex items-center justify-between gap-2">
              <button
                type="button"
                className="rounded-lg px-3 py-2 text-sm font-bold text-muted-foreground hover:text-foreground"
                onClick={() => {
                  setPickingDate(false);
                  setSkippedIds((prev) => new Set(prev).add(card.id));
                }}
              >
                {appDict("sweepSkip")}
              </button>
              <button
                type="button"
                className="rounded-lg bg-accent px-4 py-2 text-sm font-bold text-accent-foreground hover:opacity-90"
                onClick={sweepAllToToday}
              >
                {appDict("sweepAllToToday")}
              </button>
            </div>
          </div>
        ) : (
          <div className="mx-auto flex w-full max-w-md flex-col items-center gap-3 px-4 pb-10 pt-2">
            <p className="text-lg font-black text-muted-foreground">{appDict("sweepDone")}</p>
          </div>
        )}
      </DrawerContent>
    </Drawer>
  );
}
