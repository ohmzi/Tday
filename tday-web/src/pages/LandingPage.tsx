import { Navigate, useParams } from "react-router-dom";
import { useAuth } from "@/providers/AuthProvider";
import OnboardingLanding from "@/components/landing/OnboardingLanding";
import { DEFAULT_LOCALE } from "@/i18n";
import AuthBootstrapScreen from "@/components/auth/AuthBootstrapScreen";
import { hasReturningBrowser } from "@/lib/security/returningBrowser";
import { useOnboardingTelemetryHold } from "@/hooks/useOnboardingTelemetryHold";

export default function LandingPage() {
  const { authState } = useAuth();
  const { locale } = useParams();
  const loc = locale || DEFAULT_LOCALE;
  const isReturningBrowser = hasReturningBrowser();
  // A fresh admin signs in here, and the wizard's last step asks the instance-wide error-report
  // question after that. Both redirects below would unmount the wizard, so both stand down while
  // it holds the screen for that step.
  const holdingTelemetryStep = useOnboardingTelemetryHold();

  if (authState === "loading" || authState === "unavailable") {
    return <AuthBootstrapScreen />;
  }

  if (authState === "authenticated" && !holdingTelemetryStep) {
    // See AuthLayout: the Scheduled-vs-Floater default is resolved by the /app index route.
    return <Navigate to={`/${loc}/app`} replace />;
  }

  if (isReturningBrowser && !holdingTelemetryStep) {
    return <Navigate to={`/${loc}/login`} replace />;
  }

  return <OnboardingLanding />;
}
