import { NextResponse } from "next/server";
import { logSecurityEvent } from "@/lib/security/logSecurityEvent";

export async function GET() {
  try {
    return NextResponse.json(
      {
        service: "tday",
        probe: "ok",
        version: "1",
        serverTime: new Date().toISOString(),
      },
      {
        status: 200,
        headers: {
          "Cache-Control": "no-store",
        },
      },
    );
  } catch (error) {
    await logSecurityEvent("probe_failed_contract", {
      error: error instanceof Error ? error.message : String(error),
    });

    return NextResponse.json(
      {
        message: "Probe unavailable",
      },
      {
        status: 500,
        headers: {
          "Cache-Control": "no-store",
        },
      },
    );
  }
}
