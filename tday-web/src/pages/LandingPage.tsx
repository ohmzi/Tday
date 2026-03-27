import { Navigate, useParams } from "react-router-dom";
import { useAuth } from "@/providers/AuthProvider";
import OnboardingLanding from "@/components/landing/OnboardingLanding";
import { DEFAULT_LOCALE } from "@/i18n";

export default function LandingPage() {
  const { isAuthenticated } = useAuth();
  const { locale } = useParams();
  const loc = locale || DEFAULT_LOCALE;

  if (isAuthenticated) {
    return <Navigate to={`/${loc}/app/tday`} replace />;
  }

  return <OnboardingLanding />;
}
