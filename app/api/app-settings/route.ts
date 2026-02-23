import { NextResponse } from "next/server";
import { auth } from "@/app/auth";
import { UnauthorizedError } from "@/lib/customError";
import { errorHandler } from "@/lib/errorHandler";
import { getGlobalAppConfig } from "@/lib/appConfig";

export async function GET() {
  try {
    const session = await auth();
    const user = session?.user;
    if (!user?.id) {
      throw new UnauthorizedError("you must be logged in to do this");
    }

    const config = await getGlobalAppConfig();

    return NextResponse.json(
      {
        aiSummaryEnabled: config.aiSummaryEnabled,
      },
      { status: 200 },
    );
  } catch (error) {
    return errorHandler(error);
  }
}
