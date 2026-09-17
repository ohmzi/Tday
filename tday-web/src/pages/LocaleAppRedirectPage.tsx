import { Navigate, useLocation } from "react-router-dom";
import { resolveInitialLocale } from "@/i18n";
import { localizePath } from "@/lib/navigation";

/**
 * LOCALE-LESS APP ENTRY: `/app/...` with no locale segment.
 *
 * Every app route is nested under `/:locale` (router.tsx), so `/app/tday` is two
 * segments and binds `locale = "app"` before finding no child named `tday` and
 * falling through to the `*` catch-all — the installed-PWA "Page not found". The OS
 * issues these URLs itself and no `localizePath` call ever runs on them: the manifest
 * `start_url` and `shortcuts[].url` (manifest.webmanifest), a home-screen bookmark,
 * and anything the service worker opens verbatim. In a browser you enter at `/` and
 * the root route redirects to `/${resolveInitialLocale()}` first, which is why this
 * only ever bit an installed app cold-starting at `start_url`.
 *
 * A top-level static `/app/*` route ahead of `/:locale` wins that match by segment
 * ranking (static beats dynamic — the same rule that already keeps `/share` out of
 * `/:locale`), so this element sees the whole locale-less path and re-dispatches it
 * through the SAME localizer the other thirty-odd call sites use. Path, query and
 * hash all ride along: `/app/completed?scope=floater` must land on the Floater tab.
 *
 * It cannot loop. The destination always begins with a member of SUPPORTED_LOCALES,
 * and the literal segment `app` is not one, so `/app/*` can never re-match it.
 */
export default function LocaleAppRedirectPage() {
  const { pathname, search, hash } = useLocation();
  return (
    <Navigate
      to={{ pathname: localizePath(pathname, resolveInitialLocale()), search, hash }}
      replace
    />
  );
}
