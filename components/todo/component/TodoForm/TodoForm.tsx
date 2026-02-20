import clsx from "clsx";
import React, { useState } from "react";
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
import { useTranslations } from "next-intl";
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
    durationMinutes
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
  const { createMutateFn } = useCreateTodo();
  const appDict = useTranslations("app");
  const todayDict = useTranslations("today")

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
          "flex w-full flex-col rounded-2xl border bg-card shadow-[0_8px_24px_hsl(var(--shadow)/0.11)] transition-colors",
          !displayForm && "hidden",
          isFocused ? "border-ring/80" : "border-border/70",
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
            className="my-1 w-full resize-none overflow-hidden bg-transparent px-3 font-light placeholder-muted-foreground focus:outline-hidden"
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
              className="h-fit border-border/65 bg-muted/70 py-[0.3rem]! hover:bg-muted"
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
                "h-fit py-[0.3rem]! shadow-sm",
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
    const dtstart = dateRange.from;
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
            dtstart,
            due,
            durationMinutes,
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
            dtstart,
            due,
            durationMinutes,
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
          dtstart,
          due,
          durationMinutes,
          rrule,
          order: Number.MAX_VALUE,
          createdAt: new Date(),
          completed: false,
          pinned: false,
          timeZone: "",
          userID: "",
          exdates: [],
          instanceDate: rrule ? dtstart : null,
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
