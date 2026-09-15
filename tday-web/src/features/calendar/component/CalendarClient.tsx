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
import { useCalendarPagerSwipe } from "../lib/useCalendarPagerSwipe";
import { useSwipeRow } from "@/hooks/useSwipeRow";
import { shouldCloseSwipeRow } from "@/lib/swipeGesture";
import { useCalendarTodo } from "../query/get-calendar-todo";
import {
  lazy,
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
import { scrollIntoView } from "@/lib/scroll";
import type { TodoItemTypeWithDateChecksum } from "@/lib/todo/patch-todo";
import AnimatedHeight from "@/components/ui/AnimatedHeight";
import {
  DRAG_LIFT_CLASS,
  DRAG_VACATED_TRANSITION,
  dragOverlayDropAnimation,
} from "@/lib/dragLiftMotion";
import { useNavigationRefusal } from "../lib/useNavigationRefusal";
import ConfirmPlaceholder from "./LoadingPlaceholders/ConfirmPlaceholder";
import { useModalPresence } from "@/components/ui/Modal";
import { useDrawerPresence } from "@/components/ui/drawer";
import ConfirmRescheduleRecurring, {
  type PendingReschedule,
} from "./ConfirmationModals/ConfirmRescheduleRecurring";
import { useListMetaData } from "@/components/Sidebar/List/query/get-list-meta";
import CreateCalendarFormContainer from "./CalendarForm/CreateFormContainer";
import NativePageHeader, {
  NativePageBackButton,
  nativePageBarClassName,
  useNativePageBarSlots,
} from "@/components/app/NativePageHeader";
import MobileSearchHeader from "@/components/ui/MobileSearchHeader";
import { nativeScreenAccentColors } from "@/components/app/nativeScreenTheme";
import { cn } from "@/lib/utils";
import { useTranslation } from "react-i18next";
import { useRegisterCalendarCreateAction } from "@/features/calendar/context/CalendarCreateActionContext";
import TodoCheckbox from "@/components/ui/TodoCheckbox";
import { TaskActionButtons } from "@/components/ui/TaskActionButtons";
import ListDot from "@/components/ListDot";
import EditCalendarFormContainer from "./CalendarForm/EditFormContainer";
import { useCompleteCalendarTodo } from "../query/complete-calendar-todo";
import { useCompleteCalendarTodoInstance } from "../query/complete-calendar-todo-instance";
import { CalendarDays, Check, ChevronLeft, ChevronRight, Copy, Flag, GripVertical, Loader2, SquarePen, Trash } from "lucide-react";
import { isToday } from "date-fns";
import { getPriorityFlag } from "@/lib/priority";
import i18n from "@/i18n";
import { getDateFnsLocale } from "@/lib/date/dateFnsLocale";
import { flattenNotesToPlainText } from "@/lib/richNotes";
import {
  TASK_COMPLETION_CHECK_TO_STRIKE_MS,
  TASK_COMPLETION_REMOVING_TRANSITION,
  TASK_COMPLETION_STRIKE_TO_FADE_MS,
  TASK_COMPLETION_TOTAL_MS,
} from "@/lib/taskCompletionTiming";
import { usePrefersReducedMotion } from "@/lib/prefersReducedMotion";
import { buildTaskShareText } from "@/lib/listShareText";
import { useToast } from "@/hooks/use-toast";
import { SWIPE_COPY_COLOR, SWIPE_DELETE_COLOR, SWIPE_EDIT_COLOR } from "@/lib/swipeActionColors";

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

/**
 * Exported for `calendar-pager-height.test.tsx`, which asserts the thumb's rung beside the
 * slide's and the box's. The rung is the whole point of the control and nothing else can hold
 * it: the budget counter only forbids a NUMBER here, so an edit back to `duration-enter` would
 * re-open the 20ms gap below and leave every guardrail green. The argument in the comment
 * inside is exactly what a later rename would erase without noticing.
 */
export function CalendarViewSlider({
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
      {/* Emphasis, and the thumb is the one site in this file where the rung
          changes something. It travels a full segment on every view change,
          which is rule 2's own case — but the reason it matters is that the
          journey is not the only thing the tap starts: `changeView` also bumps
          `animKey` and sets `slideDirection`, so the grid below plays
          `cal-native-slide-from-*`, and `calendar-styles.css` runs both of
          those on `--tday-duration-emphasis`. The 300 meant the control that
          picks a view and the view it picked stopped 20ms apart, in the same
          gesture, every time. One page turn, one clock — the thumb included. */}
      <div
        className="absolute bottom-1.5 left-1.5 top-1.5 rounded-[20px] bg-card shadow-[0_10px_24px_-18px_hsl(var(--shadow)/0.7)] transition-transform duration-emphasis ease-out"
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
            "relative z-10 flex h-12 flex-1 items-center justify-center rounded-[20px] px-3 text-sm font-black capitalize transition-colors duration-enter",
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
      className="flex h-10 w-10 items-center justify-center rounded-full border border-white/70 bg-card/90 text-muted-foreground shadow-sm transition-all duration-enter hover:bg-card hover:text-foreground disabled:cursor-not-allowed disabled:opacity-35 dark:border-white/10 sm:h-11 sm:w-11"
    >
      <Icon className="h-5 w-5 stroke-[2.5]" />
    </button>
  );
}

export function CalendarModeCard({
  view,
  selectedDate,
  tasksByDay,
  slideDirection,
  animationKey,
  canGoPrevious,
  refusedBack,
  onNavigate,
  onSelectDate,
}: {
  view: CalendarViewMode;
  selectedDate: Date;
  tasksByDay: Map<string, TodoItemType[]>;
  slideDirection: SlideDirection | null;
  animationKey: number;
  canGoPrevious: boolean;
  /** A back navigation the floor turned down, being answered right now. */
  refusedBack: boolean;
  onNavigate: (offset: -1 | 1) => void;
  onSelectDate: (date: Date) => void;
}) {
  // `canGoPrevious` reaches the gesture as well as the chevron, and for the same
  // rule: the page behind the current month does not exist. The chevron says so
  // by being `disabled`; the gesture says so by giving under the finger and not
  // travelling far enough to look like a page about to turn.
  const { trackRef, swipeHandlers } = useCalendarPagerSwipe(
    swipeThreshold,
    onNavigate,
    canGoPrevious,
  );

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
      <div className="mb-3 flex items-center gap-2 sm:gap-3">
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

      {/* Only the pager is inside the box, which is what keeps the header
          anchored while the card resizes: the month title and the chevrons sit
          above it and never move. The card's height is whatever this pager
          currently needs, and the four things it can hold are four different
          heights — a 35-day month, a 42-day month, a week strip, a day
          summary — so every page change and every view change used to resize
          the card in the frame the slide began, under content that was still
          travelling. `AnimatedHeight` rather than a height animator of this
          screen's own: it already measures with a `ResizeObserver`, which is
          the only thing that sees all three ways this content changes size
          (the swapped page, a locale whose weekday labels wrap, a font that
          finally loads), and it is already on the rung a change of size takes,
          which is the rung the slide beside it now runs on too.

          That box clips, and nothing on this path clipped before it, so the
          bleed: `-mx-4 px-4` walks the clip edges out to the card's padding
          edge and puts the content back exactly where it was, which leaves the
          sides 16px of room for what day cells paint outside themselves
          without taking a pixel off the grid. Taking it off the grid would be
          the wrong trade twice over — a month cell is a fixed `w-[2.9rem]` in
          a seventh of the card, so a narrower card is one that hangs FURTHER
          past its last column, not less. */}
      {/* A wrapper that exists to hold one animation. The refusal cannot live
          on the pager below, which already owns an `animation` for the slide
          and would replay it on the way back out (`calendar-styles.css` argues
          that where the rule is), and it cannot live on the card above, which
          would tug the month title and the chevrons along with it and read as
          the card coming loose rather than as the page declining to turn. */}
      <div className={cn(refusedBack && "cal-native-page-refused")}>
        <AnimatedHeight className="-mx-4 px-4 sm:-mx-5 sm:px-5">
          {/* A header, sitting where the headers sit: outside the pager that
              slides and outside the track the finger moves. AGENTS.md's
              Calendar UX Contract asks for exactly this — "in month view, the
              month title and weekday row should not slide with the date grid" —
              and the labels are the reason it is right rather than merely
              asked for: they come from today and the locale, never from the
              selected date, so they are the same seven letters on every page
              this card can draw. Inside the height box all the same, because
              the row comes and goes with the view and a row that left the
              measured content would take its height out of the card in one
              frame while the rest of the change eased. Which leaves it inside
              the refusal wrapper too, so a declined back swipe nudges it along
              with everything else by 8px: kept deliberately, because that
              answer is the calendar body saying no as one thing, and a header
              held still inside a body that moved would read as the body coming
              loose rather than as the page declining. */}
          {view === "month" && <MonthWeekdayRow />}
          <div
            key={animationKey}
            className={cn(
              // Top and bottom cannot be bought by bleeding the way the sides
              // are: the box is sized to this content, so vertical padding on the
              // box would come straight out of the height the observer measured.
              // 4px above for the drag-over ring, which reaches that far out on
              // every side (`ring-2` plus `ring-offset-2`) and in week view sits
              // flush against the top of the box; 6px below for the selected
              // day's glow, the deeper of the two down there (12px down, 24px of
              // blur, 18px pulled back in). The 4px comes off the header's margin
              // above rather than being added, so the gap the eye sees is the one
              // that was always there.
              "touch-pan-y pt-1 pb-1.5",
              slideDirection === "left" && "cal-native-slide-from-left",
              slideDirection === "right" && "cal-native-slide-from-right",
            )}
            {...swipeHandlers}
          >
            {/* The element the finger actually moves, and a child of the one
                that slides for a reason that is structural rather than tidy —
                the same rule the refusal wrapper above is built on. A filling
                CSS animation outranks an inline style, so a page that arrived
                on `cal-native-slide-from-*` holds its own `transform` at
                `translateX(0)` for as long as it lives, and a drag written
                there would be ignored on every page but the very first one the
                card ever drew. One element, one owner of `transform`. */}
            <div ref={trackRef}>
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
          </div>
        </AnimatedHeight>
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

/**
 * S M T W T F S, and nothing that belongs to a page.
 *
 * Rendered by the card rather than by the grid because it is not part of one:
 * the labels are derived from today and the active locale, so every page the
 * card can turn to draws the same seven letters. Sliding them said nothing and
 * then said it again on the way back — 17px of it on a page turn, and up to
 * 96px of it under a thumb once the grid started tracking the finger, which is
 * where a pointless header became a contract breach.
 *
 * The spacing is the grid's old `space-y-3` rebuilt from the two sides that now
 * own it: 4px of the 16px above comes from this row because the pager used to
 * contribute it, and 4px of the 12px below still comes from the pager's own
 * `pt-1`, so the card draws to the same pixels it did before the row moved.
 */
function MonthWeekdayRow() {
  const weekdayLabels = eachDayOfInterval({
    start: startOfWeek(new Date()),
    end: endOfWeek(new Date()),
  }).map((date) => format(date, "EEEEE", { locale: activeDfLocale() }));

  return (
    <div className="mb-2 grid grid-cols-7 pt-1">
      {weekdayLabels.map((label, index) => (
        <div
          key={`${label}-${index}`}
          className="text-center text-xs font-black uppercase text-muted-foreground/55"
        >
          {label}
        </div>
      ))}
    </div>
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

  return (
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
              "mx-auto flex h-[3.1rem] w-[2.9rem] flex-col items-center justify-center rounded-lg text-center transition-colors duration-enter",
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
              "flex min-h-[4.8rem] flex-col items-center justify-center rounded-[20px] border text-center transition-colors duration-enter",
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

/**
 * Exported for `calendar-row-complete-interaction.test.tsx` and for nothing else — this screen
 * renders it itself, a few hundred lines down.
 *
 * Worth the export because of what it covers. This row plays the app's staged check-off and was
 * the last one running it on a clock of its own, at 280 / 620 / 960 against everybody else's
 * 160 / 360 / 260, and nothing in the repository noticed for as long as that was true: the
 * literal counter cannot see a row retimed through named constants, and reaching the row through
 * `CalendarClient` means standing up a month grid, a drag context and four queries to press one
 * checkbox.
 */
export function CalendarTaskRow({
  todo,
  listName,
  highlighted = false,
}: {
  todo: TodoItemType;
  listName?: string;
  highlighted?: boolean;
}) {
  const [displayForm, setDisplayForm] = useState(false);
  // Mounted while the form is open AND for its exit. `{displayForm && …}` handed the form the
  // very flag it was gated on, so it was unmounted on the frame that flag went false and its
  // close animation had nowhere to play. Still lazy: the row mounts nothing until first open.
  //
  // The drawer's clock, not the modal's, even though this one flag gates both shells: the
  // modal's exit is the shorter of the two, so it finishes inside this window and then waits
  // out the rest rendering nothing, while the reverse — a drawer held for the modal's 200 ms —
  // took a sheet away mid-slide, and the confirm sheet stacked on it with it.
  const editFormPresent = useDrawerPresence(displayForm);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deleteAllDialogOpen, setDeleteAllDialogOpen] = useState(false);
  // The Suspense fallbacks below are gated on these, not on the flags themselves, for the
  // reason `useModalPresence` is exported at all: a gate on the raw flag takes the placeholder
  // away one level above the modal, and the exit the primitives declare never gets a frame.
  // The real dialogs make the same call inside themselves; the fallbacks that stand in for
  // them have to make it here, because a Suspense fallback has no inside to make it in.
  const deleteFallbackPresent = useModalPresence(deleteDialogOpen);
  const deleteAllFallbackPresent = useModalPresence(deleteAllDialogOpen);
  const [itemElement, setItemElement] = useState<HTMLElement | null>(null);
  const [showHandle, setShowHandle] = useState(false);
  const { t: todayDict } = useTranslation("today");
  const { t: appDict } = useTranslation("app");
  const { toast } = useToast();
  const { mutateComplete } = useCompleteCalendarTodo();
  const { mutateComplete: mutateInstanceComplete } = useCompleteCalendarTodoInstance();
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id: todo.id,
    data: { todo },
  });
  const priorityFlag = getPriorityFlag(todo.priority);

  // Staged "checking off" sequence — identical to the scheduled task home row, and now on the
  // same clock as well. It used to run 280 / 620 / 960 against that row's 160 / 360 / 260,
  // which is the same four beats a third slower: the same task, ticked off on two screens,
  // finishing at two different speeds.
  const [completePhase, setCompletePhase] = useState<
    "checked" | "struck" | "removing" | null
  >(null);
  const completeTimers = useRef<number[]>([]);
  const completing = completePhase !== null;
  const removing = completePhase === "removing";
  // Subscribed rather than read once: this decides what the row renders, so it has to follow a
  // preference that flips mid-session.
  const reduceMotion = usePrefersReducedMotion();

  // Mobile swipe-to-reveal Edit + Copy + Delete — mirrors the scheduled task
  // home row exactly (same slide distance, same pills) so the calendar
  // matches it.
  const ACTIONS_WIDTH = 210;
  const announceSwipeOpen = useCallback(() => {
    window.dispatchEvent(new CustomEvent("tday-calendar-swipe-open", { detail: todo.id }));
  }, [todo.id]);
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
  });

  /** Copies the task's title/notes/due/priority to the clipboard as plain text. */
  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(
        buildTaskShareText({
          todo: { title: todo.title, description: todo.description, due: todo.due, priority: todo.priority },
          lang: i18n.language,
          t: appDict,
        }),
      );
      toast({ description: appDict("taskCopied") });
    } catch {
      toast({ description: appDict("taskCopyFailed"), variant: "destructive" });
    }
  };

  const setCombinedRef = (node: HTMLElement | null) => {
    setItemElement(node);
    setNodeRef(node);
    // The node an outside tap is measured against: it wraps both the pill strip
    // and the translating foreground, which is what "outside the open row" has
    // to mean. See `useSwipeRow`.
    rowRef.current = node;
  };

  useEffect(() => {
    const timers = completeTimers.current;
    return () => {
      timers.forEach((id) => window.clearTimeout(id));
    };
  }, []);

  // Close this row's swipe actions when another calendar row is swiped open —
  // one row open at a time, claimed at the other row's axis lock rather than at
  // its commit. `dismissSwipe` because this close comes from somewhere else and
  // must refuse a row whose own finger is still on it; `shouldCloseSwipeRow` is
  // the predicate all three clients answer this with. Both are argued in
  // `useSwipeRow`.
  useEffect(() => {
    const onOpen = (event: Event) => {
      const id = (event as CustomEvent<string>).detail;
      if (shouldCloseSwipeRow(id, todo.id, swipeX !== 0)) dismissSwipe();
    };
    window.addEventListener("tday-calendar-swipe-open", onOpen as EventListener);
    return () =>
      window.removeEventListener("tday-calendar-swipe-open", onOpen as EventListener);
  }, [dismissSwipe, swipeX, todo.id]);

  useEffect(() => {
    if (!highlighted || !itemElement) return;
    scrollIntoView(itemElement, { block: "center" });
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
    const removeAt = TASK_COMPLETION_CHECK_TO_STRIKE_MS + TASK_COMPLETION_STRIKE_TO_FADE_MS;
    setCompletePhase("checked"); // 1. green tick + pop
    completeTimers.current.push(
      window.setTimeout(() => setCompletePhase("struck"), TASK_COMPLETION_CHECK_TO_STRIKE_MS), // 2. strike
      window.setTimeout(() => setCompletePhase("removing"), removeAt), // 3. the ink leaves
      // 4. gone. The last leg waits for the box to shut, and with reduce-motion on there is no
      // box shutting — the same cut the other two task rows make, argued in `taskCompletionTiming`.
      window.setTimeout(() => completeTask(), reduceMotion ? removeAt : TASK_COMPLETION_TOTAL_MS),
    );
  };

  const requestDelete = () => {
    if (todo.rrule) {
      setDeleteAllDialogOpen(true);
    } else {
      setDeleteDialogOpen(true);
    }
  };

  return (
    <>
      {editFormPresent && (
        <EditCalendarFormContainer
          todo={todo}
          displayForm={displayForm}
          setDisplayForm={setDisplayForm}
        />
      )}
      {/* Layout mirrors the scheduled task home row (TodoItemCard): flat transparent
          row, swipe-to-reveal Edit/Delete on mobile, hover actions on desktop,
          flag + list dot right-aligned. Drag-to-reschedule + the recurring delete
          dialogs stay calendar-specific. */}
      <div
        ref={setCombinedRef}
        {...attributes}
        {...listeners}
        style={
          removing
            ? {
                opacity: 0,
                gridTemplateRows: "0fr",
                transition: reduceMotion ? undefined : TASK_COMPLETION_REMOVING_TRANSITION,
              }
            : { transition: reduceMotion ? undefined : DRAG_VACATED_TRANSITION }
        }
        className={cn(
          // The 1fr track is the scheduled row's collapse (TodoItemCard), which argues the
          // trick where it lives: a height cannot be animated away from `auto`, so the box
          // shuts by closing a grid row instead. The swipe actions sit out of flow and never
          // size it. Without this the calendar row faded out and left a full-height gap for
          // the rows below to jump through.
          "group relative grid max-w-full grid-rows-[1fr] overflow-hidden sm:overflow-visible",
          // The hole the card came out of, on the lift's own rung via the style
          // above rather than cutting on the frame the press fires; both halves of
          // the pick-up are argued in `dragLiftMotion.ts`.
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
              style={{ backgroundColor: SWIPE_EDIT_COLOR }}
            >
              <SquarePen className="h-5 w-5 text-white" strokeWidth={2.2} />
            </span>
            <span className="text-[11px] font-bold text-muted-foreground">{todayDict("menu.edit")}</span>
          </button>
          <button
            type="button"
            aria-label={todayDict("menu.copy")}
            onPointerDown={(e) => e.stopPropagation()}
            onMouseDown={(e) => e.stopPropagation()}
            onTouchStart={(e) => e.stopPropagation()}
            onClick={() => {
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
            <span className="text-[11px] font-bold text-muted-foreground">{todayDict("menu.copy")}</span>
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
              style={{ backgroundColor: SWIPE_DELETE_COLOR }}
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
          {...swipeHandlers}
          style={{
            transform: `translateX(${swipeX}px)`,
            // The whole whitelist — the swipe's settle and the two spellings of the highlight
            // beside it — comes from `useSwipeRow`, because all three task rows draw the same
            // three properties and used to write out three slightly different lists saying so.
            transition: swipeTransition,
            touchAction: "pan-y",
            // Lets the grid item shrink past its own content while the track closes.
            ...(removing ? { overflow: "hidden", minHeight: 0 } : null),
          }}
          className={cn(
            "relative z-10 flex items-center justify-between gap-3 px-1 py-2.5",
            "sm:cursor-grab sm:rounded-lg sm:active:cursor-grabbing sm:hover:bg-muted/40",
            // The deep-link mark, inset so the wrapper's clip cannot eat it, and declared on both
            // sides so only its colour travels. Both halves are argued in full on the identical
            // pair in `TodoItemContainer` — this row is the same defect in a third file, not a
            // variation on it.
            "inset-ring-2",
            highlighted
              ? "rounded-lg inset-ring-accent/25 sm:bg-accent/5 sm:inset-ring-transparent"
              : "inset-ring-transparent",
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

          {/* One box for the row's content, stacked from the TOP, with the row itself
              left centred — the grip handle above is positioned against the row and
              stays on its centre. Same shape as `TodoItemContainer`. */}
          <div className="flex w-full min-w-0 items-start justify-between gap-3">
          <div className="flex min-w-0 items-start gap-3">
            {/* The control's slot is the title's line box (`leading-5` = 20 px), the
                same as in `TodoItemContainer`. This row was the last of the three still
                centring its toggle across the whole column, so a calendar task with
                notes under it had its check a full line below its own title. */}
            <div className="flex h-5 shrink-0 items-center">
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
                    "select-none text-[0.98rem] font-black leading-5 text-foreground transition-colors duration-emphasis",
                    (completePhase === "struck" || removing) &&
                      "task-strike text-muted-foreground",
                  )}
                >
                  {todo.title}
                </p>
              </div>
              {todo.description && (
                <pre
                  className={cn(
                    "w-48 whitespace-pre-wrap pb-2 text-xs font-extrabold leading-4 text-muted-foreground transition-colors duration-emphasis sm:w-full",
                    // Same switch as the title above — see TodoItemContainer.
                    (completePhase === "struck" || removing) && "task-strike",
                  )}
                >
                  {flattenNotesToPlainText(todo.description)}
                </pre>
              )}
              <div className="flex flex-wrap items-center justify-start gap-2 text-xs font-black">
                <p className="font-bold text-muted-foreground">
                  {`Due ${format(todo.due, "h:mm a", { locale: activeDfLocale() })}`}
                </p>
              </div>
            </div>
          </div>

          {/* `self-stretch` so this box still spans the row and the hover toolbar it
              positions at `top-1/2` stays centred on the ROW. See `TodoItemContainer`. */}
          <div className="relative flex shrink-0 items-start gap-2 self-stretch pr-1 sm:pr-0">
            {/* Priority flag + list, right-aligned (native layout). Mobile shows
                just the list dot; desktop shows the full name pill and fades the
                meta out on hover to reveal edit/delete. */}
            <div
              className={cn(
                // The title's own line box, the same one the check circle gets at the
                // other end of the row.
                "flex h-5 items-center gap-2 transition-opacity",
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
                onCopy={() => void handleCopy()}
                onDelete={requestDelete}
                editLabel={todayDict("menu.edit")}
                copyLabel={todayDict("menu.copy")}
                deleteLabel={todayDict("menu.delete")}
              />
            </div>
          </div>
          </div>
        </div>
      </div>

      {/* The fallback is gated, and that is the whole trick. Both boundaries
          render unconditionally — that is what starts the import when the row
          mounts instead of when the button is pressed — so a fallback that drew
          itself whenever the boundary was suspended would flash a modal over the
          calendar on first paint, once per row. Gated, it draws only for the tap
          it is answering. */}
      <Suspense
        fallback={
          deleteFallbackPresent
            ? (
              <ConfirmPlaceholder
                open={deleteDialogOpen}
                onCancel={() => setDeleteDialogOpen(false)}
              />
            )
            : null
        }
      >
        <ConfirmDelete
          todo={todo}
          deleteDialogOpen={deleteDialogOpen}
          setDeleteDialogOpen={setDeleteDialogOpen}
        />
      </Suspense>
      <Suspense
        fallback={
          deleteAllFallbackPresent
            ? (
              <ConfirmPlaceholder
                open={deleteAllDialogOpen}
                onCancel={() => setDeleteAllDialogOpen(false)}
              />
            )
            : null
        }
      >
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
        "flex h-14 shrink-0 items-center justify-center rounded-full border border-white/70 bg-card/90 px-5 text-sm font-black text-accent shadow-[0_14px_30px_-16px_hsl(var(--shadow)/0.6)] transition-all duration-enter dark:border-white/10 sm:px-6",
        "hover:-translate-y-0.5 hover:bg-card hover:shadow-[0_12px_32px_-22px_hsl(var(--shadow)/0.5)]",
        "disabled:cursor-default disabled:opacity-40 disabled:hover:translate-y-0 disabled:hover:bg-card/90 disabled:hover:shadow-[0_14px_30px_-16px_hsl(var(--shadow)/0.6)]",
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
  // Same shape as the edit form above, down to which surface's exit the window is cut to.
  const createFormPresent = useDrawerPresence(showCreateForm);
  const [selectDateRange, setSelectDateRange] = useState<{
    start: Date;
    end: Date;
  } | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const { todos: calendarTodos, todoLoading: calendarTodosLoading } = useCalendarTodo(calendarRange);
  const { listMetaData } = useListMetaData()
  const [selectedDate, setSelectedDate] = useState<Date>(new Date());
  const [highlightedTaskId, setHighlightedTaskId] = useState<string | null>(null);
  const highlightTimer = useRef<number | null>(null);
  const [view, setView] = useState<CalendarViewMode>("month");
  const [slideDirection, setSlideDirection] = useState<SlideDirection | null>(null);
  const [animKey, setAnimKey] = useState(0);
  // The floor's answer to a back navigation it turned down: the hook holds both
  // the flag the card draws from and the clock it comes down on.
  const { refusing: refusedBack, refuse: refuseNavigation } = useNavigationRefusal();
  // The subscribed shape of the preference, where the refusal above takes the
  // imperative one. The drop animation is chosen during render and has to be
  // re-chosen when the preference flips; a refusal only ever asks at the instant
  // its timer arms.
  const reduceMotion = usePrefersReducedMotion();
  const selectedDateRef = useRef<Date>(selectedDate);
  const viewRef = useRef<CalendarViewMode>(view);
  selectedDateRef.current = selectedDate;
  viewRef.current = view;
  const minimumMonth = useMemo(() => startOfMonth(new Date()), []);
  const barSlots = useNativePageBarSlots();

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

  // Search is a jump-to-task affordance (not a calendar-grid filter): selecting
  // a result jumps the calendar to that date and highlights it, and the grid
  // keeps showing every task. Scoped to what this calendar is showing — the
  // tasks in the period on screen — rather than every task there is; a wider
  // net would land on dates the user cannot see from here.
  const searchResults = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    if (!query) return [];
    return calendarTodos
      .filter((todo) => {
        const title = todo.title.toLowerCase();
        const description = flattenNotesToPlainText(todo.description).toLowerCase();
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
  }, [searchQuery, calendarTodos, listMetaData]);

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
    const end = endOfDay(selectedDate);
    end.setSeconds(0, 0);
    setSelectDateRange({
      start: startOfDay(selectedDate),
      end,
    });
    setShowCreateForm(true);
  }, [selectedDate]);

  useRegisterCalendarCreateAction(openCreateForSelectedDate);

  const selectDate = useCallback((date: Date) => {
    if (!canNavigateTo(date, minimumMonth)) return;
    if (isSameDay(date, selectedDateRef.current)) return;
    setSelectedDate(date);
  }, [minimumMonth]);

  /**
   * Turns the card to `date`, if it is allowed to go there.
   *
   * @returns Whether the page actually turned. Callers that offer the user a
   *   gesture read it: the floor rule lives here, and a second copy of it at
   *   the call site is a second copy that can drift out of step with this one.
   */
  const animateToDate = useCallback((date: Date, direction: SlideDirection) => {
    if (!canNavigateTo(date, minimumMonth)) return false;
    if (isSameDay(date, selectedDateRef.current)) return false;
    setSlideDirection(direction);
    setAnimKey((key) => key + 1);
    setSelectedDate(date);
    return true;
  }, [minimumMonth]);

  const handleSelectSearchResult = useCallback(
    (id: string) => {
      const todo = calendarTodos.find((t) => t.id === id);
      if (!todo) return;
      setSearchQuery("");
      selectDate(todo.due);
      setHighlightedTaskId(id);
      if (highlightTimer.current) window.clearTimeout(highlightTimer.current);
      highlightTimer.current = window.setTimeout(() => setHighlightedTaskId(null), 2600);
    },
    [calendarTodos, selectDate],
  );

  const navigatePeriod = useCallback((offset: -1 | 1) => {
    const currentDate = selectedDateRef.current;
    const currentView = viewRef.current;
    const nextDate = periodDate(currentDate, currentView, offset);
    if (animateToDate(nextDate, offset > 0 ? "right" : "left")) return;

    // Nothing moved, and the user asked for something. Every way of asking
    // arrives here — the swipe, the arrow keys, and the chevron that is
    // `disabled` at the floor precisely because this is where the rule is
    // enforced — so the answer is given once, at the refusal, rather than at
    // each gesture that can run into it. It travels the one way because only
    // the floor ever refuses: the calendar has no ceiling.
    refuseNavigation();
  }, [animateToDate, refuseNavigation]);

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
        {/* Loading state: the bar without the block that scrolls away, so the
            chrome does not move when the calendar mounts. Shares the header's
            class so the two cannot drift. */}
        <header className={nativePageBarClassName}>
          <div aria-hidden className="pointer-events-none absolute inset-x-0 bottom-full h-screen bg-background" />
          <div className="flex shrink-0 items-center"><NativePageBackButton /></div>
          <div className="ml-auto flex shrink-0 items-center gap-2.5">{todayAction}</div>
        </header>
        <div className="flex flex-1 items-center justify-center">
          <Loader2 className="h-14 w-14 animate-spin" />
        </div>
      </div>
    );
  }

  return (
    <div className="flex min-h-full flex-col">
      {/* The search field is this page's pinned bar, so the header below
          renders only the block that scrolls away and docks its title into
          it — the same split the custom list uses. */}
      <MobileSearchHeader
        searchQuery={searchQuery}
        onSearchChange={setSearchQuery}
        placeholder={`${appDict("searchIn")} ${sidebarDict("calendar")}...`}
        pageCollapse={{
          ...barSlots,
          title: sidebarDict("calendar"),
          accentColor: nativeScreenAccentColors.calendar,
        }}
        results={searchResults}
        onSelectResult={handleSelectSearchResult}
        trailingAction={todayAction}
      />

      <NativePageHeader
        title={sidebarDict("calendar")}
        accentColor={nativeScreenAccentColors.calendar}
        icon={CalendarDays}
        barSlots={barSlots}
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
            <Loader2 className="h-5 w-5 animate-spin" />
          </div>
        )}
        {createFormPresent && selectDateRange && (
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
          refusedBack={refusedBack}
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
        <DragOverlay dropAnimation={dragOverlayDropAnimation(reduceMotion)}>
          {/* Opaque and lifted, not faded: the 70% this card used to carry is the
              word the vacated row keeps, and a thing the finger is holding reads
              as disabled at that opacity. `DRAG_LIFT_CLASS` argues it. */}
          {activeTodo ? (
            <div
              className={cn(
                "pointer-events-none w-[min(20rem,80vw)] rounded-[20px] border border-white/70 bg-card px-4 py-3 shadow-[0_24px_48px_-20px_hsl(var(--shadow)/0.6)] dark:border-white/10",
                DRAG_LIFT_CLASS,
              )}
            >
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
      {/* Mounted unconditionally: it holds its own last payload and its own presence, so
          answering it lets the card play its exit instead of blinking out. */}
      <ConfirmRescheduleRecurring
        pending={pendingReschedule}
        open={pendingReschedule !== null}
        onClose={() => setPendingReschedule(null)}
      />
    </div>
  );
}
