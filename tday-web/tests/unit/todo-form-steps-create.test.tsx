// @vitest-environment jsdom

/**
 * The Steps checklist is an edit-only affordance: it needs a persisted todo id
 * to attach to. The create sheet must not raise it, and the create payload must
 * carry no steps; the edit sheet keeps it.
 */

import { act, cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { TodoItemType } from "@/types";

const createMutateFn = vi.fn();
let todoItem: TodoItemType | undefined;

vi.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
  initReactI18next: { type: "3rdParty", init: () => {} },
}));

vi.mock("@/providers/TodoFormProvider", () => ({
  useTodoForm: () => ({
    todoItem,
    title: "Ship web fix",
    setTitle: vi.fn(),
    priority: "Low",
    setPriority: vi.fn(),
    priorityTouchedByUser: false,
    desc: "",
    setDesc: vi.fn(),
    dateRange: {
      from: new Date("2026-04-03T14:00:00.000Z"),
      to: new Date("2026-04-03T14:30:00.000Z"),
    },
    setDateRange: vi.fn(),
    listID: null,
    setListID: vi.fn(),
    rruleOptions: null,
    setRruleOptions: vi.fn(),
    derivedRepeatType: undefined,
    dateRangeChecksum: "",
    rruleChecksum: null,
  }),
}));

vi.mock("@/components/todo/hooks/useTodoFormFocusAndAutosize", () => ({
  useTodoFormFocusAndAutosize: () => ({
    titleRef: { current: null },
    textareaRef: { current: null },
  }),
}));

vi.mock("@/components/todo/hooks/useKeyboardSubmitForm", () => ({
  useKeyboardSubmitForm: vi.fn(),
}));

vi.mock("@/components/todo/hooks/useClearInput", () => ({
  useClearInput: () => vi.fn(),
}));

vi.mock("@/hooks/use-toast", () => ({
  useToast: () => ({ toast: vi.fn() }),
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
  useListMetaData: () => ({ listMetaData: {}, listMetaLoading: false }),
}));

vi.mock("@/features/floaterList/query/get-floater-list-meta", () => ({
  useFloaterListMetaData: () => ({ floaterListMetaData: {}, listMetaLoading: false }),
}));

vi.mock("@/features/completed/query/get-completedTodo", () => ({
  useCompletedTodo: () => ({ completedTodos: [], todoLoading: false }),
}));

vi.mock("@/components/todo/component/TodoForm/NLPTitleInput", () => ({
  default: () => <div data-testid="todo-title-input" />,
}));

// The real section needs a persisted todo and a query client; the form only
// decides whether to mount it, which is what these tests pin.
vi.mock("@/components/todo/component/TodoForm/TaskStepsSection", () => ({
  default: () => <div data-testid="task-steps" />,
}));

vi.mock("@/features/guide/GuideHelpLink", () => ({
  GuideHelpLink: () => <div data-testid="guide-help-link" />,
}));

import TodoForm from "@/components/todo/component/TodoForm/TodoForm";

function buildTodo(overrides: Partial<TodoItemType> = {}): TodoItemType {
  return {
    id: "todo-1",
    title: "Ship web fix",
    description: null,
    pinned: false,
    createdAt: new Date("2026-04-03T14:00:00.000Z"),
    order: 1,
    priority: "Low",
    due: new Date("2026-04-03T14:30:00.000Z"),
    rrule: null,
    timeZone: "UTC",
    userID: "user-1",
    completed: false,
    exdates: [],
    instanceDate: null,
    listID: "list-1",
    instances: [],
    ...overrides,
  } as TodoItemType;
}

/** Renders the scheduled sheet and hands back its registered submit handler. */
function renderForm() {
  let submit: (() => void) | undefined;
  render(
    <TodoForm
      displayForm
      setDisplayForm={vi.fn()}
      registerSubmit={(fn) => {
        submit = fn;
      }}
    />,
  );
  return () => submit;
}

describe("TodoForm steps", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    todoItem = undefined;
  });

  afterEach(cleanup);

  it("renders no steps section on the create form", () => {
    todoItem = undefined;
    renderForm();

    expect(screen.queryByTestId("task-steps")).toBeNull();
  });

  it("creates the scheduled task without a steps field", async () => {
    todoItem = undefined;
    const getSubmit = renderForm();

    await act(async () => {
      getSubmit()?.();
    });

    expect(createMutateFn).toHaveBeenCalledTimes(1);
    expect(createMutateFn.mock.calls[0][0]).not.toHaveProperty("steps");
  });

  it("keeps the steps section on the edit form", () => {
    todoItem = buildTodo();
    renderForm();

    expect(screen.getByTestId("task-steps")).toBeTruthy();
  });
});
