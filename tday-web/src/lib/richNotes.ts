// Canonical rich-text encoding for task/floater "notes" (`description`).
//
// The backend column is a plain nullable string end to end — formatting is
// encoded *inside* that string by convention, entirely client-side. A note
// with real formatting (bold/italic/underline/strike/lists) is stored as
// `RICH_NOTES_MARKER + sanitizedHtml`; everything else (including multi-line
// plain text) is stored as a plain string with real "\n" characters, exactly
// like today — so untouched notes round-trip byte-identical.
//
// This same convention (marker + allow-listed tags) must be reimplemented
// identically on Android and iOS for cross-platform notes to round-trip.

export const RICH_NOTES_MARKER = "<!--tday:rich-->";

// No attributes are ever kept on any of these — that's what makes stripping
// pasted font-size/color/family structural rather than best-effort cleanup.
const ALLOWED_TAGS = new Set([
  "b",
  "strong",
  "i",
  "em",
  "u",
  "s",
  "strike",
  "ul",
  "ol",
  "li",
  "p",
  "br",
]);

// Tags whose *contents* must never leak into sanitized output (unlike a
// disallowed wrapper like <div>/<span>, which is safe to unwrap and keep the
// text of).
const DROP_CONTENTS_TAGS = new Set(["script", "style"]);

const FORMATTING_TAG_RE = /<(b|strong|i|em|u|s|strike|ul|ol)(\s|>)/i;

export function isRichNotes(value: string | null | undefined): boolean {
  return typeof value === "string" && value.startsWith(RICH_NOTES_MARKER);
}

function sanitizeInto(source: Node, target: Node, doc: Document) {
  source.childNodes.forEach((child) => {
    if (child.nodeType === Node.TEXT_NODE) {
      target.appendChild(doc.createTextNode(child.textContent ?? ""));
      return;
    }
    if (child.nodeType !== Node.ELEMENT_NODE) return;
    const el = child as Element;
    const tag = el.tagName.toLowerCase();
    if (DROP_CONTENTS_TAGS.has(tag)) return;
    if (ALLOWED_TAGS.has(tag)) {
      const clean = doc.createElement(tag);
      sanitizeInto(el, clean, doc);
      target.appendChild(clean);
    } else {
      // Unwrap: drop the tag itself (and any attributes/style/font-size on
      // it) but keep its sanitized children — e.g. a pasted <span style="…">.
      sanitizeInto(el, target, doc);
    }
  });
}

// Strips everything down to the allowed tag set with no attributes. Safe to
// call on untrusted pasted HTML or on our own editor output (defense in
// depth) — every disallowed element (including <script>/<img>/<a>/<svg>) is
// either unwrapped to plain text or dropped outright, so there's no
// attribute-based XSS surface left (no href/src/style/on* ever survives).
export function sanitizeHtml(html: string): string {
  const doc = new DOMParser().parseFromString(html, "text/html");
  const container = doc.createElement("div");
  sanitizeInto(doc.body, container, doc);
  return container.innerHTML;
}

export function htmlHasFormatting(html: string): boolean {
  return FORMATTING_TAG_RE.test(html);
}

function getInlineText(el: Node): string {
  let out = "";
  el.childNodes.forEach((child) => {
    if (child.nodeType === Node.TEXT_NODE) {
      out += child.textContent ?? "";
    } else if (child.nodeType === Node.ELEMENT_NODE) {
      const tag = (child as Element).tagName.toLowerCase();
      out += tag === "br" ? "\n" : getInlineText(child);
    }
  });
  return out;
}

function extractBlockLines(container: Node): string[] {
  const lines: string[] = [];
  container.childNodes.forEach((child) => {
    if (child.nodeType !== Node.ELEMENT_NODE) {
      const text = child.textContent ?? "";
      if (text.trim().length) lines.push(text);
      return;
    }
    const el = child as Element;
    const tag = el.tagName.toLowerCase();
    if (tag === "ul" || tag === "ol") {
      let counter = 0;
      el.childNodes.forEach((liNode) => {
        if (liNode.nodeType !== Node.ELEMENT_NODE) return;
        const li = liNode as Element;
        if (li.tagName.toLowerCase() !== "li") return;
        counter += 1;
        const prefix = tag === "ul" ? "• " : `${counter}. `;
        getInlineText(li)
          .split("\n")
          .forEach((part, idx) => lines.push(idx === 0 ? prefix + part : part));
      });
    } else if (tag === "p") {
      getInlineText(el)
        .split("\n")
        .forEach((part) => lines.push(part));
    } else if (tag === "br") {
      lines.push("");
    } else {
      lines.push(...extractBlockLines(el));
    }
  });
  return lines;
}

export function htmlToPlainText(html: string): string {
  const doc = new DOMParser().parseFromString(html, "text/html");
  return extractBlockLines(doc.body).join("\n").trim();
}

function escapeHtml(text: string): string {
  return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

// Editor HTML → the string that gets saved. Multi-line-but-unstyled input
// stays a plain "\n"-joined string with no marker (matches today's data
// shape exactly); only real formatting opts into the marker+HTML encoding.
export function encodeNotes(editorHtml: string): string {
  const sanitized = sanitizeHtml(editorHtml);
  if (htmlHasFormatting(sanitized)) {
    return RICH_NOTES_MARKER + sanitized;
  }
  return htmlToPlainText(sanitized);
}

// Saved string → editor HTML, for initializing/resetting the editor.
export function decodeNotesToHtml(value: string | null | undefined): string {
  if (!value) return "<p></p>";
  if (isRichNotes(value)) {
    return sanitizeHtml(value.slice(RICH_NOTES_MARKER.length)) || "<p></p>";
  }
  return value.split("\n").map((line) => `<p>${escapeHtml(line)}</p>`).join("");
}

// Saved string → flattened plain text, for anywhere notes are shown outside
// the editor (list rows, search, share text): real markup never leaks out,
// but list bullets/numbers are kept as plain-text prefixes so the structure
// still reads.
export function flattenNotesToPlainText(value: string | null | undefined): string {
  if (!value) return "";
  if (isRichNotes(value)) {
    return htmlToPlainText(sanitizeHtml(value.slice(RICH_NOTES_MARKER.length)));
  }
  return value;
}
