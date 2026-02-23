import { auth } from "@/app/auth";
import { POST } from "@/app/api/todo/nlp/route";

jest.mock("@/app/auth", () => ({
  auth: jest.fn(),
}));

describe("POST /api/todo/nlp", () => {
  const authMock = auth as jest.Mock;
  type RouteRequest = Parameters<typeof POST>[0];
  let consoleErrorSpy: jest.SpyInstance;

  beforeEach(() => {
    jest.clearAllMocks();
    consoleErrorSpy = jest.spyOn(console, "error").mockImplementation(() => {});
  });

  afterEach(() => {
    consoleErrorSpy.mockRestore();
  });

  test("returns 401 when user is not authenticated", async () => {
    authMock.mockResolvedValue(null);

    const request = new Request("http://localhost/api/todo/nlp", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        text: "clean car tomorrow at 9pm",
      }),
    });

    const response = await POST(request as unknown as RouteRequest);
    const payload = await response.json();

    expect(response.status).toBe(401);
    expect(payload.message).toContain("logged in");
  });

  test("returns parsed NLP payload for authenticated users", async () => {
    authMock.mockResolvedValue({
      user: { id: "user-1" },
    });

    const referenceEpochMs = Date.UTC(2026, 1, 23, 10, 0, 0);
    const request = new Request("http://localhost/api/todo/nlp", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        text: "clean car tomorrow at 9pm",
        locale: "en-US",
        referenceEpochMs,
        timezoneOffsetMinutes: 0,
        defaultDurationMinutes: 60,
      }),
    });

    const response = await POST(request as unknown as RouteRequest);
    const payload = await response.json();

    expect(response.status).toBe(200);
    expect(payload.cleanTitle).toBe("clean car");
    expect(payload.matchedText.toLowerCase()).toContain("tomorrow");
    expect(payload.startEpochMs).toBeDefined();
    expect(payload.dueEpochMs).toBeDefined();
    expect(payload.dueEpochMs - payload.startEpochMs).toBe(60 * 60 * 1000);
  });
});
