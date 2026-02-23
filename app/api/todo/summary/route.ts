import { auth } from "@/app/auth";
import { UnauthorizedError, BadRequestError, ForbiddenError } from "@/lib/customError";
import { errorHandler } from "@/lib/errorHandler";
import { getGlobalAppConfig } from "@/lib/appConfig";
import { fetchTimelineTodosForUser } from "@/lib/fetchTimelineTodos";
import {
  buildFallbackSummary,
  buildReadableTaskSummary,
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
  summary?: unknown;
};

function normalizeProseSummary(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const collapsed = value
    .replace(/\r/g, " ")
    .replace(/\n+/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .replace(/^[-*•]\s*/, "")
    .trim();
  if (!collapsed) return null;
  const cleaned = collapsed
    .replace(
      /^handle the most urgent work first(?:,\s*then move to the important items)?\.?\s*/i,
      "",
    )
    .replace(/^start with the most important work first\.?\s*/i, "")
    .replace(/\(\s*due\s+([^)]+)\)/gi, (_match, rawDue: string) => {
      const dueText = String(rawDue).trim();
      const past = /^yesterday\b/i.test(dueText);
      return `, which ${past ? "was" : "is"} due ${dueText}`;
    })
    .replace(/\s+,/g, ",")
    .replace(/\s{2,}/g, " ")
    .trim();
  if (!cleaned) return null;
  if (cleaned.length < 20) return null;
  return cleaned;
}

function includesDayContext(summaryText: string, task: SummaryTaskCandidate): boolean {
  const normalizedSummary = summaryText.toLowerCase();
  const dayTarget = (task.dueDayTarget ?? "").toLowerCase().trim();

  if (dayTarget === "today") {
    return normalizedSummary.includes("today") || normalizedSummary.includes("tonight");
  }
  if (dayTarget === "tomorrow") {
    return normalizedSummary.includes("tomorrow");
  }
  if (dayTarget === "yesterday") {
    return normalizedSummary.includes("yesterday");
  }
  if (dayTarget.startsWith("on ")) {
    const noOrdinal = dayTarget.replace(/(\d+)(st|nd|rd|th)/g, "$1");
    if (normalizedSummary.includes(dayTarget) || normalizedSummary.includes(noOrdinal)) {
      return true;
    }
    const dateMatch = noOrdinal.match(/on\s+(\d+)\s+([a-z]+)/);
    if (dateMatch) {
      const [, dayNumber, monthToken] = dateMatch;
      if (
        normalizedSummary.includes(`${dayNumber} ${monthToken}`) ||
        normalizedSummary.includes(`${monthToken} ${dayNumber}`)
      ) {
        return true;
      }
    }
  }

  return false;
}

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

function normalizeAiSummary(
  raw: string | null | undefined,
  candidates: SummaryTaskCandidate[],
  mode: TodoSummaryMode,
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

  const candidateById = new Map(
    candidates.map((candidate) => [candidate.id.toUpperCase(), candidate]),
  );
  const startId = normalizeTaskId(parsed.startId);
  const defaultStartTask = candidates[0];
  if (!defaultStartTask) return null;
  let startTask = startId ? candidateById.get(startId) : null;
  if (!startTask) {
    startTask = defaultStartTask;
  }
  if (mode !== "priority" && startTask.id !== defaultStartTask.id) {
    // For non-priority views, keep recommendations chronological by due urgency.
    startTask = defaultStartTask;
  }

  const fallbackThenIds = candidates
    .filter((candidate) => candidate.id !== startTask.id)
    .map((candidate) => candidate.id)

  const fallbackThenTasks = fallbackThenIds
    .map((id) => candidateById.get(id))
    .filter((candidate): candidate is SummaryTaskCandidate => Boolean(candidate));
  const coverageTasks = [startTask, ...fallbackThenTasks];
  const fallbackSummary = buildReadableTaskSummary({
    startTask,
    thenTasks: fallbackThenTasks,
  });
  const aiSummary = normalizeProseSummary(parsed.summary);
  const coverageTitlesMentioned = (() => {
    if (!aiSummary) return 0;
    const normalizedSummary = aiSummary.toLowerCase();
    return coverageTasks.filter((task) => normalizedSummary.includes(task.title.toLowerCase())).length;
  })();
  const uniqueDayTargets = Array.from(
    new Set(coverageTasks.map((task) => task.dueDayTarget).filter((target): target is string => Boolean(target))),
  );
  const hasRequiredDayContext = (() => {
    if (!aiSummary) return false;
    return uniqueDayTargets.every((target) =>
      coverageTasks.some((task) =>
        task.dueDayTarget === target && includesDayContext(aiSummary, task),
      ),
    );
  })();
  const hasElevatedUrgencyTasks = coverageTasks.some((task) => task.priorityLabel !== "low");
  const hasUrgencyContext = (() => {
    if (!hasElevatedUrgencyTasks) return true;
    if (!aiSummary) return false;
    return /\b(urgent|important|soon|first|start|next|later|after)\b/i.test(aiSummary);
  })();
  const avoidsPriorityLabels = (() => {
    if (!aiSummary) return false;
    return !/\bpriority\b/i.test(aiSummary);
  })();
  const summary =
    coverageTitlesMentioned > 0 && hasRequiredDayContext && hasUrgencyContext && avoidsPriorityLabels
      ? aiSummary ?? fallbackSummary
      : fallbackSummary;
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
  mode,
}: {
  prompt: string;
  timeoutMs: number;
  candidates: SummaryTaskCandidate[];
  mode: TodoSummaryMode;
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
          num_predict: 1024,
        },
      }),
      signal: controller.signal,
    });

    if (!response.ok) {
      throw new Error(`Model request failed (${response.status})`);
    }

    const data = (await response.json()) as OllamaGenerateResponse;
    const summary = normalizeAiSummary(data.response, candidates, mode);
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
    const summaryCandidates = buildSummaryTaskCandidates(filteredTodos, {
      now,
      timeZone,
    });

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
        mode,
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
