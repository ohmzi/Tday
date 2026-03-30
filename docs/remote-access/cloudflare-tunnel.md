# Cloudflare Tunnel

Expose T'Day to the internet through Cloudflare's edge network using `cloudflared`. Traffic is encrypted end-to-end with a valid TLS certificate on your custom domain — no port forwarding or public IP required.

This is T'Day's **current production method** (`tday.ohmz.cloud`).

## Overview

| | |
|---|---|
| **How it works** | `cloudflared` daemon on your server creates an outbound-only connection to Cloudflare's edge. Cloudflare terminates TLS and proxies requests to `localhost:2525`. |
| **Complexity** | Low |
| **Public URL** | Yes — custom domain (e.g., `tday.ohmz.cloud`) |
| **HTTPS** | Yes — Cloudflare-managed certificate |
| **Mobile friendly** | Yes — standard HTTPS URL, no client software |
| **Self-hostable** | No — depends on Cloudflare |
| **Cost** | Free (Cloudflare Zero Trust free tier covers one tunnel) |

```
Browser / Mobile App
  └─ HTTPS ─► Cloudflare Edge (TLS termination)
                └─► cloudflared on server ─► localhost:2525 ─► tday_backend :8080
```

## Prerequisites

- A **Cloudflare account** (free tier is sufficient).
- A **domain** added to Cloudflare (Cloudflare must be the authoritative DNS).
- **Docker** running T'Day with the default `TDAY_HOST_BIND=127.0.0.1`.

## Installation

### 1. Install `cloudflared`

```bash
# Debian / Ubuntu
curl -fsSL https://pkg.cloudflare.com/cloudflare-main.gpg \
  | sudo tee /usr/share/keyrings/cloudflare-main.gpg > /dev/null
echo "deb [signed-by=/usr/share/keyrings/cloudflare-main.gpg] https://pkg.cloudflare.com/cloudflared $(lsb_release -cs) main" \
  | sudo tee /etc/apt/sources.list.d/cloudflared.list
sudo apt update && sudo apt install -y cloudflared

# macOS
brew install cloudflared

# Docker (alternative — run cloudflared as a container)
docker pull cloudflare/cloudflared:latest
```

### 2. Authenticate

```bash
cloudflared tunnel login
```

This opens a browser to authorize your Cloudflare account. A certificate is saved to `~/.cloudflared/cert.pem`.

### 3. Create the tunnel

```bash
cloudflared tunnel create tday
```

Note the **Tunnel ID** printed (a UUID like `a1b2c3d4-...`). A credentials file is created at `~/.cloudflared/<TUNNEL_ID>.json`.

### 4. Configure DNS

```bash
cloudflared tunnel route dns tday tday.yourdomain.com
```

This creates a CNAME record pointing `tday.yourdomain.com` to your tunnel.

### 5. Create the config file

```yaml
# ~/.cloudflared/config.yml
tunnel: <TUNNEL_ID>
credentials-file: /home/<user>/.cloudflared/<TUNNEL_ID>.json

ingress:
  - hostname: tday.yourdomain.com
    service: http://localhost:2525
  - service: http_status:404
```

### 6. Test the tunnel

```bash
cloudflared tunnel run tday
```

Visit `https://tday.yourdomain.com` — you should see the T'Day login page.

### 7. Run as a system service

```bash
sudo cloudflared service install
sudo systemctl enable cloudflared
sudo systemctl start cloudflared
```

The tunnel starts automatically on boot.

## T'Day Configuration

No changes to `TDAY_HOST_BIND` are needed — the default `127.0.0.1` is correct since `cloudflared` connects to localhost.

In `.env.docker`:

```bash
TDAY_ENV=production    # enables HSTS and secure cookies
```

## Edge Hardening (Recommended)

Cloudflare's dashboard allows you to add rate-limiting and challenge rules at the edge, before traffic reaches your server. See [Cloudflare Auth Hardening](../security/cloudflare-auth-hardening.md) for the recommended rules:

- Rate-limit `/api/auth/callback/credentials` (12 req / 5 min per IP).
- Rate-limit `/api/auth/register` (6 req / 60 min per IP).
- Rate-limit `/api/auth/csrf` (40 req / 1 min per IP).
- Managed Challenge action on all auth endpoints.

## Cloudflare Turnstile (Adaptive CAPTCHA)

T'Day supports Cloudflare Turnstile for adaptive CAPTCHA on authentication endpoints after repeated failures:

```bash
# .env.docker
AUTH_CAPTCHA_SECRET=<your-turnstile-secret-key>
AUTH_CAPTCHA_SITE_KEY=<your-turnstile-site-key>
AUTH_CAPTCHA_TRIGGER_FAILURES=3
```

See [Cloudflare Turnstile docs](https://developers.cloudflare.com/turnstile/) for creating a site key.

## Mobile App Configuration

Set the server URL in the Android/iOS app to:

```
https://tday.yourdomain.com
```

## Verifying the Setup

```bash
# Check tunnel status
cloudflared tunnel info tday

# Check systemd service
sudo systemctl status cloudflared

# Verify from an external device
curl -s https://tday.yourdomain.com/health
# Expected: {"status":"ok"}
```

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| 502 Bad Gateway | `tday_backend` container not running | `docker compose up -d` |
| `ERR_CONNECTION_REFUSED` locally | `cloudflared` not running | `sudo systemctl start cloudflared` |
| DNS not resolving | CNAME not created | `cloudflared tunnel route dns tday tday.yourdomain.com` |
| Tunnel connects but page is blank | Wrong ingress service port | Verify `service: http://localhost:2525` in `config.yml` |
| Cookies not persisting | `TDAY_ENV` not set to `production` | Set `TDAY_ENV=production` in `.env.docker` |

## Advantages

- No port forwarding, no public IP, no firewall changes.
- Cloudflare handles TLS certificates, DDoS protection, and edge caching.
- Free tier is generous for personal use.
- Custom domain with full DNS control.

## Limitations

- Depends on Cloudflare's infrastructure — not self-hostable.
- Domain must use Cloudflare as authoritative DNS.
- Cloudflare can inspect unencrypted HTTP traffic between its edge and `cloudflared` (though the tunnel itself is encrypted).
- Additional Cloudflare features (WAF rules, Access policies) may require a paid plan.

---

[Back to Remote Access Overview](../REMOTE_ACCESS.md)
