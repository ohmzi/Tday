import { getSecretValue } from "@/lib/security/secretSource";

const ENC_PREFIX = "enc:v1";
const IV_LENGTH_BYTES = 12;
const KEY_LENGTH_BYTES = 32;
const DEFAULT_KEY_ID = "primary";
const SENSITIVE_TEXT_FIELDS = new Set([
  "description",
  "content",
  "overriddenDescription",
]);

type KeyMaterial = {
  keyId: string;
  keyBytes: Uint8Array;
  cryptoKey: CryptoKey;
};

type KeyringCache = {
  keyring: Map<string, Uint8Array>;
  activeKeyId: string | null;
};

let cachedKeyring: KeyringCache | null = null;
let cryptoKeyCache: Map<string, CryptoKey> = new Map();

export function resetFieldEncryptionCacheForTests(): void {
  cachedKeyring = null;
  cryptoKeyCache = new Map();
}

export function encryptionConfigured(): boolean {
  return Boolean(activeKeyId());
}

export async function encryptObjectFields<T>(value: T): Promise<T> {
  if (!encryptionConfigured()) return value;
  return transformSensitiveFields(value, encryptTextValue);
}

export async function decryptObjectFields<T>(value: T): Promise<T> {
  if (value == null) return value;
  return transformSensitiveFields(value, decryptTextValue);
}

export async function encryptTextValue(raw: string): Promise<string> {
  if (!raw) return raw;
  if (isEncryptedPayload(raw)) return raw;

  const key = await activeKeyMaterial();
  if (!key) return raw;

  const iv = randomIv();
  const additionalData = encryptionAadBytes();
  const plaintext = textEncoder().encode(raw);
  const encrypted = await webCrypto().subtle.encrypt(
    {
      name: "AES-GCM",
      iv,
      additionalData,
      tagLength: 128,
    },
    key.cryptoKey,
    plaintext,
  );

  return [
    ENC_PREFIX,
    key.keyId,
    toBase64Url(iv),
    toBase64Url(new Uint8Array(encrypted)),
  ].join(":");
}

export async function decryptTextValue(raw: string): Promise<string> {
  if (!raw) return raw;
  if (!isEncryptedPayload(raw)) return raw;

  const parts = raw.split(":");
  if (parts.length !== 5) {
    throw new Error("Malformed encrypted payload");
  }
  const [prefix, version, keyId, ivRaw, cipherRaw] = parts;
  if (prefix !== "enc" || version !== "v1") {
    throw new Error("Unsupported encrypted payload");
  }

  const key = await keyMaterialById(keyId);
  if (!key) {
    throw new Error(
      `Missing field encryption key for key id "${keyId}". Configure DATA_ENCRYPTION_KEYS.`,
    );
  }

  const decrypted = await webCrypto().subtle.decrypt(
    {
      name: "AES-GCM",
      iv: fromBase64Url(ivRaw),
      additionalData: encryptionAadBytes(),
      tagLength: 128,
    },
    key.cryptoKey,
    fromBase64Url(cipherRaw),
  );

  return textDecoder().decode(decrypted);
}

async function transformSensitiveFields<T>(
  value: T,
  transform: (raw: string) => Promise<string>,
): Promise<T> {
  if (value == null) return value;

  if (Array.isArray(value)) {
    const transformed = await Promise.all(
      value.map((item) => transformSensitiveFields(item, transform)),
    );
    return transformed as unknown as T;
  }

  if (typeof value !== "object") {
    return value;
  }

  const record = value as Record<string, unknown>;
  const next: Record<string, unknown> = {};

  for (const [key, entry] of Object.entries(record)) {
    if (SENSITIVE_TEXT_FIELDS.has(key) && typeof entry === "string") {
      next[key] = await transform(entry);
      continue;
    }
    next[key] = await transformSensitiveFields(entry, transform);
  }

  return next as T;
}

function isEncryptedPayload(value: string): boolean {
  return value.startsWith(`${ENC_PREFIX}:`);
}

function parseKeyring(): KeyringCache {
  if (cachedKeyring) return cachedKeyring;

  const map = new Map<string, Uint8Array>();

  const keySetRaw = getSecretValue({
    envVar: "DATA_ENCRYPTION_KEYS",
    fileEnvVar: "DATA_ENCRYPTION_KEYS_FILE",
  });
  if (keySetRaw) {
    for (const entry of keySetRaw.split(",")) {
      const trimmed = entry.trim();
      if (!trimmed) continue;
      const sep = trimmed.indexOf(":");
      if (sep <= 0 || sep >= trimmed.length - 1) continue;
      const keyId = trimmed.slice(0, sep).trim();
      const keyRaw = trimmed.slice(sep + 1).trim();
      if (!keyId) continue;
      map.set(keyId, parseKeyMaterial(keyRaw));
    }
  }

  const explicitKey = getSecretValue({
    envVar: "DATA_ENCRYPTION_KEY",
    fileEnvVar: "DATA_ENCRYPTION_KEY_FILE",
  });
  const explicitKeyId =
    getSecretValue({
      envVar: "DATA_ENCRYPTION_KEY_ID",
      fileEnvVar: "DATA_ENCRYPTION_KEY_ID_FILE",
    }) ?? DEFAULT_KEY_ID;
  if (explicitKey) {
    map.set(explicitKeyId, parseKeyMaterial(explicitKey));
  }

  const activeKeyId = map.has(explicitKeyId)
    ? explicitKeyId
    : map.keys().next().value ?? null;

  cachedKeyring = {
    keyring: map,
    activeKeyId,
  };
  return cachedKeyring;
}

function activeKeyId(): string | null {
  return parseKeyring().activeKeyId;
}

async function activeKeyMaterial(): Promise<KeyMaterial | null> {
  const keyId = activeKeyId();
  if (!keyId) return null;
  return keyMaterialById(keyId);
}

async function keyMaterialById(keyId: string): Promise<KeyMaterial | null> {
  const bytes = parseKeyring().keyring.get(keyId);
  if (!bytes) return null;
  const cached = cryptoKeyCache.get(keyId);
  if (cached) {
    return {
      keyId,
      keyBytes: bytes,
      cryptoKey: cached,
    };
  }

  const cryptoKey = await webCrypto().subtle.importKey(
    "raw",
    bytes,
    { name: "AES-GCM" },
    false,
    ["encrypt", "decrypt"],
  );
  cryptoKeyCache.set(keyId, cryptoKey);
  return {
    keyId,
    keyBytes: bytes,
    cryptoKey,
  };
}

function parseKeyMaterial(raw: string): Uint8Array {
  const normalized = raw.trim();
  if (!normalized) {
    throw new Error("Field encryption key cannot be empty.");
  }

  const asBase64 = fromBase64(normalized);
  if (asBase64.length === KEY_LENGTH_BYTES) {
    return asBase64;
  }

  if (/^[0-9a-f]{64}$/i.test(normalized)) {
    const asHex = hexToBytes(normalized);
    if (asHex.length === KEY_LENGTH_BYTES) {
      return asHex;
    }
  }

  throw new Error(
    "Invalid field encryption key. Expected 32-byte base64 or 64-char hex.",
  );
}

function randomIv(): Uint8Array {
  const iv = new Uint8Array(IV_LENGTH_BYTES);
  webCrypto().getRandomValues(iv);
  return iv;
}

function encryptionAadBytes(): Uint8Array | undefined {
  const aadRaw = process.env.DATA_ENCRYPTION_AAD?.trim();
  if (!aadRaw) return undefined;
  return textEncoder().encode(aadRaw);
}

function webCrypto(): Crypto {
  if (!globalThis.crypto?.subtle) {
    throw new Error("Web Crypto API is not available.");
  }
  return globalThis.crypto;
}

function textEncoder(): TextEncoder {
  return new TextEncoder();
}

function textDecoder(): TextDecoder {
  return new TextDecoder();
}

function toBase64Url(bytes: Uint8Array): string {
  if (typeof Buffer !== "undefined") {
    return Buffer.from(bytes).toString("base64url");
  }
  let binary = "";
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function fromBase64Url(value: string): Uint8Array {
  if (typeof Buffer !== "undefined") {
    return new Uint8Array(Buffer.from(value, "base64url"));
  }
  const base64 = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = base64 + "=".repeat((4 - (base64.length % 4)) % 4);
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

function fromBase64(value: string): Uint8Array {
  if (typeof Buffer !== "undefined") {
    return new Uint8Array(Buffer.from(value, "base64"));
  }
  const binary = atob(value);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

function hexToBytes(value: string): Uint8Array {
  const bytes = new Uint8Array(value.length / 2);
  for (let i = 0; i < bytes.length; i++) {
    bytes[i] = Number.parseInt(value.slice(i * 2, i * 2 + 2), 16);
  }
  return bytes;
}
