# Repo Housekeeping

This document captures maintenance expectations for T'Day so the repo stays easy to change.

## Documentation Audit

The markdown inventory was checked on 2026-05-29 with `git log -1 --date=short -- <file>`.

### Markdown Inventory At Audit Start

| File | Last commit date | Commit |
|------|------------------|--------|
| `.github/ISSUE_TEMPLATE/bug_report.md` | 2026-05-29 | `a057c1b` docs: update project documentation to reflect Local Mode, Floater tasks, and mobile parity |
| `.github/ISSUE_TEMPLATE/feature_request.md` | 2026-05-29 | `a057c1b` docs: update project documentation to reflect Local Mode, Floater tasks, and mobile parity |
| `.github/PULL_REQUEST_TEMPLATE.md` | 2026-05-29 | `a057c1b` docs: update project documentation to reflect Local Mode, Floater tasks, and mobile parity |
| `AGENTS.md` | 2026-05-29 | `a057c1b` docs: update project documentation to reflect Local Mode, Floater tasks, and mobile parity |
| `CONTRIBUTING.md` | 2026-05-29 | `a057c1b` docs: update project documentation to reflect Local Mode, Floater tasks, and mobile parity |
| `README.md` | 2026-05-29 | `a057c1b` docs: update project documentation to reflect Local Mode, Floater tasks, and mobile parity |
| `SECURITY.md` | 2026-05-29 | `a057c1b` docs: update project documentation to reflect Local Mode, Floater tasks, and mobile parity |
| `android-compose/README.md` | 2026-05-29 | `df1fd00` Centralize Android list icon visuals |
| `docs/API_GUIDELINES.md` | 2026-05-29 | `a057c1b` docs: update project documentation to reflect Local Mode, Floater tasks, and mobile parity |
| `docs/ARCHITECTURE.md` | 2026-05-29 | `a057c1b` docs: update project documentation to reflect Local Mode, Floater tasks, and mobile parity |
| `docs/CODING_STANDARDS.md` | 2026-05-29 | `df1fd00` Centralize Android list icon visuals |
| `docs/DATA_MODEL.md` | 2026-05-29 | `a057c1b` docs: update project documentation to reflect Local Mode, Floater tasks, and mobile parity |
| `docs/DEPLOYMENT.md` | 2026-05-29 | `a057c1b` docs: update project documentation to reflect Local Mode, Floater tasks, and mobile parity |
| `docs/PRODUCT_DIRECTION.md` | 2026-05-29 | `a057c1b` docs: update project documentation to reflect Local Mode, Floater tasks, and mobile parity |
| `docs/REMOTE_ACCESS.md` | 2026-05-29 | `a057c1b` docs: update project documentation to reflect Local Mode, Floater tasks, and mobile parity |
| `docs/REPO_HOUSEKEEPING.md` | 2026-05-29 | `a057c1b` docs: update project documentation to reflect Local Mode, Floater tasks, and mobile parity |
| `docs/TELEMETRY.md` | 2026-05-29 | `a057c1b` docs: update project documentation to reflect Local Mode, Floater tasks, and mobile parity |
| `docs/TESTING.md` | 2026-05-29 | `a057c1b` docs: update project documentation to reflect Local Mode, Floater tasks, and mobile parity |
| `docs/adr/001-next-js-monolith-with-native-mobile.md` | 2026-05-29 | `a057c1b` docs: update project documentation to reflect Local Mode, Floater tasks, and mobile parity |
| `docs/adr/002-postgresql-with-exposed.md` | 2026-05-29 | `a057c1b` docs: update project documentation to reflect Local Mode, Floater tasks, and mobile parity |
| `docs/adr/003-jwe-jwt-sessions.md` | 2026-05-29 | `a057c1b` docs: update project documentation to reflect Local Mode, Floater tasks, and mobile parity |
| `docs/adr/004-local-ai-via-ollama.md` | 2026-05-29 | `a057c1b` docs: update project documentation to reflect Local Mode, Floater tasks, and mobile parity |
| `docs/adr/005-offline-first-android-with-sync.md` | 2026-05-29 | `a057c1b` docs: update project documentation to reflect Local Mode, Floater tasks, and mobile parity |
| `docs/adr/006-rfc5545-recurrence.md` | 2026-05-29 | `a057c1b` docs: update project documentation to reflect Local Mode, Floater tasks, and mobile parity |
| `docs/adr/007-local-mode-and-floater-workspace.md` | 2026-05-29 | `1c5effd` docs: update project documentation to reflect Local Mode, Floater tasks, and mobile parity |
| `docs/remote-access/cloudflare-tunnel.md` | 2026-05-29 | `a057c1b` docs: update project documentation to reflect Local Mode, Floater tasks, and mobile parity |
| `docs/remote-access/frp.md` | 2026-05-29 | `a057c1b` docs: update project documentation to reflect Local Mode, Floater tasks, and mobile parity |
| `docs/remote-access/ngrok.md` | 2026-05-29 | `a057c1b` docs: update project documentation to reflect Local Mode, Floater tasks, and mobile parity |
| `docs/remote-access/ssh-tunnel.md` | 2026-05-29 | `a057c1b` docs: update project documentation to reflect Local Mode, Floater tasks, and mobile parity |
| `docs/remote-access/tailscale.md` | 2026-05-29 | `a057c1b` docs: update project documentation to reflect Local Mode, Floater tasks, and mobile parity |
| `docs/remote-access/wireguard.md` | 2026-05-29 | `a057c1b` docs: update project documentation to reflect Local Mode, Floater tasks, and mobile parity |
| `docs/remote-access/zerotier.md` | 2026-05-29 | `1c5effd` docs: update project documentation to reflect Local Mode, Floater tasks, and mobile parity |
| `docs/security/cloudflare-auth-hardening.md` | 2026-05-29 | `a057c1b` docs: update project documentation to reflect Local Mode, Floater tasks, and mobile parity |
| `docs/security/operations-hardening.md` | 2026-05-29 | `a057c1b` docs: update project documentation to reflect Local Mode, Floater tasks, and mobile parity |
| `ios-swiftUI/README.md` | 2026-05-29 | `a057c1b` docs: update project documentation to reflect Local Mode, Floater tasks, and mobile parity |

### Summary By Area

| Area | Last updated before this refresh | Notes |
|------|----------------------------------|-------|
| Root README / agent guide | 2026-05-29 | Refreshed for Local Mode, RootFeedDock, Floater/Anytime, mobile parity, and agent expectations. |
| Contributing / testing / ADR base | 2026-05-29 | Updated around current monorepo structure, verification expectations, and decision record links. |
| Architecture / API / data docs | 2026-05-29 | Covers floater APIs, Room cache, SwiftData cache, local mode, sync mutations, and mobile data flow. |
| Deployment / telemetry / security | 2026-05-29 | Covers mobile local/server mode context, credential handling, telemetry boundaries, and operations hardening. |
| Android README | 2026-05-29 | Covers Room cache, RootFeedDock, Floater, Local Mode, widgets, in-app updates, and shared list icon resources. |
| iOS README | 2026-05-29 | Covers Local Mode, RootFeedDock, Floater, SwiftData cache, and current native app structure. |
| Issue/PR templates | 2026-05-29 | Includes mobile parity, data contract, Local Mode, and docs-impact prompts. |
| Remote access guides | 2026-05-29 | Scoped to ingress setup; update only when ports, host binding, certificate trust, or recommended ingress changes. |

## Recent Product Changes Audited

Recent commits after the older documentation introduced or refined:

- Local Mode for offline-only Android and iOS workspaces.
- Floater/Anytime tasks, floater lists, completed floaters, and root feed navigation.
- `RootFeedDock` icon-to-text transitions across Android and iOS.
- Room-backed Android offline cache and SwiftData-backed iOS cache with mirrored `OfflineSyncState`.
- Unified reminder selectors, pull-to-refresh, empty states, sheet chrome, swipe actions, and task completion animations.
- Calendar paging, drag-and-drop rescheduling, overdue visibility, and cross-platform calendar polish.
- Mobile server credential handling, webcredentials/AASA support, in-app update/version compatibility, and offline notice cooldowns.

## Git Expectations

- Start with `git status --short --branch`.
- Keep work scoped and avoid opportunistic refactors.
- Do not revert user changes unless explicitly asked.
- Do not use destructive cleanup commands such as `git reset --hard` or `git checkout --` unless the user explicitly requests them.
- Commit as `ohmzi <6551272+ohmzi@users.noreply.github.com>` when attribution matters.
- Do not add AI attribution trailers or bypass hooks with `--no-verify`.

## Generated Files and Local Artifacts

Never commit:

- `node_modules/`
- `tday-web/dist/`
- `coverage/`
- Gradle `build/`, `.gradle/`, `.kotlin/`
- iOS `.build/`, DerivedData, archives, and user-specific Xcode files
- Android Studio/IDE local metadata unless intentionally tracked
- `.env`, local secrets, signing keys, keystores, DSYM uploads, or generated credentials

When adding a tool that creates new caches or outputs, update `.gitignore`, this document, and any guardrail test that enforces dependency hygiene.

## Documentation Maintenance

Documentation should change in the same PR as the behavior when:

- A new product surface, route, table, DTO, local cache record, mutation kind, or app mode is added.
- Android and iOS behavior changes in a user-facing way.
- Setup, deployment, versioning, signing, telemetry, or security configuration changes.
- Verification commands, simulator requirements, or CI gates change.
- An implementation decision changes the direction captured by an ADR.

Small bug fixes do not need broad documentation churn, but they should update docs when they reveal a rule future contributors need to know.

## Cross-Platform Housekeeping

Mobile features should be checked in pairs:

- Android files under `android-compose/app/src/main/java/com/ohmz/tday/compose/feature/<feature>/`
- iOS files under `ios-swiftUI/Tday/Feature/<Feature>/`
- Shared data contracts under `shared/`
- Backend routes/services/tables under `tday-backend/` when the feature is server-backed

Before finishing a mobile change, compare:

- Feature surface and labels
- Counts, empty states, and disabled states
- Local Mode behavior
- Offline/online transitions
- Navigation and deep links
- Dark mode
- Reminder/widget/update implications

## Cleanup Policy

- Prefer deleting dead code over preserving unused compatibility stubs.
- Keep the codebase easy to scan: narrow files, named concepts, plain control flow, and boundaries that match product/data ownership.
- Keep feature-specific helpers close to the feature until a second consumer exists.
- Promote repeated mobile styling into platform theme/component layers.
- Keep backend service methods small enough to preserve tenant isolation reviewability.
- Keep shared DTOs minimal; do not leak persistence-only fields into shared contracts unless clients need them.
- Keep dependencies flowing in one direction: UI calls state/actions, state coordinates services/repositories, services/repositories touch network/database/cache.
- When a migration or compatibility shim is temporary, document the removal condition in code or this document.

## Pre-PR Housekeeping Checklist

- `git status --short --branch` shows only intentional changes.
- Documentation and templates reflect new behavior.
- New generated outputs are ignored or intentionally tracked.
- Data changes include migration, DTO, local cache, and sync updates as needed.
- API changes are documented and backwards compatibility is considered.
- Android and iOS parity was checked for mobile UI work.
- Relevant verification commands ran, or the reason for skipping is recorded in the PR.
