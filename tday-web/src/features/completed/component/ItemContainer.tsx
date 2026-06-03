import { CompletedTodoItemType } from "@/types";
import TodoCheckbox from "@/components/ui/TodoCheckbox";
import { X } from "lucide-react";
import { useUnCompleteTodo } from "../query/uncomplete-completedTodo";

export const CompletedTodoItemContainer = ({
  completedTodoItem,
}: {
  completedTodoItem: CompletedTodoItemType;
}) => {
  const { title, description, listName, listColor } = completedTodoItem;
  const { mutateUnComplete } = useUnCompleteTodo();

  return (
    <div className="group relative flex max-w-full items-center justify-between gap-3 border-b border-border/60 px-1 py-2.5 sm:rounded-lg sm:transition-colors sm:duration-150 sm:hover:bg-muted/40">
      <div className="flex min-w-0 items-center gap-3">
        <div className="shrink-0">
          <TodoCheckbox
            icon={X}
            onChange={() => mutateUnComplete(completedTodoItem)}
            complete={true}
            checked={true}
          />
        </div>

        <div className="min-w-0">
          <p className="select-none truncate text-[0.98rem] font-black leading-5 text-muted-foreground line-through">
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
