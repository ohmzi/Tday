import { Download, Share, X } from "lucide-react";
import { useInstallPrompt } from "@/hooks/useInstallPrompt";
import { cn } from "@/lib/utils";
import { DURATION_MS } from "@/lib/motion";
import { useFadeUnmount } from "@/hooks/useFadeUnmount";

/**
 * Mirrors the exit declared below, so the node is removed the frame after it finishes.
 *
 * Read twice — once by the class, once by `useFadeUnmount` — so it names the rung rather
 * than restating the number, the way `MODAL_EXIT_MS` does: a length spelled `duration-quick`
 * on one side and `150` on the other drifts apart the first time the rung moves, and a
 * half-played exit is the failure nobody sees in a test.
 */
const BANNER_EXIT_MS = DURATION_MS.quick;

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
        // Emphasis in, Quick out. The banner rises from off-screen, which is a change
        // of position and therefore rule 2's rung; the 300 it did that on named none.
        // The way out is not the arrival played backwards: this is an offer being
        // declined, and the user who tapped the X is looking at what is behind it
        // already. Quick is the rung for something leaving that nobody is meant to
        // watch go, and it keeps rule 1's cap with room to spare.
        "data-[state=open]:animate-in data-[state=open]:slide-in-from-bottom-4 data-[state=open]:fade-in data-[state=open]:duration-emphasis",
        "data-[state=closed]:animate-out data-[state=closed]:slide-out-to-bottom-4 data-[state=closed]:fade-out data-[state=closed]:duration-quick",
      )}
    >
      <div className="relative overflow-hidden rounded-lg border border-white/70 bg-card/95 p-4 shadow-[0_20px_50px_-20px_hsl(var(--shadow)/0.5)] backdrop-blur-xl dark:border-white/10">
        <button
          type="button"
          onClick={dismiss}
          className="absolute right-3 top-3 flex h-7 w-7 items-center justify-center rounded-full text-muted-foreground hover:bg-muted/60 hover:text-foreground"
          aria-label="Dismiss"
        >
          <X className="h-4 w-4" />
        </button>

        <div className="flex items-start gap-3 pr-6">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-sm bg-accent/15">
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
                  className="mt-2.5 inline-flex h-9 items-center gap-1.5 rounded-sm bg-accent px-4 text-xs font-black text-white transition-colors hover:bg-accent/90"
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
