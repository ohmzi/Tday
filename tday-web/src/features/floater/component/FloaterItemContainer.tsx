import { useCallback, useEffect, useRef, useState } from "react";
import clsx from "clsx";
import {
  TASK_COMPLETION_CHECK_TO_STRIKE_MS,
  TASK_COMPLETION_REMOVING_TRANSITION,
  TASK_COMPLETION_STRIKE_TO_FADE_MS,
  TASK_COMPLETION_TOTAL_MS,
} from "@/lib/taskCompletionTiming";
import { usePrefersReducedMotion } from "@/lib/prefersReducedMotion";
import { Check, Copy, Flag, SquarePen, Trash } from "lucide-react";
import TodoCheckbox from "@/components/ui/TodoCheckbox";
import { TaskActionButtons } from "@/components/ui/TaskActionButtons";
import FloaterListDot from "@/features/floaterList/component/FloaterListDot";
import { getPriorityFlag } from "@/lib/priority";
import {
  floaterRestingTier,
  floaterUpdatedEpochMs,
  isRestingFloatersEnabled,
} from "@/lib/floaterResting";
import { useFloaterListMetaData } from "@/features/floaterList/query/get-floater-list-meta";
import { useCompleteFloater } from "@/features/floater/query/complete-floater";
import { useDeleteFloater } from "@/features/floater/query/delete-floater";
import { PromoteFloaterMenu } from "@/features/floater/component/PromoteFloaterMenu";
import type { FloaterItemType } from "@/types";
import FloaterFormSheet from "./FloaterFormSheet";
import { hapticButtonTap } from "@/lib/haptics";
import { SWIPE_COPY_COLOR, SWIPE_DELETE_COLOR, SWIPE_EDIT_COLOR } from "@/lib/swipeActionColors";
import { useTranslation } from "react-i18next";
import { useToast } from "@/hooks/use-toast";
import { useSwipeRow } from "@/hooks/useSwipeRow";
import { shouldCloseSwipeRow } from "@/lib/swipeGesture";
import { buildTaskShareText } from "@/lib/listShareText";

type FloaterItemContainerProps = {
  floater: FloaterItemType;
  highlighted?: boolean;
  // True when the floater belongs to a shared list where the user is a VIEWER.
  readOnly?: boolean;
};

export default function FloaterItemContainer({
  floater,
  highlighted = false,
  readOnly = false,
}: FloaterItemContainerProps) {
  // No `useSortable` here, deliberately. Floater order is fixed (see
  // `FloaterGroup`) and drag-to-reorder is retired, so there is no
  // `DndContext` anywhere above this row — the subscription every row used to
  // open resolved against dnd-kit's default context, registered a droppable
  // nobody could ever drag onto, and re-ran its measuring work on every render
  // of every row in the list for a `transform` that was permanently null and an
  // `isDragging` that was permanently false. The scheduled row
  // (`TodoItemContainer`) keeps its own for the opposite reason: it really does
  // render inside a drag context.
  const { floaterListMetaData } = useFloaterListMetaData();
  const { completeMutateFn } = useCompleteFloater();
  const { deleteMutateFn } = useDeleteFloater();
  const { t: appDict, i18n } = useTranslation("app");
  const { toast } = useToast();
  const { title, description, completed, priority, listID } = floater;
  // "Resting floaters": dim Anytime tasks left untouched for a month+ (read-only cue).
  const resting =
    !completed &&
    isRestingFloatersEnabled() &&
    floaterRestingTier(floaterUpdatedEpochMs(floater), Date.now()) !== "active";
  const priorityFlag = getPriorityFlag(priority);
  const [displayForm, setDisplayForm] = useState(false);
  const [showHandle, setShowHandle] = useState(false);
  const [completePhase, setCompletePhase] = useState<
    "checked" | "struck" | "removing" | null
  >(null);
  const completeTimers = useRef<number[]>([]);
  const completing = completePhase !== null;
  const removing = completePhase === "removing";
  // Subscribed rather than read once, for the same reason as the scheduled row: this decides
  // what gets rendered, so it has to follow a preference that flips mid-session.
  const reduceMotion = usePrefersReducedMotion();
  // Matches the scheduled task home row (TodoItemCard) so the swipe distance and the
  // fully-revealed Edit + Copy + Delete pills sit in the same place — at 110 the pills
  // (~136px) outran the slide, leaving the priority flag on top of Edit.
  const ACTIONS_WIDTH = 210;
  const announceSwipeOpen = useCallback(() => {
    window.dispatchEvent(new CustomEvent("tday-floater-swipe-open", { detail: floater.id }));
  }, [floater.id]);
  const {
    swipeX,
    transition: swipeTransition,
    rowRef,
    closeSwipe,
    dismissSwipe,
    swipeHandlers,
  } = useSwipeRow({
    actionsWidth: ACTIONS_WIDTH,
    onOpen: announceSwipeOpen,
    disabled: readOnly,
  });

  /** Copies the floater's title/notes/priority to the clipboard as plain text. */
  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(
        buildTaskShareText({
          todo: { title, description, priority },
          lang: i18n.language,
          t: appDict,
        }),
      );
      toast({ description: appDict("taskCopied") });
    } catch {
      toast({ description: appDict("taskCopyFailed"), variant: "destructive" });
    }
  };

  const handleToggleComplete = () => {
    if (readOnly) return;
    if (completed) {
      completeMutateFn(floater);
      return;
    }
    if (completing) return;
    const removeAt = TASK_COMPLETION_CHECK_TO_STRIKE_MS + TASK_COMPLETION_STRIKE_TO_FADE_MS;
    setCompletePhase("checked");
    completeTimers.current.push(
      window.setTimeout(() => setCompletePhase("struck"), TASK_COMPLETION_CHECK_TO_STRIKE_MS),
      window.setTimeout(() => setCompletePhase("removing"), removeAt),
      // The scheduled row's staging module makes the same cut and argues it there: the last leg
      // waits for the collapse, so with reduce-motion on there is nothing left to wait for.
      // `reduceMotion` is this render's value, which is the answer at the instant a tap arms these
      // timers — the same question `prefersReducedMotion()` asks on the other row.
      window.setTimeout(
        () => completeMutateFn(floater),
        reduceMotion ? removeAt : TASK_COMPLETION_TOTAL_MS,
      ),
    );
  };

  useEffect(() => {
    const timers = completeTimers.current;
    return () => {
      timers.forEach((id) => window.clearTimeout(id));
    };
  }, []);

  // One row open at a time, claimed at the other row's axis lock. `dismissSwipe`
  // rather than `closeSwipe` because this close arrives from somewhere else and
  // must leave a row alone whose own finger is still on it; `shouldCloseSwipeRow`
  // is the predicate all three clients share. Both are argued in `useSwipeRow`.
  useEffect(() => {
    const onOpen = (event: Event) => {
      const id = (event as CustomEvent<string>).detail;
      if (shouldCloseSwipeRow(id, floater.id, swipeX !== 0)) dismissSwipe();
    };
    window.addEventListener("tday-floater-swipe-open", onOpen as EventListener);
    return () =>
      window.removeEventListener("tday-floater-swipe-open", onOpen as EventListener);
  }, [dismissSwipe, floater.id, swipeX]);

  return (
    <>
      <div
        // The node an outside tap is measured against: it wraps both the pill
        // strip and the translating foreground, which is what "outside the open
        // row" has to mean. The other two rows assign the same node inside a
        // combined ref callback because dnd-kit wants it too; this one has no
        // other claimant. See `useSwipeRow`.
        ref={(node) => {
          rowRef.current = node;
        }}
        style={
          removing
            ? {
                opacity: 0,
                gridTemplateRows: "0fr",
                transition: reduceMotion ? undefined : TASK_COMPLETION_REMOVING_TRANSITION,
              }
            : undefined
        }
        className={clsx(
          // The 1fr track and the removing style below are the scheduled row's collapse
          // (TodoItemCard), spelled the same way so an Anytime task and a dated one leave the
          // list identically — the argument for the trick is written out there.
          "group relative grid max-w-full grid-rows-[1fr] overflow-hidden transition-opacity sm:overflow-visible",
          resting && "opacity-50 saturate-[0.65]",
        )}
      >
        <div
          className="absolute inset-y-0 right-0 z-0 flex items-center gap-3 pr-3 sm:hidden"
          style={{ opacity: Math.min(1, Math.abs(swipeX) / ACTIONS_WIDTH) }}
        >
          <button
            type="button"
            aria-label="Edit floater"
            onPointerDown={(event) => event.stopPropagation()}
            onMouseDown={(event) => event.stopPropagation()}
            onTouchStart={(event) => event.stopPropagation()}
            onClick={() => {
              hapticButtonTap();
              setDisplayForm(true);
              closeSwipe();
            }}
            className="flex flex-col items-center gap-1"
          >
            <span
              className="flex h-[34px] w-14 items-center justify-center rounded-[17px]"
              style={{ backgroundColor: SWIPE_EDIT_COLOR }}
            >
              <SquarePen className="h-5 w-5 text-white" strokeWidth={2.2} />
            </span>
            <span className="text-[11px] font-bold text-muted-foreground">Edit</span>
          </button>
          <button
            type="button"
            aria-label="Copy floater"
            onPointerDown={(event) => event.stopPropagation()}
            onMouseDown={(event) => event.stopPropagation()}
            onTouchStart={(event) => event.stopPropagation()}
            onClick={() => {
              hapticButtonTap();
              closeSwipe();
              void handleCopy();
            }}
            className="flex flex-col items-center gap-1"
          >
            <span
              className="flex h-[34px] w-14 items-center justify-center rounded-[17px]"
              style={{ backgroundColor: SWIPE_COPY_COLOR }}
            >
              <Copy className="h-5 w-5 text-white" strokeWidth={2.2} />
            </span>
            <span className="text-[11px] font-bold text-muted-foreground">Copy</span>
          </button>
          <button
            type="button"
            aria-label="Delete floater"
            onPointerDown={(event) => event.stopPropagation()}
            onMouseDown={(event) => event.stopPropagation()}
            onTouchStart={(event) => event.stopPropagation()}
            onClick={() => {
              hapticButtonTap();
              deleteMutateFn(floater);
              closeSwipe();
            }}
            className="flex flex-col items-center gap-1"
          >
            <span
              className="flex h-[34px] w-14 items-center justify-center rounded-[17px]"
              style={{ backgroundColor: SWIPE_DELETE_COLOR }}
            >
              <Trash className="h-5 w-5 text-white" strokeWidth={2.2} />
            </span>
            <span className="text-[11px] font-bold text-muted-foreground">Delete</span>
          </button>
        </div>

        <div
          // Deliberately not interactive — see TodoItemContainer. Edit is the
          // Edit button, complete is the checkbox.
          onMouseOver={() => setShowHandle(true)}
          onMouseOut={() => setShowHandle(false)}
          onClick={() => {
            if (swipeX !== 0) closeSwipe();
          }}
          {...swipeHandlers}
          style={{
            transform: `translateX(${swipeX}px)`,
            // The scheduled and calendar rows carry the identical list from the same hook —
            // an Anytime task and a dated one have to leave under a finger the same way.
            transition: swipeTransition,
            touchAction: "pan-y",
            // Lets the grid item shrink past its content — and past the `min-h-[54px]` below,
            // which would otherwise hold the box open at a floater's full mobile height.
            ...(removing ? { overflow: "hidden", minHeight: 0 } : null),
          }}
          className={clsx(
            // min-h on mobile keeps the swipe-revealed Edit/Delete pills
            // (34px pill + label ≈ 52px) from being clipped by the row's
            // overflow-hidden on a single-line floater. Desktop is unaffected.
            "relative z-10 flex min-h-[54px] items-center justify-between gap-3 px-1 py-2.5 sm:min-h-0",
            "sm:rounded-lg",
            // The deep-link mark, inset so the wrapper's clip cannot eat it, and declared on both
            // sides so only its colour travels. Both halves are argued in full on the identical
            // pair in `TodoItemContainer`. The clip is least escapable here of the three: the
            // `min-h-[54px]` above exists precisely because this row's box is what the swipe pills
            // are kept inside.
            "inset-ring-2",
            highlighted
              ? "rounded-lg inset-ring-accent/25 sm:bg-accent/5 sm:inset-ring-transparent"
              : "inset-ring-transparent",
          )}
        >
          <div className="flex min-w-0 items-start gap-3">
            <div className="shrink-0">
              <TodoCheckbox
                icon={Check}
                complete={completed}
                onChange={handleToggleComplete}
                checked={completed || completing}
                variant="outline-solid"
              />
            </div>

            {/* Check circle sits on the first line of the title so it stays put
                no matter how many lines the title wraps to. */}
            <div className="min-w-0">
              <p
                className={clsx(
                  "select-none text-[0.98rem] font-black leading-5 text-foreground transition-colors duration-emphasis",
                  (completePhase === "struck" || removing) &&
                    "task-strike text-muted-foreground",
                )}
              >
                {title}
              </p>
              {description ? (
                <pre
                  className={clsx(
                    "w-48 whitespace-pre-wrap pt-1 text-xs font-extrabold leading-4 text-muted-foreground transition-colors duration-emphasis sm:w-full",
                    // Same switch as the title above — see TodoItemContainer.
                    (completePhase === "struck" || removing) && "task-strike",
                  )}
                >
                  {description}
                </pre>
              ) : null}
            </div>
          </div>

          <div className="relative flex shrink-0 items-center gap-2 pr-1 sm:pr-0">
            <div
              className={clsx(
                "flex items-center gap-2 transition-opacity",
                showHandle && "sm:opacity-0",
              )}
            >
              {listID ? (
                <>
                  <FloaterListDot id={listID} className="h-4 w-4 sm:hidden" />
                  <span className="hidden items-center gap-1 rounded-full border border-border/70 bg-muted/70 px-2 py-[0.2rem] text-xs font-black text-foreground/80 sm:flex">
                    <FloaterListDot id={listID} className="shrink-0 text-sm" />
                    <span className="max-w-24 truncate md:max-w-52 lg:max-w-none">
                      {floaterListMetaData[listID]?.name}
                    </span>
                  </span>
                </>
              ) : null}
              {priorityFlag ? (
                <Flag
                  className={clsx(
                    "h-4 w-4 shrink-0 sm:h-3.5 sm:w-3.5",
                    priorityFlag.className,
                  )}
                  aria-label={priorityFlag.label}
                />
              ) : null}
            </div>

            {!readOnly && (
              <div
                className={clsx(
                  "absolute right-0 top-1/2 hidden -translate-y-1/2 transition-opacity sm:block",
                  showHandle ? "sm:opacity-100" : "sm:pointer-events-none sm:opacity-0",
                )}
              >
                <div className="flex items-center gap-1">
                  <PromoteFloaterMenu floater={floater} />
                  <TaskActionButtons
                    onEdit={() => { hapticButtonTap(); setDisplayForm(true); }}
                    onCopy={() => { hapticButtonTap(); void handleCopy(); }}
                    onDelete={() => { hapticButtonTap(); deleteMutateFn(floater); }}
                    editLabel="Edit floater"
                    copyLabel="Copy floater"
                    deleteLabel="Delete floater"
                  />
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
      <FloaterFormSheet
        open={displayForm}
        onOpenChange={setDisplayForm}
        floater={floater}
      />
    </>
  );
}
