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
import NativePageTitle from "@/components/app/NativePageTitle";
import ScreenWatermark from "@/components/app/ScreenWatermark";
import { getListIcon } from "@/lib/listIcons";
import { listColorAccentColors, nativeScreenAccentColors } from "@/components/app/nativeScreenTheme";
import NativeAppBrandButton from "@/components/app/NativeAppBrandButton";
import ListDot from "@/components/ListDot";
import ListFormSheet from "@/components/Sidebar/List/ListFormSheet";
import ManageMembersSheet from "@/features/list/component/ManageMembersSheet";
import { useShareListAsText } from "@/hooks/use-share-list";
import { Button } from "@/components/ui/button";
import { useLocale } from "@/lib/navigation";
import { useUserTimezone } from "@/features/user/query/get-timezone";
import { Pencil, Search, Users, X } from "lucide-react";

const ListContainer = ({ id }: { id: string }) => {
    const locale = useLocale();
    const userTZ = useUserTimezone();
    const { t: appDict } = useTranslation("app");
    const { listMetaData } = useListMetaData();
    const { listTodos, listTodosLoading } = useList({ id });
    const [searchQuery, setSearchQuery] = useState("");
    const [editListOpen, setEditListOpen] = useState(false);
    const [membersOpen, setMembersOpen] = useState(false);
    const [earlierExpanded, setEarlierExpanded] = useState(false);

    const filteredTodos = useMemo(() => {
        const query = searchQuery.trim().toLowerCase();
        if (!query) return listTodos;
        return listTodos.filter((todo) => {
            const title = todo.title.toLowerCase();
            const description = (todo.description || "").toLowerCase();
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
                todayLabel: appDict("today"),
                tomorrowLabel: appDict("tomorrow"),
            }),
        [appDict, filteredTodos, locale, userTZ?.timeZone],
    );

    const isSearching = Boolean(searchQuery.trim());

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
                <header className="sticky top-0 z-40 flex w-full items-center justify-between gap-2.5 bg-background pt-[calc(0.5rem+env(safe-area-inset-top))] pb-1.5 lg:static lg:bg-transparent lg:pt-2 lg:pb-2">
                    <div aria-hidden className="pointer-events-none absolute inset-x-0 bottom-full h-screen bg-background lg:hidden" />
                    <NativeAppBrandButton className="min-w-0 max-w-[58%] sm:max-w-none" />
                </header>

                <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0 flex-1">
                        <NativePageTitle
                            title={listName}
                            accentColor={listAccent}
                            iconNode={<ListDot id={id} className="h-7 w-7 shrink-0" />}
                        />
                        {sharedByLabel ? (
                            <p className="mt-1 flex items-center gap-1.5 px-1 text-xs font-black text-muted-foreground">
                                <Users className="h-3.5 w-3.5" />
                                {appDict("sharedBy", { name: sharedByLabel })}
                            </p>
                        ) : null}
                    </div>
                    {editableList ? (
                        // One entry point per role: owners get the edit sheet
                        // (which hosts the Sharing section); members go straight
                        // to the members sheet.
                        <Button
                            type="button"
                            variant="ghost"
                            size="icon"
                            className="mt-4 h-14 w-14 shrink-0 rounded-full border border-white/70 bg-card/90 text-foreground shadow-[0_12px_28px_-22px_hsl(var(--shadow)/0.55)] transition-all duration-200 hover:-translate-y-0.5 hover:bg-card dark:border-white/10"
                            onClick={() =>
                                myRole === "OWNER" ? setEditListOpen(true) : setMembersOpen(true)
                            }
                            aria-label={
                                myRole === "OWNER" ? `Edit ${listName || "list"}` : appDict("members")
                            }
                        >
                            {myRole === "OWNER" ? (
                                <Pencil className="h-5 w-5" />
                            ) : (
                                <Users className="h-5 w-5" />
                            )}
                        </Button>
                    ) : null}
                </div>

                {/* Loading state */}
                {listTodosLoading && <TodoListLoading />}

                {/* Empty state — no tasks yet */}
                {!listTodosLoading && !isSearching && listTodos.length === 0 && (
                    <div className="flex min-h-[42vh] flex-col items-center justify-center text-center">
                        <p className="text-2xl font-black text-muted-foreground/70">
                            No tasks in this list
                        </p>
                    </div>
                )}

                {/* Empty state — no search results */}
                {!listTodosLoading && isSearching && filteredTodos.length === 0 && (
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
                            No matching tasks
                        </h3>
                        <p className="mb-6 text-sm text-muted-foreground">
                            Try different keywords or{" "}
                            <button
                                onClick={() => setSearchQuery("")}
                                className="text-accent hover:underline"
                            >
                                clear your search
                            </button>
                        </p>
                    </div>
                )}

                {/* Date-bucketed timeline with drag-and-drop */}
                {!listTodosLoading && !(isSearching && filteredTodos.length === 0) && listTodos.length > 0 && (
                    <TimelineSections
                        sections={timelineSections}
                        timeZone={userTZ?.timeZone}
                        earlierExpanded={earlierExpanded}
                        onToggleEarlier={() => setEarlierExpanded((value) => !value)}
                    />
                )}
            </div>

            <ListFormSheet
                open={editListOpen}
                onOpenChange={setEditListOpen}
                list={editableList}
                onManageMembers={() => setMembersOpen(true)}
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
