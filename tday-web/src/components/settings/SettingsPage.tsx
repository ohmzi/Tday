import React, { useState, useEffect, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import {
  Check,
  ChevronRight,
  Copy,
  Eye,
  EyeOff,
  Key,
  Loader2,
  Lock,
  Monitor,
  Moon,
  Pencil,
  Settings,
  Sun,
  Trash2,
  User,
} from "lucide-react";
import { useTheme } from "next-themes";
import { useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { SheetCard } from "@/components/ui/sheet-chrome";
import {
  CenteredSelectorOverlay,
  SelectorDivider,
} from "@/components/ui/sheet-chrome/CenteredSelectorOverlay";
import { cn } from "@/lib/utils";
import { useAuth } from "@/providers/AuthProvider";
import { useUserPreferences } from "@/providers/UserPreferencesProvider";
import { useToast } from "@/hooks/use-toast";
import NativePageTitle from "@/components/app/NativePageTitle";
import { nativeScreenAccentColors } from "@/components/app/nativeScreenTheme";
import NativeAppBrandButton from "@/components/app/NativeAppBrandButton";
import { api } from "@/lib/api-client";
import { getErrorMessage } from "@/lib/error-message";
import { usePushNotifications } from "@/hooks/usePushNotifications";
import { Link, usePathname } from "@/lib/navigation";
import { LANGUAGE_STORAGE_KEY, resolveInitialLocale } from "@/i18n";

const themeOptions = [
  { value: "light", labelKey: "themeLight", icon: Sun },
  { value: "dark", labelKey: "themeDark", icon: Moon },
  { value: "system", labelKey: "themeSystem", icon: Monitor },
] as const;

// Endonyms (each language shown in its own script) + a "System default" option
// that follows the browser/OS language. Order: system first, then alphabetical.
const LANGUAGE_OPTIONS = [
  { code: "system", label: "System default" },
  { code: "en", label: "English" },
  { code: "es", label: "Español" },
  { code: "fr", label: "Français" },
  { code: "de", label: "Deutsch" },
  { code: "it", label: "Italiano" },
  { code: "pt", label: "Português" },
  { code: "ru", label: "Русский" },
  { code: "zh", label: "中文" },
  { code: "ja", label: "日本語" },
  { code: "ms", label: "Bahasa Melayu" },
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
  labelFor,
}: {
  value: string;
  onChange: (value: string) => void;
  labelFor: (key: string) => string;
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
            {labelFor(option.labelKey)}
          </button>
        );
      })}
    </div>
  );
}

const fieldClass =
  "h-12 rounded-2xl border-border/70 bg-background/50 font-bold focus-visible:ring-accent/30";

/** Inline expand/collapse that animates height via the grid-rows trick — used
 * for the hidden-until-edit account editors. */
function Collapse({ open, children }: { open: boolean; children: ReactNode }) {
  return (
    <div
      className={cn(
        "grid transition-[grid-template-rows] duration-200 ease-out",
        open ? "grid-rows-[1fr]" : "grid-rows-[0fr]",
      )}
    >
      <div className="overflow-hidden">{children}</div>
    </div>
  );
}

export default function SettingsPage() {
  const { t: sidebarDict, i18n } = useTranslation("sidebar");
  const { t } = useTranslation("settings");
  const { user, refreshSession } = useAuth();
  const { preferences, updatePreferences } = useUserPreferences();
  const { toast } = useToast();
  const { theme = "system", resolvedTheme, setTheme } = useTheme();

  const navigate = useNavigate();
  const pathname = usePathname();
  const [languageOpen, setLanguageOpen] = useState(false);
  const storedLanguage = (() => {
    try {
      return localStorage.getItem(LANGUAGE_STORAGE_KEY);
    } catch {
      return null;
    }
  })();
  const selectedLanguageCode = storedLanguage ?? "system";
  // "System default" is the only language-list entry that gets translated; the
  // rest are endonyms (each language's own name) and stay as-is.
  const languageLabelFor = (option: (typeof LANGUAGE_OPTIONS)[number]) =>
    option.code === "system" ? t("systemDefault") : option.label;
  const currentLanguageLabel = (() => {
    const found = LANGUAGE_OPTIONS.find((opt) => opt.code === selectedLanguageCode);
    return found ? languageLabelFor(found) : t("systemDefault");
  })();

  const chooseLanguage = (code: string) => {
    setLanguageOpen(false);
    const target = code === "system" ? resolveInitialLocale() : code;
    try {
      if (code === "system") localStorage.removeItem(LANGUAGE_STORAGE_KEY);
      else localStorage.setItem(LANGUAGE_STORAGE_KEY, code);
    } catch {
      /* ignore storage failures */
    }
    void i18n.changeLanguage(target);
    // Swap the leading locale segment of the current URL so deep links stay valid.
    navigate(pathname.replace(/^\/[^/]+/, `/${target}`));
  };

  // Which inline account editor is open ("one at a time" is structurally
  // guaranteed by this single state value).
  const [editing, setEditing] = useState<"name" | "password" | null>(null);

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
      toast({ description: t("toast.apiKeyGenerated") });
    } catch (err) {
      toast({
        description: getErrorMessage(err, t("toast.apiKeyGenerateFailed")),
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
      toast({ description: t("toast.apiKeyRevoked") });
    } catch (err) {
      toast({
        description: getErrorMessage(err, t("toast.apiKeyRevokeFailed")),
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
      toast({ description: t("toast.apiKeyCopied") });
    } catch {
      toast({ description: t("toast.apiKeyCopyFailed"), variant: "destructive" });
    }
  };

  const handlePushToggle = async () => {
    try {
      if (push.isSubscribed) {
        await push.unsubscribe();
        toast({ description: t("toast.pushDisabled") });
      } else {
        await push.subscribe();
        toast({ description: t("toast.pushEnabled") });
      }
    } catch (err) {
      toast({
        description: getErrorMessage(err, t("toast.pushUpdateFailed")),
        variant: "destructive",
      });
    }
  };

  const handleProfileSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = name.trim();
    if (!trimmed) return;
    if (trimmed === user?.name) {
      toast({ description: t("toast.noChanges") });
      setEditing(null);
      return;
    }
    setProfileLoading(true);
    try {
      await api.PATCH({
        url: "/api/user/profile",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name: trimmed }),
      });
      toast({ description: t("toast.nameUpdated") });
      // Re-fetch the session so the new name is confirmed by the server and
      // propagated to every consumer of useAuth(); then collapse the editor.
      await refreshSession();
      setEditing(null);
    } catch (err) {
      toast({
        description: getErrorMessage(err, t("toast.nameUpdateFailed")),
        variant: "destructive",
      });
    } finally {
      setProfileLoading(false);
    }
  };

  const handlePasswordSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (newPassword.length < 8) {
      toast({ description: t("toast.passwordTooShort"), variant: "destructive" });
      return;
    }
    if (newPassword !== confirmPassword) {
      toast({ description: t("toast.passwordsDoNotMatch"), variant: "destructive" });
      return;
    }
    setPasswordLoading(true);
    try {
      await api.POST({
        url: "/api/user/change-password",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ currentPassword, newPassword }),
      });
      toast({ description: t("toast.passwordChanged") });
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
      setShowCurrentPassword(false);
      setShowNewPassword(false);
      setShowConfirmPassword(false);
      setEditing(null);
    } catch (err) {
      toast({
        description: getErrorMessage(err, t("toast.passwordChangeFailed")),
        variant: "destructive",
      });
    } finally {
      setPasswordLoading(false);
    }
  };

  const pushSubtitle =
    push.permission === "denied"
      ? t("notifications.blocked")
      : t("notifications.description");

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

      <SettingsSection title={t("profile.title")} description={t("profile.description")}>
        <div className="space-y-4">
          {/* Name — collapsed summary with an Edit affordance, expands to an inline editor. */}
          <div className="space-y-2">
            <div className="flex items-center justify-between gap-3">
              <div className="min-w-0">
                <Label className="px-1 text-sm font-extrabold text-muted-foreground">{t("profile.name")}</Label>
                <p className="px-1 text-[1.05rem] font-black text-foreground truncate">
                  {user?.name || t("profile.namePlaceholder")}
                </p>
              </div>
              {editing !== "name" ? (
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  className="shrink-0 rounded-xl font-black text-accent hover:text-accent"
                  onClick={() => {
                    setName(user?.name ?? "");
                    setEditing("name");
                  }}
                >
                  <Pencil className="mr-1.5 h-3.5 w-3.5" />
                  {t("profile.edit")}
                </Button>
              ) : null}
            </div>
            <Collapse open={editing === "name"}>
              <form onSubmit={handleProfileSubmit} className="space-y-3 pt-1">
                <div className="relative">
                  <User className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                  <Input
                    id="name"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder={t("profile.namePlaceholder")}
                    className={cn(fieldClass, "pl-10")}
                    maxLength={100}
                  />
                </div>
                <div className="flex gap-2">
                  <Button
                    type="button"
                    variant="secondary"
                    className="h-11 flex-1 rounded-2xl font-black"
                    disabled={profileLoading}
                    onClick={() => {
                      setName(user?.name ?? "");
                      setEditing(null);
                    }}
                  >
                    {t("profile.cancel")}
                  </Button>
                  <Button type="submit" disabled={profileLoading} className="h-11 flex-1 rounded-2xl font-black">
                    {profileLoading ? (
                      <>
                        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                        {t("profile.saving")}
                      </>
                    ) : (
                      t("profile.save")
                    )}
                  </Button>
                </div>
              </form>
            </Collapse>
          </div>

          {/* Username — read-only, cannot be changed. */}
          <div className="space-y-1">
            <Label className="px-1 text-sm font-extrabold text-muted-foreground">{t("profile.username")}</Label>
            <p className="px-1 text-[1.05rem] font-black text-foreground">{user?.username ?? ""}</p>
          </div>
        </div>
      </SettingsSection>

      <SettingsSection
        title={t("appearance.title")}
        description={
          theme === "system" && resolvedTheme
            ? t("appearance.descriptionSystem", {
                theme: t(resolvedTheme === "dark" ? "themeDark" : "themeLight"),
              })
            : t("appearance.description")
        }
      >
        <ThemeSegmentedControl value={theme} onChange={setTheme} labelFor={t} />
      </SettingsSection>

      <SettingsSection title={t("language.title")} description={t("language.description")}>
        <button
          type="button"
          onClick={() => setLanguageOpen(true)}
          className="flex w-full items-center justify-between gap-3 rounded-2xl py-1.5 text-left"
          aria-haspopup="dialog"
          aria-label={`${t("language.appLanguage")}, ${currentLanguageLabel}`}
        >
          <span className="text-[1.05rem] font-black text-foreground">{t("language.appLanguage")}</span>
          <span className="flex min-w-0 items-center gap-2">
            <span className="min-w-0 truncate text-sm font-black text-muted-foreground">
              {currentLanguageLabel}
            </span>
            <ChevronRight className="h-[18px] w-[18px] shrink-0 text-muted-foreground/55" strokeWidth={2.6} />
          </span>
        </button>
      </SettingsSection>

      <CenteredSelectorOverlay open={languageOpen} onOpenChange={setLanguageOpen} title={t("language.title")}>
        {LANGUAGE_OPTIONS.map((option, index) => {
          const active = option.code === selectedLanguageCode;
          return (
            <div key={option.code}>
              {index > 0 ? <SelectorDivider /> : null}
              <button
                type="button"
                onClick={() => chooseLanguage(option.code)}
                className="flex w-full items-center gap-3.5 px-5 py-3 text-left transition-colors hover:bg-muted-foreground/5"
              >
                <span className="min-w-0 flex-1 truncate text-lg font-black text-foreground">
                  {languageLabelFor(option)}
                </span>
                {active ? (
                  <Check className="h-[18px] w-[18px] shrink-0 text-accent" />
                ) : (
                  <span className="h-[18px] w-[18px] shrink-0" aria-hidden />
                )}
              </button>
            </div>
          );
        })}
      </CenteredSelectorOverlay>

      <SettingsSection title={t("aiSummary.title")} description={t("aiSummary.description")}>
        <div className="flex items-center justify-between gap-4">
          <div className="min-w-0">
            <p className="text-[1.05rem] font-black text-foreground">{t("aiSummary.toggle")}</p>
          </div>
          <SettingsSwitch
            checked={preferences?.aiSummaryEnabled !== false}
            ariaLabel={t("aiSummary.toggle")}
            onClick={() =>
              updatePreferences({
                aiSummaryEnabled: !(preferences?.aiSummaryEnabled !== false),
              })
            }
          />
        </div>
      </SettingsSection>

      {push.isSupported && (
        <SettingsSection title={t("notifications.title")}>
          <div className="flex items-center justify-between gap-4">
            <div className="min-w-0">
              <p className="text-[1.05rem] font-black text-foreground">{t("notifications.push")}</p>
              <p className="mt-0.5 text-sm font-extrabold text-muted-foreground">{pushSubtitle}</p>
            </div>
            {push.isLoading ? (
              <Loader2 className="h-5 w-5 shrink-0 animate-spin text-muted-foreground" />
            ) : (
              <SettingsSwitch
                checked={push.isSubscribed}
                disabled={push.permission === "denied"}
                ariaLabel={t("notifications.toggle")}
                onClick={handlePushToggle}
              />
            )}
          </div>
        </SettingsSection>
      )}

      <SettingsSection title={t("password.title")} description={t("password.description")}>
        {/* Collapsed summary with a Change affordance; expands to the change-password form. */}
        <div className="flex items-center justify-between gap-3">
          <div className="flex min-w-0 items-center gap-2.5">
            <Lock className="h-4 w-4 shrink-0 text-muted-foreground" />
            <span className="text-[1.05rem] font-black tracking-[0.18em] text-foreground">••••••••</span>
          </div>
          {editing !== "password" ? (
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className="shrink-0 rounded-xl font-black text-accent hover:text-accent"
              onClick={() => setEditing("password")}
            >
              <Key className="mr-1.5 h-3.5 w-3.5" />
              {t("password.changeAction")}
            </Button>
          ) : null}
        </div>
        <Collapse open={editing === "password"}>
          <form onSubmit={handlePasswordSubmit} className="space-y-4 pt-3">
          <div className="space-y-2">
            <Label htmlFor="currentPassword" className="px-1 text-sm font-extrabold text-muted-foreground">
              {t("password.current")}
            </Label>
            <div className="relative">
              <Lock className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                id="currentPassword"
                type={showCurrentPassword ? "text" : "password"}
                autoComplete="current-password"
                value={currentPassword}
                onChange={(e) => setCurrentPassword(e.target.value)}
                placeholder={t("password.currentPlaceholder")}
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
              {t("password.new")}
            </Label>
            <div className="relative">
              <Lock className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                id="newPassword"
                type={showNewPassword ? "text" : "password"}
                autoComplete="new-password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                placeholder={t("password.newPlaceholder")}
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
              {t("password.requirement")}
            </p>
          </div>
          <div className="space-y-2">
            <Label htmlFor="confirmNewPassword" className="px-1 text-sm font-extrabold text-muted-foreground">
              {t("password.confirm")}
            </Label>
            <div className="relative">
              <Lock className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                id="confirmNewPassword"
                type={showConfirmPassword ? "text" : "password"}
                autoComplete="new-password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                placeholder={t("password.confirmPlaceholder")}
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
          <Link
            href="/forgot-password"
            className="block px-1 text-[13px] font-bold text-accent transition active:opacity-60"
          >
            {t("password.forgot")}
          </Link>
          <div className="flex gap-2">
            <Button
              type="button"
              variant="secondary"
              className="h-12 flex-1 rounded-2xl font-black"
              disabled={passwordLoading}
              onClick={() => {
                setCurrentPassword("");
                setNewPassword("");
                setConfirmPassword("");
                setShowCurrentPassword(false);
                setShowNewPassword(false);
                setShowConfirmPassword(false);
                setEditing(null);
              }}
            >
              {t("password.cancel")}
            </Button>
            <Button type="submit" disabled={passwordLoading} className="h-12 flex-1 rounded-2xl font-black">
              {passwordLoading ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  {t("password.changing")}
                </>
              ) : (
                t("password.change")
              )}
            </Button>
          </div>
          </form>
        </Collapse>
      </SettingsSection>

      <SettingsSection title={t("dashboard.title")}>
        <div className="flex items-center justify-between gap-3">
          <div className="min-w-0 text-sm">
            <p className="font-black text-foreground">
              {apiKeyStatus?.enabled ? t("dashboard.enabled") : t("dashboard.disabled")}
            </p>
            <p className="text-xs font-extrabold text-muted-foreground">
              {apiKeyStatus?.enabled
                ? apiKeyStatus.keyPreview
                  ? t("dashboard.activeKeyEnding", { preview: apiKeyStatus.keyPreview })
                  : t("dashboard.activeKeyExists")
                : t("dashboard.noKey")}
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
                {apiKeyStatus?.enabled ? t("dashboard.revoking") : t("dashboard.generating")}
              </>
            ) : apiKeyStatus?.enabled ? (
              <>
                <Trash2 className="mr-2 h-4 w-4" />
                {t("dashboard.revokeKey")}
              </>
            ) : (
              <>
                <Key className="mr-2 h-4 w-4" />
                {t("dashboard.generateKey")}
              </>
            )}
          </Button>
        </div>

        {generatedApiKey && (
          <div className="space-y-2 rounded-2xl border border-border/60 bg-muted/40 p-3">
            <p className="text-xs font-extrabold text-muted-foreground">
              {t("dashboard.copyNow")}
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
