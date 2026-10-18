# Glossary

| Term | Meaning |
|---|---|
| **Gateway** | One Hermes backend (`hermes serve`) the app can reach, identified by its base URL (path prefix allowed). |
| **Hub** | The overview of every registered gateway at once: which ones answer, which are busy, and what needs the user. |
| **Needs me** | The list of sessions, on any gateway, that are waiting on the user (an approval or a question). |
| **Unreachable** | A gateway the app cannot reach right now, e.g. because it sits on another network; not an error the user must fix. |
| **Gateway registry** | The app's list of gateways: name, base URL, which one is **active**. Mirrors Hermes Desktop's "Registered gateways". |
| **Native sign-in** | RFC 8252 flow: Custom Tab to `/auth/native/authorize` with PKCE, loopback redirect, code exchanged at `/auth/native/token`. |
| **Loopback receiver** | The one-shot HTTP listener on `127.0.0.1:<random port>` that catches the sign-in redirect. |
| **Token pair** | Access token (bearer for REST) + refresh token, stored encrypted per gateway. |
| **Refresh** | `POST /auth/native/refresh`; 401 `session_expired` means sign in again, 503 means try later. |
| **WS ticket** | 30 s single-use credential from `POST /api/auth/ws-ticket`, passed as `?ticket=` on the `/api/ws` upgrade. |
| **Live link** | The app's WebSocket connection to one gateway, speaking newline-delimited JSON-RPC. |
| **Session** | A Hermes conversation (`session_id`), shared with Desktop and other clients. |
| **Turn** | One user prompt and everything the agent does until it completes, fails or is interrupted. |
| **Redirect** | Text sent while a turn is running; the gateway stops the running reply and continues with it (`session.redirect`). |
| **Queue** | Messages parked on the phone, sent one by one after the running reply. |
| **Stop** | Interrupt the running turn. |
| **Approval** | Hermes asks permission for a tool call; the user answers on the phone. |
| **Clarify** | Hermes asks the user a question (choices or free text) mid-turn. |
| **Watch service** | Foreground service that keeps the live link only while a phone-started turn runs or waits on the user. |
| **Updater** | Self-update from this repo's latest GitHub Release: APK + SHA-256 check + PackageInstaller. |
| **Project** | A Hermes workspace of one or more folders; chats belong to one by their cwd. The Project grouping uses the gateway's projects, and falls back to the cwd folder name when the gateway can't list them. |
