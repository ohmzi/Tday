import { useEffect, useState } from "react";
import { useEditCalendarTodo } from "@/features/calendar/query/update-calendar-todo";
import { useEditCalendarTodoInstance } from "@/features/calendar/query/update-calendar-todo-instance";
import { TodoItemType } from "@/types";
import { Button } from "@/components/ui/button";
import { useTranslation } from "react-i18next";
import {
  Modal,
  ModalOverlay,
  ModalContent,
  ModalHeader,
  ModalTitle,
  ModalDescription,
  ModalFooter,
  useModalPresence,
} from "@/components/ui/Modal";

export type PendingReschedule = {
  // `rescheduled` already carries the new `due` produced by moveTodoToDay.
  rescheduled: TodoItemType;
  // Original due (ISO) so patchTodo flags `dateChanged` for the whole-series edit.
  originalDueIso: string;
  rruleChecksum: string | null;
};

type ConfirmRescheduleRecurringProps = {
  // Nullable, and mounted unconditionally by CalendarClient. It used to be non-null and
  // mounted only while a reschedule was pending, which meant the component was taken away on
  // the same frame the user answered it — no flag inside it could have saved the exit.
  pending: PendingReschedule | null;
  open: boolean;
  onClose: () => void;
};

export default function ConfirmRescheduleRecurring({
  pending,
  open,
  onClose,
}: ConfirmRescheduleRecurringProps) {
  const { t: modalDict } = useTranslation("modal");
  const { editCalendarTodo } = useEditCalendarTodo();
  const { editCalendarTodoInstance } = useEditCalendarTodoInstance();

  // `pending` is cleared the instant a button is pressed, and the card still has the modal's
  // exit left to play. Holding the last non-null payload keeps those frames rendering the
  // dialog the user is watching leave, rather than an empty one.
  const [retained, setRetained] = useState(pending);
  useEffect(() => {
    if (pending) setRetained(pending);
  }, [pending]);

  const present = useModalPresence(open);
  if (!present || !retained) return null;

  const { rescheduled, originalDueIso, rruleChecksum } = retained;

  return (
    <Modal open={open} onOpenChange={(next) => !next && onClose()}>
      <ModalOverlay>
        <ModalContent>
          <ModalHeader>
            <ModalTitle>{modalDict("editAll.title")}</ModalTitle>
            <ModalDescription>{modalDict("editAll.subtitle")}</ModalDescription>
          </ModalHeader>

          <ModalFooter className="mt-4">
            <Button
              variant="outline"
              className="w-full sm:w-auto"
              onClick={() => {
                editCalendarTodoInstance({
                  ...rescheduled,
                  instanceDate: rescheduled.instanceDate ?? rescheduled.due,
                });
                onClose();
              }}
            >
              {modalDict("editAll.editInstance")}
            </Button>
            <Button
              variant="destructive"
              className="w-full sm:w-auto"
              onClick={() => {
                editCalendarTodo({
                  ...rescheduled,
                  dateRangeChecksum: originalDueIso,
                  rruleChecksum,
                });
                onClose();
              }}
            >
              {modalDict("editAll.editAll")}
            </Button>
          </ModalFooter>
        </ModalContent>
      </ModalOverlay>
    </Modal>
  );
}
