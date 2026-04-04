import LineSeparator from "@/components/ui/lineSeparator";
import {
    DropdownMenuItem,
    DropdownMenuSub,
    DropdownMenuSubContent,
    DropdownMenuSubTrigger
} from "@/components/ui/dropdown-menu";
import Spinner from "@/components/ui/spinner";
import { SquarePen, Blocks, ArrowRightLeft, Trash } from "lucide-react";
import Pin from "@/components/ui/icon/pin";
import Unpin from "@/components/ui/icon/unpin";
import { TodoItemType } from "@/types";
import { useTranslation } from "react-i18next";
import { useTodoMutation } from "@/providers/TodoMutationProvider";
import { useListMetaData } from "@/components/Sidebar/List/query/get-list-meta";
import ListDot from "@/components/ListDot";
import { Flag } from "lucide-react";

const PRESSED_MENU_ITEM_CLASS =
    "transition-[background-color,color,transform] duration-150 active:bg-accent/30 active:text-accent-foreground active:scale-[0.98]";
const PRESSED_PRIORITY_BUTTON_CLASS =
    "rounded-md p-1.5 transition-[background-color,transform] duration-150 hover:bg-accent/10 active:bg-accent/30 active:scale-95";

function TodoItemMeatballMenuContent({
    todo,
    setDisplayForm,
    setEditInstanceOnly,
}: {
    todo: TodoItemType;
    setDisplayForm: React.Dispatch<React.SetStateAction<boolean>>;
    setEditInstanceOnly: React.Dispatch<React.SetStateAction<boolean>>;
}) {
    const { t: todayDict } = useTranslation("today");
    const { t: appDict } = useTranslation("app");

    const { usePrioritizeTodo, useDeleteTodo, usePinTodo, useEditTodo } = useTodoMutation();
    const { prioritizeMutateFn } = usePrioritizeTodo();
    const { editTodoMutateFn } = useEditTodo();
    const { deleteMutateFn, deletePending } = useDeleteTodo();
    const { pinMutateFn } = usePinTodo();
    const { listMetaData } = useListMetaData();
    return (
        <>
            <DropdownMenuItem
                className={`mx-1 gap-1.5 p-1.5 px-2 ${PRESSED_MENU_ITEM_CLASS}`}
                onClick={() => pinMutateFn(todo)}
            >
                {!todo.pinned ? (
                    <Pin className="m-0" />
                ) : (
                    <Unpin />
                )}
                <p className="">{todo.pinned ? todayDict("menu.unpin")
                    : todayDict("menu.pinToTop")
                }
                </p>
            </DropdownMenuItem>
            <DropdownMenuItem
                className={`m-1.5 ${PRESSED_MENU_ITEM_CLASS}`}
                onClick={() => {
                    setDisplayForm((prev: boolean) => !prev);
                }}
            >
                <SquarePen strokeWidth={1.7} className="w-4! h-4!" />
                {todayDict("menu.edit")}
            </DropdownMenuItem>
            <DropdownMenuItem
                className={`m-1.5 ${PRESSED_MENU_ITEM_CLASS}`}
                onClick={() => {
                    setEditInstanceOnly(true);
                    setDisplayForm((prev: boolean) => !prev);
                }}
            >
                <Blocks strokeWidth={1.7} className="w-4! h-4!" />
                {todayDict("menu.editAsInstance")}

            </DropdownMenuItem>

            {/* move to list sub menu */}
            <DropdownMenuSub>
                <DropdownMenuSubTrigger className={`mx-1 bg-inherit hover:bg-popover ${PRESSED_MENU_ITEM_CLASS}`}>
                    <ArrowRightLeft strokeWidth={1.7} className="w-4! h-4!" />
                    {todayDict("menu.Move to")}
                </DropdownMenuSubTrigger>
                <DropdownMenuSubContent className="max-h-56 overflow-scroll">
                    {Object.entries(listMetaData).map(([key, value]) => {
                        const dateRangeChecksum = todo.due.toISOString();
                        const rruleChecksum = todo.rrule
                        return <DropdownMenuItem
                            key={key}
                            onClick={() => {
                                editTodoMutateFn({
                                    ...todo,
                                    listID: key,
                                    dateRangeChecksum,
                                    rruleChecksum
                                })
                            }}
                            className={PRESSED_MENU_ITEM_CLASS}
                        >
                            <ListDot id={key} className="text-base pr-0" />{value.name}
                        </DropdownMenuItem>
                    })}
                </DropdownMenuSubContent>
            </DropdownMenuSub>
            <DropdownMenuItem
                className={`m-1.5 ${PRESSED_MENU_ITEM_CLASS}`}
                onClick={() => deleteMutateFn(todo)}
            >
                {deletePending ? (
                    <Spinner className="w-4! h-4!" />
                ) : (
                    <Trash strokeWidth={1.7} className="w-4! h-4!" />
                )}
                {todayDict("menu.delete")}

            </DropdownMenuItem>
            <LineSeparator className=" my-3 w-full border-popover-border" />

            <DropdownMenuItem
                className="flex-col items-start hover:bg-inherit! cursor-default text-xs gap-4 pb-4"
                onClick={() => { }}
            >
                <p className="text-sm font-semibold text-card-foreground-muted ">
                    {appDict("priority")}
                </p>
                <div className=" flex gap-4 items-center pl-2">
                    <button className={`group cursor-pointer ${PRESSED_PRIORITY_BUTTON_CLASS}`}
                        onClick={() => {
                            prioritizeMutateFn({
                                id: todo.id,
                                level: "Low",
                                isRecurring: todo.rrule ? true : false,
                            });
                        }}>
                        <Flag
                            className="text-lime group-hover:fill-lime"
                        />
                    </button>

                    <button className={`group cursor-pointer ${PRESSED_PRIORITY_BUTTON_CLASS}`}
                        onClick={() => {
                            prioritizeMutateFn({
                                id: todo.id,
                                level: "Medium",
                                isRecurring: todo.rrule ? true : false,
                            });
                        }}>
                        <Flag
                            className="text-orange group-hover:fill-orange"
                        />
                    </button>
                    <button className={`group cursor-pointer ${PRESSED_PRIORITY_BUTTON_CLASS}`}
                        onClick={() => {
                            prioritizeMutateFn({
                                id: todo.id,
                                level: "High",
                                isRecurring: todo.rrule ? true : false,
                            });
                        }}>
                        <Flag
                            className="text-red group-hover:fill-red"
                        />
                    </button>
                </div>
            </DropdownMenuItem>
        </>

    );
}

export default TodoItemMeatballMenuContent;
