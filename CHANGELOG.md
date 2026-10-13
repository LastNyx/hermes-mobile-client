# Changelog

## Unreleased

- Self-updater: Settings shows the app version and a "Check for updates" action; a silent check at startup shows "Update available" when a newer release exists. Install verifies the APK's SHA-256 and reports failures in plain sentences.
- Background watch: when a turn you started on the phone is still running or waiting for you, a foreground service keeps the connection open with a "Hermes is working…" notification; Done, failed, approval and question notifications open that chat. Leaving the chat ends the watch.
- Model and reasoning: the chat header shows the current model; a picker sets the model and reasoning effort, and asks for confirmation before an expensive model switch.
- Slash commands: typing `/` in the composer suggests the gateway's commands, filtered as you type, and shows their output in the chat.
- Approvals and questions: approval cards offer Allow once, Allow for session, Always allow and Deny; clarify questions (several at once) are answered in the chat. Password, sudo and secret prompts are declined with "Answer this on the PC".
- Chat: opening a session loads its history; replies stream in; tool calls show as rows; Send becomes Stop while a turn runs, and text sent then steers it.
- Catch-up: after a dropped connection, the open chat fills in missed events without duplicates, or reloads history if the gateway restarted.
- Session list: lists the gateway's chats newest first, with search, pull to refresh, rename and delete (long-press), and New chat. On wide screens the list sits beside the chat.
- Network awareness: the Live link indicator turns Offline when the phone loses its physical network (VPN-only networks do not count) and reconnects at once when it returns. The session list shows a neutral "Offline" line instead of an error.
- Friendly errors: an unreachable gateway shows "Can't reach the gateway. Check your connection." instead of raw exception text.
- Auth state on the Gateways row: "Signed out — sign in again" after a rejected refresh, and "Gateway auth unavailable, retrying" while the auth provider is down.
- Signed-in requests: the app refreshes an expired token before a request and retries a GET once after a 401.
- Native sign-in: "Sign in" on a gateway row opens the gateway's login page in a Custom Tab; the row then shows "Signed in as <name>". "Sign out" clears that gateway's tokens. Tokens are encrypted on the device.
- Gateways: add, edit, remove and set the active gateway; "Test" shows whether it answers and its Hermes version; a plain warning appears for cleartext `http://` hosts other than loopback or Tailscale. IPv6 gateway URLs such as `http://[::1]:9119` are accepted.
- Startup: the app opens on the session list when the active gateway is signed in, otherwise on the Gateways screen.
