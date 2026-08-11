// @vitest-environment jsdom

import React from "react";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import LocalWorkspaceGate from "@/components/local/LocalWorkspaceGate";
import { setAppMode } from "@/lib/local/appMode";
import {
  createLocalVault,
  createOpenLocalWorkspace,
  flushWorkspaceWrites,
  resetWorkspaceCache,
  updateWorkspace,
} from "@/lib/local/localDb";

/**
 * The gate is the thing that actually keeps a stolen browser profile out, so
 * these check the two directions that matter: a sealed workspace never renders
 * the app, and the right passphrase does.
 */

const PASSPHRASE = "a river runs through it";

async function seedEncryptedWorkspace() {
  await createLocalVault(PASSPHRASE);
  updateWorkspace((workspace) => {
    workspace.todos.push({
      id: "t1",
      title: "Call the clinic about the results",
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
    });
  });
  await flushWorkspaceWrites();
  // Close the tab: the key goes, the ciphertext stays.
  resetWorkspaceCache();
}

async function seedOpenWorkspace() {
  createOpenLocalWorkspace();
  await flushWorkspaceWrites();
  // Close the tab: the cache goes, the open document stays on disk.
  resetWorkspaceCache();
}

beforeEach(() => {
  window.localStorage.clear();
  resetWorkspaceCache();
});

afterEach(() => {
  cleanup();
  setAppMode(null);
  resetWorkspaceCache();
  window.localStorage.clear();
});

describe("local workspace gate", () => {
  it("stays out of the way in server mode", () => {
    setAppMode("server");
    render(
      <LocalWorkspaceGate>
        <p>workspace</p>
      </LocalWorkspaceGate>,
    );
    expect(screen.getByText("workspace")).toBeTruthy();
  });

  it("asks for a new passphrase, with the no-recovery warning, on a first run", () => {
    setAppMode("local");
    render(
      <LocalWorkspaceGate>
        <p>workspace</p>
      </LocalWorkspaceGate>,
    );

    expect(screen.queryByText("workspace")).toBeNull();
    expect(screen.getByText(/There is no recovery/i)).toBeTruthy();
    // Both the acknowledgement and a long enough passphrase are required.
    const submit = screen.getByRole("button", { name: "Create workspace" });
    expect((submit as HTMLButtonElement).disabled).toBe(true);
  });

  it("only opens an unencrypted workspace after two deliberate taps", () => {
    setAppMode("local");
    render(
      <LocalWorkspaceGate>
        <p>workspace</p>
      </LocalWorkspaceGate>,
    );

    // The risk isn't shown, and the app isn't open, until the trigger is pressed.
    expect(screen.queryByText(/Anyone who can use this browser profile/i)).toBeNull();
    fireEvent.click(screen.getByText("Skip encryption on this device"));

    expect(screen.getByText(/Anyone who can use this browser profile/i)).toBeTruthy();
    expect(screen.queryByText("workspace")).toBeNull();

    // The first tap only reveals the consequence — the app opens on the second.
    fireEvent.click(screen.getByText("Store unencrypted"));
    expect(screen.getByText("workspace")).toBeTruthy();
  });

  it("opens an unencrypted workspace without asking for anything", async () => {
    await seedOpenWorkspace();
    setAppMode("local");

    render(
      <LocalWorkspaceGate>
        <p>workspace</p>
      </LocalWorkspaceGate>,
    );

    // Adopted in the lazy state initialiser: the app is already there on the
    // very first render, not after an effect resolves.
    expect(screen.getByText("workspace")).toBeTruthy();
  });

  it("keeps the app sealed until the right passphrase is entered", async () => {
    await seedEncryptedWorkspace();
    setAppMode("local");

    render(
      <LocalWorkspaceGate>
        <p>workspace</p>
      </LocalWorkspaceGate>,
    );

    expect(screen.queryByText("workspace")).toBeNull();
    const field = screen.getByLabelText("Passphrase");

    fireEvent.change(field, { target: { value: "not the passphrase" } });
    fireEvent.click(screen.getByRole("button", { name: "Unlock" }));
    await waitFor(() =>
      expect(screen.getByText(/doesn't unlock this workspace/i)).toBeTruthy(),
    );
    expect(screen.queryByText("workspace")).toBeNull();

    fireEvent.change(field, { target: { value: PASSPHRASE } });
    fireEvent.click(screen.getByRole("button", { name: "Unlock" }));
    await waitFor(() => expect(screen.getByText("workspace")).toBeTruthy());
  });

  it("still offers an unencrypted workspace on an origin without crypto.subtle", () => {
    // A self-hosted LAN origin over plain http: `crypto.subtle` doesn't exist,
    // but `getRandomValues` still does — `newLocalId` needs it, and it isn't
    // gated behind a secure context the way `subtle` is.
    const realCrypto = globalThis.crypto;
    Object.defineProperty(globalThis, "crypto", {
      configurable: true,
      value: { getRandomValues: realCrypto.getRandomValues.bind(realCrypto) },
    });

    try {
      setAppMode("local");
      render(
        <LocalWorkspaceGate>
          <p>workspace</p>
        </LocalWorkspaceGate>,
      );

      expect(screen.getByText("Encryption unavailable here")).toBeTruthy();
      expect(screen.queryByText("workspace")).toBeNull();

      fireEvent.click(screen.getByText("Skip encryption on this device"));
      fireEvent.click(screen.getByText("Store unencrypted"));
      expect(screen.getByText("workspace")).toBeTruthy();
    } finally {
      Object.defineProperty(globalThis, "crypto", {
        configurable: true,
        value: realCrypto,
      });
    }
  });
});
