"use client";

import React, { useState, useEffect } from "react";
import { useLocale, useTranslations } from "next-intl";
import {
  Check,
  Eye,
  EyeOff,
  Info,
  Keyboard,
  Languages,
  Loader2,
  Lock,
  User,
} from "lucide-react";
import KeyboardShortcuts from "@/components/KeyboardShortcut";
import { Link, usePathname } from "@/i18n/navigation";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";
import { useSession } from "next-auth/react";
import { toast } from "sonner";
import packageJson from "../../../../../package.json";

const localeOptions = [
  { code: "en", label: "English" },
  { code: "zh", label: "Chinese" },
  { code: "de", label: "German" },
  { code: "ja", label: "Japanese" },
  { code: "ar", label: "Arabic" },
  { code: "ru", label: "Russian" },
  { code: "es", label: "Spanish" },
  { code: "fr", label: "French" },
  { code: "ms", label: "Malay" },
  { code: "it", label: "Italian" },
  { code: "pt", label: "Portuguese" },
] as const;

export default function SettingsPage() {
  const locale = useLocale();
  const pathname = usePathname();
  const sidebarDict = useTranslations("sidebar");
  const shortcutsDict = useTranslations("shortcuts");
  const [shortcutsOpen, setShortcutsOpen] = useState(false);

  const { data: session } = useSession();

  // Profile state
  const [name, setName] = useState("");
  const [profileLoading, setProfileLoading] = useState(false);

  useEffect(() => {
    if (session?.user?.name) {
      setName(session.user.name);
    }
  }, [session?.user?.name]);

  // Password state
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [passwordLoading, setPasswordLoading] = useState(false);
  const [showCurrentPassword, setShowCurrentPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const currentLocaleLabel =
    localeOptions.find((option) => option.code === locale)?.label || locale;

  const handleProfileSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = name.trim();
    if (!trimmed) return;
    if (trimmed === session?.user?.name) {
      toast.info("No changes to save");
      return;
    }

    setProfileLoading(true);
    try {
      const res = await fetch("/api/user/profile", {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name: trimmed }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.message || "Failed to update profile");
      toast.success("Name updated successfully");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to update");
    } finally {
      setProfileLoading(false);
    }
  };

  const handlePasswordSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (newPassword.length < 8) {
      toast.error("New password must be at least 8 characters");
      return;
    }
    if (newPassword !== confirmPassword) {
      toast.error("Passwords do not match");
      return;
    }

    setPasswordLoading(true);
    try {
      const res = await fetch("/api/user/change-password", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ currentPassword, newPassword }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.message || "Failed to change password");
      toast.success("Password changed successfully");
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Failed to change password");
    } finally {
      setPasswordLoading(false);
    }
  };

  return (
    <div className="mx-auto w-full max-w-3xl space-y-5 px-4 pb-10 pt-6">
      <header className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight text-foreground">{sidebarDict("settings")}</h1>
        <p className="text-sm text-muted-foreground">Manage your account settings</p>
      </header>

      {/* Profile Section */}
      <Card className="rounded-2xl border-border/70 bg-card/95 mb-5">
        <CardHeader className="space-y-1">
          <CardTitle className="flex items-center gap-2 text-base">
            <User className="h-4 w-4 text-accent" />
            Profile
          </CardTitle>
          <CardDescription>Update your display name</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleProfileSubmit} className="space-y-6">
            <div className="space-y-2">
              <Label htmlFor="name">Name</Label>
              <div className="relative">
                <User className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                <Input
                  id="name"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="Enter your name"
                  className="pl-10 h-12 bg-background/50"
                  maxLength={100}
                />
              </div>
            </div>
            <div className="space-y-2">
              <Label>Email</Label>
              <div className="relative">
                <Input
                  value={session?.user?.email ?? ""}
                  disabled
                  className="h-12 bg-background/50 opacity-60"
                />
              </div>
            </div>
            <Button
              type="submit"
              disabled={profileLoading}
              className="w-full h-12 bg-primary hover:bg-primary/90 text-primary-foreground font-medium"
            >
              {profileLoading ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Saving...
                </>
              ) : (
                "Save Profile"
              )}
            </Button>
          </form>
        </CardContent>
      </Card>

      {/* Change Password Section */}
      <Card className="rounded-2xl border-border/70 bg-card/95 mb-5">
        <CardHeader className="space-y-1">
          <CardTitle className="flex items-center gap-2 text-base">
            <Lock className="h-4 w-4 text-accent" />
            Change Password
          </CardTitle>
          <CardDescription>Update your account password</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handlePasswordSubmit} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="currentPassword">Current Password</Label>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                <Input
                  id="currentPassword"
                  type={showCurrentPassword ? "text" : "password"}
                  value={currentPassword}
                  onChange={(e) => setCurrentPassword(e.target.value)}
                  placeholder="Enter your current password"
                  className="pl-10 pr-10 h-12 bg-background/50"
                  required
                />
                {currentPassword && (
                  <button
                    type="button"
                    onClick={() => setShowCurrentPassword(!showCurrentPassword)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors"
                  >
                    {showCurrentPassword ? <EyeOff className="h-4 w-4 opacity-40" /> : <Eye className="h-4 w-4 opacity-40" />}
                  </button>
                )}
              </div>
            </div>
            <div className="space-y-2">
              <Label htmlFor="newPassword">New Password</Label>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                <Input
                  id="newPassword"
                  type={showNewPassword ? "text" : "password"}
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  placeholder="Enter your new password"
                  className="pl-10 pr-10 h-12 bg-background/50"
                  required
                />
                {newPassword && (
                  <button
                    type="button"
                    onClick={() => setShowNewPassword(!showNewPassword)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors"
                  >
                    {showNewPassword ? <EyeOff className="h-4 w-4 opacity-40" /> : <Eye className="h-4 w-4 opacity-40" />}
                  </button>
                )}
              </div>
              <p className="text-xs text-muted-foreground">
                Password must be at least 8 characters long
              </p>
            </div>
            <div className="space-y-2">
              <Label htmlFor="confirmPassword">Confirm New Password</Label>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                <Input
                  id="confirmPassword"
                  type={showConfirmPassword ? "text" : "password"}
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  placeholder="Confirm your new password"
                  className="pl-10 pr-10 h-12 bg-background/50"
                  required
                />
                {confirmPassword && (
                  <button
                    type="button"
                    onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors"
                  >
                    {showConfirmPassword ? <EyeOff className="h-4 w-4 opacity-40" /> : <Eye className="h-4 w-4 opacity-40" />}
                  </button>
                )}
              </div>
            </div>
            <Button
              type="submit"
              disabled={passwordLoading}
              className="w-full h-12 bg-primary hover:bg-primary/90 text-primary-foreground font-medium"
            >
              {passwordLoading ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Changing password...
                </>
              ) : (
                "Change Password"
              )}
            </Button>
          </form>
        </CardContent>
      </Card>

      {/* Language Section */}
      <Card className="rounded-2xl border-border/70 bg-card/95 mb-5">
        <CardHeader className="space-y-1">
          <CardTitle className="flex items-center gap-2 text-base">
            <Languages className="h-4 w-4 text-accent" />
            Language
          </CardTitle>
          <CardDescription>Choose your app language</CardDescription>
        </CardHeader>
        <CardContent>
          <DropdownMenu modal={false}>
            <DropdownMenuTrigger asChild>
              <Button
                type="button"
                variant="outline"
                className="h-10 min-w-[220px] justify-start text-left"
              >
                <span className="truncate">{currentLocaleLabel}</span>
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="start" className="w-56">
              {localeOptions.map((option) => {
                const isActive = option.code === locale;
                return (
                  <DropdownMenuItem key={option.code} asChild>
                    <Link
                      href={pathname}
                      locale={option.code}
                      className={cn(
                        "flex w-full items-center justify-between",
                        isActive && "font-medium",
                      )}
                    >
                      <span>{option.label}</span>
                      {isActive ? <Check className="h-4 w-4 text-accent" /> : null}
                    </Link>
                  </DropdownMenuItem>
                );
              })}
            </DropdownMenuContent>
          </DropdownMenu>
        </CardContent>
      </Card>

      {/* Keyboard Shortcuts Section */}
      <Card className="rounded-2xl border-border/70 bg-card/95 mb-5">
        <CardHeader className="space-y-1">
          <CardTitle className="flex items-center gap-2 text-base">
            <Keyboard className="h-4 w-4 text-accent" />
            {shortcutsDict("title")}
          </CardTitle>
          <CardDescription>
            View keyboard shortcuts for creating and navigating tasks
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Button
            type="button"
            variant="outline"
            onClick={() => setShortcutsOpen(true)}
          >
            Open shortcuts
          </Button>
        </CardContent>
      </Card>

      <KeyboardShortcuts open={shortcutsOpen} onOpenChange={setShortcutsOpen} />

      {/* Version - bottom */}
      <div className="flex flex-col items-center gap-1.5 text-xs text-muted-foreground pt-2 pb-4">
        <div className="flex items-center gap-2">
          <Info className="h-3.5 w-3.5" />
          <span>Version {packageJson.version}</span>
        </div>
      </div>
    </div>
  );
}
