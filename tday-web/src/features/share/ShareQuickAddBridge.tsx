import { useEffect, useRef } from "react";
import { useSearchParams } from "react-router-dom";
import { useCreateTask } from "@/providers/CreateTaskProvider";

/**
 * Consumes the `?quickadd=` query param written by the share_target redirect:
 * opens the global create-task sheet prefilled with the shared text (typing
 * in the title re-runs the chrono-node date parse as usual), then strips the
 * param so a refresh doesn't re-open the sheet.
 */
export function ShareQuickAddBridge() {
  const [searchParams, setSearchParams] = useSearchParams();
  const { openCreateTask } = useCreateTask();
  const handled = useRef(false);

  const quickadd = searchParams.get("quickadd");
  const sharedTitle = searchParams.get("title");

  useEffect(() => {
    if (handled.current) return;
    const text = [sharedTitle, quickadd].filter(Boolean).join(" ").trim();
    if (!text) return;
    handled.current = true;
    openCreateTask({ title: text });
    const next = new URLSearchParams(searchParams);
    next.delete("quickadd");
    next.delete("title");
    setSearchParams(next, { replace: true });
  }, [quickadd, sharedTitle, openCreateTask, searchParams, setSearchParams]);

  return null;
}
