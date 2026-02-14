import { CompletedTodoItemType } from "@/types";
import React from "react";
import { CompletedTodoItemContainer } from "./ItemContainer";
import LineSeparator from "@/components/ui/lineSeparator";
import { useLocale } from "next-intl";

type GroupedCompletedTodoContainerProps = {
  dateTimeString: string;
  completedTodos: CompletedTodoItemType[];
};

export default function GroupedCompletedTodoContainer({
  dateTimeString,
  completedTodos,
}: GroupedCompletedTodoContainerProps) {
  const locale = useLocale();

  const formatDate = (date: Date) => {
    return new Intl.DateTimeFormat(locale, {
      day: "2-digit",
      month: "short",
      year: "numeric",
    }).format(date);
  };

  // Build a single date label matching Today's section style
  const sectionLabel =
    dateTimeString === "Today" || dateTimeString === "Yesterday"
      ? `${dateTimeString}, ${formatDate(completedTodos[0].completedAt)}`
      : formatDate(completedTodos[0].completedAt);

  return (
    <section className="mb-10">
      {/* Section heading — identical to Today page sections */}
      <div className="mb-4 mt-10 flex items-center gap-2">
        <h3 className="select-none text-lg font-semibold tracking-tight">
          {sectionLabel}
        </h3>
        <LineSeparator className="flex-1 border-border/70" />
      </div>

      {/* Task cards */}
      <div className="space-y-2">
        {completedTodos.map((todo) => (
          <CompletedTodoItemContainer
            key={todo.id}
            completedTodoItem={todo}
          />
        ))}
      </div>
    </section>
  );
}
