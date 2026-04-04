import React, { useEffect, useState, lazy, Suspense } from "react";
import TodoCheckbox from "@/components/ui/TodoCheckbox";
import clsx from "clsx";
import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { TodoItemType } from "@/types";
import GripVertical from "@/components/ui/icon/gripVertical";
import TodoFormLoading from "./TodoForm/TodoFormLoading";
import { Check } from "lucide-react";
import { getDisplayDate } from "@/lib/date/displayDate";
import { useLocale } from "@/lib/navigation";
import { useTodoMutation } from "@/providers/TodoMutationProvider";
import { useListMetaData } from "@/components/Sidebar/List/query/get-list-meta";
import ListDot from "@/components/ListDot";
import TodoItemMenuContainer from "./TodoItem/TodoMenu/TodoItemMenuContainer";
import { useUserTimezone } from "@/features/user/query/get-timezone";
import { getTodoFocusElementId } from "@/lib/todoToastNavigation";

const TodoFormContainer = lazy(() => import("./TodoForm/TodoFormContainer"));


type TodoItemContainerProps = {
  todoItem: TodoItemType,
  overdue?: boolean,
  perTaskOverdue?: boolean,
  highlighted?: boolean,
}

type TodoItemCardProps = TodoItemContainerProps & {
  containerProps?: React.HTMLAttributes<HTMLDivElement> & Record<string, unknown>;
  dragging?: boolean;
  style?: React.CSSProperties;
  setDragNodeRef?: (node: HTMLDivElement | null) => void;
}

export const TodoItemCard = ({
  todoItem,
  overdue,
  perTaskOverdue,
  highlighted = false,
  containerProps,
  dragging = false,
  style,
  setDragNodeRef,
}: TodoItemCardProps) => {
  const { listMetaData } = useListMetaData();
  const { useCompleteTodo } = useTodoMutation();
  const { completeMutateFn } = useCompleteTodo();
  const locale = useLocale();
  const userTimeZone = useUserTimezone();
  const [itemElement, setItemElement] = useState<HTMLDivElement | null>(null);
  const { title, description, completed, priority, rrule } = todoItem;
  const isOverdue = overdue || (perTaskOverdue && !completed && todoItem.due < new Date());
  const itemListID = todoItem.listID;
  const [displayForm, setDisplayForm] = useState(false);
  const [editInstanceOnly, setEditInstanceOnly] = useState(false);
  const [showHandle, setShowHandle] = useState(false);

  const setCombinedRef = (node: HTMLDivElement | null) => {
    setItemElement(node);
    setDragNodeRef?.(node);
  };

  useEffect(() => {
    if (!displayForm) {
      setShowHandle(false);
    }
  }, [displayForm]);

  useEffect(() => {
    if (!highlighted || !itemElement) {
      return;
    }

    itemElement.scrollIntoView({ behavior: "smooth", block: "center" });
  }, [highlighted, itemElement]);

  if (displayForm)
    return (
      <Suspense fallback={<TodoFormLoading />}>
        <TodoFormContainer
          editInstanceOnly={editInstanceOnly}
          setEditInstanceOnly={setEditInstanceOnly}
          displayForm={true}
          setDisplayForm={setDisplayForm}
          todo={todoItem}
        />
      </Suspense>
    );
  return (
    <div
      id={getTodoFocusElementId(todoItem.id)}
      ref={setCombinedRef}
      style={style}
      {...containerProps}
      onDoubleClick={() => setDisplayForm(true)}
      onMouseOver={() => setShowHandle(true)}
      onMouseOut={() => setShowHandle(false)}
      className={clsx(
        "group relative flex max-w-full cursor-grab items-start justify-between gap-3 rounded-2xl border border-border/65 bg-card/95 px-3 py-3 shadow-[0_1px_2px_hsl(var(--shadow)/0.08)] transition-all duration-200 active:cursor-grabbing hover:border-border hover:shadow-[0_10px_24px_hsl(var(--shadow)/0.11)]",
        highlighted && "border-accent/55 ring-2 ring-accent/20 shadow-[0_14px_30px_-20px_hsl(var(--accent)/0.65)]",
        dragging
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
            <p className={clsx(isOverdue ? "text-red" : "text-lime")}>
              {getDisplayDate(todoItem.due, true, locale, userTimeZone?.timeZone)}
            </p>
            {itemListID &&
              <p className='flex items-center gap-1 rounded-full border border-border/70 bg-muted/70 px-2 py-[0.2rem] text-foreground/80'>
                <ListDot id={itemListID} className="shrink-0 text-sm" />
                <span className="max-w-14 truncate sm:max-w-24 md:max-w-52 lg:max-w-none">
                  {listMetaData[itemListID]?.name}
                </span>
              </p>
            }
            {isOverdue && (
              <p className='rounded-full border border-red/30 bg-red/10 px-2 py-[0.2rem] text-red font-medium'>
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

export const TodoItemContainer = ({
  todoItem,
  overdue,
  perTaskOverdue,
  highlighted = false,
}: TodoItemContainerProps) => {
  //dnd kit setups
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } =
    useSortable({ id: todoItem.id });
  const style = {
    transform: CSS.Translate.toString(transform),
    transition,
  };
  return (
    <TodoItemCard
      todoItem={todoItem}
      overdue={overdue}
      perTaskOverdue={perTaskOverdue}
      highlighted={highlighted}
      containerProps={{ ...attributes, ...listeners }}
      dragging={isDragging}
      style={style}
      setDragNodeRef={setNodeRef}
    />
  );

};
