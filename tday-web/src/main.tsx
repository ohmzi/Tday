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

// Starts crash reporting only if this browser already said yes, and follows the answer from here
// on. Nothing is initialised, patched or queued before that; see `sentryInit.ts`.
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

createRoot(document.getElementById("root")!, reactRootErrorHandlers).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
