import { Navigate, useParams } from "react-router-dom";
import { useAuth } from "@/providers/AuthProvider";
import OnboardingLanding from "@/components/landing/OnboardingLanding";
import { DEFAULT_LOCALE } from "@/i18n";
import AuthBootstrapScreen from "@/components/auth/AuthBootstrapScreen";
import { hasReturningBrowser } from "@/lib/security/returningBrowser";

export default function LandingPage() {
  const { authState } = useAuth();
  const { locale } = useParams();
  const loc = locale || DEFAULT_LOCALE;
  const isReturningBrowser = hasReturningBrowser();

  if (authState === "loading" || authState === "unavailable") {
    return <AuthBootstrapScreen />;
  }

  if (authState === "authenticated") {
    // See AuthLayout: the Scheduled-vs-Floater default is resolved by the /app index route.
    return <Navigate to={`/${loc}/app`} replace />;
  }

  if (isReturningBrowser) {
    return <Navigate to={`/${loc}/login`} replace />;
  }

  return <OnboardingLanding />;
}
