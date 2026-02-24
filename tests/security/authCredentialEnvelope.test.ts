import {
  constants,
  createCipheriv,
  createPublicKey,
  publicEncrypt,
  randomBytes,
} from "crypto";
import { NextRequest } from "next/server";

const mockHandlersPost = jest.fn(async (request: Request) => {
  const formData = await request.formData();
  return Response.json({
    email: formData.get("email"),
    password: formData.get("password"),
    hasEncryptedPayload: formData.has("encryptedPayload"),
  });
});

const mockHandlersGet = jest.fn(async () => new Response(null, { status: 200 }));
const mockEnforceAuthRateLimit = jest.fn(async () => ({ allowed: true }));

jest.mock("@/app/auth", () => ({
  handlers: {
    GET: (...args: unknown[]) => mockHandlersGet(...args),
    POST: (...args: unknown[]) => mockHandlersPost(...args),
  },
}));

jest.mock("@/lib/security/authThrottle", () => ({
  buildAuthThrottleResponse: jest.fn(() => new Response(null, { status: 429 })),
  clearCredentialFailures: jest.fn(async () => {}),
  enforceAuthRateLimit: (...args: unknown[]) => mockEnforceAuthRateLimit(...args),
  recordCredentialSuccessSignal: jest.fn(async () => {}),
  recordCredentialFailure: jest.fn(async () => {}),
  requiresCaptchaChallenge: jest.fn(async () => false),
}));

jest.mock("@/lib/security/captcha", () => ({
  verifyCaptchaToken: jest.fn(async () => ({ ok: true })),
}));

jest.mock("@/lib/security/logSecurityEvent", () => ({
  logSecurityEvent: jest.fn(async () => {}),
}));

import { POST } from "@/app/api/auth/[...nextauth]/route";
import { GET as getCredentialKey } from "@/app/api/auth/credentials-key/route";

describe("auth credential envelope", () => {
  beforeEach(() => {
    mockHandlersPost.mockClear();
    mockHandlersGet.mockClear();
    mockEnforceAuthRateLimit.mockClear();
  });

  test("credentials-key endpoint returns public envelope key only", async () => {
    const response = await getCredentialKey();
    expect(response.status).toBe(200);
    const payload = (await response.json()) as Record<string, unknown>;
    expect(payload.version).toBe("1");
    expect(payload.algorithm).toBe("RSA-OAEP-256+A256GCM");
    expect(typeof payload.keyId).toBe("string");
    expect(typeof payload.publicKey).toBe("string");
    expect(payload).not.toHaveProperty("privateKey");
  });

  test("decrypts encrypted credentials before forwarding to auth handler", async () => {
    const keyResponse = await getCredentialKey();
    const keyPayload = (await keyResponse.json()) as {
      version: string;
      keyId: string;
      publicKey: string;
    };

    const expectedEmail = "secure.user@example.com";
    const expectedPassword = "super-secret-password";
    const envelope = encryptCredentialEnvelope({
      publicKey: keyPayload.publicKey,
      email: expectedEmail,
      password: expectedPassword,
    });

    const form = new URLSearchParams({
      csrfToken: "csrf-token",
      redirect: "false",
      callbackUrl: "https://tday.ohmz.cloud/app/tday",
      encryptedPayload: envelope.encryptedPayload,
      encryptedKey: envelope.encryptedKey,
      encryptedIv: envelope.encryptedIv,
      credentialKeyId: keyPayload.keyId,
      credentialEnvelopeVersion: keyPayload.version,
    });

    const request = new NextRequest(
      "http://localhost:3000/api/auth/callback/credentials",
      {
        method: "POST",
        headers: {
          "content-type": "application/x-www-form-urlencoded",
        },
        body: form.toString(),
      },
    );

    const response = await POST(request);
    expect(response.status).toBe(200);
    expect(mockHandlersPost).toHaveBeenCalledTimes(1);

    const body = (await response.json()) as {
      email: string | null;
      password: string | null;
      hasEncryptedPayload: boolean;
    };
    expect(body.email).toBe(expectedEmail);
    expect(body.password).toBe(expectedPassword);
    expect(body.hasEncryptedPayload).toBe(false);

    expect(mockEnforceAuthRateLimit).toHaveBeenCalled();
    const credentialsRateLimitCall = mockEnforceAuthRateLimit.mock.calls.find(
      (call) => call[0]?.action === "credentials",
    );
    expect(credentialsRateLimitCall?.[0]?.identifier).toBe(expectedEmail);
  });
});

function encryptCredentialEnvelope(params: {
  publicKey: string;
  email: string;
  password: string;
}): {
  encryptedPayload: string;
  encryptedKey: string;
  encryptedIv: string;
} {
  const publicKeyDer = Buffer.from(params.publicKey, "base64url");
  const publicKey = createPublicKey({
    key: publicKeyDer,
    format: "der",
    type: "spki",
  });

  const aesKey = randomBytes(32);
  const iv = randomBytes(12);
  const plaintext = Buffer.from(
    JSON.stringify({
      email: params.email,
      password: params.password,
    }),
    "utf8",
  );

  const cipher = createCipheriv("aes-256-gcm", aesKey, iv);
  const encryptedPayload = Buffer.concat([
    cipher.update(plaintext),
    cipher.final(),
    cipher.getAuthTag(),
  ]);

  const encryptedKey = publicEncrypt(
    {
      key: publicKey,
      padding: constants.RSA_PKCS1_OAEP_PADDING,
      oaepHash: "sha256",
    },
    aesKey,
  );

  return {
    encryptedPayload: encryptedPayload.toString("base64url"),
    encryptedKey: encryptedKey.toString("base64url"),
    encryptedIv: iv.toString("base64url"),
  };
}
