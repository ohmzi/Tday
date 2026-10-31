// @vitest-environment jsdom

import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook } from "@testing-library/react";

const patchMock = vi.fn();
const postMock = vi.fn();

vi.mock("@/hooks/use-toast", () => ({ useToast: () => ({ toast: vi.fn() }) }));
vi.mock("@/lib/api-client", () => ({
  api: {
    PATCH: (...args: unknown[]) => patchMock(...args),
    POST: (...args: unknown[]) => postMock(...args),
  },
}));

import { useConvertTodoToFloater } from "@/features/todayTodos/query/convert-todo-to-floater";
import { useDemoteTodo } from "@/features/todayTodos/query/demote-todo";
import { useEditTodo } from "@/features/todayTodos/query/update-todo";
import type { TodoItemType } from "@/types";

// List, today and calendar rows key themselves as `${todoId}:${instanceMs}`. The
// backend routes take the bare todo id, so every request must carry the real one.
const REAL_ID = "ckrealtodo0000000000000000";
const COMPOSITE_ID = `${REAL_ID}:1793548800000`;

function makeTodo(overrides: Partial<TodoItemType> = {}): TodoItemType {
  return {
    id: COMPOSITE_ID,
    title: "Water plants",
    description: null,
    priority: "Low",
    completed: false,
    pinned: false,
    due: new Date("2026-11-01T09:00:00Z"),
    rrule: null,
    instanceDate: null,
    createdAt: new Date("2026-10-01T00:00:00Z"),
    updatedAt: null,
    order: 1,
    listID: null,
    userID: "user-1",
    ...overrides,
  } as TodoItemType;
}

function wrapperFor(queryClient: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
}

function createClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
}

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

describe("todo demote requests carry the real todo id", () => {
  afterEach(() => {
    patchMock.mockReset();
    postMock.mockReset();
  });

  it("convert-to-floater posts to the bare todo id, not the composite row key", async () => {
    postMock.mockResolvedValue({ message: "todo demoted", floater: { id: "floater-9" } });
    patchMock.mockResolvedValue({ message: "floater updated" });
    const queryClient = createClient();

    const { result } = renderHook(() => useConvertTodoToFloater(), {
      wrapper: wrapperFor(queryClient),
    });

    result.current.convertMutateFn({
      todo: makeTodo(),
      title: "Water plants",
      description: null,
      priority: "Low",
      listID: null,
    });
    await flush();

    expect(postMock).toHaveBeenCalledTimes(1);
    expect(postMock.mock.calls[0][0].url).toBe(`/api/todo/${REAL_ID}/demote`);
    // The floater edit that follows the demote targets the id the demote returned.
    expect(patchMock.mock.calls[0][0].url).toBe("/api/floater");
    expect(JSON.parse(patchMock.mock.calls[0][0].body).id).toBe("floater-9");
  });

  it("let-it-float (useDemoteTodo) posts to the bare todo id", async () => {
    postMock.mockResolvedValue({ message: "todo demoted", floater: { id: "floater-9" } });
    const queryClient = createClient();

    const { result } = renderHook(() => useDemoteTodo(), {
      wrapper: wrapperFor(queryClient),
    });

    result.current.demoteMutateFn(makeTodo());
    await flush();

    expect(postMock).toHaveBeenCalledTimes(1);
    expect(postMock.mock.calls[0][0].url).toBe(`/api/todo/${REAL_ID}/demote`);
  });

  it("a plain edit PATCH carries the bare todo id in the body", async () => {
    patchMock.mockResolvedValue({ message: "Task updated" });
    const queryClient = createClient();

    const { result } = renderHook(() => useEditTodo(), {
      wrapper: wrapperFor(queryClient),
    });

    const todo = makeTodo();
    result.current.editTodoMutateFn({
      ...todo,
      dateRangeChecksum: todo.due.toISOString(),
      rruleChecksum: null,
    } as never);
    await flush();

    expect(patchMock).toHaveBeenCalledTimes(1);
    expect(patchMock.mock.calls[0][0].url).toBe("/api/todo");
    expect(JSON.parse(patchMock.mock.calls[0][0].body).id).toBe(REAL_ID);
  });
});
