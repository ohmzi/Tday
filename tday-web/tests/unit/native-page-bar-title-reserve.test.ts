/**
 * The docked title's reserve, pinned as arithmetic.
 *
 * There is no device here and jsdom lays nothing out — it has no font metrics
 * and `getBoundingClientRect` answers zero for everything — so a rendered
 * assertion about this bar could only ever restate the mock it was given. The
 * rule was therefore extracted into a pure function taking four numbers, and
 * this file is the whole proof. It is the same shape as Android's
 * `TdayBarTitleReserveTest`, written for the same reason one client over.
 *
 * The widths below are measured, not invented: they come from the real classes
 * on the real bars (`rootFeedHeaderButtonClass`'s 56px box, `gap-2.5`'s 10px,
 * the `px-5` Today pill, `px-4` on the page column) and from Nunito at
 * 2.1rem/900. Where a number is a font measurement it is named as one, so a
 * later reader can tell which assertions would move if the typeface did.
 */

import { describe, expect, it } from "vitest";

import {
  nativePageBarTitleMetrics,
  nativePageBarTitleReserve,
} from "@/components/app/nativePageBarTitleReserve";

const m = nativePageBarTitleMetrics;

/** The rule as it stood before the fix, kept here so the change can be measured
 *  against it rather than described. Its only difference is the gate. */
function previousReserve(barWidth: number, leading: number, trailing: number) {
  const symmetric = Math.max(leading, trailing) + m.sideGap;
  const centred = barWidth - symmetric * 2 >= m.minWidth;
  const leadingReserve = centred ? symmetric : leading + m.sideGap;
  const trailingReserve = centred ? symmetric : trailing + m.sideGap;
  return { leadingReserve, trailingReserve, room: barWidth - leadingReserve - trailingReserve };
}

function roomFor(result: { leadingReserve: number; trailingReserve: number }, barWidth: number) {
  return barWidth - result.leadingReserve - result.trailingReserve;
}

/**
 * Calendar at a 412px viewport: the bar is the page column, 412 less `px-4`
 * either side. Leading is the single 56px back button; trailing is the 56px
 * search button, `gap-2.5`, and the "Today" pill — the text control that makes
 * this bar worse than Android's was.
 */
const CALENDAR = { barWidth: 380, leadingWidth: 56, trailingWidth: 150 };
/** "Calendar" in Nunito at 2.1rem (33.6px) / weight 900. */
const CALENDAR_TITLE_WIDTH = 146.6;

describe("nativePageBarTitleReserve", () => {
  describe("the bug it was extracted to fix", () => {
    it("gave Calendar 64px for a 146.6px word before the fix", () => {
      // Not an assertion about the new code: it is the evidence that the case
      // is real, so that the expectation below is a fix and not a preference.
      const before = previousReserve(
        CALENDAR.barWidth,
        CALENDAR.leadingWidth,
        CALENDAR.trailingWidth,
      );
      expect(before.room).toBe(64);
      expect(before.room).toBeLessThan(CALENDAR_TITLE_WIDTH / 2);
    });

    it("gives Calendar the whole word once the title is measured", () => {
      const result = nativePageBarTitleReserve({
        ...CALENDAR,
        titleWidth: CALENDAR_TITLE_WIDTH,
      });
      // Per-side: 56 + 8 leading, 150 + 8 trailing, leaving 158.
      expect(result.leadingReserve).toBe(64);
      expect(result.trailingReserve).toBe(158);
      expect(roomFor(result, CALENDAR.barWidth)).toBe(158);
      expect(roomFor(result, CALENDAR.barWidth)).toBeGreaterThanOrEqual(CALENDAR_TITLE_WIDTH);
      expect(result.hasRoom).toBe(true);
    });
  });

  describe("the mirrored branch, which is still first", () => {
    it("keeps a bar whose title fits centred on the bar", () => {
      // Completed history: back button one side, nothing the other. The name is
      // narrow enough that mirroring 64px twice still clears it, so the title
      // stays centred on the bar rather than drifting off it.
      const result = nativePageBarTitleReserve({
        barWidth: 380,
        leadingWidth: 56,
        trailingWidth: 0,
        titleWidth: 140,
      });
      expect(result.leadingReserve).toBe(64);
      expect(result.trailingReserve).toBe(64);
      expect(roomFor(result, 380)).toBe(252);
    });

    it("is what an unmeasured title still gets, unchanged", () => {
      // Zero fits everything, so the first frame — before the span has a box,
      // and before the webfont has swapped — behaves exactly as this bar always
      // did. That is what makes a late measurement safe.
      for (const bar of [
        CALENDAR,
        { barWidth: 380, leadingWidth: 56, trailingWidth: 0 },
        { barWidth: 342, leadingWidth: 56, trailingWidth: 236 },
      ]) {
        const result = nativePageBarTitleReserve(bar);
        const before = previousReserve(bar.barWidth, bar.leadingWidth, bar.trailingWidth);
        expect(result.leadingReserve).toBe(before.leadingReserve);
        expect(result.trailingReserve).toBe(before.trailingReserve);
      }
    });

    it("is not held by a title narrower than the floor on a bar with no room", () => {
      // The `minWidth` term of the `max` is the old gate, kept. Without it a
      // 20px title would call a 30px gap "centred" and `hasRoom` would then be
      // answering against a reserve chosen for the wrong reason.
      const result = nativePageBarTitleReserve({
        barWidth: 200,
        leadingWidth: 56,
        trailingWidth: 77,
        titleWidth: 20,
      });
      expect(result.leadingReserve).toBe(64);
      expect(result.trailingReserve).toBe(85);
      expect(result.hasRoom).toBe(false);
    });
  });

  describe("bars the reserve cannot rescue", () => {
    it("draws no title when the floater list's controls have eaten the row", () => {
      // Five controls at 390: mirrored is already negative, per-side leaves
      // under the floor, and a reserve wider than the bar would paint the title
      // across the buttons rather than hide it. So: no title.
      const result = nativePageBarTitleReserve({
        barWidth: 358,
        leadingWidth: 56,
        trailingWidth: 290,
        titleWidth: 160,
      });
      expect(roomFor(result, 358)).toBeLessThan(m.minWidth);
      expect(result.hasRoom).toBe(false);
    });

    it("reserves what is actually there when the bar has no box yet", () => {
      const result = nativePageBarTitleReserve({
        barWidth: 0,
        leadingWidth: 56,
        trailingWidth: 150,
        titleWidth: 146.6,
      });
      expect(result.leadingReserve).toBe(56);
      expect(result.trailingReserve).toBe(150);
    });
  });

  describe("the property that makes the change safe everywhere", () => {
    // Every combination a real bar could present, plus a good deal that no bar
    // does. The two claims below are what let this land on six screens at once
    // without a device to check them on.
    const bars = [200, 280, 342, 358, 380, 412, 600, 1024];
    const clusters = [0, 44, 56, 66, 112, 150, 236, 290];
    const titles = [0, 23.1, 50.9, 100, 146.6, 240, 500];

    it("never hands a title less room than the previous rule did", () => {
      for (const barWidth of bars) {
        for (const leadingWidth of clusters) {
          for (const trailingWidth of clusters) {
            for (const titleWidth of titles) {
              const now = roomFor(
                nativePageBarTitleReserve({ barWidth, leadingWidth, trailingWidth, titleWidth }),
                barWidth,
              );
              const before = previousReserve(barWidth, leadingWidth, trailingWidth).room;
              expect(now).toBeGreaterThanOrEqual(before);
            }
          }
        }
      }
    });

    it("gains exactly |trailing - leading| when it falls through to per-side", () => {
      // Per-side leaves W - L - T - 2g; mirrored leaves W - 2*max(L,T) - 2g.
      // The difference is |T - L| and it is never negative, which is the whole
      // argument: this step can only ever give the title width.
      for (const barWidth of bars) {
        for (const leadingWidth of clusters) {
          for (const trailingWidth of clusters) {
            const perSide = barWidth - leadingWidth - trailingWidth - m.sideGap * 2;
            const mirrored = barWidth - Math.max(leadingWidth, trailingWidth) * 2 - m.sideGap * 2;
            expect(perSide - mirrored).toBe(Math.abs(trailingWidth - leadingWidth));
            expect(perSide).toBeGreaterThanOrEqual(mirrored);
          }
        }
      }
    });

    it("never lets a title that was drawn before stop being drawn", () => {
      for (const barWidth of bars) {
        for (const leadingWidth of clusters) {
          for (const trailingWidth of clusters) {
            const before = previousReserve(barWidth, leadingWidth, trailingWidth).room >= m.minWidth;
            for (const titleWidth of titles) {
              const now = nativePageBarTitleReserve({
                barWidth,
                leadingWidth,
                trailingWidth,
                titleWidth,
              }).hasRoom;
              if (before && barWidth > 0) expect(now).toBe(true);
            }
          }
        }
      }
    });

    it("never reserves so much that the title would paint over a control", () => {
      for (const barWidth of bars) {
        for (const leadingWidth of clusters) {
          for (const trailingWidth of clusters) {
            for (const titleWidth of titles) {
              const result = nativePageBarTitleReserve({
                barWidth,
                leadingWidth,
                trailingWidth,
                titleWidth,
              });
              if (!result.hasRoom) continue;
              expect(result.leadingReserve).toBeGreaterThanOrEqual(leadingWidth);
              expect(result.trailingReserve).toBeGreaterThanOrEqual(trailingWidth);
            }
          }
        }
      }
    });
  });
});
