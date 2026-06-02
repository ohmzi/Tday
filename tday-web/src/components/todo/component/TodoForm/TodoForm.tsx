import clsx from "clsx";
import React, { useEffect, useState } from "react";
import adjustHeight from "@/components/todo/lib/adjustTextareaHeight";
import { useToast } from "@/hooks/use-toast";
import LineSeparator from "@/components/ui/lineSeparator";
import { useTodoForm } from "@/providers/TodoFormProvider";
import { useTodoFormFocusAndAutosize } from "@/components/todo/hooks/useTodoFormFocusAndAutosize";
import { useKeyboardSubmitForm } from "@/components/todo/hooks/useKeyboardSubmitForm";
import { useClearInput } from "@/components/todo/hooks/useClearInput";
import { RRule } from "rrule";
import TodoInlineActionBar from "./TodoInlineActionBar/TodoInlineActionBar";
import { Button } from "@/components/ui/button";
import NLPTitleInput from "./NLPTitleInput";
import { useTranslation } from "react-i18next";
import { useTodoMutation } from "@/providers/TodoMutationProvider";
import { useCreateTodo } from "@/features/todayTodos/query/create-todo";
import ListDropdownMenu from "./ListDropdownMenu";
interface TodoFormProps {
  editInstanceOnly?: boolean;
  setEditInstanceOnly?: React.Dispatch<React.SetStateAction<boolean>>;
  displayForm: boolean;
  setDisplayForm: React.Dispatch<React.SetStateAction<boolean>>;
  persistent?: boolean;
}

const TodoForm = ({
  editInstanceOnly,
  setEditInstanceOnly,
  displayForm,
  setDisplayForm,
  persistent = false,
}: TodoFormProps) => {
  const {
    todoItem: todo,
    title,
    setTitle,
    priority,
    desc,
    setDesc,
    dateRange,
    setDateRange,
    listID,
    setListID,
    rruleOptions,
    dateRangeChecksum,
    rruleChecksum,
  } = useTodoForm();

  //adjust height of the todo description based on content size
  const { titleRef, textareaRef } = useTodoFormFocusAndAutosize(displayForm);
  const [isFocused, setIsFocused] = useState(false);
  //submit form on ctrl + Enter
  useKeyboardSubmitForm(displayForm, handleForm);
  const { toast } = useToast();
  const clearInput = useClearInput(setEditInstanceOnly, titleRef);
  const { useEditTodo, useEditTodoInstance } = useTodoMutation();
  const { editTodoMutateFn } = useEditTodo();
  const { editTodoInstanceMutateFn } = useEditTodoInstance(setEditInstanceOnly);
  const { createMutateFn, createStatus } = useCreateTodo();
  const { t: appDict } = useTranslation("app");
  const { t: todayDict } = useTranslation("today")

  useEffect(() => {
    if (!persistent && createStatus === "success") {
      setDisplayForm(false);
    }
  }, [createStatus, persistent, setDisplayForm]);

  return (
    <div
      className="w-full"
      onClick={(e) => {
        e.stopPropagation();
      }}
    >
      <form
        onFocus={() => setIsFocused(true)}
        onSubmit={handleForm}
        onBlur={() => setIsFocused(false)}
        className={clsx(
          "flex w-full flex-col rounded-[24px] border border-white/70 bg-card/95 shadow-[0_18px_42px_-32px_hsl(var(--shadow)/0.62)] transition-colors dark:border-white/10",
          !displayForm && "hidden",
          isFocused ? "border-accent/55" : "border-white/70 dark:border-white/10",
        )}
      >
        <div className="mb-4 flex flex-col gap-3">
          <NLPTitleInput
            className="mt-5 px-3"
            title={title}
            setTitle={setTitle}
            titleRef={titleRef}
            setDateRange={setDateRange}
            onSubmit={handleForm}
          />

          <textarea
            value={desc}
            ref={textareaRef}
            onChange={(e) => {
              setDesc(e.target.value);
              adjustHeight(textareaRef);
            }}
            className="my-1 w-full resize-none overflow-hidden bg-transparent px-3 text-sm font-extrabold text-muted-foreground placeholder-muted-foreground/60 focus:outline-hidden"
            name="description"
            placeholder={appDict("descPlaceholder")}
          />
          {/* DateRange, Priority, and Repeat menus */}
          <TodoInlineActionBar />
        </div>
        <LineSeparator className="m-0! p-0!" />
        {/* form footer */}
        <div className="flex text-sm w-full justify-between items-center py-1.5 px-2">
          <ListDropdownMenu listID={listID} setListID={setListID} />
          <div className="flex gap-3 w-fit">
            <Button
              variant={"outline"}
              type="button"
              className="h-fit rounded-2xl border-border/65 bg-muted/70 px-4 py-[0.35rem]! font-black hover:bg-muted"
              onClick={() => {
                clearInput();
                if (!persistent) {
                  setDisplayForm(false);
                }
              }}
            >
              {appDict("cancel")}
            </Button>
            <Button
              type="submit"
              variant={"default"}
              disabled={title.length <= 0}
              className={clsx(
                "h-fit rounded-2xl bg-accent px-4 py-[0.35rem]! font-black text-accent-foreground shadow-sm hover:bg-accent/90",
                title.length <= 0 && "disabled opacity-40 cursor-not-allowed!",
              )}
            >
              <p title="ctrl+enter">
                {editInstanceOnly ? todayDict("saveInstance") : appDict("save")}
              </p>
            </Button>
          </div>

        </div>
      </form>
    </div>
  );

  async function handleForm(e?: React.FormEvent) {
    if (e) e.preventDefault();
    const due = dateRange.to;
    try {
      const rrule = rruleOptions ? new RRule(rruleOptions).toString() : null;
      if (todo?.id && todo.id != "-1") {
        if (!persistent) {
          setDisplayForm(false);
        }
        if (editInstanceOnly) {
          editTodoInstanceMutateFn({
            ...todo,
            title,
            description: desc,
            priority,
            due,
            rrule,
          });
        } else {
          editTodoMutateFn({
            ...todo,
            dateRangeChecksum,
            rruleChecksum,
            title,
            description: desc,
            priority,
            due,
            rrule,
            listID,
          });
        }
      } else {
        clearInput();
        createMutateFn({
          id: "-1",
          title,
          description: desc,
          priority,
          due,
          rrule,
          order: Number.MAX_VALUE,
          createdAt: new Date(),
          completed: false,
          pinned: false,
          timeZone: "",
          userID: "",
          exdates: [],
          instanceDate: rrule ? due : null,
          instances: [],
          listID: listID ?? null,
        });
      }
    } catch (error) {
      if (error instanceof Error)
        toast({ description: error.message, variant: "destructive" });
    }
  }
};

export default TodoForm;
