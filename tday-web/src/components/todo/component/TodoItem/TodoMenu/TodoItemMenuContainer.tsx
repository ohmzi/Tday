import React, { lazy, Suspense } from "react";
import TodoItemMeatballMenu from "./TodoItemMeatballMenu";
import { TodoItemType } from "@/types";
import InlineMenuLoading from "../../Loading/InlineMenuLoading";
const TodoItemSideMenu = lazy(() => import("./TodoItemSideMenu"));

const TodoItemMenuContainer = ({
  className,
  todo,
  setDisplayForm,
  setEditInstanceOnly,
  displayMenu,
  ...props
}: {
  className?: string;
  todo: TodoItemType;
  setDisplayForm: React.Dispatch<React.SetStateAction<boolean>>;
  setEditInstanceOnly: React.Dispatch<React.SetStateAction<boolean>>;
  displayMenu: boolean
}) => {
  return (
    <div
      className={className}
      onPointerDown={(e) => {
        e.stopPropagation();
      }}
      onMouseDown={(e) => {
        e.stopPropagation();
      }}
      {...props}
    >
      {displayMenu && (
        <Suspense fallback={<InlineMenuLoading />}>
          <TodoItemSideMenu setDisplayForm={setDisplayForm} todo={todo} />
        </Suspense>
      )}

      <TodoItemMeatballMenu
        setDisplayForm={setDisplayForm}
        setEditInstanceOnly={setEditInstanceOnly}
        todo={todo}
      />
    </div>
  );
};
export default TodoItemMenuContainer;
