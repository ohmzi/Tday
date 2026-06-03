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
import {
  lazy,
  type PointerEvent as ReactPointerEvent,
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
import MobileSearchHeader from "@/components/ui/MobileSearchHeader";
import { cn } from "@/lib/utils";
import { useTranslation } from "react-i18next";
import { useRegisterCalendarCreateAction } from "@/features/calendar/context/CalendarCreateActionContext";
import { Button } from "@/components/ui/button";
import TodoCheckbox from "@/components/ui/TodoCheckbox";
import ListDot from "@/components/ListDot";
import EditCalendarFormContainer from "./CalendarForm/EditFormContainer";
import { useCompleteCalendarTodo } from "../query/complete-calendar-todo";
import { useCompleteCalendarTodoInstance } from "../query/complete-calendar-todo-instance";
import { CalendarDays, Check, ChevronLeft, ChevronRight, GripVertical, Pen, RefreshCcw, Trash } from "lucide-react";
import { isToday } from "date-fns";

const ConfirmDelete = lazy(() => import("./ConfirmationModals/ConfirmDelete"));
const ConfirmDeleteAll = lazy(() => import("./ConfirmationModals/ConfirmDeleteAll"));

type CalendarViewMode = "month" | "week" | "day";
type SlideDirection = "left" | "right";

const viewOptions: CalendarViewMode[] = ["month", "week", "day"];
const swipeThreshold = 48;

function dayKey(date: Date) {
  return format(startOfDay(date), "yyyy-MM-dd");
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

  const title =
    view === "month"
      ? format(selectedDate, "MMMM yyyy")
      : view === "week"
        ? `${format(startOfWeek(selectedDate), "MMM d")} - ${format(endOfWeek(selectedDate), "MMM d")}`
        : format(selectedDate, "EEEE, MMM d");

  return (
    <section className="rounded-[24px] border border-white/70 bg-card/94 p-4 shadow-[0_18px_42px_-34px_hsl(var(--shadow)/0.62)] dark:border-white/10 sm:p-5">
      <div className="mb-4 flex items-center gap-2 sm:gap-3">
        <CalendarNavButton
          label="Previous"
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
          label="Next"
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
      aria-label={format(date, "PPP")}
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
  }).map((date) => format(date, "EEEEE"));

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
              <span className="text-lg font-black leading-none">{format(date, "d")}</span>
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
              {format(date, "EEE")}
            </span>
            <span className="text-xl font-black leading-tight">{format(date, "d")}</span>
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
  return (
    <div className="flex min-h-[5.2rem] items-center justify-between rounded-[22px] border border-white/60 bg-muted/45 px-5 py-4 dark:border-white/10">
      <div>
        <p className="text-sm font-black uppercase tracking-[0.18em] text-muted-foreground/70">
          {format(selectedDate, "EEEE")}
        </p>
        <p className="text-3xl font-black text-foreground">{format(selectedDate, "MMM d")}</p>
      </div>
      <div className="rounded-full bg-accent/12 px-4 py-2 text-sm font-black text-accent">
        {taskCount === 1 ? "1 task" : `${taskCount} tasks`}
      </div>
    </div>
  );
}

function CalendarTaskRow({
  todo,
  listName,
}: {
  todo: TodoItemType;
  listName?: string;
}) {
  const [displayForm, setDisplayForm] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deleteAllDialogOpen, setDeleteAllDialogOpen] = useState(false);
  const { t: appDict } = useTranslation("app");
  const { t: todayDict } = useTranslation("today");
  const { mutateComplete } = useCompleteCalendarTodo();
  const { mutateComplete: mutateInstanceComplete } = useCompleteCalendarTodoInstance();
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id: todo.id,
    data: { todo },
  });

  const completeTask = () => {
    if (todo.instanceDate) {
      mutateInstanceComplete({ todoItem: todo });
      return;
    }
    mutateComplete({ todoItem: todo });
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
      <article
        ref={setNodeRef}
        className={cn(
          "group flex items-start justify-between gap-2 rounded-[20px] border border-white/70 bg-card/92 px-2 py-3 shadow-[0_12px_30px_-28px_hsl(var(--shadow)/0.45)] transition-all duration-200 hover:-translate-y-0.5 hover:bg-card hover:shadow-[0_16px_36px_-28px_hsl(var(--shadow)/0.5)] dark:border-white/10 sm:px-3",
          isDragging && "opacity-40",
        )}
      >
        <div className="flex min-w-0 items-start gap-2 sm:gap-3">
          <button
            type="button"
            {...attributes}
            {...listeners}
            aria-label={`Drag to reschedule ${todo.title}`}
            className="mt-0.5 flex h-9 w-5 shrink-0 cursor-grab touch-none items-center justify-center rounded-md text-muted-foreground/45 transition-colors hover:text-muted-foreground active:cursor-grabbing"
          >
            <GripVertical className="h-4 w-4" />
          </button>
          <div className="pt-0.5">
            <TodoCheckbox
              icon={Check}
              priority={todo.priority}
              complete={todo.completed}
              onChange={completeTask}
              checked={todo.completed}
              variant={todo.rrule ? "repeat" : "outline-solid"}
            />
          </div>

          <button
            type="button"
            className="min-w-0 text-left"
            onClick={() => setDisplayForm(true)}
          >
            <p className="line-clamp-2 text-[0.98rem] font-black leading-5 text-foreground">
              {todo.title}
            </p>
            {todo.description && (
              <p className="mt-1 line-clamp-2 text-xs font-extrabold leading-4 text-muted-foreground">
                {todo.description}
              </p>
            )}
            <div className="mt-2 flex flex-wrap items-center gap-2 text-xs font-black text-muted-foreground">
              <span className="rounded-full border border-border/70 bg-muted/70 px-2 py-[0.2rem] text-foreground/80">
                {format(todo.due, "h:mm a")}
              </span>
              {todo.listID && (
                <span className="flex items-center gap-1 rounded-full border border-border/70 bg-muted/70 px-2 py-[0.2rem] text-foreground/80">
                  <ListDot id={todo.listID} className="h-3 w-3" />
                  <span className="max-w-32 truncate">{listName}</span>
                </span>
              )}
              {todo.rrule && (
                <span className="flex items-center gap-1 rounded-full border border-border/70 bg-muted/70 px-2 py-[0.2rem] text-foreground/80">
                  <RefreshCcw className="h-3 w-3" />
                  {appDict("repeat")}
                </span>
              )}
            </div>
          </button>
        </div>

        <div className="flex shrink-0 items-center gap-1 opacity-100 sm:opacity-0 sm:transition-opacity sm:duration-200 sm:group-hover:opacity-100">
          <Button
            type="button"
            variant="ghost"
            size="icon"
            className="h-9 w-9 rounded-full text-muted-foreground hover:bg-muted hover:text-foreground"
            aria-label={todayDict("menu.edit")}
            onClick={() => setDisplayForm(true)}
          >
            <Pen className="h-4 w-4" />
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="icon"
            className="h-9 w-9 rounded-full text-muted-foreground hover:bg-red/10 hover:text-red"
            aria-label={todayDict("menu.delete")}
            onClick={() => {
              if (todo.rrule) {
                setDeleteAllDialogOpen(true);
              } else {
                setDeleteDialogOpen(true);
              }
            }}
          >
            <Trash className="h-4 w-4" />
          </Button>
        </div>
      </article>

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
        "flex h-11 shrink-0 items-center justify-center rounded-2xl border border-white/70 bg-card/92 px-4 text-sm font-black text-accent shadow-[0_10px_30px_-24px_hsl(var(--shadow)/0.42)] transition-all duration-200 dark:border-white/10 sm:px-5",
        "hover:bg-card hover:shadow-[0_12px_32px_-22px_hsl(var(--shadow)/0.5)]",
        "disabled:cursor-default disabled:opacity-40 disabled:hover:bg-card/92 disabled:hover:shadow-[0_10px_30px_-24px_hsl(var(--shadow)/0.42)]",
      )}
    >
      {appDict("today")}
    </button>
  );
}

export default function CalendarClient() {
  const { t: sidebarDict } = useTranslation("sidebar");
  const [mounted, setMounted] = useState(false);
  const [calendarRange, setCalendarRange] = useDateRange();
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [selectDateRange, setSelectDateRange] = useState<{
    start: Date;
    end: Date;
  } | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const { todos: calendarTodos, todoLoading: calendarTodosLoading } = useCalendarTodo(calendarRange);
  const { listMetaData } = useListMetaData()
  const [selectedDate, setSelectedDate] = useState<Date>(new Date());
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

  const filteredCalendarTodos = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    if (!query) return calendarTodos;
    return calendarTodos.filter((todo) => {
      const title = todo.title.toLowerCase();
      const description = (todo.description || "").toLowerCase();
      const listId = todo.listID ?? "";
      const listName = (listMetaData[listId]?.name || "").toLowerCase();
      return title.includes(query) || description.includes(query) || listName.includes(query);
    });
  }, [calendarTodos, listMetaData, searchQuery]);

  const tasksByDay = useMemo(() => {
    const grouped = new Map<string, TodoItemType[]>();
    filteredCalendarTodos.forEach((todo) => {
      const key = dayKey(todo.due);
      const items = grouped.get(key) ?? [];
      items.push(todo);
      grouped.set(key, items);
    });
    grouped.forEach((items) => {
      items.sort((a, b) => a.due.getTime() - b.due.getTime());
    });
    return grouped;
  }, [filteredCalendarTodos]);

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
        <MobileSearchHeader
          searchQuery={searchQuery}
          onSearchChange={setSearchQuery}
          trailingAction={todayAction}
        />
        <div className="flex flex-1 items-center justify-center">
          <Spinner className="h-14 w-14" />
        </div>
      </div>
    );
  }

  return (
    <div className="flex min-h-full flex-col">
      <MobileSearchHeader
        searchQuery={searchQuery}
        onSearchChange={setSearchQuery}
        trailingAction={todayAction}
      />
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
              Tasks due {format(selectedDate, "EEE, MMM d")}
            </h2>
            {selectedDayTasks.length > 0 && (
              <p className="mt-1 text-sm font-extrabold text-muted-foreground">
                {selectedDayTasks.length === 1 ? "1 task" : `${selectedDayTasks.length} tasks`}
              </p>
            )}
          </div>

          {selectedDayTasks.length > 0 ? (
            <div className="space-y-2">
              {selectedDayTasks.map((todo) => (
                <CalendarTaskRow
                  key={todo.id}
                  todo={todo}
                  listName={todo.listID ? listMetaData[todo.listID]?.name : undefined}
                />
              ))}
            </div>
          ) : (
            <div className="rounded-[22px] border border-dashed border-white/70 bg-card/60 px-5 py-8 text-center shadow-[0_12px_30px_-28px_hsl(var(--shadow)/0.45)] dark:border-white/10">
              <p className="text-base font-black text-foreground">No tasks due this day</p>
              <p className="mt-1 text-sm font-extrabold text-muted-foreground">
                Select another date on the calendar.
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
                {format(activeTodo.due, "h:mm a")}
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
