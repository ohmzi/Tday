import { CompletedTodoItemType } from "@/types";
import TodoCheckbox from "@/components/ui/TodoCheckbox";
import { Check } from "lucide-react";
import clsx from "clsx";
import { useEffect, useRef, useState } from "react";
import { useUnCompleteTodo } from "../query/uncomplete-completedTodo";

export const CompletedTodoItemContainer = ({
  completedTodoItem,
}: {
  completedTodoItem: CompletedTodoItemType;
}) => {
  const { title, description, listName, listColor } = completedTodoItem;
  const { mutateUnComplete } = useUnCompleteTodo();

  // Staged "un-completing" sequence, the reverse of completing, so each step is
  // visible with a small gap:
  //   unchecked (empty circle) → unstruck (remove line-through) → removing (fade) → remove.
  const [phase, setPhase] = useState<
    "unchecked" | "unstruck" | "removing" | null
  >(null);
  const timers = useRef<number[]>([]);

  useEffect(() => {
    return () => timers.current.forEach((id) => window.clearTimeout(id));
  }, []);

  const handleUncomplete = () => {
    if (phase) return;
    setPhase("unchecked"); // 1. empty the circle + pop
    timers.current.push(
      window.setTimeout(() => setPhase("unstruck"), 280), // 2. remove the strike-through
      window.setTimeout(() => setPhase("removing"), 620), // 3. start fading
      window.setTimeout(() => mutateUnComplete(completedTodoItem), 960), // 4. remove
    );
  };

  const struck = phase === null || phase === "unchecked";

  return (
    <div
      style={
        phase === "removing"
          ? { opacity: 0, transition: "opacity 300ms ease" }
          : undefined
      }
      className="group relative flex max-w-full items-center justify-between gap-3 px-1 py-2.5 sm:rounded-lg sm:transition-colors sm:duration-150 sm:hover:bg-muted/40"
    >
      <div className="flex min-w-0 items-center gap-3">
        <div className="shrink-0">
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
              "select-none truncate text-[0.98rem] font-black leading-5 text-muted-foreground transition-colors duration-300",
              struck && "line-through",
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
        <div className="flex shrink-0 items-center gap-2 pr-1">
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
  );
};
