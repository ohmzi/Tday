import { RouterProvider } from "react-router-dom";
import { router } from "@/router";
import { ThemeProvider } from "@/providers/ThemeProvider";
import QueryProvider from "@/providers/QueryProvider";
import { AuthProvider } from "@/providers/AuthProvider";
import { TooltipProvider } from "@/components/ui/tooltip";
import ErrorBoundary from "@/components/ErrorBoundary";
import LocalWorkspaceGate from "@/components/local/LocalWorkspaceGate";
import { SonnerToaster } from "@/components/ui/sonner";
import VersionGate from "@/components/app/VersionGate";
import ConnectivityGate from "@/components/app/ConnectivityGate";
import { useThemeColor } from "@/hooks/useThemeColor";

function ThemeColorSync() {
  useThemeColor();
  return null;
}

export default function App() {
  // No `disableTransitionOnChange` here, deliberately.
  //
  // It makes next-themes inject a global
  // `*,*::before,*::after{transition:none!important}` on every `setTheme` call and hold it
  // across the forced style recalc, so for exactly the frame the theme class flips, every
  // transition in the app is dead. A theme change animates nothing on its own, so the only
  // control that suffers is the one whose own `transform` changes in that same frame: the
  // appearance segmented thumb, which SNAPPED to its next slot while the "Default home
  // screen" thumb beside it — which no theme change accompanies — glided. That asymmetry is
  // the bug this prop was causing; the two controls are the same widget and have to move the
  // same way. Dropping it lets the flip crossfade the pressable colour transitions for
  // `--tday-duration-quick` (150ms) rather than cutting them dead. The route handover's own
  // view-transition rules in globals.css are unaffected either way.
  return (
    <ThemeProvider attribute="class" defaultTheme="system" enableSystem>
      <ThemeColorSync />
      <QueryProvider>
        <AuthProvider>
          <TooltipProvider>
            <ErrorBoundary>
              {/* Local Mode keeps everything in this browser, encrypted: no route
                  renders until the passphrase has been entered for this session. */}
              <LocalWorkspaceGate>
                <RouterProvider router={router} />
              </LocalWorkspaceGate>
            </ErrorBoundary>
          </TooltipProvider>
        </AuthProvider>
      </QueryProvider>
      <VersionGate />
      <ConnectivityGate />
      <SonnerToaster />
    </ThemeProvider>
  );
}
