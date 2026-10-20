import { Navigate } from "react-router-dom";
import { useUserPreferences } from "@/providers/UserPreferencesProvider";
import { useLocale } from "@/lib/navigation";
import { DefaultHomeScreen } from "@/types/enums";
import AppShellSkeleton from "@/components/app/AppShellSkeleton";

/**
 * The `/app` index route. AuthLayout, LandingPage, and OnboardingWizard all land here
 * (rather than at `/app/tday` directly) after establishing a session, because the real
 * Scheduled-vs-Floater decision needs `UserPreferencesProvider`, which only mounts inside
 * `AppLayout` — below where those three redirects fire. This is that decision point.
 *
 * Reached only once per fresh entry into the app: `RootDock` navigates straight to
 * `/app/tday` or `/app/floater` for every in-session tab tap, so a manual dock choice is
 * never re-routed back through here.
 */
export default function AppHomeRedirectPage() {
  const locale = useLocale();
  const { preferences, isLoading } = useUserPreferences();

  // Only the fetch itself blocks. A settled query with no preferences (a fetch error, most
  // likely) must still leave — falling through to the Scheduled default below — rather than
  // stranding the user on this skeleton forever with no way into the app.
  if (isLoading) {
    return <AppShellSkeleton />;
  }

  const target =
    preferences?.defaultHomeScreen === DefaultHomeScreen.floater ? "floater" : "tday";
  return <Navigate to={`/${locale}/app/${target}`} replace />;
}
