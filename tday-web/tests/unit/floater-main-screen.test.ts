// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { api } from "@/lib/api-client";
import { hiddenFloaterListIds, isFloaterShownOnMainScreen } from "@/lib/floater/mainScreen";
import { setAppMode } from "@/lib/local/appMode";
import { createLocalVault, loadWorkspace, resetWorkspaceCache } from "@/lib/local/localDb";
import { summarizeLocal } from "@/lib/local/localSummary";

const json = (body: unknown) => ({
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
});

describe("isFloaterShownOnMainScreen", () => {
  const hidden = new Set(["a"]);

  it("shows a task with no list", () => {
    expect(isFloaterShownOnMainScreen(null, hidden)).toBe(true);
    expect(isFloaterShownOnMainScreen(undefined, hidden)).toBe(true);
  });

  it("shows a task whose list is unknown", () => {
    expect(isFloaterShownOnMainScreen("b", hidden)).toBe(true);
  });

  it("hides a task in a hidden list", () => {
    expect(isFloaterShownOnMainScreen("a", hidden)).toBe(false);
  });
});

describe("hiddenFloaterListIds", () => {
  it("collects only lists explicitly turned off", () => {
    const ids = hiddenFloaterListIds([
      { id: "off", showOnMainScreen: false },
      { id: "on", showOnMainScreen: true },
      { id: "missing" },
    ]);
    expect([...ids]).toEqual(["off"]);
  });
});

describe("Local Mode", () => {
  beforeEach(async () => {
    window.localStorage.clear();
    resetWorkspaceCache();
    setAppMode("local");
    await createLocalVault("floater lists stay out of the feed");
  });

  afterEach(() => {
    setAppMode(null);
    window.localStorage.clear();
    resetWorkspaceCache();
  });

  async function createList(body: Record<string, unknown> = {}) {
    const res = await api.POST({ url: "/api/floaterList", ...json({ name: "Someday", ...body }) });
    return res.list as { id: string; showOnMainScreen: boolean };
  }

  async function readList(id: string) {
    const res = await api.GET({ url: "/api/floaterList" });
    return (res.lists as { id: string; showOnMainScreen: boolean }[]).find((list) => list.id === id)!;
  }

  async function createFloater(title: string, listID: string | null) {
    await api.POST({ url: "/api/floater", ...json({ title, priority: "Low", listID }) });
  }

  it("creates a list shown by default", async () => {
    expect((await createList()).showOnMainScreen).toBe(true);
  });

  it("reads a workspace row saved before the field existed as shown", async () => {
    const list = await createList();
    const row = loadWorkspace().floaterLists.find((candidate) => candidate.id === list.id)!;
    delete row.showOnMainScreen;

    expect((await readList(list.id)).showOnMainScreen).toBe(true);
  });

  it("persists hiding a list", async () => {
    const list = await createList();
    await api.PATCH({ url: "/api/floaterList", ...json({ id: list.id, showOnMainScreen: false }) });

    expect((await readList(list.id)).showOnMainScreen).toBe(false);
  });

  it("leaves a hidden list out of the Floater summary", async () => {
    const list = await createList({ showOnMainScreen: false });
    await createFloater("Tucked away", list.id);
    await createFloater("Out in the open", null);

    const result = summarizeLocal({ mode: "floater", timeZone: "UTC" }) as { taskCount: number };

    expect(result.taskCount).toBe(1);
  });

  it("keeps a hidden list in its own summary", async () => {
    const list = await createList({ showOnMainScreen: false });
    await createFloater("Tucked away", list.id);

    const result = summarizeLocal({ mode: "floater", listId: list.id, timeZone: "UTC" }) as {
      taskCount: number;
    };

    expect(result.taskCount).toBe(1);
  });
});
