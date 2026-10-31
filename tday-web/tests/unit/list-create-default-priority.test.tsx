// @vitest-environment jsdom

import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const createMutateFn = vi.fn();
let scheduledListMeta: Record<string, { name: string; defaultPriority?: string | null }> = {};
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

function renderCreateInList(listID: string) {
  let submit: (() => void) | undefined;
  render(
    <TodoFormContainer
      displayForm
      setDisplayForm={vi.fn()}
      overrideFields={{ listID }}
      registerSubmit={(fn) => {
        submit = fn;
      }}
    />,
  );
  return () => submit?.();
}

/** Lets effects and list-meta re-renders settle before the test acts. */
async function settle() {
  await new Promise((resolve) => setTimeout(resolve, 0));
}

describe("scheduled list create starts on the list's default priority", () => {
  beforeEach(() => {
    createMutateFn.mockReset();
    nlpPriority = null;
    scheduledListMeta = {
      "list-1": { name: "Work", defaultPriority: "High" },
    };
  });

  afterEach(() => {
    cleanup();
  });

  it("sends the list default priority when the user does not pick one", async () => {
    const submit = renderCreateInList("list-1");
    await settle();
    submit();
    expect(createMutateFn).toHaveBeenCalledTimes(1);
    expect(createMutateFn.mock.calls[0][0]).toMatchObject({ listID: "list-1", priority: "High" });
  });

  it("keeps a priority the user hand-picks over the list default", async () => {
    const submit = renderCreateInList("list-1");
    await settle();
    // Open the priority selector from its row, then pick "Lowest" (the "low" label).
    fireEvent.click(screen.getByRole("button", { name: /^priority,/ }));
    fireEvent.click(screen.getByText("low"));
    await settle();
    submit();
    expect(createMutateFn.mock.calls[0][0]).toMatchObject({ listID: "list-1", priority: "Lowest" });
  });

  it("keeps a priority typed into the title over the list default", async () => {
    nlpPriority = "Low";
    const submit = renderCreateInList("list-1");
    await act(async () => {
      await settle();
      await settle();
    });
    submit();
    expect(createMutateFn.mock.calls[0][0]).toMatchObject({ listID: "list-1", priority: "Low" });
  });

  it("does not seed from the default when the list has none", async () => {
    scheduledListMeta = { "list-1": { name: "Work", defaultPriority: null } };
    const submit = renderCreateInList("list-1");
    await settle();
    submit();
    expect(createMutateFn.mock.calls[0][0]).toMatchObject({ listID: "list-1", priority: "Low" });
  });
});
