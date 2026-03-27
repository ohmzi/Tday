import React from "react";
import Spinner from "@/components/ui/spinner";
import { TodoItemType } from "@/types";
import { Button } from "@/components/ui/button";
import Pin from "@/components/ui/icon/pin";
import Edit from "@/components/ui/icon/edit";
import Trash from "@/components/ui/icon/trash";
import { useTodoMutation } from "@/providers/TodoMutationProvider";

const TodoItemSideMenu = ({
  todo,
  setDisplayForm,
}: {
  todo: TodoItemType;
  setDisplayForm: React.Dispatch<React.SetStateAction<boolean>>;
}) => {
  const { useDeleteTodo, usePinTodo } = useTodoMutation()
  const { deleteMutateFn, deletePending } = useDeleteTodo();
  const { pinMutateFn } = usePinTodo();
  return (
    <div className="hidden sm:flex items-center gap-1 text-muted-foreground">
      <Button
        variant={"outline"}
        size={"icon"}
        className="h-8 w-8 rounded-xl border-border/55 bg-card/70 hover:bg-sidebar-accent/70 hover:text-foreground"
        onClick={() => {
          pinMutateFn(todo);
        }}
      >
        <Pin className="w-6.5! h-6.5!" />
      </Button>

      <Button
        variant={"outline"}
        size={"icon"}
        className="h-8 w-8 rounded-xl border-border/55 bg-card/70 hover:bg-sidebar-accent/70 hover:text-foreground"
        onClick={() => setDisplayForm(true)}
      >
        <Edit className="w-[1.1rem] h-[1.1rem] " />
      </Button>
      <Button
        variant={"outline"}
        size={"icon"}
        className="h-8 w-8 rounded-xl border-border/55 bg-card/70 hover:bg-red/20 hover:text-red"
        onClick={() => {
          deleteMutateFn({ id: todo.id });
        }}
      >
        {deletePending ? (
          <Spinner className="w-4 h-4" />
        ) : (
          <Trash className="w-[1.1rem] h-[1.1rem]" />
        )}
      </Button>
    </div>
  );
};

export default TodoItemSideMenu;
