import { auth } from "@/app/auth";
import { UnauthorizedError, BadRequestError, ForbiddenError } from "@/lib/customError";
import { errorHandler } from "@/lib/errorHandler";
import { getGlobalAppConfig } from "@/lib/appConfig";
import { fetchTimelineTodosForUser } from "@/lib/fetchTimelineTodos";
import {
  buildFallbackSummary,
  buildSummaryPrompt,
  buildSummaryTaskCandidates,
  filterTodosForSummaryMode,
  SummaryTaskCandidate,
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

type AiStructuredSummary = {
  startId?: unknown;
  thenIds?: unknown;
};

function extractJsonObject(raw: string): string | null {
  const trimmed = raw.trim();
  if (!trimmed) return null;

  const fenced = trimmed.match(/```(?:json)?\s*([\s\S]*?)```/i);
  if (fenced?.[1]) return fenced[1].trim();

  if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
    return trimmed;
  }

  const firstBrace = trimmed.indexOf("{");
  const lastBrace = trimmed.lastIndexOf("}");
  if (firstBrace >= 0 && lastBrace > firstBrace) {
    return trimmed.slice(firstBrace, lastBrace + 1);
  }

  return null;
}

function normalizeTaskId(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const id = value.trim().toUpperCase();
  if (!/^T\d{1,2}$/.test(id)) return null;
  return id;
}

function joinTaskTitles(titles: string[]): string {
  if (titles.length === 0) return "";
  if (titles.length === 1) return titles[0];
  if (titles.length === 2) return `${titles[0]} and ${titles[1]}`;
  return `${titles[0]}, ${titles[1]}, and ${titles[2]}`;
}

function normalizeAiSummary(
  raw: string | null | undefined,
  candidates: SummaryTaskCandidate[],
): string | null {
  const parsedRaw = (raw ?? "").replace(/\r/g, "\n").trim();
  if (!parsedRaw) return null;

  const jsonBlock = extractJsonObject(parsedRaw);
  if (!jsonBlock) return null;

  let parsed: AiStructuredSummary;
  try {
    parsed = JSON.parse(jsonBlock) as AiStructuredSummary;
  } catch {
    return null;
  }

  const titleById = new Map(
    candidates.map((candidate) => [candidate.id.toUpperCase(), candidate.title]),
  );
  const startId = normalizeTaskId(parsed.startId);
  const startTitle = startId ? titleById.get(startId) : null;
  if (!startTitle) return null;

  const thenIds = Array.isArray(parsed.thenIds) ? parsed.thenIds : [];
  const selectedThenTitles = thenIds
    .map((item) => normalizeTaskId(item))
    .filter((id): id is string => id != null)
    .map((id) => titleById.get(id))
    .filter((title): title is string => Boolean(title))
    .filter((title) => title !== startTitle);

  const fallbackThenTitles = candidates
    .map((candidate) => candidate.title)
    .filter((title) => title !== startTitle)
    .slice(0, 3);

  const thenTitles = Array.from(
    new Set(selectedThenTitles.length > 0 ? selectedThenTitles : fallbackThenTitles),
  ).slice(0, 3);

  const lines = [`- Start with ${startTitle}.`];
  if (thenTitles.length > 0) {
    lines.push(`- Then move through ${joinTaskTitles(thenTitles)}.`);
  }
  const summary = lines.join("\n");

  if (summary.length > 320) {
    return null;
  }

  return summary;
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
  candidates,
}: {
  prompt: string;
  timeoutMs: number;
  candidates: SummaryTaskCandidate[];
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
    const summary = normalizeAiSummary(data.response, candidates);
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
    const summaryCandidates = buildSummaryTaskCandidates(filteredTodos);

    const prompt = buildSummaryPrompt({
      mode,
      todos: filteredTodos,
      timeZone,
      now,
    });
    const fallbackSummary = buildFallbackSummary({
      mode,
      todos: filteredTodos,
      timeZone,
      now,
    });
    if (summaryCandidates.length === 0) {
      return NextResponse.json(
        {
          summary: fallbackSummary,
          source: "fallback",
          mode,
          taskCount: filteredTodos.length,
          generatedAt: now.toISOString(),
          fallbackReason: "no_tasks",
        },
        {
          status: 200,
          headers: {
            "Cache-Control": "no-store",
          },
        },
      );
    }

    const timeoutMs = toPositiveInt(process.env.OLLAMA_TIMEOUT_MS, 15000);
    let aiSummary: string | null = null;
    let fallbackReason: string | null = null;

    try {
      aiSummary = await requestSummaryFromOllama({
        prompt,
        timeoutMs,
        candidates: summaryCandidates,
      });
    } catch (error) {
      fallbackReason =
        error instanceof Error ? error.message : "Unknown model error";
    }

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
