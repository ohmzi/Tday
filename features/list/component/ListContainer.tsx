"use client"
import React, { useMemo, useState } from "react";
import CreateTodoBtn from "./CreateTodoBtn";
import TodoListLoading from "../../../components/todo/component/TodoListLoading";
import TodoGroup from "@/components/todo/component/TodoGroup";
import LineSeparator from "@/components/ui/lineSeparator";
import TodoFilterBar from "./TodoFilterBar";
import { getDisplayDate } from "@/lib/date/displayDate";
import { useUserTimezone } from "@/features/user/query/get-timezone";
import { RRule } from "rrule";
import { TodoItemType } from "@/types";
import clsx from "clsx";
import { useLocale } from "next-intl";
import { useUserPreferences } from "@/providers/UserPreferencesProvider";
import { usePinListTodo } from "../query/pin-list-todo";
import { useCompleteListTodo } from "../query/complete-list-todo";
import { useDeleteListTodo } from "../query/delete-list-todo";
import { usePrioritizeListTodo } from "../query/prioritize-list-todo";
import { useEditListTodo } from "../query/update-list-todo";
import { useEditListTodoInstance } from "../query/update-list-todo-instance";
import { useReorderListTodo } from "../query/reorder-list-todo";
import TodoMutationProvider from "@/providers/TodoMutationProvider";
import { useList } from "../query/get-list-todos";
import { useListMetaData } from "@/components/Sidebar/List/query/get-list-meta";
import MobileSearchHeader from "@/components/ui/MobileSearchHeader";
import ListDot from "@/components/ListDot";
import { Search, X } from "lucide-react";

const ListContainer = ({ id }: { id: string }) => {
    const locale = useLocale();
    const userTZ = useUserTimezone()
    const { listMetaData } = useListMetaData();
    const { preferences } = useUserPreferences();
    const { listTodos, listTodosLoading } = useList({ id });
    const [containerHovered, setContainerHovered] = useState(false);
    const [searchQuery, setSearchQuery] = useState("");

    const filteredTodos = useMemo(() => {
        const query = searchQuery.trim().toLowerCase();
        if (!query) return listTodos;
        return listTodos.filter((todo) => {
            const title = todo.title.toLowerCase();
            const description = (todo.description || "").toLowerCase();
            return title.includes(query) || description.includes(query);
        });
    }, [listTodos, searchQuery]);

    const pinnedTodos = useMemo(() =>
        filteredTodos.filter(({ pinned }) => pinned),
        [filteredTodos]
    );

    const unpinnedTodos = useMemo(() =>
        filteredTodos.filter(({ pinned }) => !pinned),
        [filteredTodos]
    );

    const priorityMap = useMemo(() => ({ "Low": 1, "Medium": 2, "High": 3 } as const), []);

    const groupedTodos = useMemo(() => {
        return Object.groupBy((unpinnedTodos), (todo) => {
            switch (preferences?.groupBy) {
                case "dtstart":
                    return getDisplayDate(todo.dtstart, false, locale, userTZ?.timeZone);
                case "due":
                    return getDisplayDate(todo.due, false, locale, userTZ?.timeZone);
                case "duration":
                    return Number((Math.round(todo.durationMinutes / 60 * 10) / 10).toFixed(1)).toString() + " hr";
                case "priority":
                    return String(todo.priority);
                case "rrule":
                    return todo.rrule ? new RRule(RRule.parseString(todo.rrule)).toText() : "Non repeating"
                default:
                    return "-1"
            }
        }) as Record<string, TodoItemType[]>
    }, [unpinnedTodos, preferences?.groupBy, locale, userTZ?.timeZone])

    const sortedGroupedTodos = useMemo(() => {
        const sorted: Record<string, TodoItemType[]> = {};
        for (const [key, todos] of Object.entries(groupedTodos)) {
            sorted[key] = [...todos].sort((a, b) => {
                switch (preferences?.sortBy) {
                    case "dtstart":
                        return preferences.direction == "Descending" ? a.dtstart.getTime() - b.dtstart.getTime() : b.dtstart.getTime() - a.dtstart.getTime();
                    case "due":
                        return preferences.direction == "Descending" ? a.due.getTime() - b.due.getTime() : b.due.getTime() - a.due.getTime();
                    case "duration":
                        return preferences.direction == "Descending" ? a.durationMinutes - b.durationMinutes : b.durationMinutes - a.durationMinutes;
                    case "priority":
                        return preferences.direction == "Descending" ? priorityMap[a.priority] - priorityMap[b.priority] : priorityMap[b.priority] - priorityMap[a.priority];
                    default:
                        return a.order - b.order;
                }
            });
        }

        return sorted;
    }, [groupedTodos, preferences?.sortBy, preferences?.direction, priorityMap]);

    const listName = listMetaData[id]?.name?.trim() || "";

    return (
        <TodoMutationProvider
            useCompleteTodo={useCompleteListTodo}
            useDeleteTodo={useDeleteListTodo}
            useEditTodo={useEditListTodo}
            useEditTodoInstance={useEditListTodoInstance}
            usePinTodo={usePinListTodo}
            usePrioritizeTodo={usePrioritizeListTodo}
            useReorderTodo={useReorderListTodo}
        >
            <div className="mb-20" onMouseOver={() => (setContainerHovered(true))} onMouseOut={() => setContainerHovered(false)}>
                {/* Sticky header with mobile menu + search — matches T'Day & Completed pages */}
                <MobileSearchHeader
                    searchQuery={searchQuery}
                    onSearchChange={setSearchQuery}
                    placeholder={`Search in ${listName}...`}
                />

                {/* List title with colored icon */}
                <div className="mt-8 mb-4 sm:mt-10 sm:mb-5 lg:mt-16 lg:mb-6 ml-[2px] flex items-center justify-between">
                    <div className="flex items-center gap-2">
                        <ListDot id={id} className="h-6 w-6" />
                        <h3 className="select-none text-2xl font-semibold tracking-tight">
                            {listName}
                        </h3>
                    </div>
                    <div className="hidden lg:block">
                        <TodoFilterBar containerHovered={containerHovered} />
                    </div>
                </div>
                <LineSeparator className="flex-1 border-border/70" />

                {/* Loading state */}
                {listTodosLoading && <TodoListLoading />}

                {/* Empty state — no tasks yet */}
                {!listTodosLoading && !searchQuery.trim() && listTodos.length === 0 && (
                    <div className="mt-4 rounded-2xl border border-border/65 bg-card/95 px-4 py-6 text-sm text-muted-foreground">
                        No tasks yet.
                    </div>
                )}

                {/* Empty state — no search results */}
                {!listTodosLoading && searchQuery.trim() && filteredTodos.length === 0 && (
                    <div className="mx-auto flex min-h-[45vh] max-w-md flex-col items-center justify-center text-center">
                        <div className="relative mb-6">
                            <div className="flex h-24 w-24 items-center justify-center rounded-full bg-muted/50">
                                <Search className="h-12 w-12 text-muted-foreground/50" />
                            </div>
                            <div className="absolute -right-1 -top-1 flex h-6 w-6 items-center justify-center rounded-full border-2 border-background bg-accent/20">
                                <X className="h-3 w-3 text-accent" />
                            </div>
                        </div>
                        <h3 className="mb-2 text-2xl font-semibold text-foreground">
                            No matching tasks found
                        </h3>
                        <p className="mb-6 text-sm text-muted-foreground">
                            Try adjusting your search terms or{" "}
                            <button
                                onClick={() => setSearchQuery("")}
                                className="text-accent hover:underline"
                            >
                                clear the search
                            </button>
                        </p>
                    </div>
                )}

                {/* Pinned Todos */}
                {pinnedTodos.length > 0 && (
                    <section className="mb-8 lg:mb-10 mt-5 sm:mt-6 lg:mt-8">
                        <TodoGroup todos={pinnedTodos} />
                    </section>
                )}

                {/* Grouped Todos */}
                {Object.entries(sortedGroupedTodos).map(([key, todo]) =>
                    <div key={key}>
                        <section className={clsx("mb-8 lg:mb-10", key === "-1" && "mt-5 sm:mt-6 lg:mt-8")}>
                            {key !== "-1" && (
                                <div className="mb-3 mt-6 flex items-center gap-2 sm:mt-7 lg:mb-4 lg:mt-10">
                                    <h3 className="select-none text-lg font-semibold tracking-tight">
                                        {key}
                                    </h3>
                                    <LineSeparator className="flex-1 border-border/70" />
                                </div>
                            )}
                            <TodoGroup
                                todos={todo}
                            />
                        </section>
                    </div>
                )}

                <CreateTodoBtn listID={id} />
            </div>
        </TodoMutationProvider>
    );
};

export default ListContainer;
