import React from "react";
import clsx from "clsx";
import { AlarmClock } from "lucide-react";
import { useTranslation } from "react-i18next";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useEditTodo } from "@/features/todayTodos/query/update-todo";
import { quickDeferOptions, type QuickDeferKey } from "@/lib/quickDefer";
import type { TodoItemType } from "@/types";

const LABEL_KEYS: Record<QuickDeferKey, string> = {
  laterToday: "quickDeferLaterToday",
  tonight: "quickDeferTonight",
  tomorrow: "quickDeferTomorrow",
  nextWeek: "quickDeferNextWeek",
};

const suppressDragActivation = (event: React.SyntheticEvent) => {
  event.stopPropagation();
};

/**
 * Quick Defer: one tap moves a task to Later today / Tonight / Tomorrow /
 * Next week — pure sugar over the same PATCH the edit sheet produces.
 * Callers hide it for recurring todos (their occurrences defer via the
 * per-instance edit flow instead).
 */
export function DeferTodoMenu({
  todo,
  className,
}: {
  todo: TodoItemType;
  className?: string;
}) {
  const { t: appDict } = useTranslation("app");
  const { editTodoMutateFn } = useEditTodo();

  const defer = (due: Date) =>
    editTodoMutateFn({
      ...todo,
      due,
      // The old due seeds the checksum, so patchTodo reports dateChanged.
      dateRangeChecksum: todo.due.toISOString(),
      rruleChecksum: todo.rrule ?? null,
    });

  return (
    <DropdownMenu modal={false}>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          aria-label={appDict("quickDefer")}
          title={appDict("quickDefer")}
          onPointerDown={suppressDragActivation}
          onMouseDown={suppressDragActivation}
          onTouchStart={suppressDragActivation}
          onClick={(event) => event.stopPropagation()}
          className={clsx(
            "rounded-full bg-card/80 p-1.5 text-muted-foreground backdrop-blur transition-colors hover:bg-muted hover:text-foreground",
            className,
          )}
        >
          <AlarmClock className="h-4 w-4" strokeWidth={1.8} />
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent className="text-foreground">
        {quickDeferOptions().map((option) => (
          <DropdownMenuItem key={option.key} onClick={() => defer(option.due)}>
            {appDict(LABEL_KEYS[option.key])}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
