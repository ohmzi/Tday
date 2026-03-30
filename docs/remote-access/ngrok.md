# ngrok

Expose T'Day to the internet with a single command. ngrok creates an outbound tunnel from your server to ngrok's edge, giving you a public HTTPS URL instantly — no DNS, no certificates, no port forwarding.

## Overview

| | |
|---|---|
| **How it works** | The `ngrok` agent on your server opens an outbound connection to ngrok's cloud. ngrok assigns a public URL and proxies incoming HTTPS requests through the tunnel to `localhost:2525`. |
| **Complexity** | Very low |
| **Public URL** | Yes — `https://<random>.ngrok-free.app` (free) or `https://<subdomain>.ngrok.app` (paid) |
| **HTTPS** | Yes — ngrok-managed TLS certificate |
| **Mobile friendly** | Yes — standard HTTPS URL |
| **Self-hostable** | No — depends on ngrok's infrastructure |
| **Cost** | Free tier (ephemeral URLs, limited connections); Personal $8/mo (1 custom domain, stable subdomain); Pro $20/mo (multiple domains, IP restrictions) |

```
Browser / Mobile App
  └─ HTTPS ─► ngrok Edge (TLS termination)
                └─► ngrok agent on server ─► localhost:2525 ─► tday_backend :8080
```

## Prerequisites

- An **ngrok account** (free at [ngrok.com](https://ngrok.com)).
- **Docker** running T'Day with default `TDAY_HOST_BIND=127.0.0.1`.

## Setup

### 1. Install ngrok

```bash
# Debian / Ubuntu
curl -sSL https://ngrok-agent.s3.amazonaws.com/ngrok.asc \
  | sudo tee /etc/apt/trusted.gpg.d/ngrok.asc > /dev/null
echo "deb https://ngrok-agent.s3.amazonaws.com buster main" \
  | sudo tee /etc/apt/sources.list.d/ngrok.list
sudo apt update && sudo apt install -y ngrok

# macOS
brew install ngrok

# Snap
sudo snap install ngrok

# Or download directly: https://ngrok.com/download
```

### 2. Authenticate

```bash
ngrok config add-authtoken <YOUR_AUTH_TOKEN>
```

Find your auth token at [dashboard.ngrok.com/get-started/your-authtoken](https://dashboard.ngrok.com/get-started/your-authtoken).

### 3. Start the tunnel

```bash
ngrok http 2525
```

ngrok prints the public URL:

```
Forwarding    https://a1b2c3d4.ngrok-free.app → http://localhost:2525
```

Open the HTTPS URL in any browser or enter it in the T'Day mobile app.

## Stable Subdomain (Paid)

Free-tier URLs change every time ngrok restarts. On a paid plan, you can reserve a subdomain:

```bash
ngrok http --domain=tday.ngrok.app 2525
```

Or use a custom domain (Pro plan):

```bash
# After adding the domain in the ngrok dashboard and configuring DNS
ngrok http --domain=tday.yourdomain.com 2525
```

## Running as a Service

### Using ngrok's built-in service mode

```bash
# Create a config file
ngrok config edit
```

Add to `~/.config/ngrok/ngrok.yml`:

```yaml
version: "3"
agent:
  authtoken: <YOUR_AUTH_TOKEN>
tunnels:
  tday:
    proto: http
    addr: 2525
    # domain: tday.ngrok.app    # uncomment for stable subdomain (paid)
```

### systemd service

```ini
# /etc/systemd/system/ngrok.service
[Unit]
Description=ngrok tunnel for T'Day
After=network-online.target
Wants=network-online.target

[Service]
User=<your-user>
ExecStart=/usr/local/bin/ngrok start tday --config /home/<your-user>/.config/ngrok/ngrok.yml
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl enable --now ngrok
```

## T'Day Configuration

ngrok connects to `localhost:2525` — no change to `TDAY_HOST_BIND` is needed.

```bash
# .env.docker
TDAY_ENV=production    # ngrok provides HTTPS, enable secure cookies
```

If the mobile apps connect via a different URL than the web SPA:

```bash
# .env.docker
CORS_ALLOWED_ORIGINS=https://a1b2c3d4.ngrok-free.app
```

## Mobile App Configuration

Set the server URL to the ngrok HTTPS URL:

```
https://a1b2c3d4.ngrok-free.app
```

On the free tier, the URL changes on each restart — you'll need to update the mobile app's server URL each time. A paid plan with a stable subdomain avoids this.

## Free Tier Limitations

- **Ephemeral URLs**: Change on every ngrok restart.
- **Interstitial page**: Free-tier URLs show an ngrok branding page on first visit. Automated clients (mobile apps) can add an `ngrok-skip-browser-warning` header to bypass it.
- **Rate limits**: Limited concurrent connections and requests per minute.
- **No custom domain**: URLs use `*.ngrok-free.app`.

To add the skip-warning header in the T'Day mobile apps, the apps would need a custom header — this is not currently built in. For regular use, a paid plan is recommended.

## Verifying the Setup

```bash
# Check ngrok status
curl http://localhost:4040/api/tunnels
# Returns JSON with active tunnel URLs

# Test from an external device
curl https://a1b2c3d4.ngrok-free.app/health
# Expected: {"status":"ok"}
```

ngrok also provides a local dashboard at `http://localhost:4040` showing request logs and tunnel status.

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| 502 Bad Gateway | T'Day container not running | `docker compose up -d` |
| URL changed after restart | Free tier — ephemeral URLs | Use a paid plan or reserve a domain |
| ngrok interstitial page | Free tier branding | Click through, or upgrade to a paid plan |
| `ERR_NGROK_108` | Auth token not set | `ngrok config add-authtoken <TOKEN>` |
| Mobile app can't connect | URL changed or interstitial blocking | Update URL in app; consider paid plan |
| Slow response times | ngrok relay latency | Expected for free tier; choose a region closer to you: `ngrok http --region=eu 2525` |

## Advantages

- Fastest setup — one command gets a public HTTPS URL.
- No port forwarding, no DNS, no certificates to manage.
- Built-in request inspection dashboard at `localhost:4040`.
- Good for demos, temporary access, or quick testing.

## Limitations

- Free-tier URLs are ephemeral — bad for persistent mobile app configuration.
- Free-tier interstitial page can interfere with API clients.
- Depends entirely on ngrok's infrastructure.
- Paid plans needed for stable URLs and custom domains.
- Traffic routes through ngrok's servers (latency overhead).
- No self-hosting option.

---

[Back to Remote Access Overview](../REMOTE_ACCESS.md)
