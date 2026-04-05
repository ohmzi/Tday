import { Navigate, Outlet, useParams } from "react-router-dom";
import { Loader2 } from "lucide-react";
import { SonnerToaster } from "@/components/ui/sonner";
import UnauthenticatedCacheGuard from "@/components/auth/UnauthenticatedCacheGuard";
import { useAuth } from "@/providers/AuthProvider";
import { DEFAULT_LOCALE } from "@/i18n";

export default function AuthLayout() {
  const { user, isLoading } = useAuth();
  const { locale } = useParams();
  const loc = locale || DEFAULT_LOCALE;
  const isApprovedUser = user?.approvalStatus === "APPROVED";

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <Loader2 className="h-8 w-8 animate-spin text-accent" />
      </div>
    );
  }

  if (isApprovedUser) {
    return <Navigate to={`/${loc}/app/tday`} replace />;
  }

  return (
    <div className="relative flex min-h-screen items-center justify-center bg-background">
      <UnauthenticatedCacheGuard />
      <div className="absolute inset-0 bg-gradient-to-br from-background via-background to-muted/30" />
      <div className="relative z-10 mx-4 w-full max-w-md">
        <Outlet />
      </div>
      <SonnerToaster />
    </div>
  );
}
