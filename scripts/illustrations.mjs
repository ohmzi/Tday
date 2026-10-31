#!/usr/bin/env node
// Generates the illustrated-state artwork for every client from one source.
//
//   design/illustrations/scenes/<id>.svg   one scene, 400x300, colours as var(--token)
//   design/illustrations/palette.json      the tokens, light and dark
//   design/illustrations/motion.css        web-only ambient loops (classes in the scenes)
//
// Outputs (never hand-edit them; rerun this script):
//   web      tday-web/src/components/illustrations/scenes.generated.ts
//            tday-web/src/components/illustrations/illustrations.generated.css
//   android  android-compose/app/src/main/res/drawable/illus_<id>_{light,dark}.xml  (VectorDrawable)
//            android-compose/.../core/ui/TdayIllustration.kt  (enum of the pairs)
//   ios      ios-swiftUI/Tday/Assets.xcassets/Illus<Id>.imageset  (light + dark SVG)
//            ios-swiftUI/Tday/Core/UI/TdayIllustration.swift  (enum of the asset names)
//
// Android picks light/dark itself (not drawable-night) because the in-app theme
// can differ from the system one; iOS assets follow the SwiftUI colour scheme.
//
// Native art is static: classes, <text> and dashes are dropped there, and the
// whole scene floats instead (TdayEmptyState). `--check` exits 1 when any
// output differs from what is committed, so CI catches drift.
//
// Usage: node scripts/illustrations.mjs [--check]

import { createRequire } from "node:module";
import { existsSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const require = createRequire(join(ROOT, "tday-web", "package.json"));
const { JSDOM } = require("jsdom");

const SRC = join(ROOT, "design", "illustrations");
const WEB_DIR = join(ROOT, "tday-web", "src", "components", "illustrations");
const RES = join(ROOT, "android-compose", "app", "src", "main", "res");
const XCASSETS = join(ROOT, "ios-swiftUI", "Tday", "Assets.xcassets");
const KOTLIN_ENUM = join(ROOT, "android-compose", "app", "src", "main", "java", "com", "ohmz", "tday", "compose", "core", "ui", "TdayIllustration.kt");
const SWIFT_ENUM = join(ROOT, "ios-swiftUI", "Tday", "Core", "UI", "TdayIllustration.swift");
const check = process.argv.includes("--check");

const palette = JSON.parse(readFileSync(join(SRC, "palette.json"), "utf8"));
const motionCss = readFileSync(join(SRC, "motion.css"), "utf8");
const ids = readdirSync(join(SRC, "scenes"))
  .filter((f) => f.endsWith(".svg"))
  .map((f) => f.slice(0, -4))
  .sort();

const outputs = new Map(); // path -> content
const emit = (path, content) => outputs.set(path, content);
const pascal = (id) => id.replace(/(^|[-_])(\w)/g, (_, __, c) => c.toUpperCase());
const snake = (id) => id.replace(/-/g, "_");

// ---------------------------------------------------------------- colours

function parseColor(raw, mode, where) {
  let v = raw.trim();
  const m = /^var\(--([\w-]+)\)$/.exec(v);
  if (m) {
    v = palette[mode][m[1]];
    if (v === undefined) throw new Error(`${where}: unknown token --${m[1]}`);
  }
  if (v === "none" || v === "transparent") return null;
  if (v === "white") v = "#FFFFFF";
  if (v === "black") v = "#000000";
  let hex = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(v);
  if (hex) {
    let h = hex[1];
    if (h.length === 3) h = [...h].map((c) => c + c).join("");
    return { hex: `#${h.toUpperCase()}`, alpha: 1 };
  }
  const rgba = /^rgba?\(([^)]+)\)$/.exec(v);
  if (rgba) {
    const [r, g, b, a = "1"] = rgba[1].split(",").map((s) => s.trim());
    const h = [r, g, b].map((n) => Number(n).toString(16).padStart(2, "0")).join("");
    return { hex: `#${h.toUpperCase()}`, alpha: Number(a) };
  }
  throw new Error(`${where}: unsupported colour ${raw}`);
}

// ---------------------------------------------------------------- source loading

function load(id) {
  const text = readFileSync(join(SRC, "scenes", `${id}.svg`), "utf8");
  const dom = new JSDOM(text, { contentType: "image/svg+xml" });
  const svg = dom.window.document.documentElement;
  if (svg.getAttribute("viewBox") !== "0 0 400 300") throw new Error(`${id}: viewBox must be 0 0 400 300`);
  for (const el of svg.querySelectorAll("[id]")) {
    if (!el.id.startsWith(`${id}-`)) throw new Error(`${id}: id "${el.id}" must start with "${id}-"`);
  }
  return { text, dom, svg };
}

// ---------------------------------------------------------------- web

function webMarkup(id, svg) {
  // Inner markup only; the component owns the <svg>. Tokens move to --il-*
  // and classes to il-* so neither can collide with the app's own.
  let inner = svg.innerHTML.trim();
  inner = inner.replace(/var\(--([\w-]+)\)/g, (_, t) => {
    if (palette.light[t] === undefined) throw new Error(`${id}: unknown token --${t}`);
    return `var(--il-${t})`;
  });
  inner = inner.replace(/class="([^"]+)"/g, (_, c) => `class="${c.split(/\s+/).map((k) => `il-${k}`).join(" ")}"`);
  inner = inner.replace(/<!--[\s\S]*?-->/g, "").replace(/\n\s*\n/g, "\n").replace(/^\s+/gm, "");
  return inner;
}

function emitWeb(scenes) {
  const entries = scenes.map(({ id, svg }) => `  ${JSON.stringify(id)}: ${JSON.stringify(webMarkup(id, svg))},`);
  emit(
    join(WEB_DIR, "scenes.generated.ts"),
    `// GENERATED by scripts/illustrations.mjs from design/illustrations. Do not edit.\n` +
      `export const ILLUSTRATION_SCENES = {\n${entries.join("\n")}\n} as const;\n\n` +
      `export type IllustrationId = keyof typeof ILLUSTRATION_SCENES;\n`,
  );
  const vars = (mode) =>
    Object.entries(palette[mode])
      .map(([k, v]) => `  --il-${k}: ${v};`)
      .join("\n");
  emit(
    join(WEB_DIR, "illustrations.generated.css"),
    `/* GENERATED by scripts/illustrations.mjs from design/illustrations. Do not edit. */\n` +
      `.tday-illus {\n${vars("light")}\n}\n.dark .tday-illus {\n${vars("dark")}\n}\n\n${motionCss}`,
  );
}

// ---------------------------------------------------------------- geometry

const num = (el, a, d = 0) => (el.hasAttribute(a) ? Number(el.getAttribute(a)) : d);
const f = (n) => String(Math.round(n * 1000) / 1000);

function shapePath(el) {
  switch (el.localName) {
    case "path":
      return el.getAttribute("d");
    case "rect": {
      const x = num(el, "x"), y = num(el, "y"), w = num(el, "width"), h = num(el, "height");
      let rx = el.hasAttribute("rx") ? num(el, "rx") : num(el, "ry");
      let ry = el.hasAttribute("ry") ? num(el, "ry") : rx;
      rx = Math.min(rx, w / 2);
      ry = Math.min(ry, h / 2);
      if (!rx || !ry) return `M${f(x)} ${f(y)}h${f(w)}v${f(h)}h${f(-w)}Z`;
      return (
        `M${f(x + rx)} ${f(y)}H${f(x + w - rx)}A${f(rx)} ${f(ry)} 0 0 1 ${f(x + w)} ${f(y + ry)}` +
        `V${f(y + h - ry)}A${f(rx)} ${f(ry)} 0 0 1 ${f(x + w - rx)} ${f(y + h)}` +
        `H${f(x + rx)}A${f(rx)} ${f(ry)} 0 0 1 ${f(x)} ${f(y + h - ry)}` +
        `V${f(y + ry)}A${f(rx)} ${f(ry)} 0 0 1 ${f(x + rx)} ${f(y)}Z`
      );
    }
    case "circle":
    case "ellipse": {
      const cx = num(el, "cx"), cy = num(el, "cy");
      const rx = el.localName === "circle" ? num(el, "r") : num(el, "rx");
      const ry = el.localName === "circle" ? num(el, "r") : num(el, "ry");
      return (
        `M${f(cx - rx)} ${f(cy)}A${f(rx)} ${f(ry)} 0 1 0 ${f(cx + rx)} ${f(cy)}` +
        `A${f(rx)} ${f(ry)} 0 1 0 ${f(cx - rx)} ${f(cy)}Z`
      );
    }
    case "line":
      return `M${f(num(el, "x1"))} ${f(num(el, "y1"))}L${f(num(el, "x2"))} ${f(num(el, "y2"))}`;
    case "polyline":
    case "polygon": {
      const pts = el.getAttribute("points").trim().split(/[\s,]+/).map(Number);
      let d = "";
      for (let i = 0; i < pts.length; i += 2) d += `${i ? "L" : "M"}${f(pts[i])} ${f(pts[i + 1])}`;
      return el.localName === "polygon" ? `${d}Z` : d;
    }
    default:
      return null;
  }
}

function parseTransform(t, where) {
  const ops = [];
  for (const m of (t || "").matchAll(/(\w+)\(([^)]*)\)/g)) {
    const a = m[2].trim().split(/[\s,]+/).map(Number);
    if (m[1] === "translate") ops.push({ translateX: a[0], translateY: a[1] ?? 0 });
    else if (m[1] === "scale") ops.push({ scaleX: a[0], scaleY: a[1] ?? a[0] });
    else if (m[1] === "rotate") ops.push({ rotation: a[0], pivotX: a[1] ?? 0, pivotY: a[2] ?? 0 });
    else throw new Error(`${where}: unsupported transform ${m[0]}`);
  }
  return ops;
}

// ---------------------------------------------------------------- shared static flattening

const INHERITED = ["fill", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin", "fill-rule"];

/** Static tree for native targets: <use> inlined, text dropped, styles resolved. */
function flatten(id, svg, doc) {
  const byId = (ref) => doc.getElementById(ref.replace(/^#/, ""));
  const walk = (el, inherit, opacity) => {
    const where = `${id}:<${el.localName}>`;
    if (["text", "title", "desc", "defs", "clipPath", "style"].includes(el.localName)) return null;
    if (el.localName === "use") {
      const ref = byId(el.getAttribute("href") || el.getAttribute("xlink:href"));
      if (!ref) throw new Error(`${where}: dangling use`);
      const x = num(el, "x"), y = num(el, "y");
      const g = walk(ref, inherit, opacity);
      if (!g) return null;
      const ops = parseTransform(el.getAttribute("transform"), where);
      if (x || y) ops.push({ translateX: x, translateY: y });
      return ops.length ? { kind: "group", ops, clip: null, children: [g] } : g;
    }
    const style = { ...inherit };
    for (const a of INHERITED) if (el.hasAttribute(a)) style[a] = el.getAttribute(a);
    const op = opacity * num(el, "opacity", 1);
    const ops = parseTransform(el.getAttribute("transform"), where);
    let clip = null;
    const cp = /url\(#([^)]+)\)/.exec(el.getAttribute("clip-path") || "");
    if (cp) {
      const c = byId(cp[1]);
      if (!c) throw new Error(`${where}: dangling clip-path`);
      clip = [...c.children].map(shapePath).filter(Boolean).join(" ");
    }
    if (el.localName === "g" || el.localName === "svg") {
      const children = [...el.children].map((c) => walk(c, style, op)).filter(Boolean);
      if (!children.length) return null;
      return { kind: "group", ops, clip, children };
    }
    const d = shapePath(el);
    if (!d) throw new Error(`${where}: unsupported element`);
    const leaf = {
      kind: "path",
      d,
      fill: el.hasAttribute("fill") || style.fill ? style.fill ?? "#000" : "#000",
      fillOpacity: op * num(el, "fill-opacity", 1),
      stroke: style.stroke ?? null,
      strokeOpacity: op * num(el, "stroke-opacity", 1),
      strokeWidth: Number(style["stroke-width"] ?? 1),
      cap: style["stroke-linecap"] ?? "butt",
      join: style["stroke-linejoin"] ?? "miter",
      fillRule: style["fill-rule"] ?? "nonzero",
    };
    return ops.length || clip ? { kind: "group", ops, clip, children: [leaf] } : leaf;
  };
  return walk(svg, {}, 1);
}

// ---------------------------------------------------------------- android

function vectorDrawable(id, tree, mode) {
  const lines = [];
  const color = (raw, alpha, where) => {
    const c = parseColor(raw, mode, where);
    if (!c) return null;
    const a = Math.max(0, Math.min(1, c.alpha * alpha));
    return { hex: c.hex, a };
  };
  const pad = (n) => "    ".repeat(n);
  const writeNode = (node, depth) => {
    if (node.kind === "path") {
      const fill = node.fill ? color(node.fill, node.fillOpacity, id) : null;
      const stroke = node.stroke ? color(node.stroke, node.strokeOpacity, id) : null;
      if (!fill && !stroke) return;
      const attrs = [`android:pathData="${node.d}"`];
      if (fill) {
        attrs.push(`android:fillColor="${fill.hex}"`);
        if (fill.a < 1) attrs.push(`android:fillAlpha="${f(fill.a)}"`);
        if (node.fillRule === "evenodd") attrs.push(`android:fillType="evenOdd"`);
      }
      if (stroke) {
        attrs.push(`android:strokeColor="${stroke.hex}"`, `android:strokeWidth="${f(node.strokeWidth)}"`);
        if (stroke.a < 1) attrs.push(`android:strokeAlpha="${f(stroke.a)}"`);
        if (node.cap !== "butt") attrs.push(`android:strokeLineCap="${node.cap}"`);
        if (node.join !== "miter") attrs.push(`android:strokeLineJoin="${node.join}"`);
      }
      lines.push(`${pad(depth)}<path ${attrs.join(" ")} />`);
      return;
    }
    // One <group> per transform op, outermost first, then the clip inside the
    // innermost, matching SVG's left-to-right transform list.
    let d = depth;
    const opened = [];
    for (const op of node.ops) {
      const attrs = Object.entries(op)
        .filter(([, v]) => v !== 0 || false)
        .map(([k, v]) => `android:${k}="${f(v)}"`);
      lines.push(`${pad(d)}<group${attrs.length ? " " + attrs.join(" ") : ""}>`);
      opened.push(d++);
    }
    if (node.clip) {
      lines.push(`${pad(d)}<group>`);
      opened.push(d++);
      lines.push(`${pad(d)}<clip-path android:pathData="${node.clip}" />`);
    }
    for (const c of node.children) writeNode(c, d);
    while (opened.length) lines.push(`${pad(opened.pop())}</group>`);
  };
  writeNode(tree, 1);
  return (
    `<?xml version="1.0" encoding="utf-8"?>\n` +
    `<!-- GENERATED by scripts/illustrations.mjs from design/illustrations/scenes/${id}.svg (${mode}). Do not edit. -->\n` +
    `<vector xmlns:android="http://schemas.android.com/apk/res/android"\n` +
    `    android:width="400dp" android:height="300dp"\n` +
    `    android:viewportWidth="400" android:viewportHeight="300">\n` +
    `${lines.join("\n")}\n</vector>\n`
  );
}

// ---------------------------------------------------------------- ios

function staticSvg(id, tree, mode) {
  const parts = [];
  const paint = (raw, alpha, kind) => {
    const c = raw ? parseColor(raw, mode, id) : null;
    if (!c) return ` ${kind}="none"`;
    const a = c.alpha * alpha;
    return ` ${kind}="${c.hex}"${a < 1 ? ` ${kind}-opacity="${f(a)}"` : ""}`;
  };
  let clipN = 0;
  const defs = [];
  const writeNode = (node) => {
    if (node.kind === "path") {
      let a = `<path d="${node.d}"` + paint(node.fill, node.fillOpacity, "fill");
      if (node.fillRule === "evenodd") a += ` fill-rule="evenodd"`;
      if (node.stroke) {
        a += paint(node.stroke, node.strokeOpacity, "stroke") + ` stroke-width="${f(node.strokeWidth)}"`;
        if (node.cap !== "butt") a += ` stroke-linecap="${node.cap}"`;
        if (node.join !== "miter") a += ` stroke-linejoin="${node.join}"`;
      }
      parts.push(`${a}/>`);
      return;
    }
    const t = node.ops
      .map((o) =>
        "translateX" in o
          ? `translate(${f(o.translateX)} ${f(o.translateY)})`
          : "scaleX" in o
            ? `scale(${f(o.scaleX)} ${f(o.scaleY)})`
            : `rotate(${f(o.rotation)} ${f(o.pivotX)} ${f(o.pivotY)})`,
      )
      .join(" ");
    parts.push(`<g${t ? ` transform="${t}"` : ""}>`);
    if (node.clip) {
      const cid = `c${clipN++}`;
      defs.push(`<clipPath id="${cid}"><path d="${node.clip}"/></clipPath>`);
      parts.push(`<g clip-path="url(#${cid})">`);
    }
    node.children.forEach(writeNode);
    if (node.clip) parts.push("</g>");
    parts.push("</g>");
  };
  writeNode(tree);
  return (
    `<svg xmlns="http://www.w3.org/2000/svg" width="400" height="300" viewBox="0 0 400 300">` +
    (defs.length ? `<defs>${defs.join("")}</defs>` : "") +
    `${parts.join("")}</svg>\n`
  );
}

function emitIos(id, tree) {
  const dir = join(XCASSETS, `Illus${pascal(id)}.imageset`);
  emit(join(dir, `${id}-light.svg`), staticSvg(id, tree, "light"));
  emit(join(dir, `${id}-dark.svg`), staticSvg(id, tree, "dark"));
  emit(
    join(dir, "Contents.json"),
    JSON.stringify(
      {
        images: [
          { filename: `${id}-light.svg`, idiom: "universal" },
          {
            appearances: [{ appearance: "luminosity", value: "dark" }],
            filename: `${id}-dark.svg`,
            idiom: "universal",
          },
        ],
        info: { author: "xcode", version: 1 },
        properties: { "preserves-vector-representation": true },
      },
      null,
      2,
    ) + "\n",
  );
}

// ---------------------------------------------------------------- run

const scenes = ids.map((id) => ({ id, ...load(id) }));
emitWeb(scenes);
for (const { id, svg, dom } of scenes) {
  const tree = flatten(id, svg, dom.window.document);
  emit(join(RES, "drawable", `illus_${snake(id)}_light.xml`), vectorDrawable(id, tree, "light"));
  emit(join(RES, "drawable", `illus_${snake(id)}_dark.xml`), vectorDrawable(id, tree, "dark"));
  emitIos(id, tree);
}

const camel = (id) => pascal(id).replace(/^./, (c) => c.toLowerCase());
emit(
  KOTLIN_ENUM,
  `// GENERATED by scripts/illustrations.mjs from design/illustrations. Do not edit.\n` +
    `package com.ohmz.tday.compose.core.ui\n\n` +
    `import androidx.annotation.DrawableRes\n` +
    `import com.ohmz.tday.compose.R\n\n` +
    `/** The illustrated-state scenes, each a light and a dark VectorDrawable. */\n` +
    `enum class TdayIllustration(@DrawableRes val light: Int, @DrawableRes val dark: Int) {\n` +
    ids.map((id) => `    ${snake(id).toUpperCase()}(R.drawable.illus_${snake(id)}_light, R.drawable.illus_${snake(id)}_dark),`).join("\n") +
    `\n}\n`,
);
emit(
  SWIFT_ENUM,
  `// GENERATED by scripts/illustrations.mjs from design/illustrations. Do not edit.\n` +
    `import Foundation\n\n` +
    `/// The illustrated-state scenes. Each asset carries a light and a dark appearance.\n` +
    `enum TdayIllustration: String, CaseIterable {\n` +
    ids.map((id) => `    case ${camel(id)} = "Illus${pascal(id)}"`).join("\n") +
    `\n}\n`,
);

// Generated files for a scene that no longer exists.
const stale = [];
for (const dir of [join(RES, "drawable")]) {
  if (!existsSync(dir)) continue;
  for (const file of readdirSync(dir)) {
    if (file.startsWith("illus_") && !outputs.has(join(dir, file))) stale.push(join(dir, file));
  }
}
if (existsSync(XCASSETS)) {
  for (const set of readdirSync(XCASSETS)) {
    if (set.startsWith("Illus") && set.endsWith(".imageset") && !ids.some((id) => set === `Illus${pascal(id)}.imageset`)) {
      stale.push(join(XCASSETS, set));
    }
  }
}

let drift = stale.map((p) => `stale: ${p.slice(ROOT.length + 1)}`);
for (const [path, content] of outputs) {
  const current = existsSync(path) ? readFileSync(path, "utf8") : null;
  if (current === content) continue;
  if (check) drift.push(`${current === null ? "missing" : "differs"}: ${path.slice(ROOT.length + 1)}`);
  else {
    mkdirSync(dirname(path), { recursive: true });
    writeFileSync(path, content);
  }
}
if (check) {
  if (drift.length) {
    console.error(`Illustrations are out of date — run node scripts/illustrations.mjs\n  ${drift.join("\n  ")}`);
    process.exit(1);
  }
  console.log(`Illustrations are up to date (${ids.length} scenes).`);
} else {
  for (const p of stale) rmSync(p, { recursive: true, force: true });
  console.log(`Illustrations generated (${ids.length} scenes, ${outputs.size} files${stale.length ? `, ${stale.length} stale removed` : ""}).`);
}
