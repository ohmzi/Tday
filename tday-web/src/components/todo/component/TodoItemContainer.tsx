import React, { useEffect, useRef, useState } from "react";
import TodoCheckbox from "@/components/ui/TodoCheckbox";
import clsx from "clsx";
import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { TodoItemType } from "@/types";
import GripVertical from "@/components/ui/icon/gripVertical";
import { Check, Flag, SquarePen, Trash } from "lucide-react";
import { getDisplayTime } from "@/lib/date/displayDate";
import { useLocale } from "@/lib/navigation";
import { useTodoMutation } from "@/providers/TodoMutationProvider";
import { useListMetaData } from "@/components/Sidebar/List/query/get-list-meta";
import ListDot from "@/components/ListDot";
import { getPriorityFlag } from "@/lib/priority";
import { useUserTimezone } from "@/features/user/query/get-timezone";
import { getTodoFocusElementId } from "@/lib/todoToastNavigation";
import TaskFormSheet from "@/components/todo/component/TodoForm/TaskFormSheet";
import { FloatTaskButton, TaskActionButtons } from "@/components/ui/TaskActionButtons";
import { useDemoteTodo } from "@/features/todayTodos/query/demote-todo";


type TodoItemContainerProps = {
  todoItem: TodoItemType,
  overdue?: boolean,
  perTaskOverdue?: boolean,
  highlighted?: boolean,
  showOverdueTag?: boolean,
}

type TodoItemCardProps = TodoItemContainerProps & {
  containerProps?: React.HTMLAttributes<HTMLDivElement> & Record<string, unknown>;
  dragging?: boolean;
  style?: React.CSSProperties;
  setDragNodeRef?: (node: HTMLDivElement | null) => void;
}

export const TodoItemCard = ({
  todoItem,
  overdue,
  perTaskOverdue,
  highlighted = false,
  showOverdueTag = true,
  containerProps,
  dragging = false,
  style,
  setDragNodeRef,
}: TodoItemCardProps) => {
  const { listMetaData } = useListMetaData();
  const { useCompleteTodo, useDeleteTodo, readOnly = false } = useTodoMutation();
  const { completeMutateFn } = useCompleteTodo();
  const { deleteMutateFn } = useDeleteTodo();
  const { demoteMutateFn } = useDemoteTodo();
  const locale = useLocale();
  const userTimeZone = useUserTimezone();
  const [itemElement, setItemElement] = useState<HTMLDivElement | null>(null);
  const { title, description, completed, priority, rrule } = todoItem;
  const isOverdue = overdue || (perTaskOverdue && !completed && todoItem.due < new Date());
  const itemListID = todoItem.listID;
  const priorityFlag = getPriorityFlag(priority);
  const [displayForm, setDisplayForm] = useState(false);
  const [editInstanceOnly, setEditInstanceOnly] = useState(false);
  const [showHandle, setShowHandle] = useState(false);
  // Staged "checking off" sequence so each step is visible with a small gap:
  //   checked (green tick) → struck (title line-through) → removing (fade) → remove.
  const [completePhase, setCompletePhase] = useState<
    "checked" | "struck" | "removing" | null
  >(null);
  const completeTimers = useRef<number[]>([]);
  const completing = completePhase !== null;

  // Mobile swipe-to-reveal (mirrors the native slide-to-edit/delete). The row
  // foreground translates left to expose Edit + Delete. A quick horizontal swipe
  // doesn't start a drag because the DnD sensors require a ~250ms press with
  // <5px movement, which a swipe exceeds; vertical scroll/drag is preserved via
  // axis-locking and touch-action: pan-y.
  const ACTIONS_WIDTH = 140;
  const [swipeX, setSwipeX] = useState(0);
  const [swiping, setSwiping] = useState(false);
  const swipeTouch = useRef<
    { x: number; y: number; startX: number; axis: "x" | "y" | null } | null
  >(null);

  const closeSwipe = () => setSwipeX(0);

  const handleTouchStart = (e: React.TouchEvent) => {
    if (readOnly) return;
    const t = e.touches[0];
    swipeTouch.current = { x: t.clientX, y: t.clientY, startX: swipeX, axis: null };
    setSwiping(true);
  };
  const handleTouchMove = (e: React.TouchEvent) => {
    const data = swipeTouch.current;
    if (!data) return;
    const t = e.touches[0];
    const dx = t.clientX - data.x;
    const dy = t.clientY - data.y;
    if (data.axis === null && (Math.abs(dx) > 8 || Math.abs(dy) > 8)) {
      data.axis = Math.abs(dx) > Math.abs(dy) ? "x" : "y";
      // Claim the row: tell any other open row to close so only one is open.
      if (data.axis === "x") {
        window.dispatchEvent(
          new CustomEvent("tday-swipe-open", { detail: todoItem.id }),
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
      setSwipeX((prev) => (prev < -ACTIONS_WIDTH / 2 ? -ACTIONS_WIDTH : 0));
    }
  };

  const setCombinedRef = (node: HTMLDivElement | null) => {
    setItemElement(node);
    setDragNodeRef?.(node);
  };

  const handleToggleComplete = () => {
    if (readOnly) return;
    if (completed) {
      completeMutateFn(todoItem);
      return;
    }
    if (completing) return;
    setCompletePhase("checked"); // 1. green tick + pop
    completeTimers.current.push(
      window.setTimeout(() => setCompletePhase("struck"), 280), // 2. strike the title
      window.setTimeout(() => setCompletePhase("removing"), 620), // 3. start fading
      window.setTimeout(() => completeMutateFn(todoItem), 960), // 4. remove from cache
    );
  };

  useEffect(() => {
    return () => {
      completeTimers.current.forEach((id) => window.clearTimeout(id));
    };
  }, []);

  // Close this row's swipe actions when another row is swiped open.
  useEffect(() => {
    const onOpen = (e: Event) => {
      const id = (e as CustomEvent<string>).detail;
      if (id !== todoItem.id) setSwipeX(0);
    };
    window.addEventListener("tday-swipe-open", onOpen as EventListener);
    return () => window.removeEventListener("tday-swipe-open", onOpen as EventListener);
  }, [todoItem.id]);

  useEffect(() => {
    if (!displayForm) {
      setShowHandle(false);
    }
  }, [displayForm]);

  useEffect(() => {
    if (!highlighted || !itemElement) {
      return;
    }

    itemElement.scrollIntoView({ behavior: "smooth", block: "center" });
  }, [highlighted, itemElement]);

  return (
    <>
      <div
        id={getTodoFocusElementId(todoItem.id)}
        ref={setCombinedRef}
        style={
          completePhase === "removing"
            ? { ...style, opacity: 0, transition: "opacity 300ms ease" }
            : style
        }
        {...containerProps}
        className={clsx(
          // No per-row divider — the date group owns a single divider after its
          // last task (see TodoGroup / TimelineSectionDroppable).
          "group relative max-w-full overflow-hidden sm:overflow-visible",
          dragging && "opacity-70",
        )}
      >
        {/* Mobile: Edit + Delete revealed behind the row by a left swipe — native
            pill style (blue edit, red delete), white icon, label beneath. Fade in
            with the swipe so they're invisible when closed (lets the row stay
            transparent, so the screen watermark shows through). */}
        <div
          className="absolute inset-y-0 right-0 z-0 flex items-center gap-3 pr-3 sm:hidden"
          style={{ opacity: Math.min(1, Math.abs(swipeX) / ACTIONS_WIDTH) }}
        >
          <button
            type="button"
            aria-label="Edit task"
            onPointerDown={(e) => e.stopPropagation()}
            onMouseDown={(e) => e.stopPropagation()}
            onTouchStart={(e) => e.stopPropagation()}
            onClick={() => {
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
            aria-label="Delete task"
            onPointerDown={(e) => e.stopPropagation()}
            onMouseDown={(e) => e.stopPropagation()}
            onTouchStart={(e) => e.stopPropagation()}
            onClick={() => {
              deleteMutateFn(todoItem);
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

        {/* Foreground row — slides left on swipe to reveal the actions. */}
        <div
          onDoubleClick={() => {
            if (!readOnly) setDisplayForm(true);
          }}
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
            // Flat native-style row; transparent so the screen watermark shows
            // through. Swipe actions are hidden via opacity when the row is closed.
            "relative z-10 flex items-center justify-between gap-3 px-1 py-2.5",
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

      <div className="flex min-w-0 items-start gap-3">
        <div className="shrink-0">
          <TodoCheckbox
            icon={Check}
            complete={completed}
            onChange={handleToggleComplete}
            checked={completed || completing}
            variant={rrule ? "repeat" : "outline-solid"}
          />
        </div>

        <div className="max-w-full">
          <div className="mb-1.5 flex items-center gap-1.5">
            <p
              className={clsx(
                "select-none text-[0.98rem] font-black leading-5 text-foreground transition-colors duration-300",
                (completePhase === "struck" || completePhase === "removing") &&
                  "text-muted-foreground line-through",
              )}
            >
              {title}
            </p>
          </div>
          {description && (
            <pre className="w-48 whitespace-pre-wrap pb-2 text-xs font-extrabold leading-4 text-muted-foreground sm:w-full">
              {description}
            </pre>
          )}
          <div className="flex flex-wrap items-center justify-start gap-2 text-xs font-black">
            {/* The date header already shows the day, so the row shows just the
                time ("Due 1:00 AM") — same on mobile and desktop, matching native. */}
            <p className={clsx("font-bold", isOverdue ? "text-red" : "text-muted-foreground")}>
              {`Due ${getDisplayTime(todoItem.due, locale, userTimeZone?.timeZone)}`}
            </p>
            {isOverdue && showOverdueTag && (
              <p className='rounded-full border border-red/30 bg-red/10 px-2 py-[0.2rem] text-red font-medium'>
                overdue
              </p>
            )}
          </div>
        </div>
      </div>

        <div className="relative flex shrink-0 items-center gap-2 pr-1 sm:pr-0">
          {/* Priority flag + list, right-aligned on the title line (native layout).
              Mobile shows just the list icon; desktop shows the full name pill.
              On desktop the meta fades out on hover to reveal the edit/delete actions. */}
          <div
            className={clsx(
              "flex items-center gap-2 transition-opacity",
              showHandle && "sm:opacity-0",
            )}
          >
            {itemListID && (
              <>
                <ListDot id={itemListID} className="h-4 w-4 sm:hidden" />
                <span className="hidden items-center gap-1 rounded-full border border-border/70 bg-muted/70 px-2 py-[0.2rem] text-xs font-black text-foreground/80 sm:flex">
                  <ListDot id={itemListID} className="shrink-0 text-sm" />
                  <span className="max-w-24 truncate md:max-w-52 lg:max-w-none">
                    {listMetaData[itemListID]?.name}
                  </span>
                </span>
              </>
            )}
            {priorityFlag && (
              <Flag
                className={clsx("h-4 w-4 shrink-0 sm:h-3.5 sm:w-3.5", priorityFlag.className)}
                aria-label={priorityFlag.label}
              />
            )}
          </div>

          {/* Desktop hover edit/delete actions, overlaid at the right edge. */}
          {!readOnly && (
            <div
              className={clsx(
                "absolute right-0 top-1/2 hidden -translate-y-1/2 transition-opacity sm:block",
                showHandle ? "sm:opacity-100" : "sm:pointer-events-none sm:opacity-0",
              )}
            >
              <div className="flex items-center gap-1">
                {/* "Let it float" only makes sense on carried-over tasks, and
                    the backend rejects recurring ones (their series would be
                    silently destroyed). */}
                {isOverdue && !rrule && (
                  <FloatTaskButton
                    onActivate={() => demoteMutateFn(todoItem)}
                    label="Let it float"
                  />
                )}
                <TaskActionButtons
                  onEdit={() => setDisplayForm(true)}
                  onDelete={() => deleteMutateFn(todoItem)}
                  editLabel="Edit task"
                  deleteLabel="Delete task"
                />
              </div>
            </div>
          )}
        </div>
        </div>
      </div>
      <TaskFormSheet
        open={displayForm}
        onOpenChange={setDisplayForm}
        todo={todoItem}
        editInstanceOnly={editInstanceOnly}
        setEditInstanceOnly={setEditInstanceOnly}
      />
    </>
  );
};

export const TodoItemContainer = ({
  todoItem,
  overdue,
  perTaskOverdue,
  highlighted = false,
  showOverdueTag = true,
}: TodoItemContainerProps) => {
  //dnd kit setups
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } =
    useSortable({ id: todoItem.id });
  const style = {
    transform: CSS.Translate.toString(transform),
    transition,
  };
  return (
    <TodoItemCard
      todoItem={todoItem}
      overdue={overdue}
      perTaskOverdue={perTaskOverdue}
      highlighted={highlighted}
      showOverdueTag={showOverdueTag}
      containerProps={{ ...attributes, ...listeners }}
      dragging={isDragging}
      style={style}
      setDragNodeRef={setNodeRef}
    />
  );

};
