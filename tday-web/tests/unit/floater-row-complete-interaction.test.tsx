// @vitest-environment jsdom

/**
 * The Anytime row leaves the list the same way a dated one does — green check, strike, then the
 * box closing under a fade — and it is worth its own file because it gets there by different
 * machinery. `TodoItemCard` reads its phase from `taskCompletionStaging`, which outlives the row;
 * the floater still runs the sequence on its own `useState` and `useRef<number[]>`, so nothing the
 * scheduled row's tests hold applies here by inheritance.
 *
 * The collapse is also load-bearing here in a way it is not there: this row carries `min-h-[54px]`
 * on mobile so the swipe-revealed pills are not clipped, which would hold a finished task's box
 * open at full height unless the grid item is explicitly let go of.
 *
 * The sibling file is `todo-row-complete-interaction.test.tsx`, and the two are meant to stay
 * readable side by side.
 */

import type { ReactNode } from "react";
import { act, cleanup, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { FloaterItemType } from "@/types";
import {
  TASK_COMPLETION_CHECK_TO_STRIKE_MS,
  TASK_COMPLETION_STRIKE_TO_FADE_MS,
  TASK_COMPLETION_TOTAL_MS,
} from "@/lib/taskCompletionTiming";
import { installReducedMotion } from "../setup/reduced-motion";

const patchMock = vi.fn();

vi.mock("@/lib/api-client", () => ({
  api: { PATCH: (...a: unknown[]) => patchMock(...a), DELETE: vi.fn(), POST: vi.fn() },
}));
vi.mock("@/hooks/use-toast", () => ({ useToast: () => ({ toast: vi.fn() }) }));
vi.mock("@/hooks/use-todo-action-toast", () => ({
  useTodoActionToast: () => ({
    showTodoCompletedToast: vi.fn(),
    showTodoDeletedToast: vi.fn(),
  }),
}));
vi.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: "en" } }),
}));
vi.mock("@/features/floaterList/query/get-floater-list-meta", () => ({
  useFloaterListMetaData: () => ({ floaterListMetaData: {} }),
}));
vi.mock("@/features/floater/query/delete-floater", () => ({
  useDeleteFloater: () => ({ deleteMutateFn: vi.fn(), deletePending: false }),
}));
// Both are reachable only from controls this file never touches, and both drag in machinery
// (a popover, a form sheet) that has nothing to say about how a row leaves.
vi.mock("@/features/floater/component/PromoteFloaterMenu", () => ({
  PromoteFloaterMenu: () => null,
}));
vi.mock("@/features/floater/component/FloaterFormSheet", () => ({ default: () => null }));

import FloaterItemContainer from "@/features/floater/component/FloaterItemContainer";

const FLOATER = {
  id: "fl-1",
  title: "Pick a paint colour",
  description: null,
  completed: false,
  priority: "Low",
  listID: null,
} as unknown as FloaterItemType;

const OTHER: FloaterItemType = { ...FLOATER, id: "fl-2", title: "Read the manual" };

let container: HTMLElement;

function renderRow() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  queryClient.setQueryData<FloaterItemType[]>(["floater"], [FLOATER, OTHER]);

  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <MemoryRouter initialEntries={["/en/app/floater"]}>
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      </MemoryRouter>
    );
  }

  container = render(<FloaterItemContainer floater={FLOATER} />, { wrapper: Wrapper }).container;
  return queryClient;
}

/**
 * The row's own box — the element that carries the collapse. Taken as the first thing the
 * component puts in the document rather than by class, because the class string is itself one of
 * the things under assertion here and a selector built out of it could not fail.
 */
function row(): HTMLElement {
  const element = container.firstElementChild;
  if (!element) throw new Error("the row is not on screen");
  return element as HTMLElement;
}

/**
 * The grid item inside that box, found through the checkbox. A grid item's automatic minimum size
 * is its own content — and on this row `min-h-[54px]` puts a floor under that on mobile — so the
 * track can be told to run to 0fr all it likes and the box will not close until this element is
 * allowed to be smaller than the row it holds.
 */
function foreground(): HTMLElement {
  const checkbox = screen.getByRole("checkbox");
  const item = Array.from(row().children).find((child) => child.contains(checkbox));
  if (!item) throw new Error("the row's foreground is not on screen");
  return item as HTMLElement;
}

const REAL_MATCH_MEDIA = window.matchMedia;

describe("ticking an Anytime row's checkbox", () => {
  beforeEach(() => {
    patchMock.mockReset();
    patchMock.mockResolvedValue(null);
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });

  afterEach(() => {
    window.matchMedia = REAL_MATCH_MEDIA;
    vi.useRealTimers();
    cleanup();
  });

  it("holds the box open through the strike, then closes it under the fade", async () => {
    const queryClient = renderRow();
    const title = () => screen.getByText("Pick a paint colour");

    await act(async () => {
      screen.getByRole("checkbox").click();
    });

    // 1. Checked at once, box untouched. The track is the whole height animation:
    //    `gridTemplateRows` is inert on anything that is not a grid, so these two classes going
    //    missing would leave the collapse doing nothing at all.
    expect(row().className).toContain("grid-rows-[1fr]");
    expect(row().className).toMatch(/(^|\s)grid(\s|$)/);
    expect(row().style.gridTemplateRows).toBe("");
    expect(foreground().style.overflow).toBe("");
    expect(foreground().style.minHeight).toBe("");

    // 2. Struck, and still at full height — this beat is the user reading their own edit back.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(TASK_COMPLETION_CHECK_TO_STRIKE_MS + 10);
    });
    expect(title().className).toContain("task-strike");
    expect(row().style.gridTemplateRows).toBe("");
    expect(queryClient.getQueryData<FloaterItemType[]>(["floater"])).toHaveLength(2);

    // 3. Fading AND closing, in one transition, while the row still holds its key in the cache —
    //    what is shrinking is the space it takes, so the rows below travel into it.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(TASK_COMPLETION_STRIKE_TO_FADE_MS);
    });
    expect(row().style.opacity).toBe("0");
    expect(row().style.gridTemplateRows).toBe("0fr");
    expect(row().style.transition).toContain("grid-template-rows");
    expect(foreground().style.overflow).toBe("hidden");
    expect(foreground().style.minHeight).toBe("0px");
    expect(queryClient.getQueryData<FloaterItemType[]>(["floater"])).toHaveLength(2);

    // 4. Gone once the box is shut.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(TASK_COMPLETION_TOTAL_MS);
    });
    expect(queryClient.getQueryData<FloaterItemType[]>(["floater"])?.map((f) => f.id)).toEqual([
      "fl-2",
    ]);
  });

  /**
   * The same cut the scheduled row makes, for the same reason (docs/motion.md, fifth idiom rule):
   * the last leg is there to let the collapse finish, and with reduce-motion on there is no
   * collapse — so the wait goes with it rather than being left in front of the destination. This
   * row arms its own timers, so it has to be told separately.
   */
  it("prunes as soon as the frame is finished under reduced motion", async () => {
    installReducedMotion(true);
    const queryClient = renderRow();

    await act(async () => {
      screen.getByRole("checkbox").click();
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(
        TASK_COMPLETION_CHECK_TO_STRIKE_MS + TASK_COMPLETION_STRIKE_TO_FADE_MS + 10,
      );
    });

    expect(queryClient.getQueryData<FloaterItemType[]>(["floater"])?.map((f) => f.id)).toEqual([
      "fl-2",
    ]);
  });

  /**
   * And when the preference arrives mid-sequence the row still has to draw the finished frame —
   * an empty box and no ink, arrived at rather than travelled to. This is why the row subscribes
   * to the preference instead of reading it once at mount.
   */
  it("drops the transition when the preference flips mid-sequence", async () => {
    const reduced = installReducedMotion(false);
    const queryClient = renderRow();

    await act(async () => {
      screen.getByRole("checkbox").click();
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(
        TASK_COMPLETION_CHECK_TO_STRIKE_MS + TASK_COMPLETION_STRIKE_TO_FADE_MS + 10,
      );
    });
    expect(row().style.transition).toContain("grid-template-rows");

    await act(async () => {
      reduced.set(true);
    });

    expect(row().style.transition).toBe("");
    expect(row().style.gridTemplateRows).toBe("0fr");
    expect(row().style.opacity).toBe("0");
    expect(foreground().style.minHeight).toBe("0px");

    await act(async () => {
      await vi.advanceTimersByTimeAsync(TASK_COMPLETION_TOTAL_MS);
    });
    expect(queryClient.getQueryData<FloaterItemType[]>(["floater"])).toHaveLength(1);
  });

  it("does not re-fire if the checkbox is tapped twice", async () => {
    const queryClient = renderRow();
    const checkbox = screen.getByRole("checkbox");

    await act(async () => {
      checkbox.click();
      checkbox.click();
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(TASK_COMPLETION_TOTAL_MS + 20);
    });

    expect(queryClient.getQueryData<FloaterItemType[]>(["floater"])?.map((f) => f.id)).toEqual([
      "fl-2",
    ]);
  });
});
