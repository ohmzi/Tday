import { useQuery } from "@tanstack/react-query";
import {
  compareVersions,
  createFallbackReleaseMetadata,
  CURRENT_APP_VERSION,
  CURRENT_RELEASE_PATH,
  fetchGitHubReleaseMetadataByTag,
  fetchReleaseMetadata,
  GITHUB_RELEASES_URL,
  LATEST_RELEASE_METADATA_URL,
  normalizeVersion,
  readStoredCurrentRelease,
  storeCurrentRelease,
  type ReleaseMetadata,
} from "@/features/release/lib/release";

export type ReleaseInfo = {
  currentVersion: string;
  currentRelease: ReleaseMetadata;
  latestRelease: ReleaseMetadata | null;
  hasUpdate: boolean;
  latestUrl: string;
  /**
   * True when the latest-release metadata could not be read, so `hasUpdate:
   * false` means "not known" rather than "up to date". The installed release is
   * still real; only the comparison is missing. The native apps get this for
   * free from `releaseError`; the web has to say it, because a failed check and
   * a genuine up-to-date build otherwise render identically.
   */
  latestLookupFailed: boolean;
};

function hasNotes(release: ReleaseMetadata | null | undefined) {
  return (release?.notes.length ?? 0) > 0;
}

/** Loads the installed release metadata, preferring the bundled copy and falling back to local storage. */
export async function loadCurrentRelease(): Promise<ReleaseMetadata> {
  const cachedRelease = readStoredCurrentRelease(CURRENT_APP_VERSION);
  let bundledRelease: ReleaseMetadata | null = null;

  try {
    const currentRelease = await fetchReleaseMetadata(CURRENT_RELEASE_PATH);
    if (normalizeVersion(currentRelease.version) === CURRENT_APP_VERSION) {
      bundledRelease = currentRelease;
      if (hasNotes(currentRelease)) {
        storeCurrentRelease(currentRelease);
        return currentRelease;
      }
    }
  } catch {
    // Fall back to local storage, GitHub release data, or a generated placeholder.
  }

  if (cachedRelease && hasNotes(cachedRelease)) {
    return cachedRelease;
  }

  try {
    const currentRelease = await fetchGitHubReleaseMetadataByTag(CURRENT_APP_VERSION);
    storeCurrentRelease(currentRelease);
    return currentRelease;
  } catch {
    // Fall back to the best local copy when GitHub is unavailable.
  }

  return bundledRelease ?? cachedRelease ?? createFallbackReleaseMetadata(CURRENT_APP_VERSION);
}

/**
 * Combines installed and latest release metadata into the release state every
 * signed-in user's version screen reads. Nothing here is privileged: the
 * installed version comes from the bundle and the published metadata from
 * public endpoints, so the same query backs the admin screen, the user-facing
 * version screen and the Settings row's update hint.
 */
async function getReleaseInfo(): Promise<ReleaseInfo> {
  const currentRelease = await loadCurrentRelease();

  try {
    const latestRelease = await fetchReleaseMetadata(
      `${LATEST_RELEASE_METADATA_URL}?t=${Date.now()}`,
    );
    const comparison = compareVersions(latestRelease.version, CURRENT_APP_VERSION);
    const hasUpdate = typeof comparison === "number" ? comparison > 0 : false;

    return {
      currentVersion: CURRENT_APP_VERSION,
      currentRelease,
      latestRelease: hasUpdate ? latestRelease : null,
      hasUpdate,
      latestUrl: hasUpdate ? latestRelease.releaseUrl : currentRelease.releaseUrl,
      latestLookupFailed: false,
    };
  } catch {
    return {
      currentVersion: CURRENT_APP_VERSION,
      currentRelease,
      latestRelease: null,
      hasUpdate: false,
      latestUrl: currentRelease.releaseUrl || GITHUB_RELEASES_URL,
      latestLookupFailed: true,
    };
  }
}

/** Queries the release status for the current installed build and GitHub metadata snapshot. */
export function useReleaseInfo(options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: ["releaseInfo", CURRENT_APP_VERSION],
    queryFn: getReleaseInfo,
    enabled: options?.enabled ?? true,
    staleTime: 15 * 60 * 1000,
    refetchOnWindowFocus: true,
    refetchInterval: 30 * 60 * 1000,
    retry: 1,
  });
}
