import { useMemo, useState, type FormEvent } from "react";
import { format } from "date-fns";
import {
  Check,
  Ellipsis,
  GripVertical,
  ListPlus,
  Moon,
  Search,
  Sun,
  X,
} from "lucide-react";
import ListDot from "@/components/ListDot";
import { useListMetaData } from "@/components/Sidebar/List/query/get-list-meta";
import { useCreateList } from "@/components/Sidebar/List/query/create-list";
import TodoCheckbox from "@/components/ui/TodoCheckbox";
import TodoMutationProvider from "@/providers/TodoMutationProvider";
import { useTodoMutation } from "@/providers/TodoMutationProvider";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import {
  homeCategoryRoutes,
  isNativeRouteActive,
  useNativeRouteCounts,
} from "@/components/app/nativeRouteConfig";
import { getDisplayDate } from "@/lib/date/displayDate";
import { Link, useLocale, usePathname, useRouter } from "@/lib/navigation";
import { cn } from "@/lib/utils";
import type { ListItemMetaMapType, TodoItemType } from "@/types";
import { useUserTimezone } from "@/features/user/query/get-timezone";
import { useTodo } from "@/features/todayTodos/query/get-todo";
import { useTodoTimeline } from "@/features/todayTodos/query/get-todo-timeline";
import { useCompleteTodo } from "@/features/todayTodos/query/complete-todo";
import { useDeleteTodo } from "@/features/todayTodos/query/delete-todo";
import { useEditTodo } from "@/features/todayTodos/query/update-todo";
import { useEditTodoInstance } from "@/features/todayTodos/query/update-todo-instance";
import { usePinTodo } from "@/features/todayTodos/query/pin-todo";
import { usePrioritizeTodo } from "@/features/todayTodos/query/prioritize-todo";
import { useReorderTodo } from "@/features/todayTodos/query/reorder-todo";

const topButtonClass =
  "flex h-14 w-14 items-center justify-center rounded-full border border-white/70 bg-card/90 text-foreground shadow-[0_12px_28px_-22px_hsl(var(--shadow)/0.55)] transition-all duration-200 hover:-translate-y-0.5 hover:bg-card active:translate-y-0 dark:border-white/10";

function normalizeListName(value: string) {
  return value.trim();
}

function formatListName(value: string) {
  const trimmed = value.trim();
  if (!trimmed) return value;
  return trimmed.charAt(0).toUpperCase() + trimmed.slice(1);
}

function NativeTodayTaskRow({
  todo,
  listMetaData,
  locale,
  timeZone,
}: {
  todo: TodoItemType;
  listMetaData: ListItemMetaMapType;
  locale: string;
  timeZone?: string;
}) {
  const { useCompleteTodo } = useTodoMutation();
  const { completeMutateFn } = useCompleteTodo();
  const list = todo.listID ? listMetaData[todo.listID] : null;
  const dueLabel = getDisplayDate(todo.due, true, locale, timeZone);
  const isOverdue = todo.due.getTime() < Date.now();

  return (
    <div className="flex min-h-[58px] items-center gap-3 border-b border-border/70 px-4 py-2.5 last:border-b-0">
      <span className="flex h-7 w-7 items-center justify-center rounded-xl bg-muted/70">
        {todo.listID ? (
          <ListDot id={todo.listID} className="text-base" />
        ) : (
          <span className="h-2.5 w-2.5 rounded-full bg-accent" />
        )}
      </span>
      <TodoCheckbox
        icon={Check}
        priority={todo.priority}
        complete={todo.completed}
        onChange={() => completeMutateFn(todo)}
        checked={todo.completed}
        variant={todo.rrule ? "repeat" : "outline-solid"}
      />
      <div className="min-w-0 flex-1">
        <p className="truncate text-[0.98rem] font-black leading-5 text-foreground">
          {todo.title}
        </p>
        <div className="mt-1 flex min-w-0 flex-wrap items-center gap-2 sm:hidden">
          <span className={cn("text-xs font-black", isOverdue ? "text-red" : "text-lime")}>
            {dueLabel}
          </span>
          {list && (
            <span className="inline-flex max-w-28 items-center gap-1 rounded-full border border-border/70 bg-muted/70 px-2 py-[0.15rem] text-xs font-black text-foreground/80">
              <ListDot id={todo.listID!} className="text-xs" />
              <span className="truncate">{list.name}</span>
            </span>
          )}
        </div>
      </div>
      <div className="hidden items-center gap-2 sm:flex">
        {list && (
          <span className="inline-flex max-w-32 items-center gap-1 rounded-full border border-border/70 bg-muted/70 px-2 py-[0.15rem] text-xs font-black text-foreground/80">
            <ListDot id={todo.listID!} className="text-xs" />
            <span className="truncate">{list.name}</span>
          </span>
        )}
        <span className={cn("whitespace-nowrap text-sm font-black", isOverdue ? "text-red" : "text-muted-foreground")}>
          {dueLabel}
        </span>
      </div>
      <GripVertical className="h-5 w-5 shrink-0 text-muted-foreground/60" />
    </div>
  );
}

export default function NativeHomeDashboard() {
  const router = useRouter();
  const pathname = usePathname();
  const locale = useLocale();
  const userTimeZone = useUserTimezone();
  const counts = useNativeRouteCounts();
  const { todos: todayTodos, todoLoading: todayLoading } = useTodo();
  const { todos: timelineTodos } = useTodoTimeline();
  const { listMetaData } = useListMetaData();
  const { createMutateAsync, createLoading } = useCreateList();
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [createListOpen, setCreateListOpen] = useState(false);
  const [newListName, setNewListName] = useState("");
  const [createListError, setCreateListError] = useState<string | null>(null);
  const currentHour = new Date().getHours();
  const isDaytime = currentHour >= 6 && currentHour < 18;
  const titleDate = format(new Date(), "EEE, MMM d");

  const lists = useMemo(() => {
    return Object.entries(listMetaData)
      .filter(([, list]) => Boolean(list.name?.trim()))
      .map(([id, list]) => ({ id, ...list }))
      .sort((a, b) => (b.todoCount ?? 0) - (a.todoCount ?? 0))
      .slice(0, 6);
  }, [listMetaData]);

  const sortedTodayTodos = useMemo(() => {
    return [...todayTodos].sort((a, b) => {
      if (a.pinned !== b.pinned) return a.pinned ? -1 : 1;
      const dueDelta = a.due.getTime() - b.due.getTime();
      if (dueDelta !== 0) return dueDelta;
      return a.order - b.order;
    });
  }, [todayTodos]);

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

  const handleCreateList = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const name = normalizeListName(newListName);
    if (!name) {
      setCreateListError("List name cannot be empty");
      return;
    }

    setCreateListError(null);
    await createMutateAsync({ name, color: "BLUE" });
    setNewListName("");
    setCreateListOpen(false);
  };

  return (
    <TodoMutationProvider
      useCompleteTodo={useCompleteTodo}
      useDeleteTodo={useDeleteTodo}
      useEditTodo={useEditTodo}
      useEditTodoInstance={useEditTodoInstance}
      usePinTodo={usePinTodo}
      usePrioritizeTodo={usePrioritizeTodo}
      useReorderTodo={useReorderTodo}
    >
      <div className="mx-auto flex w-full max-w-5xl flex-col gap-4 sm:gap-5 lg:max-w-6xl">
        <header className="relative flex min-h-14 items-center justify-between gap-3">
          <div className="flex min-w-0 items-center gap-2">
            {isDaytime ? (
              <Sun className="h-7 w-7 shrink-0 fill-[#F4C542] text-[#F4C542]" />
            ) : (
              <Moon className="h-7 w-7 shrink-0 fill-[#A8B8E8] text-[#A8B8E8]" />
            )}
            <h1 className="truncate text-[2rem] font-black leading-none tracking-normal text-foreground sm:text-[2.35rem]">
              T&apos;Day
            </h1>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              className={topButtonClass}
              onClick={() => {
                setSearchOpen((value) => !value);
                setSearchQuery("");
              }}
              aria-label="Search"
            >
              {searchOpen ? <X className="h-5 w-5" /> : <Search className="h-5 w-5" />}
            </button>
            <button
              type="button"
              className={topButtonClass}
              onClick={() => setCreateListOpen(true)}
              aria-label="Create list"
            >
              <ListPlus className="h-5 w-5" />
            </button>
            <button
              type="button"
              className={topButtonClass}
              onClick={() => router.push("/app/settings")}
              aria-label="Settings"
            >
              <Ellipsis className="h-5 w-5" />
            </button>
          </div>
        </header>

        {searchOpen && (
          <section className="rounded-[24px] border border-white/70 bg-card/92 p-3 shadow-[0_16px_36px_-30px_hsl(var(--shadow)/0.55)] dark:border-white/10">
            <div className="relative">
              <Search className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <input
                autoFocus
                type="search"
                value={searchQuery}
                onChange={(event) => setSearchQuery(event.target.value)}
                placeholder="Search tasks..."
                className="h-12 w-full rounded-2xl border border-border/70 bg-muted/55 pl-11 pr-4 text-sm font-extrabold outline-none transition-colors focus:border-accent/50 focus:bg-card"
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

        <section className="overflow-hidden rounded-[28px] border border-white/70 bg-card shadow-[0_18px_42px_-30px_hsl(var(--shadow)/0.6)] dark:border-white/10">
          <Link
            href="/app/tday"
            className={cn(
              "relative block overflow-hidden bg-accent px-4 py-4 text-accent-foreground sm:px-5",
              "transition-colors duration-200 hover:bg-accent/95",
            )}
          >
            <div className="pointer-events-none absolute -left-8 -top-16 h-44 w-72 rounded-full bg-white/22 blur-2xl" />
            <div className="pointer-events-none absolute -right-14 bottom-0 h-36 w-56 rounded-full bg-white/12 blur-2xl" />
            <div className="relative flex items-center justify-between gap-4">
              <div className="flex min-w-0 items-center gap-3">
                <span className="hidden h-12 w-12 shrink-0 items-center justify-center rounded-full bg-white text-[#F4C542] shadow-sm sm:flex">
                  <Sun className="h-6 w-6 fill-current" />
                </span>
                <span className="min-w-0">
                  <span className="block truncate text-[1.75rem] font-black leading-8 sm:text-[2rem]">
                    Today
                  </span>
                  <span className="block truncate text-sm font-black text-accent-foreground/78 sm:text-base">
                    {titleDate}
                  </span>
                </span>
              </div>
              <span className="text-right">
                <span className="block text-[2.5rem] font-black leading-none sm:text-[3rem]">
                  {counts.today ?? todayTodos.length}
                </span>
                <span className="block text-xs font-black text-accent-foreground/78 sm:text-sm">
                  tasks
                </span>
              </span>
            </div>
          </Link>

          <div className="bg-card">
            {todayLoading ? (
              <div className="px-4 py-5 text-sm font-extrabold text-muted-foreground">
                Loading today...
              </div>
            ) : sortedTodayTodos.length === 0 ? (
              <div className="px-4 py-5 text-sm font-extrabold text-muted-foreground">
                No tasks for today
              </div>
            ) : (
              sortedTodayTodos.slice(0, 4).map((todo) => (
                <NativeTodayTaskRow
                  key={todo.id}
                  todo={todo}
                  listMetaData={listMetaData}
                  locale={locale}
                  timeZone={userTimeZone?.timeZone ?? undefined}
                />
              ))
            )}
            {sortedTodayTodos.length > 4 && (
              <Link
                href="/app/tday"
                className="block px-4 py-3 text-center text-sm font-black text-muted-foreground transition-colors hover:bg-muted/55 hover:text-foreground"
              >
                View {sortedTodayTodos.length - 4} more
              </Link>
            )}
          </div>
        </section>

        <section className="grid grid-cols-2 gap-3 lg:grid-cols-3">
          {homeCategoryRoutes.map((route) => {
            const Icon = route.icon;
            const active = isNativeRouteActive(pathname, route);
            const count = counts[route.id];

            return (
              <Link
                key={route.id}
                href={route.path}
                className={cn(
                  "group min-h-[78px] rounded-[22px] border border-white/70 bg-card/92 p-3 sm:min-h-[94px] sm:rounded-[24px]",
                  "shadow-[0_14px_34px_-30px_hsl(var(--shadow)/0.55)] transition-all duration-200",
                  "hover:-translate-y-0.5 hover:bg-card dark:border-white/10",
                  active && "ring-2 ring-accent/25",
                )}
              >
                <div className="flex h-full items-end justify-between gap-3">
                  <div className="min-w-0">
                    <span className="mb-2 flex h-9 w-9 items-center justify-center rounded-2xl bg-muted/70">
                      <Icon className={cn("h-5 w-5 stroke-[2.4]", route.accentClass)} />
                    </span>
                    <p className="truncate text-[1.05rem] font-black leading-5 text-foreground">
                      {route.label}
                    </p>
                  </div>
                  {count != null && (
                    <span className="text-[1.65rem] font-black leading-none text-foreground">
                      {count}
                    </span>
                  )}
                </div>
              </Link>
            );
          })}
        </section>

        {lists.length > 0 && (
          <section className="space-y-2 pb-2 pt-12 sm:pt-0">
            <h2 className="px-1 text-[1.45rem] font-black leading-8 text-foreground">
              My Lists
            </h2>
            <div className="space-y-2">
              {lists.map((list) => (
                <Link
                  key={list.id}
                  href={`/app/list/${list.id}`}
                  className="flex min-h-[64px] items-center gap-3 rounded-[22px] border border-white/70 bg-card/92 px-3 shadow-[0_12px_30px_-28px_hsl(var(--shadow)/0.45)] transition-all duration-200 hover:-translate-y-0.5 hover:bg-card dark:border-white/10"
                >
                  <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-muted/70">
                    <ListDot id={list.id} className="text-xl" />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-[1rem] font-black text-foreground">
                      {formatListName(list.name)}
                    </span>
                    <span className="block truncate text-xs font-extrabold text-muted-foreground">
                      {list.todoCount ?? 0} tasks
                    </span>
                  </span>
                  <span className="text-lg font-black text-muted-foreground">
                    {list.todoCount ?? 0}
                  </span>
                </Link>
              ))}
            </div>
          </section>
        )}
      </div>

      <Dialog open={createListOpen} onOpenChange={setCreateListOpen}>
        <DialogContent className="max-w-md rounded-[28px] border border-white/70 bg-background p-5 shadow-[0_24px_60px_-30px_hsl(var(--shadow)/0.72)] dark:border-white/10">
          <form onSubmit={handleCreateList} className="space-y-4">
            <DialogHeader>
              <DialogTitle className="text-2xl font-black">New list</DialogTitle>
              <DialogDescription className="font-extrabold">
                Create a list for related tasks.
              </DialogDescription>
            </DialogHeader>
            <Input
              value={newListName}
              onChange={(event) => setNewListName(event.target.value)}
              placeholder="List name"
              className="h-12 rounded-2xl border-border/70 bg-card text-base font-extrabold"
              autoFocus
            />
            {createListError && (
              <p className="text-sm font-extrabold text-destructive">{createListError}</p>
            )}
            <DialogFooter className="gap-2 sm:space-x-0">
              <Button
                type="button"
                variant="outline"
                className="rounded-2xl border-border/70 bg-card px-4 font-black"
                onClick={() => setCreateListOpen(false)}
              >
                Cancel
              </Button>
              <Button
                type="submit"
                disabled={createLoading}
                className="rounded-2xl bg-accent px-4 font-black text-accent-foreground hover:bg-accent/90"
              >
                {createLoading ? "Creating..." : "Create"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </TodoMutationProvider>
  );
}
