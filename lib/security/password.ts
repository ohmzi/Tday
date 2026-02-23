import { pbkdf2 } from "@noble/hashes/pbkdf2";
import { sha256 } from "@noble/hashes/sha256";
import { bytesToHex, hexToBytes, randomBytes } from "@noble/hashes/utils";

const HASH_ALGORITHM_ID = "pbkdf2_sha256";
const LEGACY_ITERATIONS = 10_000;
const DEFAULT_ITERATIONS = 310_000;
const MIN_ITERATIONS = 100_000;
const MAX_ITERATIONS = 2_000_000;
const SALT_SIZE_BYTES = 16;
const DERIVED_KEY_LENGTH = 32;

type ParsedPasswordHash =
  | {
      format: "modern";
      iterations: number;
      saltHex: string;
      hashHex: string;
    }
  | {
      format: "legacy";
      iterations: number;
      saltHex: string;
      hashHex: string;
    };

export type PasswordVerification =
  | { valid: false; needsRehash: false }
  | { valid: true; needsRehash: boolean };

export function hashPassword(plainTextPassword: string): string {
  const iterations = configuredIterations();
  const salt = randomBytes(SALT_SIZE_BYTES);
  const saltHex = bytesToHex(salt);
  const hashHex = deriveHashHex(plainTextPassword, salt, iterations);
  return `${HASH_ALGORITHM_ID}$${iterations}$${saltHex}$${hashHex}`;
}

export function verifyPassword(
  plainTextPassword: string,
  storedPasswordHash: string,
): PasswordVerification {
  const parsed = parseStoredHash(storedPasswordHash);
  if (!parsed) {
    return { valid: false, needsRehash: false };
  }

  const saltBytes = safeHexToBytes(parsed.saltHex);
  if (!saltBytes) {
    return { valid: false, needsRehash: false };
  }

  const calculatedHashHex = deriveHashHex(
    plainTextPassword,
    saltBytes,
    parsed.iterations,
  );
  const valid = timingSafeHexEquals(calculatedHashHex, parsed.hashHex);
  if (!valid) {
    return { valid: false, needsRehash: false };
  }

  const targetIterations = configuredIterations();
  const needsRehash =
    parsed.format === "legacy" || parsed.iterations < targetIterations;

  return { valid: true, needsRehash };
}

function deriveHashHex(
  plainTextPassword: string,
  salt: Uint8Array,
  iterations: number,
): string {
  const hash = pbkdf2(sha256, plainTextPassword, salt, {
    c: iterations,
    dkLen: DERIVED_KEY_LENGTH,
  });
  return bytesToHex(hash);
}

function parseStoredHash(stored: string): ParsedPasswordHash | null {
  const trimmed = stored.trim();
  if (!trimmed) return null;

  if (trimmed.startsWith(`${HASH_ALGORITHM_ID}$`)) {
    const [algorithm, iterationsRaw, saltHex, hashHex] = trimmed.split("$");
    if (algorithm !== HASH_ALGORITHM_ID) return null;
    if (!iterationsRaw || !saltHex || !hashHex) return null;
    const iterations = Number.parseInt(iterationsRaw, 10);
    if (!Number.isFinite(iterations) || iterations <= 0) return null;
    if (!isHex(saltHex) || !isHex(hashHex)) return null;
    return {
      format: "modern",
      iterations,
      saltHex,
      hashHex,
    };
  }

  if (trimmed.includes(":")) {
    const [saltHex, hashHex] = trimmed.split(":");
    if (!saltHex || !hashHex) return null;
    if (!isHex(saltHex) || !isHex(hashHex)) return null;
    return {
      format: "legacy",
      iterations: LEGACY_ITERATIONS,
      saltHex,
      hashHex,
    };
  }

  return null;
}

function configuredIterations(): number {
  const configured = Number(process.env.AUTH_PBKDF2_ITERATIONS);
  if (!Number.isFinite(configured)) return DEFAULT_ITERATIONS;
  const rounded = Math.floor(configured);
  if (rounded < MIN_ITERATIONS || rounded > MAX_ITERATIONS) {
    return DEFAULT_ITERATIONS;
  }
  return rounded;
}

function safeHexToBytes(value: string): Uint8Array | null {
  try {
    return hexToBytes(value);
  } catch {
    return null;
  }
}

function timingSafeHexEquals(leftHex: string, rightHex: string): boolean {
  const left = safeHexToBytes(leftHex);
  const right = safeHexToBytes(rightHex);
  if (!left || !right) return false;
  if (left.length === 0 || right.length === 0) return false;
  if (left.length !== right.length) return false;

  let mismatch = 0;
  for (let i = 0; i < left.length; i++) {
    mismatch |= left[i] ^ right[i];
  }
  return mismatch === 0;
}

function isHex(value: string): boolean {
  return /^[0-9a-f]+$/i.test(value);
}
