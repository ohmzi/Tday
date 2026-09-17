import VersionPage from "@/components/release/VersionPage";

/** The `admin/version` element — the same release screen the Settings row opens,
 * entered from the admin dashboard, so its back button falls back there. */
export default function AppVersionPage() {
  return <VersionPage backFallbackHref="/app/admin" />;
}
