# ADR 003: NextAuth v5 with JWT Session Strategy

**Status:** Accepted  
**Date:** 2024

## Context

The application needs user authentication with support for:
- Email/password credentials.
- Session persistence across browser restarts and mobile app relaunches.
- Server-side session revocation.
- Compatibility with a native mobile client using cookies.

Options considered:
1. **NextAuth with database sessions** — sessions stored in PostgreSQL via PrismaAdapter.
2. **NextAuth with JWT sessions** — stateless tokens with server-side validation.
3. **Custom auth** — roll our own JWT implementation.

## Decision

- Use **NextAuth v5 (Auth.js)** with **JWT session strategy**.
- Use **PrismaAdapter** for user and account storage (not for session storage).
- Implement **token versioning** (`tokenVersion` on User model) for server-side revocation.
- Support **credential envelope encryption** for enhanced credential transit security.

## Rationale

- JWT sessions are stateless and scale horizontally without shared session storage.
- Token versioning provides server-enforced revocation: incrementing `tokenVersion` on password change or sign-out invalidates all existing tokens on next validation.
- NextAuth provides battle-tested CSRF protection, callback handling, and cookie management.
- The JWT callback refreshes user data from the database, providing near-real-time role and approval status enforcement.
- The mobile client can participate in the same session flow by implementing the NextAuth CSRF + credential callback sequence.

## Consequences

- **Positive**: Stateless sessions, no session table to manage, built-in CSRF protection, mobile-compatible.
- **Negative**: JWT tokens cannot be instantly revoked — revocation requires a database check on the next request (acceptable latency). Token size is larger than a session ID.
- **Mitigation**: Short session lifetime (24h default) limits the window for stale tokens.
