import React from "react";
import { useAuth } from "@/providers/AuthProvider";
import UserCardLoading from "./UserCardLoading";
import { cn } from "@/lib/utils";
import { useRouter } from "@/lib/navigation";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  DropdownMenuSeparator,
} from "@/components/ui/dropdown-menu";
import { useTranslation } from "react-i18next";
import { useTheme } from "next-themes";
import { LogOut, Moon, Sun, Monitor, Settings, Shield } from "lucide-react";
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

  const themeLabel = theme === "dark" ? "Dark" : theme === "light" ? "Light" : "System";

  if (isLoading) return <UserCardLoading collapsed={collapsed} />;
  if (!user) return null;

  const initials =
    (user.name || user.username || "U")
      .split(" ")
      .map((n) => n[0])
      .join("")
      .toUpperCase()
      .slice(0, 2) || "U";

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

  return (
    <div className="flex flex-col gap-2">
      {collapsed ? (
        <Tooltip>
          <TooltipTrigger asChild>
            <button
              type="button"
              className={railButtonClass}
              onClick={() => {
                if (theme === "light") setTheme("dark");
                else if (theme === "dark") setTheme("system");
                else setTheme("light");
              }}
              aria-label={`${themeLabel} theme`}
            >
              <span className={railIconSlot}>
                {theme === "light" ? (
                  <Sun className={railIconClass} />
                ) : theme === "dark" ? (
                  <Moon className={railIconClass} />
                ) : (
                  <Monitor className={railIconClass} />
                )}
              </span>
            </button>
          </TooltipTrigger>
          <TooltipContent side="right" sideOffset={10}>
            {themeLabel}
          </TooltipContent>
        </Tooltip>
      ) : (
        <button
          type="button"
          className={expandedButtonClass}
          onClick={() => {
            if (theme === "light") setTheme("dark");
            else if (theme === "dark") setTheme("system");
            else setTheme("light");
          }}
        >
          <span className={expandedIconChip}>
            {theme === "light" ? (
              <Sun className={expandedIconClass} />
            ) : theme === "dark" ? (
              <Moon className={expandedIconClass} />
            ) : (
              <Monitor className={expandedIconClass} />
            )}
          </span>
          <span className="min-w-0 flex-1 truncate whitespace-nowrap text-left">
            {themeLabel}
          </span>
        </button>
      )}

      <DropdownMenu>
        {collapsed ? (
          <Tooltip>
            <TooltipTrigger asChild>
              <DropdownMenuTrigger asChild>
                <button
                  type="button"
                  className={cn(railButtonClass, className)}
                  aria-label="Profile"
                >
                  <span className={railIconSlot}>
                    <span className="flex h-4 w-4 shrink-0 items-center justify-center rounded-full border border-border/60 bg-card/80 text-[9px] font-semibold leading-none text-sidebar-foreground">
                      {initials}
                    </span>
                  </span>
                </button>
              </DropdownMenuTrigger>
            </TooltipTrigger>
            <TooltipContent side="right" sideOffset={10}>
              Profile
            </TooltipContent>
          </Tooltip>
        ) : (
          <DropdownMenuTrigger asChild>
            <button
              type="button"
              className={cn(expandedButtonClass, className)}
            >
              <span className={expandedIconChip}>
                <span className="text-xs font-black leading-none text-foreground">
                  {initials}
                </span>
              </span>
              <span className="min-w-0 flex-1 truncate text-left">
                {user.name || user.username || "User"}
              </span>
            </button>
          </DropdownMenuTrigger>
        )}

        <DropdownMenuContent
          align="end"
          side={collapsed ? "right" : "top"}
          className="w-56 rounded-2xl text-foreground"
        >
          {user.role === "ADMIN" ? (
            <DropdownMenuItem
              onClick={() => {
                onNavigate?.();
                router.push("/app/admin");
              }}
              className="gap-2.5 rounded-xl py-2 text-sm font-black focus:bg-muted focus:text-foreground"
            >
              <Shield className="h-5 w-5" strokeWidth={2.4} />
              <span>{sidebarDict("admin")}</span>
            </DropdownMenuItem>
          ) : null}
          <DropdownMenuItem
            onClick={() => {
              onNavigate?.();
              router.push("/app/settings");
            }}
            className="gap-2.5 rounded-xl py-2 text-sm font-black focus:bg-muted focus:text-foreground"
          >
            <Settings className="h-5 w-5" strokeWidth={2.4} />
            <span>{sidebarDict("settings")}</span>
          </DropdownMenuItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem
            onClick={() => void handleLogout()}
            className="gap-2.5 rounded-xl py-2 text-sm font-black text-destructive focus:bg-destructive/10 focus:text-destructive"
          >
            <LogOut className="h-5 w-5" strokeWidth={2.4} />
            <span>{sidebarDict("settingMenu.logout")}</span>
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  );
};

export default UserCard;
