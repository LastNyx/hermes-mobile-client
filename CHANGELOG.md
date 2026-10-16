# Changelog

## Unreleased

## v0.2.0 - 2026-10-16

- First release from this repository, which is now public (MIT). Earlier builds were published to a separate releases repo; an app installed from v0.1.0 does not look here, so install v0.2.0 by hand once.

- Edit and branch: long-press one of your messages for Edit (the later messages are removed and the edited text is sent) or Branch from here (opens a new chat with the turns up to that message). The last reply has Retry and Regenerate. Edit and Regenerate show only while the chat is idle.
- Rotation: turning the phone keeps the open chat, its scroll position and the live connection, in both layouts.
- Reconnect: after the connection drops and comes back, the open chat re-attaches to its running turn, so the rest of the reply keeps arriving and Stop works without leaving the chat.
- Offline send: a send in airplane mode no longer crashes the app; a failed Stop keeps the turn shown as running with an error line, and a later successful Stop clears that line.
- Drafts: text typed in a chat is kept per chat and per gateway, so going Back, reopening the chat and force-stopping the app keep it.
- Plain errors and status: errors show short plain sentences (for example "Can't reach the gateway. Check your connection."); the connection line reads Waiting for network, Reconnecting, Connected or Offline · retrying; a Keystore read failure is no longer shown as signed out; REST calls time out after 30 s.
- Long chats: streamed text is batched so long replies stay smooth; very long tool output and replies end with a "... N more characters" line; while a reply runs only the last four finished steps show, with a tappable "N earlier steps" line.
- Usage line: a small grey line such as "1.2k in · 85 out" under each finished reply (none on replies loaded from history).
- Share: sharing a page or text to Hermes from another app opens a new chat with the title and link in the composer; nothing is sent.
- Safer prompts: approval and question cards ignore a tap in the first 700 ms, disable their buttons while the connection is down, show a long command in part with "Show full command", and clear when the turn ends; Allow once and Deny on a notification need the phone unlocked (Android 12 and later); a failed answer keeps the card.
- Gateway notices: a one-line banner above the composer shows gateway notices (errors red, warnings amber, info grey) with Dismiss; timed ones expire.
- Background processes: the output view shows the exit code of a failed process and cleaner text.
- Updater: an install error or a cancelled install shows a message in Settings.
- Catch-up: a dropped event now triggers a catch-up instead of losing text.
- Attachments (not yet tried on a phone): a paperclip in the composer adds photos or files (up to 6 per message; photos up to 25 MB, files up to 10 MB); thumbnails show above the box; old chats show attachments as `[image]` and a paperclip line instead of `@file:` text.
- Projects (not yet tried on a phone): Group by Project uses the gateway's projects (falls back to folder names); "+" offers No project and the gateway's projects (a new chat starts in the project's folder); More > New project creates one from a name and a folder.

- Background processes: a "Background · N running" bar above the composer lists processes the agent started with its terminal tool. Tap it to expand; tap a row for the last 12 lines of output; Stop kills a running process, Dismiss removes a finished one. Finished rows stay until dismissed, so you can see whether each one finished or failed. The list refreshes on open, after a tool finishes, when the gateway reports a process, after a reconnect, and every 5 seconds while one runs.
- Notification actions: the approval notification has Allow once and Deny; a clarify with one question has a Reply box. They work while the chat screen is open and the watch runs; leaving the chat clears the notification. They did not show on the Realme RMX1921 (see HANDOFF).
- Retry and edit: Retry sits next to Copy on the last reply, and a long-press on your last message offers Edit and resend. Both show only while the chat is idle.
- Queue: while a reply runs, long-press the button for Queue for later. A "Queued · N" panel has Send now, Edit and Remove. The queue sends one message after each clean finish, not after an error or a stop, and a message whose send fails goes back to the front. It is kept in memory only.
- Redirect: text typed while a reply runs now redirects the reply (as Desktop does) instead of steering it. The button and placeholder say Redirect.
- Gateways: a card per gateway with a status dot, an Active chip, a status line and a menu (Test connection, Sign in or out, Edit, Remove); Add and Settings are icons. Add and Edit use a sheet with a title, field icons, a live address hint, banners for the cleartext warning and errors, and a Save button that waits for both fields.
- Chats list: a top bar with the title, a Live link dot and icons (search, view, new chat, more); search is collapsed behind its icon. Rows are flat with one-line previews that hide URLs and `@file:` tokens, and group headers are sticky with counts.
- Session list: pin and archive from the long-press menu (archive has Undo) and an Archived page. Group by date (Pinned, Today, Yesterday, Last week, Older), status or project; order by last activity or created; filter by status. A status dot shows Working, Unread, Draft or Idle. Choices are saved on the device. There is no "Needs input" status because the gateway's list does not report it.
- Spacing: space around prompts, between turns and inside replies is about a quarter tighter. Text size is unchanged; tool, group and Thinking headers stay compact.
- Type: each kind of text uses one style (body, secondary, label, caption, title, code). Command output and the Thinking text now use the body size.
- Tool and Thinking rows: a tool row shows a readable label, a short preview and the duration. Three or more tool calls in a row collapse into one "Used N tools" row. The tool detail has a Copy button. The Thinking row shows a one-line preview and a spinner while it streams.
- Clarify and approval cards: long text and choices wrap inside the card and can be selected. The card scrolls when it is taller than about 60% of the screen, and the Answer and choice buttons stay visible.
- Scrolling: a chat opens at the latest message, including a pending approval or question. It follows new content only while you are near the bottom; when you have scrolled up, a "Jump to latest" button appears. Send always scrolls to the bottom.
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
