# Hermes Mobile Client

An Android app that talks **directly** to your own [Hermes](https://hermes-agent.nousresearch.com) gateway
(`hermes serve`) from your phone. No bridge and no server-side component: the app signs in with the
RFC 8252 native flow, calls the gateway's REST API with a bearer token and streams chat over JSON-RPC on `/api/ws`.

Kotlin, Jetpack Compose, OkHttp. Android 8.0 (API 26) or newer.

## What it does

- Sign in the way Hermes Desktop does: in the browser, on the gateway's own login page. Your password is never typed into the app.
- Live streaming chat with stop, redirect and a message queue; tool calls and reasoning as compact rows.
- Chats list: search, pin, archive, group by date, status or project; edit, regenerate and branch from a message.
- Approvals and clarify questions answered from the phone, also from notifications.
- Model and reasoning picker, slash commands, attachments, background processes, subagents, checkpoints.
- Scheduled jobs, capabilities (skills, toolsets, MCP servers), Hermes profiles and an Insights page.
- Several gateways, one active at a time, with tokens stored per gateway.
- In-app update from this repository's releases.

What is and is not checked on a real device is listed in [docs/work/HANDOFF.md](docs/work/HANDOFF.md).

## Install

1. Download `HermesClient.apk` from the newest [release](../../releases).
2. Optional: compare its SHA-256 with `HermesClient.apk.sha256` or the digest shown next to the asset.
3. Open the file on the phone and allow installing from that source when Android asks.
4. Open the app, add your gateway URL and sign in.

### Desktop app (Windows, Linux)

The same chat plus the **Hub**: every gateway in one window, with each one's state and a **Needs me** list of
chats waiting on you on any gateway (`docs/HUB-DESIGN.md`). Java is bundled; nothing else to install.

- Windows: `HermesClient-<version>.msi` from the release. It installs for your user only. The MSI is not code-signed,
  so SmartScreen may warn ("More info", "Run anyway").
- Linux: `HermesClient-<version>-x86_64.AppImage`: `chmod +x` it and run it.
- Data (gateways, tokens) lives in `%APPDATA%\HermesClient` or `~/.local/share/hermes-client`, readable only by your
  user. No self-update yet: download a new release by hand.
- To reach a machine's Hermes over Tailscale, follow [docs/hub-gateway-setup.md](docs/hub-gateway-setup.md).

Settings has **Check for updates**; the app also checks once at startup. It downloads the APK, verifies its
SHA-256 against the published digest and refuses on a mismatch. Android installs an update only if it is signed
with the same key as the installed app.

## Security notes

- Tokens are kept in Keystore-backed encrypted storage and are excluded from backup. Signing out calls the
  gateway's logout and wipes that gateway's tokens.
- The app accepts any gateway URL, HTTP or HTTPS. On plain `http://` to a host that is not on your tailnet
  the token crosses the network unencrypted, and the app shows a warning. Prefer HTTPS or a private network such as Tailscale.
- The app contacts only the gateway you add and `api.github.com` / `github.com` for updates.

## Build

```bash
scripts/setup-toolchain.sh   # JDK 17 + Android SDK into ~/tools (or C:/home/tools on Windows)
./build.sh test              # JVM unit tests (OkHttp MockWebServer as the fake gateway)
./build.sh assemble          # debug APK: android/app/build/outputs/apk/debug/app-debug.apk
```

A release is a pushed `v*` tag: [`.github/workflows/release.yml`](.github/workflows/release.yml) runs the tests,
builds a signed APK, the Windows MSI and the Linux AppImage, and publishes them as one GitHub Release.
Run the desktop app from source with `cd android && ./gradlew :desktopApp:run`.

## Docs

- [docs/DESIGN.md](docs/DESIGN.md): settled design decisions
- [docs/PROTOCOL.md](docs/PROTOCOL.md): the gateway wire protocol the app uses
- [docs/ROADMAP.md](docs/ROADMAP.md) and [CHANGELOG.md](CHANGELOG.md)

## License

[MIT](LICENSE)
