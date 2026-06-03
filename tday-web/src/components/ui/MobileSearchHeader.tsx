import React, { useCallback, useEffect, useRef, useState } from "react";
import { Search, X, Command } from "lucide-react";
import { Button } from "@/components/ui/button";
import NativeAppBrandButton from "@/components/app/NativeAppBrandButton";
import { cn } from "@/lib/utils";

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
  showBrandHome?: boolean;
  /** When provided, a results dropdown is shown under the input while searching. */
  results?: SearchResultItem[];
  onSelectResult?: (id: string) => void;
}

const collapsedButtonClassName = cn(
  "flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl",
  "border border-white/70 bg-card/92 text-muted-foreground shadow-[0_10px_30px_-24px_hsl(var(--shadow)/0.42)]",
  "transition-all duration-200 hover:bg-card hover:text-foreground dark:border-white/10",
);

export default function MobileSearchHeader({
  searchQuery: externalQuery,
  onSearchChange,
  placeholder = "Search tasks...",
  trailingAction,
  showBrandHome = true,
  results,
  onSelectResult,
}: MobileSearchHeaderProps) {
  const [internalQuery, setInternalQuery] = useState("");
  const [isExpanded, setIsExpanded] = useState(false);
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
    clearCollapseTimer();
    setIsExpanded(true);
    window.requestAnimationFrame(() => {
      searchInputRef.current?.focus();
    });
  }, [clearCollapseTimer]);

  const closeSearch = useCallback(() => {
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

  const brandHome = showBrandHome ? (
    <NativeAppBrandButton className="min-w-0 max-w-[58%] sm:max-w-none" />
  ) : null;

  return (
    <header
      className={cn(
        // Pinned toolbar: stays at the top, fully opaque so scrolled content is
        // hidden behind it; the safe-area inset keeps the logo below the status bar.
        "sticky top-0 z-40",
        "flex w-full items-center gap-2.5 bg-background",
        "pt-[calc(0.5rem+env(safe-area-inset-top))] pb-1.5",
        "lg:static lg:bg-transparent lg:pt-2 lg:pb-2",
        "transition-all duration-300",
        isExpanded ? "justify-stretch" : "justify-between",
      )}
    >
      {/* Opaque backing that extends above the pinned bar to cover the scroll
          container's top padding + the status-bar area, so day/task titles can
          never be seen above or behind the toolbar while scrolling. */}
      <div
        aria-hidden
        className="pointer-events-none absolute inset-x-0 bottom-full h-screen bg-background lg:hidden"
      />
      {isExpanded ? (
        <div className="relative flex min-w-0 flex-1 items-center">
          <div
            className={cn(
              "relative flex w-full items-center",
              "rounded-2xl",
              "bg-card/92",
              "border border-white/70 shadow-[0_10px_30px_-24px_hsl(var(--shadow)/0.42)]",
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
                "h-11 w-full rounded-2xl bg-transparent pl-11 pr-20",
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
            <div className="absolute left-0 right-0 top-full z-50 mt-2 overflow-hidden rounded-2xl border border-white/70 bg-card/98 shadow-[0_24px_48px_-20px_hsl(var(--shadow)/0.5)] backdrop-blur-xl dark:border-white/10">
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
          <div className="flex shrink-0 items-center gap-2.5">
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
