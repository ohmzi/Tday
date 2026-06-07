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
  username?: string | null;
  onUseDifferentAccount?: () => void;
};

export default function PendingApprovalDialog({
  open,
  onOpenChange,
  username,
  onUseDifferentAccount,
}: PendingApprovalDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Waiting for approval</DialogTitle>
          <DialogDescription>
            {username
              ? `Your account (${username}) is registered and waiting for admin approval. Sign in once it's approved to get in.`
              : "Your account is registered and waiting for admin approval. Sign in once it's approved to get in."}
          </DialogDescription>
        </DialogHeader>
        <DialogFooter className="gap-2 sm:gap-2">
          {onUseDifferentAccount ? (
            <Button
              variant="ghost"
              onClick={onUseDifferentAccount}
              className="w-full sm:w-auto"
            >
              Use a different account
            </Button>
          ) : null}
          <Button onClick={() => onOpenChange(false)} className="w-full sm:w-auto">
            Got it
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
