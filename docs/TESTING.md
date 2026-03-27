# Testing Strategy

This document defines testing expectations, conventions, and tooling for the web, backend, and Android codebases.

## Philosophy

- Test behavior, not implementation details.
- Security-critical code must always have tests.
- Tests are first-class code — apply the same quality standards as production code.

## Web Testing

### Tooling

| Tool | Purpose |
|------|---------|
| Vitest | Test runner and assertion library |
| TypeScript | Tests written in TypeScript with full type safety |
| Node test environment | API-focused tests run in Node, not jsdom |

### Running Tests

```bash
cd tday-web
npm run test                              # run all tests
TZ=UTC npx vitest tests/security/         # security suite only
TZ=UTC npx vitest --watch                 # watch mode for development
TZ=UTC npx vitest --coverage              # generate coverage report
```

Tests run with `TZ=UTC` to ensure deterministic date handling.

### Test Organization

```
tday-web/tests/
├── security/                        # Functional security tests
│   ├── authCredentialEnvelope.test.ts
│   ├── authRouteOrigin.test.ts
│   ├── authThrottleResponse.test.ts
│   ├── authPasswordProofChallengeRoute.test.ts
│   ├── fieldEncryption.test.ts
│   ├── middlewareAuthz.test.ts
│   ├── mobileProbeContract.test.ts
│   ├── password.test.ts
│   └── passwordProofChallenge.test.ts
├── summary/                         # AI summary logic
│   ├── todoSummary.test.ts
│   └── todoSummaryRoute.test.ts
├── recurrence/                      # RFC 5545 recurrence logic
│   ├── genTodoFromRRule.test.ts
│   ├── multiDayTodo.test.ts
│   └── rruleExpansion.test.ts
├── nlp/                             # NLP title parsing
│   ├── todoNlp.test.ts
│   └── todoNlpRoute.test.ts
└── guardrails/                      # Best practice enforcement
    ├── security.test.ts
    ├── coding-standards.test.ts
    ├── architecture.test.ts
    ├── api-guidelines.test.ts
    ├── android-standards.test.ts
    └── dependency-hygiene.test.ts
```

Tests are grouped by domain, not by technical layer. Each test file covers a specific capability.

### Guardrail Tests

The `tests/guardrails/` suite enforces coding standards, architecture rules, and best practices by statically scanning source files. These tests do **not** execute application code — they read source files and validate patterns, structure, and conventions. Failures indicate violations of documented best practices.

```bash
cd tday-web
npm run test -- tests/guardrails/           # run all guardrail tests
npm run test -- tests/guardrails/security   # security guardrails only
```

#### `security.test.ts` — Security Best Practices

| What it checks | Rule enforced |
|---------------|---------------|
| No hardcoded secrets or private keys in source | Secrets must come from env vars or mounted files |
| Every private API route is protected | All non-public endpoints require authentication |
| Security headers are configured | CSP, HSTS, X-Frame-Options, CORP, etc. |
| No sensitive data patterns in `console.log`/`console.info` | Passwords, tokens, secrets must never be logged |
| `.env.example` documents rate limit, lockout, and CAPTCHA vars | Auth hardening config is discoverable |
| Database schema has `tokenVersion`, `AuthThrottle`, `eventLog` | Session revocation, rate limiting, and audit trail support |

#### `coding-standards.test.ts` — Code Quality Rules

| What it checks | Rule enforced |
|---------------|---------------|
| No TypeScript `!` non-null assertions in source | Handle nullability explicitly, never force-assert |
| No `as any` without a justification comment | Type safety must not be silently bypassed |
| No `@ts-ignore` without an explanation | Suppressions require documented reasoning |
| No inline hex colors in TSX `style` attributes | Colors must come from Tailwind/CSS variable tokens |
| No `console.log` in production source | Use `console.error` or `console.warn` with intent |
| No Kotlin `!!` force-unwrap operator | Use safe calls, Elvis, or early returns |
| No hardcoded `Color(0x...)` in Android screens | Colors must come from `MaterialTheme` or `Color.kt` |
| ViewModels use `StateFlow`, never `LiveData` | Project standard is `StateFlow` exclusively |
| ViewModels use `@HiltViewModel` annotation | All ViewModels must use Hilt DI |
| No Gson usage in Kotlin source | Serialization uses `kotlinx.serialization` only |

#### `architecture.test.ts` — Architecture Methodology

| What it checks | Rule enforced |
|---------------|---------------|
| Required project directories exist | `src/`, `src/lib/`, `src/components/`, `src/features/`, `docs/`, etc. |
| `src/lib/` is organized by domain with subdirectories | No unbounded catch-all `utils.ts` files |
| `src/features/` directories contain source files | Feature modules are not empty placeholders |
| All 11 locale JSON files exist and share top-level keys | Internationalization completeness across all languages |
| Android `core/`, `feature/`, `ui/theme/` packages exist | Package structure follows the documented architecture |
| Android theme files exist (`Color.kt`, `Theme.kt`, `Type.kt`, `Dimens.kt`) | Design tokens are centralized |
| Android `build.gradle.kts` derives version from `package.json` | Single source of version truth |

#### `api-guidelines.test.ts` — API Convention Enforcement

| What it checks | Rule enforced |
|---------------|---------------|
| Backend route handlers use authentication | All non-public endpoints require auth |
| Error response shape is `{ message }` | All errors return a predictable JSON shape |
| Custom error classes follow sealed hierarchy | Error hierarchy is consistent (`AppError`) |
| Tenant isolation enforced at query level | Data queries filter by `userID` |

#### `android-standards.test.ts` — Android Codebase Standards

| What it checks | Rule enforced |
|---------------|---------------|
| No unsafe `as` casts (without `?`) in Kotlin | Use safe casts `as?` instead |
| `Color.kt` defines both light and dark color sets | Complete theme coverage |
| `Theme.kt` configures both color schemes | Light/dark theme support |
| `Dimens.kt` exists and defines `TdayDimens` object | Centralized dimension tokens |
| `Dimens.kt` includes spacing, radius, and icon tokens | Complete dimension scale |
| ViewModels have `UiState` data class | Consistent state modeling |
| ViewModels use `viewModelScope` for coroutines | Lifecycle-aware coroutine launches |
| Mutable `StateFlow` is private with `_` prefix | Encapsulated mutable state |
| Retrofit API service uses `suspend` functions | Coroutine-based network calls |
| `NetworkModule` redacts cookie headers in logs | No session leakage in debug logs |
| Uses `kotlinx.serialization`, not Gson | Consistent serialization library |
| `EncryptedCookieStore` and `SecureConfigStore` exist | Encrypted local storage for sensitive data |

#### `dependency-hygiene.test.ts` — Configuration and Infrastructure

| What it checks | Rule enforced |
|---------------|---------------|
| `package.json` has valid semver version and is private | Proper package metadata |
| Required npm scripts exist (`dev`, `build`, `lint`, `test`) | Standard development workflow |
| Test script runs in UTC timezone | Deterministic date handling in tests |
| TypeScript strict mode is enabled | Maximum type safety |
| `@/*` path alias is configured | Consistent import paths |
| `.gitignore` excludes `node_modules`, `dist/`, `.env` | No build artifacts or secrets in git |
| `.env.example` documents all critical variables | Setup reference for new developers |
| `.env.example` contains no real secrets | Placeholder values only |
| Docker Compose defines required services | Infrastructure as code |
| Docker container drops capabilities and prevents privilege escalation | Container security hardening |
| CI workflows exist for PR gate and release | Automated quality gates |
| PR template explicitly mentions `Made-with` trailer | AI attribution awareness in review |
| `CONTRIBUTING.md` warns about auto-injected trailers | Developer awareness of IDE trailer injection |
| `commit-msg` hook script exists and strips `Made-with` trailers | Automated trailer removal |
| `commit-msg` hook strips AI `Co-authored-by` trailers | No AI attribution in git history |
| `install-hooks.sh` exists | Hook installation is documented and scriptable |
| `CODING_STANDARDS.md` documents git commit hygiene | Rules are discoverable |
| All required documentation files exist | Complete project documentation |
| Version synchronizes from `package.json` to Android | Single source of version truth |

### Naming Conventions

- File names: `<feature>.test.ts` (not `.spec.ts`).
- Test descriptions: use `describe` for the module/function and `it` for specific behaviors.

```typescript
describe("fieldEncryption", () => {
  it("encrypts and decrypts a string field round-trip", () => { ... });
  it("returns original value when encryption is not configured", () => { ... });
  it("throws on tampered ciphertext", () => { ... });
});
```

- Use descriptive names that read as sentences: `it("rejects expired CSRF tokens")`.

### What Must Be Tested

| Area | Required | Rationale |
|------|----------|-----------|
| Auth flows (login, register, session) | Yes | Security-critical |
| Rate limiting and lockout | Yes | Security-critical |
| Credential encryption/envelope | Yes | Security-critical |
| Field encryption | Yes | Data protection |
| Mobile probe contract | Yes | Client compatibility |
| Recurrence (rrule expansion) | Yes | Complex date logic |
| NLP (title parsing) | Yes | User-facing feature correctness |
| Guardrails: security practices | Yes | Enforce SECURITY.md rules via static analysis |
| Guardrails: coding standards | Yes | Enforce CODING_STANDARDS.md rules via static analysis |
| Guardrails: architecture | Yes | Enforce ARCHITECTURE.md structure and conventions |
| Guardrails: API guidelines | Yes | Enforce API_GUIDELINES.md patterns |
| Guardrails: Android standards | Yes | Enforce Android coding and theme conventions |
| Guardrails: dependency hygiene | Yes | Enforce config, CI, and documentation completeness |
| CRUD routes (happy path) | Recommended | Catch regressions |
| Error paths in routes | Recommended | Ensure proper status codes |

### Test Data

- Use factory functions or inline literals — no shared mutable fixtures.
- Use deterministic data (fixed dates, known UUIDs) for reproducibility.
- Clean up any state between tests.

## Backend Testing (Ktor)

### Tooling

| Tool | Purpose |
|------|---------|
| JUnit 5 | Test framework |
| Ktor `testApplication` | In-process server testing without a real port |
| kotlinx.serialization | JSON assertion helpers |

### Running Tests

```bash
./gradlew :tday-backend:test   # all backend tests
```

### Test Organization

Tests live in `tday-backend/src/test/kotlin/com/ohmz/tday/`. Security services (password hashing, encryption, JWT, credential envelope, password proof) have dedicated test classes.

### What Should Be Tested (Backend)

| Area | Priority |
|------|----------|
| Password hashing (PBKDF2 round-trip) | High |
| JWE token encode/decode | High |
| Credential envelope encryption/decryption | High |
| Password proof challenge flow | High |
| Field encryption round-trip | High |
| Auth throttle/lockout logic | High |
| Route-level auth enforcement | Medium |
| Service-layer business logic | Medium |

### Mocking Strategy (Backend)

- Use Ktor's `testApplication` for route tests with an in-memory configuration.
- Mock external services (Ollama) at the service boundary.
- Use actual implementations for pure functions (encryption, validation, date logic).

## Android Testing

### Tooling

| Tool | Purpose |
|------|---------|
| JUnit 4 | Unit test framework |
| AndroidX Test | Android-specific test utilities |
| Espresso | UI instrumentation tests |
| Compose UI Test | Compose-specific UI testing |

### Test Locations

```
android-compose/app/src/
├── test/           # Unit tests (JVM, no Android framework)
└── androidTest/    # Instrumented tests (emulator/device)
```

### What Should Be Tested (Android)

| Area | Type | Priority |
|------|------|----------|
| Repository data mapping (DTO → domain) | Unit | High |
| Offline cache serialization/deserialization | Unit | High |
| ViewModel state transitions | Unit | High |
| Notification scheduling logic | Unit | Medium |
| Screen composition (renders, interactions) | Instrumented | Medium |
| End-to-end auth flow | Instrumented | Low (manual for now) |

### Naming Convention (Android)

```kotlin
@Test
fun `should return Success when login credentials are valid`() { ... }

@Test
fun `should clear local data when session is invalidated`() { ... }
```

Use backtick-quoted descriptive names: `should <expected behavior> when <condition>`.

## Coverage Expectations

### Web

- Security tests: **mandatory** — every auth, encryption, and access control path must be tested.
- Business logic (recurrence, NLP, summaries): **mandatory**.
- API routes: **recommended** for at least the happy path.
- React components: **optional** — focus on complex interaction logic, not visual rendering.

### Backend (Ktor)

- Security services (JWT, password, encryption, throttle): **mandatory**.
- Route-level auth enforcement: **recommended**.
- Service-layer business logic: **recommended**.

### Android

- Repository mapping and cache logic: **mandatory** when tests are added.
- ViewModel state logic: **recommended**.
- UI composition tests: **optional** for now.

### Coverage Reporting

Vitest is configured with coverage support. Reports are written to `coverage/`. Review coverage locally before pushing changes to security-critical modules.

## Before Merging (Testing Checklist)

- [ ] `cd tday-web && npm run test` passes with no failures.
- [ ] `./gradlew :tday-backend:test` passes with no failures.
- [ ] New security-related code has tests.
- [ ] New business logic (recurrence, NLP, date handling) has tests.
- [ ] Tests are deterministic — no flaky time-dependent assertions.
- [ ] No `console.log` left in test files.
- [ ] Test names describe behavior, not implementation.

## Adding a New Test

### Web

1. Create the test file in the appropriate `tday-web/tests/<domain>/` directory.
2. Name it `<feature>.test.ts`.
3. Follow existing patterns: `describe` → `it` → assert.
4. Run `cd tday-web && npm run test` to verify.
5. Check that coverage didn't drop for the affected module.

### Backend

1. Create the test file in `tday-backend/src/test/kotlin/com/ohmz/tday/`.
2. Use JUnit 5 annotations (`@Test`, `@BeforeEach`, etc.).
3. For route tests, use Ktor's `testApplication` with `application { module() }`.
4. Run `./gradlew :tday-backend:test` to verify.
