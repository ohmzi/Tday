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
          className="flex w-[min(calc(100vw-2rem),24rem)] items-start gap-3 rounded-2xl border border-border/80 bg-card/95 p-4 text-left shadow-[0_20px_45px_-24px_hsl(var(--shadow)/0.45)] backdrop-blur-2xl transition hover:border-accent/35"
        >
          <span className="mt-0.5 rounded-2xl bg-accent/12 p-2.5 text-accent">
            <DownloadCloud className="h-4 w-4" />
          </span>
          <span className="min-w-0 flex-1">
            <span className="block text-sm font-semibold text-foreground">
              New version available
            </span>
            <span className="mt-1 block text-sm leading-5 text-muted-foreground">
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
