import { useMemo, useState } from "react";
import { format } from "date-fns";
import {
  Ellipsis,
  ListPlus,
  Search,
  Sun,
  X,
} from "lucide-react";
import ScreenWatermark from "@/components/app/ScreenWatermark";
import { useListMetaData } from "@/components/Sidebar/List/query/get-list-meta";
import ListFormSheet from "@/components/Sidebar/List/ListFormSheet";
import TodoGroup from "@/components/todo/component/TodoGroup";
import TodoMutationProvider from "@/providers/TodoMutationProvider";
import {
  type NativeRouteId,
  homeCategoryRoutes,
  isNativeRouteActive,
  useNativeRouteCounts,
} from "@/components/app/nativeRouteConfig";
import { getDisplayDate } from "@/lib/date/displayDate";
import NativeAppBrandButton from "@/components/app/NativeAppBrandButton";
import { Link, useLocale, usePathname, useRouter } from "@/lib/navigation";
import { cn } from "@/lib/utils";
import { getListIcon } from "@/lib/listIcons";
import type { ListColor } from "@/types";
import { useUserTimezone } from "@/features/user/query/get-timezone";
import { useTodo } from "@/features/todayTodos/query/get-todo";
import { useTodoTimeline } from "@/features/todayTodos/query/get-todo-timeline";
import { useCompleteTodo } from "@/features/todayTodos/query/complete-todo";
import { useDeleteTodo } from "@/features/todayTodos/query/delete-todo";
import { useEditTodo } from "@/features/todayTodos/query/update-todo";
import { useEditTodoInstance } from "@/features/todayTodos/query/update-todo-instance";
import { usePrioritizeTodo } from "@/features/todayTodos/query/prioritize-todo";
import { useReorderTodo } from "@/features/todayTodos/query/reorder-todo";

const topButtonClass =
  "flex h-14 w-14 items-center justify-center rounded-full border border-white/70 bg-card/90 text-foreground shadow-[0_12px_28px_-22px_hsl(var(--shadow)/0.55)] transition-all duration-200 hover:-translate-y-0.5 hover:bg-card active:translate-y-0 dark:border-white/10";

const todayTileColor = "#6EA8E1";

const homeTileConfig: Partial<Record<NativeRouteId, { color: string; label: string }>> = {
  scheduled: { color: "#D98F4B", label: "Scheduled" },
  priority: { color: "#C97880", label: "Priority" },
  overdue: { color: "#E06F66", label: "Overdue" },
  all: { color: "#68717A", label: "All" },
  completed: { color: "#719F84", label: "Completed" },
  calendar: { color: "#9A89D2", label: "Calendar" },
};

const homeTileOrder: NativeRouteId[] = [
  "scheduled",
  "priority",
  "overdue",
  "all",
  "completed",
  "calendar",
];

const listColorCss: Record<ListColor, string> = {
  RED: "hsl(var(--accent-red))",
  ORANGE: "hsl(var(--accent-orange))",
  YELLOW: "hsl(var(--accent-yellow))",
  LIME: "hsl(var(--accent-lime))",
  BLUE: "hsl(var(--accent-blue))",
  PURPLE: "hsl(var(--accent-purple))",
  PINK: "hsl(var(--accent-pink))",
  TEAL: "hsl(var(--accent-teal))",
  CORAL: "hsl(var(--accent-coral))",
  GOLD: "hsl(var(--accent-gold))",
  DEEP_BLUE: "hsl(var(--accent-deep-blue))",
  ROSE: "hsl(var(--accent-rose))",
  LIGHT_RED: "hsl(var(--accent-light-red))",
  BRICK: "hsl(var(--accent-brick))",
  SLATE: "hsl(var(--accent-slate))",
};

function renderTileOverlay() {
  return (
    <>
      <div className="pointer-events-none absolute -left-14 -top-20 h-44 w-52 rounded-full bg-white/20 blur-2xl" />
      <div className="pointer-events-none absolute inset-0 bg-[linear-gradient(135deg,rgba(255,255,255,0.12),rgba(231,243,255,0.10)_45%,rgba(255,242,250,0.08)_68%,transparent)]" />
    </>
  );
}

function formatListName(value: string) {
  const trimmed = value.trim();
  if (!trimmed) return value;
  return trimmed.charAt(0).toUpperCase() + trimmed.slice(1);
}

export default function NativeHomeDashboard() {
  const router = useRouter();
  const pathname = usePathname();
  const locale = useLocale();
  const userTimeZone = useUserTimezone();
  const counts = useNativeRouteCounts();
  const { todos: todayTodos } = useTodo();
  const { todos: timelineTodos } = useTodoTimeline();
  const { listMetaData } = useListMetaData();
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [createListOpen, setCreateListOpen] = useState(false);
  const titleDate = format(new Date(), "EEE, MMM d");

  // Per-list active task counts, derived live from the task cache (the server's
  // todoCount can be stale/0). Mirrors native, which shows real counts.
  const listCounts = useMemo(() => {
    const counts: Record<string, number> = {};
    for (const todo of timelineTodos) {
      if (todo.completed || !todo.listID) continue;
      counts[todo.listID] = (counts[todo.listID] ?? 0) + 1;
    }
    return counts;
  }, [timelineTodos]);

  const lists = useMemo(() => {
    return Object.entries(listMetaData)
      .filter(([, list]) => Boolean(list.name?.trim()))
      .map(([id, list]) => ({ id, ...list, count: listCounts[id] ?? 0 }))
      .sort((a, b) => b.count - a.count)
      .slice(0, 6);
  }, [listMetaData, listCounts]);

  // Today's incomplete tasks, shown inline on the home screen (same data as the
  // Today screen), sorted by due time.
  const todayIncomplete = useMemo(
    () =>
      todayTodos
        .filter((todo) => !todo.completed)
        .slice()
        .sort((a, b) => a.due.getTime() - b.due.getTime()),
    [todayTodos],
  );

  const searchableTodos = useMemo(() => {
    return timelineTodos
      .filter((todo) => {
        const query = searchQuery.trim().toLowerCase();
        if (!query) return false;
        const listName = todo.listID ? listMetaData[todo.listID]?.name ?? "" : "";
        return (
          todo.title.toLowerCase().includes(query) ||
          (todo.description ?? "").toLowerCase().includes(query) ||
          listName.toLowerCase().includes(query)
        );
      })
      .slice(0, 8);
  }, [listMetaData, searchQuery, timelineTodos]);

  return (
    <TodoMutationProvider
      useCompleteTodo={useCompleteTodo}
      useDeleteTodo={useDeleteTodo}
      useEditTodo={useEditTodo}
      useEditTodoInstance={useEditTodoInstance}
      usePrioritizeTodo={usePrioritizeTodo}
      useReorderTodo={useReorderTodo}
    >
      <ScreenWatermark icon={Sun} />
      <div className="flex w-full flex-col gap-4 sm:gap-5">
        <header className="relative flex min-h-14 items-center justify-between gap-3">
          <NativeAppBrandButton className="min-w-0" />

          <div className="flex shrink-0 items-center gap-2">
            <button
              type="button"
              className={topButtonClass}
              onClick={() => {
                setSearchOpen((value) => !value);
                setSearchQuery("");
              }}
              aria-label="Search"
            >
              {searchOpen ? <X className="h-6 w-6 stroke-[2.6]" /> : <Search className="h-6 w-6 stroke-[2.6]" />}
            </button>
            <button
              type="button"
              className={topButtonClass}
              onClick={() => setCreateListOpen(true)}
              aria-label="Create list"
            >
              <ListPlus className="h-6 w-6 stroke-[2.6]" />
            </button>
            <button
              type="button"
              className={topButtonClass}
              onClick={() => router.push("/app/settings")}
              aria-label="Settings"
            >
              <Ellipsis className="h-6 w-6 stroke-[2.6]" />
            </button>
          </div>
        </header>

        {searchOpen && (
          <section className={cn(
            "border border-white/70 bg-card/90 shadow-[0_12px_28px_-22px_hsl(var(--shadow)/0.55)] dark:border-white/10 transition-all duration-200",
            searchQuery.trim() ? "rounded-[28px] pb-2" : "rounded-full",
          )}>
            <div className="relative">
              <Search className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <input
                autoFocus
                type="search"
                value={searchQuery}
                onChange={(event) => setSearchQuery(event.target.value)}
                placeholder="Search tasks..."
                className="h-14 w-full rounded-full bg-transparent pl-11 pr-4 text-base font-extrabold outline-none transition-colors md:text-sm"
              />
            </div>
            {searchQuery.trim() && (
              <div className="mt-2 max-h-72 overflow-y-auto">
                {searchableTodos.length === 0 ? (
                  <p className="px-3 py-4 text-sm font-extrabold text-muted-foreground">
                    No matching tasks
                  </p>
                ) : (
                  searchableTodos.map((todo) => (
                    <button
                      type="button"
                      key={todo.id}
                      className="flex w-full items-center gap-3 rounded-2xl px-3 py-2 text-left transition-colors hover:bg-muted/65"
                      onClick={() => router.push(`/app/todo?todo=${encodeURIComponent(todo.id)}`)}
                    >
                      <span className="h-2.5 w-2.5 rounded-full bg-accent" />
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-sm font-black text-foreground">
                          {todo.title}
                        </span>
                        <span className="block truncate text-xs font-extrabold text-muted-foreground">
                          {getDisplayDate(todo.due, true, locale, userTimeZone?.timeZone)}
                        </span>
                      </span>
                    </button>
                  ))
                )}
              </div>
            )}
          </section>
        )}

        <Link
          href="/app/today"
          className="relative flex h-[70px] items-center justify-between overflow-hidden rounded-[26px] px-5 text-white shadow-[0_14px_30px_-18px_rgba(50,90,130,0.62)] transition-transform duration-200 hover:-translate-y-0.5 active:translate-y-0.5"
          style={{ backgroundColor: todayTileColor }}
        >
          {renderTileOverlay()}
          <span className="relative truncate text-[1.38rem] font-black leading-none tracking-tight">
            {titleDate}
          </span>
          <span className="relative text-[2.1rem] font-black leading-none">
            {counts.today ?? todayTodos.length}
          </span>
        </Link>

        {todayIncomplete.length > 0 && (
          <section className="space-y-1">
            <TodoGroup
              todos={todayIncomplete}
              reorderable={false}
              perTaskOverdue
              showOverdueTag={false}
            />
          </section>
        )}

        <section className="grid grid-cols-2 gap-2.5 lg:grid-cols-3">
          {[...homeCategoryRoutes]
            .sort((a, b) => homeTileOrder.indexOf(a.id) - homeTileOrder.indexOf(b.id))
            .map((route) => {
            const Icon = route.icon;
            const active = isNativeRouteActive(pathname, route);
            const count = counts[route.id];
            const tile = homeTileConfig[route.id];

            return (
              <Link
                key={route.id}
                href={route.path}
                className={cn(
                  "group relative min-h-[94px] overflow-hidden rounded-[26px] p-3 text-white",
                  "shadow-[0_14px_30px_-18px_rgba(60,70,90,0.55)] transition-transform duration-200",
                  "hover:-translate-y-0.5 active:translate-y-0.5",
                  active && "ring-2 ring-white/50",
                )}
                style={{ backgroundColor: tile?.color }}
              >
                {renderTileOverlay()}
                <Icon className="pointer-events-none absolute -bottom-6 -right-5 h-24 w-24 text-white/18 stroke-[1.8]" />
                <div className="relative flex h-full flex-col justify-between">
                  <div className="flex items-start justify-between gap-3">
                    <Icon className="h-6 w-6 text-white stroke-[2.5]" />
                    {count != null && (
                      <span className="text-[1.72rem] font-black leading-none">
                        {count}
                      </span>
                    )}
                  </div>
                  <div className="min-w-0">
                    <p className="truncate text-[1.28rem] font-black leading-6 tracking-tight">
                      {tile?.label ?? route.label}
                    </p>
                  </div>
                </div>
              </Link>
            );
          })}
        </section>

        {lists.length > 0 && (
          <section className="space-y-2 pb-2 pt-12 sm:pt-0">
            <h2 className="px-1 text-[1.75rem] font-black leading-8 text-foreground">
              My Lists
            </h2>
            <div className="space-y-2">
              {lists.map((list) => {
                const accent = listColorCss[list.color ?? "PINK"];
                const ListIcon = getListIcon(list.iconKey);

                return (
                  <Link
                    key={list.id}
                    href={`/app/list/${list.id}`}
                    aria-label={`Open ${formatListName(list.name)}`}
                    className="relative flex h-[70px] items-center gap-3 overflow-hidden rounded-[26px] px-5 text-white shadow-[0_14px_30px_-18px_rgba(60,70,90,0.45)] transition-transform duration-200 hover:-translate-y-0.5 active:translate-y-0.5"
                    style={{
                      background: `color-mix(in srgb, hsl(var(--card-muted)) 34%, ${accent} 66%)`,
                    }}
                  >
                    {renderTileOverlay()}
                    <ListIcon className="pointer-events-none absolute -bottom-9 -right-7 h-28 w-28 text-white/18 stroke-[1.75]" />
                    <ListIcon className="relative h-6 w-6 shrink-0 text-white stroke-[2.5]" />
                    <span className="relative min-w-0 flex-1 truncate text-[1.38rem] font-black leading-none tracking-tight">
                      {formatListName(list.name)}
                    </span>
                    <span className="relative text-[1.5rem] font-black leading-none">
                      {list.count}
                    </span>
                  </Link>
                );
              })}
            </div>
          </section>
        )}
      </div>

      <ListFormSheet open={createListOpen} onOpenChange={setCreateListOpen} />
    </TodoMutationProvider>
  );
}
