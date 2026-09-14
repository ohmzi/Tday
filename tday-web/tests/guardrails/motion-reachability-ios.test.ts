import { readdirSync, readFileSync, existsSync } from "fs";
import path from "path";
import { describe, it, expect } from "vitest";

// Motion reachability — iOS.
//
// The motion audit's one structural finding was that animation specs get
// written, reviewed and merged while being UNREACHABLE: the spec reads
// correctly, sits in the file forever, and can never run. Review does not catch
// it, because reading the spec is not the same as reading the path that reaches
// it, and nothing in the build says a word.
//
// SwiftUI makes this particularly easy. A `.transition` is inert unless the
// insertion or removal happens inside an animation transaction, and the
// transaction has to come from somewhere else in the file — an
// `.animation(_:value:)` keyed to the driving state, or a `withAnimation`
// around the mutation. Nothing in the language connects the two, so an orphaned
// `.transition` and the `.animation(value:)` that should have driven it are two
// correct-looking lines twenty lines apart.
//
// These rules assert reachability rather than shape. They live here rather than
// beside the Swift because this suite actually runs: no Mac, no Xcode, no
// simulator, and it is on every PR.
const ROOT = path.resolve(__dirname, "..", "..");
const MONO = path.resolve(ROOT, "..");
const IOS_SRC = path.join(MONO, "ios-swiftUI", "Tday");

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

// ─── Swift lexing ──────────────────────────────────────────────────
//
// Everything below reasons about brace structure, so comments and literals go
// first: an interpolation like "\(a) {b}" and a regex literal like
// #"[A-Z]{2,}"# both carry braces that would desync the depth counter. Stripped
// lines keep their original length and line numbering.
function stripLiteralsAndComments(source: string): string[] {
  const out: string[] = [];
  let inBlockComment = false;
  let inMultiline = false;
  let rawDelimiter = "";

  for (const raw of source.split("\n")) {
    let result = "";
    let i = 0;
    while (i < raw.length) {
      if (inBlockComment) {
        if (raw.startsWith("*/", i)) {
          inBlockComment = false;
          result += "  ";
          i += 2;
        } else {
          result += " ";
          i += 1;
        }
        continue;
      }
      if (rawDelimiter) {
        if (raw.startsWith(rawDelimiter, i)) {
          result += " ".repeat(rawDelimiter.length);
          i += rawDelimiter.length;
          rawDelimiter = "";
        } else {
          result += " ";
          i += 1;
        }
        continue;
      }
      if (inMultiline) {
        if (raw.startsWith('"""', i)) {
          inMultiline = false;
          result += "   ";
          i += 3;
        } else {
          result += " ";
          i += 1;
        }
        continue;
      }
      if (raw.startsWith("//", i)) {
        result += " ".repeat(raw.length - i);
        break;
      }
      if (raw.startsWith("/*", i)) {
        inBlockComment = true;
        result += "  ";
        i += 2;
        continue;
      }
      const rawOpen = /^(#+)"/.exec(raw.slice(i));
      if (rawOpen) {
        rawDelimiter = `"${rawOpen[1]}`;
        result += " ".repeat(rawOpen[0].length);
        i += rawOpen[0].length;
        continue;
      }
      if (raw.startsWith('"""', i)) {
        inMultiline = true;
        result += "   ";
        i += 3;
        continue;
      }
      if (raw[i] === '"') {
        result += " ";
        i += 1;
        while (i < raw.length) {
          if (raw[i] === "\\") {
            result += "  ";
            i += 2;
            continue;
          }
          const closing = raw[i] === '"';
          result += " ";
          i += 1;
          if (closing) break;
        }
        continue;
      }
      result += raw[i];
      i += 1;
    }
    out.push(result);
  }
  return out;
}

const SWIFT_KEYWORDS = new Set([
  "if", "else", "let", "var", "guard", "return", "true", "false", "nil", "self",
  "case", "switch", "for", "in", "while", "func", "some", "any", "where", "try",
  "await", "async", "is", "as", "init", "default", "break", "continue", "do",
  "catch", "throw", "throws", "repeat", "private", "public", "internal", "static",
]);

/** Identifiers used as a whole term: `viewModel.mode` and `.today` yield nothing. */
function bareIdentifiers(text: string): string[] {
  const out: string[] = [];
  for (const match of text.matchAll(/(^|[^.\w$])([A-Za-z_]\w*)(?![\w.])/g)) {
    const name = match[2];
    if (!SWIFT_KEYWORDS.has(name)) out.push(name);
  }
  return [...new Set(out)];
}

/** Root identifiers: `viewModel.mode` yields `viewModel`, `.today` yields nothing. */
function rootIdentifiers(text: string): string[] {
  const out: string[] = [];
  for (const match of text.matchAll(/(^|[^.\w$])([A-Za-z_]\w*)/g)) {
    const name = match[2];
    if (!SWIFT_KEYWORDS.has(name)) out.push(name);
  }
  return [...new Set(out)];
}

function balancedGroups(text: string): boolean {
  let paren = 0;
  let square = 0;
  for (const ch of text) {
    if (ch === "(") paren += 1;
    else if (ch === ")") paren -= 1;
    else if (ch === "[") square += 1;
    else if (ch === "]") square -= 1;
  }
  return paren === 0 && square === 0;
}

/** 0-based index of the line closing the block opened on `line`. */
function blockEnd(code: string[], line: number): number {
  let depth = 0;
  for (let j = line; j < code.length; j += 1) {
    for (const ch of code[j]) {
      if (ch === "{") depth += 1;
      else if (ch === "}") {
        depth -= 1;
        if (depth === 0) return j;
      }
    }
  }
  return code.length - 1;
}

/**
 * The full header of the block opened on `line`, rejoined across continuations
 * and matched against `pattern`. Multi-line headers are normal in this codebase
 * — `if a, \n b, \n c {` and `private func x(\n _ a: A\n) -> some View {` — and
 * the keyword lives on a line the brace counter never sees otherwise.
 */
function joinedHeader(
  code: string[],
  line: number,
  pattern: RegExp,
): { text: string; start: number } | null {
  let text = code[line];
  for (let back = 0; back < 12; back += 1) {
    const flat = text.replace(/\s+/g, " ").trim();
    if (pattern.test(flat) && balancedGroups(text)) return { text: flat, start: line - back };
    if (line - back - 1 < 0) break;
    text = `${code[line - back - 1]} ${text}`;
  }
  return null;
}

const BRANCH_HEADER = /^\}?\s*(?:else\s+if|else|if)\b/;
const DECLARATION_HEADER = /\b(?:var|func)\s+\w+/;
const TYPE_HEADER = /\b(?:struct|class|enum|extension|actor|protocol)\s+\w+/;

interface Branch {
  /** 1-based line of the `{` that opens this branch. */
  line: number;
  /** 1-based line the condition starts on; differs when the header wraps. */
  startLine: number;
  /** Condition text; empty for a bare `else`. */
  cond: string;
  /** Brace depth of the branch body. */
  depth: number;
  /** Index of the branch that starts the `if`/`else if`/`else` chain. */
  chainRoot: number;
  /** 1-based line of the `}` that closes this branch. */
  endLine: number;
}

interface FileStructure {
  branches: Branch[];
  /** For each 0-based line, indices of the branches enclosing it, outermost first. */
  enclosing: number[][];
  /** For each 0-based line, the brace depth at the start of the line. */
  depthAt: number[];
}

function analyseStructure(code: string[]): FileStructure {
  const branches: Branch[] = [];
  const enclosing: number[][] = [];
  const depthAt: number[] = [];
  const open: number[] = [];
  let depth = 0;
  let lastClosed = -1;

  for (let i = 0; i < code.length; i += 1) {
    depthAt.push(depth);
    for (const ch of code[i]) {
      if (ch === "{") {
        depth += 1;
      } else if (ch === "}") {
        depth -= 1;
        while (open.length > 0 && branches[open[open.length - 1]].depth > depth) {
          const closed = open.pop() as number;
          branches[closed].endLine = i + 1;
          lastClosed = closed;
        }
      }
    }
    enclosing.push([...open]);

    if (!code[i].trim().endsWith("{")) continue;
    const header = joinedHeader(code, i, BRANCH_HEADER);
    if (!header) continue;
    const condMatch = /^\}?\s*(?:else\s+)?if\s+(.*?)\s*\{$/.exec(header.text);
    const isElse = /^\}?\s*else\s*\{$/.test(header.text);
    if (!condMatch && !isElse) continue;

    const chained = /^\}?\s*else\b/.test(header.text);
    branches.push({
      line: i + 1,
      startLine: header.start + 1,
      cond: condMatch ? condMatch[1] : "",
      depth,
      chainRoot: chained && lastClosed >= 0 ? branches[lastClosed].chainRoot : branches.length,
      endLine: code.length,
    });
    open.push(branches.length - 1);
  }
  return { branches, enclosing, depthAt };
}

// ─── What a type animates ──────────────────────────────────────────
//
// Scoped to the type, not the file. Several of these files hold half a dozen
// small view structs, and one struct's `.animation(_:value:)` list says nothing
// about another struct's flags.

interface TypeScope {
  name: string;
  /** 1-based, inclusive. */
  start: number;
  end: number;
  animated: Set<string>;
  ownState: Set<string>;
  /** This type's computed `var`s, by name, as their body text. */
  computed: Map<string, string>;
}

/**
 * Every `.animation(_:value:)` in a slice: its `value:` expression, and the
 * 1-based line the `.animation(` itself sits on.
 *
 * The line is what rule D needs and rule A's scope building does not, which is why
 * this returns pairs and [animatedValueExpressions] throws half of them away. The
 * call is nearly always written across four lines with `value:` on the third, so
 * the line that matters is the one the modifier OPENS on — that is the line whose
 * enclosing branches say where in the tree the modifier lives.
 */
function animatedValueSites(text: string): { line: number; expr: string }[] {
  const sites: { line: number; expr: string }[] = [];
  for (const match of text.matchAll(/\.animation\s*\(/g)) {
    const open = match.index + match[0].length - 1;
    let depth = 0;
    let close = -1;
    for (let i = open; i < text.length; i += 1) {
      if (text[i] === "(") depth += 1;
      else if (text[i] === ")") {
        depth -= 1;
        if (depth === 0) {
          close = i;
          break;
        }
      }
    }
    if (close < 0) continue;
    const args = text.slice(open + 1, close);
    const valueAt = args.indexOf("value:");
    if (valueAt < 0) continue;
    let line = 1;
    for (let i = 0; i < match.index; i += 1) if (text[i] === "\n") line += 1;
    sites.push({ line, expr: args.slice(valueAt + "value:".length) });
  }
  return sites;
}

/** The `value:` expression of every `.animation(_:value:)` in a slice. */
function animatedValueExpressions(text: string): string[] {
  return animatedValueSites(text).map((site) => site.expr);
}

/**
 * Maximal dotted chains, as segment arrays: `!viewModel.todayTodos.isEmpty` yields
 * `[viewModel, todayTodos, isEmpty]`, and a leading-dot member like `.opacity`
 * yields nothing.
 */
function dottedPaths(text: string): string[][] {
  const out: string[][] = [];
  for (const match of text.matchAll(/(^|[^.\w$])([A-Za-z_]\w*(?:\s*\.\s*[A-Za-z_]\w*)*)/g)) {
    const segments = match[2].split(".").map((part) => part.trim());
    if (SWIFT_KEYWORDS.has(segments[0])) continue;
    out.push(segments);
  }
  return out;
}

/**
 * Predicates a condition asks ABOUT a piece of state rather than state of their
 * own. `if !todos.isEmpty` is gated on `todos`; `if viewModel.isLoading` is gated
 * on `isLoading`, and reading it as "gated on viewModel" would make every
 * condition in a screen look like every other one.
 */
const COLLECTION_PREDICATES = new Set(["isEmpty", "count", "first", "last"]);

/** What a condition is actually gated on, as dotted paths. */
function gateStatePaths(cond: string): string[][] {
  return dottedPaths(cond).map((segments) =>
    segments.length > 1 && COLLECTION_PREDICATES.has(segments[segments.length - 1])
      ? segments.slice(0, -1)
      : segments,
  );
}

/** Does `expr` read `state`, or something under it? */
function expressionReads(expr: string, state: string[]): boolean {
  return dottedPaths(expr).some(
    (segments) =>
      segments.length >= state.length && state.every((name, i) => segments[i] === name),
  );
}

/**
 * The same question, asked one hop through the type's own computed properties.
 *
 * [expressionReads] is textual, and the expression that names the state is often not
 * the expression that is written: a `value:` keyed on a composite like
 * `pendingDayAnimationKey` reads the gate's collection in the property's body and
 * nothing at all on the line itself. Rule D's whole subject is a modifier that LOOKS
 * right, so a rule that only reads the line is exactly one rename away from being
 * blind at the site it was written for.
 *
 * One hop, and no transitive closure. A key assembled out of the branch's own state is
 * the shape that exists; chasing further would start pulling half a screen's
 * properties into every expression and turn a precise rule into a noisy one. Returns
 * the property it went through so the failure can name the indirection rather than
 * point at a line the state does not appear on.
 */
function expressionReadsVia(
  expr: string,
  state: string[],
  scope: TypeScope | null,
): { reads: boolean; through?: string } {
  if (expressionReads(expr, state)) return { reads: true };
  for (const name of bareIdentifiers(expr)) {
    const body = scope?.computed.get(name);
    if (body && expressionReads(body, state)) return { reads: true, through: name };
  }
  return { reads: false };
}

/** Every identifier touched inside a `withAnimation { … }` body in a slice. */
function withAnimationIdentifiers(text: string): string[] {
  const found: string[] = [];
  for (const match of text.matchAll(/\bwithAnimation\b/g)) {
    let i = match.index + match[0].length;
    while (i < text.length && text[i] !== "{") i += 1;
    if (i >= text.length) continue;
    let depth = 0;
    let end = i;
    for (; end < text.length; end += 1) {
      if (text[end] === "{") depth += 1;
      else if (text[end] === "}") {
        depth -= 1;
        if (depth === 0) break;
      }
    }
    found.push(...rootIdentifiers(text.slice(i, end)));
  }
  return found;
}

/** `var x: T { … }` — computed, so no `=` between the name and the brace. */
const COMPUTED_PROPERTY = /\bvar\s+(\w+)\s*:\s*[^={]*\{$/;

/**
 * The computed `var`s declared directly in a type's body, by name, as body text.
 *
 * Every brace-opening member is skipped whole rather than walked, which is what keeps
 * a nested view struct's properties out of the outer type's table: two structs in one
 * file routinely share a name like `items`, and resolving through the wrong one would
 * make rule D fail on a line that reads nothing of the sort.
 */
function computedProperties(code: string[], start: number, end: number): Map<string, string> {
  const map = new Map<string, string>();
  let i = start + 1;
  while (i <= end) {
    if (!code[i].trim().endsWith("{")) {
      i += 1;
      continue;
    }
    const close = blockEnd(code, i);
    const header = joinedHeader(code, i, DECLARATION_HEADER);
    const match = header && COMPUTED_PROPERTY.exec(header.text);
    if (match) {
      map.set(
        match[1],
        code
          .slice(i, close + 1)
          .join(" ")
          .replace(/^[^{]*\{/, "")
          .replace(/\}\s*$/, "")
          .replace(/\s+/g, " ")
          .trim(),
      );
    }
    i = close + 1;
  }
  return map;
}

function typeScopes(code: string[]): TypeScope[] {
  const scopes: TypeScope[] = [];
  for (let i = 0; i < code.length; i += 1) {
    if (!code[i].trim().endsWith("{")) continue;
    const header = joinedHeader(code, i, TYPE_HEADER);
    if (!header) continue;
    const match = TYPE_HEADER.exec(header.text);
    if (!match) continue;

    const end = blockEnd(code, i);
    const text = code.slice(i, end + 1).join("\n");
    const animated = new Set<string>(withAnimationIdentifiers(text));
    for (const expr of animatedValueExpressions(text)) {
      for (const name of rootIdentifiers(expr)) animated.add(name);
    }
    const ownState = new Set<string>();
    const decl = /@(?:State|Binding|FocusState|AppStorage|SceneStorage)\b[^\n]*?\bvar\s+(\w+)/g;
    for (const stateMatch of text.matchAll(decl)) ownState.add(stateMatch[1]);

    scopes.push({
      name: match[0].split(/\s+/)[1],
      start: i + 1,
      end: end + 1,
      animated,
      ownState,
      computed: computedProperties(code, i, end),
    });
  }
  return scopes;
}

interface ParsedFile {
  file: string;
  code: string[];
  raw: string[];
  structure: FileStructure;
  scopes: TypeScope[];
}

const IOS_FILES = existsSync(IOS_SRC) ? walkFiles(IOS_SRC, ".swift") : [];
const describeIOS = IOS_FILES.length === 0 ? describe.skip : describe;

const PARSED: ParsedFile[] = IOS_FILES.map((file) => {
  const source = readSource(file);
  const code = stripLiteralsAndComments(source);
  return {
    file,
    code,
    raw: source.split("\n"),
    structure: analyseStructure(code),
    scopes: typeScopes(code),
  };
});

/** The innermost type whose body contains a 1-based line. */
function scopeAt(parsed: ParsedFile, line: number): TypeScope | null {
  let best: TypeScope | null = null;
  for (const scope of parsed.scopes) {
    if (line < scope.start || line > scope.end) continue;
    if (!best || scope.start > best.start) best = scope;
  }
  return best;
}

/** The condition's gates: identifiers that are this type's own observable state. */
function gatesOf(cond: string, scope: TypeScope): string[] {
  return bareIdentifiers(cond).filter((name) => scope.ownState.has(name));
}

// ─── Allowlists ────────────────────────────────────────────────────
//
// An allowlist that grows silently is how a suite like this rots, so every
// entry is keyed by an exact `path:line` and has to state what supplies the
// animation transaction instead. An orphan is never exempted here: the fix for
// an orphan is the missing `.animation(_:value:)`.

/** Rule A — `.transition` sites whose transaction is supplied outside this type. */
const TRANSITION_DRIVEN_ELSEWHERE: Record<string, string> = {
  "ios-swiftUI/Tday/UI/Component/CreateTaskSheet.swift:242":
    "`scheduleEnabled` is written through the Binding handed to " +
    "CreateTaskSheetScheduleToggleRow, which applies `$isOn.animation(.spring(…))` " +
    "on the Toggle (:787). The Binding carries the transaction, so the due row's " +
    "transition runs on every user-driven flip of this gate.",
};

/** Rule B — panel gates that are meant to cut while their siblings animate. */
const PANEL_SWAP_CUTS_ON_PURPOSE: Record<string, string> = {};

/** Rule C — private view declarations reached from somewhere this scan cannot see. */
const VIEW_DECLARATIONS_REACHED_ELSEWHERE: Record<string, string> = {};

/**
 * Rule D — `.animation(_:value:)` inside its own gate's branch, on purpose.
 *
 * There is no honest entry for "the branch should cut": a branch that is meant to
 * cut wants no animation at all, and one written inside the branch still animates
 * everything that changes WITHIN it while animating nothing about the branch
 * appearing or going away. An entry here would have to name a third thing the
 * modifier is for.
 */
const ANIMATION_INSIDE_ITS_OWN_BRANCH: Record<string, string> = {};

/** Protocol witnesses: SwiftUI calls these, never the file. */
const PROTOCOL_WITNESSES = new Set(["body", "previews", "makeBody"]);

// Three rules that assert an absence are three rules that pass for free the day
// the lexer stops understanding Swift. These floors are the smoke alarm: if a
// parser change makes the scan see nothing, the suite says so instead of going
// quietly green. Raise a floor when the target genuinely grows; never lower one
// to make a run pass.
describeIOS("iOS motion reachability scan integrity", () => {
  it("the brace tracker balances on every scanned file", () => {
    const unbalanced = PARSED.filter((parsed) => {
      let depth = 0;
      for (const line of parsed.code) {
        for (const ch of line) {
          if (ch === "{") depth += 1;
          else if (ch === "}") depth -= 1;
        }
      }
      return depth !== 0;
    }).map((parsed) => relPath(parsed.file));

    expect(
      unbalanced,
      `the lexer lost brace depth in: ${unbalanced.join(", ")} — every rule below ` +
        "reads structure from that counter, so a desync silently disables them",
    ).toEqual([]);
  });

  it("still sees the constructs the rules are about", () => {
    const transitions = PARSED.reduce(
      (total, parsed) => total + parsed.code.filter((line) => line.includes(".transition(")).length,
      0,
    );
    const animated = PARSED.reduce(
      (total, parsed) => total + animatedValueExpressions(parsed.code.join("\n")).length,
      0,
    );
    const branches = PARSED.reduce((total, parsed) => total + parsed.structure.branches.length, 0);
    const scopes = PARSED.reduce((total, parsed) => total + parsed.scopes.length, 0);
    const decls = PARSED.reduce((total, parsed) => total + viewDeclarations(parsed).length, 0);
    const computed = PARSED.reduce(
      (total, parsed) => total + parsed.scopes.reduce((n, scope) => n + scope.computed.size, 0),
      0,
    );

    expect(IOS_FILES.length, "iOS .swift files").toBeGreaterThan(80);
    expect(transitions, "`.transition(` sites").toBeGreaterThan(15);
    expect(animated, "`.animation(_:value:)` sites").toBeGreaterThan(30);
    expect(branches, "if/else branches").toBeGreaterThan(500);
    expect(scopes, "type scopes").toBeGreaterThan(100);
    expect(decls, "private view declarations").toBeGreaterThan(60);
    // Rule D reads the gate's state through these. An empty table is not a rule that
    // fails; it is a rule that stops seeing every indirect `value:` in the tree.
    expect(computed, "computed `var` bodies").toBeGreaterThan(400);
  });
});

describeIOS("iOS motion reachability", () => {

  // A `.transition` runs only inside an animation transaction. If the state
  // that inserts and removes the view appears in no `.animation(_:value:)` and
  // is never written inside `withAnimation`, there is no transaction, and the
  // spec on the line below it is decoration: the view hard-cuts in and out.
  //
  // Known instance: SettingsScreen.swift — the Day Ahead selector overlay
  // declares `.transition(.opacity.combined(with: .scale(0.97)))` while
  // `showingDayAheadSelector` is in none of the three `.animation(value:)`
  // lines that cover its two siblings, so one of three sibling overlays cuts.
  it("every `.transition` has an `.animation(_:value:)` that can start it", () => {
    const violations: string[] = [];

    for (const parsed of PARSED) {
      const { code, raw, structure, file } = parsed;
      for (let i = 0; i < code.length; i += 1) {
        if (!code[i].includes(".transition(")) continue;

        const scope = scopeAt(parsed, i + 1);
        if (!scope) continue;

        const gateBranch = [...structure.enclosing[i]]
          .reverse()
          .map((index) => structure.branches[index])
          .find((branch) => branch.cond.length > 0);
        if (!gateBranch) continue;

        const gates = gatesOf(gateBranch.cond, scope);
        if (gates.length === 0) continue;
        if (gates.some((gate) => scope.animated.has(gate))) continue;

        const site = `${relPath(file)}:${i + 1}`;
        if (site in TRANSITION_DRIVEN_ELSEWHERE) continue;
        violations.push(
          `${site} → ${raw[i].trim()} — gated by ` +
            `${gates.map((gate) => `\`${gate}\``).join(", ")} at :${gateBranch.startLine}, which ` +
            `\`${scope.name}\` puts in no .animation(_:value:) and writes in no withAnimation`,
        );
      }
    }

    expect(violations, violations.join("\n")).toEqual([]);
  });

  // A whole-panel swap — `if a { PanelA } else if b { PanelB } else { … }` —
  // needs the same transaction: the gate has to be in the view's
  // `.animation(_:value:)` list. When a view animates some of its panel gates
  // and not others, the ones left out are not a decision but an omission: the
  // same swap crossfades or cuts depending on which flag happened to move.
  //
  // Known instance: OnboardingWizardOverlay.swift — the wizard's panel chain is
  // covered for `step`, `isConnecting`, `isCreatingAccount` and
  // `isShowingForgotPassword`, but neither `isChoosingSecurityQuestions` nor
  // `isLoadingSecurityQuestions` is in the list, so the security-questions panel
  // and its loading state cut in mid-flow.
  it("panel-swap gates are animated consistently within a view", () => {
    const violations: string[] = [];

    for (const parsed of PARSED) {
      const { code, raw, structure, file } = parsed;

      // Group branches into `if` / `else if` / `else` chains.
      const chains = new Map<number, number[]>();
      structure.branches.forEach((branch, index) => {
        const list = chains.get(branch.chainRoot) ?? [];
        list.push(index);
        chains.set(branch.chainRoot, list);
      });

      // Per type: which panel gates animate, and which do not.
      const gatesByScope = new Map<string, { gate: string; line: number }[]>();
      for (const indices of chains.values()) {
        // A swap needs an alternative branch; a bare `if x { Badge() }` is a
        // presence toggle, which is rule A's business rather than this one.
        if (indices.length < 2) continue;
        const rendersPanels = indices.every((index) => {
          const branch = structure.branches[index];
          if (branch.endLine - branch.line < 2) return false;
          const body = code.slice(branch.line, branch.endLine - 1).join("\n");
          return /(^|[^.\w])[A-Z]\w*\s*\(/.test(body) || /\b\w*(?:Content|Panel)\b/.test(body);
        });
        if (!rendersPanels) continue;

        for (const index of indices) {
          const branch = structure.branches[index];
          const scope = scopeAt(parsed, branch.line);
          if (!scope) continue;
          for (const gate of gatesOf(branch.cond, scope)) {
            const list = gatesByScope.get(scope.name) ?? [];
            list.push({ gate, line: branch.startLine });
            gatesByScope.set(scope.name, list);
          }
        }
      }

      for (const [scopeName, gated] of gatesByScope) {
        const scope = parsed.scopes.find((entry) => entry.name === scopeName);
        if (!scope) continue;
        const animatedGates = gated.filter((entry) => scope.animated.has(entry.gate));
        if (animatedGates.length === 0) continue;

        for (const entry of gated) {
          if (scope.animated.has(entry.gate)) continue;
          const site = `${relPath(file)}:${entry.line}`;
          if (site in PANEL_SWAP_CUTS_ON_PURPOSE) continue;
          violations.push(
            `${site} → ${raw[entry.line - 1].trim()} — panel gate \`${entry.gate}\` is in no ` +
              `.animation(_:value:), while \`${scopeName}\` animates sibling gates ` +
              `${[...new Set(animatedGates.map((other) => `\`${other.gate}\``))].join(", ")}`,
          );
        }
      }
    }

    expect(violations, violations.join("\n")).toEqual([]);
  });

  // Rule D — where the modifier is written, not just whether it exists.
  //
  // Rule A asks whether a transaction exists somewhere in the type. It cannot ask
  // the next question, which is the one that bit the today block and the calendar's
  // day list: a `.animation(_:value:)` written INSIDE `if <that same state> { … }`
  // is part of that branch. The update that flips the gate removes the branch and
  // the modifier together, so at the moment the removal is decided there is no
  // transaction open — the block cuts, and every `.transition` inside it cuts with
  // it, while the same modifier goes on animating changes within the branch
  // perfectly well. It reads correct, it IS correct for half its job, and rule A
  // sees a gate that is named in an `.animation(value:)` and says nothing.
  //
  // The fix is never an allowlist entry; it is moving the modifier out, onto a
  // `Group` or whatever else spans both states of the branch.
  //
  // "The state it animates" is resolved one hop through the type's computed
  // properties, not read off the line. Both original sites happened to write the
  // collection inline, but a `value:` keyed on something assembled out of it —
  // `pendingDayAnimationKey`, which is what the calendar ships — names no state
  // textually at all, and a rule that only read the line would have gone green on the
  // very defect it was written for the moment the expression was given a name.
  it("no `.animation(_:value:)` sits inside the branch it would animate", () => {
    const violations: string[] = [];

    for (const parsed of PARSED) {
      const { code, raw, structure, file } = parsed;
      for (const site of animatedValueSites(code.join("\n"))) {
        const scope = scopeAt(parsed, site.line);
        for (const index of structure.enclosing[site.line - 1] ?? []) {
          const branch = structure.branches[index];
          if (!branch.cond) continue;
          const shared = gateStatePaths(branch.cond)
            .map((state) => ({ state, hit: expressionReadsVia(site.expr, state, scope) }))
            .filter(({ hit }) => hit.reads)
            .map(({ state, hit }) =>
              hit.through
                ? `\`${state.join(".")}\` (through \`${hit.through}\`)`
                : `\`${state.join(".")}\``,
            );
          if (shared.length === 0) continue;

          const siteKey = `${relPath(file)}:${site.line}`;
          if (siteKey in ANIMATION_INSIDE_ITS_OWN_BRANCH) break;
          violations.push(
            `${siteKey} → ${raw[site.line - 1].trim()} — animates ` +
              `${[...new Set(shared)].join(", ")}, which gates the ` +
              `branch at :${branch.startLine} that this modifier is written inside. It is ` +
              "removed in the same update as the views it would animate out; hang it on a " +
              "`Group` around the `if` instead",
          );
          break;
        }
      }
    }

    expect(violations, violations.join("\n")).toEqual([]);
  });

});

// ─── Rule C — view code no path reaches ────────────────────────────

/** `enum Name { case a; case b }` → its cases, for every enum in the target. */
function collectEnumCases(): Map<string, Set<string>> {
  const enums = new Map<string, Set<string>>();
  for (const parsed of PARSED) {
    const text = parsed.code.join("\n");
    for (const match of text.matchAll(/\benum\s+(\w+)[^\n{]*\{/g)) {
      const open = match.index + match[0].length - 1;
      let depth = 0;
      let end = open;
      for (; end < text.length; end += 1) {
        if (text[end] === "{") depth += 1;
        else if (text[end] === "}") {
          depth -= 1;
          if (depth === 0) break;
        }
      }
      const cases = new Set<string>();
      for (const caseMatch of text.slice(open, end).matchAll(/^\s*case\s+([^\n:{]+)$/gm)) {
        for (const name of caseMatch[1].split(",")) {
          const clean = /^\s*(\w+)/.exec(name.split("=")[0]);
          if (clean) cases.add(clean[1]);
        }
      }
      if (cases.size > 0) enums.set(match[1], cases);
    }
  }
  return enums;
}

interface Declaration {
  name: string;
  /** 1-based line the declaration starts on, which a wrapped signature moves. */
  line: number;
  /** 1-based line of the `{` that opens the body. */
  braceLine: number;
  /** 1-based line of the `}` that closes it, inclusive. */
  endLine: number;
  bodyDepth: number;
}

const VIEW_DECLARATION =
  /\bprivate\b.*?\b(?:var|func)\s+(\w+)\s*(?:\([^)]*\))?\s*(?::|->)\s*(?:some\s+View|AnyView)\s*\{$/;

/**
 * `private var x: some View {` / `private func x(…) -> some View {`.
 *
 * Private only, on purpose: `private` in Swift is file-scoped, so a reference
 * search over this one file is sound. An `internal` helper in `extension View`
 * is used from other files and would be a false positive.
 */
function viewDeclarations(parsed: ParsedFile): Declaration[] {
  const { code, structure } = parsed;
  const decls: Declaration[] = [];
  for (let i = 0; i < code.length; i += 1) {
    if (!code[i].trim().endsWith("{")) continue;
    const header = joinedHeader(code, i, DECLARATION_HEADER);
    if (!header) continue;
    const match = VIEW_DECLARATION.exec(header.text);
    if (!match) continue;
    decls.push({
      name: match[1],
      line: header.start + 1,
      braceLine: i + 1,
      endLine: blockEnd(code, i) + 1,
      bodyDepth: structure.depthAt[i] + 1,
    });
  }
  return decls;
}

/** `private var x: Bool { … }` bodies, used to expand the guards of an if-chain. */
function boolPredicates(parsed: ParsedFile): Map<string, string> {
  const { code } = parsed;
  const map = new Map<string, string>();
  for (let i = 0; i < code.length; i += 1) {
    const match = /\bvar\s+(\w+)\s*:\s*Bool\s*\{$/.exec(code[i].trim());
    if (!match) continue;
    const body = code
      .slice(i, blockEnd(code, i) + 1)
      .join(" ")
      .replace(/^[^{]*\{/, "")
      .replace(/\}\s*$/, "")
      .replace(/\breturn\b/, "")
      .replace(/\s+/g, " ")
      .trim();
    map.set(match[1], body);
  }
  return map;
}

/**
 * The enum cases a guard covers, when it is a plain disjunction of
 * `base == .case` terms (`mode == .a || mode == .b`), directly or through
 * `private var … : Bool` predicates. Anything else — a `&&`, a negation, a call
 * — returns null, because then the guard is not a pure case test and
 * exhaustiveness cannot be claimed from it.
 */
function coveredCases(
  expr: string,
  predicates: Map<string, string>,
): { base: string; cases: Set<string> } | null {
  let text = expr;
  for (let round = 0; round < 4; round += 1) {
    let expanded = text;
    for (const [name, body] of predicates) {
      expanded = expanded.replace(new RegExp(`(^|[^.\\w])${name}\\b`, "g"), `$1(${body})`);
    }
    if (expanded === text) break;
    text = expanded;
  }

  const term = /([A-Za-z_][\w.]*)\s*==\s*\.(\w+)/g;
  const terms = [...text.matchAll(term)];
  if (terms.length === 0) return null;
  if (text.replace(term, "").replace(/[()\s|]/g, "").length > 0) return null;

  const base = terms[0][1];
  if (terms.some((entry) => entry[1] !== base)) return null;
  return { base, cases: new Set(terms.map((entry) => entry[2])) };
}

describeIOS("iOS dead view branches", () => {
  // A view can be spelled correctly, reviewed and merged — and be unreachable,
  // because the `if`-chain in front of it already covered every case of the
  // enum it switches on. Swift never says so: the chain is `if`s rather than a
  // `switch`, so there is no exhaustiveness diagnostic, and the trailing
  // `return` reads as a default rather than as dead code. Everything only that
  // branch reaches is dead with it, however much motion it declares.
  //
  // Known instance: TodoListScreen.swift `modeContent` — `isTodayMode` covers
  // `.today` and `isMinimalTimelineMode` covers the other six `TodoListMode`
  // cases, so `return AnyView(standardModeContent)` can never run, and behind
  // it `todoRow` holds the only `.swipeActions(edge: .leading)` in the app.
  it("no view is reachable only through a dead branch", () => {
    const enums = collectEnumCases();
    const violations: string[] = [];

    for (const parsed of PARSED) {
      const { code, raw, structure, file } = parsed;
      const decls = viewDeclarations(parsed);
      const predicates = boolPredicates(parsed);

      // 1. Trailing `return`s that an exhaustive guard chain has already made
      //    unreachable.
      const deadRanges: { line: number; why: string }[] = [];
      for (const decl of decls) {
        const guards: { line: number; cond: string }[] = [];
        let fallback = -1;
        for (let i = decl.braceLine; i < decl.endLine - 1; i += 1) {
          if (structure.depthAt[i] !== decl.bodyDepth) continue;
          const branch = structure.branches.find((entry) => entry.line === i + 1);
          if (branch && branch.cond.length > 0 && branch.chainRoot === structure.branches.indexOf(branch)) {
            guards.push({ line: i + 1, cond: branch.cond });
          } else if (/^return\b/.test(code[i].trim())) {
            fallback = i + 1;
          }
        }
        if (guards.length < 2 || fallback < 0) continue;

        const covers = guards.map((guard) => coveredCases(guard.cond, predicates));
        if (covers.some((cover) => cover === null)) continue;
        const resolved = covers as { base: string; cases: Set<string> }[];
        if (new Set(resolved.map((cover) => cover.base)).size !== 1) continue;
        const union = new Set(resolved.flatMap((cover) => [...cover.cases]));

        const matches = [...enums.entries()].filter(
          ([, cases]) => cases.size === union.size && [...union].every((name) => cases.has(name)),
        );
        if (matches.length !== 1) continue;

        deadRanges.push({
          line: fallback,
          why:
            `${relPath(file)}:${fallback} is unreachable — \`${decl.name}\` (:${decl.line}) ` +
            `already guards every case of \`${matches[0][0]}\` above it ` +
            `(${guards.map((guard) => `:${guard.line}`).join(", ")})`,
        });
      }

      // 2. Declarations reachable only from dead code. Iterate to a fixpoint:
      //    a view whose only caller is dead is dead in turn.
      const dead = new Map<string, string>();
      for (let round = 0; round < 8; round += 1) {
        const deadDecls = decls.filter((entry) => dead.has(entry.name));
        const inDeadCode = (line: number) =>
          deadRanges.some((range) => range.line === line) ||
          deadDecls.some((entry) => line >= entry.line && line <= entry.endLine);

        let grew = false;
        for (const decl of decls) {
          if (dead.has(decl.name) || PROTOCOL_WITNESSES.has(decl.name)) continue;
          const refs: number[] = [];
          const reference = new RegExp(`\\b${decl.name}\\b`);
          for (let i = 0; i < code.length; i += 1) {
            if (i >= decl.line - 1 && i < decl.endLine) continue;
            if (reference.test(code[i])) refs.push(i + 1);
          }
          if (refs.length > 0 && !refs.every(inDeadCode)) continue;

          dead.set(
            decl.name,
            refs.length === 0
              ? "referenced nowhere in the file"
              : `referenced only from dead code (${refs.map((line) => `:${line}`).join(", ")})`,
          );
          grew = true;
        }
        if (!grew) break;
      }

      for (const range of deadRanges) {
        violations.push(`${range.why} → ${raw[range.line - 1].trim()}`);
      }
      for (const decl of decls) {
        const why = dead.get(decl.name);
        if (!why) continue;
        const site = `${relPath(file)}:${decl.line}`;
        if (site in VIEW_DECLARATIONS_REACHED_ELSEWHERE) continue;
        violations.push(`${site} → \`${decl.name}\` is dead: ${why}`);
      }
    }

    expect(violations, violations.join("\n")).toEqual([]);
  });
});

// ─── Row actions ───────────────────────────────────────────────────
//
// `extension View` is where a modifier with no caller hides best. Rule C reads
// `private` declarations only, and says why: `private` is file-scoped in Swift,
// so a one-file reference search is a proof. An `internal` helper is visible to
// the whole module — but the module is exactly this tree (`project.yml` gives
// the app target the single source path `Tday`), so a search across the scan is
// the same proof, one target wide instead of one file wide.
//
// That gap is how the app came to ship a swipe nobody could perform. The dead
// `standardModeContent` branch went, and with it the `todoRow` that applied
// `.swipeActions` — but `todoSwipeActions`, the helper holding the
// `.swipeActions` call itself, stayed behind in `extension View` with every
// caller gone, which reads in review as a feature the app has.
//
// A modifier is declared in two halves, and the `extension View` half is the
// one review tends to read. `SwipeRevealHintModifier` stranded for longer than
// `todoSwipeActions` did and was found by eye rather than by rule, because the
// half it lived in was a `private struct … : ViewModifier` — the sort of
// declaration no scan here collected. Both halves are searched now: the
// reference proof is the same one, and a `.modifier(X())` call is as findable
// as a `.x()` one.

interface ViewHelper {
  name: string;
  /** 1-based line the declaration starts on, which a wrapped signature moves. */
  line: number;
  endLine: number;
  body: string;
  file: string;
}

/** `func …(…) -> some View {` declared directly in an `extension View`. */
function extensionViewHelpers(parsed: ParsedFile): ViewHelper[] {
  const { code } = parsed;
  const helpers: ViewHelper[] = [];
  for (const scope of parsed.scopes) {
    // `typeScopes` rejoins wrapped headers, so a member whose own `{` sits
    // within reach of the `extension View {` line above it is also recorded as
    // a scope called `View`. Reading the opening line back settles which one is
    // the extension and keeps every helper inside it from being found twice.
    if (scope.name !== "View" || !/\bextension\s+View\b/.test(code[scope.start - 1])) continue;
    let i = scope.start;
    while (i <= scope.end - 2) {
      if (!code[i].trim().endsWith("{")) {
        i += 1;
        continue;
      }
      const close = blockEnd(code, i);
      const header = joinedHeader(code, i, DECLARATION_HEADER);
      const match = header ? /\bfunc\s+(\w+)/.exec(header.text) : null;
      if (header && match) {
        helpers.push({
          name: match[1],
          line: header.start + 1,
          endLine: close + 1,
          body: code.slice(i, close + 1).join("\n"),
          file: parsed.file,
        });
      }
      i = close + 1;
    }
  }
  return helpers;
}

/** `struct X: ViewModifier { … }` — the half a `.modifier(X())` call reaches. */
function viewModifierTypes(parsed: ParsedFile): ViewHelper[] {
  const { code } = parsed;
  const types: ViewHelper[] = [];
  for (const scope of parsed.scopes) {
    const header = joinedHeader(code, scope.start - 1, TYPE_HEADER);
    if (!header) continue;
    // `typeScopes` rejoins wrapped headers, so a member declared inside a
    // `struct X: ViewModifier {` is recorded a second time under that struct's
    // name, opening at the member's own brace. A declaration header carries
    // exactly one brace and it is the last thing on it; the rejoined member
    // drags the struct's along, which is what tells the two apart — and unlike
    // reading the brace line back, it still admits a header that wraps.
    if (header.text.split("{").length !== 2 || !header.text.endsWith("{")) continue;
    if (!/\bstruct\s+\w+/.test(header.text)) continue;
    if (!/:\s*[^{]*\bViewModifier\b/.test(header.text)) continue;
    types.push({
      name: scope.name,
      line: header.start + 1,
      endLine: scope.end,
      body: code.slice(scope.start - 1, scope.end).join("\n"),
      file: parsed.file,
    });
  }
  return types;
}

/** Every line in the scan naming `name`, outside the declaration itself. */
function referenceSites(name: string, home: ViewHelper): string[] {
  const pattern = new RegExp(`\\b${name}\\b`);
  const sites: string[] = [];
  for (const parsed of PARSED) {
    for (let i = 0; i < parsed.code.length; i += 1) {
      if (parsed.file === home.file && i + 1 >= home.line && i + 1 <= home.endLine) continue;
      if (pattern.test(parsed.code[i])) sites.push(`${relPath(parsed.file)}:${i + 1}`);
    }
  }
  return sites;
}

/**
 * Which view `modeContent` hands back for one `TodoListMode` case.
 *
 * A guard it cannot read is reported rather than skipped. The whole subject here
 * is a branch structure that looked exhaustive and was not, so a rule that goes
 * quiet the moment the branching gets interesting would be the same failure in a
 * new place.
 */
function modeContentFor(
  parsed: ParsedFile,
  mode: string,
): { text: string; where: string } | { unevaluable: string } {
  const decl = viewDeclarations(parsed).find((entry) => entry.name === "modeContent");
  if (!decl) {
    return { unevaluable: `${relPath(parsed.file)} declares no \`modeContent\`` };
  }

  const { code, structure } = parsed;
  const predicates = boolPredicates(parsed);
  const guards: Branch[] = [];
  const loose: number[] = [];
  for (let i = decl.braceLine; i < decl.endLine - 1; i += 1) {
    if (structure.depthAt[i] !== decl.bodyDepth) continue;
    const index = structure.branches.findIndex((entry) => entry.line === i + 1);
    const branch = index >= 0 ? structure.branches[index] : null;
    if (branch && branch.cond.length > 0 && branch.chainRoot === index) guards.push(branch);
    else if (!branch && code[i].trim().length > 0) loose.push(i);
  }

  for (const guard of guards) {
    const cover = coveredCases(guard.cond, predicates);
    if (!cover) {
      return {
        unevaluable:
          `${relPath(parsed.file)}:${guard.line} branches on \`${guard.cond}\`, which is not a ` +
          "plain test of enum cases — nothing here can say which mode reaches which view. " +
          "`modeContent` routes on the mode and on nothing else; a guard about any other state " +
          "belongs inside the view it routes to, where it cannot cost a whole mode its rows.",
      };
    }
    if (cover.cases.has(mode)) {
      return {
        text: code.slice(guard.line, guard.endLine - 1).join("\n"),
        where: `${relPath(parsed.file)}:${guard.line}`,
      };
    }
  }

  if (loose.length === 0) {
    return {
      unevaluable:
        `${relPath(parsed.file)}:${decl.line} sends \`.${mode}\` into no branch and has no ` +
        "fallback — the case renders nothing at all",
    };
  }
  return {
    text: loose.map((line) => code[line]).join("\n"),
    where: `${relPath(parsed.file)}:${decl.line}`,
  };
}

/** Every private view declaration a view expression reaches, transitively. */
function reachedDeclarations(parsed: ParsedFile, start: string): Declaration[] {
  const byName = new Map(viewDeclarations(parsed).map((entry) => [entry.name, entry]));
  const reached = new Map<string, Declaration>();
  let frontier = [start];
  for (let round = 0; round < 16 && frontier.length > 0; round += 1) {
    const next: string[] = [];
    for (const text of frontier) {
      for (const name of bareIdentifiers(text)) {
        const decl = byName.get(name);
        if (!decl || reached.has(name)) continue;
        reached.set(name, decl);
        next.push(parsed.code.slice(decl.braceLine, decl.endLine - 1).join("\n"));
      }
    }
    frontier = next;
  }
  return [...reached.values()];
}

const SWIPE = /swipe/i;
const SWIPE_APPLICATION = /\.(?:todoTrailingSwipeActions|swipeActions)\s*\(?/;

const SWIPE_HELPERS = PARSED.flatMap((parsed) => [
  ...extensionViewHelpers(parsed),
  ...viewModifierTypes(parsed),
]).filter((helper) => SWIPE.test(helper.name) || SWIPE.test(helper.body));

const TODO_LIST_SCREEN =
  PARSED.find((parsed) => parsed.file.endsWith(`Todos${path.sep}TodoListScreen.swift`)) ?? null;

const MODE_CASES = [...(collectEnumCases().get("TodoListMode") ?? [])].sort();

describeIOS("iOS row actions", () => {
  // The mode list is read from `TodoListMode` itself rather than written out
  // here, so a new case arrives with its own reachability test instead of
  // arriving with six of seven still asserted. The floor is what catches the
  // parse going wrong: seven cases and one screen, or these rules are asserting
  // nothing about an empty set.
  it("still sees the modes and the affordances the rules are about", () => {
    expect(TODO_LIST_SCREEN, "TodoListScreen.swift in the scan").not.toBeNull();
    expect(MODE_CASES.length, "TodoListMode cases").toBeGreaterThanOrEqual(7);
    // One name from each half. The `extension View` arm alone was green over a
    // dead `SwipeRevealHintModifier` for as long as that struct existed, so an
    // arm that silently collects nothing is the failure this floor is for.
    const names = SWIPE_HELPERS.map((helper) => helper.name);
    expect(names).toContain("todoTrailingSwipeActions");
    expect(names).toContain("TodoTrailingSwipeActionsModifier");
  });

  it("every swipe affordance the app declares is applied somewhere", () => {
    const violations = SWIPE_HELPERS.filter(
      (helper) => referenceSites(helper.name, helper).length === 0,
    ).map(
      (helper) =>
        `${relPath(helper.file)}:${helper.line} → \`${helper.name}\` is a swipe nothing applies. ` +
        "Apply it on the live row or delete it: a gesture that exists only in the source reads " +
        "as a shipped feature in review, and on iOS it is also the row's assistive affordance.",
    );
    expect(violations, violations.join("\n")).toEqual([]);
  });

  // One test per `TodoListMode`, because "every mode reaches the live row" is
  // the claim the deleted branch made falsely for years: `standardModeContent`
  // held the swipe, and no mode could reach it. Asserting it per case is what
  // makes the failure say WHICH mode lost its row actions.
  it.each(MODE_CASES)("`.%s` reaches a row that carries the swipe actions", (mode) => {
    const screen = TODO_LIST_SCREEN as ParsedFile;
    const resolved = modeContentFor(screen, mode);
    expect("unevaluable" in resolved ? resolved.unevaluable : "").toBe("");
    if ("unevaluable" in resolved) return;

    const reached = reachedDeclarations(screen, resolved.text);
    const sites: { where: string; gated: boolean }[] = [];
    for (const decl of reached) {
      for (let i = decl.braceLine; i < decl.endLine; i += 1) {
        if (!SWIPE_APPLICATION.test(screen.code[i])) continue;
        sites.push({
          where: `${relPath(screen.file)}:${i + 1} (in \`${decl.name}\`)`,
          gated: screen.structure.enclosing[i].length > 0,
        });
      }
    }

    expect(
      sites.length,
      `\`.${mode}\` resolves to ${resolved.where} and reaches ${reached.length} view ` +
        "declarations, none of which applies a swipe action — the mode renders rows the user " +
        "cannot act on, and VoiceOver rows with nothing on them.",
    ).toBeGreaterThan(0);

    const gated = sites.filter((site) => site.gated).map((site) => site.where);
    expect(
      gated,
      `\`.${mode}\` reaches row actions that sit inside a branch: ${gated.join(", ")}. ` +
        "The row's actions are its accessible actions; a branch around them is a mode or a " +
        "state that silently has none. Gate the behaviour through the modifier's `enabled:` " +
        "argument instead, which keeps the affordance on the row.",
    ).toEqual([]);
  });
});

// ─── The accessible half of a row action ───────────────────────────
//
// The rule above asks whether every mode reaches a row that carries the swipe.
// This one asks the question the swipe cannot answer for itself: a
// `UIPanGestureRecognizer` is not in the accessibility tree, so a capability
// whose only entry point is that pan is a capability VoiceOver, Switch Control
// and Full Keyboard Access all report as absent. The row read as text and
// nothing else, and no compiler here says a word about it — the app target has
// no Swift toolchain on this machine and the tree had no `accessibilityAction`
// in it at all.
//
// So the invariant is parity, not presence: every action input the swipe
// modifier takes must be named inside its accessible-actions block. A fifth
// pill added next year lands as a fifth rotor entry or lands red.

/** The locales `Localizable.xcstrings` carries a value for; `en` is the key. */
const IOS_LOCALES = ["de", "es", "fr", "it", "ja", "ms", "pt", "ru", "zh"];
const STRING_CATALOG = path.join(MONO, "ios-swiftUI", "Tday", "Resources", "Localizable.xcstrings");

interface StringCatalog {
  strings: Record<string, { localizations?: Record<string, { stringUnit?: { value?: string } }> }>;
}

/**
 * Inputs a helper takes that DO something: a closure, or a value carrying one.
 *
 * Both halves of the modifier declare the same four, one as parameters and one
 * as stored properties, so reading the union costs nothing and means neither
 * half can be edited alone into disagreeing with the rule. The type test for the
 * second form is anchored on an uppercase name so `tint: TaskSwipeActionTint.edit`
 * — a colour, not an action — stays out of it.
 */
function actionInputs(helper: ViewHelper): string[] {
  const names = new Set<string>();
  for (const match of helper.body.matchAll(/\b(?:let|var)?\s*(\w+)\s*:\s*(?:@escaping\s+)?\(\s*\)\s*->/g)) {
    names.add(match[1]);
  }
  for (const match of helper.body.matchAll(/\b(\w+)\s*:\s*[A-Z]\w*Action\w*\??(?![\w.])/g)) {
    names.add(match[1]);
  }
  return [...names];
}

/**
 * The 0-based line span of every accessible-actions block in a file.
 *
 * Taken off the stripped code so a brace inside a string cannot move it, and
 * returned as a span rather than as text because the localisation rule below
 * has to read the same lines back out of the raw source with the literals still
 * in them. Both API spellings count: `.accessibilityActions { … }` and a single
 * `.accessibilityAction(named:)` with a trailing closure are the same claim.
 */
function accessibleActionSpans(parsed: ParsedFile): { start: number; end: number }[] {
  const spans: { start: number; end: number }[] = [];
  for (let i = 0; i < parsed.code.length; i += 1) {
    if (!/\.accessibilityActions?\s*[({]/.test(parsed.code[i])) continue;
    let open = i;
    while (open < parsed.code.length && !parsed.code[open].includes("{")) open += 1;
    if (open >= parsed.code.length) continue;
    spans.push({ start: i, end: blockEnd(parsed.code, open) });
  }
  return spans;
}

/** The spans of [accessibleActionSpans] that fall inside a helper's declaration. */
function helperActionSpans(helper: ViewHelper): { start: number; end: number }[] {
  const parsed = PARSED.find((entry) => entry.file === helper.file);
  if (!parsed) return [];
  return accessibleActionSpans(parsed).filter(
    (span) => span.start + 1 >= helper.line && span.end + 1 <= helper.endLine,
  );
}

const ACTION_HELPERS = SWIPE_HELPERS.filter((helper) => actionInputs(helper).length > 0);

describeIOS("iOS row actions reach the accessibility tree", () => {
  it("still sees the action inputs the rule is about", () => {
    // Without this the two rules below are green over an empty set the moment
    // the signature is reformatted past the regex — which is the same failure
    // mode as the swipe nobody applied, one layer up.
    const inputs = new Set(ACTION_HELPERS.flatMap(actionInputs));
    for (const name of ["onEdit", "onCopy", "onDelete", "extraAction"]) {
      expect([...inputs], `\`${name}\` among the swipe modifier's action inputs`).toContain(name);
    }
    // `extensionViewHelpers` slices a declaration from its opening brace, so the
    // `extension View` half's parameters are outside the text this reads and the
    // set comes from the `ViewModifier` half alone. That is the half that renders
    // the pills and would have to grow a fifth one, so naming it is the floor
    // that matters — a count would only have said "one of something".
    expect(
      ACTION_HELPERS.map((helper) => helper.name),
      "the half that renders the row's actions",
    ).toContain("TodoTrailingSwipeActionsModifier");
  });

  it("every action the swipe performs is also an accessibility action", () => {
    const violations: string[] = [];
    for (const helper of ACTION_HELPERS) {
      const spans = helperActionSpans(helper);
      const parsed = PARSED.find((entry) => entry.file === helper.file) as ParsedFile;
      const surface = spans
        .map((span) => parsed.code.slice(span.start, span.end + 1).join("\n"))
        .join("\n");
      for (const name of actionInputs(helper)) {
        if (new RegExp(`\\b${name}\\b`).test(surface)) continue;
        violations.push(
          `${relPath(helper.file)}:${helper.line} → \`${helper.name}\` takes \`${name}\` and ` +
            "never names it inside an accessibility action. The reveal is a pan, and a pan is " +
            "not in the accessibility tree: an action reachable only that way does not exist " +
            "for VoiceOver, Switch Control or Full Keyboard Access.",
        );
      }
    }
    expect(violations, violations.join("\n")).toEqual([]);
  });

  it("names those actions in a string every locale has", () => {
    const catalog = JSON.parse(readFileSync(STRING_CATALOG, "utf-8")) as StringCatalog;
    const violations: string[] = [];
    let labelled = 0;

    for (const helper of ACTION_HELPERS) {
      const parsed = PARSED.find((entry) => entry.file === helper.file) as ParsedFile;
      for (const span of helperActionSpans(helper)) {
        const raw = parsed.raw.slice(span.start, span.end + 1).join("\n");
        const literals = [...raw.matchAll(/"([^"\\]*)"/g)].map((match) => match[1]);
        const keys = [...raw.matchAll(/\bL\(\s*"([^"\\]*)"/g)].map((match) => match[1]);
        labelled += keys.length;

        for (const literal of literals.filter((value) => !keys.includes(value))) {
          violations.push(
            `${relPath(helper.file)}:${span.start + 1} → "${literal}" is an accessibility ` +
              "action name written in English. It is the only name a VoiceOver user ever " +
              "hears for that action; put it through `L(…)` like every other string here.",
          );
        }

        for (const key of keys) {
          const missing = IOS_LOCALES.filter(
            (locale) => !catalog.strings[key]?.localizations?.[locale]?.stringUnit?.value,
          );
          if (missing.length === 0) continue;
          violations.push(
            `${relPath(helper.file)}:${span.start + 1} → \`L("${key}")\` has no value for ` +
              `${missing.join(", ")}. A missing key falls back to the English key silently, so ` +
              "the action is reachable and unreadable at once.",
          );
        }
      }
    }

    expect(violations, violations.join("\n")).toEqual([]);
    expect(labelled, "localised accessibility action names").toBeGreaterThanOrEqual(3);
  });
});

// ─── A count the user just changed ─────────────────────────────────
//
// `.contentTransition(.numericText(value:))` fails the way a `.transition`
// does, and more quietly. It is not a spec that runs; it is a rendering mode
// that means nothing unless the text change it describes happens inside an
// animation transaction. Written on its own it costs nothing, breaks nothing
// and does nothing — the label swaps 7 for 6 between two frames exactly as it
// did before — while reading, in review, like the fix.
//
// Rule A cannot ask this: it is keyed on `.transition(`, and a count writes
// none. The label is never inserted or removed, only relabelled, so the pair
// that has to be held together here is `.contentTransition` and the
// `.animation(_:value:)` keyed on the count itself.
//
// The surfaces are the four feed counts — the date card's 34 pt number, the
// category tiles, the scheduled list rows and the floater list cards — whose
// only job is to report a number the user just changed. A fifth one added next
// year either rolls or lands red; there is no allowlist, because a count that
// hard-swaps is not a decision anyone has argued for.
//
// The gate is asserted here too. Reduce Motion for a roll means the new number
// arriving whole on the frame it changed, which is what `tdayAnimation`
// returning nil does — and a device pass cannot tell a missing gate from a
// short one, so the only place that distinction can be caught is the text.

/** A label whose entire content is the interpolated count, and nothing else. */
const COUNT_LABEL = /^Text\("\\\(count\)"\)$/;

interface CountLabel {
  file: string;
  /** 1-based, the `Text(` line. */
  line: number;
  chain: string;
}

/**
 * The modifier chain a label owns: the lines after it that open with `.`, plus
 * whatever a multi-line call carries between its parentheses.
 *
 * Walked off the stripped code, so a comment between two modifiers is a blank
 * line rather than the end of the chain, and so no paren inside a string can
 * move the depth counter. A blank line is skipped at depth 0 for the same
 * reason; the chain still ends at the first line that is neither — the `}` that
 * closes the stack the label sits in.
 */
function modifierChain(parsed: ParsedFile, index: number): string {
  const lines: string[] = [];
  let depth = 0;
  for (let i = index + 1; i < parsed.code.length; i += 1) {
    const trimmed = parsed.code[i].trim();
    if (depth === 0) {
      if (trimmed.length === 0) continue;
      if (!trimmed.startsWith(".")) break;
    }
    lines.push(parsed.code[i]);
    for (const ch of parsed.code[i]) {
      if (ch === "(") depth += 1;
      else if (ch === ")") depth -= 1;
    }
  }
  return lines.join("\n");
}

const COUNT_LABELS: CountLabel[] = PARSED.flatMap((parsed) =>
  parsed.raw.flatMap((line, index) =>
    COUNT_LABEL.test(line.trim())
      ? [{ file: parsed.file, line: index + 1, chain: modifierChain(parsed, index) }]
      : [],
  ),
);

describeIOS("iOS feed counts roll rather than swap", () => {
  it("still sees the labels and the chains the rule is about", () => {
    // The label is matched on raw source because the stripper blanks the very
    // interpolation that identifies it, and a chain read off the wrong array is
    // an empty string that passes nothing — so both halves are checked here
    // rather than discovered as a green run over no sites.
    expect(COUNT_LABELS.length, '`Text("\\(count)")` feed labels').toBeGreaterThanOrEqual(4);
    expect(
      [...new Set(COUNT_LABELS.map((label) => relPath(label.file)))].sort(),
      "the files the feed's counts live in",
    ).toEqual([
      "ios-swiftUI/Tday/Feature/ScheduledTaskHome/ScheduledTaskHomeScreen.swift",
      "ios-swiftUI/Tday/Feature/Todos/TodoListScreen.swift",
    ]);
    const unread = COUNT_LABELS.filter((label) => !label.chain.includes(".font("));
    expect(
      unread.map((label) => `${relPath(label.file)}:${label.line}`),
      "a count whose chain carries no `.font(` is a chain the walk stopped reading early",
    ).toEqual([]);
  });

  it("every feed count carries the digit roll and a transaction to run it in", () => {
    const violations: string[] = [];

    for (const label of COUNT_LABELS) {
      const site = `${relPath(label.file)}:${label.line}`;

      if (!label.chain.includes(".contentTransition(.numericText(")) {
        violations.push(
          `${site} → no \`.contentTransition(.numericText(\` on the label. The count reports a ` +
            "number the user just changed; without the roll it swaps between two frames and " +
            "says nothing about having changed.",
        );
        continue;
      }

      const keyed = animatedValueSites(label.chain).filter((animation) =>
        rootIdentifiers(animation.expr).includes("count"),
      );
      if (keyed.length === 0) {
        violations.push(
          `${site} → \`.numericText\` with no \`.animation(_:value:)\` keyed on \`count\`. A ` +
            "content transition is a rendering mode, not a spec: outside a transaction the " +
            "modifier is inert and the label hard-swaps exactly as it did before.",
        );
        continue;
      }

      if (!/\.animation\s*\(\s*tdayAnimation\s*\(/.test(label.chain)) {
        violations.push(
          `${site} → the roll's \`.animation(\` does not open on \`tdayAnimation(\`, so Reduce ` +
            "Motion gets a shorter roll instead of the finished number on the frame it changed.",
        );
      }
    }

    expect(violations, violations.join("\n")).toEqual([]);
  });
});
