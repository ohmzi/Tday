# SSH Tunnel

Forward T'Day's port over an encrypted SSH connection. Requires no extra software beyond an SSH client — if you can SSH into the server (directly or via a jump box), you can access T'Day from your laptop's browser.

## Overview

| | |
|---|---|
| **How it works** | An SSH local-forward (`-L`) binds a port on your local machine and tunnels traffic to `localhost:2525` on the remote server. A reverse tunnel (`-R`) does the opposite — the server pushes its port to an intermediary. |
| **Complexity** | Low |
| **Public URL** | No |
| **HTTPS** | Traffic encrypted by SSH; no browser-trusted TLS certificate |
| **Mobile friendly** | Awkward — no native SSH tunnel support on iOS/Android without third-party apps |
| **Self-hostable** | Yes — just SSH |
| **Cost** | Free (if the server has an SSH port reachable, or a small VPS as a jump box) |

```
Laptop
  └─ SSH tunnel ─► T'Day server
                     └─► localhost:2525 ─► tday_backend :8080
  └─ Browser → http://localhost:2525 (forwarded)
```

## Prerequisites

- **SSH access** to the T'Day server (direct or via a jump box / bastion host).
- T'Day running with default `TDAY_HOST_BIND=127.0.0.1`.
- An SSH client on your local machine (built into macOS, Linux; PuTTY or Windows Terminal on Windows).

## Method 1: Local Port Forward (Direct SSH)

Use this when your laptop can SSH directly into the T'Day server.

### 1. Open the tunnel

```bash
ssh -L 2525:localhost:2525 user@tday-server
```

This binds `localhost:2525` on your laptop to `localhost:2525` on the server.

### 2. Access T'Day

Open `http://localhost:2525` in your browser. Traffic flows through the SSH tunnel.

### 3. Run in the background (optional)

```bash
ssh -fNL 2525:localhost:2525 user@tday-server
```

- `-f` — background after authentication.
- `-N` — no remote shell (tunnel only).
- `-L` — local forward.

### 4. Close the tunnel

```bash
# Find the SSH process
ps aux | grep "ssh -fNL"
kill <pid>
```

## Method 2: Reverse SSH Tunnel (Server Behind NAT)

Use this when the T'Day server has no publicly reachable SSH port. The server initiates an outbound SSH connection to a relay (a VPS with a public IP) and exposes its port there.

### On the relay VPS

Ensure SSH is running and the user can accept reverse tunnels:

```bash
# /etc/ssh/sshd_config (on the relay VPS)
GatewayPorts clientspecified
```

Restart sshd: `sudo systemctl restart sshd`.

### On the T'Day server

```bash
ssh -R 0.0.0.0:2525:localhost:2525 user@relay-vps
```

Now `relay-vps:2525` forwards to the T'Day server's `localhost:2525`.

### From your laptop

```bash
# Option A: SSH into the relay and forward locally
ssh -L 2525:localhost:2525 user@relay-vps

# Option B: Access the relay directly (if GatewayPorts is enabled)
# Open http://relay-vps-ip:2525 in your browser
```

## Persistent Tunnels with `autossh`

`autossh` monitors the SSH connection and restarts it if it drops.

### Install

```bash
# Debian / Ubuntu
sudo apt install -y autossh

# macOS
brew install autossh
```

### Local forward (persistent)

```bash
autossh -M 0 -fNL 2525:localhost:2525 user@tday-server \
  -o "ServerAliveInterval 30" -o "ServerAliveCountMax 3"
```

### Reverse tunnel (persistent)

```bash
# On the T'Day server
autossh -M 0 -fNR 0.0.0.0:2525:localhost:2525 user@relay-vps \
  -o "ServerAliveInterval 30" -o "ServerAliveCountMax 3"
```

### Run on boot with systemd

```ini
# /etc/systemd/system/tday-tunnel.service
[Unit]
Description=SSH tunnel to T'Day
After=network-online.target
Wants=network-online.target

[Service]
User=<your-user>
ExecStart=/usr/bin/autossh -M 0 -NR 0.0.0.0:2525:localhost:2525 user@relay-vps \
  -o "ServerAliveInterval 30" -o "ServerAliveCountMax 3" \
  -o "StrictHostKeyChecking accept-new" -i /home/<your-user>/.ssh/id_ed25519
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl enable --now tday-tunnel
```

## T'Day Configuration

No changes needed — SSH tunnels connect to `localhost:2525`, which is the default bind address.

```bash
# .env (project root) — keep defaults
# TDAY_HOST_BIND=127.0.0.1
# TDAY_HOST_PORT=2525
```

```bash
# .env.docker — HTTP over tunnel, no HTTPS
TDAY_ENV=development
```

## Mobile Access (Workarounds)

SSH tunnels don't have native mobile support, but there are workarounds:

- **Android**: [ConnectBot](https://connectbot.org/) or [Termux](https://termux.dev/) can create SSH tunnels. Configure a local forward, then point the T'Day app to `http://localhost:2525`.
- **iOS**: [Termius](https://termius.com/) supports SSH port forwarding. Same approach.

These are clunky compared to Tailscale or WireGuard for regular mobile use.

## Verifying the Setup

```bash
# Check the tunnel is active
ss -tlnp | grep 2525

# Test from your laptop
curl http://localhost:2525/health
# Expected: {"status":"ok"}

# For reverse tunnel, test from the relay
curl http://localhost:2525/health
```

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `bind: Address already in use` | Port 2525 already taken locally | Use a different local port: `ssh -L 3000:localhost:2525 ...` then access `localhost:3000` |
| `Connection refused` through tunnel | T'Day container not running | `docker compose up -d` on the server |
| Reverse tunnel not accessible from outside relay | `GatewayPorts` not set | Add `GatewayPorts clientspecified` to relay's `sshd_config` |
| Tunnel drops after inactivity | No keepalive | Use `autossh` with `ServerAliveInterval 30` |
| `Permission denied (publickey)` | SSH key not configured | Set up key-based auth: `ssh-copy-id user@server` |

## Advantages

- Zero extra software — SSH is already installed on most systems.
- No account creation, no third-party service.
- Simple and well-understood.
- Works as a quick bridge while setting up a more permanent solution.

## Limitations

- Not practical for regular mobile use.
- Tunnel must be manually opened (or automated with `autossh` / systemd).
- Only one user per tunnel instance (each user needs their own SSH tunnel).
- No public URL — can't share a link with someone.
- Requires SSH access to the server (directly or via a relay).

---

[Back to Remote Access Overview](../REMOTE_ACCESS.md)
