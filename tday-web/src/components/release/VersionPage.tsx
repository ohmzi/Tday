import { type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { CloudDownload, Github, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
  WEB_VIEW_CARD_CLASS,
  WebViewSectionCard,
} from "@/components/ui/WebViewPageTemplate";
import NativePageHeader from "@/components/app/NativePageHeader";
import { nativeScreenAccentColors } from "@/components/app/nativeScreenTheme";
import { useIsLocalMode } from "@/hooks/useAppMode";
import { cn } from "@/lib/utils";
import {
  formatDisplayVersion,
  formatReleaseDate,
  type ReleaseMetadata,
} from "@/features/release/lib/release";
import {
  useReleaseInfo,
  type ReleaseInfo,
} from "@/features/release/query/get-release-info";
import { useServerVersion } from "@/features/release/query/use-server-version";

const SURFACE_CLASS = "rounded-sm border border-border/70 bg-background/50";

/**
 * Wraps the version content in the same native screen chrome as every other app
 * screen.
 *
 * The title, the glyph and the accent are the natives': `release_title` is the
 * same words in all three clients, and both natives lead with a cloud-download
 * mark. Neither passes a subtitle — their hero block has no such slot — so this
 * one does not either.
 */
function VersionPageShell({
  children,
  backFallbackHref,
}: {
  children: ReactNode;
  backFallbackHref?: string;
}) {
  const { t: settingsDict } = useTranslation("settings");

  return (
    <div className="w-full space-y-5 pb-10">
      <NativePageHeader
        title={settingsDict("about.appVersion")}
        accentColor={nativeScreenAccentColors.settings}
        icon={CloudDownload}
        // Opened from a bookmark there is nothing to pop, and which page owns
        // this one depends on how it was reached: the admin dashboard sends
        // admins here, the Settings row sends everyone else. The caller names
        // its own parent rather than the page assuming Admin.
        backFallbackHref={backFallbackHref}
      />

      <div className="space-y-5">{children}</div>
    </div>
  );
}

/**
 * The release card every signed-in user sees at `/app/version`, and the one
 * admins see at `/app/admin/version` — the two routes render this same
 * component with a different `backFallbackHref`.
 *
 * The fields are the native release screen's fields: the status of the build
 * against the latest published release, the publish date, the server the build
 * is talking to, the installed and latest versions, the changelog under "What's
 * new in vX", and the GitHub link.
 *
 * Two things the natives have are deliberately NOT here, because a browser tab
 * can honour neither: Android's in-app APK download/install card (its asset
 * name, size, progress, storage permission and signature-conflict states,
 * `InAppApkUpdater.kt`) and iOS's "Open Update" button, which deep-links to the
 * App Store / TestFlight. The only update action a web page can offer is
 * "View on GitHub", which is what it offers — and only when a source actually
 * handed it a release URL, the way both natives gate their own browse row on a
 * real `htmlUrl`.
 */
export default function VersionPage({ backFallbackHref }: { backFallbackHref?: string }) {
  const { data: releaseInfo, isError, refetch } = useReleaseInfo();
  const serverVersion = useServerVersion();
  const isLocalMode = useIsLocalMode();
  const retry = () => {
    void refetch();
  };

  // A rejected query is the one state that has nothing to show: no installed
  // build, no comparison, no retry but this one. The natives render the same
  // card for their `releaseError`, and rendering the spinner instead would
  // leave the page turning forever.
  if (isError) {
    return (
      <VersionPageShell backFallbackHref={backFallbackHref}>
        <ReleaseCheckFailedCard onRetry={retry} />
      </VersionPageShell>
    );
  }

  if (!releaseInfo) {
    return (
      <VersionPageShell backFallbackHref={backFallbackHref}>
        <ReleaseLoadingState />
      </VersionPageShell>
    );
  }

  const latestVersion = formatDisplayVersion(releaseInfo.latestRelease?.version);
  const latestLabel = latestVersion ? `v${latestVersion}` : "";
  const hasUpdate = releaseInfo.hasUpdate && latestLabel !== "";
  // The release whose notes are worth reading: the newer one when there is an
  // update, otherwise the installed one — the native update/installed card
  // split, collapsed into one card because the two never appear together.
  // The installed build's own changelog is unreachable while an update exists
  // on BOTH natives as well: their installed card, the only place
  // `currentRelease.body` is ever shown, is not rendered at all then.
  const notesRelease = hasUpdate && releaseInfo.latestRelease
    ? releaseInfo.latestRelease
    : releaseInfo.currentRelease;
  const notesVersion = `v${formatDisplayVersion(notesRelease.version) ?? notesRelease.version}`;
  // The natives' rule, which is narrower than "no bullets": the placeholder
  // belongs to the case with no release object for the installed tag at all.
  // A release that exists with an empty or unbulleted body renders no notes
  // block and no heading — the update card passes no empty-message by design,
  // and the installed card passes one only for `currentRelease == null`.
  const showNotes =
    notesRelease.notes.length > 0 ||
    (!hasUpdate && releaseInfo.currentReleaseIsPlaceholder);
  // Native takes the newest URL it holds (`latest?.htmlUrl ?? current?.htmlUrl`)
  // and shows the row only when it has one, so a build whose every source
  // failed shows no row rather than a link to a tag nobody published.
  const releaseUrl = notesRelease.releaseUrl;

  return (
    <VersionPageShell backFallbackHref={backFallbackHref}>
      {/* A failed latest-publication check is not "up to date". The query says
          which it was, and the honest answer keeps the error copy — the
          natives only raise their error card when they have no release data at
          all, which would have this page claim the build is current without
          having looked. */}
      {releaseInfo.latestLookupFailed ? (
        <ReleaseCheckFailedCard onRetry={retry} />
      ) : (
        <ReleaseStatusCard
          releaseInfo={releaseInfo}
          hasUpdate={hasUpdate}
          latestLabel={latestLabel}
          serverVersion={serverVersion}
          showServer={!isLocalMode}
        />
      )}

      {showNotes ? <ReleaseNotesCard versionLabel={notesVersion} release={notesRelease} /> : null}

      {releaseUrl ? <ReleaseLinkButton releaseUrl={releaseUrl} /> : null}
    </VersionPageShell>
  );
}

/**
 * The natives' loading state, and nothing else: a centred spinner 48px down,
 * with no text beside it (Android 48dp `LoadingStateTopInset`, iOS
 * `.padding(.top, 48)`). The label the web used to print is the element's
 * accessible name now, so the wait is still announced without putting a
 * sentence on screen the natives do not have.
 */
function ReleaseLoadingState() {
  const { t } = useTranslation("release");

  return (
    <div
      role="status"
      aria-label={t("loading")}
      className="flex w-full justify-center pt-12"
    >
      <Loader2 aria-hidden className="h-8 w-8 animate-spin text-accent" />
    </div>
  );
}

/**
 * Stands in for the status card when the latest-publication check could not run
 * — the state the native error card covers, adapted to a page that always knows
 * its installed version. Without it a failed check renders as "Latest / You're
 * running the latest version", which is a claim the page cannot make.
 *
 * Tinted like the natives' cards (`errorContainer` at half strength on Android,
 * `colors.error.opacity(0.16)` on iOS), and it carries the same sentence and
 * the same Retry button.
 */
function ReleaseCheckFailedCard({ onRetry }: { onRetry: () => void }) {
  const { t } = useTranslation("release");

  return (
    <Card className={cn(WEB_VIEW_CARD_CLASS, "border-destructive/25 bg-destructive/5")}>
      <CardContent className="space-y-4 py-8">
        <p className="text-sm font-medium text-destructive">{t("error")}</p>
        <Button type="button" variant="outline" onClick={onRetry}>
          {t("retry")}
        </Button>
      </CardContent>
    </Card>
  );
}

/**
 * The natives' overview card: a verdict, a sentence explaining it, the publish
 * date, and the version rows. Always drawn when the release state is known, in
 * every one of its states.
 */
function ReleaseStatusCard({
  releaseInfo,
  hasUpdate,
  latestLabel,
  serverVersion,
  showServer,
}: {
  releaseInfo: ReleaseInfo;
  hasUpdate: boolean;
  latestLabel: string;
  serverVersion: string | null;
  showServer: boolean;
}) {
  const { t } = useTranslation("release");
  const { t: settingsDict } = useTranslation("settings");
  const installedLabel = `v${formatDisplayVersion(releaseInfo.currentVersion) ?? releaseInfo.currentVersion}`;
  // Newest date the build can see, from EITHER release — the natives' own
  // `latest?.publishedAt ?? current?.publishedAt`. It belongs here, above the
  // version rows and independent of the changelog: kept inside the notes card
  // it vanished whenever that card did, which an update with an empty changelog
  // hides outright.
  const publishedAt = formatReleaseDate(
    releaseInfo.latestRelease?.publishedAt ?? releaseInfo.currentRelease.publishedAt,
  );

  const fields: Array<{ label: string; value: string }> = [];
  if (showServer) {
    // Native hides this row entirely in local mode and prints "Unavailable" in
    // server mode until the probe answers — which is normal mode's only other
    // state, since a tab talking to its own origin always has a server. Their
    // "Not connected" branch describes a build with no server configured.
    fields.push({
      label: settingsDict("about.server"),
      value: serverVersion ? `v${serverVersion}` : t("serverUnavailable"),
    });
  }
  // Native labels the row "Installed" while an update exists and "Installed
  // Version" otherwise, because next to it the "Latest" row needs the room.
  fields.push({
    label: hasUpdate ? t("installed") : t("installedVersion"),
    value: installedLabel,
  });
  if (hasUpdate) {
    fields.push({ label: t("latest"), value: latestLabel });
  }

  return (
    <WebViewSectionCard title={hasUpdate ? t("statusUpdateAvailable") : t("statusLatest")}>
      <div className="space-y-5">
        <p className="text-sm text-muted-foreground">
          {hasUpdate
            ? t("messageUpdateReady", { version: latestLabel })
            : t("messageLatest")}
        </p>
        {publishedAt ? (
          <p className="text-sm text-muted-foreground">{t("published", { date: publishedAt })}</p>
        ) : null}
        <div className={`grid min-w-0 gap-4 ${fields.length > 1 ? "md:grid-cols-2" : "md:grid-cols-1"}`}>
          {fields.map((field) => (
            <StatusField key={field.label} label={field.label} value={field.value} />
          ))}
        </div>
      </div>
    </WebViewSectionCard>
  );
}

/** Shows one release's publish date and changelog under the native "What's new in vX" heading. */
function ReleaseNotesCard({
  versionLabel,
  release,
}: {
  versionLabel: string;
  release: ReleaseMetadata;
}) {
  const { t } = useTranslation("release");
  const publishedAt = formatReleaseDate(release.publishedAt);

  return (
    <WebViewSectionCard
      title={t("whatsNew", { version: versionLabel })}
      contentClassName="space-y-4"
    >
      {publishedAt ? (
        <p className="text-sm text-muted-foreground">{t("published", { date: publishedAt })}</p>
      ) : null}
      {release.notes.length > 0 ? (
        <ReleaseNotesBlock notes={release.notes} />
      ) : (
        <div className={`${SURFACE_CLASS} px-4 py-4 text-sm text-muted-foreground`}>
          {t("noNotes")}
        </div>
      )}
    </WebViewSectionCard>
  );
}

/** Opens the matching GitHub release in a new tab. */
const ReleaseLinkButton = ({ releaseUrl }: { releaseUrl: string }) => {
  const { t } = useTranslation("release");

  return (
    <Button type="button" variant="outline" asChild className="w-full gap-2 sm:w-auto">
      <a href={releaseUrl} target="_blank" rel="noreferrer">
        <Github className="h-4 w-4" />
        {t("viewOnGithub")}
      </a>
    </Button>
  );
};

/** Renders the release summary bullet list inside the shared surface block. */
function ReleaseNotesBlock({ notes }: { notes: string[] }) {
  return (
    <div className={`${SURFACE_CLASS} px-4 py-4`}>
      <ul className="space-y-4">
        {notes.map((note) => (
          <li key={note} className="flex items-start gap-3 text-sm text-foreground">
            <span className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-accent/80" />
            <span className="break-words text-muted-foreground">{note}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** Renders a single label/value field in the compact utility layout. */
function StatusField({ label, value }: { label: string; value: string }) {
  return (
    <div className="space-y-2">
      <p className="text-sm font-medium text-foreground">{label}</p>
      <div className={`${SURFACE_CLASS} flex min-h-12 items-center px-4 py-3`}>
        <p className="break-words text-sm font-medium text-foreground">{value}</p>
      </div>
    </div>
  );
}
