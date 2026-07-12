import React, { useEffect, useState } from "react";
import { Calendar as CalendarIcon, Flag, Leaf, List as ListIcon, Repeat } from "lucide-react";
import { RRule } from "rrule";
import { useToast } from "@/hooks/use-toast";
import { useTodoForm } from "@/providers/TodoFormProvider";
import { useTodoFormFocusAndAutosize } from "@/components/todo/hooks/useTodoFormFocusAndAutosize";
import { useKeyboardSubmitForm } from "@/components/todo/hooks/useKeyboardSubmitForm";
import { useClearInput } from "@/components/todo/hooks/useClearInput";
import { useTranslation } from "react-i18next";
import { useTodoMutation } from "@/providers/TodoMutationProvider";
import { useCreateTodo } from "@/features/todayTodos/query/create-todo";
import { useCreateFloater } from "@/features/floater/query/create-floater";
import { useFloaterListMetaData } from "@/features/floaterList/query/get-floater-list-meta";
import { useListMetaData } from "@/components/Sidebar/List/query/get-list-meta";
import {
  DueDateTimeControl,
  SheetCard,
  SheetDivider,
  SheetRow,
  SheetSectionTitle,
  SheetSelectorRow,
} from "@/components/ui/sheet-chrome";
import {
  CenteredSelectorOverlay,
  SelectorDivider,
  SelectorRow,
} from "@/components/ui/sheet-chrome/CenteredSelectorOverlay";
import ListDot from "@/components/ListDot";
import FloaterListDot from "@/features/floaterList/component/FloaterListDot";
import { GuideHelpLink } from "@/features/guide/GuideHelpLink";
import NLPTitleInput from "./NLPTitleInput";
import TaskSelectorOverlays, { type TaskSelector } from "./TodoFormSelectors";
import { priorityLabelKey, repeatLabelKey } from "./labels";
import { getPriorityFlag } from "@/lib/priority";

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
  const { createMutateFn: createFloaterMutateFn, createStatus: floaterStatus } =
    useCreateFloater();
  const { floaterListMetaData } = useFloaterListMetaData();
  const { t: appDict } = useTranslation("app");

  const isEditing = Boolean(todo?.id && todo.id !== "-1");
  // Schedule on = normal todo with a due date; off = unscheduled floater (separate entity).
  const [scheduled, setScheduled] = useState(true);
  const [floaterListID, setFloaterListID] = useState<string | null>(null);
  const [floaterListOpen, setFloaterListOpen] = useState(false);

  useEffect(() => {
    if (!displayForm) return;
    setScheduled(true);
    setFloaterListID(null);
    setFloaterListOpen(false);
  }, [displayForm, todo?.id]);

  useEffect(() => {
    if (!persistent && (createStatus === "success" || floaterStatus === "success")) {
      setDisplayForm(false);
    }
  }, [createStatus, floaterStatus, persistent, setDisplayForm]);

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
        <div className="flex items-start gap-2 px-[18px] pb-2 pt-3">
          <NLPTitleInput
            className="min-w-0 flex-1 text-lg font-black"
            title={title}
            setTitle={setTitle}
            titleRef={titleRef}
            setDateRange={setDateRange}
            onSubmit={() => handleForm()}
          />
          <GuideHelpLink topic="nlp-date-syntax" className="mt-0.5" />
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

      {/* Schedule toggle (create only) — off turns the task into a floater. */}
      {!isEditing && (
        <SheetCard>
          <button
            type="button"
            role="switch"
            aria-checked={scheduled}
            onClick={() => setScheduled((value) => !value)}
            className="flex w-full items-center gap-3 px-[18px] py-3 text-left"
          >
            {scheduled ? (
              <CalendarIcon className="h-5 w-5 shrink-0 text-muted-foreground" />
            ) : (
              <Leaf className="h-5 w-5 shrink-0 text-muted-foreground" />
            )}
            <span className="min-w-0 flex-1">
              <span className="block text-base font-black text-foreground">
                {appDict("schedule")}
              </span>
              <span className="block text-xs font-bold text-muted-foreground">
                {scheduled ? appDict("scheduleOnHint") : appDict("scheduleOffHint")}
              </span>
            </span>
            <span
              className={`relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors ${scheduled ? "bg-accent" : "bg-muted-foreground/30"}`}
            >
              <span
                className={`inline-block h-5 w-5 rounded-full bg-white shadow transition-transform ${scheduled ? "translate-x-[22px]" : "translate-x-[2px]"}`}
              />
            </span>
          </button>
        </SheetCard>
      )}

      {/* Schedule (only when scheduled) */}
      {scheduled && (
        <>
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
        </>
      )}

      {/* Details */}
      <SheetSectionTitle>{appDict("details")}</SheetSectionTitle>
      <SheetCard>
        {scheduled ? (
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
        ) : (
          <SheetSelectorRow
            icon={<ListIcon className="h-5 w-5" />}
            label={appDict("list")}
            ariaLabel={`${appDict("list")}, ${
              (floaterListID && floaterListMetaData[floaterListID]?.name?.trim()) ||
              appDict("noList")
            }`}
            value={
              floaterListID && floaterListMetaData[floaterListID]?.name?.trim() ? (
                <>
                  <FloaterListDot id={floaterListID} className="h-3.5 w-3.5" />
                  <span className="truncate">
                    {floaterListMetaData[floaterListID]?.name?.trim()}
                  </span>
                </>
              ) : (
                appDict("noList")
              )
            }
            onClick={() => setFloaterListOpen(true)}
          />
        )}
        <SheetDivider />
        <SheetSelectorRow
          icon={<Flag className="h-5 w-5" />}
          label={appDict("priority")}
          ariaLabel={`${appDict("priority")}, ${appDict(priorityLabelKey[priority])}`}
          value={
            <>
              {getPriorityFlag(priority) ? (
                <Flag
                  className={`h-3.5 w-3.5 shrink-0 ${getPriorityFlag(priority)!.className}`}
                />
              ) : null}
              <span className="truncate">{appDict(priorityLabelKey[priority])}</span>
            </>
          }
          onClick={() => setActive("priority")}
        />
        {scheduled && (
          <>
            <SheetDivider />
            <SheetSelectorRow
              icon={<Repeat className="h-5 w-5" />}
              label={appDict("repeat")}
              ariaLabel={`${appDict("repeat")}, ${repeatValueLabel}`}
              value={repeatValueLabel}
              onClick={() => setActive("repeat")}
            />
          </>
        )}
      </SheetCard>

      {/* Floater list picker (shown when schedule is off) */}
      <CenteredSelectorOverlay
        open={floaterListOpen}
        onOpenChange={(open) => !open && setFloaterListOpen(false)}
        title={appDict("list")}
      >
        <SelectorRow
          label={appDict("noList")}
          selected={floaterListID == null}
          onClick={() => {
            setFloaterListID(null);
            setFloaterListOpen(false);
          }}
        />
        {Object.entries(floaterListMetaData)
          .filter(([, meta]) => Boolean(meta.name?.trim()))
          .map(([id, meta]) => (
            <div key={id}>
              <SelectorDivider />
              <SelectorRow
                label={(meta.name ?? "").trim()}
                swatchNode={<FloaterListDot id={id} className="h-2.5 w-2.5" />}
                selected={floaterListID === id}
                onClick={() => {
                  setFloaterListID(id);
                  setFloaterListOpen(false);
                }}
              />
            </div>
          ))}
      </CenteredSelectorOverlay>

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
      } else if (!scheduled) {
        // Schedule off → create an unscheduled floater (separate entity).
        clearInput();
        createFloaterMutateFn({
          id: "-1",
          title,
          description: desc.trim() ? desc : null,
          priority,
          listID: floaterListID ?? null,
          pinned: false,
          completed: false,
          createdAt: new Date(),
          updatedAt: null,
          order: Number.MAX_VALUE,
          userID: null,
        });
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
