// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { api } from "@/lib/api-client";
import { setAppMode } from "@/lib/local/appMode";
import { createLocalVault, loadWorkspace, resetWorkspaceCache } from "@/lib/local/localDb";

/**
 * Local Mode's twin of the backend's CompletedFloaterDurabilityTest — same
 * scenarios, against the browser-storage workspace instead of Postgres. See
 * docs/design/completed-floaters-durability.md. Floaters only: the identical
 * bug for scheduled Todos/CompletedTodos is intentionally untouched.
 */

const json = (body: unknown) => ({
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
});

const PASSPHRASE = "completed floaters survive their list";

beforeEach(async () => {
  window.localStorage.clear();
  resetWorkspaceCache();
  setAppMode("local");
  await createLocalVault(PASSPHRASE);
});

afterEach(() => {
  setAppMode(null);
  window.localStorage.clear();
  resetWorkspaceCache();
});

async function createFloaterList(name: string, color = "TEAL") {
  const res = await api.POST({ url: "/api/floaterList", ...json({ name, color }) });
  return res.list.id as string;
}

async function createFloater(title: string, listID: string | null) {
  const res = await api.POST({
    url: "/api/floater",
    ...json({ title, priority: "Low", listID }),
  });
  return res.floater.id as string;
}

async function completeFloater(id: string) {
  await api.PATCH({ url: "/api/floater/complete", ...json({ id }) });
}

describe("local mode: completed floaters survive their list being deleted", () => {
  it("uncomplete restores in place, unchanged, when the list was never deleted", async () => {
    const listId = await createFloaterList("Errands");
    const floaterId = await createFloater("Buy milk", listId);
    await completeFloater(floaterId);

    const before = await api.GET({ url: "/api/completedFloater" });
    expect(before.completedFloaters).toHaveLength(1);
    expect(before.completedFloaters[0].listDeleted).toBe(false);
    expect(before.completedFloaters[0].listName).toBe("Errands");

    const uncompleted = await api.PATCH({
      url: "/api/floater/uncomplete",
      ...json({ id: floaterId }),
    });
    expect(uncompleted.listRecreated).toBe(false);
    expect(uncompleted.listID).toBe(listId);

    expect((await api.GET({ url: "/api/floater" })).floaters).toHaveLength(1);
    expect(
      (await api.GET({ url: "/api/completedFloater" })).completedFloaters,
    ).toHaveLength(0);
  });

  it("recreates the deleted list under its original name/color and lands the floater there", async () => {
    const listId = await createFloaterList("Trip", "GOLD");
    const floaterId = await createFloater("Pack passport", listId);
    await completeFloater(floaterId);

    await api.DELETE({ url: "/api/floaterList", ...json({ id: listId }) });

    const afterDelete = await api.GET({ url: "/api/completedFloater" });
    expect(afterDelete.completedFloaters).toHaveLength(1);
    expect(afterDelete.completedFloaters[0].listDeleted).toBe(true);
    expect(afterDelete.completedFloaters[0].listID).toBeNull();
    expect(afterDelete.completedFloaters[0].listName).toBe("Trip");

    const uncompleted = await api.PATCH({
      url: "/api/floater/uncomplete",
      ...json({ id: floaterId }),
    });
    expect(uncompleted.listRecreated).toBe(true);
    expect(uncompleted.listName).toBe("Trip");
    expect(uncompleted.listColor).toBe("GOLD");
    // A NEW list id — the client must not assume this is the one it had before.
    expect(uncompleted.listID).not.toBe(listId);

    const floaters = await api.GET({ url: "/api/floater" });
    expect(floaters.floaters).toHaveLength(1);
    expect(floaters.floaters[0].listID).toBe(uncompleted.listID);

    const lists = await api.GET({ url: "/api/floaterList" });
    expect(lists.lists).toHaveLength(1);
    expect(lists.lists[0].id).toBe(uncompleted.listID);
    expect(lists.lists[0].name).toBe("Trip");
  });

  it("converges a second undo from the same deleted list onto the first undo's recreated list", async () => {
    const listId = await createFloaterList("Trip", "GOLD");
    const firstId = await createFloater("Pack passport", listId);
    const secondId = await createFloater("Book taxi", listId);
    await completeFloater(firstId);
    await completeFloater(secondId);

    await api.DELETE({ url: "/api/floaterList", ...json({ id: listId }) });

    const firstUndo = await api.PATCH({
      url: "/api/floater/uncomplete",
      ...json({ id: firstId }),
    });
    const secondUndo = await api.PATCH({
      url: "/api/floater/uncomplete",
      ...json({ id: secondId }),
    });

    expect(firstUndo.listRecreated).toBe(true);
    expect(secondUndo.listRecreated).toBe(true);
    // No duplicate list from the second undo — both converge on one row.
    expect(secondUndo.listID).toBe(firstUndo.listID);

    const lists = await api.GET({ url: "/api/floaterList" });
    expect(lists.lists).toHaveLength(1);

    const floaters = await api.GET({ url: "/api/floater" });
    expect(floaters.floaters).toHaveLength(2);
    for (const floater of floaters.floaters as Array<{ listID: string | null }>) {
      expect(floater.listID).toBe(firstUndo.listID);
    }
  });

  it("removing a completed floater forever no longer orphans its Floaters row", async () => {
    const floaterId = await createFloater("One-off", null);
    await completeFloater(floaterId);

    const history = await api.GET({ url: "/api/completedFloater" });
    expect(history.completedFloaters).toHaveLength(1);
    const entryId = history.completedFloaters[0].id as string;

    // Soft-completed Floaters row is still there — invisible to GET
    // /api/floater (which filters completed=false) but present internally,
    // same as the backend's Floaters table before this fix.
    expect(loadWorkspace().floaters).toHaveLength(1);

    await api.DELETE({ url: "/api/completedFloater", ...json({ id: entryId }) });

    expect(loadWorkspace().floaters).toHaveLength(0);
    expect(
      (await api.GET({ url: "/api/completedFloater" })).completedFloaters,
    ).toHaveLength(0);
  });
});
