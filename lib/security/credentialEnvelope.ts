import {
  constants,
  createDecipheriv,
  createHash,
  createPrivateKey,
  createPublicKey,
  generateKeyPairSync,
  privateDecrypt,
  type KeyObject,
} from "crypto";
import { getSecretValue } from "@/lib/security/secretSource";

const ENVELOPE_VERSION = "1";
const ENVELOPE_ALGORITHM = "RSA-OAEP-256+A256GCM";
const AES_KEY_BYTES = 32;
const AES_GCM_IV_BYTES = 12;
const AES_GCM_TAG_BYTES = 16;

type CredentialKeyMaterial = {
  keyId: string;
  privateKey: KeyObject;
  publicKeySpkiDer: Buffer;
};

type DecryptedCredentialPayload = {
  email: string;
  password: string;
};

let cachedKeyMaterial: CredentialKeyMaterial | null = null;
let warnedAboutEphemeralKey = false;

export type CredentialPublicKeyDescriptor = {
  version: string;
  algorithm: string;
  keyId: string;
  publicKey: string;
};

export type CredentialEnvelopeInput = {
  encryptedPayload: string;
  encryptedKey: string;
  encryptedIv: string;
  keyId?: string | null;
  version?: string | null;
};

export function getCredentialPublicKeyDescriptor(): CredentialPublicKeyDescriptor {
  const keyMaterial = getCredentialKeyMaterial();
  return {
    version: ENVELOPE_VERSION,
    algorithm: ENVELOPE_ALGORITHM,
    keyId: keyMaterial.keyId,
    publicKey: encodeBase64Url(keyMaterial.publicKeySpkiDer),
  };
}

export function decryptCredentialEnvelope(
  envelope: CredentialEnvelopeInput,
): DecryptedCredentialPayload {
  const keyMaterial = getCredentialKeyMaterial();

  if (envelope.version && envelope.version !== ENVELOPE_VERSION) {
    throw new Error("unsupported_envelope_version");
  }
  if (envelope.keyId && envelope.keyId !== keyMaterial.keyId) {
    throw new Error("unknown_envelope_key");
  }

  const encryptedPayload = decodeBase64Url(envelope.encryptedPayload);
  const encryptedKey = decodeBase64Url(envelope.encryptedKey);
  const encryptedIv = decodeBase64Url(envelope.encryptedIv);

  if (encryptedIv.length !== AES_GCM_IV_BYTES) {
    throw new Error("invalid_envelope_iv");
  }
  if (encryptedPayload.length <= AES_GCM_TAG_BYTES) {
    throw new Error("invalid_envelope_payload");
  }

  const symmetricKey = privateDecrypt(
    {
      key: keyMaterial.privateKey,
      padding: constants.RSA_PKCS1_OAEP_PADDING,
      oaepHash: "sha256",
    },
    encryptedKey,
  );

  if (symmetricKey.length !== AES_KEY_BYTES) {
    throw new Error("invalid_envelope_key");
  }

  const ciphertext = encryptedPayload.subarray(0, -AES_GCM_TAG_BYTES);
  const authTag = encryptedPayload.subarray(-AES_GCM_TAG_BYTES);

  const decipher = createDecipheriv("aes-256-gcm", symmetricKey, encryptedIv);
  decipher.setAuthTag(authTag);

  const plaintextBuffer = Buffer.concat([
    decipher.update(ciphertext),
    decipher.final(),
  ]);
  const parsed = JSON.parse(plaintextBuffer.toString("utf8")) as {
    email?: unknown;
    password?: unknown;
  };

  const email =
    typeof parsed.email === "string" ? parsed.email.trim().toLowerCase() : "";
  const password = typeof parsed.password === "string" ? parsed.password : "";

  if (!email || !password) {
    throw new Error("invalid_envelope_credentials");
  }

  return { email, password };
}

function getCredentialKeyMaterial(): CredentialKeyMaterial {
  if (cachedKeyMaterial) {
    return cachedKeyMaterial;
  }

  const configuredPrivateKey = getSecretValue({
    envVar: "AUTH_CREDENTIALS_PRIVATE_KEY",
    fileEnvVar: "AUTH_CREDENTIALS_PRIVATE_KEY_FILE",
  });

  if (configuredPrivateKey) {
    const normalizedKey = configuredPrivateKey.replace(/\\n/g, "\n");
    const privateKey = createPrivateKey(normalizedKey);
    const publicKeySpkiDer = createPublicKey(privateKey).export({
      type: "spki",
      format: "der",
    }) as Buffer;
    cachedKeyMaterial = {
      keyId: deriveKeyId(publicKeySpkiDer),
      privateKey,
      publicKeySpkiDer,
    };
    return cachedKeyMaterial;
  }

  const generated = generateKeyPairSync("rsa", {
    modulusLength: 2048,
    privateKeyEncoding: {
      type: "pkcs8",
      format: "pem",
    },
    publicKeyEncoding: {
      type: "spki",
      format: "der",
    },
  });

  const privateKey = createPrivateKey(generated.privateKey);
  const publicKeySpkiDer = Buffer.from(generated.publicKey);

  if (!warnedAboutEphemeralKey) {
    warnedAboutEphemeralKey = true;
    console.warn(
      "[security] auth_credentials_private_key_missing using ephemeral login envelope key; set AUTH_CREDENTIALS_PRIVATE_KEY for stable multi-instance deployments",
    );
  }

  cachedKeyMaterial = {
    keyId: deriveKeyId(publicKeySpkiDer),
    privateKey,
    publicKeySpkiDer,
  };
  return cachedKeyMaterial;
}

function deriveKeyId(publicKeySpkiDer: Buffer): string {
  return createHash("sha256")
    .update(publicKeySpkiDer)
    .digest("base64url")
    .slice(0, 24);
}

function decodeBase64Url(value: string): Buffer {
  return Buffer.from(value, "base64url");
}

function encodeBase64Url(value: Buffer): string {
  return value.toString("base64url");
}
