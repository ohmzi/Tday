"use client";
import React, { useEffect, useState } from "react";
import dynamic from "next/dynamic";
import Plus from "@/components/ui/icon/plus";
import TodoFormLoading from "../../../components/todo/component/TodoForm/TodoFormLoading";
import { useTranslations } from "next-intl";
const TodoForm = dynamic(
  () => import("../../../components/todo/component/TodoForm/TodoFormContainer"),
  { loading: () => <TodoFormLoading /> },
);

const CreateTodoBtn = ({ projectID }: { projectID?: string }) => {
  const todayDict = useTranslations("today");
  const [displayForm, setDisplayForm] = useState(false);
  useEffect(() => {
    const showCreateTodoForm = (e: KeyboardEvent) => {
      if (
        (e.target as HTMLElement)?.isContentEditable ||
        ["INPUT", "TEXTAREA"].includes((e.target as HTMLElement)?.tagName)
      )
        return;
      if (e.key.toLocaleLowerCase() === "q") {
        e.preventDefault();
        setDisplayForm(true);
      }
      return;
    };


    document.addEventListener("keydown", showCreateTodoForm);
    return () => {
      document.removeEventListener("keydown", showCreateTodoForm);
    };
  }, []);
  return (
    <div className="sticky -top-20 my-10 ml-0.5">
      {/* add more icon */}
      <button
        onClick={() => setDisplayForm(!displayForm)}
        className="group flex w-fit items-center gap-3 rounded-2xl border-2 border-dashed border-accent/40 bg-accent/5 px-4 py-2.5 text-accent transition-all duration-200 hover:cursor-pointer hover:border-accent hover:bg-accent hover:text-accent-foreground"
      >
        <Plus className="mb-[2px] -ml-1 h-5 w-5 transition-transform duration-200 group-hover:rotate-90 group-hover:stroke-accent-foreground" />
        <p className="text-[0.95rem] font-medium text-accent transition-colors group-hover:text-accent-foreground">
          {todayDict("addATask")}
        </p>
      </button>

      {/* form */}
      {displayForm && (
        <div className="mt-3">
          <TodoForm displayForm={displayForm} setDisplayForm={setDisplayForm} overrideFields={{ projectID }} />
        </div>
      )}
    </div>
  );
};

export default CreateTodoBtn;
