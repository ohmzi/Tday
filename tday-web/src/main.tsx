import "./globals.css";
import "./i18n";

import * as Sentry from "@sentry/react";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import {
  readTraceSampleRate,
  scrubSentryBreadcrumb,
  scrubSentryEvent,
  scrubSentryTransaction,
} from "./lib/observability/sentry";
import {
  clearStaleChunkReloadFlag,
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
  sendDefaultPii: false,
  tracesSampleRate: traceSampleRate,
  tracePropagationTargets: [/^\/api(\/|$)/],
  replaysSessionSampleRate: 0,
  replaysOnErrorSampleRate: 0,
  integrations: (defaultIntegrations) => [
    ...defaultIntegrations.filter(
      (integration) => integration.name !== "Breadcrumbs",
    ),
    Sentry.breadcrumbsIntegration({
      console: false,
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
// guard so a future deploy within this session can also self-heal.
window.setTimeout(clearStaleChunkReloadFlag, 8000);

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
