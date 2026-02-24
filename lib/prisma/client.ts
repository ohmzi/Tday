import { PrismaClient } from "@prisma/client";
import {
  decryptObjectFields,
  encryptObjectFields,
  encryptionConfigured,
} from "@/lib/security/fieldEncryption";

const globalPrisma = globalThis as unknown as {
  prisma: PrismaClient | undefined;
};

const withSensitiveFieldEncryption = new PrismaClient({
  log:
    process.env.NODE_ENV !== "production"
      ? ["query", "info", "warn", "error"]
      : [],
})
  .$extends({
    query: {
      $allModels: {
        async $allOperations({ operation, args, query }) {
          const shouldEncrypt =
            encryptionConfigured() &&
            [
              "create",
              "createMany",
              "update",
              "updateMany",
              "upsert",
            ].includes(operation);

          const nextArgs = shouldEncrypt
            ? await encryptObjectFields(args)
            : args;
          const result = await query(nextArgs);
          return await decryptObjectFields(result);
        },
      },
    },
  }) as unknown as PrismaClient;

// Enable query logging in development
export const prisma =
  globalPrisma.prisma ??
  withSensitiveFieldEncryption;

if (process.env.NODE_ENV !== "production") globalPrisma.prisma = prisma;
