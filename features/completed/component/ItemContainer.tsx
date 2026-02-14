import { CompletedTodoItemType } from "@/types";
import TodoCheckbox from "@/components/ui/TodoCheckbox";
import { X } from "lucide-react";
import { useUnCompleteTodo } from "../query/uncomplete-completedTodo";

export const CompletedTodoItemContainer = ({
  completedTodoItem,
}: {
  completedTodoItem: CompletedTodoItemType;
}) => {
  const { title, description, projectName, projectColour } = completedTodoItem;
  const { mutateUnComplete } = useUnCompleteTodo();

  return (
    <div className="group relative flex max-w-full items-start justify-between gap-3 rounded-2xl border border-border/65 bg-card/95 px-3 py-3 shadow-[0_1px_2px_hsl(var(--shadow)/0.08)] transition-all duration-200 hover:border-border hover:shadow-[0_10px_24px_hsl(var(--shadow)/0.11)]">
      <div className="flex items-start gap-3">
        <div className="pt-0.5">
          <TodoCheckbox
            icon={X}
            onChange={() => mutateUnComplete(completedTodoItem)}
            complete={true}
            checked={true}
            priority={completedTodoItem.priority}
          />
        </div>

        <div className="max-w-full">
          <p className="mb-2 select-none leading-none text-foreground">
            {title}
          </p>
          {description && (
            <pre className="w-48 whitespace-pre-wrap pb-2 text-xs text-muted-foreground sm:w-full sm:text-sm">
              {description}
            </pre>
          )}
          <div className="flex flex-wrap items-center justify-start gap-2 text-xs sm:text-sm">
            <p className="text-lime">Completed</p>
            {projectName && (
              <p className="flex items-center gap-1 rounded-full border border-border/70 bg-muted/70 px-2 py-[0.2rem] text-foreground/80">
                <span
                  className="inline-block h-2.5 w-2.5 shrink-0 rounded-full"
                  style={{ backgroundColor: projectColour || "currentColor" }}
                />
                <span className="max-w-14 truncate sm:max-w-24 md:max-w-52 lg:max-w-none">
                  {projectName}
                </span>
              </p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
