import { NextResponse } from "next/server";
import { getCredentialPublicKeyDescriptor } from "@/lib/security/credentialEnvelope";

export async function GET() {
  return NextResponse.json(getCredentialPublicKeyDescriptor(), {
    status: 200,
    headers: {
      "Cache-Control": "no-store",
    },
  });
}
