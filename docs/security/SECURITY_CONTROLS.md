# T'Day — Security Control Inventory

Backing detail for [SECURITY_POSTURE.md](SECURITY_POSTURE.md). Every entry was read out of the
source and then independently re-checked against its cited location.

**On by default** = works with no operator action · **Needs config** = requires an environment
variable · **Partial** = exists but incomplete · **Absent** = not implemented.

Controls marked *recently hardened* are in the working tree and not yet deployed.

Path prefixes in the evidence lines: `backend:` = `tday-backend/src/main/kotlin/com/ohmz/tday/`,
`backend-test:` = `tday-backend/src/test/kotlin/com/ohmz/tday/`. Everything else is repo-relative.

---

## Authentication & session management

### Password hashing (PBKDF2-HMAC-SHA256, 310k)

`On by default`

PBKDF2WithHmacSHA256, 256-bit derived key, 16-byte SecureRandom salt per password, stored self-describing as `pbkdf2_sha256$<iterations>$<saltHex>$<hashHex>` (hashPassword PasswordService.kt:34-40; derivation :94-99). Iteration count is AppConfig.pbkdf2Iterations = envInt("AUTH_PBKDF2_ITERATIONS", 310_000).coerceIn(100_000, 2_000_000) (AppConfig.kt:95-96) — and envInt itself discards any non-numeric or <=0 value back to the 310k default (AppConfig.kt:189-192), so a misconfigured env var cannot land below the 100k floor. Comparison is a constant-time XOR-accumulate byte loop, not String equals (PasswordService.kt:101-109). VERIFIED the same service also hashes security-question answers at BOTH write sites — registration (UserService.kt:164-165) and later question changes (SecurityQuestionService.kt:289) — so answers get the identical 310k treatment. Admin-generated temporary passwords are 16 chars from a SecureRandom alphabet (AdminService.kt:284-298).

> backend:security/PasswordService.kt:26-40,94-109; backend:config/AppConfig.kt:95-96,189-192

### Session token is an encrypted JWE, not a signed JWT

`On by default`

Sessions are com.nimbusds.jwt.EncryptedJWT with header JWEAlgorithm.DIR + EncryptionMethod.A256CBC_HS512 (JwtService.kt:101-107). The 64-byte key is derived from AUTH_SECRET via BouncyCastle HKDF-SHA256, ZERO-LENGTH salt, fixed public info string "Auth.js Generated Encryption Key" (JwtService.kt:121-132) — Auth.js-compatible, hence the cookie name. Because the token is encrypted under a direct symmetric key, claims are not attacker-readable, and the classic alg:none / RS256->HS256 confusion bugs do not apply: those need a SIGNED token whose header the verifier trusts to select an algorithm. Here decode() hands the fixed key to DirectDecrypter, which only handles `dir`, and the 64-byte length only matches A256CBC-HS512; anything else throws and the catch-all returns null (JwtService.kt:46-49,76-78). A256CBC-HS512 is encrypt-then-MAC, so tampering fails the tag. Expiry is re-checked in code after decrypt and a token with NO exp claim is rejected outright (JwtService.kt:52-58). IMPORTANT COROLLARY the first pass omitted: the session key is a pure deterministic function of AUTH_SECRET with no salt, so anyone holding AUTH_SECRET can mint valid sessions offline for any user id — and rotating AUTH_SECRET simultaneously invalidates every session, orphans every persisted throttle bucket (ClientSignals.kt:49-53 keys the HMAC on it) and changes the password-proof decoy salts (PasswordProof.kt:126).

> backend:security/JwtService.kt:45-58,76-78,101-107,121-132

### Session lifetime, absolute cap and sliding renewal

`On by default`

Three clamped values computed in AppConfig.load before the object is constructed (AppConfig.kt:80-85). sessionMaxAgeSec = AUTH_SESSION_MAX_AGE_SEC default 2_592_000s (30d), coerceIn(3600, 2_592_000) — 30 days is both the default AND the hard maximum. sessionAbsoluteMaxAgeSec = AUTH_SESSION_ABSOLUTE_MAX_AGE_SEC default 7_776_000s (90d), coerceIn(sessionMaxAgeSec, 31_536_000) — can never be shorter than the rolling age. sessionRenewThresholdSec = AUTH_SESSION_RENEW_THRESHOLD_SEC default 604_800s (7d), coerceIn(60, sessionMaxAgeSec). Every token carries a `sessionStartedAt` claim preserved across renewals (JwtService.kt:83,89); isSessionPastAbsoluteLifetime compares sessionStartedAt + absoluteMaxAge (SessionCookies.kt:55-63) and the per-request interceptor clears the cookie and logs auth_session_absolute_expired when it trips (Security.kt:128-138). shouldRenewSession re-issues only when remaining lifetime is in 1..threshold AND the absolute cap is not reached (SessionCookies.kt:65-74). Renewal is suppressed on /api/auth/callback/credentials, /api/auth/logout and /api/user/change-password so a renewal cannot resurrect a session those routes just rotated (Security.kt:154,300-307), and is skipped entirely on the API-key path (Security.kt:120).

> backend:config/AppConfig.kt:80-85; backend:security/SessionCookies.kt:55-74; backend:plugins/Security.kt:128-138,154-165,300-307

### Server-side session revocation via tokenVersion

`On by default`

Every session token carries a `tokenVersion` claim; the per-request interceptor loads the user's current tokenVersion and rejects the session on mismatch — clearing the cookie, invalidating the cache and logging auth_session_token_version_mismatch (Security.kt:143,166-176). SessionControlImpl.revokeUserSessions does `tokenVersion = tokenVersion + 1` in SQL then invalidates the auth cache (SessionControl.kt:29-46). Confirmed bump sites: logout (LogoutRoutes.kt:20, revokeApiKeys defaulting to false), change-password (UserRoutes.kt:108), security-question self-service reset (SecurityQuestionService.kt:253), admin resetPassword (AdminService.kt:246) and deleteUser/rejectUser via purgeUser (AdminService.kt:208). CONFIRMED FAIL-OPEN EDGE at Security.kt:143: `if (claims.tokenVersion == null || claims.tokenVersion == user.tokenVersion)` — a null claim is accepted regardless of the DB value; JwtService.encode only writes the claim when non-null (JwtService.kt:98) and the login route sources it from `user["tokenVersion"] as? Int` (CredentialsCallbackRoutes.kt:136). Secondary detail: change-password re-issues the cookie with `(currentClaims.tokenVersion ?: 0) + 1` computed locally rather than re-read from the DB (UserRoutes.kt:109-112) — a mismatch there fails closed (immediate logout), not open.

> backend:security/SessionControl.kt:29-46; backend:plugins/Security.kt:143,166-176; backend:routes/UserRoutes.kt:108-112

### Revocation blast radius: revokeApiKeys=true also kills the calendar feed token

`On by default`

Recently hardened. revokeUserSessions(userId, revokeApiKeys = true) calls BOTH userApiKeyService.revoke(userId) and calendarFeedService.revoke(userId) (SessionControl.kt:38-44), with the feed revoker wired in DI as a narrow fun-interface over CalendarFeedService (AppModule.kt:90-93). The ICS calendar feed is a token-in-path, no-session, unauthenticated route registered outside /api (Routing.kt:34-35), so before this a leaked feed URL kept exposing task titles after a password change. revokeApiKeys=true is passed by change-password (UserRoutes.kt:108), security-question recovery (SecurityQuestionService.kt:253), admin password reset (AdminService.kt:246) and account purge (AdminService.kt:208). Plain logout deliberately passes the default false (LogoutRoutes.kt:20) so signing out on one device does not break widgets and calendar subscriptions. There is no rotate-in-place — the user must re-add the feed.

> backend:security/SessionControl.kt:11-46; backend:di/AppModule.kt:90-93; backend:routes/auth/LogoutRoutes.kt:20

### AuthUserCache (auth-state cache and revocation latency)

`On by default`

In-process ConcurrentHashMap keyed by userId, TTL defaulted to 30_000 ms in the constructor and constructed with NO argument in DI, so 30s is what actually runs (AuthUserCache.kt:14-33; AppModule.kt:89 `single { AuthUserCache() }`). Caches role, approvalStatus, tokenVersion, timeZone, requirePasswordChange, requireSecurityQuestions — the authorization state the interceptor hydrates onto every request (Security.kt:277-295). This bounds how fast an authorization change is noticed. Every revocation path invalidates (SessionControl.kt:45), as does setQuestions (SecurityQuestionService.kt:299) and the mismatch branch (Security.kt:168). CORRECTION/ADDITION the first pass missed: AdminService.approveUser (AdminService.kt:77-97) does NOT invalidate the cache, so an approval can take up to 30s to take effect — that direction is fail-closed (the user stays PENDING briefly), not fail-open. Being in-process, the 30s bound is also why this design assumes a single backend container.

> backend:security/AuthUserCache.kt:14-33; backend:di/AppModule.kt:89; backend:services/AdminService.kt:77-97

### PENDING approval gate (the highest-leverage control here)

`On by default`

Registration is open, but UserService.register counts existing rows: `isFirst = userCount == 0L`, then role = ADMIN/USER and approvalStatus = APPROVED/PENDING accordingly (UserService.kt:138-152). Enforced in two independent places. (1) Login: CredentialsCallbackRoutes.kt:118-125 refuses to issue a session cookie unless approvalStatus == "APPROVED", returning 403 `pending_approval` — this check runs AFTER password verification, so a pending user never obtains a session. (2) Every business route: withAuth -> requireApprovedAuthUser -> requireApproved rejects anything not APPROVED with 403 (AuthContext.kt:51-56,64-84), and the WebSocket handshake does the same and closes with VIOLATED_POLICY (Routing.kt:68-79). approvalStatus is re-read from the DB/cache on every request and overwrites whatever the token carried (Security.kt:146-151), so it is not trusted from the token. requireAdminAccess additionally requires role == "ADMIN" on top of approval (AuthContext.kt:58-62) and every AdminService method binds it first. Net effect on a self-hosted box: an internet stranger can create a row and consume registration quota, and nothing else — no session, no read, no write.

> backend:services/UserService.kt:138-152; backend:routes/auth/CredentialsCallbackRoutes.kt:118-125; backend:domain/AuthContext.kt:51-62; backend:plugins/Routing.kt:68-79

### Sign-in lockout keyed on (IP, account), with a non-punitive account quota

`On by default`

Recently hardened. Failed sign-ins lock the composite ThrottleDimension.ipUsername bucket `"$ip|$normalizedIdentifier"` rather than the bare username (AuthThrottle.kt:78-84). recordFailure filters subjects to LOCKABLE_DIMENSIONS = {ip, device, ipUsername} before calling incrementFailureCounter (AuthThrottle.kt:50-54,216). Backoff (AuthThrottle.kt:425-433, defaults AppConfig.kt:132-135): no lock below AUTH_LOCKOUT_FAIL_THRESHOLD=5 failures, then min(AUTH_LOCKOUT_MAX_SEC=1800s, AUTH_LOCKOUT_BASE_SEC=30s * 2^(failures-5)) — so the 5th failure yields 30s and the 11th onward pins at 1800s; the failure counter resets after AUTH_LOCKOUT_RESET_SEC=86400s of quiet (:386-391). The account-wide backstop is a QUOTA, not a lockout: scope CREDENTIALS_ACCOUNT_SCOPE, AUTH_LIMIT_CREDENTIALS_ACCOUNT_MAX=50 per AUTH_LIMIT_CREDENTIALS_ACCOUNT_WINDOW_SEC=900s, routed only through consumeRequestQuota and never through incrementFailureCounter, so its worst case is one window (AuthThrottle.kt:41-42,133-136,162-175). buildSubjectKeys and reasonCodeFor are pure and unit-tested (AuthThrottleSubjectsTest.kt). State is in the Postgres `auththrottle` table read with SELECT ... FOR UPDATE, so it survives a restart (AuthThrottle.kt:320-325,380-383). ADDITION the first pass missed: security-question verify and self-service reset failures feed the SAME credentials buckets via authThrottle.recordFailure (SecurityQuestionRoutes.kt:107,171), so recovery guessing accrues the same (IP, account) lockout.

> backend:security/AuthThrottle.kt:30,41-54,64-101,133-175,208-233,320-325,376-433; backend:config/AppConfig.kt:130-135; backend:routes/auth/SecurityQuestionRoutes.kt:107,171

### Per-endpoint auth rate limits with real 429 + Retry-After

`On by default`

Recently hardened. CORRECTION: there are TEN auth rate-limit rejection sites, not seven — CsrfRoutes.kt:19, RegisterRoutes.kt:27, LoginChallengeRoutes.kt:31, CredentialsKeyRoutes.kt:19, CredentialsCallbackRoutes.kt:58, SessionRoutes.kt:25, and four in SecurityQuestionRoutes.kt (:46,:77,:126,:189). All ten call domain/AuthContext.kt:25-39 respondRateLimit, which appends a Retry-After header and returns HTTP 429 with {message, reason, retryAfterSeconds}; they previously threw and surfaced as 500. Defaults (AppConfig.kt:118-131): credentials 12 per 300s; csrf 40 per 60s; sessionGet 20 per 60s; credentialsKey 20 per 60s; register 6 per 3600s plus a stricter per-IP burst tier of 3 per 600s under scope "register_burst:ip" (AuthThrottle.kt:118-121,180-192). All four security-question routes reuse ThrottleAction.credentials (12/300s), so the reset wizard shares the sign-in budget. Every value is an env var, but every default is already restrictive.

> backend:domain/AuthContext.kt:25-39; backend:config/AppConfig.kt:118-131; backend:security/AuthThrottle.kt:137-145,180-192

### Second-tier request rate limits incl. a dedicated change-password bucket

`On by default`

ADDED — the first pass covered only the AuthThrottle tier. A separate interceptor (plugins/RateLimiting.kt:14-37, installed after configureSecurity so call.authUser() is populated) applies sliding-window policies by path: every /api/* request 180 per 60s; /health, /api/mobile/probe and /calendar/* 30 per 60s; POST /api/user/change-password 8 per 300s (CHANGE_PASSWORD_RATE_LIMIT_MAX/WINDOW_SEC); /ws 30 per 60s; POST /api/todo/summary 10 per 60s (RateLimiting.kt:39-98, defaults AppConfig.kt:108-117). Rejections also go through respondRateLimit, so they are 429 + Retry-After (RateLimiting.kt:28-35). Each bucket is keyed by authenticated userId when present, else by client IP (RequestRateLimiter.kt:55-79). This is what rate-limits the authenticated password-change endpoint — AuthThrottle does not cover it. LIMITATION: this limiter is an in-process ConcurrentHashMap sliding window (RequestRateLimiter.kt:37-52), so unlike AuthThrottle it resets on restart and does not span replicas.

> backend:plugins/RateLimiting.kt:14-98; backend:security/RequestRateLimiter.kt:37-79; backend:config/AppConfig.kt:108-117

### Password-proof challenge (HMAC over the stored hash)

`On by default`

POST /api/auth/login-challenge returns {version, algorithm, challengeId, saltHex, iterations, expiresAt} (LoginChallengeRoutes.kt:42-53). The client derives PBKDF2 with those parameters and returns HMAC-SHA256, keyed by the derived hash bytes, over `"login:$challengeId:$normalizedUsername"`; the server recomputes from the stored hash and compares with MessageDigest.isEqual (PasswordProof.kt:99-111). challengeId is 24 SecureRandom bytes (:62); challenges are single-use (`challenges.remove` at :95), TTL AUTH_PASSWORD_PROOF_CHALLENGE_TTL_SEC=120s (AppConfig.kt:139), capped at AUTH_PASSWORD_PROOF_MAX_ACTIVE=5000 in-memory entries with prune-then-evict (:138-148). WHAT IT PROTECTS: the password never reaches the server on this path, and the challenge is nonce'd and short-lived so a captured proof cannot be replayed. It also closes most of a username-enumeration oracle: an unknown username gets a salt derived as HMAC-SHA256(AUTH_SECRET, "login-challenge-salt:$username"), stable across calls and restarts (:56-61,119-129). CORRECTIONS: (a) the enumeration defense is not total — for an unknown user the returned `iterations` is config.pbkdf2Iterations (310_000), so an account still holding a LEGACY hash is distinguishable by its 10_000 (PasswordProof.kt:54-55); (b) evictOldest() drops `challenges.keys.firstOrNull()`, which is arbitrary ConcurrentHashMap order, not genuinely oldest-first (:142-148). WHAT IT DOES NOT PROTECT: the stored hash is password-equivalent on this path — anyone with users-table read access can mint valid proofs without cracking anything. It is also why this path can never trigger rehash-on-login, and challenge state is in-process so it does not survive a restart.

> backend:security/PasswordProof.kt:43,51-78,80-129,138-148; backend:routes/auth/CredentialsCallbackRoutes.kt:89-100

### Security-question account recovery

`On by default`

3 questions are mandatory at registration (validateSelection(required = 3), RegisterRoutes.kt:57), reset requires 2 of them (required = 2, SecurityQuestionRoutes.kt:85,136). Answers are normalized (trim + lowercase, SecurityQuestions.kt:35) then hashed with the SAME PasswordService — PBKDF2-HMAC-SHA256 at 310k with a per-answer random salt (UserService.kt:164-165, SecurityQuestionService.kt:289); only questionId + answerHash are persisted, so wording can change without a migration. FAIL COUNTER: Users.securityQuestionFailCount increments on any invalid verify or reset (SecurityQuestionService.kt:170-177,244-249) and recovery locks when failCount > SECURITY_QUESTION_FAIL_LIMIT = 3, i.e. the 4th miss locks (:26,145,206); a successful reset zeroes it (:237). Reset success also re-hashes the new password, clears requirePasswordChange, clears pendingAdminReset/adminResetRequestedAt, and revokes sessions with revokeApiKeys=true AFTER commit (:233-242,253). Password policy on reset matches registration (SecurityQuestionRoutes.kt:143-154). An account that never configured questions (Users.requireSecurityQuestions defaults true, Users.kt:28) is excluded from every recovery path (:111,130,195). CORRECTION on timing: the code does run a throwaway PBKDF2 against a lazily-built dummy hash for unknown/unconfigured/locked accounts and for missing question ids (:86,131,147,162,196,208,224), but equalization is PARTIAL — the unknown-account branch performs exactly ONE dummy derivation and returns, while a real account performs one per submitted answer (two), leaving a measurable ~1x-310k-iteration difference. Account existence is in any case already disclosed by GET /api/auth/security-questions.

> backend:services/SecurityQuestionService.kt:26,86,111,121-180,182-255,289; backend:routes/auth/SecurityQuestionRoutes.kt:85,136,143-154; backend:db/tables/Users.kt:28

### pendingAdminReset no longer gates recovery; admin can clear it; 7-day auto-expiry

`On by default`

Recently hardened. POST /api/auth/request-admin-reset is unauthenticated and sets Users.pendingAdminReset + adminResetRequestedAt for any username with no existence check (SecurityQuestionService.kt:257-267), always answering with the same generic message (SecurityQuestionRoutes.kt:197-201). That flag used to gate the recovery branch, so one anonymous POST permanently disabled the owner's own self-service recovery — and for an ADMIN target nothing could clear it, because AdminService.resetPassword refuses admin targets (AdminService.kt:223-225). Now the flag is a pure notification: only securityQuestionFailCount > 3 locks recovery, with the reasoning in the source comments (SecurityQuestionService.kt:140-149, 201-210). Three clearing mechanisms, all verified: (a) AdminService.clearResetRequest zeroes securityQuestionFailCount, pendingAdminReset and adminResetRequestedAt and is explicitly permitted against ADMIN targets including the caller (AdminService.kt:251-278), exposed as POST /api/admin/users/{id}/clear-reset-request behind withAuth + requireAdminAccess (AdminRoutes.kt:65-75); (b) listUsers stops reporting a request older than ADMIN_RESET_REQUEST_TTL_DAYS = 7 (AdminService.kt:24-31,56-72); (c) RetentionScheduler clears the stale rows past that same cutoff (RetentionScheduler.kt:94-113) — but that write is suppressed while RETENTION_DRY_RUN is true, which is the shipped default (AppConfig.kt:164), so on a stock deployment (c) only counts rows. (a) and (b) are unconditional.

> backend:services/SecurityQuestionService.kt:140-149,201-210,257-267; backend:services/AdminService.kt:24-31,56-72,223-225,251-278; backend:routes/AdminRoutes.kt:65-75; backend:services/RetentionScheduler.kt:94-113

### Registration input policy

`On by default`

Username must match `^[a-z0-9](?:[a-z0-9._-]{1,28}[a-z0-9])$` after trim + lowercase — 3 to 30 chars, alphanumeric first and last (RegisterRoutes.kt:15,39-43). Password: length >= 8, at least one uppercase, at least one non-alphanumeric (RegisterRoutes.kt:44-55) — the same three rules re-enforced on change-password (UserRoutes.kt:99-101) and self-service reset (SecurityQuestionRoutes.kt:143-154). No lowercase requirement, no digit requirement, no maximum length, no breached-password check. First name must be >= 2 chars (:35-38). Usernames are stored as submitted but matched case-insensitively everywhere via Users.username.lowerCase() (UserService.kt:180-184,203-205) so a lowercase duplicate cannot shadow a legacy mixed-case row. Duplicate check happens before insert (:63-66).

> backend:routes/auth/RegisterRoutes.kt:15,35-66; backend:routes/UserRoutes.kt:99-101; backend:services/UserService.kt:180-184

### Structured security event log with hashed identifiers

`On by default`

Throttle and session events are written through SecurityEventLogger: auth_lockout, auth_limit_* (auth_limit_ip / auth_limit_username / auth_limit_account / auth_limit_ip_burst), auth_alert_lockout_burst when a lock reaches AUTH_ALERT_LOCKOUT_BURST_SEC=900s, auth_alert_ip_concentration at AUTH_ALERT_IP_FAILURE_THRESHOLD=12 failures from one IP, auth_signal_anomaly when a known account is seen from both a new IP and a new device inside AUTH_SIGNAL_ANOMALY_WINDOW_SEC=86400s (AuthThrottle.kt:194-233,250-299), plus auth_session_absolute_expired, auth_session_renewed, auth_session_token_version_mismatch and auth_session_user_missing from the interceptor (Security.kt:130-186). Each event is written to the Postgres `eventlog` table truncated to 500 chars, and simultaneously to the SLF4J "security" logger at WARN — i.e. also to container stdout (SecurityEventLogger.kt:34,42-50). Usernames, IPs and device hints go through ClientSignals.hashSecurityValue, HMAC-SHA256 keyed by AUTH_SECRET (ClientSignals.kt:49-53), and paths are sanitized (Security.kt:297-298). CORRECTION to the first pass: the session-event details carry the raw internal userId (a cuid), not a hash (Security.kt:132-135,160,172,182) — no username or IP, but it is a direct DB key. Client IP resolution prefers cf-connecting-ip, then the first x-forwarded-for entry, then x-real-ip, then the socket address (ClientSignals.kt:27-37) — correct behind the Cloudflare Tunnel, but those headers are client-controlled if anything ever reaches the port directly, which would let an attacker rotate their own throttle bucket.

> backend:security/ClientSignals.kt:27-53; backend:security/SecurityEventLogger.kt:25-54; backend:security/AuthThrottle.kt:194-233,250-299; backend:plugins/Security.kt:130-186

### API-key auth path with a read-only scope enforced by HTTP method

`On by default`

Bearer tokens prefixed `tday_` are resolved as per-user API keys rather than session JWEs (Security.kt:42,83-121). A READ-scoped key is rejected with 403 api_key_read_only for any method outside GET/HEAD/OPTIONS, before any route handler runs and regardless of route (Security.kt:53-54,91-102). The resolved principal still carries approvalStatus from the same cache, so the PENDING gate applies to API keys too (Security.kt:103-118). Session renewal is skipped on this path (:120). Key material (UserApiKeyService.kt:105,130-135,201-249): the secret is 32 CSPRNG bytes base64url-encoded, stored as `s256$<sha256hex>` and compared with MessageDigest.isEqual; deliberately NOT PBKDF2 because a 256-bit random secret needs no stretching and this runs on every request (documented at :130-133) — legacy PBKDF2-hashed keys are still accepted. Keys support an `enabled` flag and an optional expiresAt, both checked at resolve time (:212,216-217). Keys carry no tokenVersion by design, so a tokenVersion bump does NOT revoke them — which is exactly why credential-rotation events must pass revokeApiKeys=true (SessionControl.kt:12-21).

> backend:plugins/Security.kt:53-54,83-121; backend:services/UserApiKeyService.kt:29-41,130-135,201-249; backend:security/SessionControl.kt:12-21

### Required-at-boot secrets; no dev bypass anywhere in the source

`On by default`

AUTH_SECRET and DATABASE_URL have no fallback — AppConfig.load calls error("AUTH_SECRET is required") / error("DATABASE_URL is required") and the process fails to start (AppConfig.kt:89-92). Both support Docker-secret indirection via *_FILE (AppConfig.secret, :204-218). I re-ran the grep for dev bypasses and default secrets (`SKIP_AUTH|DEV_BYPASS|DISABLE_AUTH|changeme|default secret|hardcoded`, case-insensitive) over tday-backend/src/main/kotlin and it returned ZERO matches: no test/impersonation account, no magic header, no branch skipping the auth interceptor. The only behaviour keyed on TDAY_ENV is cookie naming/Secure (SessionCookies.kt:19,94), HSTS (SecurityHeaders.kt:110), the Sentry environment tag and default trace sample rate (Application.kt:40, AppConfig.kt:168), and whether startup security warnings print (Application.kt:123). WEAK SPOT, confirmed: ClientSignalsImpl falls back to a random per-boot HMAC key if AUTH_SECRET is shorter than 16 chars, printing only "auth_secret_missing using fallback hash key" to stderr (ClientSignals.kt:16-25) — that silently orphans every persisted throttle bucket on each restart, resetting lockouts, though boot would already have failed if the var were absent entirely. Also confirmed: docker-compose.yaml ships weak Postgres defaults myuser/mypass/mydb overridable from .env (docker-compose.yaml:16-18).

> backend:config/AppConfig.kt:89-92,204-218; backend:security/ClientSignals.kt:16-25; grep -rniE "SKIP_AUTH|DEV_BYPASS|DISABLE_AUTH|changeme|default secret|hardcoded" over tday-backend/src/main/kotlin returned no matches (exit 1); docker-compose.yaml:16-18

### Production startup security warnings

`On by default`

When isProduction, logStartupSecurityWarnings emits explicit WARN lines for: unset DATA_ENCRYPTION_KEY/DATA_ENCRYPTION_KEYS, stating outright that sensitive fields are stored as PLAINTEXT because field encryption is fail-open (Application.kt:125-133); unset AUTH_CREDENTIALS_PRIVATE_KEY, so the login envelope uses an ephemeral key (:135-137); unset APPLE_TEAM_ID (:139-141); and empty ANDROID_SHA256_CERT_FINGERPRINTS (:143-145). It returns immediately outside production (:123), so a developer is not trained to ignore them. It runs at startup right after configureSecurity (Application.kt:88-89). These are warnings only — nothing refuses to boot on them.

> backend:Application.kt:88-89,122-146

### CORS deny-by-default

`On by default`

The CORS plugin installs with allowCredentials = true and allowNonSimpleContentTypes = true, but registers NO origins unless CORS_ALLOWED_ORIGINS is set — the allow-list is built purely by iterating that CSV (Cors.kt:19-37; AppConfig.kt:94 envCsv). There is no anyHost() anywhere in the file, so out of the box a cross-origin browser request receives no Access-Control-Allow-Origin and the response is unreadable to the calling page. Each configured entry is parsed as a java.net.URI and dropped with a logged warning unless the scheme is http/https and the host is non-blank; the port is preserved when present (Cors.kt:40-51). Since the SPA is served by this same Ktor process (Routing.kt:86-108), the normal case never needs CORS at all.

> backend:plugins/Cors.kt:16-51; backend:config/AppConfig.kt:94

### Credential envelope (RSA-OAEP-256 + AES-256-GCM)

`Needs config`

The web client can fetch a public key from GET /api/auth/credentials-key (CredentialsKeyRoutes.kt:15-28) and post {encryptedPayload, encryptedKey, encryptedIv} instead of a plaintext username/password. Server side: RSA/ECB/OAEPPadding with OAEPParameterSpec(SHA-256, MGF1, SHA-256) unwraps a key that must be exactly 32 bytes, then AES/GCM/NoPadding with a strictly-12-byte IV and a 128-bit tag decrypts the JSON; envelope version and keyId are both checked when supplied (CredentialEnvelope.kt:48-52,72-110). WHAT IT PROTECTS: the plaintext password is not in the request body, so it is not captured by browser extensions, devtools, HAR exports or any body-logging middlebox. WHAT IT DOES NOT PROTECT: it is not a TLS substitute and gives no forward secrecy and no replay protection — the envelope carries no nonce or timestamp, so an attacker who captures the request can replay it verbatim, and the server holds the plaintext password in memory either way. REQUIRES CONFIG, and fails soft: with AUTH_CREDENTIALS_PRIVATE_KEY unset, loadKeyMaterial generates an EPHEMERAL RSA-2048 keypair at first use and logs auth_credentials_private_key_missing (CredentialEnvelope.kt:115-148) plus a production boot warning (Application.kt:135-137) — envelopes silently break across restarts and the plaintext fallback fields take over without telling the client (CredentialsCallbackRoutes.kt:42-48).

> backend:security/CredentialEnvelope.kt:46-53,72-110,115-148; backend:Application.kt:135-137

### Legacy hash migration / rehash-on-login

`Partial`

parsePasswordHash also accepts an old `saltHex:hashHex` format and assigns it 10_000 iterations (PasswordService.kt:82-89). verifyPassword returns needsRehash = (format == "legacy" || iterations < config.pbkdf2Iterations) (PasswordService.kt:60). On a successful plaintext sign-in the route rewrites the row with a fresh hash at the current cost: `if (verification.valid && verification.needsRehash) userService.updatePasswordHash(...)` (CredentialsCallbackRoutes.kt:105-107), which is the ONLY caller of updatePasswordHash in the codebase (grep: 1 call site). PARTIAL by design and confirmed: the password-proof login branch (CredentialsCallbackRoutes.kt:89-100) never receives the plaintext, so an account that only ever logs in via password-proof is never upgraded, and change-password/reset (UserService.kt:114, SecurityQuestionService.kt:235) are the only other paths that re-hash. Raising AUTH_PBKDF2_ITERATIONS therefore migrates users lazily on next plaintext login, never in bulk.

> backend:security/PasswordService.kt:60,82-89; backend:routes/auth/CredentialsCallbackRoutes.kt:104-108

### Session cookie flags

`Partial`

httpOnly = true, path = "/" and SameSite=Lax are set unconditionally in buildSessionCookie (SessionCookies.kt:11,89-97). Name and Secure flag are environment-dependent: `sessionCookieName(isProduction)` returns `__Secure-authjs.session-token` in production and `authjs.session-token` otherwise (SessionCookies.kt:8-19), and `secure = cookieName.startsWith("__Secure-") || config.isProduction` (SessionCookies.kt:94). isProduction is TDAY_ENV (else NODE_ENV) == "production", defaulting to "development" (AppConfig.kt:93,199-202) — so a deployment that forgets TDAY_ENV=production ships a non-prefixed, non-Secure cookie AND loses HSTS (SecurityHeaders.kt:110-112). Whenever one cookie name is issued the other is explicitly expired with maxAge 0 (SessionCookies.kt:31-35,49-53,76-82) so the two variants cannot shadow each other. SameSite=Lax (not Strict) plus the empty-by-default CORS list is the entire cross-site defense — see the CSRF gap. Note the token is also accepted from an `Authorization: Bearer` header (Security.kt:264-268), which browsers never attach automatically, so that path is not a CSRF surface.

> backend:security/SessionCookies.kt:8-19,31-35,84-97; backend:config/AppConfig.kt:93,199-202

### Uniform 401 response on all sign-in failure modes

`Partial`

Unknown username, blank stored hash and wrong password all return the byte-identical body `401 {"message":"Invalid credentials"}` with no distinguishing field (CredentialsCallbackRoutes.kt:66-69,72-77,80-84,112-116), and the unknown-user branch also consumes any submitted password-proof challenge so a probe cannot distinguish by whether the challenge survived (:73). Envelope decryption failure is logged server-side as auth_credential_envelope_invalid and then silently falls back to the plaintext fields rather than telling the caller (:42-48). Only the approval state is deliberately distinguishable — 403 pending_approval (:118-125) — and by then the correct password was already supplied. WHY PARTIAL, and the first pass overclaimed this: only the RESPONSE is uniform, not the timing. The unknown-username and blank-hash branches return without performing any PBKDF2 work, while the wrong-password branch runs a full 310k-iteration derivation (:104) — a difference of order 100ms, trivially measurable, so username existence is observable from the login endpoint. There is no dummy-hash equalization here (unlike SecurityQuestionService, which at least attempts it). Separately, GET /api/auth/security-questions 404s on unknown accounts and reveals existence outright; the source documents this as an accepted tradeoff (SecurityQuestionRoutes.kt:34-36,54-64).

> backend:routes/auth/CredentialsCallbackRoutes.kt:42-48,66-125; backend:routes/auth/SecurityQuestionRoutes.kt:34-36,54-64

### Security response headers incl. enforcing CSP

`Partial`

Recently hardened. DefaultHeaders sets X-Content-Type-Options: nosniff, X-Frame-Options: DENY, Referrer-Policy: strict-origin-when-cross-origin and Permissions-Policy `camera=(), microphone=(), geolocation=(), payment=(), usb=()` unconditionally (SecurityHeaders.kt:100-106). CSP is ENFORCING by default — parseCspMode maps null, empty, "enforce" and any unrecognised value to CspMode.enforce, with CSP_MODE=report-only or off as the only escape hatches (SecurityHeaders.kt:12-17,92-98). The policy: default-src / base-uri / form-action 'self'; object-src, frame-src, frame-ancestors 'none'; script-src 'self' (no unsafe-inline, no nonce); img-src 'self' data:; font-src/media-src/manifest-src/worker-src 'self'; connect-src 'self' ws: wss: https://raw.githubusercontent.com https://api.github.com plus CSP_CONNECT_EXTRA or, when that is empty, the origin parsed from the backend's own SENTRY_DSN (SecurityHeaders.kt:57-84,89-91). HONEST WEAK SPOTS, both documented in the source comment at :43-55: style-src still carries 'unsafe-inline' for Radix/sonner/vaul runtime <style> injection, and ws:/wss: are scheme-wide sources because the header is built once while the origin varies. HSTS (max-age=63072000; includeSubDomains; preload) is production-only (:110-112). Tested in SecurityHeadersTest.kt.

> backend:plugins/SecurityHeaders.kt:12-17,40-55,57-84,86-113

### Not present — authentication & session management

- **No CSRF token validation — SameSite=Lax is the whole defense** — GET /api/auth/csrf mints a 32-byte SecureRandom hex token and returns it (CsrfRoutes.kt:27-29), but the token is never stored and never checked — nothing on the server compares it against anything. CONFIRMED by re-grepping case-insensitively for "csrf" across tday-backend/src/main/kotlin: the only non-test hits are that route, the ThrottleAction.csrf enum entry and its rate-limit policy, the route registration in Routing.kt:57, and two observability redaction regexes. No verification middleware exists. I additionally grepped for Origin and Referer header checks: the only hit is Cors.kt:28 allowHeader(HttpHeaders.Origin), i.e. CORS preflight plumbing, not a same-origin check. Practical consequence: every state-changing route (POST/PATCH/DELETE across /api/*) is protected against cross-site forgery only by SameSite=Lax on the session cookie (SessionCookies.kt:11,96) plus the empty-by-default CORS allow-list. Lax does block cross-site POSTs from a third-party page, so this is not wide open — but there is no defense in depth, and with TDAY_ENV unset the cookie also loses Secure and the __Secure- prefix.
- **No multi-factor authentication of any kind** — A password, or a password-proof HMAC over the stored hash, is the only factor. Re-ran grep -rniE "totp|mfa|two.factor|2fa|webauthn|passkey" across tday-backend/src/main/kotlin: the only two hits are the substring "fa" inside AuthThrottle's lockUntilFromFailures (:394, :425) — no TOTP, no WebAuthn/passkeys, no email or SMS second factor, no recovery codes. For a single-user self-hosted deployment behind a Cloudflare Tunnel this is a deliberate simplification, but it means credential compromise is total account compromise. The security questions are a recovery path, not a second factor: they are checked alone, with no password, at SecurityQuestionRoutes.kt:120-179.
- **No session inventory / device list / selective revocation** — Revocation is all-or-nothing. There is no table of active sessions — the only handle is the integer Users.tokenVersion bumped in SQL (SessionControl.kt:29-37), so a user cannot see which devices are signed in, cannot sign out one phone without signing out every device, and an admin cannot enumerate sessions. The AuthSignals table stores only the LAST ip hash and device hash per account for anomaly detection, one row per identifierHash, not a session list (AuthThrottle.kt:250-299). There is also no delivery path for a new-device notification: auth_signal_anomaly is written to the eventlog table and the container log only (AuthThrottle.kt:271-277), with nothing routing it to the account owner. API keys are the one exception — they ARE individually listable and revocable (UserApiKeyService.kt:92-95).
- **requirePasswordChange is advertised but never enforced** — AdminService.resetPassword sets Users.requirePasswordChange = true (AdminService.kt:233) and the flag is faithfully propagated into the auth cache, onto the request claims and out through GET /api/auth/session (Security.kt:114,149,290; SessionRoutes.kt:48). But no server-side code refuses a request because of it. CONFIRMED: grep -rn "requirePasswordChange" returns 11 hits — the column definition (Users.kt:24), writes that set it (AdminService.kt:233) or clear it (UserService.kt:117, SecurityQuestionService.kt:236), and reads that copy it into claims or the session response. There is additionally an unused UserService.requiresPasswordChange(userId) helper (UserService.kt:41,216-220) whose only two grep hits are its own interface declaration and implementation — nothing calls it. Enforcement is purely the client honouring the flag, so a user handed an admin-generated temporary password can keep using it indefinitely via curl, an old app build or an API key.
- **No password-strength check beyond three character-class rules** — The complete policy is length >= 8, at least one uppercase, at least one non-alphanumeric (RegisterRoutes.kt:44-55, identically at UserRoutes.kt:99-101 and SecurityQuestionRoutes.kt:143-154). There is no breached-password/HIBP check, no dictionary or username-similarity check, no entropy estimate, and no maximum length. "Password1!" satisfies every rule. There is also no lowercase or digit requirement. Given the PENDING approval gate and the (IP, account) lockout the practical exposure on this deployment is limited, but the stored hash is password-equivalent on the password-proof path, so a weak password plus a database read is directly exploitable.
- **Session tokens minted without a tokenVersion claim are unrevocable** — The interceptor check is `if (claims.tokenVersion == null || claims.tokenVersion == user.tokenVersion)` (Security.kt:143) — a null tokenVersion short-circuits to accept before any comparison. JwtService.encode writes the claim only when non-null (JwtService.kt:98) and the login route sources it from `user["tokenVersion"] as? Int` (CredentialsCallbackRoutes.kt:136), so any row where that read yields null produces a session that survives every subsequent revokeUserSessions call until its own 30-day expiry or the 90-day absolute cap. The column has a non-null default in the schema, so this should not arise on a current database — but the fail-open direction is chosen in code rather than the fail-closed one, and nothing logs when the branch is taken.
- **Per-user throttle, challenge and request-limit state is single-instance, in-process** — Password-proof challenges live in a ConcurrentHashMap (PasswordProof.kt:43), AuthUserCache is a ConcurrentHashMap (AuthUserCache.kt:15), and — the first pass missed this one — the general request rate limiter is also an in-process ConcurrentHashMap of sliding-window buckets (RequestRateLimiter.kt:37-52), which is what enforces the /api/* 180/60s and change-password 8/300s tiers. Only the AuthThrottle sign-in counters are in Postgres (AuthThrottle.kt:320-325). Consequences: a restart drops all outstanding login challenges (clients must re-request) AND resets every request-rate-limit bucket; running more than one backend replica would give each replica its own challenge map, its own 30s auth cache and its own request-limit buckets, so revocation would take effect per-replica and the effective rate limit would multiply by replica count. docker-compose deploys exactly one tday-backend service with no replica configuration, so this is a scaling constraint rather than a live defect — but it is not documented in the config.
- **No account-lockout or notification on the admin-facing side; no admin action audit trail** — ADDED — absent and worth naming for comparison. Admin state changes (approveUser, rejectUser, deleteUser, resetPassword, clearResetRequest) write no SecurityEventLogger entry: I read all five methods (AdminService.kt:77-97, 99-141, 150-209, 211-249, 261-278) and none calls eventLogger — AdminServiceImpl does not even take a SecurityEventLogger dependency (constructor at AdminService.kt:42-45). So there is no record of who approved, rejected, reset or deleted whom, and the generated temporary password is returned in the HTTP response body (AdminRoutes.kt:58-60) with no expiry on it. The eventlog table only ever receives throttle and session events.

---

## Brute-force, rate limiting & abuse control

### Two independent throttling layers

`On by default`

Two unrelated rate-limit systems run on the same request. (A) A global per-path limiter installed as a Ktor pipeline interceptor at ApplicationCallPipeline.Plugins, holding sliding-window timestamp deques (ArrayDeque<Long>) in a ConcurrentHashMap inside the JVM — plugins/RateLimiting.kt:18-37, wired at Application.kt:90; implementation InMemoryRequestRateLimiter at security/RequestRateLimiter.kt:37-148, bound in di/AppModule.kt:88. (B) An auth-specific throttle that stores every bucket as a Postgres row — AuthThrottleImpl at security/AuthThrottle.kt:111-452, bound in di/AppModule.kt:86, table AuthThrottle at db/tables/AuthThrottles.kt:6-24. Neither needs an env var; both use defaults compiled into AppConfig. Layer A is path-driven from a single interceptor; layer B is invoked explicitly by each auth route handler. They share no state and no keys and can block independently — a POST /api/auth/callback/credentials is assessed by api_global (layer A) AND by 3-4 AuthThrottle buckets (layer B: ip, ipUsername, the account ceiling, plus device if the client sent the header). CORRECTED/ADDED: layer A's per-user keying is load-bearing on plugin registration order — configureSecurity() (Application.kt:87) registers its Plugins-phase interceptor, which populates AuthUserKey (plugins/Security.kt:152), BEFORE configureRateLimiting() (Application.kt:90) registers its own. Reorder those two lines and every bucket silently becomes IP-keyed.

> backend:plugins/RateLimiting.kt:18-37; backend:security/RequestRateLimiter.kt:37-148; backend:security/AuthThrottle.kt:111-452; backend:Application.kt:87,90

### Auth throttle state survives container restart (Postgres-backed)

`On by default`

Every auth bucket is a row in the AuthThrottle table (columns id, scope, bucketKey, requestCount, failureCount, windowStart, lockUntil, lastFailureAt, createdAt, updatedAt), read and written inside `newSuspendedTransaction(Dispatchers.IO)` with `.forUpdate()` row locking — AuthThrottle.kt:320-374 (quota) and 376-423 (lockout counter). Consequence for a self-hoster: `docker compose restart`, a redeploy, an OOM-kill or a crash does NOT clear a lockout or a failure counter, and an attacker cannot reset their own backoff by bouncing the process. Concurrency is arbitrated by the DB row lock, not a JVM lock, so it holds across workers. Uniqueness enforced by `uniqueIndex(scope, bucketKey)` at AuthThrottles.kt:21; a second index `index(false, scope, lockUntil)` at :22 backs the retention sweep. Cost: 1 transaction per subject bucket per throttled request (up to 4 on a sign-in), so throttling is not free DB-wise. IMPORTANT CAVEAT: durability depends on AUTH_SECRET being >= 16 chars — see the HMAC control; below that, keys are regenerated per boot and every persisted bucket becomes unreachable. Contrast with layer A, which is pure JVM memory and does reset on restart.

> backend:security/AuthThrottle.kt:320-374,376-423; backend:db/tables/AuthThrottles.kt:6-24

### api_global policy — 180 requests / 60 s on every /api/* path

`On by default`

Applied to any path starting with `/api/` regardless of method (RateLimiting.kt:44-53). windowSec=API_RATE_LIMIT_WINDOW_SEC default 60, maxRequests=API_RATE_LIMIT_MAX default 180 (AppConfig.kt:108-109); reason code `api_rate_limit`. Auth routes are mounted under /api/auth (Routing.kt:37,56), so this ceiling also sits on top of every sign-in, register and recovery call — and, being a pipeline interceptor, it is the ONLY limit that fires before request-body deserialization on those routes. Keyed per authenticated user when the security interceptor has populated a principal, per client IP otherwise (RequestRateLimiter.kt:110-123), so one logged-in user cannot exhaust another's budget. Sliding window (deque of request timestamps trimmed at RequestRateLimiter.kt:125-130), not a fixed window — unlike layer B.

> backend:plugins/RateLimiting.kt:44-53; backend:config/AppConfig.kt:108-109; backend:security/RequestRateLimiter.kt:110-130; backend:plugins/Routing.kt:37,56

### infra policy — 30 requests / 60 s on /health, /api/mobile/probe and /calendar/*

`On by default`

windowSec=INFRA_RATE_LIMIT_WINDOW_SEC default 60, max=INFRA_RATE_LIMIT_MAX default 30, reason `infra_rate_limit` (RateLimiting.kt:55-64, AppConfig.kt:110-111). This is the only rate limit covering the public iCalendar feed at GET /calendar/{token} (CalendarFeedRoutes.kt:21), which is mounted OUTSIDE /api (Routing.kt:34-35, comment: token-in-path, no session/API-key auth) and therefore draws no api_global budget. CORRECTED FRAMING: the first pass called 30/min "the guessing budget against the calendar feed token" — the token is 32 bytes of SecureRandom (CalendarFeedService.kt:235, SECRET_BYTES=32 at :246), i.e. 256 bits, so guessing is not the threat this limit addresses; it is a scraping/DoS and enumeration control. Note the feed bucket is IP-keyed only when the caller carries no session cookie; a logged-in browser hitting the feed is user-keyed instead. /api/mobile/probe is assessed by both api_global and infra (RateLimitingTest.kt:87-102 asserts the recorded policy list is exactly [api_global, infra]).

> backend:plugins/RateLimiting.kt:55-64; backend:config/AppConfig.kt:110-111; backend:routes/CalendarFeedRoutes.kt:21; backend:services/CalendarFeedService.kt:235,246; backend-test:plugins/RateLimitingTest.kt:87-102

### todo_summary policy — 10 requests / 60 s on POST /api/todo/summary

`On by default`

windowSec=SUMMARY_RATE_LIMIT_WINDOW_SEC default 60, max=SUMMARY_RATE_LIMIT_MAX default 10, reason `summary_rate_limit` (RateLimiting.kt:66-75, AppConfig.kt:112-113). This endpoint fans out to the optional Ollama LLM, so the limit is a cost/DoS control on an expensive downstream, not an auth control. RateLimitingTest.kt:104-128 proves the interceptor short-circuits BEFORE the handler runs: with the policy blocked, todoService.timelineCalls==0 and summaryService.generateCalls==0. HONEST NOTE ON THAT TEST: it injects a RecordingRequestRateLimiter fake (RateLimitingTest.kt:55,106) rather than the real InMemoryRequestRateLimiter, so it proves interceptor ordering and short-circuit behaviour, not that the 10-per-60s counting is correct.

> backend:plugins/RateLimiting.kt:66-75; backend:config/AppConfig.kt:112-113; backend-test:plugins/RateLimitingTest.kt:104-128,153-165

### change_password policy — 8 requests / 300 s on POST /api/user/change-password

`On by default`

windowSec=CHANGE_PASSWORD_RATE_LIMIT_WINDOW_SEC default 300, max=CHANGE_PASSWORD_RATE_LIMIT_MAX default 8, reason `change_password_rate_limit` (RateLimiting.kt:77-86, AppConfig.kt:114-115). Protects against online guessing of the CURRENT password by someone holding a stolen session cookie — the handler verifies currentPassword, so without this cap a hijacked session would be an unlimited password oracle. The caller is authenticated, so the bucket is keyed on the user id (RequestRateLimiter.kt:110-116) and rotating source IPs does not widen the budget. Session renewal is deliberately skipped on this path (Security.kt:300-307). Verified to short-circuit: userService.changePasswordCalls==0 when blocked (RateLimitingTest.kt:130-151).

> backend:plugins/RateLimiting.kt:77-86; backend:config/AppConfig.kt:114-115; backend:security/RequestRateLimiter.kt:110-116; backend-test:plugins/RateLimitingTest.kt:130-151

### websocket_connect policy — 30 connections / 60 s on /ws

`On by default`

windowSec=WS_RATE_LIMIT_WINDOW_SEC default 60, max=WS_RATE_LIMIT_MAX default 30, reason `websocket_rate_limit` (RateLimiting.kt:88-97, AppConfig.kt:116-117). Because the interceptor sits at ApplicationCallPipeline.Plugins it runs on the HTTP upgrade request, before the handshake completes and before the route's own auth check (Routing.kt:68-79). `/ws` is not under `/api/`, so this is its only per-path limit. It caps connection churn/handshake floods; it does NOT cap message rate on an established socket — the only per-socket bounds are `maxFrameSize = 64 * 1024L`, `pingPeriod = 15s` and `timeout = 60s` (Application.kt:73-78). HONEST NOTE: this is the one global policy with no test coverage — RateLimitingTest.kt covers api_global, infra, todo_summary and change_password only.

> backend:plugins/RateLimiting.kt:88-97; backend:config/AppConfig.kt:116-117; backend:Application.kt:73-78; backend-test:plugins/RateLimitingTest.kt:53-151

### credentials quota — 12 sign-in attempts / 300 s per bucket

`On by default`

AUTH_LIMIT_CREDENTIALS_WINDOW_SEC=300, AUTH_LIMIT_CREDENTIALS_MAX=12 (AppConfig.kt:120-121), applied at AuthThrottle.kt:139. Fixed-window counter (windowStart + requestCount, reset when the window elapses — AuthThrottle.kt:350-353), applied independently to every subject bucket: credentials:ip, credentials:device (only when the client sent x-tday-device-id) and credentials:ipUsername. The `credentials` action covers six route handlers sharing these buckets: POST /api/auth/callback/credentials (CredentialsCallbackRoutes.kt:52), POST /api/auth/login-challenge (LoginChallengeRoutes.kt:29), GET /api/auth/security-questions (SecurityQuestionRoutes.kt:44), POST /api/auth/verify-security-answers (:75), POST /api/auth/reset-password (:124), POST /api/auth/request-admin-reset (:187). ADDED — this is stronger than the first pass said: recovery does not merely share the quota, it also feeds the LOCKOUT. A wrong security answer calls authThrottle.recordFailure on the same credentials buckets (SecurityQuestionRoutes.kt:107 and :171), so answer guessing arms the same exponential sign-in lock as password guessing from that (IP, account) pair.

> backend:config/AppConfig.kt:120-121; backend:security/AuthThrottle.kt:139,320-374; backend:routes/auth/SecurityQuestionRoutes.kt:44,75,107,124,171,187

### credentials_account ceiling — 50 requests / 900 s per account, quota-only (recently hardened)

`On by default`

NEW. AUTH_LIMIT_CREDENTIALS_ACCOUNT_WINDOW_SEC=900, AUTH_LIMIT_CREDENTIALS_ACCOUNT_MAX=50 (AppConfig.kt:130-131). Enforced only for ThrottleAction.credentials (AuthThrottle.kt:162-175), under a scope string deliberately NOT derived from ThrottleAction — the literal constant `credentials_account:username` (AuthThrottle.kt:42) — so it cannot collide with the per-action buckets. It is the backstop that replaced the old account-wide LOCKOUT: once failures were rebound to (IP, account), a distributed attacker with many IPs would otherwise have had an unbounded budget against one username. It is routed exclusively through consumeRequestQuota and never reaches incrementFailureCounter, which is what keeps it non-punitive. Reason code on block: `auth_limit_account` (AuthThrottle.kt:172). Effect: at most 50 credentials-action requests per 15 min against a single username from the entire internet combined = 200/hour hard ceiling. HONEST CAVEAT the first pass omitted: the account owner shares that budget with the attacker. A distributed attacker saturating the ceiling denies the owner sign-in for the remainder of the window — up to ~15 minutes. It is bounded and self-healing (which is exactly why it was chosen over a lockout), but it is not free of owner impact. A second, subtler point: a request already refused by a locked ipUsername bucket returns before incrementing that bucket (AuthThrottle.kt:341-348) yet still consumes the account ceiling, so a locked-out attacker keeps burning the owner's budget.

> backend:security/AuthThrottle.kt:41-42,123-135,162-175,341-348; backend:config/AppConfig.kt:130-131

### register quota — 6 / hour per bucket, plus a register_burst tier of 3 / 600 s per IP

`On by default`

Two stacked tiers on POST /api/auth/register (RegisterRoutes.kt:25). Tier 1: AUTH_LIMIT_REGISTER_WINDOW_SEC=3600, AUTH_LIMIT_REGISTER_MAX=6 (AppConfig.kt:126-127), applied to the ip, device and — unlike sign-in — the plain `username` bucket, because buildSubjectKeys sends non-credentials actions down the username branch (AuthThrottle.kt:78-84). Tier 2: a stricter IP-only bucket under the hardcoded scope `register_burst:ip` with AUTH_LIMIT_REGISTER_BURST_WINDOW_SEC=600 and AUTH_LIMIT_REGISTER_BURST_MAX=3 (AppConfig.kt:128-129), reason code `auth_limit_ip_burst` (AuthThrottle.kt:180-192). Practical effect on this open-registration app: one IP can create at most 3 accounts per 10 min and 6 per hour. All land PENDING anyway, so this is mostly a row-spam control on the Users table. Register can never produce a lockout, because only ThrottleAction.credentials reaches recordFailure (AuthThrottle.kt:209) — asserted in AuthThrottleSubjectsTest.kt:76-84. Note the throttle is consulted AFTER `call.receive<RegisterRequest>()` (RegisterRoutes.kt:23), so deserialization cost is borne first; only api_global fires earlier.

> backend:security/AuthThrottle.kt:78-84,118-121,180-192,209; backend:config/AppConfig.kt:126-129; backend:routes/auth/RegisterRoutes.kt:23,25

### csrf / sessionGet / credentialsKey quotas — 40, 20 and 20 per 60 s

`On by default`

Three cheap-endpoint quotas, all fixed-window, all quota-only (none can lock, since only credentials reaches recordFailure). csrf: AUTH_LIMIT_CSRF_WINDOW_SEC=60 / AUTH_LIMIT_CSRF_MAX=40 (AppConfig.kt:118-119), enforced at CsrfRoutes.kt:17 — buildSubjectKeys explicitly refuses to key csrf on any identifier (AuthThrottle.kt:78), so it is IP-only plus device when hinted; asserted in AuthThrottleSubjectsTest.kt:86-91. sessionGet: 60 s / 20 (AppConfig.kt:122-123), enforced at SessionRoutes.kt:23 on GET /api/auth/session, the endpoint every client polls for login state. credentialsKey: 60 s / 20 (AppConfig.kt:124-125), enforced at CredentialsKeyRoutes.kt:17 on GET /api/auth/credentials-key, which hands out the RSA public key used to envelope-encrypt credentials. These cap token-farming and key-fetch floods, nothing more. Not throttled at all in this family: POST /api/auth/logout (LogoutRoutes.kt has no AuthThrottle reference) — it is covered only by api_global.

> backend:config/AppConfig.kt:118-125; backend:routes/auth/CsrfRoutes.kt:17; backend:routes/auth/SessionRoutes.kt:23; backend:routes/auth/CredentialsKeyRoutes.kt:17; backend-test:security/AuthThrottleSubjectsTest.kt:86-91

### Exponential lockout — threshold 5, base 30 s, doubling, capped at 1800 s, 24 h reset

`On by default`

Parameters (AppConfig.kt:132-135): AUTH_LOCKOUT_FAIL_THRESHOLD=5, AUTH_LOCKOUT_BASE_SEC=30, AUTH_LOCKOUT_MAX_SEC=1800, AUTH_LOCKOUT_RESET_SEC=86400. Formula at AuthThrottle.kt:425-433: below 5 failures no lock; otherwise lock = min(1800, 30 * 2^(failures-5)) seconds. Failure 5→30 s, 6→60, 7→120, 8→240, 9→480, 10→960, 11→1920 clamped to 1800, and every failure after →1800 s. The counter resets only after 86400 s with no failure (AuthThrottle.kt:386-391) or on a successful sign-in / successful answer verification (clearFailures, AuthThrottle.kt:235-248). An existing longer lock is never shortened — `laterDateTime` keeps the max (AuthThrottle.kt:396,435-439). GUESSES PER HOUR AGAINST ONE ACCOUNT FROM ONE IP: guesses 1-5 land immediately (the 12/300 s quota permits them), the 5th arms 30 s; the attacker resumes at t=30 (guess 6, lock 60), t=90 (7, 120), t=210 (8, 240), t=450 (9, 480), t=930 (10, 960), t=1890 (11, 1800 capped), next at t=3690 which is past the hour — 11 guesses in the first hour, then a steady state of one guess per 1800 s = 2/hour indefinitely, because the 24 h decay never elapses under sustained attack. DISTRIBUTED CEILING: with unlimited fresh IPs the lockouts are sidestepped and the binding constraint is the account quota, 50/900 s = 200 credentials-action requests/hour. A web sign-in spends two of those (login-challenge + callback), so ~100 password guesses/hour worst case; a native client posting the password directly spends one, so 200/hour. Behind that sits PBKDF2 at AUTH_PBKDF2_ITERATIONS default 310,000, floored at 100,000 by `coerceIn(100_000, 2_000_000)` (AppConfig.kt:95-96) so an operator cannot configure it dangerously low.

> backend:security/AuthThrottle.kt:376-433,435-439; backend:config/AppConfig.kt:95-96,132-135

### Throttle dimensions and the LOCKABLE_DIMENSIONS guard (recently hardened)

`On by default`

Four dimensions exist (AuthThrottle.kt:30): `ip`, `username`, `device`, `ipUsername`. buildSubjectKeys (AuthThrottle.kt:64-86) always emits an `ip` bucket; emits `device` only when the client sent x-tday-device-id; emits nothing identifier-keyed for csrf; emits `ipUsername` (pre-hash material "$ip|$normalizedIdentifier") for credentials; and emits plain `username` for every other identifier-bearing action. LOCKABLE_DIMENSIONS = {ip, device, ipUsername} (AuthThrottle.kt:50-54), and recordFailure filters the subject list through it before calling incrementFailureCounter (AuthThrottle.kt:216). QUOTA vs LOCKOUT — the distinction the guard encodes: a quota is a fixed-window counter that self-heals at window end, so its worst-case wait is bounded by the window (900 s for the account ceiling); a lockout compounds across failures, is bounded only by lockoutMaxSec, and its counter does not decay for 24 h. An account-scoped LOCKOUT would therefore be a remote denial of service against the server's own owner — anyone who knows the username could, for one wrong password per backoff window, keep the owner locked out of their own self-hosted server from anywhere. That is why the account ceiling is enforced through consumeRequestQuota only and `username` is excluded from LOCKABLE_DIMENSIONS. HONEST CAVEAT: the `device` dimension keys on a client-supplied header with no attestation (ClientSignals.kt:39-42, truncated to 128 chars), so a deliberate attacker omits or randomizes it — telemetry-grade separation for honest clients, not an attacker control.

> backend:security/AuthThrottle.kt:23-30,44-54,64-86,216; backend:security/ClientSignals.kt:39-42

### Composite (IP, account) sign-in keying, unit-tested (recently hardened)

`On by default`

NEW. For ThrottleAction.credentials the identifier bucket is `credentials:ipUsername` with pre-hash material "$ip|$normalizedIdentifier" (AuthThrottle.kt:79-81). Identifier normalization is trim + lowercase (ClientSignals.kt:44-47), so casing cannot be used to mint fresh buckets. buildSubjectKeys, makeSubjectKey and reasonCodeFor are `internal` pure functions — no DB, no clock, no ApplicationRequest — specifically so the keying rules are testable without a test database (this repo has none). AuthThrottleSubjectsTest.kt covers 11 cases: no lockable username-only bucket for sign-in (:29-39); the same account from two IPs lands in different bucketKeys under the same scope (:41-54); same account + same IP is one bucket (:56-64); different accounts from one IP are different buckets (:66-74); register keeps the plain username bucket (:76-84); csrf keys on nothing but IP (:86-91); the device bucket appears only when hinted (:93-101); a missing identifier still yields an ip bucket (:103-108); `username` is not in LOCKABLE_DIMENSIONS (:110-117); scope strings pinned as "credentials:ip" and "credentials:ipUsername" (:119-127); and — omitted by the first pass — reasonCodeFor collapses ip/device/ipUsername to `auth_limit_ip` while username maps to `auth_limit_username` (:129-135). SCOPE OF THE TESTING CLAIM: these cover keying only. Nothing below buildSubjectKeys — the quota arithmetic, the lockout formula, clearFailures — has any test.

> backend:security/AuthThrottle.kt:56-93; backend:security/ClientSignals.kt:44-47; backend-test:security/AuthThrottleSubjectsTest.kt:29-135

### Real 429 with Retry-After on every throttled path (recently hardened)

`On by default`

A single helper, `ApplicationCall.respondRateLimit`, appends HttpHeaders.RetryAfter and responds 429 TooManyRequests with a JSON body {message, reason, retryAfterSeconds} — domain/AuthContext.kt:25-39. CORRECTED COUNT: there are 10 auth call sites across 7 route files, not 7 sites — CredentialsCallbackRoutes.kt:58, RegisterRoutes.kt:27, LoginChallengeRoutes.kt:31, CsrfRoutes.kt:19, CredentialsKeyRoutes.kt:19, SessionRoutes.kt:25, and SecurityQuestionRoutes.kt:46, :77, :126, :189 — plus the global interceptor at RateLimiting.kt:28-35. These previously built the body as Map<String, Any>, which kotlinx.serialization cannot serialize, so respond threw, StatusPages caught it and the caller got a 500 with no Retry-After (the regression is documented in AuthRateLimitResponseTest.kt:31-42). Retry-after values are computed, not guessed: from lockUntil for a lock (AuthThrottle.kt:345), from window end for a quota (:366-371), from the oldest timestamp in the deque for the in-memory limiter (RequestRateLimiter.kt:132-136), always floored at 1 s. TEST COVERAGE, stated precisely: AuthRateLimitResponseTest.kt asserts status 429 + Retry-After header + body reason/retryAfterSeconds for 8 of the 10 auth sites (callback, login-challenge, register, csrf, security-questions GET, verify-answers, reset-password, request-admin-reset — :46-134); GET /api/auth/session and GET /api/auth/credentials-key are not covered. RateLimitingTest.kt:203-213 asserts the same contract for 4 of the 5 global policies (websocket_connect untested). The web client parses retryAfterSeconds into its ApiError (tday-web/src/lib/api-client.ts:100-106) but performs no automatic backoff.

> backend:domain/AuthContext.kt:25-39; backend:plugins/RateLimiting.kt:28-35; backend-test:routes/auth/AuthRateLimitResponseTest.kt:31-42,46-134; backend-test:plugins/RateLimitingTest.kt:203-213

### Strongest-block selection across stacked buckets

`On by default`

When several buckets block the same request the response reflects the strongest one rather than the first. In the auth throttle, pickStronger (AuthThrottle.kt:446-451) prefers a candidate whose reasonCode is `auth_lockout` over a plain quota block, and otherwise prefers the larger retryAfterSeconds. In the global limiter, the interceptor evaluates every matching policy, filters to the blocked ones and takes `maxByOrNull(retryAfterSeconds)` (RateLimiting.kt:23-26). The auth throttle also assesses ALL subjects before deciding (AuthThrottle.kt:151-157) rather than returning on the first block, so an attacker cannot leave one bucket un-incremented by tripping another first. TWO PRECISION FIXES: (1) the lockout preference is not absolute — pickStronger's second clause means a quota block with a longer retryAfterSeconds beats an already-selected shorter lockout; (2) "requestCount is consumed on every bucket uniformly" is not quite true — a bucket with an active lockUntil returns at AuthThrottle.kt:341-348 without incrementing its own requestCount, while the other buckets and the account ceiling still increment.

> backend:security/AuthThrottle.kt:151-157,341-348,446-451; backend:plugins/RateLimiting.kt:23-26

### Bucket keys are HMAC-SHA256 under AUTH_SECRET, never raw usernames or IPs

`On by default`

Everything that becomes a bucket key passes through hashSecurityValue: HmacSHA256, key = AUTH_SECRET as UTF-8 bytes, output hex (ClientSignals.kt:49-53). Pre-hash material is domain-separated by dimension — makeSubjectKey hashes "${dimension.name}:$value" (AuthThrottle.kt:93) — so an ip value and a username value cannot collide; the account ceiling hashes "username:$norm" (AuthThrottle.kt:167) and the register burst bucket hashes "ip:$ip" (:184). Privacy property: the AuthThrottle table's bucketKey column and the AuthSignal table's identifierHash / lastIpHash / lastDeviceHash columns (db/tables/AuthSignals.kt:8-10) contain no plaintext usernames or IP addresses, so a DB dump or backup does not directly reveal who tried to log in from where. The same HMAC is applied to the in-memory limiter's keys (RequestRateLimiter.kt:114,121), so heap dumps are covered too. CAVEAT 1 — this is not anonymization against someone holding AUTH_SECRET: usernames and IPv4 addresses are tiny preimage spaces, trivially re-derived by enumeration, and the secret lives in the same .env.docker the app reads. CAVEAT 2 — if AUTH_SECRET is shorter than 16 characters, ClientSignalsImpl prints `[security] auth_secret_missing using fallback hash key` to stderr and generates a fresh 32-byte SecureRandom key per boot (ClientSignals.kt:16-25), which silently makes every persisted bucket unreachable, i.e. all lockouts and quotas reset on each restart. AUTH_SECRET itself is mandatory (AppConfig.kt:91-92 errors if absent) but its length is never enforced, and the warning goes to stderr only — no security event, no startup abort.

> backend:security/ClientSignals.kt:16-25,49-53; backend:security/AuthThrottle.kt:88-93,167,184; backend:db/tables/AuthSignals.kt:8-10; backend:config/AppConfig.kt:91-92

### Security event codes and where they land

`On by default`

Codes emitted by this domain. From the auth throttle (AuthThrottle.kt:194-204, logging blocked.reasonCode): `auth_limit_ip` — which covers the ip, device AND ipUsername dimensions, per reasonCodeFor at AuthThrottle.kt:96-101, deliberately so the HTTP response does not disclose which dimension tripped; `auth_limit_username`; `auth_limit_account` (:172); `auth_limit_ip_burst` (:190); `auth_lockout` (returned at :344 and emitted on failure accrual at :225). Alert codes `auth_alert_lockout_burst` (:227) and `auth_alert_ip_concentration` (:231). Anomaly code `auth_signal_anomaly` (:271). From the global limiter: `request_rate_limit_triggered` with policy, reason, subjectType, retryAfterSeconds and a sanitized path (RequestRateLimiter.kt:88-99). Every code goes three places via SecurityEventLoggerImpl.log (SecurityEventLogger.kt:25-54): the SLF4J logger named "security" at WARN with a `[security]` prefix (:34), a Sentry breadcrumb in category "security" carrying only the reasonCode (:35-39), and an INSERT into the `eventLog` table with the JSON payload truncated to 500 chars (:42-50). The DB write is wrapped in try/catch (:51-53) so a logging failure can never break the request path. Path sanitization on the rate-limit event is load-bearing and verified: /calendar/{token} carries a 32-byte secret in the path and this event is persisted, so it goes through TdayObservability.sanitizePath (RequestRateLimiter.kt:95-98), whose sanitizeSegment collapses any segment longer than 24 chars — or containing a digit, ':', '_' or '-' — to ":id" (TdayObservability.kt:141-159).

> backend:security/AuthThrottle.kt:96-101,172,190,194-204,224-232,271; backend:security/RequestRateLimiter.kt:88-99; backend:security/SecurityEventLogger.kt:25-54; backend:observability/TdayObservability.kt:141-159

### Failure-counter clearing on legitimate success

`On by default`

clearFailures (AuthThrottle.kt:235-248) zeroes failureCount and nulls lockUntil and lastFailureAt for every subject bucket of the credentials action, in one transaction. Called on successful password sign-in after the approval check passes (CredentialsCallbackRoutes.kt:127), on successful security-answer verification (SecurityQuestionRoutes.kt:105), and on a successful self-service reset (SecurityQuestionRoutes.kt:158). Two properties worth stating precisely. (1) It clears buckets built from the CURRENT request's IP (buildSubjects at AuthThrottle.kt:308-315), so a legitimate owner signing in from home clears their own (IP, account) lock but does not clear an attacker's bucket from a different IP — the intended asymmetry. (2) It does not touch the account-ceiling bucket under CREDENTIALS_ACCOUNT_SCOPE, since that subject is constructed separately at AuthThrottle.kt:165-169 and is not in the buildSubjects list; the ceiling keeps counting through a successful login until its 900 s window rolls. Note also that clearFailures resets requestCount for none of the buckets — it only zeroes the failure/lock fields, so the 12/300 s quota is unaffected by a successful sign-in.

> backend:security/AuthThrottle.kt:235-248,308-315,165-169; backend:routes/auth/CredentialsCallbackRoutes.kt:127; backend:routes/auth/SecurityQuestionRoutes.kt:105,158

### In-memory limiter bounded-growth cleanup

`On by default`

The global limiter's ConcurrentHashMap (RequestRateLimiter.kt:52) would otherwise grow one entry per distinct (policy, subject) forever, a memory-exhaustion vector for an unauthenticated attacker rotating IPs. maybeCleanup (RequestRateLimiter.kt:138-147) runs on every 256th assessment (`accessCount.incrementAndGet() % 256L`) and removes buckets whose timestamp deque is empty and whose lastSeenAt is older than the bucket's own window. Per-bucket mutation is under `synchronized(bucket)` (RequestRateLimiter.kt:70-79 and 142-145) so trimming and counting are atomic. TRADE-OFFS TO STATE PLAINLY: this is a per-JVM structure, so layer-A limits are per-process and reset on every restart or redeploy — only the auth throttle survives a bounce. And there is no hard cap on map size; the bound is eventual (entries survive until the next 256th call after their window expires), not absolute, so a burst of N distinct IPs still allocates N entries before any are reclaimed.

> backend:security/RequestRateLimiter.kt:52,66-79,138-147

### Login-challenge memory cap (5000 active challenges)

`On by default`

The password-proof flow issues a server-held challenge per /api/auth/login-challenge call, which an unauthenticated attacker can trigger at will. Two bounds keep that from being a memory DoS: AUTH_PASSWORD_PROOF_CHALLENGE_TTL_SEC=120 (AppConfig.kt:139) with pruneExpired removing anything past expiry (PasswordProof.kt:138-140), and AUTH_PASSWORD_PROOF_MAX_ACTIVE=5000 (AppConfig.kt:140) enforced by evictOldest, which removes entries until the map is below the cap before each insert (PasswordProof.kt:66-68,142-148). The endpoint is additionally under the credentials quota (12/300 s per bucket) and the 200/hour account ceiling. CORRECTION — the function is misnamed and the first pass repeated the misnomer: `challenges` is a ConcurrentHashMap (PasswordProof.kt:43), whose iteration order is by hash bucket, not insertion. `challenges.keys.firstOrNull()` therefore evicts an ARBITRARY entry, not the oldest. The 5000-entry cap is real and enforced; the eviction policy is effectively random, so under pressure a legitimate user's in-flight challenge can be dropped as readily as an attacker's (the client simply has to restart the login, and the TTL is 120 s regardless).

> backend:security/PasswordProof.kt:43,66-68,138-148; backend:config/AppConfig.kt:139-140

### Account-recovery fail counter, independent of the throttle

`On by default`

Separate from AuthThrottle, wrong security answers increment a persistent per-user column Users.securityQuestionFailCount (db/tables/Users.kt:25, incremented at SecurityQuestionService.kt:173 and :246). Recovery is refused once that count is strictly greater than SECURITY_QUESTION_FAIL_LIMIT, which is 3 (SecurityQuestionService.kt:26) — checked at :145 (verifyAnswers) and :206 (verifyAndReset). CONCRETE NUMBER the first pass omitted: because the comparison is `> 3` and the check happens before the increment, four wrong recovery attempts are permitted and the fifth is refused, surfaced as HTTP 403 with reason `reset_locked` (SecurityQuestionRoutes.kt:94-103 and :161-169). Recently hardened: pendingAdminReset no longer gates this lock — the comments at SecurityQuestionService.kt:140-144 and :201-205 record the reasoning, that pendingAdminReset is a notification any unauthenticated stranger can raise for any username, whereas the fail counter must be paid for with actual wrong answers. So only the counter can lock recovery, and it cannot be armed by a stranger for free. RESET PATHS: the counter is zeroed on a successful self-service reset (SecurityQuestionService.kt:237) and by admin action (AdminService.kt:236, :269) — notably NOT by a merely successful verifyAnswers, which only skips the increment.

> backend:services/SecurityQuestionService.kt:26,140-148,170-177,201-207,237; backend:services/AdminService.kt:236,269; backend:routes/auth/SecurityQuestionRoutes.kt:94-103,161-169

### Rate-limit parameters fail safe on bad configuration

`On by default`

Every rate-limit and lockout parameter is read through `envInt` (AppConfig.kt:189-192), which does `raw.toIntOrNull()?.takeIf { it > 0 } ?: default`. A malformed, zero or negative environment override therefore silently falls back to the compiled-in default rather than being applied. Practical consequence for comparison: an operator cannot accidentally (or a bad .env cannot maliciously) disable any limit by setting API_RATE_LIMIT_MAX=0 or AUTH_LOCKOUT_FAIL_THRESHOLD=-1 — every such value reverts to 180 and 5 respectively. Two parameters get explicit clamps on top: AUTH_PBKDF2_ITERATIONS is `coerceIn(100_000, 2_000_000)` (AppConfig.kt:95-96) and RETENTION_CRONLOG_DAYS is `coerceAtLeast(7)` (:161). HONEST LIMIT OF THIS PROPERTY: it only prevents disabling a limit, not weakening one — API_RATE_LIMIT_MAX=1000000 is accepted as-is, and the retention day counts genuinely treat 0 as "disabled" downstream (RetentionScheduler.kt:127), which envInt reaches by falling back to the default instead.

> backend:config/AppConfig.kt:95-96,161,189-192; backend:services/RetentionScheduler.kt:127

### Container-level abuse containment (pids_limit, no-new-privileges, cap_drop, loopback bind)

`On by default`

Compose-level controls bounding resource abuse the application limits do not catch. `pids_limit: 512` caps process/thread creation per container so a fork-bomb or runaway thread-per-connection pattern cannot exhaust host PIDs — docker-compose.yaml:6 (database), :28 (ollama), :75 (tday-backend). CORRECTION: that is three of the four defined services, not all — `ollama-model-setup` (:43-58) has no pids_limit; it is a short-lived pull job behind the `ai` profile, as is ollama itself (:26,:47). The backend publishes only `${TDAY_HOST_BIND:-127.0.0.1}:${TDAY_HOST_PORT:-2525}:8080` (:77), so there is no inbound listener on any routable interface and all public traffic is funnelled through the Cloudflare Tunnel — which is also what makes the cf-connecting-ip trust in ClientSignals defensible. ADDED, missed by the first pass: the backend also runs with `security_opt: no-new-privileges:true` (:96-97) and `cap_drop: ALL` (:98-99), and has a real readiness healthcheck against /health (:90-95). ADDED HONEST GAP IN THE SAME FILE: memory limits are deliberately NOT set on any service, and the comment at :71-74 explains why — a JVM under `restart: always` would OOM-crash-loop on a too-low value. So a memory-exhaustion DoS is bounded only by the host.

> docker-compose.yaml:6,28,43-58,71-77,90-99

### Throttle-table retention that preserves live lockouts (recently hardened)

`Needs config`

NEW. RetentionScheduler runs a loop on a 6-hour interval (TICK_INTERVAL = Duration.ofHours(6), RetentionScheduler.kt:162) and ages out four security bookkeeping tables, closing a disk-fill vector: these tables previously grew on every request an attacker made and nothing deleted from them, so refusing traffic cost the server more than serving it (KDoc at RetentionScheduler.kt:30-44). Defaults: eventLog 90 d (RETENTION_EVENTLOG_DAYS), authThrottle 30 d, authSignal 180 d, cronLog 90 d floored at 7 (AppConfig.kt:158-161); 0 disables a table (RetentionScheduler.kt:127). Deletes are batched at BATCH_LIMIT=5000 per transaction (:139-143,163). The load-bearing rule for this domain is at RetentionScheduler.kt:80-86: the authThrottle DELETE is conditioned on `updatedAt < cutoff AND (lockUntil IS NULL OR lockUntil < now)`, so a row still serving a lockout is never dropped — otherwise retention would have handed attackers a free lockout reset. A fifth sweep (:94-113) clears pendingAdminReset flags older than ADMIN_RESET_REQUEST_TTL_DAYS. Marked requires-config because it ships inert: RETENTION_DRY_RUN defaults to "true" (AppConfig.kt:164) and the operator must set it to false for any row to be removed. CORRECTION to the first pass: in dry-run the table purges do NOT count what they would delete — the code at RetentionScheduler.kt:130-134 deliberately skips the count query and just logs the cutoff and records "<table>=dry-run". Only the pendingAdminReset sweep runs a real count in dry-run (:97-102).

> backend:services/RetentionScheduler.kt:30-44,77-92,94-113,121-148,162-163; backend:config/AppConfig.kt:158-164

### Client IP resolution — cf-connecting-ip first, then x-forwarded-for, then x-real-ip, then socket

`Partial`

getClientIp (ClientSignals.kt:27-37) checks in strict order: `cf-connecting-ip` (trimmed, non-empty), then the first non-empty comma-separated entry of `x-forwarded-for`, then `x-real-ip`, then `request.local.remoteAddress`. There is NO trusted-proxy allowlist and no check that the request actually arrived from Cloudflare — whatever header is present is believed. In this deployment that is load-bearing on network topology rather than on code: docker-compose.yaml:77 binds the backend to `${TDAY_HOST_BIND:-127.0.0.1}:${TDAY_HOST_PORT:-2525}:8080`, so the container port is reachable only from host loopback and the sole public path is the Cloudflare Tunnel; Cloudflare overwrites cf-connecting-ip on ingress, so a remote attacker coming through the tunnel cannot forge it or choose their own bucket. The residual exposure is local: any process that can reach 127.0.0.1:2525 can set cf-connecting-ip per request and get a fresh ip and ipUsername bucket every time, defeating the IP-dimension lockout entirely and leaving only the 50/900 s account ceiling. Note also that the ordering is what makes the per-IP dimension meaningful at all — without a proxy that supplies a real client IP, remoteAddress would be the same value for everyone and the ip dimension would collapse into one global bucket.

> backend:security/ClientSignals.kt:27-37; docker-compose.yaml:77

### Abuse alert heuristics — IP failure concentration and lockout burst

`Partial`

Two threshold heuristics fire inside recordFailure (AuthThrottle.kt:224-232). (1) If the longest lock just computed is >= AUTH_ALERT_LOCKOUT_BURST_SEC (default 900 s, AppConfig.kt:137), emit `auth_alert_lockout_burst` — i.e. the backoff has escalated to at least 15 minutes, roughly 10+ failures on one bucket. (2) If the running failureCount on the `ip` dimension specifically (captured at AuthThrottle.kt:219-221) reaches AUTH_ALERT_IP_FAILURE_THRESHOLD (default 12, AppConfig.kt:136), emit `auth_alert_ip_concentration` with the count — one source IP failing against many accounts. Why this is `partial`: the heuristics compute correctly and persist, but nothing consumes them. A grep for `auth_alert` over the whole backend and web tree finds only the two emit sites at AuthThrottle.kt:227 and :231. There is no admin route, no web UI query, no push/email notification, and no Sentry-level event — only a breadcrumb, which is attached to some later event or discarded. The owner learns about an attack only by reading container logs or querying eventLog by hand.

> backend:security/AuthThrottle.kt:219-232; backend:config/AppConfig.kt:136-137; grep -rn "auth_alert" over tday-backend/src and tday-web/src returns only AuthThrottle.kt:227,231

### Auth signal anomaly detection (impossible-travel-lite)

`Partial`

On every SUCCESSFUL sign-in, recordSuccessSignal (AuthThrottle.kt:250-299) upserts one row per account into the AuthSignal table holding HMACs of the last IP and last device hint plus lastSeenAt (db/tables/AuthSignals.kt:6-20). If a new successful sign-in arrives within AUTH_SIGNAL_ANOMALY_WINDOW_SEC (default 86400 s = 24 h, AppConfig.kt:138) of the previous one AND both the IP hash and the device hash have changed, it emits `auth_signal_anomaly` with reason "ip_and_device_changed" (AuthThrottle.kt:267-278). It requires BOTH to change, so a phone moving between WiFi and cellular does not fire it. Wired at CredentialsCallbackRoutes.kt:128, after the approval check passes. It also needs a device hint to fire at all — the guard at AuthThrottle.kt:267 requires deviceHash != null — so browser sign-ins without x-tday-device-id are never evaluated. STATUS CORRECTED from on-by-default to partial for the same reason as the alerts: the detection runs, but the resulting event has no consumer (no admin route, no UI, no notification), so it changes nothing unless the owner reads logs or queries eventLog.

> backend:security/AuthThrottle.kt:250-299; backend:db/tables/AuthSignals.kt:6-20; backend:routes/auth/CredentialsCallbackRoutes.kt:128; backend:config/AppConfig.kt:138

### Not present — brute-force, rate limiting & abuse control

- **No CAPTCHA anywhere — and no AUTH_CAPTCHA_* env vars exist either** — There is no CAPTCHA, proof-of-work, or any other human-verification challenge on registration, sign-in, or account recovery. Registration is open, so an automated client can create accounts at the register_burst rate (3 per 10 min, 6 per hour per IP) with no human check; the only thing that makes this tolerable is that every account after the first lands PENDING and cannot log in. Correcting one expectation explicitly: the AUTH_CAPTCHA_* environment variables are not merely dead, they do not exist in the tree at all. A case-insensitive grep for "captcha" across the entire repository (excluding node_modules/.git/build/.gradle) returns exactly one hit, and it is unrelated — the string "captcha" listed as a sensitive query-parameter key to be redacted from telemetry, at backend:observability/TdayObservability.kt:58. Nothing reads a captcha config value because no such value is defined; the AppConfig constructor (AppConfig.kt:5-76) and AppConfig.load() (:78-174) have no captcha field.
- **No fail2ban, host firewall integration, or any IP-banning outside the process** — Nothing exports throttle decisions to the host. There is no fail2ban jail, no filter regex, no iptables/nftables call, no Cloudflare WAF rule pushed via API, and no shared blocklist. Practical consequence: an attacker's traffic always reaches the Ktor process and always costs at minimum a Postgres SELECT ... FOR UPDATE per subject bucket (AuthThrottle.kt:320-374), even while fully locked out — refusing a request is more expensive than a cache lookup would be. The security event log emits well-formed, greppable lines prefixed "[security]" (SecurityEventLogger.kt:34) that a fail2ban filter could consume, but no such integration exists in this repo. Refinement to the first pass's evidence: `ufw` does appear in the tree, but only inside docs/remote-access/{tailscale,zerotier,wireguard,frp}.md as manual firewall snippets for ALTERNATIVE remote-access topologies the owner is not using; nothing in docker-compose.yaml, Dockerfile.backend or any Kotlin source invokes a firewall.
- **No request body size cap** — There is no maximum request body size anywhere in the backend. A grep across the whole Kotlin main source set for maxRequestSize, ContentLength/contentLength checks, DoubleReceive, requestQueueLimit, maxChunkSize or maxInitialLineLength returns nothing. Handlers call `call.receive<T>()` directly and validation of field lengths happens only after deserialization. Sharpening the first pass: on the auth routes the body is deserialized BEFORE the auth throttle is consulted — RegisterRoutes.kt:23 then :25, LoginChallengeRoutes.kt:22 then :29, SecurityQuestionRoutes.kt:73 then :75, and CredentialsCallbackRoutes.kt:26 receives the body and then performs RSA envelope decryption (:31-45) before reaching enforceRateLimit at :52. So the only limit that fires ahead of that work is the api_global interceptor. The nearest thing to a size bound in the process is `maxFrameSize = 64 * 1024L` at Application.kt:76, which applies to /ws frames only. Practical consequence for a single self-hosted user: a caller can post a multi-megabyte JSON body and force the server to buffer and parse it; the per-path request-count limits bound how OFTEN this can happen (180/min on /api/*, 6/hour on register) but not how LARGE each one is. Any body-size protection currently in effect comes from Cloudflare's own upload limit in front of the tunnel, not from this application.
- **No trusted-proxy allowlist for client-IP headers** — getClientIp trusts cf-connecting-ip, then x-forwarded-for, then x-real-ip from any caller, with no verification that the connection originated from Cloudflare or from a known proxy address (ClientSignals.kt:27-37). The deployment topology is what makes this safe against remote attackers — the port is bound to 127.0.0.1 (docker-compose.yaml:77) and Cloudflare rewrites cf-connecting-ip on ingress — so the gap is not remotely exploitable as deployed. But it means the IP-dimension lockout is defended by network configuration alone: anything that can reach 127.0.0.1:2525 on the host (another container with host networking, a compromised sidecar, a local shell) can supply a fresh cf-connecting-ip per request, obtain unlimited fresh ip and ipUsername buckets, and leave only the 50/900 s account ceiling in force. If the owner ever fronts this with a different reverse proxy, or widens TDAY_HOST_BIND, the IP dimension silently becomes attacker-controlled with no code change and no warning — there is no startup log line about proxy trust either.
- **Abuse alerts are emitted but never delivered or surfaced** — auth_alert_ip_concentration, auth_alert_lockout_burst and auth_signal_anomaly are computed and written, then go nowhere. Grepping for consumers of the eventLog table or of the auth_alert codes finds only the emit sites in AuthThrottle.kt (227, 231, 271), the SecurityEventLogger INSERT (SecurityEventLogger.kt:44), the schema-creation list (DatabaseConfig.kt:94) and the retention DELETE (RetentionScheduler.kt:77-79). There is no admin API route that reads eventLog, no query of it in the React web client, no push notification and no Sentry error event — SecurityEventLogger.kt:35-39 adds only a breadcrumb, which is attached to an unrelated later event or dropped. For a single self-hosted user this means a sustained credential-stuffing campaign is fully mitigated but entirely invisible unless the owner manually tails container logs or runs SQL against eventLog.
- **No dedicated rate limits on the admin, webhook, export or notification routes** — resolvePolicies (plugins/RateLimiting.kt:39-98) special-cases exactly five paths and no others: /api/* (api_global), /health + /api/mobile/probe + /calendar/* (infra), POST /api/todo/summary, POST /api/user/change-password, and /ws. Everything else mounted under /api — adminRoutes, webhookRoutes, exportRoutes, notificationRoutes, listShareRoutes (Routing.kt:37-54) — is covered only by the 180-requests-per-60-seconds api_global budget, keyed per user. So an authenticated account can drive 180 export or webhook-creation calls per minute. Given the PENDING-by-default approval gate, every caller on these routes is an admin-approved user, so this is a blast-radius observation about a trusted-but-compromised session rather than an open exposure. POST /api/auth/logout is likewise unthrottled beyond api_global (LogoutRoutes.kt contains no AuthThrottle reference).
- **The rate-limit enforcement logic itself has no test coverage** — Added by this fact-check, because it bears directly on how much weight the numeric parameters above can carry. Every test in this domain exercises wiring, not counting. RateLimitingTest.kt injects a RecordingRequestRateLimiter fake (:55,:153-165) and AuthRateLimitResponseTest.kt injects a BlockingAuthThrottle fake (:161-169,:189-212), so both prove interceptor ordering, short-circuit-before-handler and the 429/Retry-After response contract — but neither ever runs InMemoryRequestRateLimiter's sliding-window arithmetic or AuthThrottleImpl's fixed-window and exponential-lockout arithmetic. AuthThrottleSubjectsTest.kt covers only the pure keying functions above the DB boundary. There is no test asserting that the 12th request in 300 s is allowed and the 13th blocked, that the 5th failure yields exactly 30 s, that the lock doubles, that it clamps at 1800 s, that the 24 h decay works, or that clearFailures actually clears. Root cause is stated in the source itself: the repo has no test database (AuthThrottleSubjectsTest.kt:9-17). Consequence for comparison: the parameter values in this document are read off the configuration and the formula, not verified by execution.
- **No memory limits on any container** — Added by this fact-check as the counterpart to the pids_limit control. docker-compose.yaml sets no mem_limit, no memory reservation and no JVM heap ceiling on any of the four services, and the comment at docker-compose.yaml:71-74 states this is deliberate — a JVM under `restart: always` would OOM-crash-loop on a too-low value, which was judged worse than the DoS it defends against, and the note directs the operator to set mem_limit only after measuring real RSS together with -XX:MaxRAMPercentage. Practical consequence: the in-process memory bounds documented above (the limiter's bucket map cleanup, the 5000-challenge cap) are the only ceilings on memory an unauthenticated attacker can drive; if any of them is exceeded or bypassed, nothing at the container level stops the backend from consuming host memory and taking Postgres or the tunnel down with it.

---

## Authorization, multi-tenancy & sharing

### Single route-level auth gate (withAuth)

`On by default`

Every authenticated endpoint wraps its handler in `call.withAuth { user -> ... }`. The helper resolves the principal from request attributes, refuses if absent (401 AppError.Unauthorized), refuses if approvalStatus != "APPROVED" (403), and only then invokes the block with an `AuthenticatedUser(id, role, approvalStatus, timeZone)`. Because the block returns `Either<AppError, T>`, the Left branch is routed through respondAppError rather than a 200. CORRECTED COUNTS — the first pass reported grep line-hits, not call sites. Actual `call.withAuth` invocations: TodoRoutes 15, UserRoutes 13, FloaterRoutes 9, FloaterListRoutes 6, AdminRoutes 6, ListRoutes 5, ListShareRoutes 5, TaskStepRoutes 5, WebhookRoutes 4, CompletedTodo/CompletedFloater 3 each, Export 2, Preferences 2, Notification 2, AppSettings 1, Timezone 1 = 82 across 16 files. Coverage is exact: counting route handler blocks per file against withAuth call sites, every handler in the /api tree is wrapped except /api/auth/* (pre-login), /api/mobile/probe, and GET /api/notifications/vapid-public-key. CORRECTION — there are three further unauthenticated surfaces the first pass omitted, all mounted OUTSIDE /api: GET /health (Routing.kt:28), GET /calendar/{token} (token-authenticated, see its own entry), and 3 well-known handlers in AppleAppSiteAssociationRoutes.kt (assetlinks.json, apple-app-site-association x2) that serve static app-association JSON.

> backend:domain/AuthContext.kt:69-84; public surfaces at backend:plugins/Routing.kt:28-35,56-65

### Caller identity is server-derived, never client-supplied

`On by default`

The user id handed to the service layer is always `AuthenticatedUser.id`, built from JWT claims that are re-hydrated from the Users row on every request — role, approvalStatus, tokenVersion, timeZone and both requirement flags are overwritten from the DB at Security.kt:144-151, not trusted from the token body. The API-key path builds the identical claim set from the DB at Security.kt:106-117. A grep for a client-supplied actor id across routes/ and services/ returns only two hits, ListShareRoutes.kt:62 and :73, and in both the DTO field is the TARGET member being re-roled or removed while the actor is still `user.id` — no route reads its own identity from a body or query param. CORRECTION — the first pass claimed the `userId: String` first parameter means "the ownership predicate cannot be omitted without a compile error." That is an overclaim: the compiler forces the argument to be PASSED, not to be used in a WHERE clause. The convention is enforced by review, not by the type system.

> backend:plugins/Security.kt:140-152 and :103-118 (hydration, both paths); backend:domain/AuthContext.kt:64-67; target-vs-actor at backend:routes/ListShareRoutes.kt:56-75

### Approval-status gate (PENDING users cannot use the API)

`On by default`

`requireApproved()` hard-compares `approvalStatus != "APPROVED"` and raises Forbidden("your account is awaiting admin approval"). It runs inside withAuth for all 82 authenticated handlers and again at the /ws handshake. approvalStatus is re-read from the DB through AuthUserCache (TTL default 30_000 ms, constructor default at AuthUserCache.kt:14) on both the cookie/JWT path and the API-key path, so a self-registered account holds a valid cookie but every /api route and /ws returns 403 until an admin approves. CORRECTION to the first pass — it claimed "revoking approval takes effect within 30 seconds without needing session revocation." There is no un-approve action in the codebase: `it[Users.approvalStatus]` is written in exactly two places, at registration (UserService.kt:151) and approveUser (AdminService.kt:90, APPROVED only). rejectUser refuses non-PENDING targets. De-provisioning an approved user is done by DELETE /api/admin/users/{id}, which purges every row and calls revokeUserSessions(revokeApiKeys=true) — immediate, not 30s. The 30s window only describes cache staleness of the approve transition.

> backend:domain/AuthContext.kt:51-56; cache TTL at backend:security/AuthUserCache.kt:14; ws enforcement at backend:plugins/Routing.kt:69; no un-approve path — writes to approvalStatus only at backend:services/UserService.kt:151 and backend:services/AdminService.kt:90

### Ownership scoping — userID predicate compiled into every query

`On by default`

The uniform pattern is a WHERE clause carrying `Table.userID eq userId` on read, write and delete. Four verified shapes: (1) TodoService builds two reusable Exposed predicates — `visibleTodos(userId, sharedListIds)` = own rows OR rows in lists shared with me, `mutableTodos(userId, editableListIds)` = own rows OR rows in lists where I am EDITOR; both fall back to bare `Todos.userID eq userId` when the share list is empty, so a viewer's update/delete matches 0 rows rather than erroring. (2) TodoService.update additionally calls `shareService.canEditList(...)` before allowing a todo to be MOVED into a target listID, so a task cannot be relocated into a list the caller only views — this guard was missing from the first pass. (3) ExportService.exportAll runs eight separate `.where { X.userID eq userId }` queries and the import writes `it[X.userID] = userId` on every insert (lines 181, 193, 214, 231 — the first pass cited 180), so importing a bundle naming another user's id cannot re-parent rows. (4) TaskSteps has no userID column and authorises through the parent: `ownsTodo()` requires `(Todos.id eq todoId) and (Todos.userID eq userId)`.

> backend:services/TodoService.kt:528-549 (predicates) and :144-153 (relocation guard + mutableTodos on update); backend:services/ExportService.kt:66-96 and 181,193,214,231; backend:services/TaskStepService.kt:128-132

### Centralised share ACL (single accessFor decision point)

`On by default`

ListShareService is the documented "single source of truth for list-sharing access decisions" (file header, :36-39). `accessForInTx(userId, listId, type)` returns OWNER when Lists.userID/FloaterLists.userID equals the caller, otherwise the share row's role string, otherwise null. Everything derives from it: `canEditList()` = `accessFor(...)?.canEdit == true`, and `sharedListIdsFor(userId, type, editorOnly)` supplies the id lists the Todo/Floater query predicates consume. A null result maps to 404 "list not found", not 403, so a non-member cannot distinguish an inaccessible list from a nonexistent one. The list TYPE (SCHEDULED vs FLOATER) is not client-supplied — ListShareRoutes registers the same handlers twice under /list and /floaterList with the type bound at registration, so a caller cannot claim the wrong domain for a list id.

> backend:services/ListShareService.kt:344-356 (accessForInTx), :85-86 (canEditList), :73-83 (sharedListIdsFor), :128-142 (404 for non-member); type binding at backend:routes/ListShareRoutes.kt:29-34

### Share roles: OWNER / EDITOR / VIEWER

`On by default`

Three roles, defined in shared code so backend and all clients agree. OWNER is implicit (the list's userID column) and is never stored in a share row — `parseMemberRole()` rejects OWNER as assignable (ListShareService.kt:401-402). `canEdit` is `this != VIEWER` (ShareModels.kt:15). Verified server-side enforcement points: member add/updateRole/removeMember all raise Forbidden("only the list owner can manage members") when accessFor != OWNER (ListShareService.kt:155-157, :231-233, :262-264); list rename/recolor/icon raises Forbidden("only the list owner can update the list") for both domains (ListService.kt:161-166, FloaterListService.kt:183-188); `resetFloaters` explicitly raises Forbidden("viewers cannot reset a list") for VIEWER (FloaterListService.kt:204-209); `leave` raises BadRequest("the owner cannot leave their own list"). Task-level writes inside a shared list go through the EDITOR-only `mutableTodos` predicate. Default role when adding a member without specifying one is EDITOR (AddMemberRequest.role default, ShareModels.kt:41).

> shared/src/commonMain/kotlin/com/ohmz/tday/shared/model/ShareModels.kt:10-21 and :38-42; enforcement at backend:services/ListShareService.kt:155-157,231-233,262-264,275-284,401-402; backend:services/ListService.kt:161-166; backend:services/FloaterListService.kt:183-188,204-209

### Owner-only destructive operations on shared containers

`On by default`

Deleting a shared list destroys every member's contents, so deletion bypasses the share ACL and filters directly on ownership: `FloaterLists.deleteWhere { (FloaterLists.userID eq userId) and (FloaterLists.id inList existingIds) }`, with `existingIds` itself pre-filtered by `(FloaterLists.userID eq userId)` before anything is touched. The cascades that follow (CompletedFloaters, Floaters, FloaterListShares) are deliberately NOT userID-filtered, with an inline comment at :255-257 stating the owner-only gate above is the access boundary — this is correct given existingIds is empty for a non-owner and the function returns early at :253. TodoService.demoteToFloater is likewise owner-only (`Todos.userID eq userId` on both the select and the delete) because demoting moves the row out of the shared list.

> backend:services/FloaterListService.kt:245-293; backend:services/TodoService.kt:196-201,224

### Admin authorization (requireAdminAccess)

`On by default`

`requireAdminAccess()` chains `requireApproved().bind()` then `if (role != "ADMIN") raise(Forbidden("admin access required"))`. It is enforced in the SERVICE layer, not the route layer — the route only supplies withAuth (which checks approval, not role), and all six AdminService methods call requireAdminAccess as their first statement, so a route mistake cannot bypass it. Confirmed by grep over the whole backend: requireAdminAccess appears at its definition (AuthContext.kt:58) and at exactly six call sites, all in AdminService (listUsers 48, approveUser 78, deleteUser 100, rejectUser 129, resetPassword 215, clearResetRequest 265). Nothing else in the codebase calls it, so there is no admin-only read path into another user's tasks. AdminUserResponse carries only id/name/username/role/approvalStatus/createdAt/approvedAt/pendingAdminReset/adminResetRequestedAt — never the password hash and never task data. Admin surface is exactly six endpoints under /api/admin/users: GET, PATCH /{id}, DELETE /{id}, POST /{id}/reject, POST /{id}/reset-password, POST /{id}/clear-reset-request.

> backend:domain/AuthContext.kt:58-62; call sites at backend:services/AdminService.kt:48,78,100,129,215,265; routes at backend:routes/AdminRoutes.kt:15-79; response shape at backend:models/response/UserResponses.kt:26-36

### Admin passwords cannot be reset from the admin panel

`On by default`

`AdminService.resetPassword` loads the target row and raises Forbidden("admin account passwords can't be reset here") when `target[Users.role] == UserRole.ADMIN`. This stops one admin — or an attacker holding an admin session — from minting a known password for another admin. Admins rotate their own password through POST /api/user/change-password, which requires the current password (UserRoutes.kt:98-104). On a non-admin target the generated password is exactly 16 characters: 4 guaranteed class members (upper/lower/digit/special, drawn from ambiguity-stripped alphabets that exclude I, O, l, 0, 1) plus 12 filler characters, shuffled with `SecureRandom().asKotlinRandom()`. The reset also sets requirePasswordChange=true, zeroes securityQuestionFailCount, clears pendingAdminReset, and calls revokeUserSessions(revokeApiKeys=true).

> backend:services/AdminService.kt:223-225 (refusal), :284-299 (generator), :230-241 (flags), :246 (revocation)

### clear-reset-request escape hatch with 7-day auto-expiry (recently hardened)

`On by default`

New in this session. Because /api/auth/request-admin-reset is unauthenticated, anyone can raise pendingAdminReset against any username — and a flagged admin previously had no way out, since resetPassword refuses ADMIN targets by design. `AdminService.clearResetRequest` (POST /api/admin/users/{id}/clear-reset-request) zeroes securityQuestionFailCount, clears pendingAdminReset and adminResetRequestedAt, and touches neither the password nor any session; it raises NotFound when the update affects 0 rows. It deliberately allows ADMIN targets including the caller, documented at :251-260 on the reasoning that clearing a notification flag and a failure counter grants no capability an admin lacks. Independently `isResetRequestExpired()` suppresses any request older than ADMIN_RESET_REQUEST_TTL_DAYS = 7 from the listing (both the boolean and the timestamp are nulled out in the response), so a spammed flag stops pinning a user to the top of the panel. Note the expiry is display-only: the underlying Users.pendingAdminReset column is not rewritten by listUsers.

> backend:services/AdminService.kt:25-31 (TTL + predicate), :56-72 (expiry applied in listUsers), :251-278 (clearResetRequest); route at backend:routes/AdminRoutes.kt:65-75

### Admin self-protection: no self-delete, no last-admin delete

`On by default`

`deleteUser` raises BadRequest("you cannot delete your own account") when targetId == admin.id, and when the target is an ADMIN it counts remaining admins with `(Users.role eq ADMIN) and (Users.id neq targetId)` and raises Forbidden("you cannot delete the last admin account") if that count is 0. This matters because there is no promote-to-admin path anywhere: ADMIN is assigned exactly once, in UserService.register, via `if (isFirst) UserRole.ADMIN else UserRole.USER` where isFirst is `Users.selectAll().count() == 0L`. No endpoint or service method writes Users.role after registration, so an orphaned instance could not recover its admin panel.

> backend:services/AdminService.kt:102,112-119; sole role assignment at backend:services/UserService.kt:140,150

### API key ownership scoping and expiry

`On by default`

Keys are formatted `tday_<cuid>_<secret>` where the secret is SECRET_BYTES = 32 bytes of SecureRandom, base64url without padding, stored as `s256$<sha256hex>` and compared with `MessageDigest.isEqual` (constant-time). Listing, single revoke and bulk revoke are all filtered by `UserApiKeys.userID eq userId`, so DELETE /api/user/api-key/{id} against another user's key id returns 404 "api key not found" rather than deleting it. Optional `expiresInDays` is checked on every resolve (`expiresAt != null && !expiresAt.isAfter(now)` → reject) and generate() rejects a non-positive value. Resolution only accepts rows with `enabled eq true`. The list endpoint returns keyPreview (PREVIEW_LENGTH = 4, the secret's last 4 chars) and never the secret. Two honest caveats the first pass omitted: (a) resolveKey still accepts a LEGACY PBKDF2-hashed row via `passwordService.verifyPassword` when the stored hash lacks the `s256$` prefix, and such rows only migrate on regeneration — so "all keys are SHA-256" is not guaranteed on an upgraded instance; (b) the SHA-256 choice is deliberate and documented at :130-133 (single hash, no stretching, because the secret is 256 bits of CSPRNG and this runs on every request).

> backend:services/UserApiKeyService.kt:185-199 (scoped revoke), :201-249 (resolve: enabled 212, expiry 217, constant-time 220-225, legacy PBKDF2 226-230), :251-267 (secret generation + constants)

### Session revocation propagates to every standing credential (recently hardened)

`On by default`

`SessionControl.revokeUserSessions(userId, revokeApiKeys)` increments `Users.tokenVersion` — Security.kt:143 rejects any token whose tokenVersion no longer matches the DB, clearing the cookie and logging auth_session_token_version_mismatch — and invalidates AuthUserCache. When revokeApiKeys=true it ALSO deletes all of the user's API keys AND, new in this session, the calendar feed token, because the ICS URL authenticates on its own and previously survived password changes, leaving every task title exposed after the owner believed access was cut. revokeApiKeys=true is passed on password change (UserRoutes.kt:108), admin reset (AdminService.kt:246) and user purge (AdminService.kt:208); plain logout deliberately leaves both intact. One caveat worth recording: Security.kt:143 treats a NULL tokenVersion claim as matching, so a legacy token issued before the column existed would not be invalidated by a bump.

> backend:security/SessionControl.kt:29-46; token-version check at backend:plugins/Security.kt:143,166-176; call sites at backend:routes/UserRoutes.kt:108 and backend:services/AdminService.kt:208,246

### Absolute session lifetime, independent of renewal

`On by default`

STATUS CORRECTED from requires-config: every window has a code default, so the ceiling exists on a stock deployment. Two separate clocks. Sliding renewal reissues the cookie when `remainingSeconds in 1..sessionRenewThresholdSec` and the absolute ceiling has not passed, skipped on the login callback, logout and change-password paths. Over the top, `isSessionPastAbsoluteLifetime()` compares `sessionStartedAtEpochSec + sessionAbsoluteMaxAgeSec` against now and, once exceeded, clears the cookie and logs auth_session_absolute_expired; the request then proceeds unauthenticated so withAuth returns 401. Concrete defaults from AppConfig: AUTH_SESSION_MAX_AGE_SEC = 2,592,000 (30 days), AUTH_SESSION_ABSOLUTE_MAX_AGE_SEC = 7,776,000 (90 days, coerced into [sessionMaxAgeSec, 31,536,000]), AUTH_SESSION_RENEW_THRESHOLD_SEC = 604,800 (7 days, coerced into [60, sessionMaxAgeSec]). The absolute check no-ops for a claim set with no sessionStartedAtEpochSec, but JwtService.encode always stamps it (defaulting to now), so only pre-existing legacy tokens could lack it.

> backend:security/SessionCookies.kt:55-63,65-74; applied at backend:plugins/Security.kt:128-138,154-165; defaults at backend:config/AppConfig.kt:80-85; claim always stamped at backend:security/JwtService.kt:83,89

### WebSocket handshake authorization

`On by default`

The /ws upgrade runs the same `requireApprovedAuthUser()` as the REST tree — the credential is resolved by the shared ApplicationCallPipeline interceptor, so cookie, Bearer JWT and API key all work identically. On failure the socket closes with CloseReason.Codes.VIOLATED_POLICY and a reason string distinguishing "Unauthorized" from "Pending approval", so a PENDING account cannot open a realtime channel. After the handshake the connection binds to `realtimeService.channelFor(user.id)` — a per-user MutableSharedFlow (replay = 0, extraBufferCapacity = 64) held in a ConcurrentHashMap keyed by user id — so there is no shared bus to subscribe to; events reach a user only if a publisher explicitly names their id. Handshakes are additionally rate-limited by the "websocket_connect" policy (WS_RATE_LIMIT_MAX default 30 per WS_RATE_LIMIT_WINDOW_SEC default 60).

> backend:plugins/Routing.kt:68-84; channel isolation at backend:services/RealtimeService.kt:14-28; ws throttle at backend:plugins/RateLimiting.kt:88-97 with defaults at backend:config/AppConfig.kt:116-117

### Realtime fanout set is derived from share membership

`On by default`

`RealtimePublisher.publishToCollaborators(actorId, event)` sends only to {actor} ∪ `collaboratorIdsFor(actorId)`, which is computed from actual share rows: members of lists the actor owns, plus owners and co-members of lists shared with the actor, then `result -= userId`. Cached under a per-user key with COLLABORATORS_TTL_MS = 60_000. The set is intentionally coarse — all share-connected users rather than the touched list's members — documented at RealtimeService.kt:36-39, and the events themselves carry no task content (e.g. TodoChanged(listId), MembersChanged(listId)), so a spurious event costs a refetch and that refetch is re-authorized normally. The same recipient set drives webhook dispatch and push notifications through `publishTo`.

> backend:services/ListShareService.kt:88-124 (collaboratorIdsFor) and :404-408 (TTL constant); backend:services/RealtimeService.kt:47-67

### Per-user response-cache namespace

`On by default`

Every cache entry is keyed `"$userId:$endpoint"` (plus sorted `key=value` params joined with & when present), so two users cannot collide on a cached response — there is no global key anywhere in CacheServiceImpl. Invalidation is prefix-based on the same namespace: `invalidateForUser` removes every key starting `"$userId:"`, `invalidateUserEndpoint` narrows to `"$userId:$endpoint"`. Default TTL 60_000 ms, lazy sweep every 5 * 60_000 ms triggered on read. When a mutation touches collaborators, `RealtimePublisher.publishTo` calls `cache.invalidateForUser` for each recipient other than the actor before emitting, so a collaborator's event-triggered refetch cannot be served pre-mutation data.

> backend:services/MemoryCache.kt:22-23 (TTL/sweep), :42-50 (prefix invalidation), :54-58 (key construction); cross-user invalidation at backend:services/RealtimeService.kt:55-59

### Mass-assignment posture: explicit @Serializable DTOs

`On by default`

Verified, and it holds. Every request body is a hand-written `@Serializable data class` listing exactly the fields a client may send — no ORM-entity binding, no generic map-to-column path. CreateTodoRequest is `title, description?, priority = "Low", due, rrule?, listID?` — no id, no userID, no order, no completed; the route then calls the service with `user.id` and the insert supplies `it[Todos.userID] = userId`. Same shape for CreateApiKeyRequest (label/scope/expiresInDays only — a client cannot set the key hash or the owning user) and the registration request (no role, no approvalStatus; both decided by `if (isFirst)` in UserService.register). Ktor's Json is configured `ignoreUnknownKeys = true` (with isLenient = true, encodeDefaults = true), so an attacker-supplied `"role":"ADMIN"` or `"tokenVersion":0` is silently dropped rather than bound. The one place a map is used, TodoService.update, is a server-built `Map<String,Any?>` whose keys are whitelisted one at a time in the route from named DTO fields, and the service reads only those eight known keys back out — the map never comes from JSON.

> shared/src/commonMain/kotlin/com/ohmz/tday/shared/model/TodoModels.kt:71-79; backend:models/request/UserRequests.kt:23-29; backend:plugins/Serialization.kt:10-15; whitelist built at backend:routes/TodoRoutes.kt:138-161 and consumed at backend:services/TodoService.kt:154-164

### Uniform error mapping (no authorization-state oracle)

`On by default`

`appErrorStatus()` maps the six AppError variants to fixed codes (NotFound→404, BadRequest→400, Unauthorized→401, Forbidden→403, Conflict→409, Internal→500) and every response goes out as the same `ApiError(code, message, field)` envelope. The catch-all Throwable handler replaces any leaked exception text with "An unexpected error occurred", captures the original to Sentry with a path sanitized through TdayObservability, and logs it server-side. Share-layer authorization failures return 404 "list not found" for a non-member rather than 403, so probing does not confirm that a list id exists. NUANCE the first pass flattened: the 404-not-403 rule applies to NON-members only. A VIEWER or EDITOR who is a real member but attempts an owner-only action gets a genuine 403 ("only the list owner can manage members", "only the list owner can update the list", "viewers cannot reset a list"), which does confirm membership — correctly, since they already know they are a member.

> backend:plugins/StatusPages.kt:33-40,42-56,70-78; 404-for-non-member vs 403-for-member at backend:services/ListShareService.kt:128-142 and :153-157

### Session cookie hardening (supports the authorization model)

`On by default`

Cookies are HttpOnly, Path=/, SameSite=Lax, and Secure when `cookieName.startsWith("__Secure-") || config.isProduction`. The `__Secure-authjs.session-token` name is used when isProduction, the non-prefixed `authjs.session-token` otherwise, and issuing one actively expires the other (maxAge=0) so the two cannot coexist. SameSite=Lax is what stops a cross-site form POST from riding the session into a mutating endpoint; HttpOnly keeps XSS from reading the token to replay as a Bearer. IMPORTANT CONFIG DEPENDENCY: isProduction is `resolveEnvironmentName().equals("production", ignoreCase = true)` — on a deployment whose environment name is anything else, the cookie is issued under the non-prefixed name WITHOUT the Secure flag, so it would be sent over plain HTTP. Behind the Cloudflare Tunnel the browser leg is HTTPS regardless, but the flag itself is environment-gated, not unconditional. Note also `__Secure-` (not `__Host-`) is used, so the prefix does not pin Path or forbid a Domain attribute.

> backend:security/SessionCookies.kt:8-21,84-97; isProduction derivation at backend:config/AppConfig.kt:93

### CORS origin allowlist (no wildcard with credentials)

`On by default`

STATUS CORRECTED from requires-config: the restrictive behaviour is the default and configuration only LOOSENS it. `allowCredentials = true` with an explicit host allowlist built from `config.corsAllowedOrigins`, which is `envCsv("CORS_ALLOWED_ORIGINS")` with no fallback — so an unconfigured instance has an EMPTY allowlist and permits no cross-origin request at all. Each configured entry is parsed as a java.net.URI and accepted only if the scheme is exactly http or https and the host is non-blank, otherwise it is logged ("Skipping invalid CORS origin configuration") and skipped; an explicit port is preserved in the allowHost argument. Methods are enumerated (GET/POST/PATCH/DELETE/OPTIONS) and headers are enumerated (Accept, Authorization, Content-Type, Origin, baggage, sentry-trace). There is no `anyHost()` and no Origin reflection anywhere in the file. Because the SPA is served by the same Ktor process, the normal deployment needs no entry. One detail the first pass omitted: `allowNonSimpleContentTypes = true` is set, which permits application/json on preflighted cross-origin requests from the allowlisted hosts — relevant only if an origin is configured.

> backend:plugins/Cors.kt:19-51; empty default at backend:config/AppConfig.kt:94

### Global per-user request rate limit across the whole /api tree

`On by default`

MISSED BY THE FIRST PASS, and it materially changes the user-enumeration gap. A second ApplicationCallPipeline.Plugins interceptor applies a sliding-window limiter to every request. The "api_global" policy covers any path starting /api/ at API_RATE_LIMIT_MAX = 180 requests per API_RATE_LIMIT_WINDOW_SEC = 60 by default; "infra" covers /health, /api/mobile/probe and /calendar/* at 30/60s; "todo_summary" covers POST /api/todo/summary at 10/60s; "change_password" covers POST /api/user/change-password at 8/300s; "websocket_connect" covers /ws at 30/60s. The bucket subject is the AUTHENTICATED USER when one is present and the client IP otherwise, and the subject key is hashed before use — and because configureSecurity() is installed at Application.kt:87 before configureRateLimiting() at :90, interceptors in the same phase run in that order, so `call.authUser()` is already populated and authenticated traffic really is per-account rather than per-IP. Blocking returns a real 429 with Retry-After via the shared respondRateLimit and logs request_rate_limit_triggered with a sanitized path. This is a throughput ceiling, not an authorization control — it slows enumeration and abuse but authorizes nothing.

> backend:plugins/RateLimiting.kt:14-98; subject resolution at backend:security/RequestRateLimiter.kt:106-118; defaults at backend:config/AppConfig.kt:108-117; install order at backend:Application.kt:87,90

### Calendar feed: bearer-token-in-path, read-only, owner-scoped

`Needs config`

Off until the user generates a feed (no token row exists by default). Mounted OUTSIDE /api and outside the session/API-key flow because Apple/Google Calendar cannot send headers — the opaque path token IS the credential. Format `<cuid>_<secret>` where the secret is SECRET_BYTES = 32 bytes of SecureRandom, stored as a SHA-256 hash and compared with MessageDigest.isEqual; only `enabled eq true` rows resolve. One active token per user: generate() deletes any previous row in the same transaction before inserting. The route is GET-only, strips a `.ics` suffix, and the renderer scopes strictly to the token owner: `Todos.selectAll().where { Todos.userID eq userId }`. Any failure — blank, malformed, unknown id, disabled, wrong secret, or an exception — returns a bare 404 with no distinguishing body. The path is rate-limited by the "infra" policy (30 requests per 60s by default) and its path is sanitized before being written to the event log so the secret never lands in eventlog. Revocation is now wired into every credential-rotation event via SessionControl.

> backend:routes/CalendarFeedRoutes.kt:12-34; backend:services/CalendarFeedService.kt:82-93 (one token per user), :126-161 (resolve), :165 (owner-scoped render), :235,246 (32-byte secret); throttle at backend:plugins/RateLimiting.kt:55-64

### API key scopes (READ / FULL) enforced pre-routing

`Partial`

Two scopes exist. Enforcement lives in the ApplicationCallPipeline.Plugins interceptor, BEFORE any route matches: a resolved READ key on a non-safe method gets 403 with reason "api_key_read_only" and the pipeline is finish()ed. Be honest about the shape: it is METHOD-based, not endpoint- or resource-based. `isSafeMethod()` is literally `method == Get || Head || Options`. A READ key can therefore call any GET in the app, including GET /api/export (the caller's entire data bundle, ExportRoutes.kt:23-27) and GET /api/admin/users if the owning account is an admin. There is no per-list, per-resource or admin-excluding scope. Two fail-open details the first pass omitted: `ApiKeyScope.fromStorage()` returns FULL for any unrecognised or null value — this covers legacy pre-V15 rows as documented, but it also means a client that POSTs `{"scope":"readonly"}` or `{"scope":"read-only"}` to /api/user/api-key silently receives a FULL key rather than an error, because UserRoutes.kt:167 pipes the raw string through the same lenient parser. The unit test pinning the guard is ApiKeyScopeGuardTest (READ+POST → 403, READ+GET → 200, FULL+POST → 200); the name is the test's own — there is no production class called ApiKeyScopeGuard.

> backend:plugins/Security.kt:53-54 (isSafeMethod), :91-102 (rejection); enum + lenient parse at backend:services/UserApiKeyService.kt:30-41; lenient parse reused on create at backend:routes/UserRoutes.kt:167; test at backend-test:plugins/ApiKeyScopeGuardTest.kt:42-77

### Static-file serving is path-traversal guarded

`Partial`

Active whenever STATIC_FILES_DIR is set — the shipped image sets it (Dockerfile.backend:44 ENV STATIC_FILES_DIR=/app/static), so it is on in the normal Docker deployment. The SPA catch-all resolves the requested path with `File(dir, relPath).canonicalFile` and serves it only if `candidate.isFile && candidate.path.startsWith(dir.path)`, where `dir` is itself canonicalized at startup; `../` sequences therefore resolve out before the check. Paths starting `api/` or `ws` return early so the static handler can never shadow an authorized route, and non-file misses fall through to index.html. WHY PARTIAL — the containment test is a bare string prefix with no trailing separator: `File("/app/static", "../static-secrets/x").canonicalFile` is `/app/static-secrets/x`, which satisfies `startsWith("/app/static")` and would be served. This is exploitable only if a sibling directory whose name extends the root's name exists next to it; in the shipped image /app contains no such sibling, so the practical exposure today is nil. Recorded here because the guard is one `+ File.separator` away from being airtight, and a reader comparing services should not assume the strong form.

> backend:plugins/Routing.kt:86-108; deployment default at Dockerfile.backend:44

### Not present — authorization, multi-tenancy & sharing

- **requirePasswordChange is advisory only (and its server-side reader is dead code)** — CONFIRMED and slightly worse than first reported. The flag is read from the DB, cached in AuthCachedUser, copied into the JWT claims on both auth paths, and returned by GET /api/auth/session — but no server-side code refuses a request because of it. A user handed a temporary password by an admin reset (which sets requirePasswordChange = true) holds a fully privileged session and can call every /api endpoint without ever changing it; the gate exists only in the clients. UserService declares and implements `requiresPasswordChange(userId)` but a whole-backend grep finds ZERO callers — it is dead code, which is a good indicator the gate was intended and never wired. NOTE — the companion flag requireSecurityQuestions is NOT purely advisory and should not be lumped in: SecurityQuestionService refuses the account-recovery flows when it is true (SecurityQuestionService.kt:111,130,195) and UserRoutes.kt:140 uses it to decide whether changing questions requires the current password. It still does not gate ordinary /api access. Practical consequence for a single self-hosted user: an admin-issued temporary password functions as a permanent one if the client prompt is ignored, and any non-T'Day HTTP client bypasses the prompt entirely.
- **No audit log of administrative actions** — CONFIRMED. AdminService neither imports SecurityEventLogger nor calls it. Approving a user, rejecting a registration, deleting a user and all their data, resetting a password, and clearing a reset request all leave no security-event record naming the acting admin. On a single-admin instance this matters less for attribution than for detection: if an attacker reaches an admin session, the destructive actions they take are invisible in the event log the new RetentionScheduler is otherwise curating. CORRECTION to the first pass's characterisation of coverage — SecurityEventLogger is used more widely than "Security.kt plus one credential-envelope failure": it is also injected into security/AuthThrottle.kt and security/RequestRateLimiter.kt, which emit throttle and request_rate_limit_triggered events. So auth-side telemetry is reasonably rich; it is specifically the ADMIN mutation surface that is unlogged.
- **No Origin check on the WebSocket handshake** — CONFIRMED. The /ws upgrade authorizes the principal correctly but never inspects the Origin header, and the Ktor CORS plugin does not apply to WebSocket upgrades. Since the session cookie is SameSite=Lax and browsers DO send Lax cookies on a WebSocket handshake, a page on an attacker-controlled origin visited by a logged-in user could open a socket to the instance and observe the event stream. Blast radius is bounded by design — DomainEvent payloads are content-free change notifications (TodoChanged(listId), MembersChanged(listId)) — so what leaks is activity timing and list ids, not task data, and the Cloudflare Tunnel hostname must be known. The websocket_connect rate limit (30 handshakes/60s) caps reconnection abuse but does nothing about the origin itself.
- **No CSRF token validation — SameSite=Lax is the only cross-site defence** — NOT IN THE FIRST PASS. GET /api/auth/csrf mints 32 bytes of SecureRandom as a hex token and returns it, but the token is never stored and no code path anywhere validates one: a whole-backend grep for 'csrf' outside CsrfRoutes.kt returns only rate-limit config keys and a Sentry log-scrubbing regex. The endpoint is an Auth.js client-compatibility shim, not a control. The actual cross-site defence for state-changing requests is the session cookie's SameSite=Lax attribute plus the fact that mutations use POST/PATCH/DELETE with JSON bodies. Consequence for comparison: if a reader assumes 'it has a CSRF endpoint, so it has CSRF tokens', that is wrong — and it means any future change that weakens SameSite, or any browser context where Lax is not applied, removes the defence entirely with nothing behind it.
- **Any approved user can enumerate other approved users (and the sanitizer leaves a SQL wildcard in)** — CONFIRMED, with two corrections that pull in opposite directions. GET /api/user/search?q= returns id, username and name for up to SEARCH_RESULT_LIMIT = 10 approved users matching a case-insensitive `%substring%`, excluding only the requester, at MIN_SEARCH_LENGTH = 2 characters minimum. CORRECTION 1 (milder than claimed): it is NOT unthrottled — the api_global policy covers all of /api/ at 180 requests per 60 seconds keyed on the authenticated user, so enumeration is capped, though 180 queries/minute against a small user table is still ample. CORRECTION 2 (worse than claimed): the sanitizer does NOT strip all LIKE wildcards. It keeps `[letters, digits, - . _]`, and `_` is SQL LIKE's single-character wildcard — so a query of `__` sanitizes cleanly and becomes `LIKE '%__%'`, matching every username of two or more characters and returning 10 arbitrary users with no guessing at all. Only `%` is actually removed. It is also `isLetterOrDigit()`, which is Unicode-aware, so non-ASCII letters pass through. This is a deliberate trade for the share-member picker; on a single-user or family instance it is inert, but on a multi-user instance the whole server's member list is readable by any approved account in a handful of requests.
- **No database-level tenant isolation (no row-level security)** — CONFIRMED. Every isolation guarantee in this inventory is enforced in Kotlin. Postgres has no RLS policies and the app connects as a single role with full table access, so one query that forgets its `userID eq userId` predicate is an immediate cross-user read. The codebase mitigates this with shared predicate helpers (visibleTodos/mutableTodos), a single accessFor decision point, and inline comments justifying each intentionally unfiltered query (e.g. FloaterListService.kt:255-257) — but that is convention, not enforcement. There is no defence in depth beneath the service layer, and no test that would fail if a predicate were dropped from a single query.
- **API key scope cannot exclude the admin surface** — CONFIRMED. ApiKeyScope has exactly two values and neither restricts WHICH endpoints a key reaches. Security.kt copies user.role — hydrated from the DB — straight into the JwtUserClaims for the API-key path, identically to a session, so requireAdminAccess passes for a key belonging to an admin account. A FULL key on the admin account can therefore call every /api/admin/users route (approve, reject, delete a user and all their data, reset a password, clear a reset request); a READ key on that account can still GET the full user list. There is no 'non-admin' scope, no per-endpoint allowlist, and no way to mint a key that is weaker than the account it belongs to along any axis except HTTP method. A key created for a dashboard widget carries the account's full administrative authority for whatever methods its scope permits.
- **No way to suspend an already-approved account short of deleting it** — NOT IN THE FIRST PASS. `Users.approvalStatus` is written in exactly two places — at registration (APPROVED for the first user, PENDING for everyone after) and in approveUser, which only ever sets APPROVED. rejectUser refuses any target that is not PENDING and, when it does apply, purges the user rather than flipping a status. `Users.role` is written only at registration. So the admin panel offers approve, reject-a-pending-registration, delete, reset-password and clear-reset-request — there is no suspend, no demote, and no revoke-approval. The practical consequence: if an approved account needs to be cut off (shared device, departing family member, suspected compromise), the only server-side option is DELETE /api/admin/users/{id}, which irreversibly purges every todo, floater, list, completion record, file and preference that user owns. A reversible 'disable' is not available, so the operator's realistic fallback is changing the user's password via reset-password and relying on the session/API-key/feed revocation that comes with it.

---

## Data at rest, encryption & secret handling

### Encryption key material validation

`On by default`

`parseKeyMaterial` accepts a key only as base64 (standard alphabet, `Base64.getDecoder()`) decoding to exactly 32 bytes, or a 64-character hex string decoding to 32 bytes; anything else throws `IllegalArgumentException("Invalid field encryption key. Expected 32-byte base64 or 64-char hex.")` (FieldEncryption.kt:122-139). There is no passphrase-to-key derivation, so a short or guessable operator string cannot silently become the key. CORRECTION to the first pass: the keyring is a `by lazy` (line 28), but in production `logStartupSecurityWarnings` calls `fieldEncryption.isConfigured()` unguarded during module setup (Application.kt:89, 128), which forces the lazy — so in production a malformed key aborts BOOT, not the first write. Outside production the warning function returns early (Application.kt:123) and a bad key surfaces only on first encrypt/decrypt. Note the base64 branch uses the standard alphabet, so a base64url key containing `-` or `_` is rejected.

> backend:security/FieldEncryption.kt:122-139; backend:Application.kt:89,123,128

### Secret loading via env var or *_FILE mount

`On by default`

`AppConfig.secret(envVar, fileEnvVar)` prefers the direct env var, then falls back to reading and trimming the file at the path in `<NAME>_FILE`, logging to stderr and returning null if the read fails (AppConfig.kt:204-218). This supports Docker/K8s secret mounts so key material need not sit in the process environment (visible via `docker inspect`, `/proc/<pid>/environ`). Wired for exactly eight values: DATABASE_URL (89), AUTH_SECRET (91), AUTH_CREDENTIALS_PRIVATE_KEY (100), DATA_ENCRYPTION_KEY (102), DATA_ENCRYPTION_KEYS (103), TDAY_PROBE_ENCRYPTION_KEY (147), VAPID_PUBLIC_KEY (152), VAPID_PRIVATE_KEY (153). Not wired for DATA_ENCRYPTION_AAD despite the .env.example hint (AppConfig.kt:104). The mechanism is always on; whether it is used is the operator's choice — the default deployment path is `env_file: .env.docker` (docker-compose.yaml:78-79), i.e. plain env vars. A failed secret-file read degrades to null rather than aborting, except for the two boot-required values below.

> backend:config/AppConfig.kt:204-218,89-103,147,152-153

### Hard boot failure on missing core secrets

`On by default`

`DATABASE_URL` and `AUTH_SECRET` are the only two configuration values that abort startup: both call `error("… is required")` when `secret()` returns null (AppConfig.kt:89-92). This prevents the app ever running with an empty or derived session key — load-bearing, because the JWE session key is HKDF-derived from AUTH_SECRET (JwtService.kt:43,121-132) and the auth-telemetry HMAC key is AUTH_SECRET too (ClientSignals.kt:16-25). Contrast with everything else: encryption keys, VAPID keys, the probe key, and the credential-envelope key are all nullable and degrade to silence, a warning, or an ephemeral value.

> backend:config/AppConfig.kt:89-92

### Password hashing (PBKDF2-HMAC-SHA256, 310,000 iterations)

`On by default`

`PasswordServiceImpl` derives with `PBKDF2WithHmacSHA256`, 256-bit output, and a 16-byte (128-bit) SecureRandom salt per password (PasswordService.kt:29-31, 36, 94-99). Iterations come from AUTH_PBKDF2_ITERATIONS, default 310,000, clamped to [100,000 … 2,000,000] so an operator cannot configure a weak value (AppConfig.kt:95-96). Stored format is self-describing: `pbkdf2_sha256$<iterations>$<saltHex>$<hashHex>` in `User.password` (PasswordService.kt:39, Users.kt:12). A legacy `saltHex:hashHex` format assumed to be 10,000 iterations is still parsed and accepted (PasswordService.kt:28, 82-88) but flags `needsRehash`, as does any hash below the current iteration count (line 60); the login route acts on that flag and rewrites the hash (CredentialsCallbackRoutes.kt:105-107). Note: PBKDF2 is not memory-hard; Argon2id/scrypt would resist GPU cracking better.

> backend:security/PasswordService.kt:26-40,60,82-88,94-99; backend:config/AppConfig.kt:95-96

### Constant-time comparison on every secret check

`On by default`

Password verification uses a hand-rolled XOR-accumulate loop with an empty/length pre-check (PasswordService.kt:101-109). API keys, calendar feed tokens, and password proofs all use `java.security.MessageDigest.isEqual` (UserApiKeyService.kt:222-225, CalendarFeedService.kt:140-143, PasswordProof.kt:111). This closes timing side channels on the secret-comparison step. Consistently applied — I read all four verification paths and found no `==`/`equals` comparison of a submitted secret against a stored value. Minor honest note: the API-key and feed-token comparisons operate on hex STRINGS converted to ASCII bytes rather than raw digest bytes, which is equivalent here because SHA-256 hex is always 64 characters.

> backend:security/PasswordService.kt:101-109; backend:services/UserApiKeyService.kt:222-225; backend:services/CalendarFeedService.kt:140-143; backend:security/PasswordProof.kt:111

### API key generation and storage (256-bit secret, SHA-256 at rest)

`On by default`

Generation: 32 bytes (256 bits) from SecureRandom, base64url-encoded without padding (UserApiKeyService.kt:113, 251-254, 264). The wire token is `tday_<cuid-id>_<secret>` (line 153) — the CUID identifies the row, the 256-bit secret is the only thing that authenticates. Storage: only `s256$<sha256-hex-of-secret>` plus a 4-character trailing preview goes to `user_api_keys.key_hash`/`key_preview` (lines 134-135, 256-259, 263, 265; table UserApiKeys.kt:9-10). The plaintext is returned exactly once at creation (line 153). Fast SHA-256 rather than PBKDF2 is a deliberate, documented choice (comment at lines 130-133): the secret is full-entropy CSPRNG output so key-stretching buys nothing, and this runs on every API request where a slow KDF would be a latency and DoS amplifier. Legacy PBKDF2-hashed keys are still accepted for backward compatibility (lines 226-230). Keys support an optional expiry checked on every resolve (lines 216-217) and a READ/FULL scope (lines 30-40), with `lastUsedAt` writes throttled to once per 300s (lines 238, 267).

> backend:services/UserApiKeyService.kt:126-135,201-268

### Calendar feed token generation and storage

`On by default`

Identical scheme to API keys: 32-byte/256-bit SecureRandom secret, base64url unpadded (CalendarFeedService.kt:72, 234-237, 246); wire form `<cuid-id>_<secret>` embedded in `/calendar/<token>.ics` (line 96); stored as `s256$<sha256hex>` + 4-char preview in `calendar_feed_tokens` (lines 79-80, 239-242, 245, 247; table CalendarFeedTokens.kt:9-10; migration V16__calendar_feed_tokens.sql:7,11-12). Verification is `MessageDigest.isEqual` over the hex (lines 140-143). One active token per user — generate deletes any prior row first (line 84), so rotation is implicit. `lastUsedAt` writes are throttled to once per 300s (lines 148-153, 248) to avoid a DB write per poll. This matters because the feed URL is a bearer credential handed to third-party calendar clients over a plain HTTP GET with no other auth — and note the ICS body it returns contains the DECRYPTED description of every dated task (lines 190, 214).

> backend:services/CalendarFeedService.kt:75-100,126-161,190,214,234-249

### Security question answers hashed with the password KDF

`On by default`

Answers are lowercased and trimmed (`SecurityQuestions.normalizeAnswer`, SecurityQuestions.kt:35) then run through the same `passwordService.hashPassword` — PBKDF2-HMAC-SHA256 at 310,000 iterations with a per-answer 16-byte salt — before landing in `user_security_questions.answer_hash` (SecurityQuestionService.kt:289; table UserSecurityQuestions.kt:10; migration V12__add_security_questions.sql:26-32). Only the question id and the hash are stored, never the answer or the question text. A precomputed `dummyHash` is verified against on every non-matching path so a nonexistent or unconfigured user costs the same wall-clock time as a real miss (SecurityQuestionService.kt:86, 131, 147, 162, 196, 208, 224) — account-enumeration resistance, not just hashing. Corrected from the first pass: the column is UserSecurityQuestions.kt:10, not :11.

> backend:services/SecurityQuestionService.kt:86,131,289; backend:security/SecurityQuestions.kt:35; backend:db/tables/UserSecurityQuestions.kt:10

### Session token cryptography (JWE, not a signed JWT)

`On by default`

Sessions are encrypted JWTs, not merely signed: `JWEAlgorithm.DIR` with `EncryptionMethod.A256CBC_HS512` (JwtService.kt:101) via nimbus-jose-jwt 10.0.1. The 64-byte content key is derived from AUTH_SECRET with HKDF-SHA256 (BouncyCastle `HKDFBytesGenerator`), empty salt, info string "Auth.js Generated Encryption Key" (JwtService.kt:43, 121-132) — an Auth.js-compatible derivation. Claims include a `sessionStartedAt` used for the absolute-lifetime check and a random UUID jti (lines 89, 92 — the first pass cited 292/301, which do not exist; the file is 133 lines). Expiry is AUTH_SESSION_MAX_AGE_SEC, default 30 days, clamped [1h … 30d]; absolute cap AUTH_SESSION_ABSOLUTE_MAX_AGE_SEC default 90 days clamped [maxAge … 365d]; renewal threshold 7 days clamped [60s … maxAge] (AppConfig.kt:80-85), enforced at SessionCookies.kt:55-74. The cookie is `__Secure-authjs.session-token` in production, HttpOnly, Secure, SameSite=Lax, Path=/ (SessionCookies.kt:8-11, 84-97). Because it is a JWE the claims are opaque to anyone who captures the cookie — but note the key is a pure function of AUTH_SECRET, so anyone who reads .env.docker can both decrypt and mint sessions.

> backend:security/JwtService.kt:43,89,92,101-107,121-132; backend:security/SessionCookies.kt:84-97

### CUIDs are identifiers only, never bearer secrets

`On by default`

`CuidGenerator.newCuid()` produces `c<base36 millis><base36 counter><4-char host/pid fingerprint><8 random base36 chars>` (CuidGenerator.kt:11-20). The random source is SecureRandom (line 7), but only the 8-char tail is unpredictable — roughly 41 bits — and the timestamp, rolling counter, and pid/hostname fingerprint (lines 22-31) are all guessable. I checked every place a CUID is emitted to a client and found none used as a credential: API keys are `tday_<cuid>_<256-bit secret>` (UserApiKeyService.kt:153), calendar feed tokens are `<cuid>_<256-bit secret>` (CalendarFeedService.kt:96), webhook rows use a CUID as primary key with the secret in a separate column (WebhookService.kt:112,121). In every case the CUID is the row lookup and the SecureRandom secret is what is verified — a guessed CUID gets you a not-found, not access. That separation is the control; it would silently break if a future feature made a bare CUID the whole token.

> backend:db/util/CuidGenerator.kt:7-31; backend:services/UserApiKeyService.kt:153; backend:services/CalendarFeedService.kt:96

### Auth telemetry stored as HMAC, not raw IP/device

`On by default`

`ClientSignalsImpl.hashSecurityValue` HMAC-SHA256s the value under AUTH_SECRET and hex-encodes it (ClientSignals.kt:49-53), which populates `authsignal.identifierHash` / `lastIpHash` / `lastDeviceHash` (V2__full_schema.sql:180-182). Keyed HMAC rather than bare SHA-256 matters here because the IPv4 space is small enough to brute-force a plain digest. If AUTH_SECRET is shorter than 16 chars the class falls back to a random 32-byte per-process key and prints "[security] auth_secret_missing using fallback hash key" (ClientSignals.kt:16-25) — reachable in principle (AUTH_SECRET is boot-required but not length-checked in AppConfig.kt:91-92), and the effect would be that hashes stop matching across restarts. Client IP resolution trusts `cf-connecting-ip` first, then the first `x-forwarded-for` entry, then `x-real-ip`, then the socket (lines 27-37), which is correct behind the Cloudflare Tunnel and would need revisiting on any other ingress. Device hint is a client-supplied `x-tday-device-id` header truncated to 128 chars (lines 39-42).

> backend:security/ClientSignals.kt:16-42,49-53; tday-backend/src/main/resources/db/migration/V2__full_schema.sql:180-182

### Admin-issued temporary password generation

`On by default`

Reset passwords are built from a shared static `SecureRandom` (AdminService.kt:304): one guaranteed character from each of upper/lower/digit/special plus 12 more drawn uniformly from the combined alphabet, then shuffled with `secureRandom.asKotlinRandom()` — a 16-character password, and the shuffle is CSPRNG-backed rather than the usual `Math.random()`-style default (AdminService.kt:284-301). Concrete alphabet: visually ambiguous characters are deliberately excluded (no I/O in upper, no l/o in lower, digits are 2-9 only), giving a 69-character pool ≈ 98 bits for a 16-char password (AdminService.kt:285-289).

> backend:services/AdminService.kt:284-304

### Container hardening around the data plane

`On by default`

The backend container runs with `security_opt: no-new-privileges:true` and `cap_drop: ALL` (docker-compose.yaml:96-99) plus `pids_limit: 512` (line 75); the `database` and `ollama` services each carry `pids_limit: 512` (lines 6, 28). Honest exception: the `ollama-model-setup` one-shot service (lines 43-58) has no pids_limit, and no service other than the backend sets no-new-privileges or cap_drop. The published port is bound to `${TDAY_HOST_BIND:-127.0.0.1}:${TDAY_HOST_PORT:-2525}` so the backend is unreachable from the LAN by default (line 77), and the `database` service declares no `ports:` at all, so Postgres is reachable only from inside the Compose network. Memory limits are deliberately omitted with a documented rationale (a too-low `mem_limit` under `restart: always` OOM-crash-loops the JVM, lines 71-74). This is the boundary around the volume that holds everything above.

> docker-compose.yaml:6,28,43-58,71-79,96-99

### Android client secrets encrypted at rest (Keystore-backed)

`On by default`

Missed by the first pass. The Android app persists its session cookie and server config in `EncryptedSharedPreferences` with `PrefKeyEncryptionScheme.AES256_SIV` for keys and `PrefValueEncryptionScheme.AES256_GCM` for values, under a `MasterKey` built with `KeyScheme.AES256_GCM` — i.e. the master key lives in the Android Keystore, not in the file (EncryptedCookieStore.kt:23-33, SecureConfigStore.kt:21-31). The session JWE cookie survives process death but is never written to a plain SharedPreferences file (EncryptedCookieStore.kt:13-16, 41). The offline task cache uses the same mechanism (OfflineCacheManager.kt:69 references migrating a legacy blob out of EncryptedSharedPreferences). No operator action needed; it is the only storage path in the app.

> android-compose/app/src/main/java/com/ohmz/tday/compose/core/network/EncryptedCookieStore.kt:13-33,41; android-compose/app/src/main/java/com/ohmz/tday/compose/core/data/SecureConfigStore.kt:21-31

### iOS client secrets in the Keychain (AfterFirstUnlock)

`On by default`

Missed by the first pass. `SecureStore` writes the persisted auth session cookie, cached session user, device id, and last username to the iOS Keychain as `kSecClassGenericPassword` with `kSecAttrAccessible = kSecAttrAccessibleAfterFirstUnlock` (SecureStore.swift:22-32, 357, 363, 403) — so the values are unreadable before the first post-boot unlock and are not in an iCloud-synced or world-readable plist. Two honest notes: (1) it also stores the user's PLAINTEXT password for the pending-admin-approval flow (`pendingApprovalPassword`, SecureStore.swift:31, 39-42) so the holding screen can silently re-attempt login — Keychain-protected, but it is a stored password; (2) not everything goes to the Keychain — the runtime server URL, list icons, and the TLS trusted-host fingerprints go to plain `UserDefaults` (SecureStore.swift:9-12, 301, 306, 312-314), which is fine for non-secrets but means the pinned fingerprint store is not Keychain-protected. `kSecAttrAccessibleAfterFirstUnlock` (not `…ThisDeviceOnly`) means the items are eligible for encrypted device backups.

> ios-swiftUI/Tday/Core/Data/SecureStore.swift:9-12,22-32,39-42,301-314,357,363,403

### Field-level encryption (AES-256-GCM envelope)

`Needs config`

`FieldEncryptionImpl.encrypt` uses `AES/GCM/NoPadding` with a 32-byte (256-bit) key, a 12-byte IV, and a 128-bit auth tag (FieldEncryption.kt:22-24, 42-45). Output is a self-describing string `enc:v1:<keyId>:<base64url-iv>:<base64url-ciphertext>` (FieldEncryption.kt:53), so key id and IV travel with each record and no schema column is needed. `isEncrypted()` is a prefix check on `enc:v1:` (line 83), which makes encrypt() idempotent (line 38, asserted at FieldEncryptionTest.kt:45-50) and lets plaintext and ciphertext coexist in the same column during migration. Decrypt hard-fails on a malformed payload (line 59), an unsupported prefix/version (line 61), or an unknown key id (line 64). Scope it precisely: it protects the ciphertext columns in an artifact that leaves the box (stolen pg_dump, copied volume) — nothing more; the key lives in the same .env.docker on the same host as the volume (docker-compose.yaml:78-79), so it is worth nothing against anyone with host or container access. Turned on only by setting DATA_ENCRYPTION_KEY or DATA_ENCRYPTION_KEYS (AppConfig.kt:102-103); the shipped .env.example leaves both blank (.env.example:171,174).

> backend:security/FieldEncryption.kt:21-54

### Per-record random IV, no IV reuse

`Needs config`

A fresh 12-byte IV is drawn from a single long-lived `java.security.SecureRandom` for every encrypt call (FieldEncryption.kt:26, 42) and stored inline in the payload. Two encryptions of identical plaintext therefore produce different ciphertext — asserted by a test (FieldEncryptionTest.kt:84-90). This is what stops an attacker with the DB doing equality/frequency analysis across rows, which a static-IV or ECB scheme would leak. Status corrected from the first pass: this is a property of the encryption path, and encryption itself does nothing until DATA_ENCRYPTION_KEY(S) is set, so out of the box no IV is generated at all. There is no code path that reuses an IV.

> backend:security/FieldEncryption.kt:26,42; backend-test:security/FieldEncryptionTest.kt:84-90

### AAD (Additional Authenticated Data) binding

`Needs config`

If DATA_ENCRYPTION_AAD is set, its UTF-8 bytes are fed to `cipher.updateAAD()` on both encrypt and decrypt (FieldEncryption.kt:47-50, 73-76). Any ciphertext encrypted under a different AAD fails GCM tag verification. .env.example ships the constant value `tday:v1` uncommented (.env.example:177) — so an operator who copies the template gets AAD set, but it is a single global version tag, NOT per-row context (no user id or column name is bound in), so it does not prevent an attacker with DB write access from copying a ciphertext from one row to another. Two honest caveats: (1) .env.example:178 advertises a `DATA_ENCRYPTION_AAD_FILE` variant, but AppConfig reads this one with plain `env()` and not `secret()` (AppConfig.kt:104), so the _FILE form is NOT supported for AAD; (2) the AAD is read fresh from config on every call, so changing it after data is written makes existing rows undecryptable.

> backend:security/FieldEncryption.kt:47-50,73-76; backend:config/AppConfig.kt:104

### Encryption keyring and key rotation

`Needs config`

DATA_ENCRYPTION_KEYS holds a comma-separated `keyId:keyMaterial` list parsed into a map (FieldEncryption.kt:99-112); DATA_ENCRYPTION_KEY is folded in afterwards under DATA_ENCRYPTION_KEY_ID (default `primary`, AppConfig.kt:101, FieldEncryption.kt:114-117) — note the ordering: because the single-key entry is written last (line 116), DATA_ENCRYPTION_KEY silently OVERWRITES a ring entry that shares its key id. The active write key is DATA_ENCRYPTION_KEY_ID if present in the ring, else an arbitrary first entry from a `Map` (FieldEncryption.kt:29-33) — so an unrecognised DATA_ENCRYPTION_KEY_ID does not error, it picks something else. Decrypt looks up whatever key id is embedded in the row (line 63), so old keys keep working after the write key changes. Rotation procedure: add the new key to the ring, point DATA_ENCRYPTION_KEY_ID at it, keep the old entry for reads. There is no re-encrypt/backfill job, so rows stay on their original key until next written.

> backend:security/FieldEncryption.kt:28-33,63-64,96-120

### Login credential envelope (RSA-OAEP-256 + AES-256-GCM)

`Needs config`

Hybrid encryption of the login payload: the client generates a 32-byte AES key, encrypts credentials with AES-256-GCM (12-byte IV, 128-bit tag), and wraps the AES key with `RSA/ECB/OAEPPadding` using SHA-256 + MGF1-SHA256 (CredentialEnvelope.kt:49-52, 88-100). Structural checks before decryption: IV length exactly 12 bytes, payload longer than 16 bytes, unwrapped key exactly 32 bytes (lines 85-86, 93). The key id is the first 24 base64url chars of SHA-256 over the SPKI DER (lines 150-153), and both version and key id are checked first (lines 74-79). The private key comes from AUTH_CREDENTIALS_PRIVATE_KEY / _FILE as PKCS#8 PEM (AppConfig.kt:100, CredentialEnvelope.kt:116-125). If unset, it falls back to an ephemeral 2048-bit RSA keypair generated at boot with a warning (lines 139-140) plus a production startup warning (Application.kt:135-137) — envelopes then break across restarts and the property is only as good as TLS.

> backend:security/CredentialEnvelope.kt:49-52,74-79,85-100,115-153

### Mobile probe payload encryption

`Needs config`

`ProbeEncryption` encrypts the version/compatibility JSON with AES-256-GCM, 12-byte random IV, 128-bit tag, emitting `base64url(IV || ciphertext)` (ProbeEncryption.kt:9-27) — note this one uses the raw IV-prefix layout, not the `enc:v1:` envelope format used by FieldEncryption, and there is no decrypt method server-side. The constructor hard-requires the base64url key to decode to exactly 32 bytes (lines 14-18). Wired at `/mobile/probe`, and only when TDAY_PROBE_ENCRYPTION_KEY is set; the route builds the encryptor inside `runCatching{}.getOrNull()` and simply omits `encryptedCompatibility` when the key is absent or malformed (MobileProbeRoutes.kt:12-14, 22-27) — a bad key degrades silently rather than erroring. The same key must be compiled into the apps (.env.example:198-200), so it is a shared static secret, not per-client, and the response still exposes `appVersion` in the clear (line 36). The route sets `Cache-Control: no-store` and `Pragma: no-cache` (lines 18-19). Blank by default in .env.example:200.

> backend:security/ProbeEncryption.kt:9-27; backend:routes/MobileProbeRoutes.kt:12-38

### Fail-open field encryption with a boot warning (recently hardened)

`Partial`

With no usable key, `encryptIfSensitive` returns the plaintext unchanged (FieldEncryption.kt:86) and the row is written in the clear — nothing errors, and no column marks it. The new mitigation is a startup warning: when TDAY_ENV/NODE_ENV resolves to production (AppConfig.kt:93,199-202) and `fieldEncryption.isConfigured()` is false, the app logs "DATA_ENCRYPTION_KEY/DATA_ENCRYPTION_KEYS is unset in production; sensitive fields are being stored as PLAINTEXT in Postgres" (Application.kt:128-133). It is a log line only — boot still succeeds, and the warning is suppressed entirely outside production (Application.kt:123). The same function also warns on a missing AUTH_CREDENTIALS_PRIVATE_KEY (Application.kt:135-137). Because the default environment is `development` (AppConfig.kt:202), an operator who never sets TDAY_ENV gets no warning at all.

> backend:Application.kt:122-137; backend:security/FieldEncryption.kt:86

### Scope of encrypted fields — descriptions yes, titles no

`Partial`

`sensitiveFields = setOf("description", "content", "overriddenDescription", "webhookSecret")` (FieldEncryption.kt:25). A repo-wide grep for `encryptIfSensitive` returns exactly these production call sites: `Todos.description` (TodoService.kt:76,156), `Floaters.description` (FloaterService.kt:69,118), `CompletedTodos.description` (CompletedTodoService.kt:72), `CompletedFloaters.description` (CompletedFloaterService.kt:71), `TodoInstances.overriddenDescription` (TodoService.kt:451,467), the import path (ExportService.kt:206,226,257,278,298), and `webhook_subscriptions.secret` (WebhookService.kt:121). What is NOT covered, stated plainly: task titles are plaintext by design — `Todos.title` is a bare `text("title")` (Todos.kt:10) and `encryptIfSensitive("title", …)` is asserted to be a no-op (FieldEncryptionTest.kt:66-67). Also plaintext: checklist step titles (TaskSteps.kt:14), `TodoInstances.overriddenTitle`, list names, usernames (Users.kt:11), webhook destination URLs (WebhookSubscriptions.kt:9), push endpoint/p256dh/auth (PushSubscriptions.kt:9-11), and OAuth tokens (Accounts.kt:11-16). The `"content"` entry in the set is dead — no caller passes it. Practical consequence: anyone reading the DB sees the full title of every task; only long-form notes are ciphertext.

> backend:security/FieldEncryption.kt:25; backend:db/tables/Todos.kt:10-11; backend-test:security/FieldEncryptionTest.kt:55-57,66-67

### Webhook signing secret — the one secret stored reversibly

`Partial`

Generated as 32 bytes (256 bits) SecureRandom, base64url, prefixed `whsec_` (WebhookService.kt:113, 146-149, 152). Unlike API keys and feed tokens it cannot be hashed — the server must reproduce it to sign each payload — so it is stored via `encryptIfSensitive("webhookSecret", secret)` (WebhookService.kt:121), which is why `webhookSecret` was added to `sensitiveFields` (FieldEncryption.kt:25). The migration comment states the reasoning (V17__webhook_subscriptions.sql:6-7). Marked partial because that encryption inherits the fail-open behaviour: with no DATA_ENCRYPTION_KEY set, `encryptIfSensitive` returns the raw `whsec_…` and the `?: secret` fallback on line 121 stores it verbatim in `webhook_subscriptions.secret` (table WebhookSubscriptions.kt:10). On dispatch it is decrypted (WebhookDispatchService.kt:99) and used for an `HmacSHA256` hex signature (WebhookDelivery.kt:16-20) sent as `X-Tday-Signature: sha256=<hex>` (WebhookDispatchService.kt:105, 110).

> backend:services/WebhookService.kt:113,121,146-152; backend:services/WebhookDispatchService.kt:99,105,110; tday-backend/src/main/resources/db/migration/V17__webhook_subscriptions.sql:6-7

### Password-proof challenge (available, but NOT mandatory)

`Partial`

MATERIAL CORRECTION to the first pass, which claimed the password never crosses the wire in plaintext form. It can. `CredentialsCallbackRoutes.kt:89-110` takes the proof path only when the `password` field is blank AND a challenge id and proof are both present; otherwise it falls through to `passwordService.verifyPassword(password, storedHash)` on the raw password (line 104). The server accepts either. The mechanism itself, when used: algorithm id `pbkdf2_sha256+hmac_sha256`, version 1 (PasswordProof.kt:31-32); the server issues a 24-byte (192-bit) SecureRandom base64url challenge id (line 62) plus the account's real PBKDF2 salt and iteration count; the client derives the PBKDF2 hash locally and returns `HMAC-SHA256(storedHashBytes, "login:<challengeId>:<username>")`, compared with `MessageDigest.isEqual` (lines 102-111). Anti-enumeration detail worth copying: for an unknown username the salt is `HMAC-SHA256(AUTH_SECRET, "login-challenge-salt:" + username)` truncated to 16 bytes (lines 33, 124-129), stable across calls and restarts. Challenges are single-use (`challenges.remove`, line 95), live in-process in a ConcurrentHashMap (line 43), expire after AUTH_PASSWORD_PROOF_CHALLENGE_TTL_SEC = 120s (AppConfig.kt:139; PasswordProof.kt:64,96), and are capped at AUTH_PASSWORD_PROOF_MAX_ACTIVE = 5000 (AppConfig.kt:140). Second correction: the eviction at PasswordProof.kt:142-148 takes `challenges.keys.firstOrNull()`, which is ConcurrentHashMap iteration order, not insertion order — it is arbitrary-evicted, not oldest-evicted.

> backend:routes/auth/CredentialsCallbackRoutes.kt:89-110; backend:security/PasswordProof.kt:31-34,62-64,95-111,124-129,142-148

### Retention purge of security-log tables (recently added)

`Partial`

`RetentionScheduler` loops every 6 hours (`TICK_INTERVAL = Duration.ofHours(6)`, RetentionScheduler.kt:162) deleting rows past their cutoff from EventLogs (RETENTION_EVENTLOG_DAYS=90), AuthThrottles (30), AuthSignals (180), and CronLogs (90, floored at 7 because it holds the reminder scheduler bookmark) — AppConfig.kt:158-161, RetentionScheduler.kt:77-92. A `0` value disables a table's purge (line 127); deletes are batched at 5000 rows (lines 139-143, 163). It also expires stale `pendingAdminReset` flags past ADMIN_RESET_REQUEST_TTL_DAYS (lines 96-113). This is data-at-rest minimisation: it bounds how long hashed IPs, device hashes, and auth history accumulate. Marked partial for one reason: RETENTION_DRY_RUN defaults to `true` (AppConfig.kt:164), so out of the box it only logs "[retention:dry-run] … would purge" (line 132) and deletes nothing until the operator flips it. The throttle purge preserves live lockouts (lines 80-86).

> backend:services/RetentionScheduler.kt:77-92,96-113,120-148,162-163; backend:config/AppConfig.kt:158-164

### CSRF token endpoint — issued but never verified

`Partial`

`GET /csrf` returns 32 bytes (256 bits) of SecureRandom rendered as hex (CsrfRoutes.kt:27-29), rate-limited at AUTH_LIMIT_CSRF_MAX=40 per 60s (AppConfig.kt:118-119). Confirmed with a case-insensitive grep for `csrf` across tday-backend/src/main: the only hits are the config keys (AppConfig.kt:33-34,118-119), the throttle enum/policy (AuthThrottle.kt:21,78,141), the route registration (Routing.kt:57), the route itself, and Sentry scrubbing patterns (TdayObservability.kt:16,64,66). There is NO server-side validation of the returned token anywhere. It exists for Auth.js client compatibility. The actual cross-site defence is the session cookie's SameSite=Lax + HttpOnly + Secure (SessionCookies.kt:11, 94-96), not this token. Read this row as 'do not count T'Day as having CSRF token validation'.

> backend:routes/auth/CsrfRoutes.kt:27-29; backend:security/SessionCookies.kt:11,94-96

### Secret redaction before anything leaves the box (Sentry)

`Partial`

When error reporting is enabled, `Sentry.init` sets `isSendDefaultPii = false`, and a `beforeSend` hook nulls the user IP, drops the query string entirely, and rewrites the request URL through `TdayObservability.sanitizePath` (Application.kt:38-51). `sanitizePath` replaces any path segment that is not on a fixed allow-list of 40 known route words with a placeholder (TdayObservability.kt:8-48, 72-88), so ids and tokens embedded in paths — e.g. `/calendar/<token>.ics` — do not reach the vendor. Additional patterns scrub data keys matching authorization/cookie/csrf/token/password/session/secret/username/body/payload/header and label strings containing URLs, emails, or `bearer `/`token=`/`password=` (TdayObservability.kt:50-67). Marked partial because it only matters when SENTRY_DSN is set — `sentryDsn = env("SENTRY_DSN")` is nullable (AppConfig.kt:167) and an empty DSN disables the SDK, so the default posture is 'nothing is sent at all' rather than 'sent and scrubbed'.

> backend:Application.kt:38-51; backend:observability/TdayObservability.kt:8-67,72-88

### Secrets excluded from version control

`Partial`

`.gitignore` excludes `.env`, `.env.docker`, `.env.local`, and `*.env.local` (.gitignore:35-39), and `git ls-files` confirms the only tracked env files are `.env.example` and `tday-backend/.env.example`. The current template ships placeholders, not values: `AUTH_SECRET=CHANGE_ME_WITH_A_RANDOM_32_BYTE_SECRET` (.env.example:81), blank DATA_ENCRYPTION_KEY/KEYS (.env.example:171,174), blank TDAY_PROBE_ENCRYPTION_KEY (.env.example:200). Marked partial rather than on-by-default for one concrete reason documented in the gaps below: a real-looking base64 AUTH_SECRET was committed in the initial commit and is still retrievable from history. The ignore rules protect new secrets; they did not protect the first one.

> .gitignore:35-39; .env.example:81,171,174,200; `git ls-files | grep -i '\.env'` returns only the two .env.example files

### Not present — data at rest, encryption & secret handling

- **No volume, filesystem, or database-level encryption managed by the app** — Postgres data lives in a plain Docker named volume `postgres_data:/var/lib/postgresql/data` (docker-compose.yaml:19-20, 104-106) with no encryption layer. There is no pgcrypto extension, no `pgp_sym_*` usage, and no TDE. Consequence for a single self-hoster: everything outside the four encrypted columns — task titles, checklist steps, list names, usernames, password hashes, session/auth tables, webhook URLs, push endpoints, OAuth tokens — is readable by anyone who can read the host filesystem or copy the volume. Whole-dataset at-rest coverage has to come from the host (encrypted filesystem / encrypted ZFS dataset), and this app neither provides nor checks for it.
- **No automated or off-box backup** — There is no backup script, no cron/scheduled `pg_dump`, and no snapshot mechanism anywhere in the repo — the only mention is prose advice in docs/DEPLOYMENT.md:106 telling the operator to back the volume up with a scheduled `pg_dump` themselves. RetentionScheduler deletes rows and ships with RETENTION_DRY_RUN=true specifically because the operator's DB may be their only copy of the data (AppConfig.kt:162-164). Consequence: a corrupted volume, a mistaken `docker compose down -v`, or a disk failure is total data loss, and there is no encrypted-backup story to inherit the field-encryption key handling either.
- **No HSM, KMS, or external key management — the key sits beside the data** — All key material (DATA_ENCRYPTION_KEY(S), AUTH_SECRET, AUTH_CREDENTIALS_PRIVATE_KEY, VAPID keys, TDAY_PROBE_ENCRYPTION_KEY) is loaded as a string from an env var or a file path on the same host (AppConfig.kt:89-153, 204-218) and held in process memory for the JVM's lifetime (the `by lazy` keyring at FieldEncryption.kt:28). There is no KMS/HSM/PKCS#11 integration, no envelope-encrypted data key, and no in-process key zeroization. The `*_FILE` support is the closest thing and only moves the secret from the environment to a mounted file. Consequence: root on the Docker host reads both the ciphertext and the key, so field encryption defends only against artifacts that leave the box. Any claim broader than that would be an overclaim.
- **No key-rotation backfill / re-encryption job** — The keyring supports reading rows written under any retained key id (FieldEncryption.kt:63-64), but nothing re-encrypts existing rows onto a new key. A row keeps its original `enc:v1:<oldKeyId>:…` prefix until the record happens to be updated. Consequence: after rotating DATA_ENCRYPTION_KEY_ID, the old key can never actually be retired — dropping it from DATA_ENCRYPTION_KEYS makes those rows throw `IllegalStateException("Missing field encryption key for key id …")` on read (FieldEncryption.kt:64), and that throw is not caught in the read paths. Rotation is additive-only in practice.
- **Task titles, checklist steps and list names are plaintext by design** — `sensitiveFields` covers only description/content/overriddenDescription/webhookSecret (FieldEncryption.kt:25). `Todos.title` (Todos.kt:10), `TaskSteps.title` (TaskSteps.kt:14), list names, `TodoInstances.overriddenTitle`, and `Users.username` (Users.kt:11) are all stored in the clear, and the test suite pins the behaviour. Consequence: an off-box DB dump reveals the full title of every task the user has ever created — which for a task app is most of the information content. Encrypting descriptions is a real but narrow control, and a reader should not infer 'my tasks are encrypted' from it.
- **The "content" entry in sensitiveFields is dead configuration** — `sensitiveFields` includes `"content"` (FieldEncryption.kt:25) and a test asserts `isSensitiveField("content")` is true (FieldEncryptionTest.kt:56), but no production code ever calls `encryptIfSensitive("content", …)`. Consequence is not a vulnerability — it is that the set overstates coverage when read on its own, which matters for a document whose purpose is accurate comparison. The real encrypted-field list is description / overriddenDescription / webhookSecret.
- **Encrypted fields leave the database in cleartext through export and the ICS feed** — Newly identified. Two supported paths read the encrypted description columns, decrypt them, and hand the plaintext out: (1) the portable export bundle explicitly decrypts — its own KDoc says "descriptions decrypted to plaintext" (ExportService.kt:51-52, decrypt calls at 391, 410, 419, 434, 452); (2) the calendar feed renders the decrypted description into every VEVENT (CalendarFeedService.kt:190, 214), served over an unauthenticated GET to whatever third-party calendar service holds the feed URL. Consequence: the at-rest control does not follow the data. An exported JSON bundle sitting in the user's Downloads folder, or a feed subscribed in a hosted calendar provider, contains the same notes the DB column protects — and neither is re-encrypted or key-bound.
- **A real AUTH_SECRET value is committed in the repository's git history** — Newly identified, and the most consequential secret-handling gap. The initial commit (b7a539f8) contains `.env.example:14` with `AUTH_SECRET=CHANGE_ME_WITH_A_RANDOM_32_BYTE_SECRET` — a well-formed 32-byte base64 value, not a placeholder. It was later replaced with `CHANGE_ME_WITH_A_RANDOM_32_BYTE_SECRET` (current .env.example:81), but git history is immutable and anyone with a clone can read it. AUTH_SECRET is not one secret among many here: it is the HKDF input for the session JWE content key (JwtService.kt:43, 121-132), the HMAC key for all auth-telemetry hashes (ClientSignals.kt:51), and the key for the anti-enumeration login-challenge salt (PasswordProof.kt:126). If the running deployment ever used that value, session tokens can be minted offline. Verify the server's live value and rotate if it matches; scrubbing history does not un-publish it.
- **OAuth account tokens and Web Push keys are stored plaintext** — The `Account` table's `refresh_token`, `access_token`, and `id_token` columns are plain `text()` with no encryption call site (Accounts.kt:11-16; V2__full_schema.sql:149-154), as are `push_subscriptions.endpoint`, `p256dh`, and `auth` (PushSubscriptions.kt:9-11). None of these field names appear in `sensitiveFields`. Consequence: if OAuth providers are configured (AUTH_GOOGLE_*/AUTH_DISCORD_* at .env.example:161-164), third-party tokens sit in the clear alongside the encrypted description columns — an inconsistency worth knowing when comparing against a service that encrypts all credential material uniformly.
- **Vestigial client-side-encryption columns that nothing uses** — Newly identified. `User.protectedSymmetricKey` and `User.enableEncryption` exist in the schema (Users.kt:18-19, V2__full_schema.sql:129-130), are returned by the user API (UserResponses.kt:10-11, UserService.kt:51-52), and are writable via a PATCH route (UserRoutes.kt:68-76) — the shape of a client-side/E2EE key-wrapping design. Nothing implements it: no client encrypts anything with that key. The web app never references the field at all, and the only client mention is an unused Android DTO property (ApiModels.kt:226). `enableEncryption` even defaults to `true`, so a reader querying the DB or the API would reasonably conclude encryption is on when no client-side encryption exists anywhere in this codebase.
- **Default Postgres credentials are weak unless overridden** — docker-compose.yaml:16-18 defaults to `POSTGRES_USER=myuser`, `POSTGRES_PASSWORD=mypass`, `POSTGRES_DB=mydb`. The comment above (lines 13-15) acknowledges this and tells the operator to override via the root .env and keep DATABASE_URL in sync. Mitigating context: the `database` service publishes no ports at all, so it is reachable only from inside the Compose network. Consequence: not an internet-exposed weakness, but anything else on that Docker network — or a compromised sidecar — reaches the database with a guessable credential, and the field-encryption key would be in the same .env.docker.
- **No integrity/tamper detection on plaintext columns** — GCM's auth tag protects only the encrypted description columns from modification (FieldEncryption.kt:24, 71). There is no row-level MAC, signed audit chain, or checksum over the plaintext columns, and `EventLogs` is an ordinary table that RetentionScheduler itself deletes from (RetentionScheduler.kt:77-79). Consequence: someone with DB write access can silently alter task titles, `User.role`, or `User.approvalStatus` and nothing detects it — relevant because the ADMIN/APPROVED registration gate is enforced from those very columns, and because session claims are minted from them (JwtService.kt:96-97).

---

## Network exposure, TLS & HTTP security headers

### Loopback-only host port binding (exposure model)

`On by default`

docker-compose publishes the backend as "${TDAY_HOST_BIND:-127.0.0.1}:${TDAY_HOST_PORT:-2525}:8080", so with no root .env override Docker binds the host side to 127.0.0.1 only. Nothing on the LAN or the internet reaches the container directly; the only path in is a process on the host itself (cloudflared). Protects against internet-wide port scanning, LAN neighbours and cloud-firewall misconfiguration — there is no inbound listener to find. It does NOT make the service unreachable or unfindable: with a Cloudflare Tunnel running, https://tday.<domain> is reachable from anywhere, and the hostname is not a secret (Cloudflare-issued certificates land in public Certificate Transparency logs). "No open ports" here means "no unauthenticated attack surface below HTTP", not "nobody can find it". CORRECTION vs first pass: .env.example:12 and :15 are COMMENTED-OUT documentation lines, not settings — the real default comes from the compose `:-127.0.0.1` fallback. Honest caveat for this checkout: the repo-root .env sets TDAY_HOST_BIND=0.0.0.0 (.env:1), overriding the safe default; the deployed server's value must be confirmed separately, because with 0.0.0.0 the container is exposed on every host interface and the CF-Connecting-IP trust below becomes spoofable.

> docker-compose.yaml:76-77; .env:1-2 (TDAY_HOST_BIND=0.0.0.0); .env.example:7-15 (commented docs); docs/remote-access/cloudflare-tunnel.md:5,13,21-25,31,109; SECURITY.md:79-80

### X-Content-Type-Options

`On by default`

Emitted on every response as `nosniff` via the application-wide DefaultHeaders plugin (installed in Application.module, not route-scoped). Stops the browser MIME-sniffing a response body into a more dangerous type. No env var, no opt-out.

> backend:plugins/SecurityHeaders.kt:100-101; installed at Application.kt:85

### X-Frame-Options

`On by default`

`DENY` on every response. Legacy clickjacking defence for browsers predating CSP frame-ancestors; paired with `frame-ancestors 'none'` in the CSP so both the old and new mechanism refuse framing. DENY (not SAMEORIGIN) means the app cannot frame itself either — nothing in the SPA needs to.

> backend:plugins/SecurityHeaders.kt:102; frame-ancestors at SecurityHeaders.kt:73; test asserting frame-ancestors 'none' at SecurityHeadersTest.kt:46-52

### Referrer-Policy

`On by default`

`strict-origin-when-cross-origin` on every response. Same-origin navigations still get the full URL; cross-origin requests leak only scheme+host (never path or query), and an HTTPS→HTTP downgrade sends no Referer at all. Concretely this stops a task title or a `/calendar/{token}` feed path in the address bar from being handed to a third-party host in a Referer header when a browser follows an outbound link.

> backend:plugins/SecurityHeaders.kt:103

### Permissions-Policy (recently hardened)

`On by default`

One header at SecurityHeaders.kt:106: `camera=(), microphone=(), geolocation=(), payment=(), usb=()` — an empty allowlist for each, meaning no origin (not even self) may use those APIs. The app needs none of them today; the point is that a future dependency or an injected script cannot quietly start asking. It does not cover every powerful feature (no clipboard-read, no display-capture, no serial/bluetooth, no fullscreen, no autoplay) — those remain at browser default. No unit test pins this header's contents.

> backend:plugins/SecurityHeaders.kt:104-106

### Content-Security-Policy (recently hardened)

`On by default`

Enforcing by default (header name `Content-Security-Policy`), built once at boot by buildCspHeader and emitted on every response. Verified full policy, directive for directive as written in source: `default-src 'self'; base-uri 'self'; object-src 'none'; frame-src 'none'; frame-ancestors 'none'; form-action 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; media-src 'self'; manifest-src 'self'; worker-src 'self'; connect-src 'self' ws: wss: https://raw.githubusercontent.com https://api.github.com [+ extra]`. default-src 'self' is the catch-all floor; base-uri 'self' blocks a <base> injection re-pointing relative script URLs; object-src/frame-src 'none' kill plugin and iframe embedding; form-action 'self' stops an injected form POSTing off-origin; img-src adds `data:`; font-src/media-src/worker-src/manifest-src 'self'. `unsafe-eval` appears nowhere and a test asserts that. connect-src carries `ws:`/`wss:` as bare SCHEME sources rather than a host because the SPA builds its socket URL from window.location at runtime (tday-web/src/lib/realtime.tsx:52-54) while the header is a single boot-time constant — scheme sources permit WebSocket connections only, not HTTP exfiltration. CORRECTION vs first pass: CSP_CONNECT_EXTRA does NOT append to the auto-derived Sentry origin — SecurityHeaders.kt:89-91 uses `config.cspConnectExtra.ifEmpty { … }`, so setting CSP_CONNECT_EXTRA REPLACES the parseSentryIngestOrigin result entirely. An operator who sets it and forgets to re-list the Sentry host will silently break browser error reporting.

> backend:plugins/SecurityHeaders.kt:57-84 (buildCspHeader), 89-91 (connectExtra override semantics), 93-98, 107-109 (emission); connect-src rationale in the KDoc at SecurityHeaders.kt:52-55,62-64; parseSentryIngestOrigin at SecurityHeaders.kt:26-38; tests at backend-test:plugins/SecurityHeadersTest.kt:14-67; ws URL construction at tday-web/src/lib/realtime.tsx:52-54

### CSP script-src excludes 'unsafe-inline' and 'unsafe-eval' (deliberate)

`On by default`

`script-src 'self'` — no 'unsafe-inline', no 'unsafe-eval', no nonce, no hash. This is the load-bearing directive: an XSS in the SPA cannot execute, which is what makes the rest of the policy more than decorative. A unit test pins the exact string `script-src 'self'` so a future edit cannot loosen it silently, and a second test asserts the whole policy never contains 'unsafe-eval'. The accepted cost is documented in the source KDoc: next-themes' inline anti-FOUC script is blocked, producing a brief theme flash on first paint plus one console violation; theming still applies through its normal React effects. A nonce cannot rescue it (next-themes blanks the nonce client-side) and a hash would break on any dependency bump.

> backend:plugins/SecurityHeaders.kt:48-51 (rationale),75 (directive); test asserting exactly "script-src 'self'" at SecurityHeadersTest.kt:14-20; no-unsafe-eval test at SecurityHeadersTest.kt:64-67

### CSP_MODE rollback gate (fails safe)

`On by default`

parseCspMode maps the CSP_MODE env var to enforce (default; the value for null, empty, and "enforce"), reportOnly ("report-only", "report_only", "reportonly" — switches the header name to Content-Security-Policy-Report-Only), or off ("off", "disabled", "none" — emits no CSP header at all). Any unrecognised string falls back to ENFORCE, i.e. it fails safe rather than silently disabling the policy, and that specific behaviour is unit-tested with the input "nonsense". Purpose is operational: a missed directive white-screens the SPA, and flipping an env var beats rebuilding the container image to recover.

> backend:plugins/SecurityHeaders.kt:9-17 (enum + parse), 92-97 (header-name selection); config binding at AppConfig.kt:165 (`cspMode = env("CSP_MODE")`), comment at AppConfig.kt:154-155; test at SecurityHeadersTest.kt:94-104

### CORS strict allowlist, default-deny

`On by default`

The CORS plugin registers allowed methods (DELETE, GET, OPTIONS, PATCH, POST) and headers (Accept, Authorization, Content-Type, Origin, plus `baggage` and `sentry-trace` for tracing), sets allowCredentials = true and allowNonSimpleContentTypes = true, and then adds hosts ONLY from CORS_ALLOWED_ORIGINS. That variable ships EMPTY in both .env.example:40 and .env.docker:26 and AppConfig's envCsv returns an empty list when unset, so out of the box zero foreign origins are allowed — no anyHost(), no wildcard, no reflect-the-Origin. Same-origin requests are unaffected, so the SPA served by this same Ktor process works. Each configured origin is parsed as a URI and dropped with a warning unless it has an http/https scheme and a non-blank host, so a malformed entry cannot accidentally widen the policy — it is skipped, not passed through. allowCredentials = true is only safe BECAUSE the allowlist is explicit and empty by default; the two settings must be read together. CORRECTION vs first pass: I could not verify any live/audit-observed 403 from this checkout — the rejection behaviour is Ktor's CORS plugin default (disallowed Origin refused before routing), asserted from the plugin, not from an observed response.

> backend:plugins/Cors.kt:16-38 (install), 40-51 (per-origin parsing/skip); AppConfig.kt:94 (corsAllowedOrigins = envCsv) and AppConfig.kt:182-187 (envCsv returns empty when unset); .env.example:40; .env.docker:26

### WebSocket handshake authentication and approval gate

`On by default`

GET /ws upgrades first, then the handler calls call.requireApprovedAuthUser(). Auth state was already attached by the Security intercept at the Plugins phase (which ran on the upgrade request), so the credential is resolved from the Authorization: Bearer header or an authjs session cookie, decoded as a JWE, checked for absolute-lifetime expiry and tokenVersion match, and hydrated against the Users row before /ws sees it. Unauthenticated → socket closed with CloseReason VIOLATED_POLICY (1008) and text "Unauthorized"; authenticated but not APPROVED → same 1008 close with "Pending approval". Only then does the handler subscribe to RealtimeService.channelFor(user.id) — a per-user channel, no cross-tenant event leakage. Two things the first pass missed: (1) resolveSessionToken also accepts a `tday_`-prefixed per-user API key from the Authorization header, so /ws can be authenticated by API key as well as by session (browsers cannot set that header on a WebSocket, but native/scripted clients can); (2) the rejection happens AFTER the 101 upgrade completes, not as a failed handshake, so an unauthenticated peer does briefly hold an established socket before the close frame.

> backend:plugins/Routing.kt:68-84; auth intercept at plugins/Security.kt:75,83,123-165; API-key branch at Security.kt:84-121; credential resolution at Security.kt:264-275; requireApprovedAuthUser at domain/AuthContext.kt:64-67; requireApproved at domain/AuthContext.kt:51-56

### WebSocket transport limits

`On by default`

install(WebSockets) sets pingPeriod = 15.seconds and timeout = 60.seconds (a dead peer is reaped within a minute rather than pinning a connection forever), maxFrameSize = 64 * 1024L bytes (a single frame cannot force a large server-side allocation), and masking = false (server-side masking off, normal for a server — clients still mask per RFC 6455). These bound the memory and connection cost of a misbehaving or abandoned client. Note this 64 KiB frame cap is the ONLY size bound anywhere in the request pipeline (see gaps).

> backend:Application.kt:73-78

### WebSocket connect rate limit

`On by default`

Path == "/ws" attracts the `websocket_connect` policy: 30 upgrade attempts per 60-second window (WS_RATE_LIMIT_MAX / WS_RATE_LIMIT_WINDOW_SEC, defaults 30 and 60), keyed on the authenticated user id when one is present and on the resolved client IP otherwise. Applied in the Plugins phase on the HTTP upgrade request before routing, so a connect flood is refused with a 429 + Retry-After before any socket is established. Every trip emits a `request_rate_limit_triggered` event with reason `websocket_rate_limit` into the eventLog table, with the path run through sanitizePath first.

> backend:plugins/RateLimiting.kt:88-97 (policy), 18-36 (intercept); defaults at AppConfig.kt:116-117; event emission at security/RequestRateLimiter.kt:88-99

### App-layer HTTP rate limiting with real 429s (recently hardened)

`On by default`

A single ApplicationCallPipeline.Plugins intercept resolves every policy matching the path/method, evaluates all of them, and responds with the one carrying the longest Retry-After. Policies and shipped defaults, all read from source: `api_global` on any path starting /api/ = 180 req / 60s; `infra` on /health, /api/mobile/probe and /calendar/** = 30 / 60s; `todo_summary` on POST /api/todo/summary = 10 / 60s (protects the Ollama call); `change_password` on POST /api/user/change-password = 8 / 300s; `websocket_connect` on /ws = 30 / 60s. All env-overridable (API_RATE_LIMIT_*, INFRA_RATE_LIMIT_*, SUMMARY_RATE_LIMIT_*, CHANGE_PASSWORD_RATE_LIMIT_*, WS_RATE_LIMIT_*), and envInt silently falls back to the default for any non-positive or unparsable value. Responses go through the shared respondRateLimit helper, which appends a real `Retry-After` header and a 429 body of {message, reason, retryAfterSeconds} — this is the recently hardened path; the auth rate-limit responses were converted to it from throw-based 500s (e.g. CsrfRoutes.kt:19-23).

> backend:plugins/RateLimiting.kt:14-98; respondRateLimit at domain/AuthContext.kt:25-39; defaults at AppConfig.kt:108-117; envInt fallback at AppConfig.kt:189-192; converted auth call site example at routes/auth/CsrfRoutes.kt:17-25

### Rate-limit subject keying and bucket hygiene

`On by default`

InMemoryRequestRateLimiter keys a bucket as "<policy>:<subjectType>:<HMAC-SHA256 of 'user:<id>' or 'ip:<addr>'>". Authenticated requests are keyed on the user id, so one abusive account cannot exhaust the quota of everyone behind the same NAT; unauthenticated requests fall back to the resolved client IP. This ordering works because configureSecurity() is installed before configureRateLimiting() (Application.kt:87 then :90), so within the same Plugins phase the auth intercept runs first and call.authUser() is already populated. The subject is HMAC'd with AUTH_SECRET via ClientSignals.hashSecurityValue, so raw IPs never sit in the in-memory map or in the emitted event details. It is a sliding-window log (ArrayDeque of timestamps trimmed per access), not a fixed window, so burst-at-the-boundary does not double the effective quota, and Retry-After is computed from the oldest surviving timestamp. Idle buckets are swept every 256th access. Honest limitations: state is per-process and in-memory — a container restart resets every counter and it would not survive horizontal scaling (auth lockouts, by contrast, are persisted in the AuthThrottle table); and if AUTH_SECRET were shorter than 16 chars the HMAC key falls back to a random per-boot value (ClientSignals.kt:16-25), though AUTH_SECRET is a hard boot requirement at AppConfig.kt:91-92.

> backend:security/RequestRateLimiter.kt:37-52,55-79,110-147; hashSecurityValue at security/ClientSignals.kt:49-53; hashSecret fallback at ClientSignals.kt:16-25; install order at Application.kt:87,90

### Client IP resolution order behind the tunnel

`On by default`

getClientIp reads, in order: `cf-connecting-ip`, then the first non-empty entry of `x-forwarded-for`, then `x-real-ip`, then request.local.remoteAddress. This is what makes per-IP throttling and lockout meaningful behind a proxy — without it every request would appear to come from the cloudflared process. Stated honestly: these headers are trusted UNCONDITIONALLY, with no trusted-proxy allowlist and no Ktor ForwardedHeaders/XForwardedHeaders plugin installed anywhere (grep for httpsredirect|forwardedheaders over tday-backend/src returns nothing). That is sound only while the sole ingress is loopback-bound cloudflared, which sets CF-Connecting-IP itself. If TDAY_HOST_BIND is widened to 0.0.0.0 (as this checkout's .env does) or another proxy is placed in front, any client can forge CF-Connecting-IP and get a fresh rate-limit and lockout bucket per request. The exposure model and this header trust are a package deal.

> backend:security/ClientSignals.kt:27-37; consumed at security/RequestRateLimiter.kt:110-123; documented order at docs/security/cloudflare-auth-hardening.md:24-31; .env:1; absence of ForwardedHeaders confirmed by grep over tday-backend/src

### Docker published ports: backend only

`On by default`

Exactly one `ports:` mapping exists in the whole compose file — the backend's 127.0.0.1:2525→8080. The `database` service (postgres:15) publishes NOTHING: it is reachable only over the compose bridge network by service name, so Postgres 5432 is not exposed to the host, let alone the network, and the source-committed default credentials (myuser/mypass/mydb, overridable via POSTGRES_USER/PASSWORD/DB) are not remotely reachable. The `ollama` service publishes nothing either and sits behind a compose profile (`profiles: ["ai"]`), so it does not start unless the operator opts in with --profile ai; its OLLAMA_HOST=0.0.0.0:11434 binds inside the container namespace only. Ollama's HTTP API is unauthenticated, so keeping it unpublished is doing real work. docker-compose.gpu.yaml adds only `gpus: all` to ollama and changes no networking.

> docker-compose.yaml:2-20 (database, no ports), 22-41 (ollama: profiles ai, no ports, OLLAMA_HOST 0.0.0.0:11434), 76-77 (backend: the only ports entry); docker-compose.gpu.yaml:11-13

### Container runtime hardening on the backend

`On by default`

The backend service sets `security_opt: no-new-privileges:true` (blocks setuid/setgid privilege escalation inside the container), `cap_drop: ALL` (every Linux capability removed — no CAP_NET_RAW, no CAP_NET_BIND_SERVICE, which is why it listens on 8080 rather than 80), and `pids_limit: 512`. pids_limit is set on all three long-lived services (database:6, ollama:28, backend:75) and caps runaway thread/process creation so a fork-bomb or thread-leak DoS cannot take the host down with it; the one-shot ollama-model-setup job has none. The image runs as an unprivileged user: Dockerfile.backend creates the `tday` system user/group and switches to it before the entrypoint. Memory limits are deliberately NOT set, with the reasoning recorded inline in the compose file: a too-low mem_limit under `restart: always` turns into a JVM OOM crash-loop, worse than the DoS it defends against. Note no_new_privileges/cap_drop are set ONLY on the backend — database and ollama do not carry them.

> docker-compose.yaml:6,28,71-75,96-99; Dockerfile.backend:37 (addgroup/adduser tday), 42 (USER tday), 43 (EXPOSE 8080)

### Uniform error responses (no stack traces on the wire)

`On by default`

StatusPages maps every unhandled Throwable to a flat 500 body of {code, message:"An unexpected error occurred"} — the exception is captured to Sentry (with only a sanitized route) and logged server-side, never serialized to the client. Body-parse failures (ContentTransformationException and Ktor's BadRequestException) collapse to a single generic "Invalid request body" 400 rather than echoing the deserializer's message, which would otherwise disclose field names and types. AppError variants map to a fixed status table (NotFound→404, BadRequest→400, Unauthorized→401, Forbidden→403, Conflict→409, Internal→500).

> backend:plugins/StatusPages.kt:33-40 (status table), 58-79 (install, generic 400 and 500 handlers)

### Access-log and telemetry path sanitization

`On by default`

CallLogging formats each line as "<METHOD> <sanitized path> -> <status> (<content-type>)", running every path through TdayObservability.sanitizePath. CORRECTION vs first pass on what sanitizePath actually does: it drops the ENTIRE query string and fragment outright (substringBefore('?')/('#')), it does not selectively strip named query keys — the `sensitiveQueryKeys` set is only a late fallback inside per-SEGMENT sanitization. Each path segment is then replaced unless it is in a fixed allowlist of ~40 known static route segments: segments >24 chars, containing a digit, ':', '_', '-', '@' or '=' become `:id`/`:redacted`, everything else unrecognised becomes `:value`. That matters concretely because /calendar/{token} carries a bearer-equivalent iCalendar feed secret in the URL path — it is redacted ("calendar" is NOT in the allowlist either, so the line logs as /:value/:id). The same sanitizer is applied to rate-limit events, security events, and Sentry's request.url; Sentry additionally nulls user.ipAddress, drops queryString entirely, and sets isSendDefaultPii = false.

> backend:plugins/CallLogging.kt:11-21; observability/TdayObservability.kt:8-48 (staticSegments), 50-60 (sensitiveQueryKeys), 72-89 (sanitizePath, query dropped at :73), 141-160 (sanitizeSegment); security/RequestRateLimiter.kt:95-98; Sentry beforeSend at Application.kt:38-51

### Unauthenticated public HTTP surface (minimal and enumerated)

`On by default`

Exactly four route families answer without a session, enumerated from the routing table: GET /health returns only {"status":"ok"} — no version, no build id, no dependency status — and is covered by the infra rate limit at 30/60s; GET /calendar/{token} is the iCalendar feed, authenticated solely by an opaque token in the path (also infra rate-limited, token stripped from all logs by sanitizePath, returns 404 for a blank or unknown token); the three association files /.well-known/assetlinks.json, /.well-known/apple-app-site-association and /apple-app-site-association return only the Apple team id + iOS bundle id and the Android package name + SHA-256 cert fingerprints (public by design; assetlinks returns "[]" when the fingerprints env var is unset); and /api/auth/** which is rate-limited and lockout-guarded per endpoint. Everything else under /api requires an approved session. The container healthcheck hits /health over 127.0.0.1:8080 from inside the container, so readiness does not depend on external reachability.

> backend:plugins/Routing.kt:28-35,56-65; infra policy at plugins/RateLimiting.kt:55-64; routes/CalendarFeedRoutes.kt:12-33; routes/AppleAppSiteAssociationRoutes.kt:15-36 and 54-62 (empty-array fallback); healthcheck at docker-compose.yaml:90-95

### Outbound (egress) URL validation for server-initiated HTTP — SSRF guard (recently hardened)

`On by default`

MISSED BY THE FIRST PASS and squarely in the network-exposure domain: this is the only control governing where the SERVER dials out on a user's instruction. validateOutboundUrl rejects, from the string alone, non-http(s) schemes, embedded userinfo credentials, URLs over 2048 chars (MAX_OUTBOUND_URL_LENGTH), single-label hosts with no dot (which could only resolve to a compose sibling like `database` or `ollama`), and any host that isBlockedIpLiteral matches: 0.0.0.0/8, 10/8, 127/8, 169.254/16 (including the 169.254.169.254 cloud-metadata address), 172.16-31/12, 192.168/16, 100.64-127/10 CGNAT, 224.0.0.0+ multicast/reserved, IPv6 ::, ::1, fe80::/10, fc00::/7 ULA, ff00::/8 multicast, IPv4-mapped forms like ::ffff:127.0.0.1, and the bare names localhost / *.localhost. It is wired into WebhookService.create (WebhookService.kt:105) and PushNotificationService.subscribe (PushNotificationService.kt:112). The second half is at the dispatch site: WebhookDispatchService's Ktor CIO client sets followRedirects = false, so a validated public host cannot 3xx-bounce the request into private space. Honest limit, stated in the source KDoc: hostnames are deliberately NOT resolved at validation time, so DNS rebinding (a public name that resolves to 127.0.0.1 at request time) is NOT blocked — disabled redirects and the string checks are the whole defence. Covered by OutboundUrlValidationTest.kt.

> backend:domain/Validations.kt:48 (MAX_OUTBOUND_URL_LENGTH=2048), 55-60 (DNS-rebinding caveat in KDoc), 62-100 (isBlockedIpLiteral), 106-137 (validateOutboundUrl); services/WebhookService.kt:105; services/PushNotificationService.kt:112; services/WebhookDispatchService.kt:55-61 (followRedirects = false, requestTimeout)

### Cloudflare Tunnel as the only ingress (outbound-only)

`Needs config`

cloudflared runs on the host, dials OUT to Cloudflare's edge, and proxies inbound requests to http://localhost:2525. No port forwarding, no public IP, no firewall hole, no inbound NAT rule. The tunnel is operator-installed and operator-configured — none of it lives in this repo (the config.yml at cloudflare-tunnel.md:78-87 is a sample for the operator to write on the host), so the app ships with zero remote access until an operator sets up one of the documented methods (Cloudflare Tunnel, Tailscale, WireGuard, ZeroTier, SSH tunnel, ngrok, frp). Cloudflare Tunnel is documented as the current production method for tday.ohmz.cloud. Protects against: direct origin attack (origin IP never published), volumetric DDoS absorbed at the edge, cert management errors. Does not protect against: anything arriving as a valid HTTPS request to the hostname.

> docs/remote-access/cloudflare-tunnel.md:3-25,78-87,97-105,160-163; docs/REMOTE_ACCESS.md:69-71

### HSTS with a production gate

`Needs config`

`Strict-Transport-Security: max-age=63072000; includeSubDomains; preload` (2 years, all subdomains, preload-list eligible) — emitted ONLY when config.isProduction is true, which resolves from TDAY_ENV (or the legacy NODE_ENV fallback) equalling "production", case-insensitively; the default when neither is set is "development", i.e. NO HSTS. The gating is correct rather than lazy: an operator on plain HTTP over a LAN would otherwise pin their browser to HTTPS for two years against a server that cannot speak it. The same isProduction flag also drives the __Secure- session cookie prefix. Honest note: .env.docker in this checkout reads TDAY_ENV=development (.env.docker:23), which if it were the deployed value would mean neither HSTS nor a Secure-prefixed cookie is being emitted — the tunnel doc explicitly instructs setting TDAY_ENV=production, so the deployed value must be verified on the server.

> backend:plugins/SecurityHeaders.kt:110-112; isProduction resolution at AppConfig.kt:93 and AppConfig.kt:199-202; .env.docker:23; .env.example:37; docs/remote-access/cloudflare-tunnel.md:111-115

### TLS termination at the Cloudflare edge; cleartext origin hop

`Needs config`

The browser↔Cloudflare leg is HTTPS with a Cloudflare-managed certificate for the custom domain. The Cloudflare↔cloudflared leg rides inside cloudflared's own encrypted outbound connection. The final hop — cloudflared → http://localhost:2525 → container :8080 — is PLAIN HTTP, and the Ktor server has no TLS configuration at all: embeddedServer(Netty, port = config.port, host = "0.0.0.0") with no sslConnector, no keystore, no certificate anywhere in the repo. Trust implications, stated plainly: (a) that cleartext hop never leaves the loopback interface under the default bind, so it is exposed only to root/other processes on the same host — acceptable for a single-tenant self-hosted box, not acceptable if the origin hop ever crosses a real network; (b) Cloudflare is a full man-in-the-middle by design — it terminates TLS and can read every request and response body, including passwords in transit and task contents. The project's own tunnel doc says so at line 169. There is no Cloudflare Authenticated Origin Pull, no origin certificate, and no TLS on the app itself to fall back on.

> backend:Application.kt:54 (host="0.0.0.0", no sslConnector); docker-compose.yaml:77; docs/remote-access/cloudflare-tunnel.md:13,21-25,169; SECURITY.md:81-83

### Mobile client version gate at the edge of the API

`Needs config`

Before auth resolution, the Security intercept can reject requests from outdated native clients: when TDAY_UPDATE_REQUIRED is true AND TDAY_COMPATIBILITY_MODE is "exact" (case-insensitive), any request whose path starts with /api/ (except /api/mobile/probe) carrying X-Tday-Client of `android-compose` or `ios` has its X-Tday-App-Version compared against the server's required version and is answered 426 Upgrade Required (app too old) or 409 Conflict (app newer than server). This is an availability/compatibility control rather than a security boundary — it is header-driven and trivially bypassed by omitting X-Tday-Client or X-Tday-App-Version (both paths `return null`, i.e. no block) — but it is the mechanism by which a client carrying a known-bad protocol can be cut off without a server rollback. Off unless both env vars are set (or the bundled version.json defaults say so).

> backend:plugins/Security.kt:76-81 (intercept entry), 200-237 (mobileVersionBlock); config at AppConfig.kt:141-146

### CSP style-src requires 'unsafe-inline' (documented dependency constraint)

`Partial`

`style-src 'self' 'unsafe-inline'` — genuinely weaker than the rest of the policy, and forced rather than chosen. The source KDoc names the dependencies: sonner (toasts), vaul (drawers), react-style-singleton (every Radix overlay — dialogs, popovers, dropdowns) and next-themes all inject <style> elements with inline text at runtime. Under CSP Level 3 a nonce would DISABLE 'unsafe-inline' rather than supplement it, so adding one would break all of those components. Practical residual risk: CSS-based data exfiltration and UI-redress via injected styles remain possible if an attacker can already inject markup — but they cannot execute script, which is the far larger prize.

> backend:plugins/SecurityHeaders.kt:43-47 (rationale),76 (directive); test at SecurityHeadersTest.kt:22-27

### Static-file serving: containment check and cache policy

`Partial`

The SPA is served by the same Ktor process from STATIC_FILES_DIR (set to /app/static in the shipped image, so it is on in the Docker deployment; the catch-all route does not register at all if the var is unset or the path is not a directory). Requests whose relative path starts with `api/` or `ws` return early so static serving can never shadow an API route. Cache-Control is set per file class and is exactly as claimed: `no-cache, no-store, must-revalidate` for version.json, for the empty path, and for any .html; `public, max-age=31536000, immutable` for content-hashed files under assets/; `public, max-age=3600` for everything else; the unknown-route SPA shell is also no-store. MARKED PARTIAL — the first pass overclaimed the traversal guard. The check is `candidate.isFile && candidate.path.startsWith(dir.path)` on canonical paths: canonicalization does defeat plain `../` escapes and symlink escapes UP the tree, but `startsWith` is a raw string prefix with no trailing separator, so a canonical path in a SIBLING directory whose name begins with the root's name (e.g. relPath `../static-old/x` resolving to /app/static-old/x under root /app/static) satisfies the check. No such sibling exists in the shipped image (/app holds app.jar, migrations, static), so it is not currently exploitable — but the guard is weaker than "canonical-path containment" implies and would break if anyone added /app/static-<anything>.

> backend:plugins/Routing.kt:86-108 (route + containment check at :95), 112-127 (cache policy); Dockerfile.backend:41,44 (COPY dist → /app/static, ENV STATIC_FILES_DIR=/app/static)

### Session cookie transport flags

`Partial`

Session cookies are issued with httpOnly = true (unreadable from JS, so an XSS cannot exfiltrate the session), SameSite=Lax via a cookie extension (the cookie is not attached to cross-site subresource requests or cross-site POSTs — this is what actually blocks CSRF and cross-site WebSocket auth here), path=/, and secure = true when the cookie name starts with `__Secure-` OR isProduction. The cookie name is environment-dependent: `__Secure-authjs.session-token` in production, plain `authjs.session-token` otherwise, and issuing one always writes an expired deletion cookie for the other name so the two cannot coexist. Marked partial because the Secure flag and the __Secure- prefix both hang off the same TDAY_ENV=production gate as HSTS — in a deployment left at TDAY_ENV=development but exposed over HTTPS, the session cookie is transmitted without Secure.

> backend:security/SessionCookies.kt:8-11 (names, path, SameSite), 18-19 (name selection), 23-47 (issue + delete-the-other), 84-97 (buildSessionCookie flags); isProduction resolution at AppConfig.kt:93,199-202

### Not present — network exposure, tls & http security headers

- **No Cloudflare Access / Zero Trust identity layer in front** — The tunnel publishes the app to the open internet; the login page itself is the first authentication boundary. There is no Cloudflare Access application policy, no SSO/OTP/device-posture check, and no service-token requirement ahead of the origin. Practical consequence for a single self-hosted user: every bot that finds the hostname (see the CT-log note on the loopback-bind control) can reach /api/auth/callback/credentials and /api/auth/register directly. The app-layer defences that carry that load are the throttle/lockout stack and the PENDING-approval gate — the network layer contributes nothing here. Cloudflare Access on the free Zero Trust tier would put an identity challenge in front of the whole origin and is the single highest-leverage addition to this posture; the docs mention Zero Trust only as the billing tier that covers the tunnel, and note that Access policies 'may require a paid plan'.
- **No mTLS / Authenticated Origin Pull anywhere** — Neither the edge→origin hop nor the client→server hop uses mutual TLS. The Ktor server has no sslConnector, no keystore, and no client-certificate configuration; there is no Cloudflare Authenticated Origin Pull certificate. Consequence: any process that can reach 127.0.0.1:2525 on the host — or anything on the LAN, given this checkout's TDAY_HOST_BIND=0.0.0.0 — talks to the backend as an equal peer with no transport-level identity, and the app cannot distinguish traffic that arrived via cloudflared from traffic that did not. That is also precisely why forged CF-Connecting-IP headers would be accepted in that scenario.
- **No WAF or edge rate-limit rules asserted anywhere in code or config** — The Cloudflare edge rules — Managed Challenge plus rate limits on /api/auth/callback/credentials (12 req/5 min per IP), /api/auth/register (6 req/60 min per IP) and /api/auth/csrf (40 req/1 min per IP) — exist only as a prose checklist an operator must apply by hand in the Cloudflare dashboard. Nothing in the repo creates, verifies, or drift-detects them: no Terraform, no cloudflared config, no Cloudflare API client, no test. Consequence: this document cannot claim edge protection at all. Everything actually enforced is enforced in-process by RateLimiting.kt and AuthThrottle.kt, which means an attacker's traffic reaches and costs the origin before it is refused.
- **Server version banner is not suppressed** — Ktor's DefaultHeaders plugin — the same one used to emit the security headers — also adds a `Server: Ktor/3.0.3` header (and a Date header) to every response, and SecurityHeaders.kt never overrides or clears it. Consequence: every response advertises the exact framework and patch version, free reconnaissance for anyone matching a future Ktor advisory against live hosts. Fixing it is one line inside the existing DefaultHeaders block. Low severity, but SECURITY.md's header table does not mention it.
- **The /api/auth/csrf endpoint mints a token that nothing ever validates** — GET /api/auth/csrf generates 32 bytes from SecureRandom, hex-encodes them, and returns {"csrfToken": ...}. The string "csrfToken" appears in exactly one file in the whole backend — that endpoint. There is no double-submit cookie, no header comparison, no per-session binding, and no route that rejects a request for a missing or wrong token. It is an Auth.js compatibility shim, not a control. What actually prevents CSRF here is SameSite=Lax on the session cookie plus the empty-by-default CORS allowlist — genuinely adequate for a browser SPA on a modern browser, but it means the endpoint's existence overstates the posture. Score this as "SameSite-based CSRF defence", not "CSRF tokens". (The endpoint is at least rate-limited: it runs ThrottleAction.csrf at 40/60s.)
- **No Origin check on the WebSocket handshake** — The /ws upgrade is not subject to the CORS plugin (WebSocket handshakes bypass CORS by design) and the handler performs no explicit Origin- or Host-header validation before authenticating from the session cookie. In practice this is currently mitigated — SameSite=Lax means a cross-site WebSocket handshake, a subresource request rather than a top-level navigation, does not carry the session cookie, so a foreign page's socket authenticates as nobody and is closed with 1008. But the mitigation is incidental rather than asserted: it lives in a cookie attribute, not in the socket handler, and nothing tests it. A future switch to SameSite=None would silently open a cross-origin read of the user's realtime event stream.
- **No HTTP→HTTPS redirect and no forwarded-proto awareness in the app** — Ktor's HttpsRedirect plugin is not installed, and neither is ForwardedHeaders/XForwardedHeaders. The application therefore has no notion of whether the original request was HTTPS — it only ever sees a cleartext request on the loopback hop — and cannot enforce, redirect to, or reason about TLS. All scheme enforcement is delegated to the Cloudflare edge and to the HSTS header, which only helps browsers that have already made one successful HTTPS visit. Consequence: if the app is ever fronted by something that does not redirect, or reached directly with TDAY_HOST_BIND widened to 0.0.0.0 (as this checkout's .env sets), it will happily serve the SPA and accept credentials over plain HTTP with no complaint.
- **No request body size limit or per-request timeout configured** — Nothing sets a maximum request body size, a request-read timeout, or a response-write timeout; the WebSocket maxFrameSize of 64 KiB is the only size bound anywhere in the pipeline. Consequence: an authenticated, approved user can POST an arbitrarily large JSON body to any /api route and the server will buffer and attempt to deserialize it, bounded only by the JVM heap and by api_global at 180 requests/minute — 180 large bodies per minute is plenty to cause memory pressure, and the compose file deliberately sets no mem_limit. Cloudflare's own default request-body cap is currently the only real ceiling, and it lives at the edge, not in the app. Note this sits behind the approval wall, so it is a trusted-user DoS, not an anonymous one.
- **CSP has no reporting endpoint** — buildCspHeader emits no `report-uri` and no `report-to` directive, and there is no route in the backend that accepts CSP violation reports. Consequence: the CSP_MODE=report-only rollback mode is much weaker than it looks — violations surface only in each individual browser's devtools console, so an operator who flips to report-only to diagnose a breakage has no aggregated signal and no way to learn about violations real users hit. There is also no passive detection of an injection attempt that the enforcing policy successfully blocked.
- **No cross-origin isolation headers (COOP / COEP / CORP)** — Not previously listed, and genuinely absent: the response set contains no Cross-Origin-Opener-Policy, Cross-Origin-Embedder-Policy or Cross-Origin-Resource-Policy header. Consequence for a self-hosted SPA: a page the user opens elsewhere that window.open()s this app retains a same-browsing-context-group handle to it (COOP: same-origin would sever that), and this origin's static assets and JSON responses can be embedded as subresources by any other site (CORP: same-origin would refuse). Low practical severity given script-src 'self' and SameSite=Lax already carry the main load, and COEP in particular would need auditing against the SPA's asset loading — but it is a real difference if the service being compared against sets them.
- **SECURITY.md's security-header table is stale (understates the code)** — The published header table lists only X-Content-Type-Options, X-Frame-Options, Referrer-Policy and HSTS. It predates this session's work and omits both Content-Security-Policy and Permissions-Policy, so the repo's own security document currently UNDERSTATES what the code emits. Worth recording because the audit already flagged SECURITY.md as overclaiming elsewhere: the fix should reconcile the table against SecurityHeaders.kt:101-112 rather than being written from memory. CORRECTION vs the first pass, which claimed SECURITY.md omits the fact that a repo-root .env can override the loopback bind — it does NOT omit it: SECURITY.md:80 explicitly documents TDAY_HOST_BIND=0.0.0.0 as an opt-in and calls it discouraged. The only honest complaint there is that no document records what the deployed server's value actually is, while this checkout's .env:1 sets 0.0.0.0.

---

## Container & runtime hardening

### Backend capability drop (cap_drop: ALL)

`On by default`

The tday-backend service drops every Linux capability — `cap_drop:` / `- ALL` — so the JVM process cannot bind ports below 1024 (CAP_NET_BIND_SERVICE), chown files it does not own, load kernel modules, or use CAP_SYS_ADMIN-class syscalls even if it were uid 0. Nothing is added back: no `cap_add` key appears anywhere in the file. Consistent with the app's own listener, which is port 8080 (AppConfig.kt:88 `env("PORT", "8080")`, unset in .env.docker, EXPOSE 8080 at Dockerfile.backend:43). Applies to the backend ONLY — `database` (lines 2-20), `ollama` (22-41) and `ollama-model-setup` (43-58) have no cap_drop key and keep Docker's default capability set.

> docker-compose.yaml:98-99; absence confirmed by grep for cap_drop across the whole 106-line file (single hit); port default at backend:config/AppConfig.kt:88

### no-new-privileges on the backend

`On by default`

`security_opt:` / `- no-new-privileges:true` sets the kernel NO_NEW_PRIVS bit for the container's process tree, so a setuid/setgid binary or file capability inside the image cannot raise privileges after exec — a compromised backend process cannot re-escalate through an suid helper. Verified present on tday-backend only; grep for `security_opt` across the file returns exactly this one hit, so database, ollama and ollama-model-setup do not have it.

> docker-compose.yaml:96-97

### Non-root container user (USER tday)

`On by default`

The final runtime stage creates a system group and user (`RUN addgroup -S tday && adduser -S tday -G tday`, line 37) and switches to it with `USER tday` (line 42) before EXPOSE/ENTRYPOINT. All three COPY steps (lines 39-41: app.jar, /app/migrations, /app/static) execute earlier and carry no `--chown`, so those paths are root-owned and not writable by the runtime user — the process cannot overwrite its own jar or the served SPA bundle. CORRECTION to the first pass: the claims about the Postgres and Ollama images' internal users are NOT verifiable from this repo (no image was inspected and Docker is not available on this machine). What IS verifiable is that docker-compose.yaml contains no `user:` key on any service, so every non-backend container runs whatever user its upstream image declares, unaudited here.

> Dockerfile.backend:37, :39-41, :42, :43; no `user:` key anywhere in docker-compose.yaml (grep across full file)

### pids_limit on long-running services (recently hardened)

`On by default`

`pids_limit: 512` caps the pid cgroup for database (line 6), ollama (line 28) and tday-backend (line 75). This bounds fork-bomb / runaway-thread-creation DoS: a JVM thread leak or a hostile path that spawns threads hits a hard ceiling instead of exhausting host PIDs and taking every other container on the box down with it. 512 is generous for a Ktor/Netty server plus Hikari's 10-connection pool, so it should not be reached in normal operation (an assessment, not a measured figure). Partial coverage: the one-shot `ollama-model-setup` service (43-58) has no pids_limit.

> docker-compose.yaml:6, :28, :75; absence at :43-58

### Postgres healthcheck plus ordered startup dependency

`On by default`

The database service runs `["CMD-SHELL", "pg_isready"]` every 1s, timeout 5s, 10 retries, and the backend declares `depends_on:` / `database:` / `condition: service_healthy`. The backend container is not started until Postgres accepts connections, preventing a boot race where Flyway fails against a not-yet-ready server and — under `restart: always` — becomes a crash loop. `ollama-model-setup` uses the same pattern against ollama's own healthcheck (`ollama list`, 20s/10s/5 at lines 35-39, depended on at 48-50).

> docker-compose.yaml:7-11 and :100-102; ollama pattern at :35-39 and :48-50

### Multi-stage build — no toolchain in the runtime image

`On by default`

Three stages: `node:20-alpine AS frontend` (line 1) builds the Vite SPA, `eclipse-temurin:21-jdk-alpine AS backend` (line 19) builds the fat jar with `./gradlew :tday-backend:buildFatJar --no-daemon -x test` (line 34), and the final stage is `eclipse-temurin:21-jre-alpine` (line 36). Exactly three artifacts are copied forward: the fat jar as app.jar (39), the Flyway migration SQL to /app/migrations (40), and the built SPA to /app/static (41), pointed at by `ENV STATIC_FILES_DIR=/app/static` (44). The runtime image therefore carries no JDK/javac, no Gradle or Gradle caches, no node/npm or node_modules, and no Kotlin/TypeScript sources — removing compilers and package managers an attacker could use post-exploitation. Busybox sh and wget remain (alpine base), which the healthcheck depends on.

> Dockerfile.backend:1, :19, :34, :36, :39-41, :44-45

### Frontend dependencies installed from a committed, integrity-checked lockfile

`On by default`

The frontend build stage runs `COPY tday-web/package*.json ./` then `RUN npm ci` (Dockerfile.backend:14-15), and CI does the same (pr-gate.yml:60-61 and the equivalent step in release.yml). `npm ci` refuses to run without a lockfile and installs exactly the locked tree rather than re-resolving semver ranges, and npm verifies each downloaded tarball against the `integrity` hash in the lockfile. tday-web/package-lock.json is lockfileVersion 3 and contains 736 `sha512-` integrity entries. Net effect: a compromised or repointed npm dist-tag cannot silently change what lands in the SPA bundle between builds. Scope limit: this covers the npm side only — see the gap on Gradle, which has no equivalent pinning.

> Dockerfile.backend:14-15; .github/workflows/pr-gate.yml:60-61; lockfile verified with `grep -c '"integrity": "sha512-' tday-web/package-lock.json` → 736, lockfileVersion 3 at line 4

### .dockerignore excludes secrets, VCS history, and build junk

`On by default`

The build context excludes `*.pem` (line 5), `.env*` (line 9) with `!.env.example` re-included (line 10), `.git` (14), `.cursor` (15), root `node_modules` (line 1), `tday-web/node_modules` and `tday-web/dist` (18-19), and all Gradle/Vite build output (20-27). Two independent reasons no secret reaches an image layer: (a) these ignore rules, and (b) the Dockerfile never does a blanket `COPY . .` — it copies explicit subpaths only (tday-web/package*.json, tday-web/, gradlew, gradle/wrapper, docker/*.gradle.kts, version.json, tday-backend/*, shared/src), so the root `.env` and `.env.docker` are not candidates in the first place. Docker's `.env*` pattern matches context-root only; I confirmed with `find . -name '.env*'` that the only env files in the tree are ./.env, ./.env.docker, ./.env.example and tday-backend/.env.example (the last two contain no real values). Excluding `.git` also keeps the repo's leaked-secret history out of images. Minor correction to the first pass: root `node_modules` is line 1, not within 18-27.

> .dockerignore:1, :5, :9-10, :14-15, :18-27; explicit COPY paths at Dockerfile.backend:14, :16, :21-33

### Datastore and inference services have no published ports

`On by default`

Neither `database` (2-20) nor `ollama` (22-41) declares a `ports:` key — the only `ports:` in the file is the backend's at line 76. They are reachable only over the compose default bridge, by service name (database:5432, ollama:11434). Postgres is therefore not exposed to the host or LAN at all, and Ollama — configured with `OLLAMA_HOST: 0.0.0.0:11434` (line 34) and with nothing in this compose file putting authentication in front of it — is likewise reachable only from sibling containers. Ollama and ollama-model-setup are additionally behind `profiles: ["ai"]` (lines 26, 47), so they do not start unless the stack is brought up with `--profile ai`.

> docker-compose.yaml:2-20 (no ports key), :22-41 (no ports key; OLLAMA_HOST at :34; profile at :26), :47; sole `ports:` at :76

### Restart policy

`On by default`

`restart: always` on database (:5), ollama (:27) and tday-backend (:70) — the stack self-heals across host reboots, OOM kills and crashes without an external supervisor. The one-shot `ollama-model-setup` (43-58) correctly has no restart key, so it runs once and exits. The availability upside is real, and it is also the stated reason memory limits were left off (see gaps): the comment at :71-74 notes that a JVM exceeding a too-low mem_limit under `restart: always` produces an infinite restart loop rather than a single failure.

> docker-compose.yaml:5, :27, :70; no restart key at :43-58; reasoning at :71-74

### Persistent data on named volumes

`On by default`

Postgres data lives on the named volume `postgres_data:/var/lib/postgresql/data` (19-20) and Ollama models on `ollama_data:/root/.ollama` (40-41); both are declared under the top-level `volumes:` block (104-106). These are the only two volume mounts in the file — no host bind mount appears anywhere, so no host directory is exposed into any container. Backup and encryption-at-rest of those volumes is the host's responsibility; nothing in the stack encrypts them.

> docker-compose.yaml:19-20, :40-41, :104-106

### CI actions pinned to full commit SHAs

`On by default`

Every third-party GitHub Action in both workflows is pinned to a 40-char commit SHA with the version as a trailing comment: actions/checkout@34e114876b0b11c390a56381ad16ebd13914f8d5 (v4.3.1), actions/setup-node@49933ea5288caeca8642d1e84afbd3f7d6820020 (v4.4.0), actions/setup-java@c1e323688fd81a25caa38c78aa6df2d33d3e20d9 (v4.8.0), docker/login-action@c94ce9fb468520275223c153574b00df6fe4bcc9 (v3.7.0), docker/setup-buildx-action@8d2750c68a42422c14e847fe6c8ac0403b4cbd6f (v3.12.0), docker/build-push-action@10e90e3645eae34f1e60eeb005ba3a3d33f178e8 (v6.19.2). This defeats the standard supply-chain attack where a mutable tag like `@v4` is repointed at malicious code. release.yml also narrows the token with a workflow-level `permissions: contents: write, packages: write` instead of the repo default. Correction: pr-gate.yml has pins at :24, :48, :51 and :75 (the first pass omitted :48), and pr-gate.yml declares NO `permissions:` block, so it runs with the repository default token scope.

> .github/workflows/release.yml:7-9, :20, :23, :47, :61, :103, :110, :113; .github/workflows/pr-gate.yml:24, :48, :51, :75 (full 87-line read shows no permissions key)

### CI test gate before image publish

`On by default`

The release job chain is `lint-and-test` (release.yml:15-53) → `build-and-release` (:57-58, `needs: lint-and-test`), so no image is pushed to GHCR unless the version-mirror check, frontend lint, frontend tests and `./gradlew :tday-backend:test` all pass. The PR gate additionally runs shared-module tests (`:shared:jvmTest`) and a guide-content staleness check, and blocks any PR into master whose head_ref is not `develop` (pr-gate.yml:8-17). This is the only place the new security unit tests actually execute, because the Docker image build skips tests (`buildFatJar --no-daemon -x test`, Dockerfile.backend:34). Caveat the first pass missed: BOTH release jobs carry `if: ${{ !contains(github.event.head_commit.message, '[skip release]') }}` (release.yml:16 and :22), so a commit message containing that string skips the test job — it also skips the publish job, so nothing untested gets published, but a push can bypass the gate entirely.

> .github/workflows/release.yml:14-17, :22, :52-53, :57-58; .github/workflows/pr-gate.yml:8-17, :43-87; test skip at Dockerfile.backend:34

### Production deploy builds from source on the host, not from the registry

`On by default`

The compose backend service has a `build:` block pointing at Dockerfile.backend (61-63) and the redeploy script runs `docker compose up -d --build tday-backend` over SSH (script:68), so production runs an image built on the deploy host from that host's own checkout. The GHCR images (ghcr.io/ohmzi/tday:latest and :vX.Y.Z, release.yml:112-125) are a distribution artifact for others, not the production runtime. Security consequence stated plainly: production never pulls a remote image, so a compromised registry account cannot directly change what runs — but equally, production runs code that never had to pass the CI gate, and neither path carries a signature or provenance attestation. CORRECTION to the first pass: the redeploy script does NOT ship the backend source — it rsyncs only tday-web/src/, tday-web/index.html and tday-web/public/ (script:55-65). Backend Kotlin changes must reach the host by some other route (a git pull on the host), which the script does not perform, so running this script alone rebuilds the SPA against whatever backend source the host already had.

> docker-compose.yaml:61-63, :68; scripts/redeploy-remote-backend.sh:55-65 and :68; registry push at .github/workflows/release.yml:112-125

### Boot-time security warnings for unset protective config

`On by default`

In production only (`if (!config.isProduction) return` at Application.kt:123) the app logs a warning at startup for each unset security-relevant setting: field encryption unconfigured — the message explicitly says sensitive fields are stored as PLAINTEXT in Postgres, and the code comment notes field encryption is fail-open so `encryptIfSensitive` silently returns plaintext; AUTH_CREDENTIALS_PRIVATE_KEY unset (credential envelope encryption falls back to an ephemeral key); APPLE_TEAM_ID unset; ANDROID_SHA256_CERT_FINGERPRINTS unset. These are `logger.warn` calls, not hard failures — the server starts either way. In non-production the function returns immediately and logs nothing.

> backend:Application.kt:122-146, invoked at :89

### Database connection pool ceiling

`On by default`

HikariCP is configured in `DatabaseConfig.init()` (starts line 40) with maximumPoolSize = 10, minimumIdle = 2, idleTimeout = 600_000 ms (10 min), connectionTimeout = 30_000 ms (30 s), maxLifetime = 1_800_000 ms (30 min), isAutoCommit = false. The pool cap is a de facto resource-exhaustion bound: no matter how many concurrent requests arrive, the backend cannot open more than 10 Postgres connections, so a request flood degrades into queueing and 30s connection timeouts rather than exhausting Postgres's own max_connections.

> backend:config/DatabaseConfig.kt:40, :42-53

### Container timezone pinned to UTC by default

`On by default`

`TZ: ${TZ:-UTC}` on the backend (line 84, with the explanatory comment at 81-83 noting IANA names only). Minor but forensically relevant: log timestamps and any server-local time logic are UTC unless the operator deliberately sets a zone in the root .env, so incident timelines across containers line up without conversion guesswork. The root .env in this checkout does not set TZ, so UTC is in force here. CI mirrors this for frontend tests (`TZ: UTC`, pr-gate.yml:71-72).

> docker-compose.yaml:81-84 (TZ key at :84); .env (no TZ key); .github/workflows/pr-gate.yml:71-72

### Database credentials — overridable, weak by default

`Needs config`

Postgres credentials are `POSTGRES_USER: ${POSTGRES_USER:-myuser}`, `POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-mypass}`, `POSTGRES_DB: ${POSTGRES_DB:-mydb}` — real overrides exist, but the fallbacks are the well-known weak pair, and the backend's DATABASE_URL in .env.docker still ships as `postgresql://myuser:mypass@database:5432/mydb`, so both must be changed together (the inline comment at 13-15 says exactly this). In this checkout the root .env sets only TDAY_HOST_BIND and TDAY_HOST_PORT, so the myuser/mypass defaults are in force here. Practical exposure is limited by the missing published port: using these credentials requires already having code execution inside a container on the compose network or on the host. Residual risk is lateral — anything the owner later attaches to that default bridge can log into Postgres with guessed credentials.

> docker-compose.yaml:16-18 with comment at :13-15; .env.docker:80; .env (2 lines, neither a POSTGRES_* key)

### Build-arg separation for public vs private telemetry config (recently hardened)

`Needs config`

Vite inlines VITE_* values at build time, so browser Sentry config cannot come from env_file. The compose build block passes `VITE_SENTRY_DSN: ${VITE_SENTRY_DSN:-}` and `VITE_SENTRY_TRACES_SAMPLE_RATE: ${VITE_SENTRY_TRACES_SAMPLE_RATE:-}` as build args (64-67, with the comment at :65 stating both are public values), and the Dockerfile receives them as ARG/ENV in the frontend stage only (8-13). The security-relevant property is the discipline: only already-public values (a browser-visible DSN, a sample rate) become build args, since build args land in `docker history`. The backend's own SENTRY_DSN stays in env_file at runtime (.env.docker:~SENTRY_DSN, blank by default). Both defaults are empty, so telemetry is off unless the operator sets them. Addition the first pass missed: the CI publish step passes NO build args at all (release.yml:113-125 has only context/file/push/tags/labels/cache), so the GHCR image's SPA has no Sentry DSN and the GIT_SHA build-id arg falls back to its `dev` default (Dockerfile.backend:6-7).

> docker-compose.yaml:64-67 with comment at :65; Dockerfile.backend:6-7, :8-13; .github/workflows/release.yml:113-125

### Retention scheduler ships in dry-run (recently hardened)

`Needs config`

RetentionScheduler purges eventlog / auththrottle / authsignal / cronlog on a loop whose `TICK_INTERVAL` is `Duration.ofHours(6)` (RetentionScheduler.kt:162, `delay(TICK_INTERVAL.toMillis())` at :69), with per-table windows RETENTION_EVENTLOG_DAYS=90, RETENTION_AUTHTHROTTLE_DAYS=30, RETENTION_AUTHSIGNAL_DAYS=180, RETENTION_CRONLOG_DAYS=90 `.coerceAtLeast(7)` (the floor exists because cronlog holds the reminder scheduler's last-run bookmark — the comment at AppConfig.kt:156-157 says trimming it too hard drops reminders). It is launched at startup from the application module. Critically for a self-hosted deployment, `RETENTION_DRY_RUN` defaults to "true" (AppConfig.kt:164) — the first release logs what it would delete and deletes nothing, so an operator reads a cycle's output before a DELETE runs against their only copy of the data. Data minimisation is therefore NOT in effect on a default install; the operator must set RETENTION_DRY_RUN=false.

> backend:config/AppConfig.kt:156-164; interval at backend:services/RetentionScheduler.kt:69 and :162; launched at backend:Application.kt:94 and :114-120

### Backend healthcheck and readiness semantics

`Partial`

The compose healthcheck is `["CMD", "wget", "--spider", "-q", "http://127.0.0.1:8080/health"]`, interval 30s, timeout 5s, retries 3, start_period 40s. It validates less than the inline comment above it (lines 88-89) claims: the /health handler is a static `call.respond(mapOf("status" to "ok"))` with no database round-trip. It proves the HTTP listener answers. It transitively proves the DB was reachable and Flyway migrations succeeded AT BOOT, because `dbConfig.init()` runs inside a try that logs and rethrows before the server serves anything — a migration or connection failure means the process never comes up. It does NOT detect a Postgres that dies after boot: the backend keeps reporting healthy while every request 500s. Caveat on the tooling: the healthcheck assumes `wget` is on PATH in eclipse-temurin:21-jre-alpine (busybox wget is expected on an alpine base) — I could not run the image to confirm, so treat that as unverified.

> docker-compose.yaml:90-95; handler at backend:plugins/Routing.kt:28-30; boot-time DB gate at backend:Application.kt:65-71

### Host port binding defaults to loopback

`Partial`

The backend's only port mapping is `"${TDAY_HOST_BIND:-127.0.0.1}:${TDAY_HOST_PORT:-2525}:8080"`. The DEFAULT binds to loopback, the correct pairing with the Cloudflare Tunnel model (the tunnel connects out to 127.0.0.1:2525; nothing is reachable from the LAN or the internet directly). Reported as partial, not on-by-default, because this checkout's root .env overrides it — the file contains exactly two lines, `TDAY_HOST_BIND=0.0.0.0` and `TDAY_HOST_PORT=2525` — so on whatever host uses that env file the backend is published on every host interface and is reachable from the LAN without traversing the tunnel. .env is gitignored (git ls-files shows only .env.example and tday-backend/.env.example tracked), so the deploy host may carry a different value; verify there specifically.

> docker-compose.yaml:76-77; override at .env:1-2 (whole file)

### Image pinning and pull policy

`Partial`

Base images are pinned by major-version tag, never by digest: `postgres:15` (compose:3), `node:20-alpine` (Dockerfile:1), `eclipse-temurin:21-jdk-alpine` (Dockerfile:19), `eclipse-temurin:21-jre-alpine` (Dockerfile:36). That prevents a surprise major upgrade but still floats patch content, so a rebuild can silently change the base layer. Ollama is fully floating: `ollama/ollama:latest` with `pull_policy: always` on BOTH the ollama service (23-24) and ollama-model-setup (44-45), so every `docker compose up --profile ai` fetches whatever upstream currently tags latest. The app image is `tday-backend:latest` (compose:68), built locally. A grep for `@sha256` across docker-compose.yaml returns nothing — no digest pin anywhere.

> docker-compose.yaml:3, :23-24, :44-45, :68; Dockerfile.backend:1, :19, :36; `grep @sha256 docker-compose.yaml` → no matches

### Secret delivery: env_file plus partial *_FILE indirection

`Partial`

Default path is `env_file:` / `- .env.docker` — the whole secret set (AUTH_SECRET, CRONJOB_SECRET, DATABASE_URL, DATA_ENCRYPTION_KEY(S), AUTH_CREDENTIALS_PRIVATE_KEY, VAPID keys, TDAY_PROBE_ENCRYPTION_KEY) is injected as process environment variables, readable by anyone who can run `docker inspect tday_backend`, `docker compose config`, or read /proc/<pid>/environ on the host. Env vars are not a confidentiality boundary against host-level access. A stronger, opt-in path exists: `AppConfig.secret(envVar, fileEnvVar)` prefers the direct env var and otherwise reads and trims the contents of the path in `<NAME>_FILE`, printing to stderr on read failure and returning null. IMPORTANT CORRECTION to the first pass: the indirection is implemented for exactly EIGHT secrets — DATABASE_URL_FILE (:89), AUTH_SECRET_FILE (:91), AUTH_CREDENTIALS_PRIVATE_KEY_FILE (:100), DATA_ENCRYPTION_KEY_FILE (:102), DATA_ENCRYPTION_KEYS_FILE (:103), TDAY_PROBE_ENCRYPTION_KEY_FILE (:147), VAPID_PUBLIC_KEY_FILE (:152), VAPID_PRIVATE_KEY_FILE (:153) — but .env.docker ALSO documents `CRONJOB_SECRET_FILE` (:29), `AUTH_CAPTCHA_SECRET_FILE` (:126) and `DATA_ENCRYPTION_AAD_FILE` (:158), and AppConfig has no `_FILE` reader for any of those three (DATA_ENCRYPTION_AAD is read by plain `env("DATA_ENCRYPTION_AAD")` at :104; a grep for CRONJOB_SECRET/AUTH_CAPTCHA_SECRET in AppConfig.kt returns no `secret(` call). An operator who follows those three comments would get a silently unset secret. All the _FILE lines in .env.docker ship commented out. Neither .env nor .env.docker is tracked in git.

> docker-compose.yaml:78-79; reader at backend:config/AppConfig.kt:204-218; call sites :89, :91, :100, :102, :103, :147, :152, :153; plain-env AAD at :104; unimplemented commented examples at .env.docker:29, :126, :158; `git ls-files | grep .env` → only .env.example and tday-backend/.env.example

### Post-deploy health verification in the redeploy script

`Partial`

After rebuilding, the script runs `docker compose ps tday-backend` (appended to the same SSH command at :68) and then `curl -sf http://127.0.0.1:2525/health` on the remote host (:71), so a deploy that produced a non-booting container is visible immediately. It is advisory only: the curl is suffixed with `|| true`, so a failed health check neither fails the script nor triggers a rollback, and there is no rollback path in the file at all. The script does run under `set -euo pipefail` (:10), so the rsync and build steps abort on error. Status corrected from on-by-default to partial — the check runs by default but cannot fail the deploy.

> scripts/redeploy-remote-backend.sh:10, :68, :70-71

### Not present — container & runtime hardening

- **No read-only root filesystem, no tmpfs** — No service declares `read_only: true` or a `tmpfs:` mount. The backend's container filesystem is writable outside the named volumes, so a code-execution bug can drop a payload anywhere the `tday` user can write (/tmp, its home). The partial mitigation already in place is that /app is root-owned while the process runs as tday, so the jar and the served SPA cannot be overwritten — but that is a side effect of COPY ordering, not an explicit control. Adding `read_only: true` plus a small tmpfs for /tmp is the obvious next step and is low risk for a JVM that writes nothing but logs to stdout.
- **No memory or CPU limits (deliberate)** — No `mem_limit`, `memswap_limit`, `cpus`, `ulimits`, `sysctls` or `deploy.resources` block on any service — a grep for all of those returns only the word `mem_limit` inside a comment at line 73. This is a documented, reasoned omission: the comment at :71-74 explains that a JVM under `restart: always` hitting a too-low memory ceiling produces an OOM crash loop, which is worse than the memory-exhaustion DoS the limit defends against, and that a limit should only be set after measuring real RSS and pairing it with `-XX:MaxRAMPercentage`. Practical consequence today: a memory leak or a heavy Ollama inference run can consume host RAM until the kernel OOM-killer picks a victim, possibly one of the owner's other self-hosted services on the same host. pids_limit covers the fork/thread axis; nothing covers the memory or CPU axis.
- **No image vulnerability scanning, SBOM, or provenance in CI** — The release workflow builds and pushes to ghcr.io/ohmzi/tday with docker/build-push-action but passes only context, file, push, tags, labels and gha cache — no `provenance:` or `sbom:` inputs. Neither workflow contains a Trivy/Grype/Snyk/Docker-Scout step, a CodeQL step, a `gradle dependencyCheck`, an `npm audit`, or any secret scanner (gitleaks/trufflehog). There is no .github/dependabot.yml. Practical consequence: a CVE in the postgres:15, node:20-alpine or eclipse-temurin base layers, or in any Gradle/npm dependency, surfaces only when the owner happens to rebuild and read release notes — nothing in the pipeline will tell them. For a stack whose only public exposure is a Cloudflare Tunnel behind an approval-gated login this is moderate rather than acute, but it is a real difference versus a service that gates merges on a scanner.
- **Gradle distribution and JVM dependencies are not integrity-pinned** — NEW — missed by the first pass. gradle/wrapper/gradle-wrapper.properties points at `https://services.gradle.org/distributions/gradle-9.4.1-bin.zip` with NO `distributionSha256Sum` line; `validateDistributionUrl=true` only sanity-checks the URL, it does not verify the archive's contents. There is also no `gradle/verification-metadata.xml`, so Gradle dependency verification (checksum/signature pinning for every resolved JVM artifact) is off. The backend Docker stage runs `./gradlew :tday-backend:dependencies` and `buildFatJar` inside the image build, downloading the Gradle distribution and the entire JVM dependency tree over the network with TLS as the only integrity guarantee. This is a direct asymmetry with the frontend, which does get lockfile integrity hashes via `npm ci`.
- **Docker secrets not used by default** — There is no top-level `secrets:` block and no `secrets:` key on any service, so the tmpfs-backed /run/secrets mechanism is unused out of the box. Every secret reaches the backend as a process environment variable via `env_file: .env.docker`, readable by anyone with host access through `docker inspect tday_backend`, `docker compose config`, or /proc/<pid>/environ. The application side is partly ready for the better pattern — AppConfig implements `<NAME>_FILE` indirection for 8 secrets — but compose never wires it up, and three of the `_FILE` variables documented in .env.docker (CRONJOB_SECRET_FILE, AUTH_CAPTCHA_SECRET_FILE, DATA_ENCRYPTION_AAD_FILE) have no reader in the code at all, so adopting the pattern requires both a compose edit and care about which variables actually work.
- **Container hardening is backend-only** — cap_drop, no-new-privileges and the non-root USER apply to tday-backend alone. The `database`, `ollama` and `ollama-model-setup` services run with Docker's full default capability set, without NO_NEW_PRIVS, and with no `user:` override anywhere in the file — so each runs whatever user its upstream image declares (not audited here; the first pass's assertion that the Ollama container runs as root was not verified and has been softened). Ollama is configured to listen on 0.0.0.0:11434 and nothing in this compose file puts authentication in front of it. Because there is no `networks:` section, all four services share one default bridge, so any container on that network can reach Ollama's API and Postgres's port directly. Blast radius: a compromise of the Ollama container (only started under `--profile ai`) is an unconstrained-capability process that can talk to Postgres with the myuser/mypass defaults.
- **No TLS between backend and Postgres** — DATABASE_URL is `postgresql://myuser:mypass@database:5432/mydb` with no `sslmode` parameter, and DatabaseConfig hands the parsed URL to Hikari setting only jdbcUrl, username, password, driverClassName and the pool sizing — no SSL properties at all. The Postgres wire protocol, including credentials on connect, is plaintext over the compose bridge. For a single-host deployment where the bridge is not routable this is low severity, but any container the owner later attaches to that network, or anything that can sniff the bridge interface on the host, sees query traffic and credentials. Worth noting for remediation: `parseDatabaseUrl` preserves the URL's query string when building the JDBC URL (DatabaseConfig.kt:36), so an operator CAN turn this on by appending `?sslmode=require` to DATABASE_URL — it is not blocked, just not on by default and not documented in .env.docker.
- **No egress restriction at the container/network layer** — The compose file defines no `networks:` section and no `internal: true`, so every container has unrestricted outbound access to the LAN and the internet. The SSRF defense is entirely application-level — the `validateOutboundUrl`/`isBlockedIpLiteral` guard in domain/Validations.kt wired into webhook creation and push subscription, plus `followRedirects = false` in WebhookDispatchService. That guard is the only thing between a user-supplied webhook URL and the host's other services; there is no second layer (no egress firewall, no dedicated internal network for the database) if the guard has a bypass. Noted alongside the app-layer control, not in place of it — the app-layer guard itself was not re-verified here, as it falls outside this container/runtime domain.
- **No log driver limits** — No `logging:` block on any service, so all four use Docker's default json-file driver with no max-size or max-file. Container logs grow without bound on the host disk. A sustained flood of failed logins or 4xx responses — which the backend logs via configureCallLogging() — could fill the host filesystem, which on a shared self-hosted box takes down unrelated services and can also break Postgres writes. `logging: {driver: json-file, options: {max-size: 10m, max-file: 3}}` is the standard one-line fix. Compounding factor: the new RetentionScheduler ages out database-resident audit tables but has no effect on Docker's on-disk container logs.
- **Deploy tooling relies on SSH password auth** — scripts/redeploy-remote-backend.sh accepts an SSH password via `--password`, `-p`, `--password=<pw>` or the SSH_PASSWORD env var and routes it through sshpass. It uses `export SSHPASS="$SSH_PASSWORD"` with `sshpass -e`, which is better than putting the password on the sshpass command line but still places it in the environment of the ssh/rsync child processes. There is no key-only path enforced, no host-key pinning and no `StrictHostKeyChecking` or any other `ssh -o` option anywhere in the file, and the default target is a hard-coded LAN address (`ohmz@192.168.40.69`). A password captured from shell history or an environment dump grants shell on the deploy host, which is full compromise of the stack — all the container hardening above is downstream of that.
- **No rollback path on a bad deploy** — The redeploy script rebuilds in place with `docker compose up -d --build tday-backend`; the previous image is replaced by a new `tday-backend:latest` and no version-tagged local image is kept, so there is no `docker compose down && up` to a known-good tag. The post-deploy curl is advisory (`|| true`). Recovery from a bad deploy means checking out an older commit on the host and rebuilding — minutes of downtime, dependent on the source tree still being sound, and (per the correction above) the script does not itself move backend source to the host. Note also that Flyway runs at boot with `baselineOnMigrate(true)` / `baselineVersion("2")` and `validateOnMigrate(false)`, so a rollback of code does not roll back schema, and a checksum mismatch on an edited-but-already-applied migration will not stop startup.

---

## Logging, observability & privacy

### Request access log (minimal by construction)

`On by default`

Ktor CallLogging is installed at Level.INFO with a hand-written `format {}` block emitting exactly four fields: HTTP method, the sanitized route template, the numeric status, and the response Content-Type — `"$method $route -> $status ($contentType)"`. Nothing else: no client IP, no User-Agent, no Referer, no request/response body, no Cookie/Authorization/X-CSRF-Token, no query string, no duration. The default Ktor format is never used, so there is no header/body content in the line to redact in the first place. Output goes to stdout via the logback ConsoleAppender pattern `%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n` at logback.xml:2-6. Root level is INFO; Exposed, HikariCP and io.ktor are each pinned to INFO (logback.xml:14-16) so no SQL statement logging.

> backend:plugins/CallLogging.kt:11-21 (format block 14-20); tday-backend/src/main/resources/logback.xml:2-6,10-16

### Query strings are dropped wholesale before logging

`On by default`

`sanitizePath` starts with `raw.substringBefore('?').substringBefore('#')`, so everything after `?` is discarded before any segment logic runs. This is stronger than the 9-entry `sensitiveQueryKeys` denylist that sits next to it: no query parameter — known key or not — can reach stdout, a Sentry breadcrumb, a Sentry transaction name, or the eventLog table. It also strips scheme+host when handed an absolute URL (lines 74-80), so a full URL collapses to its path. Verified by test: `sanitizePath("/api/list/list-123?token=secret")` == `/api/list/:id`, and `sanitizePath("https://example.com")` == `/`.

> backend:observability/TdayObservability.kt:72-81, sensitiveQueryKeys at :50-60; test at backend-test:observability/TdayObservabilityTest.kt:9-17,37

### Path-segment sanitization (sanitizePath / sanitizeSegment)

`On by default`

Every path is split on `/` and each segment passed through `sanitizeSegment`, which URL-decodes then classifies in a fixed order: blank -> `:value`; already-templated `:name` -> kept; member of the **39-entry** `staticSegments` allowlist (api, auth, todo, admin, health, .well-known, apple-app-site-association, …) -> kept verbatim; matches `[a-z]{2}(-[A-Z]{2})?` -> `:locale`; **length > 24 -> `:id`** (:150); **contains any digit -> `:id`** (:151); contains `@` -> `:redacted`; contains `=` -> `:redacted`; contains `:`/`_`/`-` -> `:id`; lowercased member of `sensitiveQueryKeys` -> `:redacted`; anything else -> `:value`. The length and digit rules carry the weight — deliberately blunt so an unrecognised opaque value is redacted by default rather than enumerated. A free-text segment under 25 chars with no digit or punctuation still becomes `:value`, so list names and titles in SPA URLs do not leak either (test: `/en/app/list/abc-123/Groceries` -> `/:locale/app/list/:id/:value`). Honest nuance: because the length and digit rules are checked *before* `@`, an email containing a digit is labelled `:id` rather than `:redacted` — different label, still non-revealing.

> backend:observability/TdayObservability.kt:83-89 and 141-160; staticSegments at :8-48 (39 entries); sensitiveQueryKeys at :50-60; tests at backend-test:observability/TdayObservabilityTest.kt:19-38

### Calendar feed tokens kept out of every log sink

`On by default`

The iCalendar feed is the one credential this app carries in a URL path: `GET /calendar/{token}` is mounted outside `/api` and outside all auth, and the token is the sole credential. The token is `<cuid>_<secret>` (CalendarFeedService.kt:96) where the secret is 32 random bytes base64url-encoded — roughly 69 chars containing `_` and digits, so it trips three separate `sanitizeSegment` rules (>24, digit-bearing, contains `_`). **Correction to a common overclaim: the logged form is `/:value/:id`, not `/calendar/:id`** — `calendar` is NOT in the backend `staticSegments` allowlist (it is only in the web/Android/iOS lists), so the literal segment is itself reduced to `:value`. The security property holds regardless: the token never appears. The request rate-limit event logger sanitizes the same path explicitly before persisting it, with an in-code comment naming this exact case. At rest the token is stored only as `s256$<sha256hex>` (FAST_HASH_PREFIX) plus a 4-character tail preview (PREVIEW_LENGTH=4), never in cleartext.

> backend:routes/CalendarFeedRoutes.kt:12-22; backend:services/CalendarFeedService.kt:31,80,96,234-247; backend:security/RequestRateLimiter.kt:95-97; absence of "calendar" in TdayObservability.kt:8-48

### Structured-data redaction for breadcrumbs and error extras

`On by default`

Two pure functions guard everything attached to a Sentry breadcrumb or exception scope. `safeDataValue(key, value)` returns the literal string "redacted" when the *key* matches `(authorization|cookie|csrf|token|password|session|secret|username|body|payload|header)` case-insensitively; route-shaped keys (route, path, url, href, from, to, endpoint) are run through `sanitizePath`; Numbers and Booleans pass through; everything else goes to `safeLabel`. `safeLabel(value)` returns "redacted" when the *value* matches `(https?://|wss?://|<email>|bearer\s+|token=|password=|session=|cookie=|csrf)`, returns "id" for token-shaped strings (>24 chars, contains a digit, matches `^[A-Za-z0-9_.:-]+$`), and otherwise replaces every character outside `[A-Za-z0-9_.:-]` with `_` and truncates to 64 chars. Redaction is applied on both axes — key name and value shape — and the fallback is lossy normalization, not passthrough. Note the backend key pattern does NOT include `email` (the web/Android/iOS versions do).

> backend:observability/TdayObservability.kt:131-139 (safeDataValue), :91-100 (safeLabel), :62-67 (patterns); tests at backend-test:observability/TdayObservabilityTest.kt:40-59

### Client IP is never stored or logged in cleartext

`On by default`

The IP is resolved (cf-connecting-ip, then the first entry of x-forwarded-for, then x-real-ip, then `request.local.remoteAddress`) only to be fed into `hashSecurityValue`, which HMACs it. It is not in the CallLogging format, not in any of the 11 `eventLogger.log` details maps in the codebase (the rate-limit event carries only `subjectType="ip"` and a count, never the address), and Sentry's `beforeSend` explicitly nulls `event.user.ipAddress` on the backend, Android and iOS, while the web SDK's `scrubSentryEvent` deletes `event.user.ip_address`. `isSendDefaultPii=false`/`sendDefaultPii:false` on all four SDKs stops the SDK attaching it in the first place. **Honest limit — corrected from the claim's "never transmitted":** the address exists in memory for the request, in Cloudflare's own logs (outside the operator's control), and — when a DSN is configured — Sentry's ingest still observes the transport-level source IP of the browser or device that POSTs the envelope. What is guaranteed is that no cleartext IP is in T'Day's stdout, its database, or an event field.

> backend:security/ClientSignals.kt:27-37,49-53; backend:Application.kt:42,45-50; tday-web/src/lib/observability/sentry.ts:162-167; backend:security/RequestRateLimiter.kt:88-99

### HMAC-SHA256 hashing of security identifiers

`On by default`

`ClientSignalsImpl.hashSecurityValue` runs HmacSHA256 keyed on `AUTH_SECRET` over a domain-prefixed input and returns hex. Prefixes in use: `"ip:<addr>"`, `"username:<name>"`, `"device:<x-tday-device-id>"` (truncated to 128 chars), `"user:<cuid>"`, and via `makeSubjectKey` the dimension name itself (`ip:`, `username:`, `device:`, `ipUsername:<ip>|<user>`). Every value written to `AuthThrottle.bucketKey` (varchar 255), `AuthSignal.identifierHash`, `AuthSignal.lastIpHash`, `AuthSignal.lastDeviceHash` is such a digest — a database dump yields correlation, not usernames/IPs/device IDs. The domain prefix stops a hashed IP colliding with a hashed username. The in-memory request rate limiter uses the same function for its bucket keys.

> backend:security/ClientSignals.kt:39-53; backend:security/AuthThrottle.kt:80,88-93 and :250-256; backend:db/tables/AuthSignals.kt:8-10; backend:db/tables/AuthThrottles.kt:9; backend:security/RequestRateLimiter.kt:110-123

### Security event log (eventLog table)

`On by default`

`SecurityEventLoggerImpl.log(reasonCode, details)` builds a JSON object `{reasonCode, at (ISO instant), ...details}`, emits it to a dedicated `security` SLF4J logger at WARN, adds a Sentry breadcrumb carrying only the reasonCode, then inserts it into the `eventLog` table (columns: id cuid varchar(30), capturedTime, eventName=reasonCode, log=serialized JSON truncated to 500 chars). Reason codes actually reachable, verified by enumerating all 11 call sites: `auth_limit_ip`, `auth_limit_username` (from `reasonCodeFor`), `auth_limit_account`, `auth_limit_ip_burst`, `auth_lockout`, `auth_limit` (fallback), `auth_alert_lockout_burst`, `auth_alert_ip_concentration`, `auth_signal_anomaly`, `request_rate_limit_triggered`, `auth_session_absolute_expired`, `auth_session_renewed`, `auth_session_token_version_mismatch`, `auth_session_user_missing`, `auth_credential_envelope_invalid`. The insert is wrapped in try/catch and only logs a warning on failure — a database problem degrades the audit trail but never fails the request.

> backend:security/SecurityEventLogger.kt:21-54 (500-char cap at :42, fail-open at :51-53); backend:db/tables/EventLogs.kt:6-13; call sites: AuthThrottle.kt:96-101,172,190,195-202,225,227,231,271-277, RequestRateLimiter.kt:88-99, plugins/Security.kt:130,157,169,179, routes/auth/CredentialsCallbackRoutes.kt:43

### Generic error responses / no stack traces to clients

`On by default`

The StatusPages `exception<Throwable>` handler is the catch-all: it captures the exception to Sentry with only a sanitized route, logs `logger.error("api_error", cause)` server-side (where the stack trace stays), and responds `500` with the fixed body `ApiError(500, "An unexpected error occurred", null)`. The cause's message and stack are never serialized into the response. `ContentTransformationException` and Ktor's `BadRequestException` both collapse to the constant `INVALID_REQUEST_BODY_MESSAGE = "Invalid request body"` (:16), so a malformed JSON payload cannot echo the parser's message back. Typed `AppError` responses use author-written messages mapped to status codes by `appErrorStatus`. See the gap below: one route (`POST /api/import`) constructs `AppError.Internal(e.message)` itself and bypasses this handler.

> backend:plugins/StatusPages.kt:70-78 (generic 500 at :77), :64-69, :16, :33-40

### 429 rate-limit responses carry a Retry-After and a coarse reason

`On by default`

All rate-limit rejection points call `ApplicationCall.respondRateLimit`, which appends a `Retry-After` header (integer seconds) and returns HTTP 429 with `{message, reason, retryAfterSeconds}`. Recently hardened: these paths previously threw and returned 500. For the **auth throttle** the `reason` is deliberately coarse — `reasonCodeFor` maps the `ip`, `device` and `ipUsername` dimensions all to the single client-facing string `"auth_limit_ip"`, with an in-code comment stating the precise dimension is carried in the security event log rather than the HTTP response. **Correction:** one of the 10 call sites (RateLimiting.kt:29) is the *request* rate limiter, not the auth throttle, and it does surface which policy tripped — `api_rate_limit`, `infra_rate_limit`, `summary_rate_limit`, `change_password_rate_limit`, `websocket_rate_limit` — though still not whether the bucket was per-IP or per-user. Auth call sites: RegisterRoutes.kt:27, SecurityQuestionRoutes.kt:46/77/126/189, CredentialsKeyRoutes.kt:19, CredentialsCallbackRoutes.kt:58, LoginChallengeRoutes.kt:31, CsrfRoutes.kt:19, SessionRoutes.kt:25.

> backend:domain/AuthContext.kt:25-39; backend:security/AuthThrottle.kt:95-101; backend:plugins/RateLimiting.kt:28-35,39-98

### Backend Sentry scrubbing (isSendDefaultPii=false + beforeSend + templated transactions)

`On by default`

When Sentry is enabled, three scrubs apply to every event: `options.isSendDefaultPii = false` (the SDK will not auto-attach user IP, cookies, or request headers); a `setBeforeSend` hook that nulls `event.user.ipAddress`, rewrites `event.request.url` through `TdayObservability.sanitizePath`, and nulls `event.request.queryString`. `SentryRequestPlugin` names transactions with `routeTemplate(method, path)` — e.g. `GET /api/todo/:id` — so no raw path reaches the transaction name, and its per-request breadcrumb carries only the method and the sanitized route. Scope: `beforeSend` does **not** touch `event.message` — see the ERROR-log gap below.

> backend:Application.kt:42,45-50; backend:plugins/SentryPlugin.kt:14-34

### Traces sample rate with a lower production default, clamped

`On by default`

`sentryTracesSampleRate = envDouble("SENTRY_TRACES_SAMPLE_RATE", <0.2 in production, 1.0 otherwise>).coerceIn(0.0, 1.0)` — in production only 20% of requests produce a performance transaction, and the value is clamped so a malformed env var cannot exceed 1.0 or go negative. All three clients mirror this: web `readTraceSampleRate(import.meta.env.VITE_SENTRY_TRACES_SAMPLE_RATE, import.meta.env.PROD ? 0.2 : 1.0)` with `Math.min(1, Math.max(0, …))` and a `Number.isFinite` guard; Android `TdayTelemetry.traceSampleRate(BuildConfig.SENTRY_TRACES_SAMPLE_RATE, if (DEBUG) 1.0 else 0.2).coerceIn(0.0, 1.0)`; iOS `TdayTelemetry.traceSampleRate(..., fallback: environment == "production" ? 0.2 : 1.0)` with `min(1.0, max(0.0, …))`.

> backend:config/AppConfig.kt:168-169; backend:Application.kt:44; tday-web/src/main.tsx:20-23,30 and tday-web/src/lib/observability/sentry.ts:104-112; android-compose/app/src/main/java/com/ohmz/tday/compose/core/observability/TdayTelemetry.kt:66-72; ios-swiftUI/Tday/Core/SentryConfiguration.swift:21-24,56-59

### Web Sentry scrubbing (event, transaction, breadcrumb) + replay disabled

`On by default`

Three hooks are wired: `beforeSend: scrubSentryEvent` deletes `user.ip_address`, `user.email`, `user.username`, rewrites `request.url` through `sanitizeTelemetryUrl`, deletes `request.query_string` and `request.cookies`, deletes any header whose lowercased name is in {authorization, cookie, set-cookie, x-csrf-token}, and re-runs the breadcrumb scrubber over `event.breadcrumbs`; `beforeSendTransaction: scrubSentryTransaction` re-templates the transaction name; `beforeBreadcrumb: scrubSentryBreadcrumb` **drops entirely** any breadcrumb whose category is `console` or starts with `ui.` (clicks, keypresses and console output never leave the browser) and sanitizes the message and data of the rest. Session Replay is hard-disabled: `replaysSessionSampleRate: 0` and `replaysOnErrorSampleRate: 0`. The default Breadcrumbs integration is replaced with `breadcrumbsIntegration({ console: false, dom: false })`, so suppression exists at both integration and hook level. The browser key denylist additionally includes `email`, which the backend's does not.

> tday-web/src/main.tsx:32-46; tday-web/src/lib/observability/sentry.ts:162-217 (header set at :62-67, console/ui drop at :205-207), :72-73

### Shared sanitization contract across backend, web, Android and iOS

`On by default`

The same sanitizer is implemented four times — Kotlin (backend), TypeScript (SPA), Kotlin (Android), Swift (iOS) — so all ends of a trace template identically: same `sanitizeSegment` classification, same `>24 chars + digit -> "id"` label rule, same 64-char truncation, same sensitive-label regex. Divergences worth knowing: (a) the backend `staticSegments` is 39 entries and omits SPA-only route words including `calendar`, `today`, `priority`, `blogs`, which the web (49), Android (43) and iOS (43) lists include — so the same URL can template to `/:value/:id` server-side and `/calendar/:id` client-side; (b) the web/Android/iOS check `@`/`=` *before* the length rule while the backend checks length first — both end in a redacted token; (c) the backend and Android/iOS `routeLikeDataKeys` include `endpoint`, the web's list at sentry.ts:257 does not; (d) the backend's sensitive-key regex omits `email`, which all three clients include.

> tday-web/src/lib/observability/sentry.ts:11-60,219-249,251-266; backend:observability/TdayObservability.kt:8-48,62-67,141-160; android-compose/app/src/main/java/com/ohmz/tday/compose/core/observability/TdayTelemetry.kt:8-64,133-148; ios-swiftUI/Tday/Core/SentryConfiguration.swift:35-47,144-160

### Retention safety rule: live lockouts are never purged

`On by default`

The `AuthThrottle` purge predicate is `(updatedAt < cutoff) AND (lockUntil IS NULL OR lockUntil < now)` — a row whose `lockUntil` is still in the future is preserved regardless of age. Without this the retention job would itself be a lockout-reset primitive: an attacker who tripped a long backoff could wait out the retention window and have the penalty deleted. Enforced in the SQL predicate, not in application logic, so it holds for every delete. `now` is captured once at the top of the tick, so the comparison is stable across the four purges.

> backend:services/RetentionScheduler.kt:74,80-86 (predicate at :83-85), rationale at :37-41

### Retention safety rule: cronLog floor of 7 days

`On by default`

`retentionCronLogDays = envInt("RETENTION_CRONLOG_DAYS", 90).coerceAtLeast(7)` — the operator cannot configure a cronLog retention shorter than 7 days and cannot disable it with 0 (the coerce raises 0 to 7). This exists because `ReminderPushScheduler` derives its scan window from the most recent successful `CronLog` row matching its job label; if that bookmark is deleted the scheduler falls back to a single-tick lookback and silently drops reminders due in the gap. This is an availability guard rather than a confidentiality one, and it is why cronLog retention behaves differently from the other three tables.

> backend:config/AppConfig.kt:156-161 (coerceAtLeast at :161); consumer at backend:services/ReminderPushScheduler.kt:35-36,102-107; rationale at RetentionScheduler.kt:41-43

### Stale admin-reset-request expiry (7 days)

`On by default`

Each retention tick also clears `User.pendingAdminReset` (and nulls `adminResetRequestedAt`) for any row where the flag is true and the request is older than `ADMIN_RESET_REQUEST_TTL_DAYS = 7L` (defined in AdminService.kt:25). This matters for privacy because `pendingAdminReset` can be raised by anyone who knows a username, without signing in — attacker-controllable state attached to a real account. Unlike the table purges, this branch **does** run under dry-run in read-only form: it executes a `count()` instead of the `update` and reports `staleAdminResets=<n>` so the operator can see what would happen. Recently hardened alongside `AdminService.clearResetRequest` / `POST /api/admin/users/{id}/clear-reset-request`.

> backend:services/RetentionScheduler.kt:94-113; constant at backend:services/AdminService.kt:25-31; columns at backend:db/tables/Users.kt:26-27

### Boot warnings when security-relevant env vars are unset in production

`On by default`

`logStartupSecurityWarnings` runs during module init and, only when `config.isProduction`, emits WARNs for **four** conditions (the first pass said two siblings; there are three): `FieldEncryption.isConfigured()` false -> "DATA_ENCRYPTION_KEY/DATA_ENCRYPTION_KEYS is unset in production; sensitive fields are being stored as PLAINTEXT in Postgres"; `credentialsPrivateKeyPem` blank -> credential envelope encryption will use an ephemeral key; `appleTeamId` blank -> iOS webcredentials association incomplete; `androidSha256CertFingerprints` empty -> Android web credential sharing incomplete. The in-code comment states the reason plainly: because encryption is fail-open, the operator otherwise cannot distinguish an encrypted column from an unencrypted one. These are WARN-level, so they go to stdout and (if a DSN is set) become Sentry breadcrumbs, but not Sentry events. Recently added.

> backend:Application.kt:88-89,122-146

### CSP pins telemetry egress to the declared Sentry ingest origin

`On by default`

The browser Sentry SDK POSTs envelopes directly to the ingest host, so it must be allowed in `connect-src`. Rather than a wildcard, `parseSentryIngestOrigin(config.sentryDsn)` parses the DSN URI and derives exactly `scheme://host[:port]`, rejecting any non-http/https scheme and returning null on a parse failure; that single origin becomes the default `CSP_CONNECT_EXTRA`. The resulting header restricts `connect-src` to `'self' ws: wss: https://raw.githubusercontent.com https://api.github.com <sentry-origin>` — so if the SPA were induced to exfiltrate over fetch/XHR the browser blocks any other destination. With no DSN configured, no third-party origin is added at all. Full policy also sets `default-src 'self'`, `base-uri 'self'`, `object-src 'none'`, `frame-src 'none'`, `frame-ancestors 'none'`, `form-action 'self'`, `script-src 'self'` (no unsafe-inline), `style-src 'self' 'unsafe-inline'` (forced by Radix/sonner/vaul runtime style injection), `img-src 'self' data:`, and worker/manifest/font/media `'self'`. `CSP_MODE` (enforce | report-only | off, anything unrecognised falling back to enforce) is the escape hatch; default is enforce. Recently added.

> backend:plugins/SecurityHeaders.kt:12-17,26-38,57-84 (connect-src at :58-66,82), :89-98,107-109

### Referrer-Policy limits URL leakage to third parties

`On by default`

`Referrer-Policy: strict-origin-when-cross-origin` is set on every response by the DefaultHeaders plugin. Cross-origin navigations and subresource loads therefore send only the origin, never the path — which matters here because SPA paths embed list and todo IDs, and because the calendar feed URL contains a live credential. Shipped alongside `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Permissions-Policy: camera=(), microphone=(), geolocation=(), payment=(), usb=()` (new), and HSTS `max-age=63072000; includeSubDomains; preload` gated on `config.isProduction` only.

> backend:plugins/SecurityHeaders.kt:100-113 (Referrer-Policy at :103, Permissions-Policy at :106, HSTS at :110-112)

### Credential material in the database is hash-only

`On by default`

No reversible credential is stored: `User.password` is a PBKDF2WithHmacSHA256 digest; `user_security_questions.answer_hash` holds only a hash of the recovery answer; `user_api_keys.key_hash` plus a `key_preview` column (varchar 20); `calendar_feed_tokens.token_hash` (`s256$<sha256hex>`) plus a `token_preview` column (varchar 20, populated with the last 4 chars, PREVIEW_LENGTH=4). Feed tokens and API keys are shown to the user exactly once at creation and cannot be recovered from the database. The only reversible secret in the schema that is protected is `webhook_subscriptions.secret` — now in the field-encryption set, so AES-GCM-wrapped when a key is configured and plaintext when not. See the gap below for the reversible columns that are NOT covered (`Account.access_token`/`refresh_token`/`id_token`, push subscription keys).

> backend:db/tables/UserSecurityQuestions.kt:10; backend:db/tables/UserApiKeys.kt:9-10; backend:db/tables/CalendarFeedTokens.kt:9-10; backend:services/CalendarFeedService.kt:80,239-247; backend:security/FieldEncryption.kt:25

### Health endpoint discloses nothing

`On by default`

`GET /health` is unauthenticated (required for the Docker healthcheck at docker-compose.yaml:90-95) and responds with exactly `{"status":"ok"}` — no version string, no database state, no dependency list, no hostname, no uptime. It is also covered by the `infra` request rate-limit policy (RateLimiting.kt:55-64), so it cannot be used as an unmetered liveness oracle.

> backend:plugins/Routing.kt:28-30; backend:plugins/RateLimiting.kt:55-64; docker-compose.yaml:90-95

### Backend Sentry SDK — inert unless a DSN is supplied

`Needs config`

`Sentry.init` runs unconditionally at process start, but `options.dsn = config.sentryDsn.orEmpty()` and `SENTRY_DSN` is read with `env("SENTRY_DSN")`, which returns null when unset **or blank**. An empty DSN leaves the SDK inert. `docker-compose.yaml` does not define `SENTRY_DSN`; the backend's env comes from `env_file: .env.docker`, and the shipped template `.env.example:55` sets `SENTRY_DSN=` (empty). So out of the box nothing leaves the host. To disable after enabling: blank or unset `SENTRY_DSN` and restart the container. Other fixed options: `environment` = "production"/"development", `release` = `tday-backend@<version>`, `serverName` = "tday-backend" (a constant, not the real hostname).

> backend:Application.kt:38-44; backend:config/AppConfig.kt:167 and :176-181 (env helper returns null on blank); docker-compose.yaml:78-87 (env_file, no SENTRY_DSN); .env.example:55 (empty)

### Web Sentry SDK — off unless a build-time DSN is supplied

`Needs config`

`Sentry.init({ dsn: import.meta.env.VITE_SENTRY_DSN ?? "" })`. Because this is a Vite build-time variable the DSN is baked into the SPA bundle at image build; `docker-compose.yaml:64-67` passes `VITE_SENTRY_DSN: ${VITE_SENTRY_DSN:-}` and `VITE_SENTRY_TRACES_SAMPLE_RATE` as build args (recently added), and `Dockerfile.backend:10-13` forwards them into the frontend stage. `.env.example:57` ships it empty, so the built bundle carries an empty DSN and the browser sends nothing. Disabling after the fact requires rebuilding the image without the arg — unlike the backend, it is not a restart-time toggle. Also set: `sendDefaultPii: false`, `release: tday-web@<version>`, `tracePropagationTargets: [/^\/api(\/|$)/]` so trace headers attach only to same-origin API calls and never to third-party requests.

> tday-web/src/main.tsx:25-31; docker-compose.yaml:64-67; Dockerfile.backend:8-13; .env.example:57

### Android client telemetry — off unless a build-time DSN, with the same sanitization contract

`Needs config`

Missed by the first pass. The Android app bundles Sentry 8.13.0. Auto-init is explicitly disabled in the manifest (`io.sentry.auto-init` = false) and `SentryAndroid.init` runs only from `runDeferredStartup()` on a background coroutine, with `options.dsn = BuildConfig.SENTRY_DSN`. That BuildConfig field is `localProps.getProperty("sentryDsn") ?: System.getenv("SENTRY_DSN") ?: ""` at build time — empty in a stock clone, so a self-built APK reports nothing. Set: `isSendDefaultPii = false`, `release = tday-android@<versionName>`, `dist = versionCode`, and a `setBeforeSend` that nulls `event.user.ipAddress`. `TdayTelemetry.kt` is a near-line-for-line port of the backend `TdayObservability` (same `safeLabel`, `safeDataValue`, `sanitizePath`, `sanitizeSegment` ordering, 43-entry staticSegments), and its key pattern includes `email`.

> android-compose/app/src/main/AndroidManifest.xml:26; android-compose/app/src/main/java/com/ohmz/tday/compose/TdayApplication.kt:47-67; android-compose/app/build.gradle.kts:74-80,201-202; android-compose/app/src/main/java/com/ohmz/tday/compose/core/observability/TdayTelemetry.kt:55-64,74-148

### iOS client telemetry — hard-gated on a non-empty DSN, same sanitization contract

`Needs config`

Also missed by the first pass. `SentryConfiguration.start()` reads `SENTRY_DSN` from the app bundle's Info.plist and `guard !dsn.isEmpty else { return }` — the SDK is never started at all when unset, and `TdayTelemetry.bundleString` additionally treats an unsubstituted `$(` build-setting placeholder as empty, so a misconfigured build fails closed rather than sending. `project.yml:46` ships `SENTRY_DSN: ""` and `SENTRY_TRACES_SAMPLE_RATE: "0.2"`. When enabled: `sendDefaultPii = false`, `releaseName = tday-ios@<CFBundleShortVersionString>`, `dist = CFBundleVersion`, `beforeSend` nulls `event.user.ipAddress`. Both `addBreadcrumb` and `capture` additionally short-circuit on `SentrySDK.isEnabled`. `TdayTelemetry` mirrors the backend rules (safeLabel, safeDataValue with the same key regex plus `email`, sanitizePath/sanitizeSegment with identical ordering).

> ios-swiftUI/Tday/Core/SentryConfiguration.swift:5-31,35-59,92-106,127-160; ios-swiftUI/Tday/Info.plist:64-67; ios-swiftUI/project.yml:46-47

### Retention scheduler for security bookkeeping tables

`Needs config`

`RetentionScheduler` runs as an application coroutine launched at boot, looping while the context is active with `delay(TICK_INTERVAL)` where `TICK_INTERVAL = Duration.ofHours(6)`. Each tick purges four tables by timestamp column: `eventLog` on `capturedTime` (RETENTION_EVENTLOG_DAYS, default 90), `AuthThrottle` on `updatedAt` (RETENTION_AUTHTHROTTLE_DAYS, default 30), `AuthSignal` on `lastSeenAt` (RETENTION_AUTHSIGNAL_DAYS, default 180), `CronLog` on `runAt` (RETENTION_CRONLOG_DAYS, default 90, floored at 7). Setting a value to 0 disables that table's purge (`if (retentionDays <= 0) return`) — except cronLog, where the floor raises 0 to 7. **It ships inert: `RETENTION_DRY_RUN` defaults to "true"**; each table then only logs `[retention:dry-run] <label> would purge rows older than <cutoff>` and records `<label>=dry-run` without counting rows, by design. Nothing is deleted until the operator sets `RETENTION_DRY_RUN=false`. A summary row is written to `CronLog` with the `retention` job label after any non-empty tick (which under dry-run means every tick), and a failed tick writes `retention error=<message>` with success=false.

> backend:services/RetentionScheduler.kt:50-71,73-118,120-148,150-165 (TICK_INTERVAL at :162); defaults at backend:config/AppConfig.kt:158-164; wired at Application.kt:94,114-120

### Field-level encryption at rest (AES-256-GCM) — fail-open, key beside the data

`Needs config`

`FieldEncryptionImpl` encrypts a fixed set of four logical field names — `description`, `content`, `overriddenDescription`, and (recently added) `webhookSecret` — with AES/GCM/NoPadding: 32-byte key, 12-byte random IV per value from `SecureRandom`, 128-bit auth tag, optional AAD from `DATA_ENCRYPTION_AAD`, serialized as `enc:v1:<keyId>:<ivB64Url>:<ctB64Url>`. A keyring (`DATA_ENCRYPTION_KEYS` as `kid:key,kid:key`, plus `DATA_ENCRYPTION_KEY`) supports rotation: old values decrypt under their embedded keyId while new writes use the active key. Keys must be 32-byte base64 or 64-char hex or `parseKeyMaterial` throws. **Two honest limits:** (1) it is fail-open — `encryptIfSensitive` returns the plaintext unchanged when `isConfigured()` is false, so with no env var the affected columns are plain text and nothing in the row marks them as such; (2) **the key is not stored away from the data** — it is an env var in the same `.env.docker` that Compose feeds the backend, on the same host as the Postgres volume, so it protects against a stolen `pg_dump` or a detached volume, not against host compromise. `.env.example:171` ships `DATA_ENCRYPTION_KEY=` empty, so a fresh install stores plaintext. Applied at TodoService.kt:76,156,451, FloaterService.kt:69,118, CompletedFloaterService.kt:71, ExportService.kt:206,226,257,278,298, WebhookService.kt:121.

> backend:security/FieldEncryption.kt:21-26 (sensitiveFields at :25), :35-54, :85-88 (fail-open at :86), :96-139; docker-compose.yaml:78-79 (env_file); .env.example:170-177

### Optional local LLM keeps task text off the internet

`Needs config`

The AI summary feature posts a prompt built from the user's task text to `${OLLAMA_URL}/api/chat`. The whole feature is inert unless `OLLAMA_URL` is set — `ollamaUrl = env("OLLAMA_URL", "")` and `.env.example:70` ships it empty; `generateSummary` returns null immediately on a blank URL (:43), as do `isHealthy` (:84), `warmUp` (:95) and `isConfigured` (:101). When configured, the destination is whatever host the operator names (the compose file's `ai`-profile Ollama service, i.e. task text stays inside the Docker network). Failures log only `Ollama request failed: <message>` or `Ollama returned <status>` — the prompt itself is never logged. **Correction:** the first pass claimed the outbound URL is subject to `domain/Validations.kt`; it is not. `validateOutboundUrl` is wired only into `PushNotificationService.subscribe` (:112) and `WebhookService.create` (:105). `OLLAMA_URL` is operator-supplied config and receives no SSRF egress check.

> backend:services/TodoSummaryService.kt:42-43,59,65,78,83-84,94-95,101; backend:config/AppConfig.kt:105; .env.example:70; validateOutboundUrl call sites: PushNotificationService.kt:112, WebhookService.kt:105 only

### Security-event details are sanitized per call site (no central scrub)

`Partial`

Details maps are curated per call site rather than dumped: the rate-limit event carries `policy`, `reason`, `subjectType`, `retryAfterSeconds` and a `TdayObservability.sanitizePath(...)`'d path; throttle events carry `action`, `retryAfterSeconds`, `dimension`; the anomaly event carries the HMAC `identifierHash`, not the username; session events carry `path` via `securityEventPath(call)` which is also `sanitizePath`. Two honest gaps: (1) the four session events in plugins/Security.kt put the **raw user cuid** in the `userId` detail — a stable pseudonymous identifier stored in cleartext in `eventLog` that joins directly against `User.id` and thence `User.username`; (2) `auth_credential_envelope_invalid` writes `"error" to e.message` — a **raw exception message** from the envelope-decryption path into the persisted event JSON, unfiltered. `SecurityEventLoggerImpl` does not run the details map through `safeDataValue`; sanitization is a per-call-site discipline, so a future call site can regress it silently.

> backend:plugins/Security.kt:40,130-137,157-165,169-176,179-186 and :297-298 (securityEventPath); backend:routes/auth/CredentialsCallbackRoutes.kt:43; backend:security/RequestRateLimiter.kt:88-99; backend:security/SecurityEventLogger.kt:25-32 (no central scrub)

### Not present — logging, observability & privacy

- **No retention or rotation for container stdout logs** — The retention scheduler covers exactly four database tables. It does nothing about the log stream itself, and `docker-compose.yaml` declares no `logging:` driver or `max-size`/`max-file` options for any service — so Docker's default json-file driver keeps every access-log line, every `[security] {...}` WARN payload, and every stack trace indefinitely under `/var/lib/docker/containers/...`. In practice that file is the largest and longest-lived copy of the telemetry, and it is the one with no TTL. Consequence for a single self-hoster: the disk-exhaustion problem the retention scheduler was written to solve is only half-solved, and a security event purged from `eventLog` after 90 days is still readable in the container log.
- **Retention ships disabled (RETENTION_DRY_RUN=true)** — With default configuration nothing is ever deleted — every tick logs `<table>=dry-run` and returns. The comment at AppConfig.kt:162-163 says this is deliberate for one release so the operator can read a cycle's CronLog output first, but the effect today is that a fresh deployment has the same unbounded-growth behaviour it had before the scheduler existed. The operator must explicitly set `RETENTION_DRY_RUN=false`, and neither `docker-compose.yaml` nor the shipped `.env.example` surfaces any `RETENTION_*` variable to prompt them.
- **ERROR-level log messages reach Sentry without passing through TdayObservability** — `logback.xml` installs `io.sentry.logback.SentryAppender` on the root logger with `<minimumEventLevel>ERROR</minimumEventLevel>` and no `minimumBreadcrumbLevel`, so the SDK default (INFO) applies. Two consequences: (a) every ERROR log line becomes a Sentry event carrying the formatted message verbatim, and the backend `beforeSend` only touches `user.ipAddress`, `request.url` and `request.queryString` — it never inspects `event.message`; (b) INFO/WARN lines are attached as breadcrumbs to those events, which includes the full `logger.warn("[security] {}", payload)` security-event JSON (userId cuid and all) and `logger.warn("Failed to send push to {}: {}", target.endpoint.take(60), ...)` — the first 60 characters of a push-service endpoint URL. Nothing on this path goes through `safeLabel`/`safeDataValue`. This only matters when `SENTRY_DSN` is configured, but when it is, it is the widest unscrubbed channel in the system.
- **Android HTTP breadcrumbs carry raw API URLs** — Newly found. The Android client installs `SentryOkHttpInterceptor()` on its OkHttp stack, which auto-generates an `http` breadcrumb and span for every API call with the full request URL. The Android Sentry init sets only `setBeforeSend { event.user?.ipAddress = null }` — there is no `beforeBreadcrumb`, and no equivalent of the web SDK's `scrubSentryBreadcrumb`/`sanitizeTelemetryUrl` on that automatic path. So when an Android build is compiled with a DSN, breadcrumbs carry raw paths such as `/api/todo/<cuid>` and `/api/list/<cuid>` rather than the `:id` templates that `TdayTelemetry.sanitizePath` produces for hand-written breadcrumbs. `TdayTelemetry` is only applied where app code calls it explicitly.
- **No audit trail for data access or mutation** — `eventLog` records authentication, session and rate-limit events only — all 15 reason codes are auth- or throttle-related, and all 11 `eventLogger.log` call sites live in AuthThrottle.kt, RequestRateLimiter.kt, plugins/Security.kt and CredentialsCallbackRoutes.kt. There is no record anywhere of who read, created, edited, deleted, exported or imported a task, list, file or share. `GET /api/export` streams a user's entire dataset and leaves no trace beyond a `GET /api/export -> 200` line in stdout; `AdminService.purgeUser` deletes another user's data with no persisted record. For a single-user self-host this is mostly acceptable, but if the instance has approved additional users there is no way to reconstruct what an account did.
- **Task titles, list names and step titles are stored in plaintext** — Field encryption covers `description`, `content`, `overriddenDescription` and `webhookSecret` only. It does NOT cover `todos.title`, `floaters.title`, `CompletedTodo.title`, `todo_instances.overriddenTitle`, `task_steps.title`, `Project.name` (lists), `CompletedTodo.projectName`, `File.name`/`File.url`/`File.s3Key`, or `CompletedTodo.steps` (the JSON snapshot of step titles). Since the title is where people actually write what the task is, a Postgres dump or a compromised volume exposes essentially the full content of the task list even with `DATA_ENCRYPTION_KEY` correctly configured. The encrypted `description` field is the exception, not the rule.
- **Account and device identifiers in the database are plaintext** — `User.username` is a plaintext unique-indexed varchar(255), as are `User.name`, `User.image` (a URL) and `User.timeZone` (a coarse location signal). `push_subscriptions` stores the raw `endpoint` (a per-device URL at fcm.googleapis.com or a UnifiedPush distributor), plus `p256dh` and `auth` — together a durable device identifier and the keys needed to push to it. `webhook_subscriptions.url` is plaintext (only its `secret` is encrypted). `Account` carries nullable `access_token`, `refresh_token`, `id_token` and `session_state` as plaintext text — unused by the credentials flow but present in the schema. None of these is covered by field encryption, so a database dump reveals who the users are and what devices they carry.
- **eventLog stores the raw user cuid for session events** — Four reason codes (`auth_session_absolute_expired`, `auth_session_renewed`, `auth_session_token_version_mismatch`, `auth_session_user_missing`) write `"userId" -> claims.id` into the persisted JSON. Every other identifier in the security tables is HMAC-hashed, so this is the one place pseudonymity breaks: the value joins directly against `User.id`, which joins to `User.username`. The consequence is that `eventLog` is a per-account activity trail rather than an anonymised one — arguably desirable for an admin, but it is not what "identifiers are hashed" implies, and `RETENTION_EVENTLOG_DAYS=90` is the only thing bounding it (and only once dry-run is turned off).
- **A raw exception message is written into eventLog on the credential-envelope path** — Newly found alongside the userId issue. `eventLogger.log("auth_credential_envelope_invalid", mapOf("error" to e.message))` persists the unfiltered `message` of whatever exception the envelope-decryption threw — a crypto/JOSE library string — into the `eventLog.log` column (truncated at 500 chars) and into the `[security]` WARN line on stdout. This is an unauthenticated-reachable path, since it runs before the throttle check on the credentials callback. It is not a credential leak (the decrypted username/password are not in the message), but it is the one detail value in the whole event logger that is not a curated constant, hash, or sanitized path.
- **One error path echoes a raw exception message to the client** — The StatusPages catch-all is genuinely generic, but it is not the only route to a 500. `ExportService.import` catches `Exception` and returns `AppError.Internal(e.message ?: "import failed", e)`, and `withAuth` -> `respondError` -> `respondAppError` -> `respondApiError` serializes `error.message` straight into the response body. So `POST /api/import` with a payload that provokes a JDBC, JSON or Exposed exception returns that library's message text to the caller — schema/constraint names, driver details. It is authenticated-and-approved-only, so with the pending-approval gate in place the exposure is limited to the owner and anyone they have approved; no stack trace is included, only the message string.
- **HMAC hashing key silently degrades if AUTH_SECRET is short** — `ClientSignalsImpl.hashSecret` requires `config.authSecret.length >= 16`; otherwise it prints `[security] auth_secret_missing using fallback hash key` to stderr via `System.err.println` — not the logger, so it bypasses logback formatting, the Sentry appender, and any log aggregation — and generates a random 32-byte key per process. The result: throttle bucket keys and AuthSignal identifier hashes change on every restart, so lockouts and device-anomaly baselines are silently reset by a container restart, and rows accumulated under the old key become orphans only the retention job will clear. Because the property is `by lazy`, the announcement happens once, at first use, on stderr — easy to miss.
- **Retention 'batching' is not actually batched** — `purge()` loops `while (true) { batch = delete(cutoff); if (batch < BATCH_LIMIT) break }` with `BATCH_LIMIT = 5000`, and its comment says the work is batched so a large backlog never holds one long transaction. But none of the four `deleteWhere` calls passes a row limit, so each is a single unbounded `DELETE ... WHERE ts < cutoff` inside one transaction; the loop merely runs a second (no-op) delete whenever the first removed >= 5000 rows. On the first non-dry-run tick against a large backlog this is one long-running transaction and lock, not a series of small ones. Not a confidentiality issue — but the comment overclaims, and an operator sizing the first real purge should know.
- **No log integrity or tamper-evidence, and no off-host copy** — Newly noted for comparison purposes. `eventLog` is an ordinary Postgres table with no hash chain, no append-only constraint, and no signature; anyone with database access (including the app's own DB user) can UPDATE or DELETE rows, and `AdminService` runs under the same credentials. Stdout goes only to the local Docker json-file. There is no syslog forwarder, no remote log shipper, and no WORM/append-only sink anywhere in the compose file. Practical consequence: the security event log is useful for an honest operator reviewing their own instance, but it is not evidence — a host compromise erases it, and there is no second copy to compare against.

---

## Input validation & injection resistance

### SQL parameterization via Exposed typed DSL

`On by default`

Every user-reachable query is built with JetBrains Exposed 0.57.0's typed DSL (`selectAll().where { Table.col eq value }`, `insert {}`, `update {}`, `deleteWhere {}`), which emits JDBC PreparedStatements with bound parameters. VERIFIED BY GREP over `tday-backend/src/main` + `shared/src` for `exec(`, `prepareStatement`, `createStatement`, `CustomFunction`, `wrapAsExpression`, `stringLiteral`, `QueryBuilder`: exactly two hits, both `exec(` — TodoService.kt:494 and DatabaseConfig.kt:88, catalogued separately below. Zero hits for the other six constructs. Representative DSL sites I opened: TodoService.kt:484-490, ExportService.kt:341-353, WebhookService.kt:78-81, PushNotificationService.kt:118-133, CalendarFeedService.kt:134-136, ListShareService.kt:296-306.

> backend:services/TodoService.kt:484-490; backend:services/ExportService.kt:341-353

### The one raw SQL statement reachable from user input is explicitly parameter-bound

`On by default`

TodoService.deleteInstance issues raw SQL because Exposed has no DSL for Postgres `array_append`. Verbatim: `exec("UPDATE todos SET exdates = array_append(exdates, ?::timestamp) WHERE id = ?", args = listOf(TextColumnType() to Timestamp.valueOf(instanceDate).toString(), TextColumnType() to todoId))` — two `?` placeholders bound through Exposed's typed args list, no interpolation. Both values are type-narrowed before arrival: `instanceDate` is a `java.time.LocalDateTime` (a decoded DTO field, not a free string) and `todoId` is checked against an ownership predicate at TodoService.kt:484-487 (`Todos.id eq todoId and mutableTodos(userId, editableListIds)`), which returns early from the transaction if no row matches.

> backend:services/TodoService.kt:481-500

### Boot-time DDL exec() carries no user input

`On by default`

DatabaseConfig.init() string-interpolates a `DO $$ ... CREATE TYPE ... AS ENUM (...)` block for seven Postgres enum types. Every interpolated value comes from a hardcoded `listOf` literal seven lines above in the same function (UserRole, ApprovalStatus, SortBy, GroupBy, Direction, Priority, ProjectColor plus their member names); the only transform is `name.replace("\"", "'")` on those same constants. No request data, env var, or database read reaches it. It runs once at startup inside `transaction {}` before any route is mounted. Schema is otherwise Flyway (`baselineOnMigrate=true`, `baselineVersion=2`, `validateOnMigrate=false`) plus `SchemaUtils.createMissingTablesAndColumns` over 19 declared tables.

> backend:config/DatabaseConfig.kt:73-96

### LIKE-wildcard stripping and result cap on user search

`On by default`

The only LIKE query fed by request data is user search for list sharing. Before the pattern is built the query is filtered to `isLetterOrDigit() || it in "-._"`, removing `%`, `_`, `\` and every other wildcard/escape char. Concrete parameters (verified at ListShareService.kt:407-408): MIN_SEARCH_LENGTH = 2 (shorter queries return an empty list, not a scan) and SEARCH_RESULT_LIMIT = 10. The pattern `"%${sanitized.lowercase()}%"` is still passed through Exposed's `like`, so it is a bound parameter — the filter defends against wildcard abuse (enumerating all usernames with `%`), not SQL injection, which parameterization already covers. Results are additionally restricted to `approvalStatus eq APPROVED` and exclude the requester.

> backend:services/ListShareService.kt:291-312, :407-408

### Custom column type renders only type-constrained literals

`On by default`

TimestampArrayColumnType.nonNullValueToString builds a quoted SQL literal (`value.joinToString(",", "ARRAY[", "]::timestamp(3)[]") { "'${Timestamp.valueOf(it)}'" }`). It is the one place in the schema layer that produces SQL text by concatenation, but its parameter is typed `List<LocalDateTime>` and each element goes through `java.sql.Timestamp.valueOf`, whose output is a fixed `yyyy-mm-dd hh:mm:ss[.f]` form that cannot contain a quote. The normal write path is `notNullValueToDB` (a bound `Timestamp[]`), and the `exdates` column is only ever written from parsed LocalDateTime values (TodoService.kt:84 writes `emptyList()`; ExportService.kt:216 uses `mapNotNull(::parseDueMinute)`).

> backend:db/tables/TimestampArrayColumnType.kt:28-37

### Typed DTO decoding for every request body

`On by default`

ContentNegotiation installs kotlinx-serialization JSON globally; every `call.receive<T>()` decodes into an `@Serializable` data class, so a body that is not valid JSON or has a wrong-typed/missing required field throws before handler logic. Exact configuration: `prettyPrint = false, isLenient = true, ignoreUnknownKeys = true, encodeDefaults = true`. Be precise: decoding is TYPED but NOT STRICT — `ignoreUnknownKeys = true` silently drops undeclared fields (no mass-assignment risk, since only declared fields are read and owner ids always come from the session, but also no error on junk) and `isLenient = true` accepts relaxed JSON literals. Confirmed by grep: no `SerializersModule` and no `@Polymorphic` registration anywhere in backend or shared sources.

> backend:plugins/Serialization.kt:9-16

### Malformed-body errors become 400, not 500 or a stack trace

`On by default`

StatusPages maps `ContentTransformationException` (line 64) and Ktor's `BadRequestException` (line 67) to a flat 400 carrying the private constant `INVALID_REQUEST_BODY_MESSAGE = "Invalid request body"` (StatusPages.kt:16) — the deserializer's own message, which names field paths and types, is never echoed. The catch-all `exception<Throwable>` (line 70) returns 500 with the fixed literal "An unexpected error occurred", logs server-side, and reports to Sentry with the route run through `TdayObservability.sanitizePath` first.

> backend:plugins/StatusPages.kt:16, :64-79

### Konform declarative validation on todo/floater/list writes

`On by default`

Konform 0.11.1 schemas in domain/Validations.kt are invoked via `validateOrFail` at the route layer before any service call; a failure short-circuits to a 400 carrying the joined hint strings. Concrete rules, all read in source: todo create title `minLength(1)` + `maxLength(500)`; floater create title `minLength(1)` + `maxLength(500)`; list and floater-list create name `minLength(1)` + `maxLength(255)`; todo/list/floater-list patch id `minLength(1)`. Wiring confirmed by grep at TodoRoutes.kt:93 and :137, FloaterRoutes.kt:39, ListRoutes.kt:36 and :48, FloaterListRoutes.kt:42 and :54. TWO LIMITS TO NOTE, both corrections to the first draft: (a) these run at the ROUTE layer only, so `POST /api/import` — which inserts todos, floaters and lists directly — is not covered by any of them; (b) the shared Kotlin mirror `shared/src/commonMain/.../validation/ContractValidators.kt` (MAX_TITLE_LENGTH=500, MAX_NAME_LENGTH=255) has ZERO production call sites — a grep across tday-android, tday-ios, tday-web and shared, excluding build output, returns only its own declaration and its unit test. No client actually pre-checks through it.

> backend:domain/Validations.kt:139-183; backend:routes/TodoRoutes.kt:93, :137; shared/src/commonMain/kotlin/com/ohmz/tday/shared/validation/ContractValidators.kt:13-15

### Enum/allowlist narrowing for every free-string field that maps to a DB enum

`On by default`

Three helpers convert an arbitrary request string into a known-good value or a 400: `validateRequiredEnumValue<T>` and `validateOptionalEnumValue<T>` trim the input and compare against `enumValues<T>()` by exact name; `validateOptionalValue` compares against an explicit `Set<String>`. Every cited call site verified by grep: Priority at TodoRoutes.kt:101, :141, :214, :243, FloaterRoutes.kt:40, :60, :106, CompletedTodoRoutes.kt:47, CompletedFloaterRoutes.kt:52; ListColor at ListRoutes.kt:37, :49 and FloaterListRoutes.kt:43, :55; SortBy/GroupBy/Direction at PreferencesRoutes.kt:31-33. This is what keeps `Priority.valueOf(priority)` at TodoService.kt:77 from throwing on hostile input. The import path uses a different but equally closed mechanism — `Priority.fromApiOrDefault` (shared Enums.kt:60-61, `entries.firstOrNull { it.name == value } ?: default`) and `ListColor.entries.firstOrNull { it.name == s }` at ExportService.kt:469-470 — which silently falls back to a default/null rather than erroring.

> backend:domain/Validations.kt:20-45; routes/TodoRoutes.kt:101; routes/PreferencesRoutes.kt:31-33

### Registration input policy (username regex + password composition)

`On by default`

Enforced inline in RegisterRoutes before the user row is created; every line read. Username: `body.username.trim().lowercase()` then matched against `^[a-z0-9](?:[a-z0-9._-]{1,28}[a-z0-9])$` (RegisterRoutes.kt:15) — 3-30 chars, lowercase alphanumerics plus `. _ -`, must start AND end alphanumeric. First name: `>= 2` chars after trim (line 35). Password: `length >= 8` (line 44), `any { it.isUpperCase() }` (line 48), `any { !it.isLetterOrDigit() }` (line 52). Security questions: `SecurityQuestions.validateSelection(body.securityAnswers, required = 3)` (line 57). Uniqueness via `userService.usernameExists` (line 63). Each rejection is its own 400 with a distinct message. Ordering confirmed: the AuthThrottle check runs FIRST at line 25 and returns a real 429 via `respondRateLimit`, so validation errors cannot be used as a free oracle.

> backend:routes/auth/RegisterRoutes.kt:15, :25-33, :35-63

### SSRF egress guard on user-supplied outbound URLs (recently hardened)

`On by default`

NEW this session. `validateOutboundUrl(raw, field)` rejects, in source order: empty/blank; length > MAX_OUTBOUND_URL_LENGTH (2048); unparseable as `java.net.URI`; any scheme other than `http`/`https` (kills `file:`, `gopher:`, `ftp:`, `javascript:`); any URI carrying `userInfo`; a missing host; a host that `isBlockedIpLiteral` flags; and finally any single-label host with no dot (`database`, `ollama`, `tday-backend`). `isBlockedIpLiteral` strips brackets and the `%zone` suffix, lowercases, and blocks IPv4 `0/8`, `10/8`, `127/8`, `169.254/16` (covering 169.254.169.254), `172.16-31/12`, `192.168/16`, `100.64-127/10` CGNAT, and `a >= 224`. IPv6: `::`, `::1`, `fe8`/`fe9`/`fea`/`feb` (fe80::/10), `fc`/`fd` (ULA), `ff` (multicast), and IPv4-mapped forms recurse into the IPv4 rules. Bare `localhost` and `*.localhost` are blocked. Deliberate design note in-source: hostnames are NOT resolved at validation time (DNS rebinding), with redirect suppression as the stated other half. Wired into exactly two places: WebhookService.create (line 105, before the insert) and PushNotificationService.subscribe (line 112, before the transaction). TWO CORRECTIONS to the first draft: (1) the test file has 11 `@Test` methods, not 12; (2) the claim that malformed IPv4 is "treated as blocked" is only half true — the octet-range check fires only when the host splits into EXACTLY four all-digit parts, so `999.1.1.1` is blocked (asserted in the test) but abbreviated or short dotted forms such as `127.1` or `10.1` never enter the IPv4 branch at all and pass on the "contains a dot" rule. Whether such a host then reaches a private address depends on the resolver's inet_aton-style handling, which this guard does not decide. Public IP literals are deliberately still allowed, and there is no port restriction.

> backend:domain/Validations.kt:48-137; services/WebhookService.kt:105-108; services/PushNotificationService.kt:111-114; src/test/kotlin/com/ohmz/tday/domain/OutboundUrlValidationTest.kt:20-110

### Redirect suppression and timeout on webhook delivery (recently hardened)

`On by default`

NEW this session. The webhook dispatch HttpClient is constructed with `followRedirects = false` (Ktor CIO, WebhookDispatchService.kt:59), so a destination that passed validation at registration cannot later 3xx the request onto a private address — the stated complement to not resolving DNS at validation time. Same client sets `requestTimeout = REQUEST_TIMEOUT_MS = 10_000` ms. Delivery is fire-and-forget on a detached SupervisorJob scope, retries `MAX_ATTEMPTS = 3` with `BASE_BACKOFF_MS = 1_000`, and auto-disables a subscription at `MAX_CONSECUTIVE_FAILURES = 15`. Payload is ID-only — `WebhookPayload(event, userId, listId, timestamp)` at line 82, no task content — and is signed `X-Tday-Signature: sha256=<hmac>` with the per-subscription secret (line 110).

> backend:services/WebhookDispatchService.kt:55-61, :78-83, :106-119, :163-166

### Webhook event-type allowlist

`On by default`

`WebhookService.create` filters the caller's event list with `events.map { it.trim() }.filter { it in WEBHOOK_EVENT_TYPES }.distinct()` (line 110) against the fixed six-entry list at lines 27-33 (todo.changed, floater.changed, list.changed, floaterList.changed, list.members, completed.changed); anything unrecognised is silently dropped. An empty result is stored as NULL, meaning "all events" (line 122). Because only these six strings can ever be persisted, the stored filter cannot carry attacker text into the dispatch comparison at WebhookDispatchService.kt:94-95.

> backend:services/WebhookService.kt:27-33, :110, :122

### No command execution surface

`On by default`

Absence confirmed by grep, not assumed: `grep -rn --include='*.kt' -e 'ProcessBuilder' -e 'Runtime.getRuntime' -e 'Class.forName' -e 'ScriptEngine' tday-backend/src shared/src` returns zero matches (exit 1). The backend never shells out, so there is no OS-command-injection surface regardless of input. The container entrypoint is a direct `ENTRYPOINT ["java", "-jar", "app.jar"]` in exec form with no shell wrapper.

> grep over tday-backend/src and shared/src returned no output (exit 1); Dockerfile.backend:45

### No unsafe deserialization surface

`On by default`

Absence confirmed by grep: zero matches for `ObjectInputStream`, `readObject`, `XMLDecoder`, `serializersModule` and `polymorphic` across `tday-backend/src` and `shared/src` — no Java native deserialization, no runtime-registered polymorphic type resolution. The one polymorphic hierarchy, `DomainEvent`, is a Kotlin `sealed class` with six `@SerialName`-tagged data-class subclasses, which kotlinx-serialization resolves through a closed compile-time discriminator map: an unknown `type` value is a decode error, not a class lookup. It is outbound-only, encoded to WebSocket frames at Routing.kt:80-82 and never decoded from a client. Import — the one endpoint ingesting a large attacker-shaped document — decodes into the flat `ImportRequest`/`TdayExport` structure with no type discriminator at all.

> backend:domain/DomainEvent.kt:13-38; plugins/Routing.kt:80-82; grep for ObjectInputStream/polymorphic/serializersModule returned no output

### Import bundle: version gate, id remapping, and reference nulling

`On by default`

`POST /api/import` is the largest attacker-controlled document the server accepts. Three structural defenses, all read: (1) `request.export.schemaVersion > TdayExport.CURRENT_SCHEMA_VERSION` returns a 400 before anything is read (ExportService.kt:103-107). (2) `ExportRemap.remapCollisions` — pure, DB-free, unit-testable — mints a fresh cuid for any primary key that collides or repeats within the bundle and rewrites every reference via an `idMap` with `putIfAbsent` first-occurrence-wins semantics; the retry loop is proven terminating (`idExists` is a finite set, fresh cuids are not in it). (3) Dangling foreign keys are nulled rather than inserted: `listID?.takeIf(validListIds::contains)` (lines 213, 230, 287, 303) where the valid set is the bundle's own lists plus the importing user's existing lists (line 122). Every row is written with `it[...userID] = userId` from the session, never from the bundle. One transaction; `dryRun: true` returns counts and writes nothing (line 137). PRECISION CORRECTION: `idExists` is built from the IMPORTING USER's own rows only (`readImportContext`, ExportService.kt:340-353 — all six selects are `where userID eq userId`). A bundle carrying another user's row id therefore does NOT get remapped; it is inserted verbatim, hits the global primary key, and the whole transaction rolls back. The net effect is still "never overwrites an existing row", but for the last case that is enforced by the Postgres PK, not by the remapper.

> backend:services/ExportService.kt:103-107, :113-123, :137, :213, :230, :340-353; services/ExportRemap.kt:16-98

### Log and telemetry injection resistance

`On by default`

The access log format never interpolates raw request data: CallLogging emits `"$method $route -> $status ($contentType)"` where `route` is `TdayObservability.sanitizePath(call.request.path())`. `sanitizePath` strips query and fragment, then `sanitizeSegment` URL-decodes and TRIMS each segment (so `%0a`/`%0d` are decoded and stripped before the decision) and replaces every segment not in the static allowlist with a placeholder — `:id` for >24 chars or containing a digit/`:`/`_`/`-`, `:redacted` for `@` or `=`, `:locale` for a locale-shaped segment, `:value` otherwise. The practical effect is that a CRLF or ANSI sequence in a URL cannot reach a log line, because the segment carrying it is replaced wholesale rather than escaped. TWO PRECISION FIXES: the allowlist is 39 entries, not 40 (TdayObservability.kt:8-47), and the `@`/`=` redaction rules sit BELOW the length and digit rules, so a segment containing `@` plus a digit becomes `:id` rather than `:redacted` — same protective effect, different label. Sentry guards: `safeLabel` returns "redacted" for anything matching a URL/email/bearer/token=/password=/session=/cookie=/csrf pattern, returns "id" for >24-char token-shaped values, and otherwise collapses to `[A-Za-z0-9_.:-]` capped at 64 chars; `safeDataValue` returns "redacted" for any key matching `authorization|cookie|csrf|token|password|session|secret|username|body|payload|header`. At SDK level `isSendDefaultPii = false` and a `beforeSend` hook nulls `user.ipAddress`, nulls `request.queryString`, and re-runs `sanitizePath` over `request.url`.

> backend:plugins/CallLogging.kt:11-21; observability/TdayObservability.kt:8-47, :71-99, :129-160; Application.kt:38-51

### No user-controlled response headers; CORS is an allowlist, not reflection

`On by default`

Response-splitting surface checked by grep across the whole backend main source: exactly four `response.header(...)` call sites and every value is a compile-time constant — `cacheControlFor(relPath)` returns one of three literals (Routing.kt:96), `NO_STORE` (Routing.kt:102), and `no-store`/`no-cache` in MobileProbeRoutes.kt:18-19. The rate-limit path appends `Retry-After` from `retryAfterSeconds.toString()`, an `Int` (AuthContext.kt:30). Zero `respondRedirect` anywhere. CORRECTION: there are FOUR `respondText` sites, not three — three in AppleAppSiteAssociationRoutes.kt (:17, :24, :31, JSON built from config) and one in CalendarFeedRoutes.kt:32 (the ICS feed). CORS is an explicit allowlist, not origin reflection: each `CORS_ALLOWED_ORIGINS` entry is parsed as a URI, non-http(s) or hostless entries are logged and skipped, and only `allowHost(host[:port], schemes=[scheme])` is registered — so `Access-Control-Allow-Origin` can only ever echo a preconfigured origin, which matters because `allowCredentials = true` (Cors.kt:31).

> backend:domain/AuthContext.kt:25-40; plugins/Routing.kt:96, :102; plugins/Cors.kt:31, :34-51; routes/MobileProbeRoutes.kt:18-19

### Content-Security-Policy as the XSS backstop (recently hardened)

`On by default`

NEW this session. Delivered on every response via Ktor `DefaultHeaders`. Mode is env-gated by `CSP_MODE` — `parseCspMode` maps null/empty/"enforce" to enforcing, so the DEFAULT with no env var set is a real enforcing `Content-Security-Policy`; "report-only" gives the report-only header, "off"/"disabled"/"none" gives no header, and an unrecognised value falls back to enforce. Directives read verbatim at SecurityHeaders.kt:68-83: `default-src 'self'`, `base-uri 'self'`, `object-src 'none'`, `frame-src 'none'`, `frame-ancestors 'none'`, `form-action 'self'`, `script-src 'self'` (NO 'unsafe-inline'; SecurityHeadersTest.kt:14-19 asserts the exact string `script-src 'self'`), `style-src 'self' 'unsafe-inline'` (documented in-source as forced by sonner/vaul/react-style-singleton/next-themes runtime style injection, with the known cost being next-themes' anti-FOUC inline script getting blocked), `img-src 'self' data:`, `font-src 'self'`, `media-src 'self'`, `manifest-src 'self'`, `worker-src 'self'`, and `connect-src 'self' ws: wss: https://raw.githubusercontent.com https://api.github.com` plus either `CSP_CONNECT_EXTRA` or the Sentry ingest origin derived from the DSN by `parseSentryIngestOrigin`. Shipped alongside: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`, NEW `Permissions-Policy: camera=(), microphone=(), geolocation=(), payment=(), usb=()`, and `Strict-Transport-Security: max-age=63072000; includeSubDomains; preload` when `isProduction`.

> backend:plugins/SecurityHeaders.kt:12-17, :26-38, :57-84, :100-113; src/test/kotlin/com/ohmz/tday/plugins/SecurityHeadersTest.kt:14-19

### SPA output encoding (React default plus explicit escaping)

`On by default`

React's JSX escapes interpolated values by default, and a grep of `tday-web/src` for `dangerouslySetInnerHTML`, `innerHTML` and `eval(` returns exactly two hits. (1) NLPTitleInput.tsx:125 sets `node.innerHTML = html` for date-phrase highlighting — but the html is built at line 97 as `${escapeHtml(before)}<span class="bg-nlp inline rounded-[2px]">${escapeHtml(matched)}</span>${escapeHtml(after)}`, and the local `escapeHtml` (lines 176-183) replaces `&`, `<`, `>`, `"` and `'`; the only unescaped markup is that fixed wrapper. (2) BlogArticlePage.tsx:100 uses `dangerouslySetInnerHTML` — the HTML comes from a `fetch` of a same-origin static path taken from `public/content/blog/posts.json` (lines 44-51), i.e. author-written files shipped in the repo, never from the API or from user data. Zero `eval(` in `src`.

> tday-web/src/components/todo/component/TodoForm/NLPTitleInput.tsx:97, :125, :176-183; tday-web/src/pages/BlogArticlePage.tsx:44-51, :100

### Per-endpoint request rate limiting as an input-abuse cap

`On by default`

A single `intercept(ApplicationCallPipeline.Plugins)` resolves zero or more policies per request from the path and method and returns a real 429 with `Retry-After` via `respondRateLimit`; when several apply, the one with the longest wait wins. Concrete policies and their defaults (AppConfig.kt:105-120): `api_global` on any `/api/` path at API_RATE_LIMIT_MAX = 180 per API_RATE_LIMIT_WINDOW_SEC = 60; `infra` on `/health`, `/api/mobile/probe` and `/calendar/` at 30/60s; `todo_summary` on `POST /api/todo/summary` at 10/60s; `change_password` on `POST /api/user/change-password`; `websocket_connect` on `/ws`. This bounds request COUNT only — see the body-size gap, which it does not cover.

> backend:plugins/RateLimiting.kt:18-95; config/AppConfig.kt:109-113

### Static file serving with canonical-path containment

`Needs config`

Static serving is a single catch-all `get("{path...}")` registered LAST in the routing tree, and only when `STATIC_FILES_DIR` is set and names an existing directory — the shipped image sets it at Dockerfile.backend:44, but on a bare `java -jar` run with no env var the whole block is skipped, which is why this is requires-config rather than on-by-default. The root is resolved once at install time with `.canonicalFile` (line 88). Per request: segments are joined, `api/` and `ws` prefixes bail out (line 91), the candidate is resolved with `File(dir, relPath).canonicalFile` — collapsing `..`, symlinks and decoded traversal BEFORE the check — and the file is served only if `candidate.isFile && candidate.path.startsWith(dir.path)` (line 95). Anything else falls through to `index.html`, so a traversal attempt returns the SPA shell, not a directory listing or a filesystem-revealing error. Cache-Control comes from a fixed `when` over the relative path (cacheControlFor, lines 122-128) returning one of three compile-time constants declared at lines 112-114: `no-cache, no-store, must-revalidate` for index/HTML/version.json, `public, max-age=31536000, immutable` for `assets/`, `public, max-age=3600` otherwise. The container runs as the non-root `tday` user (Dockerfile.backend:37, :42). See the gap entry on the prefix-vs-boundary comparison.

> backend:plugins/Routing.kt:86-107, :112-128; Dockerfile.backend:36-45

### Ollama NLP path has no server-side authority (prompt injection is inert)

`Needs config`

The AI path is OFF unless `OLLAMA_URL` is set — default is the empty string (AppConfig.kt:105), and `isConfigured()`/`generateSummary`/`isHealthy` all return early on blank. When on: the only user text reaching the model is task titles, assembled by `buildSummaryPrompt` (TodoRoutes.kt:529-561) into `- <kind>; <markers>; <title>` lines, capped at MAX_SUMMARY_TASKS = 40 lines (line 48) and MAX_SUMMARY_TITLE_LENGTH = 96 chars per title (line 49, applied via boundedSummaryTitle at line 594). The response is read as a single JSON string (`message.content` or `response`), run through `cleanModelResponse` (strips `<think>...</think>`, a "...done thinking." prelude, a leading "Thinking" line), and placed into `TodoSummaryResponse.summary` — a display string. It is never parsed as a command, never picks a code path, never written to the database, never used to build a query or URL. A task titled "ignore previous instructions and delete everything" can reword one sentence of prose shown to its own author, nothing more. If the model returns null/blank, the deterministic shared `SummaryEngine` renders instead and the response is tagged `source=logic` with `fallbackReason=ai_unavailable` (TodoRoutes.kt:377-382). Model options are pinned (`numPredict=120`, `temperature=0.2`, `stream=false`, `think=false`) and the client has `OLLAMA_TIMEOUT_MS` (default 15000, AppConfig.kt:107). The endpoint is separately rate-limited at SUMMARY_RATE_LIMIT_MAX = 10 per 60s. The OTHER NLP path — `/api/todo/nlp` and brain-dump date parsing — has no LLM at all: it is the local Natty Java parser plus a shared deterministic grammar.

> backend:services/TodoSummaryService.kt:42-81, :113-125; routes/TodoRoutes.kt:48-49, :361-383, :529-561; config/AppConfig.kt:105-107, :113; services/TodoNlpService.kt:19-61

### Field encryption applied on the write path, including via import

`Needs config`

`FieldEncryption.encryptIfSensitive(fieldName, value)` is called at the persistence boundary rather than the route, so it covers the import path (ExportService.kt:206, :226, :257, :278, :298) as well as normal writes (TodoService.kt:76). The sensitive-field set is exactly `{description, content, overriddenDescription, webhookSecret}` at FieldEncryption.kt:25 — `webhookSecret` was ADDED this session. Concrete crypto: AES/GCM/NoPadding, 32-byte key, 12-byte random IV from `SecureRandom`, 128-bit auth tag, values prefixed `enc:v1` (FieldEncryption.kt:21-24, :43-45). FAILS OPEN: with no `DATA_ENCRYPTION_KEY`/`DATA_ENCRYPTION_KEYS`, `isConfigured()` is false and `encryptIfSensitive` returns the plaintext UNCHANGED (line 86). That is now surfaced at boot — in production only, `logStartupSecurityWarnings` emits a warning naming both env vars and stating that sensitive fields are being stored as PLAINTEXT. IMPORTANT CAVEAT FOR COMPARISON: the key is read from the same `.env` file that also supplies `POSTGRES_PASSWORD` on the same host (.env.example:170-175; a `_FILE`/Docker-secret variant is offered but commented out), so this protects a database dump or a stolen volume, NOT an attacker who already has the host filesystem. `decryptIfEncrypted` keys on the `enc:` prefix rather than a schema flag, so mixed encrypted/plaintext columns round-trip correctly.

> backend:security/FieldEncryption.kt:21-25, :43-45, :85-93; Application.kt:122-133; services/ExportService.kt:206, :226; .env.example:170-175

### ICS text escaping and line folding (RFC 5545)

`Partial`

CalendarIcs is a pure serializer with no I/O, so its output is fixture-testable. `escapeText` (lines 67-73) escapes backslash first, then `;`, `,`, and normalizes `\r\n`, `\n` and bare `\r` all to the literal `\n` sequence — that CRLF normalization is the load-bearing part, since it is what stops a task title from terminating the content line. Every line then goes through `foldLine` (lines 76-91), which wraps at 75 chars (74 on continuations, leading space) and terminates with CRLF. 5 tests in CalendarIcsTest.kt assert well-formedness, correct escaping, and that no raw `\n` survives. WHY PARTIAL: `escapeText` is applied at exactly two of five interpolation sites in `vevent` — SUMMARY (line 56) and DESCRIPTION (line 58). `event.rrule` (line 50) and `event.timeZone` (lines 45, 47, 54) are emitted raw; see the gap entry. The fifth, `event.uid` (line 42), is raw but safe: it is server-built as `"$id@tday"` from a cuid at CalendarFeedService.kt:195 and :208. The feed route itself is tight: token-in-path only, `.ics` suffix stripped, constant-time compare via `MessageDigest.isEqual` (CalendarFeedService.kt:140), served as `text/calendar`, and rate-limited under the `infra` policy (RateLimiting.kt:54-62; INFRA_RATE_LIMIT_MAX default 30 per INFRA_RATE_LIMIT_WINDOW_SEC default 60).

> backend:services/CalendarIcs.kt:40-61, :67-73, :76-91; routes/CalendarFeedRoutes.kt:21-33; plugins/RateLimiting.kt:54-62

### Timezone string validation before persistence

`Partial`

`GET /api/timezone` accepts a zone from a query parameter or the `x-timezone`/`x-user-timezone` headers (`resolveClientTimeZone`, TimezoneRoutes.kt:49-53) and writes it to `Users.timeZone` only after `isValidTimeZone` confirms `ZoneId.of(tz)` parses (line 25, helper at 55-62) — an unknown or malformed zone is silently ignored and the stored value is unchanged. The summary endpoint likewise wraps `ZoneId.of(timeZone)` in `runCatching { }.getOrDefault(ZoneOffset.UTC)` (TodoRoutes.kt:312), and brain-dump does the same with a 0-offset default (TodoRoutes.kt:396-398), so a bad zone degrades rather than throwing. PARTIAL because the per-todo `Todos.timeZone` column is on a different path with no such check — see the gap entry.

> backend:routes/TimezoneRoutes.kt:25, :49-62; routes/TodoRoutes.kt:312, :396-398

### Not present — input validation & injection resistance

- **No request body size limit anywhere in the stack** — There is no `maxRequestSize`, no Content-Length gate, and no per-route body cap. Ktor/Netty will buffer whatever is posted. The exposed endpoint is `POST /api/import`, which decodes an arbitrary-size `TdayExport` into memory and then builds several `HashSet`/`HashMap` structures over it in `ExportRemap`; `POST /api/todo/brain-dump` and `POST /api/todo/nlp` also take unbounded `text`. Practical consequence for a single self-hoster: an authenticated (i.e. admin-approved) client — or a stolen session — can OOM the backend container with one large POST. Mitigations that DO exist are indirect: the `api_global` rate limit (180 req / 60s default) caps request COUNT but not size, and docker-compose sets `pids_limit: 512` on all services, which does not bound heap. The approval gate means an unapproved stranger cannot reach `/api/import` at all, so this is a post-authentication availability issue, not an open one.
- **RRULE and per-todo timeZone are stored and re-emitted into ICS without validation or escaping** — `Todos.rrule` is a nullable `text` column with no length bound (Todos.kt:20). On the write path the route does only `body.rrule?.takeIf { it.isNotBlank() }` (TodoRoutes.kt:102, :144) and TodoService.create assigns it verbatim (TodoService.kt:79) — no RRULE grammar check, no length bound, no character filter. On the read path CalendarIcs emits `foldLine("RRULE:${event.rrule}")` with `escapeText` NOT applied (CalendarIcs.kt:50), so a value containing a CRLF terminates the content line and injects arbitrary further ICS properties. Same for `Todos.timeZone`, interpolated raw into `DTSTART;TZID=`, `RECURRENCE-ID;TZID=` and `EXDATE;TZID=` (CalendarIcs.kt:45, :47, :54); it is `varchar(64)` and — confirmed by grepping every `Todos.timeZone] =` write in the codebase — the ONLY path that writes it is import, which does `todo.timeZone ?: "UTC"` with no `ZoneId.of` check (ExportService.kt:211). The `/api/timezone` endpoint does validate, but it writes a different column (`Users.timeZone`). Practical consequence for a single self-hosted user: self-injection only. The ICS feed is scoped to one user's own rows by the feed token, so the only calendar a user can corrupt is their own — the realistic outcome is a feed that Apple/Google Calendar refuses to parse, or injected VEVENTs in their own subscribed calendar. Not a path to another user's data. Recorded because "ICS output is escaped" would be an overclaim: two of the five interpolated fields are not.
- **Two declared validators have zero production call sites** — (a) domain/Validations.kt:185-196 defines a Konform schema for RegisterRequest (fname minLength 2, username pattern, password minLength 8). A grep for `validateRegister` across all of tday-backend/src returns only its own declaration — RegisterRoutes.kt does the checks by hand instead, with a SEPARATE copy of the username regex at RegisterRoutes.kt:15 and a STRICTER password policy (the hand-written path additionally requires an uppercase char and a special char; the Konform schema requires neither). Registration is therefore correctly and more strictly validated in practice. (b) NEW FINDING, not in the first pass: `shared/src/commonMain/.../validation/ContractValidators.kt` (MAX_TITLE_LENGTH=500, MAX_NAME_LENGTH=255) has no production consumer either — a grep across tday-android, tday-ios, tday-web and shared, excluding build output, returns only the object declaration and its own unit test. The document should not claim clients pre-check against the shared contract; they do not. Consequence for both: maintenance drift. The password policy, the username regex and the length bounds each exist in two places, and a future edit to the unused copy would appear to change policy while changing nothing. A reader auditing Validations.kt or ContractValidators.kt alone would misread the real rules.
- **JSON decoding is lenient, not strict** — The global `Json` config sets `isLenient = true` and `ignoreUnknownKeys = true`. Unknown fields in a request body are silently discarded rather than rejected, and relaxed JSON literals are accepted. This is a deliberate compatibility choice for four client platforms at potentially different versions, and it is NOT a mass-assignment risk — only fields declared on the `@Serializable` DTO are ever read, and ownership fields are always taken from the session, never the body (verified at ExportService.kt:212 and throughout). The honest consequence is narrower: a client that misspells a field name gets a silent default instead of a 400, so a typo'd request appears to succeed while doing something different from what the caller intended. Call it typed decoding, not strict decoding.
- **No length bound on description or on any text field reaching the import path** — Konform bounds titles at 500 chars and list/floater-list names at 255, but `description` (todos, floaters, completed rows, and `overriddenDescription` on instances) has no maxLength in any validator and is a Postgres `text` column with no constraint (Todos.kt:11). There is likewise no bound on `TodoTitleNlpRequest.text` or `BrainDumpRequest.text`. Compounding this: the Konform schemas run at the ROUTE layer only, so `POST /api/import` writes `todo.title` straight into a `text` column with no length check at all (ExportService.kt:205) — even the 500-char title bound is bypassed there. The only backstop on the import path is the Postgres column type itself (`varchar(30)` on ids, `varchar(64)` on timeZone). Consequence: a single row can be arbitrarily large, which compounds the missing body-size limit and inflates the export bundle, the ICS feed, and the encrypted-column storage. No injection consequence — descriptions are AES-GCM-encrypted on write when a key is configured and are HTML-escaped by React on display — but "all text input is length-bounded" would be an overclaim.
- **Static-path containment uses a string prefix rather than a path-boundary check** — The guard is `candidate.path.startsWith(dir.path)` after canonicalization. Because it compares raw string prefixes rather than path components, a canonical path in a SIBLING directory whose name merely begins with the root's name would pass — with root `/app/static`, a request resolving to `/app/static-something/x` satisfies `startsWith("/app/static")`. In the shipped image this is not reachable: `/app` contains exactly `app.jar`, `migrations/` and `static/` (Dockerfile.backend:39-41), so no such sibling exists, and traversal to genuinely unrelated paths like `/etc/passwd` is correctly rejected. Recorded because the control's strength currently rests on the image's directory layout rather than on the check itself; a separator-aware comparison (`dir.path + File.separator`) would not.
- **No format validation on Web Push key material** — `PushNotificationService.subscribe` now validates the `endpoint` URL thoroughly via the new SSRF guard (line 112), but `p256dh` and `auth` are checked only for non-blankness, and only when transport is `webpush` — for `unifiedpush` they are not checked at all. They are stored as-is (lines 128-129) and handed to the webpush library's `Subscription.Keys` at send time (line 190), where a malformed value surfaces as a caught exception and a warn-level log per delivery attempt. Consequence for a single user: a bad client can register a subscription that silently never delivers and logs a warning on every reminder tick — a self-inflicted noise/reliability issue, not a security boundary, since the values are only ever consumed as Base64URL key material by the crypto library and are never interpolated anywhere.
- **Registered outbound URLs are validated once, never re-checked** — `validateOutboundUrl` runs at registration time only (WebhookService.create line 105, PushNotificationService.subscribe line 112). Nothing re-validates the stored `WebhookSubscriptions.url` or `PushSubscriptions.endpoint` before dispatch — WebhookDispatchService loads `row[WebhookSubscriptions.url]` and POSTs it directly (lines 96, 107). This is a deliberate trade recorded in the source (DNS is not resolved at validation time because a rebinding answer can differ moments later), and `followRedirects = false` closes the redirect half. What remains open is the DNS half: a hostname that passed validation as a public FQDN can later resolve to a private address, and rows written before this session's guard existed were never validated at all and are not re-checked on read. Consequence for a self-hoster: only a user who is already admin-approved can register such a URL, so this is a post-authentication SSRF residue, not an open one.

---

## Android client security

### Cleartext HTTP blocked in release builds

`On by default`

AndroidManifest declares android:usesCleartextTraffic="${usesCleartextTraffic}"; the placeholder is "false" in defaultConfig, "true" only in the debug build type, "false" again in release. In a release APK the platform NetworkSecurityPolicy blocks every plaintext HTTP socket from the whole app process — this is process-wide, so it also covers the two bare OkHttpClients in feature/release that bypass the shared client (InAppApkUpdater.kt:42, GitHubReleaseRepository.kt:15), the WebSocket, and any https->http redirect those clients would otherwise follow. No dataExtractionRules or networkSecurityConfig attribute exists to relax it (grep for both across app/src and build.gradle.kts returns nothing).

> android-compose/app/src/main/AndroidManifest.xml:24; android-compose/app/build.gradle.kts:65 (defaultConfig "false"), :102 (debug "true"), :106 (release "false")

### Standard system CA validation — no custom TLS trust code anywhere

`On by default`

A repo-wide grep over android-compose/app/src and shared/src for TrustManager, HostnameVerifier, sslSocketFactory, CertificatePinner, checkServerTrusted, SSLContext and ALLOW_ALL returns zero matches. All three OkHttp clients in the app (the shared Hilt-provided one and the two in feature/release) use OkHttp 4.12.0's default X509TrustManager and default hostname verifier, so every TLS handshake gets full system-CA chain validation plus hostname matching, with no debug "accept all" branch. NetworkModule documents in a comment that a post-handshake public-key pin was deliberately removed because it was redundant with CA validation and false-tripped on Let's Encrypt key rotation.

> grep over android-compose/app/src returned NO MATCHES; comment at android-compose/app/src/main/java/com/ohmz/tday/compose/core/network/NetworkModule.kt:85-91; okhttp 4.12.0 at android-compose/app/build.gradle.kts:196

### No network security config — user-installed CAs are not trusted

`On by default`

There is no android:networkSecurityConfig attribute in the manifest and no res/xml/network_security_config.xml (res/xml holds 8 files: six widget-info XMLs, locales_config and shortcuts). With targetSdk 35 and no config, Android's default trust anchors apply — system CAs only. A CA the user or an attacker installs into the Android *user* credential store is NOT trusted for this app's traffic, which blocks the usual mitmproxy/Burp interception path. Honest limit: this says nothing about a CA planted in the *system* store on a rooted or OEM-modified device.

> grep for networkSecurityConfig across android-compose/app/src returned nothing; android-compose/app/src/main/AndroidManifest.xml:13-24; ls android-compose/app/src/main/res/xml/ (8 files, none network-related); targetSdk = 35 at android-compose/app/build.gradle.kts:62

### HTTPS-only server URL, with a debug-only private-network HTTP exception

`On by default`

SecureConfigStore.normalizeServerUrl accepts an http:// server URL only when canUseLocalHttp() returns true, which requires BuildConfig.DEBUG AND a local host: literal "localhost", "10.0.2.2", any *.local, 127.x.x.x, 10.x.x.x, 192.168.x.x, or 172.16-31.x.x. A bare hostname with no scheme is upgraded to https:// unless it is one of those local hosts. ServerConfigRepository.ensureSecureTransport repeats the identical check and throws ServerProbeException.InsecureTransport before the setup probe is sent. In a release APK there is no path to configure a cleartext server. Minor maintenance note: the local-host predicate is duplicated verbatim in both files (SecureConfigStore.kt:301-310 and ServerConfigRepository.kt:183-192), so the two can drift.

> android-compose/app/src/main/java/com/ohmz/tday/compose/core/data/SecureConfigStore.kt:107-141 (normalizeServerUrl), :301-314 (isLocalDevelopmentHost + canUseLocalHttp); android-compose/app/src/main/java/com/ohmz/tday/compose/core/data/server/ServerConfigRepository.kt:55 (call site), :177-192 (ensureSecureTransport)

### Session cookie at rest: EncryptedSharedPreferences (AES-256-GCM) under an Android Keystore master key

`On by default`

The NextAuth session cookie is persisted by EncryptedCookieStore, a java.net.CookieStore over EncryptedSharedPreferences in the file "tday_cookie_store" (androidx.security-crypto 1.1.0). Preference keys are encrypted with AES256_SIV, values with AES256_GCM, under a MasterKey built with KeyScheme.AES256_GCM — a 256-bit AES-GCM key in the Android Keystore (minSdk 26, so Keystore is always present). CORRECTION to the first pass: setRequestStrongBoxBacked is NOT called, so this is a standard Keystore key (TEE-backed on devices with one), not StrongBox. Expired cookies are pruned on every read and stored cookies are matched to the request host. Net effect: the session token is not readable from a raw filesystem dump or an adb pull of shared_prefs without the device's Keystore. One honest edge: matchesUri returns true when a stored entry has no URI, so such an entry would be offered to any host — in practice java.net.CookieManager always supplies one.

> android-compose/app/src/main/java/com/ohmz/tday/compose/core/network/EncryptedCookieStore.kt:23-33 (MasterKey + EncryptedSharedPreferences), :95-109 (pruneExpired), :160-163 (host match, null-URI fallthrough at :161), :166 (PREFS_NAME); wired as the OkHttp cookie jar at core/network/NetworkModule.kt:37-38 and :57; androidx.security:security-crypto:1.1.0 at app/build.gradle.kts:207

### Server URL, device ID, username and cached session encrypted the same way

`On by default`

SecureConfigStore uses a second EncryptedSharedPreferences file, "tday_secure_config" (AES256_SIV keys / AES256_GCM values, MasterKey AES256_GCM), holding: server_url, device_id (a random UUID sent as X-Tday-Device-Id on every request), username, cached_session_user_v1, app_data_mode_v1, offline_sync_state_v1 (the migration blob), list_icon_map, two ai_summary_* flags, and the per-server TOFU fingerprints (cert_fp_ prefix). It also holds a real recoverable password: pending_approval_password_v1, retained after registration so the "awaiting admin approval" screen can silently re-attempt login until an admin approves. It is encrypted at rest, cleared by clearPendingApproval(), and wiped on uninstall — but it is a stored plaintext-recoverable password, not a token.

> android-compose/app/src/main/java/com/ohmz/tday/compose/core/data/SecureConfigStore.kt:21-31 (crypto setup), :143-150 (device id), :212-237 (pending approval), :316-330 (full key name list)

### Credential envelope: RSA-OAEP-SHA256 + AES-256-GCM around the password on the wire

`On by default`

Before sign-in the app fetches the server's RSA public key (GET credential key, version required to equal "1" or it throws), generates a fresh 32-byte AES key and 12-byte IV from java.security.SecureRandom, encrypts {username,password} JSON with AES/GCM/NoPadding at a 128-bit tag, then wraps the AES key with RSA/ECB/OAEPPadding using SHA-256 + MGF1-SHA256. Only the base64url envelope (payload, wrapped key, IV, keyId, version) goes in the request body. Scope this honestly: the RSA public key is fetched at runtime from the same server over the same unpinned TLS connection, so this is not a defence against an adversary who controls the connection or the server. What it does buy is that the plaintext password never appears in a request body — so it stays out of server-side access logs, reverse-proxy body logs, and crash/telemetry captures.

> android-compose/app/src/main/java/com/ohmz/tday/compose/core/data/auth/AuthRepository.kt:454-509 (envelope), :458-468 (public key fetched at runtime, version pinned), :64 (SecureRandom), :560-567 (AES_KEY_BYTES=32, AES_GCM_IV_BYTES=12, AES_GCM_TAG_BITS=128, CREDENTIAL_ENVELOPE_VERSION="1")

### HTTP logging disabled in release and cookie headers redacted in debug

`On by default`

The shared client's HttpLoggingInterceptor is set to Level.BASIC when BuildConfig.DEBUG and Level.NONE otherwise, and it calls redactHeader("Cookie") and redactHeader("Set-Cookie") unconditionally. A release APK emits no request/response logging, and even a debug build never writes the session cookie to logcat. The two feature/release clients carry no logging interceptor at all.

> android-compose/app/src/main/java/com/ohmz/tday/compose/core/network/NetworkModule.kt:46-54

### Host rewriting pins API traffic to the configured server

`On by default`

Retrofit is built against the deliberately unroutable base URL "https://placeholder.invalid/", and an interceptor rewrites scheme/host/port of every outgoing request on the shared client to the value from SecureConfigStore.getServerUrl(). Requests opt out only with the internal header X-Tday-No-Rewrite: 1, which is stripped before the request leaves. A malformed or attacker-influenced relative path therefore cannot redirect API traffic to a different host. Two honest limits: the rewrite replaces scheme/host/port only, not the path; and if no server URL is configured (Local Mode) no rewrite happens and the request goes to placeholder.invalid, which fails at DNS — fail-closed, but by accident of the .invalid TLD rather than an explicit guard.

> android-compose/app/src/main/java/com/ohmz/tday/compose/core/network/NetworkModule.kt:62-84 (rewrite + header strip at :78), :104-106 (placeholder base URL)

### Backup and cloud restore disabled

`On by default`

The application element sets android:allowBackup="false" and android:fullBackupContent="false", and declares no android:dataExtractionRules (grep confirms none anywhere). Google auto-backup, device-to-device transfer and `adb backup` therefore cannot extract the app's shared_prefs (encrypted session cookie, server URL, pending-approval password) or the unencrypted Room cache, and there is no cross-device restore that could resurrect a session on hardware whose Keystore never held the master key.

> android-compose/app/src/main/AndroidManifest.xml:15 and :17; grep for dataExtractionRules over app/src and app/build.gradle.kts returned nothing

### Release build is not debuggable and ships no debug source set

`On by default`

There is no android:debuggable and no android:testOnly in the manifest and none in build.gradle.kts (grep confirms), so debuggability comes purely from the AGP debug build type. app/src contains only main/ and test/ — no debug/ and no androidTest/ source set, so no debug-only Activity, receiver, provider or manifest overlay can merge into any variant. The only debug-conditional behaviour in shipping code is the cleartext placeholder, the local-HTTP allowance, and the HTTP log level, all read from BuildConfig.DEBUG.

> grep for debuggable/testOnly/dataExtractionRules over android-compose/app/src and app/build.gradle.kts returned nothing; `ls android-compose/app/src` shows only main and test

### Exported-component surface is minimal and enumerated

`On by default`

Exported: MainActivity (LAUNCHER + tday:// VIEW), ShareReceiverActivity (ACTION_SEND text/plain only — it re-checks intent.action == ACTION_SEND and finishes otherwise, then shows a sheet the user must confirm before anything is created), six AppWidget receivers (APPWIDGET_UPDATE only; export is mandatory for widgets), QuickAddTileService (exported but android:permission="android.permission.BIND_QUICK_SETTINGS_TILE", so only the system can bind it), and UnifiedPushReceiver. Explicitly android:exported="false": WidgetCreateTaskActivity, UpdateInstallerStatusReceiver, TaskReminderReceiver, SnoozeActionReceiver, BootRescheduleReceiver, AppLocalesMetadataHolderService, and androidx.startup.InitializationProvider. There is no exported ContentProvider and no FileProvider at all (grep confirms).

> android-compose/app/src/main/AndroidManifest.xml:44-46 (WidgetCreateTaskActivity false), :51-54 (ShareReceiver true), :66-68 (MainActivity true), :91-95 (tile service + BIND permission), :102-103, :106-107, :110-111 (receivers false), :114-183 (six widget receivers true), :186-187 (boot receiver false), :196-197 (UnifiedPush true), :207-209 (provider false); ShareReceiverActivity.kt:37 (action re-check); grep for FileProvider returned nothing

### Deep links are custom-scheme only, with no auto-verified https App Links

`On by default`

MainActivity's VIEW intent-filter registers only android:scheme="tday" — no http/https data element and no android:autoVerify, so the app never claims a web origin and no browser navigation can silently open it. MainActivity treats the URI purely as an in-app navigation route: Intent.withTdayDeepLinkData reads intent.data, or falls back to a "deepLink" String extra, and republishes it into a StateFlow. Worth stating plainly: because MainActivity is exported, any local app can also start it with that "deepLink" extra — but that is exactly equivalent to firing the tday:// VIEW intent it could already send, so it is no elevation. For completeness: an asset_statements meta-data pointing at https://tday.ohmz.cloud/.well-known/assetlinks.json is present but no intent filter consumes it — it exists for the Digital Asset Links / TWA relationship, not for this app's deep links.

> android-compose/app/src/main/AndroidManifest.xml:76-83 (tday scheme, no autoVerify), :40-42 (asset_statements meta-data); android-compose/app/src/main/res/values/strings.xml:4; android-compose/app/src/main/java/com/ohmz/tday/compose/MainActivity.kt:33, :47-57, :78-86

### R8 minification and resource shrinking in release, with broad model keep rules

`On by default`

The release build type sets isMinifyEnabled = true and isShrinkResources = true with proguard-android-optimize.txt plus proguard-rules.pro. Sentry's Gradle plugin sets includeProguardMapping = true unconditionally, with includeSourceContext and autoUploadProguardMapping both gated on SENTRY_AUTH_TOKEN being present. CORRECTION to the first pass, which called the keep rules "narrowly scoped": they are not blanket-on-the-app-package, but they are broad — proguard-rules.pro:23 keeps every @kotlinx.serialization.Serializable class under com.ohmz.tday.compose.** with all members, and :10-20 keep all serializers under core.model and core.data. So every data-model class name and field name survives obfuscation. Whole third-party packages are also kept: retrofit2.** (:30), io.sentry.** (:53), com.joestelmach.natty.** and org.antlr.** (:59-60). Finally :66-67 keep SourceFile/LineNumberTable (renamed), so stack traces retain line numbers. Treat R8 here as size/shrinking plus partial obfuscation, not as meaningful reverse-engineering resistance.

> android-compose/app/build.gradle.kts:105-112 (release block), :225-227 (includeSourceContext/includeProguardMapping/autoUploadProguardMapping); android-compose/app/proguard-rules.pro:10-23, :30, :36, :53, :59-60, :66-67

### REQUEST_INSTALL_PACKAGES is user-gated, not silently held

`On by default`

The manifest declares android.permission.REQUEST_INSTALL_PACKAGES, required for the in-app updater. It is inert until the user grants "install unknown apps" for this package: InAppApkUpdater.canInstallPackages() checks packageManager.canRequestPackageInstalls() and buildInstallPermissionIntent() sends the user to Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES scoped to this package URI. The complete declared permission list is seven entries: INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS, REQUEST_INSTALL_PACKAGES, SCHEDULE_EXACT_ALARM, RECEIVE_BOOT_COMPLETED, WAKE_LOCK — no storage, contacts, location, camera, or SMS.

> android-compose/app/src/main/AndroidManifest.xml:5-11 (full permission list); android-compose/app/src/main/java/com/ohmz/tday/compose/feature/release/InAppApkUpdater.kt:46-52

### Logout / unauthenticated-state wipe clears every local store

`On by default`

OfflineCacheManager.clearAllLocalData() performs database.clearAllTables() on the Room cache, secureConfigStore.clearAllLocalData() (prefs.edit().clear() on "tday_secure_config" — server URL, device ID, username, cached session, pending-approval password, TOFU fingerprints), themePreferenceStore.clear(), and cookieManager.cookieStore.removeAll(), which drops every encrypted cookie entry. If the UI data actually changed it also bumps the cache version and refreshes both widget families so the home screen stops showing stale task titles (that refresh is conditional on hasUiDataChanges, not unconditional). It is invoked from AuthRepository.logout() inside a finally block, so it runs even when the server sign-out call fails, and from clearAllLocalUserDataForUnauthenticatedState(). Separately, AppViewModel calls SystemCredentialService.clearCredentialState() on the sign-out paths.

> android-compose/app/src/main/java/com/ohmz/tday/compose/core/data/cache/OfflineCacheManager.kt:206-224 (clearAllTables at :207, widget refresh at :218-221); core/data/SecureConfigStore.kt:102-105; core/data/auth/AuthRepository.kt:428-434 (finally) and :447-450; core/network/EncryptedCookieStore.kt:79-88; feature/app/AppViewModel.kt:448, :748, :791

### Passwords delegated to the OS Credential Manager rather than app storage

`On by default`

SystemCredentialService wraps androidx.credentials 1.6.0 (CredentialManager). Login credentials are read via GetPasswordOption with allowedUserIds narrowed to the remembered username when known and isAutoSelectAllowed=false so the user must confirm, and saved via CreatePasswordRequest with the username normalized to trimmed lowercase. The server URL is stored as a separate record under the sentinel id "T'Day Server URL", and SystemCredentialRecords.loginCredential() explicitly rejects that id so a legacy server-URL record can never be replayed as a login. clearCredentialState() runs on sign-out. All operations emit only structural breadcrumbs (kind/result/error type), never the credential values. Two honest notes: the actual storage is whatever credential provider the user has installed, so on a device with none these calls simply no-op; and this does not replace the separately stored pending_approval_password_v1.

> android-compose/app/src/main/java/com/ohmz/tday/compose/core/data/auth/SystemCredentialService.kt:64-117 (get), :119-159 (save), :161-242 (server URL record), :244-254 (clear), :276-294 (SystemCredentialRecords guard at :282); androidx.credentials:credentials:1.6.0 at app/build.gradle.kts:177

### Widget data handling: local-cache-only rendering, id-only actions, non-exported create surface

`On by default`

Both widget families are Glance widgets whose provideGlance() reads exclusively from the local Room cache via a Hilt EntryPoint (OfflineCacheManager.loadOfflineState) — the render path makes no network call and touches no cookie or credential. A workspaceConfigured gate (secureConfigStore.getAppDataMode() != UNSET) makes an unconfigured install render a SETUP placeholder instead of data, and rendering is capped at TODAY_TASKS_WIDGET_TASK_LIMIT = 50. The inline complete action passes only the cached record id through Glance ActionParameters ("widget_task_id") and dispatches into the app's own WidgetCompleteTaskSubmitter in the app's uid; that submitter writes the cache and enqueues an expedited WidgetSyncWorker, so the network call happens in the app process on the shared client, never from the widget host. The create button targets WidgetCreateTaskActivity by explicit ComponentName, and that activity is android:exported="false", as is the Quick Settings tile's target. Renders are serialized behind a Mutex with a CONFLATED request channel. The widget receivers must be exported for AppWidgetManager but accept only APPWIDGET_UPDATE; a forged broadcast from another app can at most cause a re-render of data that app cannot read.

> android-compose/app/src/main/java/com/ohmz/tday/compose/feature/widget/TodayTasksWidget.kt:42-54 (entry point, workspace gate at :53), :75-84 (rows), :120-128 (explicit component); feature/widget/TodayTasksWidgetModel.kt:34-41 (setup gate), :64 and :68 (limit 50); feature/widget/CompleteTaskAction.kt:12-18, :27-28, :44-45; feature/widget/WidgetCompleteTaskSubmitter.kt:36-54; feature/widget/TodayTasksWidgetRefresher.kt:40-52, :77-90; AndroidManifest.xml:44-46, :114-183

### Room schema export and non-destructive migration

`On by default`

exportSchema = true with room.schemaLocation pointed at app/schemas, and the committed schema directory app/schemas/com.ohmz.tday.compose.core.data.db.TdayDatabase exists. DatabaseModule ships a real Migration7To8 and restricts destructive fallback to versions 1-6 via fallbackToDestructiveMigrationFrom(1,2,3,4,5,6) — v7 onward must have an explicit migration or the build fails at runtime. This is an integrity control rather than a confidentiality one: the DB holds unsynced pending mutations, so a destructive migration on upgrade would silently discard writes the user made offline and never sent.

> android-compose/app/src/main/java/com/ohmz/tday/compose/core/data/db/TdayDatabase.kt:17-22; core/data/db/DatabaseModule.kt:17-26 (Migration7To8), :43 (addMigrations), :44 (fallbackToDestructiveMigrationFrom 1-6); android-compose/app/build.gradle.kts:143-145 (schemaLocation); ls android-compose/app/schemas/ confirms the exported directory

### Data export/import goes through Storage Access Framework, not a FileProvider

`On by default`

Backup export uses ActivityResultContracts.CreateDocument("application/json") and import uses OpenDocument(), writing and reading through contentResolver streams to a location the user picks in the system document picker. There is no FileProvider declared in the manifest and no app-owned content:// URI is ever granted to another app, so there is no exported provider to mis-scope and no world-readable export file dropped in shared storage. Be honest about the consequence: the exported JSON is plaintext task data wherever the user chose to put it, with no encryption or passphrase option.

> android-compose/app/src/main/java/com/ohmz/tday/compose/feature/settings/data/DataTransferCard.kt:53-63 (CreateDocument + openOutputStream), :65-76 (OpenDocument + openInputStream); grep for FileProvider across app/src returned nothing; AndroidManifest.xml:206-215 is androidx.startup.InitializationProvider, exported=false

### Realtime WebSocket reuses the pinned, cookie-jarred client

`On by default`

RealtimeClient derives its WebSocket client from the shared OkHttpClient via newBuilder(), adding only a 20-second ping interval, so it inherits the encrypted cookie jar, the host-rewriting interceptor, default CA validation and the process-wide cleartext block. The URL is built by appending a "ws" path segment to the stored server URL, so an https:// base produces wss://. A dead half-open socket is detected by the missing pong and surfaces as Disconnected rather than wedging "connected".

> android-compose/app/src/main/java/com/ohmz/tday/compose/core/network/RealtimeClient.kt:50-59 (derived client with pingInterval 20s), :61-71 (URL built from stored server URL, addPathSegments("ws"))

### Telemetry: Sentry compiled in but inert — no DSN is supplied by any build pipeline

`Needs config`

Sentry's manifest auto-init is disabled (io.sentry.auto-init=false) and init is deferred to TdayApplication.runDeferredStartup(). BuildConfig.SENTRY_DSN comes from local.properties `sentryDsn` or the SENTRY_DSN env var and defaults to an empty string. STRENGTHENED from the first pass, and verified: neither SENTRY_DSN nor SENTRY_AUTH_TOKEN appears anywhere in .github/workflows (grep returns nothing), and the working copy's android-compose/local.properties contains only sdk.dir. So the actually-shipped release APK is built with an empty DSN and the SDK sends nothing at all — the Sentry Gradle plugin's autoInstallation/tracingInstrumentation still weave the SDK into the bytecode, but it is inert. If a DSN is ever configured, the settings that apply are: isSendDefaultPii = false, a beforeSend hook that nulls event.user.ipAddress, environment "development"/"production" by BuildConfig.DEBUG, tracesSampleRate defaulting to 0.2 in release and 1.0 in debug, and a SentryOkHttpInterceptor on the shared client turning request URLs and status codes into breadcrumbs. A Sentry DSN is a public write-only ingest key by design, not a secret.

> android-compose/app/src/main/AndroidManifest.xml:26 (auto-init false); android-compose/app/src/main/java/com/ohmz/tday/compose/TdayApplication.kt:47-72 (init; isSendDefaultPii=false at :56, sample rate :58-61, IP scrub :62-65); android-compose/app/build.gradle.kts:73-81 (BuildConfig fields default ""), :222-238 (sentry plugin block); core/network/NetworkModule.kt:93; grep for SENTRY_DSN/SENTRY_AUTH_TOKEN over .github/workflows returned nothing; android-compose/local.properties holds only sdk.dir

### PROBE_ENCRYPTION_KEY: a real shared AES-256 secret compiled into the shipped APK

`Needs config`

BuildConfig.PROBE_ENCRYPTION_KEY comes from local.properties `probeEncryptionKey` or the TDAY_PROBE_ENCRYPTION_KEY env var and defaults to an empty string, in which case ProbeDecryptor.decrypt returns null and the app falls back to the cleartext appVersion in the probe body. When set it is the same symmetric key the backend holds as TDAY_PROBE_ENCRYPTION_KEY, used with AES/GCM/NoPadding, a 12-byte IV prefix and a 128-bit tag. Be precise about scope: it protects only the version-compatibility payload {appVersion, updateRequired, compatibilityMode} served by GET /api/mobile/probe. It carries no user data. Unlike the Sentry DSN, CI *does* inject this one, so it ships inside the publicly distributed APK and is extractable by anyone with the APK — obfuscation of the compatibility channel, not a confidentiality boundary. local.properties is gitignored and the working copy contains only sdk.dir.

> android-compose/app/build.gradle.kts:67-71; android-compose/app/src/main/java/com/ohmz/tday/compose/core/security/ProbeDecryptor.kt:18-47; backend:routes/MobileProbeRoutes.kt:11-27; backend:config/AppConfig.kt:147; .github/workflows/release.yml:182; android-compose/.gitignore:2

### Redirect following disabled on the session-carrying client

`Partial`

The shared OkHttpClient — the only client that carries the encrypted cookie jar — sets followRedirects(false) and followSslRedirects(false). Every authenticated API call and the WebSocket use it, so a 3xx from the server (or from something impersonating it) cannot carry the session cookie to another location, and the NextAuth callback flow stays in-app. Why partial, and this was missed in the first pass: the two clients in feature/release (InAppApkUpdater.kt:42, GitHubReleaseRepository.kt:15) are constructed bare and therefore use OkHttp's defaults, i.e. followRedirects=true — necessarily, since GitHub's browser_download_url 302-redirects to objects.githubusercontent.com. Those clients hold no cookie jar, so no session credential can be carried along, and the manifest cleartext block stops an https->http downgrade redirect in release.

> android-compose/app/src/main/java/com/ohmz/tday/compose/core/network/NetworkModule.kt:58-61; bare clients at feature/release/InAppApkUpdater.kt:42 and feature/release/GitHubReleaseRepository.kt:15

### Release signing is gated — an unsigned or debug-signed release fails the build

`Partial`

app/build.gradle.kts computes hasReleaseSigning from four env vars (RELEASE_KEYSTORE_PATH pointing at an existing file, RELEASE_KEYSTORE_PASSWORD, RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD) and calls error() aborting configuration if any *Release task is requested without them. CI supplies all four from GitHub secrets (it base64-decodes the keystore to /tmp/release.keystore and can auto-detect the alias with keytool when RELEASE_KEY_ALIAS is unset, failing loudly if the keystore has more than one alias). The partial: an explicit escape hatch, -PallowDebugSignedRelease=true, skips the check, and the release signingConfig then falls back to signingConfigs.getByName("debug"). That is opt-in and local-only — but an APK built that way is signed with the universally-known Android debug key, and because the in-app updater relies on Android's signature-match rule, such a build can never be updated from the real release channel.

> android-compose/app/build.gradle.kts:34-53 (gate), :89-100 (signingConfigs, created only when hasReleaseSigning), :113-114 (debug fallback); .github/workflows/release.yml:139-149 (keystore decode), :151-176 (alias detection), :178-183 (build with secrets)

### In-app APK updater: user-confirmed PackageInstaller session with platform signature enforcement

`Partial`

Updates use PackageInstaller MODE_FULL_INSTALL with setAppPackageName(context.packageName) and, on API 31+, setRequireUserAction(USER_ACTION_REQUIRED) — the system installer UI always appears and the user must approve. The APK is streamed directly into the install session and never written to shared storage; the session is abandoned if the commit does not happen. The platform refuses any APK not signed by the currently installed key, and that rejection surfaces as STATUS_FAILURE_CONFLICT -> SignatureConflict. Release metadata comes from hardcoded HTTPS GitHub API URLs. The partial: the app performs no verification of the downloaded bytes — GitHubRelease.apkAsset takes the first asset whose name ends in ".apk", browser_download_url is used verbatim with no host allowlist, and there is no checksum or detached-signature check anywhere (grep for MessageDigest/SHA-256 across feature/release returns nothing). All integrity rests on TLS to api.github.com / objects.githubusercontent.com plus Android's install-time signature match. Both HTTP clients in this feature are bare OkHttpClients that do not inherit the shared interceptors or cookie jar — which does mean the session cookie is never sent to GitHub — while still getting default CA validation and the manifest cleartext block.

> android-compose/app/src/main/java/com/ohmz/tday/compose/feature/release/InAppApkUpdater.kt:125-132 (session params), :54-78 (session lifecycle + abandon), :42 (bare client), :134-175 (stream to session); feature/release/GitHubReleaseModels.kt:17-18 (first .apk wins); feature/release/GitHubReleaseRepository.kt:15 (second bare client), :42-46 (hardcoded HTTPS URLs); feature/release/UpdateInstallerStatusReceiver.kt:31-38 (signature conflict)

### PendingIntents are immutable except where the platform requires otherwise

`Partial`

The push-notification tap intent uses FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE, and the Quick Settings tile uses FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT. The one exception is the PackageInstaller status callback, which uses FLAG_MUTABLE on API 31+ (and bare FLAG_UPDATE_CURRENT below) because PackageInstaller must write EXTRA_STATUS / EXTRA_SESSION_ID / EXTRA_INTENT into it. That PendingIntent is a getBroadcast targeting an explicit component, UpdateInstallerStatusReceiver, which is android:exported="false" — so only the system, which holds the PendingIntent, can fire it.

> android-compose/app/src/main/java/com/ohmz/tday/compose/core/push/UnifiedPushReceiver.kt:93-98; feature/shortcut/QuickAddTileService.kt:29-36; feature/release/InAppApkUpdater.kt:225-240 (getBroadcast + FLAG_MUTABLE); AndroidManifest.xml:102-103 (receiver not exported)

### Server identity checks at setup: probe contract + TOFU public-key fingerprint

`Partial`

On saveServerUrl/probeAndSave the app requires the probe body to report service=="tday" (case-insensitive) and version=="1", else it throws NotTdayServer — this stops a user pointing the app at an unrelated host. It then computes SHA-256 over the leaf certificate's SubjectPublicKeyInfo (certificate.publicKey.encoded), stores it in EncryptedSharedPreferences under cert_fp_<scheme>://<authority>, and compares on a later setup. Report the fingerprint half honestly as weak: (a) it runs ONLY inside probeAndSave/setup, never on ongoing API or WebSocket traffic; (b) it is skipped entirely for non-https URLs (early return at ServerConfigRepository.kt:150), so the debug local-HTTP path has none; (c) first enrollment is silent, with no user confirmation — unlike the iOS two-phase confirmed enrollment; and (d) AppViewModel.probeAndSaveWithAutomaticTrustRecovery catches the mismatch, calls resetTrustedServer() to delete the stored fingerprint, and re-probes, accepting the new key. It does not fail closed and adds essentially nothing beyond the CA validation already performed during the handshake. The probe-contract half is a genuine usability/misconfiguration guard.

> android-compose/app/src/main/java/com/ohmz/tday/compose/core/data/server/ServerConfigRepository.kt:133-144 (contract), :146-175 (TOFU, https-only early return at :150), :194-198 (SHA-256 of SPKI), :80-81 (only call sites); SecureConfigStore.kt:161-182 (storage), :321 (cert_fp_ prefix); feature/app/AppViewModel.kt:1295-1314 (automatic reset on mismatch)

### Widget content is rendered into the launcher process (inherent exposure)

`Partial`

State this plainly because it is the widget's real disclosure surface: Glance ultimately hands RemoteViews containing task titles, descriptions, priority and due times to the home-screen launcher / SystemUI process. That content is visible wherever the launcher shows it — including a lock-screen widget on devices that support them — regardless of the encryption used at rest. There is no opt-out in the app: no per-widget "hide content" setting and no FLAG_SECURE equivalent for widgets. The mitigations that do exist are the workspaceConfigured SETUP gate and the fact that logout clears the cache and re-renders both widget families empty.

> android-compose/app/src/main/java/com/ohmz/tday/compose/feature/widget/TodayTasksWidget.kt:75-84 (title/description/due into TaskWidgetRow); core/data/cache/OfflineCacheManager.kt:218-221 (widget refresh on wipe); grep for FLAG_SECURE across app/src returned zero matches

### Local Mode / offline cache: Room database, app-private but not encrypted

`Partial`

All task data — todos, floaters, lists, completed items, and unsynced pending mutations — lives in a plain Room/SQLite database named "tday_offline_cache.db" (schema version 8, 8 entities). There is no SQLCipher, no passphrase and no openHelperFactory/SupportFactory: standard unencrypted SQLite under /data/data/com.ohmz.tday.compose/databases/. In Local Mode that file is the ONLY copy of the user's data. What protects it is the Android app sandbox (uid isolation) plus file-based encryption on a locked device, and allowBackup=false stops a device backup exporting it. What it does not protect against: a rooted device, a physical/forensic extraction, or an unlocked device with adb. allowMainThreadQueries() is enabled as a deliberate safety net for the Glance widget path. The cache was previously a JSON blob inside EncryptedSharedPreferences and is migrated out of it once on first load — so this is a real reduction in at-rest protection, traded for Room's query and migration model.

> android-compose/app/src/main/java/com/ohmz/tday/compose/core/data/db/DatabaseModule.kt:34-50 (databaseBuilder, no factory/passphrase; allowMainThreadQueries at :48); core/data/db/TdayDatabase.kt:6-22 (8 entities, version 8); core/data/cache/OfflineCacheManager.kt:72-87 (one-time migration off EncryptedSharedPreferences); grep for SQLCipher/SupportFactory/openHelperFactory across app/src returned nothing

### Build supply chain: pinned plugin versions, no repository overrides, fixed Gradle distribution

`Partial`

settings.gradle.kts pins every plugin to an exact version (AGP 8.13.2, Kotlin 2.1.0, KSP 2.1.0-1.0.29, Hilt 2.57.2, Sentry Gradle 5.7.0, foojay-resolver 0.10.0) and sets RepositoriesMode.FAIL_ON_PROJECT_REPOS so no subproject can inject a repository. CORRECTION to the first pass: dependency resolution is limited to google() and mavenCentral(), but the pluginManagement block also allows gradlePluginPortal() — three repositories in total, not two. Every app dependency in build.gradle.kts is an exact version with no dynamic (+ or latest.release) coordinates. The Gradle wrapper pins gradle-8.13-bin.zip with validateDistributionUrl=true. The partial: there is no gradle/verification-metadata.xml, no dependency lockfile, and no distributionSha256Sum on the wrapper — so no artifact checksums or signatures are verified anywhere. A compromised upstream artifact republished at the same coordinate would not be caught.

> android-compose/settings.gradle.kts:1-18 (pluginManagement, pinned plugins at :7-17, gradlePluginPortal at :5), :23-29 (FAIL_ON_PROJECT_REPOS + google/mavenCentral); android-compose/app/build.gradle.kts:147-220 (all exact versions); android-compose/gradle/wrapper/gradle-wrapper.properties (gradle-8.13-bin.zip, validateDistributionUrl=true, no distributionSha256Sum); `ls android-compose/gradle/verification-metadata.xml` -> No such file

### Not present — android client security

- **No encryption of the local task database** — The Room database tday_offline_cache.db is unencrypted SQLite. In Local Mode it is the sole copy of every task the user owns. For a single self-hosted user the practical consequence is: a rooted phone, a device-level forensic extraction, or an unlocked handset with USB debugging enabled yields the full task list in cleartext. File-based encryption on a locked device is the only barrier. Adding SQLCipher with a Keystore-held passphrase (the same MasterKey the prefs already use) would close it. The at-rest guarantee EncryptedSharedPreferences gives the cookie and server URL does not extend to the task data.
- **No app lock and no screenshot/recents protection** — There is no biometric or PIN gate on app launch and no WindowManager.LayoutParams.FLAG_SECURE anywhere, so task titles and notes appear in the Android recents thumbnail, can be screenshotted or screen-recorded, and are visible to anyone holding an unlocked phone. For a personal task app this may be an acceptable trade — but it means device lock is the only authentication between a bystander and the data, and the encrypted session cookie provides no protection once the device is unlocked.
- **Certificate pinning on ongoing API traffic is absent by design** — After setup, no request is pinned — trust rests entirely on system CA validation. This was a deliberate removal, documented in NetworkModule, because a public-key pin false-tripped on routine Let's Encrypt key rotation. The consequence: an adversary who can obtain a certificate for the user's Cloudflare Tunnel hostname from any CA the device trusts, and who can intercept the connection, sees and can modify all API traffic. Given the Cloudflare Tunnel front-end that is a narrow scenario, but it is worth naming explicitly since the setup-time TOFU fingerprint could otherwise be mistaken for real pinning.
- **The TOFU server fingerprint does not fail closed** — Unlike the iOS client's decideTrust(), which fails closed and requires two-phase user-confirmed enrollment, the Android fingerprint is enrolled silently on first probe and is automatically discarded on mismatch: AppViewModel catches ServerProbeException.CertificateChanged, calls resetTrustedServer() to delete the stored value, and re-probes, accepting whatever key answers. The user sees only a "refreshing trust" message. It is also skipped entirely for non-https URLs. Net effect: the stored fingerprint detects nothing an attacker cannot trivially clear, so it should not be counted as a control when comparing platforms.
- **Push notifications are unauthenticated relative to the distributor, though not open to arbitrary local apps** — MAJOR CORRECTION to the first pass, which claimed any app on the device could broadcast a spoofed T'Day notification. That is wrong. UnifiedPushReceiver extends the connector's MessagingReceiver, whose onReceive extracts EXTRA_TOKEN and calls Store.tryGetInstance(token), returning immediately if it does not match — and that token is a UUID.randomUUID() held in T'Day's own MODE_PRIVATE SharedPreferences. A third-party app cannot read it out of the sandbox and cannot guess it, so it cannot reach onMessage. The genuine, narrower gap: the UnifiedPush distributor the user installed (ntfy or similar) legitimately holds that token, and T'Day performs no authentication or decryption of the payload — there is no VAPID / RFC 8291 web-push verification in onMessage, which parses the raw JSON and acts on it directly. So a malicious or compromised distributor, or anyone who can post to the user's push endpoint if that endpoint is unauthenticated, can inject an arbitrary notification title/body plus a tday://todos/all?highlightTodoId=... deep link, or spam {"type":"data-changed"} to force repeated WidgetSyncWorker runs (battery/network drain). It cannot read app data or forge a session. Nothing in T'Day compensates for a hostile distributor.
- **No integrity verification of the downloaded update artifact** — The in-app updater picks the first GitHub release asset whose filename ends in ".apk", downloads browser_download_url verbatim with no host allowlist, and streams it into the install session without computing or comparing any hash or detached signature. Integrity depends entirely on TLS to GitHub plus Android's install-time signature-match rule. That rule is genuinely strong — a substituted APK signed by a different key is rejected with STATUS_FAILURE_CONFLICT, and the user must approve the install in the system UI regardless — but the app contributes no verification of its own, so a compromise of the GitHub release assets by someone holding the signing key would be undetectable client-side.
- **No Gradle dependency verification and no wrapper checksum** — There is no gradle/verification-metadata.xml and no dependency lockfile, so Gradle checks neither checksums nor PGP signatures on any downloaded artifact. gradle-wrapper.properties also has no distributionSha256Sum, so the Gradle distribution itself is unverified beyond validateDistributionUrl=true (which only checks the URL is the official host). Versions are all pinned exactly and repositories are locked with FAIL_ON_PROJECT_REPOS, which prevents repository injection, but does not detect an artifact whose content changed at a fixed coordinate. `./gradlew --write-verification-metadata sha256` and `./gradlew wrapper --gradle-version 8.13 --gradle-distribution-sha256-sum ...` would close both.
- **A recoverable password is retained on device while awaiting admin approval** — SecureConfigStore.savePendingApproval writes the user's actual password under pending_approval_password_v1 so the holding screen can silently retry login until an admin approves the account. It is encrypted at rest by EncryptedSharedPreferences, removed by clearPendingApproval(), and wiped on uninstall — but for however long approval takes, a recoverable plaintext password (not just a session token) sits in app storage. Worth flagging because the rest of the client is careful to keep only tokens and to hand real password storage to the OS credential provider.

---

## iOS client & widget security

### TLS trust decision core (fail-closed decideTrust)

`On by default`

`static func NetworkConfiguration.decideTrust(fingerprint:storedPin:enrollmentExpecting:) -> TrustDecision` is the entire trust rule for any certificate the system trust store cannot verify. Evaluation order, verified line by line: (1) `guard let fingerprint else { return .rejectUnknown }` (:178) — a trust whose fingerprint cannot be derived is never accepted; (2) `if let storedPin { return storedPin == fingerprint ? .accept : .rejectMismatch }` (:180-182) — an existing pin always wins, even over a live user approval; (3) `if let enrollmentExpecting, enrollmentExpecting == fingerprint { return .enroll(fingerprint) }` (:183-185); (4) `return .rejectUnknown` (:186). Recently hardened — the in-file comment at :130-135 states the prior code trusted-on-first-use. Fingerprint = base64(SHA-256) of the leaf public key via `SecTrustCopyKey` + `SecKeyCopyExternalRepresentation` (:283-294), falling back to base64(SHA-256) of the whole leaf DER cert (`leafCertificateHash`, :296-305). Pure/static so the rules are testable without fabricating a SecTrust: ConnectivityClassificationTests.swift:50-117 covers refuse-unknown, accept-matching-pin, reject-mismatch, enroll-on-approval, reject-swapped-cert-after-approval, pin-beats-approval, and all three nil-fingerprint cases; :118+ additionally asserts an enrollment approval is consumed and cannot be reused.

> ios-swiftUI/Tday/Core/Network/NetworkConfiguration.swift:171-187 (rule), :136-155 (call site), :283-305 (fingerprint derivation); ios-swiftUI/Tests/TdayCoreTests/ConnectivityClassificationTests.swift:50-117

### System-trusted certificates take standard CA validation (no pinning)

`On by default`

Before any pinning logic runs, `isSystemTrusted(_:host:)` builds an SSL policy for the exact host (`SecPolicyCreateSSL(true, host as CFString)`), applies it with `SecTrustSetPolicies`, and returns `SecTrustEvaluateWithError(trust, nil)` (:268-272). If the chain validates against the public CA store and passes hostname validation, the delegate calls `.performDefaultHandling` — normal CA validation, no pin — so a Let's Encrypt/Cloudflare renewal never false-trips. It also actively clears any previously stored pin for that host (`if secureStore.trustedFingerprint(for: host) != nil { secureStore.clearTrustedFingerprint(for: host) }`, :123-125), so a host migrating from self-signed to a real CA does not keep enforcing a dead fingerprint. This is the normal path for the documented deployment (Cloudflare Tunnel, public CA cert), which means in that deployment the pinning machinery below is never exercised.

> ios-swiftUI/Tday/Core/Network/NetworkConfiguration.swift:117-128 and :268-272

### Local/private-host exemption from pinning and from HTTPS

`On by default`

`isLocalAddress(host:)` (:274-281) short-circuits the TLS delegate to `.performDefaultHandling` for exactly six patterns: `host == "localhost"`, `host == "127.0.0.1"`, `host == "10.0.2.2"` (Android-emulator loopback alias, inert on iOS), `host.hasPrefix("192.168.")`, `host.hasPrefix("10.")`, `host.hasSuffix(".local")`. The same predicate gates `isSecureTransportRequired(for:)` (:248-253), which is what permits plain HTTP for those hosts and forces HTTPS everywhere else. An identical copy of the predicate exists in ServerConfigRepository.swift:250-257 and TdayWidget/TodayTasksWidget.swift:199-206. Honest limits, confirmed by reading: this is prefix/suffix string matching, not CIDR parsing — a DNS name literally beginning `10.` (e.g. `10.attacker.example`) matches the exemption and would be allowed over plaintext HTTP with no pinning; and the 172.16.0.0/12 private range is NOT covered, so a 172.16.x.x LAN server is treated as remote and must use HTTPS + pinning.

> ios-swiftUI/Tday/Core/Network/NetworkConfiguration.swift:111-115, :248-253, :274-281; ios-swiftUI/Tday/Core/Data/Server/ServerConfigRepository.swift:250-257

### Two-phase, one-shot, fingerprint-bound certificate enrollment

`On by default`

Nothing is ever pinned without an on-screen user confirmation of that exact fingerprint. Phase 1: an unrecognised cert is refused with `.cancelAuthenticationChallenge` and recorded in `trustUnknownHosts[host] = fingerprint` (NetworkConfiguration.swift:152-154, :203-207); ServerConfigRepository.probe translates the resulting bare `URLError.cancelled` into `ServerProbeError.untrustedCertificate(host:fingerprint:)` (:219-224); AppViewModel stores it as `PendingCertificateApproval` (AppViewModel.swift:414-421) and the onboarding overlay shows an alert titled "Trust this server?" with the fingerprint in 8-char groups (`displayFingerprint`, AppViewModel.swift:44-50) and the trust button marked `role: .destructive` (OnboardingWizardOverlay.swift:161-182). Phase 2: on confirm, `approveServerCertificate` (AppViewModel.swift:431-453) calls `ServerConfigRepository.approveCertificate` (:233-248), which calls `allowTrustEnrollment(host:expecting:)` to store the approved fingerprint in `enrollmentExpectations` and then re-probes; the delegate consumes it via `removeValue(forKey:)` (:234-238) so the approval is ONE-SHOT, and `decideTrust` only enrolls on exact equality — a certificate swapped between the prompt and the retry returns `.rejectUnknown`. On any failure `cancelTrustEnrollment(host:)` clears the expectation (ServerConfigRepository.swift:245). All host keys are lowercased on write and read. State is guarded by an `NSLock` (`trustFailureLock`, :15) because it is touched from the URLSession delegate queue and from async callers.

> ios-swiftUI/Tday/Core/Network/NetworkConfiguration.swift:218-238, :152-154, :203-207; ios-swiftUI/Tday/Core/Data/Server/ServerConfigRepository.swift:219-224, :233-248; ios-swiftUI/Tday/Feature/App/AppViewModel.swift:414-421, :431-453; ios-swiftUI/Tday/Feature/Onboarding/OnboardingWizardOverlay.swift:161-182

### Certificate-change detection and user-facing recovery path

`On by default`

A pinned host presenting a different cert yields `.rejectMismatch`, which records the host in `trustFailureHosts` and cancels the challenge (NetworkConfiguration.swift:149-151, :189-193). Because a cancelled TLS challenge cannot carry a typed error, `consumeTrustFailure(host:)` (:197-201) is the out-of-band channel: ServerConfigRepository.probe rethrows it as `ServerProbeError.certificateChanged` (:216-218); the user sees "This server's certificate changed. Reset saved server trust only if you recognize this server." (AppViewModel.swift:1037-1038); the `canResetServerTrust` flag is passed into the setup UI as `serverCanResetTrust:` to gate a reset affordance (AppRootView.swift:138). Recovery is `resetTrustedServer(rawURL:)` (ServerConfigRepository.swift:135-148) — it clears the stored pin and re-probes, but does NOT mean 'trust whatever answers next': the re-probe goes through the same fail-closed path, so an unrecognised cert comes back as `.untrustedCertificate` and the user must confirm the fingerprint again. `recheckVersion()` (:109-128) also consumes both trust records and sets `trustFailed: true` so a refused cert is not silently reported as `.compatible`/'server unreachable'. NOTE, verified: `AppViewModel.recheckVersion()` (:459-475) reads `result.versionCheck` and `result.backendVersion` but never reads `result.trustFailed`, so on this path the flag is computed and discarded — the repository-level signal exists; no UI currently consumes it.

> ios-swiftUI/Tday/Core/Data/Server/ServerConfigRepository.swift:109-128, :135-148, :207-227; ios-swiftUI/Tday/Core/Network/NetworkConfiguration.swift:189-201; ios-swiftUI/Tday/Feature/App/AppViewModel.swift:459-475, :477-492, :1037-1038; ios-swiftUI/Tday/Feature/App/AppRootView.swift:138

### HTTPS enforcement for remote servers (two layers)

`On by default`

Layer 1 — URL normalization: `ServerConfigRepository.normalize` (:177-205) defaults a scheme-less input to `https://` (:183), rejects any scheme other than http/https (:189-191), and throws `ServerProbeError.insecureTransport` for `http` on a non-local host (:192-194). Layer 2 — per-request: `performRequestRaw` re-checks `if configuration.isSecureTransportRequired(for: url), url.scheme?.lowercased() != "https" { throw APIError(message: "HTTPS is required for remote servers", statusCode: nil) }` before the request is issued (TdayAPIService.swift:714-716), so a stale stored URL or a rewritten absolute path cannot downgrade. The WebSocket URL derives its scheme from the base URL: `components.scheme = components.scheme == "http" ? "ws" : "wss"` (RealtimeClient.swift:151). Caveat worth recording: layer 1's `normalize` is only used by ServerConfigRepository; `SecureStore.normalizeServerURL` (:258-271) performs no scheme check at all, but every save path that reaches the network goes through the repository.

> ios-swiftUI/Tday/Core/Data/Server/ServerConfigRepository.swift:177-205; ios-swiftUI/Tday/Core/Network/TdayAPIService.swift:714-716; ios-swiftUI/Tday/Core/Network/NetworkConfiguration.swift:248-253; ios-swiftUI/Tday/Core/Network/RealtimeClient.swift:146-155

### App Transport Security configuration (main app + widget)

`On by default`

Main app Info.plist declares `NSAppTransportSecurity` with `NSAllowsArbitraryLoads = false` and `NSAllowsLocalNetworking = true` — no global ATS opt-out, so the OS enforces TLS 1.2+ with forward secrecy for public hosts, and the only relaxation is for local/link-local names (which is what lets a LAN/localhost server run plain HTTP). There are no `NSExceptionDomains`, no `NSExceptionAllowsInsecureHTTPLoads`, and no `NSAllowsArbitraryLoadsInWebContent` — verified by reading the full dict, which is only the two keys. The widget extension's Info.plist repeats the identical two-key dict at :23-29.

> ios-swiftUI/Tday/Info.plist:49-55; ios-swiftUI/TdayWidget/Info.plist:23-29

### Keychain-backed secret storage (SecureStore)

`On by default`

Service name `com.ohmz.tday.ios.secure-store` (:15), item class `kSecClassGenericPassword`, one item per logical key via `kSecAttrAccount` (:401-407). Stored in the Keychain (`enum Key`, :22-32, plus per-host trust keys): persisted server URL, device ID (a random lowercased UUID sent as `X-Tday-Device-Id`), last username, the serialized auth session cookie, the cached session user blob, saved server-URL suggestion, the app data mode, the pending-approval username AND password, and every per-host TLS pin (`fingerprint.<host>`, :312-314). Accessibility class is `kSecAttrAccessibleAfterFirstUnlock`, set on both the update attributes (:355-358) and the insert query (:363) but deliberately NOT on the search query (:401-407), which would otherwise fail to match older items — chosen so background contexts (CarPlay intents, widgets, App Intents, background refresh) can read the session on a locked-but-once-unlocked device. Honest trade-off stated in the code comment (:351-354): `AfterFirstUnlock` is weaker than `WhenUnlocked`/`WhenUnlockedThisDeviceOnly` — items are readable whenever the device has been unlocked since boot, and because the class is not `...ThisDeviceOnly` they are eligible for encrypted device backup and restore onto another device. `kSecAttrSynchronizable` is never set anywhere in the repo (grep: zero hits), so items do not go to iCloud Keychain. No `kSecAttrAccessGroup` is declared and no `keychain-access-groups` entitlement exists in any target's .entitlements (grep: zero hits), so the widget and share extensions cannot read these items at all — which is precisely why the App Group hand-off below exists.

> ios-swiftUI/Tday/Core/Data/SecureStore.swift:14-20 (service), :22-32 (keys), :312-314 (trust key shape), :349-373 (write + kSecAttrAccessibleAfterFirstUnlock + error logging), :401-407 (query shape); grep for kSecAttrAccessGroup / kSecAttrSynchronizable / keychain-access-groups across Tday, TdayWidget, TdayShareExtension, TdayWatch returned nothing

### Widget App Group session hand-off (no session of its own)

`On by default`

The widget extension has no login, no Keychain access group, and no cache of credentials. For instant check-off sync the app writes `widget-backend-session.json` into the App Group container `group.com.ohmz.tday` containing `{baseURL, cookieHeader, pinnedFingerprint}` (Payload, :474-489). Protections verified on that file: written with `options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication]` (:527) — encrypted at rest, readable after first unlock, mirroring the Keychain's AfterFirstUnlock semantics — and marked `resourceValues.isExcludedFromBackup = true` (:533) so the session token is not carried into an unencrypted Finder/iTunes backup. It is written ONLY when a real session cookie is present: `guard cookies.contains(where: { authCookieNames.contains($0.name) }) else { clear(); return }` where `authCookieNames = ["authjs.session-token", "__Secure-authjs.session-token"]` (:466-469, :511-514) — a header carrying only csrf/callback-url cookies triggers `clear()` instead, so a stale session-less file cannot linger. Refreshed after each successful login (AuthRepository.swift:161) and each successful sync (SyncManager.swift:164), and deleted on session teardown and sign-out (AuthRepository.swift:309 in `clearSessionOnly`, :323 in `clearAllLocalUserDataForUnauthenticatedState`). The widget's read path (`WidgetBackendSession.load()`, TodayTasksWidget.swift:134-144) is a deliberate duplicate of the writer's shape.

> ios-swiftUI/Tday/Core/Widget/TodayTasksWidgetSnapshotStore.swift:460-545 (esp. :511-514 session-cookie gate, :527 file protection, :532-535 backup exclusion); ios-swiftUI/TdayWidget/TodayTasksWidget.swift:109-144; ios-swiftUI/Tday/Core/Data/Auth/AuthRepository.swift:161, :309, :323; ios-swiftUI/Tday/Core/Data/Sync/SyncManager.swift:164

### Widget-side TLS pinning is fail-closed and never enrolls

`On by default`

`WidgetPinnedTrustDelegate` (TodayTasksWidget.swift:156-231) mirrors the app's delegate: non-server-trust challenges and local hosts -> `.performDefaultHandling`; chains passing `SecPolicyCreateSSL(true, host)` + `SecTrustEvaluateWithError` -> `.performDefaultHandling` (:181-188); everything else must satisfy `guard let pinnedFingerprint, let fingerprint = Self.fingerprintForTrust(trust), fingerprint == pinnedFingerprint` or it returns `.cancelAuthenticationChallenge` (:191-196). Unlike the app it can NEVER pin on first use — there is no enrollment path anywhere in the widget process — so a widget tap can only reach a server the app already verified. Its `fingerprintForTrust`/`leafCertificateHash` (:210-231) are byte-identical in algorithm to the app's (SecTrustCopyKey -> SHA-256 -> base64, same DER fallback) so a pin the app stored actually matches. The completion call uses `URLSessionConfiguration.ephemeral` with `timeoutIntervalForRequest = 6` and `timeoutIntervalForResource = 6`, `waitsForConnectivity = false`, and every error is swallowed via `_ = try? await urlSession.data(for: request)` (:295-312) — the App Group pending-completion queue is the durable fallback.

> ios-swiftUI/TdayWidget/TodayTasksWidget.swift:156-231 (delegate), :285-312 (session construction and timeouts)

### Widget requests carry the client/version gate headers

`On by default`

The widget's direct `PATCH /api/todo/complete` and `PATCH /api/floater/complete` set `X-Tday-Client: ios` (:363), `X-Tday-App-Version` from the extension's `CFBundleShortVersionString` (:364-367; the widget target's MARKETING_VERSION is 0.7.0, identical to the app's, project.yml:45 and :69), and `X-User-Timezone` (:368). Without these the backend's mobile version gate (426/409 for incompatible builds) would skip the request, letting the widget write to a server the app itself is fenced off from. Accuracy note from reading the code: the widget does NOT send `X-Tday-Device-Id`, which the app's `NetworkConfiguration.defaultHeaders` always includes (:86) — so the header set is a close match for the version gate, not an exact match for per-device identification.

> ios-swiftUI/TdayWidget/TodayTasksWidget.swift:360-369; ios-swiftUI/Tday/Core/Network/NetworkConfiguration.swift:78-93; ios-swiftUI/project.yml:45, :69

### Session cookie persistence and lifecycle (CookieStore)

`On by default`

Only two cookie names are treated as authenticating: `authjs.session-token` and `__Secure-authjs.session-token` (:19-22). The chosen cookie is persisted to the Keychain as a `PersistedAuthCookie` struct carrying name/value/domain/path/expiresDate/isSecure/isHTTPOnly/originURL (:4-13, :88-105) and restored on launch only if not expired (:68-86). `syncPersistedAuthCookie()` (:45-52) first calls `removeExpiredAuthCookies()` (:132-136), which deletes expired auth cookies from `HTTPCookieStorage`, then clears the Keychain copy when no live cookie remains; it runs after every HTTP response (TdayAPIService.swift:750). Cookie selection ranks by `authCookiePriority` — +2 for not-expired, +1 for a `__Secure-` prefix (:146-155). `URLSessionConfiguration` for both the main and probe sessions sets `requestCachePolicy = .reloadIgnoringLocalAndRemoteCacheData` and `urlCache = nil` (NetworkConfiguration.swift:29-30, :41-42), so no authenticated response body is written to a URL cache on disk; every request also sends `Cache-Control: no-store` and `Pragma: no-cache` (NetworkConfiguration.swift:81-82) and sets `request.cachePolicy` per-request (TdayAPIService.swift:719).

> ios-swiftUI/Tday/Core/Network/CookieStore.swift:19-22, :45-52, :68-105, :132-166; ios-swiftUI/Tday/Core/Network/NetworkConfiguration.swift:25-47, :78-93; ios-swiftUI/Tday/Core/Network/TdayAPIService.swift:719, :750

### Reinstall-scoped credential wipe (install sentinel)

`On by default`

iOS Keychain items survive app deletion. On every launch `clearInstallScopedValuesIfAppReinstalled()` (:239-256) checks a UserDefaults-only sentinel (`app.install.sentinel`, which does NOT survive deletion); if missing, it purges the persisted server URL, app data mode, persisted auth cookie, cached session user, pending-approval credentials, last username, ALL trusted fingerprints, the runtime URL, and list icons, then writes a new sentinel UUID (:254). `AppContainer.init` captures the boolean result and passes it into `CookieStore(clearAuthCookiesBeforeRestore:)` (AppContainer.swift:44, :49-52), and CookieStore's init calls `clearAuthCookies()` before attempting any restore (CookieStore.swift:32-34), so a leftover `HTTPCookieStorage` auth cookie is dropped too.

> ios-swiftUI/Tday/Core/Data/SecureStore.swift:239-256; ios-swiftUI/Tday/Core/Data/AppContainer.swift:44, :49-52; ios-swiftUI/Tday/Core/Network/CookieStore.swift:24-39

### Pin lifecycle on sign-out / server change

`On by default`

`clearAllTrustedFingerprints()` (:166-172) iterates the `secure.trusted.hosts` index, deletes each `fingerprint.<host>` Keychain item, and removes the index key. It is called from `clearAllUserValues(preservingServerURL:)` (:183) — which runs unconditionally even when the server URL is preserved — from `clearInstallScopedValuesIfAppReinstalled()` (:251), and from `ServerConfigRepository.clearServerConfiguration()` (:161). Verified the sign-out chain end to end: `AuthRepository.logout()` (:281-284) -> `clearAllLocalUserDataForUnauthenticatedState(preservingServerConfiguration: true)` (:317-330) -> `secureStore.clearAllUserValues(preservingServerURL: true)` -> pins cleared. `enableLocalMode()` deliberately does NOT clear pins (ServerConfigRepository.swift:164-171) — it clears the server URL, cached session user, last username, and persisted auth cookie only.

> ios-swiftUI/Tday/Core/Data/SecureStore.swift:157-186; ios-swiftUI/Tday/Core/Data/Server/ServerConfigRepository.swift:157-171; ios-swiftUI/Tday/Core/Data/Auth/AuthRepository.swift:281-284, :317-330

### Server identity check before adopting a URL

`On by default`

Every URL save path — `saveServerURL(rawURL:)` (:74-84), `probeAndSave` (:91-107), `resetTrustedServer` (:135-148) and `approveCertificate` (:233-248) — probes `GET /api/mobile/probe` and refuses unless `response.service.compare("tday", options: .caseInsensitive) == .orderedSame && response.version == "1"`, throwing `ServerProbeError.notTdayServer`. The probe runs on a separate `URLSessionConfiguration.ephemeral` `probeSession` with `timeoutIntervalForRequest = 5` / `timeoutIntervalForResource = 8` (vs 30/60 for the main session), and `normalize` strips query and fragment from the URL before use (:198-199).

> ios-swiftUI/Tday/Core/Data/Server/ServerConfigRepository.swift:74-107, :135-148, :198-199, :233-248; ios-swiftUI/Tday/Core/Network/NetworkConfiguration.swift:37-47

### Mutation-safe transport retry policy

`On by default`

The one automatic transport retry (250 ms delay via `Task.sleep(nanoseconds: 250_000_000)`, `maxTransportAttempts = isIdempotent ? 2 : 1`) is restricted to idempotent methods only — `let isIdempotent = upperMethod == "GET" || upperMethod == "HEAD"` (:739) — so a POST/PATCH/DELETE is never silently re-sent and cannot double-apply. The retry guard also re-checks `isIdempotent` in the catch clause (:776-780). Retriable codes are an explicit enumeration in `isRetriableTransportError`: `.networkConnectionLost, .timedOut, .cannotConnectToHost, .cannotFindHost, .dnsLookupFailed` (:805-813); `.notConnectedToInternet` is deliberately excluded per the comment at :801-804. Because the pinning delegate's refusal surfaces as `URLError.cancelled`, which is not in that set, a refused certificate is never retried into acceptance.

> ios-swiftUI/Tday/Core/Network/TdayAPIService.swift:737-740, :776-790, :801-813

### System password AutoFill instead of app-managed credential storage

`On by default`

Login credentials are offered to / read from the OS password manager via `ASAuthorizationPasswordProvider` + `ASAuthorizationController` (SystemCredentialService.swift:211-224) and `SecAddSharedWebCredential` (:155-160), scoped to the constant `SystemCredentialScope.appCredentialHost = "tday.ohmz.cloud"` (:40-42), backed by the `com.apple.developer.associated-domains -> webcredentials:tday.ohmz.cloud` entitlement. The app therefore does not roll its own password vault for the normal login path. Note the scope is a fixed developer-owned domain, so AutoFill association does not follow a self-hoster's own hostname.

> ios-swiftUI/Tday/Core/Data/Auth/SystemCredentialService.swift:40-42, :127, :155-160, :211-224; ios-swiftUI/Tday/Tday.entitlements (com.apple.developer.associated-domains -> webcredentials:tday.ohmz.cloud)

### Keychain write failures are surfaced, not swallowed

`On by default`

`saveData(_:forRawKey:)` checks the `SecItemUpdate` status, falls through to `SecItemAdd` on `errSecItemNotFound`, and logs an `os.Logger` error (subsystem `com.ohmz.tday.ios`, category `SecureStore`) naming the key with `privacy: .public` and the OSStatus when either fails — the value itself is never logged (:359-372). The stated rationale (:366-367) is that a dropped session write would otherwise leave the user looking logged in now and silently signed out next launch with no diagnostic. Scope note: this covers write failures only; `loadData(forRawKey:)` (:382-394) returns nil on any non-success status without logging.

> ios-swiftUI/Tday/Core/Data/SecureStore.swift:6, :349-373, :382-394

### Custom URL scheme surface is a closed, navigation-only allowlist

`On by default`

The app registers one URL scheme, `tday` (Info.plist:34-44), and any incoming URL is funnelled through `AppRoute.from(url:)` before anything happens (AppRootView.swift:255-256, :619-624; AppViewModel.swift:706-711). That parser hard-gates on `url.scheme?.lowercased() == "tday"` (AppRoute.swift:71-73) and then switches over a fixed set of first path components (home, completed, calendar, settings, latest-release, help-guide, morning-sweep, forgot-password, todos, ...), returning nil for anything unrecognised — the handler's `guard let route ... else { return }` drops it silently. Every route resolves to a navigation destination; no branch performs a mutation, changes the server URL, writes a credential, or affects trust state. Consequence: any other app on the device can open a T'Day screen but cannot drive a state change through this surface.

> ios-swiftUI/Tday/Core/Navigation/AppRoute.swift:70-115+; ios-swiftUI/Tday/Info.plist:34-44; ios-swiftUI/Tday/Feature/App/AppViewModel.swift:706-711; ios-swiftUI/Tday/Feature/App/AppRootView.swift:255-256, :619-624

### Telemetry scrubbing (Sentry)

`Needs config`

Sentry is OFF unless a DSN is supplied — `SentryConfiguration.start()` reads `TdayTelemetry.bundleString("SENTRY_DSN")` and returns immediately when empty (:6-7); `bundleString` also treats an unsubstituted `$(...)` placeholder as empty (:49-54). The DSN ships blank in BOTH build definitions: project.yml:46 (`SENTRY_DSN: ""`) and, more importantly, the committed pbxproj that actually builds here — `SENTRY_DSN = ""` at TdayApp.xcodeproj/project.pbxproj:1468 and :1498. So the default build sends nothing. When enabled: `sendDefaultPii = false` (:19), `beforeSend` nulls `event.user?.ipAddress` (:26-29), environment defaults to `production` (:11), traces sample rate falls back to 0.2 in production / 1.0 otherwise but is overridable via the `SENTRY_TRACES_SAMPLE_RATE` Info.plist value (:21-24; pbxproj ships 0.2 release / 1.0 debug). API breadcrumbs never carry request bodies — only method, `TdayTelemetry.sanitizePath(url.path)` and status code (TdayAPIService.swift:755-766, :781-789, :793-798). The sanitizer allow-lists 49 static path segments and redacts anything else (SentryConfiguration.swift:38-47, :61-75), with regex denylists for URL-ish/credential-ish labels (`https?://`, `wss?://`, an email pattern, `bearer`, `token=`, `password=`, `session=`, `cookie=`, `csrf`) and for data keys (`authorization|cookie|csrf|token|password|session|secret|email|username|body|payload|header`) at :35-36.

> ios-swiftUI/Tday/Core/SentryConfiguration.swift:5-32, :34-47, :49-59, :61-75; ios-swiftUI/Tday/Core/Network/TdayAPIService.swift:755-766, :781-789; ios-swiftUI/project.yml:46; ios-swiftUI/TdayApp.xcodeproj/project.pbxproj:1468, :1498

### Probe compatibility payload decryption (AES-GCM)

`Needs config`

The `/api/mobile/probe` response's `encryptedCompatibility` field is decrypted client-side with AES-GCM (CryptoKit `AES.GCM.SealedBox(combined:)` / `AES.GCM.open`), `ivLength = 12`, requiring a 32-byte key (`keyData.count == 32`) read base64url-decoded from the `TdayProbeEncryptionKey` Info.plist entry (ProbeDecryptor.swift:11, :14-25, :29-30). That entry is the build variable `$(TDAY_PROBE_ENCRYPTION_KEY)` (Tday/Info.plist:94-95), and I confirmed the variable is defined NOWHERE — not in project.yml and not in the committed TdayApp.xcodeproj/project.pbxproj. `ProbeDecryptor` explicitly bails on an empty value or one starting with `$(` (:15-16), and returns nil on any decrypt/decode failure rather than throwing (:32-34). Consequence in the shipped build: the placeholder survives, decryption never runs, and version-compatibility silently falls back to the plaintext `response.appVersion` (`compatibility?.appVersion ?? response.appVersion`, ServerConfigRepository.swift:105, :126). The feature is inert unless the operator adds the build setting themselves. This is an integrity/authenticity aid for the version gate, not a transport control.

> ios-swiftUI/Tday/Core/Security/ProbeDecryptor.swift:11-35; ios-swiftUI/Tday/Info.plist:94-95; grep for TDAY_PROBE_ENCRYPTION_KEY over project.yml and TdayApp.xcodeproj/project.pbxproj returned zero hits; ios-swiftUI/Tday/Core/Data/Server/ServerConfigRepository.swift:100-106, :123-127

### UserDefaults vs Keychain split (what is NOT protected)

`Partial`

Deliberately in plain `UserDefaults.standard`, not the Keychain: the RUNTIME server URL (`runtime.server.url`, written at :201), the index of pinned hosts (`secure.trusted.hosts` — only the host LIST; each fingerprint VALUE goes to the Keychain via `saveString(fingerprint, forRawKey: trustKey(for: host))` at :149-154), per-list icon choices (`list.icons`, :294-303), and the install sentinel (`app.install.sentinel`, :254). No credential, cookie, or fingerprint value is in UserDefaults. Practical consequence: an attacker with filesystem access learns which server you use and which hosts you pinned, but not the pinned values or the session.

> ios-swiftUI/Tday/Core/Data/SecureStore.swift:9-12 (raw defaults keys), :145-155 (fingerprint value -> Keychain, host list -> defaults), :196-206 (runtime URL -> defaults), :294-303 (list icons)

### Local Mode (offline-only) storage

`Partial`

Local Mode is a persisted `AppDataMode` value under the Keychain key `app-data-mode-v1` (SecureStore.swift:29, :57-75); `appDataMode()` infers `.unset` only when neither a runtime nor a persisted server URL exists (:62). Entering it (`enableLocalMode`, ServerConfigRepository.swift:164-171) clears the server URL, cached session user, last username and persisted auth cookie so no server credential remains. Task data in BOTH modes lives in the same SwiftData store, constructed as `try! ModelContainer(for: CachedTodoEntity.self, ... SyncMetadataEntity.self)` with NO `ModelConfiguration` argument (AppContainer.swift:59-68) — so no explicit file-protection class, no `allowsSave`/encryption customization; it inherits iOS's default app-container data protection and is included in device backups. In Local Mode `saveOfflineState` zeroes `lastSuccessfulSyncEpochMs` and `lastSyncAttemptEpochMs` and sets `pendingMutations = []` (OfflineCacheManager.swift:166-176), so nothing queues for a server that isn't there. Honest gap: task titles/notes at rest have only the platform default, not an app-level encryption layer.

> ios-swiftUI/Tday/Core/Data/SecureStore.swift:57-75; ios-swiftUI/Tday/Core/Data/AppContainer.swift:59-68; ios-swiftUI/Tday/Core/Data/Cache/OfflineCacheManager.swift:166-212; ios-swiftUI/Tday/Core/Data/Server/ServerConfigRepository.swift:164-171

### Not present — ios client & widget security

- **Pending-approval password is stored in the Keychain in cleartext** — When registration/login lands in the PENDING (awaiting-admin-approval) state, the app stores BOTH the username and the raw password under Keychain keys `pending-approval-username` / `pending-approval-password` so the holding screen survives relaunch and can silently re-attempt login until an admin approves. It inherits `kSecAttrAccessibleAfterFirstUnlock` like everything else in SecureStore, and is cleared on approval (AppViewModel.swift:259, :328), on sign-out/full wipe (SecureStore.clearAllUserValues :181), and on reinstall (:249). Practical consequence for a single self-hosted user: between registration and admin approval, the account password sits at rest on the device readable whenever the device has been unlocked since boot, and — because the item is not `...ThisDeviceOnly` — is eligible for encrypted device backup/restore onto another device. Verified no other flow stores a password: the only writers of `.pendingApprovalPassword` are savePendingApproval and its two call sites.
- **No app-level lock: no biometric / passcode gate and no jailbreak detection** — There is no Face ID / Touch ID gate on app launch, on the Settings screen, on data export, or on the password-change flow, and no device-integrity check. Consequence: anyone who can unlock the phone has full access to the signed-in T'Day session and can export all data; the device passcode is the only boundary. This is a defensible choice for a personal task app — recorded only so the comparison is accurate.
- **Widget snapshot (task titles and notes) is stored unencrypted in App Group UserDefaults** — Unlike the session file — which is written to a `.completeFileProtectionUntilFirstUserAuthentication` file precisely because "UserDefaults ... is unencrypted on disk" (the code says so at TodayTasksWidgetSnapshotStore.swift:455-457) — the widget CONTENT snapshots (`tday.widget.todayTasksSnapshot` :121, `tday.widget.floaterTasksSnapshot` :331) and the pending-completion queue (`tday.widget.pendingCompletions` :425) are JSON blobs written via `store.set(data, forKey: snapshotKey)` to EVERY store returned by `defaultsStores()` — i.e. `UserDefaults(suiteName: "group.com.ohmz.tday")` AND `UserDefaults.standard` (:194-201, :222-228; :389, :408 for floaters). Those payloads include task titles, descriptions/notes, pinned flags, due/instance timestamps and canonical task IDs. Consequence: task content is readable from the App Group plist with only the platform default protection, and it lands in device backups. The credential is protected; the content is not.
- **No certificate/key rotation window — pinning holds exactly one fingerprint per host** — `SecureStore` keeps a single `fingerprint.<host>` string per host (get/save/clear at :138-164), and `decideTrust` accepts only exact string equality (`storedPin == fingerprint`, NetworkConfiguration.swift:180-182). There is no backup-pin list, no pin expiry, and no pin-set semantics. Consequence: for a self-signed / privately-issued deployment, rotating the server key immediately breaks every client until each user manually runs 'reset saved server trust' and re-confirms the new fingerprint by eye. (Public-CA deployments — including the documented Cloudflare Tunnel setup — are unaffected: that branch clears the pin and uses standard CA validation.) Secondary inconsistency confirmed: `SecureStore.serverTrustKey(for:)` (:280-288) builds a host:port key, but the TLS delegate and `NetworkConfiguration.trustedFingerprint(for:)` key on `url.host` alone (:258-263), so a pin is not port-scoped on the path that actually enforces it.
- **Fingerprint verification depends on the user comparing a base64 SHA-256 out of band** — The enrollment alert renders the fingerprint in 8-character groups and warns "If you're on public Wi-Fi and weren't expecting this, cancel", with the trust button styled `role: .destructive` — but the app provides no in-product way to obtain the expected value: there is no QR pairing, no pairing code, no comparison against a value fetched over a second channel. Consequence: the security of first enrollment rests entirely on the user manually reading the server's certificate fingerprint elsewhere and comparing 44 base64 characters. The control correctly refuses to auto-trust; it cannot verify for the user.
- **Hardcoded outbound call to api.github.com on every bootstrap, on an unpinned shared session** — CORRECTION to a prior claim that no hardcoded endpoints exist. `refreshGitHubReleases()` hits two hardcoded third-party URLs — `https://api.github.com/repos/ohmzi/Tday/releases/latest` and `https://api.github.com/repos/ohmzi/Tday/releases/tags/v{version}` — via `URLSession.shared`, i.e. NOT `configuration.session`, so it bypasses the app's TLS delegate entirely (public-CA validation still applies via ATS, but none of the app's own trust logic does) and bypasses `isSecureTransportRequired`. It is called unconditionally from `refreshVersionInfo()`, which runs during bootstrap and again from the manual update check, and it runs even in Local Mode (the `isLocalMode` guard only skips `recheckVersion`, not `refreshGitHubReleases`). Consequence for a self-hoster: the app contacts github.com on launch regardless of Local Mode, with no setting to disable it, revealing the device's IP and app version to a third party the user did not configure. Not a hardcoded backend URL — there is genuinely none (`currentBaseURL()` throws "Server URL is not configured" when nothing is stored) — but the original 'no hardcoded endpoints' statement was false as written.
- **No hardcoded backend URL or embedded secret — confirmed absent** — Recorded as a confirmed non-finding because it was asked for explicitly, and narrowed after the correction above. There is no baked-in backend URL: `NetworkConfiguration.currentBaseURL()` throws `APIError("Server URL is not configured")` when nothing is stored (:55-63), and every base URL originates from user input via `normalize`/`normalizeServerURL`. `SENTRY_DSN` is `""` in both project.yml:46 and the committed pbxproj (:1468, :1498); `TdayProbeEncryptionKey` is an undefined build variable and `TdayUpdateURL` is an empty string (Info.plist:94-97). The only hardcoded host in the app is `tday.ohmz.cloud`, which appears solely as the AutoFill/associated-domain scope (`webcredentials:`) — the developer's own domain, never used as a base URL or fallback. The App Group identifier `group.com.ohmz.tday` is hardcoded in four places but is an entitlement scope, not a secret.
- **Sibling extensions (Share, Watch, Watch complication) declare no ATS dictionary** — `TdayShareExtension/Info.plist`, `TdayWatch/Info.plist` and `TdayWatchWidget/Info.plist` contain no `NSAppTransportSecurity` key at all — only the main app (Info.plist:49-55) and the widget extension (TdayWidget/Info.plist:23-29) declare one, so ATS posture is declared explicitly in only 2 of 5 targets. They inherit the platform ATS default (arbitrary loads still off), so this is not an opening. Severity note added after checking: it is effectively inert, because none of the three targets performs any networking — `grep -rn "URLSession|http"` over TdayWatch and TdayWatchWidget returns nothing, and TdayShareExtension/ShareViewController.swift touches only `UserDefaults(suiteName: "group.com.ohmz.tday")`. Recorded for inventory completeness rather than as a live risk.

---

## Web SPA security

### Session token in an HttpOnly cookie, never in JS-readable storage

`On by default`

The session JWT is delivered only as a cookie built with httpOnly=true, path="/", SameSite=Lax, and secure = (cookieName.startsWith("__Secure-") || config.isProduction) — SessionCookies.kt:83-97 (buildSessionCookie). Name selection at :18-19: `__Secure-authjs.session-token` in production, `authjs.session-token` otherwise; issueSessionCookie also expires the inactive name so the two cannot coexist (:30-35). The SPA never sees, stores, or attaches a token: every request goes through one client that sets `credentials: "same-origin"` and no Authorization header (api-client.ts:66-74). Verified by grep: `Authorization` appears ZERO times in tday-web/src, and the only "bearer" hit is inside a Sentry redaction regex (sentry.ts:70). A repo-wide grep for localStorage/sessionStorage/indexedDB in tday-web/src returns no session or bearer token — the only auth-adjacent value AuthProvider keeps is a plain user profile object in React state (AuthProvider.tsx:66, type AuthUser at :42-51). Why it matters: an XSS in this SPA cannot read or exfiltrate the session cookie, because JavaScript has no API that returns an HttpOnly cookie. The attacker is reduced to riding the session in-page (same-origin fetch) rather than stealing a credential usable from elsewhere.

> backend:security/SessionCookies.kt:18-19,83-97; tday-web/src/lib/api-client.ts:66-74; tday-web/src/providers/AuthProvider.tsx:42-51,66

### Content-Security-Policy over the SPA (recently hardened)

`On by default`

Server-emitted via Ktor DefaultHeaders (SecurityHeaders.kt:100-113), installed unconditionally at Application.kt:85. Policy built at SecurityHeaders.kt:57-84: `default-src 'self'; base-uri 'self'; object-src 'none'; frame-src 'none'; frame-ancestors 'none'; form-action 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; media-src 'self'; manifest-src 'self'; worker-src 'self'; connect-src 'self' ws: wss: https://raw.githubusercontent.com https://api.github.com [+ extras]`. Mode is ENFORCING by default — parseCspMode(null) returns CspMode.enforce (:12-17), so an operator who sets nothing gets a real `Content-Security-Policy` header, not report-only; CSP_MODE downgrades it to report-only or off (AppConfig.kt:165). CORRECTION to the first pass: CSP_CONNECT_EXTRA does not ADD to the auto-derived Sentry origin, it REPLACES it — SecurityHeaders.kt:89-91 reads `config.cspConnectExtra.ifEmpty { listOfNotNull(parseSentryIngestOrigin(config.sentryDsn)) }`, so an operator who sets CSP_CONNECT_EXTRA must re-list the Sentry ingest host themselves. What it buys in a future XSS: `script-src 'self'` with no 'unsafe-inline'/'unsafe-eval' means an injected inline `<script>` or `on*=` handler does not execute and `<script src=//evil>` is blocked; `connect-src` limits exfiltration to same-origin, GitHub's two release hosts, and the Sentry ingest host; `object-src 'none'`/`frame-src 'none'` kill plugin and iframe payloads; `base-uri 'self'` blocks base-tag hijacking; `form-action 'self'` blocks credential-harvest posts. Honest weak point, documented in the source comment at :43-51: `style-src 'unsafe-inline'` is required because sonner, vaul, react-style-singleton (every Radix overlay) and next-themes inject inline <style> at runtime, so CSS-based data exfiltration/UI-redress remains possible. index.html ships zero inline script — the only script tag is `<script type="module" src="/src/main.tsx">` at tday-web/index.html:19 — so `script-src 'self'` costs nothing except next-themes' anti-FOUC inline script (deliberate, noted at :48-51).

> backend:plugins/SecurityHeaders.kt:12-17,43-55,57-84,89-91,100-113; backend:Application.kt:85; tday-web/index.html:19

### Supporting response headers on every SPA response

`On by default`

Same DefaultHeaders block: `X-Content-Type-Options: nosniff` (SecurityHeaders.kt:101 — blocks MIME-sniffing a user-supplied file into script), `X-Frame-Options: DENY` (:102, belt-and-braces with frame-ancestors 'none' for older browsers), `Referrer-Policy: strict-origin-when-cross-origin` (:103, so task ids in the path never leak to third parties), and a NEW `Permissions-Policy: camera=(), microphone=(), geolocation=(), payment=(), usb=()` (:106). `Strict-Transport-Security: max-age=63072000; includeSubDomains; preload` is emitted only inside `if (config.isProduction)` (:110-112) — production-gated, not unconditional. All four of the first group are unconditional.

> backend:plugins/SecurityHeaders.kt:101-106,110-112

### XSS sink inventory — exactly two, both bounded

`On by default`

A grep of tday-web/src, index.html and public/ for dangerouslySetInnerHTML|innerHTML|outerHTML|document.write|eval(|new Function|insertAdjacentHTML|srcdoc|javascript: returns exactly TWO hits, both innerHTML-family; no eval, no new Function, no document.write, no insertAdjacentHTML, no srcdoc, no javascript: URI anywhere.\n(1) BlogArticlePage.tsx:100 — `<div dangerouslySetInnerHTML={{__html: html}} />`. `html` is the text body of a same-origin static file: the page fetches /content/blog/posts.json, matches the URL :slug against fixed `slug` values, redirects on a miss (:36-40), then fetches that entry's `contentFile` (:44-51). Both posts.json and the two HTML bodies are build-time assets committed under tday-web/public/content/blog/ (posts.json:11,22 point at RRULE_todo_scheduling_system.html and instanceDate_drifting_problem.html). No user-supplied string reaches this sink; the :slug param only selects among hardcoded entries.\n(2) NLPTitleInput.tsx:125 — `node.innerHTML = html` on the contentEditable task-title field. This one IS fed user-typed text, but every segment goes through escapeHtml() first (:78 for the no-date path, :97 for the highlighted path), and escapeHtml (:176-183) replaces &, <, >, \" and ' — a complete escape for text and attribute contexts. The only unescaped markup is one static `<span class=\"bg-nlp inline rounded-[2px]\">` wrapper. Typing `<img src=x onerror=alert(1)>` as a task title renders as literal text.\nSecondary note: i18next is initialised with `interpolation: { escapeValue: false }` (i18n.ts:82). Safe here only because translated strings render as JSX children (React escapes them) — no HTML sink consumes a translation. All 10 locale bundles are compiled into the JS build (i18n.ts:5-14,:39-45), so there is no runtime HTTP fetch of translation JSON to tamper with.

> tday-web/src/pages/BlogArticlePage.tsx:33-51,100; tday-web/src/components/todo/component/TodoForm/NLPTitleInput.tsx:78,97,125,176-183; tday-web/public/content/blog/posts.json:11,22; tday-web/src/i18n.ts:5-14,39-45,82

### Service worker cache scope and logout wipe

`On by default`

tday-web/src/sw.ts, injectManifest strategy (vite.config.ts:51-63). Three cache tiers: (a) Workbox precache of `**/*.{js,css,html,ico,png,svg,woff2}` (vite.config.ts:59) — static build output only, no .map files; (b) `tday-navigation`, NetworkFirst with networkTimeoutSeconds: 4 for the HTML shell (sw.ts:41-55); (c) `tday-api-cache`, NetworkFirst, networkTimeoutSeconds 4, ExpirationPlugin maxEntries 100 / maxAgeSeconds 3600, CacheableResponsePlugin statuses [0,200], matching /api/todo, /api/floater and /api/floater-list (sw.ts:73-88). Tier (c) is what matters on a shared machine: real task titles, descriptions and list contents sit in on-disk Cache Storage for up to an hour. The navigation route denylists /^\/api/, /^\/ws/ AND /^\/.well-known/ (sw.ts:59; vite.config.ts:61 lists the first two). /version.json is forced NetworkOnly (sw.ts:64). Clearing: on explicit logout, on any 401 from a non-/api/auth request, and on a 401 session probe after having been authenticated, the app calls clearClientUserData (AuthProvider.tsx:106-111,128-131,211-213), which enumerates and deletes every Cache Storage cache (clearClientUserData.ts:57-64) — so the API cache does not survive logout. The SW also honours a `CLEAR_CACHES` postMessage (sw.ts:26-30) used by the manual reset path.

> tday-web/src/sw.ts:41-55,59,64,73-88; tday-web/vite.config.ts:51-63; tday-web/src/lib/security/clearClientUserData.ts:57-64

### Client-side data wipe on logout and on session expiry

`On by default`

clearClientUserData.ts:18-80 performs, in order: unsubscribe the Web Push subscription (:24-32), remove `tday.push-enabled` (:35), `sessionStorage.clear()` (:41), remove every localStorage key except an explicit preserve list (:46-53), delete every Cache Storage cache (:57-64), then enumerate `indexedDB.databases()` and delete each one (:66-79). Every step is individually try/caught and swallows failures, so a blocked storage API degrades silently rather than aborting the rest. React Query's in-memory cache is cleared alongside it via `queryClient.clear()` (AuthProvider.tsx:104,127,211). It fires on three paths: explicit logout (:211-213), the global 401 session-expiry signal raised by api-client.ts:93-95 (AuthProvider.tsx:128-131), and a 401 on the session probe when a session had previously existed (:106-111). Three keys are deliberately preserved (AuthProvider.tsx:30-34): `tday.returning-browser`, `tday.appMode`, and `tday.local.workspace.v1` — see the Local Mode entry.

> tday-web/src/lib/security/clearClientUserData.ts:18-80; tday-web/src/providers/AuthProvider.tsx:30-34,104-111,127-131,211-213

### SameSite=Lax as the actual CSRF control

`On by default`

The session cookie carries `SameSite=Lax` as a hardcoded constant (SessionCookies.kt:11, applied via the extensions map at :96). This is the only CSRF defence in the stack that actually does anything. It means a cross-site form POST, fetch or XHR from evil.example does not carry the session cookie, which covers every mutating endpoint the SPA uses — all of them are POST/PATCH/DELETE (api-client.ts:110-158). It does NOT cover top-level GET navigations, which Lax explicitly still allows the cookie on (see the /api/timezone gap). The SPA sends no CSRF header of any kind — the only header set anywhere in api-client.ts is Content-Type, passed through by each caller — and there is no Origin/Referer check on the server path. Honest characterisation: single-control CSRF protection with no defence in depth. If the SameSite attribute were ever dropped or a browser stopped honouring it, nothing else would catch a cross-site mutation.

> backend:security/SessionCookies.kt:11,96; tday-web/src/lib/api-client.ts:66-74,110-158

### CORS is an explicit allowlist that is EMPTY by default

`On by default`

ADDED — the first pass missed this, and it is load-bearing for the SameSite story. Ktor CORS is installed at Cors.kt:19-37 with `allowCredentials = true` and `allowNonSimpleContentTypes = true`, but the allowed-origin set comes entirely from `config.corsAllowedOrigins.forEach { allowConfiguredOrigin(it) }` (:34-36), which is `envCsv("CORS_ALLOWED_ORIGINS")` — an EMPTY list unless the operator sets it (AppConfig.kt:94), and .env.example:40 ships it blank. Verified: `anyHost()` appears nowhere in tday-backend/src/main. Each configured origin is parsed as a URI and rejected with a warning unless the scheme is http/https and the host is non-blank; the host+port is then registered via allowHost with that single scheme (Cors.kt:40-51) — no wildcards, no reflection of the request Origin. Net effect on a default self-host: the browser will not let any other origin read an authenticated API response, so a cross-origin XHR cannot exfiltrate task data even if SameSite were bypassed. The risk to watch when comparing services: `allowCredentials = true` means anything an operator adds to CORS_ALLOWED_ORIGINS gets cookie-authenticated cross-origin read access.

> backend:plugins/Cors.kt:19-37,40-51; backend:config/AppConfig.kt:94; .env.example:40

### API responses bypass the browser HTTP cache

`On by default`

ADDED. Every network call the SPA makes passes `cache: "no-store"` to fetch (api-client.ts:70), and both credential-flow fetches do the same (clientCredentialEnvelope.ts:69,146). This keeps authenticated JSON out of the browser's own on-disk HTTP cache, which — unlike the service worker's Cache Storage — is NOT touched by clearClientUserData. Scope limit worth stating: it constrains the browser cache only; the service worker's `tday-api-cache` still holds up to 100 API responses for an hour (that one IS wiped at logout), and this says nothing about what an intermediary caches.

> tday-web/src/lib/api-client.ts:66-74; tday-web/src/lib/security/clientCredentialEnvelope.ts:69,146

### Route guards for authenticated and admin surfaces (client-side, cosmetic)

`On by default`

ProtectedRoute renders a bootstrap screen while loading/unavailable (:11-13), redirects to /:locale/login when unauthenticated (:15-17), and to login?pending=1 when approvalStatus is present and not APPROVED (:19-21). AdminRoute requires role==="ADMIN" AND approvalStatus==="APPROVED", otherwise bounces to /:locale/app/tday (AdminRoute.tsx:12-14). Stated plainly: these are UI affordances, not access control — the values come from the /api/auth/session response held in React state and a user can edit them in devtools. The real enforcement is server-side. One nuance worth recording: ProtectedRoute's pending check is `user?.approvalStatus && user.approvalStatus !== "APPROVED"`, so a session whose approvalStatus is null falls through the guard; harmless because the server rejects unapproved sessions anyway, but it means the guard is not the thing keeping unapproved users out.

> tday-web/src/pages/ProtectedRoute.tsx:11-21; tday-web/src/pages/AdminRoute.tsx:12-14

### Telemetry scrubbing before anything leaves the browser

`On by default`

Sentry is initialised with sendDefaultPii:false (main.tsx:29), replaysSessionSampleRate:0 and replaysOnErrorSampleRate:0 (:32-33 — no session replay ever), and the default Breadcrumbs integration is filtered out and replaced by one with console:false and dom:false (:34-43). Every event passes scrubSentryEvent (sentry.ts:162-191): deletes user.ip_address / user.email / user.username, rewrites request.url through a path templater, deletes request.query_string and request.cookies, and strips the SENSITIVE_HEADERS set — authorization, cookie, set-cookie, x-csrf-token (:62-67). Path segments not on a static allowlist are collapsed to :id / :locale / :redacted / :value (sanitizeSegment, :219-230). CORRECTION: that allowlist (STATIC_SEGMENTS, sentry.ts:11-60) holds 48 entries, not 50. Breadcrumb and extra-data keys matching /(authorization|cookie|csrf|token|password|session|secret|email|username|body|payload|header)/i become the literal "redacted" (:72-73,:251-266). Trace propagation is restricted to same-origin /api paths (main.tsx:31). Net effect: a crash report from this SPA does not carry the session cookie or the content of the user's tasks. The DSN must be supplied at build time or Sentry is a no-op (`dsn: import.meta.env.VITE_SENTRY_DSN ?? ""`, main.tsx:26) — so on a default self-host with no DSN, nothing is transmitted at all.

> tday-web/src/main.tsx:25-47; tday-web/src/lib/observability/sentry.ts:11-60,62-73,162-191,219-230,251-266

### No secret is baked into the browser bundle

`On by default`

Verified by grep: exactly two VITE_* variables exist in tday-web/src, both public by definition — VITE_SENTRY_TRACES_SAMPLE_RATE (main.tsx:21) and VITE_SENTRY_DSN (main.tsx:26). They are passed as Docker build args and annotated as such in docker-compose.yaml:65-67 ("Build-time only: Vite bakes these into the SPA bundle. Both are public values.") and re-declared as ARG/ENV in the frontend stage of Dockerfile.backend:8-13. The only other compile-time defines are __APP_VERSION__ and __BUILD_ID__ (vite.config.ts:70-73). The Sentry source-map upload token is read as `process.env.SENTRY_AUTH_TOKEN` (vite.config.ts:67) — a build-environment variable, not a VITE_* one, so Vite does not inline it into client code. A guardrail test walks every .ts/.tsx under tday-web/src (skipping test files) and asserts zero matches for `(password|secret|token|apiKey|api_key)\s*[:=]\s*"..."` with an 8+ char literal, PEM private-key blocks, ghp_ tokens and sk- keys (tests/guardrails/security.test.ts:49-73).

> tday-web/src/main.tsx:21,26; tday-web/vite.config.ts:64-68,70-73; docker-compose.yaml:65-67; Dockerfile.backend:8-13; tday-web/tests/guardrails/security.test.ts:49-73

### WebSocket carries signals only, authenticated by the same cookie

`On by default`

realtime.tsx:52-54 derives the socket URL from window.location.protocol/host (wss: over the tunnel, ws: only on a plain-HTTP LAN origin) and opens /ws with no token in the URL — the HttpOnly session cookie authenticates it. Server-side the handler calls requireApprovedAuthUser and closes with CloseReason.Codes.VIOLATED_POLICY on failure, distinguishing "Unauthorized" from "Pending approval" (Routing.kt:68-79). Messages are invalidation signals only: onmessage parses a `type`/`event` string and invalidates React Query keys, never rendering payload content (realtime.tsx:63-74) — a malformed frame falls into the catch and at worst triggers a broader refetch. Reconnect uses exponential backoff capped at 30s (:84-92). The CSP allows `ws:`/`wss:` as scheme sources; the source comment at SecurityHeaders.kt:52-55 notes these permit WebSocket connections only, not HTTP exfiltration.

> tday-web/src/lib/realtime.tsx:52-74,84-92; backend:plugins/Routing.kt:68-79

### PWA share target pre-fills but never auto-submits

`On by default`

The web app manifest registers a GET share_target with action "/share", method "GET", mapping title→`title` and both text and url→`quickadd` (tday-web/public/manifest.webmanifest, share_target block — verified by parsing the JSON). The handler joins the two params into one string, opens the create-task sheet pre-filled with it, then deletes both params from the URL so a refresh does not re-open the sheet (ShareQuickAddBridge.tsx:19-29). It creates nothing. This matters for the CSRF picture: an attacker-crafted link to /share?quickadd=... cannot silently create a task — a human still has to confirm the sheet.

> tday-web/src/features/share/ShareQuickAddBridge.tsx:11-29; tday-web/public/manifest.webmanifest (share_target: action "/share", method GET)

### Local Mode data is stored client-side, unencrypted, by design

`On by default`

Local Mode keeps the entire no-login workspace in ONE localStorage key, `tday.local.workspace.v1` (localDb.ts:127, written at :200-212) as plain JSON: todos, todoInstances (with overridden titles/descriptions), floaters, lists, floaterLists, completedTodos, completedFloaters, taskSteps and preferences (LocalWorkspace shape at localDb.ts:114-125). Not encrypted, no passphrase; the file header states the contract explicitly at localDb.ts:10-11 ("Clearing the browser's cookies/site data drops this document. That is the documented contract of Local Mode on the web, not a failure mode."). api-client.ts:62-64 routes every /api/* call to the in-browser handler while the mode flag `tday.appMode` is "local" (appMode.ts:14,43-45). Shared-computer consequence, stated plainly: anyone with access to that browser profile can open devtools (or read the profile's Local Storage LevelDB on disk) and read every task. And because `tday.local.workspace.v1` and `tday.appMode` are on the preserve list (AuthProvider.tsx:30-34), signing out of a SERVER account does not remove the local workspace — moreover logging out OF Local Mode only calls setAppMode(null) and deliberately leaves the data in place (AuthProvider.tsx:189-198). The only things that remove it are Settings → Delete local data (localApi.ts:292-295 → clearWorkspace, localDb.ts:225-233) and clearing browser site data. Local Mode also exposes a full unencrypted JSON export of everything through the same GET /api/export shape (localTransfer.ts:58-142).

> tday-web/src/lib/local/localDb.ts:10-11,114-127,200-212,225-233; tday-web/src/providers/AuthProvider.tsx:30-34,189-198; tday-web/src/lib/local/localApi.ts:292-295; tday-web/src/lib/local/appMode.ts:14,43-45

### Full inventory of what else this SPA stores client-side

`On by default`

localStorage (all plaintext; all removed on logout except the three preserved keys): `tday.local.workspace.v1` (all Local Mode records — localDb.ts:127), `tday.appMode` (appMode.ts:14), `tday.language` (i18n.ts:23), `tday.push-enabled` (usePushNotifications.ts:6), `tday.returning-browser` (returningBrowser.ts:1), `tday.release.current.v1` (release.ts:9), `tday.sidebar.desktop.open` and a bare unprefixed `tab` key for the active tab (MenuProvider.tsx:30-31; SidebarToggleContainer.tsx:57 writes the same `tab` key), `tday.lastSeenGuideVersion` (guideContent.ts:66), `tday.repeatSuggestion.dismissed` (repeatSuggestionDismissal.ts:6), a per-week "week in review seen" flag (WeekInReviewCard.tsx:27,45), `tday.restingFloaters.enabled` (floaterResting.ts:31), and — worth flagging — `tday.pendingApprovalUsername`, which stores the RAW USERNAME of an account awaiting admin approval (pendingApproval.ts:5,9-25; its own comment confirms only the username is stored, never the password). sessionStorage: stale-chunk and version reload guards (chunkError.ts:48-92), the install-prompt dismissal (useInstallPrompt.ts:88), a release-announcer flag (ReleaseUpdateAnnouncer.tsx:13-21). IndexedDB: the app creates none of its own — the only IndexedDB code in tday-web/src is the teardown that enumerates and deletes whatever exists (clearClientUserData.ts:66-79), which also catches Workbox's bookkeeping DBs. Cache Storage: see the service-worker entry (up to 100 API responses, 1h TTL). In-memory only, never persisted: the raw password held in React state while the pending-approval screen is open (OnboardingWizard.tsx:111,260,308), cleared at :691.

> tday-web/src/lib/pendingApproval.ts:5,9-25; tday-web/src/lib/local/appMode.ts:14; tday-web/src/i18n.ts:23; tday-web/src/hooks/usePushNotifications.ts:6; tday-web/src/providers/MenuProvider.tsx:30-31; tday-web/src/lib/security/clearClientUserData.ts:66-79; tday-web/src/components/onboarding/OnboardingWizard.tsx:111,691

### Service-worker notification action performs an authenticated write

`On by default`

Not a defence — recorded because it is the one place the SPA's cookie is used outside a page context. The "Complete" notification action fires `fetch("/api/todo/complete", {method:"PATCH", credentials:"include", body:{id: todoId}})` directly from the service worker, with todoId taken from the server-delivered push payload's `data` field, falling back to focusing/opening the app if the request fails (sw.ts:166-180; payload plumbed in at :115). `credentials: "include"` on a same-origin URL is equivalent to same-origin here, and it is a PATCH so SameSite=Lax still applies. Blast radius is bounded: the only id available is one the server itself pushed, and the endpoint only marks a task complete. The "Snooze 1h" action just appends `?snooze=1h`/`&snooze=1h` to the opened URL (sw.ts:182-186) and is handled in-page.

> tday-web/src/sw.ts:100-136,151-189

### Query-layer error handling does not leak state on 401

`On by default`

api-client.ts:93-95 raises the global session-expiry signal on any 401 whose URL does not start with /api/auth/ — auth endpoints are excluded so a failed login cannot self-trigger a spurious global expiry. The React Query global error handler explicitly returns early on 401 to leave it to that flow, shows a generic "Server error" toast for status >= 500, a generic "Can't reach the server" toast for non-ApiError throws (suppressed when navigator.onLine is false), and only surfaces backend text for real 4xx (QueryProvider.tsx:26-45). handleSessionExpired dedupes a burst of 401s into a single expiry by returning early once already unauthenticated (AuthProvider.tsx:118-122). Combined with the wipe above, a revoked session (admin purge, password change, forced logout) causes the browser to drop its cached copy of the user's data on the next request rather than continuing to render it offline.

> tday-web/src/lib/api-client.ts:88-95; tday-web/src/providers/AuthProvider.tsx:116-134; tday-web/src/providers/QueryProvider.tsx:26-45

### Web Push subscription handling

`Needs config`

Subscribe fetches the VAPID public key from the server, requests browser permission, calls pushManager.subscribe with userVisibleOnly:true, and POSTs endpoint/p256dh/auth to /api/notifications/subscribe (usePushNotifications.ts:85-125); unsubscribe DELETEs /api/notifications/unsubscribe with the endpoint and then calls sub.unsubscribe() (:127-145). The endpoint URL the browser hands over is validated server-side by the new SSRF egress guard — verified: PushNotificationService.kt:10 imports `com.ohmz.tday.domain.validateOutboundUrl` and the override at :101 calls it on the endpoint at :112 before anything is stored. clearClientUserData also unsubscribes push before wiping storage (clearClientUserData.ts:24-32), so a logout does not leave an orphaned push channel pointing at the account. Requires-config because it is inert without VAPID keys configured on the server and explicit user permission.

> tday-web/src/hooks/usePushNotifications.ts:85-145; tday-web/src/lib/security/clearClientUserData.ts:24-32; backend:services/PushNotificationService.kt:10,101,112

### Encrypted credential envelope — SIGN-IN ONLY (first pass overclaimed this)

`Partial`

CORRECTED. The first pass said "password never leaves the browser in cleartext form." That is true of SIGN-IN and false of every other password-bearing flow. What is real: createClientCredentialEnvelope (clientCredentialEnvelope.ts:43-140) fetches an RSA public key from GET /api/auth/credentials-key, generates a fresh AES-256-GCM key with a 12-byte random IV (:98-103), encrypts `{username,password}` JSON, wraps the raw AES key under RSA-OAEP/SHA-256 (:82-93,:118-131) and posts base64url envelope fields plus keyId/version. If `window.crypto.subtle` is absent (a plain-HTTP LAN origin is not a secure context) it degrades to a challenge-response proof rather than a plaintext password: PBKDF2-HMAC-SHA256 over the server's salt and iteration count to a 32-byte key, then HMAC-SHA256 over `login:<challengeId>:<username>` (:142-193, constants at :8-10). AuthProvider.login posts that payload to /api/auth/callback/credentials (AuthProvider.tsx:161-165). WHAT IS NOT COVERED — verified by reading each caller: (1) registration POSTs `password: registerPassword` plus the three security answers as plaintext JSON to /api/auth/register (OnboardingWizard.tsx:290-303); (2) password change POSTs `{currentPassword, newPassword}` in the clear to /api/user/change-password (SettingsPage.tsx:765-767 and ForcePasswordChangeGate.tsx:49-52); (3) the forgot-password reset POSTs `{username, answers, newPassword}` in the clear to /api/auth/reset-password (ForgotPasswordPanel.tsx:175-178). The envelope's only consumer is createClientCredentialEnvelope, called at exactly two sites, both sign-in (OnboardingWizard.tsx:251 and :345). Practical effect for a self-hoster: a TLS-terminating proxy log or Cloudflare Tunnel inspection point never records the password AT LOGIN, but does see it at account creation, at every password change, and at every recovery reset.

> tday-web/src/lib/security/clientCredentialEnvelope.ts:8-10,43-140,142-193; tday-web/src/components/onboarding/OnboardingWizard.tsx:251,290-303,345; tday-web/src/components/settings/SettingsPage.tsx:765-767; tday-web/src/components/auth/ForgotPasswordPanel.tsx:175-178

### Static file serving: API-scoped, canonicalised, with a prefix-check caveat

`Partial`

CORRECTED on two points. First, the handler is not unconditional: it only registers if the STATIC_FILES_DIR env var is set AND resolves to a directory (Routing.kt:86-89). The shipped image sets it (Dockerfile.backend:44 `ENV STATIC_FILES_DIR=/app/static`), so it is on for the Docker deployment — but a bare jar without that variable serves no SPA at all. Second, the traversal check is real but not airtight: `File(dir, relPath).canonicalFile` must satisfy `candidate.isFile && candidate.path.startsWith(dir.path)` (:94-95). That is a raw STRING prefix comparison, not a path-boundary check, so `../static-backup/x` canonicalises to /app/static-backup/x which startsWith "/app/static" and would be served. Classic `../../etc/passwd` is blocked; a sibling directory whose name shares the prefix is not. On the shipped image no such sibling exists inside the container, which is why this is a caveat rather than a live finding. What does hold cleanly: requests whose path begins with `api/` or `ws` return early so a static file can never shadow an API route (:92). Unknown routes fall through to index.html with `no-cache, no-store, must-revalidate` (:99-104,:112) so a new deploy is picked up; content-hashed files under assets/ get `public, max-age=31536000, immutable` (:113,:124); version.json is no-store (:123). robots.txt disallows /api (tday-web/public/robots.txt).

> backend:plugins/Routing.kt:86-108,112-127; Dockerfile.backend:44; tday-web/public/robots.txt

### Not present — web spa security

- **The CSRF token is minted but nothing validates it** — CONFIRMED. GET /api/auth/csrf rate-limits, then generates 32 bytes from SecureRandom, hex-encodes them and returns {"csrfToken": ...} (CsrfRoutes.kt:27-29). Nothing consumes it. A case-insensitive grep for "csrf" across tday-backend/src/main and tday-web/src returns exactly 16 lines, all accounted for: the route itself (CsrfRoutes.kt:12,15,17,29), its rate-limit knobs (AppConfig.kt:33-34,118-119 — AUTH_LIMIT_CSRF_WINDOW_SEC default 60s / AUTH_LIMIT_CSRF_MAX default 40; AuthThrottle.kt:21,78,141), the route registration (Routing.kt:57), and "csrf" as a word inside the backend and frontend telemetry-redaction lists (TdayObservability.kt:16,64,66; sentry.ts:18,66,70,73). Zero validation sites. There is no double-submit cookie, no header comparison, no session-bound token, no Origin/Referer check. The SPA never even calls the endpoint. Consequence for a single self-hosted user: nothing is currently broken, because SameSite=Lax genuinely blocks cross-site POST/PATCH/DELETE. But the endpoint's existence is misleading — a reader auditing this stack would reasonably conclude CSRF tokens are in force when the entire protection is one cookie attribute with no second layer.
- **GET /api/timezone changes server state, and SameSite=Lax does not stop GET** — CONFIRMED. The timezone route is a GET that writes: inside `get { call.withAuth { ... } }` it resolves a timezone from the `?timezone=` query param, then the `x-timezone` header, then `x-user-timezone` (TimezoneRoutes.kt:19-23, resolver at :49-53), and if it differs from the stored value it runs Users.update setting timeZone and updatedAt (:30-37). SameSite=Lax deliberately allows the session cookie on top-level GET navigations, so a link or redirect that a logged-in user follows can flip their stored timezone cross-site — the one mutation the SameSite control does not cover. Two things bound it: the value must parse via ZoneId.of, so it must be a real IANA zone (isValidTimeZone, :55-62), making it a nuisance (tasks appearing at wrong local times, recurrence boundaries shifting) rather than data loss or privilege escalation; and the web SPA never calls it — useUserTimezone only reads a React Query cache entry keyed ["userTimezone"] and never populates it (get-timezone.ts:4-12). It is nonetheless a live, reachable, state-changing GET on the same cookie-authenticated origin.
- **Source maps are built and publicly served** — CONFIRMED. vite.config.ts:89 sets `sourcemap: true`, and the Sentry Vite plugin (vite.config.ts:64-68) is configured with org "tday-kb", project "tday-web" and an authToken but NO `sourcemaps.filesToDeleteAfterUpload`, so the .map files stay in dist. Dockerfile.backend:41 does `COPY --from=frontend /web/dist /app/static` and sets STATIC_FILES_DIR to it (:44); the Ktor static handler serves any file under that root (Routing.kt:90-97) with `public, max-age=31536000, immutable` for anything under assets/ (:113,:124). Verified concretely: `ls tday-web/dist/assets` shows 105 .map files, including AdminPage-*.js.map, AdminRoute-*.js.map and BlogArticlePage-*.js.map, one beside every chunk. Practical effect: anyone who can reach the tunnel URL can fetch the full original TypeScript for the whole app including the admin screens — component names, internal comments, route tables, and the exact client-side validation logic. Not a secret leak (the bundle carries no keys), but it removes all friction from mapping the attack surface.
- **No CSP fallbacks: no Trusted Types, no SRI, no Clear-Site-Data** — CONFIRMED — the grep returns nothing at all. Meaning: (a) there is no `require-trusted-types-for 'script'` directive, so the two innerHTML sinks are governed only by their own hand-rolled escapeHtml — a future third sink would not be caught by the platform; (b) the logout response sends no `Clear-Site-Data: "cache", "cookies", "storage"` header, so the browser-side wipe depends entirely on the JS in clearClientUserData.ts running to completion — if the tab is closed mid-logout or an await rejects outside its try/catch, the API cache and localStorage survive; (c) no Subresource Integrity, though this is near-moot since CSP pins script-src to 'self' and every script is same-origin build output.
- **CSP_MODE is undocumented in the shipped .env examples** — CONFIRMED. CSP_MODE and CSP_CONNECT_EXTRA are read by AppConfig.kt:165-166 and decide whether the policy is enforcing, report-only, or absent. Neither appears in .env.example or tday-backend/.env.example — `grep -n "CSP" .env.example tday-backend/.env.example` produces no output, while the same file does document CORS_ALLOWED_ORIGINS (.env.example:40). The default is safe — parseCspMode(null) is CspMode.enforce — so an operator who never touches it gets the enforcing header. The gap is discoverability plus a trap: an operator whose deployment breaks under CSP has no documented knob to flip to report-only while diagnosing, and an operator who does discover CSP_CONNECT_EXTRA will silently lose the auto-derived Sentry ingest origin because the extras list REPLACES it (SecurityHeaders.kt:89-91). The security guardrail test that checks .env.example for rate-limit and lockout variables has no equivalent assertion for the CSP variables.
- **Logout does not unregister the service worker** — CONFIRMED. clearClientUserData deletes every Cache Storage cache but contains no `registration.unregister()` call anywhere in its 80 lines. The only unregister() in tday-web/src is resetAppData.ts:25, a manual stale-build escape hatch whose own doc comment (:14-16) says it is deliberately scoped to Cache Storage + the Service Worker and leaves localStorage and IndexedDB alone — it is not wired into logout. In practice the residue is benign: after the wipe the SW's caches hold only re-fetched static build assets, and the push subscription is separately unsubscribed (clearClientUserData.ts:24-32). Worth recording for comparison because on a genuinely shared machine the next user inherits a live, controlling service worker for the origin rather than a clean slate.
- **No client-side idle timeout or re-authentication prompt** — CONFIRMED. There is no inactivity timer, screen lock, or re-auth-on-sensitive-action anywhere in the SPA. AuthProvider's only timer is a 15-second retry (AUTH_SESSION_RETRY_DELAY_MS, AuthProvider.tsx:24) fired solely when the session probe reports the backend "unavailable" (:144-155). useVersionGate's setInterval polls /version.json for new builds and reacts to visibilitychange, not to session freshness (useVersionGate.ts:106-108). Session lifetime is governed entirely server-side by the rolling renewal and absolute expiry helpers (SessionCookies.kt:55-72). Consequence on a shared or unattended computer: an open tab stays fully usable for as long as the server-side session lasts, and the tab surfaces no lock screen.

---
