const CREDENTIAL_KEY_ENDPOINT = "/api/auth/credentials-key";

type CredentialKeyResponse = {
  version: string;
  algorithm: string;
  keyId: string;
  publicKey: string;
};

export type ClientCredentialEnvelope = {
  encryptedPayload: string;
  encryptedKey: string;
  encryptedIv: string;
  credentialKeyId: string;
  credentialEnvelopeVersion: string;
};

export async function createClientCredentialEnvelope(
  email: string,
  password: string,
): Promise<ClientCredentialEnvelope> {
  if (typeof window === "undefined" || !window.crypto?.subtle) {
    throw new Error("Secure credential encryption is unavailable in this browser.");
  }

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
      email: email.trim().toLowerCase(),
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
