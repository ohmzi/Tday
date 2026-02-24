import { createHmac, randomBytes, timingSafeEqual } from "crypto";
import {
  configuredPasswordIterations,
  parsePasswordHash,
} from "@/lib/security/password";

const PASSWORD_PROOF_VERSION = "1";
const PASSWORD_PROOF_ALGORITHM = "pbkdf2_sha256+hmac_sha256";
const DEFAULT_CHALLENGE_TTL_SECONDS = 120;
const DEFAULT_MAX_CHALLENGES = 5000;
const SALT_BYTES = 16;

type PasswordProofChallengeEntry = {
  email: string;
  saltHex: string;
  iterations: number;
  expiresAtMs: number;
};

const challengeStore = new Map<string, PasswordProofChallengeEntry>();

export type PasswordProofChallengePayload = {
  version: string;
  algorithm: string;
  challengeId: string;
  saltHex: string;
  iterations: number;
  expiresAt: string;
};

export function normalizePasswordProofEmail(
  value: string | null | undefined,
): string | null {
  if (typeof value !== "string") return null;
  const normalized = value.trim().toLowerCase();
  return normalized.length > 0 ? normalized : null;
}

export function issuePasswordProofChallenge(params: {
  email: string;
  storedPasswordHash?: string | null;
}): PasswordProofChallengePayload {
  const normalizedEmail = normalizePasswordProofEmail(params.email);
  if (!normalizedEmail) {
    throw new Error("invalid_challenge_email");
  }

  const parsedHash = parsePasswordHash(params.storedPasswordHash ?? "");
  const iterations = parsedHash?.iterations ?? configuredPasswordIterations();
  const saltHex = parsedHash?.saltHex ?? randomBytes(SALT_BYTES).toString("hex");
  const challengeId = randomBytes(24).toString("base64url");
  const now = Date.now();
  const expiresAtMs = now + challengeTtlMs();

  pruneExpiredChallenges(now);
  evictOldestChallengesIfNeeded();
  challengeStore.set(challengeId, {
    email: normalizedEmail,
    saltHex,
    iterations,
    expiresAtMs,
  });

  return {
    version: PASSWORD_PROOF_VERSION,
    algorithm: PASSWORD_PROOF_ALGORITHM,
    challengeId,
    saltHex,
    iterations,
    expiresAt: new Date(expiresAtMs).toISOString(),
  };
}

export function verifyPasswordProofChallenge(params: {
  email: string;
  challengeId: string;
  proofHex: string;
  proofVersion?: string | null;
  storedPasswordHash: string | null | undefined;
}): boolean {
  const normalizedEmail = normalizePasswordProofEmail(params.email);
  if (!normalizedEmail) return false;

  const challengeId = params.challengeId.trim();
  if (!challengeId) return false;

  const proofVersion = params.proofVersion?.trim();
  if (proofVersion && proofVersion !== PASSWORD_PROOF_VERSION) {
    return false;
  }

  const providedProofHex = normalizeProofHex(params.proofHex);
  if (!providedProofHex) return false;

  const challenge = challengeStore.get(challengeId);
  if (!challenge) return false;
  challengeStore.delete(challengeId);

  if (challenge.expiresAtMs < Date.now()) return false;
  if (challenge.email !== normalizedEmail) return false;

  const parsedHash = parsePasswordHash(params.storedPasswordHash ?? "");
  if (!parsedHash) return false;
  if (
    parsedHash.saltHex !== challenge.saltHex ||
    parsedHash.iterations !== challenge.iterations
  ) {
    return false;
  }

  const expectedProof = createHmac(
    "sha256",
    Buffer.from(parsedHash.hashHex, "hex"),
  )
    .update(buildPasswordProofMessage(challengeId, normalizedEmail))
    .digest();

  const providedProof = Buffer.from(providedProofHex, "hex");
  if (providedProof.length !== expectedProof.length) return false;

  return timingSafeEqual(expectedProof, providedProof);
}

export function consumePasswordProofChallenge(challengeId: string): void {
  const normalized = challengeId.trim();
  if (!normalized) return;
  challengeStore.delete(normalized);
}

export function resetPasswordProofChallengesForTests(): void {
  challengeStore.clear();
}

export function passwordProofVersion(): string {
  return PASSWORD_PROOF_VERSION;
}

export function passwordProofAlgorithm(): string {
  return PASSWORD_PROOF_ALGORITHM;
}

export function buildPasswordProofMessage(
  challengeId: string,
  normalizedEmail: string,
): string {
  return `login:${challengeId}:${normalizedEmail}`;
}

function normalizeProofHex(raw: string): string | null {
  const normalized = raw.trim().toLowerCase();
  if (!/^[0-9a-f]+$/.test(normalized)) {
    return null;
  }
  if (normalized.length % 2 !== 0) {
    return null;
  }
  return normalized;
}

function challengeTtlMs(): number {
  return envSeconds(
    "AUTH_PASSWORD_PROOF_CHALLENGE_TTL_SEC",
    DEFAULT_CHALLENGE_TTL_SECONDS,
  ) * 1000;
}

function pruneExpiredChallenges(nowMs: number): void {
  for (const [challengeId, challenge] of challengeStore.entries()) {
    if (challenge.expiresAtMs <= nowMs) {
      challengeStore.delete(challengeId);
    }
  }
}

function evictOldestChallengesIfNeeded(): void {
  const maxChallenges = envInt(
    "AUTH_PASSWORD_PROOF_MAX_ACTIVE",
    DEFAULT_MAX_CHALLENGES,
  );
  while (challengeStore.size >= maxChallenges) {
    const oldestChallengeId = challengeStore.keys().next().value as
      | string
      | undefined;
    if (!oldestChallengeId) break;
    challengeStore.delete(oldestChallengeId);
  }
}

function envInt(key: string, fallback: number): number {
  const parsed = Number.parseInt(process.env[key] ?? "", 10);
  if (!Number.isFinite(parsed)) return fallback;
  return Math.max(1, parsed);
}

function envSeconds(key: string, fallback: number): number {
  return envInt(key, fallback);
}
