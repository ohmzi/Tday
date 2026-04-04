// @vitest-environment jsdom

import React from "react";
import { render, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

let createStatus: "idle" | "pending" | "success" | "error" = "idle";

vi.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}));

vi.mock("@/providers/TodoFormProvider", () => ({
  useTodoForm: () => ({
    todoItem: undefined,
    title: "Ship web fix",
    setTitle: vi.fn(),
    priority: "Low",
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
  useToast: () => ({
    toast: vi.fn(),
  }),
}));

vi.mock("@/providers/TodoMutationProvider", () => ({
  useTodoMutation: () => ({
    useEditTodo: () => ({
      editTodoMutateFn: vi.fn(),
    }),
    useEditTodoInstance: () => ({
      editTodoInstanceMutateFn: vi.fn(),
    }),
  }),
}));

vi.mock("@/features/todayTodos/query/create-todo", () => ({
  useCreateTodo: () => ({
    createMutateFn: vi.fn(),
    createStatus,
  }),
}));

vi.mock("@/components/todo/component/TodoForm/TodoInlineActionBar/TodoInlineActionBar", () => ({
  default: () => <div data-testid="todo-inline-action-bar" />,
}));

vi.mock("@/components/todo/component/TodoForm/NLPTitleInput", () => ({
  default: () => <div data-testid="todo-title-input" />,
}));

vi.mock("@/components/todo/component/TodoForm/ListDropdownMenu", () => ({
  default: () => <div data-testid="todo-list-dropdown" />,
}));

vi.mock("@/components/ui/button", () => ({
  Button: ({ children, ...props }: React.ButtonHTMLAttributes<HTMLButtonElement>) => (
    <button {...props}>{children}</button>
  ),
}));

vi.mock("@/components/ui/lineSeparator", () => ({
  default: () => <div data-testid="line-separator" />,
}));

import TodoForm from "@/components/todo/component/TodoForm/TodoForm";

describe("TodoForm create close behavior", () => {
  afterEach(() => {
    createStatus = "idle";
  });

  it("closes the create card after a successful create", async () => {
    const setDisplayForm = vi.fn();
    createStatus = "success";

    render(
      <TodoForm
        displayForm
        setDisplayForm={setDisplayForm}
      />,
    );

    await waitFor(() => {
      expect(setDisplayForm).toHaveBeenCalledWith(false);
    });
  });

  it("keeps persistent forms open after a successful create", async () => {
    const setDisplayForm = vi.fn();
    createStatus = "success";

    render(
      <TodoForm
        displayForm
        setDisplayForm={setDisplayForm}
        persistent
      />,
    );

    await new Promise((resolve) => {
      setTimeout(resolve, 0);
    });

    expect(setDisplayForm).not.toHaveBeenCalled();
  });
});
