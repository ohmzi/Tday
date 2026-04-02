export type ReleaseMetadata = {
  version: string;
  publishedAt: string | null;
  notes: string[];
  releaseUrl: string;
  compareUrl: string | null;
};

const RELEASE_STORAGE_KEY = "tday.release.current.v1";

export const CURRENT_APP_VERSION = normalizeVersion(__APP_VERSION__) ?? "0.0.0";
export const GITHUB_RELEASES_URL = "https://github.com/ohmzi/Tday/releases";
export const GITHUB_RELEASES_API_URL = "https://api.github.com/repos/ohmzi/Tday/releases";
export const CURRENT_RELEASE_PATH = "/release/current-release.json";
export const LATEST_RELEASE_METADATA_URL =
  "https://raw.githubusercontent.com/ohmzi/Tday/master/tday-web/public/release/latest-changes.json";

/** Normalizes a version string by trimming it and removing a leading `v`. */
export function normalizeVersion(raw: string | null | undefined): string | null {
  if (!raw) return null;
  const value = raw.trim();
  if (!value) return null;
  return value.replace(/^[vV]/, "");
}

/** Splits a normalized version into comparable numeric parts. */
function parseVersionParts(raw: string | null | undefined): number[] | null {
  const normalized = normalizeVersion(raw);
  if (!normalized) return null;
  const parts = normalized.split(".");
  if (parts.length < 3 || parts.length > 4) return null;

  const numericParts = parts.map((part) => Number.parseInt(part, 10));
  if (numericParts.some((part) => !Number.isFinite(part) || part < 0)) {
    return null;
  }

  if (numericParts.length === 3) {
    numericParts.push(0);
  }

  return numericParts;
}

/** Compares two semantic versions and returns the numeric difference at the first changed part. */
export function compareVersions(
  left: string | null | undefined,
  right: string | null | undefined,
): number | null {
  const leftParts = parseVersionParts(left);
  const rightParts = parseVersionParts(right);

  if (!leftParts || !rightParts) {
    return null;
  }

  for (let index = 0; index < 4; index += 1) {
    const diff = (leftParts[index] ?? 0) - (rightParts[index] ?? 0);
    if (diff !== 0) return diff;
  }

  return 0;
}

/** Formats a version for display without altering its numeric meaning. */
export function formatDisplayVersion(raw: string | null | undefined): string | null {
  return normalizeVersion(raw);
}

/** Formats a release date while falling back to the raw string for unknown formats. */
export function formatReleaseDate(raw: string | null | undefined): string | null {
  if (!raw) return null;

  const date = new Date(raw);
  if (Number.isNaN(date.getTime())) {
    return raw;
  }

  return new Intl.DateTimeFormat(undefined, {
    month: "long",
    day: "numeric",
    year: "numeric",
  }).format(date);
}

/** Narrows unknown JSON-like values to plain object records. */
function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

/** Normalizes stored release notes and caps them to the top three highlights. */
function normalizeNotes(raw: unknown): string[] {
  if (!Array.isArray(raw)) return [];

  return raw
    .map((note) => (typeof note === "string" ? note.trim() : ""))
    .filter(Boolean)
    .slice(0, 3);
}

/** Extracts the top changelog bullets from a GitHub release body. */
export function parseGitHubReleaseNotes(body: string | null | undefined): string[] {
  if (!body) return [];

  return body
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line.startsWith("* ") || line.startsWith("- "))
    .map((line) => line.replace(/^[*-]\s+/, "").trim())
    .filter(Boolean)
    .slice(0, 3);
}

/** Builds a safe fallback release payload for offline or first-load states. */
export function createFallbackReleaseMetadata(version = CURRENT_APP_VERSION): ReleaseMetadata {
  const normalizedVersion = normalizeVersion(version) ?? CURRENT_APP_VERSION;

  return {
    version: normalizedVersion,
    publishedAt: null,
    notes: [],
    releaseUrl: `${GITHUB_RELEASES_URL}/tag/v${normalizedVersion}`,
    compareUrl: null,
  };
}

/** Parses a GitHub release API payload into the shared release metadata shape. */
export function parseGitHubReleaseMetadata(raw: unknown): ReleaseMetadata | null {
  if (!isRecord(raw)) return null;

  const version = normalizeVersion(
    typeof raw.tag_name === "string" ? raw.tag_name : null,
  );
  if (!version) return null;

  const releaseUrl =
    typeof raw.html_url === "string" && raw.html_url.trim()
      ? raw.html_url.trim()
      : `${GITHUB_RELEASES_URL}/tag/v${version}`;

  return {
    version,
    publishedAt:
      typeof raw.published_at === "string" && raw.published_at.trim()
        ? raw.published_at.trim()
        : null,
    notes: parseGitHubReleaseNotes(
      typeof raw.body === "string" ? raw.body : null,
    ),
    releaseUrl,
    compareUrl: null,
  };
}

/** Parses arbitrary JSON into a validated release metadata object. */
export function parseReleaseMetadata(raw: unknown): ReleaseMetadata | null {
  if (!isRecord(raw)) return null;

  const version = normalizeVersion(
    typeof raw.version === "string" ? raw.version : null,
  );
  if (!version) return null;

  const releaseUrl =
    typeof raw.releaseUrl === "string" && raw.releaseUrl.trim()
      ? raw.releaseUrl.trim()
      : `${GITHUB_RELEASES_URL}/tag/v${version}`;

  const compareUrl =
    typeof raw.compareUrl === "string" && raw.compareUrl.trim()
      ? raw.compareUrl.trim()
      : null;

  return {
    version,
    publishedAt:
      typeof raw.publishedAt === "string" && raw.publishedAt.trim()
        ? raw.publishedAt.trim()
        : null,
    notes: normalizeNotes(raw.notes),
    releaseUrl,
    compareUrl,
  };
}

/** Fetches release metadata from a JSON endpoint and validates its shape. */
export async function fetchReleaseMetadata(url: string): Promise<ReleaseMetadata> {
  const response = await fetch(url, {
    method: "GET",
    cache: "no-store",
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    throw new Error(`Release metadata lookup failed (${response.status})`);
  }

  const json = (await response.json()) as unknown;
  const metadata = parseReleaseMetadata(json);
  if (!metadata) {
    throw new Error("Release metadata is invalid");
  }

  return metadata;
}

/** Fetches a GitHub release by tag and normalizes it into the shared metadata shape. */
export async function fetchGitHubReleaseMetadataByTag(
  version: string,
): Promise<ReleaseMetadata> {
  const normalizedVersion = normalizeVersion(version);
  if (!normalizedVersion) {
    throw new Error("Release version is invalid");
  }

  const response = await fetch(
    `${GITHUB_RELEASES_API_URL}/tags/v${normalizedVersion}`,
    {
      method: "GET",
      cache: "no-store",
      headers: {
        Accept: "application/vnd.github+json",
      },
    },
  );

  if (!response.ok) {
    throw new Error(`GitHub release lookup failed (${response.status})`);
  }

  const json = (await response.json()) as unknown;
  const metadata = parseGitHubReleaseMetadata(json);
  if (!metadata) {
    throw new Error("GitHub release metadata is invalid");
  }

  return metadata;
}

/** Reads the locally stored release snapshot that matches the installed app version. */
export function readStoredCurrentRelease(version = CURRENT_APP_VERSION): ReleaseMetadata | null {
  if (typeof window === "undefined") return null;

  try {
    const raw = window.localStorage.getItem(RELEASE_STORAGE_KEY);
    if (!raw) return null;

    const parsed = parseReleaseMetadata(JSON.parse(raw));
    if (!parsed) return null;

    return normalizeVersion(parsed.version) === normalizeVersion(version) ? parsed : null;
  } catch {
    return null;
  }
}

/** Persists the installed release snapshot for offline display on later visits. */
export function storeCurrentRelease(release: ReleaseMetadata) {
  if (typeof window === "undefined") return;

  try {
    window.localStorage.setItem(RELEASE_STORAGE_KEY, JSON.stringify(release));
  } catch {
    // Ignore storage write failures in restricted browser contexts.
  }
}
