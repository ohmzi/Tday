import { Navigate, useParams } from "react-router-dom";
import { useAuth } from "@/providers/AuthProvider";
import AdminUserControl from "@/components/admin/AdminUserControl";
import { DEFAULT_LOCALE } from "@/i18n";

export default function AdminPage() {
  const { user } = useAuth();
  const { locale } = useParams();
  const loc = locale || DEFAULT_LOCALE;

  if (user?.role !== "ADMIN" || user?.approvalStatus !== "APPROVED") {
    return <Navigate to={`/${loc}/app/tday`} replace />;
  }

  return <AdminUserControl />;
}
