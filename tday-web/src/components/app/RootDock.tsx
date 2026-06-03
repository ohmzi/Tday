import { Calendar1, Home, MoreHorizontal } from "lucide-react";
import { usePathname, useRouter } from "@/lib/navigation";
import {
  nativeAppContentClassName,
  nativeAppHorizontalPaddingClassName,
} from "@/components/app/nativeAppLayout";
import { cn } from "@/lib/utils";

type DockTab = "home" | "calendar" | "more";

const dockTabs: Array<{
  id: DockTab;
  label: string;
  icon: typeof Home;
  path?: string;
}> = [
  { id: "home", label: "Home", icon: Home, path: "/app/tday" },
  { id: "calendar", label: "Calendar", icon: Calendar1, path: "/app/calendar" },
  { id: "more", label: "More", icon: MoreHorizontal },
];

function activeDockTab(pathname: string): DockTab {
  if (pathname.includes("/app/calendar")) {
    return "calendar";
  }
  if (pathname.includes("/app/tday") || pathname.includes("/app/today")) {
    return "home";
  }
  return "more";
}

export default function RootDock({
  onOpenMore,
  moreOpen,
}: {
  onOpenMore: () => void;
  moreOpen: boolean;
}) {
  const router = useRouter();
  const pathname = usePathname();
  const activeTab = moreOpen ? "more" : activeDockTab(pathname);

  return (
    <div
      className={cn(
        "pointer-events-none fixed inset-x-0 bottom-[calc(18px+env(safe-area-inset-bottom))] z-40",
        nativeAppHorizontalPaddingClassName,
      )}
    >
      <div
        className={cn(
          nativeAppContentClassName,
          // Left-aligned on mobile (matching native), centered on desktop.
          "flex items-center justify-start sm:justify-center",
        )}
      >
        <nav
          aria-label="Primary app navigation"
          className={cn(
            "pointer-events-auto h-16 rounded-[25px] border border-white/70 bg-muted/80 p-1.5",
            "shadow-[0_18px_42px_-24px_hsl(var(--shadow)/0.65)] backdrop-blur-xl",
            "dark:border-white/10 dark:bg-muted/80",
          )}
        >
          <div className="flex h-full items-center gap-1">
            {dockTabs.map((tab) => {
              const Icon = tab.icon;
              const selected = tab.id === activeTab;
              // The "More" button is hidden on mobile and only shown on desktop.
              const isMore = tab.id === "more";

              return (
                <button
                  key={tab.id}
                  type="button"
                  aria-label={tab.label}
                  onClick={() => {
                    if (isMore) {
                      onOpenMore();
                      return;
                    }
                    router.push(tab.path!);
                  }}
                  aria-current={selected ? "page" : undefined}
                  className={cn(
                    isMore ? "hidden sm:flex" : "flex",
                    "h-12 min-w-12 items-center justify-center gap-2 rounded-[20px] px-3",
                    "text-sm font-black transition-all duration-200",
                    selected
                      ? "bg-card text-foreground shadow-[0_10px_24px_-18px_hsl(var(--shadow)/0.7)]"
                      : "text-muted-foreground hover:bg-card/55 hover:text-foreground",
                    selected && !isMore ? "sm:min-w-[104px]" : "sm:min-w-12",
                  )}
                >
                  <Icon className="h-5 w-5 stroke-[2.4]" />
                  <span className={cn("hidden", selected && !isMore && "sm:inline")}>
                    {tab.label}
                  </span>
                </button>
              );
            })}
          </div>
        </nav>
      </div>
    </div>
  );
}
