import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import TodoListLoading from "@/components/todo/component/TodoListLoading";
import TimelineSections from "@/components/todo/dnd/TimelineSections";
import { buildTimelineSections } from "@/lib/timeline/buildTimelineSections";
import { useCompleteListTodo } from "../query/complete-list-todo";
import { useDeleteListTodo } from "../query/delete-list-todo";
import { usePrioritizeListTodo } from "../query/prioritize-list-todo";
import { useEditListTodo } from "../query/update-list-todo";
import { useEditListTodoInstance } from "../query/update-list-todo-instance";
import { useReorderListTodo } from "../query/reorder-list-todo";
import TodoMutationProvider from "@/providers/TodoMutationProvider";
import { useList } from "../query/get-list-todos";
import { useListMetaData } from "@/components/Sidebar/List/query/get-list-meta";
import NativePageHeader, { useNativePageBarSlots } from "@/components/app/NativePageHeader";
import MobileSearchHeader from "@/components/ui/MobileSearchHeader";
import ScreenWatermark from "@/components/app/ScreenWatermark";
import EmptyState from "@/components/app/EmptyState";
import { getListIcon } from "@/lib/listIcons";
import { listColorAccentColors, nativeScreenAccentColors } from "@/components/app/nativeScreenTheme";
import ListFormSheet from "@/components/Sidebar/List/ListFormSheet";
import ManageMembersSheet from "@/features/list/component/ManageMembersSheet";
import SummaryButton from "@/features/summary/SummaryButton";
import { useShareListAsText } from "@/hooks/use-share-list";
import { useIsLocalMode } from "@/hooks/useAppMode";
import { Button } from "@/components/ui/button";
import { useLocale } from "@/lib/navigation";
import { useUserTimezone } from "@/features/user/query/get-timezone";
import { Pencil, Search, Users } from "lucide-react";
import { flattenNotesToPlainText } from "@/lib/richNotes";

const ListContainer = ({ id }: { id: string }) => {
    const locale = useLocale();
    const isLocalMode = useIsLocalMode();
    const userTZ = useUserTimezone();
    const { t: appDict } = useTranslation("app");
    const { listMetaData } = useListMetaData();
    const { listTodos, listTodosLoading } = useList({ id });
    const [searchQuery, setSearchQuery] = useState("");
    const [editListOpen, setEditListOpen] = useState(false);
    const [membersOpen, setMembersOpen] = useState(false);
    const [earlierExpanded, setEarlierExpanded] = useState(false);
    // Empty date buckets are drop targets and nothing else, so they exist only
    // for the length of a drag.
    const [dragActive, setDragActive] = useState(false);

    const filteredTodos = useMemo(() => {
        const query = searchQuery.trim().toLowerCase();
        if (!query) return listTodos;
        return listTodos.filter((todo) => {
            const title = todo.title.toLowerCase();
            const description = flattenNotesToPlainText(todo.description).toLowerCase();
            return title.includes(query) || description.includes(query);
        });
    }, [listTodos, searchQuery]);

    const timelineSections = useMemo(
        () =>
            buildTimelineSections({
                todos: filteredTodos,
                locale,
                timeZone: userTZ?.timeZone,
                futureOnly: false,
                placesEarlierBeforeToday: true,
                includeEmptyDropTargets: dragActive,
                todayLabel: appDict("today"),
                tomorrowLabel: appDict("tomorrow"),
            }),
        [appDict, dragActive, filteredTodos, locale, userTZ?.timeZone],
    );

    const isSearching = Boolean(searchQuery.trim());
    // This page keeps its search field as the pinned bar, so the header below
    // renders only the block that scrolls away and docks its title into it —
    // the same split the floater list uses.
    const barSlots = useNativePageBarSlots();

    const listName = listMetaData[id]?.name?.trim() || "";
    const listColor = listMetaData[id]?.color;
    const listAccent = listColor
        ? listColorAccentColors[listColor]
        : nativeScreenAccentColors.all;
    const editableList = listMetaData[id]
        ? {
            id,
            name: listMetaData[id].name,
            color: listMetaData[id].color,
            iconKey: listMetaData[id].iconKey,
        }
        : null;
    const myRole = listMetaData[id]?.myRole ?? "OWNER";
    const isViewer = myRole === "VIEWER";
    const sharedByLabel = listMetaData[id]?.ownerUsername;
    const shareListAsText = useShareListAsText({ listName, todos: listTodos });

    return (
        <TodoMutationProvider
            useCompleteTodo={useCompleteListTodo}
            useDeleteTodo={useDeleteListTodo}
            useEditTodo={useEditListTodo}
            useEditTodoInstance={useEditListTodoInstance}
            usePrioritizeTodo={usePrioritizeListTodo}
            useReorderTodo={useReorderListTodo}
            readOnly={isViewer}
        >
            <div className="mb-20">
                <ScreenWatermark icon={getListIcon(listMetaData[id]?.iconKey)} color={listAccent} />
                {/* The list's own icon leads the header, so the edit/members
                    control moves into the pinned bar where the other screens
                    keep their actions. */}
                <MobileSearchHeader
                    searchQuery={searchQuery}
                    onSearchChange={setSearchQuery}
                    placeholder={
                        listName
                            ? `${appDict("searchIn")} ${listName}...`
                            : `${appDict("searchTasks")}...`
                    }
                    pageCollapse={{ ...barSlots, title: listName, accentColor: listAccent }}
                    // Same gate as the summary beside it, for the same reason: an
                    // empty list has nothing for a query to narrow, and the button
                    // would only raise a keyboard over the empty-state scene. The
                    // unsearched set, so a word that matches nothing keeps the field.
                    searchUnavailable={!listTodosLoading && listTodos.length === 0}
                    trailingAction={
                        <div className="flex shrink-0 items-center gap-2">
                            {/* Same gate the native list screens use: a summary is
                                only offered where there is something to summarize. */}
                            {listTodos.length > 0 ? (
                                <SummaryButton mode="list" listId={id} />
                            ) : null}
                            {editableList ? (
                                // One entry point per role: owners get the edit sheet
                                // (which hosts the Sharing section); members go straight
                                // to the members sheet.
                                <Button
                                    type="button"
                                    variant="ghost"
                                    size="icon"
                                    className="h-14 w-14 shrink-0 rounded-full border border-white/70 bg-card/90 text-foreground shadow-[0_12px_28px_-22px_hsl(var(--shadow)/0.55)] transition-all duration-200 hover:-translate-y-0.5 hover:bg-card dark:border-white/10"
                                    onClick={() =>
                                        myRole === "OWNER" ? setEditListOpen(true) : setMembersOpen(true)
                                    }
                                    aria-label={
                                        myRole === "OWNER" ? `Edit ${listName || "list"}` : appDict("members")
                                    }
                                >
                                    {myRole === "OWNER" ? (
                                        <Pencil className="h-6 w-6 stroke-[2.6]" />
                                    ) : (
                                        <Users className="h-6 w-6 stroke-[2.6]" />
                                    )}
                                </Button>
                            ) : null}
                        </div>
                    }
                />

                <NativePageHeader
                    title={listName}
                    accentColor={listAccent}
                    icon={getListIcon(listMetaData[id]?.iconKey)}
                    barSlots={barSlots}
                    beneathTitle={
                        sharedByLabel ? (
                            <p className="mt-1 flex items-center gap-1.5 px-1 text-xs font-black text-muted-foreground">
                                <Users className="h-3.5 w-3.5" />
                                {appDict("sharedBy", { name: sharedByLabel })}
                            </p>
                        ) : null
                    }
                />

                {/* Loading state */}
                {listTodosLoading && <TodoListLoading />}

                {/* Empty state — no tasks yet */}
                {!listTodosLoading && !isSearching && listTodos.length === 0 && (
                    <EmptyState
                        icon={getListIcon(listMetaData[id]?.iconKey)}
                        accentColor={listAccent}
                        title={appDict("listEmpty")}
                        description={appDict("listEmptyBody")}
                    />
                )}

                {/* Empty state — no search results */}
                {!listTodosLoading && isSearching && filteredTodos.length === 0 && (
                    <EmptyState
                        icon={Search}
                        accentColor={listAccent}
                        title={appDict("noMatchingTasks")}
                        description={appDict("searchEmptyBody")}
                        action={
                            <button
                                type="button"
                                onClick={() => setSearchQuery("")}
                                className="rounded-full border border-border/60 bg-card px-5 py-2.5 text-sm font-black text-foreground shadow-[0_12px_28px_-22px_hsl(var(--shadow)/0.55)] transition-transform hover:-translate-y-0.5"
                            >
                                {appDict("clearSearch")}
                            </button>
                        }
                    />
                )}

                {/* Date-bucketed timeline with drag-and-drop */}
                {!listTodosLoading && !(isSearching && filteredTodos.length === 0) && listTodos.length > 0 && (
                    <TimelineSections
                        sections={timelineSections}
                        timeZone={userTZ?.timeZone}
                        // A live query outranks a shut bucket: a list opens
                        // with Earlier closed, and a task the search turns up in
                        // there must not stay hidden behind its header. Native
                        // makes the same call.
                        earlierExpanded={earlierExpanded || isSearching}
                        onToggleEarlier={() => setEarlierExpanded((value) => !value)}
                        onDragActiveChange={setDragActive}
                    />
                )}
            </div>

            <ListFormSheet
                open={editListOpen}
                onOpenChange={setEditListOpen}
                list={editableList}
                // Collaborators need accounts; a local workspace has none, so the
                // Members entry disappears while plain-text sharing stays.
                onManageMembers={isLocalMode ? undefined : () => setMembersOpen(true)}
                onShareList={() => void shareListAsText()}
            />
            <ManageMembersSheet
                open={membersOpen}
                onOpenChange={setMembersOpen}
                listId={id}
                listType="list"
                listName={listName}
                myRole={myRole}
                onShareExternal={() => void shareListAsText()}
            />
        </TodoMutationProvider>
    );
};

export default ListContainer;
