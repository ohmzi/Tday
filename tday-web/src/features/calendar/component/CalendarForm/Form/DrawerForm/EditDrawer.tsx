import React, { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { RRule } from "rrule";
import { TodoItemType } from "@/types";
import { Drawer, DrawerContent, DrawerTitle } from "@/components/ui/drawer";
import { SheetHeader } from "@/components/ui/sheet-chrome";
import { useEditCalendarTodo } from "@/features/calendar/query/update-calendar-todo";
import ConfirmEditAllDrawer from "@/features/calendar/component/ConfirmationModals/ConfirmEditAllDrawer";
import ConfirmCancelEditDrawer from "@/features/calendar/component/ConfirmationModals/ConfirmCancelEditDrawer";
import deriveRepeatType from "@/lib/deriveRepeatType";
import CalendarTaskFormBody from "../CalendarTaskFormBody";

type DrawerDateRange = { from: Date; to: Date };

type EditCalendarFormProps = {
  todo: TodoItemType;
  displayForm: boolean;
  setDisplayForm: React.Dispatch<React.SetStateAction<boolean>>;
};

export default function EditCalendarDrawer({
  todo,
  displayForm,
  setDisplayForm,
}: EditCalendarFormProps) {
  const { t: appDict } = useTranslation("app");
  const titleRef = useRef<HTMLDivElement | null>(null);

  const dateRangeChecksum = useMemo(() => todo.due.toISOString(), [todo.due]);
  const rruleChecksum = useMemo(() => todo.rrule, [todo.rrule]);

  const [cancelEditDialogOpen, setCancelEditDialogOpen] = useState(false);
  const [editAllDialogOpen, setEditAllDialogOpen] = useState(false);

  const [title, setTitle] = useState(todo.title);
  const [description, setDescription] = useState(todo.description ?? "");
  const [priority, setPriority] = useState(todo.priority);
  const [dateRange, setDateRange] = useState<DrawerDateRange>({
    from: todo.due,
    to: todo.due,
  });
  const [rruleOptions, setRruleOptions] = useState(
    todo?.rrule ? RRule.parseString(todo.rrule) : null,
  );
  const [listID, setListID] = useState<string | null>(todo.listID ?? null);
  const derivedRepeatType = deriveRepeatType({ rruleOptions });

  const hasUnsavedChanges = useMemo(() => {
    const rruleString = rruleOptions ? RRule.optionsToString(rruleOptions) : null;
    return (
      title !== todo.title ||
      description !== (todo.description ?? "") ||
      priority !== todo.priority ||
      dateRange.to?.getTime() !== todo.due?.getTime() ||
      rruleString !== (todo.rrule ?? null)
    );
  }, [title, description, priority, dateRange, rruleOptions, todo]);

  const { editCalendarTodo, editTodoStatus } = useEditCalendarTodo();

  useEffect(() => {
    if (editTodoStatus === "success") {
      setDisplayForm(false);
    }
  }, [editTodoStatus, setDisplayForm]);

  const handleSubmit = () => {
    if (title.trim().length <= 0) return;
    if (todo.rrule) {
      setEditAllDialogOpen(true);
    } else {
      editCalendarTodo({
        ...todo,
        rrule: rruleOptions ? new RRule(rruleOptions).toString() : null,
        title,
        description,
        priority,
        due: dateRange.to,
        listID,
      });
    }
  };

  const handleClose = () => {
    if (hasUnsavedChanges) {
      setCancelEditDialogOpen(true);
      return;
    }
    setDisplayForm(false);
  };

  return (
    <>
      <ConfirmCancelEditDrawer
        cancelEditDialogOpen={cancelEditDialogOpen}
        setCancelEditDialogOpen={setCancelEditDialogOpen}
        setDisplayForm={setDisplayForm}
      />
      <ConfirmEditAllDrawer
        todo={{
          ...todo,
          title,
          description,
          priority,
          due: dateRange.to,
          rrule: rruleOptions ? new RRule(rruleOptions).toString() : null,
          listID,
        }}
        rruleChecksum={rruleChecksum!}
        dateRangeChecksum={dateRangeChecksum}
        setDisplayForm={setDisplayForm}
        editAllDialogOpen={editAllDialogOpen}
        setEditAllDialogOpen={setEditAllDialogOpen}
      />

      <Drawer
        open={displayForm}
        onOpenChange={(open) => {
          if (!open) {
            handleClose();
          } else {
            setDisplayForm(true);
          }
        }}
      >
        <DrawerContent className="flex max-h-[92dvh] flex-col overflow-hidden rounded-t-[28px] border-white/70 shadow-[0_24px_70px_-34px_hsl(var(--shadow)/0.82)] dark:border-white/10 sm:left-1/2 sm:right-auto sm:w-[min(720px,calc(100vw-2rem))] sm:-translate-x-1/2">
          <DrawerTitle className="sr-only">{appDict("editTask")}</DrawerTitle>
          <SheetHeader
            title={appDict("editTask")}
            onClose={handleClose}
            onConfirm={handleSubmit}
            confirmDisabled={title.trim().length <= 0}
            confirmLabel={appDict("save")}
            closeLabel={appDict("cancel")}
          />
          <div className="min-h-0 flex-1 overflow-y-auto px-4 pb-6 sm:px-5">
            <CalendarTaskFormBody
              titleRef={titleRef}
              title={title}
              setTitle={setTitle}
              description={description}
              setDescription={setDescription}
              priority={priority}
              setPriority={setPriority}
              dateRange={dateRange}
              setDateRange={setDateRange}
              listID={listID}
              setListID={setListID}
              setRruleOptions={setRruleOptions}
              derivedRepeatType={derivedRepeatType}
              onSubmit={handleSubmit}
            />
          </div>
        </DrawerContent>
      </Drawer>
    </>
  );
}
