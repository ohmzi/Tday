import {
  ArrowUpRight,
  CloudDownload,
  ExternalLink,
  Github,
  Info,
  Loader2,
  Sparkles,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import MobileSearchHeader from "@/components/ui/MobileSearchHeader";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Link } from "@/lib/navigation";
import {
  CURRENT_APP_VERSION,
  formatDisplayVersion,
  formatReleaseDate,
  formatReleaseFileSize,
  GITHUB_RELEASES_URL,
  parseReleaseNotes,
  type GitHubRelease,
} from "@/features/release/lib/release";
import { useReleaseInfo } from "@/features/release/query/get-release-info";

const PAGE_CARD_CLASS = "rounded-2xl border-border/70 bg-card/95";
const SURFACE_CLASS = "rounded-xl border border-border/70 bg-muted/20";

export default function VersionPage() {
  const releaseInfoQuery = useReleaseInfo();
  const releaseInfo = releaseInfoQuery.data;

  if (!releaseInfo) {
    return (
      <div className="w-full space-y-5 pb-10">
        <div className="lg:hidden">
          <MobileSearchHeader />
        </div>
        <VersionPageHeader />
        <Card className={PAGE_CARD_CLASS}>
          <CardContent className="flex items-center gap-3 py-8">
            <Loader2 className="h-5 w-5 animate-spin text-accent" />
            <p className="text-sm text-muted-foreground">Checking the latest release…</p>
          </CardContent>
        </Card>
      </div>
    );
  }

  const browseUrl =
    releaseInfo.latestRelease?.htmlUrl ??
    releaseInfo.currentRelease?.htmlUrl ??
    releaseInfo.latestUrl ??
    GITHUB_RELEASES_URL;

  return (
    <div className="w-full space-y-5 pb-10">
      <div className="lg:hidden">
        <MobileSearchHeader />
      </div>
      <VersionPageHeader />

      <ReleaseSummaryCard releaseInfo={releaseInfo} />

      {releaseInfo.error ? (
        <Card className="rounded-2xl border-destructive/20 bg-card/95">
          <CardHeader className="space-y-2">
            <CardTitle className="text-lg">Couldn’t reach GitHub right now</CardTitle>
            <CardDescription>{releaseInfo.error}</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-3">
            <Button type="button" onClick={() => releaseInfoQuery.refetch()}>
              Try again
            </Button>
            <Button type="button" variant="outline" asChild>
              <a href={browseUrl} target="_blank" rel="noreferrer">
                View releases on GitHub
              </a>
            </Button>
          </CardContent>
        </Card>
      ) : (
        <>
          <ReleaseDetailCard
            title="Installed Version"
            description={
              releaseInfo.currentRelease
                ? "This is the version currently running in your browser."
                : "This is the version currently running in your browser. GitHub notes are not available for this tag yet."
            }
            release={releaseInfo.currentRelease}
            versionLabel={CURRENT_APP_VERSION}
            tone="default"
            fallbackNote="No release notes are available for this version yet."
          />

          {releaseInfo.hasUpdate && releaseInfo.latestRelease ? (
            <ReleaseDetailCard
              title="Update Available"
              description="A newer GitHub release is available for T'Day."
              release={releaseInfo.latestRelease}
              versionLabel={releaseInfo.latestRelease.version}
              tone="accent"
            />
          ) : null}
        </>
      )}

      <Card className={PAGE_CARD_CLASS}>
        <CardContent className="flex flex-col gap-3 py-5 sm:flex-row sm:items-center sm:justify-between">
          <div className="space-y-1">
            <p className="text-sm font-medium text-foreground">Need the full GitHub release?</p>
            <p className="text-sm text-muted-foreground">
              Open the release thread for full notes, assets, and download links.
            </p>
          </div>
          <Button type="button" variant="outline" asChild className="gap-2">
            <a href={browseUrl} target="_blank" rel="noreferrer">
              <Github className="h-4 w-4" />
              View on GitHub
              <ExternalLink className="h-4 w-4" />
            </a>
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}

function VersionPageHeader() {
  return (
    <header className="mt-8 space-y-3 sm:mt-10 lg:mt-0">
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-semibold tracking-tight text-foreground sm:text-3xl">
          App Version
        </h1>
        <VersionBadge value={CURRENT_APP_VERSION} tone="default" />
      </div>
      <p className="max-w-2xl text-sm leading-6 text-muted-foreground">
        Check the build you have open, compare it with the latest GitHub release, and jump out to
        the full release page when you need the complete details.
      </p>

      <Link
        href="/app/settings"
        className="inline-flex items-center gap-2 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
      >
        Back to settings
        <ArrowUpRight className="h-4 w-4" />
      </Link>
    </header>
  );
}

function ReleaseSummaryCard({
  releaseInfo,
}: {
  releaseInfo: NonNullable<ReturnType<typeof useReleaseInfo>["data"]>;
}) {
  const latestVersion = formatDisplayVersion(releaseInfo.latestRelease?.version);
  const checkedDate = formatReleaseDate(releaseInfo.checkedAt);

  return (
    <Card className={PAGE_CARD_CLASS}>
      <CardContent className="space-y-6 py-6">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div className="space-y-2">
            <div className="inline-flex items-center gap-2 text-sm font-medium text-muted-foreground">
              {releaseInfo.hasUpdate ? (
                <Sparkles className="h-4 w-4 text-accent" />
              ) : (
                <Info className="h-4 w-4 text-muted-foreground" />
              )}
              Status
            </div>
            <h2 className="text-2xl font-semibold tracking-tight text-foreground">
              {releaseInfo.hasUpdate ? "Update Available" : "Latest"}
            </h2>
            <p className="max-w-xl text-sm leading-6 text-muted-foreground">
              {releaseInfo.hasUpdate && latestVersion
                ? `Version ${latestVersion} is newer than the build you have open right now.`
                : "You’re already running the latest published web version."}
            </p>
          </div>

          {latestVersion ? (
            <VersionBadge value={latestVersion} tone={releaseInfo.hasUpdate ? "accent" : "default"} />
          ) : null}
        </div>

        <div className="grid gap-3 sm:grid-cols-2">
          <VersionStat
            label="Installed"
            value={`v${formatDisplayVersion(releaseInfo.currentVersion) ?? releaseInfo.currentVersion}`}
          />
          <VersionStat
            label="Latest"
            value={
              latestVersion
                ? `v${latestVersion}`
                : "Unavailable"
            }
          />
        </div>

        {checkedDate ? (
          <p className="text-xs font-medium uppercase tracking-[0.16em] text-muted-foreground/80">
            Checked {checkedDate}
          </p>
        ) : null}
      </CardContent>
    </Card>
  );
}

function ReleaseDetailCard({
  title,
  description,
  release,
  versionLabel,
  tone,
  fallbackNote,
}: {
  title: string;
  description: string;
  release: GitHubRelease | null;
  versionLabel: string;
  tone: "default" | "accent";
  fallbackNote?: string;
}) {
  const notes = parseReleaseNotes(release?.body);
  const publishedAt = formatReleaseDate(release?.publishedAt);

  return (
    <Card
      className={
        tone === "accent"
          ? "rounded-2xl border-accent/25 bg-card/95"
          : PAGE_CARD_CLASS
      }
    >
      <CardHeader className="space-y-2">
        <div className="flex flex-wrap items-center gap-3">
          <CardTitle className="text-xl">{title}</CardTitle>
          <VersionBadge value={versionLabel} tone={tone} />
        </div>
        <CardDescription>{description}</CardDescription>
      </CardHeader>

      <CardContent className="space-y-5">
        {publishedAt ? (
          <p className="text-sm text-muted-foreground">Published {publishedAt}</p>
        ) : null}

        {notes.length > 0 ? (
          <ReleaseNotesBlock title={`What’s new in v${formatDisplayVersion(versionLabel) ?? versionLabel}`} notes={notes} />
        ) : (
          <div className={`${SURFACE_CLASS} px-4 py-4 text-sm text-muted-foreground`}>
            {fallbackNote ?? "No release notes are available for this release yet."}
          </div>
        )}

        {tone === "accent" && release?.assets.length ? (
          <div className="space-y-3">
            <h3 className="text-sm font-semibold text-foreground">Assets</h3>
            <div className="space-y-2">
              {release.assets.map((asset) => (
                <a
                  key={asset.name}
                  href={asset.browserDownloadUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="flex items-center justify-between gap-4 rounded-xl border border-border/70 bg-muted/20 px-4 py-4 transition hover:border-accent/35 hover:bg-muted/30"
                >
                  <span className="min-w-0">
                    <span className="block truncate text-sm font-medium text-foreground">
                      {asset.name}
                    </span>
                    <span className="mt-1 block text-sm text-muted-foreground">
                      {formatReleaseFileSize(asset.size)}
                      {asset.downloadCount > 0 ? ` · ${asset.downloadCount} downloads` : ""}
                    </span>
                  </span>
                  <CloudDownload className="h-4 w-4 shrink-0 text-muted-foreground" />
                </a>
              ))}
            </div>
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}

function ReleaseNotesBlock({
  title,
  notes,
}: {
  title: string;
  notes: string[];
}) {
  return (
    <div className="space-y-3">
      <h3 className="text-sm font-semibold text-foreground">{title}</h3>
      <div className={`${SURFACE_CLASS} px-4 py-4`}>
        <ul className="space-y-3">
          {notes.map((note) => (
            <li key={note} className="flex items-start gap-3 text-sm leading-6 text-foreground">
              <span className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-accent/80" />
              <span>{note}</span>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}

function VersionStat({
  label,
  value,
}: {
  label: string;
  value: string;
}) {
  return (
    <div className={`${SURFACE_CLASS} px-4 py-4`}>
      <p className="text-xs font-medium uppercase tracking-[0.16em] text-muted-foreground/80">
        {label}
      </p>
      <p className="mt-2 text-base font-semibold text-foreground">{value}</p>
    </div>
  );
}

function VersionBadge({
  value,
  tone,
}: {
  value: string;
  tone: "default" | "accent";
}) {
  return (
    <span
      className={
        tone === "accent"
          ? "inline-flex items-center rounded-full bg-accent/12 px-3 py-1 text-sm font-semibold text-foreground"
          : "inline-flex items-center rounded-full bg-muted/70 px-3 py-1 text-sm font-semibold text-foreground"
      }
    >
      v{formatDisplayVersion(value) ?? value}
    </span>
  );
}
