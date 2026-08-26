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

> docker-compose.yaml:6,28,43-58,70-76,89-98

### Throttle-table retention that preserves live lockouts (recently hardened)

`Needs config`

NEW. RetentionScheduler runs a loop on a 6-hour interval (TICK_INTERVAL = Duration.ofHours(6), RetentionScheduler.kt:162) and ages out four security bookkeeping tables, closing a disk-fill vector: these tables previously grew on every request an attacker made and nothing deleted from them, so refusing traffic cost the server more than serving it (KDoc at RetentionScheduler.kt:30-44). Defaults: eventLog 90 d (RETENTION_EVENTLOG_DAYS), authThrottle 30 d, authSignal 180 d, cronLog 90 d floored at 7 (AppConfig.kt:158-161); 0 disables a table (RetentionScheduler.kt:127). Deletes are batched at BATCH_LIMIT=5000 per transaction (:139-143,163). The load-bearing rule for this domain is at RetentionScheduler.kt:80-86: the authThrottle DELETE is conditioned on `updatedAt < cutoff AND (lockUntil IS NULL OR lockUntil < now)`, so a row still serving a lockout is never dropped — otherwise retention would have handed attackers a free lockout reset. A fifth sweep (:94-113) clears pendingAdminReset flags older than ADMIN_RESET_REQUEST_TTL_DAYS. Marked requires-config because it ships inert: RETENTION_DRY_RUN defaults to "true" (AppConfig.kt:164) and the operator must set it to false for any row to be removed. CORRECTION to the first pass: in dry-run the table purges do NOT count what they would delete — the code at RetentionScheduler.kt:130-134 deliberately skips the count query and just logs the cutoff and records "<table>=dry-run". Only the pendingAdminReset sweep runs a real count in dry-run (:97-102).

> backend:services/RetentionScheduler.kt:30-44,77-92,94-113,121-148,162-163; backend:config/AppConfig.kt:158-164

### Client IP resolution — cf-connecting-ip first, then x-forwarded-for, then x-real-ip, then socket

`Partial`

getClientIp (ClientSignals.kt:27-37) checks in strict order: `cf-connecting-ip` (trimmed, non-empty), then the first non-empty comma-separated entry of `x-forwarded-for`, then `x-real-ip`, then `request.local.remoteAddress`. There is NO trusted-proxy allowlist and no check that the request actually arrived from Cloudflare — whatever header is present is believed. In this deployment that is load-bearing on network topology rather than on code: docker-compose.yaml:76 binds the backend to `${TDAY_HOST_BIND:-127.0.0.1}:${TDAY_HOST_PORT:-2525}:8080`, so the container port is reachable only from host loopback and the sole public path is the Cloudflare Tunnel; Cloudflare overwrites cf-connecting-ip on ingress, so a remote attacker coming through the tunnel cannot forge it or choose their own bucket. The residual exposure is local: any process that can reach 127.0.0.1:2525 can set cf-connecting-ip per request and get a fresh ip and ipUsername bucket every time, defeating the IP-dimension lockout entirely and leaving only the 50/900 s account ceiling. Note also that the ordering is what makes the per-IP dimension meaningful at all — without a proxy that supplies a real client IP, remoteAddress would be the same value for everyone and the ip dimension would collapse into one global bucket.

> backend:security/ClientSignals.kt:27-37; docker-compose.yaml:76

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
- **No trusted-proxy allowlist for client-IP headers** — getClientIp trusts cf-connecting-ip, then x-forwarded-for, then x-real-ip from any caller, with no verification that the connection originated from Cloudflare or from a known proxy address (ClientSignals.kt:27-37). The deployment topology is what makes this safe against remote attackers — the port is bound to 127.0.0.1 (docker-compose.yaml:76) and Cloudflare rewrites cf-connecting-ip on ingress — so the gap is not remotely exploitable as deployed. But it means the IP-dimension lockout is defended by network configuration alone: anything that can reach 127.0.0.1:2525 on the host (another container with host networking, a compromised sidecar, a local shell) can supply a fresh cf-connecting-ip per request, obtain unlimited fresh ip and ipUsername buckets, and leave only the 50/900 s account ceiling in force. If the owner ever fronts this with a different reverse proxy, or widens TDAY_HOST_BIND, the IP dimension silently becomes attacker-controlled with no code change and no warning — there is no startup log line about proxy trust either.
- **Abuse alerts are emitted but never delivered or surfaced** — auth_alert_ip_concentration, auth_alert_lockout_burst and auth_signal_anomaly are computed and written, then go nowhere. Grepping for consumers of the eventLog table or of the auth_alert codes finds only the emit sites in AuthThrottle.kt (227, 231, 271), the SecurityEventLogger INSERT (SecurityEventLogger.kt:44), the schema-creation list (DatabaseConfig.kt:94) and the retention DELETE (RetentionScheduler.kt:77-79). There is no admin API route that reads eventLog, no query of it in the React web client, no push notification and no Sentry error event — SecurityEventLogger.kt:35-39 adds only a breadcrumb, which is attached to an unrelated later event or dropped. For a single self-hosted user this means a sustained credential-stuffing campaign is fully mitigated but entirely invisible unless the owner manually tails container logs or runs SQL against eventLog.
- **No dedicated rate limits on the admin, webhook, export or notification routes** — resolvePolicies (plugins/RateLimiting.kt:39-98) special-cases exactly five paths and no others: /api/* (api_global), /health + /api/mobile/probe + /calendar/* (infra), POST /api/todo/summary, POST /api/user/change-password, and /ws. Everything else mounted under /api — adminRoutes, webhookRoutes, exportRoutes, notificationRoutes, listShareRoutes (Routing.kt:37-54) — is covered only by the 180-requests-per-60-seconds api_global budget, keyed per user. So an authenticated account can drive 180 export or webhook-creation calls per minute. Given the PENDING-by-default approval gate, every caller on these routes is an admin-approved user, so this is a blast-radius observation about a trusted-but-compromised session rather than an open exposure. POST /api/auth/logout is likewise unthrottled beyond api_global (LogoutRoutes.kt contains no AuthThrottle reference).
- **The rate-limit enforcement logic itself has no test coverage** — Added by this fact-check, because it bears directly on how much weight the numeric parameters above can carry. Every test in this domain exercises wiring, not counting. RateLimitingTest.kt injects a RecordingRequestRateLimiter fake (:55,:153-165) and AuthRateLimitResponseTest.kt injects a BlockingAuthThrottle fake (:161-169,:189-212), so both prove interceptor ordering, short-circuit-before-handler and the 429/Retry-After response contract — but neither ever runs InMemoryRequestRateLimiter's sliding-window arithmetic or AuthThrottleImpl's fixed-window and exponential-lockout arithmetic. AuthThrottleSubjectsTest.kt covers only the pure keying functions above the DB boundary. There is no test asserting that the 12th request in 300 s is allowed and the 13th blocked, that the 5th failure yields exactly 30 s, that the lock doubles, that it clamps at 1800 s, that the 24 h decay works, or that clearFailures actually clears. Root cause is stated in the source itself: the repo has no test database (AuthThrottleSubjectsTest.kt:9-17). Consequence for comparison: the parameter values in this document are read off the configuration and the formula, not verified by execution.
- **No memory limits on any container** — Added by this fact-check as the counterpart to the pids_limit control. docker-compose.yaml sets no mem_limit, no memory reservation and no JVM heap ceiling on any of the four services, and the comment at docker-compose.yaml:70-73 states this is deliberate — a JVM under `restart: always` would OOM-crash-loop on a too-low value, which was judged worse than the DoS it defends against, and the note directs the operator to set mem_limit only after measuring real RSS together with -XX:MaxRAMPercentage. Practical consequence: the in-process memory bounds documented above (the limiter's bucket map cleanup, the 5000-challenge cap) are the only ceilings on memory an unauthenticated attacker can drive; if any of them is exceeded or bypassed, nothing at the container level stops the backend from consuming host memory and taking Postgres or the tunnel down with it.

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

### Field encryption at rest is OPT-IN, and "off" is a supported configuration

`Off by default — deliberately`

Backend field encryption is controlled by two independent things: whether a key exists, and whether the operator has declared that a key is *required*.

- A key exists when `DATA_ENCRYPTION_KEY` or `DATA_ENCRYPTION_KEYS` (or their `_FILE` variants) are set — `AppConfig.kt:103-104`. The shipped template leaves both blank (`.env.example:171,174`), so a fresh deploy has no key.
- `REQUIRE_ENCRYPTION_AT_REST` defaults to `false` — `AppConfig.kt:108`, and the template ships it explicitly false (`.env.example:192`).

There is no `ALLOW_PLAINTEXT_AT_REST` anywhere in the repo — a full-tree grep returns nothing. It was replaced by `REQUIRE_ENCRYPTION_AT_REST`, which has the opposite polarity: the old variable permitted plaintext, the new one forbids it.

`startupEncryptionVerdict(isProduction, encryptionConfigured, requireEncryptionAtRest)` is a pure function with exactly four outcomes (`Application.kt:156-165`):

| production | key present | `REQUIRE_ENCRYPTION_AT_REST` | verdict |
|---|---|---|---|
| no | any | any | `NotApplicable` — nothing is checked |
| yes | yes | any | `Ok` |
| yes | no | `false` (default) | `ProceedWithPlaintextNotice` — **boots** |
| yes | no | `true` | `RefuseBoot` — hard startup failure |

`enforceStartupSecurityPolicy` acts on the verdict at `Application.kt:172-184`. In the default configuration it logs **one INFO line, not a warning**, every boot (`Application.kt:179-183`):

> Field encryption at rest is OFF (no DATA_ENCRYPTION_KEY). Task titles and descriptions are stored as plaintext in Postgres — encrypt your backups. Set REQUIRE_ENCRYPTION_AT_REST=true to make a missing key a startup failure.

INFO rather than WARN is the point: this is a chosen configuration, not a mistake. With the opt-in set and no usable key, boot dies with `error(MISSING_ENCRYPTION_KEY_MESSAGE)` (`Application.kt:167-170,176`), and the message names the fix in both directions (set a key, or unset the variable). That failure is an uncaught exception during Ktor module init, so the container exits — and every service in `docker-compose.yaml` carries `restart: always` (`:5,27,70`), so opting in means accepting a crash loop as the failure mode. That is the trade the opt-in exists to make explicit.

**Why the default is off.** On a single-operator self-hosted box, field encryption defends one artifact: a database copy that leaves the host. Anyone who can read the live database already has the host — and the key would be sitting in the same `.env.docker` as the Postgres volume (`docker-compose.yaml:77-78` `env_file`). Against that adversary the key buys nothing. What a fail-closed default *does* reliably produce is an outage: one forgotten variable becomes a container that exits at startup, and `restart: always` turns that into a crash loop on a machine reachable only over SSH. The reasoning is recorded in the code itself (`Application.kt:139-155`, `AppConfig.kt:106-107`, `.env.example:179-192`).

**The polarity is pinned by tests.** `StartupEncryptionPolicyTest.kt` asserts each row of the table above (`:19-30` production-without-a-key boots; `:32-42` production with a key is `Ok`; `:44-55` opting in refuses boot; `:57-67` opting in with a key is fine; `:69-86` non-production is never blocked) and then enumerates the entire 2×2×2 space to assert that `RefuseBoot` occurs in exactly one configuration and no other (`:88-104`). A future "simplification" back to fail-closed-by-default breaks a test rather than a deployment.

**Limitations, stated plainly:**
- `isProduction` resolves from `TDAY_ENV`, then `NODE_ENV`, else `"development"` (`AppConfig.kt:94,203-206`). An operator who sets neither to `production` gets **no** notice line at all, and `REQUIRE_ENCRYPTION_AT_REST=true` is silently ignored (`Application.kt:161`). `.env.example:191` warns about this, but nothing in the code does.
- The opt-in checks that a key is *loadable* (`isConfigured()` is `activeKeyId != null`, `FieldEncryption.kt:44`), not that any particular row is encrypted, and not that the key is the one existing rows were written under. Turning it on does not encrypt anything already written.

> backend:Application.kt:88-89,129-137,156-184; backend:config/AppConfig.kt:94,103-108,203-206; backend-test:StartupEncryptionPolicyTest.kt:19-104; docker-compose.yaml:5,27,69,77-78; .env.example:171-192

### Exactly which fields are encrypted (titles are now included)

`sensitiveFields` is `{"title", "overriddenTitle", "description", "content", "overriddenDescription", "webhookSecret"}` — `FieldEncryption.kt:27-34`. **`title` and `overriddenTitle` are in the set**, which is the correction that matters most: for a task app the title is where the information is, and it is ciphertext at rest whenever a key is configured. The code notes the reason it was safe to add: no query anywhere sorts, searches or filters on a title column (`FieldEncryption.kt:25-26`). I checked that independently — a repo-wide grep for `orderBy(...title`, `title like` and `Todos.title eq` over `tday-backend/src/main/kotlin` returns no hits; the only non-crypto uses of a title column are ciphertext-to-ciphertext copies (below).

Every write site goes through `encryptRequired("title", …)` (a non-null wrapper over `encryptIfSensitive`, `FieldEncryption.kt:155-156`) and every read site through `decryptRequired`/`decryptIfEncrypted` (`:158-159`):

| Column | Write | Read |
|---|---|---|
| `Todos.title` | `TodoService.kt:77,157`; import `ExportService.kt:207` | `TodoService.kt:242,523`, `ListService.kt:130`, `ExportService.kt:393` |
| `Todos.description` | `TodoService.kt:78,160`, `ExportService.kt:208` | `TodoService.kt:243,524`, `ExportService.kt:394` |
| `Floaters.title` / `.description` | `FloaterService.kt:70-71,119,122`, `ExportService.kt:227-228` | `FloaterService.kt:283-284,301-302`, `FloaterListService.kt:146`, `ExportService.kt:421-422` |
| `TodoInstances.overriddenTitle` / `overriddenDescription` | `TodoService.kt:458,462,477,481`, `ExportService.kt:258,260` | `ExportService.kt:412-413`, ICS feed `CalendarFeedService.kt:179,215` |
| `CompletedTodos` / `CompletedFloaters` title+description | `CompletedTodoService.kt:73,76`, `CompletedFloaterService.kt:72,75`, `ExportService.kt:279,281,299,301` | `CompletedTodoService.kt:96-97`, `CompletedFloaterService.kt:93-94`, `ExportService.kt:436-437,454-455` |
| `TaskSteps.title` (checklist steps) | `TaskStepService.kt:73` | `TaskStepService.kt:146` |
| `webhook_subscriptions.secret` | `WebhookService.kt:121` | `WebhookDispatchService.kt:99` |

Two non-obvious paths were checked and are correct:

- **Push notifications.** The reminder scheduler decrypts the title before building the push body — `ReminderPushScheduler.kt:88`. Without that, every due-task push would read `enc:v1:primary:…`.
- **The ICS calendar feed.** `renderIcs` decrypts the parent task's title and description (`CalendarFeedService.kt:190-191`) and each instance override — `overriddenTitle` at `:179` when the override row is read, `overriddenDescription` at `:215` where the per-instance VEVENT is built. The rendered `SUMMARY`/`DESCRIPTION` (`:201-202,214-216`) are therefore plaintext by construction.

Ciphertext-to-ciphertext copies stay encrypted without a round trip (task↔floater promotion/demotion `TodoService.kt:217-219`, `FloaterService.kt:257-258`; completion snapshots `TodoService.kt:308-310`, `FloaterService.kt:173-174`). The `CompletedTodos.steps` JSON snapshot copies step titles at rest, so that column holds ciphertext too and any future reader must `decryptIfEncrypted` each title — the constraint is written into the code as a comment (`TodoService.kt:286-304`).

`"content"` is a dead entry in `sensitiveFields` — no caller passes that field name (the only other `"content"` in the backend is an Ollama response key, `TodoSummaryService.kt:72`). `TaskSteps` has no title-update path — the interface is create/toggle/delete/reorder only (`TaskStepService.kt:34-40`) — so there is no unencrypted back door into that column.

**Honest limitations:**
- `GET /api/export` emits the decrypted plaintext of everything (`ExportRoutes.kt:23`, `ExportService.kt:393-455`). The JSON export file is an unencrypted copy of all task content regardless of this control.
- The ICS feed URL is an unauthenticated bearer credential handed to third-party calendar clients, and the body it returns is decrypted titles and descriptions. Field encryption does not follow data out of either path.

> backend:security/FieldEncryption.kt:25-34,155-159; backend:services/TaskStepService.kt:34-40,73,146; backend:services/ReminderPushScheduler.kt:88; backend:services/CalendarFeedService.kt:179,190-191,201-202,214-216; backend:services/TodoService.kt:286-304; backend:routes/ExportRoutes.kt:23; backend-test:security/FieldEncryptionTest.kt:52-82

### The `enc:v1` envelope: AES-256-GCM, random IV per write

`Needs config`

`AES/GCM/NoPadding` with a 32-byte (256-bit) key, a fresh 12-byte IV per call, and a 128-bit authentication tag — constants at `FieldEncryption.kt:21-24`, cipher setup at `:51-54`. Output is a self-describing string:

```
enc:v1:<keyId>:<base64url-iv>:<base64url-ciphertext+tag>
```

(`FieldEncryption.kt:62`). Key id and IV travel with the record, so no schema column is needed and the format survives key rotation. Decode splits on `:` and demands exactly five parts (`:67-68`), which is why the IV and ciphertext are base64url (`:161-165`) — that alphabet cannot itself contain `:`.

The IV comes from one long-lived `SecureRandom` (`:35,51`), so two encryptions of identical plaintext produce different ciphertext — asserted at `FieldEncryptionTest.kt:118-125`. That is what denies equality/frequency analysis across rows to someone holding a dump. Within `FieldEncryption.kt` there is no IV-reuse path and no ECB or static-IV fallback.

`isEncrypted()` is a prefix test on `enc:v1:` (`:92`), which makes `encrypt()` idempotent (`:47`, asserted `FieldEncryptionTest.kt:44-50`) — double-encryption is impossible even if a value is written twice.

Decrypt hard-fails rather than degrading: wrong part count (`:68`), unrecognised prefix/version (`:70`), or a key id not in the ring (`:72-73`). **The last one is a real operational hazard:** rows written under a key that is later dropped from the ring do not read back as garbage, they throw, and the request that touched them fails.

`encrypt("")` returns `""` (`:47`) — an empty description stays an empty string, not a ciphertext blob.

> backend:security/FieldEncryption.kt:21-24,35,46-63,65-88,92,161-165; backend-test:security/FieldEncryptionTest.kt:38-50,118-125

### Key material, AAD, keyring and rotation

`Needs config`

**Key material** (`FieldEncryption.kt:131-148`): accepted only as standard-alphabet base64 decoding to exactly 32 bytes, or a 64-character hex string decoding to 32 bytes; anything else throws `IllegalArgumentException("Invalid field encryption key. Expected 32-byte base64 or 64-char hex.")`. There is no passphrase-to-key derivation, so a short operator string cannot silently become the key. Note the base64 branch uses the **standard** alphabet (`Base64.getDecoder()`, `:136`), so a base64url key containing `-` or `_` is rejected.

Because `enforceStartupSecurityPolicy` evaluates `fieldEncryption.isConfigured()` as a call argument at `Application.kt:173`, and that function is invoked unconditionally at `Application.kt:89`, the `by lazy` keyring (`FieldEncryption.kt:37`) is forced during boot **in every environment**, not only production. A malformed key therefore aborts startup everywhere — development and CI included — rather than surfacing on the first write. (This corrects the previous documentation, which described the check as production-only.)

**AAD** (`FieldEncryption.kt:56-59,82-85`): when `DATA_ENCRYPTION_AAD` is set, its UTF-8 bytes are fed to `updateAAD()` on both encrypt and decrypt, so ciphertext produced under a different AAD fails tag verification. `.env.example:177` ships `tday:v1` uncommented, so a copied template already has it set even when no key is — which means an operator who enables encryption later inherits `tday:v1` and must then never change it, because changing the AAD after data is written makes existing rows undecryptable. Two further caveats: it is a single global version tag with no per-row context (no user id, no column name), so it does **not** stop someone with DB write access relocating a ciphertext from one row to another; and it is read with plain `env()` at `AppConfig.kt:105`, so the `DATA_ENCRYPTION_AAD_FILE` form advertised at `.env.example:178` is **not** implemented — following that comment yields a silently unset AAD.

**Keyring and rotation** (`FieldEncryption.kt:105-129`): `DATA_ENCRYPTION_KEYS` is a comma-separated `keyId:keyMaterial` list parsed into a map; malformed entries with no separator are skipped silently (`:113-114`). `DATA_ENCRYPTION_KEY` is then folded in last under `DATA_ENCRYPTION_KEY_ID` (default `primary`, `AppConfig.kt:102`) — so the single-key variable silently **overwrites** a ring entry sharing its id (`:123-126`). The active write key is `DATA_ENCRYPTION_KEY_ID` if present in the ring, otherwise an arbitrary first map entry (`:38-42`) — an unrecognised key id does not error, it picks something else. Decrypt looks up whatever key id is embedded in the row (`:72`), so retired keys keep working for reads.

Rotation procedure: add the new key to `DATA_ENCRYPTION_KEYS`, point `DATA_ENCRYPTION_KEY_ID` at it, and **keep the old entry indefinitely** — there is no re-encrypt or backfill job anywhere in the repo, so rows stay on their original key until something rewrites them, and removing the old key makes those rows unreadable (hard failure, per the decrypt section above).

> backend:security/FieldEncryption.kt:37-42,56-59,72,82-85,105-129,131-148; backend:config/AppConfig.kt:102-105; backend:Application.kt:89,173; .env.example:177-178

### Encryption is not retroactive, and mixed columns are safe

`encryptIfSensitive` returns the input unchanged when no key is configured or the field is not sensitive (`FieldEncryption.kt:94-97`), and `decryptIfEncrypted` passes anything without the `enc:v1:` prefix straight through (`:99-103`). The consequence is the design property that makes the opt-in workable: **a column can hold plaintext and ciphertext rows side by side indefinitely, and both read back correctly.**

There is no migration or backfill anywhere in the repo. Turning encryption on protects **new writes only** — every task that existed before the key was set stays plaintext in Postgres until it is next edited, and nothing on the row marks which state it is in. Turning it off (or losing the key) does not corrupt anything already plaintext, but does break every row already written as ciphertext.

Both halves are pinned by tests: pre-existing plaintext titles pass through unchanged (`FieldEncryptionTest.kt:96-102`) and `encryptIfSensitive`/`encryptRequired` are no-ops with no key configured (`:104-110`).

> backend:security/FieldEncryption.kt:94-103; backend-test:security/FieldEncryptionTest.kt:91-110

### The right control for "a database dump leaves the host" is backup encryption

Field encryption's entire scope is an artifact that leaves the box — a stolen `pg_dump`, a copied volume. It is worth nothing against anyone with host or container access, because the key lives in the same `.env.docker` on the same machine as the volume (`docker-compose.yaml:77-78`).

For the owner's actual threat model — a network snooper, and someone with the device or a backup — the backend key is not the load-bearing control. The copy that realistically leaves the host is the backup, and `scripts/backup-database.sh` can encrypt it directly:

- `--encrypt age` / `TDAY_BACKUP_ENCRYPTION=age`: public-key encryption to an `age` recipient, so the backup host never holds the decryption key (`scripts/backup-database.sh:172-183,294-299`).
- `--encrypt openssl`: `openssl enc -aes-256-cbc -md sha512 -pbkdf2 -iter 600000 -salt`, passphrase passed via `-pass env:` so it never appears in `argv` or a log line (`:184-190,300-307`).
- **Default is `none`** (`:107`), and the script says so out loud in its own output: "NOT encrypted - this file contains credential hashes, API keys and task data in restorable form" (`:308-310`).
- `scripts/restore-database.sh` reverses both formats (`:125-137`), keyed off the file extension.

**Honest limitations of the backup story:**
- Nothing is scheduled. `docs/security/backups.md:18` states it outright: no timer, no cron entry, no compose service is installed by cloning the repo. Backups happen only when the operator wires one up (the doc gives a cron recipe at `:219-231`).
- Encryption is opt-in on top of that: an operator who runs the script with no flags gets an unencrypted dump, deleted after 30 days by the script's own pruning (`:106`).
- With `--encrypt openssl`, `TDAY_BACKUP_PASSPHRASE` is a single point of loss — `.env.example:250-251` says plainly that losing it means the dump is gone.

The dump captures everything in restorable form — users, credential hashes, API keys, webhooks, calendar-feed tokens, sessions, and task fields exactly as stored (`scripts/backup-database.sh:8-11`). It does **not** contain `DATA_ENCRYPTION_KEY`/`DATA_ENCRYPTION_KEYS`/`AUTH_SECRET` (`.env.example:259-262`), so a restore onto a host with a different key comes up but cannot read any `enc:v1:` row. Full procedure in `docs/security/backups.md`.

Choosing backup encryption over field encryption is the coherent position for this deployment: it covers the copy that actually travels, and it cannot take the server down.

> scripts/backup-database.sh:8-11,44-53,106-107,172-190,294-310; scripts/restore-database.sh:48,125-137; docs/security/backups.md:18,219-231; .env.example:186-187,250-251,259-262

### Credential storage

`On by default`

**Passwords** — `PBKDF2WithHmacSHA256`, 256-bit derived key, 16-byte SecureRandom salt per password (`PasswordService.kt:29-31,36,94-99`). Iterations come from `AUTH_PBKDF2_ITERATIONS`, default **310,000**, clamped to [100,000 … 2,000,000] (`AppConfig.kt:96-97`); `envInt` discards non-numeric or non-positive values back to the default (`AppConfig.kt:193-196`), so a typo cannot land below the floor. Stored self-describing as `pbkdf2_sha256$<iterations>$<saltHex>$<hashHex>` in `User.password` (`PasswordService.kt:39`, `Users.kt:12`). A legacy `saltHex:hashHex` form assumed to be 10,000 iterations is still parsed and accepted (`:28,82-88`) but sets `needsRehash`, as does any hash below the current iteration count (`:60`). PBKDF2 is not memory-hard — Argon2id or scrypt would resist GPU cracking better; this is a known, accepted trade.

**Security-question answers** use the identical KDF: normalized (lowercased, trimmed) then `passwordService.hashPassword` before landing in `user_security_questions.answer_hash` (`SecurityQuestionService.kt:289`, normalization at `SecurityQuestions.kt:35`). Only the question id and the hash are stored.

**API keys** — 32 bytes (256 bits) of `SecureRandom`, base64url unpadded (`UserApiKeyService.kt:127,251-254,264`). Wire form is `tday_<cuid>_<secret>`, returned exactly once at creation (`:151-159,262`). Storage is `s256$<sha256-hex>` plus a 4-character trailing preview (`:134-135,141-142,256-259,263,265`). Fast SHA-256 instead of PBKDF2 is deliberate and documented at `:130-133`: the secret is full-entropy CSPRNG output so stretching buys nothing, and this runs on every API request where a slow KDF is a latency and DoS amplifier. Legacy PBKDF2-hashed keys are still accepted and rotate to the fast format on regeneration (`:226-230`).

**Calendar feed tokens** — identical scheme: 32-byte SecureRandom secret, base64url (`CalendarFeedService.kt:78,235-238,247`), wire form `<cuid>_<secret>` embedded in `/calendar/<token>.ics` (`:97`), stored as `s256$<sha256hex>` + 4-char preview (`:80-81,89-90,240-248`). One active token per user; `generate` deletes any prior row first (`:85`), so rotation is implicit.

**Constant-time comparison on every secret check.** Passwords use a hand-rolled XOR-accumulate loop with an empty/length pre-check (`PasswordService.kt:101-109`). API keys, feed tokens and password proofs use `java.security.MessageDigest.isEqual` (`UserApiKeyService.kt:222-225`, `CalendarFeedService.kt:141-144`, `PasswordProof.kt:108-111`). I read all four verification paths; none compares a submitted secret with `==`/`equals`. Minor honest note: the API-key and feed-token comparisons run over hex *strings* converted to ASCII bytes rather than raw digest bytes — equivalent here because SHA-256 hex is always 64 characters.

**The one secret stored reversibly** is the webhook signing secret: the server must reproduce it to sign each payload, so it is generated as `whsec_<base64url of 32 random bytes>` (`WebhookService.kt:113,146-149`) and stored via `encryptIfSensitive("webhookSecret", secret) ?: secret` (`:121`). In the default no-key configuration that fallback stores the raw `whsec_…` verbatim in `webhook_subscriptions.secret` (`WebhookSubscriptions.kt:10`). This is the one place where running without a key means a *credential*, not just content, sits in the clear — worth knowing, though it is a credential the server hands to the operator's own webhook receiver, not a login credential.

> backend:security/PasswordService.kt:28-40,60,82-88,94-109; backend:config/AppConfig.kt:96-97,193-196; backend:services/SecurityQuestionService.kt:289; backend:security/SecurityQuestions.kt:35; backend:services/UserApiKeyService.kt:127-135,151-159,222-230,251-265; backend:services/CalendarFeedService.kt:78-97,141-144,235-249; backend:security/PasswordProof.kt:108-111; backend:services/WebhookService.kt:113,121,146-149

### What is plaintext in Postgres regardless of the encryption setting

Field encryption never touches these columns, whether or not a key is configured:

- **Usernames** (`Users.kt:11`), display names (`Users.kt:10`), avatar URLs (`Users.kt:13`).
- **List names** — `Lists.name`, table `"Project"` (`Lists.kt:9`), and floater list names.
- **Push subscription rows** — `endpoint`, `p256dh`, `auth` (`PushSubscriptions.kt:9-11`). What that costs depends on the transport column (`:14`): for `unifiedpush` the endpoint is a plain POST target (`:12-14`), so the endpoint alone is enough to push arbitrary notifications to the device; for `webpush` a VAPID-signed request is required, so an attacker also needs `VAPID_PRIVATE_KEY` — which lives in the same `.env.docker`.
- **OAuth tokens** — `refresh_token`, `access_token`, `id_token` on the `Account` table (`Accounts.kt:11,12,16`).
- **Webhook destination URLs** (`WebhookSubscriptions.kt:9`) — only the secret on line 10 is in `sensitiveFields`.
- **All task metadata** — due dates, recurrence rules (`rrule`), exception dates, time zones, priority, pinned state, list membership, completion timestamps (`Todos.kt:12-23`). A dump with every title encrypted still reveals how many tasks exist, when they are due, and how they cluster.

Two corrections to earlier documentation: **checklist step titles are no longer plaintext** (`TaskSteps.title` is encrypted at `TaskStepService.kt:73` and decrypted at `:146`), and **task titles are no longer plaintext** when a key is set. And the standing caveat: in the default configuration no key is set, so all of the above *plus* every title and description is plaintext in Postgres. That is the chosen configuration, not a regression.

Unrelated vestigial columns: `Users.protectedSymmetricKey` and `Users.enableEncryption` (`Users.kt:18-19`) survive from the original Next.js app. Both are still writable and readable over the API — `GET` echoes them (`UserService.kt:51-52`), `PATCH /users?enableEncryption=` flips the boolean (`UserRoutes.kt:68-70`, `UserService.kt:59-65`), and `protectedSymmetricKey` accepts opaque client text and stores it raw (`UserRoutes.kt:74-76`, `UserService.kt:72`). Nothing anywhere reads either value to decide whether to encrypt anything — a full-tree grep across backend, web, iOS and shared finds no other consumer. `enableEncryption` is not a per-user encryption switch and must not be read as one.

> backend:db/tables/Users.kt:10-13,18-19; backend:db/tables/Lists.kt:9; backend:db/tables/PushSubscriptions.kt:9-14; backend:db/tables/Accounts.kt:11-16; backend:db/tables/WebhookSubscriptions.kt:9-10; backend:db/tables/Todos.kt:10-23; backend:services/TaskStepService.kt:73,146; backend:services/UserService.kt:51-52,59-72; backend:routes/UserRoutes.kt:68-76

---

## Network exposure, TLS & HTTP security headers

### Loopback-only host port binding (exposure model)

`On by default`

docker-compose publishes the backend as "${TDAY_HOST_BIND:-127.0.0.1}:${TDAY_HOST_PORT:-2525}:8080", so with no root .env override Docker binds the host side to 127.0.0.1 only. Nothing on the LAN or the internet reaches the container directly; the only path in is a process on the host itself (cloudflared). Protects against internet-wide port scanning, LAN neighbours and cloud-firewall misconfiguration — there is no inbound listener to find. It does NOT make the service unreachable or unfindable: with a Cloudflare Tunnel running, https://tday.<domain> is reachable from anywhere, and the hostname is not a secret (Cloudflare-issued certificates land in public Certificate Transparency logs). "No open ports" here means "no unauthenticated attack surface below HTTP", not "nobody can find it". CORRECTION vs first pass: .env.example:12 and :15 are COMMENTED-OUT documentation lines, not settings — the real default comes from the compose `:-127.0.0.1` fallback. Honest caveat for this checkout: the repo-root .env sets TDAY_HOST_BIND=0.0.0.0 (.env:1), overriding the safe default; the deployed server's value must be confirmed separately, because with 0.0.0.0 the container is exposed on every host interface and the CF-Connecting-IP trust below becomes spoofable.

> docker-compose.yaml:75-76; .env:1-2 (TDAY_HOST_BIND=0.0.0.0); .env.example:7-15 (commented docs); docs/remote-access/cloudflare-tunnel.md:5,13,21-25,31,109; SECURITY.md:79-80

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

> docker-compose.yaml:6,28,70-74,95-98; Dockerfile.backend:37 (addgroup/adduser tday), 42 (USER tday), 43 (EXPOSE 8080)

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

> backend:plugins/Routing.kt:28-35,56-65; infra policy at plugins/RateLimiting.kt:55-64; routes/CalendarFeedRoutes.kt:12-33; routes/AppleAppSiteAssociationRoutes.kt:15-36 and 54-62 (empty-array fallback); healthcheck at docker-compose.yaml:89-94

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

> backend:Application.kt:54 (host="0.0.0.0", no sslConnector); docker-compose.yaml:76; docs/remote-access/cloudflare-tunnel.md:13,21-25,169; SECURITY.md:81-83

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

> docker-compose.yaml:97-98; absence confirmed by grep for cap_drop across the whole 106-line file (single hit); port default at backend:config/AppConfig.kt:88

### no-new-privileges on the backend

`On by default`

`security_opt:` / `- no-new-privileges:true` sets the kernel NO_NEW_PRIVS bit for the container's process tree, so a setuid/setgid binary or file capability inside the image cannot raise privileges after exec — a compromised backend process cannot re-escalate through an suid helper. Verified present on tday-backend only; grep for `security_opt` across the file returns exactly this one hit, so database, ollama and ollama-model-setup do not have it.

> docker-compose.yaml:95-96

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

### Production deploys the CI-published registry image, not a host build

`On by default`

The compose backend service has **no** `build:` block; its image is `${TDAY_BACKEND_IMAGE:-ghcr.io/ohmzi/tday:latest}` (60-67), and `scripts/deploy-release.sh` pins that variable to `ghcr.io/ohmzi/tday:v<version>` read from root `version.json` before recreating the container. Building from a checkout is possible only by explicitly layering `docker-compose.build.yaml`, which tags its output `tday-backend:local` so it can never be confused with a released image. Security consequence stated plainly: production now runs exactly the artifact that passed the CI gate (`build-and-release` `needs: lint-and-test`), which closes the previous hole where the deploy host ran code that never had to pass a test — but it moves trust to GHCR, so a compromised registry account or a compromised Actions run can change what production runs on the next deploy. Neither the image nor the release carries a signature or provenance attestation (no `provenance:`/`sbom:` inputs on the build-push step, no cosign), and the deploy resolves a mutable tag rather than a digest, so there is no way to verify at deploy time that `:v0.7.2` is the same bytes CI pushed. Pinning `TDAY_BACKEND_IMAGE` to an `@sha256:` digest is supported by the same variable and is the cheap hardening step.

Deploying is a deliberate act on the host, not an effect of merging: merging to `master` publishes the image and nothing else. A published release that is never deployed is its own hazard, because `version.json` sets `compatibility.mode: "exact"` — mobile clients that take the in-app update are locked out of a server still on the old version.

> docker-compose.yaml:60-67; docker-compose.build.yaml:24-32; scripts/deploy-release.sh; registry push at .github/workflows/release.yml:112-125

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

> docker-compose.yaml:80-83 (TZ key at :84); .env (no TZ key); .github/workflows/pr-gate.yml:71-72

### Database credentials — overridable, weak by default

`Needs config`

Postgres credentials are `POSTGRES_USER: ${POSTGRES_USER:-myuser}`, `POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-mypass}`, `POSTGRES_DB: ${POSTGRES_DB:-mydb}` — real overrides exist, but the fallbacks are the well-known weak pair, and the backend's DATABASE_URL in .env.docker still ships as `postgresql://myuser:mypass@database:5432/mydb`, so both must be changed together (the inline comment at 13-15 says exactly this). In this checkout the root .env sets only TDAY_HOST_BIND and TDAY_HOST_PORT, so the myuser/mypass defaults are in force here. Practical exposure is limited by the missing published port: using these credentials requires already having code execution inside a container on the compose network or on the host. Residual risk is lateral — anything the owner later attaches to that default bridge can log into Postgres with guessed credentials.

> docker-compose.yaml:16-18 with comment at :13-15; .env.docker:80; .env (2 lines, neither a POSTGRES_* key)

### Build-arg separation for public vs private telemetry config (recently hardened)

`Needs config`

Vite inlines VITE_* values at build time, so browser Sentry config cannot come from env_file. The local-build override's build block passes `VITE_SENTRY_DSN: ${VITE_SENTRY_DSN:-}` and `VITE_SENTRY_TRACES_SAMPLE_RATE: ${VITE_SENTRY_TRACES_SAMPLE_RATE:-}` as build args (docker-compose.build.yaml:29-32, with the comment at :30 stating both are public values), and the Dockerfile receives them as ARG/ENV in the frontend stage only (8-13). The security-relevant property is the discipline: only already-public values (a browser-visible DSN, a sample rate) become build args, since build args land in `docker history`. The backend's own SENTRY_DSN stays in env_file at runtime (.env.docker:~SENTRY_DSN, blank by default). Both defaults are empty, so telemetry is off unless the operator sets them. Addition the first pass missed: the CI publish step passes NO build args at all (release.yml:113-125 has only context/file/push/tags/labels/cache), so the GHCR image's SPA has no Sentry DSN and the GIT_SHA build-id arg falls back to its `dev` default (Dockerfile.backend:6-7).

> docker-compose.build.yaml:29-32 with comment at :30; Dockerfile.backend:6-7, :8-13; .github/workflows/release.yml:113-125

### Retention scheduler ships in dry-run (recently hardened)

`Needs config`

RetentionScheduler purges eventlog / auththrottle / authsignal / cronlog on a loop whose `TICK_INTERVAL` is `Duration.ofHours(6)` (RetentionScheduler.kt:162, `delay(TICK_INTERVAL.toMillis())` at :69), with per-table windows RETENTION_EVENTLOG_DAYS=90, RETENTION_AUTHTHROTTLE_DAYS=30, RETENTION_AUTHSIGNAL_DAYS=180, RETENTION_CRONLOG_DAYS=90 `.coerceAtLeast(7)` (the floor exists because cronlog holds the reminder scheduler's last-run bookmark — the comment at AppConfig.kt:156-157 says trimming it too hard drops reminders). It is launched at startup from the application module. Critically for a self-hosted deployment, `RETENTION_DRY_RUN` defaults to "true" (AppConfig.kt:164) — the first release logs what it would delete and deletes nothing, so an operator reads a cycle's output before a DELETE runs against their only copy of the data. Data minimisation is therefore NOT in effect on a default install; the operator must set RETENTION_DRY_RUN=false.

> backend:config/AppConfig.kt:156-164; interval at backend:services/RetentionScheduler.kt:69 and :162; launched at backend:Application.kt:94 and :114-120

### Backend healthcheck and readiness semantics

`Partial`

The compose healthcheck is `["CMD", "wget", "--spider", "-q", "http://127.0.0.1:8080/health"]`, interval 30s, timeout 5s, retries 3, start_period 40s. It validates less than the inline comment above it (lines 88-89) claims: the /health handler is a static `call.respond(mapOf("status" to "ok"))` with no database round-trip. It proves the HTTP listener answers. It transitively proves the DB was reachable and Flyway migrations succeeded AT BOOT, because `dbConfig.init()` runs inside a try that logs and rethrows before the server serves anything — a migration or connection failure means the process never comes up. It does NOT detect a Postgres that dies after boot: the backend keeps reporting healthy while every request 500s. Caveat on the tooling: the healthcheck assumes `wget` is on PATH in eclipse-temurin:21-jre-alpine (busybox wget is expected on an alpine base) — I could not run the image to confirm, so treat that as unverified.

> docker-compose.yaml:89-94; handler at backend:plugins/Routing.kt:28-30; boot-time DB gate at backend:Application.kt:65-71

### Host port binding defaults to loopback

`Partial`

The backend's only port mapping is `"${TDAY_HOST_BIND:-127.0.0.1}:${TDAY_HOST_PORT:-2525}:8080"`. The DEFAULT binds to loopback, the correct pairing with the Cloudflare Tunnel model (the tunnel connects out to 127.0.0.1:2525; nothing is reachable from the LAN or the internet directly). Reported as partial, not on-by-default, because this checkout's root .env overrides it — the file contains exactly two lines, `TDAY_HOST_BIND=0.0.0.0` and `TDAY_HOST_PORT=2525` — so on whatever host uses that env file the backend is published on every host interface and is reachable from the LAN without traversing the tunnel. .env is gitignored (git ls-files shows only .env.example and tday-backend/.env.example tracked), so the deploy host may carry a different value; verify there specifically.

> docker-compose.yaml:75-76; override at .env:1-2 (whole file)

### Image pinning and pull policy

`Partial`

Base images are pinned by major-version tag, never by digest: `postgres:15` (compose:3), `node:20-alpine` (Dockerfile:1), `eclipse-temurin:21-jdk-alpine` (Dockerfile:19), `eclipse-temurin:21-jre-alpine` (Dockerfile:36). That prevents a surprise major upgrade but still floats patch content, so a rebuild can silently change the base layer. Ollama is fully floating: `ollama/ollama:latest` with `pull_policy: always` on BOTH the ollama service (23-24) and ollama-model-setup (44-45), so every `docker compose up --profile ai` fetches whatever upstream currently tags latest. The app image is `tday-backend:latest` (compose:68), built locally. A grep for `@sha256` across docker-compose.yaml returns nothing — no digest pin anywhere.

> docker-compose.yaml:3, :23-24, :44-45, :68; Dockerfile.backend:1, :19, :36; `grep @sha256 docker-compose.yaml` → no matches

### Secret delivery: env_file plus partial *_FILE indirection

`Partial`

Default path is `env_file:` / `- .env.docker` — the whole secret set (AUTH_SECRET, CRONJOB_SECRET, DATABASE_URL, DATA_ENCRYPTION_KEY(S), AUTH_CREDENTIALS_PRIVATE_KEY, VAPID keys, TDAY_PROBE_ENCRYPTION_KEY) is injected as process environment variables, readable by anyone who can run `docker inspect tday_backend`, `docker compose config`, or read /proc/<pid>/environ on the host. Env vars are not a confidentiality boundary against host-level access. A stronger, opt-in path exists: `AppConfig.secret(envVar, fileEnvVar)` prefers the direct env var and otherwise reads and trims the contents of the path in `<NAME>_FILE`, printing to stderr on read failure and returning null. IMPORTANT CORRECTION to the first pass: the indirection is implemented for exactly EIGHT secrets — DATABASE_URL_FILE (:89), AUTH_SECRET_FILE (:91), AUTH_CREDENTIALS_PRIVATE_KEY_FILE (:100), DATA_ENCRYPTION_KEY_FILE (:102), DATA_ENCRYPTION_KEYS_FILE (:103), TDAY_PROBE_ENCRYPTION_KEY_FILE (:147), VAPID_PUBLIC_KEY_FILE (:152), VAPID_PRIVATE_KEY_FILE (:153) — but .env.docker ALSO documents `CRONJOB_SECRET_FILE` (:29), `AUTH_CAPTCHA_SECRET_FILE` (:126) and `DATA_ENCRYPTION_AAD_FILE` (:158), and AppConfig has no `_FILE` reader for any of those three (DATA_ENCRYPTION_AAD is read by plain `env("DATA_ENCRYPTION_AAD")` at :104; a grep for CRONJOB_SECRET/AUTH_CAPTCHA_SECRET in AppConfig.kt returns no `secret(` call). An operator who follows those three comments would get a silently unset secret. All the _FILE lines in .env.docker ship commented out. Neither .env nor .env.docker is tracked in git.

> docker-compose.yaml:77-78; reader at backend:config/AppConfig.kt:204-218; call sites :89, :91, :100, :102, :103, :147, :152, :153; plain-env AAD at :104; unimplemented commented examples at .env.docker:29, :126, :158; `git ls-files | grep .env` → only .env.example and tday-backend/.env.example

### Post-deploy verification in the deploy script

`On by default`

`scripts/deploy-release.sh` runs under `set -euo pipefail` and fails the deploy — non-zero exit, warnings on stderr — if the container never reaches `healthy` within `--timeout` (default 180s), or if `/version.json` and `/api/mobile/probe` do not report the version that was deployed, checked on loopback and, when `--url` is given, through the public ingress. That closes the previous gap where the health check was suffixed with `|| true` and could not fail anything. It also compares the Flyway migrations baked into the running image against the target image's and refuses to proceed when the target adds any, unless `--backup` (take a dump now, via scripts/backup-database.sh) or `--skip-backup` is passed — the only automated guard that exists between a deploy and an irreversible migration. What it still does not do: roll back. A failed verification leaves the new container running; recovery is a manual redeploy of the previous tag.

> scripts/deploy-release.sh

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
- **No image signing, digest pinning or provenance on the deploy** — RESOLVED IN PART: the sshpass-based `scripts/redeploy-remote-backend.sh`, which accepted an SSH password via `--password`/`SSH_PASSWORD`, enforced no host-key pinning and targeted a hard-coded LAN address, has been removed; deploys now run locally on the host via `scripts/deploy-release.sh` with no SSH surface at all. What replaces that risk is registry trust: the deploy resolves the mutable tag `ghcr.io/ohmzi/tday:v<version>` with no cosign verification, no `provenance:`/`sbom:` attestation from the build-push step, and no digest pin, so anyone who can push to that GHCR repo (a compromised PAT, a compromised Actions run) controls what the next deploy runs. `TDAY_BACKEND_IMAGE` accepts an `@sha256:` digest, so pinning is available without a code change.
- **No automatic rollback on a bad deploy** — IMPROVED, not solved. Every release is now a distinct immutable tag in GHCR, so recovery is `./scripts/deploy-release.sh --version <previous>` — seconds, not a rebuild from source, and it no longer depends on the host's source tree being sound. But nothing performs it: `deploy-release.sh` exits non-zero on a failed verification and leaves the new container running, and there is no health-gated automatic revert. Flyway still runs at boot with `baselineOnMigrate(true)` / `baselineVersion("2")` and `validateOnMigrate(false)`, so rolling the code back does not roll the schema back, and a checksum mismatch on an edited-but-already-applied migration will not stop startup. The migration/backup guard in the deploy script is what makes the irreversible case visible before it happens, not something that undoes it.

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

`GET /health` is unauthenticated (required for the Docker healthcheck at docker-compose.yaml:89-94) and responds with exactly `{"status":"ok"}` — no version string, no database state, no dependency list, no hostname, no uptime. It is also covered by the `infra` request rate-limit policy (RateLimiting.kt:55-64), so it cannot be used as an unmetered liveness oracle.

> backend:plugins/Routing.kt:28-30; backend:plugins/RateLimiting.kt:55-64; docker-compose.yaml:89-94

### Backend Sentry SDK — inert unless a DSN is supplied

`Needs config`

`Sentry.init` runs unconditionally at process start, but `options.dsn = config.sentryDsn.orEmpty()` and `SENTRY_DSN` is read with `env("SENTRY_DSN")`, which returns null when unset **or blank**. An empty DSN leaves the SDK inert. `docker-compose.yaml` does not define `SENTRY_DSN`; the backend's env comes from `env_file: .env.docker`, and the shipped template `.env.example:55` sets `SENTRY_DSN=` (empty). So out of the box nothing leaves the host. To disable after enabling: blank or unset `SENTRY_DSN` and restart the container. Other fixed options: `environment` = "production"/"development", `release` = `tday-backend@<version>`, `serverName` = "tday-backend" (a constant, not the real hostname).

> backend:Application.kt:38-44; backend:config/AppConfig.kt:167 and :176-181 (env helper returns null on blank); docker-compose.yaml:77-86 (env_file, no SENTRY_DSN); .env.example:55 (empty)

### Web Sentry SDK — off unless a build-time DSN is supplied

`Needs config`

`Sentry.init({ dsn: import.meta.env.VITE_SENTRY_DSN ?? "" })`. Because this is a Vite build-time variable the DSN is baked into the SPA bundle at image build; `docker-compose.build.yaml:29-32` passes `VITE_SENTRY_DSN: ${VITE_SENTRY_DSN:-}` and `VITE_SENTRY_TRACES_SAMPLE_RATE` as build args (recently added), and `Dockerfile.backend:10-13` forwards them into the frontend stage. `.env.example:57` ships it empty, so the built bundle carries an empty DSN and the browser sends nothing. Disabling after the fact requires rebuilding the image without the arg — unlike the backend, it is not a restart-time toggle. Also set: `sendDefaultPii: false`, `release: tday-web@<version>`, `tracePropagationTargets: [/^\/api(\/|$)/]` so trace headers attach only to same-origin API calls and never to third-party requests.

> tday-web/src/main.tsx:25-31; docker-compose.build.yaml:29-32; Dockerfile.backend:8-13; .env.example:57

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

> backend:security/FieldEncryption.kt:21-26 (sensitiveFields at :25), :35-54, :85-88 (fail-open at :86), :96-139; docker-compose.yaml:77-78 (env_file); .env.example:170-177

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

The Android app stores its offline task cache in a SQLCipher-encrypted database, refuses any certificate the system CA store cannot vouch for unless the user has pinned it out of band, blocks certificate enrollment entirely on publicly routable hosts, keeps task content out of screenshots and the recents thumbnail by default, and offers an optional biometric app lock that is **off by default**. What it does **not** do is defend a rooted or running-and-unlocked device: every control below is an at-rest or over-the-shoulder control, not a runtime-compromise control.

### Local database encryption: SQLCipher over the whole Room file

The offline cache is a SQLCipher database, not plain SQLite. `net.zetetic:sqlcipher-android:4.17.0` (the androidx-compatible artifact, so it plugs into Room directly) plus `androidx.sqlite:sqlite:2.4.0` `app/build.gradle.kts:193-194`. The native library is loaded once per process before any database is opened, `System.loadLibrary("sqlcipher")` `DatabaseModule.kt:41`, and Room is wired to it by handing `SupportOpenHelperFactory(passphrase)` to `Room.databaseBuilder(...).openHelperFactory(...)` `DatabaseModule.kt:58-66`. No `PRAGMA cipher_*` is overridden anywhere in the app, so SQLCipher's own defaults apply.

The encrypted file is `tday_offline_cache_encrypted.db` `LegacyPlaintextCacheMigration.kt:14`, replacing the pre-encryption `tday_offline_cache.db` `:11`.

Coverage is the **whole database file**, not a field-level scheme: SQLCipher encrypts every page, so all eight tables declared on `TdayDatabase` `TdayDatabase.kt:6-22` are covered together with their indices and the `-wal`/`-shm` sidecars — `cached_todos`, `cached_floaters`, `cached_lists`, `cached_floater_lists`, `cached_completed`, `cached_completed_floaters`, `pending_mutations`, `sync_metadata`. That includes every task **title and description** on scheduled tasks, floaters and completed history `Entities.kt:19-20,41-42,87-88,106-107`, and the title/description carried by queued offline edits `Entities.kt:124-125`.

**What this protects:** a powered-off or locked handset whose `/data` partition is imaged, an extracted flash dump, a stolen device. Combined with `android:allowBackup="false"` / `android:fullBackupContent="false"` and no `dataExtractionRules` anywhere in `app/src` (grep-confirmed) `AndroidManifest.xml:15,17`, there is also no cloud-backup, `adb backup` or device-to-device-transfer copy of it.

**What this does not protect:** anything running as the app's own UID. See the key section below.

**One data-loss caveat, not a confidentiality one.** Schema v7→v8 ships a real `Migration` because `pending_mutations` is not re-fetchable `DatabaseModule.kt:19-28,70`, but `fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5, 6)` `DatabaseModule.kt:71` means a device still on a pre-v7 schema has its cache — including any unsynced mutations — destroyed rather than migrated.

### Key management: Keystore-wrapped, deliberately not user-authentication-bound

The passphrase is 32 bytes from `SecureRandom`, generated once on first launch and hex-encoded to 64 lowercase characters `DatabasePassphraseStore.kt:46-58, 76-77`. Hex rather than raw bytes because SQLCipher takes the passphrase as a NUL-terminated C string — raw random bytes would be silently truncated at the first zero byte, roughly a 1-in-8 chance per 32-byte key `DatabasePassphraseStore.kt:67-74`; the hex alphabet also makes the value safe to inline in the one `ATTACH ... KEY` statement that cannot take a bound parameter, and that invariant is asserted at runtime, not assumed `LegacyPlaintextCacheMigration.kt:403`. Encoding round-trip and quote-freedom are unit-tested `app/src/test/java/com/ohmz/tday/compose/core/data/db/DatabasePassphraseTest.kt:11-45`.

It is stored in `EncryptedSharedPreferences` (file `tday_database_key`, entry `offline_cache_passphrase_v1`) with keys under `AES256_SIV` and values under `AES256_GCM`, wrapped by a `MasterKey` built with `KeyScheme.AES256_GCM` — i.e. a 256-bit AES-GCM key that lives in the Android Keystore and never leaves it `DatabasePassphraseStore.kt:27-37, 60-64`. `minSdk 26` `app/build.gradle.kts:61`, so Keystore is always present. `setRequestStrongBoxBacked` is not called, so this is a standard Keystore key (TEE-backed where the device provides one), not StrongBox. The write uses `commit()`, not `apply()`: a process killed between generating the key and opening the database would otherwise leave an encrypted file nothing could ever open `DatabasePassphraseStore.kt:54-56`.

**The deliberate limit — the key is not bound to user authentication.** `MasterKey.Builder` does not call `setUserAuthenticationRequired(true)` `DatabasePassphraseStore.kt:27-29`. This is a conscious choice, documented in the source `DatabasePassphraseStore.kt:17-23`: the home-screen widget renders while the device is locked, and a user-authentication-bound Keystore key is unusable in that state, which would leave the widget permanently blank. Two honest consequences:

- **Any code running as the app's UID can ask the Keystore to unwrap the passphrase**, so an attacker with root on a running device — or with a device that is unlocked and in their hands — can read the full task database.
- The key is exercised **with no user presence at all**: every widget refresh starts the app process and opens the database on a locked screen. The window in which the plaintext database is reachable is therefore "whenever the device is powered on", not "whenever the user is using the app".

Encryption at rest here defeats file-dump and offline-forensics attacks; it does not defeat live-device compromise. The optional app lock is a separate control and is deliberately *not* wired to this key, for the same widget reason `AppLock.kt:36-38`.

### Migrating the legacy plaintext cache

Devices that ran a pre-encryption build still hold `tday_offline_cache.db` in plaintext. That file is not disposable — alongside re-fetchable task rows it holds `pending_mutations` the server has never seen — so it is **copied**, never discarded, using SQLCipher's `ATTACH` + `sqlcipher_export` recipe with `user_version` carried across by hand so Room does not mistake a populated database for a brand-new one `LegacyPlaintextCacheMigration.kt:384-421`. It runs synchronously before the Room builder, so the cost is one slow first launch `DatabaseModule.kt:52-56`.

Progress is **persisted state, not file existence** — `NOT_STARTED` / `FAILED` / `COMPLETED` plus an attempt counter in plain prefs `tday_cache_migration_state` `LegacyPlaintextCacheMigration.kt:36-45, 205-230`. File existence would be wrong: a failed export leaves the plaintext file in place and Room then creates a fresh encrypted file moments later, which would read as "already migrated" forever `:30-35`.

Ordering guarantees, each covered by unit tests in `app/src/test/java/com/ohmz/tday/compose/core/data/db/LegacyCacheMigrationStateTest.kt`:

- The attempt is counted and persisted as `FAILED` **before** the export runs, so a process killed mid-export still burns an attempt and a reproducible crash cannot retry on every launch forever `:175-182`.
- `deleteLegacyFile()` is unreachable unless `export()` returned normally `:184-191`. **On any failure the plaintext file is left exactly as it was** and the half-written encrypted file is deleted `:287-294`.
- Retrying stops after `MAX_LEGACY_MIGRATION_ATTEMPTS = 5` `:24, 44-45`. After that the app stops trying and the plaintext file stays on disk.
- A retry may only reuse the encrypted file if that file is provably disposable: never when `pending_mutations` is non-empty, and in **local mode** never when it holds any content rows at all, because local mode empties the mutation queue on every sync so an empty queue there means "nothing to sync", not "the server has a copy" `:98-105`. An `UNSET` data mode gets the strict (local-mode) rule too `:276-278`. If it is not disposable the migration **defers** to a later launch rather than destroying either copy `:135-139`.
- Deletion of the plaintext file is a best-effort overwrite pass over the file and its `-wal`/`-shm`/`-journal` companions before unlinking, so the titles do not stay trivially recoverable in freed blocks; on wear-levelled flash this is a mitigation, not a guarantee `:428-455`.

**What happens to unsynced pending mutations on failure — stated honestly.** They are not deleted, but they are also not *reachable*: after a failed export Room opens a fresh empty encrypted database, so those queued offline edits sit in the stranded plaintext file, invisible to the app, until a retry succeeds. In server mode the visible cost is limited to edits that never reached the server (content rows re-sync). **In local mode the visible cost is the user's entire task list**, and the retry can only run while the new encrypted cache is still empty — once the user starts entering tasks into it, `canDiscardEncryptedCache` returns false and the migration defers indefinitely, leaving the old data stranded permanently. Because that state is otherwise invisible, Settings renders a persistent warning whenever the plaintext file is still on disk `LegacyPlaintextCacheMigration.kt:232-241`, `SettingsScreen.kt:1428-1459` — a `Log.e` no one reads is not a way to tell someone their data is exposed.

### Certificate handling: system CAs, no user CAs, LAN-only enrollment

Every OkHttp connection on the shared Hilt-provided client goes through `ServerTrustManager`, installed as the SSL socket factory's trust manager `NetworkModule.kt:59-65`. Order of operations `ServerTrustManager.kt:116-175`:

1. **System (public CA) validation runs first.** A chain the platform trusts is accepted with no pinning at all, so the owner's Cloudflare-fronted host connects with zero prompts and routine certificate renewals never false-trip `:124-134`.
2. A stored pin is **not cleared** by a successful system validation. This is deliberate and closes a downgrade: this hook runs *before* OkHttp's hostname check, so an on-path attacker holding any valid certificate for a host they own could otherwise force the pin to be dropped and turn the next attempt from "certificate changed" into a first-run "trust this certificate?" prompt `:125-133`. Clearing stays an explicit user action ("Reset trusted server") `ServerConfigRepository.kt:133-135`.
3. If the system rejects the chain, the pure decision function `decideServerTrust` decides `ServerTrustDecision.kt:42-68`:
   - no derivable fingerprint → refuse `:50`;
   - a stored pin exists → exact (case-insensitive) match accepts, anything else is `RejectMismatch` `:52-58`, surfaced to the setup screen as a typed "certificate changed" error `ServerTrustManager.kt:155-161`. **There is no trust-on-first-use and no automatic re-enrollment on mismatch**;
   - **no stored pin and the host is not private/LAN → `RejectPublicHost`** `:61`;
   - private/LAN host with a one-shot, user-confirmed fingerprint that matches exactly → enroll and pin `:63-65`;
   - otherwise refuse, recording the offered fingerprint so the UI can show it and ask `:67`, `ServerTrustManager.kt:162-165`.

**The LAN-only enrollment rule.** A self-signed or privately issued certificate is a LAN situation — the owner's own box on their own network. A publicly routable host that fails public CA validation is either misconfigured or being intercepted, so offering a tappable "trust this certificate" button there would hand an attacker on hostile wifi exactly what they need. On the `RejectPublicHost` path the trust manager therefore **records nothing at all**, because the setup screen builds its enrollment prompt out of that record — leaving it empty makes the failure unrecoverable in-app by construction rather than by UI discipline `ServerTrustManager.kt:166-172`. An unresolvable host fails closed into the public case `ServerTrustDecision.kt:60-61`. "Private" means RFC1918 IPv4, loopback, `169.254/16` link-local, the emulator alias `10.0.2.2`, `localhost`, `.local` mDNS names, and the IPv6 equivalents (`::1`, `fc00::/7`, `fe80::/10`); every other DNS name is public `ServerTrustDecision.kt:78-124`. An **already-stored** pin is still honoured on any host, public included — it was established out of band and remains a fail-closed comparison, not a prompt. All of this is unit-tested, including "public host never gets the option to trust an unverifiable certificate", "approving a public host's certificate still refuses to enroll it", "an unknown host is treated as public", and "hosts that only look private are still public" `app/src/test/java/com/ohmz/tday/compose/core/network/ServerTrustDecisionTest.kt:104-231`.

Enrollment is two-phase and single-use: the authorisation names the exact fingerprint the user just read on screen, is consumed by the very next handshake, and is cancelled if the retry fails `ServerTrustManager.kt:83-89`, `:143`, `ServerConfigRepository.kt:142-152`. The fingerprint shown and stored is the SHA-256 of the leaf certificate's public key, uppercase colon-hex `ServerTrustManager.kt:199-202`, held in `EncryptedSharedPreferences` under `cert_fp_<trustKey>` `SecureConfigStore.kt:162-183`.

**User-installed CAs are not trusted.** There is no `android:networkSecurityConfig` attribute in the manifest and no `res/xml/network_security_config.xml` (grep for `networkSecurityConfig` over `app/src` returns nothing; `res/xml` holds only the six widget-info files, `locales_config` and `shortcuts`). With `targetSdk = 35` `app/build.gradle.kts:62` and no config, the platform default applies: system trust anchors only. A CA installed into the Android **user** credential store — the usual mitmproxy/Burp path — is not trusted for this app's traffic. Honest limit: this says nothing about a CA planted in the **system** store on a rooted or OEM-modified device, and nothing about the Cloudflare edge, which terminates TLS and sees plaintext by design.

**Cleartext.** `android:usesCleartextTraffic` is `false` in `defaultConfig` and in release, `true` only in the debug build type `AndroidManifest.xml:24`, `app/build.gradle.kts:65,102,106`, so a release APK cannot open a plaintext HTTP socket through the platform HTTP stack. On top of that, `ensureSecureTransport` rejects any non-HTTPS server URL unless the build is a debug build *and* the host is private/LAN — the same `isPrivateNetworkHost` predicate the trust manager uses, shared on purpose so the two rules cannot drift `ServerConfigRepository.kt:200-207`; URL normalization applies the same predicate via `canUseLocalHttp` `SecureConfigStore.kt:306-311`.

**Two honest gaps in the TLS story.** (a) The hostname verifier accepts either OkHttp's default verification **or** a pinned session `NetworkModule.kt:105-110`; for a pinned self-signed certificate the hostname check is therefore bypassed and the public-key pin is the identity check `ServerTrustManager.kt:101-114`. That is sound — the pin is keyed by host:port and compared against the presented key — but it is a pin, not a name check. (b) The in-app updater's two bare `OkHttpClient` instances (`GitHubReleaseRepository.kt:15` and `InAppApkUpdater.kt:42`) do **not** go through `ServerTrustManager`; they use platform defaults against GitHub over public CAs. They are still covered by the process-wide cleartext block.

### FLAG_SECURE and the optional app lock

**Screenshot / recents protection: on by default.** `AppSecurityPreferenceStore.isScreenshotProtectionEnabled()` defaults to `true` `AppSecurityPreferenceStore.kt:26-27`, and `applyScreenshotProtection()` sets or clears `FLAG_SECURE` from `onStart` so a toggle takes effect on the next foreground without a restart `:63-71`. It is applied by **every** activity that can display or accept task text, not just the main one: `MainActivity.kt:79`, the widget's create-task sheet `WidgetCreateTaskActivity.kt:66-68`, and the share receiver `ShareReceiverActivity.kt:62-64`. This keeps task content out of screenshots, screen recordings, non-secure display mirroring, and the app-switcher thumbnail. Android offers no way to suppress only the recents thumbnail, so it necessarily blocks deliberate screenshots too — which is why it is a user-facing setting rather than a hard-coded flag `SettingsScreen.kt:1369-1389`. It does nothing about a photograph of the screen, it is a per-window flag so it does not extend to the Glance widget rendered by the launcher, and a user who turns it off loses it everywhere.

**Biometric app lock: opt-in, off by default.** `isAppLockEnabled()` defaults to `false` and stays off unless the user turns it on `AppSecurityPreferenceStore.kt:33-45`, `SettingsScreen.kt:1391-1426`. When on, `MainActivity` shows an opaque overlay layered over the real UI (so nav and view-model state survive) that swallows all pointer input `MainActivity.kt:66-73`, `AppLock.kt:86-118`, and drives a `BiometricPrompt` `MainActivity.kt:95-133`. Authenticators are `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` on API 30+, falling back to `BIOMETRIC_WEAK or DEVICE_CREDENTIAL` on API 28–29 where the framework rejects the strong combination `AppLock.kt:40-45`. A cold start always locks; returning from the background re-locks after a 2-second grace so a share sheet or system dialog does not demand a fingerprint every time `AppLock.kt:54-65`, `MainActivity.kt:77-88`. Cancelling the prompt leaves the app locked with a retry button `MainActivity.kt:117-121`.

Honest limits on the app lock, all deliberate:
- **It is a UI gate only.** No key is bound to the authentication result, so it does not make any data more encrypted than it already is `AppLock.kt:36-38`, `AppSecurityPreferenceStore.kt:36-40`.
- **It fails open** when the device has no screen lock left to authenticate against (the user removed it after enabling the setting): rather than trapping someone out of their own tasks with no recovery short of reinstalling, the overlay is dismissed `AppLock.kt:66-77`, `MainActivity.kt:99-105`. Enabling the setting is likewise refused up front on a device that cannot satisfy it `SettingsScreen.kt:1409-1412`.
- **It gates `MainActivity` only.** The widget create-task sheet and the share receiver open without it; they accept new text rather than displaying the existing task list, but they are not behind the lock.
- It is irrelevant to anyone with `adb`, root, or the ability to read the app's data directory.

### What is still exposed on Android

- **A rooted or unlocked running device.** The database passphrase is deliberately not authentication-bound, so anything running as the app's UID can unwrap it and read every task. This is the acknowledged cost of keeping the widget alive on a locked screen.
- **Widget content in the launcher.** Glance renders task titles into RemoteViews handed to the home-screen launcher process `TodayTasksWidget.kt:41-70`. Once there they are visible wherever the launcher shows them, including lock-screen widgets on launchers that support them. `FLAG_SECURE` does not apply to another process's window, and there is no per-widget "hide content" setting. The source of that content is the encrypted cache `TodayTasksWidget.kt:46-49`; the rendering is not protected.
- **Notifications.** Reminder text goes through `NotificationManager` into the shade and the lock screen — the task title is the notification's content title, with no `setVisibility`/`setPublicVersion` override `TaskReminderReceiver.kt:70-77` — and the title also travels in the `AlarmManager` PendingIntent extras held by `system_server` `TaskReminderScheduler.kt:81-85, 123-127`. Neither is covered by database encryption.
- **`repeat_suggestion_prefs` still holds normalized task titles in cleartext.** `RepeatSuggestionDismissalStore` writes up to 200 normalized titles into a plain `MODE_PRIVATE` SharedPreferences file, with an in-code comment asserting they are "not sensitive" `RepeatSuggestionDismissalStore.kt:5-33`. This is the same leak the web client fixed by switching to salted HMAC digests `tday-web/src/lib/repeatSuggestionDismissal.ts`; **Android has not been fixed and remains an outstanding gap.** The file is inside the app sandbox and excluded from backups, so it is reachable only by root/forensics — but so is the database, and the database is encrypted while this is not.
- **A stranded legacy plaintext cache.** If the one-time migration is abandoned (5 failures) or deferred indefinitely, `tday_offline_cache.db` stays on disk in the clear. Settings warns about it; nothing removes it automatically.
- **Other plain-prefs state** — reminder options `ReminderPreferenceStore.kt`, quiet hours `QuietHoursPreferenceStore.kt`, the day-ahead option `DayAhead.kt`, the UnifiedPush endpoint `UnifiedPushPreferenceStore.kt`, resting-floater and guide/onboarding flags, the app-security toggles themselves `AppSecurityPreferenceStore.kt:16-17`, and the cache-migration status `LegacyPlaintextCacheMigration.kt:205-207` — is unencrypted by design; none of it is task content, and all of it is excluded from backups. Everything that *is* sensitive lives in `EncryptedSharedPreferences`: the session cookie `EncryptedCookieStore.kt:27-32`, the server config, device id, certificate pins, cached session user and pending-approval password `SecureConfigStore.kt:26-31`, the theme preference `ThemePreferenceStore.kt:19-24`, and the SQLCipher passphrase `DatabasePassphraseStore.kt:31-37`.
- **No root, tamper, or debugger detection.** The app makes no attempt to notice that it is running on a compromised device.
- **Debug builds** permit cleartext HTTP and are excluded from the LAN-only cleartext refusal `ServerConfigRepository.kt:205`; that relaxation does not exist in a release APK.

---

## iOS client & widget security

Every claim below was read out of the current working tree, and every `file:line` was opened. Where a control is opt-in, off by default, or protects less than its name suggests, that is stated in the same sentence as the claim.

### Local SwiftData store — iOS Data Protection + backup exclusion, NOT application-level encryption

State this precisely: the local task database is **not encrypted by T'Day**. SwiftData exposes no passphrase or cipher hook, so the store stays an ordinary SQLite file. What the app controls is (a) which OS Data Protection class the file carries and (b) whether copies of it are allowed into backups. Both are now set deliberately.

`LocalStoreFileProtection.apply(to:)` stamps `FileProtectionType.completeUntilFirstUserAuthentication` and `isExcludedFromBackup = true` on the store **and both SQLite sidecars** — `default.store`, `default.store-wal`, `default.store-shm` (`ios-swiftUI/Tday/Core/Data/AppContainer.swift:163-180`, class constant at `:150`, sidecar list at `:155-161`, `setAttributes` at `:169-172`, backup flag at `:173-176`). Covering the `-wal` is the load-bearing part: it holds committed-but-not-yet-checkpointed rows, i.e. the text the user typed most recently, so stamping only the `.store` would protect almost nothing (rationale at `AppContainer.swift:152-154`; pinned by `ios-swiftUI/Tests/TdayCoreTests/TodayTasksWidgetSnapshotStoreTests.swift:416-424`).

It is applied at container construction (`AppContainer.swift:88`) and re-applied on **every** foreground and background transition via `reapplyDatabaseProtection()` (`AppContainer.swift:131-133`, called from `ios-swiftUI/Tday/Feature/App/AppRootView.swift:329` and `:350`). Reason, from the code: SQLite deletes and recreates the sidecars as it checkpoints, and a recreated file is born with the container default rather than whatever was stamped at launch (`AppContainer.swift:125-130`).

Application is **best-effort by design** — `try?` on both the attribute write and the resource-value write, and files that do not exist are skipped (`AppContainer.swift:165-179`). A store that cannot be re-stamped still opens, because failing there would take the user's unsynced offline edits down with it. So the honest guarantee is "stamped whenever the OS allows it, as of the last app-lifecycle transition", not "guaranteed stamped at all times": a `-wal` that SQLite recreates mid-session is unstamped until the next foreground/background event. Verified by `TodayTasksWidgetSnapshotStoreTests.swift:426-460`, which also documents that the Simulator does not implement Data Protection at all (the attribute is simply absent there; the test demands it only on real hardware).

**Correction to a common misreading:** the container is deliberately built **without** an explicit `ModelConfiguration(url:)` (`AppContainer.swift:62-71`, with the rationale at `:72-85`). The store URL is read back off the container SwiftData already opened (`:86-87`). This is intentional and security-relevant: because the app declares an App Group, SwiftData's default store location is inside the **group** container, not the app's own Application Support — so a hand-written path would have opened an empty second store and stranded every existing install's cache *and* its unsynced pending mutations. The fallback path at `:87` is only ever handed to `apply`, which skips non-existent files, so a miss degrades to "no attributes stamped", never to a second store.

Consequence worth recording: because the store lives in the App Group container, any T'Day extension on the phone entitled to `group.com.ohmz.tday` — the widget (`ios-swiftUI/TdayWidget/TdayWidget.entitlements`) and the share extension (`ios-swiftUI/TdayShareExtension/TdayShareExtension.entitlements`), both of which declare that group and nothing else — is inside the same protection boundary. That is an intra-app scope note, not a third-party exposure. (The watch targets declare the same identifier, but a watchOS App Group is a separate container on the watch; see the Apple Watch limitation below.)

**What this does and does not protect against.** It protects against the "someone has my device or a device backup" leg of the threat model in two specific ways: the file's contents are covered by hardware-backed Data Protection keyed to the device (unreadable when the device is powered off, and before the first unlock after boot), and the file is kept out of device/iCloud backups — which closes the path where an *unencrypted* Finder/iTunes backup would hand over task text in the clear. It does **not** protect against anything with code execution on an unlocked (or once-unlocked) device: after the first post-boot unlock the OS holds the class key, so the SQLite file is readable in plaintext to any process that can reach it. There is no T'Day-held key, no passphrase, and nothing the biometric app lock below withholds.

### Why `.completeUntilFirstUserAuthentication` and not `.complete`

`.complete` makes a file unreadable whenever the device is locked. The home-screen widgets have to keep rendering on a locked device, and background refresh has to keep running, so `.complete` would break both. `.completeUntilFirstUserAuthentication` is therefore **required**, not a weaker compromise made out of convenience (`AppContainer.swift:145-148`; the same reasoning at `ios-swiftUI/Tday/Core/Widget/TodayTasksWidgetSnapshotStore.swift:125-128`).

State the tradeoff plainly: **content is readable to the OS from the first unlock after boot until the next reboot.** The class only buys protection in two windows — powered off, and booted-but-never-unlocked. It buys nothing during ordinary daily use. The app makes the same call for the widget snapshot and the widget session file, so all three sit at the same class and the widget stays functional; the visible cost is that between a reboot and the first unlock the widget falls back to its setup/empty state.

### Widget content snapshots — moved off UserDefaults into a protected App Group file

Widget content snapshots — task titles, notes/descriptions, due times, priorities, pinned flags and task IDs (`TodayTasksWidgetSnapshotStore.swift:54-109` for the Today row shape, `:354-395` for the Floater one) — now live as JSON files in the App Group container `group.com.ohmz.tday`: `widget-today-snapshot.json` and `widget-floater-snapshot.json` (`TodayTasksWidgetSnapshotStore.swift:129-165`, file names at `:130-132`, container lookup at `:160-164`).

Each write is `options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication]` followed by re-applying `isExcludedFromBackup = true` (`:146`, `:151-154`). The backup flag must be re-applied on every write because an atomic write replaces the file. Pinned by `TodayTasksWidgetSnapshotStoreTests.swift:337-363`, which asserts the class equals `.completeUntilFirstUserAuthentication` (and fails on real hardware if no class was recorded) and asserts the backup exclusion. The write is best-effort: a failure is swallowed and the widget keeps its previous timeline entry (`:155-157`).

**Migration off UserDefaults.** These snapshots previously went to App Group *and* standard `UserDefaults` as plain JSON — a plist with only the container's default protection, included in backups. `drainLegacyDefaultsSnapshot()` runs on the first `loadSnapshot()` after the upgrade, *before* the file read, so no upgrade loses widget content: it takes the old copy, mirrors it into the protected file if the file does not yet exist, and removes the key from **both** stores so the plaintext copies stop lingering in a backed-up plist (`TodayTasksWidgetSnapshotStore.swift:261-305` for Today, `:472-512` for Floaters; legacy keys `tday.widget.todayTasksSnapshot` at `:174` and `tday.widget.floaterTasksSnapshot` at `:409`). Every later call is a no-op — the keys are gone. Verified end to end by `TodayTasksWidgetSnapshotStoreTests.swift:365-407` (content survives, file exists, both plaintext copies deleted, still readable afterwards from the file alone).

One timing caveat to state: the deletion happens **in the app process**. The widget-side reader still falls back to the legacy `UserDefaults` keys, read-only and deliberately never re-written, so that a device that installs this build renders correctly before the app is next opened (`ios-swiftUI/TdayWidget/TodayTasksWidget.swift:557-578`, and the Floater twin at `:1375-1396`). Until the user opens the app once, the old plaintext plist copy is still on disk.

The widget-side snapshot reader is a deliberate duplicate of the same file shape (`TdayWidget/TodayTasksWidget.swift:102-122`). The widget's session hand-off file `widget-backend-session.json` uses the identical protection class + backup exclusion (`TodayTasksWidgetSnapshotStore.swift:623`, `:628-631`) and is written only when a real session cookie is present, otherwise cleared (`:607-610`); the widget's reader is at `TdayWidget/TodayTasksWidget.swift:131-166`. Note the in-code comments call these files "encrypted at rest" (`TodayTasksWidgetSnapshotStore.swift:553`, `:590`) — prefer "iOS Data Protection class `completeUntilFirstUserAuthentication` plus backup exclusion" in documentation. There is no T'Day-held key, and the file is readable to any process on the device after the first post-boot unlock.

**Honest remainder in App Group `UserDefaults`.** The widget's pending-completion queue is still App Group `UserDefaults` under `tday.widget.pendingCompletions`, alongside a short-lived check-off animation queue under `tday.widget.checkingCompletions` (`TodayTasksWidgetSnapshotStore.swift:520-544`; widget side `TdayWidget/TodayTasksWidget.swift:13-31`). That is acceptable because the entries carry only `{kind, id}` and `{kind, id, atEpochMs}` — no task text — but they are unprotected relative to everything above, and the IDs they hold sit in an unencrypted, backed-up plist.

### Task text that is still in plaintext `UserDefaults` — three residual paths

These are real, currently present, and are the honest counterweight to the section above. None of them is covered by the file-protection work.

- **Repeat-suggestion dismissal list.** When the user dismisses a "make this repeating?" chip, the app appends the *normalized task title* — verbatim, up to 200 entries — to standard `UserDefaults` under `repeatSuggestionDismissed` (`ios-swiftUI/Tday/UI/Component/CreateTaskSheet.swift:401-414`, key at `:414`). "Normalized" is only grammar-stripping, lowercasing, whitespace-squashing and trimming (`ios-swiftUI/Tday/Core/Data/Todo/TodoRepository.swift:1298-1303`), so the stored strings are the user's task titles. This is the same leak that was fixed on the web client by storing salted HMAC digests; **the iOS client has not had that fix applied**. It is unprotected, backup-eligible plist content.
- **Share-extension queue.** A share captured by the share extension is queued as plain JSON `{title, notes}` in App Group `UserDefaults` under `tday.share.pendingShares` until the app next activates and pops it (`ios-swiftUI/Tday/Core/Share/PendingShareStore.swift:14-33`; writer at `ios-swiftUI/TdayShareExtension/ShareViewController.swift:97-103`). The window is short but the content is whatever the user shared, in the clear.
- **Local reminder notifications.** Scheduled reminders put the task title in `content.title` and the description in `content.body` (`ios-swiftUI/Tday/Core/Notification/TaskReminderScheduler.swift:102-104`), so task text is held in the OS notification store and is rendered on the lock screen. The biometric app lock does not affect this.

### The protection boundary stops at the phone: Apple Watch mirror

Every Today snapshot save also pushes the snapshot to a paired Apple Watch over WatchConnectivity's application context (`TodayTasksWidgetSnapshotStore.swift:258`; `ios-swiftUI/Tday/Core/Widget/WatchSessionManager.swift:29-38`). On the watch it is persisted as plain JSON in the watch's own App Group `UserDefaults` under `tday.watch.todaySnapshot` — with no protection class and no backup exclusion — and the complication reads the same key (`ios-swiftUI/TdayWatch/TdayWatchApp.swift:43-56`, payload shape carrying `title` and `description` at `:11-17` and `:25-39`; complication reader at `ios-swiftUI/TdayWatchWidget/TdayWatchComplication.swift:9-20`). So for a user with a paired watch, the same task titles and notes exist in an unprotected plist on a second device. This is not a defect in the phone-side work; it is the honest edge of it.

### TLS trust model

**1. System-trusted is the normal path.** Before any pinning logic runs, `isSystemTrusted(_:host:)` builds an SSL policy for the exact host and evaluates the chain (`ios-swiftUI/Tday/Core/Network/NetworkConfiguration.swift:318-322`). A chain that validates against the public CA store with hostname validation gets `.performDefaultHandling` — plain CA validation, no pin — so a Let's Encrypt/Cloudflare renewal never false-trips. That branch also **clears** any pin previously stored for the host, so a server migrating from self-signed to a real CA does not keep enforcing a dead fingerprint (`:127-133`). For the documented deployment (Cloudflare Tunnel, public CA certificate) this is the only branch ever taken, and none of the machinery below is exercised.

**2. Fail-closed.** For anything the system cannot verify, nothing is trusted unless it matches a stored pin or the user has just approved that exact fingerprint on screen. The rule is the pure, static `decideTrust(fingerprint:storedPin:enrollmentExpecting:isPrivateHost:)` (`NetworkConfiguration.swift:196-222`), kept pure precisely so it is testable without fabricating a `SecTrust`. A trust whose fingerprint cannot be derived is never accepted (`:213`) — the code comment records that the previous implementation fell through to `.useCredential` there, and that it trusted-on-first-use (`:135-140`); pinned by `ios-swiftUI/Tests/TdayCoreTests/ConnectivityClassificationTests.swift:112-141`. An existing pin wins even over a live user approval (`:215-217`). Fingerprint = base64(SHA-256) of the leaf public key, falling back to base64(SHA-256) of the whole leaf DER certificate (`:333-355`).

**3. Two-phase, one-shot, fingerprint-bound enrollment.** Phase 1: the unknown certificate is refused (`.cancelAuthenticationChallenge`) and recorded with the fingerprint it offered (`:158-160`, `:238-251`); `ServerConfigRepository.probe` translates the resulting bare `URLError.cancelled` into `ServerProbeError.untrustedCertificate(host:fingerprint:)` (`ios-swiftUI/Tday/Core/Data/Server/ServerConfigRepository.swift:232-238`); `AppViewModel` turns it into a `PendingCertificateApproval` and the setup UI shows the fingerprint for confirmation (`ios-swiftUI/Tday/Feature/App/AppViewModel.swift:413-421`). Phase 2: on confirm, `approveCertificate` records `allowTrustEnrollment(host:expecting:)` and re-probes, clearing the expectation on any failure (`ServerConfigRepository.swift:247-262`; `NetworkConfiguration.swift:268-282`). The approval is bound to **one exact fingerprint** and is consumed by the next challenge via `removeValue(forKey:)` (`:284-288`), so it cannot be replayed (test: `ConnectivityClassificationTests.swift:235-261`) and a certificate swapped between the prompt and the retry still returns `.rejectUnknown` (test: `:90-100`). Host keys are lowercased on read and write; the state is guarded by an `NSLock` because it is touched from the URLSession delegate queue and from async callers (`NetworkConfiguration.swift:15`).

**4. Enrollment is offered only for PRIVATE/LAN hosts.** `isPrivateHost` (from `isLocalAddress`, `:324-331`) is passed into `decideTrust` and gates enrollment. For a **public** hostname whose certificate the system cannot verify, the decision is `.rejectUntrustedPublic` — a flat refusal with **no option to trust it**, even if an approval were somehow pending (`:202-209`; enum case documented at `:181-183`). This is deliberately kept in a separate record from `trustUnknownHosts`, because that record is exactly what makes the setup screen render a "Trust this certificate" button, and on hostile public wifi that prompt *is* the attack (`:161-166`, `:253-266`). It surfaces as `ServerProbeError.untrustedPublicCertificate(host:)`, which carries **no fingerprint on purpose** (`ServerConfigRepository.swift:12-14`, raised at `:229-231`), produces a plain refusal message (`AppViewModel.swift:1042`), and explicitly suppresses the "reset saved server trust" affordance so it cannot be mistaken for a way to push past the refusal (`AppViewModel.swift:1084-1093`). An already-established pin on a public host is still honoured, and a change still reads as a mismatch — a public host simply can never acquire a *new* pin (`NetworkConfiguration.swift:202-208`). Covered by `ConnectivityClassificationTests.swift:143-233`.

Note the direction of the change: local hosts **used to** short-circuit the delegate to `.performDefaultHandling`, which meant a self-signed LAN server simply failed the handshake with no way through (comment at `NetworkConfiguration.swift:116-119`). `isPrivateHost` now grants no trust by itself in the app's delegate — it only decides who may be *offered* enrollment. It does still gate `isSecureTransportRequired(for:)` (`:298-303`), which is what permits plain HTTP for those hosts and forces HTTPS everywhere else (`ServerConfigRepository.swift:200-202`).

**5. Certificate change is detected and recoverable.** A pinned host presenting a different certificate yields `.rejectMismatch`, recorded out of band (a cancelled TLS challenge cannot carry a typed error) and rethrown as `ServerProbeError.certificateChanged` (`NetworkConfiguration.swift:155-157`, `:224-236`; `ServerConfigRepository.swift:224-226`). Recovery clears the pin and re-probes through the same fail-closed path — it does **not** mean "trust whatever answers next" (`ServerConfigRepository.swift:138-147`). A silent recheck also reports `trustFailed` rather than `.compatible`, so an intercepted connection is not masked as "server unreachable" (`:115-136`).

**6. The widget's own trust check is fail-closed and can never enroll.** The widget process has no Keychain access (no `keychain-access-groups` entitlement exists in any target — its entitlements file declares only the App Group), so the app hands it the pinned fingerprint through the App Group session file. `WidgetPinnedTrustDelegate` mirrors the app: local hosts (`:197-200`) and system-trusted chains (`:202-208`) take default handling; anything else must match the handed-over pin exactly or the challenge is cancelled (`TdayWidget/TodayTasksWidget.swift:169-218`, pin check at `:211-216`). There is no enrollment path anywhere in the widget process, so a widget tap can only ever reach a server the app already verified. Note the widget **does** still short-circuit local hosts to default handling — unlike the app — so the widget cannot reach a self-signed LAN server at all; it is fail-closed in the safe direction.

**Honest limitations of the trust model.**
- `isLocalAddress` is prefix/suffix **string matching, not CIDR parsing**: `localhost`, `127.0.0.1`, `10.0.2.2`, `hasPrefix("192.168.")`, `hasPrefix("10.")`, `hasSuffix(".local")` (`NetworkConfiguration.swift:324-331`, duplicated in `ServerConfigRepository.swift:264-271` and `TdayWidget/TodayTasksWidget.swift:220-227`). A DNS name that merely *begins* `10.` (e.g. `10.attacker.example`) or *ends* `.local` is classified private — which now means it is eligible for the enrollment prompt and for plain HTTP. Conversely, `172.16.0.0/12` is **not** covered, so a 172.16.x.x LAN server is treated as public and must present a CA-valid certificate. Because the predicate now decides who may be shown a trust prompt, this string matching is more load-bearing than it was.
- Pinning holds exactly one fingerprint per host: no backup pin, no rotation window, no port scoping on the enforcing path (`SecureStore.serverTrustKey(for:)` builds a `host:port` key at `ios-swiftUI/Tday/Core/Data/SecureStore.swift:280-288`, but the delegate and `NetworkConfiguration.trustedFingerprint(for:)` key on host alone, `:308-313`). Rotating a self-signed server key breaks every client until the user resets trust and re-confirms by eye.
- First enrollment still rests on the user comparing a base64 SHA-256 out of band. There is no QR pairing or second-channel verification. The control correctly refuses to auto-trust; it cannot verify on the user's behalf.
- ATS is declared with `NSAllowsArbitraryLoads = false` and `NSAllowsLocalNetworking = true`, no exception domains, in the main app (`ios-swiftUI/Tday/Info.plist:53-59`) and the widget (`ios-swiftUI/TdayWidget/Info.plist:23-29`).
- Unrelated to the server: `refreshGitHubReleases()` calls `api.github.com` on `URLSession.shared` (`AppViewModel.swift:1124-1137`, request at `:1146`), bypassing the app's own TLS delegate entirely. ATS-level CA validation applies; none of the trust logic above does.

### Keychain usage and what the accessibility class implies

`SecureStore` writes one `kSecClassGenericPassword` item per logical key under service `com.ohmz.tday.ios.secure-store` (`SecureStore.swift:14-20`, `:401-407`). Stored there: persisted server URL, device ID, last username, the serialized auth session cookie, cached session user, saved server-URL suggestion, app data mode, the pending-approval username **and password**, and every per-host TLS pin as `fingerprint.<host>` (`:22-32`, `:312-314`).

The accessibility class actually used is **`kSecAttrAccessibleAfterFirstUnlock`**, set on both the update attributes and the insert query but deliberately not on the search query, which would fail to match older items (`:355-358`, `:363`, `:401-407`). The rationale in the code is that background contexts — widgets, App Intents, CarPlay, background refresh — must be able to read the session on a locked-but-once-unlocked device; `WhenUnlocked` would deny those reads (`:351-354`).

Be honest about what that implies: the class is **not** `…ThisDeviceOnly`, so these items are **eligible for encrypted device backup and restore onto another device**. Combined with `AfterFirstUnlock`, the practical reading is: the session cookie and the pending-approval password are readable whenever the device has been unlocked since boot, and can travel to a different device via an encrypted backup. `kSecAttrSynchronizable` is never set anywhere in the repo (grep: zero hits), so nothing goes to iCloud Keychain. No `kSecAttrAccessGroup` and no `keychain-access-groups` entitlement exist in any target (grep: zero hits), so extensions genuinely cannot read these items — their App IDs differ and no shared access group is declared — which is precisely why the App Group hand-off file exists. The app's only other entitlement is `com.apple.developer.associated-domains: webcredentials:tday.ohmz.cloud` (`ios-swiftUI/Tday/Tday.entitlements`), which scopes password AutoFill and grants no keychain sharing.

Split worth stating exactly: the pinned fingerprint **values** go to the Keychain; only the *index of pinned host names* (`secure.trusted.hosts`) goes to plain `UserDefaults`, alongside the runtime server URL, list icons, and the install sentinel (`:145-155`, `:9-12`, `:196-206`, `:294-303`). An attacker with filesystem access learns which server you use and which hosts you pinned, not the pinned values or the session.

Keychain items survive app deletion, so `clearInstallScopedValuesIfAppReinstalled()` purges server URL, data mode, auth cookie, cached user, pending-approval credentials, last username and **all** trusted fingerprints on a fresh install, keyed off a UserDefaults-only sentinel (`:239-256`).

### Optional biometric app lock — default OFF

"Require Face ID to open T'Day" is opt-in and **off by default**: the flag is a plain `UserDefaults` boolean that reads `false` when absent (`ios-swiftUI/Tday/Core/Security/ProbeDecryptor.swift:56-64`, default at `:61`; UI at `ios-swiftUI/Tday/Feature/Settings/SettingsScreen.swift:333-361`). `NSFaceIDUsageDescription` is declared, without which LocalAuthentication could only ever fall back to the passcode (`Tday/Info.plist:32-33`).

`isEnabled` is checked first and wins outright in `coverMode`, so with the setting off there is no state the lock can be in that renders anything — the default configuration behaves exactly as the app did before the feature existed (`ProbeDecryptor.swift:113-123`; test asserting `.hidden` across all four state combinations at `TodayTasksWidgetSnapshotStoreTests.swift:464-474`). When enabled: locked from the first frame on cold start (`:100-104`), prompted from `.task` because `onChange(of: scenePhase)` never fires for the launch value (`AppRootView.swift:286-290`), re-armed at `.background` only — not `.inactive`, which would relock mid-prompt and loop (`AppRootView.swift:343-347`) — and an app-switcher privacy cover is drawn while the app is merely leaving the foreground (`ProbeDecryptor.swift:120-122`; cover at `AppRootView.swift:989-1004`). Authentication uses `.deviceOwnerAuthentication`, so the device passcode is the fallback when Face ID fails or is unavailable (`ProbeDecryptor.swift:156-159`).

**Does it cover modally-presented content? Yes — that is the specific reason for its implementation.** A root-level `.overlay` renders inside the app's view hierarchy, and a `.sheet` / `.fullScreenCover` is presented *on top of* that hierarchy, so a create-task sheet left open at backgrounding used to stay fully readable over the "locked" app and appeared in the app-switcher snapshot. The real gate is `AppLockWindowHost`, a separate `UIWindow` at `windowLevel = .alert + 1`, above sheets, full-screen covers and UIKit alerts (`AppRootView.swift:870-881`, mounted at `:272-285`, level set at `:966`). The in-hierarchy overlay is retained only as a fallback for the case where no window scene exists yet, e.g. very early in a cold start (`AppRootView.swift:233-255`, `:949-951`). The system biometric prompt is drawn out of process and still appears above the lock window.

**What the app lock does NOT do.** It hides the UI and nothing else. It holds **no key material and gates nothing on disk** (`ProbeDecryptor.swift:83-86`): the SwiftData store, the widget snapshots and the widget session file all stay readable at `.completeUntilFirstUserAuthentication` whether or not it is on — which is exactly why widgets keep rendering with the lock enabled (and is what the Settings copy tells the user, `SettingsScreen.swift:355`). It also **fails open** when the device has neither biometrics nor a passcode enrolled, because staying locked would brick the app with no recovery path (`ProbeDecryptor.swift:159-166`). It gates app *launch/foreground* only — not data export, not the password-change flow, not widget rendering, not App Intent completions, not the share extension, and not reminder notifications on the lock screen. So: it stops a bystander picking up an unlocked phone; it does not stop anyone with filesystem access, and it is not the control protecting data at rest.

Still absent on iOS: no jailbreak/device-integrity check (grep: zero hits), and no screenshot suppression — iOS has no `FLAG_SECURE` equivalent, and the app-switcher cover only exists when the lock is enabled. Screenshots the user takes of task content are ordinary photos and go to Photos and iCloud Photos.

---

## Web SPA security

The browser client has two independent data modes, and almost every claim below applies to exactly one of them. `tday.appMode` in `localStorage` selects which (`tday-web/src/lib/local/appMode.ts:12-14,43-45`):

- **Server Mode** — the normal signed-in workspace. Task data lives on the backend; the browser holds a session cookie and a transient cache.
- **Local Mode** — a no-login workspace that never leaves the browser. This is the only mode with the passphrase vault.

### Local Mode: passphrase-encrypted workspace vault

`Opt-in per browser (only reachable by choosing "This device" in the wizard, OnboardingWizard.tsx:136-141) — the default once chosen, but not mandatory: a "Skip encryption on this device" opt-out is one section below.`

The whole Local Mode workspace is a single JSON document sealed with a key derived from a passphrase only the user knows. Parameters, all pinned by `version: 1`:

- **KDF:** PBKDF2-HMAC-SHA256, **310,000 iterations**, 16-byte random salt (`localCrypto.ts:14-21,139-145`) — matched to the backend's own password-hashing default (`AppConfig.kt:96`, `AUTH_PBKDF2_ITERATIONS` default `310_000`). Raising the count requires a new envelope version (`localCrypto.ts:16-17`).
- **Cipher:** AES-GCM-256. `deriveKey` is called with `extractable = false` (`localCrypto.ts:143`), so the raw key bytes cannot be exported back out of the `CryptoKey`, even by script running on the page.
- **IV:** a **fresh 96-bit IV is generated inside `sealVault` on every single write** — `crypto.getRandomValues(new Uint8Array(12))` at `localCrypto.ts:21,155`, never reused, never derived. Regression-covered by "mints a fresh IV for every write under the same key" (`tests/unit/local-vault-crypto.test.ts:65`).
- **What is persisted:** exactly the envelope `{version, salt, iv, ciphertext}`, all base64, under the single key `tday.local.workspace.v1` (`localCrypto.ts:29-37`; `localDb.ts:145,282-285`). The ciphertext carries WebCrypto's appended GCM tag. The passphrase is never stored; the derived key is never stored.
- **Key lifetime:** the `CryptoKey`, its salt and the decrypted rows are module-local variables in `localDb.ts` (`:240-243`) and nothing else. They are dropped by `lockLocalVault()` (`:501-509`), which also bumps a write generation so a queued encrypt-and-write cannot land after the lock. A page reload therefore relocks: the gate re-inspects storage, sees an envelope, and asks again (`localDb.ts:262-273,275-279`).
- **Wrong passphrase:** decryption fails the GCM tag check. WebCrypto raises a bare `OperationError`, which `openVault` translates into `LocalVaultError("wrong-passphrase")` (`localCrypto.ts:174-193`). Nothing is unlocked, nothing is overwritten, and the user can retry indefinitely — **there is no attempt counter and no lockout** (`LocalWorkspaceGate.tsx:455-467`), because the ciphertext is on the attacker's own disk anyway and a counter would only inconvenience the owner.
- **Gate placement:** `LocalWorkspaceGate` wraps the router itself (`src/App.tsx:27-31`), and returns the app only when the mode is not local or the vault is unlocked (`LocalWorkspaceGate.tsx:79`), so in Local Mode no route renders before the passphrase is entered. Any read that races the gate is surfaced as a synthetic HTTP 423 rather than a blank screen (`api-client.ts:59-65`). The gate is a UI convenience, not the boundary — the boundary is that without the passphrase there is no key to decrypt with.
- **Minimum passphrase length is 10 characters** (`passphrasePolicy.ts`, shared with the Settings upgrade flow so the two screens can't drift apart; enforced in the gate at `LocalWorkspaceGate.tsx:332-341`), higher than the server's password minimum, on the stated grounds that an offline guessing attack against the stored ciphertext has no rate limit. The field is `autoComplete="off"` and is never offered to a password manager (`:136-159`, comment at `:154-155`).

**What this protects against:** exactly the second threat in the owner's model — someone who gets the browser profile, the disk, or a filesystem backup. The Local Storage LevelDB yields `{version, salt, iv, ciphertext}` and nothing else. Verified end-to-end by "stores nothing but ciphertext, and stays shut without the passphrase" (`tests/unit/local-workspace.test.ts:449`).

**What it does not protect against, stated plainly:**
- **A workspace the user chose not to encrypt.** None of the above applies — see "Local Mode: the unencrypted opt-out" below.
- **Anything running in the page while the vault is unlocked.** The vault is a storage-at-rest control, not an XSS control. An injected script (or a malicious extension with page access) can call the same `loadWorkspace()` the app calls (`localDb.ts:285-294`) and read every task. Non-extractability stops the *key bytes* leaving; it does not stop the *plaintext* being read through the app's own code path.
- **A device where the user is already sitting in an unlocked session.** There is **no idle timeout and no auto-lock** — a repo-wide search finds no timer that calls `lockLocalVault()`. The key is held for the life of the page, and only an explicit sign-out (`AuthProvider.tsx:198-208`), a reload, or closing the tab drops it.
- **A weak passphrase.** The only check is length ≥ 10 (`passphrasePolicy.ts`) — no dictionary check, no strength meter. 310k PBKDF2 rounds raise the cost per guess, they do not rescue a guessable phrase, and `autoComplete="off"` deliberately keeps password managers out of the flow, which pushes users toward something memorable.
- **A keylogger or a shoulder-surfer capturing the passphrase.**
- **The Local Mode export.** `GET /api/export` answered locally (`localApi.ts:249-250`) produces the full workspace as plaintext JSON (`localTransfer.ts:58-142`) and downloads it as a plain file (`fileTransfer.ts:4-17`, wired at `DataTransferCard.tsx:91-92`). This is the one supported way data leaves the vault in the clear; there is no encrypted-export option. Once exported it is outside the vault entirely.
- **Changing your mind about the passphrase.** There is no rekey path — `localDb.ts` exposes create, unlock, lock, migrate and wipe, and nothing else. Changing the passphrase means export → delete local data → set up again → import. An unencrypted workspace *can* move the other way, one-way, from Settings — see below — but there is no route back from encrypted to unencrypted short of that same export/delete/re-import cycle.

### Local Mode: no recovery, and that is the whole design

`Always`

**Losing the passphrase loses the data. Permanently.** There is no reset link, no escrow, no backup key, no support account, and no server that has ever seen a byte of this workspace. The salt is stored, but nothing derived from the passphrase is stored anywhere that could be used to verify or recover it — only the passphrase itself, plus PBKDF2, can produce a key that passes the GCM tag check.

This is the largest user-facing risk in the entire web client, so the UI states it before the user commits: setup shows a destructive-styled warning ("There is no recovery… if you forget this passphrase, these tasks are gone for good") and requires an explicit "I understand that losing this passphrase means losing this workspace" checkbox before the submit button enables (`LocalWorkspaceGate.tsx:372-405,324-328`). The unlock screen's "I forgot my passphrase" path leads only to a two-step confirmed **wipe**, with the wording "Deleting is the only way past a forgotten passphrase" (`:495-524` → `clearWorkspace`, `localDb.ts:510-522`).

Two secondary ways the data disappears, both by design: clearing browser site data removes the envelope (documented in the source header, `localDb.ts:15-16`), and Settings → "Delete local data" removes it (`SettingsPage.tsx:673` → `localApi.ts:292-295` → `clearWorkspace`). Signing out of a *server* account does **not**: `tday.local.workspace.v1`, `tday.appMode` and `tday.returning-browser` are on the explicit preserve list of the logout wipe (`AuthProvider.tsx:34-38`).

### Local Mode: the unencrypted opt-out

`Opt-in, off by default — the passphrase screen is still the first thing shown`

A user who would rather not carry a passphrase can decline it. This is reachable from every screen that would otherwise ask for one — first setup, the legacy migration prompt, and the insecure-origin screen below — behind a two-step "Skip encryption on this device" → "Store unencrypted" confirmation naming the trade before it is taken (`LocalWorkspaceGate.tsx:240-290`, warning text at `:293-294`).

**The storage-format problem this solves.** A workspace stored in the clear by choice is, byte for byte, the same shape as the pre-encryption *legacy* document a build before this one would have written. Without a discriminator, every load of an intentionally unencrypted workspace would misclassify as `legacy` and re-show the "Protect your tasks" migration prompt forever. The fix is a small wrapper document, `{protection: "none", version, workspace}` (`localCrypto.ts:57-63`), written instead of the bare workspace object. `isOpenWorkspaceDocument` checks it (`localCrypto.ts:135-146`) and is deliberately consulted *before* `isLegacyPlaintextWorkspace` can fire (`localCrypto.ts:168-173`), so a wrapped document is never misread as the thing it is trying not to be classified as.

**Where the choice lives:** in the document on disk, not in a side flag. The alternative — a second `localStorage` key such as `tday.local.protection`, or a flag alongside `tday.appMode` — was rejected: two sources of truth for the same fact can desync (the exact class of bug the migration write's loud-failure behaviour, below, already exists to prevent), and the wrapper needs no cross-key consistency to reason about.

**State machine:** `LocalVaultState` gains an `open` member (`localDb.ts:200-234`) — storage holding a readable-with-no-key document, not yet adopted into this session. It is deliberately *not* collapsed into `unlocked` at the probe (`inspectStorage`, `localDb.ts:262-273`): that function has no side effects, so returning `unlocked` before anything is actually cached would be a lie the next `loadWorkspace()` call would immediately contradict. Adoption — reading the document and populating the in-memory cache — happens one layer up, in the gate's `resolveVaultState` (`LocalWorkspaceGate.tsx:38-55`), which is why an unencrypted workspace opens on the very first paint with no visible transition through a locked-looking state.

**Crypto surface: none, on purpose.** `openInTheClear` (`localDb.ts:382-404`) and `openLocalWorkspace` (`localDb.ts:419-434`) touch no key material at all — there is nothing to derive, nothing non-extractable, nothing to hold in memory beyond the plaintext rows themselves. `saveWorkspace` (`localDb.ts:296-329`) still funnels every write through the one queue and the one `writeGeneration` cancellation guard the encrypted path uses; only the branch inside the queued task differs — `sealVault` versus `openWorkspaceDocumentJson` (`localDb.ts:311-315`) — so a lock, a clear, or an upgrade mid-write cancels an in-flight unencrypted write exactly as it would an encrypted one.

**The insecure-origin case is the one place this changes existing behaviour, not just adds to it.** `inspectStorage` now checks for an open document *before* checking `crypto.subtle` availability (`localDb.ts:262-268`), so a plain-`http://` LAN origin that already holds an unencrypted workspace opens it without ever touching the crypto-availability check. See the next section for what changes on that origin when nothing is stored yet.

**Upgrading to encrypted, one-way.** `protectPlaintextWorkspace` (`localDb.ts:445-465`) seals whatever unencrypted rows the browser is currently holding — whether adopted from an open document or a legacy one — under a chosen passphrase, reusing the same `openWith` used by first-time setup. It flushes any queued open write first: `openWith` bumps `writeGeneration` before its own write, so a queued write still in flight would otherwise be silently cancelled by the upgrade, losing an edit that was never sealed into either document. Reachable from Settings → Workspace → "Encrypt this workspace" (`SettingsPage.tsx`, `workspace.encrypt*` message keys) as well as from the legacy migration prompt. Covered by "upgrades to encrypted in place, preserving the rows" and "fails an upgrade loudly, leaving the workspace open rather than half-sealed" (`tests/unit/local-workspace.test.ts`, `describe("local mode without encryption")`).

**No downgrade, deliberately.** There is no button that takes an encrypted workspace back to unencrypted. Offering one would mean "the vault protects the disk" quietly becomes "the vault protects the disk until someone at an already-unlocked session clicks a button" — the honest route for that is the same export → delete local data → set up again → import cycle used for a passphrase change.

**What is stored in the clear, exactly:** the full workspace document — every task, list, note and completed entry — sits in `localStorage` under `tday.local.workspace.v1`, wrapped but unencrypted, readable by anything with access to that browser profile, that machine's disk, or a backup of either. This is the trade the two-step confirmation states before it can be taken; it is not a smaller version of what the passphrase vault protects against, it is the complete absence of that protection.

### Local Mode: migrating a workspace written before encryption existed

`Automatic prompt; skippable`

Builds before this one wrote the workspace as plain JSON under the same storage key. The gate detects that by **shape**, not by key name — a stored document that is not a valid envelope, not an open-workspace wrapper, but has a numeric `schemaVersion` or a `todos` array is classified `legacy` (`localCrypto.ts:168-173`; `localDb.ts:262-268`). The user is shown the setup panel with migration wording ("This browser already holds tasks in the clear… they'll be encrypted in place — nothing is lost", `LocalWorkspaceGate.tsx:364-369`), and on submit `protectPlaintextWorkspace` reads the plaintext document, coerces it to the current shape, and seals it under the new key (`localDb.ts:445-465` → `openWith`).

The prompt can be declined. Doing so calls `keepLegacyWorkspaceOpen` (`localDb.ts:436-441`), which wraps the same legacy rows into the open-workspace document instead of encrypting them — the rows are not touched, only the storage form gains the `{protection: "none"}` wrapper described above. Declining is a one-time write specifically so the migration prompt does not return on the next load; without the wrapper, a bare plaintext document would keep re-triggering it forever.

The migration write is deliberately **not** routed through the normal queued `saveWorkspace`, whose write path swallows storage failures (`localDb.ts:296-329`). It writes synchronously and rethrows a `LocalVaultError("storage")` if `localStorage` refuses (`localDb.ts:345-380`), because a silently-failed migration would leave the plaintext document on disk while telling the user their tasks were now encrypted. The same loud-failure rule covers `openInTheClear` (`localDb.ts:382-404`), the counterpart used by "skip encryption" and by `keepLegacyWorkspaceOpen`. Covered by "fails the migration loudly when the browser refuses to store the envelope" and, for the unencrypted path, "fails an upgrade loudly, leaving the workspace open rather than half-sealed" (`tests/unit/local-workspace.test.ts`).

Honest limit on the *normal* write path: a quota or blocked-storage failure after unlock is logged and swallowed, and the in-memory copy stays authoritative for the session (`localDb.ts:296-329`). That is a durability trade, not a confidentiality one — nothing plaintext is written to an encrypted workspace on that path — but edits made in that session can be lost on reload. For an unencrypted workspace the same swallow means a lost edit, not a lost secret; there is no confidentiality trade to make either way.

### Local Mode on an insecure origin: offers the unencrypted workspace

`Always`

`crypto.subtle` is only handed to secure contexts, so a self-hosted T'Day served over plain `http://` on a LAN address cannot encrypt anything. The gate enters an `unsupported` state, explains why (`localCrypto.ts:91-99`; `LocalWorkspaceGate.tsx:536-570`), and points the user at `https://` or `localhost` for an encrypted workspace — but it no longer stops there. Encryption is the one thing this specific origin cannot offer; it is not a reason to withhold the unencrypted workspace the same user could pick freely on `https`. The same two-step "Skip encryption on this device" confirmation used elsewhere is offered here too, naming the cause plainly ("this page is served over plain http, so the browser will not give T'Day the encryption API") before the user can take it.

Before this reversal the behaviour was the opposite — refuse to open at all, on the grounds that degrading silently to plaintext would be worse than refusing. That is still true of a *silent* degrade. It stopped being true once the same trade was made an explicit, warned, two-tap user choice everywhere else in Local Mode: refusing here just meant an `https` user could have a device workspace while a LAN user serving the same app over plain http could not, for a reason that has nothing to do with what either of them is choosing to store.

Existing stored data is left untouched either way. Server Mode remains available. Once an unencrypted workspace exists on this origin, `inspectStorage` finds the open document before it ever checks `crypto.subtle` (`localDb.ts:262-268`), so a reload opens it directly — the insecure-origin screen is only ever seen with nothing stored yet.

### Repeat-suggestion dismissals: salted HMAC digests, not titles

`On by default`

The "Make this repeat?" chip remembers which titles the user has waved away. It used to store those titles **verbatim** in `localStorage` under `tday.repeatSuggestion.dismissed` — a plaintext leak of real task titles that sat *outside* the vault and so survived Local Mode encryption entirely. Fixed: the document is now version 2 and stores `HMAC-SHA-256(salt, normalizedTitle)` truncated to 16 bytes, hex-encoded, capped at the 200 most recent entries (`repeatSuggestionDismissal.ts:21-28,53-61,146-163`).

**Salt provenance matters and is handled correctly:** the salt is 128 bits from `randomBytes`, minted once **per browser workspace** and stored in the same document (`:26,82-90`) — explicitly not a constant baked into the bundle, which would let one precomputed dictionary of common task titles unmask every install. If the page has no CSPRNG the feature is disabled outright rather than falling back to a guessable salt or to plaintext (`:86-89`). Per-workspace salting is regression-covered ("mints a distinct salt per workspace, so digests are not portable", `tests/unit/repeat-suggestion-dismissal.test.ts:42`).

HMAC comes from `@noble/hashes`, not `crypto.subtle` (`:13-19`), for two stated reasons: the check runs synchronously during render, and `crypto.subtle` does not exist on a plain-http LAN origin. So this protection holds even on the deployments where the vault itself is unavailable.

Migration of the v1 plaintext array runs **on read**, not on the next dismissal, so a user who never dismisses anything again is still not left with old titles in the clear; if the upgraded document cannot be written, the plaintext is deleted anyway (`:101-144`). Corrupt, unparseable or future-version documents are discarded rather than trusted (`:107-112,141-143`).

Honest limit: this is a keyed digest of a low-entropy input, so it resists a *precomputed* table, not a targeted guess. Someone with the profile who suspects a specific title can HMAC that guess with the stored salt and confirm it. It is a membership oracle, not a hiding place — but it no longer hands over the title list.

### Session handling: HttpOnly cookie, nothing token-shaped in storage

`Always`

The session JWT reaches the browser only as a cookie built with `httpOnly = true`, `path = "/"`, `SameSite=Lax`, and `secure` when the name is `__Secure-`-prefixed or the backend is in production (`tday-backend/src/main/kotlin/com/ohmz/tday/security/SessionCookies.kt:86-99`; name selection at `:8-19`).

The SPA never reads, stores, or attaches a token. Every request goes through one client that sets `credentials: "same-origin"` and no `Authorization` header (`api-client.ts:73-80`). The only auth-adjacent value the app keeps is a plain user-profile object in React state (`AuthProvider.tsx:46-55`) — id, name, username, role, approval status, timezone — never a credential.

**Why this matters for XSS:** JavaScript has no API that returns an HttpOnly cookie, so an injected script cannot exfiltrate the session for use from another machine. It is reduced to riding the session in-page via same-origin `fetch` — bad, but bounded by the tab's lifetime and by the CSP that pins `script-src` to `'self'` with no `'unsafe-inline'` (`tday-backend/.../plugins/SecurityHeaders.kt:75`, rationale at `:48-51`). A bearer token in `localStorage` would have converted the same bug into a stealable, portable, long-lived credential.

Two honest qualifications on that CSP sentence. First, the header mode is env-controlled: `CSP_MODE` unset or `enforce` enforces, `report-only` only reports, `off` omits the header entirely (`SecurityHeaders.kt:12-17`; `AppConfig.kt:169`) — the default is enforce, but a deployment can turn it off. Second, `connect-src` includes the bare `ws:` and `wss:` scheme sources (`SecurityHeaders.kt:58-66`) because the SPA derives its socket URL from `window.location.host`, so an injected script is blocked from loading foreign *scripts* but is not fully boxed in against opening a socket to an arbitrary host.

On sign-out, on a global 401 raised by any non-`/api/auth/` request (`api-client.ts:103-104`), and on a 401 to the session probe, `clearClientUserData()` unsubscribes Web Push, clears `sessionStorage`, removes every `localStorage` key except the three preserved ones, deletes every Cache Storage cache, and attempts to delete every IndexedDB database (`lib/security/clearClientUserData.ts:18-80`; wired at `AuthProvider.tsx:109-114,125-134,219-222`). React Query's in-memory cache is cleared alongside (`AuthProvider.tsx:111,132,219`). Two honest limits: every step is individually try/caught and swallows failures, so a blocked storage API degrades silently; and the IndexedDB sweep returns early on any browser without `indexedDB.databases()` (`:67-69`), which is a no-op rather than a wipe on Firefox.

Signing out of Local Mode instead flushes pending encrypted writes, drops the key with `lockLocalVault()`, clears the query cache and returns to the wizard, deliberately leaving the sealed workspace in place (`AuthProvider.tsx:198-208`).

### What is still in browser storage in the clear

`Always — full inventory`

Neither the vault nor the cookie design covers these. Nothing here is a credential, but be precise about it:

**Server Mode task data is not in the vault at all.** In Server Mode the workspace lives on the backend and the browser holds no encrypted copy. What it *does* hold is the service worker's `tday-api-cache`: up to 100 responses from `/api/todo*`, `/api/floater*`, `/api/floater-list*`, plaintext, 1-hour TTL, network-first (`src/sw.ts:73-88`). On a shared machine that is up to an hour of real task titles and descriptions readable from Cache Storage. Logout deletes it (`clearClientUserData.ts:57-64`); a closed tab mid-logout does not. Local Mode never populates it — `fetchApi` short-circuits to the in-browser handler before any `fetch` happens (`api-client.ts:68-71`).

**`localStorage`, all plaintext** (all removed on logout except the three preserved keys): `tday.appMode` (`appMode.ts:14`), `tday.language` (`i18n.ts:23`), `tday.push-enabled` (`hooks/usePushNotifications.ts:6`), `tday.returning-browser` (`lib/security/returningBrowser.ts:1`), `tday.release.current.v1` (`features/release/lib/release.ts:9`), `tday.sidebar.desktop.open` plus a bare unprefixed `tab` key (`providers/MenuProvider.tsx:30-31`; `components/Sidebar/SidebarToggleContainer.tsx:57`), `tday.lastSeenGuideVersion` (`features/guide/guideContent.ts:66`), a per-week "week in review seen" flag (`features/summary/WeekInReviewCard.tsx:23,27,45`), and `tday.restingFloaters.enabled` (`lib/floaterResting.ts:31`). Two entries need their own note: `tday.local.workspace.v1` holds only the sealed envelope, and `tday.repeatSuggestion.dismissed` holds digests — see above. Worth flagging: **`tday.pendingApprovalUsername` stores the raw username** of an account awaiting admin approval (`lib/pendingApproval.ts:5,7-29`); its own comment confirms the password is never stored.

**`sessionStorage`:** stale-chunk and version-reload guards (`lib/chunkError.ts:48-92`), install-prompt dismissal (`hooks/useInstallPrompt.ts:88`), release-announcer flag (`components/release/ReleaseUpdateAnnouncer.tsx:13-21`). No task data.

**IndexedDB:** the app creates none of its own. React Query is configured with no persister — its cache is memory-only (`providers/QueryProvider.tsx:47-61`). The only IndexedDB code in the tree is the teardown that enumerates and deletes whatever exists (`clearClientUserData.ts:66-79`), which also catches Workbox's bookkeeping databases.

**In memory only, never persisted:** the decrypted Local Mode workspace while unlocked and the derived vault key when the workspace is encrypted (`localDb.ts:240-243`), the React Query cache, and the raw registration password held in React state while the onboarding/pending-approval screens are open (`OnboardingWizard.tsx:92`, cleared at `:161,191,317`). For an unencrypted workspace there is no key to hold — only the decrypted rows, which for that workspace are the same bytes already sitting in `localStorage`.

**Nothing leaves the browser in Local Mode.** Error reporting is compiled out unless a DSN is supplied at build time — `Sentry.init` is called with `dsn: import.meta.env.VITE_SENTRY_DSN ?? ""` and, when enabled, runs with `sendDefaultPii: false`, both replay sample rates at 0, and console/DOM breadcrumbs disabled (`src/main.tsx:25-47`).

---
