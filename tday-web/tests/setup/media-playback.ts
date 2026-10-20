/**
 * The two media methods jsdom leaves as holes.
 *
 * jsdom defines `HTMLMediaElement.prototype.play` and `.load`, but both are placeholders that
 * report "Not implemented" and return `undefined`. A browser's `play()` answers with a promise
 * that settles when the cue starts or is refused, and `TodoCheckbox` reads that promise — it
 * records a breadcrumb when the browser refuses a cue. Against jsdom's `undefined` every test
 * that ticks a task would fail on a `TypeError` from the app's own catch handler, which is a
 * failure in the harness and not in the app.
 *
 * So the two are given the platform's shape here, once, for the same reason `web-storage.ts`
 * installs a `localStorage` that works: the harness should hand the app a browser rather than a
 * browser-shaped hole. A resolved promise is the honest default — jsdom's own media stack cannot
 * decode anything, and refusing would make "no sound" the answer in every suite that merely
 * happens to tick a checkbox.
 *
 * A test with an opinion still overrides the prototype over the top: `feedback-preferences` spies
 * on `play` to count cues, and `todo-checkbox-sound-refusal` replaces both to drive a refusal.
 * `load()` has nothing to re-run resource selection on here and is a no-op, its closest stand-in.
 */

if (typeof window !== "undefined" && typeof HTMLMediaElement !== "undefined") {
  HTMLMediaElement.prototype.play = function play(): Promise<void> {
    return Promise.resolve();
  };

  HTMLMediaElement.prototype.load = function load(): void {
    // Stand-in for a browser's re-run of resource selection.
  };
}
