import { CompletedFloaterItemType } from "@/types";
import TodoCheckbox from "@/components/ui/TodoCheckbox";
import { AlertTriangle, Check } from "lucide-react";
import clsx from "clsx";
import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useUnCompleteFloater } from "../query/uncomplete-completedFloater";
import { listColorAccentColors } from "@/components/app/nativeScreenTheme";

// The floater twin of CompletedTodoItemContainer (see ItemContainer.tsx) —
// same staged un-completing sequence and layout, plus a "list deleted" note
// for items whose list was removed (undo still works: the backend recreates
// it under its original name/color).
export const CompletedFloaterItemContainer = ({
  completedFloaterItem,
}: {
  completedFloaterItem: CompletedFloaterItemType;
}) => {
  const { title, description, listName, listColor, listDeleted } =
    completedFloaterItem;
  const { t: completedDict } = useTranslation("completed");
  const { mutateUnComplete } = useUnCompleteFloater();

  const [phase, setPhase] = useState<
    "unchecked" | "unstruck" | "removing" | null
  >(null);
  const timers = useRef<number[]>([]);

  useEffect(() => {
    return () => timers.current.forEach((id) => window.clearTimeout(id));
  }, []);

  const handleUncomplete = () => {
    if (phase) return;
    setPhase("unchecked");
    timers.current.push(
      window.setTimeout(() => setPhase("unstruck"), 280),
      window.setTimeout(() => setPhase("removing"), 620),
      window.setTimeout(() => mutateUnComplete(completedFloaterItem), 960),
    );
  };

  const struck = phase === null || phase === "unchecked";
  const dotColor = listColor
    ? listColorAccentColors[listColor] ?? "currentColor"
    : "currentColor";

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
        <div className="flex shrink-0 items-center gap-1.5 pr-1">
          {/* Mobile: colored dot only. Desktop: dot + list name pill. */}
          <span
            className="inline-block h-3 w-3 shrink-0 rounded-full sm:hidden"
            style={{ backgroundColor: dotColor }}
          />
          <span className="hidden items-center gap-1 rounded-full border border-border/70 bg-muted/70 px-2 py-[0.2rem] text-xs font-black text-foreground/80 sm:flex">
            <span
              className="inline-block h-2.5 w-2.5 shrink-0 rounded-full"
              style={{ backgroundColor: dotColor }}
            />
            <span className="max-w-24 truncate md:max-w-52 lg:max-w-none">
              {listName}
            </span>
          </span>
          {listDeleted && (
            // Icon, not text, so it survives at every width (the pill next to
            // it is desktop-only) — warns before Undo that this will recreate
            // the list rather than only surfacing it in the toast afterward.
            // `role="img"` + `aria-label` land straight on the <svg> (Lucide
            // spreads unrecognized props onto it), giving it an accessible
            // name without relying on a native title tooltip alone.
            <AlertTriangle
              role="img"
              aria-label={completedDict("listDeletedSuffix")}
              className="h-3.5 w-3.5 shrink-0 text-muted-foreground/70"
            />
          )}
        </div>
      )}
    </div>
  );
};
