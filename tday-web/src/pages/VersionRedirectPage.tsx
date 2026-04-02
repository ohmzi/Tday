import { Navigate, useParams } from "react-router-dom";
import { useAuth } from "@/providers/AuthProvider";
import { DEFAULT_LOCALE } from "@/i18n";

export default function VersionRedirectPage() {
  const { user } = useAuth();
  const { locale } = useParams();
  const loc = locale || DEFAULT_LOCALE;
  const isAdmin = user?.role === "ADMIN" && user?.approvalStatus === "APPROVED";

  return (
    <Navigate
      to={isAdmin ? `/${loc}/app/admin/version` : `/${loc}/app/tday`}
      replace
    />
  );
}
