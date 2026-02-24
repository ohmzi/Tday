import React from "react";
import { useSession } from "next-auth/react";
import Image from "next/image";
import UserCardLoading from "./UserCardLoading";
import { cn } from "@/lib/utils";
import { useRouter } from "@/i18n/navigation";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  DropdownMenuSeparator,
} from "@/components/ui/dropdown-menu";
import { useTranslations } from "next-intl";
import { useTheme } from "next-themes";
import { LogOut, Moon, Sun, Monitor, Settings, Shield } from "lucide-react";
import { signOut } from "next-auth/react";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { useQueryClient } from "@tanstack/react-query";
import { clearClientUserData } from "@/lib/security/clearClientUserData";

const railButtonClass =
  "group flex h-10 min-h-10 w-10 shrink-0 items-center justify-center rounded-xl text-sidebar-foreground/70 transition-colors duration-200 hover:bg-sidebar-accent/70 hover:text-sidebar-foreground";

const expandedButtonClass =
  "group flex h-10 min-h-10 w-full min-w-0 shrink-0 items-center gap-0 overflow-hidden rounded-xl border border-transparent pl-0 pr-3 text-sm font-medium text-sidebar-foreground/70 transition-colors duration-200 hover:bg-sidebar-accent/70 hover:text-sidebar-foreground";

const railIconSlot = "flex h-10 w-10 shrink-0 items-center justify-center";
const railIconClass =
  "h-4 w-4 transition-colors duration-200";

const UserCard = ({
  className,
  collapsed = false,
  onNavigate,
}: {
  className?: string;
  collapsed?: boolean;
  onNavigate?: () => void;
}) => {
  const { data, status } = useSession();
  const sidebarDict = useTranslations("sidebar");
  const { setTheme, theme } = useTheme();
  const router = useRouter();
  const queryClient = useQueryClient();

  const themeLabel = theme === "dark" ? "Dark" : theme === "light" ? "Light" : "System";

  if (status === "loading") return <UserCardLoading collapsed={collapsed} />;
  if (!data) return null;

  const { user } = data;
  const initials =
    (user?.name || user?.email?.split("@")[0] || "U")
      .split(" ")
      .map((n) => n[0])
      .join("")
      .toUpperCase()
      .slice(0, 2) || "U";

  const handleLogout = async () => {
    queryClient.clear();
    await clearClientUserData();

    await signOut({ redirect: false }).catch(() => {
      // Ignore sign-out transport failures; we'll still route user away locally.
    });

    if (typeof window !== "undefined") {
      window.location.replace(window.location.origin);
      return;
    }

    router.replace("/");
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
                if (theme === "light") {
                  setTheme("dark");
                } else if (theme === "dark") {
                  setTheme("system");
                } else {
                  setTheme("light");
                }
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
            if (theme === "light") {
              setTheme("dark");
            } else if (theme === "dark") {
              setTheme("system");
            } else {
              setTheme("light");
            }
          }}
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
                    {user?.image ? (
                      <Image
                        src={user.image}
                        alt={user?.name || "User profile picture"}
                        width={16}
                        height={16}
                        sizes="16px"
                        className="h-4 w-4 shrink-0 rounded-full border border-border/60 object-cover"
                        loading="lazy"
                      />
                    ) : (
                      <span className="flex h-4 w-4 shrink-0 items-center justify-center rounded-full border border-border/60 bg-card/80 text-[9px] font-semibold leading-none text-sidebar-foreground">
                        {initials}
                      </span>
                    )}
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
                  className={cn(
                    "group flex h-10 min-h-10 w-full min-w-0 shrink-0 items-center gap-0 overflow-hidden rounded-xl border border-transparent pl-0 pr-3 text-sidebar-foreground/70 transition-colors duration-200 hover:bg-sidebar-accent/70 hover:text-sidebar-foreground",
                    className,
                  )}
                >
                  <span className={railIconSlot}>
                    {user?.image ? (
                      <Image
                        src={user.image}
                        alt={user?.name || "User profile picture"}
                        width={16}
                        height={16}
                        sizes="16px"
                        className="h-4 w-4 shrink-0 rounded-full border border-border/60 object-cover"
                        loading="lazy"
                      />
                    ) : (
                      <span className="flex h-4 w-4 shrink-0 items-center justify-center rounded-full border border-border/60 bg-card/80 text-[9px] font-semibold leading-none text-sidebar-foreground">
                        {initials}
                      </span>
                    )}
                  </span>

                  <span className="min-w-0 flex-1 truncate text-left text-sm font-medium text-sidebar-foreground">
                    {user?.name || user?.email?.split("@")[0] || "User"}
                  </span>
                </button>
          </DropdownMenuTrigger>
        )}

        <DropdownMenuContent
          align="end"
          side={collapsed ? "right" : "top"}
          className="w-56 text-foreground"
        >
          {user?.role === "ADMIN" ? (
            <DropdownMenuItem
              onClick={() => {
                onNavigate?.();
                router.push("/app/admin");
              }}
              className="focus:bg-sidebar-accent/50 focus:text-sidebar-foreground"
            >
              <Shield className="h-4 w-4" />
              <span>{sidebarDict("admin")}</span>
            </DropdownMenuItem>
          ) : null}
          <DropdownMenuItem
            onClick={() => {
              onNavigate?.();
              router.push("/app/settings");
            }}
            className="focus:bg-sidebar-accent/50 focus:text-sidebar-foreground"
          >
            <Settings className="h-4 w-4" />
            <span>{sidebarDict("settings")}</span>
          </DropdownMenuItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem
            onClick={() => {
              void handleLogout();
            }}
            className="text-destructive focus:bg-destructive/10 focus:text-destructive"
          >
            <LogOut className="h-4 w-4" />
            <span>{sidebarDict("settingMenu.logout")}</span>
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  );
};

export default UserCard;
