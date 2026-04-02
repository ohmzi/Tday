import { Github, Info, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
  WEB_VIEW_CARD_CLASS,
  WebViewPageTemplate,
  WebViewSectionCard,
} from "@/components/ui/WebViewPageTemplate";
import {
  formatDisplayVersion,
  formatReleaseDate,
  type ReleaseMetadata,
} from "@/features/release/lib/release";
import { useReleaseInfo } from "@/features/release/query/get-release-info";

const SURFACE_CLASS = "rounded-xl border border-border/70 bg-background/50";

export default function VersionPage() {
  const { data: releaseInfo } = useReleaseInfo();

  if (!releaseInfo) {
    return (
      <WebViewPageTemplate
        title="App Version"
        description="Review the deployed build and the latest release available to admins."
        icon={Info}
        backHref="/app/admin"
        backLabel="Back to admin"
      >
        <Card className={WEB_VIEW_CARD_CLASS}>
          <CardContent className="flex items-center gap-3 py-8">
            <Loader2 className="h-5 w-5 animate-spin text-accent" />
            <p className="text-sm text-muted-foreground">Loading release information…</p>
          </CardContent>
        </Card>
      </WebViewPageTemplate>
    );
  }

  return (
    <WebViewPageTemplate
      title="App Version"
      description="Review the deployed build and the latest release available to admins."
      icon={Info}
      backHref="/app/admin"
      backLabel="Back to admin"
    >
      <ReleaseStatusCard releaseInfo={releaseInfo} />

      {releaseInfo.hasUpdate && releaseInfo.latestRelease ? (
        <ReleaseDetailCard
          title="Latest release"
          release={releaseInfo.latestRelease}
          fallbackNote="No release notes are available for this release yet."
        />
      ) : null}

      <ReleaseDetailCard
        title="Current release"
        release={releaseInfo.currentRelease}
        fallbackNote="No release notes are bundled with this release yet."
      />
    </WebViewPageTemplate>
  );
}

/** Lays out the compact release-status fields in the shared grid. */
const ReleaseStatusFields = ({
  fields,
  hasUpdate,
}: {
  fields: Array<{ label: string; value: string }>;
  hasUpdate: boolean;
}) => (
  <div className={`grid min-w-0 gap-4 ${hasUpdate ? "md:grid-cols-3" : "md:grid-cols-2"}`}>
    {fields.map((field) => (
      <StatusField key={field.label} label={field.label} value={field.value} />
    ))}
  </div>
);

/** Shows the version and published date fields for a release card. */
const ReleaseMetadataFields = ({
  version,
  publishedAt,
}: {
  version: string;
  publishedAt: string | null;
}) => (
  <div className={`grid min-w-0 gap-4 ${publishedAt ? "md:grid-cols-2" : "md:grid-cols-1"}`}>
    <StatusField
      label="Version"
      value={`v${formatDisplayVersion(version) ?? version}`}
    />
    {publishedAt ? <StatusField label="Published" value={publishedAt} /> : null}
  </div>
);

/** Switches between real release notes and the empty fallback copy. */
const ReleaseNotesSection = ({
  notes,
  fallbackNote,
}: {
  notes: string[];
  fallbackNote: string;
}) => (
  <div className="space-y-2">
    <p className="text-sm font-medium text-foreground">Release Notes</p>
    {notes.length > 0 ? (
      <ReleaseNotesBlock notes={notes} />
    ) : (
      <div className={`${SURFACE_CLASS} px-4 py-4 text-sm text-muted-foreground`}>
        {fallbackNote}
      </div>
    )}
  </div>
);

/** Opens the matching GitHub release in a new tab. */
const ReleaseLinkButton = ({ releaseUrl }: { releaseUrl: string }) => (
  <Button
    type="button"
    variant="outline"
    asChild
    className="w-full gap-2 sm:w-auto"
  >
    <a href={releaseUrl} target="_blank" rel="noreferrer">
      <Github className="h-4 w-4" />
      Open Release
    </a>
  </Button>
);

/** Summarizes whether the installed web build matches the latest published release metadata. */
function ReleaseStatusCard({
  releaseInfo,
}: {
  releaseInfo: NonNullable<ReturnType<typeof useReleaseInfo>["data"]>;
}) {
  const latestVersion = formatDisplayVersion(releaseInfo.latestRelease?.version);
  const statusLabel = releaseInfo.hasUpdate ? "Update available" : "Up to date";
  const statusFields = releaseInfo.hasUpdate
    ? [
        { label: "Status", value: statusLabel },
        {
          label: "Installed Version",
          value: `v${formatDisplayVersion(releaseInfo.currentVersion) ?? releaseInfo.currentVersion}`,
        },
        {
          label: "Latest Release",
          value: latestVersion ? `v${latestVersion}` : "Unavailable",
        },
      ]
    : [
        { label: "Status", value: statusLabel },
        {
          label: "Installed Version",
          value: `v${formatDisplayVersion(releaseInfo.currentVersion) ?? releaseInfo.currentVersion}`,
        },
      ];

  return (
    <WebViewSectionCard
      title="Release Status"
      contentClassName={undefined}
    >
      <ReleaseStatusFields fields={statusFields} hasUpdate={releaseInfo.hasUpdate} />
    </WebViewSectionCard>
  );
}

/** Shows the saved or latest release metadata in the shared section-card layout. */
function ReleaseDetailCard({
  title,
  release,
  fallbackNote,
}: {
  title: string;
  release: ReleaseMetadata;
  fallbackNote: string;
}) {
  const publishedAt = formatReleaseDate(release.publishedAt);

  return (
    <WebViewSectionCard
      title={title}
      contentClassName="space-y-6"
    >
      <ReleaseMetadataFields version={release.version} publishedAt={publishedAt} />
      <ReleaseNotesSection notes={release.notes} fallbackNote={fallbackNote} />
      <ReleaseLinkButton releaseUrl={release.releaseUrl} />
    </WebViewSectionCard>
  );
}

/** Renders the short release summary bullet list inside the shared surface block. */
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

/** Renders a single label/value field in the compact admin utility layout. */
function StatusField({
  label,
  value,
}: {
  label: string;
  value: string;
}) {
  return (
    <div className="space-y-2">
      <p className="text-sm font-medium text-foreground">{label}</p>
      <div className={`${SURFACE_CLASS} flex min-h-12 items-center px-4 py-3`}>
        <p className="break-words text-sm font-medium text-foreground">{value}</p>
      </div>
    </div>
  );
}
