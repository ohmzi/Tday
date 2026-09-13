import React from "react";
import { useDeleteCalendarTodo } from "@/features/calendar/query/delete-calendar-todo";
import { useDeleteCalendarInstanceTodo } from "@/features/calendar/query/delete-calendar-instance-todo";
import { TodoItemType } from "@/types";
import { Button } from "@/components/ui/button";
import { useTranslation } from "react-i18next";
import { Modal, ModalOverlay, ModalHeader, ModalTitle, ModalDescription, ModalContent, ModalFooter, useModalPresence } from "@/components/ui/Modal";

type ConfirmDeleteAllProp = {
  todo: TodoItemType;
  deleteAllDialogOpen: boolean;
  setDeleteAllDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
};

export default function ConfirmDeleteAll({
  todo,
  deleteAllDialogOpen,
  setDeleteAllDialogOpen,
}: ConfirmDeleteAllProp) {
  const { t: modalDict } = useTranslation("modal");
  const { deleteMutate } = useDeleteCalendarTodo();
  const { deleteInstanceMutate } = useDeleteCalendarInstanceTodo();
  // Held in the tree for the modal's exit rather than dropped on the frame the flag flips.
  // Modal's own portal already does this (`useModalPresence` in Modal.tsx); repeating the raw
  // `if (!open) return null` here would take the whole subtree away one level higher up and
  // undo it.
  const present = useModalPresence(deleteAllDialogOpen);
  if (!present) return null;
  return (
    <Modal open={deleteAllDialogOpen} onOpenChange={setDeleteAllDialogOpen}>
      <ModalOverlay>
        <ModalContent>
          <ModalHeader>
            <ModalTitle>
              {modalDict("deleteAll.title")}
            </ModalTitle>
            <ModalDescription>
              {modalDict("deleteAll.subtitle")}
            </ModalDescription>
          </ModalHeader>
          <ModalFooter>
            <Button
              variant={"outline"}
              className="bg-popover min-w-0"
              onClick={() => {
                deleteInstanceMutate(todo);
                setDeleteAllDialogOpen(false);
              }}
            >
              {modalDict("deleteAll.deleteInstance")}
            </Button>
            <Button
              variant={"destructive"}
              onClick={() => {
                deleteMutate(todo);
                setDeleteAllDialogOpen(false);
              }}
            >
              {modalDict("deleteAll.deleteAll")}
            </Button>
          </ModalFooter>
        </ModalContent>
      </ModalOverlay>
    </Modal>
  )
}