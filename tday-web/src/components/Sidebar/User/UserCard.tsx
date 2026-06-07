import React from "react";
import { useAuth } from "@/providers/AuthProvider";
import UserCardLoading from "./UserCardLoading";
import { cn } from "@/lib/utils";
import { useRouter } from "@/lib/navigation";
import { useTranslation } from "react-i18next";
import { useTheme } from "next-themes";
import {
  LogOut,
  Moon,
  Sun,
  Monitor,
  Settings,
  Shield,
  type LucideIcon,
} from "lucide-react";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { useToast } from "@/hooks/use-toast";

const railButtonClass =
  "group flex h-10 min-h-10 w-10 shrink-0 items-center justify-center rounded-xl text-sidebar-foreground/70 transition-colors duration-200 hover:bg-sidebar-accent/70 hover:text-sidebar-foreground";

const expandedButtonClass =
  "group flex h-12 min-h-12 w-full min-w-0 shrink-0 items-center gap-3 overflow-hidden rounded-2xl px-2 text-base font-black text-muted-foreground transition-colors duration-200 hover:bg-card/70 hover:text-foreground";

const railIconSlot = "flex h-10 w-10 shrink-0 items-center justify-center";
const expandedIconChip =
  "flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-muted/70";
const railIconClass = "h-4 w-4 transition-colors duration-200";
const expandedIconClass = "h-5 w-5";

const UserCard = ({
  className,
  collapsed = false,
  onNavigate,
}: {
  className?: string;
  collapsed?: boolean;
  onNavigate?: () => void;
}) => {
  const { user, isLoading, logout } = useAuth();
  const { t: sidebarDict } = useTranslation("sidebar");
  const { setTheme, theme } = useTheme();
  const router = useRouter();
  const { toast } = useToast();

  const themeLabel =
    theme === "dark" ? "Dark Theme" : theme === "light" ? "Light Theme" : "System Theme";
  const ThemeIcon: LucideIcon =
    theme === "light" ? Sun : theme === "dark" ? Moon : Monitor;

  if (isLoading) return <UserCardLoading collapsed={collapsed} />;
  if (!user) return null;

  const initials =
    (user.name || user.username || "U")
      .split(" ")
      .map((n) => n[0])
      .join("")
      .toUpperCase()
      .slice(0, 2) || "U";

  const cycleTheme = () => {
    if (theme === "light") setTheme("dark");
    else if (theme === "dark") setTheme("system");
    else setTheme("light");
  };

  const handleLogout = async () => {
    try {
      await logout();
    } catch (error) {
      toast({
        variant: "destructive",
        description:
          error instanceof Error && error.message
            ? error.message
            : "Unable to log out. Please try again.",
      });
    }
  };

  // Renders an inline sidebar action button that adapts to the collapsed rail.
  const renderAction = (
    key: string,
    Icon: LucideIcon,
    label: string,
    onClick: () => void,
    options?: { destructive?: boolean },
  ) => {
    const destructive = options?.destructive ?? false;
    if (collapsed) {
      return (
        <Tooltip key={key}>
          <TooltipTrigger asChild>
            <button
              type="button"
              onClick={onClick}
              aria-label={label}
              className={cn(
                railButtonClass,
                destructive &&
                  "text-destructive hover:bg-destructive/10 hover:text-destructive",
              )}
            >
              <span className={railIconSlot}>
                <Icon className={railIconClass} strokeWidth={2.2} />
              </span>
            </button>
          </TooltipTrigger>
          <TooltipContent side="right" sideOffset={10}>
            {label}
          </TooltipContent>
        </Tooltip>
      );
    }
    return (
      <button
        key={key}
        type="button"
        onClick={onClick}
        className={cn(
          expandedButtonClass,
          destructive && "text-destructive hover:text-destructive",
        )}
      >
        <span className={expandedIconChip}>
          <Icon className={expandedIconClass} strokeWidth={2.2} />
        </span>
        <span className="min-w-0 flex-1 truncate whitespace-nowrap text-left">
          {label}
        </span>
      </button>
    );
  };

  return (
    <div className={cn("flex flex-col gap-2", className)}>
      {renderAction("theme", ThemeIcon, themeLabel, cycleTheme)}

      {/* Inert user button — shows who's signed in; opens nothing for now. */}
      {collapsed ? (
        <div className={railButtonClass} aria-label={user.name || user.username || "User"}>
          <span className={railIconSlot}>
            <span className="flex h-4 w-4 shrink-0 items-center justify-center rounded-full border border-border/60 bg-card/80 text-[9px] font-semibold leading-none text-sidebar-foreground">
              {initials}
            </span>
          </span>
        </div>
      ) : (
        <div className="flex h-12 min-h-12 w-full min-w-0 shrink-0 items-center gap-3 overflow-hidden rounded-2xl px-2 text-base font-black text-foreground">
          <span className={expandedIconChip}>
            <span className="text-xs font-black leading-none text-foreground">{initials}</span>
          </span>
          <span className="min-w-0 flex-1 truncate text-left">
            {user.name || user.username || "User"}
          </span>
        </div>
      )}

      {renderAction("settings", Settings, sidebarDict("settings"), () => {
        onNavigate?.();
        router.push("/app/settings");
      })}

      {user.role === "ADMIN"
        ? renderAction("admin", Shield, sidebarDict("admin"), () => {
            onNavigate?.();
            router.push("/app/admin");
          })
        : null}

      {renderAction(
        "logout",
        LogOut,
        sidebarDict("settingMenu.logout"),
        () => void handleLogout(),
        { destructive: true },
      )}
    </div>
  );
};

export default UserCard;
