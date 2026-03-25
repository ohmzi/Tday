import { NextRequest, NextResponse } from "next/server";
import { z } from "zod";
import { requireAdmin } from "@/lib/auth/requireAdmin";
import { errorHandler } from "@/lib/errorHandler";
import { getGlobalAppConfig, setGlobalAiSummaryEnabled } from "@/lib/appConfig";
import { BadRequestError } from "@/lib/customError";
import { apiCache } from "@/lib/cache/memoryCache";

const adminSettingsSchema = z.object({
  aiSummaryEnabled: z.boolean(),
});

async function validateOllamaHealth(): Promise<string | null> {
  const ollamaUrl = (process.env.OLLAMA_URL?.trim() || "http://ollama:11434");
  const model = process.env.OLLAMA_MODEL?.trim() || "qwen2.5:0.5b";
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 8000);

  try {
    const tagsRes = await fetch(`${ollamaUrl}/api/tags`, { signal: controller.signal });
    if (!tagsRes.ok) {
      return `Ollama is not reachable (HTTP ${tagsRes.status})`;
    }
    const tagsData = (await tagsRes.json()) as { models?: { name: string }[] };
    const models = tagsData.models ?? [];
    const hasModel = models.some((m) => m.name === model || m.name.startsWith(`${model}:`));
    if (!hasModel) {
      return `Ollama model "${model}" is not installed. Available: ${models.map((m) => m.name).join(", ") || "none"}`;
    }
    return null;
  } catch (err) {
    if (err instanceof DOMException && err.name === "AbortError") {
      return "Ollama health check timed out";
    }
    return `Cannot reach Ollama at ${ollamaUrl}: ${err instanceof Error ? err.message : "unknown error"}`;
  } finally {
    clearTimeout(timer);
  }
}

export async function GET() {
  try {
    await requireAdmin();
    const config = await getGlobalAppConfig();
    return NextResponse.json(
      {
        aiSummaryEnabled: config.aiSummaryEnabled,
        updatedAt: config.updatedAt instanceof Date ? config.updatedAt.toISOString() : (config.updatedAt ?? null),
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

    const wantsEnabled = parsed.data.aiSummaryEnabled;

    if (wantsEnabled) {
      const validationError = await validateOllamaHealth();
      if (validationError) {
        console.error("ai_summary_validation_failed", validationError);
        const current = await getGlobalAppConfig();
        return NextResponse.json(
          {
            aiSummaryEnabled: current.aiSummaryEnabled,
            validationError,
          },
          { status: 200 },
        );
      }
    }

    const config = await setGlobalAiSummaryEnabled({
      enabled: wantsEnabled,
      updatedById: admin.id,
    });

    // App-settings is global; flush all cached entries for it
    apiCache.clear();

    return NextResponse.json(
      {
        aiSummaryEnabled: config.aiSummaryEnabled,
        updatedAt: config.updatedAt instanceof Date ? config.updatedAt.toISOString() : (config.updatedAt ?? null),
      },
      { status: 200 },
    );
  } catch (error) {
    return errorHandler(error);
  }
}
