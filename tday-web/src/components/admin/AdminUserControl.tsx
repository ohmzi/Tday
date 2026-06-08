import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import { useAuth } from "@/providers/AuthProvider";
import { Button } from "@/components/ui/button";
import { SheetCard } from "@/components/ui/sheet-chrome";
import { ArrowUpRight, Check, Copy, KeyRound, Loader2, Trash2, Users, X } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useToast } from "@/hooks/use-toast";
import NativePageTitle from "@/components/app/NativePageTitle";
import { nativeScreenAccentColors } from "@/components/app/nativeScreenTheme";
import NativeAppBrandButton from "@/components/app/NativeAppBrandButton";
import { api } from "@/lib/api-client";
import { getErrorMessage } from "@/lib/error-message";
import { cn } from "@/lib/utils";
import { Link } from "@/lib/navigation";

// Cohesive action-button styling shared by every row button (approve/deny/reset/
// delete) so they read as one family: same height, radius, weight, and icon gap —
// full-width on mobile, auto-width from sm up. Colour conveys intent.
const ACTION_BUTTON_BASE =
  "h-10 flex-1 gap-2 rounded-xl text-sm font-bold transition-colors sm:flex-none sm:px-4";
const ACTION_PRIMARY = "bg-primary text-primary-foreground hover:bg-primary/90";
const ACTION_NEUTRAL =
  "border border-border/60 bg-muted/50 text-foreground hover:bg-muted";
const ACTION_DESTRUCTIVE =
  "border border-destructive/30 bg-destructive/10 text-destructive hover:bg-destructive/20";
import { CURRENT_APP_VERSION, formatDisplayVersion } from "@/features/release/lib/release";

type AdminUser = {
  id: string;
  name: string | null;
  username: string;
  role: "ADMIN" | "USER";
  approvalStatus: "APPROVED" | "PENDING";
  createdAt: string;
  approvedAt: string | null;
  pendingAdminReset?: boolean;
  adminResetRequestedAt?: string | null;
};

type PendingApprovalRowProps = {
  user: AdminUser;
  actionUserId: string | null;
  actionType: AdminActionType;
  onApprove: (userId: string) => void;
  onReject: (userId: string) => void;
};

type AdminActionType = "approve" | "reject" | "delete" | "reset" | null;

type ApprovedUserRowProps = {
  user: AdminUser;
  sessionUserId: string | null | undefined;
  actionUserId: string | null;
  actionType: AdminActionType;
  onDelete: (userId: string) => void;
  onResetPassword: (userId: string) => void;
};

/** Rounded grouped section card with a big ExtraBold title — mirrors the
 * native settings cards so the admin page feels at home on mobile. */
const SectionCard = ({ title, children }: { title: string; children: ReactNode }) => (
  <SheetCard className="space-y-4 p-[18px]">
    <h2 className="text-[1.4rem] font-black leading-tight text-foreground">{title}</h2>
    {children}
  </SheetCard>
);

/** Renders a pending-user row with the approve action wired to the current request state. */
const PendingApprovalRow = ({
  user,
  actionUserId,
  actionType,
  onApprove,
  onReject,
}: PendingApprovalRowProps) => {
  const busy = actionUserId === user.id;
  return (
    <div className="flex flex-col gap-3 rounded-2xl border border-border/70 bg-muted/20 p-3.5 sm:flex-row sm:items-center sm:justify-between">
      <div className="min-w-0">
        <p className="truncate text-[1.05rem] font-black text-foreground">
          {user.name?.trim() || user.username}
        </p>
        <p className="truncate text-sm font-extrabold text-muted-foreground">{user.username}</p>
      </div>
      <div className="flex items-center gap-2 sm:shrink-0">
        <Button
          onClick={() => {
            onReject(user.id);
          }}
          disabled={busy}
          className={cn(ACTION_BUTTON_BASE, ACTION_DESTRUCTIVE)}
        >
          {busy && actionType === "reject" ? (
            <Loader2 className="h-4 w-4 animate-spin" />
          ) : (
            <X className="h-4 w-4" />
          )}
          Deny
        </Button>
        <Button
          onClick={() => {
            onApprove(user.id);
          }}
          disabled={busy}
          className={cn(ACTION_BUTTON_BASE, ACTION_PRIMARY)}
        >
          {busy && actionType === "approve" ? (
            <Loader2 className="h-4 w-4 animate-spin" />
          ) : (
            <Check className="h-4 w-4" />
          )}
          Approve
        </Button>
      </div>
    </div>
  );
};

/** Renders an approved-user row and prevents deleting the current signed-in admin. */
const ApprovedUserRow = ({
  user,
  sessionUserId,
  actionUserId,
  actionType,
  onDelete,
  onResetPassword,
}: ApprovedUserRowProps) => {
  const isCurrentUser = sessionUserId === user.id;
  const busy = actionUserId === user.id;
  const resetRequested = Boolean(user.pendingAdminReset);

  return (
    <div className="flex flex-col gap-3 rounded-2xl border border-border/70 bg-muted/20 p-3.5 sm:flex-row sm:items-center sm:justify-between">
      <div className="min-w-0">
        <p className="truncate text-[1.05rem] font-black text-foreground">
          {user.name?.trim() || user.username}
        </p>
        <p className="truncate text-sm font-extrabold text-muted-foreground">{user.username}</p>
        {/* Only meaningful tags are shown — the implicit "USER" role is dropped. */}
        {(user.role === "ADMIN" || isCurrentUser || resetRequested) && (
          <div className="mt-2 flex flex-wrap items-center gap-1.5 text-xs font-extrabold">
            {user.role === "ADMIN" ? (
              <span className="rounded-full border border-primary/30 bg-primary/10 px-2 py-0.5 text-primary">
                Admin
              </span>
            ) : null}
            {isCurrentUser ? (
              <span className="rounded-full border border-border/70 px-2 py-0.5 text-muted-foreground">
                You
              </span>
            ) : null}
            {resetRequested ? (
              <span className="rounded-full border border-destructive/40 bg-destructive/10 px-2 py-0.5 text-destructive">
                Reset requested
              </span>
            ) : null}
          </div>
        )}
      </div>
      <div className="flex items-center gap-2 sm:shrink-0">
        {/* Admins can't have their password reset from here — they manage it
            themselves under Settings → Change password. */}
        {user.role !== "ADMIN" && (
          <Button
            onClick={() => {
              onResetPassword(user.id);
            }}
            disabled={busy}
            // Turns red when the user has asked an admin to reset their password.
            className={cn(
              ACTION_BUTTON_BASE,
              resetRequested ? ACTION_DESTRUCTIVE : ACTION_NEUTRAL,
            )}
          >
            {busy && actionType === "reset" ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <KeyRound className="h-4 w-4" />
            )}
            Reset password
          </Button>
        )}
        <Button
          onClick={() => {
            onDelete(user.id);
          }}
          disabled={busy || isCurrentUser}
          className={cn(ACTION_BUTTON_BASE, ACTION_DESTRUCTIVE)}
        >
          {busy && actionType === "delete" ? (
            <Loader2 className="h-4 w-4 animate-spin" />
          ) : (
            <Trash2 className="h-4 w-4" />
          )}
          Delete
        </Button>
      </div>
    </div>
  );
};

/** Renders the shared admin page header. */
const AdminPageHeader = () => (
  <NativePageTitle
    title="Admin"
    accentColor={nativeScreenAccentColors.settings}
    icon={Users}
  />
);

/** Links admins into the release details page from the main admin dashboard. */
const VersionLinkRow = () => (
  <Link
    href="/app/admin/version"
    className="group flex items-center justify-between gap-4 rounded-2xl border border-border/70 bg-muted/20 px-4 py-4 transition-colors hover:border-accent/35 hover:bg-muted/30 active:opacity-70"
  >
    <div className="min-w-0">
      <div className="flex items-center gap-2 text-[1.05rem] font-black text-foreground">
        <span>Version {formatDisplayVersion(CURRENT_APP_VERSION) ?? CURRENT_APP_VERSION}</span>
      </div>
    </div>
    <ArrowUpRight className="h-5 w-5 shrink-0 text-muted-foreground transition-transform group-hover:translate-x-0.5 group-hover:-translate-y-0.5" />
  </Link>
);

/** Chooses the correct pending-approval content state for the admin list card. */
const PendingApprovalsContent = ({
  loading,
  pendingUsers,
  actionUserId,
  actionType,
  onApprove,
  onReject,
}: {
  loading: boolean;
  pendingUsers: AdminUser[];
  actionUserId: string | null;
  actionType: AdminActionType;
  onApprove: (userId: string) => void;
  onReject: (userId: string) => void;
}) => {
  if (loading) {
    return <p className="text-sm font-extrabold text-muted-foreground">Loading users...</p>;
  }

  if (pendingUsers.length === 0) {
    return (
      <p className="rounded-2xl border border-border/70 bg-muted/25 px-3.5 py-3 text-sm font-extrabold text-muted-foreground">
        No pending users.
      </p>
    );
  }

  return pendingUsers.map((user) => (
    <PendingApprovalRow
      key={user.id}
      user={user}
      actionUserId={actionUserId}
      actionType={actionType}
      onApprove={onApprove}
      onReject={onReject}
    />
  ));
};

/** Chooses the correct approved-users content state for the admin list card. */
const ApprovedUsersContent = ({
  loading,
  approvedUsers,
  sessionUserId,
  actionUserId,
  actionType,
  onDelete,
  onResetPassword,
}: {
  loading: boolean;
  approvedUsers: AdminUser[];
  sessionUserId: string | null | undefined;
  actionUserId: string | null;
  actionType: AdminActionType;
  onDelete: (userId: string) => void;
  onResetPassword: (userId: string) => void;
}) => {
  if (loading) {
    return <p className="text-sm font-extrabold text-muted-foreground">Loading users...</p>;
  }

  if (approvedUsers.length === 0) {
    return (
      <p className="rounded-2xl border border-border/70 bg-muted/25 px-3.5 py-3 text-sm font-extrabold text-muted-foreground">
        No approved users.
      </p>
    );
  }

  return approvedUsers.map((user) => (
    <ApprovedUserRow
      key={user.id}
      user={user}
      sessionUserId={sessionUserId}
      actionUserId={actionUserId}
      actionType={actionType}
      onDelete={onDelete}
      onResetPassword={onResetPassword}
    />
  ));
};

export default function AdminUserControl() {
  const { user: sessionUser } = useAuth();
  const { toast } = useToast();
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [actionUserId, setActionUserId] = useState<string | null>(null);
  const [actionType, setActionType] = useState<AdminActionType>(null);
  const [resetResult, setResetResult] = useState<{ username: string; password: string } | null>(null);

  const fetchUsers = useCallback(async () => {
    setLoading(true);
    try {
      const body = (await api.GET({ url: "/api/admin/users" })) as {
        users?: AdminUser[];
      } | null;
      setUsers(body?.users || []);
    } catch (error) {
      toast({
        description: getErrorMessage(error, "Failed to load users"),
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  }, [toast]);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  const pendingUsers = useMemo(
    () => users.filter((user) => user.approvalStatus === "PENDING"),
    [users],
  );
  const approvedUsers = useMemo(
    () => users.filter((user) => user.approvalStatus === "APPROVED"),
    [users],
  );

  const approveUser = async (userId: string) => {
    setActionUserId(userId);
    setActionType("approve");
    try {
      await api.PATCH({ url: `/api/admin/users/${userId}` });
      toast({ description: "User approved" });
      await fetchUsers();
    } catch (error) {
      toast({
        description: getErrorMessage(error, "Failed to approve user"),
        variant: "destructive",
      });
    } finally {
      setActionUserId(null);
      setActionType(null);
    }
  };

  const rejectUser = async (userId: string) => {
    const target = users.find((u) => u.id === userId);
    const confirmed = window.confirm(
      `Reject the registration for ${target?.username ?? "this account"}? The account will be permanently removed.`,
    );
    if (!confirmed) return;

    setActionUserId(userId);
    setActionType("reject");
    try {
      await api.POST({
        url: `/api/admin/users/${userId}/reject`,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({}),
      });
      toast({ description: "Registration rejected" });
      await fetchUsers();
    } catch (error) {
      toast({
        description: getErrorMessage(error, "Failed to reject registration"),
        variant: "destructive",
      });
    } finally {
      setActionUserId(null);
      setActionType(null);
    }
  };

  const deleteUser = async (userId: string) => {
    const confirmed = window.confirm(
      "Delete this user and all of their data permanently?",
    );
    if (!confirmed) return;

    setActionUserId(userId);
    setActionType("delete");
    try {
      await api.DELETE({ url: `/api/admin/users/${userId}` });
      toast({ description: "User deleted" });
      await fetchUsers();
    } catch (error) {
      toast({
        description: getErrorMessage(error, "Failed to delete user"),
        variant: "destructive",
      });
    } finally {
      setActionUserId(null);
      setActionType(null);
    }
  };

  const resetPassword = async (userId: string) => {
    const target = users.find((u) => u.id === userId);
    const confirmed = window.confirm(
      `Reset the password for ${target?.username ?? "this user"}? Their current sessions will be signed out and they'll be required to set a new password on next sign-in.`,
    );
    if (!confirmed) return;

    setActionUserId(userId);
    setActionType("reset");
    try {
      const body = (await api.POST({
        url: `/api/admin/users/${userId}/reset-password`,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({}),
      })) as { password?: string } | null;
      if (!body?.password) {
        throw new Error("Server did not return a password");
      }
      setResetResult({ username: target?.username ?? "", password: body.password });
    } catch (error) {
      toast({
        description: getErrorMessage(error, "Failed to reset password"),
        variant: "destructive",
      });
    } finally {
      setActionUserId(null);
      setActionType(null);
    }
  };

  const copyPassword = async () => {
    if (!resetResult) return;
    try {
      await navigator.clipboard.writeText(resetResult.password);
      toast({ description: "Password copied to clipboard" });
    } catch {
      toast({ description: "Could not copy — select and copy manually", variant: "destructive" });
    }
  };

  return (
    <div className="w-full space-y-5 pb-10">
      <header className="sticky top-0 z-40 flex w-full items-center justify-between gap-2.5 bg-background pt-[calc(0.5rem+env(safe-area-inset-top))] pb-1.5 lg:static lg:bg-transparent lg:pt-2 lg:pb-2">
        <div aria-hidden className="pointer-events-none absolute inset-x-0 bottom-full h-screen bg-background lg:hidden" />
        <NativeAppBrandButton className="min-w-0 max-w-[58%] sm:max-w-none" />
      </header>

      <AdminPageHeader />

      {loading || pendingUsers.length > 0 ? (
        <SectionCard title="Pending approvals">
          <div className="space-y-2.5">
            <PendingApprovalsContent
              loading={loading}
              pendingUsers={pendingUsers}
              actionUserId={actionUserId}
              actionType={actionType}
              onApprove={approveUser}
              onReject={rejectUser}
            />
          </div>
        </SectionCard>
      ) : null}

      <SectionCard title="Approved users">
        <div className="space-y-2.5">
          <ApprovedUsersContent
            loading={loading}
            approvedUsers={approvedUsers}
            sessionUserId={sessionUser?.id}
            actionUserId={actionUserId}
            actionType={actionType}
            onDelete={deleteUser}
            onResetPassword={resetPassword}
          />
        </div>
      </SectionCard>

      <SectionCard title="App version">
        <VersionLinkRow />
      </SectionCard>

      <Dialog
        open={resetResult !== null}
        onOpenChange={(open) => {
          if (!open) setResetResult(null);
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Temporary password</DialogTitle>
            <DialogDescription>
              {resetResult?.username
                ? `Share this one-time password with ${resetResult.username}. `
                : "Share this one-time password with the user. "}
              It is shown only once. They will be required to set a new password the next time they sign in.
            </DialogDescription>
          </DialogHeader>
          <div className="flex items-center gap-2 rounded-xl border border-border/70 bg-muted/30 px-3 py-2">
            <code className="min-w-0 flex-1 break-all font-mono text-sm text-foreground">
              {resetResult?.password}
            </code>
            <Button size="sm" variant="outline" onClick={copyPassword}>
              <Copy className="mr-2 h-4 w-4" />
              Copy
            </Button>
          </div>
          <DialogFooter>
            <Button onClick={() => setResetResult(null)}>Done</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
