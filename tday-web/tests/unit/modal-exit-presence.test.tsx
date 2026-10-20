// @vitest-environment jsdom

/**
 * The modal's exit, proved by the DOM rather than by the class list.
 *
 * `ModalOverlay` used to be `if (!isOpen) return null` above a `createPortal`. The portal was
 * therefore torn out of the document on the same frame the flag flipped, which means no exit
 * animation declared anywhere on that subtree could ever have run — the node was gone before the
 * first frame of it. Eight call sites blinked out, and five of them repeated the same guard one
 * level up, so the defect survived being "fixed" in the component alone.
 *
 * A class-list assertion cannot catch that: the classes were right, the node was absent. So what
 * these tests assert is presence — that the card is still in `document.body` after close, that it
 * is marked `data-state="closed"` while it is there, and that it leaves exactly when the exit it
 * declares is over.
 */

import { useState } from "react";
import { act, cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  MODAL_EXIT_MS,
  Modal,
  ModalContent,
  ModalOverlay,
  useModalPresence,
} from "@/components/ui/Modal";

function Dialog({ open }: { open: boolean }) {
  return (
    <Modal open={open} onOpenChange={() => {}}>
      <ModalOverlay>
        <ModalContent>
          <p>Delete this task?</p>
        </ModalContent>
      </ModalOverlay>
    </Modal>
  );
}

const card = () => screen.queryByText("Delete this task?")?.closest("[data-state]") ?? null;
const scrim = () => document.querySelector<HTMLElement>(".fixed.inset-0[data-state]");

describe("modal exit", () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it("keeps the portal in the document past the frame it is closed", async () => {
    const { rerender } = render(<Dialog open />);
    expect(card()).not.toBeNull();
    expect(card()?.getAttribute("data-state")).toBe("open");

    rerender(<Dialog open={false} />);

    // The assertion this whole file exists for: the card is still rendered on the frame after
    // close. Under the old `if (!isOpen) return null` it was already gone here.
    expect(card()).not.toBeNull();
    expect(card()?.getAttribute("data-state")).toBe("closed");
    expect(scrim()?.getAttribute("data-state")).toBe("closed");
  });

  it("declares an exit on both the scrim and the card while it is leaving", () => {
    const { rerender } = render(<Dialog open />);
    rerender(<Dialog open={false} />);

    // Lingering in the DOM is only half of it — something has to animate during those frames.
    expect(scrim()?.className).toContain("data-[state=closed]:animate-out");
    expect(card()?.className).toContain("data-[state=closed]:animate-out");
  });

  it("leaves once the exit it declares is over, and not before", async () => {
    const { rerender } = render(<Dialog open />);
    rerender(<Dialog open={false} />);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(MODAL_EXIT_MS - 20);
    });
    expect(card()).not.toBeNull();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(20);
    });
    expect(card()).toBeNull();
    expect(document.body.querySelector("[data-state]")).toBeNull();
  });

  it("reopening mid-exit goes straight back to entering", async () => {
    const { rerender } = render(<Dialog open />);
    rerender(<Dialog open={false} />);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(MODAL_EXIT_MS / 2);
    });
    rerender(<Dialog open />);

    // `data-state` reads the open flag, never the presence value, so a modal caught mid-exit is
    // entering again rather than stuck marked closed with an exit painted on it.
    expect(card()?.getAttribute("data-state")).toBe("open");

    await act(async () => {
      await vi.advanceTimersByTimeAsync(MODAL_EXIT_MS);
    });
    expect(card()).not.toBeNull();
  });

  it("skips the wait under prefers-reduced-motion", () => {
    const original = window.matchMedia;
    window.matchMedia = ((query: string) => ({
      matches: query.includes("prefers-reduced-motion"),
      media: query,
      addEventListener: () => {
        /* the hook reads `matches` once and never subscribes */
      },
      removeEventListener: () => {
        /* see above */
      },
    })) as unknown as typeof window.matchMedia;

    try {
      const { rerender } = render(<Dialog open />);
      rerender(<Dialog open={false} />);
      // Nothing is animating, so lingering would be a stall rather than a courtesy.
      expect(card()).toBeNull();
    } finally {
      window.matchMedia = original;
    }
  });
});

/**
 * The call sites. Five dialogs wrapped the whole modal in their own `if (!open) return null`, and
 * three were mounted as `{open && <Dialog open={open} />}` — either of which takes the subtree
 * away above `ModalOverlay`, where nothing inside the component can help. They now gate on the
 * same presence value the overlay does, which is what this covers.
 */
describe("a call site that owns the decision to render the modal at all", () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  function CallSite() {
    const [open, setOpen] = useState(true);
    const present = useModalPresence(open);

    return (
      <>
        <button type="button" onClick={() => setOpen(false)}>
          close
        </button>
        {present ? (
          <Modal open={open} onOpenChange={setOpen}>
            <ModalOverlay>
              <ModalContent>
                <p>Delete this task?</p>
              </ModalContent>
            </ModalOverlay>
          </Modal>
        ) : null}
      </>
    );
  }

  it("holds its own subtree for the exit instead of dropping it on the flag", async () => {
    render(<CallSite />);
    expect(card()).not.toBeNull();

    act(() => {
      screen.getByText("close").click();
    });
    expect(card()).not.toBeNull();
    expect(card()?.getAttribute("data-state")).toBe("closed");

    await act(async () => {
      await vi.advanceTimersByTimeAsync(MODAL_EXIT_MS);
    });
    expect(card()).toBeNull();
  });
});
