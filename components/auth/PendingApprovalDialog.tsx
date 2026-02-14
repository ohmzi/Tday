"use client";

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";

type PendingApprovalDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
};

export default function PendingApprovalDialog({
  open,
  onOpenChange,
}: PendingApprovalDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Registration received</DialogTitle>
          <DialogDescription>
            Your account is registered and waiting for admin approval. You will
            be able to sign in after approval is granted.
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button onClick={() => onOpenChange(false)} className="w-full sm:w-auto">
            Got it
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
