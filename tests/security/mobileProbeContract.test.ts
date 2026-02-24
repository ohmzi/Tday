import { GET } from "@/app/api/mobile/probe/route";

describe("mobile probe contract", () => {
  test("does not expose auth endpoint paths", async () => {
    const response = await GET();
    expect(response.status).toBe(200);

    const payload = (await response.json()) as Record<string, unknown>;
    expect(payload.service).toBe("tday");
    expect(payload.version).toBe("1");
    expect(payload.probe).toBe("ok");
    expect(payload).not.toHaveProperty("auth");
    expect(payload).not.toHaveProperty("csrfPath");
    expect(payload).not.toHaveProperty("credentialsPath");
    expect(payload).not.toHaveProperty("registerPath");
  });
});
