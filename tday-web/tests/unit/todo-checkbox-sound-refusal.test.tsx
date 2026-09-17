// @vitest-environment jsdom

/**
 * What happens when the browser refuses a cue.
 *
 * `feedback-preferences.test.tsx` asks whether the app TRIES to play — the switch's round trip,
 * the level, the clip. This file asks the other question, the one nothing asked while the bug was
 * live: what the app does when the try is refused.
 *
 * `HTMLMediaElement.play()` answers with a promise, and `TodoCheckbox` discarded it. Every refusal
 * it can report — a media resource the element could not load, a clip that would not decode, a
 * policy that would not let it start — was reported to nobody, so "the switch is on and nothing
 * pops" reached a user with no console line, no rejected promise and no breadcrumb to follow.
 *
 * The refusals here are the two shapes a real one takes. `NotSupportedError` is what a browser
 * throws when resource selection failed (the source is missing, or the response was not audio —
 * the Ktor backend answers any unknown path with the SPA shell, so a dropped clip degrades to
 * HTML with no 404 to notice). `NotAllowedError` is the autoplay policy, where the element had a
 * clip and the browser said no anyway. They lead to different answers, so they are asserted apart.
 *
 * These tests fail without the catch: a discarded rejection records nothing and reloads nothing.
 */

import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Check } from "lucide-react";

vi.mock("@/lib/observability/sentry", () => ({
  addDiagnosticBreadcrumb: vi.fn(),
}));

import { addDiagnosticBreadcrumb } from "@/lib/observability/sentry";
import TodoCheckbox from "@/components/ui/TodoCheckbox";

const breadcrumb = vi.mocked(addDiagnosticBreadcrumb);

/** The `this` of each `play()` call, in order — which element the tap asked. */
let played: HTMLAudioElement[] = [];
let rebuilt: HTMLAudioElement[] = [];
let browserPlay: typeof HTMLMediaElement.prototype.play;
let browserLoad: typeof HTMLMediaElement.prototype.load;

/** A refusal with the name a browser gives one, since that name is what the fix reports. */
function refusal(name: string, message: string): Error {
  return Object.assign(new Error(message), { name });
}

const notSupported = () =>
  refusal("NotSupportedError", "The element has no supported sources.");

function stubPlay(rejection: Error | null) {
  const play = vi.fn(function (this: HTMLAudioElement) {
    played.push(this);
    return rejection ? Promise.reject(rejection) : Promise.resolve();
  });
  HTMLMediaElement.prototype.play = play as unknown as typeof browserPlay;
  return play;
}

function stubLoad() {
  const load = vi.fn(function (this: HTMLAudioElement) {
    rebuilt.push(this);
  });
  HTMLMediaElement.prototype.load = load as unknown as typeof browserLoad;
  return load;
}

beforeEach(() => {
  window.localStorage.clear();
  played = [];
  rebuilt = [];
  breadcrumb.mockClear();
  browserPlay = HTMLMediaElement.prototype.play;
  browserLoad = HTMLMediaElement.prototype.load;
});

afterEach(() => {
  cleanup();
  HTMLMediaElement.prototype.play = browserPlay;
  HTMLMediaElement.prototype.load = browserLoad;
});

/**
 * `TodoCheckbox` builds its own players, so the constructor is the only place they can be
 * caught. The mock is a function expression on purpose: an arrow function is not constructible,
 * and `new Audio(...)` would throw.
 */
function renderCheckbox(complete: boolean) {
  const built: HTMLAudioElement[] = [];
  const browserAudio = window.Audio;
  const audioSpy = vi.spyOn(window, "Audio").mockImplementation((function (
    this: unknown,
    src?: string,
  ) {
    const element = new browserAudio(src);
    built.push(element);
    return element;
  }) as unknown as typeof Audio);
  pendingAudioSpy = audioSpy;

  render(
    <TodoCheckbox
      complete={complete}
      checked={complete}
      onChange={() => undefined}
      icon={Check}
    />,
  );
  return { checkbox: screen.getByRole("checkbox"), built };
}

let pendingAudioSpy: { mockRestore: () => void } | null = null;

afterEach(() => {
  pendingAudioSpy?.mockRestore();
  pendingAudioSpy = null;
});

/** Let the promise `play()` returned settle, as it does between the tap and the catch. */
const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

describe("a refused cue", () => {
  it("names the completing half and the browser's own reason, structurally", async () => {
    stubPlay(notSupported());
    stubLoad();

    renderCheckbox(false).checkbox.click();
    await settle();

    // `complete` is the state BEFORE the tap, so a completing tap reads false here and gets the
    // completing cue. Pinned because the wrong half would be a wrong sound, not a missing one.
    expect(breadcrumb).toHaveBeenCalledTimes(1);
    const [operation, data] = breadcrumb.mock.calls[0];
    expect(operation).toBe("sound.play_refused");
    // Which cue and what the browser called it — no title, no list name, no task id.
    expect(data).toEqual({
      cue: "complete",
      reason: "NotSupportedError",
      media_error: 0,
    });
  });

  it("names the other half on an un-completing tap", async () => {
    stubPlay(notSupported());
    stubLoad();

    renderCheckbox(true).checkbox.click();
    await settle();

    expect(breadcrumb).toHaveBeenCalledTimes(1);
    expect(breadcrumb.mock.calls[0][1]).toMatchObject({ cue: "uncomplete" });
  });

  it("reloads an element whose source never arrived, so the row is not mute for good", async () => {
    stubPlay(notSupported());
    const load = stubLoad();

    renderCheckbox(false).checkbox.click();
    await settle();

    // A media element whose resource selection failed does not retry on its own: without this
    // one failed fetch at mount would refuse every later tap on that row for its whole life.
    expect(load).toHaveBeenCalledTimes(1);
    expect(rebuilt).toEqual(played);
  });

  it("leaves the element alone when the browser refused the policy and not the clip", async () => {
    stubPlay(refusal("NotAllowedError", "play() failed because the user didn't interact."));
    const load = stubLoad();

    renderCheckbox(false).checkbox.click();
    await settle();

    // Still reported — it is a refusal either way — but deliberately not recovered, and this test
    // is where that boundary is pinned. The cue is asked for synchronously inside the tap, so a
    // real tap carries the activation the policy wants and this branch is not reached from one;
    // were it reached, reloading answers it with nothing (the element had a clip) and a retry has
    // no fresh activation to offer, so it would be refused identically. A policy refusal is the
    // report's job, not a recovery's — the reason in the breadcrumb is what tells it apart from
    // the source-load refusal the `load()` path above does cure.
    expect(breadcrumb).toHaveBeenCalledTimes(1);
    expect(breadcrumb.mock.calls[0][1]).toMatchObject({ reason: "NotAllowedError" });
    expect(load).not.toHaveBeenCalled();
  });
});

describe("a cue that plays", () => {
  it("is not reported and does not rebuild its element", async () => {
    stubPlay(null);
    const load = stubLoad();

    renderCheckbox(false).checkbox.click();
    await settle();

    // The control for the three above: if this file recorded a breadcrumb unconditionally, or
    // reloaded unconditionally, every test in it would pass while proving nothing.
    expect(breadcrumb).not.toHaveBeenCalled();
    expect(load).not.toHaveBeenCalled();
  });

  it("is still asked for once, on the completing clip, from a completing tap", async () => {
    stubPlay(null);
    stubLoad();

    renderCheckbox(false).checkbox.click();
    await settle();

    // The call itself, and the element it landed on: `play()` refused is only interesting if
    // `play()` was called with the right clip, and this is the assertion that would catch the
    // selection drifting to the un-completing file.
    expect(played).toHaveLength(1);
    expect(played[0].src.endsWith("/task-complete.wav")).toBe(true);
    expect(played[0].volume).toBe(0.5);
  });
});
