import VersionPage from "@/components/release/VersionPage";

/**
 * The `version` route element — the release screen for every signed-in user,
 * reached from the Settings About card's App Version row.
 *
 * This used to be a redirect: admins were forwarded to `/app/admin/version` and
 * everyone else was bounced back to Today, which left the row's version string
 * untappable in practice. Both routes now render the one release component, so
 * the admin guard (still on `/app/admin/version` in the router) is the only
 * thing that still separates them — the page itself was never admin-only, its
 * data comes from the bundle and public release metadata.
 *
 * The name is historical: renaming the file means editing the lazy import in
 * `src/router.tsx`, which another change is holding.
 */
export default function VersionRedirectPage() {
  return <VersionPage backFallbackHref="/app/settings" />;
}
