import { readFileSync, readdirSync, statSync } from "node:fs";
import { join, resolve } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * Every toast in the app is one frosted, fully-rounded pill, and that pill is
 * drawn by the surrounding sonner `<li>` (see `toastOptions.classNames` in
 * `src/components/ui/sonner.tsx`), not by the toast's own content.
 *
 * A `toast.custom` toast is rendered with `data-styled="false"`, which means
 * sonner's own surface rules never apply to it — the `<li>` still wears the
 * shared `toast` class list, so a custom toast that ALSO draws its own
 * bordered/backdrop-blurred card renders as a box inside a box: a second,
 * differently-rounded, differently-opaque surface nested in the pill. That is
 * exactly what `ReleaseUpdateAnnouncer` used to do (a `rounded-[24px]
 * bg-popover/92 backdrop-blur-xl` button inside the shared rounded-full pill),
 * and it is why the reference, `VersionGate`, renders only `<ClickableToast>`.
 *
 * There are three custom-toast call sites, all of them covered here:
 * `components/app/VersionGate.tsx`, `components/release/ReleaseUpdateAnnouncer.tsx`,
 * and the tap-through path of `hooks/use-toast.ts` (which calls the aliased
 * `sonnerToast.custom`).
 *
 * jsdom has no cascade, so a rendered-className assertion cannot see the
 * computed double box. Reading the source as text is the only cheap guard —
 * the same reason `toast-action-specificity.test.ts` reads source too.
 */

const SRC_ROOT = resolve(__dirname, "../../src");

/** Strip `//` line comments and `/* … *\/` block comments so the box markers in
 *  the explanatory prose (e.g. VersionGate's "backdrop-blurred card") are not
 *  mistaken for a real class list. */
function stripComments(source: string): string {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, "")
    .replace(/(^|[^:])\/\/.*$/gm, "$1");
}

/** A custom-toast call on any receiver: `toast.custom(`, and the aliased
 *  `sonnerToast.custom(` that `hooks/use-toast.ts` uses (case-insensitive, so
 *  both spellings of the alias are caught). */
const TOAST_CUSTOM = /toast\.custom\(/i;

/** Every `.ts`/`.tsx` under src that calls a custom toast. */
function filesCallingToastCustom(dir: string): string[] {
  const out: string[] = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      out.push(...filesCallingToastCustom(full));
    } else if (/\.tsx?$/.test(entry)) {
      const source = readFileSync(full, "utf8");
      if (TOAST_CUSTOM.test(source)) out.push(full);
    }
  }
  return out;
}

/** The balanced text of each custom-toast call — content JSX and its options
 *  object, but nothing else in the file. */
function extractToastCustomCalls(source: string): string[] {
  const calls: string[] = [];
  for (const match of source.matchAll(new RegExp(TOAST_CUSTOM, "gi"))) {
    const start = match.index;
    let depth = 0;
    let i = start + match[0].length - 1; // sit on the opening "("
    for (; i < source.length; i++) {
      const ch = source[i];
      if (ch === "(") depth++;
      else if (ch === ")") {
        depth--;
        if (depth === 0) break;
      }
    }
    calls.push(source.slice(start, i + 1));
  }
  return calls;
}

/** The `className="…"` string literals inside a region. */
function classNameStrings(region: string): string[] {
  return [...region.matchAll(/className\s*=\s*"([^"]*)"/g)].map((m) => m[1]);
}

/**
 * A custom toast drawing its own surface: a border, a backdrop blur, a popover
 * fill, an off-ladder radius, a bespoke shadow, or a width cap. Any one of
 * these on the toast's own element is the double-box bug the shared pill
 * already covers.
 */
const HAND_ROLLED_BOX_MARKERS = [
  "border",
  "backdrop-blur",
  "bg-popover",
  "rounded-[",
  "rounded-card",
  "shadow-[",
  "w-[min(",
];

describe("every custom toast rides on sonner's shared pill", () => {
  const customToastFiles = filesCallingToastCustom(SRC_ROOT);
  const cases = customToastFiles.map(
    (file) => [file.slice(SRC_ROOT.length + 1), file] as const,
  );

  it("finds the custom-toast call sites", () => {
    expect(customToastFiles.length).toBeGreaterThan(0);
  });

  it.each(cases)("%s renders ClickableToast, not its own box", (_name, file) => {
    const calls = extractToastCustomCalls(stripComments(readFileSync(file, "utf8")));
    expect(calls.length).toBeGreaterThan(0);

    for (const call of calls) {
      // The shared renderer is what supplies every custom toast's content —
      // as JSX (`<ClickableToast …>`) or via `React.createElement(ClickableToast, …)`.
      expect(call).toMatch(/ClickableToast/);

      // …and the custom toast contributes no surface of its own.
      for (const classes of classNameStrings(call)) {
        for (const marker of HAND_ROLLED_BOX_MARKERS) {
          expect(
            classes,
            `${_name} hand-rolls a toast surface ("${classes}"); custom toasts must render content only — the sonner <li> already draws the pill.`,
          ).not.toContain(marker);
        }
      }
    }
  });
});
