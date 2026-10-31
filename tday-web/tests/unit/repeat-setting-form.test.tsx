// @vitest-environment jsdom

/**
 * The Repeat row must reach the request body: picking a preset in the sheet has to
 * end up as an RRULE string on the create POST, and on the edit PATCH with the
 * checksum that tells the backend the rule changed.
 */

import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { TodoItemType } from "@/types";

const createMutateFn = vi.fn();
const editTodoMutateFn = vi.fn();

let todoItem: TodoItemType | undefined;

vi.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
  initReactI18next: { type: "3rdParty", init: () => {} },
}));

vi.mock("@/hooks/use-toast", () => ({
  useToast: () => ({ toast: vi.fn() }),
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

vi.mock("@/providers/TodoMutationProvider", () => ({
  useTodoMutation: () => ({
    useEditTodo: () => ({ editTodoMutateFn }),
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

// Captures the props the form hands the title field, so the instance-mode test can
// assert the repeat setter is withheld rather than only that a row is missing.
let lastNlpProps: Record<string, unknown> = {};
vi.mock("@/components/todo/component/TodoForm/NLPTitleInput", () => ({
  default: (props: Record<string, unknown>) => {
    lastNlpProps = props;
    return <div data-testid="todo-title-input" />;
  },
}));

vi.mock("@/components/todo/component/TodoForm/RepeatSuggestionChip", () => ({
  default: () => <div data-testid="repeat-suggestion-chip" />,
}));

vi.mock("@/features/completed/query/get-completedTodo", () => ({
  useCompletedTodo: () => ({ completedTodos: [], todoLoading: false }),
}));

vi.mock("@/components/todo/component/TodoForm/TaskStepsSection", () => ({
  default: () => <div data-testid="task-steps" />,
}));

vi.mock("@/features/guide/GuideHelpLink", () => ({
  GuideHelpLink: () => <div data-testid="guide-help-link" />,
}));

import TodoFormContainer from "@/components/todo/component/TodoForm/TodoFormContainer";

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
    listID: null,
    instances: [],
    ...overrides,
  } as TodoItemType;
}

function renderForm() {
  let submit: (() => void) | undefined;
  render(
    <TodoFormContainer
      displayForm
      setDisplayForm={vi.fn()}
      todo={todoItem}
      overrideFields={{ title: "Water plants" }}
      registerSubmit={(fn) => {
        submit = fn;
      }}
    />,
  );
  return () => submit;
}

/** Opens the Repeat sheet and taps the preset whose label key is `labelKey`. */
function pickRepeat(labelKey: string) {
  fireEvent.click(screen.getByRole("button", { name: /^repeat,/ }));
  fireEvent.click(screen.getByText(labelKey));
}

describe("TodoForm repeat setting", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    todoItem = undefined;
  });

  afterEach(cleanup);

  it("create: picking Daily sends an RRULE with the daily freq", async () => {
    const getSubmit = renderForm();
    pickRepeat("everyDay");

    await act(async () => {
      getSubmit()?.();
    });

    expect(createMutateFn).toHaveBeenCalledTimes(1);
    expect(createMutateFn.mock.calls[0][0].rrule).toBe("RRULE:FREQ=DAILY");
  });

  it("create: picking Weekdays sends a weekly rule restricted to Mon-Fri", async () => {
    const getSubmit = renderForm();
    pickRepeat("weekdaysOnly");

    await act(async () => {
      getSubmit()?.();
    });

    expect(createMutateFn.mock.calls[0][0].rrule).toBe(
      "RRULE:FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR",
    );
  });

  it("create: picking a repeat marks it user-touched so the title phrase cannot re-apply", () => {
    renderForm();
    expect(lastNlpProps.repeatTouchedByUser).toBe(false);

    pickRepeat("everyDay");

    expect(lastNlpProps.repeatTouchedByUser).toBe(true);
  });

  it("create: no repeat picked sends a null rrule", async () => {
    const getSubmit = renderForm();

    await act(async () => {
      getSubmit()?.();
    });

    expect(createMutateFn.mock.calls[0][0].rrule).toBeNull();
  });

  it("edit: a one-off task gaining a repeat sends the new rule against a null checksum", async () => {
    todoItem = buildTodo({ rrule: null });
    const getSubmit = renderForm();
    pickRepeat("everyWeek");

    await act(async () => {
      getSubmit()?.();
    });

    expect(editTodoMutateFn).toHaveBeenCalledTimes(1);
    const params = editTodoMutateFn.mock.calls[0][0];
    expect(params.rrule).toBe("RRULE:FREQ=WEEKLY");
    expect(params.rruleChecksum).toBeNull();
  });

  it("edit: changing an existing repeat sends the new rule against the old checksum", async () => {
    todoItem = buildTodo({ rrule: "RRULE:FREQ=DAILY" });
    const getSubmit = renderForm();
    pickRepeat("everyWeek");

    await act(async () => {
      getSubmit()?.();
    });

    const params = editTodoMutateFn.mock.calls[0][0];
    expect(params.rrule).toBe("RRULE:FREQ=WEEKLY");
    expect(params.rruleChecksum).toBe("RRULE:FREQ=DAILY");
  });

  it("edit of a single occurrence renders the form but offers no repeat control", () => {
    todoItem = buildTodo({ rrule: "RRULE:FREQ=DAILY", instanceDate: new Date("2026-04-04T14:00:00.000Z") });
    render(
      <TodoFormContainer
        displayForm
        editInstanceOnly
        setDisplayForm={vi.fn()}
        todo={todoItem}
        registerSubmit={vi.fn()}
      />,
    );

    // The form really rendered: the title field is present before the absence checks mean anything.
    expect(screen.getByTestId("todo-title-input")).toBeTruthy();
    expect(screen.queryByRole("button", { name: /^repeat,/ })).toBeNull();
    expect(screen.queryByTestId("repeat-suggestion-chip")).toBeNull();
    // The title field may not set a repeat either.
    expect(lastNlpProps.setRruleOptions).toBeUndefined();
  });

  it("outside instance mode the title field and suggestion chip get the repeat setter", () => {
    todoItem = buildTodo();
    renderForm();

    expect(screen.getByTestId("todo-title-input")).toBeTruthy();
    expect(screen.getByTestId("repeat-suggestion-chip")).toBeTruthy();
    expect(typeof lastNlpProps.setRruleOptions).toBe("function");
  });

  it("edit: clearing a repeat sends a null rrule against the old checksum", async () => {
    todoItem = buildTodo({ rrule: "RRULE:FREQ=DAILY" });
    const getSubmit = renderForm();
    pickRepeat("noRepeat");

    await act(async () => {
      getSubmit()?.();
    });

    const params = editTodoMutateFn.mock.calls[0][0];
    expect(params.rrule).toBeNull();
    expect(params.rruleChecksum).toBe("RRULE:FREQ=DAILY");
  });
});
