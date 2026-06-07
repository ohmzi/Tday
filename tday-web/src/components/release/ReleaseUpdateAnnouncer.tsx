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
          className="flex w-[min(calc(100vw-2rem),24rem)] items-center gap-3 rounded-[24px] border border-border bg-popover/92 px-4 py-3.5 text-left text-popover-foreground backdrop-blur-xl shadow-[0_10px_30px_-12px_hsl(var(--shadow)/0.45)] transition-colors hover:bg-popover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-foreground/20"
        >
          <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-[#E06F66]/15 text-[#E06F66]">
            <DownloadCloud className="size-[18px]" />
          </span>
          <span className="block min-w-0 flex-1">
            <span className="block font-extrabold leading-tight">
              Update available
            </span>
            <span className="mt-0.5 block text-[13px] font-medium leading-snug text-current/75">
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
