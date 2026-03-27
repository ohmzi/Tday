import { hmac } from "@noble/hashes/hmac";
import { pbkdf2 } from "@noble/hashes/pbkdf2";
import { sha256 } from "@noble/hashes/sha256";
import { bytesToHex, hexToBytes } from "@noble/hashes/utils";

const CREDENTIAL_KEY_ENDPOINT = "/api/auth/credentials-key";
const PASSWORD_PROOF_CHALLENGE_ENDPOINT = "/api/auth/login-challenge";
const PASSWORD_PROOF_VERSION = "1";
const PASSWORD_PROOF_ALGORITHM = "pbkdf2_sha256+hmac_sha256";
const PASSWORD_PROOF_DERIVED_KEY_BYTES = 32;

type CredentialKeyResponse = {
  version: string;
  algorithm: string;
  keyId: string;
  publicKey: string;
};

type PasswordProofChallengeResponse = {
  version: string;
  algorithm: string;
  challengeId: string;
  saltHex: string;
  iterations: number;
};

type ClientCredentialEnvelope = {
  encryptedPayload: string;
  encryptedKey: string;
  encryptedIv: string;
  credentialKeyId: string;
  credentialEnvelopeVersion: string;
};

type ClientPasswordProof = {
  passwordProof: string;
  passwordProofChallengeId: string;
  passwordProofVersion: string;
};

export type ClientCredentialPayload = ClientCredentialEnvelope | ClientPasswordProof;

export async function createClientCredentialEnvelope(
  email: string,
  password: string,
): Promise<ClientCredentialPayload> {
  const normalizedEmail = email.trim().toLowerCase();
  if (!normalizedEmail || !password) {
    throw new Error("Email and password are required.");
  }

  if (typeof window === "undefined") {
    throw new Error("Secure credential encryption is unavailable in this browser.");
  }

  if (!window.crypto?.subtle) {
    return createPasswordProofPayload(normalizedEmail, password);
  }

  return createEnvelopePayload(normalizedEmail, password);
}

async function createEnvelopePayload(
  normalizedEmail: string,
  password: string,
): Promise<ClientCredentialEnvelope> {
  const response = await fetch(CREDENTIAL_KEY_ENDPOINT, {
    method: "GET",
    credentials: "same-origin",
    cache: "no-store",
    headers: {
      Accept: "application/json",
    },
  });
  if (!response.ok) {
    throw new Error("Unable to initialize secure sign-in.");
  }

  const descriptor = (await response.json()) as CredentialKeyResponse;
  if (!descriptor?.publicKey || !descriptor?.keyId || !descriptor?.version) {
    throw new Error("Invalid secure sign-in configuration.");
  }

  const publicKey = await window.crypto.subtle.importKey(
    "spki",
    decodeBase64Url(descriptor.publicKey),
    {
      name: "RSA-OAEP",
      hash: "SHA-256",
    },
    false,
    ["encrypt"],
  );

  const aesKey = await window.crypto.subtle.generateKey(
    {
      name: "AES-GCM",
      length: 256,
    },
    true,
    ["encrypt"],
  );
  const iv = window.crypto.getRandomValues(new Uint8Array(12));
  const plaintext = new TextEncoder().encode(
    JSON.stringify({
      email: normalizedEmail,
      password,
    }),
  );

  const encryptedPayload = new Uint8Array(
    await window.crypto.subtle.encrypt(
      {
        name: "AES-GCM",
        iv,
      },
      aesKey,
      plaintext,
    ),
  );

  const rawAesKey = new Uint8Array(await window.crypto.subtle.exportKey("raw", aesKey));
  const encryptedKey = new Uint8Array(
    await window.crypto.subtle.encrypt(
      {
        name: "RSA-OAEP",
      },
      publicKey,
      rawAesKey,
    ),
  );

  return {
    encryptedPayload: encodeBase64Url(encryptedPayload),
    encryptedKey: encodeBase64Url(encryptedKey),
    encryptedIv: encodeBase64Url(iv),
    credentialKeyId: descriptor.keyId,
    credentialEnvelopeVersion: descriptor.version,
  };
}

async function createPasswordProofPayload(
  normalizedEmail: string,
  password: string,
): Promise<ClientPasswordProof> {
  const response = await fetch(PASSWORD_PROOF_CHALLENGE_ENDPOINT, {
    method: "POST",
    credentials: "same-origin",
    cache: "no-store",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      email: normalizedEmail,
    }),
  });

  const payload = await safeJson(response);
  if (!response.ok) {
    const message = extractMessageFromPayload(payload);
    throw new Error(message);
  }

  if (!isPasswordProofChallengeResponse(payload)) {
    throw new Error("Invalid secure sign-in configuration.");
  }

  const saltBytes = safeHexToBytes(payload.saltHex);
  if (!saltBytes) {
    throw new Error("Invalid secure sign-in challenge.");
  }

  const iterations = Math.floor(payload.iterations);
  if (iterations <= 0) {
    throw new Error("Invalid secure sign-in challenge.");
  }

  const derivedKey = pbkdf2(sha256, password, saltBytes, {
    c: iterations,
    dkLen: PASSWORD_PROOF_DERIVED_KEY_BYTES,
  });
  const proofMessage = new TextEncoder().encode(
    buildPasswordProofMessage(payload.challengeId, normalizedEmail),
  );
  const proofBytes = hmac(sha256, derivedKey, proofMessage);

  return {
    passwordProof: bytesToHex(proofBytes),
    passwordProofChallengeId: payload.challengeId,
    passwordProofVersion: PASSWORD_PROOF_VERSION,
  };
}

function buildPasswordProofMessage(
  challengeId: string,
  normalizedEmail: string,
): string {
  return `login:${challengeId}:${normalizedEmail}`;
}

async function safeJson(response: Response): Promise<unknown> {
  try {
    return await response.json();
  } catch {
    return null;
  }
}

function extractMessageFromPayload(payload: unknown): string {
  if (!payload || typeof payload !== "object") {
    return "Unable to initialize secure sign-in.";
  }

  const message = (payload as { message?: unknown }).message;
  return typeof message === "string" && message.trim().length > 0
    ? message
    : "Unable to initialize secure sign-in.";
}

function isPasswordProofChallengeResponse(
  payload: unknown,
): payload is PasswordProofChallengeResponse {
  if (!payload || typeof payload !== "object") return false;
  const candidate = payload as PasswordProofChallengeResponse;
  return (
    candidate.version === PASSWORD_PROOF_VERSION &&
    candidate.algorithm === PASSWORD_PROOF_ALGORITHM &&
    typeof candidate.challengeId === "string" &&
    typeof candidate.saltHex === "string" &&
    Number.isFinite(candidate.iterations)
  );
}

function safeHexToBytes(value: string): Uint8Array | null {
  try {
    return hexToBytes(value);
  } catch {
    return null;
  }
}

function decodeBase64Url(value: string): ArrayBuffer {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized + "=".repeat((4 - (normalized.length % 4)) % 4);
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
}

function encodeBase64Url(value: Uint8Array): string {
  let binary = "";
  value.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}
