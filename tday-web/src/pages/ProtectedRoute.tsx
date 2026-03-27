import { Navigate, Outlet, useParams } from "react-router-dom";
import { useAuth } from "@/providers/AuthProvider";
import { Loader2 } from "lucide-react";
import { DEFAULT_LOCALE } from "@/i18n";

export default function ProtectedRoute() {
  const { user, isLoading, isAuthenticated } = useAuth();
  const { locale } = useParams();
  const loc = locale || DEFAULT_LOCALE;

  if (isLoading) {
    return (
      <div className="flex h-screen w-full items-center justify-center bg-background">
        <Loader2 className="h-8 w-8 animate-spin text-accent" />
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to={`/${loc}/login`} replace />;
  }

  if (user?.approvalStatus && user.approvalStatus !== "APPROVED") {
    return <Navigate to={`/${loc}/login?pending=1`} replace />;
  }

  return <Outlet />;
}
