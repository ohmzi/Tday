# ADR 004: Local AI Summaries via Ollama

**Status:** Accepted  
**Date:** 2024

## Context

T'Day offers backend task summaries with optional AI enhancement. Options for the AI backend:

1. **Cloud API** (OpenAI, Anthropic) — high quality, pay-per-token, requires internet.
2. **Self-hosted Ollama** — local inference, privacy-preserving, no per-token cost.
3. **No AI** — use deterministic backend summary logic only.

## Decision

- Use **Ollama** as an optional local AI runtime through the Docker Compose `ai` profile.
- Default model: `qwen3.5:0.8b` (small footprint, fast inference for task summaries).
- Keep Summary available without Ollama by falling back to deterministic backend logic.
- Feature is globally toggleable via admin settings (`AppConfig.aiSummaryEnabled`).
- Mobile clients read the server setting in Server Mode. Local Mode should not imply an Ollama dependency unless a future on-device/local integration is explicitly designed.

## Rationale

- **Privacy**: Task data never leaves the user's infrastructure. Critical for a personal task planner.
- **Cost**: No API fees. The small model runs on modest hardware (CPU-only is viable; GPU accelerates it).
- **Simplicity**: Ollama is a single binary/container with a simple HTTP API.
- **Quality**: Task summarization is a low-complexity NLP task, and the logic fallback keeps the feature predictable when AI is unavailable.

## Consequences

- **Positive**: Zero ongoing cost, full data privacy, and graceful behavior without a running AI service.
- **Negative**: Lower quality than frontier models for complex summarization. GPU recommended for acceptable latency.
- **Trade-off**: First-time AI setup requires pulling the model (`ollama pull`). Docker Compose handles the service lifecycle when the `ai` profile is enabled.
