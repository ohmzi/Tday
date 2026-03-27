# ADR 001: Ktor Backend with Vite SPA and Native Mobile Clients

**Status:** Accepted (supersedes original Next.js decision)
**Date:** 2025

## Context

T'Day needs a web interface, REST API, real-time WebSocket support, and mobile clients. The original architecture used a Next.js App Router monolith with Prisma ORM. As the project matured, the backend was migrated to Kotlin/Ktor for several reasons:

1. **Kotlin Multiplatform sharing** — a Kotlin backend enables sharing DTOs, enums, and validators with the Android client through a KMP `shared` module.
2. **Stronger server-side type safety** — Kotlin's null safety, sealed types, and coroutines provide a more robust server environment.
3. **Decoupled frontend** — separating the SPA from the backend simplifies deployment (one fat JAR + static files) and allows independent frontend iteration.

## Decision

- Use **Ktor 3 (Netty)** as the backend API server, with **Koin** for DI and **Exposed** for database access.
- Use **Vite + React 18 + TypeScript** as a standalone SPA (`tday-web/`), served as static files by the Ktor backend in production.
- Use a **Kotlin Multiplatform `shared` module** for DTOs, enums, and validators consumed by the backend, Android, and iOS.
- Build **native mobile clients**: Android (Kotlin + Jetpack Compose) and iOS (SwiftUI) that consume the same REST API.
- Package everything into a **single Docker image** via a multi-stage build (Node → Gradle → JRE).

## Rationale

- **Single deployment unit** — one container runs the JVM backend and serves the SPA static files. Simple infrastructure.
- **KMP code sharing** — shared types eliminate model drift between backend and clients.
- **Vite SPA** — fast HMR in development, simple static build for production. React Query handles server state efficiently.
- **Native mobile** — better UX, offline support, and push notifications compared to a WebView wrapper.
- **Ktor** — lightweight, coroutine-native, and well-suited for the project's scale.

## Consequences

- **Positive**: Faster development with shared types, single-container deployment, independently deployable mobile clients, strong type safety end-to-end.
- **Negative**: Scaling the API independently of the frontend requires refactoring. The API surface must remain stable for mobile clients.
- **Accepted risk**: If traffic grows significantly, the backend may need horizontal scaling. Acceptable for a personal/small-team product.
