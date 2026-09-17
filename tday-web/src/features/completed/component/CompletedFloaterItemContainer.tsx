import { CompletedFloaterItemType } from "@/types";
import TodoCheckbox from "@/components/ui/TodoCheckbox";
import FloaterListDot from "@/features/floaterList/component/FloaterListDot";
import { AlertTriangle, Check } from "lucide-react";
import clsx from "clsx";
import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  TASK_COMPLETION_CHECK_TO_STRIKE_MS,
  TASK_COMPLETION_REMOVING_TRANSITION,
  TASK_COMPLETION_STRIKE_TO_FADE_MS,
  TASK_COMPLETION_TOTAL_MS,
} from "@/lib/taskCompletionTiming";
import { usePrefersReducedMotion } from "@/lib/prefersReducedMotion";
import { useUnCompleteFloater } from "../query/uncomplete-completedFloater";

// The floater twin of CompletedTodoItemContainer (see ItemContainer.tsx) —
// same staged un-completing sequence and layout, plus a "list deleted" note
// for items whose list was removed (undo still works: the backend recreates
// it under its original name/color).
export const CompletedFloaterItemContainer = ({
  completedFloaterItem,
}: {
  completedFloaterItem: CompletedFloaterItemType;
}) => {
  const { title, description, listID, listName, listColor, listDeleted } =
    completedFloaterItem;
  const { t: completedDict } = useTranslation("completed");
  const { mutateUnComplete } = useUnCompleteFloater();

  const [phase, setPhase] = useState<
    "unchecked" | "unstruck" | "removing" | null
  >(null);
  const timers = useRef<number[]>([]);
  const removing = phase === "removing";
  const reduceMotion = usePrefersReducedMotion();

  useEffect(() => {
    return () => timers.current.forEach((id) => window.clearTimeout(id));
  }, []);

  const handleUncomplete = () => {
    if (phase) return;
    const removeAt = TASK_COMPLETION_CHECK_TO_STRIKE_MS + TASK_COMPLETION_STRIKE_TO_FADE_MS;
    setPhase("unchecked");
    timers.current.push(
      window.setTimeout(() => setPhase("unstruck"), TASK_COMPLETION_CHECK_TO_STRIKE_MS),
      window.setTimeout(() => setPhase("removing"), removeAt),
      window.setTimeout(
        () => mutateUnComplete(completedFloaterItem),
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
      // Both tracks: the row one is the un-tick collapse and the column one is the wrapping
      // fix — see `ItemContainer`, which owns the argument for each.
      className="relative grid max-w-full grid-cols-[minmax(0,1fr)] grid-rows-[1fr] overflow-hidden sm:overflow-visible"
    >
      <div
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
                // Wrapped and clamped to two lines rather than `truncate`d onto one — the
                // argument for both classes is in `ItemContainer`, and it is the same row.
                "select-none line-clamp-2 wrap-anywhere text-[0.98rem] font-black leading-5 text-muted-foreground transition-colors duration-emphasis",
                // Struck at rest, lifted on the beat — see CompletedTodoItemContainer.
                struck ? "line-through" : "task-unstrike",
              )}
            >
              {title}
            </p>
            {description && (
              <pre className="w-48 whitespace-pre-wrap wrap-anywhere pt-0.5 text-xs font-extrabold leading-4 text-muted-foreground sm:w-full">
                {description}
              </pre>
            )}
          </div>
        </div>

        {listName && (
          // The list mark reads with the title's first line, in the same `h-5` box the
          // restore circle gets. See `ItemContainer` for why it is the list's glyph rather
          // than a dot, and why the name is capped at every width.
          //
          // `FloaterListDot`, not `ListDot`: the two stores are disjoint, so a completed
          // Floater has to be resolved against the Floater lists. It resolves by id first and
          // falls through to `listName` — which this row expects to be the whole answer,
          // because a deleted list is the one case the backend nulls a completed Floater's
          // `listID`, and that is exactly the row the `AlertTriangle` below is warning about.
          <div className="flex h-5 shrink-0 items-center gap-1.5 pr-1">
            {/* Mobile: the glyph alone. Desktop: the glyph + list name pill. */}
            <FloaterListDot
              id={listID}
              name={listName}
              color={listColor}
              className="h-4 w-4 sm:hidden"
            />
            <span className="hidden items-center gap-1 rounded-full border border-border/70 bg-muted/70 px-2 py-[0.2rem] text-xs font-black text-foreground/80 sm:flex">
              <FloaterListDot
                id={listID}
                name={listName}
                color={listColor}
                className="shrink-0 text-sm"
              />
              <span className="max-w-24 truncate md:max-w-52 lg:max-w-64">
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
    </div>
  );
};
