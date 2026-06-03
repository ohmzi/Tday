import React, { useEffect, useState } from "react";
import { Calendar as CalendarIcon, Flag, List as ListIcon, Repeat } from "lucide-react";
import { RRule } from "rrule";
import { useToast } from "@/hooks/use-toast";
import { useTodoForm } from "@/providers/TodoFormProvider";
import { useTodoFormFocusAndAutosize } from "@/components/todo/hooks/useTodoFormFocusAndAutosize";
import { useKeyboardSubmitForm } from "@/components/todo/hooks/useKeyboardSubmitForm";
import { useClearInput } from "@/components/todo/hooks/useClearInput";
import { useTranslation } from "react-i18next";
import { useTodoMutation } from "@/providers/TodoMutationProvider";
import { useCreateTodo } from "@/features/todayTodos/query/create-todo";
import { useListMetaData } from "@/components/Sidebar/List/query/get-list-meta";
import {
  DueDateTimeControl,
  SheetCard,
  SheetDivider,
  SheetRow,
  SheetSectionTitle,
  SheetSelectorRow,
} from "@/components/ui/sheet-chrome";
import ListDot from "@/components/ListDot";
import NLPTitleInput from "./NLPTitleInput";
import TaskSelectorOverlays, { type TaskSelector } from "./TodoFormSelectors";
import { priorityLabelKey, repeatLabelKey } from "./labels";

interface TodoFormProps {
  editInstanceOnly?: boolean;
  setEditInstanceOnly?: React.Dispatch<React.SetStateAction<boolean>>;
  displayForm: boolean;
  setDisplayForm: React.Dispatch<React.SetStateAction<boolean>>;
  persistent?: boolean;
  registerSubmit?: (submit: () => void) => void;
  onCanSubmitChange?: (canSubmit: boolean) => void;
}

const TodoForm = ({
  editInstanceOnly,
  setEditInstanceOnly,
  displayForm,
  setDisplayForm,
  persistent = false,
  registerSubmit,
  onCanSubmitChange,
}: TodoFormProps) => {
  const {
    todoItem: todo,
    title,
    setTitle,
    priority,
    setPriority,
    desc,
    setDesc,
    dateRange,
    setDateRange,
    listID,
    setListID,
    rruleOptions,
    setRruleOptions,
    derivedRepeatType,
    dateRangeChecksum,
    rruleChecksum,
  } = useTodoForm();

  const { titleRef } = useTodoFormFocusAndAutosize(displayForm);
  const [active, setActive] = useState<TaskSelector>(null);
  const { listMetaData } = useListMetaData();
  useKeyboardSubmitForm(displayForm, handleForm);
  const { toast } = useToast();
  const clearInput = useClearInput(setEditInstanceOnly, titleRef);
  const { useEditTodo, useEditTodoInstance } = useTodoMutation();
  const { editTodoMutateFn } = useEditTodo();
  const { editTodoInstanceMutateFn } = useEditTodoInstance(setEditInstanceOnly);
  const { createMutateFn, createStatus } = useCreateTodo();
  const { t: appDict } = useTranslation("app");

  useEffect(() => {
    if (!persistent && createStatus === "success") {
      setDisplayForm(false);
    }
  }, [createStatus, persistent, setDisplayForm]);

  // Bridge submit + title-empty state up to the native sheet header.
  useEffect(() => {
    registerSubmit?.(handleForm);
  });
  useEffect(() => {
    onCanSubmitChange?.(title.trim().length > 0);
  }, [title, onCanSubmitChange]);

  const repeatValueLabel = derivedRepeatType
    ? appDict(repeatLabelKey[derivedRepeatType])
    : appDict("noRepeat");
  const selectedListName = listID ? listMetaData[listID]?.name?.trim() : null;

  return (
    <div className="flex flex-col gap-3 pb-2">
      {/* Title + Notes */}
      <SheetCard>
        <div className="px-[18px] pb-2 pt-3">
          <NLPTitleInput
            className="text-lg font-black"
            title={title}
            setTitle={setTitle}
            titleRef={titleRef}
            setDateRange={setDateRange}
            onSubmit={handleForm}
          />
        </div>
        <SheetDivider />
        <input
          value={desc}
          onChange={(e) => setDesc(e.target.value)}
          name="description"
          placeholder={appDict("notes")}
          className="w-full bg-transparent px-[18px] py-3 text-base font-bold text-foreground placeholder:font-bold placeholder:text-muted-foreground/60 focus:outline-hidden"
        />
      </SheetCard>

      {/* Schedule */}
      <SheetSectionTitle>{appDict("schedule")}</SheetSectionTitle>
      <SheetCard>
        <SheetRow icon={<CalendarIcon className="h-5 w-5" />} label={appDict("due")}>
          <DueDateTimeControl
            due={dateRange.to}
            onDateClick={() => setActive("date")}
            onTimeClick={() => setActive("time")}
          />
        </SheetRow>
      </SheetCard>

      {/* Details */}
      <SheetSectionTitle>{appDict("details")}</SheetSectionTitle>
      <SheetCard>
        <SheetSelectorRow
          icon={<ListIcon className="h-5 w-5" />}
          label={appDict("list")}
          ariaLabel={`${appDict("list")}, ${selectedListName ?? appDict("noList")}`}
          value={
            listID && selectedListName ? (
              <>
                <ListDot id={listID} className="h-3.5 w-3.5" />
                <span className="truncate">{selectedListName}</span>
              </>
            ) : (
              appDict("noList")
            )
          }
          onClick={() => setActive("list")}
        />
        <SheetDivider />
        <SheetSelectorRow
          icon={<Flag className="h-5 w-5" />}
          label={appDict("priority")}
          ariaLabel={`${appDict("priority")}, ${appDict(priorityLabelKey[priority])}`}
          value={appDict(priorityLabelKey[priority])}
          onClick={() => setActive("priority")}
        />
        <SheetDivider />
        <SheetSelectorRow
          icon={<Repeat className="h-5 w-5" />}
          label={appDict("repeat")}
          ariaLabel={`${appDict("repeat")}, ${repeatValueLabel}`}
          value={repeatValueLabel}
          onClick={() => setActive("repeat")}
        />
      </SheetCard>

      <TaskSelectorOverlays
        active={active}
        setActive={setActive}
        dateRange={dateRange}
        setDateRange={setDateRange}
        priority={priority}
        setPriority={setPriority}
        listID={listID}
        setListID={setListID}
        setRruleOptions={setRruleOptions}
        derivedRepeatType={derivedRepeatType}
      />
    </div>
  );

  async function handleForm(e?: React.FormEvent) {
    if (e) e.preventDefault();
    if (title.trim().length <= 0) return;
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
