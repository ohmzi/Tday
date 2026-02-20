import { useEffect, useState } from "react";
import dynamic from "next/dynamic";
import TodoCheckbox from "@/components/ui/TodoCheckbox";
import clsx from "clsx";
import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { TodoItemType } from "@/types";
import GripVertical from "@/components/ui/icon/gripVertical";
import TodoFormLoading from "./TodoForm/TodoFormLoading";
import { Check } from "lucide-react";
import { getDisplayDate } from "@/lib/date/displayDate";
import { useLocale } from "next-intl";
import { useTodoMutation } from "@/providers/TodoMutationProvider";
import { useListMetaData } from "@/components/Sidebar/List/query/get-list-meta";
import ListDot from "@/components/ListDot";
import TodoItemMenuContainer from "./TodoItem/TodoMenu/TodoItemMenuContainer";
import { useUserTimezone } from "@/features/user/query/get-timezone";

const TodoFormContainer = dynamic(
  () => import("./TodoForm/TodoFormContainer"),
  { loading: () => <TodoFormLoading /> },
);


type TodoItemContainerProps = {
  todoItem: TodoItemType,
  overdue?: boolean
}

export const TodoItemContainer = ({ todoItem, overdue }: TodoItemContainerProps) => {
  const { listMetaData } = useListMetaData();
  const { useCompleteTodo } = useTodoMutation();
  const { completeMutateFn } = useCompleteTodo();
  const locale = useLocale();
  const userTimeZone = useUserTimezone();
  //dnd kit setups
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } =
    useSortable({ id: todoItem.id });
  const style = {
    transform: CSS.Translate.toString(transform),
    transition,
  };
  const { title, description, completed, priority, rrule, dtstart } = todoItem;
  const itemListID = todoItem.listID;
  const [displayForm, setDisplayForm] = useState(false);
  const [editInstanceOnly, setEditInstanceOnly] = useState(false);
  const [showHandle, setShowHandle] = useState(false);


  useEffect(() => {
    if (!displayForm) {
      setShowHandle(false);
    }
  }, [displayForm]);


  if (displayForm)
    return (
      <TodoFormContainer
        editInstanceOnly={editInstanceOnly}
        setEditInstanceOnly={setEditInstanceOnly}
        displayForm={true}
        setDisplayForm={setDisplayForm}
        todo={todoItem}
      />
    );
  return (
    <div
      ref={setNodeRef}
      style={style}
      {...attributes}
      {...listeners}
      onDoubleClick={() => setDisplayForm(true)}
      onMouseOver={() => setShowHandle(true)}
      onMouseOut={() => setShowHandle(false)}
      className={clsx(
        "group relative flex max-w-full cursor-grab items-start justify-between gap-3 rounded-2xl border border-border/65 bg-card/95 px-3 py-3 shadow-[0_1px_2px_hsl(var(--shadow)/0.08)] transition-all duration-200 active:cursor-grabbing hover:border-border hover:shadow-[0_10px_24px_hsl(var(--shadow)/0.11)]",
        isDragging
          ? "z-30 border-border/70 bg-card/95 shadow-2xl opacity-85 touch-manipulation"
          : "shadow-[0_1px_2px_hsl(var(--shadow)/0.08)]",
      )}
    >
      <div
        className={clsx(
          "absolute bottom-1/2 -left-5 hidden translate-y-1/2 p-1 transition-colors sm:block",
          showHandle ? "text-muted-foreground" : "text-transparent",
        )}
      >
        <GripVertical className="h-4 w-4" />
      </div>

      <div className="flex items-start gap-3">
        <div className="pt-0.5">
          <TodoCheckbox
            icon={Check}
            priority={priority}
            complete={completed}
            onChange={() => completeMutateFn(todoItem)}
            checked={completed}
            variant={rrule ? "repeat" : "outline-solid"}
          />
        </div>

        <div className="max-w-full">
          <p className="mb-2 select-none leading-none text-foreground">
            {title}
          </p>
          <pre className="w-48 whitespace-pre-wrap pb-2 text-xs text-muted-foreground sm:w-full sm:text-sm">
            {description}
          </pre>
          <div className="flex flex-wrap items-center justify-start gap-2 text-xs sm:text-sm">
            <p className={clsx(overdue ? "text-orange" : "text-lime")}>
              {getDisplayDate(dtstart, true, locale, userTimeZone?.timeZone)}
            </p>
            {itemListID &&
              <p className='flex items-center gap-1 rounded-full border border-border/70 bg-muted/70 px-2 py-[0.2rem] text-foreground/80'>
                <ListDot id={itemListID} className="shrink-0 text-sm" />
                <span className="max-w-14 truncate sm:max-w-24 md:max-w-52 lg:max-w-none">
                  {listMetaData[itemListID]?.name}
                </span>
              </p>
            }
            {overdue && (
              <p className='rounded-full border border-border/70 bg-muted/70 px-2 py-[0.2rem] text-orange'>
                overdue
              </p>
            )}
          </div>
        </div>
      </div>

      <div>
        <TodoItemMenuContainer
          displayMenu={showHandle}
          className={clsx("flex items-center gap-2 transition-opacity", !showHandle && "opacity-0")}
          todo={todoItem}
          setDisplayForm={setDisplayForm}
          setEditInstanceOnly={setEditInstanceOnly}
        />
      </div>
    </div>
  );

};
