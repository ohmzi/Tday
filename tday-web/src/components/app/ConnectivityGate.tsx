import { useEffect } from "react";
import { toast as sonnerToast } from "sonner";

// A single, stable id so the offline notice can be replaced/dismissed rather
// than stacking up if connectivity flaps.
const OFFLINE_TOAST_ID = "connectivity-offline";

/**
 * App-wide connectivity watcher. Mirrors the native apps: a toast when the
 * connection drops and a toast when it returns. Renders nothing.
 */
export default function ConnectivityGate() {
  useEffect(() => {
    const showOffline = () => {
      sonnerToast.error("You're offline", {
        id: OFFLINE_TOAST_ID,
        description: "Changes will sync when your connection returns.",
        duration: Infinity,
      });
    };

    const showOnline = () => {
      sonnerToast.dismiss(OFFLINE_TOAST_ID);
      sonnerToast.success("Back online", {
        description: "Syncing your latest changes…",
        duration: 3000,
      });
    };

    window.addEventListener("offline", showOffline);
    window.addEventListener("online", showOnline);

    // Reflect the state the app launched in (e.g. opened while already offline).
    if (typeof navigator !== "undefined" && navigator.onLine === false) {
      showOffline();
    }

    return () => {
      window.removeEventListener("offline", showOffline);
      window.removeEventListener("online", showOnline);
    };
  }, []);

  return null;
}
