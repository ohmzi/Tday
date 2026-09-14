import { readdirSync, readFileSync, existsSync, statSync } from "fs";
import path from "path";
import { describe, it, expect } from "vitest";

/**
 * WEB MOTION REACHABILITY
 *
 * Every animation in here read correctly in review and could never run. That is the whole
 * category: the spec is present, it is well written, and nothing on the page ever reaches it.
 * A reviewer reading the diff sees a `@keyframes` block or a `data-[state=open]:animate-in`
 * and has no way to tell, from the file in front of them, whether anything will ever play it —
 * so eleven of these were written, reviewed and merged across the three clients.
 *
 * The five rules below are each anchored to an instance that was live on `develop` when this
 * file was written. A rule with no known instance is not a rule, it is noise.
 *
 *   A. Dead keyframes / dead animation utilities. `.animate-scroll-left` (`globals.css:300`)
 *      and `.animate-task-complete` (`:529`) had zero call sites anywhere under `src/`.
 *   B. An enter keyed to a Radix state with no matching exit.
 *      `CenteredSelectorOverlay.tsx:26,30` declared `data-[state=open]:animate-in` and no
 *      `data-[state=closed]:animate-out`, so Radix unmounted the node on the frame the state
 *      flipped and the exit never had a frame to play in.
 *   C. The subtree torn down synchronously. `Modal.tsx:75` was `if (!isOpen) return null`
 *      above a `createPortal`, with eight call sites — five of which repeated the same guard
 *      and three of which were conditionally mounted on the very flag they were handed. Any
 *      exit declared anywhere on that subtree is unreachable by construction.
 *   D. A cross-document-only view transition in an SPA. `globals.css` declared
 *      `@view-transition { navigation: auto }`, which fires only on a real document
 *      navigation; this app is a `createBrowserRouter` SPA and never performs one, and
 *      `document.startViewTransition` appears nowhere in the repo.
 *   E. Motion in a module nothing imports. Rule A asks whether a class is named anywhere under
 *      `src/`, which is the right question one level too low: `.animate-pulse` was named twelve
 *      times in `components/app/skeletons.tsx`, and no module imported that file. Eleven
 *      components were in that state — the pre-dock `SidebarContainer.tsx` among them — and
 *      between them they held four of web's durations and its only `ease-in`.
 *
 * These are static reads of source text — the same approach `android-standards.test.ts` and
 * `sentry-privacy.test.ts` take — because the defect is in what the source can reach, not in
 * what a render happens to produce. A rendered-DOM assertion cannot see a `@keyframes` block
 * that nothing names.
 */

const ROOT = path.resolve(__dirname, "..", "..");
const MONO = path.resolve(ROOT, "..");
const SRC = path.join(ROOT, "src");
const GLOBALS_CSS = path.join(SRC, "globals.css");
const INDEX_HTML = path.join(ROOT, "index.html");
const ROUTER_TSX = path.join(SRC, "router.tsx");

function walkFiles(dir: string, exts: string[]): string[] {
  const results: string[] = [];
  if (!existsSync(dir)) return results;
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      results.push(...walkFiles(full, exts));
    } else if (exts.some((ext) => entry.name.endsWith(ext))) {
      results.push(full);
    }
  }
  return results;
}

function readSource(filePath: string): string {
  return readFileSync(filePath, "utf-8");
}

function relPath(filePath: string): string {
  return path.relative(MONO, filePath);
}

const TS_FILES = walkFiles(SRC, [".ts", ".tsx"]);
const TSX_FILES = TS_FILES.filter((f) => f.endsWith(".tsx"));

/**
 * Blanks line and block comments, preserving both length and line count so reported line
 * numbers stay true. Every rule below reads this rather than the raw file: prose describing a
 * defect must not read as the defect, and a class name mentioned only in a comment is not a
 * call site. The `[^:]` guard keeps `https://` in a string from being eaten.
 */
function stripTsComments(source: string): string {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, (m) => m.replace(/[^\n]/g, " "))
    .replace(/(^|[^:])\/\/[^\n]*/g, (m, lead: string) => lead + " ".repeat(m.length - lead.length));
}

const CODE = new Map<string, string>(
  TS_FILES.map((f) => [f, stripTsComments(readSource(f))]),
);

const codeOf = (file: string) => CODE.get(file) ?? stripTsComments(readSource(file));

/**
 * Where a class name or a keyframe name can legitimately be USED. Markup and code only:
 * globals.css is deliberately excluded, because a dead utility naming its own dead keyframe
 * would otherwise keep itself alive. CSS-side references are resolved separately, and only
 * through rules that are themselves reachable.
 */
const CALL_SITE_TEXT = [
  ...TS_FILES.map(codeOf),
  existsSync(INDEX_HTML) ? readSource(INDEX_HTML) : "",
].join("\n");

/**
 * Whole-identifier match. `\b` is not enough: CSS identifiers contain hyphens, and `\bfade-in\b`
 * matches inside `tday-rows-fade-in`, which would report a dead keyframe as live.
 */
function namedIn(haystack: string, name: string): boolean {
  const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  return new RegExp(`(?<![\\w-])${escaped}(?![\\w-])`).test(haystack);
}

const referencedInSource = (name: string) => namedIn(CALL_SITE_TEXT, name);

// ─── A minimal CSS reader ───────────────────────────────────────────
// Enough to answer "which rule does this declaration belong to, and on what line does that
// rule start". Not a CSS parser: it tracks braces, and that is all this file needs.

interface CssBlock {
  prelude: string;
  line: number;
}

interface CssDeclaration {
  prop: string;
  value: string;
  selector: string;
  selectorLine: number;
  line: number;
  /**
   * The preludes of every block this declaration is nested inside, outermost first — in this
   * file that is the `@media`/`@supports` wrappers and nothing else. Rule F needs it because the
   * question it asks is about a declaration's CONTEXT rather than its own rule: a hint and the
   * `animation: none` that cancels it share a selector and differ only by which `@media` they sit
   * in, and without this the two are indistinguishable.
   */
  media: string[];
}

/** Blanks comments in place so reported line numbers stay true to the file on disk. */
function stripCssComments(css: string): string {
  return css.replace(/\/\*[\s\S]*?\*\//g, (match) => match.replace(/[^\n]/g, " "));
}

function parseCss(css: string): { blocks: CssBlock[]; decls: CssDeclaration[] } {
  const source = stripCssComments(css);
  const blocks: CssBlock[] = [];
  const decls: CssDeclaration[] = [];
  const stack: CssBlock[] = [];
  let buf = "";
  let line = 1;
  let bufLine = 1;

  const flush = () => {
    const text = buf.trim();
    buf = "";
    if (!text || stack.length === 0) return;
    const colon = text.indexOf(":");
    if (colon <= 0) return;
    const owner = stack[stack.length - 1];
    decls.push({
      prop: text.slice(0, colon).trim().toLowerCase(),
      value: text.slice(colon + 1).trim(),
      selector: owner.prelude,
      selectorLine: owner.line,
      line: bufLine,
      media: stack.slice(0, -1).map((block) => block.prelude),
    });
  };

  for (const ch of source) {
    if (ch === "{") {
      const block: CssBlock = { prelude: buf.trim(), line: bufLine };
      blocks.push(block);
      stack.push(block);
      buf = "";
    } else if (ch === "}") {
      flush();
      stack.pop();
      buf = "";
    } else if (ch === ";") {
      flush();
    } else {
      if (buf.trim() === "") bufLine = line;
      buf += ch;
    }
    if (ch === "\n") line += 1;
  }

  return { blocks, decls };
}

const GLOBALS = parseCss(readSource(GLOBALS_CSS));
const GLOBALS_REL = relPath(GLOBALS_CSS);

const classesIn = (selector: string): string[] =>
  [...selector.matchAll(/\.(-?[_a-zA-Z][\w-]*)/g)].map((m) => m[1]);

/**
 * A rule is reachable if it is not keyed to a class, or if at least one of its classes is named
 * in markup. `::view-transition-old(root)` and `body` are reachable; `.animate-scroll-left` is
 * only reachable if something renders that class.
 */
function selectorIsReachable(selector: string): boolean {
  const classes = classesIn(selector);
  if (classes.length === 0) return true;
  return classes.some(referencedInSource);
}

// ─── Rule A — nothing declares motion nobody can play ───────────────

describe("motion reachability A — dead keyframes and dead animation utilities", () => {
  /**
   * Deliberate exemptions. Empty, and it should stay that way: an animation utility that
   * nothing renders is not a style choice, it is a rule the browser will never evaluate.
   * Anything added here needs the call site it is waiting for named in the comment.
   */
  const UNUSED_CLASS_ALLOWLIST: string[] = [];

  /**
   * Keyframes referenced from JS rather than from CSS (an inline `animationName`, a
   * `Web Animations` call). Empty today — every keyframe in globals.css is named by a CSS rule.
   */
  const KEYFRAME_ALLOWLIST: string[] = [];

  it("every animation utility class declared in globals.css is rendered somewhere in src/", () => {
    const seen = new Set<string>();
    const violations: string[] = [];

    for (const decl of GLOBALS.decls) {
      if (!decl.prop.startsWith("animation")) continue;
      const classes = classesIn(decl.selector);
      if (classes.length === 0) continue;
      if (classes.some(referencedInSource)) continue;
      if (classes.every((c) => UNUSED_CLASS_ALLOWLIST.includes(c))) continue;

      const key = `${decl.selectorLine}:${decl.selector}`;
      if (seen.has(key)) continue;
      seen.add(key);
      violations.push(
        `${GLOBALS_REL}:${decl.selectorLine} → ${decl.selector} { ${decl.prop}: … } ` +
          `— no element in src/ ever carries ${classes.map((c) => `.${c}`).join(" or ")}`,
      );
    }

    expect(violations).toEqual([]);
  });

  it("every @keyframes declared in globals.css is named by a rule something can reach", () => {
    const declared = GLOBALS.blocks
      .map((b) => ({ block: b, match: /^@keyframes\s+(["']?)([\w-]+)\1$/.exec(b.prelude) }))
      .filter((entry) => entry.match !== null)
      .map((entry) => ({ name: entry.match![2], line: entry.block.line }));

    // Only the animation declarations that a reachable rule owns count as a reference. A dead
    // utility naming a keyframe keeps neither of them alive — that is how `scroll-left` and
    // `task-complete-out` survived: each was named exactly once, by a class nothing renders.
    const reachableAnimationValues = GLOBALS.decls
      .filter((d) => d.prop === "animation" || d.prop === "animation-name")
      .filter((d) => selectorIsReachable(d.selector))
      .map((d) => d.value);

    const violations: string[] = [];
    for (const { name, line } of declared) {
      if (KEYFRAME_ALLOWLIST.includes(name)) continue;
      if (reachableAnimationValues.some((value) => namedIn(value, name))) continue;
      if (referencedInSource(name)) continue;
      violations.push(
        `${GLOBALS_REL}:${line} → @keyframes ${name} — named by no reachable rule and by nothing in src/`,
      );
    }

    expect(violations).toEqual([]);
  });
});

// ─── Rule B — an enter keyed to a state pair needs its exit ─────────

/** `className="…"` / `className={…}` values, so `cn("a", "b")` is read as one class list. */
function classNameValues(source: string): { text: string; line: number }[] {
  const out: { text: string; line: number }[] = [];
  const re = /className\s*=\s*/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(source)) !== null) {
    const start = m.index + m[0].length;
    const line = source.slice(0, start).split("\n").length;
    const ch = source[start];
    if (ch === '"' || ch === "'") {
      const end = source.indexOf(ch, start + 1);
      if (end === -1) continue;
      out.push({ text: source.slice(start + 1, end), line });
      re.lastIndex = end + 1;
    } else if (ch === "{") {
      let depth = 0;
      let j = start;
      for (; j < source.length; j++) {
        if (source[j] === "{") depth += 1;
        else if (source[j] === "}") {
          depth -= 1;
          if (depth === 0) break;
        }
      }
      out.push({ text: source.slice(start + 1, j), line });
      re.lastIndex = j + 1;
    }
  }
  return out;
}

const RADIX_ENTER = "data-[state=open]:animate-in";
const RADIX_EXIT = "data-[state=closed]:animate-out";

describe("motion reachability B — a Radix enter without its exit", () => {
  it("every data-[state=open]:animate-in has a data-[state=closed]:animate-out beside it", () => {
    const violations: string[] = [];

    for (const file of TSX_FILES) {
      const source = codeOf(file);
      if (!source.includes(RADIX_ENTER)) continue;
      for (const { text, line } of classNameValues(source)) {
        if (!text.includes(RADIX_ENTER)) continue;
        if (text.includes(RADIX_EXIT)) continue;
        violations.push(
          `${relPath(file)}:${line} → declares ${RADIX_ENTER} with no ${RADIX_EXIT}; ` +
            "Radix unmounts the node on the frame the state flips, so the exit never plays",
        );
      }
    }

    expect(violations).toEqual([]);
  });

  it("reads every enter it is asked to judge", () => {
    // Anti-rot: the rule above only sees classes inside a `className` attribute. If a state-keyed
    // enter ever moves into a cva variant table or a bare constant, this fails rather than
    // letting the rule pass on a file it no longer understands.
    const unreadable: string[] = [];
    for (const file of TSX_FILES) {
      const source = codeOf(file);
      const total = source.split(RADIX_ENTER).length - 1;
      if (total === 0) continue;
      const read = classNameValues(source).reduce(
        (n, v) => n + (v.text.split(RADIX_ENTER).length - 1),
        0,
      );
      if (read < total) {
        unreadable.push(
          `${relPath(file)} → ${total - read} occurrence(s) of ${RADIX_ENTER} outside a className attribute`,
        );
      }
    }
    expect(unreadable).toEqual([]);
  });
});

// ─── Rule C — the subtree torn down on the frame it should exit ─────

/**
 * The Modal primitives that own a portal and now declare an exit. A file that renders either of
 * these has an exit animation on its subtree whether or not the word `animate` appears in it,
 * which is exactly why the five confirmation dialogs went unnoticed: each repeated Modal's own
 * `return null` guard one level up, so fixing Modal alone would have fixed nothing at them.
 */
const MODAL_PORTAL_PRIMITIVES = ["ModalOverlay", "ModalContent"];

/** Hooks that hold a node in the DOM past the moment its open flag goes false. */
const PRESENCE_HOOKS = ["useModalPresence", "useFadeUnmount"];

const OPEN_FLAG = /open|visible|show|display|present|mount/i;

/** Props whose value IS the open state, rather than data the component happens to need. */
const OPEN_PROP = /\b(open|isOpen|visible|isVisible|show|shown|[A-Za-z]*Open|display[A-Z]\w*)\s*=\s*\{/g;

function presenceIdentifiers(source: string): string[] {
  const out: string[] = [];
  for (const hook of PRESENCE_HOOKS) {
    const re = new RegExp(`(?:const|let)\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*${hook}\\s*\\(`, "g");
    let m: RegExpExecArray | null;
    while ((m = re.exec(source)) !== null) out.push(m[1]);
  }
  return out;
}

describe("motion reachability C — a portal torn down before its exit can play", () => {
  /**
   * Deliberate exemptions, by path. Empty, and it should stay that way.
   *
   * Note what does NOT need to be listed here: a file that returns null on an open flag but
   * declares no motion at all is already out of scope, because the scope test below asks for an
   * enter animation or a Modal portal first. `PendingApprovalScreen.tsx` is exactly that shape —
   * a full-screen takeover with no enter and no exit — so the guard there cuts nothing short and
   * the rule stays quiet without an entry. The day it gains an animation, it starts failing,
   * which is the point.
   */
  const SYNCHRONOUS_TEARDOWN_ALLOWLIST: string[] = [];

  it("no animated subtree is dropped by a `return null` on its own open flag", () => {
    const violations: string[] = [];

    for (const file of TSX_FILES) {
      const source = codeOf(file);
      const rel = relPath(file);
      if (SYNCHRONOUS_TEARDOWN_ALLOWLIST.includes(rel)) continue;

      const declaresEnter = /(?<![\w-])animate-in(?![\w-])/.test(source);
      const rendersPortal = MODAL_PORTAL_PRIMITIVES.some((name) =>
        source.includes(`<${name}`),
      );
      if (!declaresEnter && !rendersPortal) continue;

      const presence = presenceIdentifiers(source);
      const lines = source.split("\n");
      for (let i = 0; i < lines.length; i++) {
        const guard = /if\s*\(([^)]*)\)\s*return\s+null/.exec(lines[i]);
        if (!guard) continue;
        // Every flag the guard negates, not just the first — `if (!open || !data)` drops the
        // subtree on `open` exactly as hard as `if (!open)` does.
        for (const m of guard[1].matchAll(/!\s*([A-Za-z_$][\w$]*)/g)) {
          const flag = m[1];
          if (!OPEN_FLAG.test(flag)) continue;
          if (presence.includes(flag)) continue;
          violations.push(
            `${rel}:${i + 1} → \`if (!${flag}) return null\` drops the subtree on the frame ` +
              `${flag} goes false; gate it on ${PRESENCE_HOOKS[0]}(${flag}) instead`,
          );
        }
      }
    }

    expect(violations).toEqual([]);
  });

  it("no animated subtree is dropped by a conditional mount on the flag it is handed", () => {
    // The same defect one level up: `{open && <Dialog open={open} />}`. The component is given
    // the flag so it can animate on it, and then never gets to see it go false.
    const violations: string[] = [];

    for (const file of TSX_FILES) {
      const source = codeOf(file);
      const rel = relPath(file);
      const lines = source.split("\n");
      const presence = presenceIdentifiers(source);

      const offsets: number[] = [];
      let acc = 0;
      for (const l of lines) {
        offsets.push(acc);
        acc += l.length + 1;
      }

      for (let i = 0; i < lines.length; i++) {
        const open = /\{\s*([A-Za-z_$][\w$]*)\s*&&/.exec(lines[i]);
        if (!open) continue;
        const flag = open[1];
        if (presence.includes(flag)) continue;

        // Walk the conditional's own braces to its end, so the props we read belong to it.
        const start = offsets[i] + open.index;
        let depth = 0;
        let j = start;
        for (; j < source.length; j++) {
          if (source[j] === "{") depth += 1;
          else if (source[j] === "}") {
            depth -= 1;
            if (depth === 0) break;
          }
        }
        const body = source.slice(start, j + 1);
        if (!/<[A-Z]/.test(body)) continue;

        OPEN_PROP.lastIndex = 0;
        let prop: RegExpExecArray | null;
        while ((prop = OPEN_PROP.exec(body)) !== null) {
          const valueStart = prop.index + prop[0].length;
          const valueEnd = body.indexOf("}", valueStart);
          const value = body.slice(valueStart, valueEnd === -1 ? undefined : valueEnd);
          if (!namedIn(value, flag)) continue;
          violations.push(
            `${rel}:${i + 1} → \`{${flag} && …}\` mounts a component it also hands ` +
              `${prop[1]}={${value.trim()}}; the component never sees ${flag} go false, so its ` +
              "exit is unreachable",
          );
          break;
        }
      }
    }

    expect(violations).toEqual([]);
  });
});

// ─── Rule D — a cross-document view transition in an SPA ────────────

describe("motion reachability D — view transitions that can never be triggered", () => {
  const VIEW_TRANSITION_AT_RULE = GLOBALS.blocks.find((b) =>
    /^@view-transition\b/.test(b.prelude),
  );
  const VIEW_TRANSITION_PSEUDOS = GLOBALS.blocks.filter((b) =>
    /::view-transition-(old|new|group|image-pair)\b/.test(b.prelude),
  );

  /**
   * `@view-transition { navigation: auto }` opts into the CROSS-DOCUMENT flavour: it fires on a
   * real document navigation and on nothing else. A same-document transition needs
   * `document.startViewTransition`, directly or via React Router's own `viewTransition` opt-in.
   */
  const triggers = TS_FILES.filter((f) => {
    const source = codeOf(f);
    return (
      /\bstartViewTransition\s*\(/.test(source) ||
      /\bviewTransition\s*[:=]/.test(source) ||
      /\bviewTransition\b\s*\}/.test(source)
    );
  }).map(relPath);

  it("the app is a single-page router, so no cross-document navigation ever happens", () => {
    // The premise of the rule below. If this ever stops being true, the rule needs rewriting
    // rather than deleting.
    expect(readSource(ROUTER_TSX)).toContain("createBrowserRouter");
  });

  it("a declared @view-transition is backed by something that can start one", () => {
    if (!VIEW_TRANSITION_AT_RULE) return;
    expect(
      triggers,
      `${GLOBALS_REL}:${VIEW_TRANSITION_AT_RULE.line} declares @view-transition { navigation: auto }, ` +
        "which is cross-document only, in an SPA that never leaves the document and never calls " +
        "document.startViewTransition",
    ).not.toEqual([]);
  });

  it("no ::view-transition rule is left behind with nothing to trigger it", () => {
    if (VIEW_TRANSITION_AT_RULE || triggers.length > 0) return;
    expect(
      VIEW_TRANSITION_PSEUDOS.map((b) => `${GLOBALS_REL}:${b.line} → ${b.prelude}`),
    ).toEqual([]);
  });
});

// ─── Rule E — motion in a module nothing imports ────────────────────

const TESTS_DIR = path.join(ROOT, "tests");
const VITE_CONFIG = path.join(ROOT, "vite.config.ts");

/**
 * The two modules that are entry points rather than imports: `index.html` names `main.tsx` in a
 * `<script type="module">`, and `vite.config.ts` names `sw.ts` as the service-worker source.
 * Both are asserted below rather than trusted, because the day one of them is renamed this rule
 * would otherwise start reporting the app's own entry as dead motion.
 */
const ENTRY_MODULES = [path.join(SRC, "main.tsx"), path.join(SRC, "sw.ts")];

/**
 * Resolves an import specifier the way Vite resolves it for this project: `@/` is `src/`,
 * relative paths are relative to the importer, and an extensionless path picks up `.ts`/`.tsx`
 * or the directory's `index`. Bare specifiers are packages and resolve to nothing here.
 */
function resolveSpecifier(spec: string, fromFile: string): string | null {
  let base: string;
  if (spec.startsWith("@/")) base = path.join(SRC, spec.slice(2));
  else if (spec.startsWith("./") || spec.startsWith("../")) {
    base = path.resolve(path.dirname(fromFile), spec);
  } else return null;

  const candidates = [
    base,
    `${base}.ts`,
    `${base}.tsx`,
    `${base}.js`,
    path.join(base, "index.ts"),
    path.join(base, "index.tsx"),
  ];
  return (
    candidates.find((c) => existsSync(c) && statSync(c).isFile()) ?? null
  );
}

/**
 * Every project-local specifier in a file, from any position — `import … from`, a dynamic
 * `import()`, and `vi.mock("@/…")` alike. A suite that mocks a module is still naming it, and
 * this rule is looking for files nothing names at all, not files only the app names.
 */
const LOCAL_SPECIFIER = /["']((?:@\/|\.\.?\/)[^"']+)["']/g;

/** What counts as a declared motion: Tailwind's motion utilities, plus inline CSS motion. */
const MOTION_DECLARATION =
  /(?<![\w-])(?:transition-(?:all|colors|opacity|shadow|transform|none|discrete|\[)|transition(?![\w-])|animate-[a-z0-9-]+|duration-(?:\d+|\[)|ease-(?:in|out|linear|\[)|animation(?:Name|Duration)?\s*[:=])/;

describe("motion reachability E — motion in a module nothing imports", () => {
  /**
   * Deliberate exemptions, by repo-relative path. Empty, and it should stay that way: a
   * component nothing imports is never mounted, so every transition and animation it declares
   * is unreachable by construction — the same defect as a dead `@keyframes`, one level up.
   *
   * Note what is NOT in scope here: a module that is dead but declares no motion. That is a
   * dead-code question and this file only answers motion ones, which is why deleting the
   * eleven below left `SidebarToggleContainer.tsx` and `carousel.tsx` where they are.
   */
  const ORPHANED_MOTION_ALLOWLIST: string[] = [];

  const TEST_FILES = walkFiles(TESTS_DIR, [".ts", ".tsx"]);

  it("names the app's entry modules, so they are never read as orphans", () => {
    // The premise of the rule below. If either entry moves, it fails here — with the reason —
    // rather than ten lines down with `main.tsx` reported as dead motion.
    expect(readSource(INDEX_HTML)).toContain("/src/main.tsx");
    expect(readSource(VITE_CONFIG)).toContain('"sw.ts"');
  });

  it("every module under src/ that declares motion is imported by something", () => {
    const imported = new Set<string>();
    for (const file of [...TS_FILES, ...TEST_FILES]) {
      const source = stripTsComments(readSource(file));
      for (const match of source.matchAll(LOCAL_SPECIFIER)) {
        const resolved = resolveSpecifier(match[1], file);
        if (resolved) imported.add(resolved);
      }
    }

    const violations: string[] = [];
    for (const file of TS_FILES) {
      if (ENTRY_MODULES.includes(file)) continue;
      if (imported.has(file)) continue;
      if (file.endsWith(".d.ts")) continue;
      const rel = relPath(file);
      if (ORPHANED_MOTION_ALLOWLIST.includes(rel)) continue;

      const source = codeOf(file);
      const match = MOTION_DECLARATION.exec(source);
      if (!match) continue;
      const line = source.slice(0, match.index).split("\n").length;
      violations.push(
        `${rel}:${line} → declares \`${match[0]}\` and no module imports this file, ` +
          "so nothing ever mounts it and the motion cannot run",
      );
    }

    expect(violations).toEqual([]);
  });
});

// ─── Rule F — a compositor hint that outlives its animation ─────────

/**
 * The ratchet against the `will-change` anti-pattern. A hint is a standing instruction to keep an
 * element on its own compositor layer: on a phone a texture the size of the element, held for as
 * long as the declaration applies, and a full-screen one is about ten megabytes. It is worth that
 * only when it reaches the element BEFORE the motion it prepares, and only for as long as that
 * motion is coming.
 *
 * `globals.css` has none, and the compositor-hints block there argues why at length: a class that
 * carries both the hint and the `animation-name` hands them to the engine in one style
 * recalculation, so there is no earlier frame for the layer to have been built in. PR 59 wrote
 * nine of those and took all nine back out. The rules below are therefore a ratchet for the next
 * attempt rather than a guard over a hint that exists — they read on an empty list today, which is
 * the honest state and not a broken test. The live assertion is the last pair: the app's one real
 * hint is set in JavaScript, and this is what keeps it the only one.
 *
 * Three shapes, each the one the anti-pattern actually takes in a stylesheet:
 *
 *   A hint in a rule that declares no animation at all. Nothing about that rule is transient, so
 *   nothing ever takes the layer back.
 *
 *   A hint in a rule whose animation is `infinite`. That reads as transient and is not: the class
 *   stays on the element for as long as the element is drawn, which is what `.tday-empty-scene`
 *   and `.tday-empty-sparkle` do and why neither has one.
 *
 *   A hint on a class whose animation is cancelled under `prefers-reduced-motion`. This is the
 *   shape that got past review in PR 59 and the reason this rule grew a third assertion. The floor
 *   at the top of `globals.css` reaches `animation-duration` and deliberately not `animation-name`,
 *   while each block down the file cancels its own motion with `animation: none`, which does reach
 *   it. Read the base rule alone — which is all the first two assertions do — and the hint looks
 *   paired with a motion; read it under the preference and it is a layer reserved for a keyframe
 *   that never runs, for every user who asked for less of exactly this.
 *
 * What this still cannot see is how long a class stays on an element, because the answer is in
 * React and not in the stylesheet. That is the compositor-hints block's paragraph, and it is the
 * reviewer's job.
 */
describe("motion reachability F — every compositor hint is transient", () => {
  const ruleKey = (decl: CssDeclaration) => `${decl.selectorLine}:${decl.selector}`;
  const underReducedMotion = (decl: CssDeclaration): boolean =>
    decl.media.some((prelude) => /prefers-reduced-motion/.test(prelude));

  /** Every `animation*` declaration in globals.css, grouped by the rule that owns it. */
  const animationDecls = new Map<string, CssDeclaration[]>();
  for (const decl of GLOBALS.decls) {
    if (!decl.prop.startsWith("animation")) continue;
    const key = ruleKey(decl);
    animationDecls.set(key, [...(animationDecls.get(key) ?? []), decl]);
  }

  // `will-change: auto` is the initial value and reserves nothing — it is how a rule GIVES a hint
  // back, which is exactly what the third assertion below asks for, so counting it as a hint would
  // make that assertion unsatisfiable.
  const hints = GLOBALS.decls.filter(
    (decl) => decl.prop === "will-change" && !/^auto$/i.test(decl.value),
  );

  const namesAnAnimation = (decl: CssDeclaration): boolean =>
    (animationDecls.get(ruleKey(decl)) ?? []).some(
      (d) => d.prop === "animation" || d.prop === "animation-name",
    );

  const runsForever = (decl: CssDeclaration): boolean =>
    (animationDecls.get(ruleKey(decl)) ?? []).some((d) =>
      /(?<![\w-])infinite(?![\w-])/.test(d.value),
    );

  it("every will-change sits in a rule that also declares an animation", () => {
    const violations = hints.filter((decl) => !namesAnAnimation(decl)).map(
      (decl) =>
        `${GLOBALS_REL}:${decl.line} → ${decl.selector} { will-change: ${decl.value} } — the rule ` +
        "declares no `animation`, so the class is not a motion and the layer it reserves is permanent",
    );

    expect(violations).toEqual([]);
  });

  it("no will-change sits in a rule whose animation never ends", () => {
    const violations = hints.filter(runsForever).map(
      (decl) =>
        `${GLOBALS_REL}:${decl.line} → ${decl.selector} { will-change: ${decl.value} } — the rule's ` +
        "animation is `infinite`, so this is an always-on hint wearing an animation's clothes",
    );

    expect(violations).toEqual([]);
  });

  it("a hint is given back wherever reduced motion cancels the animation it was for", () => {
    // Matched on the selector text, which is what a hint and its own reduced-motion override share
    // — `.tday-duck-enter { will-change: … }` up the file and `.tday-duck-enter { animation: none }`
    // inside the `@media`. Anything more clever would be a selector engine, and this file is
    // explicit that it is not one.
    const violations: string[] = [];

    for (const off of GLOBALS.decls) {
      if (!underReducedMotion(off)) continue;
      if (off.prop !== "animation" && off.prop !== "animation-name") continue;
      if (!/^none$/i.test(off.value)) continue;

      const hinted = hints.filter(
        (hint) => hint.selector === off.selector && !underReducedMotion(hint),
      );
      if (hinted.length === 0) continue;

      const givenBack = GLOBALS.decls.some(
        (decl) =>
          ruleKey(decl) === ruleKey(off) &&
          decl.prop === "will-change" &&
          /^auto$/i.test(decl.value),
      );
      if (givenBack) continue;

      violations.push(
        `${GLOBALS_REL}:${off.line} → ${off.selector} { ${off.prop}: none } cancels the animation ` +
          `hinted at ${GLOBALS_REL}:${hinted[0].line}, but does not add \`will-change: auto\` — under ` +
          "`prefers-reduced-motion` that class is a compositor layer held for a keyframe that never runs",
      );
    }

    expect(violations).toEqual([]);
  });

  it("globals.css is the only stylesheet, and the only markup, that names one", () => {
    // The other door into the same defect: a `will-change` in a feature stylesheet, or Tailwind's
    // `will-change-*` utility in a class string, where no rule above would ever look at it. Both
    // are always-on by construction — a utility sits on an element for that element's whole life.
    const violations: string[] = [];

    for (const file of walkFiles(SRC, [".css"])) {
      if (file === GLOBALS_CSS) continue;
      const source = stripCssComments(readSource(file));
      const match = /will-change/.exec(source);
      if (!match) continue;
      violations.push(
        `${relPath(file)}:${source.slice(0, match.index).split("\n").length} → declares ` +
          "`will-change` outside globals.css, where rule F cannot see whether it ever ends",
      );
    }

    for (const file of TS_FILES) {
      const source = codeOf(file);
      const match = /(?<![\w-])will-change-[a-z[]/.exec(source);
      if (!match) continue;
      violations.push(
        `${relPath(file)}:${source.slice(0, match.index).split("\n").length} → renders ` +
          `\`${match[0]}…\`, a utility that stays on the element for as long as the element does`,
      );
    }

    expect(violations).toEqual([]);
  });

  it("the one hint set from JavaScript is the header's, and it clears what it sets", () => {
    // The assertion that is not a vacuum, and the reason the three above are allowed to be one.
    // A hint written through `element.style` escapes every rule here — no selector, no `@media`,
    // nothing a stylesheet reader can reach — so the ratchet on it has to be "there is exactly one
    // file, and it is the one whose argument was reviewed". `RootFeedHeroHeader` earns it by
    // setting the hint when a scroll pass begins, a frame before the writes it prepares, and
    // dropping it on a timer; a second file wanting the same has to come here and say why.
    const HEADER = path.join(SRC, "components/app/RootFeedHeroHeader.tsx");

    const setters = TS_FILES.filter((file) => /(?<![\w-])willChange(?![\w-])/.test(codeOf(file)))
      .map(relPath)
      .sort();
    expect(setters).toEqual([relPath(HEADER)]);

    // Whatever it sets, it also gives back. A setter with no reset is the permanent layer under a
    // different spelling, and it is the spelling no other assertion in this file can see. The
    // value assigned is not checked — it is a variable here, and a test that insisted on a literal
    // would only push the next author into inlining one.
    const header = codeOf(HEADER);
    const assignments = [...header.matchAll(/\.willChange = ([^;]+);/g)].map((m) => m[1].trim());
    expect(assignments.length).toBeGreaterThan(1);
    expect(assignments).toContain('""');
  });
});
