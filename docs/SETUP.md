# Setup

How to run your own T'Day server and connect the apps to it.

You only need a server for **Server Mode**: syncing across devices, the web app, shared lists, and
integrations. The Android and iOS apps also run in **Local Mode**, which needs no server and no
account. Pick it on first launch and skip this guide.

- [What you need](#what-you-need)
- [1. Get the files](#1-get-the-files)
- [2. Configure](#2-configure)
- [3. Start the stack](#3-start-the-stack)
- [4. Create your account](#4-create-your-account)
- [5. Connect your apps](#5-connect-your-apps)
- [Reaching it from other devices](#reaching-it-from-other-devices)
- [Optional: AI summaries with Ollama](#optional-ai-summaries-with-ollama)
- [Optional: web push notifications](#optional-web-push-notifications)
- [Optional: crash reporting](#optional-crash-reporting)
- [Updating](#updating)
- [Backups](#backups)
- [Building from source](#building-from-source)
- [Troubleshooting](#troubleshooting)

---

## What you need

- A machine that runs Docker with the Compose v2 plugin: a home server, NAS, VPS, or your own
  computer.
- `git` and `openssl`, to fetch the files and generate secrets.
- Optional: a tunnel, VPN, or reverse proxy so your phone can reach the server from outside your
  network. [Reaching it from other devices](#reaching-it-from-other-devices) covers this.

The stack is three containers:

| Service                      | Container      | Port                                                 |
|------------------------------|----------------|------------------------------------------------------|
| Ktor backend + web app       | `tday_backend` | `localhost:2525 → 8080` (bound to localhost by default) |
| PostgreSQL 15                | `tday_db`      | 5432 (internal only)                                 |
| Ollama (optional `ai` profile) | `tday_ollama`  | 11434 (internal only)                                |

## 1. Get the files

```bash
git clone https://github.com/ohmzi/Tday.git
cd Tday
```

`docker-compose.yaml` pulls the released image (`ghcr.io/ohmzi/tday:latest`), the same image CI
publishes for every [release](https://github.com/ohmzi/Tday/releases). Nothing gets built on your
machine unless you [ask for it](#building-from-source).

## 2. Configure

T'Day reads configuration from two files:

| File          | Read by                       | Holds                                                   |
|---------------|-------------------------------|---------------------------------------------------------|
| `.env.docker` | The backend container         | App secrets and settings                                |
| `.env`        | Docker Compose, at the root   | Host port binding, database credentials, timezone, AI model |

Both are git-ignored. Start from the template:

```bash
cp .env.example .env.docker
openssl rand -base64 32   # paste the output into AUTH_SECRET
```

Then edit `.env.docker`:

| Variable                    | Required | What to set                                                                                   |
|-----------------------------|----------|-----------------------------------------------------------------------------------------------|
| `AUTH_SECRET`               | Yes      | A random secret from `openssl rand -base64 32`. It encrypts session tokens.                   |
| `DATABASE_URL`              | Yes      | The template value matches the bundled database. Change it only if you change the credentials below. |
| `TDAY_ENV`                  | Recommended | `production` once the app is served over HTTPS. This turns on secure cookies and HSTS.     |
| `DATA_ENCRYPTION_KEY`       | Optional | A 32-byte key (`openssl rand -base64 32`) for field-level encryption at rest (AES-256-GCM).   |
| `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY` | Optional | Needed for [web push notifications](#optional-web-push-notifications).           |
| `SENTRY_DSN`                | Optional | Your own Sentry project, for your server's error reports. Blank means the server never reports. See [crash reporting](#optional-crash-reporting). |

Then create a root `.env` and change the database password from its default:

```bash
# .env (project root, next to docker-compose.yaml)
POSTGRES_USER=tday
POSTGRES_PASSWORD=<a long random password>
POSTGRES_DB=tday
```

If you do this, update `DATABASE_URL` in `.env.docker` to match:
`postgresql://tday:<password>@database:5432/tday`.

Other root `.env` settings you might want:

| Variable         | Default     | Purpose                                                                  |
|------------------|-------------|--------------------------------------------------------------------------|
| `TDAY_HOST_BIND` | `127.0.0.1` | Host interface for the web port. See [Reaching it from other devices](#reaching-it-from-other-devices). |
| `TDAY_HOST_PORT` | `2525`      | Host port mapped to the backend.                                         |
| `TZ`             | `UTC`       | Server **log** timestamps only. Task times always follow each user's device timezone. |
| `OLLAMA_MODEL`   | `qwen3.5:0.8b` | The model used for [AI summaries](#optional-ai-summaries-with-ollama). |

Every variable, including rate limits, session lifetimes, and `_FILE` secret mounts, is documented
in [`.env.example`](../.env.example) and in
[DEPLOYMENT.md → Configuration](DEPLOYMENT.md#configuration).

## 3. Start the stack

```bash
docker compose up -d
docker compose ps                          # all services should become "healthy"
curl -fsS http://127.0.0.1:2525/health     # the backend answers once it is ready
```

The backend applies its database migrations automatically on startup. Open
<http://localhost:2525> on the host machine to reach the web app.

## 4. Create your account

Register from the web app. **The first account on a new server becomes the admin and is approved
automatically.**

Anyone who registers after that waits in a *pending* state until the admin approves them from the
web app's **Admin** page. This keeps a server that is reachable from the internet from filling up
with strangers.

## 5. Connect your apps

| App     | How to get it                                                                                        | Then                                                            |
|---------|------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------|
| Web     | Open your server's URL in a browser. It is an installable PWA.                                      | Sign in.                                                        |
| Android | Download the signed APK from the [latest release](https://github.com/ohmzi/Tday/releases/latest) (Android 8.0+). Later releases can be installed from inside the app. | Choose **Self-hosted**, enter your server URL, and sign in. |
| iOS     | Join the open beta on [TestFlight](https://testflight.apple.com/join/paKSyGAG) (iOS 17+).            | Choose **Self-hosted**, enter your server URL, and sign in. |

> [!IMPORTANT]
> Server Mode requires the app and the server to run the **same release**. When they differ, the
> app blocks sync and tells you which side to update. Update the server and the apps together.

Started in Local Mode and want to move to a server? Export from **Settings** on the device, sign in
to the server, then import the file there. Nothing moves until you do it yourself.

---

## Reaching it from other devices

By default the web port is bound to `127.0.0.1`, so only the host itself can reach it. This is
deliberate: the backend speaks plain HTTP, and your password and session cookie should not travel
over a network unencrypted.

To use T'Day from your phone, put something in front that adds encryption:

| Option                                        | Good for                                                   |
|-----------------------------------------------|------------------------------------------------------------|
| Cloudflare Tunnel, ngrok, Caddy reverse proxy | A public HTTPS URL without opening router ports            |
| Tailscale, WireGuard, ZeroTier                | Private access from your own devices only                  |
| SSH tunnel, frp                               | Quick or advanced setups                                   |

Each option has a step-by-step guide in [REMOTE_ACCESS.md](REMOTE_ACCESS.md). Once the app is
served over HTTPS, set `TDAY_ENV=production` in `.env.docker`.

If your proxy runs on another machine, make sure it forwards the real client IP
(`X-Forwarded-For`, `X-Real-IP`, or `cf-connecting-ip`). Otherwise every request looks like it
comes from the proxy, and all users share one rate-limit bucket.

On a trusted home network only, you can skip the proxy and set `TDAY_HOST_BIND=0.0.0.0` in the root
`.env`. The app is then reachable over plain HTTP from any device on that network.

## Optional: AI summaries with Ollama

T'Day can write a short summary of your day. With no AI configured it uses a built-in,
deterministic summary, so this step is optional. Either way, nothing leaves your server.

To use a local model:

1. In `.env.docker`, set `OLLAMA_URL=http://ollama:11434`.
2. Start the stack with the `ai` profile:

   ```bash
   docker compose --profile ai pull ollama ollama-model-setup
   docker compose --profile ai up -d
   ```

The profile starts `tday_ollama` and a one-shot setup container that pulls the model and removes
the old `qwen2.5:0.5b` default if it's there. The default model is `qwen3.5:0.8b`, which needs a
recent Ollama runtime, so keep pulling the Ollama images when you update.

To use a different model, set `OLLAMA_MODEL` in the **root `.env`**. Compose passes that value to
both the setup container and the backend, and it overrides any `OLLAMA_MODEL` in `.env.docker`.

Ollama runs on the CPU by default. For an NVIDIA GPU, install the
[NVIDIA Container Toolkit](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/) on
the host and add the GPU override to every Compose command:

```bash
docker compose -f docker-compose.yaml -f docker-compose.gpu.yaml --profile ai up -d
```

## Optional: web push notifications

The web app can notify you about due tasks even when the tab is closed. This needs a VAPID key
pair:

```bash
npx web-push generate-vapid-keys --json
```

Put the two values in `VAPID_PUBLIC_KEY` and `VAPID_PRIVATE_KEY` in `.env.docker`, then recreate the
backend with `docker compose up -d`. The Android and iOS apps schedule reminders on the device and
don't need this.

## Optional: crash reporting

Crash reporting is optional, and off unless someone turns it on.

**Your server's own error reports.** Leave `SENTRY_DSN` blank in `.env.docker` (the default) and your
server never reports anything. To get error reports from your server:

1. Create a project in your own [Sentry](https://sentry.io) account and put its DSN in `SENTRY_DSN`
   in `.env.docker`, then recreate the backend with `docker compose up -d`.
2. Sign in to the web app as an admin and switch on **Settings → Privacy → Server error reports**.
   The switch is off by default, and it does not appear until `SENTRY_DSN` is set. Until an admin
   turns it on, a server with a DSN still sends nothing.

`SENTRY_TRACES_SAMPLE_RATE` (optional) sets how many requests are traced; it defaults to `0.1` in
production. Once the admin switch is on, the server sends errors and that sample of requests (as
`http.server` transactions, with route template, method, status and duration). Server reports hold error types, stack traces, and route templates, never task or list
content. See [TELEMETRY.md](TELEMETRY.md) for exactly what is and is not sent.

**The apps and the web app.** The published web image and the release apps carry the maintainer's own
client DSNs. Each person is asked once, after setup, whether to share crash reports, and nothing is
sent unless they say yes; they can change their mind in **Settings → Privacy**. Those reports go
straight from the device or browser to the maintainer's Sentry, not through your server, and the report
text never includes your server's address. One exception: a browser sends an `Origin` header with the
web app's report, so the maintainer's Sentry can see which site the web report came from; the web
app's FAQ says so. An app or image you build yourself has no DSN unless you give it one,
so it never asks.

---

## Updating

```bash
docker compose pull
docker compose up -d
```

> [!WARNING]
> Database migrations can't be rolled back. [Take a backup](#backups) before updating to a release
> that changes the database.

If you run from a clone, `git pull && ./scripts/deploy-release.sh` is the safer option. It pins the
exact release named in `version.json`, refuses to apply new migrations unless you pass `--backup`
(to take one now) or `--skip-backup`, and checks that the running server reports the new version.
See [DEPLOYMENT.md → Updating in Production](DEPLOYMENT.md#updating-in-production).

Remember to update the mobile apps as well. Server Mode needs matching versions.

## Backups

Everything the server knows lives in the `postgres_data` Docker volume. Never run
`docker compose down -v` unless you want to delete it.

```bash
./scripts/backup-database.sh      # verified pg_dump into ./backups, optionally encrypted
./scripts/restore-database.sh     # restore one of those files (asks for confirmation)
```

Backups don't run on a schedule until you set one up. [security/backups.md](security/backups.md)
covers scheduling, encryption, and restore drills.

Each user can also export their own data as a portable JSON file from **Settings**.

## Building from source

To build the backend image from your checkout instead of pulling a release:

```bash
docker compose -f docker-compose.yaml -f docker-compose.build.yaml up -d --build
```

The image build needs BuildKit, which is the default builder from Docker 23 and in the Compose v2 plugin
this guide already requires. With the legacy builder (`DOCKER_BUILDKIT=0`, or an engine older than 23) it
stops at the web build step, because that step mounts an optional build secret for the Sentry source-map
upload. You do not need to set any Sentry value to build.

To build the mobile apps yourself:

- **Android:** open `android-compose/` in Android Studio (SDK 35) and run on a device or emulator.
  See [android-compose/README.md](../android-compose/README.md).
- **iOS:** open `ios-swiftUI/TdayApp.xcodeproj` in Xcode on macOS and run the `Tday` scheme. See
  [ios-swiftUI/README.md](../ios-swiftUI/README.md).

For a development setup with hot reload, see
[CONTRIBUTING.md → Development Setup](../CONTRIBUTING.md#development-setup).

## Troubleshooting

| Symptom                                               | Likely cause and fix                                                                                              |
|-------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------|
| The web UI works on the host but not from other devices | The port is bound to `127.0.0.1`. Set up [remote access](#reaching-it-from-other-devices).                      |
| The backend keeps restarting                          | Check `docker compose logs tday-backend`. A missing `AUTH_SECRET` or `DATABASE_URL` stops startup.                |
| Can't log in after changing `POSTGRES_PASSWORD`       | `DATABASE_URL` in `.env.docker` must use the same user, password, and database name.                               |
| You keep getting logged out behind HTTPS              | Set `TDAY_ENV=production` so session cookies are marked `Secure`.                                                 |
| The mobile app says to update the app or the server   | Server Mode needs the app and server on the same release. Update whichever side is older.                         |
| A new release doesn't show up after pulling           | NAS UIs have their own update flow, for example Unraid **Force update** or Portainer **Re-pull image**.           |

NAS-specific issues (TrueNAS, Unraid, Synology, Proxmox) are covered in
[DEPLOYMENT.md → Self-hosting on a NAS](DEPLOYMENT.md#self-hosting-on-a-nas-truenas--unraid--synology--proxmox).
