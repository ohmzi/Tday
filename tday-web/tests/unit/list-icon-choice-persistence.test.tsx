// @vitest-environment jsdom

import React, { type ReactNode } from "react";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

/**
 * WHETHER THE PICKER HELD A CHOICE OR A PREVIEW, AND WHAT THAT PUTS ON THE WIRE
 *
 * The inference is only ever allowed to fill an icon nobody chose, so the database
 * needs a value that means "nobody chose" — and until this change there wasn't one.
 * Every sheet on every client seeded its picker with the default and then posted it,
 * so `iconKey` came out `"inbox"` on every list anyone had ever made, and an inference
 * keyed on null would have fired approximately never while one keyed on `"inbox"`
 * would have overridden the people who actually wanted an inbox.
 *
 * That makes the request body the whole fix, which is why this file drives the sheet
 * rather than testing a helper: the bug was never in a function, it was in a component
 * sending a field it had no business sending. `list-icon-inference.test.ts` covers what
 * the resolver does with the value once it is stored; this covers whether the value
 * ever gets stored.
 *
 * The edit sheet is worth more attention than the create one, and had the nastier
 * version of the bug: it seeded from the list, so opening it to RENAME a list would
 * post `"inbox"` back for an icon the user had never touched, from a screen that says
 * nothing about icons. A guess that a rename destroys is not a guess anyone can rely
 * on, so the rename case below is the one this file exists for.
 */

const createMutateAsync = vi.fn();
const patchMock = vi.fn();

vi.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key: string) => key }),
  initReactI18next: { type: "3rdParty", init: () => {} },
}));

// The sheet chrome is not what is under test; the confirm button is the only part of
// it this needs, and the real one lives behind a portal and an animation.
vi.mock("@/components/ui/AppBottomSheet", () => ({
  default: ({
    children,
    onConfirm,
    confirmDisabled,
  }: {
    children: ReactNode;
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

vi.mock("@/lib/api-client", () => ({
  api: {
    PATCH: (...args: unknown[]) => patchMock(...args),
  },
}));

vi.mock("@/hooks/use-toast", () => ({
  useToast: () => ({ toast: vi.fn() }),
}));

vi.mock("@/hooks/use-undoable-list-delete", () => ({
  useUndoableListDelete: () => vi.fn(),
}));

vi.mock("@/components/Sidebar/List/query/create-list", () => ({
  useCreateList: () => ({
    createMutateAsync: (...args: unknown[]) => createMutateAsync(...args),
    createLoading: false,
  }),
}));

import ListFormSheet from "@/components/Sidebar/List/ListFormSheet";

function renderSheet(props: React.ComponentProps<typeof ListFormSheet>) {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false }, queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <ListFormSheet {...props} />
    </QueryClientProvider>,
  );
}

const typeName = (value: string) =>
  fireEvent.change(screen.getByPlaceholderText("listName"), { target: { value } });
const confirm = () => fireEvent.click(screen.getByText("confirm"));
const tapIcon = (label: string) =>
  fireEvent.click(screen.getByLabelText(`Use ${label} icon`));

/** What actually went over the wire on a PATCH. */
const patchedBody = () => JSON.parse(patchMock.mock.calls[0][0].body);

beforeEach(() => {
  createMutateAsync.mockReset();
  createMutateAsync.mockResolvedValue({ id: "list-1", name: "Groceries" });
  patchMock.mockReset();
  patchMock.mockResolvedValue(undefined);
});

afterEach(cleanup);

describe("creating a list", () => {
  it("posts no icon key while the picker is only previewing one", async () => {
    renderSheet({ open: true, onOpenChange: vi.fn() });
    typeName("Groceries");
    confirm();

    await waitFor(() => expect(createMutateAsync).toHaveBeenCalled());
    const params = createMutateAsync.mock.calls[0][0];
    expect(params.name).toBe("Groceries");
    // Not "inbox", and not "cart" either — the sheet is SHOWING the cart it inferred
    // while the user types, and showing it is not the same as the user picking it.
    // Sending either one would spend the only value that can mean "not chosen".
    expect(params.iconKey).toBeUndefined();
  });

  it("posts the icon the moment one is picked", () => {
    renderSheet({ open: true, onOpenChange: vi.fn() });
    typeName("Groceries");
    tapIcon("Work");
    confirm();

    expect(createMutateAsync).toHaveBeenCalledWith(
      expect.objectContaining({ name: "Groceries", iconKey: "work" }),
    );
  });

  it("records a deliberate tap on the default, which the inference must never undo", () => {
    // The case that makes "stored inbox beats an inferred cart" mean anything: without
    // this, a user who wants the plain glyph on a list called Groceries has no way to
    // say so, because their choice is indistinguishable from never having chosen.
    renderSheet({ open: true, onOpenChange: vi.fn() });
    typeName("Groceries");
    tapIcon("Inbox");
    confirm();

    expect(createMutateAsync).toHaveBeenCalledWith(
      expect.objectContaining({ iconKey: "inbox" }),
    );
  });
});

describe("editing a list", () => {
  const unset = { id: "list-1", name: "Groceries", color: "BLUE" as const, iconKey: null };

  it("renames without writing an icon over one that was never chosen", async () => {
    renderSheet({ open: true, onOpenChange: vi.fn(), list: unset });
    typeName("Groceries and Errands");
    confirm();

    await waitFor(() => expect(patchMock).toHaveBeenCalled());
    const body = patchedBody();
    expect(body.name).toBe("Groceries and Errands");
    // Absent, not null: `ListService.update` writes the column only for a value that
    // arrives, so an omitted field is the only way to say "leave the icon alone".
    expect("iconKey" in body).toBe(false);
  });

  it("keeps a chosen icon out of harm's way on a rename too", async () => {
    renderSheet({
      open: true,
      onOpenChange: vi.fn(),
      list: { ...unset, iconKey: "work" },
    });
    typeName("Work Stuff");
    confirm();

    await waitFor(() => expect(patchMock).toHaveBeenCalled());
    expect("iconKey" in patchedBody()).toBe(false);
  });

  it("saves a tap on the default even though the list already draws one", async () => {
    // The trap in the "nothing changed" check: an unset icon RESOLVES to inbox, so a
    // comparison against the resolved value would read this tap as no change at all and
    // drop it — leaving the list still unset and still liable to be guessed at.
    renderSheet({ open: true, onOpenChange: vi.fn(), list: unset });
    tapIcon("Inbox");
    confirm();

    await waitFor(() => expect(patchMock).toHaveBeenCalled());
    expect(patchedBody().iconKey).toBe("inbox");
  });

  it("closes without a request when nothing was changed at all", async () => {
    const onOpenChange = vi.fn();
    renderSheet({ open: true, onOpenChange, list: unset });
    confirm();

    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false));
    expect(patchMock).not.toHaveBeenCalled();
  });
});
