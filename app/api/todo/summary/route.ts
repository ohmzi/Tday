import { auth } from "@/app/auth";
import { UnauthorizedError, BadRequestError, ForbiddenError } from "@/lib/customError";
import { errorHandler } from "@/lib/errorHandler";
import { getGlobalAppConfig } from "@/lib/appConfig";
import { fetchTimelineTodosForUser } from "@/lib/fetchTimelineTodos";
import {
  buildFallbackSummary,
  buildSummaryPrompt,
  filterTodosForSummaryMode,
  TodoSummaryMode,
} from "@/lib/todoSummary";
import { resolveTimezone } from "@/lib/resolveTimeZone";
import { NextRequest, NextResponse } from "next/server";
import { z } from "zod";

const summaryRequestSchema = z.object({
  mode: z.enum(["today", "scheduled", "all", "priority"]),
});

type OllamaGenerateResponse = {
  response?: string;
};

function normalizeAiSummary(raw: string | null | undefined): string | null {
  const cleaned = (raw ?? "").replace(/\r/g, "").trim();
  if (!cleaned) return null;
  if (cleaned.length <= 1200) return cleaned;
  return `${cleaned.slice(0, 1197).trimEnd()}...`;
}

function toPositiveInt(value: string | undefined, fallback: number): number {
  if (!value) return fallback;
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed <= 0) return fallback;
  return Math.floor(parsed);
}

async function requestSummaryFromOllama({
  prompt,
  timeoutMs,
}: {
  prompt: string;
  timeoutMs: number;
}): Promise<string> {
  const ollamaUrl = process.env.OLLAMA_URL?.trim() || "http://ollama:11434";
  const model = process.env.OLLAMA_MODEL?.trim() || "qwen2.5:0.5b";
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);

  try {
    const response = await fetch(`${ollamaUrl}/api/generate`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model,
        stream: false,
        prompt,
        options: {
          temperature: 0.2,
          num_predict: 220,
        },
      }),
      signal: controller.signal,
    });

    if (!response.ok) {
      throw new Error(`Model request failed (${response.status})`);
    }

    const data = (await response.json()) as OllamaGenerateResponse;
    const summary = normalizeAiSummary(data.response);
    if (!summary) {
      throw new Error("Model returned an empty summary");
    }
    return summary;
  } finally {
    clearTimeout(timer);
  }
}

export async function POST(req: NextRequest) {
  try {
    const session = await auth();
    const user = session?.user;

    if (!user?.id) {
      throw new UnauthorizedError("You must be logged in to do this");
    }

    const body = await req.json().catch(() => null);
    const parsed = summaryRequestSchema.safeParse(body);
    if (!parsed.success) {
      throw new BadRequestError("Invalid mode for summary");
    }

    const mode = parsed.data.mode as TodoSummaryMode;
    const appConfig = await getGlobalAppConfig();
    if (!appConfig.aiSummaryEnabled) {
      throw new ForbiddenError("AI summary is disabled by admin");
    }

    const timeZone = await resolveTimezone(user, req);
    const now = new Date();
    const timelineTodos = await fetchTimelineTodosForUser({
      userId: user.id,
      timeZone,
      recurringFutureDays: 365,
      now,
    });
    const filteredTodos = filterTodosForSummaryMode({
      mode,
      todos: timelineTodos,
      timeZone,
      now,
    });

    const prompt = buildSummaryPrompt({
      mode,
      todos: filteredTodos,
      timeZone,
      now,
    });

    const timeoutMs = toPositiveInt(process.env.OLLAMA_TIMEOUT_MS, 15000);
    let aiSummary: string | null = null;
    let fallbackReason: string | null = null;

    try {
      aiSummary = await requestSummaryFromOllama({
        prompt,
        timeoutMs,
      });
    } catch (error) {
      fallbackReason =
        error instanceof Error ? error.message : "Unknown model error";
    }

    const fallbackSummary = buildFallbackSummary({
      mode,
      todos: filteredTodos,
      timeZone,
      now,
    });

    return NextResponse.json(
      {
        summary: aiSummary ?? fallbackSummary,
        source: aiSummary ? "ai" : "fallback",
        mode,
        taskCount: filteredTodos.length,
        generatedAt: now.toISOString(),
        fallbackReason: aiSummary ? null : fallbackReason,
      },
      {
        status: 200,
        headers: {
          "Cache-Control": "no-store",
        },
      },
    );
  } catch (error) {
    return errorHandler(error);
  }
}
