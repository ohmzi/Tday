"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useSession } from "next-auth/react";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Check, Loader2, RefreshCcw, Trash2, Users } from "lucide-react";
import { toast } from "sonner";
import MobileSearchHeader from "@/components/ui/MobileSearchHeader";

type AdminUser = {
  id: string;
  name: string | null;
  email: string;
  role: "ADMIN" | "USER";
  approvalStatus: "APPROVED" | "PENDING";
  createdAt: string;
  approvedAt: string | null;
};

type AdminSettingsResponse = {
  aiSummaryEnabled: boolean;
  updatedAt?: string;
};

export default function AdminUserControl() {
  const { data: session } = useSession();
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [actionUserId, setActionUserId] = useState<string | null>(null);
  const [actionType, setActionType] = useState<"approve" | "delete" | null>(null);
  const [aiSummaryEnabled, setAiSummaryEnabled] = useState(true);
  const [settingsLoading, setSettingsLoading] = useState(true);
  const [settingsSaving, setSettingsSaving] = useState(false);

  const fetchUsers = useCallback(async () => {
    setLoading(true);
    try {
      const res = await fetch("/api/admin/users", { cache: "no-store" });
      const body = await res.json();
      if (!res.ok) {
        throw new Error(body?.message || "Failed to load users");
      }
      setUsers(body.users || []);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Failed to load users");
    } finally {
      setLoading(false);
    }
  }, []);

  const fetchAdminSettings = useCallback(async () => {
    setSettingsLoading(true);
    try {
      const res = await fetch("/api/admin/settings", { cache: "no-store" });
      const body = (await res.json()) as AdminSettingsResponse & {
        message?: string;
      };
      if (!res.ok) {
        throw new Error(body?.message || "Failed to load admin settings");
      }
      setAiSummaryEnabled(Boolean(body.aiSummaryEnabled));
    } catch (error) {
      toast.error(
        error instanceof Error ? error.message : "Failed to load admin settings",
      );
    } finally {
      setSettingsLoading(false);
    }
  }, []);

  useEffect(() => {
    void fetchUsers();
    void fetchAdminSettings();
  }, [fetchUsers, fetchAdminSettings]);

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
      const res = await fetch(`/api/admin/users/${userId}`, {
        method: "PATCH",
      });
      const body = await res.json();
      if (!res.ok) throw new Error(body?.message || "Failed to approve user");
      toast.success("User approved");
      await fetchUsers();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Failed to approve user");
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
      const res = await fetch(`/api/admin/users/${userId}`, {
        method: "DELETE",
      });
      const body = await res.json();
      if (!res.ok) throw new Error(body?.message || "Failed to delete user");
      toast.success("User deleted");
      await fetchUsers();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Failed to delete user");
    } finally {
      setActionUserId(null);
      setActionType(null);
    }
  };

  const toggleAiSummary = async () => {
    const nextValue = !aiSummaryEnabled;
    setSettingsSaving(true);
    try {
      const res = await fetch("/api/admin/settings", {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ aiSummaryEnabled: nextValue }),
      });
      const body = (await res.json()) as AdminSettingsResponse & {
        message?: string;
      };
      if (!res.ok) {
        throw new Error(body?.message || "Failed to update admin settings");
      }
      setAiSummaryEnabled(Boolean(body.aiSummaryEnabled));
      toast.success(
        body.aiSummaryEnabled
          ? "AI summaries enabled for all users"
          : "AI summaries disabled for all users",
      );
    } catch (error) {
      toast.error(
        error instanceof Error ? error.message : "Failed to update admin settings",
      );
    } finally {
      setSettingsSaving(false);
    }
  };

  return (
    <div className="w-full space-y-5 pb-10">
      <div className="lg:hidden">
        <MobileSearchHeader />
      </div>

      <header className="mt-8 flex flex-wrap items-center justify-between gap-3 sm:mt-10 lg:mt-0">
        <div className="space-y-1">
          <h1 className="flex items-center gap-2 text-2xl font-semibold tracking-tight">
            <Users className="h-5 w-5 text-accent" />
            Admin
          </h1>
          <p className="text-sm text-muted-foreground">
            Approve pending registrations and manage user access.
          </p>
        </div>
        <Button
          variant="outline"
          size="sm"
          onClick={() => void fetchUsers()}
          disabled={loading}
        >
          {loading ? (
            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
          ) : (
            <RefreshCcw className="mr-2 h-4 w-4" />
          )}
          Refresh
        </Button>
      </header>

      <Card className="rounded-2xl border-border/70 bg-card/95">
        <CardHeader>
          <CardTitle>Feature toggle</CardTitle>
          <CardDescription>
            Global controls applied to every user account.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border/70 bg-muted/20 px-3 py-3">
            <div className="min-w-0">
              <p className="text-sm font-medium text-foreground">AI task summary</p>
            </div>
            <Button
              size="sm"
              variant={aiSummaryEnabled ? "default" : "outline"}
              onClick={() => void toggleAiSummary()}
              disabled={settingsLoading || settingsSaving}
            >
              {settingsSaving ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : null}
              {aiSummaryEnabled ? "Enabled" : "Disabled"}
            </Button>
          </div>
        </CardContent>
      </Card>

      <Card className="rounded-2xl border-border/70 bg-card/95">
        <CardHeader>
          <CardTitle>Pending approvals</CardTitle>
          <CardDescription>
            New accounts can sign in only after approval.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          {loading ? (
            <p className="text-sm text-muted-foreground">Loading users...</p>
          ) : pendingUsers.length === 0 ? (
            <p className="rounded-xl border border-border/70 bg-muted/25 px-3 py-2 text-sm text-muted-foreground">
              No pending users.
            </p>
          ) : (
            pendingUsers.map((user) => (
              <div
                key={user.id}
                className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border/70 bg-muted/20 px-3 py-2"
              >
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-foreground">
                    {user.name?.trim() || user.email}
                  </p>
                  <p className="truncate text-xs text-muted-foreground">{user.email}</p>
                </div>
                <Button
                  size="sm"
                  onClick={() => void approveUser(user.id)}
                  disabled={actionUserId === user.id}
                >
                  {actionUserId === user.id && actionType === "approve" ? (
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  ) : (
                    <Check className="mr-2 h-4 w-4" />
                  )}
                  Approve
                </Button>
              </div>
            ))
          )}
        </CardContent>
      </Card>

      <Card className="rounded-2xl border-border/70 bg-card/95">
        <CardHeader>
          <CardTitle>Approved users</CardTitle>
          <CardDescription>
            Tasks remain private. Admin can only manage account access.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          {loading ? (
            <p className="text-sm text-muted-foreground">Loading users...</p>
          ) : approvedUsers.length === 0 ? (
            <p className="rounded-xl border border-border/70 bg-muted/25 px-3 py-2 text-sm text-muted-foreground">
              No approved users.
            </p>
          ) : (
            approvedUsers.map((user) => {
              const isCurrentUser = session?.user?.id === user.id;
              return (
                <div
                  key={user.id}
                  className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border/70 bg-muted/20 px-3 py-2"
                >
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-foreground">
                      {user.name?.trim() || user.email}
                    </p>
                    <p className="truncate text-xs text-muted-foreground">
                      {user.email}
                    </p>
                    <div className="mt-1 flex items-center gap-2 text-xs text-muted-foreground">
                      <span className="rounded-full border border-border/70 px-2 py-0.5">
                        {user.role}
                      </span>
                      {isCurrentUser ? (
                        <span className="rounded-full border border-border/70 px-2 py-0.5">
                          You
                        </span>
                      ) : null}
                    </div>
                  </div>
                  <Button
                    size="sm"
                    variant="destructive"
                    onClick={() => void deleteUser(user.id)}
                    disabled={actionUserId === user.id || isCurrentUser}
                  >
                    {actionUserId === user.id && actionType === "delete" ? (
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    ) : (
                      <Trash2 className="mr-2 h-4 w-4" />
                    )}
                    Delete
                  </Button>
                </div>
              );
            })
          )}
        </CardContent>
      </Card>
    </div>
  );
}
