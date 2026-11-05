// @vitest-environment jsdom

import type { ReactNode } from "react";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const floater = (id: string, title: string, listID: string | null) => ({
  id,
  title,
  description: null,
  priority: "Low",
  pinned: false,
  completed: false,
  order: 0,
  listID,
  createdAt: new Date("2026-01-01T00:00:00.000Z"),
  updatedAt: new Date("2026-01-01T00:00:00.000Z"),
});

const state = vi.hoisted(() => ({
  floaters: [] as unknown[],
  emptyStateArgs: null as null | { pendingRowCount: number },
}));

vi.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key: string) => key }),
  initReactI18next: { type: "3rdParty", init: () => {} },
}));
vi.mock("@/lib/navigation", () => ({
  Link: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  useRouter: () => ({ push: vi.fn() }),
}));
vi.mock("@/features/floater/query/get-floater", () => ({
  useFloater: () => ({ floaters: state.floaters, floaterLoading: false }),
}));
vi.mock("@/features/floaterList/query/get-floater-list-meta", () => ({
  useFloaterListMetaData: () => ({
    floaterListMetaData: {
      hidden: { name: "Someday", showOnMainScreen: false, todoCount: 1 },
      shown: { name: "Errands", showOnMainScreen: true, todoCount: 1 },
    },
  }),
}));
vi.mock("@/features/completed/query/get-completedFloater", () => ({
  useCompletedFloater: () => ({ completedFloaters: [] }),
}));
vi.mock("@/features/floater/lib/useFloaterEmptyState", () => ({
  useFloaterEmptyState: (args: { pendingRowCount: number }) => {
    state.emptyStateArgs = args;
    return { showEmpty: args.pendingRowCount === 0, celebrate: false, sceneLeavingOnCancel: false };
  },
}));
vi.mock("@/hooks/useSkeletonCrossfade", () => ({
  useSkeletonCrossfade: () => ({ showSkeleton: false, skeletonClassName: "" }),
}));
vi.mock("@/lib/todo/focusHandoff", () => ({ useFocusParamHandoff: () => undefined }));
vi.mock("@/components/app/ScreenWatermark", () => ({ default: () => null }));
vi.mock("@/components/app/EmptyState", () => ({
  default: ({ title }: { title: string }) => <div>{`scene:${title}`}</div>,
}));
vi.mock("@/components/app/EmptyStateSlot", () => ({
  default: ({ children }: { children: ReactNode }) => <>{children}</>,
}));
vi.mock("@/features/floaterList/component/FloaterListFormSheet", () => ({ default: () => null }));
vi.mock("@/features/floater/component/FloaterGroup", () => ({
  default: ({ floaters }: { floaters: { id: string; title: string }[] }) => (
    <div data-testid="feed">
      {floaters.map((row) => (
        <div key={row.id}>{row.title}</div>
      ))}
    </div>
  ),
}));
vi.mock("@/components/app/RootFeedHeroHeader", () => ({
  default: ({
    onSearchQueryChange,
    results,
  }: {
    onSearchQueryChange: (query: string) => void;
    results?: ReactNode;
  }) => (
    <div>
      <input aria-label="search" onChange={(event) => onSearchQueryChange(event.target.value)} />
      <div data-testid="results">{results}</div>
    </div>
  ),
}));

import NativeFloaterTaskHomeDashboard from "@/features/floater/component/NativeFloaterTaskHomeDashboard";

function renderFeed() {
  return render(
    <MemoryRouter>
      <NativeFloaterTaskHomeDashboard />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  state.emptyStateArgs = null;
});

afterEach(cleanup);

describe("the Floater main screen with a hidden list", () => {
  it("leaves the hidden list's tasks out of the feed", () => {
    state.floaters = [
      floater("1", "Tucked away", "hidden"),
      floater("2", "Buy milk", "shown"),
      floater("3", "Loose idea", null),
    ];
    renderFeed();

    const feed = screen.getByTestId("feed");
    expect(feed.textContent).toContain("Buy milk");
    expect(feed.textContent).toContain("Loose idea");
    expect(feed.textContent).not.toContain("Tucked away");
  });

  it("still finds the hidden list's tasks in search", () => {
    state.floaters = [floater("1", "Tucked away", "hidden")];
    renderFeed();

    fireEvent.change(screen.getByLabelText("search"), { target: { value: "tucked" } });

    expect(screen.getByTestId("results").textContent).toContain("Tucked away");
  });

  it("keeps a search from putting hidden tasks in the feed", () => {
    state.floaters = [
      floater("1", "Tucked away", "hidden"),
      floater("2", "Tucked in the open", "shown"),
    ];
    renderFeed();

    fireEvent.change(screen.getByLabelText("search"), { target: { value: "tucked" } });

    expect(screen.getByTestId("feed").textContent).not.toContain("Tucked away");
  });

  it("shows no rows and no scene when only hidden tasks remain", () => {
    state.floaters = [floater("1", "Tucked away", "hidden")];
    renderFeed();

    expect(screen.queryByTestId("feed")).toBeNull();
    expect(screen.queryByText("scene:floaterEmpty")).toBeNull();
    expect(state.emptyStateArgs?.pendingRowCount).toBe(1);
  });

  it("keeps the hidden list's task in its tile count", () => {
    state.floaters = [floater("1", "Tucked away", "hidden")];
    renderFeed();

    expect(screen.getByText("Someday").closest("a,button,div")?.parentElement?.textContent).toContain("1");
  });
});
