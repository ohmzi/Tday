import { readdirSync, readFileSync, statSync } from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

/**
 * THE RADIUS LADDER
 *
 * `rounded-xl` rendered 12px while `rounded-md` rendered 14px. Not a typo and
 * not a value anybody chose: `globals.css` overrode three of Tailwind v4's
 * eight radius keys and let the framework supply the other five from its own
 * `theme.css`, where they are scaled against a `--radius` half the size of
 * ours. A rung that is not declared is a rung the framework picks, and half a
 * namespace is a ladder whose rungs are only accidentally in order.
 *
 * So rule B, not rule A, is the one that would have caught this. Sorting the
 * declared rungs (A) only proves the half somebody remembered is monotonic;
 * demanding that every key Tailwind ships appears here at all (B) is what
 * closes the hole the defect came through. B reads the key list off
 * `node_modules/tailwindcss/theme.css` rather than hard-coding eight names, so
 * a Tailwind upgrade that adds a ninth rung fails this test instead of quietly
 * reopening it.
 *
 * Rule C is the call-site half. The fix was a RENAME — `2xl` and `3xl` were
 * retired as names, not as pixels — and a rename only holds if the retired
 * names cannot come back. Comments are blanked first, the way
 * `reduced-motion-floor` blanks them: three files still describe a card that
 * used to be `rounded-2xl`, and prose about a shape that no longer exists is
 * not a call site.
 *
 * It walks `public/` as well as `src/`, because the blog articles there are
 * fetched and injected into a routed page under this same stylesheet, and they
 * are the one place a retired name costs MORE than a wrong corner: the compiler
 * never reads them, so `rounded-2xl` would emit no rule at all and the `<pre>`
 * would go square, with nothing anywhere to say so. A rename that stops at the
 * files TypeScript happens to check is not a rename.
 *
 * A static read of the stylesheet, like its neighbours. jsdom applies no
 * stylesheet and Tailwind's cascade is exactly what is on trial here.
 */

const ROOT = path.resolve(__dirname, "..", "..");
const SRC = path.join(ROOT, "src");
const PUBLIC = path.join(ROOT, "public");
const GLOBALS_CSS = path.join(SRC, "globals.css");
const TAILWIND_THEME = path.join(ROOT, "node_modules", "tailwindcss", "theme.css");

/** Blanks comments, preserving length, so prose about a defect never reads as one. */
function stripBlockComments(source: string): string {
  return source.replace(/\/\*[\s\S]*?\*\//g, (match) => " ".repeat(match.length));
}

/** Blanks `/* *\/` and `//` alike — the three surviving mentions are both kinds. */
function stripSourceComments(source: string): string {
  return stripBlockComments(source).replace(/\/\/[^\n]*/g, (match) => " ".repeat(match.length));
}

/**
 * Markup gets its own stripper rather than the source one: HTML has no `//`
 * comment, so blanking that sequence would swallow the rest of any line
 * carrying an `https://`, and an offender sitting after a link would vanish.
 */
function stripMarkupComments(source: string): string {
  return source.replace(/<!--[\s\S]*?-->/g, (match) => " ".repeat(match.length));
}

/** Blanks comments in whichever syntax the file is actually written in. */
function stripComments(file: string, source: string): string {
  return file.endsWith(".html") ? stripMarkupComments(source) : stripSourceComments(source);
}

/** The body of `@theme inline { … }`, brace-matched so a nested block cannot end it early. */
function themeBlock(css: string): string {
  const open = css.indexOf("@theme inline");
  if (open < 0) throw new Error("globals.css declares no `@theme inline` block");
  const start = css.indexOf("{", open);
  let depth = 0;
  for (let i = start; i < css.length; i += 1) {
    if (css[i] === "{") depth += 1;
    else if (css[i] === "}") {
      depth -= 1;
      if (depth === 0) return css.slice(start + 1, i);
    }
  }
  throw new Error("the `@theme inline` block is never closed");
}

/** `1rem`, at the root size the app never moves. */
const ROOT_PX = 16;

/**
 * Resolves the shapes the ladder is actually written in — `Npx`, `Nrem`, and
 * `calc(var(--radius) ± Npx)` — and nothing else. A rung written in a form this
 * cannot read returns null and fails rule A by name rather than being scored as
 * a zero and sorting below everything.
 */
function resolvePx(value: string, radiusPx: number): number | null {
  const direct = value.match(/^(-?[\d.]+)(px|rem)$/);
  if (direct) {
    return Number(direct[1]) * (direct[2] === "rem" ? ROOT_PX : 1);
  }
  const bare = value.match(/^var\(--radius\)$/);
  if (bare) return radiusPx;
  const offset = value.match(/^calc\(\s*var\(--radius\)\s*([+-])\s*([\d.]+)px\s*\)$/);
  if (offset) {
    return radiusPx + (offset[1] === "-" ? -1 : 1) * Number(offset[2]);
  }
  return null;
}

const GLOBALS = readFileSync(GLOBALS_CSS, "utf-8");
const THEME = stripBlockComments(themeBlock(stripBlockComments(GLOBALS)));

/** `--radius` itself lives outside `@theme`, on `:root`. */
const RADIUS_PX = (() => {
  const declared = stripBlockComments(GLOBALS).match(/--radius:\s*([^;]+);/);
  if (!declared) throw new Error("globals.css declares no `--radius`");
  const resolved = resolvePx(declared[1].trim(), Number.NaN);
  if (resolved === null) throw new Error(`--radius is written as \`${declared[1].trim()}\``);
  return resolved;
})();

const DECLARED = new Map<string, string>();
for (const match of THEME.matchAll(/--radius-([a-z0-9]+):\s*([^;]+);/g)) {
  DECLARED.set(match[1], match[2].trim());
}

const TAILWIND_KEYS = [
  ...new Set(
    [...readFileSync(TAILWIND_THEME, "utf-8").matchAll(/^\s*--radius-([a-z0-9]+):/gm)].map(
      (match) => match[1],
    ),
  ),
];

function sourceFiles(dir: string): string[] {
  const found: string[] = [];
  for (const entry of readdirSync(dir)) {
    const full = path.join(dir, entry);
    if (statSync(full).isDirectory()) found.push(...sourceFiles(full));
    else if (/\.(ts|tsx|css|html)$/.test(entry)) found.push(full);
  }
  return found;
}

describe("radius A — the ladder is monotonic", () => {
  const LADDER = ["xs", "sm", "md", "lg", "xl"];

  it("resolves every rung it names", () => {
    for (const key of LADDER) {
      const value = DECLARED.get(key);
      expect(value, `--radius-${key} is not declared in \`@theme inline\``).toBeDefined();
      expect(
        resolvePx(value!, RADIUS_PX),
        `--radius-${key} is written as \`${value}\`, which this test cannot resolve`,
      ).not.toBeNull();
    }
  });

  it("climbs strictly from xs to xl", () => {
    const resolved = LADDER.map((key) => ({
      key,
      px: resolvePx(DECLARED.get(key) ?? "", RADIUS_PX),
    }));
    for (let i = 1; i < resolved.length; i += 1) {
      const below = resolved[i - 1];
      const above = resolved[i];
      expect(
        above.px,
        `--radius-${above.key} renders ${above.px}px, at or below --radius-${below.key}'s ${below.px}px`,
      ).toBeGreaterThan(below.px!);
    }
  });
});

describe("radius B — the whole namespace is declared", () => {
  it("finds keys to check at all", () => {
    // A Tailwind layout change that hid the defaults would turn rule B green by
    // having nothing to say, which is the failure mode it exists to prevent.
    expect(TAILWIND_KEYS.length, `no --radius-* keys found in ${TAILWIND_THEME}`).toBeGreaterThan(
      4,
    );
  });

  it("leaves no rung for Tailwind to decide", () => {
    for (const key of TAILWIND_KEYS) {
      expect(
        DECLARED.has(key),
        `--radius-${key} is Tailwind's, not ours: declare it, or retire it with \`initial\``,
      ).toBe(true);
    }
  });
});

describe("radius C — the retired names stay retired", () => {
  it("spells no `rounded-2xl`, `rounded-3xl` or `rounded-4xl` under src or public", () => {
    const offenders: string[] = [];
    for (const file of [...sourceFiles(SRC), ...sourceFiles(PUBLIC)]) {
      const code = stripComments(file, readFileSync(file, "utf-8"));
      for (const match of code.matchAll(/rounded-(?:[a-z]+-)?[234]xl/g)) {
        const line = code.slice(0, match.index).split("\n").length;
        offenders.push(`${path.relative(ROOT, file)}:${line} — ${match[0]}`);
      }
    }
    expect(
      offenders,
      `these render a corner the ladder no longer names:\n${offenders.join("\n")}`,
    ).toEqual([]);
  });
});
