/** Browser helpers for saving/loading a JSON file client-side. */

/** Serializes `data` and prompts the browser to save it as `filename`. */
export function downloadJson(filename: string, data: unknown): void {
  const blob = new Blob([JSON.stringify(data, null, 2)], {
    type: "application/json",
  });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  // Revoke on the next tick so the click has committed the navigation.
  setTimeout(() => URL.revokeObjectURL(url), 0);
}

/** Reads a picked file as text and parses it as JSON, throwing on bad JSON. */
export async function readJsonFile(file: File): Promise<unknown> {
  const text = await file.text();
  return JSON.parse(text);
}

/** A filesystem-safe timestamp like `2026-07-14-1530` for export filenames. */
export function fileTimestamp(now: Date = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return (
    `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}` +
    `-${pad(now.getHours())}${pad(now.getMinutes())}`
  );
}
