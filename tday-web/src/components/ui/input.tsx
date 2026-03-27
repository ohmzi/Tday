import * as React from "react";

import { cn } from "@/lib/utils";

/**
 * The app's base text input: themed border, ring and placeholder, with `file:` rules so a
 * file input picks up the same treatment instead of the browser's own chrome.
 *
 * `forwardRef` matters beyond convention here — callers focus these imperatively (opening a
 * task form puts the caret in the title field) and form libraries register them by ref.
 *
 * The class list stays one literal on purpose: Tailwind extracts class names by scanning
 * source text, so splitting or building it up would hide utilities from the compiler.
 */
const Input = React.forwardRef<HTMLInputElement, React.ComponentProps<"input">>(
  ({ className, type, ...props }, ref) => {
    return (
      <input
        type={type}
        className={cn(
          "flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-base ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium file:text-foreground placeholder:text-muted-foreground focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 md:text-sm",
          className,
        )}
        ref={ref}
        {...props}
      />
    );
  },
);
Input.displayName = "Input";

export { Input };
