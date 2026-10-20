import { existsSync, readdirSync, readFileSync } from "fs";
import path from "path";
import { describe, expect, it } from "vitest";

/**
 * THE EIGHTEENTH TRIPLET
 *
 * `press-affordance-unification` exists because Android wrote the same three
 * statements seventeen times — read the press, animate a scale, animate an offset —
 * and the copies agreed about the shape while disagreeing about the number, which is
 * how one app ended up pressing to seven different depths. `Modifier.tdayPressable`
 * is that shape written once. Nothing about the migration stops an eighteenth copy:
 * the triplet is four lines, it reads correctly, it reviews clean, and the only thing
 * wrong with it is that the number in it was invented at the call site. That is
 * exactly the defect a reviewer cannot see and a grep can.
 *
 * This is the Android half of the row. `press-affordance-cascade.test.ts` next door
 * guards the web half — the layer the global press rule has to sit in — and the two
 * are deliberately separate files: they assert different things about different
 * languages and share nothing but the row they close.
 *
 * WHAT IS BANNED, PRECISELY. A composable that reads its own press with
 * `collectIsPressedAsState` and squashes to a float literal of its own. Not every
 * pressed-driven number in [0.90, 1.0) — the tree has several that are deliberately
 * not press depths, and lumping them in would make the rule something people switch
 * off rather than something they obey:
 *
 *   - `TdaySegmentedSlider.kt`'s press halo scales from 0.92 to 1 when a finger
 *     lands. The literal is the RESTING value: the halo grows into view rather than
 *     sinking, so there is no depth in it to name. The rule reads which branch of the
 *     conditional the press selects and reports only that one, which is why the halo
 *     is not on the allowlist below — an entry for it would have been stale on the
 *     day it was written.
 *   - `RootFeedDock.kt:555` multiplies the token'd press scale by a 0.94→1 label
 *     expansion. No conditional, so nothing to mistake for a depth.
 *   - The two swipe-action buttons DO squash to 0.92 under a finger and are the
 *     allowlist, because their 0.92 is one factor of a composed transform rather than
 *     the scale a finger sees. That argument is written at both call sites and
 *     `docs/motion.md`'s "Deliberate non-tokens" table carries it; the second test
 *     below asserts it is still there, because an allowlist entry that outlives its
 *     reason is a rule turned off.
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

/** The one file allowed to pair a press with a scale: the shape written once. */
const MODIFIER = path.join(ANDROID_SRC, "core", "ui", "TdayPressable.kt");

/**
 * The two sites whose literal is argued rather than invented, by composable rather
 * than by file: `CalendarScreen.kt` is three thousand lines with three composables
 * that read a press, and allowlisting the file would blind the rule across all of it.
 */
const ARGUED: { rel: string; composable: string }[] = [
  {
    rel: "android-compose/app/src/main/java/com/ohmz/tday/compose/core/ui/TaskSwipeActionButton.kt",
    composable: "TaskSwipeActionButton",
  },
  {
    rel: "android-compose/app/src/main/java/com/ohmz/tday/compose/feature/calendar/CalendarScreen.kt",
    composable: "CalendarSwipeActionButton",
  },
];

/** The argument each allowlisted site has to keep carrying, spelled as it is written. */
const ARGUMENT_MARKER = "not a token — see docs/motion.md";

function walkKotlin(dir: string): string[] {
  const results: string[] = [];
  if (!existsSync(dir)) return results;
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) results.push(...walkKotlin(full));
    else if (entry.name.endsWith(".kt")) results.push(full);
  }
  return results;
}

const skipAndroid = !existsSync(ANDROID_SRC);
const describeAndroid = skipAndroid ? describe.skip : describe;
const ALL_KT = skipAndroid ? [] : walkKotlin(ANDROID_SRC);

/**
 * Comment bodies and string contents replaced with spaces, byte for byte, so offsets
 * and line numbers still line up. The same masking `motion-reachability-android.test.ts`
 * does and for the same two reasons: brace matching is only honest over masked text,
 * and — here especially — a call site's own comment arguing for its literal must not
 * be able to trip the rule it is arguing about.
 */
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

/**
 * `RegExpMatchArray.index` is optional in the type because a match can be built by
 * hand; it is always set on one from `matchAll()`, which is the only way this file
 * makes them. Reading it through a helper keeps that explicit — a non-null assertion
 * would let a synthetic match scan from offset 0 and report the wrong line, and a
 * guardrail pointing at the wrong line is worse than one that crashes.
 */
function matchIndex(match: RegExpMatchArray): number {
  if (match.index === undefined) {
    throw new Error(`regex match carries no index: ${JSON.stringify(match[0]?.slice(0, 60))}`);
  }
  return match.index;
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

interface ComposableBlock {
  name: string;
  /** Offsets of the body's `{` and its match. */
  start: number;
  end: number;
}

/**
 * Every `@Composable fun`, with its brace-matched body.
 *
 * The generic form matters: `fun <T> TdaySegmentedSlider(` is one of the ten
 * composables in the tree that read a press, and a signature pattern that misses it
 * would not fail — it would quietly stop scanning the file. That is why the test
 * below asserts every `collectIsPressedAsState` in the tree lands inside a block this
 * function found.
 */
function composableBlocks(masked: string): ComposableBlock[] {
  const blocks: ComposableBlock[] = [];
  const signature = /\bfun\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?(\w+)\s*\(/g;
  for (const annotation of masked.matchAll(/@Composable\b/g)) {
    signature.lastIndex = matchIndex(annotation);
    const declaration = signature.exec(masked);
    if (!declaration) continue;
    const paramsOpen = matchIndex(declaration) + declaration[0].length - 1;
    const paramsClose = matchDelimiter(masked, paramsOpen);
    if (paramsClose === -1) continue;
    // Skip the return type, if any, to the body's opening brace. An expression-bodied
    // composable has no brace and no room for a press triplet, so it is passed over.
    let cursor = paramsClose + 1;
    while (cursor < masked.length && cursor < paramsClose + 300 && masked[cursor] !== "{") cursor++;
    if (masked[cursor] !== "{") continue;
    const bodyClose = matchDelimiter(masked, cursor);
    if (bodyClose === -1) continue;
    blocks.push({ name: declaration[1], start: cursor, end: bodyClose });
  }
  return blocks;
}

/**
 * The `if (` that opens a conditional. Its condition and its two branches are read by
 * delimiter matching below rather than by more regex, because the branches are the
 * half a pattern gets wrong: ktlint wraps a long `targetValue =` into braced branches
 * across five lines, and a rule that can only read `if (a) b else c` on one line is
 * evaded by running the formatter.
 */
const PRESS_IF = /\bif\s*\(/g;
const BARE_FLOAT = /^\s*(0\.9\d*)f?\s*$/;

const WORD = /[\w$]/;

/** True if `word` sits at `at` as a whole token rather than inside an identifier. */
function keywordAt(masked: string, at: number, word: string): boolean {
  if (!masked.startsWith(word, at)) return false;
  if (at > 0 && WORD.test(masked[at - 1])) return false;
  const after = masked[at + word.length];
  return after === undefined || !WORD.test(after);
}

/**
 * The branch beginning at `from`: a braced block's contents, or the expression up to
 * whichever of `stops` closes it at depth zero. `null` when the shape runs off the end
 * of the body or closes a delimiter it never opened, which is what a partial match on
 * something that is not a conditional looks like.
 */
function readBranch(
  masked: string,
  from: number,
  stops: (masked: string, at: number) => boolean,
): { text: string; end: number } | null {
  let i = from;
  while (i < masked.length && /\s/.test(masked[i])) i++;
  if (masked[i] === "{") {
    const close = matchDelimiter(masked, i);
    if (close === -1) return null;
    return { text: masked.slice(i + 1, close), end: close + 1 };
  }
  let depth = 0;
  for (let j = i; j < masked.length; j++) {
    const ch = masked[j];
    if (ch === "(" || ch === "{" || ch === "[") depth++;
    else if (ch === ")" || ch === "}" || ch === "]") {
      if (depth === 0) return stops(masked, j) ? { text: masked.slice(i, j), end: j } : null;
      depth--;
    } else if (stops(masked, j)) {
      return { text: masked.slice(i, j), end: j };
    }
  }
  return null;
}

interface PressConditional {
  /** Offset of the `if`, within the text it was read from. */
  at: number;
  condition: string;
  whenTrue: string;
  whenFalse: string;
  /** The whole conditional, for the failure message. */
  source: string;
}

/**
 * Every `if (…) … else …` in `masked`, in both the shapes Kotlin writes them.
 *
 * `if (pressed) 0.93f else 1f` and the same thing with braced branches are the same
 * defect, and the braced one is what a developer copying the nearest in-repo example
 * would write: `TdaySegmentedSlider.kt`'s selector scale is wrapped exactly that way.
 * Reading only one of the two shapes would make the ban a formatting preference.
 */
function conditionals(masked: string): PressConditional[] {
  const found: PressConditional[] = [];
  for (const opener of masked.matchAll(PRESS_IF)) {
    const at = matchIndex(opener);
    const condOpen = at + opener[0].length - 1;
    const condClose = matchDelimiter(masked, condOpen);
    if (condClose === -1) continue;
    const thenBranch = readBranch(masked, condClose + 1, (text, j) => keywordAt(text, j, "else"));
    if (!thenBranch) continue;
    let cursor = thenBranch.end;
    while (cursor < masked.length && /\s/.test(masked[cursor])) cursor++;
    if (!keywordAt(masked, cursor, "else")) continue;
    const elseBranch = readBranch(masked, cursor + "else".length, (text, j) =>
      text[j] === "\n" || text[j] === "," || text[j] === ")" || text[j] === "}",
    );
    if (!elseBranch) continue;
    found.push({
      at,
      condition: masked.slice(condOpen + 1, condClose).trim(),
      whenTrue: thenBranch.text,
      whenFalse: elseBranch.text,
      source: masked.slice(at, elseBranch.end),
    });
  }
  return found;
}

interface PressDepth {
  line: number;
  composable: string;
  source: string;
}

/**
 * Sites in `source` where a composable that reads its own press squashes to a literal.
 *
 * Three conditions, all of which have to hold, because each one on its own reports
 * something that is not the defect:
 *
 *   1. the composable reads `collectIsPressedAsState` — a `pressed` handed in as a
 *      parameter is the caller's press, and the caller is where it is named;
 *   2. the literal is the value the conditional selects WHEN PRESSED, not the value it
 *      rests at — a surface sinking to 0.92 and a halo growing from 0.92 are opposite
 *      motions and only the first has a depth to name;
 *   3. something within reach of the conditional says `scale` — the same rule
 *      `android.pressScale` counts by, so a press-conditional alpha is not reported as
 *      a press depth.
 *
 * What it passes over is a branch that is not a bare float, and that is not a hole: a
 * branch reading `TdayMotionTokens.PressScales.Row` is the fix rather than the defect.
 * This bans the literal, not the shape. A depth computed further up and handed to the
 * conditional by name does escape it, and nothing here pretends otherwise — the nearest
 * thing to a net under that is `android.pressScale`, which only catches the assignment
 * if the line it is written on happens to say scale or press. Chasing it properly means
 * resolving locals, and a guardrail that guesses at a value is a rule people turn off.
 */
function pressDepthLiteralsIn(source: string): PressDepth[] {
  const masked = maskNoise(source);
  const found: PressDepth[] = [];
  for (const block of composableBlocks(masked)) {
    const body = masked.slice(block.start, block.end);
    if (!body.includes("collectIsPressedAsState")) continue;
    for (const conditional of conditionals(body)) {
      const condition = conditional.condition;
      if (!/press/i.test(condition)) continue;
      // `if (!pressed) 1f else 0.93f` sinks just as far as `if (pressed) 0.93f else 1f`.
      const whenPressed = condition.startsWith("!") ? conditional.whenFalse : conditional.whenTrue;
      const literal = BARE_FLOAT.exec(whenPressed);
      if (!literal) continue;
      const depth = Number(literal[1]);
      if (!(depth >= 0.9 && depth < 1)) continue;
      const at = conditional.at;
      const reach = body.slice(Math.max(0, at - 300), at + conditional.source.length);
      if (!/scale/i.test(reach)) continue;
      found.push({
        line: lineOf(masked, block.start + at),
        composable: block.name,
        source: conditional.source.replace(/\s+/g, " ").trim(),
      });
    }
  }
  return found;
}

/** The same scan over a file on disk, which is how the ban reaches the tree. */
function pressDepthLiterals(file: string): PressDepth[] {
  return pressDepthLiteralsIn(readFileSync(file, "utf-8"));
}

const argued = (rel: string, composable: string) =>
  ARGUED.some((entry) => entry.rel === rel && entry.composable === composable);

describeAndroid("Android presses to a named depth or to an argued one", () => {
  it("no composable reads its own press and invents a scale for it", () => {
    const violations: string[] = [];
    for (const file of ALL_KT) {
      if (file === MODIFIER) continue;
      const rel = path.relative(MONO, file);
      for (const site of pressDepthLiterals(file)) {
        if (argued(rel, site.composable)) continue;
        violations.push(
          `${rel}:${site.line} → '${site.composable}' presses to its own number ` +
            `(\`${site.source}\`). Name the surface's class instead: ` +
            "`Modifier.tdayPressable(interactionSource, scale = TdayMotionTokens.PressScales.Bar " +
            "| .Card | .Row)` — Bar 0.94 for a round bar button, Card 0.97 for a card or tile, " +
            "Row 0.985 for a full-width row. If the number is deliberately NOT a press depth, " +
            `argue it where it is written (\`${ARGUMENT_MARKER}\`), add the composable to ARGUED ` +
            "here, and give it a row in docs/motion.md's 'Deliberate non-tokens' table.",
        );
      }
    }
    expect(violations).toEqual([]);
  });

  it("every argued site still trips the rule, and still carries its argument", () => {
    for (const { rel, composable } of ARGUED) {
      const file = path.join(MONO, rel);
      expect(existsSync(file), `${rel} is gone — delete its ARGUED entry`).toBe(true);
      const sites = pressDepthLiterals(file).filter((site) => site.composable === composable);
      expect(
        sites.length,
        `${rel} → '${composable}' no longer presses to a literal — delete its ARGUED entry`,
      ).toBeGreaterThan(0);
      expect(
        readFileSync(file, "utf-8"),
        `${rel} → '${composable}' is allowlisted for an argument that is no longer written there`,
      ).toContain(ARGUMENT_MARKER);
    }
  });

  it("the modifier it excludes is still the modifier", () => {
    // The exclusion is a path, and a renamed or relocated `TdayPressable.kt` would
    // stop being excluded silently. It would also stop being found by the two
    // assertions at the foot of this file, which are the ones that keep its default on
    // a token — so a missing file is reported here rather than leaving three checks
    // quietly agreeing about nothing.
    expect(existsSync(MODIFIER)).toBe(true);
    const masked = maskNoise(readFileSync(MODIFIER, "utf-8"));
    expect(masked).toContain("fun Modifier.tdayPressable(");
    expect(masked).toContain("collectIsPressedAsState");
    // It holds the shape without holding a depth: the squash is the `scale` parameter,
    // which is why excluding this file costs the rule nothing.
    expect(pressDepthLiterals(MODIFIER)).toEqual([]);
  });

  it("found the tree, the presses in it, and the modifier's callers", () => {
    // An empty walk reports no violations and passes everything, which is the one way
    // this file can be green and worthless — the argument `motion-parity.test.ts` makes
    // for its own counters.
    expect(ALL_KT.length).toBeGreaterThan(50);

    const pressReaders = ALL_KT.filter((file) =>
      maskNoise(readFileSync(file, "utf-8")).includes("collectIsPressedAsState"),
    );
    expect(pressReaders.length).toBeGreaterThanOrEqual(8);

    let callSites = 0;
    for (const file of ALL_KT) {
      if (file === MODIFIER) continue;
      callSites += [...maskNoise(readFileSync(file, "utf-8")).matchAll(/\.tdayPressable\s*\(/g)]
        .length;
    }
    expect(callSites).toBeGreaterThanOrEqual(15);
  });

  it("every press read in the tree is inside a composable this file can see", () => {
    // The scan is per-composable, so a signature shape the parser cannot match does not
    // fail — it silently stops looking, and the next triplet written in that file is
    // invisible. `fun <T> TdaySegmentedSlider(` was exactly that hole while this was
    // being written. Orphans are therefore an error here rather than a quiet skip.
    const orphans: string[] = [];
    for (const file of ALL_KT) {
      const masked = maskNoise(readFileSync(file, "utf-8"));
      if (!masked.includes("collectIsPressedAsState")) continue;
      const blocks = composableBlocks(masked);
      for (const match of masked.matchAll(/\bcollectIsPressedAsState\b/g)) {
        const at = matchIndex(match);
        const lineStart = masked.lastIndexOf("\n", at) + 1;
        if (masked.slice(lineStart, at).trimStart().startsWith("import")) continue;
        if (!blocks.some((block) => at > block.start && at < block.end)) {
          orphans.push(`${path.relative(MONO, file)}:${lineOf(masked, at)}`);
        }
      }
    }
    expect(orphans).toEqual([]);
  });

  it("the rule reports a hand-rolled triplet when it sees one, in either formatting", () => {
    // The negative control. Every assertion above is "nothing was found", and a rule
    // that can only say that is indistinguishable from a rule that finds nothing ever.
    // This is the seventeenth copy, written back out in the shape they all had — and
    // then written again with its branches braced, which is what ktlint does to a
    // `targetValue =` that runs long and is how `TdaySegmentedSlider.kt` already writes
    // its selector scale. An earlier draft of this control called `composableBlocks`
    // and the patterns by hand, which proved the helpers compose and nothing about the
    // function the ban actually calls; it goes through `pressDepthLiteralsIn` now, so
    // the three gates the scan really applies are the three the control exercises.
    const triplet = (targetValue: string) => `
      @Composable
      private fun RelapsedButton(onClick: () -> Unit) {
          val interactionSource = remember { MutableInteractionSource() }
          val pressed by interactionSource.collectIsPressedAsState()
          val pressedScale by animateFloatAsState(
              targetValue = ${targetValue},
              label = "relapsedScale",
          )
          Card(modifier = Modifier.graphicsLayer { scaleX = pressedScale })
      }
    `;

    const oneLine = pressDepthLiteralsIn(triplet("if (pressed) 0.93f else 1f"));
    expect(oneLine.map((site) => site.composable)).toEqual(["RelapsedButton"]);

    const braced = pressDepthLiteralsIn(
      triplet("\n                  if (pressed) {\n                      0.93f\n                  } else {\n                      1f\n                  }"),
    );
    expect(braced.map((site) => site.composable)).toEqual(["RelapsedButton"]);

    // And the fix reads clean through the same path, which is the half that keeps the
    // ban from being a ban on conditionals: a branch that names a token is not a depth.
    expect(pressDepthLiteralsIn(triplet("if (pressed) TdayMotionTokens.PressScales.Bar else 1f")))
      .toEqual([]);
  });
});

describeAndroid("the shared press modifier defaults to a token", () => {
  const signature = (() => {
    if (skipAndroid) return "";
    const masked = maskNoise(readFileSync(MODIFIER, "utf-8"));
    const at = masked.indexOf("fun Modifier.tdayPressable(");
    if (at < 0) throw new Error("TdayPressable.kt no longer declares Modifier.tdayPressable");
    const open = masked.indexOf("(", at);
    return masked.slice(open, matchDelimiter(masked, open) + 1);
  })();

  it("the depth a call site gets without asking is a PressScales read", () => {
    // Seventeen call sites got their depth by typing one. The default is the whole
    // point of the migration: a surface whose author has not decided which class it
    // belongs to lands on Card, the middle of the three, rather than on a new number.
    expect(signature).toMatch(/scale\s*:\s*Float\s*=\s*TdayMotionTokens\.PressScales\.\w+/);
  });

  it("no default in the signature is a literal depth", () => {
    // `offsetY` defaults to `TdayPress.SinkOffset` and `enabled` to `true`; neither is
    // a scale. A float literal appearing anywhere in this parameter list would be a
    // depth written at the one site that exists to stop depths being written.
    expect(signature).not.toMatch(/\d*\.\d+f?\b/);
  });
});
