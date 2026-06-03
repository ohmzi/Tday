import React, { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Options, RRule } from "rrule";
import { TodoItemType } from "@/types";
import { useCreateCalendarTodo } from "@/features/calendar/query/create-calendar-todo";
import ConfirmCancelEditDialog from "@/features/calendar/component/ConfirmationModals/ConfirmCancelEdit";
import { Modal, ModalOverlay, ModalContent } from "@/components/ui/Modal";
import { SheetHeader } from "@/components/ui/sheet-chrome";
import deriveRepeatType from "@/lib/deriveRepeatType";
import CalendarTaskFormBody from "../CalendarTaskFormBody";

type ModalDateRange = { from: Date; to: Date };

type CreateCalendarFormProps = {
  start: Date;
  end: Date;
  displayForm: boolean;
  setDisplayForm: React.Dispatch<React.SetStateAction<boolean>>;
};

const CreateCalendarForm = ({
  start,
  end,
  displayForm,
  setDisplayForm,
}: CreateCalendarFormProps) => {
  void start;
  const { t: appDict } = useTranslation("app");
  const titleRef = useRef<HTMLDivElement | null>(null);

  const [cancelEditDialogOpen, setCancelEditDialogOpen] = useState(false);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [priority, setPriority] = useState<TodoItemType["priority"]>("Low");
  const [dateRange, setDateRange] = useState<ModalDateRange>({ from: end, to: end });
  const [rruleOptions, setRruleOptions] = useState<Partial<Options> | null>(null);
  const [listID, setListID] = useState<string | null>(null);
  const derivedRepeatType = deriveRepeatType({ rruleOptions });

  const { createCalendarTodo, createTodoStatus } = useCreateCalendarTodo();

  const hasUnsavedChanges = useMemo(() => {
    const rruleString = rruleOptions ? RRule.optionsToString(rruleOptions) : null;
    return (
      title !== "" ||
      description !== "" ||
      priority !== "Low" ||
      dateRange.to?.getTime() !== end.getTime() ||
      rruleString !== null
    );
  }, [rruleOptions, title, description, priority, dateRange, end]);

  useEffect(() => {
    if (createTodoStatus === "success") {
      setDisplayForm(false);
    }
  }, [createTodoStatus, setDisplayForm]);

  const handleClose = () => {
    if (hasUnsavedChanges) {
      setCancelEditDialogOpen(true);
      return;
    }
    setDisplayForm(false);
  };

  const handleSubmit = () => {
    if (title.trim().length <= 0) return;
    createCalendarTodo({
      title,
      description,
      priority,
      due: dateRange.to || end,
      rrule: rruleOptions ? new RRule(rruleOptions).toString() : null,
      listID,
    });
  };

  return (
    <>
      <ConfirmCancelEditDialog
        cancelEditDialogOpen={cancelEditDialogOpen}
        setCancelEditDialogOpen={setCancelEditDialogOpen}
        setDisplayForm={setDisplayForm}
      />

      <Modal
        open={displayForm}
        onOpenChange={(open) => {
          if (!open) handleClose();
        }}
      >
        <ModalOverlay>
          <ModalContent className="max-w-lg overflow-hidden p-0">
            <SheetHeader
              title={appDict("newTask")}
              onClose={handleClose}
              onConfirm={handleSubmit}
              confirmDisabled={title.trim().length <= 0}
              confirmLabel={appDict("save")}
              closeLabel={appDict("cancel")}
            />
            <div className="max-h-[80dvh] overflow-y-auto px-4 pb-5 sm:px-5">
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
          </ModalContent>
        </ModalOverlay>
      </Modal>
    </>
  );
};

export default CreateCalendarForm;
