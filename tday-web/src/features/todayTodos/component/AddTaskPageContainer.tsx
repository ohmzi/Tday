import React, { useState } from "react";
import { useTranslation } from "react-i18next";
import TodoMutationProvider from "@/providers/TodoMutationProvider";
import { usePinTodo } from "../query/pin-todo";
import { useCompleteTodo } from "../query/complete-todo";
import { useDeleteTodo } from "../query/delete-todo";
import { usePrioritizeTodo } from "../query/prioritize-todo";
import { useEditTodo } from "../query/update-todo";
import { useEditTodoInstance } from "../query/update-todo-instance";
import { useReorderTodo } from "../query/reorder-todo";
import MobileSearchHeader from "@/components/ui/MobileSearchHeader";
import TaskFormSheet from "@/components/todo/component/TodoForm/TaskFormSheet";
import { Button } from "@/components/ui/button";

export default function AddTaskPageContainer() {
  const [displayForm, setDisplayForm] = useState(true);
  const { t: todayDict } = useTranslation("today");

  return (
    <TodoMutationProvider
      useCompleteTodo={useCompleteTodo}
      useDeleteTodo={useDeleteTodo}
      useEditTodo={useEditTodo}
      useEditTodoInstance={useEditTodoInstance}
      usePinTodo={usePinTodo}
      usePrioritizeTodo={usePrioritizeTodo}
      useReorderTodo={useReorderTodo}
    >
      <MobileSearchHeader />

      <div className="mt-8 sm:mt-4">
        <h3 className="mb-4 select-none text-2xl font-semibold tracking-tight">
          {todayDict("addATask")}
        </h3>
        <Button
          type="button"
          onClick={() => setDisplayForm(true)}
          className="rounded-2xl bg-accent px-5 font-black text-accent-foreground hover:bg-accent/90"
        >
          {todayDict("addATask")}
        </Button>
        <TaskFormSheet
          open={displayForm}
          onOpenChange={setDisplayForm}
          persistent
        />
      </div>
    </TodoMutationProvider>
  );
}
