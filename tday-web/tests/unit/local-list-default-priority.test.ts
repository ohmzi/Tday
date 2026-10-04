// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { api } from "@/lib/api-client";
import { setAppMode } from "@/lib/local/appMode";
import { createLocalVault, resetWorkspaceCache } from "@/lib/local/localDb";

/**
 * A list's default priority in Local Mode. Server Mode has carried it end to end
 * since the feature shipped; the local twin dropped it on the floor, so a task
 * created inside a list always started at Low (issue: "webapp creating task
 * within a list is not carrying its priority level", local half).
 */
const json = (body: unknown) => ({
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
});

const PASSPHRASE = "a list remembers its default";

type ListDto = { id: string; defaultPriority?: string | null };

async function createList(overrides: Record<string, unknown> = {}) {
  const res = (await api.POST({
    url: "/api/list",
    ...json({ name: "Errands", ...overrides }),
  })) as { list: ListDto };
  return res.list;
}

async function lists(): Promise<ListDto[]> {
  const res = (await api.GET({ url: "/api/list" })) as unknown as {
    lists: ListDto[];
  };
  return res.lists;
}

beforeEach(async () => {
  window.localStorage.clear();
  resetWorkspaceCache();
  setAppMode("local");
  await createLocalVault(PASSPHRASE);
});

afterEach(() => {
  setAppMode(null);
  resetWorkspaceCache();
  window.localStorage.clear();
});

describe("local list default priority", () => {
  it("is returned on the created list and on the list query", async () => {
    const created = await createList({ defaultPriority: "High" });
    expect(created.defaultPriority).toBe("High");

    const [listed] = await lists();
    expect(listed.defaultPriority).toBe("High");
  });

  it("reads as null when the list has no default", async () => {
    await createList();
    const [listed] = await lists();
    expect(listed.defaultPriority).toBeNull();
  });

  it("survives an update that changes it, and is left alone when omitted", async () => {
    const created = await createList({ defaultPriority: "Medium" });

    await api.PATCH({
      url: "/api/list",
      ...json({ id: created.id, defaultPriority: "High" }),
    });
    expect((await lists())[0].defaultPriority).toBe("High");

    // A rename that says nothing about priority must not clear it.
    await api.PATCH({
      url: "/api/list",
      ...json({ id: created.id, name: "Errands and chores" }),
    });
    expect((await lists())[0].defaultPriority).toBe("High");
  });

  it("rejects a priority outside the four tiers", async () => {
    await expect(
      createList({ defaultPriority: "Whenever" }),
    ).rejects.toThrow();
  });
});
