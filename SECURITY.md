# Security Policy

For product/data context, see [`docs/PRODUCT_DIRECTION.md`](docs/PRODUCT_DIRECTION.md) and [`docs/DATA_MODEL.md`](docs/DATA_MODEL.md). Security rules apply to Server Mode; Local Mode stays on-device unless a future explicit migration/import flow is designed.

## Current Status

Last reviewed **2026-10-21**. Status means: **In place** — enforced in code and covered by a guardrail or test; **Partial** — enforced with a documented limitation; **To be added** — not implemented yet.

Evidence for every row lives in [Security Posture](docs/security/SECURITY_POSTURE.md) and the control-by-control inventory with `file:line` citations in [SECURITY_CONTROLS.md](docs/security/SECURITY_CONTROLS.md). The "To be added" table is the reference checklist's items this project does not implement yet, plus the verified gaps recorded in that inventory.

| Measure | Status |
|---------|--------|
| Authentication — JWE session in an HttpOnly cookie (`__Secure-` prefix in production); Bearer accepted for API clients | In place |
| Authorization — single `withAuth` gate, server-derived identity, admin role plus approval-status checks | In place |
| Account approval — new accounts start `PENDING` until an admin approves them | In place |
| Password handling — PBKDF2-HMAC-SHA256 at 310,000 iterations, optional RSA/AES credential envelope | In place |
| Session lifecycle — absolute lifetime cap, sliding renewal, `tokenVersion` revocation on password change or sign-out | In place |
| Login abuse controls — per-IP, per-device and per-username throttling with exponential lockout, state in Postgres | In place |
| Rate limiting — global `/api` budget plus route-specific buckets returning 429 with `Retry-After` | In place |
| Input validation — shared KMP validators and typed `@Serializable` DTOs on the request path | In place |
| SQL injection protection — Exposed parameterized queries, ownership predicates compiled into every query | In place |
| XSS protection — React output escaping plus an enforcing Content-Security-Policy | In place |
| CSRF protection — `SameSite=Lax` session cookie and a deny-by-default CORS allowlist | Partial — no CSRF token validation |
| Security headers — CSP, Permissions-Policy, `nosniff`, `X-Frame-Options: DENY`, referrer policy, HSTS in production | In place |
| CORS — deny by default, explicit origin allowlist, no wildcard with credentials | In place |
| Multi-tenant isolation — row ownership scoping, share ACL (OWNER/EDITOR/VIEWER), API-key scopes | In place |
| Realtime authorization — authenticated WebSocket handshake, fanout derived from share membership | In place |
| Secrets management — required-at-boot secrets, `_FILE` indirection for mounted secrets, no dev bypass | In place |
| Data at rest — AES-256-GCM field encryption with key rotation; mobile caches in SQLCipher and Keychain-protected stores | In place |
| Security event log — structured codes with HMAC-hashed identifiers, lockout/IP-concentration/anomaly alerts | In place |
| Dependency vulnerabilities — Dependabot alerts, CodeQL analysis, guardrail tests, SHA-pinned Actions | In place |
| Shell injection protection — no request path executes a shell or spawns a process | In place |
| Supply-chain integrity — release APK signature verified against a pinned certificate digest | In place |
| Backups — verified `pg_dump`, optional encryption, documented restore procedure | In place |
| Mobile at-rest protection — Android SQLCipher cache with a Keystore-wrapped passphrase; iOS Keychain plus Data Protection | In place |
| Screenshot/recents protection — Android `FLAG_SECURE` on task-text activities and an optional biometric app lock | In place |
| Certificate pinning — user-configured public-key pins for self-hosted servers; iOS fails closed and asks before accepting a new key | In place |
| Local Mode isolation — local-only data is never uploaded; the web vault is PBKDF2 + AES-GCM and relocks on reload | In place |
| Telemetry privacy — consent-gated Sentry on every client, allow-listed fields, scrubbed identifiers | In place |
| HTTPS enforcement — host bound to `127.0.0.1` by default; TLS provided by the operator's tunnel or reverse proxy | In place |

### To be added in future

| Measure | Status |
|---------|--------|
| CSRF token validation (defense in depth beyond `SameSite`) | To be added |
| Multi-factor authentication | To be added |
| Session/device inventory and selective revocation | To be added |
| Request body-size limits | To be added |
| Origin check on the WebSocket handshake | To be added |
| Admin action audit trail | To be added |
| CAPTCHA on registration and sign-in | To be added |
| Operator IP-ban / fail2ban integration | To be added |
| Read-only root filesystem, tmpfs, and container memory limits | To be added |
| Image vulnerability scanning, SBOM, provenance and image signing | To be added |
| Digest-pinned deploys and automatic rollback | To be added |
| TLS between backend and Postgres, and network egress restriction | To be added |
| Log retention/rotation, off-host copy and tamper evidence | To be added |
| WAF or edge rate-limit rules; Cloudflare Access or mTLS (optional hardening) | To be added |
| HTTP→HTTPS redirect and forwarded-proto awareness in the app | To be added |
| COOP/COEP/CORP isolation headers and a CSP reporting endpoint | To be added |
| Reverse-proxy hardening guidance — version banner suppression and explicit dotfile deny | To be added |
| Password-strength check beyond the three character-class rules | To be added |
| Server version banner suppression | To be added |
| Strict JSON decoding and length bounds on all free-text fields | To be added |

## Reporting Vulnerabilities

If you discover a security vulnerability, please report it privately. Do **not** open a public issue.

**Contact:** [Report a vulnerability privately](https://github.com/ohmzi/Tday/security/advisories/new) through GitHub's private vulnerability reporting (the repository's "Security" tab), or email the repository owner directly.

Include:
- Description of the vulnerability.
- Steps to reproduce.
- Potential impact.
- Suggested fix (if any).

We aim to acknowledge reports within 48 hours and provide a fix or mitigation plan within 7 days.

## Authentication and Authorization

### Credential Handling

- Passwords are hashed with **PBKDF2** (configurable iterations via `AUTH_PBKDF2_ITERATIONS`, default 310,000).
- Passwords are never logged, returned in API responses, or stored in plaintext.
- Client-side credential envelopes optionally encrypt credentials in transit using an RSA key pair (`AUTH_CREDENTIALS_PRIVATE_KEY`).

### Session Management

- Sessions use **encrypted JWT (JWE)** via **Nimbus JOSE JWT** + **BouncyCastle** key derivation.
- Session lifetime is configurable (`AUTH_SESSION_MAX_AGE_SEC`, default 24h).
- Sessions are delivered in HTTP-only authjs cookies: `__Secure-authjs.session-token` in production and `authjs.session-token` elsewhere. Bearer tokens are also accepted for API clients.
- Sessions persist across browser/app restarts (no session-only cookies).
- Server-enforced **token versioning** (`tokenVersion` on User model) revokes all sessions on password change or sign-out.
- The Ktor pipeline intercept validates tokens on every request by checking `tokenVersion`, expiry, role, and approval status against the database before route handlers read the session.
- Mobile sessions use encrypted cookie storage with session restoration on app launch.

### Account Approval

- New accounts default to `PENDING` approval status.
- Only `APPROVED` users can access protected API routes and app pages.
- Admin approval is required before new users gain access.

### Rate Limiting and Lockout

- Auth endpoints are rate-limited per IP and per email (configurable windows and thresholds).
- App-layer request throttling protects `/api/**`, `/health`, `/api/mobile/probe`, `POST /api/todo/summary`, `POST /api/user/change-password`, and `/ws`.
- Exponential lockout activates after repeated credential failures (`AUTH_LOCKOUT_*` settings).
- Rate limit and lockout state is stored in the database (`AuthThrottle` model).

### Admin Access

- Admin routes require both `APPROVED` status and `ADMIN` role.
- Admin checks are centralized in the shared auth context helper used by routes and services.

## API Security

### Ktor Pipeline Enforcement

All API requests pass through Ktor plugins that enforce:

- **Authentication**: `Security.kt` reads a JWE token from the `Authorization` header or session cookies and attaches validated claims to the call.
- **Approval gate**: `call.withAuth { }` rejects unapproved users from private REST routes, and `/ws` rejects pending users during the handshake.
- **Token refresh**: Claims are verified against the database on every request (role, approval, `tokenVersion`).
- **Request throttling**: `RateLimiting.kt` applies route-specific 429 responses before protected handlers run.

### Security Headers

Every response includes (via `SecurityHeaders.kt`):

| Header | Value |
|--------|-------|
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |
| `Strict-Transport-Security` | `max-age=63072000; includeSubDomains; preload` (production only) |

### HTTPS Enforcement

- The Docker host port is bound to **`127.0.0.1` (localhost only) by default**, preventing direct external HTTP access. Traffic reaches the backend through a tunnel or VPN — see [Remote Access](docs/REMOTE_ACCESS.md) for all supported methods (Cloudflare Tunnel, Tailscale, WireGuard, ZeroTier, SSH tunnels, ngrok, frp).
- Direct external access is opt-in: set `TDAY_HOST_BIND=0.0.0.0` in the project-root `.env` file. This is **discouraged** for production.
- Production deployments should use a method that provides TLS termination (Cloudflare Tunnel, ngrok, Caddy reverse proxy, or Tailscale Funnel).
- VPN-based methods (Tailscale, WireGuard, ZeroTier) encrypt traffic at the tunnel layer; browser-trusted HTTPS is optional but recommended for production cookie security.
- HSTS headers are applied when `TDAY_ENV=production` (`NODE_ENV=production` is still accepted as a compatibility fallback).

## Data Protection

### Field Encryption at Rest

- Sensitive database fields can be encrypted/decrypted transparently at the service layer.
- Uses AES-256-GCM with configurable key ID and AAD (`DATA_ENCRYPTION_*` variables).
- Key rotation is supported: keep previous keys in `DATA_ENCRYPTION_KEYS` during rollover.

### Mobile Client Security

- Android server URL and credentials are stored in `EncryptedSharedPreferences`.
- iOS server config, credentials, cookies, and mode state are stored through Keychain-backed `SecureStore`.
- Session cookies persist in encrypted platform stores.
- Android task data is cached in Room; iOS task data is cached in SwiftData. Both caches are local app data and are cleared through logout/session invalidation flows where appropriate.
- Local Mode does not send task/list/floater data to a server and should not silently migrate local-only data into Server Mode.
- Optional public-key fingerprint pinning for self-hosted servers.
- All local user data (credentials, cache, cookies) is wiped on logout or session invalidation.
- Custom client headers (`X-Tday-Client`, `X-Tday-App-Version`, `X-Tday-Device-Id`) for audit trails.
- OkHttp logging redacts `Cookie` and `Set-Cookie` headers.

## Secrets Management

- Production secrets should come from a secrets manager or mounted files.
- The Ktor backend (`AppConfig.kt`) supports `_FILE` suffix for all sensitive variables:
  `AUTH_SECRET`, `DATABASE_URL`, `DATA_ENCRYPTION_KEY`, `DATA_ENCRYPTION_KEYS`, `DATA_ENCRYPTION_AAD`.
- Never commit real secrets. `.env.example` contains placeholder values only; `.env.docker` is a local ignored runtime file created from that template.
- Rotate secrets on a fixed schedule (recommended: 60-90 days).

## Dependency Security

- Keep dependencies updated regularly.
- Review `npm audit` output before merging dependency updates.
- Pin major versions in `tday-web/package.json` to avoid surprise breaking changes.
- Android and backend dependencies are version-locked in `build.gradle.kts`.

## Security Event Monitoring

Structured security event codes are emitted to the `eventLog` database table:

| Code | Meaning |
|------|---------|
| `auth_limit_ip` | IP-based rate limit triggered |
| `auth_limit_email` | Email-based rate limit triggered |
| `auth_lockout` | Account locked out after repeated failures |
| `auth_credential_envelope_invalid` | Encrypted credential envelope could not be decrypted |
| `auth_alert_ip_concentration` | Suspicious IP concentration detected |
| `auth_alert_lockout_burst` | Burst of lockouts in a short window |
| `auth_signal_anomaly` | Behavioral anomaly detected |
| `auth_session_absolute_expired` | Session exceeded the configured absolute lifetime and was cleared |
| `auth_session_renewed` | Active session was renewed because it crossed the renewal threshold |
| `auth_session_token_version_mismatch` | Session was cleared because the user's token version changed |
| `auth_session_user_missing` | Session referenced a user row that no longer exists |
| `request_rate_limit_triggered` | App-layer request throttle triggered |

The request-rate event includes a `reason` detail such as `api_rate_limit`, `infra_rate_limit`, `summary_rate_limit`, `change_password_rate_limit`, or `websocket_rate_limit`.

Alert when `auth_lockout` grows rapidly, `auth_limit_ip` spikes from a narrow IP range, session-clearing events cluster unexpectedly, or `request_rate_limit_triggered` starts clustering on one path.
