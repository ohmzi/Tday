import { existsSync, readdirSync, readFileSync } from "fs";
import path from "path";
import { describe, expect, it } from "vitest";

/**
 * `RegExpMatchArray.index` is typed optional because a match object can be
 * constructed by hand, but it is always set on one produced by `exec()` or
 * `matchAll()` — which is the only way this file makes them. Reading it through
 * this helper keeps the invariant explicit: a non-null assertion would let a
 * future refactor that passes in a synthetic match silently scan from offset 0
 * and report the wrong line, which for a guardrail is worse than crashing.
 */
function matchIndex(match: RegExpMatchArray): number {
  if (match.index === undefined) {
    throw new Error(`regex match carries no index: ${JSON.stringify(match[0]?.slice(0, 60))}`);
  }
  return match.index;
}

/**
 * Animation specs on Android get written, reviewed and merged while being UNREACHABLE.
 * The spec sits in the file, reads correctly, names its durations and easings — and can
 * never run. Review does not catch it, because nothing about the spec is wrong; what is
 * wrong is the composition around it. A `fadeOut` over content that has already emptied,
 * a 320 ms exit on a flag nothing ever sets false, an `animateDpAsState` whose target is
 * fixed for the life of the composition, an enter on an `AnimatedVisibility` that enters
 * composition already visible — all four read as working motion and all four are dead.
 *
 * Nothing else in this repo asserts reachability. There is no Compose UI test run on CI,
 * no device in the loop, and a Gradle unit test cannot see composition structure either.
 * So this is a source scanner, in the one place that actually runs on every PR: the web
 * guardrail suite, which already reads Kotlin and Swift from vitest (see
 * `android-standards.test.ts`, `settings-icons.test.ts`) and finishes in milliseconds on
 * Linux with no Mac, no Gradle and no emulator.
 *
 * Each rule below is here because it caught a real, shipped instance. They are written to
 * be proof-positive: a site is reported only when the defect can be shown from the source,
 * never on suspicion, so an unfamiliar construct is passed over rather than guessed at.
 */

const ROOT = path.resolve(__dirname, "..", "..");
const MONO = path.resolve(ROOT, "..");
const ANDROID_SRC = path.join(
  MONO,
  "android-compose",
  "app",
  "src",
  "main",
  "java",
  "com",
  "ohmz",
  "tday",
  "compose",
);

function walkFiles(dir: string, ext: string): string[] {
  const results: string[] = [];
  if (!existsSync(dir)) return results;
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      results.push(...walkFiles(full, ext));
    } else if (entry.name.endsWith(ext)) {
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

const skipAndroid = !existsSync(ANDROID_SRC);
const describeAndroid = skipAndroid ? describe.skip : describe;
const ALL_KT = skipAndroid ? [] : walkFiles(ANDROID_SRC, ".kt");

// ---------------------------------------------------------------------------
// Source model
//
// Every rule reads a *masked* copy of the file: comment bodies and string-literal
// contents are replaced with spaces, byte for byte, so offsets and line numbers still
// line up with the original. Masking is what makes brace and paren matching honest — a
// `)` inside a KDoc example or a `"$name)"` template would otherwise close a call early
// and the scanner would silently analyse the wrong span.
// ---------------------------------------------------------------------------

function maskNoise(source: string): string {
  const out = source.split("");
  let i = 0;
  const blank = (from: number, to: number) => {
    for (let k = from; k < to && k < out.length; k++) {
      if (out[k] !== "\n") out[k] = " ";
    }
  };
  while (i < source.length) {
    const two = source.slice(i, i + 2);
    if (two === "//") {
      const end = source.indexOf("\n", i);
      blank(i, end === -1 ? source.length : end);
      i = end === -1 ? source.length : end;
    } else if (two === "/*") {
      const end = source.indexOf("*/", i + 2);
      const stop = end === -1 ? source.length : end + 2;
      blank(i, stop);
      i = stop;
    } else if (source.startsWith('"""', i)) {
      const end = source.indexOf('"""', i + 3);
      const stop = end === -1 ? source.length : end + 3;
      blank(i + 3, stop - 3);
      i = stop;
    } else if (source[i] === '"') {
      let k = i + 1;
      while (k < source.length && source[k] !== '"' && source[k] !== "\n") {
        if (source[k] === "\\") k++;
        k++;
      }
      blank(i + 1, k);
      i = k + 1;
    } else {
      i++;
    }
  }
  return out.join("");
}

/** 1-indexed line number of a character offset, for reporting `file:line`. */
function lineOf(source: string, index: number): number {
  let line = 1;
  for (let i = 0; i < index && i < source.length; i++) {
    if (source[i] === "\n") line++;
  }
  return line;
}

/** Index of the delimiter closing the one at `open`, or -1. */
function matchDelimiter(masked: string, open: number): number {
  const pairs: Record<string, string> = { "(": ")", "{": "}", "[": "]" };
  const closer = pairs[masked[open]];
  if (!closer) return -1;
  let depth = 0;
  for (let i = open; i < masked.length; i++) {
    const ch = masked[i];
    if (ch === "(" || ch === "{" || ch === "[") depth++;
    else if (ch === ")" || ch === "}" || ch === "]") {
      depth--;
      if (depth === 0) return ch === closer ? i : -1;
    }
  }
  return -1;
}

interface KotlinCall {
  /** Offset of the callee name. */
  start: number;
  /** Offset of `(` and of its matching `)`. */
  argsOpen: number;
  argsClose: number;
  /** Raw argument text between the parens. */
  args: string;
  /** Trailing lambda body, if the call has one. */
  lambda: string | null;
  lambdaOpen: number;
  lambdaClose: number;
}

function findCalls(masked: string, callee: string): KotlinCall[] {
  const calls: KotlinCall[] = [];
  const pattern = new RegExp(`\\b${callee}\\s*\\(`, "g");
  for (const match of masked.matchAll(pattern)) {
    const start = matchIndex(match);
    const argsOpen = start + match[0].length - 1;
    const argsClose = matchDelimiter(masked, argsOpen);
    if (argsClose === -1) continue;
    let lambdaOpen = -1;
    let lambdaClose = -1;
    let cursor = argsClose + 1;
    while (cursor < masked.length && /\s/.test(masked[cursor])) cursor++;
    if (masked[cursor] === "{") {
      lambdaOpen = cursor;
      lambdaClose = matchDelimiter(masked, cursor);
    }
    calls.push({
      start,
      argsOpen,
      argsClose,
      args: masked.slice(argsOpen + 1, argsClose),
      lambda:
        lambdaOpen !== -1 && lambdaClose !== -1
          ? masked.slice(lambdaOpen + 1, lambdaClose)
          : null,
      lambdaOpen,
      lambdaClose,
    });
  }
  return calls;
}

/** Split on `separator` at nesting depth zero (arguments, `&&` chains, `||` chains). */
function splitTopLevel(text: string, separator: string): string[] {
  const parts: string[] = [];
  let depth = 0;
  let current = "";
  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    if (ch === "(" || ch === "{" || ch === "[") depth++;
    if (ch === ")" || ch === "}" || ch === "]") depth--;
    if (depth === 0 && text.startsWith(separator, i)) {
      parts.push(current);
      current = "";
      i += separator.length - 1;
      continue;
    }
    current += ch;
  }
  parts.push(current);
  return parts.map((part) => part.trim()).filter((part) => part.length > 0);
}

function namedArgument(args: string, name: string): string | null {
  for (const arg of splitTopLevel(args, ",")) {
    const match = /^([A-Za-z_]\w*)\s*=\s*([\s\S]+)$/.exec(arg);
    if (match && match[1] === name) return match[2].trim();
  }
  return null;
}

const collapse = (text: string) => text.replace(/\s+/g, " ").trim();

interface Declaration {
  kind: "val" | "var";
  /** True for `by remember { … }` — a delegated read, never a plain constant. */
  delegated: boolean;
  isConst: boolean;
  rhs: string;
  index: number;
}

/**
 * Right-hand side of a `val`/`var`, followed across the line breaks Kotlin allows inside
 * one expression: a continuation line opening with `.`, `?.`, an operator or a closer is
 * still the same declaration, anything else starts the next statement.
 */
function declarationRhs(masked: string, equals: number): string {
  let depth = 0;
  let i = equals + 1;
  for (; i < masked.length; i++) {
    const ch = masked[i];
    if (ch === "(" || ch === "{" || ch === "[") depth++;
    else if (ch === ")" || ch === "}" || ch === "]") {
      if (depth === 0) break;
      depth--;
    } else if (ch === "\n" && depth === 0) {
      const nextLine = /^[ \t]*(\S)/.exec(masked.slice(i + 1, i + 400));
      const lead = nextLine?.[1];
      if (!lead || !/[.?+\-*/&|)]/.test(lead)) break;
    }
  }
  return masked.slice(equals + 1, i).trim();
}

function declarationsOf(masked: string): Map<string, Declaration> {
  const declarations = new Map<string, Declaration>();
  const pattern = /\b(const\s+)?(val|var)\s+([A-Za-z_]\w*)\s*(:[^=\n]+)?(by|=)\s/g;
  for (const match of masked.matchAll(pattern)) {
    const name = match[3];
    // First declaration wins: a name shadowed in a nested scope is exactly the case
    // where the scanner should stay quiet rather than reason about the wrong binding.
    if (declarations.has(name)) continue;
    const delegated = match[5] === "by";
    const equals = matchIndex(match) + match[0].length - 1;
    declarations.set(name, {
      kind: match[2] as "val" | "var",
      delegated,
      isConst: Boolean(match[1]),
      rhs: delegated ? "" : declarationRhs(masked, equals),
      index: matchIndex(match),
    });
  }
  return declarations;
}

// ---------------------------------------------------------------------------
// Rule A — content empties as the exit starts
// ---------------------------------------------------------------------------

describeAndroid("Rule A — AnimatedVisibility content survives its own exit", () => {
  /**
   * `AnimatedVisibility` keeps its content composed for the whole exit; that is the only
   * reason an exit can be seen at all. So the content lambda must not be written against
   * the same state that drives `visible`, because that state is already gone on the frame
   * `visible` flips false. `TdayToastHost.kt` did exactly this: a 140 ms fadeOut + 180 ms
   * slideOut, and a content lambda opening `val visibleToast = toast ?: return@…`, so the
   * card vanished on the first frame of the exit and the exit played over nothing.
   *
   * The fix is always the same shape: retain the last non-null value and draw that.
   */
  it("content lambda does not bail out while the exit is still playing", () => {
    const violations: string[] = [];
    for (const file of ALL_KT) {
      const source = readSource(file);
      const masked = maskNoise(source);
      for (const call of findCalls(masked, "AnimatedVisibility")) {
        if (!call.lambda) continue;
        const bail = call.lambda.indexOf("return@AnimatedVisibility");
        if (bail !== -1) {
          const at = lineOf(source, call.lambdaOpen + 1 + bail);
          violations.push(
            `${relPath(file)}:${at} → return@AnimatedVisibility empties the content on the ` +
              `frame 'visible' flips false; the exit then plays over nothing`,
          );
        }
      }
    }
    expect(violations).toEqual([]);
  });

  it("content lambda does not render from the nullable that drives visibility", () => {
    const violations: string[] = [];
    for (const file of ALL_KT) {
      const source = readSource(file);
      const masked = maskNoise(source);
      for (const call of findCalls(masked, "AnimatedVisibility")) {
        if (!call.lambda) continue;
        const visible = namedArgument(call.args, "visible");
        const nullCheck = visible && /^([A-Za-z_]\w*)\s*!=\s*null$/.exec(collapse(visible));
        if (!nullCheck) continue;
        const name = nullCheck[1];
        const deref = new RegExp(`\\b${name}\\s*(!!|\\?\\.|\\?:)`).exec(call.lambda);
        if (deref) {
          const at = lineOf(source, call.lambdaOpen + 1 + matchIndex(deref));
          violations.push(
            `${relPath(file)}:${at} → content reads '${name}', the same nullable that drives ` +
              `visible; it is null for the whole exit`,
          );
        }
      }
    }
    expect(violations).toEqual([]);
  });
});

// ---------------------------------------------------------------------------
// Rule B — write-once visibility flag
// ---------------------------------------------------------------------------

/**
 * Two sheets used to drive an `AnimatedVisibility` from a local `var` that was set true
 * once and never set false, so the exit half of the spec was dead code: the host `Dialog`
 * was torn down on dismiss and the sheet was cut, not slid. Both were exempted here BY
 * NAME while their fix — `and-create-sheet-dismiss-cut`, PR 15a — was a different unit of
 * work, and the exemption was self-closing: the test below asserts each listed site still
 * has the defect, so an entry cannot outlive the bug.
 *
 * PR 15a landed and the list is empty. Both sheets now start the exit and hand control
 * back to the caller only once the transition has settled, so neither has a write-once
 * flag left to exempt. The list stays because the mechanism is the point: a future
 * exemption is an entry plus the ledger row that deletes it again, never a rule turned off.
 */
const RULE_B_PENDING_FIX: string[] = [];

function writeOnceVisibilityFlags(file: string): string[] {
  const source = readSource(file);
  const masked = maskNoise(source);
  const declarations = declarationsOf(masked);
  const found: string[] = [];
  for (const call of findCalls(masked, "AnimatedVisibility")) {
    const visible = namedArgument(call.args, "visible");
    if (!visible) continue;
    const name = /^[A-Za-z_]\w*$/.test(collapse(visible)) ? collapse(visible) : null;
    if (!name) continue;
    const declaration = declarations.get(name);
    if (!declaration || declaration.kind !== "var") continue;
    // An explicitly written exit is what makes this reportable: the spec is in the file,
    // it names durations, and it is unreachable.
    const exit = namedArgument(call.args, "exit");
    if (!exit) continue;
    const assignments = [
      ...masked.matchAll(new RegExp(`(?:^|[^.\\w])${name}\\s*=\\s*([^=\\n][^\\n]*)`, "g")),
    ].filter((match) => matchIndex(match) !== declaration.index);
    if (assignments.length === 0) continue;
    const everSetFalse = assignments.some((match) => /^false\b/.test(match[1].trim()));
    // An assignment of anything other than a literal is a value we cannot read here, so
    // the flag is not provably write-once and the site is left alone.
    const opaque = assignments.some((match) => !/^(true|false)\b/.test(match[1].trim()));
    if (everSetFalse || opaque) continue;
    found.push(
      `${relPath(file)}:${lineOf(source, call.start)} → '${name}' is never set false, so the ` +
        `exit spec at this AnimatedVisibility is dead code`,
    );
  }
  return found;
}

describeAndroid("Rule B — an exit spec needs a flag that can go false", () => {
  it("no AnimatedVisibility exit hangs off a write-once flag", () => {
    const violations: string[] = [];
    for (const file of ALL_KT) {
      if (RULE_B_PENDING_FIX.includes(relPath(file))) continue;
      violations.push(...writeOnceVisibilityFlags(file));
    }
    expect(violations).toEqual([]);
  });

  it("every exempted site still has the defect the exemption is for", () => {
    const stale = RULE_B_PENDING_FIX.filter(
      (rel) => writeOnceVisibilityFlags(path.join(MONO, rel)).length === 0,
    );
    expect(
      stale,
      "these sites are fixed — delete them from RULE_B_PENDING_FIX and tick their ledger row",
    ).toEqual([]);
  });
});

// ---------------------------------------------------------------------------
// Rule C — composition-constant animation target
// ---------------------------------------------------------------------------

/**
 * `animateDpAsState` and friends animate towards a target. If that target cannot change
 * for the life of the composition, the animation never runs a single frame: it settles on
 * its initial value and stays there. `CreateTaskBottomSheet.kt` animated a height built
 * from `LocalConfiguration.screenHeightDp` and two `const val` fractions — fixed for the
 * session — and `OnboardingWizardOverlay.kt` animated a literal `1.dp` towards itself.
 * Both read as motion in review. Neither could move.
 *
 * The analysis is proof-positive and deliberately small: a target is reported only when
 * every identifier in it can be walked back to a literal, a `const val`, or the screen
 * metrics, with no branch anywhere in the expression. Anything the scanner cannot resolve
 * — a parameter, a state read, a call it does not know — makes the target unknown, and
 * unknown targets are never reported.
 */

/** Reading these cannot change without recreating the composition around it. */
const CONSTANT_ROOTS = new Set(["LocalConfiguration"]);

/** Pure members/operations that pass a constant through unchanged. */
const CONSTANT_PRESERVING = new Set([
  "current",
  "screenHeightDp",
  "screenWidthDp",
  "dp",
  "sp",
  "coerceAtMost",
  "coerceAtLeast",
  "coerceIn",
  "times",
  "div",
  "plus",
  "minus",
  "toFloat",
  "toInt",
  "roundToInt",
]);

const ANIMATE_AS_STATE = [
  "animateDpAsState",
  "animateFloatAsState",
  "animateIntAsState",
  "animateColorAsState",
  "animateOffsetAsState",
  "animateSizeAsState",
  "animateValueAsState",
];

function isCompositionConstant(
  expression: string,
  declarations: Map<string, Declaration>,
  depth = 0,
): boolean {
  if (depth > 6) return false;
  const text = collapse(expression);
  if (text.length === 0) return false;
  // A branch, an elvis, or a lambda means the value depends on something that can change.
  if (/\bif\b|\bwhen\b|\?:|->/.test(text)) return false;
  const identifiers = text
    .replace(/\b\d[\d_]*(\.\d+)?[fFLdD]?/g, " ")
    .match(/[A-Za-z_]\w*/g);
  if (!identifiers) return true; // a bare literal, e.g. `1.dp`
  for (const identifier of identifiers) {
    if (CONSTANT_ROOTS.has(identifier)) continue;
    if (CONSTANT_PRESERVING.has(identifier)) continue;
    const declaration = declarations.get(identifier);
    if (!declaration) return false;
    if (declaration.delegated || declaration.kind === "var") return false;
    if (declaration.isConst) {
      if (!/^-?\d[\d_]*(\.\d+)?[fFLdD]?$/.test(collapse(declaration.rhs))) return false;
      continue;
    }
    if (!isCompositionConstant(declaration.rhs, declarations, depth + 1)) return false;
  }
  return true;
}

describeAndroid("Rule C — an animated target has to be able to change", () => {
  it("no animate*AsState animates towards a composition-constant target", () => {
    const violations: string[] = [];
    for (const file of ALL_KT) {
      const source = readSource(file);
      const masked = maskNoise(source);
      const declarations = declarationsOf(masked);
      for (const callee of ANIMATE_AS_STATE) {
        for (const call of findCalls(masked, callee)) {
          const named = namedArgument(call.args, "targetValue");
          const positional = splitTopLevel(call.args, ",")[0] ?? "";
          const target = named ?? (positional.includes("=") ? "" : positional);
          if (!target) continue;
          if (!isCompositionConstant(target, declarations)) continue;
          violations.push(
            `${relPath(file)}:${lineOf(source, call.start)} → ${callee} targets ` +
              `'${collapse(target)}', which is fixed for the life of the composition; ` +
              `the animation can never run`,
          );
        }
      }
    }
    expect(violations).toEqual([]);
  });
});

// ---------------------------------------------------------------------------
// Rule D — a guard that implies `visible`
// ---------------------------------------------------------------------------

/**
 * An `AnimatedVisibility` only plays its enter when it is composed with `visible` false
 * and then flipped true. Put it behind a guard that is implied by `visible` — a lazy
 * `item {}` that exists only once the same conditions hold — and the two become true on
 * the same frame: the node enters composition with initialState == targetState and the
 * enter is skipped entirely. `TodoListScreen.kt`'s Earlier empty scene did this and 34 %
 * of the screen snapped open with a 190 ms fade+expand spec sitting right above it.
 *
 * The fix is a `MutableTransitionState` remembered ABOVE the guard, seeded from the live
 * visibility value, so it survives the guard mounting and unmounting. Seeded from `false`
 * instead it would animate the scene in on every cold entry into an already-finished
 * Today, which is a behaviour change nobody asked for — hence the second test: hoisted,
 * not merely used.
 */

interface GuardedVisibility {
  call: KotlinCall;
  guardCondition: string;
  guardIndex: number;
}

function enclosingGuard(masked: string, index: number): { condition: string; index: number } | null {
  let best: { condition: string; index: number } | null = null;
  for (const match of masked.matchAll(/\bif\s*\(/g)) {
    const open = matchIndex(match) + match[0].length - 1;
    const close = matchDelimiter(masked, open);
    if (close === -1 || close > index) continue;
    let cursor = close + 1;
    while (cursor < masked.length && /\s/.test(masked[cursor])) cursor++;
    if (masked[cursor] !== "{") continue;
    const blockEnd = matchDelimiter(masked, cursor);
    if (blockEnd === -1 || index < cursor || index > blockEnd) continue;
    if (!best || matchIndex(match) > best.index) {
      best = { condition: masked.slice(open + 1, close), index: matchIndex(match) };
    }
  }
  return best;
}

function guardedVisibilities(masked: string): GuardedVisibility[] {
  const guarded: GuardedVisibility[] = [];
  for (const call of findCalls(masked, "AnimatedVisibility")) {
    const guard = enclosingGuard(masked, call.start);
    if (!guard) continue;
    guarded.push({ call, guardCondition: guard.condition, guardIndex: guard.index });
  }
  return guarded;
}

/** Flatten a boolean expression to its `&&` terms, expanding local `val`s one name at a time. */
function conjunctsOf(
  expression: string,
  declarations: Map<string, Declaration>,
  depth = 0,
): string[] {
  const terms: string[] = [];
  for (const term of splitTopLevel(expression, "&&")) {
    const text = collapse(term);
    const bare = /^[A-Za-z_]\w*$/.test(text) ? declarations.get(text) : undefined;
    if (bare && bare.kind === "val" && !bare.delegated && depth < 6 && bare.rhs.includes("&&")) {
      terms.push(...conjunctsOf(bare.rhs, declarations, depth + 1));
    } else if (bare && bare.kind === "val" && !bare.delegated && depth < 6 && bare.rhs) {
      terms.push(collapse(bare.rhs));
    } else {
      terms.push(text);
    }
  }
  return terms;
}

describeAndroid("Rule D — an enter spec needs a frame where it is not yet visible", () => {
  it("no AnimatedVisibility sits behind a guard its own 'visible' implies", () => {
    const violations: string[] = [];
    for (const file of ALL_KT) {
      const source = readSource(file);
      const masked = maskNoise(source);
      const declarations = declarationsOf(masked);
      for (const { call, guardCondition } of guardedVisibilities(masked)) {
        const visible = namedArgument(call.args, "visible");
        if (!visible) continue;
        const guardTerms = conjunctsOf(guardCondition, declarations);
        if (guardTerms.length === 0) continue;
        for (const branch of splitTopLevel(visible, "||")) {
          const branchTerms = new Set(conjunctsOf(branch, declarations));
          // `visible` implies the guard: the branch cannot turn true without the guard
          // already being true, so the guard can mount this node already visible.
          const implies = guardTerms.every((term) => branchTerms.has(term));
          if (!implies) continue;
          violations.push(
            `${relPath(file)}:${lineOf(source, call.start)} → guard '${collapse(guardCondition)}' ` +
              `is implied by visible branch '${collapse(branch)}', so this enters composition ` +
              `already visible and the enter spec never plays`,
          );
          break;
        }
      }
    }
    expect(violations).toEqual([]);
  });

  it("a guarded visibleState is remembered above its guard, not inside it", () => {
    const violations: string[] = [];
    for (const file of ALL_KT) {
      const source = readSource(file);
      const masked = maskNoise(source);
      const declarations = declarationsOf(masked);
      for (const { call, guardIndex, guardCondition } of guardedVisibilities(masked)) {
        const state = namedArgument(call.args, "visibleState");
        if (!state) continue;
        const name = collapse(state);
        if (!/^[A-Za-z_]\w*$/.test(name)) continue;
        const declaration = declarations.get(name);
        if (declaration && declaration.index < guardIndex) continue;
        violations.push(
          `${relPath(file)}:${lineOf(source, call.start)} → '${name}' is remembered inside ` +
            `guard '${collapse(guardCondition)}', so it is re-seeded every time the guard ` +
            `mounts and the transition has no earlier state to animate from`,
        );
      }
    }
    expect(violations).toEqual([]);
  });
});

// ---------------------------------------------------------------------------
// The canary
//
// Every rule above passes vacuously if the walk finds nothing — a moved package, a renamed
// directory, a bad join — and a suite that passes on an empty file list is worse than no
// suite, because it reports green. These numbers are floors, not counts: they only have to
// be updated when the app genuinely shrinks.
// ---------------------------------------------------------------------------

describeAndroid("the scanner is actually reading the app", () => {
  it("walks the Compose source tree", () => {
    expect(ALL_KT.length).toBeGreaterThan(50);
  });

  it("finds the animation call sites the rules are about", () => {
    let animatedVisibility = 0;
    let animateAsState = 0;
    for (const file of ALL_KT) {
      const masked = maskNoise(readSource(file));
      animatedVisibility += findCalls(masked, "AnimatedVisibility").length;
      for (const callee of ANIMATE_AS_STATE) {
        animateAsState += findCalls(masked, callee).length;
      }
    }
    expect(animatedVisibility).toBeGreaterThanOrEqual(8);
    expect(animateAsState).toBeGreaterThanOrEqual(80);
  });
});
