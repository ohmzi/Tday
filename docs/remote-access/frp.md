# frp (Fast Reverse Proxy)

Self-hosted reverse proxy that exposes local services through a public VPS. Run `frps` (server) on a VPS with a public IP and `frpc` (client) on the T'Day host — traffic flows through the VPS to your local T'Day instance. Ideal when you run multiple self-hosted services behind one relay.

## Overview

| | |
|---|---|
| **How it works** | `frpc` on the T'Day server creates an outbound connection to `frps` on a public VPS. `frps` listens for incoming HTTP/HTTPS traffic and forwards it through the tunnel to `frpc`, which delivers it to `localhost:2525`. |
| **Complexity** | Medium–High |
| **Public URL** | Yes — your VPS IP or a custom domain pointed at the VPS |
| **HTTPS** | Yes — with Caddy/nginx on the VPS, or frps's built-in TLS (requires manual cert management) |
| **Mobile friendly** | Yes — standard HTTPS URL |
| **Self-hostable** | Yes — fully self-hosted |
| **Cost** | VPS cost (~$4-6/month) |

```
Browser / Mobile App
  └─ HTTPS ─► VPS (frps + Caddy for TLS)
                └─► frp tunnel ─► frpc on T'Day server
                                    └─► localhost:2525 ─► tday_backend :8080
```

## Prerequisites

- A **VPS with a public IP** (any provider — 1 vCPU / 512 MB is sufficient).
- A **domain** with DNS pointed at the VPS (A record).
- **Docker** running T'Day on the home server.

## VPS Setup (frps — server)

### 1. Download frp

```bash
# Check latest release at https://github.com/fatedier/frp/releases
FRP_VERSION="0.61.1"
wget https://github.com/fatedier/frp/releases/download/v${FRP_VERSION}/frp_${FRP_VERSION}_linux_amd64.tar.gz
tar -xzf frp_${FRP_VERSION}_linux_amd64.tar.gz
sudo mv frp_${FRP_VERSION}_linux_amd64/frps /usr/local/bin/
```

### 2. Create the server config

```toml
# /etc/frp/frps.toml
bindPort = 7000

auth.method = "token"
auth.token = "CHANGE_ME_TO_A_SECURE_RANDOM_STRING"

webServer.addr = "127.0.0.1"
webServer.port = 7500
```

- `bindPort` — port where `frpc` connects (outbound from home server).
- `auth.token` — shared secret between server and client. Generate with `openssl rand -base64 32`.
- `webServer` — optional admin dashboard (bind to localhost, access via SSH tunnel).

### 3. Open the frp port

```bash
sudo ufw allow 7000/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
```

### 4. Run as a systemd service

```ini
# /etc/systemd/system/frps.service
[Unit]
Description=frp server
After=network-online.target
Wants=network-online.target

[Service]
ExecStart=/usr/local/bin/frps -c /etc/frp/frps.toml
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl enable --now frps
```

## T'Day Server Setup (frpc — client)

### 1. Download frp

```bash
FRP_VERSION="0.61.1"
wget https://github.com/fatedier/frp/releases/download/v${FRP_VERSION}/frp_${FRP_VERSION}_linux_amd64.tar.gz
tar -xzf frp_${FRP_VERSION}_linux_amd64.tar.gz
sudo mv frp_${FRP_VERSION}_linux_amd64/frpc /usr/local/bin/
```

### 2. Create the client config

```toml
# /etc/frp/frpc.toml
serverAddr = "<VPS_PUBLIC_IP>"
serverPort = 7000

auth.method = "token"
auth.token = "CHANGE_ME_TO_A_SECURE_RANDOM_STRING"

[[proxies]]
name = "tday-web"
type = "http"
localPort = 2525
customDomains = ["tday.yourdomain.com"]
```

- `serverAddr` — your VPS public IP.
- `auth.token` — must match the server.
- `customDomains` — the domain that will resolve to the VPS.

### 3. Run as a systemd service

```ini
# /etc/systemd/system/frpc.service
[Unit]
Description=frp client
After=network-online.target
Wants=network-online.target

[Service]
ExecStart=/usr/local/bin/frpc -c /etc/frp/frpc.toml
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl enable --now frpc
```

## Adding HTTPS with Caddy

frp handles the tunnel; use Caddy on the VPS for automatic TLS.

### 1. Install Caddy on the VPS

```bash
sudo apt install -y debian-keyring debian-archive-keyring apt-transport-https
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' \
  | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' \
  | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt update && sudo apt install -y caddy
```

### 2. Configure frps to use a different HTTP port

Since Caddy needs port 80/443:

```toml
# /etc/frp/frps.toml (updated)
bindPort = 7000
vhostHTTPPort = 8080

auth.method = "token"
auth.token = "CHANGE_ME_TO_A_SECURE_RANDOM_STRING"
```

Restart frps: `sudo systemctl restart frps`.

### 3. Configure Caddy

```
# /etc/caddy/Caddyfile
tday.yourdomain.com {
    reverse_proxy localhost:8080
}
```

```bash
sudo systemctl restart caddy
```

Caddy automatically obtains a Let's Encrypt certificate. Clients access `https://tday.yourdomain.com`.

### 4. DNS

Point `tday.yourdomain.com` to the VPS public IP (A record).

## T'Day Configuration

No changes to `TDAY_HOST_BIND` — frpc connects to `localhost:2525` on the T'Day server.

```bash
# .env.docker — with Caddy HTTPS
TDAY_ENV=production

# .env.docker — without Caddy (plain HTTP)
TDAY_ENV=development
```

## Mobile App Configuration

Set the server URL to:

```
https://tday.yourdomain.com
```

## Multiple Services

frp excels at exposing multiple local services through one VPS. Add more proxies to `frpc.toml`:

```toml
[[proxies]]
name = "tday-web"
type = "http"
localPort = 2525
customDomains = ["tday.yourdomain.com"]

[[proxies]]
name = "other-service"
type = "http"
localPort = 3000
customDomains = ["other.yourdomain.com"]
```

Add corresponding entries in the Caddyfile:

```
other.yourdomain.com {
    reverse_proxy localhost:8080
}
```

## Verifying the Setup

```bash
# On VPS — check frps is running
sudo systemctl status frps

# On T'Day server — check frpc is connected
sudo systemctl status frpc

# From an external device
curl https://tday.yourdomain.com/health
# Expected: {"status":"ok"}

# frps admin dashboard (via SSH tunnel to VPS)
ssh -L 7500:localhost:7500 user@vps
# Then open http://localhost:7500 in browser
```

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| frpc can't connect to frps | Port 7000 not open on VPS | `sudo ufw allow 7000/tcp` |
| `authentication failed` | Token mismatch | Ensure `auth.token` matches in both `frps.toml` and `frpc.toml` |
| Domain returns 404 | `customDomains` doesn't match request `Host` header | Verify domain in `frpc.toml` matches the DNS record |
| 502 from Caddy | frps not on expected port | Verify `vhostHTTPPort` in `frps.toml` matches Caddy's `reverse_proxy` |
| T'Day not responding | Container not running | `docker compose up -d` on T'Day server |
| High latency | All traffic routes through VPS | Expected — traffic must traverse VPS even for nearby clients |

## Advantages

- Fully self-hosted — no third-party service dependency.
- Supports multiple local services through one VPS and domain.
- Mature project with active development and good documentation.
- Lightweight — minimal resource usage on both server and client.
- Flexible: supports HTTP, HTTPS, TCP, UDP, and STCP proxy types.

## Limitations

- Requires a VPS with a public IP (recurring cost).
- More setup than Cloudflare Tunnel or ngrok.
- TLS requires an additional reverse proxy (Caddy/nginx) or manual cert management.
- Token-based auth only (no mutual TLS out of the box without additional config).
- All traffic routes through the VPS — adds latency compared to peer-to-peer solutions.
- Version management: frps and frpc versions must be compatible.

---

[Back to Remote Access Overview](../REMOTE_ACCESS.md)
