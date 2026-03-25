# Contributing to T'Day

This document covers everything a developer needs to know before writing code, opening a PR, or deploying changes.

## Table of Contents

- [Development Setup](#development-setup)
- [Branch Strategy](#branch-strategy)
- [Commit Messages](#commit-messages)
- [Pull Request Process](#pull-request-process)
- [Coding Conventions](#coding-conventions)
- [Linting and Formatting](#linting-and-formatting)
- [Testing](#testing)
- [Before Merging Checklist](#before-merging-checklist)

## Development Setup

### Web (Next.js)

```bash
npm install                    # installs deps + runs prisma generate
cp .env.example .env.local     # configure local env
npx prisma migrate deploy      # apply migrations to local Postgres
npm run dev                    # http://localhost:3000 (Turbopack)
```

### Android (Compose)

1. Open `android-compose/` in Android Studio.
2. Ensure Android SDK 35 is installed.
3. Set the server URL at first launch to point to your local or remote T'Day instance.
4. Run on emulator or physical device.

### Database

PostgreSQL 15 is required. Use Docker or a local installation:

```bash
docker run -d --name tday_dev_db \
  -e POSTGRES_USER=myuser -e POSTGRES_PASSWORD=mypass -e POSTGRES_DB=mydb \
  -p 5432:5432 postgres:15
```

### Full Stack (Docker Compose)

```bash
docker compose up -d --build
docker exec -it tday_ollama ollama pull qwen2.5:0.5b
```

## Branch Strategy

| Branch | Purpose | Deploys to |
|--------|---------|------------|
| `master` | Production-ready code | GHCR Docker image (auto) |
| `develop` | Integration branch for features | — |
| `feature/*` | New features | — |
| `fix/*` | Bug fixes | — |
| `chore/*` | Tooling, deps, docs | — |

**Rules:**

- All work branches are created from `develop`.
- PRs to `master` are only accepted from `develop` (enforced by CI).
- Never push directly to `master` or `develop`.

### Branch Naming

```
feature/add-calendar-drag-drop
fix/login-session-expiry-redirect
chore/update-prisma-to-v7
```

Use lowercase, hyphens as separators, short but descriptive.

## Commit Messages

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <short summary>

<optional body>
```

**Types:** `feat`, `fix`, `chore`, `docs`, `style`, `refactor`, `test`, `perf`, `ci`

**Scopes (examples):** `web`, `android`, `api`, `auth`, `prisma`, `ci`, `docker`

**Examples:**

```
feat(api): add batch todo completion endpoint
fix(android): handle expired session during background sync
chore(deps): bump next.js to 15.6
docs: add architecture decision record for offline sync
```

- First line: imperative mood, max 72 characters.
- Body: explain *why*, not *what* (the diff shows what changed).
- Reference issue numbers when applicable: `Closes #42`.
- **No AI attribution.** Commit messages and PR descriptions must not include "Co-authored-by" trailers, "written by", or any other attribution referencing AI tools (Cursor, Codex, Copilot, ChatGPT, etc.). The human who commits the code owns it and is responsible for it.

## Pull Request Process

1. Create a branch from `develop` following naming conventions.
2. Make your changes following [coding standards](docs/CODING_STANDARDS.md).
3. Ensure lint passes: `npm run lint`.
4. Ensure tests pass: `npm run test` (includes guardrail tests).
5. Open a PR against `develop` using the [PR template](.github/PULL_REQUEST_TEMPLATE.md).
6. Request review from at least one maintainer.
7. Address all review comments.
8. Squash-merge when approved.

**CI enforcement:** PRs to `master` run lint and the full test suite automatically. The Docker image will **not** be built or released unless all tests pass. See [Deployment > Test-Before-Build Policy](docs/DEPLOYMENT.md#test-before-build-policy).

### PR Size Guidelines

- Aim for < 400 lines changed per PR.
- Split large features into incremental PRs.
- Refactoring PRs should not include behavior changes.

### Review Checklist (for reviewers)

- [ ] Changes match the PR description.
- [ ] No secrets, tokens, or credentials in the diff.
- [ ] Error handling covers failure paths.
- [ ] New API endpoints follow [API guidelines](docs/API_GUIDELINES.md).
- [ ] Database changes include a migration and are backward-compatible.
- [ ] Tests cover the happy path and at least one error path.
- [ ] No console.log / Log.d left from debugging.

## Coding Conventions

See [`docs/CODING_STANDARDS.md`](docs/CODING_STANDARDS.md) for the full rules. Key highlights:

### TypeScript (Web)

- Strict mode is enabled — no `any` unless absolutely unavoidable.
- Use `interface` for object shapes, `type` for unions and mapped types.
- Validate request bodies with Zod schemas.
- Use `@/` path alias for imports (never relative paths that climb more than one level).
- Error handling: throw `BaseServerError` subclasses; catch with `errorHandler`.

### Kotlin (Android)

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html).
- Use `StateFlow` for UI state, never `LiveData`.
- Prefix private mutable state with `_` (e.g., `_uiState`).
- All ViewModels use `@HiltViewModel` with constructor injection.
- Use `runCatching` for operations that can fail.
- Constants in `companion object` with `UPPER_SNAKE_CASE`.

## Linting and Formatting

### Web

```bash
npm run lint       # ESLint (next/core-web-vitals + next/typescript)
```

ESLint is configured in `eslint.config.mjs`. Fix all warnings before committing.

### Android

Use Android Studio's built-in formatter with default Kotlin style. Run **Analyze > Inspect Code** before pushing.

### General

- No trailing whitespace.
- Files end with a single newline.
- UTF-8 encoding everywhere.
- 2-space indentation for TypeScript/JSON; 4-space for Kotlin.

## Testing

See [`docs/TESTING.md`](docs/TESTING.md) for the full strategy. Quick reference:

### Web

```bash
npm run test                       # all tests
npx jest tests/security/           # security suite only
npx jest --watch                   # watch mode
```

- Tests live in `tests/` organized by domain.
- Name files `*.test.ts`.
- Security-critical code must have tests.

### Android

- Unit test dependencies are wired (`junit`, `espresso`, `compose-ui-test`).
- Tests go in `app/src/test/` (unit) and `app/src/androidTest/` (instrumented).
- Test naming: `should <expected behavior> when <condition>`.

## Before Merging Checklist

Every PR must satisfy these before merge:

- [ ] `npm run lint` passes with no warnings.
- [ ] `npm run test` passes with no failures (including guardrail tests).
- [ ] CI pipeline passes (lint + tests are enforced automatically on PRs to `master`).
- [ ] No secrets or credentials in the diff.
- [ ] Backward compatibility maintained (or migration provided).
- [ ] Prisma migration reviewed if schema changed.
- [ ] Error handling and logging added where appropriate.
- [ ] API changes documented in the PR description.
- [ ] Android changes tested on emulator or device.

**Note:** The release pipeline will not build or push a Docker image unless all tests pass. Broken tests block the entire release.
