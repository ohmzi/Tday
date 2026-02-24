import { createHmac } from "crypto";
import { hashPassword, parsePasswordHash } from "@/lib/security/password";
import {
  buildPasswordProofMessage,
  consumePasswordProofChallenge,
  issuePasswordProofChallenge,
  resetPasswordProofChallengesForTests,
  verifyPasswordProofChallenge,
} from "@/lib/security/passwordProof";

describe("password proof challenge", () => {
  beforeEach(() => {
    resetPasswordProofChallengesForTests();
  });

  test("verifies a valid challenge proof and rejects replay", () => {
    const email = "proof.user@example.com";
    const storedPasswordHash = hashPassword("TopSecret#123");
    const parsed = parsePasswordHash(storedPasswordHash);
    expect(parsed).not.toBeNull();

    const challenge = issuePasswordProofChallenge({
      email,
      storedPasswordHash,
    });
    const proofHex = createProofHex({
      keyHex: parsed?.hashHex as string,
      challengeId: challenge.challengeId,
      email: email.toLowerCase(),
    });

    const firstAttempt = verifyPasswordProofChallenge({
      email,
      challengeId: challenge.challengeId,
      proofHex,
      proofVersion: challenge.version,
      storedPasswordHash,
    });
    expect(firstAttempt).toBe(true);

    const replayAttempt = verifyPasswordProofChallenge({
      email,
      challengeId: challenge.challengeId,
      proofHex,
      proofVersion: challenge.version,
      storedPasswordHash,
    });
    expect(replayAttempt).toBe(false);
  });

  test("fails when password hash changes after challenge issuance", () => {
    const email = "proof.change@example.com";
    const oldHash = hashPassword("OldPassword#1");
    const challenge = issuePasswordProofChallenge({
      email,
      storedPasswordHash: oldHash,
    });

    const oldMeta = parsePasswordHash(oldHash);
    const proofHex = createProofHex({
      keyHex: oldMeta?.hashHex as string,
      challengeId: challenge.challengeId,
      email: email.toLowerCase(),
    });

    const rotatedHash = hashPassword("NewPassword#2");
    const verified = verifyPasswordProofChallenge({
      email,
      challengeId: challenge.challengeId,
      proofHex,
      proofVersion: challenge.version,
      storedPasswordHash: rotatedHash,
    });
    expect(verified).toBe(false);
  });

  test("explicitly consumed challenge cannot be used", () => {
    const email = "proof.consume@example.com";
    const storedPasswordHash = hashPassword("ConsumeMe#9");
    const challenge = issuePasswordProofChallenge({
      email,
      storedPasswordHash,
    });

    consumePasswordProofChallenge(challenge.challengeId);

    const parsed = parsePasswordHash(storedPasswordHash);
    const proofHex = createProofHex({
      keyHex: parsed?.hashHex as string,
      challengeId: challenge.challengeId,
      email: email.toLowerCase(),
    });

    const verified = verifyPasswordProofChallenge({
      email,
      challengeId: challenge.challengeId,
      proofHex,
      proofVersion: challenge.version,
      storedPasswordHash,
    });
    expect(verified).toBe(false);
  });
});

function createProofHex(params: {
  keyHex: string;
  challengeId: string;
  email: string;
}): string {
  return createHmac("sha256", Buffer.from(params.keyHex, "hex"))
    .update(buildPasswordProofMessage(params.challengeId, params.email))
    .digest("hex");
}
