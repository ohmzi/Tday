import { Hourglass, Loader2 } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";

type PendingApprovalDialogProps = {
  open: boolean;
  username?: string | null;
  isChecking?: boolean;
  onCheckStatus: () => void;
  onUseDifferentAccount: () => void;
};

// Mirrors the native (iOS/Android) "Waiting for approval" holding screen: an
// hourglass-titled, non-dismissible card with a primary "Check approval status"
// action and a "Use a different account" escape hatch beneath it.
export default function PendingApprovalDialog({
  open,
  username,
  isChecking = false,
  onCheckStatus,
  onUseDifferentAccount,
}: PendingApprovalDialogProps) {
  return (
    <Dialog open={open}>
      <DialogContent
        className="max-w-md rounded-3xl"
        // Non-dismissible — the only ways out are checking status (and getting
        // approved) or abandoning the account, matching the native apps.
        onInteractOutside={(event) => event.preventDefault()}
        onEscapeKeyDown={(event) => event.preventDefault()}
      >
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2 text-xl font-black">
            <Hourglass className="h-5 w-5 text-primary" strokeWidth={2.5} />
            Waiting for approval
          </DialogTitle>
          <DialogDescription className="pt-1 text-sm font-medium leading-relaxed">
            {username
              ? `Your account (${username}) is waiting for an administrator to approve it. We'll let you in as soon as it's approved.`
              : "Your account is waiting for an administrator to approve it. We'll let you in as soon as it's approved."}
          </DialogDescription>
        </DialogHeader>
        <div className="flex flex-col gap-1 pt-2">
          <Button
            onClick={() => void onCheckStatus()}
            disabled={isChecking}
            className="h-12 w-full rounded-full font-black"
          >
            {isChecking ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Checking…
              </>
            ) : (
              "Check approval status"
            )}
          </Button>
          <Button
            variant="ghost"
            onClick={onUseDifferentAccount}
            disabled={isChecking}
            className="w-full font-bold text-primary hover:text-primary"
          >
            Use a different account
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
