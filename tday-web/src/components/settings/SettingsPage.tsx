import React, { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import {
  Bell,
  BellOff,
  Check,
  Eye,
  EyeOff,
  Info,
  Keyboard,
  Languages,
  Loader2,
  Lock,
  Monitor,
  Moon,
  Settings,
  Sun,
  User,
} from "lucide-react";
import { useTheme } from "next-themes";
import KeyboardShortcuts from "@/components/KeyboardShortcut";
import { Link, usePathname, useLocale } from "@/lib/navigation";
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
import { useAuth } from "@/providers/AuthProvider";
import { useToast } from "@/hooks/use-toast";
import NativePageTitle from "@/components/app/NativePageTitle";
import { nativeScreenAccentColors } from "@/components/app/nativeScreenTheme";
import MobileSearchHeader from "@/components/ui/MobileSearchHeader";
import { api } from "@/lib/api-client";
import { getErrorMessage } from "@/lib/error-message";
import { CURRENT_APP_VERSION, formatDisplayVersion } from "@/features/release/lib/release";
import { usePushNotifications } from "@/hooks/usePushNotifications";
import type { SupportedLocale } from "@/i18n";

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
] as const satisfies readonly { code: SupportedLocale; label: string }[];

const themeOptions = [
  {
    value: "light",
    label: "Light",
    description: "Use the bright T'Day palette.",
    icon: Sun,
  },
  {
    value: "dark",
    label: "Dark",
    description: "Use the low-light T'Day palette.",
    icon: Moon,
  },
  {
    value: "system",
    label: "System following",
    description: "Follow this device's appearance setting.",
    icon: Monitor,
  },
] as const;

export default function SettingsPage() {
  const locale = useLocale();
  const pathname = usePathname();
  const { t: sidebarDict } = useTranslation("sidebar");
  const { t: shortcutsDict } = useTranslation("shortcuts");
  const [shortcutsOpen, setShortcutsOpen] = useState(false);
  const { user } = useAuth();
  const { toast } = useToast();
  const { theme = "system", resolvedTheme, setTheme } = useTheme();

  const [name, setName] = useState("");
  const [profileLoading, setProfileLoading] = useState(false);

  useEffect(() => {
    if (user?.name) setName(user.name);
  }, [user?.name]);

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [passwordLoading, setPasswordLoading] = useState(false);
  const [showCurrentPassword, setShowCurrentPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  const push = usePushNotifications();

  const handlePushToggle = async () => {
    try {
      if (push.isSubscribed) {
        await push.unsubscribe();
        toast({ description: "Push notifications disabled" });
      } else {
        await push.subscribe();
        toast({ description: "Push notifications enabled" });
      }
    } catch (err) {
      toast({
        description: getErrorMessage(err, "Failed to update notification settings"),
        variant: "destructive",
      });
    }
  };

  const currentLocaleLabel =
    localeOptions.find((option) => option.code === locale)?.label || locale;

  const handleProfileSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = name.trim();
    if (!trimmed) return;
    if (trimmed === user?.name) {
      toast({ description: "No changes to save" });
      return;
    }
    setProfileLoading(true);
    try {
      await api.PATCH({
        url: "/api/user/profile",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name: trimmed }),
      });
      toast({ description: "Name updated successfully" });
    } catch (err) {
      toast({
        description: getErrorMessage(err, "Failed to update"),
        variant: "destructive",
      });
    } finally {
      setProfileLoading(false);
    }
  };

  const handlePasswordSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (newPassword.length < 8) {
      toast({ description: "New password must be at least 8 characters", variant: "destructive" });
      return;
    }
    if (newPassword !== confirmPassword) {
      toast({ description: "Passwords do not match", variant: "destructive" });
      return;
    }
    setPasswordLoading(true);
    try {
      await api.POST({
        url: "/api/user/change-password",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ currentPassword, newPassword }),
      });
      toast({ description: "Password changed successfully" });
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
    } catch (err) {
      toast({
        description: getErrorMessage(err, "Failed to change password"),
        variant: "destructive",
      });
    } finally {
      setPasswordLoading(false);
    }
  };

  return (
    <div className="w-full space-y-5 pb-10">
      <MobileSearchHeader />

      <NativePageTitle
        title={sidebarDict("settings")}
        accentColor={nativeScreenAccentColors.settings}
        icon={Settings}
        subtitle="Manage your account settings"
      />

      <Card className="rounded-2xl border-border/70 bg-card/95 mb-5">
        <CardHeader className="space-y-1">
          <CardTitle className="flex items-center gap-2 text-base"><User className="h-4 w-4 text-accent" />Profile</CardTitle>
          <CardDescription>Update your display name</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleProfileSubmit} className="space-y-6">
            <div className="space-y-2">
              <Label htmlFor="name">Name</Label>
              <div className="relative">
                <User className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                <Input id="name" value={name} onChange={(e) => setName(e.target.value)} placeholder="Enter your name" className="pl-10 h-12 bg-background/50" maxLength={100} />
              </div>
            </div>
            <div className="space-y-2">
              <Label>Email</Label>
              <Input value={user?.email ?? ""} disabled className="h-12 bg-background/50 opacity-60" />
            </div>
            <Button type="submit" disabled={profileLoading} className="w-full h-12 bg-primary hover:bg-primary/90 text-primary-foreground font-medium">
              {profileLoading ? (<><Loader2 className="mr-2 h-4 w-4 animate-spin" />Saving...</>) : "Save Profile"}
            </Button>
          </form>
        </CardContent>
      </Card>

      <Card className="rounded-2xl border-border/70 bg-card/95 mb-5">
        <CardHeader className="space-y-1">
          <CardTitle className="flex items-center gap-2 text-base"><Lock className="h-4 w-4 text-accent" />Change Password</CardTitle>
          <CardDescription>Update your account password</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handlePasswordSubmit} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="currentPassword">Current Password</Label>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                <Input id="currentPassword" type={showCurrentPassword ? "text" : "password"} autoComplete="current-password" value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} placeholder="Enter your current password" className="pl-10 pr-10 h-12 bg-background/50" required />
                {currentPassword && (
                  <button type="button" onClick={() => setShowCurrentPassword(!showCurrentPassword)} className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors">
                    {showCurrentPassword ? <EyeOff className="h-4 w-4 opacity-40" /> : <Eye className="h-4 w-4 opacity-40" />}
                  </button>
                )}
              </div>
            </div>
            <div className="space-y-2">
              <Label htmlFor="newPassword">New Password</Label>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                <Input id="newPassword" type={showNewPassword ? "text" : "password"} autoComplete="new-password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} placeholder="Enter your new password" className="pl-10 pr-10 h-12 bg-background/50" minLength={8} required />
                {newPassword && (
                  <button type="button" onClick={() => setShowNewPassword(!showNewPassword)} className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors">
                    {showNewPassword ? <EyeOff className="h-4 w-4 opacity-40" /> : <Eye className="h-4 w-4 opacity-40" />}
                  </button>
                )}
              </div>
              <p className="text-xs text-muted-foreground">Password must be at least 8 characters long</p>
            </div>
            <div className="space-y-2">
              <Label htmlFor="confirmNewPassword">Confirm New Password</Label>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                <Input id="confirmNewPassword" type={showConfirmPassword ? "text" : "password"} autoComplete="new-password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} placeholder="Confirm your new password" className="pl-10 pr-10 h-12 bg-background/50" minLength={8} required />
                {confirmPassword && (
                  <button type="button" onClick={() => setShowConfirmPassword(!showConfirmPassword)} className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors">
                    {showConfirmPassword ? <EyeOff className="h-4 w-4 opacity-40" /> : <Eye className="h-4 w-4 opacity-40" />}
                  </button>
                )}
              </div>
            </div>
            <Button type="submit" disabled={passwordLoading} className="w-full h-12 bg-primary hover:bg-primary/90 text-primary-foreground font-medium">
              {passwordLoading ? (<><Loader2 className="mr-2 h-4 w-4 animate-spin" />Changing password...</>) : "Change Password"}
            </Button>
          </form>
        </CardContent>
      </Card>

      <Card className="rounded-2xl border-border/70 bg-card/95 mb-5">
        <CardHeader className="space-y-1">
          <CardTitle className="flex items-center gap-2 text-base"><Languages className="h-4 w-4 text-accent" />Language</CardTitle>
          <CardDescription>Choose your app language</CardDescription>
        </CardHeader>
        <CardContent>
          <DropdownMenu modal={false}>
            <DropdownMenuTrigger asChild>
              <Button type="button" variant="outline" className="h-10 min-w-[220px] justify-start text-left">
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
                      className={cn("flex w-full items-center justify-between", isActive && "font-medium")}
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

      <Card className="rounded-2xl border-border/70 bg-card/95 mb-5">
        <CardHeader className="space-y-1">
          <CardTitle className="flex items-center gap-2 text-base">
            <Monitor className="h-4 w-4 text-accent" />
            Appearance
          </CardTitle>
          <CardDescription>
            Choose light, dark, or follow your system setting.
            {theme === "system" && resolvedTheme ? ` Currently using ${resolvedTheme}.` : ""}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid gap-2 sm:grid-cols-3">
            {themeOptions.map((option) => {
              const Icon = option.icon;
              const selected = theme === option.value;

              return (
                <button
                  key={option.value}
                  type="button"
                  onClick={() => setTheme(option.value)}
                  className={cn(
                    "flex min-h-28 flex-col items-start gap-3 rounded-2xl border p-4 text-left transition-colors",
                    selected
                      ? "border-accent/55 bg-accent/10 text-foreground ring-2 ring-accent/15"
                      : "border-border/70 bg-background/50 text-muted-foreground hover:bg-muted/55 hover:text-foreground",
                  )}
                  aria-pressed={selected}
                >
                  <span className="flex h-10 w-10 items-center justify-center rounded-2xl bg-card shadow-sm">
                    <Icon className={cn("h-5 w-5", selected ? "text-accent" : "text-muted-foreground")} />
                  </span>
                  <span>
                    <span className="block text-sm font-black">{option.label}</span>
                    <span className="mt-1 block text-xs font-extrabold opacity-75">
                      {option.description}
                    </span>
                  </span>
                </button>
              );
            })}
          </div>
        </CardContent>
      </Card>

      {push.isSupported && (
        <Card className="rounded-2xl border-border/70 bg-card/95 mb-5">
          <CardHeader className="space-y-1">
            <CardTitle className="flex items-center gap-2 text-base">
              {push.isSubscribed ? <Bell className="h-4 w-4 text-accent" /> : <BellOff className="h-4 w-4 text-accent" />}
              Push Notifications
            </CardTitle>
            <CardDescription>
              {push.permission === "denied"
                ? "Notifications are blocked in your browser settings. Please update your browser permissions to enable them."
                : "Receive notifications when important things happen, even when the app isn't open."}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Button
              type="button"
              variant={push.isSubscribed ? "outline" : "default"}
              disabled={push.isLoading || push.permission === "denied"}
              onClick={handlePushToggle}
              className="h-10"
            >
              {push.isLoading ? (
                <><Loader2 className="mr-2 h-4 w-4 animate-spin" />Updating...</>
              ) : push.isSubscribed ? (
                "Disable Notifications"
              ) : (
                "Enable Notifications"
              )}
            </Button>
          </CardContent>
        </Card>
      )}

      <Card className="rounded-2xl border-border/70 bg-card/95 mb-5">
        <CardHeader className="space-y-1">
          <CardTitle className="flex items-center gap-2 text-base"><Keyboard className="h-4 w-4 text-accent" />{shortcutsDict("title")}</CardTitle>
          <CardDescription>View keyboard shortcuts for creating and navigating tasks</CardDescription>
        </CardHeader>
        <CardContent>
          <Button type="button" variant="outline" onClick={() => setShortcutsOpen(true)}>Open shortcuts</Button>
        </CardContent>
      </Card>

      <KeyboardShortcuts open={shortcutsOpen} onOpenChange={setShortcutsOpen} />

      <div className="flex flex-col items-center gap-1.5 text-xs text-muted-foreground pt-2 pb-4">
        <div className="flex items-center gap-2">
          <Info className="h-3.5 w-3.5" />
          <span>Version {formatDisplayVersion(CURRENT_APP_VERSION) ?? CURRENT_APP_VERSION}</span>
        </div>
      </div>
    </div>
  );
}
