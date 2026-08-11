import { describe, expect, it } from "vitest";
import {
  deriveVaultKey,
  envelopeSalt,
  isLegacyPlaintextWorkspace,
  isOpenWorkspaceDocument,
  isVaultEnvelope,
  LocalVaultError,
  LOCAL_OPEN_VERSION,
  LOCAL_VAULT_ITERATIONS,
  LOCAL_VAULT_VERSION,
  openVault,
  openWorkspaceDocumentJson,
  randomVaultSalt,
  sealVault,
} from "@/lib/local/localCrypto";

/**
 * The crypto module is pure, so it is tested straight rather than through the
 * api client: these four properties are what stop a stolen browser profile from
 * reading the workspace.
 */

const PASSPHRASE = "correct horse battery staple";
const DOCUMENT = JSON.stringify({
  schemaVersion: 1,
  todos: [{ id: "t1", title: "Call the clinic about the results" }],
});

describe("local vault crypto", () => {
  it("round-trips a workspace document through a passphrase", async () => {
    const salt = randomVaultSalt();
    const key = await deriveVaultKey(PASSPHRASE, salt);

    const envelope = await sealVault(key, salt, DOCUMENT);
    expect(envelope.version).toBe(LOCAL_VAULT_VERSION);
    expect(isVaultEnvelope(envelope)).toBe(true);
    // Nothing but the four persisted fields, and no plaintext among them.
    expect(Object.keys(envelope).sort()).toEqual([
      "ciphertext",
      "iv",
      "salt",
      "version",
    ]);
    expect(JSON.stringify(envelope)).not.toContain("clinic");

    // A reload re-derives from the stored salt alone.
    const reopened = await deriveVaultKey(PASSPHRASE, envelopeSalt(envelope));
    expect(await openVault(reopened, envelope)).toBe(DOCUMENT);
  });

  it("rejects a wrong passphrase cleanly instead of throwing raw WebCrypto noise", async () => {
    const salt = randomVaultSalt();
    const envelope = await sealVault(
      await deriveVaultKey(PASSPHRASE, salt),
      salt,
      DOCUMENT,
    );

    const wrongKey = await deriveVaultKey("correct horse battery stapl", salt);
    const failure = await openVault(wrongKey, envelope).catch((error) => error);

    expect(failure).toBeInstanceOf(LocalVaultError);
    expect((failure as LocalVaultError).code).toBe("wrong-passphrase");
    expect((failure as LocalVaultError).message).toMatch(/passphrase/i);
  });

  it("mints a fresh IV for every write under the same key", async () => {
    const salt = randomVaultSalt();
    const key = await deriveVaultKey(PASSPHRASE, salt);

    const first = await sealVault(key, salt, DOCUMENT);
    const second = await sealVault(key, salt, DOCUMENT);

    // Reusing an IV under one AES-GCM key would leak the XOR of the two writes.
    expect(second.iv).not.toBe(first.iv);
    expect(second.ciphertext).not.toBe(first.ciphertext);
    // The salt is per workspace, so it stays put across writes.
    expect(second.salt).toBe(first.salt);
    expect(await openVault(key, second)).toBe(DOCUMENT);
  });

  it("tells a legacy plaintext workspace apart from an envelope", async () => {
    const legacy = { schemaVersion: 1, todos: [], lists: [] };
    expect(isLegacyPlaintextWorkspace(legacy)).toBe(true);
    expect(isVaultEnvelope(legacy)).toBe(false);

    const salt = randomVaultSalt();
    const envelope = await sealVault(
      await deriveVaultKey(PASSPHRASE, salt),
      salt,
      DOCUMENT,
    );
    expect(isLegacyPlaintextWorkspace(envelope)).toBe(false);
    expect(isVaultEnvelope(envelope)).toBe(true);

    // Neither: nothing stored, or a blob from some other app.
    expect(isLegacyPlaintextWorkspace(null)).toBe(false);
    expect(isLegacyPlaintextWorkspace("{}")).toBe(false);
    expect(isLegacyPlaintextWorkspace({ hello: "world" })).toBe(false);
    // A future envelope version is not readable by this build.
    expect(isVaultEnvelope({ ...envelope, version: LOCAL_VAULT_VERSION + 1 })).toBe(
      false,
    );
  });

  it("tells an unencrypted-by-choice workspace apart from a legacy plaintext one", async () => {
    const rows = { schemaVersion: 1, todos: [], lists: [] };
    const open = JSON.parse(openWorkspaceDocumentJson(JSON.stringify(rows)));

    // The whole point of the wrapper: an open document must never be read as a
    // legacy one, or the migration prompt fires on every load forever.
    expect(isOpenWorkspaceDocument(open)).toBe(true);
    expect(isLegacyPlaintextWorkspace(open)).toBe(false);
    expect(isVaultEnvelope(open)).toBe(false);
    expect(open.workspace).toEqual(rows);

    // And the other two shapes are not open documents.
    expect(isOpenWorkspaceDocument(rows)).toBe(false);
    const salt = randomVaultSalt();
    const envelope = await sealVault(
      await deriveVaultKey(PASSPHRASE, salt),
      salt,
      DOCUMENT,
    );
    expect(isOpenWorkspaceDocument(envelope)).toBe(false);

    expect(isOpenWorkspaceDocument(null)).toBe(false);
    expect(isOpenWorkspaceDocument({ protection: "none" })).toBe(false);
    // A future wrapper is not readable by this build, as with the envelope.
    expect(
      isOpenWorkspaceDocument({ ...open, version: LOCAL_OPEN_VERSION + 1 }),
    ).toBe(false);
  });

  it("stretches the passphrase at the backend's PBKDF2 policy", () => {
    expect(LOCAL_VAULT_ITERATIONS).toBeGreaterThanOrEqual(310_000);
  });
});
