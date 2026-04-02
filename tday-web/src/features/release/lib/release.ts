export type GitHubReleaseAsset = {
  name: string;
  browserDownloadUrl: string;
  size: number;
  downloadCount: number;
};

export type GitHubRelease = {
  tagName: string;
  name: string | null;
  body: string | null;
  publishedAt: string | null;
  htmlUrl: string;
  assets: GitHubReleaseAsset[];
  version: string;
};

type RawGitHubReleaseAsset = {
  name?: string;
  browser_download_url?: string;
  size?: number;
  download_count?: number;
};

type RawGitHubRelease = {
  tag_name?: string;
  name?: string;
  body?: string;
  published_at?: string;
  html_url?: string;
  assets?: RawGitHubReleaseAsset[];
};

export const CURRENT_APP_VERSION = normalizeVersion(__APP_VERSION__) ?? "0.0.0";
export const GITHUB_RELEASES_URL = "https://github.com/ohmzi/Tday/releases";

export function normalizeVersion(raw: string | null | undefined): string | null {
  if (!raw) return null;
  const value = raw.trim();
  if (!value) return null;
  return value.replace(/^[vV]/, "");
}

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

export function formatDisplayVersion(raw: string | null | undefined): string | null {
  return normalizeVersion(raw);
}

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

export function formatReleaseFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;

  const kb = bytes / 1024;
  if (kb < 1024) return `${kb.toFixed(1)} KB`;

  const mb = kb / 1024;
  return `${mb.toFixed(1)} MB`;
}

export function parseReleaseNotes(body: string | null | undefined): string[] {
  if (!body) return [];

  return body
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .filter((line) => !line.startsWith("#"))
    .map((line) => line.replace(/^[-*•]\s+/, "").replace(/^\d+\.\s+/, "").trim())
    .filter(Boolean);
}

export function mapGitHubRelease(raw: RawGitHubRelease): GitHubRelease {
  const tagName = typeof raw.tag_name === "string" ? raw.tag_name : "";
  const htmlUrl =
    typeof raw.html_url === "string" && raw.html_url.trim()
      ? raw.html_url
      : GITHUB_RELEASES_URL;

  return {
    tagName,
    name: typeof raw.name === "string" ? raw.name : null,
    body: typeof raw.body === "string" ? raw.body : null,
    publishedAt: typeof raw.published_at === "string" ? raw.published_at : null,
    htmlUrl,
    assets: Array.isArray(raw.assets)
      ? raw.assets
          .filter(
            (asset): asset is Required<Pick<RawGitHubReleaseAsset, "name" | "browser_download_url">> &
              RawGitHubReleaseAsset =>
              typeof asset?.name === "string" &&
              typeof asset?.browser_download_url === "string",
          )
          .map((asset) => ({
            name: asset.name,
            browserDownloadUrl: asset.browser_download_url,
            size: typeof asset.size === "number" ? asset.size : 0,
            downloadCount: typeof asset.download_count === "number" ? asset.download_count : 0,
          }))
      : [],
    version: normalizeVersion(tagName) ?? tagName,
  };
}
