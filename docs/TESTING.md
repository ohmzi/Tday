# Testing Strategy

This document defines testing expectations, conventions, and tooling for the web, backend, Android, and iOS codebases.

## Philosophy

- Test behavior, not implementation details.
- Security-critical code must always have tests.
- Tests are first-class code — apply the same quality standards as production code.
- For mobile UI work, verify Android/iOS parity even when only one platform changed.
- For data model work, verify shared DTOs, backend persistence, mobile local cache, and sync replay together.

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
npm run test -- tests/guardrails/         # guardrail suite only
npx vitest --watch                        # watch mode for development
npx vitest run --coverage                 # generate coverage report
TZ=UTC npx vitest run tests/unit/move-todo-to-day.test.ts
```

Use `TZ=UTC` for focused date-sensitive runs. The default `npm run test` command runs Vitest with the repository config in `vitest.config.ts`.

### Test Organization

```
tday-web/tests/
├── guardrails/                      # Static standards, security, API, docs, and platform checks
│   ├── android-standards.test.ts
│   ├── api-guidelines.test.ts
│   ├── architecture.test.ts
│   ├── coding-standards.test.ts
│   ├── dependency-hygiene.test.ts
│   ├── i18n-parity.test.ts
│   ├── security.test.ts
│   └── sentry-privacy.test.ts
├── setup/
│   └── web-storage.ts               # Browser storage stubs for React tests
└── unit/
    ├── AuthProvider.test.tsx
    ├── api-client.test.ts
    ├── create-todo-mutations.test.tsx
    ├── get-timezone.test.tsx
    ├── get-todo-timeline.test.tsx
    ├── get-todo.test.tsx
    ├── i18n.test.ts
    ├── move-todo-to-day.test.ts
    ├── publicRouteAuthGuard.test.tsx
    ├── release-info.test.ts
    ├── todo-form-create-close.test.tsx
    └── todo-toast-navigation.test.ts
```

Web tests are intentionally split into behavior-focused unit tests and repository-wide guardrails. Add a focused unit test beside the closest existing unit coverage when changing client behavior, API client behavior, cache mutation helpers, auth provider logic, release metadata, routing guards, timezone/date handling, or i18n behavior. Add or update guardrails when a documented standard should be enforceable across the repository.

### Guardrail Tests

The `tests/guardrails/` suite enforces coding standards, architecture rules, and best practices by statically scanning source files. These tests do **not** execute application code — they read source files and validate patterns, structure, and conventions. Failures indicate violations of documented best practices.

```bash
cd tday-web
npm run test -- tests/guardrails/           # run all guardrail tests
npm run test -- tests/guardrails/security.test.ts
```

#### `security.test.ts` — Security Best Practices

| What it checks | Rule enforced |
|---------------|---------------|
| No hardcoded secrets or private keys in source | Secrets must come from env vars or mounted files |
| Every private API route is protected | All non-public endpoints require authentication |
| Security headers are configured | CSP, HSTS, X-Frame-Options, CORP, etc. |
| No sensitive data patterns in `console.log`/`console.info` | Passwords, tokens, secrets must never be logged |
| `.env.example` documents rate limit, lockout, and CAPTCHA vars | Auth hardening config is discoverable |
| Security monitoring docs list emitted event codes | Operational alerting stays aligned with backend emitters |
| Database schema has `tokenVersion`, `AuthThrottle`, `eventLog` | Session revocation, rate limiting, and audit trail support |

#### `coding-standards.test.ts` — Code Quality Rules

| What it checks | Rule enforced |
|---------------|---------------|
| No TypeScript `!` non-null assertions in source | Handle nullability explicitly, never force-assert |
| No `as any` without a justification comment | Type safety must not be silently bypassed |
| No `@ts-ignore` without an explanation | Suppressions require documented reasoning |
| No inline hex colors in TSX `style` attributes | Colors must come from Tailwind/CSS variable tokens |
| No `console.log` in production source | Use `console.error` or `console.warn` with intent |
| No deep relative imports in web source | Use the `@/` alias instead of climbing across source directories |
| No Kotlin `!!` force-unwrap operator | Use safe calls, Elvis, or early returns |
| No hardcoded `Color(0x...)` in Android screens/components | Colors must come from the theme layer; broad detection is advisory in this suite |
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
| iOS docs and project structure are represented in repository docs | Native iOS remains a first-class surface |

#### `api-guidelines.test.ts` — API Convention Enforcement

| What it checks | Rule enforced |
|---------------|---------------|
| Backend route files use `Route` extension functions | Route modules stay composable and consistent |
| Backend data route handlers use authentication helpers | Non-public endpoints require authenticated context |
| Auth route files exist for the complete auth flow | CSRF, register, credential callback, session, and logout remain mounted |
| `Routing.kt` mounts every major route group | Route files are not orphaned after refactors |
| Shared route constants cover client-facing route groups | Backend route changes stay discoverable to shared/mobile clients |
| `StatusPages.kt` handles exceptions | HTTP error translation stays centralized |

#### `android-standards.test.ts` — Android Codebase Standards

| What it checks | Rule enforced |
|---------------|---------------|
| No unsafe `as` casts (without `?`) in Kotlin | Use safe casts `as?` instead |
| `Color.kt` defines both light and dark color sets | Complete theme coverage |
| `Theme.kt` configures both color schemes | Light/dark theme support |
| `Dimens.kt` exists and defines `TdayDimens` object | Centralized dimension tokens |
| Shared semantic color literals appear only in theme files | Feature code reuses named theme colors |
| No hardcoded user-facing Compose or Toast text | Static copy comes from Android string resources |
| ViewModels use `viewModelScope` for coroutines | Lifecycle-aware coroutine launches |
| Mutable `StateFlow` is private with `_` prefix | Encapsulated mutable state |
| Retrofit API service exists | Network access remains centralized behind the API service |
| `NetworkModule` uses `kotlinx.serialization`, not Gson | Consistent serialization library |
| `EncryptedCookieStore` and `SecureConfigStore` exist | Encrypted local storage for sensitive data |

#### `dependency-hygiene.test.ts` — Configuration and Infrastructure

| What it checks | Rule enforced |
|---------------|---------------|
| `package.json` has valid semver version and is private | Proper package metadata |
| Required npm scripts exist (`dev`, `build`, `lint`, `test`) | Standard development workflow |
| `.gitignore` excludes `node_modules`, `tday-web/dist`, `.env` | No build artifacts or secrets in git |
| `.env.example` documents all critical variables | Setup reference for new developers |
| `.env.example` contains no real secrets | Placeholder values only |
| Docker Compose defines required services | Infrastructure as code |
| Docker container drops capabilities and prevents privilege escalation | Container security hardening |
| CI workflows exist for PR gate and release | Automated quality gates |
| PR template includes a no-AI-attribution checklist item | AI attribution awareness in review |
| `commit-msg` hook script exists and strips `Made-with` trailers | Automated trailer cleanup |
| `install-hooks.sh` exists | Hook installation is documented and scriptable |
| All required documentation files exist | Complete project documentation |
| Version mirrors synchronize from `package.json` to iOS metadata and env examples | Single source of version truth |
| `postversion` stages every checked-in version mirror | Release bumps include all generated metadata |

#### `i18n-parity.test.ts` — Locale Key Parity

| What it checks | Rule enforced |
|---------------|---------------|
| Every locale has the same flattened key set as English | Adding or removing web copy requires updating all locale files |

#### `sentry-privacy.test.ts` — Telemetry Privacy and Coverage

| What it checks | Rule enforced |
|---------------|---------------|
| Backend, web, Android, and iOS declare Sentry SDK dependencies | Telemetry wiring remains explicit |
| Sentry initializes on every platform | Error reporting is available where configured |
| `sendDefaultPii` is disabled and IP addresses are stripped | Telemetry must not collect personal network identifiers |
| DSNs come from env/build configuration | No hardcoded Sentry DSNs in committed source |
| Exception capture and HTTP tracing are wired where expected | Production errors include useful, privacy-safe context |
| Source upload is conditional on `SENTRY_AUTH_TOKEN` | Local and self-hosted builds work without private tokens |
| `docs/TELEMETRY.md` documents collection and no-op behavior | Privacy expectations stay discoverable |

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
| Recurrence and date movement rules | Yes | Complex date logic |
| NLP (title parsing) | Yes | User-facing feature correctness; currently covered in backend services |
| Web API/cache mutation helpers | Yes | Client/server contract correctness |
| Guardrails: security practices | Yes | Enforce SECURITY.md rules via static analysis |
| Guardrails: coding standards | Yes | Enforce CODING_STANDARDS.md rules via static analysis |
| Guardrails: architecture | Yes | Enforce ARCHITECTURE.md structure and conventions |
| Guardrails: API guidelines | Yes | Enforce API_GUIDELINES.md patterns |
| Guardrails: Android standards | Yes | Enforce Android coding and theme conventions |
| Guardrails: dependency hygiene | Yes | Enforce config, CI, and documentation completeness |
| Guardrails: i18n parity | Yes | Keep all web locales aligned |
| Guardrails: telemetry privacy | Yes | Keep Sentry privacy defaults and docs aligned |
| Mobile Local Mode and sync behavior | Recommended | Prevent offline/local regressions |
| Android/iOS parity for visible mobile features | Manual + tests where practical | Avoid product drift |
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

Tests live in `tday-backend/src/test/kotlin/com/ohmz/tday/` and are grouped by package:

- `security/`: password hashing, JWT/JWE session handling, field encryption, credential envelope, and password proof.
- `routes/`: todo, floater, list, mobile probe, security enforcement, Apple app association, and auth route flows.
- `plugins/`: rate limiting.
- `services/`: NLP parsing and service-level behavior.

### What Should Be Tested (Backend)

| Area | Priority |
|------|----------|
| Password hashing (PBKDF2 round-trip) | High |
| JWE token encode/decode | High |
| Credential envelope encryption/decryption | High |
| Password proof challenge flow | High |
| Field encryption round-trip | High |
| Auth throttle/lockout logic | High |
| Todo, floater, list, and completed route behavior | High |
| Mobile probe and app association contract routes | Medium |
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

Current JVM tests cover API response helpers, offline sync state serialization, encrypted credential records, Room/cache mappers, todo delete/cache behavior, task rescheduling, realtime client behavior, app/auth ViewModel state, and login credential coordination.

### What Should Be Tested (Android)

| Area | Type | Priority |
|------|------|----------|
| Repository data mapping (DTO → domain) | Unit | High |
| Room cache mapping and legacy cache migration | Unit | High |
| Pending mutation creation/replay behavior | Unit | High |
| ViewModel state transitions | Unit | High |
| Local Mode server-only affordances | Unit/manual | High |
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

## iOS Testing

### Tooling

| Tool | Purpose |
|------|---------|
| XCTest | Unit and integration tests |
| Xcode test runner | Simulator/device execution |
| SwiftData in-memory containers | Repository/cache tests where practical |

### Running Tests

```bash
xcodebuild test -project ios-swiftUI/TdayApp.xcodeproj -scheme Tday -destination 'platform=iOS Simulator,name=iPhone 16,OS=18.6'
```

### Test Locations

```text
ios-swiftUI/Tests/
└── TdayCoreTests/
```

Current XCTest coverage includes API model contracts, cache mapper date parsing, completed-sync merging, connectivity classification, realtime client behavior, server URL persistence, system credential login handling, and Today widget snapshot storage.

### What Should Be Tested (iOS)

| Area | Type | Priority |
|------|------|----------|
| SwiftData cache mapping | Unit | High |
| Repository data mapping (API → domain/cache) | Unit | High |
| Pending mutation creation/replay behavior | Unit | High |
| ViewModel state transitions | Unit | Medium |
| Local Mode server-only affordances | Unit/manual | High |
| Reminder scheduling helpers | Unit | Medium |
| Navigation/deep-link routing helpers | Unit | Medium |

For visual polish, build the app and do a simulator/device spot check when automated UI tests are not practical.

## Coverage Expectations

### Web

- Guardrail tests: **mandatory** for standards that can be checked statically.
- Web API client, auth provider, cache mutation, timezone/date, routing guard, release, toast, and i18n behavior: **mandatory** when changed.
- Backend security and route behavior should be covered in backend tests, not duplicated as fake web route tests.
- React components: **optional** — focus on complex interaction logic, not visual rendering.

### Backend (Ktor)

- Security services (JWT, password, encryption, throttle): **mandatory**.
- Route-level auth enforcement: **recommended**.
- Service-layer business logic: **recommended**.

### Android

- Repository mapping and cache logic: **mandatory** when tests are added.
- ViewModel state logic: **recommended**.
- UI composition tests: **optional** for now.

### iOS

- SwiftData cache and repository mapping: **mandatory** when the local data shape changes.
- Local Mode and sync behavior: **recommended** for app-flow changes.
- UI tests: **optional** for now; use simulator/device spot checks for polish.

### Coverage Reporting

Vitest is configured with coverage support. Reports are written to `coverage/`. Review coverage locally before pushing changes to security-critical modules.

## Before Merging (Testing Checklist)

- [ ] `cd tday-web && npm run test` passes with no failures.
- [ ] `./gradlew :tday-backend:test` passes with no failures.
- [ ] `cd android-compose && ./gradlew :app:compileDebugKotlin` passes for Android code changes.
- [ ] `cd android-compose && ./gradlew :app:testDebugUnitTest` passes for Android data/ViewModel changes when tests exist.
- [ ] `xcodebuild test -project ios-swiftUI/TdayApp.xcodeproj -scheme Tday -destination 'platform=iOS Simulator,name=iPhone 16,OS=18.6'` passes for iOS code changes when the simulator is available.
- [ ] New security-related code has tests.
- [ ] New business logic (recurrence, NLP, date handling) has tests.
- [ ] New data model changes are covered across shared/backend/mobile cache as appropriate.
- [ ] Mobile UI changes received an Android/iOS parity pass.
- [ ] Tests are deterministic — no flaky time-dependent assertions.
- [ ] No `console.log` left in test files.
- [ ] Test names describe behavior, not implementation.

## Adding a New Test

### Web

1. Create the test file in `tday-web/tests/unit/` for behavior tests or `tday-web/tests/guardrails/` for repository standards.
2. Name it `<feature>.test.ts` or `<feature>.test.tsx`.
3. Follow existing patterns: `describe` → `it` → assert.
4. Run `cd tday-web && npm run test` to verify.
5. Check that coverage didn't drop for the affected module when coverage matters.

### Backend

1. Create the test file in `tday-backend/src/test/kotlin/com/ohmz/tday/`.
2. Use JUnit 5 annotations (`@Test`, `@BeforeEach`, etc.).
3. For route tests, use Ktor's `testApplication` with `application { module() }`.
4. Run `./gradlew :tday-backend:test` to verify.

### Android

1. Add unit tests under `android-compose/app/src/test/` or instrumentation tests under `android-compose/app/src/androidTest/`.
2. Prefer repository/cache/ViewModel tests before UI instrumentation.
3. Run `cd android-compose && ./gradlew :app:testDebugUnitTest`.

### iOS

1. Add tests under `ios-swiftUI/Tests/TdayCoreTests/`.
2. Prefer cache/repository/helper tests before UI tests.
3. Run the `xcodebuild test` command above or the same test action in Xcode.
