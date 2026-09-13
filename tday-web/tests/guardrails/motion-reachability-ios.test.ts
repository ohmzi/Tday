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
}

/** The `value:` expression of every `.animation(_:value:)` in a slice. */
function animatedValueExpressions(text: string): string[] {
  const exprs: string[] = [];
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
    if (valueAt >= 0) exprs.push(args.slice(valueAt + "value:".length));
  }
  return exprs;
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

    expect(IOS_FILES.length, "iOS .swift files").toBeGreaterThan(80);
    expect(transitions, "`.transition(` sites").toBeGreaterThan(15);
    expect(animated, "`.animation(_:value:)` sites").toBeGreaterThan(30);
    expect(branches, "if/else branches").toBeGreaterThan(500);
    expect(scopes, "type scopes").toBeGreaterThan(100);
    expect(decls, "private view declarations").toBeGreaterThan(60);
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
