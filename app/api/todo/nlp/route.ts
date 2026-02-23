import { auth } from "@/app/auth";
import { BadRequestError, UnauthorizedError } from "@/lib/customError";
import { errorHandler } from "@/lib/errorHandler";
import { parseTodoTitle } from "@/lib/todoNlp";
import { NextRequest, NextResponse } from "next/server";
import { z } from "zod";

const nlpRequestSchema = z.object({
  text: z.string().trim().min(1).max(280),
  locale: z.string().trim().min(1).max(24).optional(),
  referenceEpochMs: z.number().finite().int().optional(),
  timezoneOffsetMinutes: z.number().finite().int().optional(),
  defaultDurationMinutes: z.number().finite().int().positive().max(24 * 60).optional(),
});

export async function POST(req: NextRequest) {
  try {
    const session = await auth();
    const user = session?.user;
    if (!user?.id) {
      throw new UnauthorizedError("You must be logged in to do this");
    }

    const body = await req.json().catch(() => null);
    const parsed = nlpRequestSchema.safeParse(body);
    if (!parsed.success) {
      throw new BadRequestError("Invalid NLP parse payload");
    }

    const acceptLanguage = req.headers.get("accept-language");
    const locale = parsed.data.locale?.trim() || acceptLanguage;
    const result = parseTodoTitle({
      text: parsed.data.text,
      locale,
      referenceEpochMs: parsed.data.referenceEpochMs,
      timezoneOffsetMinutes: parsed.data.timezoneOffsetMinutes,
      defaultDurationMinutes: parsed.data.defaultDurationMinutes,
    });

    return NextResponse.json(result, { status: 200 });
  } catch (error) {
    return errorHandler(error);
  }
}
