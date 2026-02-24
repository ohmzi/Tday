import {
  decryptTextValue,
  encryptObjectFields,
  encryptTextValue,
  resetFieldEncryptionCacheForTests,
} from "@/lib/security/fieldEncryption";

describe("field encryption helpers", () => {
  const originalKey = process.env.DATA_ENCRYPTION_KEY;
  const originalKeyId = process.env.DATA_ENCRYPTION_KEY_ID;
  const originalKeys = process.env.DATA_ENCRYPTION_KEYS;

  beforeEach(() => {
    process.env.DATA_ENCRYPTION_KEY_ID = "primary";
    process.env.DATA_ENCRYPTION_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    delete process.env.DATA_ENCRYPTION_KEYS;
    resetFieldEncryptionCacheForTests();
  });

  afterAll(() => {
    if (originalKey === undefined) {
      delete process.env.DATA_ENCRYPTION_KEY;
    } else {
      process.env.DATA_ENCRYPTION_KEY = originalKey;
    }

    if (originalKeyId === undefined) {
      delete process.env.DATA_ENCRYPTION_KEY_ID;
    } else {
      process.env.DATA_ENCRYPTION_KEY_ID = originalKeyId;
    }

    if (originalKeys === undefined) {
      delete process.env.DATA_ENCRYPTION_KEYS;
    } else {
      process.env.DATA_ENCRYPTION_KEYS = originalKeys;
    }

    resetFieldEncryptionCacheForTests();
  });

  test("encrypts and decrypts text payloads", async () => {
    const cipher = await encryptTextValue("hello world");
    expect(cipher.startsWith("enc:v1:primary:")).toBe(true);
    await expect(decryptTextValue(cipher)).resolves.toBe("hello world");
  });

  test("encrypts only sensitive fields in object payloads", async () => {
    const payload = {
      title: "Task A",
      description: "sensitive todo detail",
      nested: {
        content: "sensitive note body",
        untouched: "plain",
      },
    };

    const encrypted = await encryptObjectFields(payload);
    expect(encrypted.title).toBe(payload.title);
    expect(encrypted.description.startsWith("enc:v1:")).toBe(true);
    expect(encrypted.nested.content.startsWith("enc:v1:")).toBe(true);
    expect(encrypted.nested.untouched).toBe("plain");
  });

  test("can decrypt values encrypted with an older key from key ring", async () => {
    const oldCipher = await encryptTextValue("legacy data");

    process.env.DATA_ENCRYPTION_KEY_ID = "new";
    process.env.DATA_ENCRYPTION_KEY = "MDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDA=";
    process.env.DATA_ENCRYPTION_KEYS =
      "primary:MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=,new:MDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDA=";
    resetFieldEncryptionCacheForTests();

    await expect(decryptTextValue(oldCipher)).resolves.toBe("legacy data");
  });
});
