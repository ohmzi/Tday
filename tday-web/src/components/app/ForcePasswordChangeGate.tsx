import { useState } from "react";
import { Loader2, ShieldAlert } from "lucide-react";
import { useAuth } from "@/providers/AuthProvider";
import { api } from "@/lib/api-client";
import { getErrorMessage } from "@/lib/error-message";
import { useToast } from "@/hooks/use-toast";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

/**
 * Blocking prompt shown when an admin has reset the current user's password.
 * The user must set a new password (entering the temporary one issued by the
 * admin) before they can continue. Backend clears the flag on success.
 */
export default function ForcePasswordChangeGate() {
  const { user, refreshSession } = useAuth();
  const { toast } = useToast();
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [saving, setSaving] = useState(false);

  if (!user?.requirePasswordChange) return null;

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (newPassword.length < 8) {
      toast({ description: "New password must be at least 8 characters", variant: "destructive" });
      return;
    }
    if (newPassword !== confirmPassword) {
      toast({ description: "Passwords do not match", variant: "destructive" });
      return;
    }
    setSaving(true);
    try {
      await api.POST({
        url: "/api/user/change-password",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ currentPassword, newPassword }),
      });
      toast({ description: "Password updated" });
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
      await refreshSession();
    } catch (error) {
      toast({
        description: getErrorMessage(error, "Failed to update password"),
        variant: "destructive",
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open>
      <DialogContent
        onInteractOutside={(event) => event.preventDefault()}
        onEscapeKeyDown={(event) => event.preventDefault()}
      >
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <ShieldAlert className="h-5 w-5 text-accent" />
            Set a new password
          </DialogTitle>
          <DialogDescription>
            An administrator reset your password. Enter the temporary password you
            were given and choose a new one to continue.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="space-y-3">
          <div className="space-y-1.5">
            <Label htmlFor="forceCurrentPassword">Temporary password</Label>
            <Input
              id="forceCurrentPassword"
              type="password"
              autoComplete="current-password"
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              required
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="forceNewPassword">New password</Label>
            <Input
              id="forceNewPassword"
              type="password"
              autoComplete="new-password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              minLength={8}
              required
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="forceConfirmPassword">Confirm new password</Label>
            <Input
              id="forceConfirmPassword"
              type="password"
              autoComplete="new-password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              minLength={8}
              required
            />
          </div>
          <Button type="submit" disabled={saving} className="w-full">
            {saving ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
            Update password
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}
