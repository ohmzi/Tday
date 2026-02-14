"use client";

import React, { useState } from "react";
import { useTranslations } from "next-intl";
import TodoMutationProvider from "@/providers/TodoMutationProvider";
import { usePinTodo } from "../query/pin-todo";
import { useCompleteTodo } from "../query/complete-todo";
import { useDeleteTodo } from "../query/delete-todo";
import { usePrioritizeTodo } from "../query/prioritize-todo";
import { useEditTodo } from "../query/update-todo";
import { useEditTodoInstance } from "../query/update-todo-instance";
import { useReorderTodo } from "../query/reorder-todo";
import TodoFormContainer from "@/components/todo/component/TodoForm/TodoFormContainer";
import MobileSearchHeader from "@/components/ui/MobileSearchHeader";

export default function AddTaskPageContainer() {
  const [displayForm, setDisplayForm] = useState(true);
  const todayDict = useTranslations("today");

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
        <div className="max-w-[64rem]">
          <TodoFormContainer
            displayForm={true}
            setDisplayForm={setDisplayForm}
            persistent
          />
        </div>
      </div>
    </TodoMutationProvider>
  );
}
