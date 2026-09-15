// @vitest-environment jsdom

/**
 * The sound and haptic preferences, tested at the two places a user meets them:
 * the vibrator and the checkbox.
 *
 * Asserting that `setSoundEnabled(false)` makes `isSoundEnabled()` return false
 * would test a pair of functions against each other and prove nothing about the
 * app — the same trap `motion-parity.test.ts` writes up at length. So the reads
 * here are taken through the things that actually consume them: `hapticSuccess`,
 * which is one of eight verbs sharing a single gate, and `TodoCheckbox`, which is
 * the only surface in the app that plays a sound at all.
 *
 * jsdom implements neither `navigator.vibrate` nor `HTMLMediaElement.play`, which
 * is convenient: both are stubbed outright, so "did the app try" is observable
 * without a device.
 */

import { render, screen, cleanup } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Check } from "lucide-react";

import { hapticReveal, hapticSuccess, hapticsSupported } from "@/lib/haptics";
import {
  isHapticsEnabled,
  isSoundEnabled,
  setHapticsEnabled,
  setSoundEnabled,
} from "@/lib/feedbackPreferences";
import TodoCheckbox from "@/components/ui/TodoCheckbox";

const vibrate = vi.fn();
const play = vi.fn();

beforeEach(() => {
  window.localStorage.clear();
  vibrate.mockClear();
  play.mockClear();
  Object.defineProperty(navigator, "vibrate", {
    configurable: true,
    writable: true,
    value: vibrate,
  });
  window.HTMLMediaElement.prototype.play = play as unknown as () => Promise<void>;
});

afterEach(() => {
  cleanup();
  delete (navigator as { vibrate?: unknown }).vibrate;
});

describe("haptic preference", () => {
  it("buzzes by default, so an untouched install behaves as it always did", () => {
    expect(isHapticsEnabled()).toBe(true);
    hapticSuccess();
    expect(vibrate).toHaveBeenCalledTimes(1);
  });

  it("stays silent once turned off, and comes back when turned on", () => {
    setHapticsEnabled(false);
    hapticSuccess();
    expect(vibrate).not.toHaveBeenCalled();

    setHapticsEnabled(true);
    hapticSuccess();
    expect(vibrate).toHaveBeenCalledTimes(1);
  });

  it("reads the preference at the buzz, not at import", () => {
    // The module was imported at the top of this file, before any of these tests
    // wrote to storage. A cached flag would have frozen the answer there and this
    // is the assertion that would catch it.
    hapticSuccess();
    setHapticsEnabled(false);
    hapticSuccess();
    expect(vibrate).toHaveBeenCalledTimes(1);
  });

  it("reports no support where there is no vibrator, and asks nobody to vibrate", () => {
    delete (navigator as { vibrate?: unknown }).vibrate;
    expect(hapticsSupported()).toBe(false);
    hapticSuccess();
    expect(vibrate).not.toHaveBeenCalled();
  });

  it("covers the newest verb too, because the gate is the chokepoint and not a habit", () => {
    // `hapticReveal` is the ninth name in a file whose gate lives in one private
    // function, and the point of putting it there was that the next verb added
    // inherits it without anyone remembering to. This is that claim, asserted of
    // the verb that arrived after the claim was made — and it is the half
    // `swipe-row-reveal-haptic.test.tsx` cannot see, because that file mocks this
    // module away to ask a different question.
    setHapticsEnabled(false);
    hapticReveal();
    expect(vibrate).not.toHaveBeenCalled();

    setHapticsEnabled(true);
    hapticReveal();
    expect(vibrate).toHaveBeenCalledTimes(1);
  });
});

describe("sound preference", () => {
  function renderCheckbox(complete: boolean) {
    render(
      <TodoCheckbox
        complete={complete}
        checked={complete}
        onChange={() => undefined}
        icon={Check}
      />,
    );
    return screen.getByRole("checkbox");
  }

  it("pops by default", () => {
    renderCheckbox(false).click();
    expect(play).toHaveBeenCalledTimes(1);
  });

  it("stays silent once turned off — in both directions", () => {
    setSoundEnabled(false);

    renderCheckbox(false).click();
    expect(play).not.toHaveBeenCalled();

    cleanup();
    // The un-completing clip is the other half of the same preference: a person
    // who muted the app did not ask to be muted only while finishing things.
    renderCheckbox(true).click();
    expect(play).not.toHaveBeenCalled();
  });

  it("leaves the haptic alone, because they are two switches", () => {
    setSoundEnabled(false);
    renderCheckbox(false).click();
    expect(play).not.toHaveBeenCalled();
    expect(vibrate).toHaveBeenCalledTimes(1);
  });
});

describe("both preferences, where storage refuses", () => {
  it("defaults to on rather than to off", () => {
    // A browser with site data blocked throws on read. Falling to "off" there
    // would silence the app for someone who never asked for silence — the same
    // way round `globals.css` falls when it cannot ask about reduced motion.
    const getItem = vi
      .spyOn(window.localStorage, "getItem")
      .mockImplementation(() => {
        throw new Error("blocked");
      });

    expect(isSoundEnabled()).toBe(true);
    expect(isHapticsEnabled()).toBe(true);

    getItem.mockRestore();
  });
});
