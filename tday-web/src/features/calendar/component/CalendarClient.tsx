import "../style/calendar-styles.css";
import {
  addDays,
  addMonths,
  addWeeks,
  eachDayOfInterval,
  endOfDay,
  endOfMonth,
  endOfWeek,
  format,
  isBefore,
  isSameDay,
  isSameMonth,
  startOfDay,
  startOfMonth,
  startOfWeek,
  subDays,
  subMonths,
  subWeeks,
} from "date-fns";
import { TodoItemType } from "@/types";
import { useDateRange } from "../hooks/useDateRange";
import { useCalendarTodo } from "../query/get-calendar-todo";
import { useTodoTimeline } from "@/features/todayTodos/query/get-todo-timeline";
import {
  lazy,
  type PointerEvent as ReactPointerEvent,
  type TouchEvent as ReactTouchEvent,
  Suspense,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import {
  DndContext,
  DragOverlay,
  KeyboardSensor,
  MouseSensor,
  TouchSensor,
  useDraggable,
  useDroppable,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragStartEvent,
} from "@dnd-kit/core";
import { useEditCalendarTodo } from "../query/update-calendar-todo";
import { useUserTimezone } from "@/features/user/query/get-timezone";
import { moveTodoToDay } from "@/lib/moveTodoToDay";
import type { TodoItemTypeWithDateChecksum } from "@/lib/todo/patch-todo";
import ConfirmRescheduleRecurring, {
  type PendingReschedule,
} from "./ConfirmationModals/ConfirmRescheduleRecurring";
import Spinner from "@/components/ui/spinner";
import { useListMetaData } from "@/components/Sidebar/List/query/get-list-meta";
import CreateCalendarFormContainer from "./CalendarForm/CreateFormContainer";
import NativePageTitle from "@/components/app/NativePageTitle";
import { nativeScreenAccentColors } from "@/components/app/nativeScreenTheme";
import NativeAppBrandButton from "@/components/app/NativeAppBrandButton";
import { cn } from "@/lib/utils";
import { useTranslation } from "react-i18next";
import { useRegisterCalendarCreateAction } from "@/features/calendar/context/CalendarCreateActionContext";
import TodoCheckbox from "@/components/ui/TodoCheckbox";
import { TaskActionButtons } from "@/components/ui/TaskActionButtons";
import ListDot from "@/components/ListDot";
import EditCalendarFormContainer from "./CalendarForm/EditFormContainer";
import { useCompleteCalendarTodo } from "../query/complete-calendar-todo";
import { useCompleteCalendarTodoInstance } from "../query/complete-calendar-todo-instance";
import { CalendarDays, Check, ChevronLeft, ChevronRight, Flag, GripVertical, SquarePen, Trash } from "lucide-react";
import { isToday } from "date-fns";
import { getPriorityFlag } from "@/lib/priority";
import i18n from "@/i18n";
import { getDateFnsLocale } from "@/lib/date/dateFnsLocale";

const ConfirmDelete = lazy(() => import("./ConfirmationModals/ConfirmDelete"));
const ConfirmDeleteAll = lazy(() => import("./ConfirmationModals/ConfirmDeleteAll"));

type CalendarViewMode = "month" | "week" | "day";
type SlideDirection = "left" | "right";

const viewOptions: CalendarViewMode[] = ["month", "week", "day"];
const swipeThreshold = 48;

function dayKey(date: Date) {
  return format(startOfDay(date), "yyyy-MM-dd");
}

// Active date-fns Locale for the current i18n language, so calendar labels
// (month/weekday names) render in the selected language.
function activeDfLocale() {
  return getDateFnsLocale(i18n.language);
}

function taskCountText(count: number) {
  if (count <= 0) return "";
  return count > 9 ? "9+" : String(count);
}

function calendarRangeFor(date: Date, view: CalendarViewMode) {
  if (view === "month") {
    return {
      start: startOfWeek(startOfMonth(date)),
      end: endOfWeek(endOfMonth(date)),
    };
  }

  if (view === "week") {
    return {
      start: startOfWeek(date),
      end: endOfWeek(date),
    };
  }

  return {
    start: startOfDay(date),
    end: endOfDay(date),
  };
}

function periodDate(date: Date, view: CalendarViewMode, offset: number) {
  if (view === "month") return offset > 0 ? addMonths(date, offset) : subMonths(date, Math.abs(offset));
  if (view === "week") return offset > 0 ? addWeeks(date, offset) : subWeeks(date, Math.abs(offset));
  return offset > 0 ? addDays(date, offset) : subDays(date, Math.abs(offset));
}

function canNavigateTo(date: Date, minimumMonth: Date) {
  return !isBefore(startOfMonth(date), minimumMonth);
}

function makeMonthDays(date: Date) {
  return eachDayOfInterval({
    start: startOfWeek(startOfMonth(date)),
    end: endOfWeek(endOfMonth(date)),
  });
}

function CalendarViewSlider({
  view,
  onViewChange,
}: {
  view: CalendarViewMode;
  onViewChange: (view: CalendarViewMode) => void;
}) {
  const { t: appDict } = useTranslation("app");
  const selectedIndex = viewOptions.indexOf(view);

  return (
    <div className="relative flex w-full rounded-[25px] border border-white/70 bg-muted/80 p-1.5 shadow-[0_18px_42px_-30px_hsl(var(--shadow)/0.62)] backdrop-blur-xl dark:border-white/10">
      <div
        className="absolute bottom-1.5 left-1.5 top-1.5 rounded-[20px] bg-card shadow-[0_10px_24px_-18px_hsl(var(--shadow)/0.7)] transition-transform duration-300 ease-out"
        style={{
          width: "calc((100% - 0.75rem) / 3)",
          transform: `translateX(${selectedIndex * 100}%)`,
        }}
      />
      {viewOptions.map((option) => (
        <button
          key={option}
          type="button"
          onClick={() => onViewChange(option)}
          className={cn(
            "relative z-10 flex h-12 flex-1 items-center justify-center rounded-[20px] px-3 text-sm font-black capitalize transition-colors duration-200",
            option === view ? "text-foreground" : "text-muted-foreground hover:text-foreground",
          )}
          aria-pressed={option === view}
        >
          {appDict(option)}
        </button>
      ))}
    </div>
  );
}

function CalendarNavButton({
  label,
  disabled,
  direction,
  onClick,
}: {
  label: string;
  disabled?: boolean;
  direction: "previous" | "next";
  onClick: () => void;
}) {
  const Icon = direction === "previous" ? ChevronLeft : ChevronRight;

  return (
    <button
      type="button"
      aria-label={label}
      disabled={disabled}
      onClick={onClick}
      className="flex h-10 w-10 items-center justify-center rounded-full border border-white/70 bg-card/90 text-muted-foreground shadow-sm transition-all duration-200 hover:bg-card hover:text-foreground disabled:cursor-not-allowed disabled:opacity-35 dark:border-white/10 sm:h-11 sm:w-11"
    >
      <Icon className="h-5 w-5 stroke-[2.5]" />
    </button>
  );
}

function CalendarModeCard({
  view,
  selectedDate,
  tasksByDay,
  slideDirection,
  animationKey,
  canGoPrevious,
  onNavigate,
  onSelectDate,
}: {
  view: CalendarViewMode;
  selectedDate: Date;
  tasksByDay: Map<string, TodoItemType[]>;
  slideDirection: SlideDirection | null;
  animationKey: number;
  canGoPrevious: boolean;
  onNavigate: (offset: -1 | 1) => void;
  onSelectDate: (date: Date) => void;
}) {
  const touchStartX = useRef<number | null>(null);
  const swipeTrackingRef = useRef(false);

  const handlePointerDown = (event: ReactPointerEvent<HTMLDivElement>) => {
    const target = event.target as HTMLElement;
    if (target.closest("button")) {
      swipeTrackingRef.current = false;
      touchStartX.current = null;
      return;
    }
    swipeTrackingRef.current = true;
    touchStartX.current = event.clientX;
  };

  const handlePointerUp = (event: ReactPointerEvent<HTMLDivElement>) => {
    if (!swipeTrackingRef.current) return;
    const startX = touchStartX.current;
    touchStartX.current = null;
    swipeTrackingRef.current = false;
    if (startX == null) return;

    const delta = event.clientX - startX;
    if (Math.abs(delta) < swipeThreshold) return;
    onNavigate(delta < 0 ? 1 : -1);
  };

  const { t: appDict } = useTranslation("app");
  const dfLocale = activeDfLocale();
  const title =
    view === "month"
      ? format(selectedDate, "MMMM yyyy", { locale: dfLocale })
      : view === "week"
        ? `${format(startOfWeek(selectedDate), "MMM d", { locale: dfLocale })} - ${format(endOfWeek(selectedDate), "MMM d", { locale: dfLocale })}`
        : format(selectedDate, "EEEE, MMM d", { locale: dfLocale });

  return (
    <section className="rounded-[24px] border border-white/70 bg-card/94 p-4 shadow-[0_18px_42px_-34px_hsl(var(--shadow)/0.62)] dark:border-white/10 sm:p-5">
      <div className="mb-4 flex items-center gap-2 sm:gap-3">
        <CalendarNavButton
          label={appDict("previous")}
          direction="previous"
          disabled={!canGoPrevious}
          onClick={() => onNavigate(-1)}
        />
        <div className="min-w-0 flex-1 text-center">
          <h2 className="truncate text-xl font-black tracking-normal text-foreground sm:text-2xl">
            {title}
          </h2>
        </div>
        <CalendarNavButton
          label={appDict("next")}
          direction="next"
          onClick={() => onNavigate(1)}
        />
      </div>

      <div
        key={animationKey}
        className={cn(
          "touch-pan-y",
          slideDirection === "left" && "cal-native-slide-from-left",
          slideDirection === "right" && "cal-native-slide-from-right",
        )}
        onPointerDown={handlePointerDown}
        onPointerUp={handlePointerUp}
      >
        {view === "month" && (
          <MonthCalendarGrid
            selectedDate={selectedDate}
            tasksByDay={tasksByDay}
            onSelectDate={onSelectDate}
          />
        )}
        {view === "week" && (
          <WeekCalendarStrip
            selectedDate={selectedDate}
            tasksByDay={tasksByDay}
            onSelectDate={onSelectDate}
          />
        )}
        {view === "day" && (
          <DayCalendarSummary
            selectedDate={selectedDate}
            taskCount={tasksByDay.get(dayKey(selectedDate))?.length ?? 0}
          />
        )}
      </div>
    </section>
  );
}

function DroppableDayCell({
  date,
  disabled,
  onSelectDate,
  className,
  children,
}: {
  date: Date;
  disabled?: boolean;
  onSelectDate: (date: Date) => void;
  className: string;
  children: React.ReactNode;
}) {
  const key = dayKey(date);
  const { setNodeRef, isOver } = useDroppable({
    id: `day:${key}`,
    data: { dayKey: key },
    disabled,
  });

  return (
    <button
      ref={setNodeRef}
      type="button"
      disabled={disabled}
      onClick={() => onSelectDate(date)}
      aria-label={format(date, "PPP", { locale: activeDfLocale() })}
      className={cn(
        className,
        // `isOver` is only true mid-drag, so this highlights the active drop target.
        isOver && "relative z-10 ring-2 ring-accent ring-offset-2 ring-offset-card",
      )}
    >
      {children}
    </button>
  );
}

function MonthCalendarGrid({
  selectedDate,
  tasksByDay,
  onSelectDate,
}: {
  selectedDate: Date;
  tasksByDay: Map<string, TodoItemType[]>;
  onSelectDate: (date: Date) => void;
}) {
  const today = new Date();
  const minimumMonth = startOfMonth(today);
  const days = makeMonthDays(selectedDate);
  const weekdayLabels = eachDayOfInterval({
    start: startOfWeek(today),
    end: endOfWeek(today),
  }).map((date) => format(date, "EEEEE", { locale: activeDfLocale() }));

  return (
    <div className="space-y-3">
      <div className="grid grid-cols-7">
        {weekdayLabels.map((label, index) => (
          <div
            key={`${label}-${index}`}
            className="text-center text-xs font-black uppercase text-muted-foreground/55"
          >
            {label}
          </div>
        ))}
      </div>
      <div className="grid grid-cols-7 gap-y-2">
        {days.map((date) => {
          const selected = isSameDay(date, selectedDate);
          const todayDate = isSameDay(date, today);
          const currentMonth = isSameMonth(date, selectedDate);
          const disabled = isBefore(startOfMonth(date), minimumMonth);
          const count = tasksByDay.get(dayKey(date))?.length ?? 0;

          return (
            <DroppableDayCell
              key={date.toISOString()}
              date={date}
              disabled={disabled}
              onSelectDate={onSelectDate}
              className={cn(
                "mx-auto flex h-[3.1rem] w-[2.9rem] flex-col items-center justify-center rounded-2xl text-center transition-colors duration-200",
                "hover:bg-accent/10 disabled:cursor-not-allowed disabled:opacity-30",
                selected && "bg-accent text-accent-foreground shadow-[0_12px_24px_-18px_hsl(var(--accent)/0.8)] hover:bg-accent",
                !selected && todayDate && "border border-accent/45 text-accent",
                !selected && !todayDate && currentMonth && "text-foreground",
                !selected && !todayDate && !currentMonth && "text-muted-foreground/45",
              )}
            >
              <span className="text-lg font-black leading-none">{format(date, "d", { locale: activeDfLocale() })}</span>
              <span
                className={cn(
                  "mt-1 flex h-3 items-center gap-1 text-[0.62rem] font-black leading-none",
                  selected ? "text-accent-foreground/90" : count > 0 ? "text-accent" : "text-transparent",
                )}
              >
                {count > 0 && <span className="h-1.5 w-1.5 rounded-full bg-current" />}
                {taskCountText(count)}
              </span>
            </DroppableDayCell>
          );
        })}
      </div>
    </div>
  );
}

function WeekCalendarStrip({
  selectedDate,
  tasksByDay,
  onSelectDate,
}: {
  selectedDate: Date;
  tasksByDay: Map<string, TodoItemType[]>;
  onSelectDate: (date: Date) => void;
}) {
  const today = new Date();
  const days = eachDayOfInterval({
    start: startOfWeek(selectedDate),
    end: endOfWeek(selectedDate),
  });

  return (
    <div className="grid grid-cols-7 gap-2">
      {days.map((date) => {
        const selected = isSameDay(date, selectedDate);
        const todayDate = isSameDay(date, today);
        const count = tasksByDay.get(dayKey(date))?.length ?? 0;

        return (
          <DroppableDayCell
            key={date.toISOString()}
            date={date}
            onSelectDate={onSelectDate}
            className={cn(
              "flex min-h-[4.8rem] flex-col items-center justify-center rounded-[20px] border text-center transition-colors duration-200",
              selected
                ? "border-accent bg-accent text-accent-foreground shadow-[0_12px_24px_-18px_hsl(var(--accent)/0.8)]"
                : "border-white/60 bg-muted/45 text-foreground hover:bg-muted/70 dark:border-white/10",
              !selected && todayDate && "text-accent",
            )}
          >
            <span className="text-[0.65rem] font-black uppercase tracking-wide opacity-70">
              {format(date, "EEE", { locale: activeDfLocale() })}
            </span>
            <span className="text-xl font-black leading-tight">{format(date, "d", { locale: activeDfLocale() })}</span>
            <span className={cn("text-[0.68rem] font-black", count > 0 ? "opacity-90" : "opacity-0")}>
              {taskCountText(count)}
            </span>
          </DroppableDayCell>
        );
      })}
    </div>
  );
}

function DayCalendarSummary({
  selectedDate,
  taskCount,
}: {
  selectedDate: Date;
  taskCount: number;
}) {
  const { t: appDict } = useTranslation("app");
  const dfLocale = activeDfLocale();
  return (
    <div className="flex min-h-[5.2rem] items-center justify-between rounded-[22px] border border-white/60 bg-muted/45 px-5 py-4 dark:border-white/10">
      <div>
        <p className="text-sm font-black uppercase tracking-[0.18em] text-muted-foreground/70">
          {format(selectedDate, "EEEE", { locale: dfLocale })}
        </p>
        <p className="text-3xl font-black text-foreground">{format(selectedDate, "MMM d", { locale: dfLocale })}</p>
      </div>
      <div className="rounded-full bg-accent/12 px-4 py-2 text-sm font-black text-accent">
        {appDict("taskCount", { count: taskCount })}
      </div>
    </div>
  );
}

function CalendarTaskRow({
  todo,
  listName,
  highlighted = false,
}: {
  todo: TodoItemType;
  listName?: string;
  highlighted?: boolean;
}) {
  const [displayForm, setDisplayForm] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deleteAllDialogOpen, setDeleteAllDialogOpen] = useState(false);
  const [itemElement, setItemElement] = useState<HTMLElement | null>(null);
  const [showHandle, setShowHandle] = useState(false);
  const { t: todayDict } = useTranslation("today");
  const { mutateComplete } = useCompleteCalendarTodo();
  const { mutateComplete: mutateInstanceComplete } = useCompleteCalendarTodoInstance();
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id: todo.id,
    data: { todo },
  });
  const priorityFlag = getPriorityFlag(todo.priority);

  // Staged "checking off" sequence — identical to the home/scheduled row.
  const [completePhase, setCompletePhase] = useState<
    "checked" | "struck" | "removing" | null
  >(null);
  const completeTimers = useRef<number[]>([]);
  const completing = completePhase !== null;

  // Mobile swipe-to-reveal Edit + Delete — mirrors the home row exactly (same
  // 140px slide distance, same pills) so the calendar matches it.
  const ACTIONS_WIDTH = 140;
  const [swipeX, setSwipeX] = useState(0);
  const [swiping, setSwiping] = useState(false);
  const swipeTouch = useRef<
    { x: number; y: number; startX: number; axis: "x" | "y" | null } | null
  >(null);
  const closeSwipe = () => setSwipeX(0);

  const setCombinedRef = (node: HTMLElement | null) => {
    setItemElement(node);
    setNodeRef(node);
  };

  useEffect(() => {
    const timers = completeTimers.current;
    return () => {
      timers.forEach((id) => window.clearTimeout(id));
    };
  }, []);

  // Close this row's swipe actions when another calendar row is swiped open.
  useEffect(() => {
    const onOpen = (event: Event) => {
      const id = (event as CustomEvent<string>).detail;
      if (id !== todo.id) setSwipeX(0);
    };
    window.addEventListener("tday-calendar-swipe-open", onOpen as EventListener);
    return () =>
      window.removeEventListener("tday-calendar-swipe-open", onOpen as EventListener);
  }, [todo.id]);

  useEffect(() => {
    if (!highlighted || !itemElement) return;
    itemElement.scrollIntoView({ behavior: "smooth", block: "center" });
  }, [highlighted, itemElement]);

  const completeTask = () => {
    if (todo.instanceDate) {
      mutateInstanceComplete({ todoItem: todo });
      return;
    }
    mutateComplete({ todoItem: todo });
  };

  const handleToggleComplete = () => {
    if (todo.completed) {
      completeTask();
      return;
    }
    if (completing) return;
    setCompletePhase("checked"); // 1. green tick + pop
    completeTimers.current.push(
      window.setTimeout(() => setCompletePhase("struck"), 280), // 2. strike the title
      window.setTimeout(() => setCompletePhase("removing"), 620), // 3. start fading
      window.setTimeout(() => completeTask(), 960), // 4. remove from cache
    );
  };

  const requestDelete = () => {
    if (todo.rrule) {
      setDeleteAllDialogOpen(true);
    } else {
      setDeleteDialogOpen(true);
    }
  };

  const handleTouchStart = (event: ReactTouchEvent) => {
    const touch = event.touches[0];
    swipeTouch.current = { x: touch.clientX, y: touch.clientY, startX: swipeX, axis: null };
    setSwiping(true);
  };
  const handleTouchMove = (event: ReactTouchEvent) => {
    const data = swipeTouch.current;
    if (!data) return;
    const touch = event.touches[0];
    const dx = touch.clientX - data.x;
    const dy = touch.clientY - data.y;
    if (data.axis === null && (Math.abs(dx) > 8 || Math.abs(dy) > 8)) {
      data.axis = Math.abs(dx) > Math.abs(dy) ? "x" : "y";
      if (data.axis === "x") {
        window.dispatchEvent(
          new CustomEvent("tday-calendar-swipe-open", { detail: todo.id }),
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

  return (
    <>
      {displayForm && (
        <EditCalendarFormContainer
          todo={todo}
          displayForm={displayForm}
          setDisplayForm={setDisplayForm}
        />
      )}
      {/* Layout mirrors the home/scheduled row (TodoItemCard): flat transparent
          row, swipe-to-reveal Edit/Delete on mobile, hover actions on desktop,
          flag + list dot right-aligned. Drag-to-reschedule + the recurring delete
          dialogs stay calendar-specific. */}
      <div
        ref={setCombinedRef}
        {...attributes}
        {...listeners}
        style={
          completePhase === "removing"
            ? { opacity: 0, transition: "opacity 300ms ease" }
            : undefined
        }
        className={cn(
          "group relative max-w-full overflow-hidden sm:overflow-visible",
          isDragging && "opacity-70",
        )}
      >
        {/* Mobile: Edit + Delete revealed behind the row by a left swipe. */}
        <div
          className="absolute inset-y-0 right-0 z-0 flex items-center gap-3 pr-3 sm:hidden"
          style={{ opacity: Math.min(1, Math.abs(swipeX) / ACTIONS_WIDTH) }}
        >
          <button
            type="button"
            aria-label={todayDict("menu.edit")}
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
            <span className="text-[11px] font-bold text-muted-foreground">{todayDict("menu.edit")}</span>
          </button>
          <button
            type="button"
            aria-label={todayDict("menu.delete")}
            onPointerDown={(e) => e.stopPropagation()}
            onMouseDown={(e) => e.stopPropagation()}
            onTouchStart={(e) => e.stopPropagation()}
            onClick={() => {
              requestDelete();
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
            <span className="text-[11px] font-bold text-muted-foreground">{todayDict("menu.delete")}</span>
          </button>
        </div>

        {/* Foreground row — slides left on swipe to reveal the actions. */}
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
          className={cn(
            "relative z-10 flex items-center justify-between gap-3 px-1 py-2.5",
            "sm:cursor-grab sm:rounded-lg sm:active:cursor-grabbing sm:hover:bg-muted/40",
            highlighted && "rounded-lg ring-2 ring-accent/25 sm:bg-accent/5 sm:ring-0",
          )}
        >
          <div
            className={cn(
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
                complete={todo.completed}
                onChange={handleToggleComplete}
                checked={todo.completed || completing}
                variant={todo.rrule ? "repeat" : "outline-solid"}
              />
            </div>

            <div className="max-w-full">
              <div className="mb-1.5 flex items-center gap-1.5">
                <p
                  className={cn(
                    "select-none text-[0.98rem] font-black leading-5 text-foreground transition-colors duration-300",
                    (completePhase === "struck" || completePhase === "removing") &&
                      "text-muted-foreground line-through",
                  )}
                >
                  {todo.title}
                </p>
              </div>
              {todo.description && (
                <pre className="w-48 whitespace-pre-wrap pb-2 text-xs font-extrabold leading-4 text-muted-foreground sm:w-full">
                  {todo.description}
                </pre>
              )}
              <div className="flex flex-wrap items-center justify-start gap-2 text-xs font-black">
                <p className="font-bold text-muted-foreground">
                  {`Due ${format(todo.due, "h:mm a", { locale: activeDfLocale() })}`}
                </p>
              </div>
            </div>
          </div>

          <div className="relative flex shrink-0 items-center gap-2 pr-1 sm:pr-0">
            {/* Priority flag + list, right-aligned (native layout). Mobile shows
                just the list dot; desktop shows the full name pill and fades the
                meta out on hover to reveal edit/delete. */}
            <div
              className={cn(
                "flex items-center gap-2 transition-opacity",
                showHandle && "sm:opacity-0",
              )}
            >
              {todo.listID && (
                <>
                  <ListDot id={todo.listID} className="h-4 w-4 sm:hidden" />
                  <span className="hidden items-center gap-1 rounded-full border border-border/70 bg-muted/70 px-2 py-[0.2rem] text-xs font-black text-foreground/80 sm:flex">
                    <ListDot id={todo.listID} className="shrink-0 text-sm" />
                    <span className="max-w-24 truncate md:max-w-52 lg:max-w-none">
                      {listName}
                    </span>
                  </span>
                </>
              )}
              {priorityFlag && (
                <Flag
                  className={cn("h-4 w-4 shrink-0 sm:h-3.5 sm:w-3.5", priorityFlag.className)}
                  aria-label={priorityFlag.label}
                />
              )}
            </div>

            <div
              className={cn(
                "absolute right-0 top-1/2 hidden -translate-y-1/2 transition-opacity sm:block",
                showHandle ? "sm:opacity-100" : "sm:pointer-events-none sm:opacity-0",
              )}
            >
              <TaskActionButtons
                onEdit={() => setDisplayForm(true)}
                onDelete={requestDelete}
                editLabel={todayDict("menu.edit")}
                deleteLabel={todayDict("menu.delete")}
              />
            </div>
          </div>
        </div>
      </div>

      <Suspense fallback={null}>
        <ConfirmDelete
          todo={todo}
          deleteDialogOpen={deleteDialogOpen}
          setDeleteDialogOpen={setDeleteDialogOpen}
        />
      </Suspense>
      <Suspense fallback={null}>
        <ConfirmDeleteAll
          todo={todo}
          deleteAllDialogOpen={deleteAllDialogOpen}
          setDeleteAllDialogOpen={setDeleteAllDialogOpen}
        />
      </Suspense>
    </>
  );
}

function CalendarTodayButton({
  disabled,
  onClick,
}: {
  disabled: boolean;
  onClick: () => void;
}) {
  const { t: appDict } = useTranslation("app");

  return (
    <button
      type="button"
      aria-label={appDict("today")}
      disabled={disabled}
      onClick={onClick}
      className={cn(
        "flex h-14 shrink-0 items-center justify-center rounded-full border border-white/70 bg-card/90 px-5 text-sm font-black text-accent shadow-[0_12px_28px_-22px_hsl(var(--shadow)/0.55)] transition-all duration-200 dark:border-white/10 sm:px-6",
        "hover:-translate-y-0.5 hover:bg-card hover:shadow-[0_12px_32px_-22px_hsl(var(--shadow)/0.5)]",
        "disabled:cursor-default disabled:opacity-40 disabled:hover:translate-y-0 disabled:hover:bg-card/90 disabled:hover:shadow-[0_12px_28px_-22px_hsl(var(--shadow)/0.55)]",
      )}
    >
      {appDict("today")}
    </button>
  );
}

export default function CalendarClient() {
  const { t: sidebarDict } = useTranslation("sidebar");
  const { t: appDict } = useTranslation("app");
  const [mounted, setMounted] = useState(false);
  const [calendarRange, setCalendarRange] = useDateRange();
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [selectDateRange, setSelectDateRange] = useState<{
    start: Date;
    end: Date;
  } | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const { todos: calendarTodos, todoLoading: calendarTodosLoading } = useCalendarTodo(calendarRange);
  const { todos: allTimelineTodos } = useTodoTimeline();
  const { listMetaData } = useListMetaData()
  const [selectedDate, setSelectedDate] = useState<Date>(new Date());
  const [highlightedTaskId, setHighlightedTaskId] = useState<string | null>(null);
  const highlightTimer = useRef<number | null>(null);
  const [view, setView] = useState<CalendarViewMode>("month");
  const [slideDirection, setSlideDirection] = useState<SlideDirection | null>(null);
  const [animKey, setAnimKey] = useState(0);
  const selectedDateRef = useRef<Date>(selectedDate);
  const viewRef = useRef<CalendarViewMode>(view);
  selectedDateRef.current = selectedDate;
  viewRef.current = view;
  const minimumMonth = useMemo(() => startOfMonth(new Date()), []);

  const { editCalendarTodo } = useEditCalendarTodo();
  const { timeZone } = useUserTimezone();
  const [activeTodo, setActiveTodo] = useState<TodoItemType | null>(null);
  const [pendingReschedule, setPendingReschedule] = useState<PendingReschedule | null>(null);

  // Match TodoGroup's sensors so long-press-to-drag feels identical app-wide.
  // MouseSensor gets a distance threshold so a plain click on the drag handle
  // still selects/edits instead of starting a drag.
  const sensors = useSensors(
    useSensor(MouseSensor, { activationConstraint: { distance: 5 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 250, tolerance: 5 } }),
    useSensor(KeyboardSensor),
  );

  const handleDragStart = useCallback((event: DragStartEvent) => {
    setActiveTodo((event.active.data.current?.todo as TodoItemType | undefined) ?? null);
  }, []);

  const handleDragEnd = useCallback(
    (event: DragEndEvent) => {
      const { active, over } = event;
      setActiveTodo(null);
      if (!over) return;

      const todo = active.data.current?.todo as TodoItemType | undefined;
      const data = over.data.current as { dayKey: string } | undefined;
      if (!todo || !data) return;

      const targetDayKey = data.dayKey;
      // Compare in the same local-tz key space the droppable cells use; the cell
      // and the task's due day must match exactly for this to be a no-op.
      if (dayKey(todo.due) === targetDayKey) return;

      // moveTodoToDay preserves time-of-day in the user's configured timezone.
      const nextRange = moveTodoToDay(todo, targetDayKey, timeZone);

      if (todo.rrule) {
        // Recurring task: defer to the "this occurrence / all occurrences" prompt.
        setPendingReschedule({
          rescheduled: { ...todo, ...nextRange },
          originalDueIso: todo.due.toISOString(),
          rruleChecksum: todo.rrule,
        });
        return;
      }

      editCalendarTodo({
        ...(todo as TodoItemTypeWithDateChecksum),
        ...nextRange,
        dateRangeChecksum: todo.due.toISOString(),
        rruleChecksum: todo.rrule,
      });
    },
    [editCalendarTodo, timeZone],
  );

  const rangeAnchor = useMemo(() => {
    if (view === "month") return startOfMonth(selectedDate).getTime();
    if (view === "week") return startOfWeek(selectedDate).getTime();
    return startOfDay(selectedDate).getTime();
  }, [view, selectedDate]);

  useEffect(() => {
    setMounted(true);
  }, []);

  useEffect(() => {
    const dateForRange =
      view === "month"
        ? startOfMonth(selectedDate)
        : view === "week"
          ? startOfWeek(selectedDate)
          : selectedDate;
    setCalendarRange(calendarRangeFor(dateForRange, view));
  }, [rangeAnchor, selectedDate, setCalendarRange, view]);

  // Search is a broad jump-to-task affordance (not a calendar-grid filter): match any
  // active, not-overdue task across all dates; selecting one jumps the calendar to that
  // date and highlights it. The calendar grid keeps showing every task.
  const searchResults = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    if (!query) return [];
    const now = new Date();
    return allTimelineTodos
      .filter((todo) => !todo.completed && todo.due >= now)
      .filter((todo) => {
        const title = todo.title.toLowerCase();
        const description = (todo.description || "").toLowerCase();
        const listId = todo.listID ?? "";
        const listName = (listMetaData[listId]?.name || "").toLowerCase();
        return title.includes(query) || description.includes(query) || listName.includes(query);
      })
      .sort((a, b) => a.due.getTime() - b.due.getTime())
      .slice(0, 8)
      .map((todo) => ({
        id: todo.id,
        title: todo.title,
        subtitle: format(todo.due, "EEE, MMM d • h:mm a", { locale: activeDfLocale() }),
      }));
  }, [searchQuery, allTimelineTodos, listMetaData]);

  useEffect(() => {
    return () => {
      if (highlightTimer.current) window.clearTimeout(highlightTimer.current);
    };
  }, []);

  const tasksByDay = useMemo(() => {
    const grouped = new Map<string, TodoItemType[]>();
    calendarTodos.forEach((todo) => {
      const key = dayKey(todo.due);
      const items = grouped.get(key) ?? [];
      items.push(todo);
      grouped.set(key, items);
    });
    grouped.forEach((items) => {
      items.sort((a, b) => a.due.getTime() - b.due.getTime());
    });
    return grouped;
  }, [calendarTodos]);

  const selectedDayTasks = tasksByDay.get(dayKey(selectedDate)) ?? [];

  const openCreateForSelectedDate = useCallback(() => {
    setSelectDateRange({
      start: startOfDay(selectedDate),
      end: endOfDay(selectedDate),
    });
    setShowCreateForm(true);
  }, [selectedDate]);

  useRegisterCalendarCreateAction(openCreateForSelectedDate);

  const selectDate = useCallback((date: Date) => {
    if (!canNavigateTo(date, minimumMonth)) return;
    if (isSameDay(date, selectedDateRef.current)) return;
    setSelectedDate(date);
  }, [minimumMonth]);

  const animateToDate = useCallback((date: Date, direction: SlideDirection) => {
    if (!canNavigateTo(date, minimumMonth)) return;
    if (isSameDay(date, selectedDateRef.current)) return;
    setSlideDirection(direction);
    setAnimKey((key) => key + 1);
    setSelectedDate(date);
  }, [minimumMonth]);

  const handleSelectSearchResult = useCallback(
    (id: string) => {
      const todo = allTimelineTodos.find((t) => t.id === id);
      if (!todo) return;
      setSearchQuery("");
      selectDate(todo.due);
      setHighlightedTaskId(id);
      if (highlightTimer.current) window.clearTimeout(highlightTimer.current);
      highlightTimer.current = window.setTimeout(() => setHighlightedTaskId(null), 2600);
    },
    [allTimelineTodos, selectDate],
  );

  const navigatePeriod = useCallback((offset: -1 | 1) => {
    const currentDate = selectedDateRef.current;
    const currentView = viewRef.current;
    const nextDate = periodDate(currentDate, currentView, offset);
    animateToDate(nextDate, offset > 0 ? "right" : "left");
  }, [animateToDate]);

  const jumpToToday = useCallback(() => {
    const today = new Date();
    if (isSameDay(today, selectedDateRef.current)) return;
    const direction = today < selectedDateRef.current ? "left" : "right";
    animateToDate(today, direction);
  }, [animateToDate]);

  const changeView = useCallback((nextView: CalendarViewMode) => {
    if (nextView === viewRef.current) return;
    const currentIndex = viewOptions.indexOf(viewRef.current);
    const nextIndex = viewOptions.indexOf(nextView);
    setView(nextView);
    setSlideDirection(nextIndex > currentIndex ? "right" : "left");
    setAnimKey((key) => key + 1);
  }, []);

  useEffect(() => {
    const handler = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement;
      if (
        target?.isContentEditable ||
        ["INPUT", "TEXTAREA", "SELECT", "BUTTON"].includes(target.tagName)
      ) {
        return;
      }

      const key = event.key.toLowerCase();
      if (!["arrowleft", "arrowright", "t", "1", "2", "3", "n"].includes(key)) {
        return;
      }

      event.preventDefault();
      if (key === "arrowleft") navigatePeriod(-1);
      if (key === "arrowright") navigatePeriod(1);
      if (key === "t") jumpToToday();
      if (key === "n") openCreateForSelectedDate();
      if (key === "1") changeView("month");
      if (key === "2") changeView("week");
      if (key === "3") changeView("day");
    };

    document.addEventListener("keydown", handler, true);
    return () => document.removeEventListener("keydown", handler, true);
  }, [changeView, jumpToToday, navigatePeriod, openCreateForSelectedDate]);

  const canGoPrevious = canNavigateTo(periodDate(selectedDate, view, -1), minimumMonth);

  const todayAction = (
    <CalendarTodayButton
      disabled={isToday(selectedDate)}
      onClick={jumpToToday}
    />
  );

  if (!mounted) {
    return (
      <div className="flex h-full w-full flex-col">
        <header className="sticky top-0 z-40 flex w-full items-center justify-between gap-2.5 bg-background pt-[calc(0.5rem+env(safe-area-inset-top))] pb-1.5 lg:static lg:bg-transparent lg:pt-2 lg:pb-2">
          <div aria-hidden className="pointer-events-none absolute inset-x-0 bottom-full h-screen bg-background lg:hidden" />
          <NativeAppBrandButton className="min-w-0 max-w-[58%] sm:max-w-none" />
          <div className="flex shrink-0 items-center gap-2.5">{todayAction}</div>
        </header>
        <div className="flex flex-1 items-center justify-center">
          <Spinner className="h-14 w-14" />
        </div>
      </div>
    );
  }

  return (
    <div className="flex min-h-full flex-col">
      <header className="sticky top-0 z-40 flex w-full items-center justify-between gap-2.5 bg-background pt-[calc(0.5rem+env(safe-area-inset-top))] pb-1.5 lg:static lg:bg-transparent lg:pt-2 lg:pb-2">
        <div aria-hidden className="pointer-events-none absolute inset-x-0 bottom-full h-screen bg-background lg:hidden" />
        <NativeAppBrandButton className="min-w-0 max-w-[58%] sm:max-w-none" />
        <div className="flex shrink-0 items-center gap-2.5">{todayAction}</div>
      </header>
      <NativePageTitle
        title={sidebarDict("calendar")}
        accentColor={nativeScreenAccentColors.calendar}
        icon={CalendarDays}
      />
      <DndContext
        sensors={sensors}
        onDragStart={handleDragStart}
        onDragEnd={handleDragEnd}
        onDragCancel={() => setActiveTodo(null)}
      >
      <div className="relative flex w-full flex-1 flex-col gap-4 sm:gap-5">
        {calendarTodosLoading && (
          <div className="pointer-events-none absolute right-0 top-2 z-10 rounded-full border border-white/70 bg-card/90 p-2 shadow-sm dark:border-white/10">
            <Spinner className="h-5 w-5" />
          </div>
        )}
        {showCreateForm && selectDateRange && (
          <CreateCalendarFormContainer
            start={selectDateRange.start}
            end={selectDateRange.end}
            displayForm={showCreateForm}
            setDisplayForm={setShowCreateForm}
          />
        )}

        <CalendarViewSlider view={view} onViewChange={changeView} />
        <CalendarModeCard
          view={view}
          selectedDate={selectedDate}
          tasksByDay={tasksByDay}
          slideDirection={slideDirection}
          animationKey={animKey}
          canGoPrevious={canGoPrevious}
          onNavigate={navigatePeriod}
          onSelectDate={selectDate}
        />

        <section className="pb-4">
          <div className="mb-3">
            <h2
              className="text-xl font-black leading-tight sm:text-[1.35rem]"
              style={{ color: nativeScreenAccentColors.calendar }}
            >
              {appDict("tasksDueOn", {
                date: format(selectedDate, "EEE, MMM d", { locale: activeDfLocale() }),
              })}
            </h2>
            {selectedDayTasks.length > 0 && (
              <p className="mt-1 text-sm font-extrabold text-muted-foreground">
                {appDict("taskCount", { count: selectedDayTasks.length })}
              </p>
            )}
          </div>

          {selectedDayTasks.length > 0 ? (
            <div className="space-y-0">
              {selectedDayTasks.map((todo) => (
                <CalendarTaskRow
                  key={todo.id}
                  todo={todo}
                  listName={todo.listID ? listMetaData[todo.listID]?.name : undefined}
                  highlighted={highlightedTaskId === todo.id}
                />
              ))}
            </div>
          ) : (
            <div className="rounded-[22px] border border-dashed border-white/70 bg-card/60 px-5 py-8 text-center shadow-[0_12px_30px_-28px_hsl(var(--shadow)/0.45)] dark:border-white/10">
              <p className="text-base font-black text-foreground">{appDict("noTasksDueThisDay")}</p>
              <p className="mt-1 text-sm font-extrabold text-muted-foreground">
                {appDict("selectAnotherDate")}
              </p>
            </div>
          )}
        </section>
      </div>
        <DragOverlay dropAnimation={null}>
          {activeTodo ? (
            <div className="pointer-events-none w-[min(20rem,80vw)] rounded-[20px] border border-white/70 bg-card px-4 py-3 shadow-[0_24px_48px_-20px_hsl(var(--shadow)/0.6)] dark:border-white/10">
              <p className="line-clamp-1 text-[0.98rem] font-black leading-5 text-foreground">
                {activeTodo.title}
              </p>
              <span className="mt-1 inline-flex rounded-full border border-border/70 bg-muted/70 px-2 py-[0.2rem] text-xs font-black text-foreground/80">
                {format(activeTodo.due, "h:mm a", { locale: activeDfLocale() })}
              </span>
            </div>
          ) : null}
        </DragOverlay>
      </DndContext>
      {pendingReschedule && (
        <ConfirmRescheduleRecurring
          pending={pendingReschedule}
          open={pendingReschedule !== null}
          onClose={() => setPendingReschedule(null)}
        />
      )}
    </div>
  );
}
