import { useEffect, useRef, useState } from "react";
import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import clsx from "clsx";
import {
  TASK_COMPLETION_CHECK_TO_STRIKE_MS,
  TASK_COMPLETION_FADE_MS,
  TASK_COMPLETION_STRIKE_TO_FADE_MS,
  TASK_COMPLETION_TOTAL_MS,
} from "@/lib/taskCompletionTiming";
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
  // attributes/listeners intentionally unused — see TodoItemContainer.
  const { setNodeRef, transform, transition, isDragging } =
    useSortable({ id: floater.id });
  const style = {
    transform: CSS.Translate.toString(transform),
    transition,
  };
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
  // Matches the scheduled task home row (TodoItemCard) so the swipe distance and the
  // fully-revealed Edit + Copy + Delete pills sit in the same place — at 110 the pills
  // (~136px) outran the slide, leaving the priority flag on top of Edit.
  const ACTIONS_WIDTH = 210;
  const [swipeX, setSwipeX] = useState(0);
  const [swiping, setSwiping] = useState(false);
  const swipeTouch = useRef<
    { x: number; y: number; startX: number; axis: "x" | "y" | null } | null
  >(null);

  const closeSwipe = () => setSwipeX(0);

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
    setCompletePhase("checked");
    completeTimers.current.push(
      window.setTimeout(() => setCompletePhase("struck"), TASK_COMPLETION_CHECK_TO_STRIKE_MS),
      window.setTimeout(
        () => setCompletePhase("removing"),
        TASK_COMPLETION_CHECK_TO_STRIKE_MS + TASK_COMPLETION_STRIKE_TO_FADE_MS,
      ),
      window.setTimeout(() => completeMutateFn(floater), TASK_COMPLETION_TOTAL_MS),
    );
  };

  useEffect(() => {
    const timers = completeTimers.current;
    return () => {
      timers.forEach((id) => window.clearTimeout(id));
    };
  }, []);

  useEffect(() => {
    const onOpen = (event: Event) => {
      const id = (event as CustomEvent<string>).detail;
      if (id !== floater.id) setSwipeX(0);
    };
    window.addEventListener("tday-floater-swipe-open", onOpen as EventListener);
    return () =>
      window.removeEventListener("tday-floater-swipe-open", onOpen as EventListener);
  }, [floater.id]);

  const handleTouchStart = (event: React.TouchEvent) => {
    if (readOnly) return;
    const touch = event.touches[0];
    swipeTouch.current = {
      x: touch.clientX,
      y: touch.clientY,
      startX: swipeX,
      axis: null,
    };
    setSwiping(true);
  };
  const handleTouchMove = (event: React.TouchEvent) => {
    const data = swipeTouch.current;
    if (!data) return;
    const touch = event.touches[0];
    const dx = touch.clientX - data.x;
    const dy = touch.clientY - data.y;
    if (data.axis === null && (Math.abs(dx) > 8 || Math.abs(dy) > 8)) {
      data.axis = Math.abs(dx) > Math.abs(dy) ? "x" : "y";
      if (data.axis === "x") {
        window.dispatchEvent(
          new CustomEvent("tday-floater-swipe-open", { detail: floater.id }),
        );
      }
    }
    if (data.axis === "x") {
      setSwipeX(Math.min(0, Math.max(-ACTIONS_WIDTH, data.startX + dx)));
    }
  };
  const handleTouchEnd = () => {
    const data = swipeTouch.current;
    setSwiping(false);
    swipeTouch.current = null;
    if (data?.axis === "x") {
      setSwipeX((previous) => (previous < -ACTIONS_WIDTH / 2 ? -ACTIONS_WIDTH : 0));
    }
  };

  return (
    <>
      <div
        ref={setNodeRef}
        style={
          completePhase === "removing"
            ? { ...style, opacity: 0, transition: `opacity ${TASK_COMPLETION_FADE_MS}ms ease` }
            : style
        }
        className={clsx(
          "group relative max-w-full overflow-hidden transition-opacity sm:overflow-visible",
          isDragging && "opacity-70",
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
          onTouchStart={handleTouchStart}
          onTouchMove={handleTouchMove}
          onTouchEnd={handleTouchEnd}
          style={{
            transform: `translateX(${swipeX}px)`,
            transition: swiping
              ? "none"
              : "transform 220ms ease, background-color 150ms ease",
            touchAction: "pan-y",
          }}
          className={clsx(
            // min-h on mobile keeps the swipe-revealed Edit/Delete pills
            // (34px pill + label ≈ 52px) from being clipped by the row's
            // overflow-hidden on a single-line floater. Desktop is unaffected.
            "relative z-10 flex min-h-[54px] items-center justify-between gap-3 px-1 py-2.5 sm:min-h-0",
            "sm:rounded-lg",
            highlighted && "rounded-lg ring-2 ring-accent/25 sm:bg-accent/5 sm:ring-0",
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
                  "select-none text-[0.98rem] font-black leading-5 text-foreground transition-colors duration-300",
                  (completePhase === "struck" || completePhase === "removing") &&
                    "task-strike text-muted-foreground",
                )}
              >
                {title}
              </p>
              {description ? (
                <pre
                  className={clsx(
                    "w-48 whitespace-pre-wrap pt-1 text-xs font-extrabold leading-4 text-muted-foreground transition-colors duration-300 sm:w-full",
                    (completePhase === "struck" || completePhase === "removing") &&
                      "line-through",
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
