import { Hourglass, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import MockHomeBackdrop from "@/components/auth/MockHomeBackdrop";

type PendingApprovalScreenProps = {
  open: boolean;
  username?: string | null;
  isChecking?: boolean;
  onCheckStatus: () => void;
  onUseDifferentAccount: () => void;
};

// Full-screen "Waiting for approval" takeover, mirroring the native apps: the home
// dashboard sits blurred behind a centered, non-dismissible card. A pending account
// has no session/data, so the backdrop is a static mock of the home (desktop on lg,
// the native category-tile layout on mobile) — the same trick the login screen uses.
export default function PendingApprovalScreen({
  open,
  username,
  isChecking = false,
  onCheckStatus,
  onUseDifferentAccount,
}: PendingApprovalScreenProps) {
  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-background px-4"
      role="dialog"
      aria-modal="true"
      aria-label="Waiting for approval"
    >
      {/* Blurred, dimmed mock home behind the card. */}
      <div className="absolute inset-0 overflow-hidden" aria-hidden>
        <div className="h-full w-full scale-[1.06] blur-lg">
          <MockHomeBackdrop />
        </div>
        <div className="absolute inset-0 bg-background/45" />
      </div>

      <div className="relative z-10 w-full max-w-md rounded-3xl border border-border bg-background p-6 shadow-2xl">
        <div className="flex items-center gap-2">
          <Hourglass className="h-5 w-5 text-primary" strokeWidth={2.5} />
          <h2 className="text-xl font-black text-foreground">Waiting for approval</h2>
        </div>
        <p className="mt-2 text-sm font-medium leading-relaxed text-muted-foreground">
          {username
            ? `Your account (${username}) is waiting for an administrator to approve it. We'll let you in as soon as it's approved.`
            : "Your account is waiting for an administrator to approve it. We'll let you in as soon as it's approved."}
        </p>
        <div className="mt-5 flex flex-col gap-1">
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
      </div>
    </div>
  );
}
