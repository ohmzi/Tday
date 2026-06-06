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

  if (authState === "loading" || authState === "unavailable") {
    return <AuthBootstrapScreen />;
  } 

  if (isApprovedUser) {
    return <Navigate to={`/${loc}/app/tday`} replace />;
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
