import { TodoItemType } from "@/types";
import { useEffect, useMemo, useRef, useState } from "react";
import { RRule } from "rrule";
import { useTranslation } from "react-i18next";
import ConfirmCancelEditDialog from "@/features/calendar/component/ConfirmationModals/ConfirmCancelEdit";
import ConfirmEditAllDialog from "@/features/calendar/component/ConfirmationModals/ConfirmEditAll";
import { useEditCalendarTodo } from "@/features/calendar/query/update-calendar-todo";
import { Modal, ModalOverlay, ModalContent } from "@/components/ui/Modal";
import { SheetHeader } from "@/components/ui/sheet-chrome";
import type { CalendarTaskFormState } from "@/features/calendar/hooks/useCalendarTaskFormState";
import { normalizeRrule } from "@/lib/normalize-rrule";
// The body is the lazy half of this screen; the sheet around it is not. See
// LazyCalendarTaskFormBody for why the split moved in here.
import CalendarTaskFormBody from "../LazyCalendarTaskFormBody";

type CalendarFormProps = {
  todo: TodoItemType;
  displayForm: boolean;
  setDisplayForm: React.Dispatch<React.SetStateAction<boolean>>;
  /** Owned by EditFormContainer so it outlives the 640 px modal/drawer swap. */
  form: CalendarTaskFormState;
};

const CalendarForm = ({ todo, displayForm, setDisplayForm, form }: CalendarFormProps) => {
  const { t: appDict } = useTranslation("app");
  const titleRef = useRef<HTMLDivElement | null>(null);

  const dateRangeChecksum = useMemo(() => todo.due.toISOString(), [todo.due]);
  const rruleChecksum = useMemo(() => todo.rrule, [todo.rrule]);

  const [cancelEditDialogOpen, setCancelEditDialogOpen] = useState(false);
  const [editAllDialogOpen, setEditAllDialogOpen] = useState(false);

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

  const nextRrule = rruleOptions ? new RRule(rruleOptions).toString() : null;
  const repeatChanged = nextRrule !== normalizeRrule(todo.rrule);

  const hasUnsavedChanges = useMemo(() => {
    return (
      title !== todo.title ||
      description !== (todo.description ?? "") ||
      priority !== todo.priority ||
      dateRange.to?.getTime() !== todo.due?.getTime() ||
      repeatChanged
    );
  }, [title, description, priority, dateRange, repeatChanged, todo]);

  const { editCalendarTodo, editTodoStatus } = useEditCalendarTodo();

  useEffect(() => {
    if (editTodoStatus === "success") {
      setDisplayForm(false);
    }
  }, [editTodoStatus, setDisplayForm]);

  const handleClose = () => {
    if (hasUnsavedChanges) {
      setCancelEditDialogOpen(true);
      return;
    }
    setDisplayForm(false);
  };

  const handleSubmit = () => {
    if (title.trim().length <= 0) return;
    // A repeat belongs to the whole series: one occurrence cannot carry a different rule.
    // Changing it therefore skips the this-or-all choice, whose "this occurrence" branch
    // would silently drop the new rule. The series is patched directly, with the same
    // checksums the "all" branch uses so the occurrence's date does not move the series.
    if (todo.rrule && !repeatChanged) {
      setEditAllDialogOpen(true);
    } else {
      editCalendarTodo({
        ...todo,
        dateRangeChecksum,
        rruleChecksum,
        rrule: nextRrule,
        title,
        description,
        priority,
        due: dateRange.to,
        listID,
      });
    }
  };

  return (
    <>
      <ConfirmCancelEditDialog
        cancelEditDialogOpen={cancelEditDialogOpen}
        setCancelEditDialogOpen={setCancelEditDialogOpen}
        setDisplayForm={setDisplayForm}
      />

      <ConfirmEditAllDialog
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

      <Modal
        open={displayForm}
        onOpenChange={(open) => {
          if (!open) handleClose();
        }}
      >
        <ModalOverlay>
          <ModalContent className="max-w-lg overflow-hidden p-0">
            <SheetHeader
              title={appDict("editTask")}
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
                priorityTouchedByUser={priorityTouchedByUser}
                setPriorityTouchedByUser={setPriorityTouchedByUser}
                dateRange={dateRange}
                setDateRange={setDateRange}
                listID={listID}
                setListID={setListID}
                rruleOptions={rruleOptions}
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

export default CalendarForm;
