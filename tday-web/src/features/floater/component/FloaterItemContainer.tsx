import { useEffect, useRef, useState } from "react";
import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import clsx from "clsx";
import { Check, Flag, GripVertical, SquarePen, Trash } from "lucide-react";
import TodoCheckbox from "@/components/ui/TodoCheckbox";
import { TaskActionButtons } from "@/components/ui/TaskActionButtons";
import FloaterListDot from "@/features/floaterList/component/FloaterListDot";
import { getPriorityFlag } from "@/lib/priority";
import { useFloaterListMetaData } from "@/features/floaterList/query/get-floater-list-meta";
import { useCompleteFloater } from "@/features/floater/query/complete-floater";
import { useDeleteFloater } from "@/features/floater/query/delete-floater";
import type { FloaterItemType } from "@/types";
import FloaterFormSheet from "./FloaterFormSheet";
import { hapticButtonTap } from "@/lib/haptics";

type FloaterItemContainerProps = {
  floater: FloaterItemType;
  highlighted?: boolean;
};

export default function FloaterItemContainer({
  floater,
  highlighted = false,
}: FloaterItemContainerProps) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } =
    useSortable({ id: floater.id });
  const style = {
    transform: CSS.Translate.toString(transform),
    transition,
  };
  const { floaterListMetaData } = useFloaterListMetaData();
  const { completeMutateFn } = useCompleteFloater();
  const { deleteMutateFn } = useDeleteFloater();
  const { title, description, completed, priority, listID } = floater;
  const priorityFlag = getPriorityFlag(priority);
  const [displayForm, setDisplayForm] = useState(false);
  const [showHandle, setShowHandle] = useState(false);
  const [completePhase, setCompletePhase] = useState<
    "checked" | "struck" | "removing" | null
  >(null);
  const completeTimers = useRef<number[]>([]);
  const completing = completePhase !== null;
  // Matches the home/scheduled row (TodoItemCard) so the swipe distance and the
  // fully-revealed Edit + Delete pills sit in the same place — at 110 the pills
  // (~136px) outran the slide, leaving the priority flag on top of Edit.
  const ACTIONS_WIDTH = 140;
  const [swipeX, setSwipeX] = useState(0);
  const [swiping, setSwiping] = useState(false);
  const swipeTouch = useRef<
    { x: number; y: number; startX: number; axis: "x" | "y" | null } | null
  >(null);

  const closeSwipe = () => setSwipeX(0);

  const handleToggleComplete = () => {
    if (completed) {
      completeMutateFn(floater);
      return;
    }
    if (completing) return;
    setCompletePhase("checked");
    completeTimers.current.push(
      window.setTimeout(() => setCompletePhase("struck"), 280),
      window.setTimeout(() => setCompletePhase("removing"), 620),
      window.setTimeout(() => completeMutateFn(floater), 960),
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
            ? { ...style, opacity: 0, transition: "opacity 300ms ease" }
            : style
        }
        {...attributes}
        {...listeners}
        className={clsx(
          "group relative max-w-full overflow-hidden sm:overflow-visible",
          isDragging && "opacity-80",
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
              style={{ backgroundColor: "#4C7DDE" }}
            >
              <SquarePen className="h-5 w-5 text-white" strokeWidth={2.2} />
            </span>
            <span className="text-[11px] font-bold text-muted-foreground">Edit</span>
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
              style={{ backgroundColor: "#FF453A" }}
            >
              <Trash className="h-5 w-5 text-white" strokeWidth={2.2} />
            </span>
            <span className="text-[11px] font-bold text-muted-foreground">Delete</span>
          </button>
        </div>

        <div
          onDoubleClick={() => setDisplayForm(true)}
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
            "sm:cursor-grab sm:rounded-lg sm:active:cursor-grabbing sm:hover:bg-muted/40",
            highlighted && "rounded-lg ring-2 ring-accent/25 sm:bg-accent/5 sm:ring-0",
          )}
        >
          <div
            className={clsx(
              "absolute bottom-1/2 -left-5 hidden translate-y-1/2 p-1 transition-colors sm:block",
              showHandle ? "text-muted-foreground" : "text-transparent",
            )}
          >
            <GripVertical className="h-4 w-4" />
          </div>

          <div className="flex min-w-0 items-center gap-3">
            <div className="shrink-0">
              <TodoCheckbox
                icon={Check}
                complete={completed}
                onChange={handleToggleComplete}
                checked={completed || completing}
                variant="outline-solid"
              />
            </div>

            {/* Floaters have no due time, so the title is vertically centered on
                the check circle (intentionally offset from the home/scheduled row,
                which has a "Due …" line beneath the title). */}
            <div className="min-w-0">
              <p
                className={clsx(
                  "select-none text-[0.98rem] font-black leading-5 text-foreground transition-colors duration-300",
                  (completePhase === "struck" || completePhase === "removing") &&
                    "text-muted-foreground line-through",
                )}
              >
                {title}
              </p>
              {description ? (
                <pre className="w-48 whitespace-pre-wrap pt-1 text-xs font-extrabold leading-4 text-muted-foreground sm:w-full">
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

            <div
              className={clsx(
                "absolute right-0 top-1/2 hidden -translate-y-1/2 transition-opacity sm:block",
                showHandle ? "sm:opacity-100" : "sm:pointer-events-none sm:opacity-0",
              )}
            >
              <TaskActionButtons
                onEdit={() => { hapticButtonTap(); setDisplayForm(true); }}
                onDelete={() => { hapticButtonTap(); deleteMutateFn(floater); }}
                editLabel="Edit floater"
                deleteLabel="Delete floater"
              />
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
