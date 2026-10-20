import React from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
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

type confirmCancelEditProp = {
  cancelEditDialogOpen: boolean;
  setCancelEditDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
  setDisplayForm: React.Dispatch<React.SetStateAction<boolean>>;
};

export default function ConfirmCancelEdit({
  cancelEditDialogOpen,
  setCancelEditDialogOpen,
  setDisplayForm,
}: confirmCancelEditProp) {
  const { t: modalDict } = useTranslation("modal");

  // Held in the tree for the modal's exit rather than dropped on the frame the flag flips.
  // Modal's own portal already does this (`useModalPresence` in Modal.tsx); repeating the raw
  // `if (!open) return null` here would take the whole subtree away one level higher up and
  // undo it.
  const present = useModalPresence(cancelEditDialogOpen);
  if (!present) return null;

  return (
    <Modal open={cancelEditDialogOpen} onOpenChange={setCancelEditDialogOpen}>
      <ModalOverlay>
        <ModalContent>
          <ModalHeader>
            <ModalTitle>{modalDict("cancelEdit.title")}</ModalTitle>
            <ModalDescription>
              {modalDict("cancelEdit.subtitle")}
            </ModalDescription>
          </ModalHeader>

          <ModalFooter className="mt-4">
            <Button
              variant="outline"
              className="w-full sm:w-auto bg-popover"
              onClick={() => setCancelEditDialogOpen(false)}
              onMouseDown={(e) => { e.stopPropagation(); e.preventDefault() }}
            >
              {modalDict("cancel")}
            </Button>
            <Button
              variant="destructive"
              className="w-full sm:w-auto"
              onMouseDown={(e) => { e.stopPropagation(); e.preventDefault() }}
              onClick={() => {
                setCancelEditDialogOpen(false);
                setDisplayForm(false);
              }}
            >
              {modalDict("confirm")}
            </Button>
          </ModalFooter>
        </ModalContent>
      </ModalOverlay>
    </Modal>
  );
}