import "./globals.css";
import "./i18n";

import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import {
  initSentryIfConsented,
  reactRootErrorHandlers,
} from "./lib/observability/sentryInit";
import {
  clearStaleChunkReloadFlag,
  clearVersionReloadFlag,
  reloadOnceForStaleChunk,
} from "./lib/chunkError";

// Asks the server whether this instance allows error reports and starts crash reporting only once
// the answer arrives, then follows the admin's answer from there on. Nothing is initialised,
// patched or queued before it; see `sentryInit.ts`.
initSentryIfConsented();

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

const rootElement = document.getElementById("root");
if (!rootElement) throw new Error("T'Day could not find its root element");

createRoot(rootElement, reactRootErrorHandlers).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
