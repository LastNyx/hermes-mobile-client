# Publishing a machine's Hermes on your tailnet (for the Hub)

Each machine keeps `hermes serve` on `127.0.0.1`; `tailscale serve` publishes it on the tailnet with HTTPS
(`docs/HUB-DESIGN.md` H12). Nothing listens on the LAN, no firewall rule is added, and tokens never cross plain HTTP.

Values used below (change them per machine):

| | Laptop (Windows) | Arch PC |
|---|---|---|
| Tailnet name | `laptop-bhdsivdk.tailb2b759.ts.net` | `cachyos-x8664.tailb2b759.ts.net` |
| Hermes port (loopback) | `9119` | `9119` |
| Tailnet HTTPS port | `9443` (8443 is Atlas) | `9443` |
| Gateway URL for the app | `https://laptop-bhdsivdk.tailb2b759.ts.net:9443` | `https://cachyos-x8664.tailb2b759.ts.net:9443` |

## Why three settings and not one

Read in the Hermes source (`hermes_cli/web_server.py`, v0.22.2):

- The sign-in gate turns on only for a non-loopback bind **or** a non-loopback `dashboard.public_url`. Behind
  `tailscale serve` every request arrives from `127.0.0.1`, so without `public_url` the gateway runs **without
  sign-in** for anyone on the tailnet.
- The Host/Origin guard accepts only the bound host or the `public_url` host, so without it the tailnet name is refused.
- With the gate on, Hermes refuses to start until a sign-in provider exists. The simplest is username and password
  (`dashboard.basic_auth`, stored as a scrypt hash, never in plain text).

## Steps (each machine)

1. **Set the public URL** (one line in Hermes' `config.yaml`, under `dashboard:`):
   ```yaml
   dashboard:
     public_url: https://laptop-bhdsivdk.tailb2b759.ts.net:9443
   ```
   Hermes Desktop's own private backends on that machine keep working (`_desktop_loopback_auth_exempt`).
2. **Create the login, typed by you** (the password is never given to an agent):
   ```sh
   hermes serve --host 127.0.0.1 --port 9119
   ```
   With the gate on and no provider, it asks "How do you want to authenticate the dashboard?": choose `[1]`, enter a
   username and password. It saves `dashboard.basic_auth` (hash + signing secret) and starts. Leave it running.
3. **Publish it on the tailnet:**
   ```sh
   tailscale serve --bg --https=9443 http://127.0.0.1:9119
   ```
4. **Check from another tailnet device** (or the same machine):
   ```sh
   curl -s https://laptop-bhdsivdk.tailb2b759.ts.net:9443/api/status
   ```
   Pass = JSON with `"auth_required": true` and `native_pkce` in `auth_flows`. If `auth_required` is false, stop:
   the gateway is open on the tailnet; recheck step 1.
5. **Start at login.**
   - Windows: Task Scheduler, "At log on", action `hermes serve --host 127.0.0.1 --port 9119 --skip-build`.
     `tailscale serve --bg` survives reboots by itself.
   - Arch: a user unit `~/.config/systemd/user/hermes-serve.service` with
     `ExecStart=%h/.local/bin/hermes serve --host 127.0.0.1 --port 9119 --skip-build`, then
     `systemctl --user enable --now hermes-serve`.
6. **Add it in the app:** Gateways, Add, name (e.g. "Laptop"), the gateway URL from the table, Sign in.

## Undo

```sh
tailscale serve --https=9443 off
```
Remove `dashboard.public_url` (and `dashboard.basic_auth` if wanted) from `config.yaml`, and the login task or unit.

## WorkLab

WorkLab is on the company tailnet and keeps its own setup. A device is on one tailnet at a time, so the Hub shows it
as "Unreachable (other network?)" while you are on your own tailnet (H13).
