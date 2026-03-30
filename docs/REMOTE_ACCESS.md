# Remote Access

How to reach your self-hosted T'Day instance from outside the local network.

## Background

By default, Docker Compose binds the backend to **`127.0.0.1:2525`** (localhost only). This means no external device — phone, laptop on another network, or browser outside the LAN — can reach T'Day directly. You need an ingress method that bridges external clients to `localhost:2525` on the Docker host.

```
External Client
  └─ HTTPS / VPN ─► [ ingress method ] ─► localhost:2525 ─► tday_backend :8080
```

This document compares every supported method and links to a dedicated setup guide for each.

## Quick Comparison

| Method | Complexity | Public URL | HTTPS | Mobile Friendly | Self-Hostable | Recurring Cost |
|--------|-----------|------------|-------|-----------------|---------------|----------------|
| [Cloudflare Tunnel](remote-access/cloudflare-tunnel.md) | Low | Yes (`tday.ohmz.cloud`) | Yes (Cloudflare edge) | Yes | No (Cloudflare service) | Free |
| [Tailscale](remote-access/tailscale.md) | Very low | Optional (Funnel) | Yes (WireGuard + optional Funnel TLS) | Yes (native apps) | Partial (Headscale) | Free (personal) |
| [WireGuard](remote-access/wireguard.md) | Medium | No (private IPs) | Encrypted tunnel | Yes (native apps) | Yes | VPS cost (~$4-6/mo) |
| [ZeroTier](remote-access/zerotier.md) | Low | No (private IPs) | Encrypted tunnel | Yes (native apps) | Yes (self-hosted controller) | Free (≤25 nodes) |
| [SSH Tunnel](remote-access/ssh-tunnel.md) | Low | No | Encrypted tunnel | Awkward (no native mobile) | Yes | Free / VPS cost |
| [ngrok](remote-access/ngrok.md) | Very low | Yes | Yes (ngrok edge) | Yes | No (ngrok service) | Free / $8+/mo |
| [frp](remote-access/frp.md) | Medium-High | Yes | Yes (with cert config) | Yes | Yes | VPS cost (~$4-6/mo) |

### Column Definitions

- **Complexity**: How much effort to install, configure, and maintain.
- **Public URL**: Whether external users get a stable `https://...` address without installing anything.
- **HTTPS**: Whether traffic is encrypted in transit. All methods encrypt the tunnel; "Yes" in the HTTPS column means browsers see a valid TLS certificate.
- **Mobile Friendly**: Whether Android and iOS T'Day apps can connect without special client software or awkward workflows.
- **Self-Hostable**: Whether you can run the entire infrastructure yourself, with no third-party dependency.
- **Recurring Cost**: Ongoing cost beyond what you already pay for the server.

## Choosing a Method

### Personal use, mobile apps, no public URL needed

**Tailscale** is the simplest path. Install on the server and each device — all devices join a private WireGuard mesh. No port forwarding, no DNS, no certificates to manage. The Android and iOS Tailscale apps run in the background and give every device a stable private IP.

### Personal use, want a public URL

**Cloudflare Tunnel** (current production setup) or **Tailscale Funnel**. Cloudflare gives you a custom domain with edge caching and DDoS protection. Tailscale Funnel gives you a `https://<machine>.<tailnet>.ts.net` URL with less configuration but no custom domain.

### Full self-hosting, no third-party dependencies

**WireGuard** on a small VPS. You control every component. Requires the most manual setup but has zero dependency on external services.

### Quick demo or temporary access

**ngrok** gives you a public HTTPS URL in one command. Free tier URLs are ephemeral (change on restart); paid tier gives stable subdomains.

### Multiple self-hosted services behind one VPS

**frp** (Fast Reverse Proxy) is purpose-built for exposing many local services through one public relay. Good if T'Day is one of several self-hosted apps you want to reach remotely.

### SSH access already available

**SSH tunneling** requires no extra software. If you can SSH into the server, you can forward the port. Not ideal for mobile apps but works well for browser-only access from a laptop.

## T'Day Configuration for Remote Access

Regardless of which method you choose, T'Day needs a few configuration changes depending on your approach.

### Localhost-only methods (Cloudflare Tunnel, ngrok, SSH Tunnel, frp)

These methods connect to `localhost:2525` on the Docker host. **No change** to `TDAY_HOST_BIND` is needed — keep the default `127.0.0.1`.

### Network-binding methods (Tailscale, WireGuard, ZeroTier)

These methods assign your server a private IP on a virtual network. You need the backend to listen on that interface.

**Option A — Bind to the specific VPN IP** (recommended):

```bash
# .env (project root)
TDAY_HOST_BIND=100.x.y.z    # your Tailscale/WireGuard/ZeroTier IP
```

**Option B — Bind to all interfaces** (simpler, less restrictive):

```bash
# .env (project root)
TDAY_HOST_BIND=0.0.0.0
```

When using Option B, ensure your host firewall only allows traffic from the VPN subnet. See the individual setup guides for firewall rules.

### Production environment settings

When exposing T'Day over HTTPS (any method), set these in `.env.docker`:

```bash
TDAY_ENV=production          # enables HSTS headers and secure cookies
```

If clients access T'Day from a different origin than the one serving the SPA (e.g., mobile apps hitting a Tailscale IP while the web app is on a domain):

```bash
CORS_ALLOWED_ORIGINS=https://tday.ohmz.cloud,http://100.x.y.z:2525
```

### Mobile app configuration

The Android and iOS apps prompt for a server URL on first launch. Enter the URL appropriate for your access method:

| Method | Server URL |
|--------|-----------|
| Cloudflare Tunnel | `https://tday.ohmz.cloud` |
| Tailscale | `http://<tailscale-ip>:2525` |
| Tailscale Funnel | `https://<machine>.<tailnet>.ts.net` |
| WireGuard | `http://<wireguard-ip>:2525` |
| ZeroTier | `http://<zerotier-ip>:2525` |
| ngrok | `https://<your-subdomain>.ngrok-free.app` |
| frp | `https://<your-domain>` |

## Setup Guides

Each guide walks through installation, configuration, verification, and T'Day-specific settings:

| Guide | Description |
|-------|-------------|
| [Cloudflare Tunnel](remote-access/cloudflare-tunnel.md) | Expose T'Day via Cloudflare's edge network with a custom domain and HTTPS |
| [Tailscale](remote-access/tailscale.md) | Private WireGuard mesh VPN with optional public Funnel URL |
| [WireGuard](remote-access/wireguard.md) | Self-hosted VPN using a relay VPS for full control |
| [ZeroTier](remote-access/zerotier.md) | Peer-to-peer virtual LAN with a managed or self-hosted controller |
| [SSH Tunnel](remote-access/ssh-tunnel.md) | Port forwarding over SSH for quick laptop-only access |
| [ngrok](remote-access/ngrok.md) | Instant public HTTPS URL via ngrok's tunnel service |
| [frp](remote-access/frp.md) | Self-hosted reverse proxy for exposing multiple local services |

## Security Considerations

Regardless of the access method:

- T'Day enforces **JWE session tokens**, **PBKDF2 password hashing**, and **rate limiting** at the application layer.
- **HSTS headers** are applied when `TDAY_ENV=production`.
- **CSRF protection** is active on all state-changing endpoints.
- For methods that provide HTTPS (Cloudflare, ngrok, Tailscale Funnel), browsers enforce secure cookies automatically.
- For methods that expose plain HTTP on a private network (Tailscale IP, WireGuard IP, ZeroTier IP), traffic is still encrypted by the VPN tunnel, but browsers will not enforce `Secure` cookie flags. Set `TDAY_ENV=development` or omit it in these cases to avoid cookie issues.
- See [SECURITY.md](../SECURITY.md) and [Cloudflare Auth Hardening](security/cloudflare-auth-hardening.md) for additional hardening guidance.
