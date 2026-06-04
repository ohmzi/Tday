import type { ElementType } from "react";
import {
  Calendar1,
  CalendarClock,
  CheckCircle,
  Clock3,
  Flag,
  Layers,
  Settings,
  Sun,
} from "lucide-react";
import BubbleChartIcon from "@/components/icons/BubbleChartIcon";
import { useCompletedTodo } from "@/features/completed/query/get-completedTodo";
import { useTodo } from "@/features/todayTodos/query/get-todo";
import { useTodoTimeline } from "@/features/todayTodos/query/get-todo-timeline";
import { useFloater } from "@/features/floater/query/get-floater";
import type { TodoItemType } from "@/types";

export type NativeRouteId =
  | "today"
  | "overdue"
  | "scheduled"
  | "priority"
  | "all"
  | "completed"
  | "floater"
  | "calendar"
  | "settings";

export type NativeRouteItem = {
  id: NativeRouteId;
  label: string;
  path: string;
  icon: ElementType;
  accentClass: string;
};

export type NativeRouteCounts = Record<NativeRouteId, number | null>;

export const nativeRoutes: NativeRouteItem[] = [
  {
    id: "today",
    label: "Today",
    path: "/app/tday",
    icon: Sun,
    accentClass: "text-accent-blue",
  },
  {
    id: "overdue",
    label: "Overdue",
    path: "/app/overdue",
    icon: Clock3,
    accentClass: "text-accent-red",
  },
  {
    id: "scheduled",
    label: "Scheduled",
    path: "/app/scheduled",
    icon: CalendarClock,
    accentClass: "text-accent-purple",
  },
  {
    id: "priority",
    label: "Priority",
    path: "/app/priority",
    icon: Flag,
    accentClass: "text-accent-gold",
  },
  {
    id: "all",
    label: "All Tasks",
    path: "/app/todo",
    icon: Layers,
    accentClass: "text-muted-foreground",
  },
  {
    id: "completed",
    label: "Completed",
    path: "/app/completed",
    icon: CheckCircle,
    accentClass: "text-lime",
  },
  {
    id: "floater",
    label: "Floater",
    path: "/app/floater",
    icon: BubbleChartIcon,
    accentClass: "text-accent-teal",
  },
  {
    id: "calendar",
    label: "Calendar",
    path: "/app/calendar",
    icon: Calendar1,
    accentClass: "text-accent-purple",
  },
  {
    id: "settings",
    label: "Settings",
    path: "/app/settings",
    icon: Settings,
    accentClass: "text-muted-foreground",
  },
];

export const homeCategoryRoutes = nativeRoutes.filter((route) =>
  ["overdue", "scheduled", "all", "priority", "completed", "calendar"].includes(route.id),
);

// Settings lives in the user/account menu. Floater is a root dock tab, so the
// More sheet keeps the secondary destinations, including Calendar.
export const moreNavigationRoutes = nativeRoutes.filter(
  (route) =>
    route.id !== "today" && route.id !== "settings" && route.id !== "floater",
);

export function isNativeRouteActive(pathname: string, route: NativeRouteItem) {
  if (route.id === "floater") {
    return pathname.includes("/app/floater");
  }
  if (route.id === "today") {
    return pathname.includes("/app/tday") || pathname.includes("/app/today");
  }
  if (route.id === "all") {
    return pathname.includes("/app/todo");
  }
  return pathname.includes(route.path);
}

export function isPriorityTodo(priority: string | null | undefined) {
  const normalized = (priority || "").trim().toLowerCase();
  return (
    normalized === "medium" ||
    normalized === "high" ||
    normalized === "important" ||
    normalized === "urgent"
  );
}

export function useNativeRouteCounts(): NativeRouteCounts {
  const { todos } = useTodo();
  const { todos: timelineTodos } = useTodoTimeline();
  const { completedTodos } = useCompletedTodo();
  const { floaters } = useFloater();
  const now = new Date();
  const overdueCount = timelineTodos.filter((todo) => todo.due < now).length;
  const scheduledCount = timelineTodos.filter((todo) => todo.due >= now).length;
  const priorityCount = timelineTodos.filter((todo: TodoItemType) =>
    isPriorityTodo(todo.priority)
  ).length;

  return {
    today: todos.length,
    overdue: overdueCount,
    scheduled: scheduledCount,
    priority: priorityCount,
    all: timelineTodos.length,
    completed: completedTodos.length,
    floater: floaters.filter((floater) => !floater.completed).length,
    calendar: scheduledCount,
    settings: null,
  };
}
