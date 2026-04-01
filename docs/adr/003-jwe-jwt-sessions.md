# ADR 003: Custom JWE Sessions with Token Versioning

**Status:** Accepted (supersedes original NextAuth decision)
**Date:** 2025

## Context

The application needs user authentication with support for:
- Email/password credentials.
- Session persistence across browser restarts and mobile app relaunches.
- Server-side session revocation.
- Compatibility with native mobile clients using cookies.

The original architecture used NextAuth v5 (Auth.js) with JWT session strategy. When the backend migrated to Kotlin/Ktor, a custom auth implementation was needed since NextAuth is a Node.js library.

Options considered:
1. **Custom JWE implementation** — full control, Kotlin-native, uses Nimbus JOSE JWT.
2. **Spring Security** — mature but brings significant framework overhead for a Ktor app.
3. **Ktor's built-in JWT plugin** — lightweight but limited (no encryption, minimal session management).

## Decision

- Implement custom **encrypted JWT (JWE) sessions** using **Nimbus JOSE JWT** + **BouncyCastle** for key derivation.
- Store user and account data in PostgreSQL via Exposed (no separate session table).
- Implement **token versioning** (`tokenVersion` on User model) for server-side revocation.
- Support **credential envelope encryption** for enhanced credential transit security (RSA).
- Use authjs-compatible cookie names (`__Secure-authjs.session-token` in production, `authjs.session-token` otherwise) for browser and mobile session delivery.

## Rationale

- JWE (encrypted JWT) provides both integrity and confidentiality — session contents are opaque to clients.
- Token versioning provides server-enforced revocation: incrementing `tokenVersion` on password change or sign-out invalidates all existing tokens on the next validation.
- The Ktor pipeline intercept validates every request by checking `tokenVersion`, expiry, role, and approval status against the database — providing near-real-time enforcement.
- Credential envelope encryption (optional RSA) adds defense-in-depth for credential transit.
- Cookie-based session delivery works for both the web SPA and mobile clients (which implement the CSRF + callback flow).
- Using Nimbus JOSE JWT + BouncyCastle gives full control over token format, encryption algorithm, and key management.

## Consequences

- **Positive**: Full control over auth, encrypted tokens, server-enforced revocation, mobile-compatible, no external auth service dependency.
- **Negative**: More code to maintain than using an auth library. JWT tokens cannot be instantly revoked — revocation requires a database check on the next request (acceptable latency).
- **Mitigation**: Sessions use a 30-day rolling inactivity window with a 90-day absolute cap, which keeps browser relaunches ergonomic while still bounding stale-token exposure. Comprehensive security tests cover all auth paths.
