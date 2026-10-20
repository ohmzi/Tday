import { Navigate, Outlet, useParams } from "react-router-dom";
import { useAuth } from "@/providers/AuthProvider";
import { DEFAULT_LOCALE } from "@/i18n";
import AuthBootstrapScreen from "@/components/auth/AuthBootstrapScreen";

export default function ProtectedRoute() {
  const { user, authState, isAuthenticated } = useAuth();
  const { locale } = useParams();
  const loc = locale || DEFAULT_LOCALE;

  if (authState === "loading" || authState === "unavailable") {
    return <AuthBootstrapScreen />;
  }

  if (!isAuthenticated) {
    return <Navigate to={`/${loc}/login`} replace />;
  }

  if (user?.approvalStatus && user.approvalStatus !== "APPROVED") {
    return <Navigate to={`/${loc}/login?pending=1`} replace />;
  }

  return <Outlet />;
}
