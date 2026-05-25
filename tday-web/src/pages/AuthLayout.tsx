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

  return (
    <div className="relative flex min-h-screen items-center justify-center bg-background">
      <div className="absolute inset-0 bg-gradient-to-br from-background via-background to-muted/30" />
      <div className="relative z-10 mx-4 w-full max-w-md">
        <Outlet />
      </div>
      <SonnerToaster />
    </div>
  );
}
