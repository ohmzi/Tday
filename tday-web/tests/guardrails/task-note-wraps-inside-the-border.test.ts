import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * A NOTE DIVIDES AT THE APP'S BORDER INSTEAD OF RUNNING THROUGH IT
 *
 * The report was one screen — "the webapp on desktop has the note of task going way out of
 * boundries of the app, it should follow the task title and stay within the limits of the app" —
 * and the note is a `<pre>` with `pre-wrap`, which is a breaking rule rather than a width rule: it
 * breaks at spaces and newlines and NEVER inside a word. A note that is one long URL, a stack trace
 * or a token is therefore a single unbreakable run, and an unbreakable run is not squeezed into a
 * narrow box the way the title above it is — it paints straight past the box, past the row, and
 * past the `max-w-6xl` border, at every desktop width. The row's own marks (the list pill, the
 * priority flag) stay put, which is what makes it read as one note escaping rather than a row
 * growing.
 *
 * `wrap-anywhere` (`overflow-wrap: anywhere`) is the fix, and `break-words` is NOT: it breaks a
 * long word at paint time but leaves the word's min-content width untouched, so the box it is in
 * still refuses to be as narrow as it needs to be. The two Completed rows were fixed this way in
 * `completed-row-wraps-inside-the-border`, measured there in headless Chromium against the app's
 * own compiled Tailwind — one long URL in a note put the notes box 134px past the border at 1440
 * and 694px past at 640, with the title already wrapping. The three remaining rows are the root
 * feeds and the calendar, and the note is the same `<pre>` with the same class list minus the one
 * class that made it safe.
 *
 * So this file pins the DECLARATION that decides it, on every row that draws a note. jsdom lays
 * nothing out, so the arithmetic cannot be asserted here — but the class either is on the `<pre>`
 * or it is not, and that is the whole difference between the report and no report.
 *
 * ## What was measured instead
 *
 * The Floater row was rendered in headless Chromium against the app's own compiled Tailwind and
 * this build's `index-*.css`, inside the real shell (`max-w-6xl` in an `overflow-x-hidden`
 * scroller) with the reported note — a line of prose plus one 350-character unbroken URL. Measured
 * by a `Range` over the note's text, so it is where the ink lands and not the box, which sits
 * inside the border in both versions:
 *
 *   without `wrap-anywhere`   1119px past the border at 1440 · 1247 at 1024 · 1487 at 768 ·
 *                             1615 at 640 · 1857 at 390. The note's box did not grow a millimetre:
 *                             the LINE count stayed at 4 while the run left the app.
 *   with `wrap-anywhere`      0px past at every one of those widths, the note dividing into 6, 6,
 *                             7, 8 and 16 lines as the column narrows.
 */

const WEB = resolve(__dirname, "..", "..", "src");

/** Source with block comments and whole-line `//` prose removed, so every match is code. */
function readCode(path: string): string {
  return readFileSync(resolve(WEB, path), "utf8")
    .replace(/\/\*[\s\S]*?\*\//g, "")
    .split("\n")
    .filter((line) => !line.trimStart().startsWith("//"))
    .join("\n");
}

/** Every web row that draws a task's notes, across the root feeds, the calendar and Completed. */
const ROWS_WITH_NOTES = [
  "components/todo/component/TodoItemContainer.tsx",
  "features/floater/component/FloaterItemContainer.tsx",
  "features/calendar/component/CalendarClient.tsx",
  "features/completed/component/ItemContainer.tsx",
  "features/completed/component/CompletedFloaterItemContainer.tsx",
];

describe("a task's note divides at the app's border", () => {
  it.each(ROWS_WITH_NOTES)("%s lets an unbroken run in a note break", (path) => {
    // `pre-wrap` keeps the note's own line breaks, which is why the note is a `<pre>` at all;
    // `wrap-anywhere` is the half that lets a run with no space in it break too, and it is the
    // class the `w-48`/`sm:w-full` pair below it was already assuming.
    expect(readCode(path)).toContain("whitespace-pre-wrap wrap-anywhere");
  });

  it.each(ROWS_WITH_NOTES)("%s does not fall back to `break-words` on its note", (path) => {
    // The near miss worth pinning: `break-words` reads as the same fix, renders the same on prose,
    // and does nothing about the min-content width the unbroken run was pinning the box to.
    expect(readCode(path)).not.toContain("whitespace-pre-wrap break-words");
  });
});
