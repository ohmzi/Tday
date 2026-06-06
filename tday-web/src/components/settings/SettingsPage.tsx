import React, { useState, useEffect, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import {
  Copy,
  Eye,
  EyeOff,
  Key,
  Loader2,
  Lock,
  Monitor,
  Moon,
  Settings,
  Sun,
  Trash2,
  User,
} from "lucide-react";
import { useTheme } from "next-themes";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { SheetCard } from "@/components/ui/sheet-chrome";
import { cn } from "@/lib/utils";
import { useAuth } from "@/providers/AuthProvider";
import { useToast } from "@/hooks/use-toast";
import NativePageTitle from "@/components/app/NativePageTitle";
import { nativeScreenAccentColors } from "@/components/app/nativeScreenTheme";
import NativeAppBrandButton from "@/components/app/NativeAppBrandButton";
import { api } from "@/lib/api-client";
import { getErrorMessage } from "@/lib/error-message";
import { usePushNotifications } from "@/hooks/usePushNotifications";

const themeOptions = [
  { value: "light", label: "Light", icon: Sun },
  { value: "dark", label: "Dark", icon: Moon },
  { value: "system", label: "System", icon: Monitor },
] as const;

/** Rounded grouped section card with a big ExtraBold title — mirrors the
 * native SettingsSectionCard / SettingsSectionTitle. */
function SettingsSection({
  title,
  description,
  children,
}: {
  title: string;
  description?: ReactNode;
  children: ReactNode;
}) {
  return (
    <SheetCard className="space-y-4 p-[18px]">
      <div className="space-y-1">
        <h2 className="text-[1.4rem] font-black leading-tight text-foreground">{title}</h2>
        {description ? (
          <p className="text-sm font-extrabold text-muted-foreground">{description}</p>
        ) : null}
      </div>
      {children}
    </SheetCard>
  );
}

/** Pill switch — mirrors the native toggle used across the app. */
function SettingsSwitch({
  checked,
  onClick,
  disabled,
  ariaLabel,
}: {
  checked: boolean;
  onClick: () => void;
  disabled?: boolean;
  ariaLabel: string;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={ariaLabel}
      disabled={disabled}
      onClick={onClick}
      className={cn(
        "relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors",
        checked ? "bg-accent" : "bg-muted-foreground/30",
        disabled && "opacity-45",
      )}
    >
      <span
        className={cn(
          "inline-block h-5 w-5 rounded-full bg-white shadow transition-transform",
          checked ? "translate-x-[22px]" : "translate-x-[2px]",
        )}
      />
    </button>
  );
}

/** Sliding 3-way segmented control — mirrors the native theme selector. */
function ThemeSegmentedControl({
  value,
  onChange,
}: {
  value: string;
  onChange: (value: string) => void;
}) {
  const index = Math.max(
    0,
    themeOptions.findIndex((option) => option.value === value),
  );
  return (
    <div className="relative flex h-14 rounded-[22px] bg-muted/60 p-1.5">
      <span
        aria-hidden
        className="absolute bottom-1.5 left-1.5 top-1.5 w-[calc((100%-0.75rem)/3)] rounded-[16px] bg-card shadow-sm transition-transform duration-200 ease-out"
        style={{ transform: `translateX(${index * 100}%)` }}
      />
      {themeOptions.map((option) => {
        const Icon = option.icon;
        const selected = option.value === value;
        return (
          <button
            key={option.value}
            type="button"
            onClick={() => onChange(option.value)}
            aria-pressed={selected}
            className={cn(
              "relative z-10 flex flex-1 items-center justify-center gap-1.5 rounded-[16px] text-[0.9rem] font-black transition-colors",
              selected ? "text-accent" : "text-muted-foreground",
            )}
          >
            <Icon className="h-4 w-4" strokeWidth={2.6} />
            {option.label}
          </button>
        );
      })}
    </div>
  );
}

const fieldClass =
  "h-12 rounded-2xl border-border/70 bg-background/50 font-bold focus-visible:ring-accent/30";

export default function SettingsPage() {
  const { t: sidebarDict } = useTranslation("sidebar");
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

  const [apiKeyStatus, setApiKeyStatus] = useState<{
    enabled: boolean;
    keyPreview?: string | null;
    createdAt?: string | null;
  } | null>(null);
  const [generatedApiKey, setGeneratedApiKey] = useState<string | null>(null);
  const [apiKeyLoading, setApiKeyLoading] = useState(false);
  const [showApiKey, setShowApiKey] = useState(false);

  useEffect(() => {
    let cancelled = false;
    api
      .GET({ url: "/api/user/api-key" })
      .then((res) => {
        if (!cancelled) setApiKeyStatus(res?.status ?? { enabled: false });
      })
      .catch(() => {
        if (!cancelled) setApiKeyStatus({ enabled: false });
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const handleGenerateApiKey = async () => {
    setApiKeyLoading(true);
    try {
      const res = await api.POST({
        url: "/api/user/api-key",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({}),
      });
      const created = res?.apiKey;
      setGeneratedApiKey(created?.key ?? null);
      setShowApiKey(true);
      setApiKeyStatus({
        enabled: true,
        keyPreview: created?.keyPreview ?? null,
        createdAt: created?.createdAt ?? null,
      });
      toast({ description: "API key generated. Copy it now — it won't be shown again." });
    } catch (err) {
      toast({
        description: getErrorMessage(err, "Failed to generate API key"),
        variant: "destructive",
      });
    } finally {
      setApiKeyLoading(false);
    }
  };

  const handleRevokeApiKey = async () => {
    setApiKeyLoading(true);
    try {
      await api.DELETE({
        url: "/api/user/api-key",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({}),
      });
      setApiKeyStatus({ enabled: false });
      setGeneratedApiKey(null);
      setShowApiKey(false);
      toast({ description: "API key revoked" });
    } catch (err) {
      toast({
        description: getErrorMessage(err, "Failed to revoke API key"),
        variant: "destructive",
      });
    } finally {
      setApiKeyLoading(false);
    }
  };

  const handleCopyApiKey = async () => {
    if (!generatedApiKey) return;
    try {
      await navigator.clipboard.writeText(generatedApiKey);
      toast({ description: "API key copied to clipboard" });
    } catch {
      toast({ description: "Failed to copy API key", variant: "destructive" });
    }
  };

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

  const pushSubtitle =
    push.permission === "denied"
      ? "Blocked in your browser settings. Update your browser permissions to enable them."
      : "Get notified about important things, even when the app isn't open.";

  return (
    <div className="w-full space-y-3 pb-10">
      <header className="sticky top-0 z-40 flex w-full items-center justify-between gap-2.5 bg-background pt-[calc(0.5rem+env(safe-area-inset-top))] pb-1.5 lg:static lg:bg-transparent lg:pt-2 lg:pb-2">
        <div aria-hidden className="pointer-events-none absolute inset-x-0 bottom-full h-screen bg-background lg:hidden" />
        <NativeAppBrandButton className="min-w-0 max-w-[58%] sm:max-w-none" />
      </header>

      <NativePageTitle
        title={sidebarDict("settings")}
        accentColor={nativeScreenAccentColors.settings}
        icon={Settings}
        className="mb-1"
      />

      <SettingsSection title="Profile" description="Update your display name">
        <form onSubmit={handleProfileSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="name" className="px-1 text-sm font-extrabold text-muted-foreground">
              Name
            </Label>
            <div className="relative">
              <User className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                id="name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Enter your name"
                className={cn(fieldClass, "pl-10")}
                maxLength={100}
              />
            </div>
          </div>
          <div className="space-y-2">
            <Label className="px-1 text-sm font-extrabold text-muted-foreground">Email</Label>
            <Input value={user?.email ?? ""} disabled className={cn(fieldClass, "opacity-60")} />
          </div>
          <Button type="submit" disabled={profileLoading} className="h-12 w-full rounded-2xl font-black">
            {profileLoading ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Saving...
              </>
            ) : (
              "Save profile"
            )}
          </Button>
        </form>
      </SettingsSection>

      <SettingsSection
        title="Appearance"
        description={
          theme === "system" && resolvedTheme
            ? `Follow your device, light, or dark. Currently using ${resolvedTheme}.`
            : "Choose light, dark, or follow your system setting."
        }
      >
        <ThemeSegmentedControl value={theme} onChange={setTheme} />
      </SettingsSection>

      {push.isSupported && (
        <SettingsSection title="Notifications">
          <div className="flex items-center justify-between gap-4">
            <div className="min-w-0">
              <p className="text-[1.05rem] font-black text-foreground">Push notifications</p>
              <p className="mt-0.5 text-sm font-extrabold text-muted-foreground">{pushSubtitle}</p>
            </div>
            {push.isLoading ? (
              <Loader2 className="h-5 w-5 shrink-0 animate-spin text-muted-foreground" />
            ) : (
              <SettingsSwitch
                checked={push.isSubscribed}
                disabled={push.permission === "denied"}
                ariaLabel="Toggle push notifications"
                onClick={handlePushToggle}
              />
            )}
          </div>
        </SettingsSection>
      )}

      <SettingsSection title="Change password" description="Update your account password">
        <form onSubmit={handlePasswordSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="currentPassword" className="px-1 text-sm font-extrabold text-muted-foreground">
              Current password
            </Label>
            <div className="relative">
              <Lock className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                id="currentPassword"
                type={showCurrentPassword ? "text" : "password"}
                autoComplete="current-password"
                value={currentPassword}
                onChange={(e) => setCurrentPassword(e.target.value)}
                placeholder="Enter your current password"
                className={cn(fieldClass, "pl-10 pr-10")}
                required
              />
              {currentPassword && (
                <button
                  type="button"
                  onClick={() => setShowCurrentPassword(!showCurrentPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground transition-colors hover:text-foreground"
                >
                  {showCurrentPassword ? <EyeOff className="h-4 w-4 opacity-40" /> : <Eye className="h-4 w-4 opacity-40" />}
                </button>
              )}
            </div>
          </div>
          <div className="space-y-2">
            <Label htmlFor="newPassword" className="px-1 text-sm font-extrabold text-muted-foreground">
              New password
            </Label>
            <div className="relative">
              <Lock className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                id="newPassword"
                type={showNewPassword ? "text" : "password"}
                autoComplete="new-password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                placeholder="Enter your new password"
                className={cn(fieldClass, "pl-10 pr-10")}
                minLength={8}
                required
              />
              {newPassword && (
                <button
                  type="button"
                  onClick={() => setShowNewPassword(!showNewPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground transition-colors hover:text-foreground"
                >
                  {showNewPassword ? <EyeOff className="h-4 w-4 opacity-40" /> : <Eye className="h-4 w-4 opacity-40" />}
                </button>
              )}
            </div>
            <p className="px-1 text-xs font-extrabold text-muted-foreground">
              Password must be at least 8 characters long
            </p>
          </div>
          <div className="space-y-2">
            <Label htmlFor="confirmNewPassword" className="px-1 text-sm font-extrabold text-muted-foreground">
              Confirm new password
            </Label>
            <div className="relative">
              <Lock className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                id="confirmNewPassword"
                type={showConfirmPassword ? "text" : "password"}
                autoComplete="new-password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                placeholder="Confirm your new password"
                className={cn(fieldClass, "pl-10 pr-10")}
                minLength={8}
                required
              />
              {confirmPassword && (
                <button
                  type="button"
                  onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground transition-colors hover:text-foreground"
                >
                  {showConfirmPassword ? <EyeOff className="h-4 w-4 opacity-40" /> : <Eye className="h-4 w-4 opacity-40" />}
                </button>
              )}
            </div>
          </div>
          <Button type="submit" disabled={passwordLoading} className="h-12 w-full rounded-2xl font-black">
            {passwordLoading ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Changing password...
              </>
            ) : (
              "Change password"
            )}
          </Button>
        </form>
      </SettingsSection>

      <SettingsSection
        title="Dashboard access"
        description='Generate a personal API key to connect external dashboards (e.g. the Homarr "Tday" widget). Paste this key with your Tday server URL into the integration. Keep it secret — it grants access to your tasks.'
      >
        <div className="flex items-center justify-between gap-3">
          <div className="min-w-0 text-sm">
            <p className="font-black text-foreground">
              {apiKeyStatus?.enabled ? "API access enabled" : "API access disabled"}
            </p>
            <p className="text-xs font-extrabold text-muted-foreground">
              {apiKeyStatus?.enabled
                ? apiKeyStatus.keyPreview
                  ? `Active key ending in …${apiKeyStatus.keyPreview}`
                  : "An active key exists"
                : "No key has been generated"}
            </p>
          </div>
          <Button
            type="button"
            variant={apiKeyStatus?.enabled ? "destructive" : "default"}
            disabled={apiKeyLoading || apiKeyStatus === null}
            onClick={apiKeyStatus?.enabled ? handleRevokeApiKey : handleGenerateApiKey}
            className="h-11 shrink-0 rounded-2xl font-black"
          >
            {apiKeyLoading ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                {apiKeyStatus?.enabled ? "Revoking..." : "Generating..."}
              </>
            ) : apiKeyStatus?.enabled ? (
              <>
                <Trash2 className="mr-2 h-4 w-4" />
                Revoke key
              </>
            ) : (
              <>
                <Key className="mr-2 h-4 w-4" />
                Generate key
              </>
            )}
          </Button>
        </div>

        {generatedApiKey && (
          <div className="space-y-2 rounded-2xl border border-border/60 bg-muted/40 p-3">
            <p className="text-xs font-extrabold text-muted-foreground">
              Copy your API key now — it won't be shown again.
            </p>
            <div className="flex gap-2">
              <Input
                type={showApiKey ? "text" : "password"}
                value={generatedApiKey}
                readOnly
                className="h-10 flex-1 rounded-xl bg-background/50 font-mono text-xs"
              />
              <Button type="button" variant="outline" size="icon" className="h-10 w-10 shrink-0 rounded-xl" onClick={() => setShowApiKey(!showApiKey)}>
                {showApiKey ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </Button>
              <Button type="button" variant="outline" size="icon" className="h-10 w-10 shrink-0 rounded-xl" onClick={handleCopyApiKey}>
                <Copy className="h-4 w-4" />
              </Button>
            </div>
          </div>
        )}
      </SettingsSection>
    </div>
  );
}
