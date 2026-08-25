import React, { useCallback, useEffect, useRef, useState } from "react";
import { Search, X, Command } from "lucide-react";
import { Button } from "@/components/ui/button";
import NativeAppBrandButton from "@/components/app/NativeAppBrandButton";
import {
  NativePageBackButton,
  nativePageBarClassName,
  nativePageBarDockedTitleClassName,
  nativePageBarTitleLayerClassName,
  nativePageHeaderMetrics,
  useNativePageBarResync,
} from "@/components/app/NativePageHeader";
import type { NativePageBarSlots } from "@/components/app/NativePageHeader";
import { cn } from "@/lib/utils";
import { hapticButtonTap, hapticDismiss } from "@/lib/haptics";

export interface SearchResultItem {
  id: string;
  title: string;
  subtitle?: string;
}

interface MobileSearchHeaderProps {
  searchQuery?: string;
  onSearchChange?: (query: string) => void;
  placeholder?: string;
  trailingAction?: React.ReactNode;
  /** When provided, a results dropdown is shown under the input while searching. */
  results?: SearchResultItem[];
  onSelectResult?: (id: string) => void;
  /**
   * Lets this bar double as the dock for a collapsing page header: the page's
   * title travels up into here as the block below it scrolls away, and this bar
   * drops its own brand to make room. NativePageHeader owns the scroll maths
   * and writes to these nodes; this component only places them.
   */
  pageCollapse?: NativePageBarSlots & { title: string; accentColor: string };
}

const collapsedButtonClassName = cn(
  "flex h-14 w-14 shrink-0 items-center justify-center rounded-full",
  "border border-white/70 bg-card/90 text-foreground shadow-[0_12px_28px_-22px_hsl(var(--shadow)/0.55)]",
  "transition-all duration-200 hover:-translate-y-0.5 hover:bg-card dark:border-white/10",
);

export default function MobileSearchHeader({
  searchQuery: externalQuery,
  onSearchChange,
  placeholder = "Search tasks...",
  trailingAction,
  results,
  onSelectResult,
  pageCollapse,
}: MobileSearchHeaderProps) {
  const [internalQuery, setInternalQuery] = useState("");
  const [isExpanded, setIsExpanded] = useState(false);
  const ownHeaderRef = useRef<HTMLElement>(null);
  // The collapsing header measures its progress against this bar's bottom edge,
  // so when it is driving us it has to hold the same node.
  const headerRef = pageCollapse?.barRef ?? ownHeaderRef;
  const [isSearchFocused, setIsSearchFocused] = useState(false);
  const searchInputRef = useRef<HTMLInputElement>(null);
  const collapseTimerRef = useRef<number | null>(null);
  const isMac =
    typeof window !== "undefined" &&
    navigator.userAgent.toLowerCase().includes("mac");

  const searchQuery = externalQuery ?? internalQuery;
  const setSearchQuery = onSearchChange ?? setInternalQuery;
  const hasQuery = searchQuery.trim().length > 0;

  const clearCollapseTimer = useCallback(() => {
    if (collapseTimerRef.current != null) {
      window.clearTimeout(collapseTimerRef.current);
      collapseTimerRef.current = null;
    }
  }, []);

  const openSearch = useCallback(() => {
    hapticButtonTap();
    clearCollapseTimer();
    setIsExpanded(true);
    window.requestAnimationFrame(() => {
      searchInputRef.current?.focus();
    });
  }, [clearCollapseTimer]);

  const closeSearch = useCallback(() => {
    hapticDismiss();
    clearCollapseTimer();
    setSearchQuery("");
    setIsSearchFocused(false);
    setIsExpanded(false);
    searchInputRef.current?.blur();
  }, [clearCollapseTimer, setSearchQuery]);

  useEffect(() => {
    if (hasQuery) {
      setIsExpanded(true);
    }
  }, [hasQuery]);

  useEffect(() => {
    return () => clearCollapseTimer();
  }, [clearCollapseTimer]);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === "k") {
        e.preventDefault();
        openSearch();
      }
      if (e.key === "Escape" && (isExpanded || isSearchFocused || hasQuery)) {
        e.preventDefault();
        if (hasQuery) {
          setSearchQuery("");
          return;
        }
        closeSearch();
      }
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [closeSearch, hasQuery, isExpanded, isSearchFocused, openSearch, setSearchQuery]);

  const handleBlur = () => {
    setIsSearchFocused(false);
    if (hasQuery) return;
    clearCollapseTimer();
    collapseTimerRef.current = window.setTimeout(() => {
      setIsExpanded(false);
    }, 120);
  };

  // The expanded field swaps the row's contents out, so anything the header has
  // styled comes back bare; nudge it to re-apply on every toggle. Only when one
  // is actually listening, or this would wake every scroll-driven component on
  // the screen each time the field opens.
  useNativePageBarResync(pageCollapse ? headerRef.current : null, isExpanded);

  // A page sharing this bar leads with its back chevron, the same as every
  // other non-root page; the brand only belongs on a root feed. Rendered
  // outside the expanded/collapsed branch below, because unmounting it with the
  // field open left the screen with no way back and no title either.
  const leading = pageCollapse ? (
    <div ref={pageCollapse.leadingRef} className="flex shrink-0 items-center">
      <NativePageBackButton />
    </div>
  ) : null;
  const brandHome = pageCollapse ? null : (
    <NativeAppBrandButton className="min-w-0 max-w-[58%] sm:max-w-none" />
  );

  return (
    <header
      ref={headerRef}
      className={cn(
        // The shared pinned-toolbar geometry, not a restatement of it: a
        // collapsing page header measures its handoff against this bar's bottom
        // edge, so the two must not drift.
        nativePageBarClassName,
        "transition-all duration-300",
        isExpanded ? "justify-stretch" : "justify-between",
      )}
    >
      {/* Opaque backing that extends above the pinned bar to cover the scroll
          container's top padding + the status-bar area, so day/task titles can
          never be seen above or behind the toolbar while scrolling. */}
      <div
        aria-hidden
        className="pointer-events-none absolute inset-x-0 bottom-full h-screen bg-background"
      />
      {/* Both out of flow, so they live outside the branch and never remount —
          unmounting the title left the page with no heading at all while the
          field was open. The title comes last so it paints over the dissolve
          band; while the field is up, the field paints over it. */}
      {pageCollapse ? (
        <>
          <div
            ref={pageCollapse.fadeRef}
            aria-hidden
            className="pointer-events-none absolute inset-x-0 top-full bg-gradient-to-b from-background to-transparent"
            style={{ height: nativePageHeaderMetrics.contentFadeHeight, opacity: 0 }}
          />
          {/* The bar's copy of the page title, at the same size as the block's
              own. See NativePageHeader for the handoff, and for why the
              centring is the layer's job rather than a `-translate-y-1/2` on
              the title. `aria-hidden` because the block's h1 is the page's real
              heading. */}
          <div aria-hidden className={nativePageBarTitleLayerClassName}>
            <span
              ref={pageCollapse.dockedTitleRef}
              className={nativePageBarDockedTitleClassName}
              // Not a class: the collapse writes `opacity` inline every frame,
              // so an `opacity-0` utility here never won and the title stayed
              // visible under the open field. This is read by the frame callback
              // instead — see NativePageHeader. `useNativePageBarResync` above
              // already re-runs it whenever `isExpanded` flips.
              data-bar-title-suppressed={isExpanded ? "true" : "false"}
              style={{ color: pageCollapse.accentColor, opacity: 0, visibility: "hidden" }}
            >
              {pageCollapse.title}
            </span>
          </div>
        </>
      ) : null}
      {leading}
      {isExpanded ? (
        <div className="relative flex min-w-0 flex-1 items-center">
          <div
            className={cn(
              "relative flex w-full items-center",
              "rounded-full",
              "bg-card/90",
              "border border-white/70 shadow-[0_12px_28px_-22px_hsl(var(--shadow)/0.55)]",
              "transition-colors duration-200",
              "dark:border-white/10",
              isSearchFocused && ["bg-card", "border-accent/45"],
            )}
          >
            <Search
              className={cn(
                "pointer-events-none absolute left-4 h-4 w-4",
                "transition-colors duration-200",
                isSearchFocused ? "text-accent" : "text-muted-foreground",
              )}
            />

            <input
              ref={searchInputRef}
              type="text"
              placeholder={placeholder}
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              onFocus={() => {
                clearCollapseTimer();
                setIsSearchFocused(true);
              }}
              onBlur={handleBlur}
              className={cn(
                "h-14 w-full rounded-full bg-transparent pl-11 pr-20",
                "text-base font-extrabold text-foreground md:text-sm",
                "placeholder:text-muted-foreground/50",
                "outline-none",
              )}
            />

            <div className="absolute right-3 flex items-center gap-2">
              {!hasQuery && (
                <button
                  type="button"
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => searchInputRef.current?.focus()}
                  className={cn(
                    "hidden items-center gap-1 sm:flex",
                    "rounded-full bg-muted/60 px-2 py-1",
                    "text-xs font-black text-muted-foreground/60",
                    "hover:bg-muted hover:text-muted-foreground",
                    "transition-all duration-200",
                  )}
                >
                  {isMac ? (
                    <>
                      <Command className="h-3 w-3" />
                      <span>K</span>
                    </>
                  ) : (
                    <span>Ctrl+K</span>
                  )}
                </button>
              )}
              <Button
                variant="ghost"
                size="icon"
                className="h-7 w-7 rounded-full hover:bg-accent/15"
                aria-label={hasQuery ? "Clear search" : "Close search"}
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => {
                  if (hasQuery) {
                    setSearchQuery("");
                    searchInputRef.current?.focus();
                    return;
                  }
                  closeSearch();
                }}
              >
                <X className="h-4 w-4" />
              </Button>
            </div>
          </div>

          {onSelectResult && hasQuery && (
            <div className="absolute left-0 right-0 top-full z-50 mt-2 overflow-hidden rounded-[28px] border border-white/70 bg-card/98 shadow-[0_24px_48px_-20px_hsl(var(--shadow)/0.5)] backdrop-blur-xl dark:border-white/10">
              {results && results.length > 0 ? (
                <ul className="max-h-72 overflow-y-auto py-1">
                  {results.map((result) => (
                    <li key={result.id}>
                      <button
                        type="button"
                        onMouseDown={(e) => e.preventDefault()}
                        onClick={() => onSelectResult(result.id)}
                        className="flex w-full flex-col items-start gap-0.5 px-4 py-2.5 text-left transition-colors hover:bg-muted/70"
                      >
                        <span className="line-clamp-1 text-sm font-black text-foreground">
                          {result.title}
                        </span>
                        {result.subtitle && (
                          <span className="line-clamp-1 text-xs font-extrabold text-muted-foreground">
                            {result.subtitle}
                          </span>
                        )}
                      </button>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="px-4 py-3 text-sm font-extrabold text-muted-foreground">
                  No matching tasks
                </p>
              )}
            </div>
          )}
        </div>
      ) : (
        <>
          {brandHome}
          <div ref={pageCollapse?.trailingRef} className="ml-auto flex shrink-0 items-center gap-2.5">
            <button
              type="button"
              aria-label="Search"
              onClick={openSearch}
              className={collapsedButtonClassName}
            >
              <Search className="h-5 w-5 stroke-[2.4]" />
            </button>
            {trailingAction}
          </div>
        </>
      )}
    </header>
  );
}
