import React, { useState, useEffect, useRef } from "react";
import { Search, X, Command } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

interface MobileSearchHeaderProps {
  searchQuery?: string;
  onSearchChange?: (query: string) => void;
  placeholder?: string;
}

export default function MobileSearchHeader({
  searchQuery: externalQuery,
  onSearchChange,
  placeholder = "Search tasks...",
}: MobileSearchHeaderProps) {
  const [internalQuery, setInternalQuery] = useState("");
  const [isSearchFocused, setIsSearchFocused] = useState(false);
  const searchInputRef = useRef<HTMLInputElement>(null);
  const isMac =
    typeof window !== "undefined" &&
    navigator.userAgent.toLowerCase().includes("mac");

  const searchQuery = externalQuery ?? internalQuery;
  const setSearchQuery = onSearchChange ?? setInternalQuery;

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === "k") {
        e.preventDefault();
        searchInputRef.current?.focus();
      }
      if (e.key === "Escape" && (isSearchFocused || searchQuery.trim().length > 0)) {
        setSearchQuery("");
        setIsSearchFocused(false);
        searchInputRef.current?.blur();
      }
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [isSearchFocused, searchQuery, setSearchQuery]);

  return (
    <header
      className={cn(
        "sticky top-0 z-40",
        "flex items-center gap-3 py-1.5",
        "bg-background/88 backdrop-blur-2xl",
        "lg:static lg:bg-transparent lg:backdrop-blur-none lg:pb-2 lg:pt-2",
        "transition-all duration-300",
      )}
    >
      <div className="flex-1 flex justify-center">
        <div className="relative w-full max-w-xl">
          <div
            className={cn(
              "relative flex items-center",
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
                "absolute left-4 h-4 w-4 pointer-events-none",
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
              onFocus={() => setIsSearchFocused(true)}
              onBlur={() => setIsSearchFocused(false)}
              className={cn(
                "w-full h-11 pl-11 pr-24",
                "bg-transparent",
                "rounded-2xl",
                "text-base font-extrabold text-foreground md:text-sm",
                "placeholder:text-muted-foreground/50",
                "outline-none",
              )}
            />

            <div className="absolute right-3 flex items-center gap-2">
              {searchQuery ? (
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-7 w-7 rounded-full hover:bg-accent/15"
                  onClick={() => {
                    setSearchQuery("");
                    setIsSearchFocused(false);
                    searchInputRef.current?.blur();
                  }}
                >
                  <X className="h-4 w-4" />
                </Button>
              ) : (
                <button
                  type="button"
                  onClick={() => searchInputRef.current?.focus()}
                  className={cn(
                    "hidden sm:flex items-center gap-1",
                    "px-2 py-1 rounded-full",
                    "bg-muted/60 text-muted-foreground/60",
                    "text-xs font-black",
                    "hover:bg-muted hover:text-muted-foreground",
                    "transition-all duration-200",
                    isSearchFocused && "opacity-0 pointer-events-none",
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
            </div>
          </div>
        </div>
      </div>
    </header>
  );
}
