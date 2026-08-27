# Deployment

How T'Day is built, deployed, and operated in production. Product direction and data boundaries are documented in [`PRODUCT_DIRECTION.md`](PRODUCT_DIRECTION.md) and [`DATA_MODEL.md`](DATA_MODEL.md).

## Environments

| Environment | Branch | URL | Deployment |
|-------------|--------|-----|------------|
| Production | `master` | `tday.ohmz.cloud` | GitHub Actions publishes the image to GHCR; the host runs `scripts/deploy-release.sh` to pull and recreate |
| Development | `develop` | Local only | `docker compose -f docker-compose.yaml -f docker-compose.build.yaml up -d --build`, or local dev servers |

Merging to `master` publishes a release; it does **not** reach into this host. Deploying is a separate,
deliberate step on the deploy host — see [Deploying a release](#deploying-a-release).

Mobile clients can also run in Local Mode without a deployed backend. Deployment work affects Server Mode, remote access, app compatibility checks, and server-backed sync.

## Docker

### Architecture

```
docker-compose.yaml            # the stack; backend image is pulled, never built
├── tday_backend    (Ktor + Vite SPA, port 2525 → 8080)
├── tday_db         (PostgreSQL 15, internal port 5432)
└── tday_ollama     (optional Ollama AI profile, internal port 11434)

docker-compose.build.yaml      # opt-in override that builds tday_backend locally
docker-compose.gpu.yaml        # opt-in override for NVIDIA-accelerated Ollama
```

The `tday-backend` service in `docker-compose.yaml` has **no `build:` section**. Its image is
`${TDAY_BACKEND_IMAGE:-ghcr.io/ohmzi/tday:latest}`, so a bare `docker compose up -d` on the deploy
host pulls a released image and can never silently replace production with a working-tree build.
Layer `docker-compose.build.yaml` on top when you actually want to build.

### Build

The Docker image (`Dockerfile.backend`) is a multi-stage build:

1. **Stage 1 — Frontend** (`node:20-alpine`): `npm ci` + `npm run build` in `tday-web/` → static assets at `/web/dist`
2. **Stage 2 — Backend** (`eclipse-temurin:21-jdk-alpine`): Copies Docker-specific Gradle files from `docker/`, `shared/src`, and `tday-backend/src`, then runs `./gradlew :tday-backend:buildFatJar -x test`
3. **Stage 3 — Runtime** (`eclipse-temurin:21-jre-alpine`): Non-root user `tday`, copies fat JAR to `app.jar` and static files to `/app/static`, sets `STATIC_FILES_DIR=/app/static`

The production image is a **single JVM process** serving both the REST API and the static SPA.

Each frontend build also stamps a **unique build id** (`git-sha + UTC timestamp`) into the JS bundle as `__BUILD_ID__` and emits a matching `dist/version.json` (`{ "buildId", "version" }`), served at `/version.json`. This is the cache key that drives client cache invalidation — see [Web Cache Invalidation & Client Updates](#web-cache-invalidation--client-updates). For a readable SHA in the id, pass it as a build arg (the `.git` dir is not copied into the frontend stage):

```bash
docker compose -f docker-compose.yaml -f docker-compose.build.yaml \
  build --build-arg GIT_SHA=$(git rev-parse --short HEAD) tday-backend
```

Without it the id falls back to a timestamp only — still unique per build, just less traceable.
The release workflow passes **no** build args, so CI-published images carry a `dev-<timestamp>`
build id and an empty `VITE_SENTRY_DSN`; both are cosmetic for cache invalidation (the id is still
unique per build) but browser-side Sentry is off in the published image.

```bash
# Local build (development). docker-compose.build.yaml adds the build: section and
# tags the result tday-backend:local so it cannot be confused with a released image.
docker compose -f docker-compose.yaml -f docker-compose.build.yaml up -d --build

# Optional local AI summaries
# Set OLLAMA_URL=http://ollama:11434 in .env.docker, then run:
docker compose --profile ai pull ollama ollama-model-setup
docker compose -f docker-compose.yaml -f docker-compose.build.yaml --profile ai up -d --build
```

Setting `COMPOSE_FILE=docker-compose.yaml:docker-compose.build.yaml` in your shell (or in the root
`.env`) makes a plain `docker compose up -d --build` build locally again, if you prefer that on a
development machine. Never set it on the deploy host.

When the `ai` profile is enabled, Compose starts `tday_ollama` plus a one-shot model setup container. The setup container pulls `qwen3.5:0.8b` and attempts to remove the old `qwen2.5:0.5b` model. Pull the Ollama images during updates too; the qwen3.5 model requires a recent Ollama runtime. If the AI profile is not enabled, Summary still works through the backend logic fallback.

Ollama runs on CPU by default. For NVIDIA GPU acceleration, install the NVIDIA Container Toolkit on the host and add the GPU override file to every Compose command:

```bash
docker compose -f docker-compose.yaml -f docker-compose.gpu.yaml --profile ai up -d
```

Add `-f docker-compose.build.yaml` to that as well if you are also building the backend locally.

### Docker Security

The `tday_backend` container runs with:
- `security_opt: no-new-privileges:true`
- `cap_drop: ALL`
- No privileged mode

### Network Security

By default the backend port is bound to **`127.0.0.1`** (localhost only). External clients cannot reach it over HTTP — an ingress method is required to bridge external traffic to `localhost:2525`.

```
Browser / Mobile App
  └─ HTTPS / VPN ─► [ ingress method ] ─► localhost:2525 ─► tday_backend :8080
```

The binding is controlled by two variables in the **project-root `.env`** file (not `.env.docker`):

| Variable | Default | Purpose |
|----------|---------|---------|
| `TDAY_HOST_BIND` | `127.0.0.1` | Interface to bind on the Docker host |
| `TDAY_HOST_PORT` | `2525` | Host port mapped to the container's `8080` |

To allow direct external access (development / trusted LAN only):

```bash
# .env (project root)
TDAY_HOST_BIND=0.0.0.0
```

When exposing the port externally, set `TDAY_ENV=production` in `.env.docker` so the backend enables secure cookies and HSTS headers.

For detailed instructions on all supported remote access methods — including Cloudflare Tunnel, Tailscale, WireGuard, ZeroTier, SSH tunnels, ngrok, and frp — see **[Remote Access](REMOTE_ACCESS.md)**.

### Self-hosting on a NAS (TrueNAS / Unraid / Synology / Proxmox)

These are the recurring "I deployed it but can't reach it / it behaves oddly" issues on NAS and
home-server platforms, with the symptom and the fix.

| Symptom                                                                                                           | Cause                                                                                                        | Fix                                                                                                                                                                                                                                            |
|-------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Container is "running" but the web UI is unreachable from any other device                                        | Default bind is `127.0.0.1` (localhost of the container/VM only)                                             | Either put a reverse proxy / VPN in front (recommended — gives HTTPS), or for a trusted LAN set `TDAY_HOST_BIND=0.0.0.0` in the **root `.env`** and recreate. On Unraid/TrueNAS custom-app UIs, map host port → container `8080` the same way. |
| Behind a reverse proxy, registration/login throttles trip far too early, or all users share one rate-limit bucket | Proxy isn't forwarding the real client IP, so every request looks like the proxy's IP                        | Configure the proxy to send `X-Forwarded-For` (or `X-Real-IP`; Cloudflare sends `cf-connecting-ip`). T'Day reads these in priority order — no extra "trust proxy" flag needed.                                                                 |
| Logged out constantly / "secure cookie" warnings when served over a domain                                        | App not in production mode, so cookies aren't marked Secure for an HTTPS origin, or the origin is cross-site | Set `TDAY_ENV=production` in `.env.docker` once you terminate HTTPS at the proxy. Add the external origin to `CORS_ALLOWED_ORIGINS` only if the web app is served from a *different* origin than the API.                                      |
| Server **log** timestamps are in UTC, not your local time                                                         | Container defaults to UTC                                                                                    | Optional: set `TZ` (IANA name) in the root `.env` — passed to the backend container. This affects **logs only**. Task due times, overdue, and reminders are always shown in each user's own device timezone and are unaffected by `TZ` (see [Server timezone](#server-timezone)). |
| Data lost after recreating the container                                                                          | App data lives only in the `postgres_data` named volume                                                      | Keep the named volume (don't `down -v`); back it up with scheduled `pg_dump`. On Unraid/TrueNAS, map the Postgres data path to persistent array storage, not a temp/ephemeral dir.                                                             |
| New version doesn't appear after pulling                                                                          | NAS UIs use their own update flow                                                                            | Unraid: container → **Force update**. Portainer: **Recreate** → **Re-pull image** (see [Updating in Production](#updating-in-production)). `docker compose pull && up -d` for plain Compose.                                                   |

For HTTPS without exposing a port at all (and to satisfy the secure-context requirements of PWAs and
WebCrypto), prefer a tunnel/VPN from [Remote Access](REMOTE_ACCESS.md) over binding `0.0.0.0`
directly.

### Health Checks

| Service | Check | Interval |
|---------|-------|----------|
| PostgreSQL | `pg_isready` | 1s (10 retries) |
| Ollama, optional `ai` profile | `ollama list` | 20s (5 retries) |
| T'Day backend | Depends on PostgreSQL; uses Ollama opportunistically when configured | — |

## CI/CD Pipeline

### Workflows

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `pr-gate.yml` | PR to `master` | Validates source branch (`develop` only), runs web lint + test, backend test |
| `release.yml` | Push to `master` | Runs lint + tests, resolves the release version (auto-bumping the patch when it is already tagged), builds the signed APK and the Docker image, pushes the release commit, tags, publishes the GitHub release, and only then pushes the image |
| `ios-testflight.yml` | Push of a `v*` tag (uploads), or `workflow_dispatch` on any ref (build-only) | Diffs the tag against the last release that actually reached TestFlight; when an iOS-relevant file changed, archives the `Tday` scheme on macOS and — on a tag push only — uploads it to TestFlight |

### Test-Before-Build Policy

**No Docker image is built or published unless all tests pass.** This is enforced in both CI workflows:

- **PR Gate** (`pr-gate.yml`): On every PR to `master`, the pipeline validates the source branch is `develop`, then runs `npm run lint` and `npm run test` in `tday-web/`, followed by `./gradlew :tday-backend:test`. PRs cannot merge if either step fails.
- **Release** (`release.yml`): On push to `master`, a `lint-and-test` job runs first. The `build-and-release` job (Docker build, push, tag, release) has `needs: lint-and-test` — it will not start unless lint and tests pass.

```
PR to master:
  check-source-branch → lint-and-test → hook-check → (merge allowed)

Push to master:
  lint-and-test → build-and-release
                    build:   version bump → APK (signed + cert verified) → Docker image
                    publish: release commit → tag → GitHub release + APK → Docker push
```

This ensures:
- Broken code never produces a Docker image.
- Security guardrails, coding standards, and architecture tests gate every release.
- Test failures block the pipeline before any artifact is published.

### Publication Order

Inside `build-and-release`, everything that can fail is built before anything leaves the runner, and
the Docker image is pushed **last** — after the tag and the GitHub release with its APK exist.

That order is chosen for its failure modes, not for tidiness. The backend runs `exact` compatibility
with `updateRequired: true`, so a deployed server rejects every client whose `X-Tday-App-Version`
differs from its own, and the Android in-app updater can only fetch an APK from the latest GitHub
release. So:

- **Image published without a release** (the old order) is unrecoverable from the user's side: pull
  the image and every client is locked out with no version to update to.
- **Release published without an image** is benign: the running server stays on the previous image
  and keeps serving, and re-running the workflow publishes the image.

The release commit is pushed to `master` before the tag, the release, or the image, because it is the
only step that an outside event (a concurrent push to `master`) can reject. Abandoning the run there
leaves no tag, no release, and no image behind.

The image is *built* before the publish steps and only *pushed* afterwards, so a broken
`Dockerfile.backend` fails while nothing has been published yet. The push step replays the same build
from the layer cache rather than rebuilding it.

### Release Process

1. Features are developed on `feature/*` branches.
2. PRs merge into `develop` after review.
3. When ready for release, PR `develop` → `master`.
4. `pr-gate.yml` validates source branch, then runs lint + tests.
5. On merge to `master`, `release.yml`:
   - Runs lint and the full test suite (including guardrails).
   - **Resolves the release version.** If root `version.json` still carries a version that is already
     tagged, the job runs `scripts/version.mjs bump patch` — so **every merge into `master` produces a
     release, even when nobody bumped the version by hand**. A version a PR deliberately raised
     (minor/major) is used as-is.
   - Generates structured release metadata from the commit range since the previous GitHub release.
   - Builds and signs the Android release APK, then reports the signing certificate's SHA-256 digest.
     A missing APK fails the run — it is a required release asset.
   - **Only if tests pass**: builds the Docker image for `ghcr.io/ohmzi/tday:latest` and `:v<version>`
     without pushing it yet.
   - Commits the version bump plus `tday-web/public/release/current-release.json` and
     `latest-changes.json` back to `master` as `chore(release): v<version> [skip release]`. This push
     uses the write deploy key in `RELEASE_DEPLOY_KEY`, which is the bypass actor on the `master`
     ruleset (GitHub does not allow granting that bypass to `github-actions[bot]` on a
     personally-owned repository).
   - Creates the Git tag and a GitHub release with the APK attached.
   - Pushes the Docker image, last — see [Publication Order](#publication-order).
   - Fast-forwards `develop` to `master` so the bump does not conflict on the next PR. This step is
     non-fatal; if it is skipped, merge `master` into `develop` by hand.

Because the release job bumps the patch version itself, the version climbs one patch per merge. Bump
`minor`/`major` in a PR when a release deserves it.

### Version Bumping

The **single source of truth** for the app/server version is root `version.json`. All other systems derive from it:

- **CI/CD**: Reads `version.json` → Docker image tags (`:v1.6.0`, `:latest`), Git tags, GitHub releases.
- **Web**: `scripts/version.mjs sync` mirrors the version into `tday-web/package.json` and `package-lock.json`; Vite bundles that package value.
- **Backend**: `tday-backend/build.gradle.kts` parses `version.json`, embeds it as `tday-version.json`, and `AppConfig` uses it as the default `TDAY_APP_VERSION`/backend release fallback.
- **Android**: `app/build.gradle.kts` parses root `version.json` at build time → `versionName` and computed `versionCode`.
- **iOS**: `scripts/version.mjs sync` mirrors the version, build number, and update URL into `Info.plist`, Xcode project metadata, and `project.yml`.
- **Backend compatibility templates**: The same sync script mirrors version/update-required values into `.env.example` and `tday-backend/.env.example`. Live deployment env files such as `.env.docker` stay operator-owned; leave `TDAY_APP_VERSION` unset there so the container inherits the image's version, and set it only to deliberately pin the probe.
- **Runtime**: Android sends `BuildConfig.VERSION_NAME` and iOS sends `CFBundleShortVersionString` in the `X-Tday-App-Version` HTTP header.

To bump the version deliberately before merging to `master` (a patch bump happens automatically on
merge, so this is only needed for `minor`/`major`):

```bash
node scripts/version.mjs bump patch   # 1.6.0 -> 1.6.1
node scripts/version.mjs bump minor   # 1.6.0 -> 1.7.0
node scripts/version.mjs bump major   # 1.6.0 -> 2.0.0
node scripts/version.mjs check
```

The bump command updates `version.json`, increments `ios.buildNumber`, and syncs every checked-in mirror. If you manually edit `version.json`, run `node scripts/version.mjs sync` and then `node scripts/version.mjs check`.

**Never** set release version numbers directly in Gradle files, iOS project files, web package files, lockfiles, or example env templates.

### Version Reference

Every file that contains or controls a version number, grouped by platform.

#### Root Manifest (source of truth)

| File | What | Notes |
|------|------|-------|
| `version.json` | App semver, exact compatibility policy, iOS build number, iOS update URL | **Edit this file or use `scripts/version.mjs bump`**; all other app/server versions derive from it. |

#### Web App

| File | What | Notes |
|------|------|-------|
| `tday-web/package.json` / `package-lock.json` (`"version"`) | App semver mirror | Auto-synced from `version.json`. |
| `tday-web/vite.config.ts` (`__APP_VERSION__`) | Build-time define from `npm_package_version` | Injected into the SPA at build; fallback `"0.0.0"`. |
| `tday-web/src/main.tsx` | Sentry release (`tday-web@<version>`) | Derived at build time. `VITE_SENTRY_TRACES_SAMPLE_RATE` controls trace sampling. |

#### Android

| File | What | Notes |
|------|------|-------|
| `android-compose/app/build.gradle.kts` | `versionName` / `versionCode` | Parsed from root `version.json` at build time. `versionCode` = `major*10_000_000 + minor*10_000 + patch` (`0.7.2` → `70002`), one decimal slot per component so the encoding never collides. `minor` ≤ 999, `patch` ≤ 9999, ceiling 2_100_000_000 — all enforced by `require` checks that fail the build. |
| `android-compose/.../TdayApplication.kt` | Sentry release (`tday-android@<version>`) | Uses `BuildConfig.VERSION_NAME`. `SENTRY_TRACES_SAMPLE_RATE` or `local.properties:sentryTracesSampleRate` controls trace sampling. |
| `android-compose/.../NetworkModule.kt` | `X-Tday-App-Version` HTTP header | Uses `BuildConfig.VERSION_NAME`. |

#### iOS

| File | What | Notes |
|------|------|-------|
| `ios-swiftUI/Tday/Info.plist` (`CFBundleShortVersionString`) | Marketing version (e.g. `1.21.0`) | Auto-synced from `version.json`. |
| `ios-swiftUI/Tday/Info.plist` (`TdayUpdateURL`) | App Store/TestFlight update URL | Auto-synced from `version.json` `ios.updateUrl`; leave empty only for builds without direct iOS update action. |
| `ios-swiftUI/project.yml` / `TdayApp.xcodeproj/project.pbxproj` (`MARKETING_VERSION`, `CURRENT_PROJECT_VERSION`) | Xcode project metadata | Auto-synced from `version.json`; keep both aligned when regenerating the project. |
| `ios-swiftUI/Tday/Info.plist` (`CFBundleVersion`) | Build number | Mirrors `ios.buildNumber`; the bump command increments it. |
| `ios-swiftUI/.../SentryConfiguration.swift` | Sentry release (`tday-ios@<version>`) | Uses `CFBundleShortVersionString`. `SENTRY_DSN` and `SENTRY_TRACES_SAMPLE_RATE` flow through `Info.plist` build settings. |

#### Backend

| File | What | Notes |
|------|------|-------|
| `tday-backend/build.gradle.kts` (`version`) | Gradle artifact version | Parsed from root `version.json` and embedded as `tday-version.json`. |
| `tday-backend/.../Application.kt` | Sentry release (`tday-backend@<version>`) | Reads `TDAY_BACKEND_VERSION`, then `TDAY_APP_VERSION`, then embedded manifest version. `SENTRY_TRACES_SAMPLE_RATE` controls trace sampling. |

For Sentry project setup, release artifact verification, alerting, smoke drills,
and failure triage, see [`SENTRY_RUNBOOK.md`](SENTRY_RUNBOOK.md). Do not store
Sentry account passwords in deployment files; use DSNs for SDK configuration and
least-privilege auth tokens only for release/source artifact upload.

#### Server Compatibility (`TDAY_APP_VERSION`)

The manifest and `TDAY_APP_VERSION` environment variable tell the backend which app version it is compatible with. When exact compatibility is enabled with `TDAY_UPDATE_REQUIRED=true`, mobile clients that connect with a different version are shown an "Update Required" or "Server Update Needed" screen.

Local Mode does not require this probe. Server Mode Android and iOS clients use `/api/mobile/probe` plus the `X-Tday-App-Version` header to decide whether the installed app and server can safely sync. The backend also rejects mismatched mobile API requests: older apps receive `426 Upgrade Required`; apps newer than the server receive `409 Conflict`.

| File | Purpose | Notes |
|------|---------|-------|
| `.env.docker` | **Live override** for the running Docker container | Leave `TDAY_APP_VERSION` commented out (the default) so the server reports the version baked into the image and follows every release automatically. Set it only to pin the probe to a specific version. |
| `.env.example` | Template for new deployments (project root) | Auto-synced to the manifest version; copy the value into live env files when that version should be required. |
| `tday-backend/.env.example` | Template for local backend development | Auto-synced to the manifest version; copy the value into live env files when that version should be required. |
| `tday-backend/.../AppConfig.kt` (`probeAppVersion`, `backendVersion`) | Reads `TDAY_APP_VERSION` at startup | Env-driven, falling back to the embedded `tday-version.json` — which `tday-backend/build.gradle.kts` builds from root `version.json`. |

**Keeping the server on the same version as the apps:** don't. Leave `TDAY_APP_VERSION` unset in
`.env.docker`. The backend then reports `versionDefaults.version`, which is compiled into the image
from root `version.json`, so pulling a new image is the only step — the server, web, Android, and
iOS all report the same version and move together on every release.

```bash
docker compose pull tday-backend && docker compose up -d tday-backend
```

Only set `TDAY_APP_VERSION` when you deliberately want the probe to advertise something other than
the image's own version. A stale pin here is invisible until a client refuses to sync.

### Android Signing

Distributable Android release builds must use the same release keystore every time, or Android will reject updates with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.

- CI supplies the release signing credentials through `RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD`.
- The preferred secrets are `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_PASSWORD`, and `RELEASE_KEY_ALIAS`. The release workflow still accepts the legacy `TWA_KEYSTORE_BASE64`, `TWA_STORE_PASSWORD`, and `TWA_KEY_PASSWORD` secrets, and derives the alias from the keystore when `RELEASE_KEY_ALIAS` is unset — but it now logs which secret supplied each value and warns on every run that takes either fallback, so the fallback can never be silent.
- The three keystore secrets must all come from the same generation. Setting some of the `RELEASE_*` secrets while leaving the rest on `TWA_*` fails the run immediately, naming the missing secrets, instead of surfacing later as an opaque "keystore password was incorrect".
- Deriving the alias depends on parsing `keytool -list` output, so it fails — loudly, listing the aliases it found — if the keystore ever holds more than one. CI never guesses which alias to sign with, because a wrong guess ships an APK that cannot update existing installs. Set `RELEASE_KEY_ALIAS` to remove that dependency.
- Every release logs the signing certificate's SHA-256 digest from `apksigner verify --print-certs`, so a change of signing identity is visible in the run log rather than on the first user who cannot install the update. Set the `ANDROID_SIGNING_CERT_SHA256` repository *variable* (not a secret — certificate digests are public, and the same value is served in `/.well-known/assetlinks.json`) to turn that report into an enforced pin that fails the release on any mismatch.
- Local `assembleRelease` or `bundleRelease` builds now fail fast if those variables are missing, instead of silently producing a debug-signed release APK that cannot update an existing release install.
- For a local-only build that is not meant to update an existing release-signed install, you can opt in explicitly with `-PallowDebugSignedRelease=true`.
- The Android app can download a release APK in-app and hand it directly to the system installer. The first sideloaded update still requires enabling "Install unknown apps" for T'Day in Android settings.
- Historical note: GitHub Android APKs published before the stable signing fix on April 1, 2026 may have been signed with ephemeral debug certificates from CI runners. Devices on one of those installs must uninstall once and reinstall `v1.8.1` or newer before sideloaded updates will work again.

### iOS Signing and Associated Domains

- The iOS app uses `ios-swiftUI/TdayApp.xcodeproj`, automatic signing, and the `Tday` scheme.
- `/.well-known/apple-app-site-association` is served by the backend for webcredentials/deep-link support.
- `CFBundleShortVersionString`, `CFBundleVersion`, iOS `MARKETING_VERSION`, `CURRENT_PROJECT_VERSION`, `TdayUpdateURL`, and example `TDAY_APP_VERSION` values are synced from root `version.json` by `scripts/version.mjs sync`.
- Set `ios.updateUrl` in `version.json` to the App Store or TestFlight URL before distributing an iOS build that should offer direct updates.
- Both configurations set `CODE_SIGN_IDENTITY = Apple Development` once at the project level, with no target overrides. That is deliberate and is **not** a bug: under `CODE_SIGN_STYLE = Automatic` the archive is signed with a development identity and the App Store re-sign happens in `-exportArchive`, which the lane drives with `signingStyle: automatic` + `method: app-store`. Forcing `Apple Distribution` at archive time would require a distribution certificate to resolve ~30 minutes earlier than it is actually needed, for no benefit.
- `DEVELOPMENT_TEAM` is `JUFACN2FS3` in the project and is overridden from CI by the `IOS_TEAM_ID` repository variable.

### iOS TestFlight Releases

`.github/workflows/ios-testflight.yml` archives the five iOS/watchOS bundles and uploads them to
TestFlight. It is separate from `release.yml` on purpose: an Apple upload needs a macOS runner and
depends on Apple-side state (App IDs, profiles, processing) that should never be able to block the
Docker image or the Android APK.

#### What ships in one archive

The `Tday` scheme archives one app with four embedded bundles, so **five App IDs** and five App Store
distribution profiles are involved:

| Bundle | Identifier | Capabilities |
|---|---|---|
| App | `com.ohmz.tday.ios` | App Groups `group.com.ohmz.tday`, Associated Domains `webcredentials:tday.ohmz.cloud` |
| Widget extension | `com.ohmz.tday.ios.TdayTasksWidget` | App Groups `group.com.ohmz.tday` |
| Share extension | `com.ohmz.tday.ios.TdayShareExtension` | App Groups `group.com.ohmz.tday` |
| Watch app | `com.ohmz.tday.ios.watchkitapp` | App Groups `group.com.ohmz.tday` |
| Watch complication | `com.ohmz.tday.ios.watchkitapp.widget` | App Groups `group.com.ohmz.tday` |

`TdayTests` is a unit-test bundle; it is not archived and needs no App ID. There is no push, no
iCloud, no Sign in with Apple, no HealthKit, and no keychain sharing.

#### Trigger and path filter

The workflow fires on **`push` of a `v*` tag**. `release.yml` pushes that tag over
`RELEASE_DEPLOY_KEY`, and deploy-key pushes trigger workflows (unlike `GITHUB_TOKEN`-produced
events, which is why an `on: release: [published]` trigger would never fire at all). At the tag,
`version.json` is authoritative and the pbxproj's `CURRENT_PROJECT_VERSION` already equals
`ios.buildNumber` — **the pipeline never derives or increments a build number.**

A cheap `decide` job on Linux gates the expensive macOS job. It:

1. refuses any tag whose name disagrees with `version.json` at that commit (this is what stops the
   45 legacy `v1.x` tags from ever building, so the trigger pattern can stay a simple `v*`);
2. runs `node scripts/version.mjs check`, so a drifted mirror fails before an archive starts;
3. resolves the comparison base — **the last tag whose iOS build actually reached TestFlight**,
   not simply the previous tag (see below);
4. runs `scripts/ios-release-changed.mjs` to diff the two tags;
5. checks that the Apple secrets exist, but only once it knows a build is wanted — so a
   web-only release stays green on a repo that has not finished the Apple setup.

That diff cannot be a `paths:` filter — `on: push: tags:` does not accept one. It also cannot be a
naive "did `ios-swiftUI/` change", because **every** release commit rewrites `Info.plist`,
`project.yml`, `project.pbxproj` and all ten `guide.*.json` files with new version numbers. So the
script compares those specific files with the version tokens normalised away: a file whose *only*
difference is the release bump does not count, while any other edit to that same file does.
`version.json` is excluded from the relevant set entirely, since `ios.buildNumber` increments on
every release. A `workflow_dispatch` run with `force` bypasses the filter.

#### Two modes: `release` and `verify`

The workflow runs in one of two modes, and the mode is **derived from the event and the ref** — it
is not an input:

| Trigger | Mode | What it does |
|---------|------|--------------|
| Push of a `v*` tag | `release` | Archive, sign, export, **upload to TestFlight** |
| `workflow_dispatch`, any ref, any inputs | `verify` | Archive, sign, export, **stop** |

`verify` mode is how a change to this pipeline gets tested. Before it existed, the only way to
exercise the iOS build was to cut a real release, so iterating on a Swift compile error that only
CI can reproduce cost one version bump per error. Now:

```bash
gh workflow run ios-testflight.yml --ref develop
```

archives the whole app on a macOS runner, proves the five bundles compile and that signing,
provisioning and the export all resolve, and uploads nothing.

**There is deliberately no input that makes a manual run upload.** App Store Connect spends a
`(version, buildNumber)` pair the moment it *accepts* an upload and will never accept that pair
again — not even after the build fails processing. A branch dispatch reads the same `version.json`
a release does, so an upload from `develop` would burn the build number the next real release
needs, with no way to reclaim it short of a version bump. Two independent gates enforce this:

1. the `decide` job computes the mode from `GITHUB_EVENT_NAME` / `GITHUB_REF` in one place and
   publishes it as a job output, and the macOS job refuses to start unless the mode and
   `TDAY_SKIP_UPLOAD` it receives are a consistent pair (an empty output — a renamed step, a typo
   — fails the job rather than defaulting to an upload);
2. the fastlane `beta` lane **re-derives the same predicate from GitHub's own environment** and
   downgrades itself to build-only if the workflow ever hands it something inconsistent. It does
   not trust the workflow, so an expression bug in the YAML cannot on its own produce an upload.

Dispatching against a *tag* ref is still `verify` — the event, not just the ref, has to be a push.
The retry path for a genuine release is GitHub's own re-run of the tag-push run, which preserves
`event: push`.

A verify run also cannot be mistaken for a release afterwards: the comparison-base walk below
filters run history to `--event push`, so a build-only run is never eligible to become the "last
tag that shipped".

**Why the base is not just the previous tag.** `git describe ... HEAD^` looks right and is subtly
wrong: it is pure git topology and knows nothing about whether that release's iOS build succeeded.
If v0.8.0 changed a Swift file but its upload died on a transient App Store Connect error and
nobody re-ran it, a web-only v0.8.1 would diff `v0.8.0..v0.8.1`, see only version mirrors, skip,
and report **success** — with TestFlight still serving the pre-0.8.0 binary and two consecutive
releases claiming green. So `decide` instead walks this workflow's own run history (via
`gh run list` plus the run's job-level conclusions, which is why the job needs `actions: read`) and
anchors on the newest tag whose `testflight` **job** concluded `success`. A run whose macOS job was
*skipped* still concludes `success` at the run level, so the job conclusion is what is checked.
A stranded change therefore stays inside the diff until it actually ships.

When no such tag can be established — first ever run, a deleted tag, a `gh` failure — the answer is
to **build**. Failing safe there costs macOS minutes; failing the other way silently drops a
release.

#### Signing model

App Store Connect API key (`.p8`) plus Xcode automatic ("cloud") signing via
`-allowProvisioningUpdates` — **not** `fastlane match`. No certificates are committed and no
keychain is provisioned.

What `-allowProvisioningUpdates` does here is **mint and renew the five App Store provisioning
profiles on demand**, against App IDs and an App Group that **already exist**. It does not create
the App Group, and you should not rely on it to create the App IDs either. All five entitlements
files request `com.apple.security.application-groups`, so if `group.com.ohmz.tday` is missing the
archive fails on the first App-Group-bearing target with *"Provisioning profile … doesn't include
the com.apple.security.application-groups entitlement"* — and missing it on only one of the five
(the watch complication is the easy one to forget) fails only that target, deep into a 40-minute
build. Steps 1–3 of the one-time setup below are therefore mandatory, not optional.

**The API key needs the Admin role.** See step 4 — App Manager is enough to upload a build but not
to create the distribution certificate and profiles this model depends on.

The `.p8` is written `0600` under `RUNNER_TEMP`, which is outside the checked-out workspace, and
deleted in an `ensure` block. It is never written into the repository, the `.ipa`, or a workflow
artifact — the `.ipa` is deliberately not uploaded as an artifact at all.

**Where the distribution certificate comes from — watch this on the first few runs.** Nothing in
the pipeline installs or persists a certificate, so the only path to an Apple Distribution identity
is Xcode obtaining one during the build. There are two possibilities and the workflow does not get
to choose:

- **Cloud-managed** (expected, and what Xcode 13+ does when it authenticates with an API key rather
  than a signed-in Apple ID): Apple holds the private key, and every run reuses the same
  certificate. This is the arrangement this pipeline is designed around.
- **Local**: Xcode mints a certificate whose private key exists only in that ephemeral runner's
  keychain and dies with the job. Apple caps Apple Distribution certificates at **2 per team**, so
  the third release would fail at CodeSign with *"You already have a current Distribution
  certificate"* and keep failing until someone revokes by hand in the portal.

The `Report the signing identities that were used` step prints
`security find-identity -v -p codesigning` after every build so this is observable rather than
guesswork. After the first successful run, check the Developer portal: the distribution certificate
should be listed as **Managed**. If a new certificate appears on each release instead, the fallback
is to export a distribution `.p12` once, store it as a secret, and have the lane `create_keychain` +
`import_certificate` — leaving `-allowProvisioningUpdates` responsible only for profiles.

#### Re-run safety

Re-running the workflow on the same tag would re-upload an identical `(version, buildNumber)` pair,
which App Store Connect rejects. The `beta` lane therefore asks `latest_testflight_build_number` what
TestFlight already holds for the current marketing version and exits cleanly when this build is
already there. The upload is also the **last** step in the lane, so anything that can fail has
already failed before a byte is sent.

**"Already uploaded" does not mean "testers can install it."** A build number is spent the moment
App Store Connect *accepts* the upload, even if processing then fails — and Apple will not let you
re-use it. So the lane also reads the build's `processingState` and, when it is `FAILED` or
`INVALID`, **fails the run** with an explicit instruction to bump `version.json` rather than
skipping quietly. That state lookup goes through Spaceship's private API and is deliberately
wrapped in a rescue: if it cannot answer, the lane degrades to a skip whose message says plainly
that a build missing from TestFlight means a spent build number.

For the same reason the `force` dispatch input only bypasses the **path filter**. It cannot force
an upload — a manual run is always `verify` mode — and even on the release path a duplicate upload
cannot succeed. There is no escape hatch for a spent build number other than a version bump.

In `verify` mode the lane **skips the duplicate check entirely** rather than consulting App Store
Connect. A run that will not upload has nothing to protect, and asking anyway would break the
verification case: once a build number is spent, the check would either skip `build_app` and
report a green run that compiled nothing, or — for a `FAILED`/`INVALID` build — fail a run whose
only job was to prove that the project still compiles.

#### Export compliance

`Tday/Info.plist` declares `ITSAppUsesNonExemptEncryption = false`. Without it, every upload parks at
"Missing Compliance" awaiting a manual answer — and because the lane sets
`skip_waiting_for_build_processing: true` (App Store Connect processing takes 10-30 minutes and
would bill macOS runner minutes for a result nothing consumes), fastlane never gets a chance to
answer that question after the fact. The key is what makes skipping the wait safe.

T'Day's cryptography is:

| Where | What | Purpose |
|---|---|---|
| `CredentialEnvelope.swift` | AES-256-GCM (CryptoKit) + RSA-2048 OAEP-SHA256 (Security) | Wraps the sign-in username/password for transport to the user's own server |
| `ProbeDecryptor.swift` | AES-256-GCM (CryptoKit) | Decrypts the server's version-compatibility probe |
| `NetworkConfiguration`, `TodayTasksWidget` | SHA-256 (CryptoKit) | Hashing, not encryption |
| Everywhere | HTTPS/TLS via `URLSession` | Transport |

**The basis for the exemption**, in order of how cleanly it fits:

1. **Encryption limited to that provided within the operating system.** Every primitive above is
   Apple's own CryptoKit / Security / URLSession. T'Day implements no cipher, bundles no
   third-party crypto library, and ships no modified crypto.
2. **Publicly available source code** (EAR §740.13(e)) — T'Day is open source at
   `github.com/ohmzi/Tday`. Note this exemption carries a **one-time email notification** to BIS
   (`crypt@bis.doc.gov`) and the NSA (`enc@nsa.gov`) giving the source URL. Relying on it means
   sending that email.

Two bases are deliberately **not** relied on:

- *"Limited to authentication"* on its own. It covers `CredentialEnvelope`, but **not**
  `ProbeDecryptor`, which applies confidentiality to ordinary application data (`appVersion`,
  `updateRequired`, `compatibilityMode`) using a key bundled in the app. An earlier draft of this
  PR claimed the authentication exemption covered everything; that was wrong, and the declaration
  now rests on basis 1 above, which does cover it.
- *"Short key length."* AES-256 and RSA-2048 exceed the 56-bit symmetric / 512-bit asymmetric
  thresholds.

> **This is a legal export declaration, not a build setting.** The Apple account holder must confirm
> it before the first upload. The reasoning is restated in a comment above the key in `Info.plist`.
> If they are not comfortable with the above, the alternative is `ITSAppUsesNonExemptEncryption =
> true` plus the annual self-classification report to BIS; the pipeline still works, but the first
> build of each version then needs the compliance question answered by hand in App Store Connect.

#### Secrets and variables

| Name | Kind | Purpose |
|---|---|---|
| `ASC_KEY_ID` | secret | 10-character App Store Connect API Key ID |
| `ASC_ISSUER_ID` | secret | Issuer UUID shown above the key list |
| `ASC_KEY_P8_BASE64` | secret | base64 of the `.p8` Apple lets you download exactly once |
| `TDAY_PROBE_ENCRYPTION_KEY` | secret | Already set — the same secret `release.yml` passes to the Android build. `Tday/Info.plist` publishes `TdayProbeEncryptionKey` as `$(TDAY_PROBE_ENCRYPTION_KEY)`, and an undefined build setting expands to `""`, which makes `ProbeDecryptor` return `nil` and silently disables the whole server version-compatibility gate |
| `IOS_TEAM_ID` | **variable** | Apple Team ID (`JUFACN2FS3`). Not a secret — it is printed on every provisioning profile |
| `IOS_XCODE_VERSION` | **variable** | Optional Xcode pin, e.g. `16.4`. Unset, the job warns and uses the runner default |

The **`decide` job on Linux** fails with an actionable message if any of those secrets is missing,
rather than 40 minutes later inside `xcodebuild` — and rather than booting a macOS runner (billed at
10x the Linux rate) to run a four-variable emptiness test. No secret is ever echoed; only emptiness
is tested. The check is gated on the build actually being wanted, so a web-only release still
reports green on a repo where the Apple setup is unfinished.

`TDAY_PROBE_ENCRYPTION_KEY` reaches the build through a temporary `xcconfig` written `0600` under
`RUNNER_TEMP` and deleted in the lane's `ensure` block — **not** through `xcargs`, because gym
echoes the assembled `xcodebuild` command into the build log.

#### One-time setup (Apple account holder only)

The App Store Connect app record already exists — bundle `com.ohmz.tday.ios`, Apple ID
`6772349115`, name "Tday". Do not create another. Everything below is on the Apple side or in
repository settings, and cannot be done from a pull request.

1. **Register the five App IDs** at
   [developer.apple.com → Certificates, Identifiers & Profiles → Identifiers](https://developer.apple.com/account/resources/identifiers/list).
   Create an App ID for each identifier in the table above.
2. **Create the App Group** `group.com.ohmz.tday` (Identifiers → App Groups), then enable the **App
   Groups** capability on all five App IDs and assign that group to each.
3. **Enable Associated Domains** on `com.ohmz.tday.ios` only.
4. **Mint an App Store Connect API key**: App Store Connect → Users and Access → Integrations → App
   Store Connect API → Team Keys → `+`. Give it the **Admin** role (Account Holder also works).
   Download the `.p8` immediately; Apple only offers it once.

   > **Admin, not App Manager.** App Manager covers the TestFlight half — it can upload a build.
   > It does **not** cover the Developer-Portal half, and this signing model lives on that half:
   > `-allowProvisioningUpdates` drives xcodebuild against Certificates, Identifiers & Profiles to
   > create the distribution certificate and the five App Store profiles, and Apple's program-roles
   > matrix restricts creating *distribution* certificates and *distribution* profiles to Account
   > Holder and Admin. With an App Manager key the run gets all the way to the CodeSign step of the
   > first signed target — roughly 40 minutes of macOS time — and then fails with a provisioning
   > error. **If the key already in the repository secrets was minted as App Manager, re-mint it.**
5. **Set the repository secrets and variables** (from the repo root):

   ```bash
   gh secret set ASC_KEY_ID        --body "XXXXXXXXXX"
   gh secret set ASC_ISSUER_ID     --body "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
   gh secret set ASC_KEY_P8_BASE64 --body "$(base64 -w0 ~/Downloads/AuthKey_XXXXXXXXXX.p8)"
   #   on macOS the flag is different:
   #   gh secret set ASC_KEY_P8_BASE64 --body "$(base64 -i ~/Downloads/AuthKey_XXXXXXXXXX.p8)"

   gh variable set IOS_TEAM_ID --body "JUFACN2FS3"
   ```

6. **Pin Xcode** once you have seen a run log (the "Select Xcode" step prints every version
   installed on the runner):

   ```bash
   gh variable set IOS_XCODE_VERSION --body "16.4"   # use a version the log actually lists
   ```

7. **Confirm the export-compliance declaration** described above.
8. **Create the TestFlight group and public link**: App Store Connect → TestFlight → Groups → `+`.
   Enable **Public Link** and, if you want testers to get builds without per-build action, turn on
   automatic distribution for the group.
9. **Set `ios.updateUrl`** once that public link exists. It is `""` today, so
   `AppViewModel.bundleUpdateURL()` returns `nil` and both in-app update surfaces render an
   explanatory sentence with no button. Edit `version.json`, then:

   ```bash
   node scripts/version.mjs sync && node scripts/version.mjs check
   ```

   Never hand-edit the mirrored values — `Info.plist`, `project.yml` and the pbxproj are generated
   from `version.json`.

#### The first run

`workflow_dispatch` is only exposed for workflows that exist on the **default branch**, so this
workflow cannot be exercised at all until it merges to `master` — and that same merge triggers
`release.yml`, which bumps, commits and pushes a tag, which fires this workflow automatically.
There is no gap in between, and the path filter cannot save you: a change to
`.github/workflows/ios-testflight.yml` is itself iOS-relevant, so the first post-merge tag always
resolves `should_build=true`.

That means the first execution would otherwise be an unsupervised production archive of five
bundles plus a real TestFlight upload, on a pipeline where `xcodebuild` and `fastlane` have never
run once. Rehearse it with a `verify` run instead:

1. Finish steps 1–9 below **before** merging.
2. Merge to `master`. Cancel the automatic run the release tag triggers.
3. Dispatch the workflow manually — `gh workflow run ios-testflight.yml --ref develop`, or the
   **Run workflow** button. It archives, signs, exports the `.ipa` and stops, proving signing,
   profiles, entitlements and the App Group without touching TestFlight.
4. When that is green, cut the next release normally; the tag push uploads.

`verify` mode is permanent, not scaffolding. It is the right way to test any later change to
signing, the lane, or the Swift sources this pipeline compiles — and it is the only way to see a
compile error that reproduces on a macOS runner without spending a release to find it.

#### Building it locally

```bash
cd ios-swiftUI
bundle install
ASC_KEY_ID=... ASC_ISSUER_ID=... ASC_KEY_P8_BASE64=... \
  TDAY_SKIP_UPLOAD=true bundle exec fastlane beta
```

`TDAY_SKIP_UPLOAD=true` puts the lane in the same build-only mode `verify` runs use — archive and
export, no upload. Drop it to upload for real. Locally that is all it takes; inside GitHub Actions
the lane additionally refuses to upload from anything but a tag push, whatever this variable says.

`TDAY_PROBE_ENCRYPTION_KEY` is optional locally: the lane warns loudly and continues without it,
producing a build whose server version-compatibility gate is inert. CI treats it as required.

There is deliberately **no committed `Gemfile.lock`** — see the comment in `ios-swiftUI/Gemfile`.
To add one, run `bundle lock --add-platform arm64-darwin --add-platform x86_64-darwin` on a machine
with Ruby and commit the result; the workflow picks it up with no change.

## Configuration

### Environment Variables

All configuration is via environment variables. See `.env.example` for the full list with documentation.

The Ktor backend (`AppConfig.kt`) loads all settings from environment variables and supports `_FILE` suffixes for secret file mounts (Docker/Kubernetes).

#### Required

| Variable | Purpose |
|----------|---------|
| `DATABASE_URL` | PostgreSQL connection string (JDBC or `postgresql://` format) |
| `AUTH_SECRET` | JWE encryption secret (generate: `openssl rand -base64 32`) |

#### Recommended

| Variable                            | Purpose                                                                                                                        |
|-------------------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| `AUTH_PBKDF2_ITERATIONS`            | Password hash iterations (default: 310,000)                                                                                    |
| `AUTH_SESSION_MAX_AGE_SEC`          | Rolling web-session inactivity window in seconds (default: 2,592,000)                                                          |
| `AUTH_SESSION_ABSOLUTE_MAX_AGE_SEC` | Absolute session cap from original login time in seconds (default: 7,776,000)                                                  |
| `AUTH_SESSION_RENEW_THRESHOLD_SEC`  | Renewal threshold in seconds before expiry (default: 604,800)                                                                  |
| `AUTH_CREDENTIALS_PRIVATE_KEY`      | RSA key for credential envelope encryption; recommended in production to avoid ephemeral startup keys                          |
| `APPLE_TEAM_ID`                     | Apple Developer Team ID used in Tday's canonical `apple-app-site-association` webcredentials payload for iOS Password AutoFill |
| `IOS_BUNDLE_ID`                     | iOS app bundle identifier for webcredentials association (default: `com.ohmz.tday.ios`)                                        |
| `ANDROID_PACKAGE_NAME`              | Android app package name for Digital Asset Links credential sharing (default: `com.ohmz.tday.compose`)                         |
| `ANDROID_SHA256_CERT_FINGERPRINTS`  | Comma-separated SHA-256 signing certificate fingerprints for Android Digital Asset Links credential sharing                    |
| `OLLAMA_URL`                        | Optional Ollama service URL. Leave blank for backend logic-only summaries; use `http://ollama:11434` with the Compose `ai` profile |
| `OLLAMA_MODEL`                      | AI model for summaries when Ollama is enabled (default: `qwen3.5:0.8b`)                                                        |

The native iOS app saves and retrieves Tday credentials under the canonical `tday.ohmz.cloud` Apple Passwords scope, regardless of the server URL a user connects to.
The native Android app can save and retrieve app-scoped password credentials immediately. Sharing
credentials with the canonical `tday.ohmz.cloud` web scope requires
`ANDROID_SHA256_CERT_FINGERPRINTS` so the backend can serve `/.well-known/assetlinks.json`.

### Server timezone

**You almost never need to set this, and the server's timezone does not affect anyone's task
times.** T'Day uses the industry-standard "store UTC, render per-user" model:

- Every task due time is stored in **UTC**. Each client converts the user's local input to a UTC
  instant when saving, and converts it back to **that user's own device timezone** when displaying.
- Each user's timezone is detected from their device and synced to the server automatically
  (`X-User-Timezone` header + `GET /api/timezone`, stored on the user record). It is used for
  per-user, server-side groupings like the "today / overdue" summary.
- All server-side time math runs in UTC, so it is **independent of the container clock**. Two users
  in different timezones see the same task at the correct local time for each of them, on web, iOS,
  and Android alike.
- Reminders are scheduled **on each device** and fire at the task's absolute due instant, rendered
  in that device's local time — also independent of the server timezone.

Because of this, the `TZ` environment variable only changes the **server's log timestamps**. Leave
it at the default `UTC` unless you specifically want logs in your local zone:

```bash
# root .env — affects log timestamps only, NOT task due times or reminders
TZ=America/New_York
```

`TZ` is read by Docker Compose and passed into the backend container
(`docker-compose.yaml`: `TZ: ${TZ:-UTC}`). The PostgreSQL service is unaffected — all timestamps
are stored and compared in UTC. If task times ever appear off for a single user, the cause is that
user's **device** timezone being wrong, not the server `TZ`.

#### Docker Compose (project-root `.env`)

These variables are read by Docker Compose for port binding. They belong in the **root `.env`** file, not `.env.docker`.

| Variable | Default | Purpose |
|----------|---------|---------|
| `TDAY_HOST_BIND` | `127.0.0.1` | Network interface for the host port binding (`127.0.0.1` = localhost only, `0.0.0.0` = all interfaces) |
| `TDAY_HOST_PORT` | `2525` | Host port mapped to the backend container's port `8080` |

#### Optional

| Variable | Purpose |
|----------|---------|
| `TDAY_ENV` | Runtime mode (`production` enables production-only behavior such as HSTS and secure session cookies; `NODE_ENV` is still accepted as a fallback) |
| `CORS_ALLOWED_ORIGINS` | Comma-separated list of allowed cross-origin web origins (same-origin requests work without it) |
| `DATA_ENCRYPTION_KEY` / `DATA_ENCRYPTION_KEY_ID` | Field-level encryption at rest |
| `API_RATE_LIMIT_MAX` / `API_RATE_LIMIT_WINDOW_SEC` | Global `/api/**` request budget |
| `INFRA_RATE_LIMIT_MAX` / `INFRA_RATE_LIMIT_WINDOW_SEC` | `/health` and `/api/mobile/probe` request budget |
| `SUMMARY_RATE_LIMIT_MAX` / `SUMMARY_RATE_LIMIT_WINDOW_SEC` | `POST /api/todo/summary` request budget |
| `CHANGE_PASSWORD_RATE_LIMIT_MAX` / `CHANGE_PASSWORD_RATE_LIMIT_WINDOW_SEC` | `POST /api/user/change-password` request budget |
| `WS_RATE_LIMIT_MAX` / `WS_RATE_LIMIT_WINDOW_SEC` | `/ws` connect-attempt budget |
| `AUTH_LIMIT_SESSION_GET_MAX` / `AUTH_LIMIT_SESSION_GET_WINDOW_SEC` | `GET /api/auth/session` budget |
| `AUTH_LIMIT_CREDENTIALS_KEY_MAX` / `AUTH_LIMIT_CREDENTIALS_KEY_WINDOW_SEC` | `GET /api/auth/credentials-key` budget |

### Secrets via Files

For Docker/Kubernetes secret mounts, append `_FILE` to any sensitive variable:

```bash
AUTH_SECRET_FILE=/run/secrets/auth_secret
DATABASE_URL_FILE=/run/secrets/database_url
```

The Ktor backend's `AppConfig` reads file contents into the corresponding variable at startup when `_FILE` variants are present.

## Database Migrations

### How Migrations Work

T'Day uses **Flyway** for database migrations. Flyway runs automatically on Ktor backend startup (`DatabaseConfig.kt`), applying any pending migrations from `tday-backend/src/main/resources/db/migration/`.

Migration files follow the naming convention: `V<number>__<description>.sql`. The current baseline sequence is:

- `V1__baseline.sql`: legacy placeholder kept for compatibility.
- `V2__full_schema.sql`: full schema snapshot generated from the live PostgreSQL schema for clean installs.
- `V3__add_missing_indexes.sql`: first incremental migration after the schema snapshot.

Existing databases with pre-Flyway schema but no migration history are baselined at version `2`, which skips the placeholder and full-schema migrations and applies only new incremental migrations.

### Creating Migrations

1. Create the next SQL file in `tday-backend/src/main/resources/db/migration/` following the naming convention (`V4__...`, `V5__...`, and so on).
2. Write the DDL/DML statements, or generate/review SQL from a local database when a full snapshot is explicitly needed.
3. Update the corresponding Exposed `Table` objects in `tday-backend/src/main/kotlin/com/ohmz/tday/db/tables/` to match.
4. Restart the backend — Flyway applies the migration automatically.
5. Commit both the migration SQL and the Exposed table changes.

### Migration Safety

- Always review generated SQL before committing.
- Do not regenerate `V2__full_schema.sql` for routine schema changes. Add a new incremental migration instead.
- Backward-compatible changes are preferred (add columns with defaults, don't drop columns immediately).
- For destructive changes, use a multi-step migration:
  1. Add new column / table.
  2. Deploy code that writes to both old and new.
  3. Migrate data.
  4. Remove old column / table.

## Rollback Strategy

### Application Rollback

Redeploy the previous release tag. The backend image is a compose variable, so no file needs editing
and the rest of the stack is left alone:

```bash
./scripts/deploy-release.sh --version 1.4.0
# equivalently, by hand:
TDAY_BACKEND_IMAGE=ghcr.io/ohmzi/tday:v1.4.0 docker compose up -d --no-build tday-backend
```

Never `docker compose down -v` — the `-v` deletes the `postgres_data` volume, which is the only copy
of the data.

### Database Rollback

Flyway does not support automatic down-migrations. For rollbacks:

1. Write a manual SQL script to reverse the migration.
2. Apply via `psql` or a migration tool.
3. Keep database backups before every release.

## Observability

### Logging

- Application logs go to stdout/stderr via **Logback** (Docker captures them).
- Security events are written to the `eventLog` database table.
- OkHttp (Android) logs at DEBUG level with cookie redaction.
- Log configuration is in `tday-backend/src/main/resources/logback.xml`.

### Monitoring Recommendations

- Monitor `auth_lockout`, `auth_limit_ip`, and `auth_limit_ip_burst` event codes for abuse (the
  burst code fires when an IP exceeds the short-window account-creation tier).
- Set alerts for container restarts.
- Monitor PostgreSQL connection pool (HikariCP) and disk usage.
- Check the Ollama health endpoint only when the `ai` profile is enabled. Without Ollama, Summary falls back to backend logic.

### Backups

- Database: Schedule automated PostgreSQL dumps with encryption at rest.
- Secrets: Store in a secrets manager with audit logging.

## Updating in Production

### Deploying a release

Merging to `master` builds and publishes `ghcr.io/ohmzi/tday:v<version>` and `:latest`. It does not
touch any host. On the deploy host, pull that image and recreate the backend:

```bash
git pull                                              # so version.json names the release you want
./scripts/deploy-release.sh --url https://tday.ohmz.cloud
```

The script:

1. reads `version` from root `version.json` (override with `--version X.Y.Z` or `--image REF`),
2. pulls `ghcr.io/ohmzi/tday:v<version>`, falling back to `:latest` with a warning if that tag is
   not published yet (`--no-fallback` to fail instead),
3. compares the Flyway migrations baked into the **running** image with the target image's and
   refuses to continue if the target adds any, unless you pass `--backup` (take one now) or
   `--skip-backup` (you already have one) — Flyway has no down-migrations,
4. recreates only `tday-backend` with `TDAY_BACKEND_IMAGE` pinned to that tag, waits for the
   healthcheck, then verifies `/version.json` and `/api/mobile/probe` report the expected version,
   on loopback and (with `--url`) through the ingress.

`--dry-run` resolves the image and runs the migration check without deploying.

Deploy **once** per release. Every distinct image re-hashes the SPA chunks, and each rebuild is a
fresh cache generation for already-open PWA/iOS-Safari clients; recreating the container from the
*same* image is harmless, but a string of separate builds is not.

`version.json` sets `compatibility.mode: "exact"`, so the server and the mobile apps must report the
same version. Publish a release and leave the server behind and every client that takes the in-app
update is locked out until this step runs. Deploy promptly after a release.

### Plain Docker Compose

`docker compose up -d` on the deploy host also pulls, because the backend service has no `build:`
section and defaults to `ghcr.io/ohmzi/tday:latest`:

```bash
docker compose pull tday-backend && docker compose up -d tday-backend
```

This skips the migration/backup guard and the version verification, so prefer the script.

### Portainer

1. Containers → select **tday_backend**.
2. Click **Recreate** → enable **Re-pull image** → click **Recreate**.

### Post-Update

- Flyway migrations run automatically on container start.
- Existing databases without Flyway history are baselined at version `2`; empty databases replay the full schema snapshot and then incremental migrations.
- Verify the app is healthy by checking `GET /health` returns `{ "status": "ok" }`.
- Confirm `GET /version.json` and `GET /api/mobile/probe` both report the released version (`deploy-release.sh` does this for you).
- Review `docker logs tday_backend` for startup errors.
- **Web clients self-update.** Already-open browsers/PWAs detect the new build and reload into it automatically — no manual cache clearing on each release. See below.

### Rolling back

There is no automated rollback. Redeploy the previous tag:

```bash
./scripts/deploy-release.sh --version 0.7.1
```

That only reverses the application. Any Flyway migration the newer image applied stays applied —
restore from a dump (`scripts/restore-database.sh`) if the schema has to go back too.

## Web Cache Invalidation & Client Updates

### Why this exists

`tday-web` is a Vite PWA served same-origin by the backend from `/app/static`. Every build produces content-hashed chunks and a fresh `index.html`. Without coordination, an already-open client (or a Service Worker / browser / Cloudflare cache) keeps an **old `index.html`** whose chunk hashes no longer exist on the server. When the app then lazy-loads a route chunk it gets a 404 → the SPA fallback returns `index.html` (HTML) → the dynamic import fails and the app crashes (`Failed to fetch dynamically imported module`, or in WebKit `undefined is not an object (evaluating 'o._result.default')`).

This system makes deploys self-healing across every OS/browser. It has four cooperating layers, each guarded against reload loops.

### 1. Per-build cache key

- `tday-web/vite.config.ts` computes a unique `BUILD_ID` once per build (`GIT_SHA` env/arg → else `git rev-parse` → else `dev`, suffixed with a UTC timestamp). It is both:
  - injected into the bundle as the `__BUILD_ID__` define (declared in `src/vite-env.d.ts`), and
  - emitted as `dist/version.json` via an inline `generateBundle` plugin (single source of truth — the same const feeds both, so they can never disagree).
- The backend serves `dist/version.json` at `/version.json` automatically (it rides in the static dir; no backend route needed).

### 2. HTTP cache headers (`tday-backend/.../plugins/Routing.kt`)

The static handler sets `Cache-Control` by path (`cacheControlFor`), so browsers and Cloudflare cache correctly:

| Resource | `Cache-Control` | Rationale |
|----------|-----------------|-----------|
| `index.html`, SPA-fallback HTML, `/version.json` | `no-cache, no-store, must-revalidate` | Always revalidate so a new build is picked up immediately |
| `/assets/**` (content-hashed) | `public, max-age=31536000, immutable` | Filename changes every build, so cache forever safely |
| everything else (icons, manifest, locales) | `public, max-age=3600` | Modest TTL for non-hashed static files |

The HTML rule is keyed so the **SPA fallback** (e.g. a deep link like `/en/app/tday`) is also `no-store`, not just literal `index.html`.

### 3. Service worker (`tday-web/src/sw.ts`)

- **Navigations are NetworkFirst** (`cacheName: tday-navigation`, 4s timeout), falling back to the precached `/index.html` **only when offline**. So online clients always fetch the current `index.html` (and thus current chunk refs); offline still works.
- `/version.json` has an explicit `NetworkOnly` route, and is excluded from precache (the Workbox `globPatterns` deliberately omits `json` — **do not add it**).
- A `message` handler supports `{type:"SKIP_WAITING"}` and `{type:"CLEAR_CACHES"}` for client-driven activation / hard reset.

### 4. Client version poller (`useVersionGate` → `components/app/VersionGate.tsx`, mounted in `App.tsx`)

Fetches `/version.json` (`cache:"no-store"`) on mount, on tab refocus (`visibilitychange`), and every 15 minutes. On a `buildId` mismatch it applies a **hybrid UX**:

- **Silent reload** when the tab had been backgrounded/idle and the user is *not* typing (no editable element focused) — zero interruption.
- Otherwise a non-blocking **"New version available — Reload"** toast (Sonner).

Reload-loop protection lives in `src/lib/chunkError.ts` (`versionReloadAlreadyTried` / `markVersionReloadTried` / `clearVersionReloadFlag`), a separate one-shot guard from the reactive stale-chunk guard. Both clear after 8s of healthy runtime (`main.tsx`), so a *second* deploy within the same session can also self-heal.

### Layer composition (defense in depth)

1. **NetworkFirst nav** (structural) — any reload/navigation while online lands on fresh `index.html`.
2. **`useVersionGate`** (proactive) — detects a deploy before a stale chunk is even requested.
3. **`chunkError.ts` + `vite:preloadError`** (reactive net) — recovers any stale dynamic import that still slips through; thanks to NetworkFirst the recovery reload reliably lands on the current build. See also the Safari/WebKit notes in [Developer notes](#developer-notes--gotchas).

### Verifying after a deploy

```bash
# build key is served and never cached
curl -sI http://127.0.0.1:2525/version.json | grep -i cache-control   # no-store
curl -s  http://127.0.0.1:2525/version.json                            # {"buildId":...}
# HTML shell never cached, hashed assets immutable
curl -sI http://127.0.0.1:2525/            | grep -i cache-control      # no-store
curl -sI http://127.0.0.1:2525/assets/<hashed>.js | grep -i cache-control  # immutable
```

End-to-end (real Safari engine) with Playwright WebKit: load the app fresh and confirm no spurious reload; intercept `/version.json` to return a different `buildId` and confirm the prompt/auto-reload fires. (`webkit` browser is bundled; run `sudo npx playwright install-deps` once for the system libs.)

### Recovering a device stuck on a pre-this-code build

This mechanism only takes effect once a device has loaded **one** build that contains it. A device still pinned to an older cached build needs a one-time manual clear:

- If installed to the home screen (PWA), delete that icon first — it caches separately from the browser.
- iOS Safari: **Settings → Safari → Clear History and Website Data**. Desktop: DevTools → Application → Service Workers → *Unregister*, then *Clear site data*, then hard reload.

### Developer notes / gotchas

- **Don't churn caches needlessly.** Each build re-hashes every chunk (and the Sentry plugin injects a fresh debug id), so rapid back-to-back rebuilds while someone is testing can leave a device's precache half-updated. Batch changes into one build.
- **Keep `version.json` uncacheable.** Don't add `json` to the SW `globPatterns`, and keep the `NetworkOnly` route + the `no-store` header.
- **New static assets:** content-hashed output goes under `/assets/**` (immutable) automatically; anything you drop in `tday-web/public/` is non-hashed and gets the 1h default — bump its handling in `cacheControlFor` if it needs different behavior.
- **Tuning:** poll cadence is `CHECK_INTERVAL_MS` in `useVersionGate.ts`; the toast UX is in `VersionGate.tsx`.
- **Safari/WebKit:** Safari has never shipped `requestIdleCallback` and only added `structuredClone` in 15.4 — guard browser globals with `typeof x === "function"` fallbacks (see `usePrefetchRoutes.ts`, `mergeInstanceAndTodo.ts`). A WebKit-only crash surfaces as the `RouteErrorPage`/`ErrorBoundary` screen; both expose an "Error details" expander, and both route stale-chunk errors through the auto-reload.

### Key files

| Concern | File |
|---------|------|
| Build id + `version.json` emit | `tday-web/vite.config.ts`, `src/vite-env.d.ts` |
| Cache-Control headers | `tday-backend/src/main/kotlin/com/ohmz/tday/plugins/Routing.kt` (`cacheControlFor`) |
| SW strategy + messages | `tday-web/src/sw.ts` |
| Version poller + UX | `tday-web/src/hooks/useVersionGate.ts`, `src/components/app/VersionGate.tsx`, `src/App.tsx` |
| Reload guards + reactive net | `tday-web/src/lib/chunkError.ts`, `src/main.tsx` |
| Build-arg for SHA | `Dockerfile.backend` |
