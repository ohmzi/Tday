import React, { useEffect, useRef, useState } from "react";
import clsx from "clsx";
import Search from "./icon/search";
import { cn } from "@/lib/utils";

interface SearchBarProps {
  className?: string;
  onSubmit?: (e: React.FormEvent<HTMLFormElement>) => void;
  onChange?: (e: React.ChangeEvent<HTMLInputElement>) => void;
}

const SearchBar = ({ className, onSubmit, onChange }: SearchBarProps) => {
  const [clicked, setClicked] = useState(false);
  const inputRef = useRef<HTMLInputElement | null>(null);

  //when mouse clicks outside input or esacpe is pressed hide the shadow
  useEffect(() => {
    function handleMouseDown(e: MouseEvent) {
      const input = inputRef.current;
      if (input && !input.contains(e.target as Node)) {
        setClicked(false);
      }
    }
    function handleEscape(e: KeyboardEvent) {
      const input = inputRef.current;
      if (input && e.key === "Escape") {
        setClicked(false);
        input.blur();
      }
    }
    document.addEventListener("keydown", handleEscape);
    document.addEventListener("mousedown", handleMouseDown);
    return () => {
      document.removeEventListener("mousedown", handleMouseDown);
      document.removeEventListener("keydown", handleEscape);
    };
  }, []);
  function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (onSubmit) onSubmit(e);
  }
  return (
    <form
      onSubmit={(e) => handleSubmit(e)}
      className={cn(
        clsx(
          "m-auto flex h-11 w-full items-center gap-3 rounded-md border border-border/60 bg-muted/70 px-2 py-5 transition-all duration-200 sm:w-[85%] md:w-[70%] lg:w-[60%] xl:w-full 2xl:w-[60%]",
          clicked && "border-border bg-card shadow-[0_10px_28px_hsl(var(--shadow)/0.16)]"
        ),
        className
      )}
    >
      <button className="rounded-full p-1 text-muted-foreground transition-colors hover:bg-sidebar-accent/80 hover:text-foreground">
        <Search className="w-5 h-5" />
      </button>
      <input
        ref={inputRef}
        className="bg-transparent w-full outline-hidden placeholder:text-sm text-foreground placeholder:text-muted-foreground"
        placeholder="Search"
        onClick={() => setClicked(true)}
        onFocus={() => setClicked(true)}
        onChange={(e) => {
          if (onChange) {
            onChange(e);
          }
        }}
      />
    </form>
  );
};

export default SearchBar;
