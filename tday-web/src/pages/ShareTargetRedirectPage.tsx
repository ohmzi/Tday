import { Navigate, useLocation } from "react-router-dom";
import { resolveInitialLocale } from "@/i18n";

/**
 * PWA share_target entry: the OS share sheet opens `/share?title=…&quickadd=…`
 * (see manifest.webmanifest). Bounce into the localized Today screen keeping
 * the query string, where ShareQuickAddBridge opens the create sheet prefilled.
 */
export default function ShareTargetRedirectPage() {
  const { search } = useLocation();
  return (
    <Navigate
      to={{ pathname: `/${resolveInitialLocale()}/app/tday`, search }}
      replace
    />
  );
}
