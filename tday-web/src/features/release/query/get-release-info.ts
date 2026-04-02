import { useQuery } from "@tanstack/react-query";
import {
  compareVersions,
  createFallbackReleaseMetadata,
  CURRENT_APP_VERSION,
  CURRENT_RELEASE_PATH,
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
};

/** Loads the installed release metadata, preferring the bundled copy and falling back to local storage. */
async function loadCurrentRelease(): Promise<ReleaseMetadata> {
  const cachedRelease = readStoredCurrentRelease(CURRENT_APP_VERSION);

  try {
    const currentRelease = await fetchReleaseMetadata(CURRENT_RELEASE_PATH);
    if (normalizeVersion(currentRelease.version) === CURRENT_APP_VERSION) {
      storeCurrentRelease(currentRelease);
      return currentRelease;
    }
  } catch {
    // Fall back to the stored copy or a generated placeholder.
  }

  return cachedRelease ?? createFallbackReleaseMetadata(CURRENT_APP_VERSION);
}

/** Combines installed and latest release metadata into the admin-facing release state. */
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
    };
  } catch {
    return {
      currentVersion: CURRENT_APP_VERSION,
      currentRelease,
      latestRelease: null,
      hasUpdate: false,
      latestUrl: currentRelease.releaseUrl || GITHUB_RELEASES_URL,
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
