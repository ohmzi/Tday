import { CompletedFloaterItemType } from "@/types";
import React from "react";
import { CompletedFloaterItemContainer } from "./CompletedFloaterItemContainer";
import { useLocale } from "@/lib/navigation";

type GroupedCompletedFloaterContainerProps = {
  dateTimeString: string;
  completedFloaters: CompletedFloaterItemType[];
};

// The floater twin of GroupedContainer.tsx.
export default function GroupedCompletedFloaterContainer({
  dateTimeString,
  completedFloaters,
}: GroupedCompletedFloaterContainerProps) {
  const locale = useLocale();

  const formatDate = (date: Date) => {
    return new Intl.DateTimeFormat(locale, {
      day: "2-digit",
      month: "short",
      year: "numeric",
    }).format(date);
  };

  const sectionLabel =
    dateTimeString === "Today" || dateTimeString === "Yesterday"
      ? `${dateTimeString}, ${formatDate(completedFloaters[0].completedAt)}`
      : formatDate(completedFloaters[0].completedAt);

  return (
    <section className="mb-3">
      <div className="mb-1.5 mt-3 flex items-center gap-2">
        <h3 className="select-none text-2xl font-black tracking-tight text-muted-foreground">
          {sectionLabel}
        </h3>
      </div>

      <div className="space-y-0 border-b border-border/60 pb-1">
        {completedFloaters.map((floater) => (
          <CompletedFloaterItemContainer
            key={floater.id}
            completedFloaterItem={floater}
          />
        ))}
      </div>
    </section>
  );
}
