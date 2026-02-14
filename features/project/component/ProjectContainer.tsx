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
import { usePinProjectTodo } from "../query/pin-project-todo";
import { useCompleteProjectTodo } from "../query/complete-project-todo";
import { useDeleteProjectTodo } from "../query/delete-project-todo";
import { usePrioritizeProjectTodo } from "../query/prioritize-project-todo";
import { useEditProjectTodo } from "../query/update-project-todo";
import { useEditProjectTodoInstance } from "../query/update-project-todo-instance";
import { useReorderProjectTodo } from "../query/reorder-project-todo";
import TodoMutationProvider from "@/providers/TodoMutationProvider";
import { useProject } from "../query/get-project-todos";
import { useProjectMetaData } from "@/components/Sidebar/Project/query/get-project-meta";
import MobileSearchHeader from "@/components/ui/MobileSearchHeader";
import ProjectTag from "@/components/ProjectTag";
import { Search, X } from "lucide-react";

const ProjectContainer = ({ id }: { id: string }) => {
    const locale = useLocale();
    const userTZ = useUserTimezone()
    const { projectMetaData } = useProjectMetaData();
    const { preferences } = useUserPreferences();
    const { projectTodos, projectTodosLoading } = useProject({ id });
    const [containerHovered, setContainerHovered] = useState(false);
    const [searchQuery, setSearchQuery] = useState("");

    const filteredTodos = useMemo(() => {
        const query = searchQuery.trim().toLowerCase();
        if (!query) return projectTodos;
        return projectTodos.filter((todo) => {
            const title = todo.title.toLowerCase();
            const description = (todo.description || "").toLowerCase();
            return title.includes(query) || description.includes(query);
        });
    }, [projectTodos, searchQuery]);

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

    const tagName = projectMetaData[id]?.name?.replace(/^#+\s*/, "") || "";

    return (
        <TodoMutationProvider
            useCompleteTodo={useCompleteProjectTodo}
            useDeleteTodo={useDeleteProjectTodo}
            useEditTodo={useEditProjectTodo}
            useEditTodoInstance={useEditProjectTodoInstance}
            usePinTodo={usePinProjectTodo}
            usePrioritizeTodo={usePrioritizeProjectTodo}
            useReorderTodo={useReorderProjectTodo}
        >
            <div className="mb-20" onMouseOver={() => (setContainerHovered(true))} onMouseOut={() => setContainerHovered(false)}>
                {/* Sticky header with mobile menu + search — matches Tday & Completed pages */}
                <MobileSearchHeader
                    searchQuery={searchQuery}
                    onSearchChange={setSearchQuery}
                    placeholder={`Search in #${tagName}...`}
                />

                {/* Tag title with colored icon */}
                <div className="mt-16 mb-6 sm:my-6 ml-[2px] flex items-center justify-between">
                    <div className="flex items-center gap-2">
                        <ProjectTag id={id} className="h-6 w-6" />
                        <h3 className="select-none text-2xl font-semibold tracking-tight">
                            {tagName}
                        </h3>
                    </div>
                    <TodoFilterBar containerHovered={containerHovered} />
                </div>
                <LineSeparator className="flex-1 border-border/70" />

                {/* Loading state */}
                {projectTodosLoading && <TodoListLoading />}

                {/* Empty state — no tasks yet */}
                {!projectTodosLoading && !searchQuery.trim() && projectTodos.length === 0 && (
                    <div className="mt-4 rounded-2xl border border-border/65 bg-card/95 px-4 py-6 text-sm text-muted-foreground">
                        No tasks yet. Press <kbd className="mx-1 rounded border border-border bg-muted px-1.5 py-0.5 text-xs font-medium">Q</kbd> or tap the button below to add one.
                    </div>
                )}

                {/* Empty state — no search results */}
                {!projectTodosLoading && searchQuery.trim() && filteredTodos.length === 0 && (
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
                    <TodoGroup
                        className="relative my-8 rounded-2xl border border-border/65 bg-card/95 p-3 shadow-[0_8px_24px_hsl(var(--shadow)/0.11)]"
                        todos={pinnedTodos}
                    />
                )}

                {/* Grouped Todos */}
                {Object.entries(sortedGroupedTodos).map(([key, todo]) =>
                    <div key={key}>
                        <div className={clsx(key !== "-1" && "my-8")}>
                            {key !== "-1" && <p className="text-sm text-muted-foreground">{preferences?.groupBy?.slice(0, 1).toUpperCase() + "" + preferences?.groupBy?.slice(1,)}<span className="text-lg">{" " + key} </span></p>}
                            {key !== "-1" && <LineSeparator className="border-border/70" />}
                            <TodoGroup
                                todos={todo}
                                className="bg-transparent"
                            />
                        </div>
                    </div>
                )}

                <CreateTodoBtn projectID={id} />
            </div>
        </TodoMutationProvider>
    );
};

export default ProjectContainer;
