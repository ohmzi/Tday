import { useQuery } from "@tanstack/react-query";
import {
  compareVersions,
  CURRENT_APP_VERSION,
  GITHUB_RELEASES_URL,
  mapGitHubRelease,
  normalizeVersion,
  type GitHubRelease,
} from "@/features/release/lib/release";

const GITHUB_RELEASES_API_URL = "https://api.github.com/repos/ohmzi/Tday/releases";

export type ReleaseInfo = {
  currentVersion: string;
  currentRelease: GitHubRelease | null;
  latestRelease: GitHubRelease | null;
  hasUpdate: boolean;
  checkedAt: string;
  error: string | null;
  latestUrl: string;
};

async function fetchGitHubRelease(url: string): Promise<GitHubRelease> {
  const response = await fetch(url, {
    method: "GET",
    headers: {
      Accept: "application/vnd.github+json",
    },
    cache: "no-store",
  });

  if (!response.ok) {
    throw new Error(`GitHub release lookup failed (${response.status})`);
  }

  const json = (await response.json()) as Record<string, unknown>;
  return mapGitHubRelease(json);
}

async function fetchReleaseByTag(tag: string): Promise<GitHubRelease | null> {
  try {
    return await fetchGitHubRelease(`${GITHUB_RELEASES_API_URL}/tags/${tag}`);
  } catch {
    return null;
  }
}

async function getReleaseInfo(): Promise<ReleaseInfo> {
  const checkedAt = new Date().toISOString();

  try {
    const latestRelease = await fetchGitHubRelease(`${GITHUB_RELEASES_API_URL}/latest`);
    const currentVersion = CURRENT_APP_VERSION;
    const normalizedLatestVersion = normalizeVersion(latestRelease.tagName);
    const comparison = compareVersions(normalizedLatestVersion, currentVersion);
    const hasUpdate = typeof comparison === "number" ? comparison > 0 : false;

    const currentRelease =
      normalizedLatestVersion === currentVersion
        ? latestRelease
        : (await fetchReleaseByTag(`v${currentVersion}`)) ??
          (await fetchReleaseByTag(currentVersion));

    return {
      currentVersion,
      currentRelease,
      latestRelease,
      hasUpdate,
      checkedAt,
      error: null,
      latestUrl: latestRelease.htmlUrl || GITHUB_RELEASES_URL,
    };
  } catch (error) {
    return {
      currentVersion: CURRENT_APP_VERSION,
      currentRelease: null,
      latestRelease: null,
      hasUpdate: false,
      checkedAt,
      error: error instanceof Error ? error.message : "Could not fetch release information.",
      latestUrl: GITHUB_RELEASES_URL,
    };
  }
}

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
