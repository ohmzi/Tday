import { Home, Leaf, MoreHorizontal } from "lucide-react";
import { useTranslation } from "react-i18next";
import { usePathname, useRouter } from "@/lib/navigation";
import { hapticTick } from "@/lib/haptics";
import { useRef, useEffect, useState, useCallback } from "react";
import {
  nativeAppContentClassName,
  nativeAppHorizontalPaddingClassName,
} from "@/components/app/nativeAppLayout";
import { nativeScreenAccentColors } from "@/components/app/nativeScreenTheme";
import { cn } from "@/lib/utils";

type DockTab = "home" | "floater" | "more";

const dockTabs: Array<{
  id: DockTab;
  labelKey: "home" | "root_feed_tab_floater" | "more";
  icon: typeof Home;
  path?: string;
  accentColor?: string;
}> = [
  { id: "home", labelKey: "home", icon: Home, path: "/app/tday", accentColor: nativeScreenAccentColors.today },
  {
    id: "floater",
    labelKey: "root_feed_tab_floater",
    icon: Leaf,
    path: "/app/floater",
    accentColor: nativeScreenAccentColors.floater,
  },
  { id: "more", labelKey: "more", icon: MoreHorizontal },
];

function activeDockTab(pathname: string): DockTab {
  if (pathname.includes("/app/floater")) {
    return "floater";
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
  const { t: appDict } = useTranslation("app");
  const activeTab = moreOpen ? "more" : activeDockTab(pathname);

  // Sliding indicator pill
  const navRef = useRef<HTMLDivElement>(null);
  const buttonRefs = useRef<Map<DockTab, HTMLButtonElement>>(new Map());
  const [pillStyle, setPillStyle] = useState<React.CSSProperties>({});

  const updatePill = useCallback(() => {
    const btn = buttonRefs.current.get(activeTab);
    const nav = navRef.current;
    if (!btn || !nav) return;
    const navRect = nav.getBoundingClientRect();
    const btnRect = btn.getBoundingClientRect();
    setPillStyle({
      transform: `translateX(${btnRect.left - navRect.left - 6}px)`,
      width: `${btnRect.width}px`,
      height: `${btnRect.height}px`,
      opacity: 1,
    });
  }, [activeTab]);

  useEffect(() => {
    // Measure now, then again after the tab width transition (200ms) settles —
    // when the active tab collapses/expands the immediate rects are mid-anim,
    // which would otherwise leave the indicator offset.
    updatePill();
    const raf = requestAnimationFrame(updatePill);
    const timer = window.setTimeout(updatePill, 260);
    return () => {
      cancelAnimationFrame(raf);
      window.clearTimeout(timer);
    };
  }, [updatePill]);

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
          ref={navRef}
          aria-label="Primary app navigation"
          className={cn(
            // overflow-hidden clips the sliding indicator so it can never poke
            // out past the dock's right edge from a transient/stale measurement
            // (e.g. when the More sheet opens and the active tab collapses).
            "pointer-events-auto relative h-16 overflow-hidden rounded-[25px] border border-white/70 bg-muted/80 p-1.5",
            "shadow-[0_18px_42px_-24px_hsl(var(--shadow)/0.65)] backdrop-blur-xl",
            "dark:border-white/10 dark:bg-muted/80",
          )}
        >
          {/* Sliding indicator pill */}
          <div
            className="pointer-events-none absolute left-1.5 top-1.5 rounded-[20px] bg-card shadow-[0_10px_24px_-18px_hsl(var(--shadow)/0.7)] transition-all duration-300 ease-[cubic-bezier(0.25,1,0.5,1)]"
            style={pillStyle}
          />
          <div className="relative flex h-full items-center gap-1">
            {dockTabs.map((tab) => {
              const Icon = tab.icon;
              const selected = tab.id === activeTab;
              const label = appDict(tab.labelKey);
              // The "More" button is hidden on mobile and only shown on desktop.
              const isMore = tab.id === "more";

              return (
                <button
                  key={tab.id}
                  ref={(el) => {
                    if (el) buttonRefs.current.set(tab.id, el);
                  }}
                  type="button"
                  aria-label={label}
                  onClick={() => {
                    hapticTick();
                    if (isMore) {
                      onOpenMore();
                      return;
                    }
                    router.push(tab.path!);
                  }}
                  aria-current={selected ? "page" : undefined}
                  className={cn(
                    isMore ? "hidden sm:flex" : "flex",
                    "relative z-[1] h-12 min-w-12 items-center justify-center gap-2 rounded-[20px] px-3",
                    "text-sm font-black transition-all duration-200",
                    selected
                      ? ""
                      : "text-muted-foreground hover:bg-card/55 hover:text-foreground",
                    selected && !isMore ? "sm:min-w-[104px]" : "sm:min-w-12",
                  )}
                  style={selected && tab.accentColor ? { color: tab.accentColor } : undefined}
                >
                  <Icon className="h-6 w-6 stroke-[2.6]" />
                  <span className={cn("hidden", selected && !isMore && "sm:inline")}>
                    {label}
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
