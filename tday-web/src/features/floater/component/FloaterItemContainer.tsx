import { useCallback, useEffect, useState } from "react";
import clsx from "clsx";
import { TASK_COMPLETION_REMOVING_TRANSITION } from "@/lib/taskCompletionTiming";
import { usePrefersReducedMotion } from "@/lib/prefersReducedMotion";
import {
  stageTaskCompletion,
  useTaskCompletionPhase,
} from "@/lib/taskCompletionStaging";
import { Check, Copy, Flag, SquarePen, Trash } from "lucide-react";
import TodoCheckbox from "@/components/ui/TodoCheckbox";
import { TaskActionButtons } from "@/components/ui/TaskActionButtons";
import FloaterListDot from "@/features/floaterList/component/FloaterListDot";
import { useScopedListId } from "@/providers/ListScopeProvider";
import { shouldShowListMark } from "@/lib/listMark";
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
  // Same rule as the scheduled row, and applied here even though this screen's rows
  // cannot currently trip it: `/api/floaterList/:id` answers with `FloaterListTodoDto`,
  // which carries no list id at all, so an Anytime list's detail already draws no mark.
  // One rule in both rows is still worth the line — the day that DTO gains the field to
  // fix something else, the Anytime detail would otherwise quietly start repeating its
  // own title the way the native clients' did.
  const scopedListId = useScopedListId();
  const showListMark = shouldShowListMark(listID, scopedListId);
  // "Resting floaters": dim Anytime tasks left untouched for a month+ (read-only cue).
  const resting =
    !completed &&
    isRestingFloatersEnabled() &&
    floaterRestingTier(floaterUpdatedEpochMs(floater), Date.now()) !== "active";
  const priorityFlag = getPriorityFlag(priority);
  const [displayForm, setDisplayForm] = useState(false);
  const [showHandle, setShowHandle] = useState(false);
  // Staged completion is read from `taskCompletionStaging`, not held in local state, for the
  // same reason the scheduled row reads it that way: this row has no right to the window
  // between the tap and the commit — an Anytime re-sort or a filter change unmounts it
  // mid-sequence, and component-owned timers would drop the commit along with the row. Keyed
  // by task id, so a row that leaves and comes back rejoins its own sequence.
  const completePhase = useTaskCompletionPhase(floater.id);
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
    // Green tick + pop now; strike, fade and the actual completion are scheduled by the staging
    // module so that none of them depend on this row still being on screen when they come due.
    stageTaskCompletion(floater.id, () => completeMutateFn(floater));
  };

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
          {/* One box for the row's content, stacked from the TOP, with the row itself
              left centred. `min-h-[54px]` above floors this row on mobile so the swipe
              pills are not clipped, and top-aligning the outer box would have spent
              that floor entirely below the content — every single-line floater rising
              7 px so that the wrapped ones could be fixed. Same shape as
              `TodoItemContainer`. */}
          <div className="flex w-full min-w-0 items-start justify-between gap-3">
          <div className="flex min-w-0 items-start gap-3">
            {/* The control's slot is the title's line box (`leading-5` = 20 px), the
                same as in `TodoItemContainer` — bare `items-start` is only right while
                the toggle is exactly 20 px, and the recurring variant is 21.6. */}
            <div className="flex h-5 shrink-0 items-center">
              <TodoCheckbox
                icon={Check}
                complete={completed}
                onChange={handleToggleComplete}
                checked={completed || completing}
                variant="outline-solid"
              />
            </div>

            {/* Check circle sits on the first line of the title so it stays put
                no matter how many lines the title wraps to — and, as of this change,
                so does the flag at the other end of the row. */}
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
                // `wrap-anywhere` (`overflow-wrap: anywhere`) is what keeps a note inside the app
                // border, and the report is one long URL: `pre-wrap` breaks at spaces and newlines
                // but never inside a word, so an unbroken run is not squeezed into the note's box —
                // it paints past it, and past the `max-w-6xl` border with it, however narrow the
                // column gets. `break-words` will not do: it leaves the run's min-content width
                // untouched. Same class, same reason and the same measurement as the two Completed
                // rows — see `features/completed/component/ItemContainer.tsx`.
                <pre
                  className={clsx(
                    "w-48 whitespace-pre-wrap wrap-anywhere pt-1 text-xs font-extrabold leading-4 text-muted-foreground transition-colors duration-emphasis sm:w-full",
                    // Same switch as the title above — see TodoItemContainer.
                    (completePhase === "struck" || removing) && "task-strike",
                  )}
                >
                  {description}
                </pre>
              ) : null}
            </div>
          </div>

          {/* `self-stretch` so this box still spans the row and the hover toolbar it
              positions at `top-1/2` stays centred on the ROW — it is a menu for the
              task, not a mark on its title. See `TodoItemContainer`. */}
          <div className="relative flex shrink-0 items-start gap-2 self-stretch pr-1 sm:pr-0">
            <div
              className={clsx(
                // The title's own line box, the same one the check circle gets at the
                // other end of the row.
                "flex h-5 items-center gap-2 transition-opacity",
                showHandle && "sm:opacity-0",
              )}
            >
              {showListMark && listID ? (
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
      </div>
      <FloaterFormSheet
        open={displayForm}
        onOpenChange={setDisplayForm}
        floater={floater}
      />
    </>
  );
}
