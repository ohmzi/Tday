import { Calendar, CheckCircle2, Clock, Hourglass, Layers, Loader2, Star, Sun, TriangleAlert } from "lucide-react";
import { Button } from "@/components/ui/button";

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
        <div className="h-full w-full scale-[1.04] blur-xl">
          <MockHomeBackdrop />
        </div>
        <div className="absolute inset-0 bg-background/55" />
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

// Static, decorative stand-in for the home dashboard — colours/labels mirror
// NativeHomeDashboard's tiles. It only ever shows blurred, so the data is faked.
const MOCK_TILES = [
  { label: "Scheduled", color: "#D98F4B", count: 5, Icon: Clock },
  { label: "Priority", color: "#C97880", count: 2, Icon: Star },
  { label: "Overdue", color: "#E06F66", count: 1, Icon: TriangleAlert },
  { label: "All", color: "#68717A", count: 12, Icon: Layers },
  { label: "Completed", color: "#719F84", count: 8, Icon: CheckCircle2 },
  { label: "Calendar", color: "#9A89D2", count: null, Icon: Calendar },
] as const;

function MockHomeBackdrop() {
  return (
    <div className="flex h-full w-full gap-4 p-4 sm:p-6 lg:p-8">
      {/* Desktop-only sidebar rail. */}
      <aside className="hidden w-64 shrink-0 flex-col gap-3 rounded-3xl border border-border/60 bg-card/60 p-4 lg:flex">
        <div className="flex items-center gap-2 px-1">
          <Sun className="h-6 w-6 text-amber-400" />
          <span className="text-xl font-black text-foreground">{"T'Day"}</span>
        </div>
        <div className="mt-2 space-y-2">
          <div className="h-10 rounded-2xl bg-accent/20" />
          <div className="h-10 rounded-2xl bg-muted/60" />
          <div className="h-10 rounded-2xl bg-muted/60" />
          <div className="h-10 rounded-2xl bg-muted/60" />
        </div>
        <div className="mt-4 space-y-2 border-t border-border/60 pt-4">
          <div className="h-7 w-24 rounded-full bg-muted/60" />
          <div className="h-8 rounded-2xl bg-muted/50" />
          <div className="h-8 rounded-2xl bg-muted/50" />
        </div>
      </aside>

      {/* Main column — header, Today hero tile, category-tile grid. */}
      <div className="flex min-w-0 flex-1 flex-col gap-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Sun className="h-6 w-6 text-amber-400" />
            <span className="text-2xl font-black text-foreground">{"T'Day"}</span>
          </div>
          <div className="flex items-center gap-2">
            <div className="h-9 w-9 rounded-full bg-muted/70" />
            <div className="h-9 w-9 rounded-full bg-muted/70" />
            <div className="h-9 w-9 rounded-full bg-muted/70" />
          </div>
        </div>

        <div
          className="flex h-[70px] items-center justify-between rounded-[26px] px-5 text-white"
          style={{ backgroundColor: "#6EA8E1" }}
        >
          <span className="text-[1.38rem] font-black leading-none tracking-tight">Today</span>
          <span className="text-[2.1rem] font-black leading-none">4</span>
        </div>

        <div className="grid grid-cols-2 gap-2.5 lg:grid-cols-3">
          {MOCK_TILES.map(({ label, color, count, Icon }) => (
            <div
              key={label}
              className="relative min-h-[94px] overflow-hidden rounded-[26px] p-3 text-white"
              style={{ backgroundColor: color }}
            >
              <Icon className="pointer-events-none absolute -bottom-5 -right-4 h-20 w-20 text-white/15" strokeWidth={1.8} />
              <div className="relative flex h-full flex-col justify-between">
                <div className="flex items-start justify-between gap-3">
                  <Icon className="h-6 w-6 text-white" strokeWidth={2.5} />
                  {count != null && (
                    <span className="text-[1.72rem] font-black leading-none">{count}</span>
                  )}
                </div>
                <p className="truncate text-[1.28rem] font-black leading-6 tracking-tight">{label}</p>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
