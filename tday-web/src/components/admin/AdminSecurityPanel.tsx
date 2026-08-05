import { useCallback, useEffect, useState, type ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { Loader2, ShieldAlert, ShieldCheck, ShieldOff } from "lucide-react";
import { useToast } from "@/hooks/use-toast";
import { api } from "@/lib/api-client";
import parseApiDateTime from "@/lib/date/parseApiDateTime";
import { getErrorMessage } from "@/lib/error-message";
import { cn } from "@/lib/utils";
import { ACTION_BUTTON_BASE, ACTION_NEUTRAL, SectionCard } from "@/components/admin/adminChrome";

type SecurityAlert = {
  id: string;
  type: string;
  detail: string;
  suppressedCount: number;
  pushed: boolean;
  createdAt: string;
};

type AbuseBlock = {
  id: string;
  subject: string;
  scope: string;
  reason: string | null;
  strikes: number;
  blockedUntil: string | null;
  createdAt: string;
  updatedAt: string;
};

const ALERT_TYPE_LABELS: Record<string, string> = {
  abuse_block_applied: "Automatic block applied",
  auth_alert_lockout_burst: "Burst of sign-in lockouts",
  auth_alert_ip_concentration: "Many attempts from one source",
  admin_reset_requested: "Password reset requested",
  auth_signal_anomaly: "Unusual sign-in activity",
};

const BLOCK_SCOPE_LABELS: Record<string, string> = {
  register: "Registration",
  auth: "Sign-in",
};

const BLOCK_REASON_LABELS: Record<string, string> = {
  register_violations: "Repeated registration failures",
  register_pending_flood: "Flood of pending sign-ups",
  auth_lockouts: "Repeated sign-in lockouts",
};

/** Falls back to a readable form of an unknown server code rather than showing
 * a raw snake_case token, so a newly added alert type still renders sensibly. */
const humanizeCode = (code: string) => {
  const spaced = code.replace(/_/g, " ").trim();
  return spaced.length > 0 ? spaced.charAt(0).toUpperCase() + spaced.slice(1) : "Unknown";
};

const alertTypeLabel = (type: string) => ALERT_TYPE_LABELS[type] ?? humanizeCode(type);
const blockScopeLabel = (scope: string) => BLOCK_SCOPE_LABELS[scope] ?? humanizeCode(scope);
const blockReasonLabel = (reason: string | null) =>
  reason ? (BLOCK_REASON_LABELS[reason] ?? humanizeCode(reason)) : "Automatic block";

const VISIBLE_ALERT_LIMIT = 8;

const MINUTE_MS = 60_000;
const HOUR_MS = 60 * MINUTE_MS;
const DAY_MS = 24 * HOUR_MS;

/** Coarse duration wording — a glanceable panel needs "3h", not "3h 14m 02s". */
const formatDuration = (millis: number) => {
  if (millis < MINUTE_MS) return "less than a minute";
  if (millis < HOUR_MS) return `${Math.floor(millis / MINUTE_MS)} min`;
  if (millis < DAY_MS) return `${Math.floor(millis / HOUR_MS)}h`;
  return `${Math.floor(millis / DAY_MS)}d`;
};

const formatAge = (value: string) => {
  const parsed = parseApiDateTime(value);
  const elapsed = Date.now() - parsed.getTime();
  if (Number.isNaN(elapsed)) return value;
  if (elapsed < MINUTE_MS) return "just now";
  return `${formatDuration(elapsed)} ago`;
};

const formatExpiry = (value: string | null) => {
  if (!value) return "No expiry recorded";
  const parsed = parseApiDateTime(value);
  const remaining = parsed.getTime() - Date.now();
  if (Number.isNaN(remaining)) return value;
  if (remaining <= 0) return "Expiring now";
  return `Lifts in ${formatDuration(remaining)}`;
};

/** Server timestamps are UTC with no zone suffix; parse first, then show the
 * admin's local wall-clock time in the hover title. */
const formatExactTime = (value: string) => parseApiDateTime(value).toLocaleString();

const MetaText = ({ children }: { children: ReactNode }) => (
  <span className="text-xs font-extrabold text-muted-foreground">{children}</span>
);

const AlertRow = ({ alert }: { alert: SecurityAlert }) => (
  <div className="rounded-2xl border border-border/70 bg-muted/20 p-3.5">
    <p className="text-[1.05rem] font-black text-foreground">{alertTypeLabel(alert.type)}</p>
    <p className="mt-1 break-words text-sm font-extrabold text-muted-foreground">{alert.detail}</p>
    <div className="mt-2 flex flex-wrap items-center gap-x-2 gap-y-1">
      <MetaText>
        <span title={formatExactTime(alert.createdAt)}>{formatAge(alert.createdAt)}</span>
      </MetaText>
      {alert.suppressedCount > 0 ? (
        <MetaText>· {alert.suppressedCount} more folded in</MetaText>
      ) : null}
      {/* "Sent" not "Delivered": the backend only knows the push service accepted
          it, not that a device buzzed. */}
      <MetaText>· {alert.pushed ? "Push sent" : "No push sent"}</MetaText>
    </div>
  </div>
);

const BlockRow = ({
  block,
  clearingId,
  onClear,
}: {
  block: AbuseBlock;
  clearingId: string | null;
  onClear: (block: AbuseBlock) => void;
}) => {
  const busy = clearingId === block.id;
  return (
    <div className="flex flex-col gap-3 rounded-2xl border border-border/70 bg-muted/20 p-3.5 sm:flex-row sm:items-center sm:justify-between">
      <div className="min-w-0">
        <p className="text-[1.05rem] font-black text-foreground">
          {blockScopeLabel(block.scope)} blocked
        </p>
        <p className="mt-1 text-sm font-extrabold text-muted-foreground">
          {blockReasonLabel(block.reason)}
        </p>
        <div className="mt-2 flex flex-wrap items-center gap-x-2 gap-y-1">
          <MetaText>
            <span title={block.blockedUntil ? formatExactTime(block.blockedUntil) : undefined}>
              {formatExpiry(block.blockedUntil)}
            </span>
          </MetaText>
          <MetaText>· Strike {block.strikes}</MetaText>
          <MetaText>
            ·{" "}
            <code className="font-mono text-[0.7rem] text-muted-foreground">{block.subject}</code>
          </MetaText>
        </div>
      </div>
      <div className="flex items-center gap-2 sm:shrink-0">
        <Button
          onClick={() => {
            onClear(block);
          }}
          disabled={busy}
          className={cn(ACTION_BUTTON_BASE, ACTION_NEUTRAL)}
        >
          {busy ? (
            <Loader2 className="h-4 w-4 animate-spin" />
          ) : (
            <ShieldOff className="h-4 w-4" />
          )}
          Clear
        </Button>
      </div>
    </div>
  );
};

/** The reassuring resting state: nothing happened, and that is stated outright
 * instead of leaving two empty boxes to interpret. */
const AllClearRow = () => (
  <div className="flex items-start gap-3 rounded-2xl border border-border/70 bg-muted/25 px-3.5 py-3">
    <ShieldCheck className="mt-0.5 h-5 w-5 shrink-0 text-primary" />
    <div className="min-w-0">
      <p className="text-[1.05rem] font-black text-foreground">All clear</p>
      <p className="text-sm font-extrabold text-muted-foreground">
        No security alerts. No active blocks.
      </p>
    </div>
  </div>
);

const NoticeRow = ({ children }: { children: ReactNode }) => (
  <p className="rounded-2xl border border-border/70 bg-muted/25 px-3.5 py-3 text-sm font-extrabold text-muted-foreground">
    {children}
  </p>
);

const SubHeading = ({ children }: { children: ReactNode }) => (
  <h3 className="text-sm font-black uppercase tracking-wide text-muted-foreground">{children}</h3>
);

export default function AdminSecurityPanel() {
  const { toast } = useToast();
  const [alerts, setAlerts] = useState<SecurityAlert[]>([]);
  const [blocks, setBlocks] = useState<AbuseBlock[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadFailed, setLoadFailed] = useState(false);
  const [clearingId, setClearingId] = useState<string | null>(null);

  const fetchSecurity = useCallback(async () => {
    setLoading(true);
    try {
      const [alertsBody, blocksBody] = await Promise.all([
        api.GET({ url: "/api/admin/security/alerts" }) as Promise<{
          alerts?: SecurityAlert[];
        } | null>,
        api.GET({ url: "/api/admin/security/blocks" }) as Promise<{
          blocks?: AbuseBlock[];
        } | null>,
      ]);
      setAlerts(alertsBody?.alerts || []);
      setBlocks(blocksBody?.blocks || []);
      setLoadFailed(false);
    } catch (error) {
      // A failed fetch must never render as "All clear" — a silent false calm on a
      // security panel is worse than an error message.
      setLoadFailed(true);
      setAlerts([]);
      setBlocks([]);
      toast({
        description: getErrorMessage(error, "Failed to load security status"),
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  }, [toast]);

  useEffect(() => {
    fetchSecurity();
  }, [fetchSecurity]);

  const clearBlock = async (block: AbuseBlock) => {
    const confirmed = window.confirm(
      `Lift the ${blockScopeLabel(block.scope).toLowerCase()} block on ${block.subject}? That source can try again immediately, and its strike count restarts from zero.`,
    );
    if (!confirmed) return;

    setClearingId(block.id);
    try {
      await api.POST({
        url: `/api/admin/security/blocks/${block.id}/clear`,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({}),
      });
      toast({ description: "Block cleared" });
      await fetchSecurity();
    } catch (error) {
      toast({
        description: getErrorMessage(error, "Failed to clear block"),
        variant: "destructive",
      });
    } finally {
      setClearingId(null);
    }
  };

  const quiet = alerts.length === 0 && blocks.length === 0;

  const renderBody = () => {
    if (loading) {
      return <p className="text-sm font-extrabold text-muted-foreground">Loading security status...</p>;
    }

    if (loadFailed) {
      return (
        <div className="flex items-start gap-3 rounded-2xl border border-destructive/30 bg-destructive/10 px-3.5 py-3">
          <ShieldAlert className="mt-0.5 h-5 w-5 shrink-0 text-destructive" />
          <div className="min-w-0">
            <p className="text-[1.05rem] font-black text-destructive">Status unavailable</p>
            <p className="text-sm font-extrabold text-muted-foreground">
              Could not load security alerts or blocks. Reload the page to try again.
            </p>
          </div>
        </div>
      );
    }

    if (quiet) {
      return <AllClearRow />;
    }

    return (
      <>
        <div className="space-y-2.5">
          <SubHeading>Recent alerts</SubHeading>
          {alerts.length === 0 ? (
            <NoticeRow>No security alerts.</NoticeRow>
          ) : (
            <>
              {alerts.slice(0, VISIBLE_ALERT_LIMIT).map((alert) => (
                <AlertRow key={alert.id} alert={alert} />
              ))}
              {/* The server keeps the newest 50; this panel is meant to be glanced
                  at, so only the freshest handful are rendered. */}
              {alerts.length > VISIBLE_ALERT_LIMIT ? (
                <p className="px-1 text-xs font-extrabold text-muted-foreground">
                  Showing the {VISIBLE_ALERT_LIMIT} newest of {alerts.length} recent alerts.
                </p>
              ) : null}
            </>
          )}
        </div>
        <div className="space-y-2.5">
          <SubHeading>Active blocks</SubHeading>
          {blocks.length === 0 ? (
            <NoticeRow>No active blocks.</NoticeRow>
          ) : (
            <>
              {blocks.map((block) => (
                <BlockRow
                  key={block.id}
                  block={block}
                  clearingId={clearingId}
                  onClear={clearBlock}
                />
              ))}
              {/* Says plainly what the short code is, because it looks like an ID
                  an admin might expect to trace back to a device or address. */}
              <NoticeRow>
                Blocked sources are shown as an anonymised code, not an IP address. The server only
                stores a one-way hash, so the same code always means the same source but cannot be
                turned back into one.
              </NoticeRow>
            </>
          )}
        </div>
      </>
    );
  };

  return <SectionCard title="Security">{renderBody()}</SectionCard>;
}
