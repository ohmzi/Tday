import { hashPassword, verifyPassword } from "@/lib/security/password";

describe("password security helpers", () => {
  const originalIterations = process.env.AUTH_PBKDF2_ITERATIONS;

  beforeEach(() => {
    process.env.AUTH_PBKDF2_ITERATIONS = "120000";
  });

  afterAll(() => {
    if (originalIterations === undefined) {
      delete process.env.AUTH_PBKDF2_ITERATIONS;
    } else {
      process.env.AUTH_PBKDF2_ITERATIONS = originalIterations;
    }
  });

  it("hashes using the modern pbkdf2 format", () => {
    const hash = hashPassword("Strong!Pass123");

    expect(hash.startsWith("pbkdf2_sha256$")).toBe(true);
    expect(hash.split("$")).toHaveLength(4);
  });

  it("verifies modern hashes without requiring rehash when iterations match", () => {
    const hash = hashPassword("Strong!Pass123");
    const result = verifyPassword("Strong!Pass123", hash);

    expect(result.valid).toBe(true);
    expect(result.needsRehash).toBe(false);
  });

  it("accepts legacy salt:hash values and flags them for rehash", () => {
    const hash =
      "00112233445566778899aabbccddeeff:dde375bcf7482d20564baa867f92279a59a1810a444ce22ee15174b8f27c95c3";
    const result = verifyPassword("hello", hash);

    expect(result.valid).toBe(true);
    expect(result.needsRehash).toBe(true);
  });

  it("rejects invalid passwords", () => {
    const hash = hashPassword("Strong!Pass123");
    const result = verifyPassword("wrong-password", hash);

    expect(result.valid).toBe(false);
    expect(result.needsRehash).toBe(false);
  });
});
