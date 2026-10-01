import "./globals.css";
import "./i18n";

import * as Sentry from "@sentry/react";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import {
  SENTRY_PII_HEADER_SNIPPETS,
  readTraceSampleRate,
  scrubSentryBreadcrumb,
  scrubSentryEvent,
  scrubSentryTransaction,
} from "./lib/observability/sentry";
import {
  clearStaleChunkReloadFlag,
  clearVersionReloadFlag,
  reloadOnceForStaleChunk,
} from "./lib/chunkError";

const traceSampleRate = readTraceSampleRate(
  import.meta.env.VITE_SENTRY_TRACES_SAMPLE_RATE,
  import.meta.env.PROD ? 0.2 : 1.0,
);

Sentry.init({
  dsn: import.meta.env.VITE_SENTRY_DSN ?? "",
  environment: import.meta.env.MODE,
  release: `tday-web@${__APP_VERSION__}`,
  // Sentry 11 dropped `sendDefaultPii` and now collects user info, cookies, headers,
  // query strings and bodies unless told otherwise. This is the privacy-first posture
  // `sendDefaultPii: false` used to give, with cookies and query strings off outright.
  dataCollection: {
    userInfo: false,
    cookies: false,
    httpHeaders: {
      request: { deny: SENTRY_PII_HEADER_SNIPPETS },
      response: { deny: SENTRY_PII_HEADER_SNIPPETS },
    },
    httpBodies: [],
    urlQueryParams: false,
    genAI: { inputs: false, outputs: false },
    databaseQueryData: false,
  },
  tracesSampleRate: traceSampleRate,
  tracePropagationTargets: [/^\/api(\/|$)/],
  replaysSessionSampleRate: 0,
  replaysOnErrorSampleRate: 0,
  integrations: (defaultIntegrations) => [
    // Console breadcrumbs moved out of `breadcrumbsIntegration` into their own
    // `Console` integration in 11; both stay off (console output can carry user text).
    ...defaultIntegrations.filter(
      (integration) =>
        integration.name !== "Breadcrumbs" && integration.name !== "Console",
    ),
    Sentry.breadcrumbsIntegration({
      dom: false,
    }),
    Sentry.browserTracingIntegration(),
  ],
  beforeBreadcrumb: scrubSentryBreadcrumb,
  beforeSend: scrubSentryEvent,
  beforeSendTransaction: scrubSentryTransaction,
});

// Recover from stale dynamic-import chunks after a deploy: when a hashed chunk
// referenced by an old cached bundle no longer exists, Vite fires this event.
// Reload once to fetch the fresh index.html (and current chunks).
window.addEventListener("vite:preloadError", (event) => {
  event.preventDefault();
  reloadOnceForStaleChunk();
});

// If the app stays alive past initial render, it loaded cleanly — clear the
// reload guards so a future deploy within this session can also self-heal.
window.setTimeout(() => {
  clearStaleChunkReloadFlag();
  clearVersionReloadFlag();
}, 8000);

if ("serviceWorker" in navigator && import.meta.env.PROD) {
  navigator.serviceWorker
    .register("/sw.js", { scope: "/" })
    .catch((err) => console.warn("SW registration failed:", err));
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
