import { useEffect, useRef } from "react";
import { isEditableTarget } from "@/lib/keyboard";

export type GlobalHotkeyHandlers = {
  /** Plain `N` outside any input. */
  onNewTask: () => void;
  /** `Cmd/Ctrl+K` (also fires inside inputs, like every palette). */
  onTogglePalette: () => void;
  /** `?` outside any input. */
  onShowShortcuts: () => void;
};

/**
 * App-wide keyboard layer. Listens on `window` so page-scoped `document`
 * listeners (e.g. the floater search's own Cmd/Ctrl+K) run first and can
 * claim a chord with preventDefault — we skip anything already handled.
 */
export function useGlobalHotkeys(handlers: GlobalHotkeyHandlers) {
  // Handlers live in a ref so the window listener binds exactly once.
  const handlersRef = useRef(handlers);
  handlersRef.current = handlers;

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.defaultPrevented) return;

      if (
        (event.metaKey || event.ctrlKey) &&
        !event.altKey &&
        !event.shiftKey &&
        event.key.toLowerCase() === "k"
      ) {
        event.preventDefault();
        handlersRef.current.onTogglePalette();
        return;
      }

      if (isEditableTarget(event.target)) return;
      if (event.metaKey || event.ctrlKey || event.altKey) return;

      if (event.key === "n" || event.key === "N") {
        event.preventDefault();
        handlersRef.current.onNewTask();
        return;
      }

      if (event.key === "?") {
        event.preventDefault();
        handlersRef.current.onShowShortcuts();
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, []);
}
