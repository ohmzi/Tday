import React, { useState, useEffect, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import {
  AtSign,
  BellRing,
  Calendar,
  Check,
  ChevronDown,
  ChevronRight,
  CircleHelp,
  Copy,
  Eye,
  EyeOff,
  Home,
  Info,
  Key,
  KeyRound,
  Languages,
  Leaf,
  Loader2,
  Lock,
  LogOut,
  Monitor,
  Moon,
  Pencil,
  RefreshCw,
  Search,
  Server,
  Settings,
  ShieldQuestion,
  Sparkles,
  Sun,
  Trash2,
  User,
  UsersRound,
  Waves,
  Webhook,
  type LucideIcon,
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
import NativePageHeader, { useNativePageBarSlots } from "@/components/app/NativePageHeader";
import MobileSearchHeader from "@/components/ui/MobileSearchHeader";
import EmptyState from "@/components/app/EmptyState";
import DataTransferCard from "./DataTransferCard";
import {
  CardDivider,
  RowIcon,
  RowIconSlot,
  SettingsOptionRow,
  SettingsPill,
} from "./SettingsControls";
import { nativeScreenAccentColors } from "@/components/app/nativeScreenTheme";
import { api } from "@/lib/api-client";
import parseApiDateTime from "@/lib/date/parseApiDateTime";
import { hapticTick } from "@/lib/haptics";
import { getErrorMessage } from "@/lib/error-message";
import { deleteLocalWorkspace } from "@/lib/local/localApi";
import {
  getLocalProtection,
  protectPlaintextWorkspace,
  type LocalProtection,
} from "@/lib/local/localDb";
import {
  MIN_PASSPHRASE_LENGTH,
  validatePassphrase,
} from "@/lib/local/passphrasePolicy";
import { resetAppData } from "@/lib/resetAppData";
import { CURRENT_APP_VERSION, formatDisplayVersion } from "@/features/release/lib/release";
import { useIsLocalMode } from "@/hooks/useAppMode";
import { usePushNotifications } from "@/hooks/usePushNotifications";
import {
  isRestingFloatersEnabled,
  setRestingFloatersEnabled,
} from "@/lib/floaterResting";
import { Link, usePathname } from "@/lib/navigation";
import { GuideHelpLink } from "@/features/guide/GuideHelpLink";
import { LANGUAGE_STORAGE_KEY, resolveInitialLocale } from "@/i18n";
import { DefaultHomeScreen } from "@/types/enums";
import {
  fetchAllSecurityQuestions,
  fetchSecurityQuestionStatus,
  updateSecurityQuestions,
  type SecurityQuestion,
  type SecurityQuestionStatus,
} from "@/lib/securityQuestions";

const themeOptions = [
  { value: "light", labelKey: "themeLight", icon: Sun },
  { value: "dark", labelKey: "themeDark", icon: Moon },
  { value: "system", labelKey: "themeSystem", icon: Monitor },
] as const;

// Labels reuse the "app" namespace's own root-feed strings (the ones RootDock renders)
// rather than new settings-scoped copies, so the wording always matches the in-app dock.
const defaultHomeScreenOptions = [
  { value: DefaultHomeScreen.scheduled, labelKey: "scheduledTaskHome", icon: Home },
  { value: DefaultHomeScreen.floater, labelKey: "root_feed_tab_floater", icon: Leaf },
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

type ApiKeyScope = "READ" | "FULL";

/** Metadata for an outbound webhook subscription (GET /api/webhook). */
type WebhookInfo = {
  id: string;
  url: string;
  events: string[];
  enabled: boolean;
  consecutiveFailures: number;
  lastStatus?: number | null;
  lastAttemptAt?: string | null;
  createdAt: string;
};

// The event types a webhook can filter on — mirrors WEBHOOK_EVENT_TYPES on the
// backend. An empty selection means "all events".
const WEBHOOK_EVENT_TYPES = [
  "todo.changed",
  "floater.changed",
  "list.changed",
  "floaterList.changed",
  "list.members",
  "completed.changed",
] as const;

/** Metadata for a personal API key, as returned by GET /api/user/api-key. */
type ApiKeyInfo = {
  id: string;
  label?: string | null;
  scope: string;
  keyPreview: string;
  createdAt?: string | null;
  lastUsedAt?: string | null;
  expiresAt?: string | null;
  expired?: boolean;
};

/** Rounded grouped section card with a big ExtraBold title — mirrors the
 * native SettingsSectionCard / SettingsSectionTitle. */
function SettingsSection({
  title,
  titleAction,
  children,
}: {
  title?: string;
  titleAction?: ReactNode;
  children: ReactNode;
}) {
  // The shadow lives here rather than on SheetCard: that component is shared with
  // the bottom sheets and the guide, which sit on their own surfaces already.
  return (
    <SheetCard className="space-y-4 p-[18px] shadow-[0_16px_34px_-24px_hsl(var(--shadow)/0.5)]">
      {/* A card that is a single action — How-To, Sign out — carries no heading:
          the row's own label is the only one it could have, twice over. */}
      {title ? (
        <div className="flex items-center justify-between gap-2">
          <h2 className="text-[1.4rem] font-black leading-tight text-foreground">{title}</h2>
          {titleAction}
        </div>
      ) : null}
      {children}
    </SheetCard>
  );
}

/** Sub-section heading used inside a combined card (e.g. Appearance + Language
 * grouped together) — matches the SettingsSection header styling. */
function SectionHeading({
  title,
  titleAction,
}: {
  title: string;
  titleAction?: ReactNode;
}) {
  return (
    <div className="flex items-center justify-between gap-2">
      <h2 className="text-[1.4rem] font-black leading-tight text-foreground">{title}</h2>
      {titleAction}
    </div>
  );
}

/** A row that only reports a fact — a version string, nothing to tap. Keeps the
 * icon column of the card it sits in so its label lines up with the rest. */
function SettingsFactRow({
  icon,
  label,
  value,
}: {
  icon?: LucideIcon;
  label: string;
  value: string;
}) {
  return (
    <div className="flex items-center gap-3 py-1.5">
      <span className="flex min-w-0 flex-1 items-center gap-3.5">
        {icon ? <RowIcon icon={icon} /> : <RowIconSlot />}
        <span className="min-w-0 flex-1 text-[1.05rem] font-black text-foreground">{label}</span>
      </span>
      <span className="shrink-0 text-sm font-black text-muted-foreground">{value}</span>
    </div>
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

/**
 * Sliding 2-way segmented control for "Default home screen" — same shape as
 * ThemeSegmentedControl (a named, mutually-exclusive choice, not a toggle), kept as its own
 * component rather than generalizing that one so the theme control's already-shipped
 * behavior stays untouched.
 */
function DefaultHomeScreenSegmentedControl({
  value,
  onChange,
  labelFor,
}: {
  value: DefaultHomeScreen;
  onChange: (value: DefaultHomeScreen) => void;
  labelFor: (key: string) => string;
}) {
  const index = Math.max(
    0,
    defaultHomeScreenOptions.findIndex((option) => option.value === value),
  );
  return (
    <div className="relative flex h-14 rounded-[22px] bg-muted/60 p-1.5">
      <span
        aria-hidden
        className="absolute bottom-1.5 left-1.5 top-1.5 w-[calc((100%-0.75rem)/2)] rounded-[16px] bg-card shadow-sm transition-transform duration-200 ease-out"
        style={{ transform: `translateX(${index * 100}%)` }}
      />
      {defaultHomeScreenOptions.map((option) => {
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

/** Editor action buttons, matching the native SettingsEditorActions capsules:
 * Cancel = subdued onSurface text on a faint onSurface capsule, Save = primary capsule. */
const editorCancelClass =
  "h-12 flex-1 rounded-full font-black bg-foreground/[0.06] text-foreground/70 hover:bg-foreground/10 hover:text-foreground/80 active:opacity-80";
const editorSaveClass = "h-12 flex-1 rounded-full font-black";

/** API key timestamps are zoneless-UTC strings from the backend; parse first,
 * then show the viewer's local wall-clock time. */
const formatKeyTimestamp = (value: string) =>
  parseApiDateTime(value).toLocaleString(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  });

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
  const { t: guideDict } = useTranslation("guide");
  const { t: appDict } = useTranslation("app");
  const { user, refreshSession, logout } = useAuth();
  // Local Mode has no account, no server and no collaborators, so everything
  // that talks to one is hidden — mirroring the native settings screens.
  const isLocalMode = useIsLocalMode();
  const { preferences, updatePreferences } = useUserPreferences();
  const { toast } = useToast();
  const { theme = "system", setTheme } = useTheme();

  const navigate = useNavigate();
  const pathname = usePathname();
  const [languageOpen, setLanguageOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const barSlots = useNativePageBarSlots();
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
  const [editing, setEditing] = useState<
    "name" | "password" | "securityQuestions" | null
  >(null);

  const [name, setName] = useState("");
  const [profileLoading, setProfileLoading] = useState(false);

  useEffect(() => {
    if (user?.name) setName(user.name);
  }, [user?.name]);

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [passwordLoading, setPasswordLoading] = useState(false);
  const [showCurrentPassword, setShowCurrentPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  // Security questions — collapsed summary uses the status (fetched on mount); the
  // catalogue is lazy-loaded when the editor first opens.
  const [sqStatus, setSqStatus] = useState<SecurityQuestionStatus | null>(null);
  const [sqCatalogue, setSqCatalogue] = useState<SecurityQuestion[]>([]);
  const [sqQuestionIds, setSqQuestionIds] = useState<[number | null, number | null, number | null]>([
    null,
    null,
    null,
  ]);
  const [sqAnswers, setSqAnswers] = useState<[string, string, string]>(["", "", ""]);
  const [sqCurrentPassword, setSqCurrentPassword] = useState("");
  const [showSqPassword, setShowSqPassword] = useState(false);
  const [sqError, setSqError] = useState<string | null>(null);
  const [sqLoading, setSqLoading] = useState(false);
  // Already-configured accounts must re-enter their password to change questions;
  // legacy not-configured accounts set them for the first time without one.
  const sqConfigured = sqStatus != null && !sqStatus.requireSecurityQuestions;

  const push = usePushNotifications();
  const [restingFloatersOn, setRestingFloatersOn] = useState(() =>
    isRestingFloatersEnabled(),
  );

  const [apiKeys, setApiKeys] = useState<ApiKeyInfo[] | null>(null);
  const [generatedApiKey, setGeneratedApiKey] = useState<string | null>(null);
  const [apiKeyLoading, setApiKeyLoading] = useState(false);
  const [showApiKey, setShowApiKey] = useState(false);
  const [newKeyLabel, setNewKeyLabel] = useState("");
  const [newKeyScope, setNewKeyScope] = useState<ApiKeyScope>("READ");
  const [revokingKeyId, setRevokingKeyId] = useState<string | null>(null);
  const [generateKeyDialogOpen, setGenerateKeyDialogOpen] = useState(false);
  const [expandedKeyId, setExpandedKeyId] = useState<string | null>(null);
  const [calendarFeed, setCalendarFeed] = useState<{
    enabled: boolean;
    tokenPreview?: string | null;
    createdAt?: string | null;
  } | null>(null);
  const [generatedFeedUrl, setGeneratedFeedUrl] = useState<string | null>(null);
  const [feedLoading, setFeedLoading] = useState(false);
  const [showFeedUrl, setShowFeedUrl] = useState(false);
  const [webhooks, setWebhooks] = useState<WebhookInfo[] | null>(null);
  const [newWebhookUrl, setNewWebhookUrl] = useState("");
  const [newWebhookEvents, setNewWebhookEvents] = useState<string[]>([]);
  const [webhookLoading, setWebhookLoading] = useState(false);
  const [generatedWebhookSecret, setGeneratedWebhookSecret] = useState<string | null>(null);
  const [showWebhookSecret, setShowWebhookSecret] = useState(false);
  const [revokingWebhookId, setRevokingWebhookId] = useState<string | null>(null);
  const [signingOut, setSigningOut] = useState(false);
  const [deleteLocalOpen, setDeleteLocalOpen] = useState(false);
  // Read once per render pass rather than subscribed to: the only thing that can
  // change it while this screen is open is the encrypt flow just below.
  const [localProtection, setLocalProtection] = useState<LocalProtection | null>(
    () => getLocalProtection(),
  );
  const [encryptOpen, setEncryptOpen] = useState(false);
  const [encryptPassphrase, setEncryptPassphrase] = useState("");
  const [encryptConfirmation, setEncryptConfirmation] = useState("");
  const [encryptAcknowledged, setEncryptAcknowledged] = useState(false);
  const [encryptBusy, setEncryptBusy] = useState(false);
  const [encryptError, setEncryptError] = useState("");
  const [resetCacheOpen, setResetCacheOpen] = useState(false);
  const [resettingCache, setResettingCache] = useState(false);
  // The backend's own version, for the About card's Server row. Stays null
  // until the probe answers, and the row stays away with it — an empty or
  // "unknown" Server line says less than no line at all.
  const [serverVersion, setServerVersion] = useState<string | null>(null);
  const appVersionLabel = `v${formatDisplayVersion(CURRENT_APP_VERSION) ?? CURRENT_APP_VERSION}`;

  useEffect(() => {
    if (isLocalMode) return;
    let cancelled = false;
    fetchSecurityQuestionStatus()
      .then((status) => {
        if (!cancelled) setSqStatus(status);
      })
      .catch(() => {
        if (!cancelled) setSqStatus({ questionIds: [], requireSecurityQuestions: true });
      });
    return () => {
      cancelled = true;
    };
  }, [isLocalMode]);

  // Local Mode never asks: there is no server to ask, and the row that would
  // show the answer is not rendered there either. `/api/mobile/probe` is the
  // same unauthenticated endpoint the native apps read their backend version
  // from, so this needs no session and no new route.
  useEffect(() => {
    if (isLocalMode) return;
    let cancelled = false;
    api
      .GET({ url: "/api/mobile/probe" })
      .then((res) => {
        const version = res?.appVersion;
        if (!cancelled) setServerVersion(typeof version === "string" && version ? version : null);
      })
      .catch(() => {
        if (!cancelled) setServerVersion(null);
      });
    return () => {
      cancelled = true;
    };
  }, [isLocalMode]);

  useEffect(() => {
    if (isLocalMode) return;
    let cancelled = false;
    api
      .GET({ url: "/api/user/api-key" })
      .then((res) => {
        if (!cancelled) setApiKeys(res?.keys ?? []);
      })
      .catch(() => {
        if (!cancelled) setApiKeys([]);
      });
    return () => {
      cancelled = true;
    };
  }, [isLocalMode]);

  const handleGenerateApiKey = async () => {
    setApiKeyLoading(true);
    try {
      const res = await api.POST({
        url: "/api/user/api-key",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          label: newKeyLabel.trim() || undefined,
          scope: newKeyScope,
        }),
      });
      const created = res?.apiKey;
      setGeneratedApiKey(created?.key ?? null);
      setShowApiKey(true);
      setNewKeyLabel("");
      if (created) {
        setApiKeys((prev) => [
          {
            id: created.id,
            label: created.label ?? null,
            scope: created.scope,
            keyPreview: created.keyPreview,
            createdAt: created.createdAt ?? null,
            expiresAt: created.expiresAt ?? null,
          },
          ...(prev ?? []),
        ]);
      }
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

  const handleRevokeApiKey = async (id: string) => {
    setRevokingKeyId(id);
    try {
      await api.DELETE({ url: `/api/user/api-key/${id}` });
      setApiKeys((prev) => (prev ?? []).filter((key) => key.id !== id));
      toast({ description: t("toast.apiKeyRevoked") });
    } catch (err) {
      toast({
        description: getErrorMessage(err, t("toast.apiKeyRevokeFailed")),
        variant: "destructive",
      });
    } finally {
      setRevokingKeyId(null);
    }
  };

  useEffect(() => {
    if (isLocalMode) return;
    let cancelled = false;
    api
      .GET({ url: "/api/user/calendar-feed" })
      .then((res) => {
        if (!cancelled) setCalendarFeed(res?.status ?? { enabled: false });
      })
      .catch(() => {
        if (!cancelled) setCalendarFeed({ enabled: false });
      });
    return () => {
      cancelled = true;
    };
  }, [isLocalMode]);

  const handleGenerateFeed = async () => {
    setFeedLoading(true);
    try {
      const res = await api.POST({
        url: "/api/user/calendar-feed",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({}),
      });
      const token = res?.feed?.token as string | undefined;
      // The subscribe URL is built client-side so the server never needs to know
      // its own public origin.
      const url = token
        ? `${window.location.origin}/calendar/${token}.ics`
        : null;
      setGeneratedFeedUrl(url);
      setShowFeedUrl(true);
      setCalendarFeed({
        enabled: true,
        tokenPreview: res?.feed?.tokenPreview ?? null,
        createdAt: res?.feed?.createdAt ?? null,
      });
      toast({ description: t("toast.calendarFeedGenerated") });
    } catch (err) {
      toast({
        description: getErrorMessage(err, t("toast.calendarFeedGenerateFailed")),
        variant: "destructive",
      });
    } finally {
      setFeedLoading(false);
    }
  };

  const handleRevokeFeed = async () => {
    setFeedLoading(true);
    try {
      await api.DELETE({ url: "/api/user/calendar-feed" });
      setCalendarFeed({ enabled: false });
      setGeneratedFeedUrl(null);
      setShowFeedUrl(false);
      toast({ description: t("toast.calendarFeedRevoked") });
    } catch (err) {
      toast({
        description: getErrorMessage(err, t("toast.calendarFeedRevokeFailed")),
        variant: "destructive",
      });
    } finally {
      setFeedLoading(false);
    }
  };

  const handleCopyFeedUrl = async () => {
    if (!generatedFeedUrl) return;
    try {
      await navigator.clipboard.writeText(generatedFeedUrl);
      toast({ description: t("toast.calendarFeedCopied") });
    } catch {
      toast({ description: t("toast.calendarFeedCopyFailed"), variant: "destructive" });
    }
  };

  useEffect(() => {
    if (isLocalMode) return;
    let cancelled = false;
    api
      .GET({ url: "/api/webhook" })
      .then((res) => {
        if (!cancelled) setWebhooks(res?.webhooks ?? []);
      })
      .catch(() => {
        if (!cancelled) setWebhooks([]);
      });
    return () => {
      cancelled = true;
    };
  }, [isLocalMode]);

  const toggleWebhookEvent = (event: string) => {
    setNewWebhookEvents((prev) =>
      prev.includes(event) ? prev.filter((e) => e !== event) : [...prev, event],
    );
  };

  const handleCreateWebhook = async () => {
    setWebhookLoading(true);
    try {
      const res = await api.POST({
        url: "/api/webhook",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          url: newWebhookUrl.trim(),
          events: newWebhookEvents,
        }),
      });
      const created = res?.webhook;
      setGeneratedWebhookSecret(created?.secret ?? null);
      setShowWebhookSecret(true);
      setNewWebhookUrl("");
      setNewWebhookEvents([]);
      if (created) {
        setWebhooks((prev) => [
          {
            id: created.id,
            url: created.url,
            events: created.events ?? [],
            enabled: true,
            consecutiveFailures: 0,
            createdAt: created.createdAt,
          },
          ...(prev ?? []),
        ]);
      }
      toast({ description: t("toast.webhookCreated") });
    } catch (err) {
      toast({
        description: getErrorMessage(err, t("toast.webhookCreateFailed")),
        variant: "destructive",
      });
    } finally {
      setWebhookLoading(false);
    }
  };

  const handleDeleteWebhook = async (id: string) => {
    setRevokingWebhookId(id);
    try {
      await api.DELETE({ url: `/api/webhook/${id}` });
      setWebhooks((prev) => (prev ?? []).filter((w) => w.id !== id));
      toast({ description: t("toast.webhookDeleted") });
    } catch (err) {
      toast({
        description: getErrorMessage(err, t("toast.webhookDeleteFailed")),
        variant: "destructive",
      });
    } finally {
      setRevokingWebhookId(null);
    }
  };

  const handleCopyWebhookSecret = async () => {
    if (!generatedWebhookSecret) return;
    try {
      await navigator.clipboard.writeText(generatedWebhookSecret);
      toast({ description: t("toast.webhookSecretCopied") });
    } catch {
      toast({ description: t("toast.webhookSecretCopyFailed"), variant: "destructive" });
    }
  };

  // Mirrors the sidebar UserCard logout: on success logout() redirects away, so we
  // only need to clear the busy state when it fails.
  const handleLogout = async () => {
    if (signingOut) return;
    setSigningOut(true);
    try {
      await logout();
    } catch (error) {
      setSigningOut(false);
      toast({
        variant: "destructive",
        description:
          error instanceof Error && error.message
            ? error.message
            : "Unable to log out. Please try again.",
      });
    }
  };

  // Destroys the browser workspace. Irreversible — there is no server copy, which
  // is exactly what the confirmation spells out.
  const handleDeleteLocalData = async () => {
    deleteLocalWorkspace();
    setDeleteLocalOpen(false);
    toast({ description: t("workspace.deleteDone") });
    await handleLogout();
  };

  const closeEncryptDialog = () => {
    setEncryptOpen(false);
    // Nothing keeps a copy of the passphrase once the dialog is done with it.
    setEncryptPassphrase("");
    setEncryptConfirmation("");
    setEncryptAcknowledged(false);
    setEncryptError("");
  };

  // Seals a workspace the user chose to leave in the clear. One-way: there is no
  // matching "decrypt", because a vault anyone at an unlocked session can undo
  // protects nothing.
  const handleEncryptWorkspace = async () => {
    const problem = validatePassphrase(encryptPassphrase, encryptConfirmation);
    if (problem === "too-short") {
      setEncryptError(t("workspace.encryptTooShort", { count: MIN_PASSPHRASE_LENGTH }));
      return;
    }
    if (problem === "mismatch") {
      setEncryptError(t("workspace.encryptMismatch"));
      return;
    }
    setEncryptBusy(true);
    setEncryptError("");
    try {
      await protectPlaintextWorkspace(encryptPassphrase);
      setLocalProtection(getLocalProtection());
      closeEncryptDialog();
      toast({ description: t("workspace.encryptDone") });
    } catch (error) {
      console.error(error);
      setEncryptError(getErrorMessage(error, t("workspace.encryptFailed")));
    } finally {
      setEncryptBusy(false);
    }
  };

  // Manual escape hatch for a client stuck on a half-updated build (see
  // lib/resetAppData). No success toast — the page reloads immediately.
  const handleResetAppData = async () => {
    setResettingCache(true);
    try {
      await resetAppData();
    } catch {
      setResettingCache(false);
      setResetCacheOpen(false);
      toast({
        description: t("troubleshooting.resetFailed"),
        variant: "destructive",
      });
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

  const openGenerateKeyDialog = () => {
    setNewKeyLabel("");
    setNewKeyScope("READ");
    setGeneratedApiKey(null);
    setShowApiKey(false);
    setGenerateKeyDialogOpen(true);
  };

  const closeGenerateKeyDialog = () => {
    if (apiKeyLoading) return;
    setGenerateKeyDialogOpen(false);
    setGeneratedApiKey(null);
    setShowApiKey(false);
    setNewKeyLabel("");
  };

  const handlePushToggle = async () => {
    try {
      if (push.isSubscribed) {
        await push.unsubscribe();
      } else {
        await push.subscribe();
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
    // Validation errors render inline under the field, not as toasts.
    if (newPassword.length < 8) {
      setPasswordError(t("toast.passwordTooShort"));
      return;
    }
    if (newPassword !== confirmPassword) {
      setPasswordError(t("toast.passwordsDoNotMatch"));
      return;
    }
    setPasswordError(null);
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

  // Seed three distinct question selections — the user's existing ones first, then the
  // first unused catalogue entries to fill any gaps.
  const seedQuestionIds = (
    preferred: number[],
    catalogue: SecurityQuestion[],
  ): [number | null, number | null, number | null] => {
    const chosen: number[] = [];
    for (const id of preferred) {
      if (catalogue.some((q) => q.id === id) && !chosen.includes(id)) chosen.push(id);
      if (chosen.length === 3) break;
    }
    for (const q of catalogue) {
      if (chosen.length === 3) break;
      if (!chosen.includes(q.id)) chosen.push(q.id);
    }
    return [chosen[0] ?? null, chosen[1] ?? null, chosen[2] ?? null];
  };

  const openSecurityQuestions = async () => {
    setEditing("securityQuestions");
    setSqError(null);
    setSqCurrentPassword("");
    setShowSqPassword(false);
    setSqAnswers(["", "", ""]);
    let catalogue = sqCatalogue;
    if (catalogue.length === 0) {
      try {
        catalogue = await fetchAllSecurityQuestions();
        setSqCatalogue(catalogue);
      } catch (err) {
        toast({
          description: getErrorMessage(err, t("toast.securityQuestionsLoadFailed")),
          variant: "destructive",
        });
        return;
      }
    }
    setSqQuestionIds(seedQuestionIds(sqStatus?.questionIds ?? [], catalogue));
  };

  const closeSecurityQuestions = () => {
    setSqCurrentPassword("");
    setShowSqPassword(false);
    setSqAnswers(["", "", ""]);
    setSqError(null);
    setEditing(null);
  };

  const handleSecurityQuestionsSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const ids = sqQuestionIds;
    if (ids.some((id) => id == null)) {
      setSqError(t("securityQuestions.errorSelectAll"));
      return;
    }
    if (new Set(ids).size !== 3) {
      setSqError(t("securityQuestions.errorDistinct"));
      return;
    }
    if (sqAnswers.some((a) => a.trim().length === 0)) {
      setSqError(t("securityQuestions.errorAnswersRequired"));
      return;
    }
    if (sqConfigured && !sqCurrentPassword) {
      setSqError(t("securityQuestions.errorPasswordRequired"));
      return;
    }
    setSqError(null);
    setSqLoading(true);
    try {
      const answers = ids.map((id, i) => ({ questionId: id as number, answer: sqAnswers[i].trim() }));
      await updateSecurityQuestions(answers, sqConfigured ? sqCurrentPassword : undefined);
      toast({ description: t("toast.securityQuestionsUpdated") });
      setSqStatus({ questionIds: ids as number[], requireSecurityQuestions: false });
      closeSecurityQuestions();
    } catch (err) {
      toast({
        description: getErrorMessage(err, t("toast.securityQuestionsUpdateFailed")),
        variant: "destructive",
      });
    } finally {
      setSqLoading(false);
    }
  };

  // Search on this screen is scoped to this screen: it keeps the cards whose own
  // labels carry the word and drops the rest. A card, not a row, is the unit —
  // the rows inside one share its heading and its dividers, so hiding half of a
  // card leaves the other half reading as a fragment of nothing.
  const settingsQuery = searchQuery.trim().toLowerCase();
  const cardMatches = (...labels: string[]) =>
    settingsQuery.length === 0 ||
    labels.some((label) => label.toLowerCase().includes(settingsQuery));

  const showAccountCard =
    !isLocalMode &&
    cardMatches(
      t("profile.title"),
      t("profile.name"),
      t("profile.username"),
      t("password.title"),
      t("securityQuestions.title"),
    );
  const showAppearanceCard = cardMatches(
    t("appearance.title"),
    t("themeLight"),
    t("themeDark"),
    t("themeSystem"),
    t("behavior.title"),
    t("behavior.defaultHomeScreen"),
    appDict("scheduledTaskHome"),
    appDict("root_feed_tab_floater"),
    t("language.title"),
    t("language.appLanguage"),
  );
  const showPreferencesCard = cardMatches(
    t("featureToggle.title"),
    t("aiSummary.title"),
    // The rows print the short titles, matching iOS and Android. The longer
    // descriptive variants below print nowhere, but "fade" and "push" are still
    // the words people type, so they stay in the term list.
    t("restingFloaters.title"),
    t("restingFloaters.toggle"),
    ...(push.isSupported ? [t("notifications.title"), t("notifications.push")] : []),
  );
  // Server Mode only. Export and import are an account's data moving in and out
  // of an account; a browser-only workspace has no account to move it between,
  // and the card's own "sign in to a server to import" line was the tell that it
  // was half-usable there. Hidden outright rather than half-disabled.
  const showDataCard =
    !isLocalMode && cardMatches(t("data.title"), t("data.download"), t("data.import"));
  // The two blurbs stopped being printed when the "?" took over explaining these
  // cards, but they are still sentences people half-remember and type at the
  // search box, so they stay in the term lists.
  const showCalendarFeedCard =
    !isLocalMode && cardMatches(t("calendarFeed.title"), t("calendarFeed.blurb"));
  const showWebhooksCard =
    !isLocalMode && cardMatches(t("webhooks.title"), t("webhooks.blurb"), t("webhooks.add"));
  const showDashboardCard =
    !isLocalMode && cardMatches(t("dashboard.title"), t("dashboard.generateKey"));
  // Splitting the old workspace/dashboard card splits its term list too: each
  // card answers only to labels it still shows, so a hit never scrolls to a card
  // whose matching row moved out from under it.
  const showAboutCard = cardMatches(
    t("about.title"),
    t("about.appVersion"),
    ...(isLocalMode
      ? [
          t("workspace.localTitle"),
          t("workspace.localDetail"),
          t("workspace.encrypt"),
        ]
      : [t("about.server")]),
  );
  const showMaintenanceCard = cardMatches(
    ...(!isLocalMode && user?.role === "ADMIN" ? [sidebarDict("admin")] : []),
    t("troubleshooting.reset"),
  );
  const showGuideCard = cardMatches(guideDict("title"));
  const showSignOutCard = cardMatches(
    ...(isLocalMode
      ? [t("workspace.leave"), t("workspace.delete")]
      : [t("signOut")]),
  );
  const noSettingsMatch =
    settingsQuery.length > 0 &&
    !showAccountCard &&
    !showAppearanceCard &&
    !showPreferencesCard &&
    !showDataCard &&
    !showCalendarFeedCard &&
    !showWebhooksCard &&
    !showDashboardCard &&
    !showAboutCard &&
    !showMaintenanceCard &&
    !showGuideCard &&
    !showSignOutCard;

  return (
    <div className="w-full space-y-3 pb-10">
      {/* The search field is this page's pinned bar, so the header below renders
          only the block that scrolls away and docks its title into it — the same
          split the custom list uses. */}
      <MobileSearchHeader
        searchQuery={searchQuery}
        onSearchChange={setSearchQuery}
        placeholder={`${appDict("searchIn")} ${sidebarDict("settings")}...`}
        pageCollapse={{
          ...barSlots,
          title: sidebarDict("settings"),
          accentColor: nativeScreenAccentColors.settings,
        }}
      />

      <NativePageHeader
        title={sidebarDict("settings")}
        accentColor={nativeScreenAccentColors.settings}
        icon={Settings}
        barSlots={barSlots}
        className="mb-1"
      />

      {/* Account card — Server Mode only; a local workspace has no account,
          password or recovery questions to manage. No heading, matching the
          native cards: "Profile" over a Name / Password / Security questions
          stack names nothing the rows do not already say. */}
      {showAccountCard && (
      <SettingsSection>
        <div className="space-y-4">
          {/* Name — collapsed summary with an Edit affordance, expands to an inline editor. */}
          <div className="space-y-2">
            <div className="flex items-center justify-between gap-3">
              <div className="flex min-w-0 items-center gap-3.5">
                <RowIcon icon={User} />
                <div className="min-w-0">
                  <Label className="text-sm font-extrabold text-muted-foreground">{t("profile.name")}</Label>
                  <p className="text-[1.05rem] font-black text-foreground truncate">
                    {user?.name || t("profile.unknownUser")}
                  </p>
                </div>
              </div>
              {editing !== "name" ? (
                <SettingsPill
                  icon={Pencil}
                  label={t("profile.edit")}
                  onClick={() => {
                    setName(user?.name ?? "");
                    setEditing("name");
                  }}
                />
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
                    variant="ghost"
                    className={editorCancelClass}
                    disabled={profileLoading}
                    onClick={() => {
                      setName(user?.name ?? "");
                      setEditing(null);
                    }}
                  >
                    {t("profile.cancel")}
                  </Button>
                  <Button type="submit" disabled={profileLoading} className={editorSaveClass}>
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
          <div className="flex min-w-0 items-center gap-3.5">
            <RowIcon icon={AtSign} />
            <div className="min-w-0 space-y-1">
              <Label className="text-sm font-extrabold text-muted-foreground">{t("profile.username")}</Label>
              <p className="text-[1.05rem] font-black text-foreground">{user?.username ?? ""}</p>
            </div>
          </div>

          <div className="h-px bg-border/60" />

          {/* Password — collapsed summary with a Change affordance; expands to the change-password form. */}
          <div className="space-y-2">
            <div className="flex items-center justify-between gap-3">
              <div className="flex min-w-0 items-center gap-3.5">
                <RowIcon icon={Lock} />
                <div className="min-w-0">
                  <Label className="text-sm font-extrabold text-muted-foreground">{t("password.title")}</Label>
                  <p className="text-[1.05rem] font-black tracking-[0.18em] text-foreground">••••••••</p>
                </div>
              </div>
              {editing !== "password" ? (
                <SettingsPill
                  icon={Key}
                  label={t("password.changeAction")}
                  onClick={() => setEditing("password")}
                />
              ) : null}
            </div>
            <Collapse open={editing === "password"}>
              <form onSubmit={handlePasswordSubmit} className="space-y-4 pt-1">
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
                      onChange={(e) => {
                        setNewPassword(e.target.value);
                        setPasswordError(null);
                      }}
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
                      onChange={(e) => {
                        setConfirmPassword(e.target.value);
                        setPasswordError(null);
                      }}
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
                  {passwordError ? (
                    <p className="px-1 text-xs font-extrabold text-destructive">{passwordError}</p>
                  ) : null}
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
                    variant="ghost"
                    className={editorCancelClass}
                    disabled={passwordLoading}
                    onClick={() => {
                      setCurrentPassword("");
                      setNewPassword("");
                      setConfirmPassword("");
                      setPasswordError(null);
                      setShowCurrentPassword(false);
                      setShowNewPassword(false);
                      setShowConfirmPassword(false);
                      setEditing(null);
                    }}
                  >
                    {t("password.cancel")}
                  </Button>
                  <Button type="submit" disabled={passwordLoading} className={editorSaveClass}>
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
          </div>

          <div className="h-px bg-border/60" />

          {/* Security questions — collapsed summary with a Change affordance; expands to selects + answers. */}
          <div className="space-y-2">
            <div className="flex items-center justify-between gap-3">
              <div className="flex min-w-0 items-center gap-3.5">
                <RowIcon icon={ShieldQuestion} />
                <div className="min-w-0">
                  <Label className="text-sm font-extrabold text-muted-foreground">{t("securityQuestions.title")}</Label>
                  <p className="text-[1.05rem] font-black text-foreground">
                    {sqStatus == null
                      ? "—"
                      : sqConfigured
                        ? t("securityQuestions.configured")
                        : t("securityQuestions.notConfigured")}
                  </p>
                </div>
              </div>
              {editing !== "securityQuestions" ? (
                <SettingsPill
                  icon={ShieldQuestion}
                  label={t("securityQuestions.changeAction")}
                  onClick={openSecurityQuestions}
                />
              ) : null}
            </div>
            <Collapse open={editing === "securityQuestions"}>
              <form onSubmit={handleSecurityQuestionsSubmit} className="space-y-4 pt-1">
                {sqConfigured ? (
                  <div className="space-y-2">
                    <Label htmlFor="sqCurrentPassword" className="px-1 text-sm font-extrabold text-muted-foreground">
                      {t("password.current")}
                    </Label>
                    <div className="relative">
                      <Lock className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                      <Input
                        id="sqCurrentPassword"
                        type={showSqPassword ? "text" : "password"}
                        autoComplete="current-password"
                        value={sqCurrentPassword}
                        onChange={(e) => {
                          setSqCurrentPassword(e.target.value);
                          setSqError(null);
                        }}
                        placeholder={t("password.currentPlaceholder")}
                        className={cn(fieldClass, "pl-10 pr-10")}
                      />
                      {sqCurrentPassword && (
                        <button
                          type="button"
                          onClick={() => setShowSqPassword(!showSqPassword)}
                          className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground transition-colors hover:text-foreground"
                        >
                          {showSqPassword ? <EyeOff className="h-4 w-4 opacity-40" /> : <Eye className="h-4 w-4 opacity-40" />}
                        </button>
                      )}
                    </div>
                  </div>
                ) : null}

                {[0, 1, 2].map((i) => {
                  const otherIds = sqQuestionIds.filter((_, idx) => idx !== i);
                  const options = sqCatalogue.filter((q) => !otherIds.includes(q.id));
                  return (
                    <div key={i} className="space-y-2">
                      <Label className="px-1 text-sm font-extrabold text-muted-foreground">
                        {t("securityQuestions.questionLabel", { index: i + 1 })}
                      </Label>
                      <select
                        value={sqQuestionIds[i] ?? ""}
                        onChange={(e) => {
                          const next = [...sqQuestionIds] as [number | null, number | null, number | null];
                          next[i] = Number(e.target.value);
                          setSqQuestionIds(next);
                          setSqError(null);
                        }}
                        aria-label={t("securityQuestions.questionLabel", { index: i + 1 })}
                        className={cn(fieldClass, "w-full px-3 text-foreground")}
                      >
                        {options.map((q) => (
                          <option key={q.id} value={q.id}>
                            {q.text}
                          </option>
                        ))}
                      </select>
                      <Input
                        value={sqAnswers[i]}
                        onChange={(e) => {
                          const next = [...sqAnswers] as [string, string, string];
                          next[i] = e.target.value;
                          setSqAnswers(next);
                          setSqError(null);
                        }}
                        placeholder={t("securityQuestions.answerPlaceholder")}
                        className={fieldClass}
                        autoComplete="off"
                      />
                    </div>
                  );
                })}

                {sqError ? (
                  <p className="px-1 text-xs font-extrabold text-destructive">{sqError}</p>
                ) : null}

                <div className="flex gap-2">
                  <Button
                    type="button"
                    variant="ghost"
                    className={editorCancelClass}
                    disabled={sqLoading}
                    onClick={closeSecurityQuestions}
                  >
                    {t("password.cancel")}
                  </Button>
                  <Button type="submit" disabled={sqLoading} className={editorSaveClass}>
                    {sqLoading ? (
                      <>
                        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                        {t("securityQuestions.saving")}
                      </>
                    ) : (
                      t("securityQuestions.save")
                    )}
                  </Button>
                </div>
              </form>
            </Collapse>
          </div>
        </div>
      </SettingsSection>
      )}

      {showAppearanceCard && (
      <SheetCard className="space-y-4 p-[18px] shadow-[0_16px_34px_-24px_hsl(var(--shadow)/0.5)]">
        <SectionHeading title={t("appearance.title")} />
        <ThemeSegmentedControl value={theme} onChange={setTheme} labelFor={t} />

        <CardDivider />

        <SectionHeading title={t("behavior.title")} />
        <p className="text-[0.95rem] font-bold text-muted-foreground">
          {t("behavior.defaultHomeScreen")}
        </p>
        <DefaultHomeScreenSegmentedControl
          value={preferences?.defaultHomeScreen ?? DefaultHomeScreen.scheduled}
          onChange={(value) => updatePreferences({ defaultHomeScreen: value })}
          labelFor={appDict}
        />

        <CardDivider />

        <SectionHeading title={t("language.title")} />
        <button
          type="button"
          onClick={() => setLanguageOpen(true)}
          className="flex w-full items-center justify-between gap-3 rounded-2xl py-1.5 text-left"
          aria-haspopup="dialog"
          aria-label={`${t("language.appLanguage")}, ${currentLanguageLabel}`}
        >
          <span className="flex min-w-0 flex-1 items-center gap-3.5">
            <RowIcon icon={Languages} />
            <span className="min-w-0 truncate text-[1.05rem] font-black text-foreground">
              {t("language.appLanguage")}
            </span>
          </span>
          {/* The value is the pill's label, so the pill's glyph has to say
              something the label does not: repeating the row's own Languages
              icon would say nothing, and a chevron pointing right would promise
              another screen. Down is the picker this row actually opens. */}
          <SettingsPill icon={ChevronDown} label={currentLanguageLabel} />
        </button>
      </SheetCard>
      )}

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

      {showPreferencesCard && (
      <SheetCard className="space-y-4 p-[18px] shadow-[0_16px_34px_-24px_hsl(var(--shadow)/0.5)]">
        {/* One card, one title, one "?" — Android and iOS both draw these
            switches under a single `Feature toggle` heading, and three headings
            with three help links each was more chrome than the three rows they
            introduced. The link lands on `ai-summary` because the guide now
            lists it first under Integrations and it is this card's own first
            row, so the reader arrives at the top of a section rather than
            mid-list. The other two switches are documented in their own
            sections (resting-floaters under Organizing, push-notifications
            under Reminders), which is where their topics belong. */}
        <SectionHeading
          title={t("featureToggle.title")}
          titleAction={<GuideHelpLink topic="ai-summary" />}
        />
        <div className="flex items-center justify-between gap-4">
          <div className="flex min-w-0 items-center gap-3.5">
            <RowIcon icon={Sparkles} />
            <div className="min-w-0">
              <p className="text-[1.05rem] font-black text-foreground">{t("aiSummary.title")}</p>
            </div>
          </div>
          <SettingsSwitch
            checked={preferences?.aiSummaryEnabled !== false}
            ariaLabel={t("aiSummary.title")}
            onClick={() =>
              updatePreferences({
                aiSummaryEnabled: !(preferences?.aiSummaryEnabled !== false),
              })
            }
          />
        </div>

        <CardDivider />
        <div className="flex items-center justify-between gap-4">
          <div className="flex min-w-0 items-center gap-3.5">
            <RowIcon icon={Waves} />
            <div className="min-w-0">
              <p className="text-[1.05rem] font-black text-foreground">
                {t("restingFloaters.title")}
              </p>
            </div>
          </div>
          <SettingsSwitch
            checked={restingFloatersOn}
            // The visible label is the row's only text, so the switch has to say what
            // it DOES rather than repeat the noun beside it.
            ariaLabel={t("restingFloaters.toggle")}
            onClick={() => {
              const next = !restingFloatersOn;
              setRestingFloatersEnabled(next);
              setRestingFloatersOn(next);
            }}
          />
        </div>

        {push.isSupported && (
          <>
            <CardDivider />
            <div className="flex items-center justify-between gap-4">
              <div className="flex min-w-0 items-center gap-3.5">
                <RowIcon icon={BellRing} />
                <div className="min-w-0">
                  <p className="text-[1.05rem] font-black text-foreground">{t("notifications.title")}</p>
                  {push.permission === "denied" && (
                    <p className="mt-0.5 text-sm font-extrabold text-muted-foreground">
                      {t("notifications.blocked")}
                    </p>
                  )}
                </div>
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
          </>
        )}
      </SheetCard>
      )}

      {/* Calendar feed, webhooks and dashboard API keys are all consumed by
          something outside the browser, so they need a server to serve them. */}
      {showCalendarFeedCard && (
      <SettingsSection
        title={t("calendarFeed.title")}
        titleAction={<GuideHelpLink topic="calendar-feed" />}
      >
        <div className="flex items-center justify-between gap-3">
          <div className="min-w-0 text-sm">
            <p className="font-black text-foreground">
              {calendarFeed?.enabled
                ? t("calendarFeed.active")
                : t("calendarFeed.inactive")}
            </p>
            {calendarFeed?.enabled && calendarFeed.tokenPreview ? (
              <p className="text-xs font-extrabold text-muted-foreground">
                {t("dashboard.activeKeyEnding", { preview: calendarFeed.tokenPreview })}
              </p>
            ) : null}
          </div>
          <Button
            type="button"
            variant={calendarFeed?.enabled ? "destructive" : "default"}
            disabled={feedLoading || calendarFeed === null}
            onClick={calendarFeed?.enabled ? handleRevokeFeed : handleGenerateFeed}
            className="h-11 shrink-0 rounded-2xl font-black"
          >
            {feedLoading ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                {calendarFeed?.enabled
                  ? t("calendarFeed.revoking")
                  : t("calendarFeed.generating")}
              </>
            ) : calendarFeed?.enabled ? (
              <>
                <Trash2 className="mr-2 h-4 w-4" />
                {t("calendarFeed.revoke")}
              </>
            ) : (
              <>
                <Calendar className="mr-2 h-4 w-4" />
                {t("calendarFeed.generate")}
              </>
            )}
          </Button>
        </div>

        {generatedFeedUrl && (
          <div className="space-y-2 rounded-2xl border border-border/60 bg-muted/40 p-3">
            <p className="text-xs font-extrabold text-muted-foreground">
              {t("calendarFeed.copyUrl")}
            </p>
            <div className="flex gap-2">
              <Input
                type={showFeedUrl ? "text" : "password"}
                value={generatedFeedUrl}
                readOnly
                className="h-10 flex-1 rounded-xl bg-background/50 font-mono text-xs"
              />
              <Button type="button" variant="outline" size="icon" className="h-10 w-10 shrink-0 rounded-xl" onClick={() => setShowFeedUrl(!showFeedUrl)}>
                {showFeedUrl ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </Button>
              <Button type="button" variant="outline" size="icon" className="h-10 w-10 shrink-0 rounded-xl" onClick={handleCopyFeedUrl}>
                <Copy className="h-4 w-4" />
              </Button>
            </div>
          </div>
        )}
      </SettingsSection>
      )}

      {showWebhooksCard && (
      <SettingsSection
        title={t("webhooks.title")}
        titleAction={<GuideHelpLink topic="webhooks" />}
      >
        <div className="space-y-3">
          <Input
            type="url"
            inputMode="url"
            value={newWebhookUrl}
            onChange={(event) => setNewWebhookUrl(event.target.value)}
            placeholder={t("webhooks.urlPlaceholder")}
            className="h-11 rounded-2xl font-mono text-xs"
          />
          <div className="space-y-1.5">
            <p className="text-xs font-extrabold text-muted-foreground">
              {t("webhooks.eventsLabel")}
            </p>
            <div className="flex flex-wrap gap-2">
              {WEBHOOK_EVENT_TYPES.map((event) => {
                const selected = newWebhookEvents.includes(event);
                return (
                  <button
                    key={event}
                    type="button"
                    onClick={() => toggleWebhookEvent(event)}
                    aria-pressed={selected}
                    className={cn(
                      "rounded-full px-3 py-1.5 text-xs font-black transition-colors",
                      selected
                        ? "bg-accent text-accent-foreground"
                        : "bg-muted/60 text-muted-foreground",
                    )}
                  >
                    {event}
                  </button>
                );
              })}
            </div>
          </div>
          <Button
            type="button"
            disabled={webhookLoading || newWebhookUrl.trim().length === 0}
            onClick={handleCreateWebhook}
            className="h-11 w-full rounded-2xl font-black"
          >
            {webhookLoading ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                {t("webhooks.creating")}
              </>
            ) : (
              <>
                <Webhook className="mr-2 h-4 w-4" />
                {t("webhooks.add")}
              </>
            )}
          </Button>
        </div>

        {generatedWebhookSecret && (
          <div className="space-y-2 rounded-2xl border border-border/60 bg-muted/40 p-3">
            <p className="text-xs font-extrabold text-muted-foreground">
              {t("webhooks.secretCopyNow")}
            </p>
            <div className="flex gap-2">
              <Input
                type={showWebhookSecret ? "text" : "password"}
                value={generatedWebhookSecret}
                readOnly
                className="h-10 flex-1 rounded-xl bg-background/50 font-mono text-xs"
              />
              <Button type="button" variant="outline" size="icon" className="h-10 w-10 shrink-0 rounded-xl" onClick={() => setShowWebhookSecret(!showWebhookSecret)}>
                {showWebhookSecret ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </Button>
              <Button type="button" variant="outline" size="icon" className="h-10 w-10 shrink-0 rounded-xl" onClick={handleCopyWebhookSecret}>
                <Copy className="h-4 w-4" />
              </Button>
            </div>
          </div>
        )}

        {webhooks !== null && webhooks.length > 0 && (
          <div className="space-y-2">
            {webhooks.map((webhook) => (
              <div
                key={webhook.id}
                className="flex items-center justify-between gap-3 rounded-2xl border border-border/60 bg-muted/30 p-3"
              >
                <div className="min-w-0 text-sm">
                  <p className="truncate font-mono text-xs font-black text-foreground">
                    {webhook.url}
                  </p>
                  <p className="text-xs font-extrabold text-muted-foreground">
                    {webhook.events.length > 0
                      ? webhook.events.join(", ")
                      : t("webhooks.allEvents")}
                    {webhook.enabled ? "" : ` · ${t("webhooks.disabled")}`}
                  </p>
                </div>
                <Button
                  type="button"
                  variant="destructive"
                  size="icon"
                  disabled={revokingWebhookId === webhook.id}
                  onClick={() => handleDeleteWebhook(webhook.id)}
                  aria-label={t("webhooks.delete")}
                  className="h-10 w-10 shrink-0 rounded-xl"
                >
                  {revokingWebhookId === webhook.id ? (
                    <Loader2 className="h-4 w-4 animate-spin" />
                  ) : (
                    <Trash2 className="h-4 w-4" />
                  )}
                </Button>
              </div>
            ))}
          </div>
        )}

        {webhooks !== null && webhooks.length === 0 && (
          <p className="text-xs font-extrabold text-muted-foreground">
            {t("webhooks.none")}
          </p>
        )}
      </SettingsSection>
      )}

      {/* Dashboard access — the keys that reach this account from outside the
          browser, so it sits with the other two integration cards. The heading
          is load-bearing: the backend sends people here BY NAME — McpRoutes,
          McpToolCatalog and McpToolDispatcher all say "Settings → Dashboard
          access", with backend tests asserting the exact string. */}
      {showDashboardCard && (
      <SettingsSection
        title={t("dashboard.title")}
        titleAction={<GuideHelpLink topic="api-key-homarr" />}
      >
        {/* Name + access level now live in the generate dialog, keeping this
            list uncluttered. */}
        <Button
          type="button"
          onClick={openGenerateKeyDialog}
          className="h-11 w-full rounded-2xl font-black"
        >
          <Key className="mr-2 h-4 w-4" />
          {t("dashboard.generateKey")}
        </Button>

        {/* Existing keys — tap a row to expand it in place with created/last-used
            details; the trash icon revokes directly without expanding anything. */}
        {apiKeys !== null && apiKeys.length > 0 && (
          <div className="space-y-2">
            {apiKeys.map((key) => {
              const expanded = expandedKeyId === key.id;
              return (
                <div
                  key={key.id}
                  className="rounded-2xl border border-border/60 bg-muted/30"
                >
                  <div className="flex items-center gap-2 p-3">
                    {/* data-no-press: the app-wide press ripple assumes a
                        roughly square target — on this wide, short row it
                        balloons into an ugly clipped band, so this row gets
                        its own contained background highlight instead. */}
                    <button
                      type="button"
                      onClick={() => {
                        hapticTick();
                        setExpandedKeyId(expanded ? null : key.id);
                      }}
                      aria-expanded={expanded}
                      data-no-press
                      className="-mx-1.5 flex min-w-0 flex-1 items-center gap-2 rounded-xl px-1.5 py-1 text-left transition-colors hover:bg-muted-foreground/5 active:bg-muted-foreground/10"
                    >
                      <div className="flex min-w-0 flex-1 items-center gap-3.5">
                        <RowIcon icon={KeyRound} />
                        <div className="min-w-0 flex-1 text-sm">
                          <p className="truncate font-black text-foreground">
                            {key.label?.trim() || t("dashboard.unnamedKey")}
                          </p>
                          <p className="truncate text-xs font-extrabold text-muted-foreground">
                            {t("dashboard.activeKeyEnding", { preview: key.keyPreview })}
                            {" · "}
                            {key.scope === "READ" ? t("dashboard.scopeRead") : t("dashboard.scopeFull")}
                            {key.expired ? ` · ${t("dashboard.expired")}` : ""}
                          </p>
                        </div>
                      </div>
                      <ChevronRight
                        className={cn(
                          "h-4 w-4 shrink-0 text-muted-foreground/40 transition-transform duration-200",
                          expanded && "rotate-90",
                        )}
                      />
                    </button>
                    <Button
                      type="button"
                      variant="destructive"
                      size="icon"
                      disabled={revokingKeyId === key.id}
                      onClick={() => handleRevokeApiKey(key.id)}
                      aria-label={t("dashboard.revokeKey")}
                      className="h-10 w-10 shrink-0 rounded-xl"
                    >
                      {revokingKeyId === key.id ? (
                        <Loader2 className="h-4 w-4 animate-spin" />
                      ) : (
                        <Trash2 className="h-4 w-4" />
                      )}
                    </Button>
                  </div>
                  <Collapse open={expanded}>
                    <div className="space-y-1.5 border-t border-border/40 px-3 pb-3 pt-2.5">
                      {key.createdAt && (
                        <p className="text-xs font-bold text-muted-foreground">
                          {t("dashboard.createdAt", { date: formatKeyTimestamp(key.createdAt) })}
                        </p>
                      )}
                      <p className="text-xs font-bold text-muted-foreground">
                        {key.lastUsedAt
                          ? t("dashboard.lastUsedAt", { date: formatKeyTimestamp(key.lastUsedAt) })
                          : t("dashboard.neverUsed")}
                      </p>
                    </div>
                  </Collapse>
                </div>
              );
            })}
          </div>
        )}

        {apiKeys !== null && apiKeys.length === 0 && (
          <p className="text-xs font-extrabold text-muted-foreground">
            {t("dashboard.noKey")}
          </p>
        )}
      </SettingsSection>
      )}

      {/* About — what this install is, mirroring the native About card: the
          build running in this browser, and in Server Mode the backend it talks
          to. Local Mode adds where the workspace lives, because there the
          browser IS the install. Sync belongs to the sync surfaces, not here. */}
      {showAboutCard && (
      <SettingsSection
        title={t("about.title")}
        titleAction={
          <GuideHelpLink topic={isLocalMode ? "local-mode" : "server-mode"} />
        }
      >
        {isLocalMode && (
          <>
            {/* Where this workspace lives — the web counterpart of the native
                Workspace row (Android SettingsWorkspaceContent). */}
            <div className="flex min-w-0 items-center gap-3.5">
              {/* Nothing to tap here, so the icon column stays empty — the label
                  still lines up with the rows below. */}
              <RowIconSlot />
              <div className="min-w-0 space-y-1">
                <p className="text-[1.05rem] font-black text-foreground">
                  {t("workspace.localTitle")}
                </p>
                <p className="text-sm font-extrabold text-muted-foreground">
                  {t("workspace.localDetail")}
                </p>
                {/* How this browser stores it — the one thing the user chose at
                    setup, and the only thing still changeable afterwards. */}
                <p className="text-sm font-extrabold text-muted-foreground">
                  {localProtection === "passphrase"
                    ? t("workspace.encryptedDetail")
                    : t("workspace.unencryptedDetail")}
                </p>
              </div>
            </div>
            {localProtection === "none" && (
              <>
                <CardDivider />
                <SettingsOptionRow
                  icon={KeyRound}
                  label={t("workspace.encrypt")}
                  onClick={() => setEncryptOpen(true)}
                />
              </>
            )}
            <CardDivider />
          </>
        )}

        <SettingsFactRow icon={Info} label={t("about.appVersion")} value={appVersionLabel} />

        {/* Server Mode only, and only once the probe has answered: a browser-only
            workspace has no server, and an empty or "unknown" server line is
            worse than none. */}
        {!isLocalMode && serverVersion !== null && (
          <>
            <CardDivider />
            {/* Carries a glyph like every other row in this card. Without one it
                fell back to the empty icon slot and read as a stray line under a
                version row that has one. */}
            <SettingsFactRow icon={Server} label={t("about.server")} value={`v${serverVersion}`} />
          </>
        )}
      </SettingsSection>
      )}

      {showDataCard && <DataTransferCard />}

      {/* Admin and the cache reset are both ways out of an install gone wrong,
          so they share a card. Admin is admin-only, which leaves everyone else
          the reset row alone — that one renders in every mode, so the card
          never comes up empty. */}
      {showMaintenanceCard && (
      <SettingsSection>
        {!isLocalMode && user?.role === "ADMIN" && (
          <>
            <SettingsOptionRow
              icon={UsersRound}
              label={sidebarDict("admin")}
              href="/app/admin"
              showChevron
            />
            <CardDivider />
          </>
        )}

        {/* Recovery for a client stuck on a half-updated build after a deploy —
            the in-app replacement for "delete the PWA and clear site data".
            Available in every mode: it only touches caches, never stored data. */}
        <SettingsOptionRow
          icon={RefreshCw}
          label={t("troubleshooting.reset")}
          onClick={() => setResetCacheOpen(true)}
        />
      </SettingsSection>
      )}

      {showGuideCard && (
      <SettingsSection>
        {/* How-To & feature guide — a searchable index of everything T'Day can do,
            reachable by everyone (works offline / in every mode). */}
        <SettingsOptionRow
          icon={CircleHelp}
          label={guideDict("title")}
          href="/app/guide"
          showChevron
        />
      </SettingsSection>
      )}

      {/* Last card on the screen, matching the native settings design. */}
      {showSignOutCard && (
      <SettingsSection>
        {isLocalMode ? (
          <>
            {/* Leaving keeps the tasks in this browser, so the label stays the
                neutral foreground colour it has always had; only the glyph
                takes the destructive tint the other two exit rows use. */}
            <SettingsOptionRow
              icon={LogOut}
              label={t("workspace.leave")}
              onClick={handleLogout}
              disabled={signingOut}
              destructiveIcon
            />
            <CardDivider />
            <SettingsOptionRow
              icon={Trash2}
              label={t("workspace.delete")}
              onClick={() => setDeleteLocalOpen(true)}
              disabled={signingOut}
              destructive
            />
          </>
        ) : (
          <SettingsOptionRow
            icon={LogOut}
            label={t("signOut")}
            onClick={handleLogout}
            disabled={signingOut}
            destructive
          />
        )}
      </SettingsSection>
      )}

      {/* No matching settings — this screen is never genuinely empty, so its
          empty state is the search coming back with nothing. */}
      {noSettingsMatch && (
        <EmptyState
          icon={Search}
          accentColor={nativeScreenAccentColors.settings}
          title={appDict("noMatchingSettings")}
          description={appDict("searchEmptyBody")}
          action={
            <button
              type="button"
              onClick={() => setSearchQuery("")}
              className="rounded-full border border-border/60 bg-card px-5 py-2.5 text-sm font-black text-foreground shadow-[0_14px_30px_-16px_hsl(var(--shadow)/0.6)] transition-transform hover:-translate-y-0.5"
            >
              {appDict("clearSearch")}
            </button>
          }
        />
      )}

      {/* Non-destructive recovery, so the confirm action keeps the picker's
          accent Done styling rather than the destructive tint below. */}
      <CenteredSelectorOverlay
        open={resetCacheOpen}
        onOpenChange={(open) => !resettingCache && setResetCacheOpen(open)}
        title={t("troubleshooting.confirmTitle")}
      >
        <p className="px-5 pb-1 text-sm font-bold leading-relaxed text-muted-foreground">
          {t("troubleshooting.confirmBody")}
        </p>
        <div className="flex flex-col gap-2 px-4 pb-1 pt-3">
          <button
            type="button"
            disabled={resettingCache}
            onClick={() => void handleResetAppData()}
            className="flex w-full items-center justify-center gap-2 rounded-2xl bg-muted/70 py-3 text-base font-black text-accent transition-colors hover:bg-muted disabled:opacity-50"
          >
            {resettingCache ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
            {t("troubleshooting.confirmAction")}
          </button>
          <button
            type="button"
            disabled={resettingCache}
            onClick={() => setResetCacheOpen(false)}
            className="w-full rounded-2xl bg-muted/70 py-3 text-base font-black text-foreground transition-colors hover:bg-muted disabled:opacity-50"
          >
            {t("troubleshooting.confirmCancel")}
          </button>
        </div>
      </CenteredSelectorOverlay>

      {/* Same chrome as the task form's Repeat / Priority pickers — the app's
          centered selector card — so a confirm reads like the rest of the UI.
          Actions stack like the picker's Done button (destructive one first). */}
      {/* Turning on encryption after the fact. Same chrome as the delete confirm
          below, because it is the other one-way door on this screen. */}
      <CenteredSelectorOverlay
        open={encryptOpen}
        onOpenChange={(open) => (open ? setEncryptOpen(true) : closeEncryptDialog())}
        title={t("workspace.encryptTitle")}
      >
        <p className="px-5 pb-1 text-sm font-bold leading-relaxed text-muted-foreground">
          {t("workspace.encryptBody")}
        </p>
        <div className="flex flex-col gap-2 px-4 pb-1 pt-3">
          <Input
            type="password"
            value={encryptPassphrase}
            onChange={(event) => {
              setEncryptPassphrase(event.target.value);
              setEncryptError("");
            }}
            placeholder={t("workspace.encryptPassphrase")}
            aria-label={t("workspace.encryptPassphrase")}
            // Never offered to a password manager: this passphrase is the key.
            autoComplete="off"
          />
          <Input
            type="password"
            value={encryptConfirmation}
            onChange={(event) => {
              setEncryptConfirmation(event.target.value);
              setEncryptError("");
            }}
            placeholder={t("workspace.encryptRepeat")}
            aria-label={t("workspace.encryptRepeat")}
            autoComplete="off"
          />
          <label className="flex cursor-pointer items-start gap-2.5 py-1">
            <input
              type="checkbox"
              checked={encryptAcknowledged}
              onChange={(event) => setEncryptAcknowledged(event.target.checked)}
              className="mt-[3px] h-4 w-4 shrink-0 accent-[hsl(var(--primary))]"
            />
            <span className="text-sm font-bold leading-snug text-muted-foreground">
              {t("workspace.encryptAcknowledge")}
            </span>
          </label>
          {encryptError && (
            <p className="text-sm font-bold text-destructive">{encryptError}</p>
          )}
          <button
            type="button"
            onClick={() => void handleEncryptWorkspace()}
            disabled={
              encryptBusy ||
              !encryptAcknowledged ||
              encryptPassphrase.length < MIN_PASSPHRASE_LENGTH ||
              encryptConfirmation.length === 0
            }
            className="w-full rounded-2xl bg-primary/10 py-3 text-base font-black text-primary transition-colors hover:bg-primary/20 disabled:opacity-50"
          >
            {t("workspace.encryptAction")}
          </button>
          <button
            type="button"
            onClick={closeEncryptDialog}
            className="w-full rounded-2xl bg-muted/70 py-3 text-base font-black text-foreground transition-colors hover:bg-muted"
          >
            {t("workspace.encryptCancel")}
          </button>
        </div>
      </CenteredSelectorOverlay>

      {/* API key creation: name + access level, then (on success) the
          one-time reveal — all in one dialog so the settings list stays clean. */}
      <CenteredSelectorOverlay
        open={generateKeyDialogOpen}
        onOpenChange={(open) => (open ? setGenerateKeyDialogOpen(true) : closeGenerateKeyDialog())}
        title={t("dashboard.newKeyTitle")}
      >
        {generatedApiKey ? (
          <div className="space-y-3 px-4 pb-1 pt-1">
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
            <button
              type="button"
              onClick={closeGenerateKeyDialog}
              className="w-full rounded-2xl bg-muted/70 py-3 text-base font-black text-accent transition-colors hover:bg-muted"
            >
              {t("dashboard.done")}
            </button>
          </div>
        ) : (
          <div className="space-y-3 px-4 pb-1 pt-1">
            <Input
              type="text"
              value={newKeyLabel}
              onChange={(event) => setNewKeyLabel(event.target.value)}
              maxLength={60}
              placeholder={t("dashboard.labelPlaceholder")}
              className="h-11 rounded-2xl font-extrabold"
              autoFocus
            />
            <div className="flex rounded-2xl bg-muted/60 p-1.5">
              {(["READ", "FULL"] as ApiKeyScope[]).map((scope) => (
                <button
                  key={scope}
                  type="button"
                  onClick={() => setNewKeyScope(scope)}
                  aria-pressed={newKeyScope === scope}
                  className={cn(
                    "flex flex-1 items-center justify-center rounded-[13px] py-2 text-[0.9rem] font-black transition-colors",
                    newKeyScope === scope
                      ? "bg-card text-accent shadow-sm"
                      : "text-muted-foreground",
                  )}
                >
                  {scope === "READ" ? t("dashboard.scopeRead") : t("dashboard.scopeFull")}
                </button>
              ))}
            </div>
            <p className="text-xs font-extrabold text-muted-foreground">
              {newKeyScope === "READ"
                ? t("dashboard.scopeReadHint")
                : t("dashboard.scopeFullHint")}
            </p>
            <Button
              type="button"
              disabled={apiKeyLoading}
              onClick={handleGenerateApiKey}
              className="h-11 w-full rounded-2xl font-black"
            >
              {apiKeyLoading ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  {t("dashboard.generating")}
                </>
              ) : (
                <>
                  <Key className="mr-2 h-4 w-4" />
                  {t("dashboard.generateKey")}
                </>
              )}
            </Button>
            <button
              type="button"
              onClick={closeGenerateKeyDialog}
              disabled={apiKeyLoading}
              className="w-full rounded-2xl bg-muted/70 py-3 text-base font-black text-foreground transition-colors hover:bg-muted disabled:opacity-50"
            >
              {t("dashboard.cancel")}
            </button>
          </div>
        )}
      </CenteredSelectorOverlay>

      <CenteredSelectorOverlay
        open={deleteLocalOpen}
        onOpenChange={setDeleteLocalOpen}
        title={t("workspace.deleteConfirmTitle")}
      >
        <p className="px-5 pb-1 text-sm font-bold leading-relaxed text-muted-foreground">
          {t("workspace.deleteConfirmBody")}
        </p>
        <div className="flex flex-col gap-2 px-4 pb-1 pt-3">
          <button
            type="button"
            onClick={() => void handleDeleteLocalData()}
            className="w-full rounded-2xl bg-destructive/10 py-3 text-base font-black text-destructive transition-colors hover:bg-destructive/20"
          >
            {t("workspace.deleteConfirmAction")}
          </button>
          <button
            type="button"
            onClick={() => setDeleteLocalOpen(false)}
            className="w-full rounded-2xl bg-muted/70 py-3 text-base font-black text-foreground transition-colors hover:bg-muted"
          >
            {t("workspace.deleteConfirmCancel")}
          </button>
        </div>
      </CenteredSelectorOverlay>
    </div>
  );
}
