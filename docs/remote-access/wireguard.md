# WireGuard

Self-hosted VPN using a relay VPS. You run WireGuard on both a small public VPS and the T'Day server — devices connect to the VPS and route traffic through the VPN to reach T'Day. Full control, no third-party dependencies.

## Overview

| | |
|---|---|
| **How it works** | WireGuard creates encrypted point-to-point tunnels between peers. A VPS with a public IP acts as a relay. The T'Day server and client devices connect outbound to the VPS, forming a private network. |
| **Complexity** | Medium |
| **Public URL** | No — clients use a private WireGuard IP |
| **HTTPS** | Traffic is encrypted by the WireGuard tunnel; no browser-trusted TLS certificate by default |
| **Mobile friendly** | Yes — official WireGuard apps for Android and iOS |
| **Self-hostable** | Yes — fully self-hosted |
| **Cost** | VPS cost (~$4-6/month for a small instance) |

```
Phone / Laptop (WireGuard client)
  └─ WireGuard tunnel ─► VPS (relay, public IP)
                            └─► WireGuard tunnel ─► T'Day server (home network)
                                                      └─► localhost:2525 ─► tday_backend :8080
```

## Prerequisites

- A **VPS with a public IP** (any provider: Hetzner, DigitalOcean, Vultr, Linode — a 1 vCPU / 512 MB instance is sufficient).
- Root or sudo access on the VPS, the T'Day server, and client devices.
- T'Day running via Docker Compose.

## Network Plan

Assign private IPs within a WireGuard subnet (e.g., `10.0.0.0/24`):

| Peer | WireGuard IP | Role |
|------|-------------|------|
| VPS | `10.0.0.1` | Relay / hub |
| T'Day server | `10.0.0.2` | Hosts T'Day |
| Your phone | `10.0.0.3` | Client |
| Your laptop | `10.0.0.4` | Client |

## VPS Setup (Relay)

### 1. Install WireGuard

```bash
sudo apt update && sudo apt install -y wireguard
```

### 2. Generate keys

```bash
wg genkey | tee /etc/wireguard/private.key | wg pubkey > /etc/wireguard/public.key
chmod 600 /etc/wireguard/private.key
```

### 3. Create the config

```ini
# /etc/wireguard/wg0.conf
[Interface]
PrivateKey = <VPS_PRIVATE_KEY>
Address = 10.0.0.1/24
ListenPort = 51820

# T'Day server
[Peer]
PublicKey = <TDAY_SERVER_PUBLIC_KEY>
AllowedIPs = 10.0.0.2/32

# Phone
[Peer]
PublicKey = <PHONE_PUBLIC_KEY>
AllowedIPs = 10.0.0.3/32

# Laptop
[Peer]
PublicKey = <LAPTOP_PUBLIC_KEY>
AllowedIPs = 10.0.0.4/32
```

### 4. Enable IP forwarding and start

```bash
echo "net.ipv4.ip_forward=1" | sudo tee -a /etc/sysctl.conf
sudo sysctl -p

sudo systemctl enable --now wg-quick@wg0
```

### 5. Open the WireGuard port

```bash
sudo ufw allow 51820/udp
```

## T'Day Server Setup

### 1. Install WireGuard

```bash
sudo apt update && sudo apt install -y wireguard
```

### 2. Generate keys

```bash
wg genkey | tee /etc/wireguard/private.key | wg pubkey > /etc/wireguard/public.key
chmod 600 /etc/wireguard/private.key
```

### 3. Create the config

```ini
# /etc/wireguard/wg0.conf
[Interface]
PrivateKey = <TDAY_SERVER_PRIVATE_KEY>
Address = 10.0.0.2/24

[Peer]
PublicKey = <VPS_PUBLIC_KEY>
Endpoint = <VPS_PUBLIC_IP>:51820
AllowedIPs = 10.0.0.0/24
PersistentKeepalive = 25
```

`PersistentKeepalive = 25` keeps the outbound connection alive through NAT.

### 4. Start and enable

```bash
sudo systemctl enable --now wg-quick@wg0
```

### 5. Configure T'Day to listen on the WireGuard interface

```bash
# .env (project root)
TDAY_HOST_BIND=10.0.0.2
TDAY_HOST_PORT=2525
```

Restart T'Day:

```bash
docker compose down && docker compose up -d
```

## Client Setup

### Android / iOS

1. Install the **WireGuard** app from the Play Store or App Store.
2. Create a new tunnel with this config:

```ini
[Interface]
PrivateKey = <PHONE_PRIVATE_KEY>
Address = 10.0.0.3/24
DNS = 1.1.1.1

[Peer]
PublicKey = <VPS_PUBLIC_KEY>
Endpoint = <VPS_PUBLIC_IP>:51820
AllowedIPs = 10.0.0.0/24
PersistentKeepalive = 25
```

3. Activate the tunnel.
4. In the T'Day app, set the server URL to `http://10.0.0.2:2525`.

### Laptop / Desktop

Same as above — install WireGuard, import the config with the laptop's private key and `Address = 10.0.0.4/24`, activate, and open `http://10.0.0.2:2525` in a browser.

## Optional: Adding HTTPS with Caddy on the VPS

If you want a public domain with TLS (so mobile apps and browsers get HTTPS):

### 1. Install Caddy on the VPS

```bash
sudo apt install -y debian-keyring debian-archive-keyring apt-transport-https
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' \
  | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' \
  | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt update && sudo apt install -y caddy
```

### 2. Configure Caddy as a reverse proxy

```
# /etc/caddy/Caddyfile
tday.yourdomain.com {
    reverse_proxy 10.0.0.2:2525
}
```

### 3. Point DNS and start

Point `tday.yourdomain.com` to the VPS's public IP (A record), then:

```bash
sudo systemctl restart caddy
```

Caddy automatically obtains a Let's Encrypt certificate. Clients now use `https://tday.yourdomain.com`.

With HTTPS via Caddy, set `TDAY_ENV=production` in `.env.docker`.

## T'Day Configuration

```bash
# .env.docker — plain HTTP over WireGuard (no Caddy)
TDAY_ENV=development

# .env.docker — HTTPS via Caddy on VPS
TDAY_ENV=production
```

## Verifying the Setup

```bash
# On the T'Day server — confirm WireGuard is active
sudo wg show

# From a client device on the VPN
curl http://10.0.0.2:2525/health
# Expected: {"status":"ok"}

# If using Caddy on VPS
curl https://tday.yourdomain.com/health
```

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| Peers can't connect to VPS | Port 51820 not open | `sudo ufw allow 51820/udp` |
| T'Day server can't reach VPS | NAT not being traversed | Verify `PersistentKeepalive = 25` in server config |
| Client can ping VPS but not T'Day server | VPS not forwarding | Check `net.ipv4.ip_forward=1` and peer `AllowedIPs` |
| `Connection refused` on `10.0.0.2:2525` | T'Day not bound to WireGuard IP | Set `TDAY_HOST_BIND=10.0.0.2` and restart |
| Handshake but no data transfer | Key mismatch | Regenerate and redistribute keys |

## Advantages

- Fully self-hosted — no third-party service dependency.
- WireGuard is fast, lightweight, and part of the Linux kernel.
- Native mobile apps work well.
- Can add HTTPS with a reverse proxy on the VPS.
- VPS doubles as a relay for other self-hosted services.

## Limitations

- Requires a VPS with a public IP (small recurring cost).
- More manual setup than Tailscale or Cloudflare Tunnel.
- Key management is manual — adding a new device means editing configs on the VPS.
- No built-in coordination, DNS, or NAT traversal magic (Tailscale automates all of this).

---

[Back to Remote Access Overview](../REMOTE_ACCESS.md)
