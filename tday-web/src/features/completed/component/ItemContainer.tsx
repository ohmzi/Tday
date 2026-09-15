import { CompletedTodoItemType } from "@/types";
import TodoCheckbox from "@/components/ui/TodoCheckbox";
import { Check } from "lucide-react";
import clsx from "clsx";
import { useEffect, useRef, useState } from "react";
import {
  TASK_COMPLETION_CHECK_TO_STRIKE_MS,
  TASK_COMPLETION_REMOVING_TRANSITION,
  TASK_COMPLETION_STRIKE_TO_FADE_MS,
  TASK_COMPLETION_TOTAL_MS,
} from "@/lib/taskCompletionTiming";
import { usePrefersReducedMotion } from "@/lib/prefersReducedMotion";
import { useUnCompleteTodo } from "../query/uncomplete-completedTodo";

export const CompletedTodoItemContainer = ({
  completedTodoItem,
}: {
  completedTodoItem: CompletedTodoItemType;
}) => {
  const { title, description, listName, listColor } = completedTodoItem;
  const { mutateUnComplete } = useUnCompleteTodo();

  // Un-completing is the check-off played backwards, and it is now played on the check-off's
  // own beats rather than on a third of its own:
  //   unchecked (empty circle) → unstruck (the rule lifts) → removing (ink out, box shut) → gone.
  // The constants are the task rows' — one task leaving one list is one motion, and which
  // direction it went is not a reason for it to take a different length of time.
  const [phase, setPhase] = useState<
    "unchecked" | "unstruck" | "removing" | null
  >(null);
  const timers = useRef<number[]>([]);
  const removing = phase === "removing";
  // Subscribed rather than read once: this decides what the row renders, so it has to follow a
  // preference that flips mid-session.
  const reduceMotion = usePrefersReducedMotion();

  useEffect(() => {
    return () => timers.current.forEach((id) => window.clearTimeout(id));
  }, []);

  const handleUncomplete = () => {
    if (phase) return;
    const removeAt = TASK_COMPLETION_CHECK_TO_STRIKE_MS + TASK_COMPLETION_STRIKE_TO_FADE_MS;
    setPhase("unchecked"); // 1. empty the circle + pop
    timers.current.push(
      window.setTimeout(() => setPhase("unstruck"), TASK_COMPLETION_CHECK_TO_STRIKE_MS), // 2.
      window.setTimeout(() => setPhase("removing"), removeAt), // 3. the ink leaves
      // 4. gone. The last leg waits for the box to shut, and with reduce-motion on there is no
      // box shutting — the cut the task rows make, argued in `taskCompletionTiming`.
      window.setTimeout(
        () => mutateUnComplete(completedTodoItem),
        reduceMotion ? removeAt : TASK_COMPLETION_TOTAL_MS,
      ),
    );
  };

  const struck = phase === null || phase === "unchecked";

  return (
    <div
      style={
        removing
          ? {
              opacity: 0,
              gridTemplateRows: "0fr",
              transition: reduceMotion ? undefined : TASK_COMPLETION_REMOVING_TRANSITION,
            }
          : undefined
      }
      // The 1fr track is the task rows' collapse (TodoItemCard), which argues the trick where
      // it lives: a height cannot be animated away from `auto`, so the box shuts by closing a
      // grid row. Without it this row faded out and left its gap for the list to jump through.
      className="relative grid max-w-full grid-rows-[1fr] overflow-hidden sm:overflow-visible"
    >
      <div
        // Lets the grid item shrink past its own content while the track closes.
        style={removing ? { overflow: "hidden", minHeight: 0 } : undefined}
        // `items-start`: the text column is always the tallest thing in this row, so
        // it is what sets the row's height either way — the only child this moves is
        // the trailing list mark, which is exactly the one that was floating.
        className="group flex max-w-full items-start justify-between gap-3 px-1 py-2.5 sm:rounded-lg sm:transition-colors sm:duration-quick sm:hover:bg-muted/40"
      >
        {/* Stacked from the TOP with the control in the title's own line box
            (`leading-5` = 20 px). The title truncates here, but the notes under it do
            not: a completed task with two lines of notes had its restore circle sitting
            beside the notes rather than beside the task. */}
        <div className="flex min-w-0 items-start gap-3">
          <div className="flex h-5 shrink-0 items-center">
            <TodoCheckbox
              icon={Check}
              onChange={handleUncomplete}
              complete={true}
              checked={phase === null}
            />
          </div>

          <div className="min-w-0">
            <p
              className={clsx(
                "select-none truncate text-[0.98rem] font-black leading-5 text-muted-foreground transition-colors duration-emphasis",
                // Bare `line-through` while the row is struck, because this row is BORN struck —
                // `.task-strike` is a reveal and would fade a rule in on every row in the list at
                // mount. `.task-unstrike` is the beat the user actually asked for.
                struck ? "line-through" : "task-unstrike",
              )}
            >
              {title}
            </p>
            {description && (
              <pre className="w-48 whitespace-pre-wrap pt-0.5 text-xs font-extrabold leading-4 text-muted-foreground sm:w-full">
                {description}
              </pre>
            )}
          </div>
        </div>

        {listName && (
          // The list mark is an annotation on the task, so it reads with the title's
          // first line — the `h-5` box is the same one the restore circle gets at the
          // other end of the row.
          <div className="flex h-5 shrink-0 items-center gap-2 pr-1">
            {/* Mobile: colored dot only. Desktop: dot + list name pill. */}
            <span
              className="inline-block h-3 w-3 shrink-0 rounded-full sm:hidden"
              style={{ backgroundColor: listColor || "currentColor" }}
            />
            <span className="hidden items-center gap-1 rounded-full border border-border/70 bg-muted/70 px-2 py-[0.2rem] text-xs font-black text-foreground/80 sm:flex">
              <span
                className="inline-block h-2.5 w-2.5 shrink-0 rounded-full"
                style={{ backgroundColor: listColor || "currentColor" }}
              />
              <span className="max-w-24 truncate md:max-w-52 lg:max-w-none">
                {listName}
              </span>
            </span>
          </div>
        )}
      </div>
    </div>
  );
};
