import NextAuth from "next-auth";
import Credentials from "next-auth/providers/credentials";
import { PrismaAdapter } from "@auth/prisma-adapter";
import { prisma } from "@/lib/prisma/client";
import type { Adapter } from "next-auth/adapters";
import { CredentialsSignin } from "@auth/core/errors";
import { hashPassword, verifyPassword } from "@/lib/security/password";

class PendingApprovalError extends CredentialsSignin {
  code = "pending_approval";
}

export const { handlers, auth } = NextAuth({
  adapter: PrismaAdapter(prisma) as Adapter,
  secret: process.env.AUTH_SECRET,
  trustHost: process.env.AUTH_TRUST_HOST === "true",
  useSecureCookies: process.env.NODE_ENV === "production",
  session: { strategy: "jwt", maxAge: 60 * 60 * 24 * 7 },
  jwt: { maxAge: 60 * 60 * 24 * 7 },
  providers: [
    Credentials({
      credentials: {
        email: {},
        password: {},
      },
      authorize: async (credentials) => {
        const { email, password } = credentials as {
          email: string;
          password: string;
        };

        const user = await prisma.user.findUnique({
          where: { email: email },
        });

        if (!user || !user.password) {
          throw new Error("Invalid credentials.");
        }

        try {
          const verification = verifyPassword(password, user.password);
          if (!verification.valid) {
            // Keep login errors generic to avoid account enumeration.
            throw new Error("Invalid credentials.");
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
          throw new Error("Authentication failed.");
        }
      },
    }),
  ],
  callbacks: {
    jwt({ token, user, account }) {
      if (user) {
        if (!user.id) {
          throw new Error("Authentication failed.");
        }
        token.id = user.id;
        token.timeZone = user.timeZone;
        token.role = user.role;
        token.approvalStatus = user.approvalStatus;
        if (account?.provider === "credentials") {
          token.name = user.name;
        }
      }
      return token;
    },
    session({ session, token }) {
      if (!token.id || !token.role || !token.approvalStatus) {
        throw new Error("Invalid session.");
      }

      session.user.id = token.id;
      session.user.timeZone = token.timeZone ?? null;
      session.user.role = token.role;
      session.user.approvalStatus = token.approvalStatus;
      return session;
    },
  },
});
