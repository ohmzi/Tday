import NextAuth from "next-auth";
import { CredentialsSignin } from "next-auth";
import Credentials from "next-auth/providers/credentials";
import { PrismaAdapter } from "@auth/prisma-adapter";
import { prisma } from "@/lib/prisma/client";
import type { Adapter } from "next-auth/adapters";
import { hashPassword, verifyPassword } from "@/lib/security/password";
import { getSecretValue } from "@/lib/security/secretSource";
import {
  revokeUserSessions,
  sessionMaxAgeSeconds,
} from "@/lib/security/sessionControl";
import { runSecurityConfigChecks } from "@/lib/security/configHealth";

class PendingApprovalError extends CredentialsSignin {
  code = "pending_approval";
}

const authSecret = getSecretValue({
  envVar: "AUTH_SECRET",
  fileEnvVar: "AUTH_SECRET_FILE",
});
const maxSessionAgeSeconds = sessionMaxAgeSeconds();

export const { handlers, auth } = NextAuth({
  adapter: PrismaAdapter(prisma) as Adapter,
  secret: authSecret,
  trustHost: process.env.AUTH_TRUST_HOST === "true",
  session: {
    strategy: "jwt",
    maxAge: maxSessionAgeSeconds,
  },
  jwt: {
    maxAge: maxSessionAgeSeconds,
  },
  providers: [
    Credentials({
      credentials: {
        email: {},
        password: {},
      },
      authorize: async (credentials) => {
        runSecurityConfigChecks();

        const email = normalizeCredentialField(credentials?.email);
        const password = normalizeCredentialField(credentials?.password);
        if (!email || !password) {
          return null;
        }

        const user = await prisma.user.findUnique({
          where: { email: email },
        });

        if (!user || !user.password) {
          return null;
        }

        try {
          const verification = verifyPassword(password, user.password);
          if (!verification.valid) {
            // Keep login errors generic to avoid account enumeration.
            return null;
          }

          if (verification.needsRehash) {
            await prisma.user.update({
              where: { id: user.id },
              data: { password: hashPassword(password) },
            });
          }

          if (user.approvalStatus !== "APPROVED") {
            throw new PendingApprovalError();
          }

          return user;
        } catch (error) {
          if (error instanceof PendingApprovalError) {
            throw error;
          }
          // Keep credential failures generic for clients while avoiding
          // misclassifying them as server configuration errors.
          return null;
        }
      },
    }),
  ],
  events: {
    async signOut(message) {
      const userId =
        "token" in message && typeof message.token?.id === "string"
          ? message.token.id
          : null;
      if (!userId) return;

      try {
        await revokeUserSessions(userId);
      } catch (error) {
        console.warn(
          `[security] revoke_sessions_on_signout_failed userId=${userId} error=${String(
            error,
          )}`,
        );
      }
    },
  },
  callbacks: {
    async jwt({ token, user, account }) {
      if (user) {
        if (!user.id) {
          throw new Error("Authentication failed.");
        }
        token.id = user.id;
        token.timeZone = user.timeZone;
        token.role = user.role;
        token.approvalStatus = user.approvalStatus;
        token.tokenVersion = user.tokenVersion;
        token.revoked = false;
        if (account?.provider === "credentials") {
          token.name = user.name;
        }
      }

      if (!token.id) {
        token.revoked = true;
        return token;
      }

      const latestUser = await prisma.user.findUnique({
        where: { id: token.id },
        select: {
          id: true,
          timeZone: true,
          role: true,
          approvalStatus: true,
          tokenVersion: true,
        },
      });

      if (!latestUser) {
        token.revoked = true;
        return token;
      }

      if ((token.tokenVersion ?? -1) !== latestUser.tokenVersion) {
        token.revoked = true;
        return token;
      }

      token.revoked = false;
      token.tokenVersion = latestUser.tokenVersion;
      token.timeZone = latestUser.timeZone;
      token.role = latestUser.role;
      token.approvalStatus = latestUser.approvalStatus;
      return token;
    },
    session({ session, token }) {
      if (
        token.revoked ||
        !token.id ||
        !token.role ||
        !token.approvalStatus ||
        typeof token.tokenVersion !== "number"
      ) {
        throw new Error("Invalid session.");
      }

      session.user.id = token.id;
      session.user.timeZone = token.timeZone ?? null;
      session.user.role = token.role;
      session.user.approvalStatus = token.approvalStatus;
      session.user.tokenVersion = token.tokenVersion;
      return session;
    },
  },
});

function normalizeCredentialField(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const normalized = value.trim();
  return normalized.length > 0 ? normalized : null;
}
