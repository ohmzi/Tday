import { DefaultSession, DefaultUser } from "next-auth";
import { DefaultJWT } from "next-auth/jwt";
import { ApprovalStatus, UserRole } from "@prisma/client";

declare module "next-auth" {
  interface User extends DefaultUser {
    id: string;
    timeZone: string | null;
    role: UserRole;
    approvalStatus: ApprovalStatus;
  }

  interface Session {
    user: {
      id: string;
      timeZone: string | null;
      role: UserRole;
      approvalStatus: ApprovalStatus;
    } & DefaultSession["user"];
  }
}

declare module "next-auth/jwt" {
  interface JWT extends DefaultJWT {
    id?: string;
    timeZone?: string | null;
    role?: UserRole;
    approvalStatus?: ApprovalStatus;
  }
}

