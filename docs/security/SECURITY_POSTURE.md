# T'Day — Security Posture

*Inventory of implemented security controls, written so it can be compared control-for-control
against other self-hosted services.*

## What this is

This documents the security controls that actually exist in T'Day — a self-hosted personal task app (Kotlin/Ktor backend, React SPA, native Android and iOS clients) — so they can be held up against the owner's other self-hosted services and compared control-for-control. Every claim below was read out of the source at backend version 0.7.0, including the uncommitted hardening in the current working tree. It is an inventory, not a vulnerability report: where a control is absent, partial, or off by default, that is stated plainly, because a document that overclaims is useless for comparison.

## Deployment status — read this first

This document describes **the code**. Part of it is **not yet running on the live server**.

Measured directly against `https://tday.ohmz.cloud` while writing:

```
strict-transport-security: max-age=63072000; includeSubDomains; preload
x-content-type-options: nosniff
x-frame-options: DENY
referrer-policy: strict-origin-when-cross-origin
cache-control: no-cache, no-store, must-revalidate

Origin: https://evil.example.com  ->  403
GET /health                       ->  {"status":"ok"}
```

**Live today:** HSTS (so the server runs `TDAY_ENV=production`), frame-deny, nosniff,
referrer-policy, strict CORS.

**Written but NOT deployed.** Everything below is in the working tree and takes effect only after
a deploy of the release that carries them — `./scripts/deploy-release.sh` (backend/web) — plus a
store or sideload build (mobile):

*Backend:* Content-Security-Policy and Permissions-Policy · the (IP, account) throttle re-keying and
the non-punitive account ceiling · real 429s at all eight auth rejection sites (previously 500s) ·
the SSRF egress guard · field encryption extended to task titles, and made opt-in via
`REQUIRE_ENCRYPTION_AT_REST` · the retention scheduler (ships in dry-run) · the `purgeUser`
foreign-key fix · calendar-feed revocation on credential change · `pids_limit` on every container.

*Android:* SQLCipher on the offline cache · LAN-only certificate enrollment · `FLAG_SECURE` ·
optional biometric app lock.

*iOS:* fail-closed TLS with LAN-only enrollment · Data Protection + backup exclusion on the SwiftData
store and widget snapshots · optional biometric app lock.

*Web:* passphrase-encrypted Local Mode, with a warned, two-step opt-out to store it unencrypted instead · the salted-digest fix for suggestion dismissals.

Anything marked *recently hardened* below is in this list.

Two standing items, independent of this document:

1. A real `AUTH_SECRET` sits in this repo's **public** git history. Anyone with it can forge a
   session cookie for any user. Verify the deploy host is not still using it and rotate if so.
2. This checkout's root `.env` sets `TDAY_HOST_BIND=0.0.0.0`, which publishes the backend on every
   host interface rather than loopback. `.env` is gitignored so the server keeps its own copy —
   confirm with `docker port tday_backend 8080` (expect `127.0.0.1:2525`).


## Threat model

- **An internet attacker who finds the hostname.** Defended. Public access is a Cloudflare Tunnel to a loopback-bound port, so there is nothing below HTTP to attack. Registration is open, but the first user became ADMIN/APPROVED and everyone after lands PENDING and gets no session, no read, and no write until an admin approves. What remains reachable is the auth surface: login, register, and the recovery wizard — all rate-limited, all lockout-guarded, all backed by 310,000-iteration PBKDF2. The realistic outcome of a scanner finding this box is a row in the Users table and a 429.
- **Someone on the same public wifi.** Defended on all three clients. TLS terminates at the Cloudflare edge with a public CA certificate; both mobile clients block cleartext in release builds and now fail closed on any certificate the OS cannot verify, offering enrollment **only** for private/LAN hosts — so a bad certificate on a public hostname is refused outright with no button to accept it. Android additionally ignores user-installed CAs (no `network_security_config` on a modern `targetSdk`), which defeats the install-a-profile-then-intercept attack. The login password is never in a request body (RSA-OAEP + AES-GCM envelope), though registration, password change and recovery send it inside TLS.
- **Someone on the owner's LAN.** Mostly defended by topology, and that is the load-bearing part. Postgres and Ollama publish no host ports at all; only the backend does, and the compose default binds it to 127.0.0.1. **Caveat that must be verified on the deploy host:** this checkout's root `.env` sets `TDAY_HOST_BIND=0.0.0.0`, which exposes the backend on every host interface. If that is the live value, anything on the LAN can reach the API directly and can forge `CF-Connecting-IP` to get a fresh rate-limit bucket per request, because there is no trusted-proxy allowlist.
- **Someone who is already an approved user.** Partially defended. Ownership scoping is enforced in Kotlin on every query and share access flows through one `accessFor` decision point, but there is no database row-level security beneath it, any approved user can enumerate other approved usernames via the share picker, and there is no way to suspend an approved account short of deleting it and all its data.
- **Someone with a shell on the host. Explicitly out of scope.** They have `AUTH_SECRET`, so they can mint sessions offline for any user; if field encryption is enabled at all, `DATA_ENCRYPTION_KEY` sits in the same `.env.docker` as the Postgres volume, so it buys nothing against them; and they have the Docker socket. Field encryption here protects a stolen `pg_dump` or a detached volume, nothing more. Do not read any control below as defending against host compromise.
- **Loss is now defended, but only if you use it.** `scripts/backup-database.sh` + `restore-database.sh` ship with the repo (see [backups.md](backups.md)), with optional encryption and retention pruning. They are not scheduled for you — an unscheduled backup script is not a backup, so wire up the cron and test a restore.

## Posture at a glance

| Area | Status | The short version |
|---|---|---|
| Authentication & sessions | Solid | 310k PBKDF2, encrypted JWE sessions, server-side revocation, absolute 90-day cap. No MFA, no session inventory, one fail-open branch on a null `tokenVersion` claim, and login timing still leaks account existence. |
| Brute-force & rate limiting | Strong | Two independent layers; sign-in lockouts key on (IP, account) and survive restarts in Postgres; real 429 + Retry-After everywhere. No CAPTCHA, no body-size cap, and the counting arithmetic itself has no test coverage. |
| Authorization & multi-tenancy | Solid | One `withAuth` gate on all 82 authenticated handlers, ownership predicate compiled into every query, one central share ACL. No DB-level isolation, API keys cannot be scoped below the account's admin rights, no suspend. |
| Data at rest & crypto | Solid | AES-256-GCM field encryption now covers task titles as well as descriptions, is a tested opt-in (REQUIRE_ENCRYPTION_AT_REST, default false) that boots with a plaintext INFO line rather than crash-looping, and every credential is hash-only with constant-time comparison; this box deliberately runs without a backend key and relies on encrypted backups instead, which leaves task content and all metadata plaintext in Postgres. |
| Network, TLS & headers | Solid | No inbound ports, outbound-only tunnel, enforcing CSP with `script-src 'self'`, CORS empty by default. TLS terminates at Cloudflare (full MITM by design); HSTS and the Secure cookie flag both hang off `TDAY_ENV=production`. |
| Container & runtime | Solid | Backend runs non-root with `cap_drop: ALL`, `no-new-privileges`, `pids_limit: 512`; multi-stage build, no toolchain in the runtime image. Hardening is backend-only, no read-only rootfs, no memory limits, no image scanning, deploy over SSH password. |
| Logging & privacy | Solid | Access log is four fields, query strings are dropped wholesale, IPs and usernames are HMAC'd before storage. Retention scheduler ships in dry-run so nothing is actually pruned, and Docker's own container logs are never rotated. |
| Input validation & injection | Strong | Typed Exposed DSL throughout, exactly one raw SQL statement and it is parameter-bound, no subprocess and no unsafe deserialization, new SSRF egress guard on user-supplied URLs. No request body size limit anywhere. |
| Android client | Strong | offline cache encrypted whole-file with SQLCipher under a Keystore-wrapped key, fail-closed certificate pinning with LAN-only enrollment, FLAG_SECURE on by default, optional biometric app lock (off by default); the DB key is deliberately not auth-bound so a rooted or unlocked-and-running device still reads everything, widget content still renders in the launcher, and reminder titles still ride in notifications and AlarmManager extras. |
| iOS client & widget | Strong | fail-closed TLS with LAN-only certificate enrollment and a never-enrolling widget, Data Protection + backup exclusion on the SwiftData store and widget snapshots, Keychain secrets; but task data at rest is OS-protected (readable after first unlock), not app-encrypted, the biometric lock is opt-in and off by default, and task titles still sit in plaintext UserDefaults in three places (repeat-suggestion dismissals, share queue, Apple Watch mirror). |
| Web SPA | Strong | Local Mode is passphrase-encrypted at rest by default (AES-GCM-256, PBKDF2-SHA256 310k, fresh IV per write, non-extractable memory-only key), the session stays an HttpOnly cookie the JS never touches, and the repeat-suggestion title leak is closed with per-workspace salted HMAC digests; the honest costs are that a lost passphrase is unrecoverable with no rekey path, the vault does nothing against script in an already-unlocked page (no idle auto-lock), Server Mode task data still sits plaintext in the service worker's API cache for up to an hour, and the user can opt a given browser workspace out of encryption entirely — behind a warned, two-step confirmation, but with no technical barrier once taken. |

## The five controls that matter most here

**1. The PENDING approval gate on registration.** Registration is open, but `UserService.register` makes only the first-ever user ADMIN/APPROVED; everyone after is PENDING. That status is re-read from the database on every single request and re-checked at the WebSocket handshake, not merely at login, so a stranger who registers gets a row and nothing else — no session cookie, no read, no write, no realtime channel. On a box whose only public surface is an open signup form, this one control is what turns "anyone on the internet can create an account" into "anyone on the internet can consume six registrations per hour and then stop." Most of the authorization machinery below it never has to face an untrusted user.

**2. Loopback binding plus an outbound-only tunnel.** There is no inbound port, no port-forward, and no public origin IP: `cloudflared` dials out from the host and proxies to `127.0.0.1:2525`. Postgres and Ollama publish nothing at all, which matters because Ollama's HTTP API is unauthenticated. This is also what makes the rest of the network posture defensible — the app trusts `CF-Connecting-IP` with no proxy allowlist, which is safe only while the tunnel is genuinely the sole path in. The corollary is that the deployment's binding value is a security parameter: confirm the deploy host is not running with `TDAY_HOST_BIND=0.0.0.0`.

**3. Persistent (IP, account) lockout on top of a 310,000-iteration KDF.** Failed sign-ins lock a composite `ip|username` bucket, not the bare username, so a stranger who knows the owner's username cannot lock them out of their own server; the account-wide backstop is a self-healing 50-per-15-minute quota rather than a punitive lock. The counters live in Postgres behind `SELECT … FOR UPDATE`, so bouncing the container does not reset an attacker's backoff. Behind it sits PBKDF2-HMAC-SHA256 at 310k iterations with a per-password salt, floored at 100k in code so a bad env var cannot weaken it. Against a single account from one IP that works out to roughly eleven guesses in the first hour and two per hour thereafter.

**4. Revocation that reaches every standing credential.** Sessions are encrypted JWEs carrying a `tokenVersion` claim; bumping the column in SQL invalidates every issued token within the 30-second auth-cache TTL. Password change, admin reset, security-question recovery, and account purge additionally pass `revokeApiKeys=true`, which now also kills the calendar feed token — an unauthenticated, token-in-path URL that previously kept leaking task titles to a third-party calendar service after the owner believed they had cut access. On a self-hosted box where the owner hands out feed URLs and API keys to widgets and scripts, "change my password" meaning "cut everything" is the difference between real revocation and theatre. The one hole to know: a token whose `tokenVersion` claim is null is accepted unconditionally rather than rejected.

**5. An enforcing CSP with `script-src 'self'`, over a session cookie JavaScript cannot read.** The SPA is served by the same Ktor process, and the session cookie is HttpOnly, so no XSS can steal a credential usable from elsewhere. The CSP is enforcing by default — an unset `CSP_MODE` yields a real `Content-Security-Policy`, not report-only — with no `unsafe-inline` and no `unsafe-eval` on scripts, `object-src`/`frame-src`/`frame-ancestors 'none'`, `base-uri` and `form-action 'self'`, and a `connect-src` bounded to self, the WebSocket schemes, two GitHub hosts, and the Sentry ingest origin. Given that the app renders arbitrary user-authored task titles and notes, and has exactly two raw-HTML sinks (one fed only by build-time static files, one that escapes every user segment), this is the backstop that keeps a future third sink from becoming account compromise. `style-src` still needs `'unsafe-inline'` for Radix/sonner/vaul.

## Comparison checklist

Take these to any other self-hosted service. T'Day's own answer follows each.

1. **If registration is open, what does an unapproved account actually get — and is that gate re-checked per request or only at login?**
   **T'Day:** Nothing. First user is ADMIN/APPROVED, all others PENDING; approval status is re-read from the DB on every request and at the WebSocket handshake, so a pending account gets no session, no read, no write.

2. **Is the container's port published to 0.0.0.0 or 127.0.0.1, and what is the only public path in?**
   **T'Day:** Compose default is `127.0.0.1:2525` behind an outbound-only Cloudflare Tunnel; Postgres and Ollama publish no ports at all. Verify the deploy host — this checkout's `.env` overrides the bind to `0.0.0.0`.

3. **What KDF and work factor protect passwords, and is that factor floored in code so a misconfigured env var cannot weaken it?**
   **T'Day:** PBKDF2-HMAC-SHA256, 310,000 iterations, 256-bit output, 16-byte per-password salt, clamped to `[100_000, 2_000_000]`. Rehash-on-login when the cost rises (plaintext-login path only).

4. **Can a stranger who merely knows my username lock me out of my own server?**
   **T'Day:** No. Lockouts key on an (IP, account) composite guarded by an explicit `LOCKABLE_DIMENSIONS` set; the account-wide backstop is a non-punitive 50-per-900s quota, never a lock.

5. **Do brute-force counters and lockouts survive a container restart?**
   **T'Day:** Yes — auth throttle state is Postgres rows read with `FOR UPDATE`. The general per-path request limiter is in-process and does reset on restart.

6. **When the service throttles me, does it return a real 429 with `Retry-After`, or a 500 the client cannot back off from?**
   **T'Day:** Real 429 with `Retry-After` and a `{message, reason, retryAfterSeconds}` body, at all ten auth rejection sites and all five global policies. Recently fixed — these previously threw and returned 500.

7. **Where does the browser keep the session token, and can any XSS read it?**
   **T'Day:** HttpOnly, SameSite=Lax cookie holding an encrypted JWE (A256CBC-HS512, key HKDF'd from `AUTH_SECRET`). The SPA never reads or stores a token; there is no bearer token in localStorage and no `Authorization` header anywhere in the frontend.

8. **Can the server actually kill an already-issued session, and how far does that revocation reach?**
   **T'Day:** Yes, via a `tokenVersion` bump, effective within the 30s auth-cache TTL. Password change, recovery, admin reset, and purge also revoke API keys and the calendar feed token. Hole: a token with a null `tokenVersion` claim is accepted unconditionally.

9. **What actually stops cross-site request forgery — a validated token, an Origin check, or only SameSite?**
   **T'Day:** Only SameSite=Lax plus an empty-by-default CORS allowlist. `GET /api/auth/csrf` mints a token that nothing on the server ever validates; do not score it as CSRF protection.

10. **Is there an enforcing Content-Security-Policy, and does `script-src` allow `unsafe-inline` or `unsafe-eval`?**
    **T'Day:** Enforcing by default (`CSP_MODE` unset → enforce; an unrecognised value also falls back to enforce). `script-src 'self'` with neither directive, unit-tested. `style-src` requires `'unsafe-inline'` for Radix/sonner/vaul runtime style injection.

11. **Is multi-factor authentication available at all?**
    **T'Day:** No. TOTP, WebAuthn, passkeys, email/SMS second factors, and recovery codes are all absent. Password (or an HMAC proof over the stored hash) is the only factor; the security questions are a recovery path, not a second factor.

12. **Exactly which columns are encrypted at rest, where does the key live, and what happens if it is missing?**
    **T'Day:** `title`, `overriddenTitle`, `description`, `content`, `overriddenDescription`, `webhookSecret` — AES-256-GCM, 32-byte key, fresh 12-byte IV per value, 128-bit tag, `enc:v1:<keyId>:<iv>:<ct>` envelope with keyring rotation. **Opt-in and off by default**: with no `DATA_ENCRYPTION_KEY` the server boots and logs that content is plaintext; `REQUIRE_ENCRYPTION_AT_REST=true` turns a missing key into a startup failure. Usernames, list names, push keys and OAuth tokens stay plaintext regardless, and encryption is not retroactive.

13. **When a user hands the service a URL it will call, what blocks private and metadata addresses — and does it follow redirects?**
    **T'Day:** `validateOutboundUrl` blocks non-http(s) schemes, embedded userinfo, bare container hostnames, and IPv4/IPv6 loopback, RFC1918, link-local (incl. 169.254.169.254), CGNAT, ULA, multicast, and IPv4-mapped forms; wired into webhook creation and push subscribe. Webhook dispatch sets `followRedirects = false`. DNS rebinding is explicitly not covered — hostnames are not resolved at validation time.

14. **Does the container run non-root with capabilities dropped, and is that on every service or just the app?**
    **T'Day:** Backend only: non-root `USER tday`, `cap_drop: ALL`, `no-new-privileges:true`, `pids_limit: 512`. Postgres and Ollama get `pids_limit` and nothing else. No read-only root filesystem and no memory limits anywhere (deliberate — a too-low `mem_limit` under `restart: always` OOM-crash-loops the JVM).

15. **Do the rate-limit and audit tables store raw usernames and IPs?**
    **T'Day:** HMAC-SHA256 under `AUTH_SECRET`, domain-prefixed per dimension, so a DB dump yields correlation not identities. Two exceptions: session events store the raw internal user cuid, and the credential-envelope failure path stores a raw exception message.

16. **Do the security and audit tables ever get pruned, and does pruning avoid wiping an active lockout?**
    **T'Day:** A 6-hour scheduler purges eventlog/auththrottle/authsignal/cronlog at 90/30/180/90 days, with a SQL predicate that preserves any row still serving a lockout. It ships with `RETENTION_DRY_RUN=true`, so on a stock deployment nothing is actually deleted. Docker's own container logs have no rotation at all.

17. **Is there an audit trail of who did what — data access, exports, admin actions?**
    **T'Day:** Auth and throttle events only. No record of reads, writes, exports, or imports, and — notably — no record of admin approve, reject, delete, password-reset, or clear-reset actions. `AdminService` does not even take a logger dependency.

18. **Does the mobile client verify TLS itself, and what happens on a certificate it cannot verify?**
    **T'Day:** iOS fails closed: a pure `decideTrust` refuses unknown certs, and enrollment requires a two-phase, one-shot, user-confirmed fingerprint match. Android does system CA validation only; its setup-time fingerprint is enrolled silently and auto-cleared on mismatch, so score Android as unpinned.

19. **Are dependencies installed from a lockfile with integrity hashes, and is the build toolchain itself verified?**
    **T'Day:** The SPA uses `npm ci` against a lockfile with 736 sha512 integrity entries. The JVM side has no `gradle/verification-metadata.xml`, no dependency lock, and no `distributionSha256Sum` on the Gradle wrapper — TLS is the only integrity guarantee for every JVM artifact.

20. **Is there a request body size limit, and is there an off-box backup?**
    **T'Day:** No to both. Nothing caps request body size on any endpoint (the 64 KiB WebSocket frame cap is the only size bound in the pipeline), and the repo contains no backup script, cron, or snapshot mechanism — only prose advice in the deployment doc.

## Known gaps

### Authentication and session

- **No MFA of any kind.** Credential compromise is total account compromise. Verified absent: no TOTP, WebAuthn, passkeys, email/SMS, or recovery codes.
- **No CSRF token validation.** `/api/auth/csrf` mints a 32-byte token and nothing ever checks it. SameSite=Lax plus an empty CORS allowlist is the entire cross-site defence, with no defence in depth behind it. If SameSite were ever loosened, every mutating route would be open.
- **A null `tokenVersion` claim is accepted.** `Security.kt:143` short-circuits to accept before comparing. A session minted without that claim survives every subsequent revocation until its own 30-day expiry. The column has a non-null default so this should not arise on a current DB, but the branch fails open rather than closed and logs nothing.
- **`requirePasswordChange` is advertised and never enforced.** Admin reset sets the flag, the session endpoint reports it, and no server-side code refuses a request because of it — the reader helper `requiresPasswordChange` has zero call sites. An admin-issued temporary password works indefinitely via curl, an old app build, or an API key.
- **Login timing leaks account existence.** Unknown username and blank-hash branches return without doing PBKDF2 work; a wrong password runs the full 310k derivation. The response bodies are byte-identical but the ~100ms difference is trivially measurable. `GET /api/auth/security-questions` discloses existence outright, documented as an accepted tradeoff.
- **No session inventory.** Revocation is all-or-nothing on an integer column. The user cannot see which devices are signed in and cannot sign out one phone without signing out all of them; there is no new-device notification delivery path.
- **`GET /api/timezone` mutates server state.** SameSite=Lax deliberately allows cookies on top-level GET navigations, so a link a logged-in owner follows can flip their stored timezone cross-site. Bounded — the value must parse as a real IANA zone — but it is a live state-changing GET on a cookie-authenticated origin.

### Abuse and availability

- **No request body size limit.** An approved user (or a stolen session) can POST a multi-megabyte body to `/api/import` and force the server to buffer and parse it. On the auth routes the body is deserialized *before* the throttle check; only the 180-req/min global limiter fires earlier. No memory limits at the container level either, so the ceiling is host RAM.
- **No CAPTCHA, no fail2ban, no edge WAF asserted in code.** The documented Cloudflare rate-limit and Managed Challenge rules exist only as a prose checklist an operator applies by hand — no Terraform, no drift detection, no test. Every attacker request reaches the origin and costs at least one `SELECT … FOR UPDATE` per bucket, even when fully locked out.
- **The rate-limit arithmetic has no test coverage.** All existing tests inject fakes and prove interceptor ordering and the 429 response contract. Nothing exercises the sliding-window counting, the exponential lockout formula, the 1800s clamp, the 24h decay, or `clearFailures`. The numbers in this document are read off the configuration and the formula, not verified by execution.
- **No trusted-proxy allowlist.** `CF-Connecting-IP`, `X-Forwarded-For`, and `X-Real-IP` are trusted unconditionally. This is safe only while the port is loopback-bound. Widen the bind or front it with a different proxy and the IP dimension silently becomes attacker-controlled, with no code change and no warning.

### Data at rest and recovery

- **Field encryption is opt-in and off by default, and does not follow the data.** With no `DATA_ENCRYPTION_KEY` the affected columns are plaintext and nothing on the row marks them as such — a deliberate, supported configuration (`REQUIRE_ENCRYPTION_AT_REST=true` makes a missing key a hard startup failure instead). Independently of that: the portable export bundle decrypts descriptions, and the ICS calendar feed renders them into every VEVENT, so the at-rest control does not survive export or a subscribed third-party calendar.
- **A real `AUTH_SECRET` sits in public git history.** The initial commit's `.env.example` contains a well-formed 32-byte base64 value, since replaced by a placeholder. `AUTH_SECRET` is the HKDF input for the session key, the HMAC key for every throttle bucket, and the login-challenge salt key. If the running deployment ever used it, sessions can be minted offline. Verify the server's live value and rotate; scrubbing history does not un-publish it.
- **No volume encryption and no KMS.** The field-encryption key is an env var in the same `.env.docker` the Compose stack feeds the backend, on the same host as the Postgres volume. It protects a dump that leaves the host, not the host itself. Backups exist now (see above) but are not scheduled by default.
- **Vestigial `enableEncryption` / `protectedSymmetricKey` columns.** They exist in the schema and the user API, `enableEncryption` defaults to `true`, and no client implements client-side encryption anywhere. Reading the DB or the API would give a false impression that E2EE is on.

### Observability and accountability

- **Retention ships inert.** `RETENTION_DRY_RUN=true` by default, and neither `docker-compose.yaml` nor `.env.example` mentions any `RETENTION_*` variable, so an operator has no prompt to turn it on. Until they do, the unbounded-growth problem the scheduler was written to solve is unsolved.
- **Container logs are never rotated.** No `logging:` block on any service, so json-file logs grow without bound. On a shared host a flood of failed logins can fill the disk and take unrelated services down, and a security event pruned from `eventLog` after 90 days is still sitting in the container log.
- **Abuse alerts are computed and never delivered.** `auth_alert_ip_concentration`, `auth_alert_lockout_burst`, and `auth_signal_anomaly` have exactly zero consumers — no admin route, no UI query, no notification, no Sentry event. A sustained credential-stuffing campaign is fully mitigated and entirely invisible unless the owner tails logs or runs SQL by hand.
- **No admin action audit trail and no log integrity.** `eventLog` is an ordinary table with no hash chain or append-only constraint, deletable by the same DB user the app runs as, with no off-host copy. It is useful for an honest operator reviewing their own box; it is not evidence.
- **ERROR-level log lines reach Sentry unscrubbed.** The Logback Sentry appender turns every ERROR into an event carrying the formatted message verbatim, and `beforeSend` never inspects `event.message`. INFO/WARN lines — including the full `[security]` event JSON and truncated push endpoint URLs — ride along as breadcrumbs. Only matters when a DSN is configured (none is by default), but when it is, this is the widest unscrubbed channel in the system.

### Clients

- **App lock is available but off by default.** Both mobile clients now ship an optional biometric/device-credential gate, and Android adds `FLAG_SECURE` screenshot and recents protection. Neither is enabled unless you turn it on, so out of the box device unlock is still the only boundary.
- **Both mobile clients store a recoverable password while awaiting admin approval.** Android's `pending_approval_password_v1` and iOS's `pending-approval-password` hold the actual password (encrypted at rest) so the holding screen can silently retry login. iOS's Keychain class is `AfterFirstUnlock` and not `…ThisDeviceOnly`, so it is eligible for encrypted device backup and restore onto another device.
- **iOS relies on Data Protection, not application-level encryption.** The SwiftData store and the widget content snapshots use `completeUntilFirstUserAuthentication` and are excluded from backups — protected while the device is off or before first unlock, readable after it. That class is required, not chosen: anything stricter stops the widget rendering on a locked device. Android's equivalent store is genuinely encrypted (SQLCipher); iOS's is not.
- **Android widget content is rendered into the launcher process.** Task titles and notes are handed to the home-screen launcher as RemoteViews, visible wherever it displays them including lock-screen widgets, with no per-widget hide-content setting and no `FLAG_SECURE` equivalent.
- **iOS contacts api.github.com on every launch** via `URLSession.shared` — bypassing the app's own TLS delegate — even in Local Mode, with no setting to disable it.
- **Web source maps are built and publicly served.** 105 `.map` files ship in `dist/assets` and are served with `max-age=31536000, immutable`, including the admin screens. No secrets leak, but it removes all friction from mapping the attack surface.
- **Web Local Mode is passphrase-encrypted by default, and the passphrase is unrecoverable.** AES-GCM-256 with a PBKDF2-SHA256 310k-iteration key, fresh IV per write, key held only in memory. The tradeoff is absolute: lose the passphrase and the Local Mode workspace is gone, with no reset path.
- **Web Local Mode encryption can be declined per browser.** A two-step "Skip encryption on this device" confirmation, shown wherever the passphrase would otherwise be asked for, stores the workspace as plain JSON instead — readable by anything with access to that browser profile or its disk. The choice can be reversed one-way from Settings (unencrypted → encrypted, in place); there is no reverse of that reverse.

### Supply chain and operations

- **No image scanning, SBOM, or provenance.** No Trivy/Grype/Snyk/Scout step, no CodeQL, no `npm audit`, no dependency-check, no secret scanner, no Dependabot config. A CVE in the base images or any dependency surfaces only if the owner happens to read release notes.
- **No Gradle dependency verification and no wrapper checksum.** Versions are pinned and repositories are locked, but nothing verifies artifact content — a compromised upstream republishing at a fixed coordinate would not be caught. Asymmetric with the frontend, which does get lockfile integrity hashes.
- **Deploy relies on SSH password auth via `sshpass`,** with no host-key pinning and no `StrictHostKeyChecking` option anywhere in the script. A captured password is shell on the deploy host, which is downstream of every container control listed above. There is also no rollback path — the previous image is replaced in place and the post-deploy health check is suffixed `|| true`.
- **Production builds from source on the host, not from the registry.** That means a compromised registry account cannot change what runs — but equally, production runs code that never had to pass CI, and `[skip release]` in a commit message bypasses the test gate entirely.

### Configuration traps worth checking on the live host

- **`TDAY_ENV`.** If it is not `production`, the app silently ships a non-prefixed session cookie *without* the `Secure` flag and emits no HSTS. This checkout's `.env.docker` reads `TDAY_ENV=development`.
- **`TDAY_HOST_BIND`.** The compose default is `127.0.0.1`; this checkout's root `.env` overrides it to `0.0.0.0`, which would put the backend on every host interface and make the client-IP headers forgeable.
- **`AUTH_SECRET` length.** It is required to boot but its length is never checked. Below 16 characters, `ClientSignalsImpl` prints one line to *stderr* and generates a random per-boot HMAC key, silently orphaning every persisted throttle bucket and resetting all lockouts on each restart.
- **`CSP_CONNECT_EXTRA` replaces rather than appends.** Setting it drops the auto-derived Sentry ingest origin, silently breaking browser error reporting.
- **Three documented `_FILE` variables do not exist in code.** `.env.docker` advertises `CRONJOB_SECRET_FILE`, `AUTH_CAPTCHA_SECRET_FILE`, and `DATA_ENCRYPTION_AAD_FILE`; `AppConfig` implements the indirection for eight secrets, none of which are those three. Following those comments yields a silently unset secret.
- **Default Postgres credentials.** `myuser`/`mypass`/`mydb` unless overridden in the root `.env` and kept in sync with `DATABASE_URL`. Not remotely reachable (the service publishes no ports), but anything else on the compose network can log in with a guessable credential, and the field-encryption key is in the same env file.


## Appendix A — where `SECURITY.md` disagrees with the code

The repo's policy document has drifted. Trust this one over it.

| `SECURITY.md` claim | Actual behaviour |
|---|---|
| "`AUTH_SESSION_MAX_AGE_SEC`, default 24h" | Default is `2_592_000` s = **30 days** (`config/AppConfig.kt:71`) |
| Event code `auth_limit_email` | No such code. Emits `auth_limit_ip` / `auth_limit_username`, plus new `auth_limit_account` |
| "rate-limited per IP and per email" | Per IP, per device hint, per **username** — sign-in now keys on an (IP, username) composite |
| "`_FILE` suffix for all sensitive variables: … `DATA_ENCRYPTION_AAD`" | `DATA_ENCRYPTION_AAD` uses plain `env()`, so it has **no** `_FILE` support (`config/AppConfig.kt:95`) |
| Security-headers table | Predates CSP and Permissions-Policy |
| "Optional public-key fingerprint pinning" | iOS is now **fail-closed** with explicit user confirmation, not optional trust-on-first-use |


## Appendix B — how this was produced

Eleven domain sweeps read the source directly, each followed by an independent fact-checking pass
instructed to delete or correct any control whose cited `file:line` did not support the claim, and
to downgrade any status that overstated reality. That second pass exists because the previous
security document overclaimed — an inventory used for comparison is worse than useless if it is
optimistic.

**290 controls** and **102 gaps** verified across 11 domains at backend v0.7.0. Live-server behaviour
in "Deployment status" was measured with `curl`, not inferred from code.

Full per-control detail with `file:line` evidence: [SECURITY_CONTROLS.md](SECURITY_CONTROLS.md).
