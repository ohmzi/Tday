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
- **Adaptive CAPTCHA** (Cloudflare Turnstile) triggers after configurable failure count and now fails closed if required but `AUTH_CAPTCHA_SECRET` is missing.
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

- Server URL and credentials stored in Android `EncryptedSharedPreferences`.
- Session cookies persist in an encrypted cookie store.
- Optional public-key fingerprint pinning for self-hosted servers.
- All local user data (credentials, cache, cookies) is wiped on logout or session invalidation.
- Custom client headers (`X-Tday-Client`, `X-Tday-App-Version`, `X-Tday-Device-Id`) for audit trails.
- OkHttp logging redacts `Cookie` and `Set-Cookie` headers.

## Secrets Management

- Production secrets should come from a secrets manager or mounted files.
- The Ktor backend (`AppConfig.kt`) supports `_FILE` suffix for all sensitive variables:
  `AUTH_SECRET`, `DATABASE_URL`, `AUTH_CAPTCHA_SECRET`, `DATA_ENCRYPTION_KEY`, `DATA_ENCRYPTION_KEYS`, `DATA_ENCRYPTION_AAD`.
- Never commit real secrets. `.env.example` and `.env.docker` contain placeholder values only.
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
| `auth_captcha_failed` | CAPTCHA verification failed |
| `register_captcha_failed` | Registration CAPTCHA failed |
| `auth_alert_ip_concentration` | Suspicious IP concentration detected |
| `auth_alert_lockout_burst` | Burst of lockouts in a short window |
| `auth_signal_anomaly` | Behavioral anomaly detected |
| `auth_captcha_misconfigured` | CAPTCHA should have been enforced but the server secret was missing |
| `request_rate_limit_triggered` | App-layer request throttle triggered |

Alert when `auth_lockout` grows rapidly, `auth_limit_ip` spikes from a narrow IP range, or `request_rate_limit_triggered` starts clustering on one path.
