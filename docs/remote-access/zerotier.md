# ZeroTier

Access T'Day over a peer-to-peer virtual LAN. ZeroTier creates a software-defined network that works like a flat Ethernet segment — every device gets a private IP and can reach every other device directly, regardless of NAT or firewalls.

## Overview

| | |
|---|---|
| **How it works** | Devices join a ZeroTier network ID. The ZeroTier client establishes direct peer-to-peer connections (UDP hole-punching) or relays through ZeroTier's root servers when direct connectivity isn't possible. |
| **Complexity** | Low |
| **Public URL** | No — clients use a private ZeroTier IP |
| **HTTPS** | Traffic is encrypted by ZeroTier; no browser-trusted TLS certificate by default |
| **Mobile friendly** | Yes — official apps for Android and iOS |
| **Self-hostable** | Yes — [ztncui](https://github.com/key-networks/ztncui) or the ZeroTier self-hosted controller replace the cloud controller |
| **Cost** | Free for up to 25 devices; paid plans for more |

```
Phone / Laptop (ZeroTier client)
  └─ ZeroTier tunnel ─► Peer-to-peer (or via ZeroTier relay)
                          └─► T'Day server (ZeroTier IP 172.x.y.z)
                                └─► localhost:2525 ─► tday_backend :8080
```

## Prerequisites

- A **ZeroTier account** (free at [my.zerotier.com](https://my.zerotier.com)).
- **Docker** running T'Day on the server.
- ZeroTier-compatible OS on every device.

## Network Setup

### 1. Create a network

1. Log in to [my.zerotier.com](https://my.zerotier.com).
2. Click **Create A Network**.
3. Note the **Network ID** (a 16-character hex string like `a1b2c3d4e5f6g7h8`).
4. Under **Access Control**, choose **Private** (devices must be authorized before joining).

### 2. Configure IP assignment

Under **IPv4 Auto-Assign**, select a subnet (e.g., `172.22.0.0/16` — ZeroTier suggests one automatically). Devices that join the network are assigned IPs from this range.

## Server Setup

### 1. Install ZeroTier

```bash
curl -s https://install.zerotier.com | sudo bash
```

### 2. Join the network

```bash
sudo zerotier-cli join <NETWORK_ID>
```

### 3. Authorize the server

Go to [my.zerotier.com](https://my.zerotier.com) → your network → **Members**. Check the **Auth** box next to the server's entry.

### 4. Note the ZeroTier IP

```bash
sudo zerotier-cli listnetworks
# Look for the IP assigned to your network (e.g., 172.22.0.1)
```

### 5. Configure T'Day

```bash
# .env (project root)
TDAY_HOST_BIND=172.22.0.1   # your server's ZeroTier IP
TDAY_HOST_PORT=2525
```

Or bind to all interfaces with a firewall rule:

```bash
TDAY_HOST_BIND=0.0.0.0
```

```bash
# Restrict access to the ZeroTier interface
sudo ufw allow in on zt+ to any port 2525
sudo ufw deny in on eth0 to any port 2525
```

### 6. Restart T'Day

```bash
docker compose down && docker compose up -d
```

## Client Setup

### Android / iOS

1. Install **ZeroTier One** from the Play Store or App Store.
2. Add a network using the Network ID.
3. Authorize the device in the ZeroTier web console.
4. In the T'Day app, set the server URL to `http://172.22.0.1:2525`.

### Laptop / Desktop

```bash
# Install
curl -s https://install.zerotier.com | sudo bash

# Join
sudo zerotier-cli join <NETWORK_ID>
```

Authorize in the web console, then open `http://172.22.0.1:2525` in a browser.

## T'Day Configuration

```bash
# .env.docker — plain HTTP over ZeroTier
TDAY_ENV=development
```

If mobile apps and browser access the same instance via different origins:

```bash
# .env.docker
CORS_ALLOWED_ORIGINS=http://172.22.0.1:2525
```

## Verifying the Setup

```bash
# On the server
sudo zerotier-cli listnetworks
# Should show the network as "OK" with an IP

# From a client on the ZeroTier network
curl http://172.22.0.1:2525/health
# Expected: {"status":"ok"}

# Check peer connectivity
sudo zerotier-cli listpeers
```

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| Device shows `REQUESTING_CONFIGURATION` | Not authorized | Authorize in ZeroTier web console |
| `Connection refused` on ZeroTier IP | T'Day not bound to ZeroTier interface | Set `TDAY_HOST_BIND` to ZeroTier IP or `0.0.0.0` |
| Slow connection / high latency | Traffic relaying through root servers | Check `zerotier-cli listpeers` — `RELAY` means no direct path; ensure UDP is not blocked |
| Can't reach server from mobile | ZeroTier VPN not active on phone | Open ZeroTier app, enable the network toggle |
| Firewall blocking traffic | Host firewall rejecting ZeroTier subnet | Allow ZeroTier interface: `sudo ufw allow in on zt+` |

## Advantages

- Simple setup — one network ID to share across all devices.
- Peer-to-peer when possible (low latency).
- Free for up to 25 devices.
- No VPS required.
- Can be self-hosted (controller) for full independence.
- Works across NAT without port forwarding.

## Limitations

- Every device needs the ZeroTier app installed and authorized.
- Free tier limited to 25 devices (paid plans for more).
- No built-in public URL / HTTPS — would need an additional reverse proxy.
- NAT traversal relies on UDP; some corporate networks block it.
- Controller depends on ZeroTier's cloud by default (self-hosting is possible but more complex).

---

[Back to Remote Access Overview](../REMOTE_ACCESS.md)
