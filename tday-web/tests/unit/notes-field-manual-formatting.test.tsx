// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key: string) => key }),
  initReactI18next: { type: "3rdParty", init: () => {} },
}));

import NotesField from "@/components/todo/component/NotesField/NotesField";
import { RICH_NOTES_MARKER } from "@/lib/richNotes";

function notesField() {
  return screen.getByTestId("notes-editor");
}

function pasteIntoNotes(text: string) {
  const dataTransfer = {
    getData: (type: string) => (type === "text/plain" ? text : ""),
    types: ["text/plain"],
  };
  fireEvent.paste(notesField(), { clipboardData: dataTransfer });
}

// The Format menu's buttons call the exact same Tiptap commands
// (toggleBold/toggleItalic/...) that these built-in keyboard shortcuts run —
// StarterKit's Bold/Italic/Strike extensions and the separately-registered
// Underline extension all register them — so exercising a mark through the
// shortcut is an equally direct test of the same "toggle a mark on the
// current selection" path the buttons use, without needing jsdom to compute
// real text-selection screen coordinates (which it can't — see NotesField's
// menuPosition effect, tested only for absence of a crash, not exact pixels
// jsdom can't produce anyway).
// prosemirror-keymap resolves "Mod-" against navigator.platform; jsdom's
// default platform doesn't match its Mac/iOS regex, so "Mod" means Ctrl
// here — passing metaKey too would build a non-matching "Ctrl-Meta-…" combo.
function fireShortcut(key: string, options: KeyboardEventInit = {}) {
  fireEvent.keyDown(notesField(), { key, ctrlKey: true, ...options });
}

describe("NotesField manual formatting", () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it("selects all then bolds via the same command the Format menu's Bold button calls", () => {
    const handleChange = vi.fn();
    render(<NotesField value="" onChange={handleChange} placeholder="Notes" />);

    pasteIntoNotes("hello world");
    fireShortcut("a"); // select all
    fireShortcut("b"); // Mod-b: toggleBold — identical command FormatButton's onClick runs

    const lastCall = handleChange.mock.calls.at(-1)?.[0] as string;
    expect(lastCall.startsWith(RICH_NOTES_MARKER)).toBe(true);
    // Tiptap's Bold extension emits <strong> (both <b> and <strong> are in
    // richNotes.ts's allow-list, so either sanitizes through — this is just
    // which one Tiptap itself happens to produce).
    expect(lastCall).toContain("<strong>hello world</strong>");
  });

  it("applies italic, underline, and strikethrough via their own shortcuts (same commands their buttons call)", () => {
    const handleChange = vi.fn();
    render(<NotesField value="" onChange={handleChange} placeholder="Notes" />);

    pasteIntoNotes("emphasis");
    fireShortcut("a");
    fireShortcut("i"); // Mod-i: toggleItalic
    expect(handleChange.mock.calls.at(-1)?.[0]).toContain("<em>emphasis</em>");

    fireShortcut("i"); // back off, so the next mark isn't stacked on top
    fireShortcut("u"); // Mod-u: toggleUnderline
    expect(handleChange.mock.calls.at(-1)?.[0]).toContain("<u>emphasis</u>");

    fireShortcut("u");
    fireShortcut("s", { shiftKey: true }); // Mod-Shift-s: toggleStrike
    expect(handleChange.mock.calls.at(-1)?.[0]).toContain("<s>emphasis</s>");
  });

  it("toggling the same mark twice removes it and returns to a plain (unmarked) string", () => {
    const handleChange = vi.fn();
    render(<NotesField value="" onChange={handleChange} placeholder="Notes" />);

    pasteIntoNotes("plain again");
    fireShortcut("a");
    fireShortcut("b");
    fireShortcut("b");

    const lastCall = handleChange.mock.calls.at(-1)?.[0] as string;
    expect(lastCall.startsWith(RICH_NOTES_MARKER)).toBe(false);
    expect(lastCall).toBe("plain again");
  });

  it("keeps the format bar usable with no selection, so a mark can be armed for what gets typed next", () => {
    const handleChange = vi.fn();
    render(<NotesField value="" onChange={handleChange} placeholder="Notes" />);

    // .focus() sets activeElement (which ProseMirror checks); the
    // fireEvent is what flushes the resulting React state update inside act.
    notesField().focus();
    fireEvent.focus(notesField());

    // Regression guard for two linked bugs reported from real use: the
    // buttons used to carry disabled={selection.empty}, and `disabled` also
    // applies pointer-events-none — so a mousedown with no selection fell
    // through to the wrapper instead of hitting the button's own
    // preventDefault, the editor blurred, and the whole bar unmounted
    // mid-tap. Enabled buttons are also what make "put the cursor down, tap
    // Bold, then type" possible at all.
    const bold = screen.getByLabelText("bold") as HTMLButtonElement;
    expect(bold.disabled).toBe(false);
    expect((screen.getByLabelText("bulletedList") as HTMLButtonElement).disabled).toBe(false);

    fireEvent.mouseDown(bold);
    fireEvent.click(bold);

    // Still mounted after being tapped with an empty selection, and the mark
    // is now armed (ProseMirror storedMarks) for whatever gets typed next.
    expect(screen.getByLabelText("bold").getAttribute("aria-pressed")).toBe("true");
  });

  it("continues a list on Enter instead of ending it", () => {
    const handleChange = vi.fn();
    render(<NotesField value="" onChange={handleChange} placeholder="Notes" />);

    // No select-all here: paste leaves the cursor collapsed at the end of
    // "milk", and splitListItem (what Enter runs) needs a collapsed cursor.
    pasteIntoNotes("milk");
    fireShortcut("8", { shiftKey: true }); // Mod-Shift-8: toggleBulletList
    expect(handleChange.mock.calls.at(-1)?.[0]).toContain("<ul>");

    // NotesField used to intercept plain Enter to submit the form, which
    // pre-empted StarterKit's ListItem Enter binding (splitListItem) — so a
    // list silently ended at the first newline. Now Enter reaches ProseMirror.
    fireEvent.keyDown(notesField(), { key: "Enter", code: "Enter", keyCode: 13 });

    const html = handleChange.mock.calls.at(-1)?.[0] as string;
    expect(html.match(/<li>/g)?.length).toBe(2);
  });

  it("does not crash when the field is focused with no selection (menu-position effect stays inert)", () => {
    const handleChange = vi.fn();
    render(<NotesField value="" onChange={handleChange} placeholder="Notes" />);

    notesField().focus();
    fireEvent.blur(notesField());

    // No assertion beyond "didn't throw" — this exercises the
    // focus/selectionUpdate/blur listeners this component registers on
    // mount without a real text selection for jsdom to compute a rect for.
  });
});
