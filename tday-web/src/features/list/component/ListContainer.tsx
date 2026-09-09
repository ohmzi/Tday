import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import TodoListLoading from "@/components/todo/component/TodoListLoading";
import TimelineSections from "@/components/todo/dnd/TimelineSections";
import TimelineEmptyState from "@/features/todayTodos/component/TimelineEmptyState";
import { buildTimelineSections, hasNonEarlierTimelineTodos } from "@/lib/timeline/buildTimelineSections";
import { useEarlierExpandHandoff } from "@/features/todayTodos/lib/useEarlierExpandHandoff";
import {
  TODAY_EARLIER_EXIT_MS,
  shouldShowTodayEmptyIllustration,
} from "@/features/todayTodos/lib/todayEarlierIllustration";
import { useCompleteListTodo } from "../query/complete-list-todo";
import { useDeleteListTodo } from "../query/delete-list-todo";
import { usePrioritizeListTodo } from "../query/prioritize-list-todo";
import { useEditListTodo } from "../query/update-list-todo";
import { useEditListTodoInstance } from "../query/update-list-todo-instance";
import { useReorderListTodo } from "../query/reorder-list-todo";
import TodoMutationProvider from "@/providers/TodoMutationProvider";
import TaskSelectionProvider from "@/providers/TaskSelectionProvider";
import BulkSelectButton from "@/components/todo/bulk/BulkSelectButton";
import { useList } from "../query/get-list-todos";
import { useListMetaData } from "@/components/Sidebar/List/query/get-list-meta";
import NativePageHeader, { useNativePageBarSlots } from "@/components/app/NativePageHeader";
import MobileSearchHeader from "@/components/ui/MobileSearchHeader";
import ScreenWatermark from "@/components/app/ScreenWatermark";
import EmptyState from "@/components/app/EmptyState";
import { taskJustCompleted } from "@/lib/task-completion-signal";
import { useCelebrateEmptyTransition } from "@/hooks/use-celebrate-empty-transition";
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
    // Earlier empty-state parity with Today/All/Priority/Scheduled: the same
    // hand-off state machine (`useEarlierExpandHandoff`'s own doc comment),
    // not a fresh `useState(false)` — a custom list's own Earlier bucket
    // (`buildTimelineSections`'s `kind: "earlier"` below) is exactly the same
    // display-time subset of `listTodos` those screens already read, so the
    // requirement-3 sequencing is the same interaction reused, not a new one.
    const {
        expanded: earlierExpanded,
        handoffPending: earlierHandoffPending,
        toggle: toggleEarlierExpanded,
    } = useEarlierExpandHandoff(TODAY_EARLIER_EXIT_MS);
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

    // The same "earlier" bucket `TimelineSections` renders below — reusing
    // `buildTimelineSections`'s own classification rather than a second
    // dayKey comparison (see `useTimelineEmptyState`'s doc comment on the All/
    // Priority/Scheduled screens for why that reuse matters). Every other
    // dated todo `buildTimelineSections` places is "current" by construction,
    // whether or not this list opened with a due date on every row.
    const earlierSection = useMemo(
        () => timelineSections.find((section) => section.kind === "earlier") ?? null,
        [timelineSections],
    );
    const hasEarlierItems = Boolean(earlierSection && earlierSection.todos.length > 0);
    const nonEarlierTodoCount = filteredTodos.length - (earlierSection?.todos.length ?? 0);
    const hasNonEarlierListTodos = nonEarlierTodoCount > 0;
    // Search-independent twin of `hasNonEarlierListTodos` above, built from
    // the RAW `listTodos` rather than `filteredTodos` — see
    // `hasNonEarlierTimelineTodos`'s own doc comment for why
    // `useCelebrateEmptyTransition` specifically needs this instead of the
    // search-filtered signal every other derivation above legitimately uses.
    const hasNonEarlierRawListTodos = useMemo(
        () =>
            hasNonEarlierTimelineTodos({
                todos: listTodos,
                locale,
                timeZone: userTZ?.timeZone,
                futureOnly: false,
                placesEarlierBeforeToday: true,
                includeEmptyDropTargets: false,
                todayLabel: appDict("today"),
                tomorrowLabel: appDict("tomorrow"),
            }),
        [appDict, listTodos, locale, userTZ?.timeZone],
    );
    // Remote sibling of `taskJustCompleted()` below — fires for a completion
    // on another device or by a collaborator, not just this tab's own tap.
    // Requirement 4: watches the non-Earlier count, so finishing every
    // current task still celebrates however many overdue tasks Earlier still
    // holds. Fed the raw signal, not `hasNonEarlierListTodos`: this ref-based
    // watcher has no notion of *why* its input changed, so a search query
    // must not be able to fake (or swallow) the empty transition it watches
    // for.
    const remoteEmptied = useCelebrateEmptyTransition(!hasNonEarlierRawListTodos);

    const isSearching = Boolean(searchQuery.trim());
    // Requirement 1, generalized from Today: zero non-Earlier tasks, not
    // loading, not mid-search — a list with only overdue tasks left still
    // earns the "all done" illustration, same as All/Priority/Scheduled.
    const showEmpty = !listTodosLoading && !isSearching && !hasNonEarlierListTodos;
    // Finishing the list is a payoff, not an absence: the confetti is for the
    // tick that emptied it, not for a list that was already empty. Hoisted so
    // the illustration/Earlier hand-off below reads the exact same signal —
    // see `shouldShowTodayEmptyIllustration`.
    const celebrate = taskJustCompleted() || remoteEmptied;
    // Requirements 1-3: who owns the empty-state slot once `showEmpty` is
    // true — the exact function Today/All/Priority/Scheduled call, reused
    // rather than a parallel List-only decision.
    const showEmptyIllustration = shouldShowTodayEmptyIllustration({
        showEmpty,
        hasEarlierItems,
        earlierExpanded,
        earlierHandoffPending,
        celebrate,
    });
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
            {/* `filteredTodos`, not the rendered sections: Select all covers
                everything the current search leaves standing, including rows
                inside a collapsed Earlier bucket. */}
            <TaskSelectionProvider
                rows={filteredTodos}
                readOnly={isViewer}
                // List rows carry no listID of their own, so a bulk move learns
                // where they came from from the screen instead.
                scopeListId={id}
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
                                {/* Selection mode is entered from an explicit button
                                    in the header cluster, never a long-press — that
                                    gesture belongs to drag-to-reschedule on the
                                    native clients and parity means one entry point. */}
                                <BulkSelectButton />
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
                                        className="h-14 w-14 shrink-0 rounded-full border border-white/70 bg-card/90 text-foreground shadow-[0_14px_30px_-16px_hsl(var(--shadow)/0.6)] transition-all duration-200 hover:-translate-y-0.5 hover:bg-card dark:border-white/10"
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

                    {/* Empty state — no current tasks (Earlier's own overdue
                        tasks, if any, render below via `TimelineSections`;
                        see `showEmptyIllustration`'s derivation above and
                        `AllTasksTimelineContainer`'s matching JSX-ordering
                        comment for why this renders BEFORE that block). */}
                    {showEmptyIllustration && (
                        <TimelineEmptyState
                            icon={getListIcon(listMetaData[id]?.iconKey)}
                            accentColor={listAccent}
                            isDayDone={false}
                            celebrate={celebrate}
                            earlierHandoffPending={earlierHandoffPending}
                            locale={locale}
                            emptyTitle="listEmpty"
                            emptyBody="listEmptyBody"
                            appDict={appDict}
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
                                    className="rounded-full border border-border/60 bg-card px-5 py-2.5 text-sm font-black text-foreground shadow-[0_14px_30px_-16px_hsl(var(--shadow)/0.6)] transition-transform hover:-translate-y-0.5"
                                >
                                    {appDict("clearSearch")}
                                </button>
                            }
                        />
                    )}

                    {/* Date-bucketed timeline with drag-and-drop — renders
                        whenever the list holds anything at all, Earlier's own
                        overdue tasks included, so a list with only overdue
                        tasks left still shows its (collapsed) Earlier header
                        under the illustration above. */}
                    {!listTodosLoading && !(isSearching && filteredTodos.length === 0) && listTodos.length > 0 && (
                        <TimelineSections
                            sections={timelineSections}
                            timeZone={userTZ?.timeZone}
                            // A live query outranks a shut bucket: a list opens
                            // with Earlier closed, and a task the search turns up in
                            // there must not stay hidden behind its header. Native
                            // makes the same call. `!earlierHandoffPending`: mid
                            // hand-off, Earlier's own rows stay hidden until the
                            // illustration above has actually finished exiting —
                            // requirement 3's sequencing, reused from Today.
                            earlierExpanded={(earlierExpanded && !earlierHandoffPending) || isSearching}
                            // Passes `showEmptyIllustration` through exactly like
                            // Today/All/Priority/Scheduled do: expanding Earlier
                            // hands off through the illustration first when it
                            // currently owns the slot (requirement 3), and stays
                            // the plain immediate toggle otherwise.
                            onToggleEarlier={() => toggleEarlierExpanded(showEmptyIllustration)}
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
            </TaskSelectionProvider>
        </TodoMutationProvider>
    );
};

export default ListContainer;
