// @vitest-environment jsdom

import React from "react";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// Enter-to-submit only fires on desktop pointers; the tests flip this to cover
// the touch fallback (Enter dismisses the keyboard instead).
let desktopPointer = true;

const createFloaterMutateFn = vi.fn();
const editFloaterMutateFn = vi.fn();

vi.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key: string) => key }),
  initReactI18next: { type: "3rdParty", init: () => {} },
}));

vi.mock("@/components/ui/AppBottomSheet", () => ({
  default: ({
    children,
    onConfirm,
    confirmDisabled,
  }: {
    children: React.ReactNode;
    onConfirm?: () => void;
    confirmDisabled?: boolean;
  }) => (
    <div>
      <button type="button" disabled={confirmDisabled} onClick={onConfirm}>
        confirm
      </button>
      {children}
    </div>
  ),
}));

vi.mock("@/features/floater/query/create-floater", () => ({
  useCreateFloater: () => ({
    createMutateFn: createFloaterMutateFn,
    createStatus: "idle",
  }),
}));

vi.mock("@/features/floater/query/update-floater", () => ({
  useEditFloater: () => ({
    editTodoMutateFn: editFloaterMutateFn,
    editTodoStatus: "idle",
  }),
}));

vi.mock("@/features/floaterList/query/get-floater-list-meta", () => ({
  useFloaterListMetaData: () => ({ floaterListMetaData: {} }),
}));

vi.mock("@/hooks/use-toast", () => ({
  useToast: () => ({ toast: vi.fn() }),
}));

import FloaterFormSheet from "@/features/floater/component/FloaterFormSheet";

function titleField() {
  return screen.getByPlaceholderText("floaterTitlePlaceholder");
}

function notesField() {
  return screen.getByPlaceholderText("notes");
}

describe("floater sheet Enter-to-submit", () => {
  beforeEach(() => {
    desktopPointer = true;
    window.matchMedia = ((query: string) => ({
      matches: query.includes("hover: hover") ? desktopPointer : false,
      media: query,
      addEventListener: () => {},
      removeEventListener: () => {},
      addListener: () => {},
      removeListener: () => {},
      onchange: null,
      dispatchEvent: () => false,
    })) as unknown as typeof window.matchMedia;
    render(<FloaterFormSheet open onOpenChange={vi.fn()} />);
  });

  afterEach(() => {
    cleanup();
    createFloaterMutateFn.mockReset();
    editFloaterMutateFn.mockReset();
  });

  it("creates the floater when Enter is pressed in the title", () => {
    fireEvent.change(titleField(), { target: { value: "Water the plants" } });
    fireEvent.keyDown(titleField(), { key: "Enter" });

    expect(createFloaterMutateFn).toHaveBeenCalledTimes(1);
    expect(createFloaterMutateFn.mock.calls[0][0]).toMatchObject({
      title: "Water the plants",
    });
  });

  it("creates the floater when Enter is pressed in the notes field", () => {
    fireEvent.change(titleField(), { target: { value: "Water the plants" } });
    fireEvent.change(notesField(), { target: { value: "the big one" } });
    fireEvent.keyDown(notesField(), { key: "Enter" });

    expect(createFloaterMutateFn.mock.calls[0][0]).toMatchObject({
      title: "Water the plants",
      description: "the big one",
    });
  });

  it("leaves Shift+Enter to insert a newline in the title", () => {
    fireEvent.change(titleField(), { target: { value: "Water the plants" } });
    fireEvent.keyDown(titleField(), { key: "Enter", shiftKey: true });

    expect(createFloaterMutateFn).not.toHaveBeenCalled();
  });

  it("does nothing when the title is empty", () => {
    fireEvent.keyDown(titleField(), { key: "Enter" });

    expect(createFloaterMutateFn).not.toHaveBeenCalled();
  });

  it("dismisses the keyboard instead of submitting on touch devices", () => {
    desktopPointer = false;
    fireEvent.change(titleField(), { target: { value: "Water the plants" } });
    titleField().focus();
    expect(document.activeElement).toBe(titleField());

    fireEvent.keyDown(titleField(), { key: "Enter" });

    expect(createFloaterMutateFn).not.toHaveBeenCalled();
    expect(document.activeElement).not.toBe(titleField());
  });
});
