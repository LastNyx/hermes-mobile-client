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
so ticket Status travels with the branch. The planner (Hermes + `orchestrate` skill) reads the
first ticket whose Status is `todo` and whose blockers are `done`.

## State

| Field | Value |
|---|---|
| Feature branch | `feature/v1` (tickets in `.scratch/v1/issues/`, 13 tickets incl. 02b, 04b) |
| Last done ticket | 04b (review, 64db4a2) |
| Last done ticket (also) | 04 (review, 76f8781; data layer only) |
| Next ticket | 05 live link (todo, not started) |
| Phone checkpoints | 1 after 03: PASSED 2026-10-12 (sign-in on WorkLab works); 2 after 06 (chat); 3 at the end |
| APK delivery | After every ticket that passes: build the debug APK and send it to the user on Telegram, until the in-app updater (ticket 11) works. Use the Bot API `sendDocument` directly (curl `-F document=@<apk>`, bot token + first `TELEGRAM_ALLOWED_USERS` id from the Hermes `.env`, `--max-time 600`) and confirm `ok:true` with the file size. `hermes send` with `MEDIA:` times out on a 16 MB APK and only reports it as a warning. |
| Notes | Toolchain + skeleton verified on Windows: `./build.sh test` and `assembleDebug` pass. |
