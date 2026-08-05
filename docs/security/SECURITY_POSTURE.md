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

**Written but NOT deployed:** Content-Security-Policy, Permissions-Policy, the (IP, account)
throttle re-keying, the SSRF egress guard, the retention scheduler, the `purgeUser` foreign-key
fix, calendar-feed revocation on credential change, and the iOS fail-closed TLS rewrite. None of
it is committed yet. Anything marked *recently hardened* below is in this category and takes
effect only after `docker compose up -d --build tday-backend` plus an app rebuild.

Two standing items, independent of this document:

1. A real `AUTH_SECRET` sits in this repo's **public** git history. Anyone with it can forge a
   session cookie for any user. Verify the deploy host is not still using it and rotate if so.
2. This checkout's root `.env` sets `TDAY_HOST_BIND=0.0.0.0`, which publishes the backend on every
   host interface rather than loopback. `.env` is gitignored so the server keeps its own copy —
   confirm with `docker port tday_backend 8080` (expect `127.0.0.1:2525`).


## Threat model

- **An internet attacker who finds the hostname.** Defended. Public access is a Cloudflare Tunnel to a loopback-bound port, so there is nothing below HTTP to attack. Registration is open, but the first user became ADMIN/APPROVED and everyone after lands PENDING and gets no session, no read, and no write until an admin approves. What remains reachable is the auth surface: login, register, and the recovery wizard — all rate-limited, all lockout-guarded, all backed by 310,000-iteration PBKDF2. The realistic outcome of a scanner finding this box is a row in the Users table and a 429.
- **Someone on the same public wifi as the owner.** Defended for the browser and iOS; weaker on Android. TLS terminates at the Cloudflare edge with a public CA certificate, and both mobile clients block cleartext HTTP in release builds. iOS fails closed on any certificate the OS cannot verify and requires a user-confirmed fingerprint to enroll one. Android relies purely on system CA validation, and its stored fingerprint auto-resets on mismatch — treat Android as CA-validation-only, not pinned. A password sent at login never appears in a request body (RSA-OAEP + AES-GCM envelope), but registration, password change, and recovery reset all send it in the clear inside TLS.
- **Someone on the owner's LAN.** Mostly defended by topology, and that is the load-bearing part. Postgres and Ollama publish no host ports at all; only the backend does, and the compose default binds it to 127.0.0.1. **Caveat that must be verified on the deploy host:** this checkout's root `.env` sets `TDAY_HOST_BIND=0.0.0.0`, which exposes the backend on every host interface. If that is the live value, anything on the LAN can reach the API directly and can forge `CF-Connecting-IP` to get a fresh rate-limit bucket per request, because there is no trusted-proxy allowlist.
- **Someone who is already an approved user.** Partially defended. Ownership scoping is enforced in Kotlin on every query and share access flows through one `accessFor` decision point, but there is no database row-level security beneath it, any approved user can enumerate other approved usernames via the share picker, and there is no way to suspend an approved account short of deleting it and all its data.
- **Someone with a shell on the host. Explicitly out of scope.** They have `AUTH_SECRET`, so they can mint sessions offline for any user; they have `DATA_ENCRYPTION_KEY`, which sits in the same `.env.docker` as the Postgres volume, so field encryption buys nothing against them; and they have the Docker socket. Field encryption here protects a stolen `pg_dump` or a detached volume, nothing more. Do not read any control below as defending against host compromise.
- **Not defended at all: loss.** There is no backup mechanism in the repo, automated or otherwise. A corrupted volume or a `docker compose down -v` is total data loss.

## Posture at a glance

| Area | Status | The short version |
|---|---|---|
| Authentication & sessions | Solid | 310k PBKDF2, encrypted JWE sessions, server-side revocation, absolute 90-day cap. No MFA, no session inventory, one fail-open branch on a null `tokenVersion` claim, and login timing still leaks account existence. |
| Brute-force & rate limiting | Strong | Two independent layers; sign-in lockouts key on (IP, account) and survive restarts in Postgres; real 429 + Retry-After everywhere. No CAPTCHA, no body-size cap, and the counting arithmetic itself has no test coverage. |
| Authorization & multi-tenancy | Solid | One `withAuth` gate on all 82 authenticated handlers, ownership predicate compiled into every query, one central share ACL. No DB-level isolation, API keys cannot be scoped below the account's admin rights, no suspend. |
| Data at rest & crypto | Adequate | Every credential is hash-only; AES-256-GCM field encryption exists but is off by default, fails open to plaintext, and covers descriptions — not task titles. Key lives beside the data. A real `AUTH_SECRET` is in public git history. |
| Network, TLS & headers | Solid | No inbound ports, outbound-only tunnel, enforcing CSP with `script-src 'self'`, CORS empty by default. TLS terminates at Cloudflare (full MITM by design); HSTS and the Secure cookie flag both hang off `TDAY_ENV=production`. |
| Container & runtime | Solid | Backend runs non-root with `cap_drop: ALL`, `no-new-privileges`, `pids_limit: 512`; multi-stage build, no toolchain in the runtime image. Hardening is backend-only, no read-only rootfs, no memory limits, no image scanning, deploy over SSH password. |
| Logging & privacy | Solid | Access log is four fields, query strings are dropped wholesale, IPs and usernames are HMAC'd before storage. Retention scheduler ships in dry-run so nothing is actually pruned, and Docker's own container logs are never rotated. |
| Input validation & injection | Strong | Typed Exposed DSL throughout, exactly one raw SQL statement and it is parameter-bound, no subprocess and no unsafe deserialization, new SSRF egress guard on user-supplied URLs. No request body size limit anywhere. |
| Android client | Solid | Cleartext blocked in release, session cookie in Keystore-backed EncryptedSharedPreferences, no custom TLS trust code, backups disabled. Task database is unencrypted SQLite, server fingerprint auto-resets on mismatch, no app lock. |
| iOS client & widget | Strong | Fail-closed `decideTrust`, one-shot user-confirmed fingerprint enrollment, Keychain storage, widget inherits pinning and can never enroll. Stores a recoverable password while awaiting approval; widget content snapshot sits unencrypted in App Group defaults. |
| Web SPA | Solid | HttpOnly cookie the JS never sees, enforcing CSP, exactly two HTML sinks and both bounded, full storage wipe on logout and on 401. No CSRF token validation, source maps are publicly served, and `GET /api/timezone` mutates state. |

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
    **T'Day:** Only `description`, `overriddenDescription`, and `webhookSecret` (AES-256-GCM, 12-byte random IV, 128-bit tag). Task titles, checklist steps, list names, usernames, OAuth tokens, and push keys are plaintext. Off unless `DATA_ENCRYPTION_KEY` is set, fails open to plaintext with a production-only boot warning, and the key sits in the same `.env.docker` as the database volume.

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

- **Task titles are plaintext by design.** Field encryption covers descriptions, not titles — and for a task app the title is where the information is. Also plaintext: checklist steps, list names, usernames, `CompletedTodo.steps`, webhook destination URLs, push endpoints and keys, and OAuth `access_token`/`refresh_token`/`id_token`.
- **Field encryption fails open and does not follow the data.** No key means plaintext with no marker on the row. Separately, the portable export bundle explicitly decrypts descriptions and the ICS calendar feed renders them into every VEVENT — so the at-rest control does not survive export or a subscribed third-party calendar.
- **A real `AUTH_SECRET` sits in public git history.** The initial commit's `.env.example` contains a well-formed 32-byte base64 value, since replaced by a placeholder. `AUTH_SECRET` is the HKDF input for the session key, the HMAC key for every throttle bucket, and the login-challenge salt key. If the running deployment ever used it, sessions can be minted offline. Verify the server's live value and rotate; scrubbing history does not un-publish it.
- **No backups, no volume encryption, no KMS.** Postgres data is a plain Docker volume. There is no `pg_dump` cron, no snapshot, no off-box copy, and no rotation backfill (retiring an old encryption key makes rows written under it throw on read).
- **Vestigial `enableEncryption` / `protectedSymmetricKey` columns.** They exist in the schema and the user API, `enableEncryption` defaults to `true`, and no client implements client-side encryption anywhere. Reading the DB or the API would give a false impression that E2EE is on.

### Observability and accountability

- **Retention ships inert.** `RETENTION_DRY_RUN=true` by default, and neither `docker-compose.yaml` nor `.env.example` mentions any `RETENTION_*` variable, so an operator has no prompt to turn it on. Until they do, the unbounded-growth problem the scheduler was written to solve is unsolved.
- **Container logs are never rotated.** No `logging:` block on any service, so json-file logs grow without bound. On a shared host a flood of failed logins can fill the disk and take unrelated services down, and a security event pruned from `eventLog` after 90 days is still sitting in the container log.
- **Abuse alerts are computed and never delivered.** `auth_alert_ip_concentration`, `auth_alert_lockout_burst`, and `auth_signal_anomaly` have exactly zero consumers — no admin route, no UI query, no notification, no Sentry event. A sustained credential-stuffing campaign is fully mitigated and entirely invisible unless the owner tails logs or runs SQL by hand.
- **No admin action audit trail and no log integrity.** `eventLog` is an ordinary table with no hash chain or append-only constraint, deletable by the same DB user the app runs as, with no off-host copy. It is useful for an honest operator reviewing their own box; it is not evidence.
- **ERROR-level log lines reach Sentry unscrubbed.** The Logback Sentry appender turns every ERROR into an event carrying the formatted message verbatim, and `beforeSend` never inspects `event.message`. INFO/WARN lines — including the full `[security]` event JSON and truncated push endpoint URLs — ride along as breadcrumbs. Only matters when a DSN is configured (none is by default), but when it is, this is the widest unscrubbed channel in the system.

### Clients

- **Android's local task database is unencrypted SQLite.** No SQLCipher, no passphrase. In Local Mode it is the only copy of the user's data. A rooted device, a forensic extraction, or an unlocked handset with adb yields the full task list. The session cookie and server URL *are* Keystore-encrypted; the task data is not.
- **Android's server fingerprint is not a control.** Enrolled silently on first probe, skipped entirely for non-HTTPS, and automatically cleared and re-enrolled on mismatch by `probeAndSaveWithAutomaticTrustRecovery`. Do not count it when comparing against iOS's fail-closed implementation.
- **No app lock on either mobile client.** No biometric or passcode gate, no screenshot/recents protection on Android, no jailbreak detection on iOS. Device unlock is the only boundary between a bystander and the full signed-in session.
- **Both mobile clients store a recoverable password while awaiting admin approval.** Android's `pending_approval_password_v1` and iOS's `pending-approval-password` hold the actual password (encrypted at rest) so the holding screen can silently retry login. iOS's Keychain class is `AfterFirstUnlock` and not `…ThisDeviceOnly`, so it is eligible for encrypted device backup and restore onto another device.
- **iOS's widget content snapshot is unencrypted.** The session hand-off file is deliberately written with `completeFileProtectionUntilFirstUserAuthentication` and excluded from backup; the widget *content* snapshots (task titles, notes, due times, IDs) go to App Group and standard `UserDefaults` as plain JSON with only the platform default protection, and land in backups.
- **Android widget content is rendered into the launcher process.** Task titles and notes are handed to the home-screen launcher as RemoteViews, visible wherever it displays them including lock-screen widgets, with no per-widget hide-content setting and no `FLAG_SECURE` equivalent.
- **iOS contacts api.github.com on every launch** via `URLSession.shared` — bypassing the app's own TLS delegate — even in Local Mode, with no setting to disable it.
- **Web source maps are built and publicly served.** 105 `.map` files ship in `dist/assets` and are served with `max-age=31536000, immutable`, including the admin screens. No secrets leak, but it removes all friction from mapping the attack surface.
- **Web Local Mode stores the whole workspace unencrypted in one localStorage key,** and logging out of a server account deliberately preserves it. Only Settings → Delete local data or clearing browser site data removes it.

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
