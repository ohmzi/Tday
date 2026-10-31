// @vitest-environment jsdom

import { cleanup, fireEvent, render } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const createMutateFn = vi.fn();
let floaterListMeta: Record<string, { name: string; defaultPriority?: string | null }> = {};

vi.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key: string) => key }),
  initReactI18next: { type: "3rdParty", init: () => {} },
}));

vi.mock("@/hooks/use-toast", () => ({
  useToast: () => ({ toast: vi.fn() }),
}));

vi.mock("@/features/floater/query/create-floater", () => ({
  useCreateFloater: () => ({ createMutateFn, createStatus: "idle" }),
}));

vi.mock("@/features/floater/query/update-floater", () => ({
  useEditFloater: () => ({ editTodoMutateFn: vi.fn(), editTodoStatus: "idle" }),
}));

vi.mock("@/features/floaterList/query/get-floater-list-meta", () => ({
  useFloaterListMetaData: () => ({ floaterListMetaData: floaterListMeta, floaterListMetaLoading: false }),
}));

vi.mock("@/features/floaterList/component/FloaterListDot", () => ({
  default: () => null,
}));

vi.mock("@/components/todo/component/NotesField/NotesField", () => ({
  default: () => null,
}));

import FloaterFormSheet from "@/features/floater/component/FloaterFormSheet";

function renderCreateInFloaterList() {
  return render(
    <FloaterFormSheet
      open
      onOpenChange={vi.fn()}
      overrideFields={{ listID: "floater-list-1" }}
    />,
  );
}

/** Types a title and presses the sheet's save action. */
function saveTitled(view: ReturnType<typeof render>, title: string) {
  fireEvent.change(view.getByPlaceholderText("floaterTitlePlaceholder"), {
    target: { value: title },
  });
  fireEvent.click(view.getByRole("button", { name: "save" }));
}

describe("floater list create starts on the list's default priority", () => {
  beforeEach(() => {
    createMutateFn.mockReset();
    floaterListMeta = {
      "floater-list-1": { name: "Errands", defaultPriority: "High" },
    };
  });

  afterEach(() => {
    cleanup();
  });

  it("sends the list default priority when the user does not pick one", async () => {
    const view = renderCreateInFloaterList();
    await new Promise((resolve) => setTimeout(resolve, 0));
    saveTitled(view, "Buy stamps");
    expect(createMutateFn).toHaveBeenCalledTimes(1);
    expect(createMutateFn.mock.calls[0][0]).toMatchObject({
      listID: "floater-list-1",
      priority: "High",
    });
  });

  it("keeps a priority the user hand-picks over the list default", async () => {
    const view = renderCreateInFloaterList();
    await new Promise((resolve) => setTimeout(resolve, 0));
    // Open the priority selector from its row, then pick "Lowest" (the "low" label).
    fireEvent.click(view.getByRole("button", { name: /^priority,/ }));
    fireEvent.click(view.getByText("low"));
    saveTitled(view, "Buy stamps");
    expect(createMutateFn.mock.calls[0][0]).toMatchObject({
      listID: "floater-list-1",
      priority: "Lowest",
    });
  });
});
