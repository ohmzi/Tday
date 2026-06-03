import React, { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Options, RRule } from "rrule";
import { TodoItemType } from "@/types";
import { Drawer, DrawerContent, DrawerTitle } from "@/components/ui/drawer";
import { SheetHeader } from "@/components/ui/sheet-chrome";
import { useCreateCalendarTodo } from "@/features/calendar/query/create-calendar-todo";
import ConfirmCancelEditDrawer from "@/features/calendar/component/ConfirmationModals/ConfirmCancelEditDrawer";
import deriveRepeatType from "@/lib/deriveRepeatType";
import CalendarTaskFormBody from "../CalendarTaskFormBody";

type DrawerDateRange = { from: Date; to: Date };

type CreateCalendarFormProps = {
  start: Date;
  end: Date;
  displayForm: boolean;
  setDisplayForm: React.Dispatch<React.SetStateAction<boolean>>;
};

export default function CreateCalendarDrawer({
  start,
  end,
  displayForm,
  setDisplayForm,
}: CreateCalendarFormProps) {
  void start;
  const { t: appDict } = useTranslation("app");
  const titleRef = useRef<HTMLDivElement | null>(null);

  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [priority, setPriority] = useState<TodoItemType["priority"]>("Low");
  const [dateRange, setDateRange] = useState<DrawerDateRange>({ from: end, to: end });
  const [rruleOptions, setRruleOptions] = useState<Partial<Options> | null>(null);
  const [listID, setListID] = useState<string | null>(null);
  const derivedRepeatType = deriveRepeatType({ rruleOptions });

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
