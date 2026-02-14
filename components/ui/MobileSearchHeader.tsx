"use client";

import React, { useState, useEffect, useRef } from "react";
import { Menu, Search, X, Command } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { useMenu } from "@/providers/MenuProvider";

interface MobileSearchHeaderProps {
  searchQuery?: string;
  onSearchChange?: (query: string) => void;
  placeholder?: string;
}

export default function MobileSearchHeader({
  searchQuery: externalQuery,
  onSearchChange,
  placeholder = "Search notes...",
}: MobileSearchHeaderProps) {
  const { setShowMenu } = useMenu();
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
        "flex h-16 items-center gap-3",
        "bg-background/60 backdrop-blur-2xl",
        "border-b border-border/30",
        "-mx-4 px-4",
        "sm:-mx-6 sm:px-6",
        "lg:static lg:mx-0 lg:h-auto lg:border-0 lg:bg-transparent lg:backdrop-blur-none lg:px-0 lg:pb-2 lg:pt-4",
        "transition-all duration-300",
      )}
    >
      <Button
        variant="ghost"
        size="icon"
        className={cn(
          "lg:hidden h-10 w-10 rounded-xl",
          "hover:bg-accent/80",
          "transition-all duration-200",
        )}
        aria-label="Open menu"
        onClick={() => setShowMenu(true)}
      >
        <Menu className="h-5 w-5" />
      </Button>

      <div className="flex-1 flex justify-center">
        <div className="relative w-full max-w-xl">
          <div
            className={cn(
              "relative flex items-center",
              "rounded-md",
              "bg-muted/50",
              "border border-border/40",
              "transition-colors duration-200",
              isSearchFocused && ["bg-card", "border-border"],
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
                "rounded-md",
                "text-sm text-foreground",
                "placeholder:text-muted-foreground/50",
                "outline-none",
              )}
            />

            <div className="absolute right-3 flex items-center gap-2">
              {searchQuery ? (
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-7 w-7 rounded-md hover:bg-accent/50"
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
                    "px-2 py-1 rounded-md",
                    "bg-muted/60 text-muted-foreground/60",
                    "text-xs font-medium",
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

      <div className="w-10 lg:hidden" />
    </header>
  );
}
