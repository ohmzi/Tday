// @vitest-environment jsdom
import { createHmac, pbkdf2Sync } from "node:crypto";
import { afterEach, describe, expect, it, vi } from "vitest";

import { createClientCredentialEnvelope } from "@/lib/security/clientCredentialEnvelope";

/**
 * The password-proof fallback (browsers with no `crypto.subtle`) derives its proof with
 * `@noble/hashes`, and the server verifies it against standard PBKDF2/HMAC-SHA-256. Pin the
 * client's answer to Node's own implementation so a hashing-library upgrade cannot change
 * what the backend is asked to accept.
 */

const CHALLENGE = {
  version: "1",
  algorithm: "pbkdf2_sha256+hmac_sha256",
  challengeId: "challenge-123",
  saltHex: "00112233445566778899aabbccddeeff",
  iterations: 1200,
};

describe("password proof fallback", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("matches PBKDF2-SHA256 + HMAC-SHA256 computed by Node crypto", async () => {
    vi.stubGlobal("crypto", {});
    const fetchMock = vi.fn(async () => ({
      ok: true,
      json: async () => CHALLENGE,
    }));
    vi.stubGlobal("fetch", fetchMock);

    const payload = await createClientCredentialEnvelope("  Alice  ", "pa55word!");

    const derivedKey = pbkdf2Sync(
      "pa55word!",
      Buffer.from(CHALLENGE.saltHex, "hex"),
      CHALLENGE.iterations,
      32,
      "sha256",
    );
    const expectedProof = createHmac("sha256", derivedKey)
      .update(`login:${CHALLENGE.challengeId}:alice`)
      .digest("hex");

    expect(payload).toEqual({
      passwordProof: expectedProof,
      passwordProofChallengeId: CHALLENGE.challengeId,
      passwordProofVersion: "1",
    });
  });

  it("refuses a challenge whose salt is not hex", async () => {
    vi.stubGlobal("crypto", {});
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => ({
        ok: true,
        json: async () => ({ ...CHALLENGE, saltHex: "not-hex" }),
      })),
    );

    await expect(createClientCredentialEnvelope("alice", "pa55word!")).rejects.toThrow(
      "Invalid secure sign-in challenge.",
    );
  });
});
