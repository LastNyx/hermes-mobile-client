# Changelog

## Unreleased

- Model picker: opening a chat shows the session's current reasoning level in the header chip (for example `sonnet · high`). The picker is a bottom sheet with reasoning chips (the current one ticked) and model rows (the current one highlighted). Confirm and errors show in a rounded box at the top; if the reasoning level cannot be read, the model list still loads.
- Look and layout: Sessions, Gateways and Settings share one top bar and use tonal 12 dp cards with no dividers. Settings has Text size and About sections and no bottom Back button. New chat is in the Sessions top bar.
- Text size: Settings has a Text size setting (Small 0.9x, Default 1.0x, Large 1.15x, Extra large 1.3x) with a sample line. The whole app rescales at once, and the choice is saved on the device.
- Compact chat: less space after a prompt and inside a reply, smaller reply and code text, smaller Markdown headings, and a 44 dp composer. The top bar keeps the 48 dp back-button minimum.
- Chat top bar and composer: the chat header shows a back arrow on the phone layout, the session title and the model chip. Replies sit on a rounded surface; the whole reply is copied with an icon that turns into a check and is hidden while it streams. The composer is rounded, with a round Send, Steer or Stop button.
- Reasoning and tool rows: reasoning and tool rows are collapsible; the reasoning row has a surface and a chevron.
- Chat spacing: turns are spaced apart in the chat, with the turn-spacing fix included.
- Code blocks: code is shown in cards with a Copy button.
- Markdown replies: assistant replies render headings, lists, tables, links (web and mail only) and inline code.
- Self-updater: Settings shows the app version and a "Check for updates" action; a silent check at startup shows "Update available" when a newer release exists. Install verifies the APK's SHA-256 and reports failures in plain sentences.
- Background watch: when a turn you started on the phone is still running or waiting for you, a foreground service keeps the connection open with a "Hermes is working…" notification; Done, failed, approval and question notifications open that chat. Leaving the chat ends the watch.
- Model and reasoning: the chat header shows the current model; a picker sets the model and reasoning effort, and asks for confirmation before an expensive model switch.
- Slash commands: typing `/` in the composer suggests the gateway's commands, filtered as you type. Tapping a suggestion runs it and shows its output, warning or notice in the chat. Pressing Send on a typed command runs it as a command. Output appears in a card, cut at 12 lines with Show more.
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
