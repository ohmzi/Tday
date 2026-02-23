import { auth } from "@/app/auth";
import { getGlobalAppConfig } from "@/lib/appConfig";
import { fetchTimelineTodosForUser } from "@/lib/fetchTimelineTodos";
import { resolveTimezone } from "@/lib/resolveTimeZone";
import { TodoItemType } from "@/types";
import { POST } from "@/app/api/todo/summary/route";

jest.mock("@/app/auth", () => ({
  auth: jest.fn(),
}));

jest.mock("@/lib/fetchTimelineTodos", () => ({
  fetchTimelineTodosForUser: jest.fn(),
}));

jest.mock("@/lib/appConfig", () => ({
  getGlobalAppConfig: jest.fn(),
}));

jest.mock("@/lib/resolveTimeZone", () => ({
  resolveTimezone: jest.fn(),
}));

function makeTodo(
  id: string,
  params: {
    priority?: string;
    due?: Date;
    dtstart?: Date;
  } = {},
): TodoItemType {
  const now = new Date();
  const due = params.due ?? new Date(now.getTime() + 60 * 60 * 1000);
  const start = params.dtstart ?? new Date(due.getTime() - 60 * 60 * 1000);
  return {
    id,
    title: `Task ${id}`,
    description: null,
    pinned: false,
    createdAt: now,
    order: 0,
    priority: params.priority ?? "High",
    dtstart: start,
    durationMinutes: 60,
    due,
    rrule: null,
    timeZone: "UTC",
    userID: "user-1",
    completed: false,
    exdates: [],
    instances: [],
    listID: null,
  };
}

describe("POST /api/todo/summary", () => {
  const authMock = auth as jest.Mock;
  const appConfigMock = getGlobalAppConfig as jest.Mock;
  const fetchTimelineMock = fetchTimelineTodosForUser as jest.Mock;
  const resolveTimezoneMock = resolveTimezone as jest.Mock;
  let consoleErrorSpy: jest.SpyInstance;

  beforeEach(() => {
    jest.clearAllMocks();
    consoleErrorSpy = jest.spyOn(console, "error").mockImplementation(() => {});
    appConfigMock.mockResolvedValue({ aiSummaryEnabled: true });
    resolveTimezoneMock.mockResolvedValue("UTC");
    fetchTimelineMock.mockResolvedValue([makeTodo("todo-1")]);
  });

  afterEach(() => {
    consoleErrorSpy.mockRestore();
  });

  test("returns 401 when user is not authenticated", async () => {
    authMock.mockResolvedValue(null);

    const request = new Request("http://localhost/api/todo/summary", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ mode: "all" }),
    });

    const response = await POST(request as any);
    const payload = await response.json();

    expect(response.status).toBe(401);
    expect(payload.message).toContain("logged in");
  });

  test("falls back to deterministic summary when model call fails", async () => {
    authMock.mockResolvedValue({
      user: {
        id: "user-1",
        timeZone: "UTC",
      },
    } as any);

    const originalFetch = global.fetch;
    (global as any).fetch = jest.fn().mockRejectedValue(new Error("model down"));

    const request = new Request("http://localhost/api/todo/summary", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ mode: "all" }),
    });

    const response = await POST(request as any);
    const payload = await response.json();

    expect(response.status).toBe(200);
    expect(payload.source).toBe("fallback");
    expect(payload.summary).toContain("Start with");
    expect(payload.summary).toContain("due");

    (global as any).fetch = originalFetch;
  });

  test("returns AI summary when model call succeeds", async () => {
    authMock.mockResolvedValue({
      user: {
        id: "user-1",
        timeZone: "UTC",
      },
    } as any);

    const originalFetch = global.fetch;
    (global as any).fetch = jest.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          response: JSON.stringify({
            startId: "T1",
            thenIds: [],
          }),
        }),
        {
          status: 200,
          headers: { "Content-Type": "application/json" },
        },
      ),
    );

    const request = new Request("http://localhost/api/todo/summary", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ mode: "all" }),
    });

    const response = await POST(request as any);
    const payload = await response.json();

    expect(response.status).toBe(200);
    expect(payload.source).toBe("ai");
    expect(payload.summary).toContain("Start with");
    expect(payload.summary).toContain("due");
    expect(payload.summary).not.toMatch(/\b\d{1,2}(:\d{2})?\s*(AM|PM)\b/i);
    expect(payload.summary).not.toContain("priority");

    (global as any).fetch = originalFetch;
  });

  test("uses model-written summary prose when provided", async () => {
    authMock.mockResolvedValue({
      user: {
        id: "user-1",
        timeZone: "UTC",
      },
    } as any);

    const originalFetch = global.fetch;
    (global as any).fetch = jest.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          response: JSON.stringify({
            startId: "T1",
            thenIds: [],
            summary: "Task todo-1 is urgent and due today, so handle it first.",
          }),
        }),
        {
          status: 200,
          headers: { "Content-Type": "application/json" },
        },
      ),
    );

    const request = new Request("http://localhost/api/todo/summary", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ mode: "all" }),
    });

    const response = await POST(request as any);
    const payload = await response.json();

    expect(response.status).toBe(200);
    expect(payload.source).toBe("ai");
    expect(payload.summary).toBe("Task todo-1 is urgent and due today, so handle it first.");

    (global as any).fetch = originalFetch;
  });

  test("accepts long model-written summary prose", async () => {
    authMock.mockResolvedValue({
      user: {
        id: "user-1",
        timeZone: "UTC",
      },
    } as any);
    const longSummary = [
      "Task todo-1 is urgent and due today, so begin there and keep momentum with clear checkpoints.",
      "Break the work into small steps, confirm each step before moving on, and keep interruptions low.",
      "If you get blocked, switch to a quick supporting action, then return and finish the core task with focus.",
      "Close by reviewing what was done, noting any follow-up items, and setting up the next action while context is fresh.",
      "This plan favors steady execution and reduces context switching so the task actually gets completed.",
      "Keep the cadence simple, deliberate, and realistic for the time you have today.",
    ].join(" ");

    const originalFetch = global.fetch;
    (global as any).fetch = jest.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          response: JSON.stringify({
            startId: "T1",
            thenIds: [],
            summary: longSummary,
          }),
        }),
        {
          status: 200,
          headers: { "Content-Type": "application/json" },
        },
      ),
    );

    const request = new Request("http://localhost/api/todo/summary", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ mode: "all" }),
    });

    const response = await POST(request as any);
    const payload = await response.json();

    expect(response.status).toBe(200);
    expect(payload.source).toBe("ai");
    expect(payload.summary).toBe(longSummary);
    expect(longSummary.length).toBeGreaterThan(520);

    (global as any).fetch = originalFetch;
  });

  test("falls back when model prose omits required day/date context", async () => {
    authMock.mockResolvedValue({
      user: {
        id: "user-1",
        timeZone: "UTC",
      },
    } as any);
    const base = Date.now();
    fetchTimelineMock.mockResolvedValue([
      makeTodo("today-low", {
        priority: "Low",
        due: new Date(base + 2 * 60 * 60 * 1000),
        dtstart: new Date(base + 60 * 60 * 1000),
      }),
      makeTodo("far-high", {
        priority: "High",
        due: new Date(base + 30 * 24 * 60 * 60 * 1000),
        dtstart: new Date(base + 30 * 24 * 60 * 60 * 1000 - 60 * 60 * 1000),
      }),
    ]);

    const originalFetch = global.fetch;
    (global as any).fetch = jest.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          response: JSON.stringify({
            startId: "T1",
            thenIds: ["T2"],
            summary: "Start with Task today-low, then do Task far-high.",
          }),
        }),
        {
          status: 200,
          headers: { "Content-Type": "application/json" },
        },
      ),
    );

    const request = new Request("http://localhost/api/todo/summary", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ mode: "all" }),
    });

    const response = await POST(request as any);
    const payload = await response.json();

    expect(response.status).toBe(200);
    expect(payload.source).toBe("ai");
    expect(payload.summary).not.toBe("Start with Task today-low, then do Task far-high.");
    expect(payload.summary.toLowerCase()).toMatch(/today|tomorrow|on \d/);

    (global as any).fetch = originalFetch;
  });

  test("priority mode fallback keeps upcoming tasks when AI focuses only on overdue items", async () => {
    authMock.mockResolvedValue({
      user: {
        id: "user-1",
        timeZone: "UTC",
      },
    } as any);
    const base = Date.now();
    fetchTimelineMock.mockResolvedValue([
      makeTodo("past-a", {
        priority: "High",
        due: new Date(base - 30 * 60 * 60 * 1000),
        dtstart: new Date(base - 31 * 60 * 60 * 1000),
      }),
      makeTodo("past-b", {
        priority: "Medium",
        due: new Date(base - 28 * 60 * 60 * 1000),
        dtstart: new Date(base - 29 * 60 * 60 * 1000),
      }),
      makeTodo("future-a", {
        priority: "High",
        due: new Date(base + 4 * 24 * 60 * 60 * 1000 + 8 * 60 * 60 * 1000),
        dtstart: new Date(base + 4 * 24 * 60 * 60 * 1000 + 7 * 60 * 60 * 1000),
      }),
      makeTodo("future-b", {
        priority: "Medium",
        due: new Date(base + 4 * 24 * 60 * 60 * 1000 + 20 * 60 * 60 * 1000),
        dtstart: new Date(base + 4 * 24 * 60 * 60 * 1000 + 19 * 60 * 60 * 1000),
      }),
    ]);

    const originalFetch = global.fetch;
    (global as any).fetch = jest.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          response: JSON.stringify({
            startId: "T1",
            thenIds: ["T2"],
            summary: "Start with Task past-a due yesterday, then Task past-b due yesterday.",
          }),
        }),
        {
          status: 200,
          headers: { "Content-Type": "application/json" },
        },
      ),
    );

    const request = new Request("http://localhost/api/todo/summary", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ mode: "priority" }),
    });

    const response = await POST(request as any);
    const payload = await response.json();

    expect(response.status).toBe(200);
    expect(payload.source).toBe("ai");
    expect(payload.summary).toContain("Task future-a");
    expect(payload.summary).toContain("Task future-b");

    (global as any).fetch = originalFetch;
  });

  test("keeps all-mode AI summary start aligned with earliest due task", async () => {
    authMock.mockResolvedValue({
      user: {
        id: "user-1",
        timeZone: "UTC",
      },
    } as any);
    const base = Date.now();
    fetchTimelineMock.mockResolvedValue([
      makeTodo("far-high", {
        priority: "High",
        due: new Date(base + 30 * 24 * 60 * 60 * 1000),
        dtstart: new Date(base + 30 * 24 * 60 * 60 * 1000 - 60 * 60 * 1000),
      }),
      makeTodo("today-low", {
        priority: "Low",
        due: new Date(base + 2 * 60 * 60 * 1000),
        dtstart: new Date(base + 60 * 60 * 1000),
      }),
    ]);

    const originalFetch = global.fetch;
    (global as any).fetch = jest.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          response: JSON.stringify({
            startId: "T2",
            thenIds: ["T1"],
          }),
        }),
        {
          status: 200,
          headers: { "Content-Type": "application/json" },
        },
      ),
    );

    const request = new Request("http://localhost/api/todo/summary", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ mode: "all" }),
    });

    const response = await POST(request as any);
    const payload = await response.json();

    expect(response.status).toBe(200);
    expect(payload.source).toBe("ai");
    expect(payload.summary).toContain("Start with Task today-low");
    expect(payload.summary).toContain("due today");

    (global as any).fetch = originalFetch;
  });

  test("returns 403 when AI summary is disabled by admin", async () => {
    authMock.mockResolvedValue({
      user: {
        id: "user-1",
        timeZone: "UTC",
      },
    } as any);
    appConfigMock.mockResolvedValue({ aiSummaryEnabled: false });

    const request = new Request("http://localhost/api/todo/summary", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ mode: "today" }),
    });

    const response = await POST(request as any);
    const payload = await response.json();
    expect(response.status).toBe(403);
    expect(payload.message).toContain("disabled");
  });
});
