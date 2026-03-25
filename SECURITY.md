# Security Policy

## Reporting Vulnerabilities

If you discover a security vulnerability, please report it privately. Do **not** open a public issue.

**Contact:** Open a private security advisory via GitHub's "Security" tab, or email the repository owner directly.

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

- Sessions use **JWT** strategy via NextAuth v5.
- Session lifetime is configurable (`AUTH_SESSION_MAX_AGE_SEC`, default 24h).
- Sessions persist across browser/app restarts (no session-only cookies).
- Server-enforced **token versioning** revokes all sessions on password change or sign-out.
- Mobile sessions use encrypted cookie storage with session restoration on app launch.

### Account Approval

- New accounts default to `PENDING` approval status.
- Only `APPROVED` users can access protected API routes and app pages.
- Admin approval is required before new users gain access.

### Rate Limiting and Lockout

- Auth endpoints are rate-limited per IP and per email (configurable windows and thresholds).
- Exponential lockout activates after repeated credential failures (`AUTH_LOCKOUT_*` settings).
- **Adaptive CAPTCHA** (Cloudflare Turnstile) triggers after configurable failure count.
- Rate limit and lockout state is stored in the database (`AuthThrottle` model).

### Admin Access

- Admin routes require both `APPROVED` status and `ADMIN` role.
- Admin checks are centralized in `lib/auth/requireAdmin.ts`.

## API Security

### Middleware Enforcement

All API requests pass through Next.js edge middleware that enforces:

- **Authentication**: JWT validation required for all routes except `/api/auth/*` and `/api/mobile/probe`.
- **Approval gate**: Unapproved users receive 403 on private routes.
- **Cache prevention**: Private API responses include `Cache-Control: no-store`.

### Security Headers

Every response includes:

| Header | Value |
|--------|-------|
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |
| `Cross-Origin-Resource-Policy` | `same-origin` |
| `Cross-Origin-Opener-Policy` | `same-origin` |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=()` |
| `Content-Security-Policy` | Restrictive policy with `frame-ancestors 'none'` |
| `Strict-Transport-Security` | `max-age=63072000; includeSubDomains; preload` (production only) |

### HTTPS Enforcement

- Production middleware optionally enforces HTTPS via 308 redirects (`AUTH_ENFORCE_HTTPS_REDIRECT`).
- Protocol detection respects `cf-visitor`, `x-forwarded-proto`, and `x-forwarded-host` for reverse proxy setups.

## Data Protection

### Field Encryption at Rest

- Sensitive database fields are encrypted/decrypted transparently via a Prisma client extension.
- Uses AES-256-GCM with configurable key ID and AAD (`DATA_ENCRYPTION_*` variables).
- Key rotation is supported: keep previous keys in `DATA_ENCRYPTION_KEYS` during rollover.

### Mobile Client Security

- Server URL and credentials stored in Android `EncryptedSharedPreferences`.
- Session cookies persist in an encrypted cookie store.
- Optional public-key fingerprint pinning for self-hosted servers.
- All local user data (credentials, cache, cookies) is wiped on logout or session invalidation.
- Custom client headers (`X-Tday-Client`, `X-Tday-App-Version`, `X-Tday-Device-Id`) for audit trails.
- OkHttp logging redacts `Cookie` and `Set-Cookie` headers.

## Secrets Management

- Production secrets should come from a secrets manager or mounted files.
- Docker entrypoint supports `*_FILE` suffix for all sensitive variables:
  `AUTH_SECRET`, `CRONJOB_SECRET`, `DATABASE_URL`, `AUTH_CAPTCHA_SECRET`, `DATA_ENCRYPTION_KEY`, `DATA_ENCRYPTION_KEYS`, `DATA_ENCRYPTION_AAD`.
- Never commit real secrets. `.env.example` and `.env.docker` contain placeholder values only.
- Rotate secrets on a fixed schedule (recommended: 60-90 days).

See [`docs/security/operations-hardening.md`](docs/security/operations-hardening.md) for the full operations checklist and [`docs/security/cloudflare-auth-hardening.md`](docs/security/cloudflare-auth-hardening.md) for edge-layer rules.

## Dependency Security

- Keep dependencies updated regularly.
- Review `npm audit` output before merging dependency updates.
- Pin major versions in `package.json` to avoid surprise breaking changes.
- Android dependencies are version-locked in `build.gradle.kts`.

## Security Event Monitoring

Structured security event codes are emitted to the `eventLog` table:

| Code | Meaning |
|------|---------|
| `auth_limit_ip` | IP-based rate limit triggered |
| `auth_limit_email` | Email-based rate limit triggered |
| `auth_lockout` | Account locked out after repeated failures |
| `auth_captcha_failed` | CAPTCHA verification failed |
| `register_captcha_failed` | Registration CAPTCHA failed |
| `auth_alert_ip_concentration` | Suspicious IP concentration detected |
| `auth_alert_lockout_burst` | Burst of lockouts in a short window |
| `auth_signal_anomaly` | Behavioral anomaly detected |
| `probe_failed_contract` | Mobile probe contract validation failed |

Alert when `auth_lockout` grows rapidly or `auth_limit_ip` spikes from a narrow IP range.
