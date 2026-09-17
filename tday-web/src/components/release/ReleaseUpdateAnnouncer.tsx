import { useEffect, useRef } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";
import ClickableToast from "@/hooks/ClickableToast";
import { useRouter } from "@/lib/navigation";
import { useAuth } from "@/providers/AuthProvider";
import { useReleaseInfo } from "@/features/release/query/get-release-info";
import { formatDisplayVersion } from "@/features/release/lib/release";

const buildToastSessionKey = (version: string) => `tday.release-toast.${version}`;

function hasSessionFlag(key: string): boolean {
  try {
    return window.sessionStorage.getItem(key) === "1";
  } catch {
    return false;
  }
}

function setSessionFlag(key: string) {
  try {
    window.sessionStorage.setItem(key, "1");
  } catch {
    // Ignore storage failures so the release toast still works.
  }
}

export default function ReleaseUpdateAnnouncer() {
  const { t: appDict } = useTranslation("app");
  const { isAuthenticated, user } = useAuth();
  const router = useRouter();
  const lastAnnouncedVersionRef = useRef<string | null>(null);
  const isAdmin = isAuthenticated && user?.role === "ADMIN" && user?.approvalStatus === "APPROVED";
  const releaseInfoQuery = useReleaseInfo({ enabled: isAdmin });

  useEffect(() => {
    if (!isAdmin) return;

    const releaseInfo = releaseInfoQuery.data;
    const latestVersion = formatDisplayVersion(releaseInfo?.latestRelease?.version);

    if (!releaseInfo?.hasUpdate || !latestVersion) return;
    if (lastAnnouncedVersionRef.current === latestVersion) return;
    if (window.location.pathname.includes("/app/admin/version")) return;

    const sessionKey = buildToastSessionKey(latestVersion);
    if (hasSessionFlag(sessionKey)) {
      lastAnnouncedVersionRef.current = latestVersion;
      return;
    }

    lastAnnouncedVersionRef.current = latestVersion;
    setSessionFlag(sessionKey);

    // Content only, like every other toast: the surrounding sonner <li> draws
    // the shared frosted pill. This used to hand-roll its own bordered,
    // backdrop-blurred card (a `rounded-[24px] bg-popover/92` button inside the
    // rounded-full pill), which rendered as a box inside a box — larger,
    // lighter, off the shared radius, and left-aligned. A custom toast is
    // `data-styled="false"`, so sonner's own surface rules skip it; see
    // [ClickableToast] and `src/components/ui/sonner.tsx`. Distinct from
    // VersionGate's stale-build prompt: both are id-less, so sonner keeps them
    // separate toasts, and this one is timed (the prompt is pinned).
    toast.custom(
      (id) => (
        <ClickableToast
          title={appDict("releaseUpdateTitle")}
          description={appDict("releaseUpdateVersion", { version: latestVersion })}
          onClick={() => {
            toast.dismiss(id);
            router.push("/app/admin/version");
          }}
        />
      ),
      {
        duration: 10000,
      },
    );
  }, [appDict, isAdmin, router, releaseInfoQuery.data]);

  return null;
}
