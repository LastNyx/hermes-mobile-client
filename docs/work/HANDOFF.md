# Handoff

Where the build stands, so work can continue on another PC (Windows or Arch Linux).

## Resume on another machine

What the machine needs first (tools, secrets, keystore, workers): `docs/work/PREREQUISITES.md`.

```bash
git clone https://github.com/LastNyx/hermes-mobile-client.git && cd hermes-mobile-client
git checkout <feature branch below>
scripts/setup-toolchain.sh        # JDK 17 + Android SDK into C:/home/tools or ~/tools
./build.sh test                   # must pass before dispatching anything
```

Tickets live in `.scratch/<feature>/issues/` and are committed (only `.scratch/runs*` is ignored),
so ticket Status travels with the branch. The planner (Hermes + `orchestrate` skill) picks the
next `todo` ticket whose blockers are `review` or `done` (`review` counts until the reviewer round
after all tickets; only then do tickets become `done`).

## Working agreement with the user

- One ticket at a time. After each ticket: verify (`git log --stat -1`, `./build.sh test`,
  `assembleDebug`, check the screen part was really built), set Status, update this file, push,
  send the APK, then **report and wait for the user's go** before dispatching the next ticket.
- Dev worker: `python <hermes-dev-team>/kit/run-worker.py dev "ticket: .scratch/v1/issues/<NN>-<slug>.md"`
  (Windows: `C:/home/project/hermes-dev-team/kit`). Never two workers at once.
- If a worker skips part of a ticket, do not mark it done: write a `<NN>b` follow-up ticket, have the
  user approve it, then dispatch.
- Known pattern for the reviewer round: workers write one test per ticket, so failure paths in
  02, 02b, 03, 04 are implemented but not separately tested.
- Ticket 11 needs planner work the worker cannot do: the public repo
  `LastNyx/hermes-mobile-client-releases` (user creates it), a release keystore (user keeps the
  backup outside git), repo secrets, and `.github/workflows/release.yml` (protected file, planner
  writes it with the user's yes).

## State

| Field | Value |
|---|---|
| Feature branch | `feature/v1` (tickets in `.scratch/v1/issues/`, 15 tickets incl. 02b, 04b, 05b, 05c) |
| Last done ticket | 11 (review, d0a4bf2; Updater + UpdateController + SettingsScreen + PackageInstaller glue; app side only, tested against fixtures, never run against a real release) |
| Last done ticket (also) | 10 (review, 8c5835a; WatchPolicy + WatchHub + WatchService (dataSync) + notifications; JVM-tested policy only, needs phone test) |
| Last done ticket (also) | 09 (review, 1466f67; model/reasoning dialog + slash suggestions in ChatScreen; command.dispatch send/skill/prefill/alias not handled) |
| Last done ticket (also) | 08 (review, eec2de7; approval + clarify cards in ChatScreen, sudo/secret declined; clarify answers first question only; JVM-tested only, not device-tested: approvals are off on WorkLab) |
| Last done ticket (also) | 07 (review, b12e6e2; replay by seq, epoch/truncated reload, open_requests exposed; no new screen) |
| Last done ticket (also) | 06 (review, bee4ca0; ChatController + reducer + ChatScreen wired in SessionsHost; phone checkpoint 2 pending) |
| Last done ticket (also) | 05c (review, af906e8; network monitor ignores the Tailscale VPN network; awaiting phone re-test) |
| Last done ticket (also) | 05b (review, aa52403; network monitor, list reload on reconnect, friendly offline error; awaiting phone re-test) |
| Last done ticket (also) | 05 (review, d31b2f1; `LiveLink` + Connected/Connecting/Offline indicator in `SessionsHost`) |
| Last done ticket (also) | 04b (review, 64db4a2) |
| Last done ticket (also) | 04 (review, 76f8781; data layer only) |
| Next ticket | none. All tickets are in review. Next: release plumbing (planner: releases repo, keystore, release.yml), then the reviewer round, then docs |
| Phone checkpoints | 1 after 03: PASSED 2026-10-12 (sign-in on WorkLab works); 2 after 06 (chat): PASSED 2026-10-12 (text-only UI, polish ticket not written yet); 3 at the end |
| APK delivery | After every ticket that passes: build the debug APK and send it to the user on Telegram, until the in-app updater (ticket 11) works. Use the Bot API `sendDocument` directly (curl `-F document=@<apk>`, bot token + first `TELEGRAM_ALLOWED_USERS` id from the Hermes `.env`, `--max-time 600`) and confirm `ok:true` with the file size. `hermes send` with `MEDIA:` times out on a 16 MB APK and only reports it as a warning. |
| Notes | Toolchain + skeleton verified on Windows: `./build.sh test` and `assembleDebug` pass. |
| Open follow-ups | Slash suggestions: pressing Enter on `/cmd` sends it as a chat message (only tapping a suggestion runs it); `command.dispatch` send/skill/prefill/alias unhandled; clarify answers first question only; chat UI is text-only (no polish ticket yet); reasoning level not read from session. Not device-tested: approvals (off on WorkLab), model switch beyond the dialog, reconnect catch-up with a slow turn. |
