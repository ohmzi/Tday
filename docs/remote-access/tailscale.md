# Tailscale

Access T'Day over a private WireGuard mesh VPN managed by Tailscale. Install on the server and each client device — they join a shared "tailnet" with stable private IPs. No port forwarding, no DNS configuration, and no certificates to manage.

## Overview

| | |
|---|---|
| **How it works** | Each device runs the Tailscale agent, which establishes direct WireGuard tunnels to other devices. Traffic is peer-to-peer when possible, relayed through Tailscale's DERP servers when NAT traversal fails. |
| **Complexity** | Very low |
| **Public URL** | Optional — [Tailscale Funnel](https://tailscale.com/kb/1223/funnel/) exposes a `https://<machine>.<tailnet>.ts.net` URL |
| **HTTPS** | WireGuard encryption on the tunnel; Funnel adds browser-trusted TLS |
| **Mobile friendly** | Yes — native Tailscale apps for Android and iOS |
| **Self-hostable** | Partial — [Headscale](https://github.com/juanfont/headscale) replaces Tailscale's coordination server; traffic remains peer-to-peer WireGuard |
| **Cost** | Free for personal use (up to 100 devices, 3 users) |

```
Phone / Laptop (Tailscale installed)
  └─ WireGuard ─► Tailscale mesh ─► Server (Tailscale IP 100.x.y.z)
                                       └─► localhost:2525 ─► tday_backend :8080
```

## Prerequisites

- A **Tailscale account** (sign up at [tailscale.com](https://tailscale.com) — free for personal use).
- **Docker** running T'Day on the server.
- Tailscale-compatible OS on every device that needs access.

## Server Setup

### 1. Install Tailscale

```bash
# Debian / Ubuntu
curl -fsSL https://tailscale.com/install.sh | sh

# macOS
brew install tailscale

# Other distros: https://tailscale.com/download
```

### 2. Start and authenticate

```bash
sudo tailscale up
```

A URL is printed — open it in a browser to authorize the server in your tailnet.

### 3. Note the Tailscale IP

```bash
tailscale ip -4
# Example output: 100.64.0.1
```

This is the stable private IP other devices on your tailnet will use to reach the server.

### 4. Configure T'Day to listen on the Tailscale interface

**Option A — Bind to the Tailscale IP only** (recommended):

```bash
# .env (project root)
TDAY_HOST_BIND=100.64.0.1   # replace with your actual Tailscale IP
TDAY_HOST_PORT=2525
```

**Option B — Bind to all interfaces** (simpler):

```bash
# .env (project root)
TDAY_HOST_BIND=0.0.0.0
TDAY_HOST_PORT=2525
```

With Option B, add a firewall rule so only the Tailscale subnet can reach port 2525:

```bash
# UFW example
sudo ufw allow in on tailscale0 to any port 2525
sudo ufw deny in on eth0 to any port 2525
```

### 5. Restart T'Day

```bash
docker compose down && docker compose up -d
```

## Client Setup

### Android / iOS (T'Day mobile apps)

1. Install **Tailscale** from the Play Store or App Store.
2. Sign in with the same Tailscale account.
3. Enable the VPN — your phone joins the tailnet.
4. Open the T'Day app and set the server URL to `http://100.64.0.1:2525` (your server's Tailscale IP).

### Laptop / Desktop (browser)

1. Install Tailscale on the machine.
2. Sign in and connect.
3. Open `http://100.64.0.1:2525` in any browser.

## Optional: Tailscale Funnel (Public HTTPS URL)

Funnel exposes your T'Day instance to the public internet through Tailscale's infrastructure, with a valid TLS certificate. Useful if you want browser access without installing Tailscale on every client.

### Enable Funnel

```bash
# On the server
tailscale funnel 2525
```

Tailscale prints the public URL:

```
https://<machine-name>.<tailnet-name>.ts.net/
```

### Make Funnel persistent

```bash
tailscale funnel --bg 2525
```

The `--bg` flag keeps Funnel running after the terminal closes.

### Funnel limitations

- URL format is `https://<machine>.<tailnet>.ts.net` — no custom domains.
- HTTPS only (port 443), which is good for secure cookies.
- Traffic routes through Tailscale's infrastructure, not peer-to-peer.
- Funnel must be explicitly enabled in the Tailscale admin console under **DNS → Funnel**.

## Optional: Headscale (Self-Hosted Coordination)

If you want to eliminate dependency on Tailscale's cloud coordination server:

1. Deploy [Headscale](https://github.com/juanfont/headscale) on a small VPS.
2. Point all Tailscale clients to your Headscale instance with `--login-server`.
3. Traffic still flows peer-to-peer over WireGuard — Headscale only handles key exchange and coordination.

This is a more advanced setup. See the [Headscale documentation](https://headscale.net/) for details.

## T'Day Configuration

```bash
# .env.docker
# Tailscale IPs use plain HTTP, so don't set TDAY_ENV=production
# (production mode forces Secure cookies which require HTTPS)
TDAY_ENV=development
```

If using Tailscale Funnel (HTTPS):

```bash
# .env.docker
TDAY_ENV=production          # HTTPS is available, enable secure cookies
```

If mobile apps and browser access the same instance via different URLs:

```bash
# .env.docker
CORS_ALLOWED_ORIGINS=http://100.64.0.1:2525
```

## Verifying the Setup

```bash
# On the server — confirm Tailscale is running
tailscale status

# From another device on the tailnet
curl http://100.64.0.1:2525/health
# Expected: {"status":"ok"}

# If using Funnel
curl https://<machine>.<tailnet>.ts.net/health
# Expected: {"status":"ok"}
```

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `Connection refused` from other device | T'Day not bound to Tailscale IP | Set `TDAY_HOST_BIND=100.x.y.z` or `0.0.0.0` and restart |
| Tailscale connected but no route | Firewall blocking port 2525 | `sudo ufw allow in on tailscale0 to any port 2525` |
| Funnel URL returns 502 | Wrong port in `tailscale funnel` | Verify `tailscale funnel 2525` (host port, not container port) |
| Cookies not persisting (Funnel) | `TDAY_ENV` not set | Set `TDAY_ENV=production` for HTTPS via Funnel |
| Cookies not persisting (direct IP) | `TDAY_ENV=production` with HTTP | Set `TDAY_ENV=development` for plain HTTP access |
| Mobile app can't connect | Tailscale VPN not active on phone | Open Tailscale app and toggle the connection on |

## Advantages

- Simplest setup of any method — one command per device.
- Peer-to-peer WireGuard: fast, low-latency, encrypted.
- Native mobile apps make it work well with T'Day's Android/iOS clients.
- MagicDNS gives you human-friendly names (e.g., `http://server:2525`).
- Free for personal use with generous limits.

## Limitations

- Every client device must install the Tailscale app and authenticate.
- Coordination depends on Tailscale's cloud (unless you self-host with Headscale).
- No custom domain (Funnel uses `*.ts.net`).
- Funnel is public — anyone with the URL can reach your T'Day login page (application-layer auth still applies).

---

[Back to Remote Access Overview](../REMOTE_ACCESS.md)
