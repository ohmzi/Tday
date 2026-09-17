// @vitest-environment jsdom

/**
 * A REUSABLE LIST'S SWITCH MUST READ THE STORED FLAG, AND TURNING IT OFF MUST REACH THE SERVER
 *
 * `FloaterListFormSheet` seeds its "Reusable list" switch from the `list` prop and
 * compares the flip against that same prop to decide whether anything changed. The
 * container builds that prop by hand (`editableList`) and had left `reusable` out of
 * it, so the switch's whole world was a field that did not exist:
 *
 *   - it seeded to `false` on every open — the sheet showed OFF for a list the server
 *     had stored as reusable, while the Reset button beside it (gated on the real
 *     `listMeta.reusable`) kept drawing;
 *   - turning it back off satisfied the unchanged-check against that same `false` and
 *     was read as "nothing to send", so the PATCH never fired and `reusable` stayed
 *     `true` on the server. Reset could not be retired.
 *
 * The reset endpoint is not the culprit — it un-completes floaters and never touches
 * `reusable` — so nothing here mocks a reset; the desync is entirely the dropped field.
 * This file drives the container rather than the sheet because the sheet was always
 * correct given a prop that carried the value. Asserting the toggle's `aria-checked`
 * and the PATCH body together pins both halves: the seed AND the persistence.
 */

import type { ReactNode } from "react";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const patchMock = vi.fn();

/** The list as the metadata query hands it to the container — reusable, owned. */
const listMeta = {
  name: "Packing",
  color: "TEAL" as const,
  iconKey: "inbox",
  reusable: true,
  myRole: "OWNER" as const,
  todoCount: 0,
};

vi.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key: string) => key }),
  initReactI18next: { type: "3rdParty", init: () => {} },
}));

vi.mock("@/lib/api-client", () => ({
  api: {
    PATCH: (...args: unknown[]) => patchMock(...args),
    POST: vi.fn(),
    DELETE: vi.fn(),
    GET: vi.fn(),
  },
}));

vi.mock("@/hooks/use-toast", () => ({ useToast: () => ({ toast: vi.fn() }) }));
vi.mock("@/hooks/use-undoable-delete", () => ({ useUndoableDelete: () => vi.fn() }));
vi.mock("@/hooks/use-share-list", () => ({ useShareListAsText: () => vi.fn() }));
vi.mock("@/hooks/useAppMode", () => ({ useIsLocalMode: () => false }));
vi.mock("@/lib/navigation", () => ({
  usePathname: () => "/app/floater-list/list-1",
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock("@/features/floaterList/query/get-floater-list-meta", () => ({
  useFloaterListMetaData: () => ({ floaterListMetaData: { "list-1": listMeta } }),
}));
vi.mock("@/features/floaterList/query/get-floater-list", () => ({
  useFloaterList: () => ({
    floaterList: null,
    floaterListTodos: [],
    floaterListLoading: false,
  }),
}));
vi.mock("@/features/floaterList/query/reset-floater-list", () => ({
  useResetFloaterList: () => ({ mutate: vi.fn(), isPending: false }),
}));
vi.mock("@/features/floater/lib/useFloaterEmptyState", () => ({
  useFloaterEmptyState: () => ({ showEmpty: false, celebrate: false, sceneLeavingOnCancel: false }),
}));
vi.mock("@/hooks/useSkeletonCrossfade", () => ({
  useSkeletonCrossfade: () => ({ showSkeleton: false, skeletonClassName: "" }),
}));

// Everything the screen draws around the two controls under test is stubbed to
// nothing; only `MobileSearchHeader` is left holding anything, because the edit
// button — the way into the sheet — lives in its trailing cluster.
vi.mock("@/components/app/NativePageHeader", () => ({
  default: () => null,
  useNativePageBarSlots: () => ({}),
}));
vi.mock("@/components/app/ScreenWatermark", () => ({ default: () => null }));
vi.mock("@/components/ui/MobileSearchHeader", () => ({
  default: ({ trailingAction }: { trailingAction?: ReactNode }) => <div>{trailingAction}</div>,
}));
vi.mock("@/features/summary/SummaryButton", () => ({ default: () => null }));
vi.mock("@/components/app/EmptyState", () => ({ default: () => null }));
vi.mock("@/components/app/EmptyStateSlot", () => ({
  default: ({ children }: { children: ReactNode }) => <>{children}</>,
}));
vi.mock("@/features/list/component/ManageMembersSheet", () => ({ default: () => null }));

// The sheet chrome is a portal behind an animation; the confirm button is the only
// part of it this needs, and the mock renders the body regardless of `open`.
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

import FloaterListContainer from "@/features/floaterList/component/FloaterListContainer";

function renderContainer() {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false }, queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <FloaterListContainer id="list-1" />
    </QueryClientProvider>,
  );
}

/** Opens the owner's edit sheet, which is what seeds the form from the list. */
const openEditSheet = () =>
  fireEvent.click(screen.getByLabelText("listSettings Packing"));
const toggle = () => screen.getByRole("switch");
const confirm = () => fireEvent.click(screen.getByText("confirm"));

beforeEach(() => {
  patchMock.mockReset();
  patchMock.mockResolvedValue(undefined);
});

afterEach(cleanup);

describe("the floater list edit sheet's Reusable switch", () => {
  it("seeds ON from the stored flag, not from a field the prop forgot", () => {
    renderContainer();
    openEditSheet();

    // Without the container carrying `reusable` into `editableList`, the sheet seeds
    // from `undefined ?? false` and this reads "false" — the reported symptom.
    expect(toggle().getAttribute("aria-checked")).toBe("true");
  });

  it("persists the flip back OFF, so the Reset affordance can be retired", async () => {
    renderContainer();
    openEditSheet();

    expect(toggle().getAttribute("aria-checked")).toBe("true");
    fireEvent.click(toggle());
    expect(toggle().getAttribute("aria-checked")).toBe("false");

    confirm();

    // The unchanged-check compares against the list prop; with `reusable` dropped it
    // saw `false === false`, treated the off-flip as no change and never PATCHed.
    await waitFor(() => expect(patchMock).toHaveBeenCalled());
    const body = JSON.parse(patchMock.mock.calls[0][0].body);
    expect(body).toMatchObject({ id: "list-1", reusable: false });
  });
});
