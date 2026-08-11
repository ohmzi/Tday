// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { ApiError, api } from "@/lib/api-client";
import { setAppMode } from "@/lib/local/appMode";
import {
  clearWorkspace,
  createLocalVault,
  createOpenLocalWorkspace,
  flushWorkspaceWrites,
  getLocalProtection,
  getLocalVaultState,
  keepLegacyWorkspaceOpen,
  openLocalWorkspace,
  protectPlaintextWorkspace,
  resetWorkspaceCache,
  unlockLocalVault,
  LOCAL_WORKSPACE_STORAGE_KEY,
} from "@/lib/local/localDb";
import {
  isOpenWorkspaceDocument,
  isVaultEnvelope,
} from "@/lib/local/localCrypto";

/**
 * Local Mode is exercised through the shared api client, because that is how
 * every feature hook reaches it — if these pass, the app's own query/mutation
 * code paths work against browser storage unchanged.
 */

type TodoDto = {
  id: string;
  title: string;
  due: string;
  rrule: string | null;
  completed: boolean;
  listID: string | null;
};

const json = (body: unknown) => ({
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
});

/** A page reload: pending writes land, memory goes, the passphrase reopens it. */
async function reopenWorkspace(passphrase: string = PASSPHRASE) {
  await flushWorkspaceWrites();
  resetWorkspaceCache();
  await unlockLocalVault(passphrase);
}

/**
 * Makes every `localStorage` write throw, the way a browser out of quota does.
 *
 * Patched on the prototype, not the instance: jsdom's `Storage` is a proxy whose
 * `defineProperty` trap writes a stored *item* rather than shadowing the method,
 * so an instance-level stub is silently ignored and the failure never happens.
 */
function refuseStorageWrites(): () => void {
  const real = Storage.prototype.setItem;
  Storage.prototype.setItem = () => {
    throw new DOMException("quota", "QuotaExceededError");
  };
  return () => {
    Storage.prototype.setItem = real;
  };
}

async function createTodo(overrides: Record<string, unknown> = {}) {
  const res = await api.POST({
    url: "/api/todo",
    ...json({
      title: "Water the plants",
      priority: "Low",
      due: "2026-08-04T09:30:00.000",
      rrule: null,
      ...overrides,
    }),
  });
  return (res as { todo: TodoDto }).todo;
}

// Every test below starts from a fresh workspace sealed under this passphrase,
// which is how Local Mode is set up by default. The unencrypted alternative gets
// its own describe block at the bottom, with its own setup.
const PASSPHRASE = "a river runs through it";

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

describe("local mode session", () => {
  it("signs the browser in as an approved local user without a network call", async () => {
    const session = await api.GET({ url: "/api/auth/session" });
    expect(session.user.id).toBe("local");
    expect(session.user.approvalStatus).toBe("APPROVED");
    expect(session.user.role).toBe("USER");
    expect(session.user.requirePasswordChange).toBe(false);
  });

  it("fails server-only routes as 404 instead of pretending to succeed", async () => {
    await expect(api.GET({ url: "/api/user/api-key" })).rejects.toBeInstanceOf(
      ApiError,
    );
    await expect(api.GET({ url: "/api/user/api-key" })).rejects.toMatchObject({
      status: 404,
    });
  });
});

describe("local mode todos", () => {
  it("creates a todo and returns it from the timeline and the date range", async () => {
    const todo = await createTodo();
    expect(todo.id).toBeTruthy();
    expect(todo.due).toBe("2026-08-04T09:30:00.000");

    const timeline = await api.GET({ url: "/api/todo?timeline=true" });
    expect(timeline.todos).toHaveLength(1);
    expect(timeline.todos[0].title).toBe("Water the plants");

    const dayStart = Date.UTC(2026, 7, 4, 0, 0, 0);
    const dayEnd = Date.UTC(2026, 7, 4, 23, 59, 59);
    const inRange = await api.GET({
      url: `/api/todo?start=${dayStart}&end=${dayEnd}`,
    });
    expect(inRange.todos).toHaveLength(1);

    const otherDayStart = Date.UTC(2026, 7, 5, 0, 0, 0);
    const otherDayEnd = Date.UTC(2026, 7, 5, 23, 59, 59);
    const outOfRange = await api.GET({
      url: `/api/todo?start=${otherDayStart}&end=${otherDayEnd}`,
    });
    expect(outOfRange.todos).toHaveLength(0);
  });

  it("always returns recurring templates so the client can expand them", async () => {
    await createTodo({ rrule: "RRULE:FREQ=DAILY;INTERVAL=1" });
    const farFutureStart = Date.UTC(2030, 0, 1);
    const farFutureEnd = Date.UTC(2030, 0, 31);
    const res = await api.GET({
      url: `/api/todo?start=${farFutureStart}&end=${farFutureEnd}`,
    });
    expect(res.todos).toHaveLength(1);
    expect(res.todos[0].rrule).toBe("RRULE:FREQ=DAILY;INTERVAL=1");
  });

  it("floors a due date to the minute, matching the server contract", async () => {
    const todo = await createTodo({ due: "2026-08-04T09:30:45.678" });
    expect(todo.due).toBe("2026-08-04T09:30:00.000");
  });

  it("moves a completed one-off out of the feed and into history", async () => {
    const todo = await createTodo();
    await api.PATCH({ url: "/api/todo/complete", ...json({ id: todo.id }) });

    const timeline = await api.GET({ url: "/api/todo?timeline=true" });
    expect(timeline.todos).toHaveLength(0);

    const history = await api.GET({ url: "/api/completedTodo" });
    expect(history.completedTodos).toHaveLength(1);
    expect(history.completedTodos[0].originalTodoID).toBe(todo.id);
    expect(history.completedTodos[0].title).toBe("Water the plants");
  });

  it("records one history entry per recurring occurrence", async () => {
    const todo = await createTodo({ rrule: "RRULE:FREQ=DAILY;INTERVAL=1" });
    const firstOccurrence = Date.UTC(2026, 7, 4, 9, 30);
    const secondOccurrence = Date.UTC(2026, 7, 5, 9, 30);

    // The feeds send an occurrence timestamp as epoch millis.
    await api.PATCH({
      url: "/api/todo/complete",
      ...json({ id: todo.id, instanceDate: firstOccurrence }),
    });
    await api.PATCH({
      url: "/api/todo/complete",
      ...json({ id: todo.id, instanceDate: firstOccurrence }),
    });
    await api.PATCH({
      url: "/api/todo/complete",
      ...json({ id: todo.id, instanceDate: secondOccurrence }),
    });

    const history = await api.GET({ url: "/api/completedTodo" });
    expect(history.completedTodos).toHaveLength(2);

    // The series itself keeps running.
    const timeline = await api.GET({ url: "/api/todo?timeline=true" });
    expect(timeline.todos).toHaveLength(1);
  });

  it("uncompletes a recurring occurrence back out of history", async () => {
    const todo = await createTodo({ rrule: "RRULE:FREQ=DAILY;INTERVAL=1" });
    const occurrence = Date.UTC(2026, 7, 4, 9, 30);
    await api.PATCH({
      url: "/api/todo/complete",
      ...json({ id: todo.id, instanceDate: occurrence }),
    });
    await api.PATCH({
      url: "/api/todo/uncomplete",
      ...json({ id: todo.id, instanceDate: occurrence }),
    });

    const history = await api.GET({ url: "/api/completedTodo" });
    expect(history.completedTodos).toHaveLength(0);
  });

  it("rejects a create without a valid due date", async () => {
    await expect(
      api.POST({ url: "/api/todo", ...json({ title: "No date", priority: "Low" }) }),
    ).rejects.toMatchObject({ status: 400 });
  });
});

describe("local mode lists", () => {
  it("counts a list's pending tasks and cascades a delete", async () => {
    const created = await api.POST({ url: "/api/list", ...json({ name: "Home" }) });
    const listId = created.list.id as string;

    await createTodo({ listID: listId });
    await createTodo({ title: "Second", listID: listId });

    const lists = await api.GET({ url: "/api/list" });
    expect(lists.lists).toHaveLength(1);
    expect(lists.lists[0].todoCount).toBe(2);
    // Sharing is meaningless in a single-browser workspace.
    expect(lists.lists[0].myRole).toBe("OWNER");
    expect(lists.lists[0].isShared).toBe(false);

    const detail = await api.GET({ url: `/api/list/${listId}` });
    expect(detail.todos).toHaveLength(2);

    const deleted = await api.DELETE({ url: "/api/list", ...json({ id: listId }) });
    expect(deleted.deletedIds).toEqual([listId]);

    const timeline = await api.GET({ url: "/api/todo?timeline=true" });
    expect(timeline.todos).toHaveLength(0);
  });

  it("resets a reusable floater list", async () => {
    const created = await api.POST({
      url: "/api/floaterList",
      ...json({ name: "Packing", reusable: true }),
    });
    const listId = created.list.id as string;

    const floater = await api.POST({
      url: "/api/floater",
      ...json({ title: "Passport", priority: "Low", listID: listId }),
    });
    await api.PATCH({
      url: "/api/floater/complete",
      ...json({ id: floater.floater.id }),
    });

    expect((await api.GET({ url: "/api/floater" })).floaters).toHaveLength(0);

    const reset = await api.POST({
      url: `/api/floaterList/${listId}/reset`,
      body: "{}",
    });
    expect(reset.resetCount).toBe("1");
    expect((await api.GET({ url: "/api/floater" })).floaters).toHaveLength(1);
    expect(
      (await api.GET({ url: "/api/completedFloater" })).completedFloaters,
    ).toHaveLength(0);
  });
});

describe("local mode floaters", () => {
  it("promotes a floater into a scheduled todo and consumes the floater", async () => {
    const created = await api.POST({
      url: "/api/floater",
      ...json({ title: "Book dentist", priority: "High" }),
    });
    const floaterId = created.floater.id as string;

    const promoted = await api.POST({
      url: `/api/floater/${floaterId}/promote`,
      ...json({ due: "2026-08-06T10:00:00.000" }),
    });
    expect(promoted.todo.title).toBe("Book dentist");
    expect(promoted.todo.due).toBe("2026-08-06T10:00:00.000");
    // Floater lists and todo lists are separate types; membership stays behind.
    expect(promoted.todo.listID).toBeNull();

    expect((await api.GET({ url: "/api/floater" })).floaters).toHaveLength(0);
    expect((await api.GET({ url: "/api/todo?timeline=true" })).todos).toHaveLength(1);
  });

  it("demotes a one-off todo into a floater but refuses a recurring one", async () => {
    const todo = await createTodo();
    const demoted = await api.POST({
      url: `/api/todo/${todo.id}/demote`,
      body: "{}",
    });
    expect(demoted.floater.title).toBe("Water the plants");
    expect((await api.GET({ url: "/api/todo?timeline=true" })).todos).toHaveLength(0);

    const recurring = await createTodo({ rrule: "RRULE:FREQ=DAILY;INTERVAL=1" });
    await expect(
      api.POST({ url: `/api/todo/${recurring.id}/demote`, body: "{}" }),
    ).rejects.toMatchObject({ status: 400 });
  });
});

describe("local mode task steps", () => {
  it("appends, toggles, reorders and deletes steps", async () => {
    const todo = await createTodo();
    const first = await api.POST({
      url: "/api/todo/steps",
      ...json({ todoId: todo.id, title: "Fill the can" }),
    });
    const second = await api.POST({
      url: "/api/todo/steps",
      ...json({ todoId: todo.id, title: "Check the soil" }),
    });
    expect(first.step.position).toBe(0);
    expect(second.step.position).toBe(1);

    await api.POST({
      url: "/api/todo/steps/toggle",
      ...json({ id: first.step.id, completed: true }),
    });
    await api.POST({
      url: "/api/todo/steps/reorder",
      ...json({ todoId: todo.id, orderedIds: [second.step.id, first.step.id] }),
    });

    const listed = await api.GET({ url: `/api/todo/${todo.id}/steps` });
    expect(listed.steps.map((step: { title: string }) => step.title)).toEqual([
      "Check the soil",
      "Fill the can",
    ]);
    expect(listed.steps[1].completed).toBe(true);

    await api.POST({
      url: "/api/todo/steps/delete",
      ...json({ id: second.step.id }),
    });
    expect((await api.GET({ url: `/api/todo/${todo.id}/steps` })).steps).toHaveLength(1);
  });
});

describe("local mode preferences", () => {
  it("persists a preference patch across a reload of the workspace", async () => {
    await api.PATCH({
      url: "/api/preferences",
      ...json({ sortBy: "priority", aiSummaryEnabled: false }),
    });

    await reopenWorkspace();
    const prefs = await api.GET({ url: "/api/preferences" });
    expect(prefs.sortBy).toBe("priority");
    expect(prefs.aiSummaryEnabled).toBe(false);
    // The deprecated nested mirror stays null, as on the server.
    expect(prefs.userPreferences).toBeNull();
  });
});

describe("local mode summary", () => {
  it("always answers from the deterministic engine, never an AI source", async () => {
    const capability = await api.GET({ url: "/api/app-settings" });
    expect(capability.aiSummaryConfigured).toBe(false);

    const empty = await api.POST({
      url: "/api/todo/summary",
      ...json({ mode: "today", timeZone: "UTC" }),
    });
    expect(empty.source).toBe("logic");
    expect(empty.taskCount).toBe(0);
    expect(empty.reason).toBe("empty");
    expect(empty.summary).toBeTruthy();

    await createTodo({ due: new Date().toISOString() });
    const populated = await api.POST({
      url: "/api/todo/summary",
      ...json({ mode: "today", timeZone: "UTC" }),
    });
    expect(populated.source).toBe("logic");
    expect(populated.taskCount).toBe(1);
    expect(populated.summary).toContain("Water the plants");
  });

  it("rejects an unknown summary mode", async () => {
    await expect(
      api.POST({ url: "/api/todo/summary", ...json({ mode: "nonsense" }) }),
    ).rejects.toMatchObject({ status: 400 });
  });

  it("splits a brain dump into candidate tasks on device", async () => {
    const res = await api.POST({
      url: "/api/todo/brain-dump",
      ...json({ text: "- water the plants every day\n- call mum;book dentist" }),
    });
    expect(res.candidates).toHaveLength(3);
    expect(res.candidates[0].rrule).toBe("RRULE:FREQ=DAILY;INTERVAL=1");
    expect(res.candidates.map((c: { title: string }) => c.title)).toContain(
      "book dentist",
    );
  });
});

describe("local mode data transfer", () => {
  it("exports a bundle and re-imports it additively with fresh ids", async () => {
    const list = await api.POST({ url: "/api/list", ...json({ name: "Home" }) });
    await createTodo({ listID: list.list.id });

    const bundle = await api.GET({ url: "/api/export" });
    expect(bundle.source).toBe("local-web");
    expect(bundle.todos).toHaveLength(1);
    expect(bundle.lists).toHaveLength(1);

    const preview = await api.POST({
      url: "/api/import",
      ...json({ export: bundle, dryRun: true }),
    });
    expect(preview.dryRun).toBe(true);
    expect(preview.imported.todos).toBe(1);
    // Every id already exists here, so nothing is overwritten.
    expect(preview.imported.remappedIds).toBeGreaterThan(0);
    expect((await api.GET({ url: "/api/todo?timeline=true" })).todos).toHaveLength(1);

    await api.POST({
      url: "/api/import",
      ...json({ export: bundle, dryRun: false }),
    });
    const todos = (await api.GET({ url: "/api/todo?timeline=true" })).todos;
    expect(todos).toHaveLength(2);
    expect(new Set(todos.map((todo: TodoDto) => todo.id)).size).toBe(2);
    // The copied todo points at the copied list, not the original.
    const lists = (await api.GET({ url: "/api/list" })).lists;
    expect(lists).toHaveLength(2);
    expect(lists.every((entry: { todoCount: number }) => entry.todoCount === 1)).toBe(
      true,
    );
  });

  it("refuses a bundle from a newer schema version", async () => {
    await expect(
      api.POST({
        url: "/api/import",
        ...json({ export: { schemaVersion: 99 }, dryRun: true }),
      }),
    ).rejects.toMatchObject({ status: 400 });
  });
});

describe("local mode persistence", () => {
  it("survives a page reload and disappears when site data is cleared", async () => {
    await createTodo();
    await flushWorkspaceWrites();
    expect(window.localStorage.getItem(LOCAL_WORKSPACE_STORAGE_KEY)).toBeTruthy();

    // Reload: a fresh module cache reading the same storage.
    await reopenWorkspace();
    expect((await api.GET({ url: "/api/todo?timeline=true" })).todos).toHaveLength(1);

    // "Clear cookies and site data" — the documented way to lose a local workspace.
    window.localStorage.clear();
    resetWorkspaceCache();
    await createLocalVault(PASSPHRASE);
    expect((await api.GET({ url: "/api/todo?timeline=true" })).todos).toHaveLength(0);
  });

  it("stores nothing but ciphertext, and stays shut without the passphrase", async () => {
    await createTodo({ title: "Call the clinic about the results" });
    await flushWorkspaceWrites();

    const stored = window.localStorage.getItem(LOCAL_WORKSPACE_STORAGE_KEY);
    expect(stored).not.toContain("clinic");
    expect(isVaultEnvelope(JSON.parse(stored ?? "null"))).toBe(true);

    // Snooping the profile after the tab closed finds a locked workspace.
    await flushWorkspaceWrites();
    resetWorkspaceCache();
    expect(getLocalVaultState()).toBe("locked");
    await expect(unlockLocalVault("not the passphrase")).rejects.toMatchObject({
      code: "wrong-passphrase",
    });
    expect(getLocalVaultState()).toBe("locked");

    // The real passphrase still opens it — a failed attempt breaks nothing.
    await unlockLocalVault(PASSPHRASE);
    expect((await api.GET({ url: "/api/todo?timeline=true" })).todos).toHaveLength(1);
  });

  it("encrypts a legacy plaintext workspace in place instead of wiping it", async () => {
    // A workspace exactly as a build before encryption left it.
    resetWorkspaceCache();
    window.localStorage.setItem(
      LOCAL_WORKSPACE_STORAGE_KEY,
      JSON.stringify({
        schemaVersion: 1,
        todos: [
          {
            id: "legacy-1",
            title: "Renew the passport",
            description: null,
            pinned: false,
            priority: "Low",
            due: "2026-08-04T09:30:00.000",
            rrule: null,
            timeZone: null,
            completed: false,
            order: 0,
            listID: null,
            createdAt: "2026-08-01T09:00:00.000",
            updatedAt: "2026-08-01T09:00:00.000",
            exdates: [],
          },
        ],
        preferences: { sortBy: "priority", groupBy: null, direction: null, aiSummaryEnabled: true },
      }),
    );
    expect(getLocalVaultState()).toBe("legacy");

    await protectPlaintextWorkspace(PASSPHRASE);
    expect(getLocalVaultState()).toBe("unlocked");

    const stored = window.localStorage.getItem(LOCAL_WORKSPACE_STORAGE_KEY);
    expect(stored).not.toContain("passport");
    expect(isVaultEnvelope(JSON.parse(stored ?? "null"))).toBe(true);

    // The rows came through the migration unharmed.
    await reopenWorkspace();
    const todos = (await api.GET({ url: "/api/todo?timeline=true" })).todos;
    expect(todos).toHaveLength(1);
    expect(todos[0].title).toBe("Renew the passport");
    expect((await api.GET({ url: "/api/preferences" })).sortBy).toBe("priority");
  });

  it("fails the migration loudly when the browser refuses to store the envelope", async () => {
    // The dangerous shape of a storage failure: the legacy plaintext is still on
    // disk, so reporting success would open the app while claiming an encryption
    // that never happened.
    resetWorkspaceCache();
    window.localStorage.setItem(
      LOCAL_WORKSPACE_STORAGE_KEY,
      JSON.stringify({
        schemaVersion: 1,
        todos: [],
        preferences: { sortBy: null, groupBy: null, direction: null, aiSummaryEnabled: true },
      }),
    );

    const restore = refuseStorageWrites();
    try {
      await expect(protectPlaintextWorkspace(PASSPHRASE)).rejects.toMatchObject({
        code: "storage",
      });
    } finally {
      restore();
    }

    // Nothing was adopted: the gate keeps asking rather than opening the app.
    expect(getLocalVaultState()).toBe("legacy");
  });
});

describe("local mode without encryption", () => {
  // This block starts from nothing rather than the sealed-workspace beforeEach
  // above: the whole point here is that no passphrase is ever involved.
  beforeEach(() => {
    window.localStorage.clear();
    resetWorkspaceCache();
    setAppMode("local");
    createOpenLocalWorkspace();
  });

  it("round-trips a workspace with no key at all", async () => {
    await createTodo({ title: "Water the plants" });
    await flushWorkspaceWrites();
    resetWorkspaceCache();

    // A reload finds it readable without asking for anything.
    expect(getLocalVaultState()).toBe("open");
    openLocalWorkspace();
    expect(getLocalVaultState()).toBe("unlocked");
    expect(getLocalProtection()).toBe("none");
    const todos = (await api.GET({ url: "/api/todo?timeline=true" })).todos;
    expect(todos).toHaveLength(1);
    expect(todos[0].title).toBe("Water the plants");
  });

  it("stores rows in the clear, honestly, inside the open wrapper", async () => {
    await createTodo({ title: "Call the clinic about the results" });
    await flushWorkspaceWrites();

    const stored = window.localStorage.getItem(LOCAL_WORKSPACE_STORAGE_KEY);
    // Unlike the encrypted path, the title really is readable in storage — that
    // is the documented trade the user opted into, not a leak to catch.
    expect(stored).toContain("clinic");
    const parsed = JSON.parse(stored ?? "null");
    expect(isOpenWorkspaceDocument(parsed)).toBe(true);
    expect(isVaultEnvelope(parsed)).toBe(false);
  });

  it("never reads back as the legacy migration prompt", async () => {
    // Cold, right after creation — a fresh session probing storage from scratch.
    resetWorkspaceCache();
    expect(getLocalVaultState()).toBe("open");

    // And again after a write and a reload — a wrapper written only on first
    // save, rather than on every save, would regress exactly this.
    openLocalWorkspace();
    await createTodo();
    await flushWorkspaceWrites();
    resetWorkspaceCache();
    expect(getLocalVaultState()).toBe("open");
    expect(getLocalVaultState()).not.toBe("legacy");
  });

  it("cancels a queued write on clear, the same as the encrypted path", async () => {
    await createTodo();
    // Deliberately not flushed: the write is still queued when the wipe happens.
    clearWorkspace();
    await flushWorkspaceWrites();
    expect(window.localStorage.getItem(LOCAL_WORKSPACE_STORAGE_KEY)).toBeNull();
  });

  it("keeps answering after Delete local data instead of throwing 423", async () => {
    await createTodo();
    await flushWorkspaceWrites();
    clearWorkspace();
    const todos = (await api.GET({ url: "/api/todo?timeline=true" })).todos;
    expect(todos).toHaveLength(0);
  });

  it("upgrades to encrypted in place, preserving the rows", async () => {
    await createTodo({ title: "Renew the passport" });
    await flushWorkspaceWrites();

    await protectPlaintextWorkspace(PASSPHRASE);
    expect(getLocalVaultState()).toBe("unlocked");
    expect(getLocalProtection()).toBe("passphrase");

    const stored = window.localStorage.getItem(LOCAL_WORKSPACE_STORAGE_KEY);
    expect(stored).not.toContain("passport");
    expect(isVaultEnvelope(JSON.parse(stored ?? "null"))).toBe(true);

    await reopenWorkspace();
    const todos = (await api.GET({ url: "/api/todo?timeline=true" })).todos;
    expect(todos).toHaveLength(1);
    expect(todos[0].title).toBe("Renew the passport");
  });

  it("fails an upgrade loudly, leaving the workspace open rather than half-sealed", async () => {
    await createTodo({ title: "Renew the passport" });
    await flushWorkspaceWrites();

    const restore = refuseStorageWrites();
    try {
      await expect(protectPlaintextWorkspace(PASSPHRASE)).rejects.toMatchObject({
        code: "storage",
      });
    } finally {
      restore();
    }

    // The failed upgrade left this session's own state untouched — still open,
    // not silently promoted to a passphrase it never actually applied.
    expect(getLocalProtection()).toBe("none");

    // And a fresh session finds the same thing on disk: still readable without a
    // key, not a document half-sealed by the failed write.
    resetWorkspaceCache();
    expect(getLocalVaultState()).toBe("open");
    openLocalWorkspace();
    const todos = (await api.GET({ url: "/api/todo?timeline=true" })).todos;
    expect(todos).toHaveLength(1);
    expect(todos[0].title).toBe("Renew the passport");
  });

  it("wraps a legacy plaintext workspace when the migration prompt is declined", async () => {
    resetWorkspaceCache();
    window.localStorage.setItem(
      LOCAL_WORKSPACE_STORAGE_KEY,
      JSON.stringify({
        schemaVersion: 1,
        todos: [
          {
            id: "t1",
            title: "Pick up the dry cleaning",
            description: null,
            pinned: false,
            priority: "Low",
            due: "2026-08-04T09:30:00.000",
            rrule: null,
            timeZone: null,
            completed: false,
            order: 0,
            listID: null,
            createdAt: "2026-08-01T09:00:00.000",
            updatedAt: "2026-08-01T09:00:00.000",
            exdates: [],
          },
        ],
        preferences: { sortBy: null, groupBy: null, direction: null, aiSummaryEnabled: true },
      }),
    );
    expect(getLocalVaultState()).toBe("legacy");

    keepLegacyWorkspaceOpen();
    expect(getLocalVaultState()).toBe("unlocked");
    expect(getLocalProtection()).toBe("none");

    // Persisted as an open document, so the prompt does not return on reload.
    await flushWorkspaceWrites();
    resetWorkspaceCache();
    expect(getLocalVaultState()).toBe("open");
    openLocalWorkspace();
    const todos = (await api.GET({ url: "/api/todo?timeline=true" })).todos;
    expect(todos).toHaveLength(1);
    expect(todos[0].title).toBe("Pick up the dry cleaning");
  });
});
