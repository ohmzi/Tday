// @vitest-environment jsdom

/**
 * The seams inside `CalendarClient` itself.
 *
 * Two of this screen's motion fixes are each covered at both ends and nowhere in
 * the middle: `useNavigationRefusal` is tested as a hook and `CalendarModeCard`
 * is tested with `refusedBack` handed in by a test, while `dragOverlayDropAnimation`
 * is tested as a pure config function. Between them sits the only code that
 * makes either one a feature — the `if (animateToDate(…)) return;
 * refuseNavigation();` in `navigatePeriod`, the `refusedBack={refusedBack}` it
 * feeds, and the `dropAnimation={…}` on the overlay — and cutting any of those
 * three lines left the whole suite green. That is the gap this file is for, and
 * it matters more than usual here because `CalendarClient.tsx` is the file six
 * units of this programme all edit.
 *
 * So this renders the screen rather than a piece of it, which the neighbouring
 * files deliberately do not: they are about what a wrapper draws and what a
 * clock does, and a whole calendar between the test and the assertion would only
 * make those harder to read. This one is about the wiring, so the wiring has to
 * be present.
 *
 * The refusal is driven by the arrow key rather than by a swipe. Both ways of
 * asking arrive at the same `navigatePeriod` — that is the reason the answer
 * lives there rather than in the gesture handler — and the keyboard is the one
 * jsdom can deliver without inventing pointer geometry it does not implement.
 */

import { act, cleanup, fireEvent, render } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { DURATION_MS } from "@/lib/motion";
import { dragOverlayDropAnimation } from "@/lib/dragLiftMotion";
import { installReducedMotion } from "../setup/reduced-motion";

vi.mock("@/lib/api-client", () => ({
  api: {
    GET: vi.fn(async () => ({ todos: [], lists: [] })),
    PATCH: vi.fn(),
    DELETE: vi.fn(),
    POST: vi.fn(),
  },
}));
vi.mock("@/hooks/use-toast", () => ({ useToast: () => ({ toast: vi.fn() }) }));
vi.mock("@/features/user/query/get-timezone", () => ({
  useUserTimezone: () => ({ timeZone: "UTC" }),
}));

/**
 * The overlay's `dropAnimation` is a prop, and a prop is not readable from the
 * DOM: dnd-kit runs the drop through `Element.animate`, which jsdom does not
 * implement, and the settled tree is identical whatever the config says. So
 * `DragOverlay` is replaced by something that records what it was handed. The
 * rest of the library is the real thing — the sensors, the context and the
 * droppable cells are what the screen builds around.
 */
const dropAnimations: unknown[] = [];
vi.mock("@dnd-kit/core", async () => {
  const actual = await vi.importActual<typeof import("@dnd-kit/core")>("@dnd-kit/core");
  return {
    ...actual,
    DragOverlay: ({ dropAnimation, children }: { dropAnimation: unknown; children?: unknown }) => {
      dropAnimations.push(dropAnimation);
      return <div data-testid="drag-overlay">{children as never}</div>;
    },
  };
});

import CalendarClient from "@/features/calendar/component/CalendarClient";

const originalMatchMedia = window.matchMedia;

beforeEach(() => {
  dropAnimations.length = 0;
  vi.useFakeTimers({ shouldAdvanceTime: true });
  installReducedMotion(false);
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
  window.matchMedia = originalMatchMedia;
});

function renderCalendar() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  return render(
    <MemoryRouter initialEntries={["/en/app/calendar"]}>
      <QueryClientProvider client={queryClient}>
        <CalendarClient />
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

/** Every navigation gesture funnels through `navigatePeriod`; this is the cheapest one. */
function pressArrow(key: "ArrowLeft" | "ArrowRight") {
  act(() => {
    fireEvent.keyDown(document.body, { key });
  });
}

const refused = () => document.querySelector(".cal-native-page-refused");

describe("CalendarClient wires its own motion up", () => {
  it("answers a back navigation it refuses, and takes the answer down again", () => {
    // The calendar opens on today, so the month behind it is behind the floor
    // and `animateToDate` declines — which is the branch `refuseNavigation()`
    // sits in. Nothing else on this screen raises that class.
    renderCalendar();
    expect(refused()).toBeNull();

    pressArrow("ArrowLeft");
    expect(refused()).not.toBeNull();

    act(() => {
      vi.advanceTimersByTime(DURATION_MS.quick);
    });
    expect(refused()).toBeNull();
  });

  it("stays silent when the page actually turns", () => {
    // The other half of the same line, and the reason `animateToDate` returns a
    // boolean at all: a refusal played on every navigation would be the calendar
    // resisting the pages it is perfectly happy to turn.
    renderCalendar();

    pressArrow("ArrowRight");

    expect(refused()).toBeNull();
  });

  it("hands the drag overlay a landing rather than a cut", () => {
    // `dropAnimation={null}` is not "no animation" but "no landing": dnd-kit
    // removes the overlay on the frame of the release. The config's own content
    // is asserted in drag-lift-motion.test.ts; what is asserted here is
    // that this screen is the one asking for it.
    renderCalendar();

    expect(dropAnimations.length).toBeGreaterThan(0);
    expect(dropAnimations[dropAnimations.length - 1]).toEqual(dragOverlayDropAnimation(false));
    expect(dropAnimations[dropAnimations.length - 1]).not.toBeNull();
  });
});
