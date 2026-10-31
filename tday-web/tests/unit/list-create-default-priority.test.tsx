// @vitest-environment jsdom

import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

type ListMeta = Record<string, { name: string; defaultPriority?: string | null }>;

const createMutateFn = vi.fn();
// Read on every render, so a rerender with a new object is what a list-meta refetch looks like.
let scheduledListMeta: ListMeta = {};
// What the NLP title field reports as a typed priority ("!!", "low priority", ...).
let nlpPriority: "Lowest" | "Low" | "Medium" | "High" | null = null;

vi.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key: string) => key }),
  initReactI18next: { type: "3rdParty", init: () => {} },
}));

vi.mock("@/hooks/use-toast", () => ({
  useToast: () => ({ toast: vi.fn() }),
}));

vi.mock("@/components/todo/hooks/useTodoFormFocusAndAutosize", () => ({
  useTodoFormFocusAndAutosize: () => ({ titleRef: { current: null }, textareaRef: { current: null } }),
}));

vi.mock("@/components/todo/hooks/useKeyboardSubmitForm", () => ({
  useKeyboardSubmitForm: vi.fn(),
}));

vi.mock("@/providers/TodoMutationProvider", () => ({
  useTodoMutation: () => ({
    useEditTodo: () => ({ editTodoMutateFn: vi.fn() }),
    useEditTodoInstance: () => ({ editTodoInstanceMutateFn: vi.fn() }),
  }),
}));

vi.mock("@/features/todayTodos/query/create-todo", () => ({
  useCreateTodo: () => ({ createMutateFn, createStatus: "idle" }),
}));

vi.mock("@/features/todayTodos/query/convert-todo-to-floater", () => ({
  useConvertTodoToFloater: () => ({ convertMutateFn: vi.fn(), convertPending: false }),
}));

vi.mock("@/features/floater/query/create-floater", () => ({
  useCreateFloater: () => ({ createMutateFn: vi.fn(), createStatus: "idle" }),
}));

vi.mock("@/components/Sidebar/List/query/get-list-meta", () => ({
  useListMetaData: () => ({ listMetaData: scheduledListMeta, listMetaLoading: false }),
}));

vi.mock("@/features/floaterList/query/get-floater-list-meta", () => ({
  useFloaterListMetaData: () => ({ floaterListMetaData: {}, listMetaLoading: false }),
}));

vi.mock("@/features/guide/GuideHelpLink", () => ({
  GuideHelpLink: () => null,
}));

vi.mock("@/components/todo/component/TodoForm/TaskStepsSection", () => ({
  default: () => null,
}));

vi.mock("@/components/todo/component/TodoForm/RepeatSuggestionChip", () => ({
  default: () => null,
}));

vi.mock("@/features/completed/query/get-completedTodo", () => ({
  useCompletedTodo: () => ({ completedTodos: [], todoLoading: false }),
}));

// Stands in for the NLP title field: it seeds a title once, then reports any typed priority
// through the setter the form hands it, the same way the real field does on input.
vi.mock("@/components/todo/component/TodoForm/NLPTitleInput", async () => {
  const React = await import("react");
  function NLPTitleStub({
    setTitle,
    setPriority,
  }: {
    setTitle: (value: string) => void;
    setPriority?: (value: "Lowest" | "Low" | "Medium" | "High") => void;
  }) {
    React.useEffect(() => {
      setTitle("Ship web fix");
      // A tick later, the way typing arrives: after the form's mount-time seeding.
      const timer = setTimeout(() => {
        if (nlpPriority && setPriority) setPriority(nlpPriority);
      }, 0);
      return () => clearTimeout(timer);
      // Mount-only on purpose: this stands in for one title entry, not every keystroke.
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);
    return null;
  }
  return { default: NLPTitleStub };
});

import TodoFormContainer from "@/components/todo/component/TodoForm/TodoFormContainer";

type Harness = {
  submit: () => void;
  /** Simulates a list-meta refetch: the next render sees a new listMetaData object. */
  refetchListMeta: (meta: ListMeta) => void;
};

function renderCreateInList(listID: string | undefined): Harness {
  let submit: (() => void) | undefined;
  const tree = () => (
    <TodoFormContainer
      displayForm
      setDisplayForm={vi.fn()}
      overrideFields={listID ? { listID } : undefined}
      registerSubmit={(fn) => {
        submit = fn;
      }}
    />
  );
  const view = render(tree());
  return {
    submit: () => submit?.(),
    refetchListMeta: (meta) => {
      scheduledListMeta = meta;
      view.rerender(tree());
    },
  };
}

/** Lets the mount-time effects and the NLP stand-in's timer run. */
async function settle() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

const WORK_HIGH: ListMeta = { "list-1": { name: "Work", defaultPriority: "High" } };

describe("scheduled list create starts on the list's default priority", () => {
  beforeEach(() => {
    createMutateFn.mockReset();
    nlpPriority = null;
    scheduledListMeta = WORK_HIGH;
  });

  afterEach(() => {
    cleanup();
  });

  it("sends the list default priority when the user does not pick one", async () => {
    const form = renderCreateInList("list-1");
    await settle();
    form.submit();
    expect(createMutateFn).toHaveBeenCalledTimes(1);
    expect(createMutateFn.mock.calls[0][0]).toMatchObject({ listID: "list-1", priority: "High" });
  });

  it("keeps a priority the user hand-picks over the list default", async () => {
    const form = renderCreateInList("list-1");
    await settle();
    // Open the priority selector from its row, then pick "Lowest" (the "low" label).
    fireEvent.click(screen.getByRole("button", { name: /^priority,/ }));
    fireEvent.click(screen.getByText("low"));
    await settle();
    form.refetchListMeta({ "list-1": { name: "Work", defaultPriority: "High" } });
    form.submit();
    expect(createMutateFn.mock.calls[0][0]).toMatchObject({ listID: "list-1", priority: "Lowest" });
  });

  it("keeps a priority typed into the title when list meta refetches afterwards", async () => {
    nlpPriority = "Low";
    const form = renderCreateInList("list-1");
    await settle();
    // The title's "!!"-style priority is in; a later list-meta refetch must not overwrite it.
    form.refetchListMeta({ "list-1": { name: "Work", defaultPriority: "High" } });
    await settle();
    form.submit();
    expect(createMutateFn.mock.calls[0][0]).toMatchObject({ listID: "list-1", priority: "Low" });
  });

  it("applies the list default when list meta arrives after the form opened", async () => {
    scheduledListMeta = {};
    const form = renderCreateInList("list-1");
    await settle();
    form.refetchListMeta(WORK_HIGH);
    await settle();
    form.submit();
    expect(createMutateFn.mock.calls[0][0]).toMatchObject({ listID: "list-1", priority: "High" });
  });

  it("keeps a title-typed priority when the list meta arrives late", async () => {
    scheduledListMeta = {};
    nlpPriority = "Low";
    const form = renderCreateInList("list-1");
    await settle();
    // Typed before the list loaded, then the list's default lands: the typed one stays.
    form.refetchListMeta(WORK_HIGH);
    await settle();
    form.submit();
    expect(createMutateFn.mock.calls[0][0]).toMatchObject({ listID: "list-1", priority: "Low" });
  });

  it("does not seed from the default when the list has none", async () => {
    scheduledListMeta = { "list-1": { name: "Work", defaultPriority: null } };
    const form = renderCreateInList("list-1");
    await settle();
    form.submit();
    expect(createMutateFn.mock.calls[0][0]).toMatchObject({ listID: "list-1", priority: "Low" });
  });

  describe("after a create clears the form", () => {
    // The sheet stays mounted in these tests, so the priority row shows what the next task
    // would start with. Labels come from priorityLabelKey: Low→"normal", High→"urgent".
    const priorityRowLabel = () => screen.getByRole("button", { name: /^priority,/ }).getAttribute("aria-label");

    it("resets to Low when there is no list", async () => {
      const form = renderCreateInList(undefined);
      await settle();
      fireEvent.click(screen.getByRole("button", { name: /^priority,/ }));
      fireEvent.click(screen.getByText("urgent"));
      await settle();
      form.submit();
      await settle();
      expect(priorityRowLabel()).toContain("normal");
    });

    it("resets to the list's default High after a create", async () => {
      const form = renderCreateInList("list-1");
      await settle();
      form.submit();
      await settle();
      expect(priorityRowLabel()).toContain("urgent");
    });

    it("resets to the list's default after the user hand-picked a different priority", async () => {
      const form = renderCreateInList("list-1");
      await settle();
      fireEvent.click(screen.getByRole("button", { name: /^priority,/ }));
      fireEvent.click(screen.getByText("low"));
      await settle();
      form.submit();
      await settle();
      expect(priorityRowLabel()).toContain("urgent");
    });
  });
});
