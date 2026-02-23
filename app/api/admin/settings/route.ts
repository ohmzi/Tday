import { NextRequest, NextResponse } from "next/server";
import { z } from "zod";
import { requireAdmin } from "@/lib/auth/requireAdmin";
import { errorHandler } from "@/lib/errorHandler";
import { getGlobalAppConfig, setGlobalAiSummaryEnabled } from "@/lib/appConfig";
import { BadRequestError } from "@/lib/customError";

const adminSettingsSchema = z.object({
  aiSummaryEnabled: z.boolean(),
});

export async function GET() {
  try {
    await requireAdmin();
    const config = await getGlobalAppConfig();
    return NextResponse.json(
      {
        aiSummaryEnabled: config.aiSummaryEnabled,
        updatedAt: config.updatedAt,
      },
      { status: 200 },
    );
  } catch (error) {
    return errorHandler(error);
  }
}

export async function PATCH(req: NextRequest) {
  try {
    const admin = await requireAdmin();
    const body = await req.json().catch(() => null);
    const parsed = adminSettingsSchema.safeParse(body);
    if (!parsed.success) {
      throw new BadRequestError("Invalid admin settings payload");
    }

    const config = await setGlobalAiSummaryEnabled({
      enabled: parsed.data.aiSummaryEnabled,
      updatedById: admin.id,
    });

    return NextResponse.json(
      {
        aiSummaryEnabled: config.aiSummaryEnabled,
        updatedAt: config.updatedAt,
      },
      { status: 200 },
    );
  } catch (error) {
    return errorHandler(error);
  }
}
