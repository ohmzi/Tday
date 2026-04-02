import { useEffect, useRef } from "react";
import { DownloadCloud } from "lucide-react";
import { toast } from "sonner";
import { useNavigate } from "react-router-dom";
import { useLocale } from "@/lib/navigation";
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
  const { isAuthenticated, user } = useAuth();
  const locale = useLocale();
  const navigate = useNavigate();
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

    toast.custom(
      (id) => (
        <button
          type="button"
          onClick={() => {
            toast.dismiss(id);
            navigate(`/${locale}/app/admin/version`);
          }}
          className="flex w-[min(calc(100vw-2rem),24rem)] items-start gap-3 rounded-2xl border border-border/80 bg-card/95 p-4 text-left shadow-[0_20px_45px_-24px_hsl(var(--shadow)/0.45)] backdrop-blur-2xl transition hover:border-accent/35"
        >
          <span className="mt-0.5 rounded-2xl bg-accent/12 p-2.5 text-accent">
            <DownloadCloud className="h-4 w-4" />
          </span>
          <span className="min-w-0 flex-1">
            <span className="block text-sm font-semibold text-foreground">
              Update available
            </span>
            <span className="mt-1 block text-sm leading-5 text-muted-foreground">
              Version {latestVersion}
            </span>
          </span>
        </button>
      ),
      {
        duration: 10000,
      },
    );
  }, [isAdmin, locale, navigate, releaseInfoQuery.data]);

  return null;
}
