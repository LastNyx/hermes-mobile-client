# Hub: one desktop and phone client for every Hermes (settled by grilling, 2026-10-18)

The user runs Hermes on three machines: the Windows laptop, the Arch PC (cachyos-x8664) and WorkLab. Today each
one is reached on its own. The **Hub** shows all of them in one window, and any session can be opened and chatted
in place. The client stays **Hermes-only**: no screen control, shells or file transfer (those belong to tools like
Muxin, T3 Code and Waku, which drive other agent CLIs; we borrow only their hub and session UX ideas).

```
Desktop app (Windows, Linux) ─┐                ┌─► hermes serve on Laptop  (tailscale serve --https)
Android app ──────────────────┼── HTTPS + WS ──┼─► hermes serve on Arch    (tailscale serve --https)
   shared Kotlin + Compose ───┘                └─► hermes serve on WorkLab (company tailnet)
```

Decisions in `docs/DESIGN.md` (Q1-Q18) still hold unless a row below replaces them.

## Decisions

| # | Decision |
|---|---|
| H1 | Same repo, branch `feature/hub` from `main` (v1 was merged by PR #1). The v1 handoff commit was cherry-picked as the first commit. The repo keeps its name; renaming waits for the user's say-so. |
| H2 | Desktop = **Compose Multiplatform**. The repo splits into `shared` (Kotlin data layer + shared Compose screens), `androidApp` and `desktopApp` (JVM, Windows and Linux). |
| H3 | **Restructure first**, as its own batch: shared module + androidApp + an empty desktop window. Phone tests stay green and the APK behaves the same. No hub feature lands until this batch passes review. |
| H4 | Shared UI: chat, approval and clarify cards, session list live in `shared`. Each app adds only its shell (window vs. phone navigation, notifications, updater). |
| H5 | Desktop v1 = **Hub plus full chat**: every gateway's status, the Needs-me list, and any session opens in place. |
| H6 | Desktop layout: three panes. Left = gateways (online or offline, busy count) with Needs me on top. Middle = sessions of the selected gateway, or of all. Right = the open chat. |
| H7 | One chat open at a time. Switching chats keeps the other gateways' live links up. |
| H8 | The desktop keeps a **live link to every signed-in gateway** while it runs. The phone keeps its current watch policy (DESIGN Q9) until it is improved later. |
| H9 | **Needs me** = `session.active_list` items with status `waiting`, on every gateway, refreshed on `sessions.changed` and every 10 s. Clicking one opens that chat, which attaches and shows the approval or clarify card. The hub never attaches to chats the user did not open. |
| H10 | Sign-in on the desktop = the same native flow as the phone: system browser to the gateway's `/login`, redirect caught on a `127.0.0.1` loopback port, PKCE S256 (reuses the shared code). |
| H11 | Desktop tokens: a JSON file in the app's own data folder, readable only by the user (chmod 600 on Linux, user-profile ACL on Windows). No keyring, no service, nothing installed in the OS. Accepted risk: anyone logged in as the user, or reading a backup, can read the tokens. |
| H12 | Each Hermes stays bound to `127.0.0.1`; `tailscale serve --https` publishes it on the tailnet. Setting this up on Laptop and Arch is part of this feature: a checklist in `docs/hub-gateway-setup.md`, verified from this laptop. |
| H13 | WorkLab is on the company tailnet and the user's machines are on his own; a device is on one tailnet at a time. The hub shows gateways it cannot reach as **Unreachable (other network?)** and reconnects by itself when the network changes. All three are not expected to be online together. |
| H14 | Shipping: CI builds a Windows MSI (or portable zip) and a Linux tar.gz/AppImage with a bundled Java runtime, attached to the same GitHub Release as the APK. No desktop self-update in v1. |
| H15 | One version for both apps; each pushed change bumps only Z (one tag, one Release). |
| H16 | `.team.json` test = `./build.sh test`: every module's JVM tests (shared commonTest + jvmTest, androidApp unit tests, desktopApp tests) plus a compile of both apps. |
| H17 | Words: **Hub** is the overview, **Needs me** is the attention list, **Gateway** stays one Hermes. |

## Facts behind the decisions (checked 2026-10-18)

- `session.active_list` returns `SessionActiveItem.status` in `idle|starting|waiting|working|streaming|resuming`
  (`docs/PROTOCOL.md` §4.2, §4.4). Approvals and clarify are server requests sent only to clients attached to that
  session (§3.4, §5.1), which is why H9 opens the chat instead of answering from the list.
- `sessions.changed` is a session-less broadcast (§5).
- Hermes source on this PC (`hermes_cli/web_server.py:541-562`): any non-loopback bind forces the auth gate, and a
  non-loopback `dashboard.public_url` does too. `tailscale serve` connects from loopback, so **H12 must set
  `dashboard.public_url` to the `https://<machine>.<tailnet>.ts.net` URL**, or the gateway runs ungated behind the
  proxy and its Host check rejects the tailnet hostname (`web_server.py:525-538, 624-654`). The setup checklist
  must verify `auth_required: true` in `/api/status` through the tailnet URL. Not yet run.
- `tailscale status` from this laptop: laptop-bhdsivdk, cachyos-x8664 (offline 6 days), galaxy-tab-s8, realme-xt.
  WorkLab (100.65.118.116) is not on this tailnet.

## Order

1. **Batch R (restructure, H3):** shared module + androidApp move, build.sh and CI updated, empty desktop window,
   `.team.json` per H16. Phone checked by the user on the device.
2. **Gateway setup (H12):** checklist doc, Laptop gateway through `tailscale serve`, then Arch when it is online.
3. **Desktop sign-in and chat:** token file (H11), native sign-in (H10), shared screens (H4) in the window.
4. **Hub:** gateway pane with status, live links to all (H8), Needs me (H9), the unreachable state (H13).
5. **Packaging (H14).**

Each step gets tickets with `to-tickets` and the orchestrate fields before any dispatch.

## Not decided here

- The phone's own hub (overview of every gateway on the phone): later, by the user's word ("phone will be improved
  later").
- Desktop notifications for Needs me: not asked. Raise it when the hub batch is planned.
