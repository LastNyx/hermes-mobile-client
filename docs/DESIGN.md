# Hermes Mobile Client: design (settled by grilling, 2026-10-12)

An Android app that talks **directly** to a Hermes gateway (`hermes serve`, the same backend Hermes
Desktop's "Remote gateway" uses). No bridge, no PC-side install: the gateway's own auth gate admits
the phone, exactly as it admits Desktop.

```
Android app ── HTTP(S), bearer token ──► hermes serve (e.g. WorkLab http://100.65.118.116:9119)
            └─ WebSocket /api/ws?ticket=… (JSON-RPC, same protocol as Desktop/TUI)
```

Why not hermes-mobile-remote's design: its bridge exists because it predates/avoids the gateway auth
gate. With a gated remote gateway, the bridge is an extra process, extra pairing and an extra trust
store for no gain. We copy its **documentation layout** and its **self-update method**, not its code.

## Decisions

| # | Decision |
|---|---|
| Q1 | Repo `C:\home\project\hermes-mobile-client`, remote `github.com/LastNyx/hermes-mobile-client`. Push only on the user's say-so. |
| Q2 | Sign-in = RFC 8252 native flow, as Desktop: `GET /auth/native/authorize` (S256 PKCE, loopback `redirect_uri`) → gateway's own `/login` page → code → `POST /auth/native/token` → bearer access + refresh. `POST /auth/native/refresh` rotates; 401 `session_expired` → re-login. |
| Q3 | v1 scope: live streaming chat with stop/steer; session list/resume/new; approvals + clarify on phone; model + reasoning picker; slash commands; background notifications. |
| Q4 | Any URL the user enters, HTTP or HTTPS (cleartext permitted app-wide). Accepted risk: bearer tokens cross plain HTTP on non-tailnet URLs. UI shows a warning on `http://` non-tailnet hosts. |
| Q5 | Self-update copied from hermes-mobile-remote `Updater.kt`: GitHub Releases latest → APK asset → SHA-256 vs release digest → `PackageInstaller` session; same signing key required. |
| Q6 | Login opens in a Chrome Custom Tab; the app runs a one-shot listener on `127.0.0.1:<random port>` for the redirect. The password never touches app code. (Gateway rejects `localhost`; must be an IP literal.) |
| Q7 | Toolchain: portable JDK 17 + Android cmdline-tools under `C:\home\tools`, no admin, no PATH edits; `local.properties`/`org.gradle.java.home` point at them. |
| Q8 | Releases: GitHub Actions builds a signed APK on tag `v*`; keystore in repo secrets; the user keeps a keystore backup outside git. |
| Q9 | Notifications: a foreground service holds the gateway socket only while a phone-started turn runs or waits on the user (approval/clarify), then stops. |
| Q10 | Multiple gateways, a registry like Desktop's: add/edit/remove, one active, switchable; tokens stored per gateway. |
| Q11 | `docs/PROTOCOL.md` is extracted from the Hermes source on this PC (auth routes, ws-ticket, JSON-RPC methods/events, close codes) and committed; tickets cite it. Workers have no web. |
| Q12 | Kotlin + Jetpack Compose + OkHttp, minSdk 26, package `io.github.lastnyx.hermesclient`. |
| Q13 | `.team.json` test = `./gradlew testDebugUnitTest`; JVM tests against a fake gateway (OkHttp MockWebServer) for auth, refresh, ws events. UI is verified by the user on the phone. |
| Q14 | FULL lane (dev → reviewer → docs). Docs mirror hermes-mobile-remote: README, INSTALL, FEATURES, ARCHITECTURE, SECURITY, TROUBLESHOOTING (+ PROTOCOL). |
| Q15 | Tokens in Keystore-backed encrypted storage, excluded from backup; logout calls `/auth/logout` and wipes the gateway's tokens. |
| Q16 | The repo is **public**. CI publishes release APKs as GitHub Releases of this same repo (`GITHUB_TOKEN`, no extra secret); the Updater reads its latest release without a token (`BuildConfig.RELEASES_REPO`). Supersedes the earlier private-source / separate releases-repo split. |
| Q17 | Work must be resumable on another PC: docs/work/HANDOFF.md is committed on the feature branch, and the branch is pushed after every ticket. Tickets, reviews and research (`.scratch/`) live in the separate private repo `LastNyx/hermes-mobile-scratch`, cloned into `.scratch/` (git-ignored here). |
| Q18 | `scripts/setup-toolchain.sh` installs the same JDK 17 + Android SDK on Windows (git-bash, `C:/home/tools`) and Linux/Arch (`~/tools`). |

## Facts verified against WorkLab (2026-10-12)

- `GET /api/status` is public: version 0.21.5.
- `GET /api/auth/providers` → `basic` (Username & Password) only, so the native flow lands on `/login`.
- `POST /api/auth/ws-ticket` mints a 30 s single-use ticket; the WS upgrade carries `?ticket=`.
- `/api/ws` speaks newline-delimited JSON-RPC, `gateway.ready` right after accept, client pings ~15 s.

## Next

1. On build signal: install toolchain (Q7), write `docs/PROTOCOL.md` (Q11), `CONTEXT.md` glossary.
2. `to-tickets` → tracer-bullet tickets; show Seam + Test command per ticket for approval.
3. `orchestrate` FULL lane.
