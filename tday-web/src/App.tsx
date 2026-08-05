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
  return (
    <ThemeProvider attribute="class" defaultTheme="system" enableSystem disableTransitionOnChange>
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
