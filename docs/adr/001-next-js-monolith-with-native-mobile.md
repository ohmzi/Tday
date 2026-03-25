# ADR 001: Next.js Monolith with Native Mobile Client

**Status:** Accepted  
**Date:** 2024

## Context

T'Day needs a web interface, REST API, and mobile support. The options considered were:

1. **Separate frontend + backend** (e.g., React SPA + Express/Fastify API).
2. **Next.js full-stack monolith** with API Route Handlers serving both SSR pages and a REST API.
3. **Mobile**: Wrap the web app in a WebView vs. build a native client.

## Decision

- Use **Next.js App Router** as a single full-stack application. Pages and API routes coexist in the same deployment.
- Build a **native Android client** (Kotlin + Jetpack Compose) that consumes the same REST API.
- No separate backend service — Prisma ORM connects directly to PostgreSQL from Next.js server functions.

## Rationale

- **Single deployment unit** simplifies infrastructure (one Docker container for the app).
- Next.js SSR provides good SEO for marketing/landing pages while the authenticated app shell uses client-side rendering with React Query.
- API Route Handlers in Next.js are sufficient for the current scale — no need for a dedicated API framework.
- A native Android client provides better UX, offline support, and push notifications compared to a WebView wrapper.
- The mobile client only depends on the REST API contract, keeping it decoupled from the web implementation.

## Consequences

- **Positive**: Faster development velocity with a single codebase for web. Mobile client is independently deployable.
- **Negative**: Scaling the API independently of the frontend requires refactoring into a separate service. The API surface must remain stable for mobile clients.
- **Accepted risk**: If traffic grows significantly, the monolith may need to be split. This is acceptable for a personal/small-team product.
