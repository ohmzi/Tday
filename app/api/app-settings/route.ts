import { NextResponse } from "next/server";
import { auth } from "@/app/auth";
import { UnauthorizedError } from "@/lib/customError";
import { errorHandler } from "@/lib/errorHandler";
import { getGlobalAppConfig } from "@/lib/appConfig";
import { apiCache, cacheKey } from "@/lib/cache/memoryCache";
import { apiTimer } from "@/lib/performance/apiTimer";

export async function GET() {
  try {
    const session = await auth();
    const user = session?.user;
    if (!user?.id) {
      throw new UnauthorizedError("you must be logged in to do this");
    }

    const done = apiTimer("GET /api/app-settings");
    const key = cacheKey(user.id, "app-settings");
    const cached = apiCache.get<unknown>(key);
    if (cached) {
      done();
      return NextResponse.json(cached, { status: 200 });
    }

    const config = await getGlobalAppConfig();
    const body = { aiSummaryEnabled: config.aiSummaryEnabled };
    apiCache.set(key, body, 300_000);
    done();

    return NextResponse.json(body, { status: 200 });
  } catch (error) {
    return errorHandler(error);
  }
}
