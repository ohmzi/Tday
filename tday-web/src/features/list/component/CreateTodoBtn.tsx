import React, { useEffect, useState } from "react";
import Plus from "@/components/ui/icon/plus";
import { useTranslation } from "react-i18next";
import TaskFormSheet from "@/components/todo/component/TodoForm/TaskFormSheet";
import { hapticButtonTap } from "@/lib/haptics";

const CreateTodoBtn = ({ listID }: { listID?: string }) => {
  const { t: todayDict } = useTranslation("today");
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
    <div className="sticky -top-20 mt-6 mb-8 sm:mt-8 sm:mb-10 lg:mt-10 ml-[2px]">
      {/* add more icon */}
      <button
        type="button"
        onClick={() => { hapticButtonTap(); setDisplayForm(!displayForm); }}
        className="group flex w-fit cursor-pointer items-center gap-3 rounded-[20px] border border-accent/35 bg-accent/10 px-4 py-2.5 text-accent shadow-[0_12px_30px_-28px_hsl(var(--accent)/0.7)] transition-all duration-200 hover:-translate-y-0.5 hover:border-accent/55 hover:bg-accent hover:text-accent-foreground active:translate-y-0 active:border-accent active:bg-accent active:text-accent-foreground"
      >
        <Plus className="h-5 w-5 transition-colors duration-200 group-hover:stroke-accent-foreground group-active:stroke-accent-foreground" />
        <p className="text-[0.95rem] font-black text-accent transition-colors group-hover:text-accent-foreground group-active:text-accent-foreground">
          {todayDict("addATask")}
        </p>
      </button>

      <TaskFormSheet
        open={displayForm}
        onOpenChange={setDisplayForm}
        overrideFields={{ listID }}
      />
    </div>
  );
};

export default CreateTodoBtn;
