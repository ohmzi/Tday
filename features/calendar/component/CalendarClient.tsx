"use client";
import { CalendarToolbar } from "./CalendarToolbar";
import { Calendar, dateFnsLocalizer, View } from "react-big-calendar";
import "react-big-calendar/lib/css/react-big-calendar.css";
import "../style/calendar-styles.css";
import "react-big-calendar/lib/addons/dragAndDrop/styles.css";
import useWindowSize from "@/hooks/useWindowSize";
import withDragAndDrop from "react-big-calendar/lib/addons/dragAndDrop";
import {
  format,
  parse,
  startOfWeek,
  getDay,
  startOfMonth,
  endOfMonth,
  endOfWeek,
  startOfDay,
  endOfDay,
} from "date-fns";
import { enUS } from "date-fns/locale/en-US";
import { TodoItemType } from "@/types";
import CalendarHeader, { TimeViewHeader } from "./CalendarHeader";
import { agendaComponents } from "./CalendarAgenda";
import CalendarEvent from "./CalendarEvent";
import { calendarEventPropStyles } from "../lib/calendarEventPropStyles";
import { useDateRange } from "../hooks/useDateRange";
import { useCalendarTodo } from "../query/get-calendar-todo";
import { useEditCalendarTodo } from "../query/update-calendar-todo";
import { useEditCalendarTodoInstance } from "../query/update-calendar-todo-instance";
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import Spinner from "@/components/ui/spinner";
import { subMilliseconds } from "date-fns";
import { useProjectMetaData } from "@/components/Sidebar/Project/query/get-project-meta";
import CreateCalendarFormContainer from "./CalendarForm/CreateFormContainer";
import MobileSearchHeader from "@/components/ui/MobileSearchHeader";
import { cn } from "@/lib/utils";

const locales = { "en-US": enUS };
const localizer = dateFnsLocalizer({
  format,
  parse,
  startOfWeek,
  getDay,
  locales,
});
const DnDCalendar = withDragAndDrop<TodoItemType>(Calendar);

/* ── Context + wrapper so tapping a day cell in month view drills to day view ── */
const DrillToDayContext = createContext<((date: Date) => void) | null>(null);

function MonthDateCellWrapper({
  children,
  value,
}: {
  children: React.ReactNode;
  value: Date;
}) {
  const drillToDay = useContext(DrillToDayContext);
  return (
    <div
      role="button"
      tabIndex={-1}
      style={{ flex: 1, display: "flex" }}
      onClick={() => drillToDay?.(value)}
    >
      {children}
    </div>
  );
}

export default function CalendarClient() {
  const [mounted, setMounted] = useState(false);
  const [calendarRange, setCalendarRange] = useDateRange();
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [selectDateRange, setSelectDateRange] = useState<{
    start: Date;
    end: Date;
  } | null>(null);
  const [isTouch, setIsTouch] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const { todos: calendarTodos, todoLoading: calendarTodosLoading } = useCalendarTodo(calendarRange);
  const { editCalendarTodo } = useEditCalendarTodo();
  const { editCalendarTodoInstance } = useEditCalendarTodoInstance();
  const { projectMetaData } = useProjectMetaData()

  // Filter calendar events by search query
  const filteredCalendarTodos = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    if (!query) return calendarTodos;
    return calendarTodos.filter((todo) => {
      const title = todo.title.toLowerCase();
      const description = (todo.description || "").toLowerCase();
      const tagName = (projectMetaData[todo.projectID || ""]?.name || "").toLowerCase();
      return title.includes(query) || description.includes(query) || tagName.includes(query);
    });
  }, [calendarTodos, projectMetaData, searchQuery]);

  // --- navigation & animation state ---
  const [selectedDate, setSelectedDate] = useState<Date | null>(null);
  const [view, setView] = useState<View>("month");
  const [slideDirection, setSlideDirection] = useState<"left" | "right" | null>(null);
  const [animKey, setAnimKey] = useState(0);
  const selectedDateRef = useRef<Date | null>(null);
  selectedDateRef.current = selectedDate;
  // detect touch devices (disable dnd on touch screens)
  useEffect(() => {
    const hasTouch =
      typeof window !== "undefined" &&
      (navigator.maxTouchPoints > 0 || "ontouchstart" in window) &&
      window.innerWidth <= 1024; // treat wide devices as desktop
    setIsTouch(hasTouch);
  }, []);
  // Initialize on mount
  useEffect(() => {
    setMounted(true);
    setSelectedDate(new Date());
  }, []);

  // Trigger slide animation and navigate to a new date
  const navigateTo = useCallback((newDate: Date, direction: "left" | "right") => {
    setSlideDirection(direction);
    setAnimKey((k) => k + 1);
    setSelectedDate(newDate);
  }, []);

  // Drill into day view — used by context wrapper for mobile taps
  const drillToDay = useCallback(
    (date: Date) => {
      const direction = date < (selectedDate ?? new Date()) ? "left" : "right";
      navigateTo(date, direction);
      setView("day");
      // Range will be set via updateRangeForDate after it's defined
      setCalendarRange({ start: startOfDay(date), end: endOfDay(date) });
    },
    [selectedDate, navigateTo, setCalendarRange],
  );

  // Helper function to update calendar range based on date and view
  const updateRangeForDate = useCallback((date: Date, currentView: View) => {
    if (currentView === "month") {
      setCalendarRange({
        start: startOfWeek(startOfMonth(date)),
        end: endOfWeek(endOfMonth(date)),
      });
    } else if (currentView === "week") {
      setCalendarRange({
        start: startOfWeek(date),
        end: endOfWeek(date),
      });
    } else if (currentView === "day") {
      setCalendarRange({
        start: startOfDay(date),
        end: endOfDay(date),
      });
    }
  }, [setCalendarRange]);

  useEffect(() => {
    if (!selectedDate) return;

    const handler = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement;
      if (
        target?.isContentEditable ||
        ["INPUT", "TEXTAREA"].includes(target.tagName)
      )
        return;

      const key = e.key.toLowerCase();
      e.preventDefault();

      switch (key) {
        case "arrowleft": {
          const d = selectedDateRef.current;
          if (!d) break;
          const newDate =
            view === "month"
              ? new Date(d.getFullYear(), d.getMonth() - 1, d.getDate())
              : view === "week"
                ? new Date(d.getFullYear(), d.getMonth(), d.getDate() - 7)
                : new Date(d.getFullYear(), d.getMonth(), d.getDate() - 1);
          navigateTo(newDate, "left");
          updateRangeForDate(newDate, view);
          break;
        }
        case "arrowright": {
          const d = selectedDateRef.current;
          if (!d) break;
          const newDate =
            view === "month"
              ? new Date(d.getFullYear(), d.getMonth() + 1, d.getDate())
              : view === "week"
                ? new Date(d.getFullYear(), d.getMonth(), d.getDate() + 7)
                : new Date(d.getFullYear(), d.getMonth(), d.getDate() + 1);
          navigateTo(newDate, "right");
          updateRangeForDate(newDate, view);
          break;
        }
        case "t": {
          const today = new Date();
          const d = selectedDateRef.current;
          const direction = d && today < d ? "left" : "right";
          navigateTo(today, direction);
          updateRangeForDate(today, view);
          break;
        }
        case "1":
          setView("month");
          updateRangeForDate(selectedDate, "month");
          break;
        case "2":
          setView("week");
          updateRangeForDate(selectedDate, "week");
          break;
        case "3":
          setView("day");
          updateRangeForDate(selectedDate, "day");
          break;
      }
    };

    document.addEventListener("keydown", handler, true);
    return () => document.removeEventListener("keydown", handler, true);
  }, [view, selectedDate, updateRangeForDate, navigateTo]);

  const { width } = useWindowSize();

  // Don't render calendar until mounted
  if (!mounted || !selectedDate) {
    return (
      <div className="h-full w-full flex flex-col">
        <MobileSearchHeader searchQuery={searchQuery} onSearchChange={setSearchQuery} />
        <div className="flex-1 flex items-center justify-center">
          <Spinner className="w-14 h-14" />
        </div>
      </div>
    );
  }

  return (
    <DrillToDayContext.Provider value={drillToDay}>
    <div className="h-full flex flex-col">
      {calendarTodosLoading && (
        <div className="w-full h-full bg-black/20 fixed z-100">
          <div className="fixed top-1/2 left-1/2 -translate-y-1/2 ">
            <Spinner className="h-20 w-20" />
          </div>
        </div>
      )}

      <MobileSearchHeader searchQuery={searchQuery} onSearchChange={setSearchQuery} />
      <div className="flex-1 min-h-0 flex flex-col overflow-hidden sm:py-10">
        {showCreateForm && selectDateRange && (
          <CreateCalendarFormContainer
            start={selectDateRange.start}
            end={selectDateRange.end}
            displayForm={showCreateForm}
            setDisplayForm={setShowCreateForm}
          />
        )}
        <div
          key={animKey}
          className={cn(
            "flex-1 min-h-0 flex flex-col",
            slideDirection === "left" && "cal-slide-from-left",
            slideDirection === "right" && "cal-slide-from-right",
          )}
        >
        <DnDCalendar
          components={{
            toolbar: CalendarToolbar,
            header: CalendarHeader, // month view — day name only
            agenda: agendaComponents,
            event: CalendarEvent,
            dateCellWrapper: MonthDateCellWrapper,
            week: { header: TimeViewHeader },
            day: { header: TimeViewHeader },
          }}
          view={view}
          onView={(newView) => {
            setView(newView);
            updateRangeForDate(selectedDate, newView);
          }}
          date={selectedDate}
          onNavigate={(newDate, _view, action) => {
            const direction =
              action === "PREV" ? "left" : action === "NEXT" ? "right"
              : newDate < (selectedDate ?? new Date()) ? "left" : "right";
            navigateTo(newDate, direction);
            updateRangeForDate(newDate, view);
          }}
          drilldownView="day"
          getDrilldownView={(targetDate, currentViewName) => {
            // Only drill down from month → day or week → day
            if (currentViewName === "month" || currentViewName === "week") return "day";
            return null; // no drill from day view
          }}
          onDrillDown={(date) => {
            // Drill into day view with slide animation
            const direction = date < (selectedDate ?? new Date()) ? "left" : "right";
            navigateTo(date, direction);
            setView("day");
            updateRangeForDate(date, "day");
          }}
          selectable
          onSelectSlot={({ start, end, action }) => {
            if (action === "click") {
              // In month view, tap on a day cell → switch to day view
              if (view === "month") {
                const direction = start < (selectedDate ?? new Date()) ? "left" : "right";
                navigateTo(start, direction);
                setView("day");
                updateRangeForDate(start, "day");
                return;
              }
              // In day/week views, a click opens the create form for that time slot
              const adjustedEnd = subMilliseconds(end, 1);
              setSelectDateRange({ start, end: adjustedEnd });
              setShowCreateForm(true);
              return;
            }
            const adjustedEnd = subMilliseconds(end, 1);
            setSelectDateRange({ start, end: adjustedEnd });
            setShowCreateForm(true);
          }}
          localizer={localizer}
          events={filteredCalendarTodos}
          startAccessor="dtstart"
          endAccessor="due"
          draggableAccessor={() => !isTouch}
          resizable={!isTouch}
          step={60}
          timeslots={1}
          messages={{ event: "Todo" }}
          formats={{
            timeGutterFormat: (date) =>
              width < 600 ? format(date, "HH:mm") : format(date, "hh:mm a"),
            eventTimeRangeFormat: () => "",
            // Day view header label: "Mon 13"
            dayHeaderFormat: (date) => format(date, "EEEE, MMM d"),
          }}
          eventPropGetter={(event) => calendarEventPropStyles(event.priority, event.projectID ? projectMetaData[event.projectID]?.color : undefined)}

          onRangeChange={setCalendarRange}
          onEventResize={({ event: todo, ...resizeEvent }) => {
            if (!todo.rrule) {
              editCalendarTodo({
                ...todo,
                dtstart: new Date(resizeEvent.start),
                due: new Date(resizeEvent.end),
              });
            } else {
              editCalendarTodoInstance({
                ...todo,
                instanceDate: todo.instanceDate || todo.dtstart,
                dtstart: new Date(resizeEvent.start),
                due: new Date(resizeEvent.end),
              });
            }
          }}
          onEventDrop={({ event: todo, ...dropEvent }) => {
            if (!todo.rrule) {
              editCalendarTodo({
                ...todo,
                dtstart: new Date(dropEvent.start),
                due: new Date(dropEvent.end),
              });
            } else {
              editCalendarTodoInstance({
                ...todo,
                instanceDate: todo.instanceDate || todo.dtstart,
                dtstart: new Date(dropEvent.start),
                due: new Date(dropEvent.end),
              });
            }
          }}
        />
        </div>
      </div>
    </div>
    </DrillToDayContext.Provider>
  );
}