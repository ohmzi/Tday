// @vitest-environment jsdom

import { renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const toastMock = vi.fn();

vi.mock("@/hooks/use-toast", () => ({
  useToast: () => ({
    toast: (...args: unknown[]) => toastMock(...args),
  }),
}));

vi.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

import { useUndoableDelete } from "@/hooks/use-undoable-delete";

type CapturedToast = {
  description: string;
  // label is icon-based now (a ReactElement carrying its accessible name via
  // aria-label on the wrapper) — see use-undoable-delete.tsx.
  action: { label: { props: { "aria-label"?: string } }; onClick: () => void };
  onAutoClose: () => void;
  onDismiss: () => void;
};

function toastAt(index: number): CapturedToast {
  return toastMock.mock.calls[index]?.[0] as CapturedToast;
}

describe("useUndoableDelete", () => {
  beforeEach(() => {
    toastMock.mockClear();
  });

  it("commits exactly once even when both close callbacks fire", () => {
    const commit = vi.fn();
    const undo = vi.fn();
    const { result } = renderHook(() => useUndoableDelete());

    result.current({ message: "Task deleted", commit, undo });

    const toast = toastAt(0);
    expect(toast.description).toBe("Task deleted");
    // Icon-based action now — the accessible name still reads "undo" (the
    // mocked t() returns the key), carried on the wrapper's aria-label.
    expect(toast.action.label.props["aria-label"]).toBe("undo");

    // Sonner can fire both onAutoClose and onDismiss for one toast.
    toast.onAutoClose();
    toast.onDismiss();

    expect(commit).toHaveBeenCalledTimes(1);
    expect(undo).not.toHaveBeenCalled();
  });

  it("never commits after undo, and undo runs only once", () => {
    const commit = vi.fn();
    const undo = vi.fn();
    const { result } = renderHook(() => useUndoableDelete());

    result.current({ message: "Task deleted", commit, undo });

    const toast = toastAt(0);
    toast.action.onClick();
    toast.action.onClick();
    toast.onDismiss();
    toast.onAutoClose();

    expect(undo).toHaveBeenCalledTimes(1);
    expect(commit).not.toHaveBeenCalled();
  });

  it("ignores undo after the commit already ran", () => {
    const commit = vi.fn();
    const undo = vi.fn();
    const { result } = renderHook(() => useUndoableDelete());

    result.current({ message: "Task deleted", commit, undo });

    const toast = toastAt(0);
    toast.onAutoClose();
    toast.action.onClick();

    expect(commit).toHaveBeenCalledTimes(1);
    expect(undo).not.toHaveBeenCalled();
  });

  it("gives rapid successive deletes independent commit timers", () => {
    const commitA = vi.fn();
    const undoA = vi.fn();
    const commitB = vi.fn();
    const undoB = vi.fn();
    const { result } = renderHook(() => useUndoableDelete());

    result.current({ message: "first", commit: commitA, undo: undoA });
    result.current({ message: "second", commit: commitB, undo: undoB });

    const first = toastAt(0);
    const second = toastAt(1);

    // Undo the first delete while the second auto-closes.
    first.action.onClick();
    second.onAutoClose();
    first.onAutoClose();

    expect(undoA).toHaveBeenCalledTimes(1);
    expect(commitA).not.toHaveBeenCalled();
    expect(commitB).toHaveBeenCalledTimes(1);
    expect(undoB).not.toHaveBeenCalled();
  });
});
