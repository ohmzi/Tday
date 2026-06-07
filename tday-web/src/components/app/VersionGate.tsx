import { useCallback } from "react";
import { DownloadCloud } from "lucide-react";
import { toast } from "sonner";
import { useVersionGate } from "@/hooks/useVersionGate";

/**
 * App-wide stale-build guard. Detects new deploys and either reloads silently
 * (idle tab, not editing) or offers a non-blocking "New version available"
 * toast. Renders nothing.
 */
export default function VersionGate() {
  const onPromptUpdate = useCallback((reload: () => void) => {
    toast.custom(
      (id) => (
        <button
          type="button"
          onClick={() => {
            toast.dismiss(id);
            reload();
          }}
          className="flex w-[min(calc(100vw-2rem),24rem)] items-center gap-3 rounded-[24px] border border-border bg-popover/92 px-4 py-3.5 text-left text-popover-foreground backdrop-blur-xl shadow-[0_10px_30px_-12px_hsl(var(--shadow)/0.45)] transition-colors hover:bg-popover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-foreground/20"
        >
          <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-[#E06F66]/15 text-[#E06F66]">
            <DownloadCloud className="size-[18px]" />
          </span>
          <span className="block min-w-0 flex-1">
            <span className="block font-extrabold leading-tight">
              New version available
            </span>
            <span className="mt-0.5 block text-[13px] font-medium leading-snug text-current/75">
              Reload to get the latest update.
            </span>
          </span>
        </button>
      ),
      { duration: Infinity },
    );
  }, []);

  useVersionGate({ onPromptUpdate });
  return null;
}
