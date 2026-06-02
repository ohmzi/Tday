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
} from "@/components/ui/Modal";

export type PendingReschedule = {
  // `rescheduled` already carries the new `due` produced by moveTodoToDay.
  rescheduled: TodoItemType;
  // Original due (ISO) so patchTodo flags `dateChanged` for the whole-series edit.
  originalDueIso: string;
  rruleChecksum: string | null;
};

type ConfirmRescheduleRecurringProps = {
  pending: PendingReschedule;
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

  if (!open) return null;

  const { rescheduled, originalDueIso, rruleChecksum } = pending;

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
