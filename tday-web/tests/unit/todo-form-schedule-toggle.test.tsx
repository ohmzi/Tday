// @vitest-environment jsdom

import React from "react";
import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { TodoItemType } from "@/types";

const convertMutateFn = vi.fn();
const editTodoMutateFn = vi.fn();
const editTodoInstanceMutateFn = vi.fn();
const createFloaterMutateFn = vi.fn();
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
    useEditTodo: () => ({ editTodoMutateFn }),
    useEditTodoInstance: () => ({ editTodoInstanceMutateFn }),
  }),
}));

vi.mock("@/features/todayTodos/query/create-todo", () => ({
  useCreateTodo: () => ({ createMutateFn, createStatus: "idle" }),
}));

// The conversion under test: the sheet must reach for it, not for the todo PATCH,
// when the Schedule toggle is turned off on an existing task.
vi.mock("@/features/todayTodos/query/convert-todo-to-floater", () => ({
  useConvertTodoToFloater: () => ({ convertMutateFn, convertPending: false }),
}));

vi.mock("@/features/floater/query/create-floater", () => ({
  useCreateFloater: () => ({ createMutateFn: createFloaterMutateFn, createStatus: "idle" }),
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

// Steps need a persisted todo and a query client; not what this test is about.
vi.mock("@/components/todo/component/TodoForm/TaskStepsSection", () => ({
  default: () => <div data-testid="task-steps" />,
}));

// GuideHelpLink renders a router Link; this test renders the form without one.
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

/** Renders the sheet and hands back its registered submit handler. */
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

async function submitForm(getSubmit: () => (() => void) | undefined) {
  await act(async () => {
    getSubmit()?.();
  });
}

describe("TodoForm schedule toggle", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    todoItem = undefined;
  });

  // This suite has no globals, so RTL does not register its own auto-cleanup:
  // without this the second render's switch joins the first one's in the document.
  afterEach(cleanup);

  it("converts an existing task to a floater when Schedule is turned off and saved", async () => {
    todoItem = buildTodo();
    const getSubmit = renderForm();

    // The toggle is offered while editing — that is the control the user reached for.
    fireEvent.click(screen.getByRole("switch"));
    await submitForm(getSubmit);

    expect(convertMutateFn).toHaveBeenCalledTimes(1);
    expect(convertMutateFn).toHaveBeenCalledWith(
      expect.objectContaining({
        todo: expect.objectContaining({ id: "todo-1" }),
        title: "Ship web fix",
        priority: "Low",
      }),
    );
    // The conversion is not a PATCH: there is no kind field for one to carry.
    expect(editTodoMutateFn).not.toHaveBeenCalled();
  });

  it("still PATCHes when Schedule is left on", async () => {
    todoItem = buildTodo();
    const getSubmit = renderForm();

    await submitForm(getSubmit);

    expect(editTodoMutateFn).toHaveBeenCalledTimes(1);
    expect(convertMutateFn).not.toHaveBeenCalled();
  });

  it("does not offer the conversion for a recurring task, which cannot be demoted", () => {
    todoItem = buildTodo({ rrule: "RRULE:FREQ=DAILY;INTERVAL=1" });
    renderForm();

    expect(screen.queryByRole("switch")).toBeNull();
  });

  it("keeps creating a floater for a new task with Schedule off", async () => {
    todoItem = undefined;
    const getSubmit = renderForm();

    fireEvent.click(screen.getByRole("switch"));
    await submitForm(getSubmit);

    expect(createFloaterMutateFn).toHaveBeenCalledTimes(1);
    expect(createMutateFn).not.toHaveBeenCalled();
    expect(convertMutateFn).not.toHaveBeenCalled();
  });
});
