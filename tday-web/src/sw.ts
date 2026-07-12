/// <reference lib="webworker" />
import { precacheAndRoute, cleanupOutdatedCaches } from "workbox-precaching";
import { registerRoute, NavigationRoute } from "workbox-routing";
import { NetworkFirst, NetworkOnly } from "workbox-strategies";
import { CacheableResponsePlugin } from "workbox-cacheable-response";
import { ExpirationPlugin } from "workbox-expiration";
import { createHandlerBoundToURL } from "workbox-precaching";
import type { RouteHandlerCallback } from "workbox-core";

declare const self: ServiceWorkerGlobalScope;

/** TS DOM types omit `actions` from NotificationOptions; extend here. */
interface NotificationOptionsWithActions extends NotificationOptions {
  actions?: Array<{ action: string; title: string; icon?: string }>;
}

/* ── Lifecycle ── */
self.addEventListener("install", () => self.skipWaiting());
self.addEventListener("activate", (event) => event.waitUntil(self.clients.claim()));

/* Let the page drive activation / a hard cache reset after a deploy. */
self.addEventListener("message", (event) => {
  const data = event.data as { type?: string } | undefined;
  if (data?.type === "SKIP_WAITING") {
    self.skipWaiting();
  } else if (data?.type === "CLEAR_CACHES") {
    event.waitUntil(
      caches.keys().then((keys) => Promise.all(keys.map((k) => caches.delete(k)))),
    );
  }
});

/* ── Precaching ── */
cleanupOutdatedCaches();
precacheAndRoute(self.__WB_MANIFEST);

/* ── Navigation: network-first so a new deploy's index.html (with current ── */
/* ── chunk hashes) is fetched while online; fall back to the precached    ── */
/* ── shell only when the network fails/times out (offline).               ── */
const offlineShellHandler = createHandlerBoundToURL("/index.html");
const networkFirstNavigation = new NetworkFirst({
  cacheName: "tday-navigation",
  networkTimeoutSeconds: 4,
  plugins: [new CacheableResponsePlugin({ statuses: [0, 200] })],
});

const navigationHandler: RouteHandlerCallback = async (options) => {
  try {
    const response = await networkFirstNavigation.handle(options);
    if (response) return response;
  } catch {
    /* offline / network error — fall back to the precached shell */
  }
  return offlineShellHandler(options);
};

registerRoute(
  new NavigationRoute(navigationHandler, {
    denylist: [/^\/api/, /^\/ws/, /^\/.well-known/],
  }),
);

/* The build-version probe must always hit the origin, never a cache. */
registerRoute(/^\/version\.json/, new NetworkOnly());

/* ── Runtime API caching ── */
/* Network-first (not stale-while-revalidate): after a mutation the app           */
/* refetches these lists, and SWR would hand back the pre-mutation cache first —   */
/* clobbering the optimistic update so a newly added task/floater appears to       */
/* "not update right away" on the home and floater screens. Network-first returns  */
/* fresh data online and only falls back to the cache when the network fails       */
/* (offline), keeping the PWA usable offline.                                      */
const apiCacheConfig = {
  cacheName: "tday-api-cache",
  networkTimeoutSeconds: 4,
  plugins: [
    new CacheableResponsePlugin({ statuses: [0, 200] }),
    new ExpirationPlugin({ maxEntries: 100, maxAgeSeconds: 60 * 60 }),
  ],
};

registerRoute(
  ({ url }) =>
    url.pathname.startsWith("/api/todo") ||
    url.pathname.startsWith("/api/floater") ||
    url.pathname.startsWith("/api/floater-list"),
  new NetworkFirst(apiCacheConfig),
);

/* ── Push notifications ── */
interface PushPayload {
  title?: string;
  body?: string;
  url?: string;
  icon?: string;
  badgeCount?: number;
  todoId?: string;
}

self.addEventListener("push", (event) => {
  if (!event.data) return;

  let payload: PushPayload;
  try {
    payload = event.data.json() as PushPayload;
  } catch {
    payload = { title: "T'Day", body: event.data.text() };
  }

  const title = payload.title ?? "T'Day";
  const options: NotificationOptionsWithActions = {
    body: payload.body ?? "",
    icon: "/icon-192.png",
    badge: "/icon-192.png",
    data: { url: payload.url ?? "/", todoId: payload.todoId },
    actions: [
      { action: "complete", title: "Complete" },
      { action: "snooze", title: "Snooze 1h" },
    ],
  };

  const tasks: Promise<unknown>[] = [
    self.registration.showNotification(title, options),
  ];

  // Update app badge count if supported
  if (payload.badgeCount != null && "setAppBadge" in navigator) {
    tasks.push(
      payload.badgeCount > 0
        ? navigator.setAppBadge(payload.badgeCount)
        : navigator.clearAppBadge(),
    );
  }

  event.waitUntil(Promise.all(tasks));
});

function focusOrOpenApp(url: string): Promise<unknown> {
  return self.clients
    .matchAll({ type: "window", includeUncontrolled: true })
    .then((clientList) => {
      for (const client of clientList) {
        if (new URL(client.url).pathname === url && "focus" in client) {
          return client.focus();
        }
      }
      return self.clients.openWindow(url);
    });
}

self.addEventListener("notificationclick", (event) => {
  event.notification.close();

  const action = event.action;
  const data = event.notification.data as
    | { url?: string; todoId?: string }
    | undefined;
  const baseUrl = data?.url ?? "/";
  const todoId = data?.todoId;

  // "Complete" now finishes the task in place via the same endpoint the app
  // uses (PATCH /api/todo/complete) instead of merely opening the task. The
  // session cookie rides along with credentials: "include". If the request
  // fails (offline, expired session), fall back to opening the app so the user
  // can still act on it.
  if (action === "complete" && todoId) {
    event.waitUntil(
      fetch("/api/todo/complete", {
        method: "PATCH",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ id: todoId }),
      })
        .then((res) => {
          if (!res.ok) throw new Error(`complete failed: ${res.status}`);
        })
        .catch(() => focusOrOpenApp(baseUrl)),
    );
    return;
  }

  let url = baseUrl;
  if (action === "snooze") {
    // Snooze — the client handles this via a special query param on open.
    url = `${url}${url.includes("?") ? "&" : "?"}snooze=1h`;
  }

  event.waitUntil(focusOrOpenApp(url));
});
