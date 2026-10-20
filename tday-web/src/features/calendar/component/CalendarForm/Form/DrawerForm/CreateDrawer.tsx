import React, { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { RRule } from "rrule";
import { Drawer, DrawerContent, DrawerTitle } from "@/components/ui/drawer";
import { SheetHeader } from "@/components/ui/sheet-chrome";
import { useCreateCalendarTodo } from "@/features/calendar/query/create-calendar-todo";
import ConfirmCancelEditDrawer from "@/features/calendar/component/ConfirmationModals/ConfirmCancelEditDrawer";
import type { CalendarTaskFormState } from "@/features/calendar/hooks/useCalendarTaskFormState";
// The body is the lazy half of this screen; the sheet around it is not. See
// LazyCalendarTaskFormBody for why the split moved in here.
import CalendarTaskFormBody from "../LazyCalendarTaskFormBody";

type CreateCalendarFormProps = {
  start: Date;
  end: Date;
  displayForm: boolean;
  setDisplayForm: React.Dispatch<React.SetStateAction<boolean>>;
  /** Owned by CreateFormContainer so it outlives the 640 px modal/drawer swap. */
  form: CalendarTaskFormState;
};

export default function CreateCalendarDrawer({
  start,
  end,
  displayForm,
  setDisplayForm,
  form,
}: CreateCalendarFormProps) {
  // The slot dates only seed the draft, and that now happens in CreateFormContainer;
  // the drawer reads the seeded range back out of `form`.
  void start;
  void end;
  const { t: appDict } = useTranslation("app");
  const titleRef = useRef<HTMLDivElement | null>(null);

  const {
    title,
    setTitle,
    description,
    setDescription,
    priority,
    setPriority,
    priorityTouchedByUser,
    setPriorityTouchedByUser,
    dateRange,
    setDateRange,
    rruleOptions,
    setRruleOptions,
    listID,
    setListID,
    derivedRepeatType,
  } = form;

  const [cancelEditDialogOpen, setCancelEditDialogOpen] = useState(false);
  const { createCalendarTodo, createTodoStatus } = useCreateCalendarTodo();

  const hasUnsavedChanges = useMemo(
    () => title !== "" || description !== "" || priority !== "Low",
    [title, description, priority],
  );

  useEffect(() => {
    if (createTodoStatus === "success") setDisplayForm(false);
  }, [createTodoStatus, setDisplayForm]);

  const handleSubmit = () => {
    if (title.trim().length <= 0) return;
    createCalendarTodo({
      title,
      description,
      priority,
      due: dateRange.to,
      rrule: rruleOptions ? new RRule(rruleOptions).toString() : null,
      listID,
    });
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
          <DrawerTitle className="sr-only">{appDict("newTask")}</DrawerTitle>
          <SheetHeader
            title={appDict("newTask")}
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
              priorityTouchedByUser={priorityTouchedByUser}
              setPriorityTouchedByUser={setPriorityTouchedByUser}
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
