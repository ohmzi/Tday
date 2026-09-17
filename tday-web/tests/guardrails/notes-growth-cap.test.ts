import { readdirSync, readFileSync, statSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

/**
 * A LONG NOTE STOPS GROWING THE SHEET AT HALF THE SCREEN
 *
 * The report was a behaviour, not a bug: type more than about two lines into a
 * task's notes and the sheet stops growing — it is already at its ceiling — so the
 * note the user is still writing slides down under the fold. Reading it back means
 * scrolling the whole form, with the schedule and the list and the steps travelling
 * past on the way. Nothing was broken; the note simply had no ceiling of its own, so
 * the only ceiling it could meet was the sheet's, and the sheet's is shared with
 * everything else in the form.
 *
 * So the title-and-notes block gets a budget of its own and the fields scroll inside
 * it. The numbers are the whole design and there are only two of them, so this file
 * holds them to three things: that they stay in the stated relationship, that the
 * block cannot outgrow the sheet it lives in, and that no fourth surface quietly
 * renders a notes field without them.
 *
 * ## Why a source read
 *
 * jsdom lays nothing out — every box it reports is 0×0 — so the one assertion that
 * could actually prove any of this, a measured height, is exactly what this
 * environment cannot produce. What it CAN read is the decision: which class each
 * element carries. That is the same trade `task-row-first-line-alignment` and
 * `radius-ladder` make, and for the same reason.
 *
 * ## The trap this file exists for
 *
 * The notes field is the flex child that gives. Written as `flex-1` — which is what
 * the shorthand suggests and what a reader would "tidy" it into — its `flex-basis`
 * is zero, so the card measures it as empty, collapses to the title's height, and
 * hands the notes nothing at all. The field would be a 0-px sliver on a card that
 * looked, at a glance, entirely correct. `flex-auto` is the load-bearing word and
 * rule 2 is why it cannot be simplified away.
 */

const ROOT = path.resolve(__dirname, "..", "..");
const SRC = path.join(ROOT, "src");

const CHROME = "src/components/ui/sheet-chrome/index.tsx";
const NOTES_FIELD = "src/components/todo/component/NotesField/NotesField.tsx";
const APP_BOTTOM_SHEET = "src/components/ui/AppBottomSheet.tsx";

/** Paths under `tday-web/`. */
const read = (file: string) => stripComments(readFileSync(path.join(ROOT, file), "utf8"));

/**
 * Blanks block and line comments, preserving length.
 *
 * Read for the reason `radius-ladder` reads it: the negative rules below are about
 * what the code DOES, and a file that explains `flex-auto` by naming `flex-1` — this
 * change's own component does — would otherwise fail a rule it obeys. Prose about a
 * decision is not a call site. The positive rules read the stripped source too, so
 * that none of them can be satisfied by a comment describing the thing.
 */
function stripComments(source: string): string {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, (match) => " ".repeat(match.length))
    .replace(/\/\/[^\n]*/g, (match) => " ".repeat(match.length));
}

/**
 * Reads a `const NAME = "max-h-[<n><unit>]";` cap into the number and the unit.
 *
 * The unit is returned rather than assumed: a budget retuned into `vh` while the one
 * it is measured against stays in `dvh` would compare as two numbers that agree and
 * render as two boxes that do not, and `vh` on mobile does not move with the browser
 * chrome the sheet above it moves with.
 */
function budget(source: string, name: string): { value: number; unit: string } {
  const match = source.match(
    new RegExp(`const ${name} = "max-h-\\[(\\d+(?:\\.\\d+)?)([a-z]+)\\]"`),
  );
  if (!match) {
    throw new Error(
      `${name} is not written as a \`max-h-[<n><unit>]\` class, so this test cannot read it`,
    );
  }
  return { value: Number(match[1]), unit: match[2] };
}

/** Every `.ts`/`.tsx` under `src/`. */
function sourceFiles(dir: string): string[] {
  const found: string[] = [];
  for (const entry of readdirSync(dir)) {
    const full = path.join(dir, entry);
    if (statSync(full).isDirectory()) found.push(...sourceFiles(full));
    else if (/\.tsx?$/.test(entry)) found.push(full);
  }
  return found;
}

const CHROME_SOURCE = read(CHROME);
const CARD_CAP = budget(CHROME_SOURCE, "TITLE_NOTES_MAX");
const TITLE_CAP = budget(CHROME_SOURCE, "TITLE_FIELD_MAX");

describe("notes A — the two caps are still the stated pair", () => {
  it("reads both caps out of the card", () => {
    // A failure here rather than a silent zero: the constants are the design, and a
    // rename that took them out of this file's reach would turn the rules below green
    // by having nothing to say.
    expect(CARD_CAP.value).toBeGreaterThan(0);
    expect(TITLE_CAP.value).toBeGreaterThan(0);
  });

  it("gives the title half the block, so neither field can starve the other", () => {
    // Half the card, which is the whole claim: whatever either field does, the other
    // still has half the block to breathe in. It is also what makes the title cheap
    // when only the notes are long — a cap, not a share, so a one-line title takes
    // the line it needs and leaves the rest to the note.
    //
    // About the allocation, not the rendered pixels. The two windows come out some
    // way apart once the notes' own padding and their format bar are counted, and
    // TITLE_FIELD_MAX argues in place why that is the right trade rather than a
    // `calc` against the bar's current height.
    expect(
      CARD_CAP.value,
      `the title's ${TITLE_CAP.value}${TITLE_CAP.unit} is not half the card's ${CARD_CAP.value}${CARD_CAP.unit}`,
    ).toBe(TITLE_CAP.value * 2);
  });

  it("measures both against the same screen", () => {
    expect(TITLE_CAP.unit).toBe(CARD_CAP.unit);
  });

  it("leaves the sheet it sits in room for its own header and the rest of the form", () => {
    const sheetCap = read(APP_BOTTOM_SHEET).match(/max-h-\[(\d+)dvh\]/);
    expect(sheetCap, "AppBottomSheet no longer declares a `max-h-[<n>dvh]` ceiling").not.toBe(
      null,
    );
    // A block that could claim the sheet's whole ceiling would be a sheet whose
    // header and schedule row are always below the fold — the defect, reintroduced
    // from the other end.
    expect(CARD_CAP.value).toBeLessThan(Number(sheetCap![1]));
  });
});

describe("notes B — the block is capped, and the fields inside it give", () => {
  it("caps the card and lays it out as a column the children shrink in", () => {
    expect(CHROME_SOURCE).toContain('cn("flex flex-col", TITLE_NOTES_MAX)');
  });

  it("scrollers the title at its own cap rather than letting it grow", () => {
    expect(CHROME_SOURCE).toContain('cn("min-w-0 flex-1 overflow-y-auto", TITLE_FIELD_MAX)');
  });

  it("pins the divider, so the overflow cannot squeeze it between the two fields", () => {
    expect(CHROME_SOURCE).toContain('<SheetDivider className="shrink-0" />');
  });

  it("makes the notes field the half that gives, with a basis it can be measured by", () => {
    const source = read(NOTES_FIELD);
    // `flex-auto`, and `min-h-0` under it so the shrink is allowed to go below the
    // field's own content. See the header: `flex-1` here collapses the card.
    expect(source).toContain('"flex min-h-0 w-full flex-auto flex-col"');
    expect(source).not.toContain("flex-1");
  });

  it("scrolls the editor in a viewport that the card's cap, not the note, decides", () => {
    const source = read(NOTES_FIELD);
    expect(source).toContain('"relative min-h-0 flex-auto overflow-y-auto"');
  });

  it("leaves the format bar outside that viewport, so the marks stay reachable", () => {
    const source = read(NOTES_FIELD);
    // The bar is the sibling AFTER the viewport in a flex column. A `shrink-0` box
    // after a `flex-auto` one that scrolls is pinned to the bottom of the card; moved
    // inside the viewport it would scroll away with the note, and a user three screens
    // into a long note would have no Bold button at all.
    const viewport = source.indexOf('"relative min-h-0 flex-auto overflow-y-auto"');
    const formatBar = source.indexOf("border-t border-border px-2 py-1");
    expect(viewport).toBeGreaterThan(-1);
    expect(formatBar).toBeGreaterThan(viewport);
    expect(source).toContain('"flex shrink-0 items-center gap-0.5 border-t border-border px-2 py-1"');
  });
});

describe("notes C — every surface that renders notes renders this card", () => {
  /**
   * The three title-and-notes cards — the task sheet, the calendar form and the
   * floater sheet. They were three copies of the same markup, which is how a fix
   * lands on one of them; they are now one component, and this is what holds them
   * to it. Discovered by scanning rather than listed, so a fourth surface added
   * later is covered by having been written, not by having been remembered.
   */
  const callSites = sourceFiles(SRC)
    .filter((file) => /<NotesField\b/.test(read(path.relative(ROOT, file))))
    .map((file) => path.relative(ROOT, file));

  it("finds the call sites at all", () => {
    // An empty sweep would pass every rule below by having nothing to check, which is
    // the failure mode this whole file is written against.
    expect(callSites.length).toBeGreaterThanOrEqual(3);
  });

  it.each(callSites)("%s renders its notes inside SheetTitleNotesCard", (file) => {
    expect(
      read(file),
      `${file} renders <NotesField> outside the capped card, so a long note there grows the sheet without limit`,
    ).toContain("<SheetTitleNotesCard");
  });

  it("renders the card from one place, so the caps are spelled once", () => {
    const defineCount = sourceFiles(SRC).filter((file) =>
      /export function SheetTitleNotesCard\b/.test(read(path.relative(ROOT, file))),
    ).length;
    expect(defineCount).toBe(1);
  });
});
