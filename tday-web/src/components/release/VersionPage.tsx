import { type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Github, Info, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
  WEB_VIEW_CARD_CLASS,
  WebViewSectionCard,
} from "@/components/ui/WebViewPageTemplate";
import NativePageHeader from "@/components/app/NativePageHeader";
import { nativeScreenAccentColors } from "@/components/app/nativeScreenTheme";
import {
  formatDisplayVersion,
  formatReleaseDate,
  type ReleaseMetadata,
} from "@/features/release/lib/release";
import {
  useReleaseInfo,
  type ReleaseInfo,
} from "@/features/release/query/get-release-info";

const SURFACE_CLASS = "rounded-sm border border-border/70 bg-background/50";

/** Wraps the version content in the same native screen chrome as every other app screen. */
function VersionPageShell({
  children,
  backFallbackHref,
}: {
  children: ReactNode;
  backFallbackHref?: string;
}) {
  const { t: settingsDict } = useTranslation("settings");
  const { t } = useTranslation("release");

  return (
    <div className="w-full space-y-5 pb-10">
      <NativePageHeader
        title={settingsDict("about.appVersion")}
        accentColor={nativeScreenAccentColors.settings}
        icon={Info}
        subtitle={t("subtitle")}
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
 * against the latest published release, the installed and latest versions, the
 * publish date, the changelog under "What's new in vX", and the GitHub link.
 *
 * Deliberately NOT here, because a browser tab can honour neither: Android's
 * in-app APK download/install card (its asset name, size, progress, storage
 * permission and signature-conflict states, `InAppApkUpdater.kt`) and iOS's
 * "Open Update" button, which deep-links to the App Store / TestFlight. The
 * only update action a web page can offer is "View on GitHub", which is what
 * it offers.
 */
export default function VersionPage({ backFallbackHref }: { backFallbackHref?: string }) {
  const { t } = useTranslation("release");
  const { data: releaseInfo, refetch } = useReleaseInfo();

  if (!releaseInfo) {
    return (
      <VersionPageShell backFallbackHref={backFallbackHref}>
        <Card className={WEB_VIEW_CARD_CLASS}>
          <CardContent className="flex items-center gap-3 py-8">
            <Loader2 className="h-5 w-5 animate-spin text-accent" />
            <p className="text-sm text-muted-foreground">{t("loading")}</p>
          </CardContent>
        </Card>
      </VersionPageShell>
    );
  }

  const latestVersion = formatDisplayVersion(releaseInfo.latestRelease?.version);
  const latestLabel = latestVersion ? `v${latestVersion}` : "";
  const hasUpdate = releaseInfo.hasUpdate && latestLabel !== "";
  // The release whose notes are worth reading: the newer one when there is an
  // update, otherwise the installed one — the native update/installed card
  // split, collapsed into one card because the two never appear together.
  const notesRelease = hasUpdate && releaseInfo.latestRelease
    ? releaseInfo.latestRelease
    : releaseInfo.currentRelease;
  const notesVersion = `v${formatDisplayVersion(notesRelease.version) ?? notesRelease.version}`;
  // The native update card hides the whole notes block when the changelog is
  // empty and offers no empty-message; the installed card always says so. Same
  // rule here, minus an install button that would have kept the empty card up.
  const showNotes = notesRelease.notes.length > 0 || !hasUpdate;

  return (
    <VersionPageShell backFallbackHref={backFallbackHref}>
      {releaseInfo.latestLookupFailed ? (
        <ReleaseCheckFailedCard
          onRetry={() => {
            void refetch();
          }}
        />
      ) : (
        <ReleaseStatusCard releaseInfo={releaseInfo} hasUpdate={hasUpdate} latestLabel={latestLabel} />
      )}

      {showNotes ? <ReleaseNotesCard versionLabel={notesVersion} release={notesRelease} /> : null}

      <ReleaseLinkButton releaseUrl={notesRelease.releaseUrl} />
    </VersionPageShell>
  );
}

/**
 * Replaces the status card when the latest-publication check could not run —
 * the one state the native error card covers, adapted to a page that always
 * knows its installed version. Without it a failed check renders as "Latest /
 * You're running the latest version", which is a claim the page cannot make.
 */
function ReleaseCheckFailedCard({ onRetry }: { onRetry: () => void }) {
  const { t } = useTranslation("release");

  return (
    <Card className={WEB_VIEW_CARD_CLASS}>
      <CardContent className="space-y-4 py-8">
        <p className="text-sm text-muted-foreground">{t("error")}</p>
        <Button type="button" variant="outline" onClick={onRetry}>
          {t("retry")}
        </Button>
      </CardContent>
    </Card>
  );
}

/** Summarizes whether the installed web build matches the latest published release metadata. */
function ReleaseStatusCard({
  releaseInfo,
  hasUpdate,
  latestLabel,
}: {
  releaseInfo: ReleaseInfo;
  hasUpdate: boolean;
  latestLabel: string;
}) {
  const { t } = useTranslation("release");
  const installedLabel = `v${formatDisplayVersion(releaseInfo.currentVersion) ?? releaseInfo.currentVersion}`;
  const fields: Array<{ label: string; value: string }> = [
    // Native labels the row "Installed" while an update exists and "Installed
    // Version" otherwise, because next to it the "Latest" row needs the room.
    { label: hasUpdate ? t("installed") : t("installedVersion"), value: installedLabel },
  ];
  if (hasUpdate) {
    fields.push({ label: t("latest"), value: latestLabel });
  }

  return (
    <WebViewSectionCard title={hasUpdate ? t("statusUpdateAvailable") : t("statusLatest")}>
      <div className="space-y-5">
        <p className="text-sm text-muted-foreground">
          {hasUpdate
            ? t("messageUpdateAvailable", { version: latestLabel })
            : t("messageLatest")}
        </p>
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
          <li key={note} className="flex items-start gap-3 text-sm leading-6 text-foreground">
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
