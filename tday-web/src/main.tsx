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

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
