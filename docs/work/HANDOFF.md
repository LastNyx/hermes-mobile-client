# Handoff

Where the build stands, so work can continue on another PC (Windows or Arch Linux).

## Resume on another machine

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
| Feature branch | `feature/v1` (tickets in `.scratch/v1/issues/`, 14 tickets incl. 02b, 04b, 05b) |
| Last done ticket | 05b (review, aa52403; network monitor, list reload on reconnect, friendly offline error; awaiting phone re-test) |
| Last done ticket (also) | 05 (review, d31b2f1; `LiveLink` + Connected/Connecting/Offline indicator in `SessionsHost`) |
| Last done ticket (also) | 04b (review, 64db4a2) |
| Last done ticket (also) | 04 (review, 76f8781; data layer only) |
| Next ticket | 06 chat (todo, not started) |
| Phone checkpoints | 1 after 03: PASSED 2026-10-12 (sign-in on WorkLab works); 2 after 06 (chat); 3 at the end |
| APK delivery | After every ticket that passes: build the debug APK and send it to the user on Telegram, until the in-app updater (ticket 11) works. Use the Bot API `sendDocument` directly (curl `-F document=@<apk>`, bot token + first `TELEGRAM_ALLOWED_USERS` id from the Hermes `.env`, `--max-time 600`) and confirm `ok:true` with the file size. `hermes send` with `MEDIA:` times out on a 16 MB APK and only reports it as a warning. |
| Notes | Toolchain + skeleton verified on Windows: `./build.sh test` and `assembleDebug` pass. |
