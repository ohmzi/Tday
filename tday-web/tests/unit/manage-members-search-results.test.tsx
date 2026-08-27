// @vitest-environment jsdom

import type { ReactNode } from "react";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

/**
 * The share sheet used to drop anyone already on the list out of its search results and then
 * report "No users found" — so looking up a member who was already shared with looked exactly
 * like the account not existing. It now shows them as a disabled row instead.
 */

const OWNER = { userId: "user-owner", username: "owner@tday.test", name: "Owner", role: "OWNER" };
const MEMBER = { userId: "user-mu", username: "mu@tday.test", name: "Mu", role: "VIEWER" };
const STRANGER = { id: "user-new", username: "new@tday.test", name: "Newcomer" };

let searchResults: { id: string; username: string; name: string | null }[] = [];

vi.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key: string) => key }),
  initReactI18next: { type: "3rdParty", init: () => {} },
}));

vi.mock("@/components/ui/AppBottomSheet", () => ({
  default: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/lib/haptics", () => ({ hapticTick: vi.fn() }));

vi.mock("@/features/list/query/share-members", () => ({
  useListMembers: () => ({
    members: { owner: OWNER, members: [MEMBER] },
    membersLoading: false,
  }),
  useAddListMember: () => ({ addMemberMutateFn: vi.fn(), addMemberPending: false }),
  useUpdateListMemberRole: () => ({ updateRoleMutateFn: vi.fn() }),
  useRemoveListMember: () => ({ removeMemberMutateFn: vi.fn() }),
  useLeaveList: () => ({ leaveListMutateFn: vi.fn(), leaveListPending: false }),
}));

vi.mock("@/features/user/query/search-users", () => ({
  useSearchUsers: () => ({ users: searchResults, searchPending: false }),
}));

import ManageMembersSheet from "@/features/list/component/ManageMembersSheet";

function renderSheet() {
  return render(
    <ManageMembersSheet
      open
      onOpenChange={vi.fn()}
      listId="list-1"
      listType="list"
      listName="Groceries"
      myRole="OWNER"
    />,
  );
}

/** The sheet debounces the query by 250ms before it renders any result rows. */
async function typeSearch(container: HTMLElement, value: string) {
  const input = container.querySelector("input");
  if (!input) throw new Error("search input not rendered");
  const { fireEvent } = await import("@testing-library/react");
  fireEvent.change(input, { target: { value } });
}

describe("ManageMembersSheet search results", () => {
  beforeEach(() => {
    searchResults = [];
  });

  // Not configured globally, and screen queries the whole document.
  afterEach(cleanup);

  it("shows an existing member as already added instead of hiding them", async () => {
    searchResults = [{ id: MEMBER.userId, username: MEMBER.username, name: MEMBER.name }];
    const { container } = renderSheet();

    await typeSearch(container, "mu@tday.test");

    await waitFor(() => {
      expect(screen.getByText("alreadyAMember")).toBeTruthy();
    });
    expect(screen.queryByText("noUsersFound")).toBeNull();
  });

  it("still offers an add button for someone who is not on the list", async () => {
    searchResults = [STRANGER];
    const { container } = renderSheet();

    await typeSearch(container, "new@tday.test");

    // "addMember" is also the section heading, so this looks for the button specifically.
    await waitFor(() => {
      const addButtons = [...container.querySelectorAll("button")].filter((button) =>
        button.textContent?.includes("addMember"),
      );
      expect(addButtons.length).toBe(1);
    });
    expect(screen.queryByText("alreadyAMember")).toBeNull();
  });

  it("reports no users found only when the search really returned nothing", async () => {
    searchResults = [];
    const { container } = renderSheet();

    await typeSearch(container, "nobody@tday.test");

    await waitFor(() => {
      expect(screen.getByText("noUsersFound")).toBeTruthy();
    });
  });
});
