/**
 * The two task cues are precached, and the glob that says so lives where the build reads it.
 *
 * This is a config assertion because nothing else can see the difference. `injectManifest` builds
 * the manifest at bundle time from `injectManifest.globPatterns`; the `workbox` key beside it is
 * read by `generateSW` alone, and this project injects `src/sw.ts` instead. The list used to sit
 * under `workbox`, so it was inert and workbox fell back to its own default — js, wasm, css and
 * html, and nothing else. Nothing failed and nothing was logged: the manifest simply had no
 * audio in it, `/task-complete.wav` stayed a network asset, and an element that could not load it
 * refuses `play()` without reporting the refusal, which is the shape the bug took in the field.
 *
 * A unit test cannot see a build-time glob, so this pins the two things that went wrong: the key
 * the pattern is written under, and `wav` being in it.
 */

import { readFileSync } from "fs";
import path from "path";
import { describe, expect, it } from "vitest";

const ROOT = path.resolve(__dirname, "..", "..");
const VITE_CONFIG = path.join(ROOT, "vite.config.ts");

/**
 * Comments stripped first, because this file's own explanation of the dead key names it — and a
 * test that a comment can satisfy is a test that says nothing.
 *
 * Both patterns are anchored to the start of a line on purpose. A comment here is written on its
 * own lines, and an unanchored block-comment match would find the two characters that open one
 * inside the glob's own brace expansion and run on to the next closing pair in the file,
 * deleting the very line under test along with everything between.
 */
const source = readFileSync(VITE_CONFIG, "utf-8")
  .replace(/^[ \t]*\/\*[\s\S]*?\*\/[ \t]*$/gm, "")
  .replace(/^[ \t]*\/\/.*$/gm, "");

function block(option: string): string {
  return source.match(new RegExp(`${option}:\\s*\\{([\\s\\S]*?)\\}`))?.[1] ?? "";
}

describe("the task cues are in the precache manifest", () => {
  it("writes the glob under `injectManifest`, the key the build reads", () => {
    expect(block("injectManifest")).toMatch(/globPatterns/);
  });

  it("does not write it under `workbox`, where it would be inert", () => {
    expect(block("workbox")).not.toMatch(/globPatterns/);
  });

  it("asks for wav, so both clips are fetched at install and not at the tap", () => {
    expect(block("injectManifest")).toMatch(/\bwav\b/);
  });
});
