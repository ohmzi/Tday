import { RouterProvider } from "react-router-dom";
import { router } from "@/router";
import { ThemeProvider } from "@/providers/ThemeProvider";
import QueryProvider from "@/providers/QueryProvider";
import { AuthProvider } from "@/providers/AuthProvider";
import { TooltipProvider } from "@/components/ui/tooltip";
import ErrorBoundary from "@/components/ErrorBoundary";
import { SonnerToaster } from "@/components/ui/sonner";
import VersionGate from "@/components/app/VersionGate";
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
              <RouterProvider router={router} />
            </ErrorBoundary>
          </TooltipProvider>
        </AuthProvider>
      </QueryProvider>
      <VersionGate />
      <SonnerToaster />
    </ThemeProvider>
  );
}
