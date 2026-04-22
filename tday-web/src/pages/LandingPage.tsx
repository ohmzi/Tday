import { Navigate, useParams } from "react-router-dom";
import { Loader2 } from "lucide-react";
import { useAuth } from "@/providers/AuthProvider";
import OnboardingLanding from "@/components/landing/OnboardingLanding";
import { DEFAULT_LOCALE } from "@/i18n";

export default function LandingPage() {
  const { isAuthenticated, isLoading } = useAuth();
  const { locale } = useParams();
  const loc = locale || DEFAULT_LOCALE;

  if (isLoading) {
    return (
      <div className="flex h-screen w-full items-center justify-center bg-background">
        <Loader2 className="h-8 w-8 animate-spin text-accent" />
      </div>
    );
  }

  if (isAuthenticated) {
    return <Navigate to={`/${loc}/app/tday`} replace />;
  }

  return <OnboardingLanding />;
}
