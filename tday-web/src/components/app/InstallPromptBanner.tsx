import { Download, Share, X } from "lucide-react";
import { useInstallPrompt } from "@/hooks/useInstallPrompt";
import { cn } from "@/lib/utils";
import { useFadeUnmount } from "@/hooks/useFadeUnmount";

// Mirrors the exit declared below, so the node is removed the frame after it finishes.
const BANNER_EXIT_MS = 200;

export default function InstallPromptBanner() {
  const { showBanner, isIosSafari, promptInstall, dismiss } = useInstallPrompt();

  // The banner declared a 300ms slide-in and no exit, and was then dropped by a bare
  // `if (!showBanner) return null` — so dismissing it removed it between frames. Kept alive
  // for BANNER_EXIT_MS so the slide-out below has somewhere to run.
  const present = useFadeUnmount(showBanner, BANNER_EXIT_MS);
  if (!present) return null;

  return (
    <div
      data-state={showBanner ? "open" : "closed"}
      className={cn(
        "fixed inset-x-0 bottom-[calc(90px+env(safe-area-inset-bottom))] z-50 mx-auto w-[calc(100%-2rem)] max-w-md",
        "data-[state=open]:animate-in data-[state=open]:slide-in-from-bottom-4 data-[state=open]:fade-in data-[state=open]:duration-300",
        "data-[state=closed]:animate-out data-[state=closed]:slide-out-to-bottom-4 data-[state=closed]:fade-out data-[state=closed]:duration-200",
      )}
    >
      <div className="relative overflow-hidden rounded-2xl border border-white/70 bg-card/95 p-4 shadow-[0_20px_50px_-20px_hsl(var(--shadow)/0.5)] backdrop-blur-xl dark:border-white/10">
        <button
          type="button"
          onClick={dismiss}
          className="absolute right-3 top-3 flex h-7 w-7 items-center justify-center rounded-full text-muted-foreground hover:bg-muted/60 hover:text-foreground"
          aria-label="Dismiss"
        >
          <X className="h-4 w-4" />
        </button>

        <div className="flex items-start gap-3 pr-6">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-accent/15">
            <Download className="h-5 w-5 text-accent" />
          </div>
          <div className="min-w-0">
            <p className="text-sm font-black text-foreground">Install T'Day</p>
            {isIosSafari ? (
              <p className="mt-1 text-xs font-extrabold leading-relaxed text-muted-foreground">
                Tap{" "}
                <Share className="inline h-3.5 w-3.5 -translate-y-px text-accent" />{" "}
                then <span className="font-black text-foreground">"Add to Home Screen"</span> to
                launch T'Day full-screen from your home screen.
              </p>
            ) : (
              <>
                <p className="mt-1 text-xs font-extrabold text-muted-foreground">
                  Add T'Day to your home screen to launch it in its own window.
                </p>
                <button
                  type="button"
                  onClick={promptInstall}
                  className="mt-2.5 inline-flex h-9 items-center gap-1.5 rounded-xl bg-accent px-4 text-xs font-black text-white transition-colors hover:bg-accent/90"
                >
                  <Download className="h-3.5 w-3.5" />
                  Install
                </button>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
