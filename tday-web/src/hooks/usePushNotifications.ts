import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api-client";
import { isPushSupported, urlBase64ToUint8Array } from "@/lib/push/vapid";

const PUSH_ENABLED_KEY = "tday.push-enabled";

type PushState = {
  isSupported: boolean;
  permission: NotificationPermission | "default";
  isSubscribed: boolean;
  isLoading: boolean;
};

async function fetchVapidKey(): Promise<string | null> {
  try {
    const data = await api.GET({ url: "/api/notifications/vapid-public-key" });
    return (data as { publicKey?: string })?.publicKey ?? null;
  } catch {
    return null;
  }
}

async function getExistingSubscription(): Promise<PushSubscription | null> {
  try {
    const reg = await navigator.serviceWorker.getRegistration("/");
    return (await reg?.pushManager.getSubscription()) ?? null;
  } catch {
    return null;
  }
}

export function usePushNotifications() {
  const [state, setState] = useState<PushState>({
    isSupported: isPushSupported(),
    permission: typeof Notification !== "undefined" ? Notification.permission : "default",
    isSubscribed: false,
    isLoading: true,
  });

  // Reconcile state on mount
  useEffect(() => {
    if (!state.isSupported) {
      setState((s) => ({ ...s, isLoading: false }));
      return;
    }

    let cancelled = false;
    (async () => {
      const sub = await getExistingSubscription();
      const localFlag = localStorage.getItem(PUSH_ENABLED_KEY) === "1";
      const subscribed = sub !== null && localFlag;

      // If localStorage says enabled but subscription is gone, clean up
      if (localFlag && !sub) {
        localStorage.removeItem(PUSH_ENABLED_KEY);
      }

      if (!cancelled) {
        setState((s) => ({
          ...s,
          isSubscribed: subscribed,
          permission: Notification.permission,
          isLoading: false,
        }));
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [state.isSupported]);

  const subscribe = useCallback(async () => {
    setState((s) => ({ ...s, isLoading: true }));
    try {
      const vapidKey = await fetchVapidKey();
      if (!vapidKey) throw new Error("VAPID key not available");

      const permission = await Notification.requestPermission();
      if (permission !== "granted") {
        setState((s) => ({ ...s, permission, isLoading: false }));
        return;
      }

      const reg = await navigator.serviceWorker.ready;
      const sub = await reg.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(vapidKey),
      });

      const json = sub.toJSON();
      await api.POST({
        url: "/api/notifications/subscribe",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          endpoint: json.endpoint,
          p256dh: json.keys?.p256dh ?? "",
          auth: json.keys?.auth ?? "",
        }),
      });

      localStorage.setItem(PUSH_ENABLED_KEY, "1");
      setState((s) => ({
        ...s,
        isSubscribed: true,
        permission: "granted",
        isLoading: false,
      }));
    } catch (err) {
      setState((s) => ({ ...s, isLoading: false }));
      throw err;
    }
  }, []);

  const unsubscribe = useCallback(async () => {
    setState((s) => ({ ...s, isLoading: true }));
    try {
      const sub = await getExistingSubscription();
      if (sub) {
        await api.DELETE({
          url: "/api/notifications/unsubscribe",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ endpoint: sub.endpoint }),
        });
        await sub.unsubscribe();
      }
      localStorage.removeItem(PUSH_ENABLED_KEY);
      setState((s) => ({ ...s, isSubscribed: false, isLoading: false }));
    } catch (err) {
      setState((s) => ({ ...s, isLoading: false }));
      throw err;
    }
  }, []);

  return { ...state, subscribe, unsubscribe };
}
