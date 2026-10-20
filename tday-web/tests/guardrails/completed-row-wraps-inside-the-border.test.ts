import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * A COMPLETED ROW DIVIDES AT THE APP'S BORDER INSTEAD OF RUNNING THROUGH IT
 *
 * The report was one sentence — "completed screen on desktop has task in one line even as it goes
 * outside of screen, it should do this better, divide into more lines where the border of the web
 * app is" — and the class doing the harm is the least suspicious one on the row: `truncate`, which
 * this app reads as "keep it short" and which is `overflow:hidden; text-overflow:ellipsis;
 * white-space:nowrap`. The `nowrap` is not a cap, it is the opposite: it makes the title's
 * min-content width its entire text on one line, and min-content is what the row is built out of.
 *
 * The chain that carries it: the row is a `flex ... justify-between` inside a grid whose only
 * column was IMPLICIT (`grid-rows-[1fr]` declares rows), and an implicit `auto` track is floored
 * at its content's min-content contribution. `max-w-full` on the row caps the row's own box, never
 * the TRACK it sits in, and at `sm` and up the wrapper is `overflow-visible` — so the track grew,
 * the row grew with it, and both painted past the 1152px `max-w-6xl` border. The only clip in the
 * chain is the scroller's `overflow-x-hidden`, at the viewport edge, never at the border. A
 * descendant cannot fix this by shrinking either: `min-w-0` on the title's column cannot reduce an
 * ancestor's min-content contribution.
 *
 * Two things finish it, and both are pinned below because a row that only removes `truncate` is
 * one long word away from the report again: `wrap-anywhere` (`overflow-wrap: anywhere`) is what
 * lets an unbroken token break at all — `break-words` does not reduce a word's min-content width
 * and the word is the widest thing in the row — and the list-name pill's cap has to exist at
 * EVERY width, because the pill is `shrink-0` (pinned by `task-row-first-line-alignment`) and the
 * backend column behind it is `varchar(255)`.
 *
 * ## Why a source read, and what was measured instead
 *
 * jsdom lays nothing out, so the arithmetic — the thing that was actually wrong — cannot be
 * asserted here, and neither can the fix. It was measured in headless Chromium against the app's
 * own compiled Tailwind and its own bundled Nunito, with the real shell (`max-w-6xl` inside the
 * `overflow-x-hidden` scroller, `px-4 sm:px-6 lg:px-10`), a 156-character title, a 55-character
 * list name and a 255-character one:
 *
 *   shipped   row's right edge past the content border, at every width:
 *             550px at 1440 · 758 at 1024 · 826 at 768 · 842 at 640 · 960 at 390 · 1030 at 320 —
 *             and the long title alone, with no list mark drawn at all, was already 138px past at
 *             1440 and 346px past at 1024. A 255-character list name on its own put the row 1363px
 *             past at 1440. `truncate` never ellipsized anything: the title's box was its whole
 *             text width in every case.
 *   this fix  0px past at 1440, 1024, 768, 640, 390 and 320, for each of: long title + long name;
 *             long title with no mark; short title + 255-character name; long title with a long
 *             unbroken URL in its notes; and a title that is one 108-character token. The title is
 *             two lines at every width and never more (at 1024 it needs three and the clamp cuts
 *             the third), and the name ellipsizes inside its cap instead of the row growing.
 *
 * So this file pins the DECLARATIONS that decide it, which is the same instrument — and the same
 * reason for it — as `task-row-first-line-alignment.test.ts`.
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

const ROWS = [
  "features/completed/component/ItemContainer.tsx",
  "features/completed/component/CompletedFloaterItemContainer.tsx",
];

describe("a completed row divides at the app's border", () => {
  it.each(ROWS)("%s wraps its title instead of nowrap-ing it onto one line", (path) => {
    const row = readCode(path);

    // The fix, and the two bounds on it. `line-clamp-2` is how far the title may divide — two
    // lines is what both native clients draw, Android by `maxLines = 2` on its completed title
    // and iOS by its own note that "its titles wrap to two lines" — and `wrap-anywhere` is what
    // makes a title with no space in it able to break at all.
    expect(row).toContain("select-none line-clamp-2 wrap-anywhere");

    // The defect, in the one string it lived in. Not a bare `toContain("truncate")`: the list
    // NAME is still truncated on purpose, inside its cap, at the other end of the same row.
    expect(row).not.toContain('"select-none truncate');
  });

  it.each(ROWS)("%s gives its grid a real column, not an implicit auto track", (path) => {
    // An implicit track is floored at its content's min-content width, and `max-w-full` on a
    // child caps the child and never the track. `minmax(0, 1fr)` removes the floor, which is
    // what makes the border the hard edge rather than a line the row runs through.
    expect(readCode(path)).toContain("grid-cols-[minmax(0,1fr)] grid-rows-[1fr]");
  });

  it.each(ROWS)("%s caps its list-name pill at every width, desktop included", (path) => {
    // The pill is `shrink-0` (see `task-row-first-line-alignment`), so nothing above it can
    // compress it; and the `lg` step used to REMOVE the cap (`lg:max-w-none`) on exactly the
    // breakpoint this report is about. Measured with the track pinned, an uncapped 255-character
    // name put the pill 1195px past the border and gave the scroller a 1055px horizontal run.
    const row = readCode(path);
    expect(row).toContain("max-w-24 truncate md:max-w-52 lg:max-w-64");
    expect(row).not.toContain("lg:max-w-none");
  });

  it.each(ROWS)("%s lets an unbroken run in the notes break", (path) => {
    // The third contributor, and the one with nothing to do with the title: a note is a `<pre>`
    // with `pre-wrap`, so it breaks at spaces and not inside a word, and `w-48`/`sm:w-full` is
    // the width the unbroken run was then squeezed into. One long URL in a note pushed the NOTES
    // box 134px past the border at 1440 and 694px past at 640 with the title already wrapping.
    expect(readCode(path)).toContain("whitespace-pre-wrap wrap-anywhere");
  });
});
