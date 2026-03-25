# ADR 004: Local AI Summaries via Ollama

**Status:** Accepted  
**Date:** 2024

## Context

T'Day offers AI-powered task summaries. Options for the AI backend:

1. **Cloud API** (OpenAI, Anthropic) — high quality, pay-per-token, requires internet.
2. **Self-hosted Ollama** — local inference, privacy-preserving, no per-token cost.
3. **No AI** — skip the feature.

## Decision

- Use **Ollama** as a local AI runtime, deployed as a Docker Compose service alongside the app.
- Default model: `qwen2.5:0.5b` (small footprint, fast inference for task summaries).
- Feature is globally toggleable via admin settings (`AppConfig.aiSummaryEnabled`).

## Rationale

- **Privacy**: Task data never leaves the user's infrastructure. Critical for a personal task planner.
- **Cost**: No API fees. The small model runs on modest hardware (CPU-only is viable; GPU accelerates it).
- **Simplicity**: Ollama is a single binary/container with a simple HTTP API.
- **Quality**: Task summarization is a low-complexity NLP task — a 0.5B parameter model is sufficient.

## Consequences

- **Positive**: Zero ongoing cost, full data privacy, works offline/air-gapped.
- **Negative**: Lower quality than frontier models for complex summarization. GPU recommended for acceptable latency.
- **Trade-off**: First-time setup requires pulling the model (`ollama pull`). Docker Compose handles the service lifecycle.
