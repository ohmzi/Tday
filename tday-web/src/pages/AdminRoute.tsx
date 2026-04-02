import type { ReactNode } from "react";
import { Navigate, Outlet, useParams } from "react-router-dom";
import { useAuth } from "@/providers/AuthProvider";
import { DEFAULT_LOCALE } from "@/i18n";

/** Restricts child routes to approved admins and sends everyone else back to Today. */
export default function AdminRoute({ children }: { children?: ReactNode }) {
  const { user } = useAuth();
  const { locale } = useParams();
  const loc = locale || DEFAULT_LOCALE;

  if (user?.role !== "ADMIN" || user?.approvalStatus !== "APPROVED") {
    return <Navigate to={`/${loc}/app/tday`} replace />;
  }

  return children ?? <Outlet />;
}
