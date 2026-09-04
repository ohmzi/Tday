import { useEffect } from "react";
import { Navigate, Outlet, useParams } from "react-router-dom";
import { SonnerToaster } from "@/components/ui/sonner";
import { useAuth } from "@/providers/AuthProvider";
import { DEFAULT_LOCALE } from "@/i18n";
import AuthBootstrapScreen from "@/components/auth/AuthBootstrapScreen";
import { markReturningBrowser } from "@/lib/security/returningBrowser";

export default function AuthLayout() {
  const { user, authState } = useAuth();
  const { locale } = useParams();
  const loc = locale || DEFAULT_LOCALE;
  const isApprovedUser = user?.approvalStatus === "APPROVED";

  useEffect(() => {
    markReturningBrowser();
  }, []);

  // Only the brief initial probe blocks. When the backend is unreachable the
  // wizard still has to render: its Mode step is how a visitor opens a local,
  // no-login workspace, which works with no server at all. (The probe keeps
  // retrying, so a recovered session still redirects into the app below.)
  if (authState === "loading") {
    return <AuthBootstrapScreen />;
  }

  if (isApprovedUser) {
    // Not straight to /app/tday: the Scheduled-vs-Floater default lives behind
    // UserPreferencesProvider, which only mounts inside AppLayout, below this gate. The /app
    // index route (AppHomeRedirectPage) makes the real call once that preference is loaded.
    return <Navigate to={`/${loc}/app`} replace />;
  }

  // The onboarding wizard rendered by /login and /register owns the full-screen
  // layout (background + centered card), so AuthLayout only handles auth gating
  // here and lets the wizard control its own presentation.
  return (
    <>
      <Outlet />
      <SonnerToaster />
    </>
  );
}
