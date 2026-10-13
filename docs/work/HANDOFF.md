# Handoff

Where the build stands (as of 2026-10-13), so work can continue on another PC (Windows or Arch Linux).
Read this top to bottom, then `docs/DESIGN.md` (settled decisions; do not reopen) and `docs/PROTOCOL.md`
(the only source for gateway endpoints and methods).

## Resume on another machine

What the machine needs first (tools, secrets, keystore, workers): `docs/work/PREREQUISITES.md`.

```bash
git clone https://github.com/LastNyx/hermes-mobile-client.git && cd hermes-mobile-client
git checkout feature/v1
scripts/setup-toolchain.sh        # JDK 17 + Android SDK into C:/home/tools or ~/tools
./build.sh test                   # must pass before dispatching anything
```

Also clone `LastNyx/hermes-mobile-client-releases` next to this repo (README + CHANGELOG live there).
Tickets live in `.scratch/v1/issues/` and are committed (only `.scratch/runs*` is ignored), so ticket
Status travels with the branch.

## Where we are

- Branch `feature/v1`, pushed to origin up to `2badbf5` on 2026-10-13 with the user's go.
  Nothing has been merged to main, and nothing has been tagged since v0.1.0.
- Tickets 01-17 plus fixes in `.scratch/v1/issues/` are `Status: done`.
- `./build.sh test`: 50 tests, 0 failures. A flaky LiveLinkTest race was fixed by `05-fix-flaky-livelink-test`.
- Kotlin upgraded from 2.1.0 to 2.4.21 (batch 5). `jvmTarget` moved to `kotlin { compilerOptions }`.
- Phase 2 Markdown chat is done (batch 6: tickets 13 Markdown replies, 14 code cards and Copy, 15 reasoning rows,
  16 chat look, 16-fix turn spacing, 17 Markdown library version fix).
- Markdown library is `com.mikepenz:multiplatform-markdown-renderer-m3` **0.33.0**; see `docs/work/MARKDOWN-LIB.md`.
  On the Realme RMX1921 (Android 11) the debug build with 0.33.0 opens a chat with a Markdown reply (bold, bullets,
  inline code) and does not crash. That is the only device check of the Markdown work so far.
- Phone feedback: the UI looks minimalist, "okay, not great enough". A design ticket comes next, then Phase 3.
- Test device: Realme RMX1921, Android 11, USB debugging on. `adb` is in `~/tools/android-sdk/platform-tools`.
- v0.1.0 is published (tag `v0.1.0`). All of the fixes below are NOT in the published v0.1.0.
- Machine now: Arch Linux PC. Repo at `~/Proyek/hermes-mobile-client`. Toolchain installed by
  `scripts/setup-toolchain.sh` into `~/tools` (JDK 17 + Android SDK). `build.sh` and
  `scripts/setup-toolchain.sh` are executable. Dev-team kit at `~/Dokumen/projects/hermes-dev-team/kit`
  (run with `python3 -u`).
- Telegram APK delivery works: Bot API `sendDocument` with `TELEGRAM_BOT_TOKEN` and the first `TELEGRAM_ALLOWED_USERS`
  id from `~/.hermes/.env`. The user had to send `/start` to the bot first. `hermes send` has no home channel set.
- Reference app for building features: `Giton22/herald` (Kotlin Multiplatform Android client for Hermes).
  Read its `docs/ROADMAP.md` and `docs/hermes-protocol-research.md` for how a feature behaves; do not copy
  code and do not build anything `docs/PROTOCOL.md` does not list.
- Plan: `docs/ROADMAP.md`.

### Review batches (`.scratch/v1/batches.md`)

| Batch | Tickets | Base | Result |
|---|---|---|---|
| 1 | 01, 02, 02b, 03 | `16a46fc` | PASS round 2 (`review-1-2.md`) |
| 2 | 04, 04b, 05, 05b | `beb9368` | PASS round 2 (`review-2-2.md`) |
| 3 | 05c, 06, 07, 08 | `37aae4a` | FAIL round 2 on two style findings, fixed by `06-fix-dedupe-history-mapping`, then PASS round 3 (`review-3-2.md`, `review-3-3.md`) |
| 4 | 09, 10, 11 | `f573778` | PASS round 2 (`review-4-2.md`) |
| 5 | 12 (Kotlin 2.1.0 to 2.4.21) | `34cd034` | PASS round 1 (`review-5-1.md`) |
| 6 | 13, 14, 15, 16, 16-fix, 17 (Markdown chat) | `b9ef8da` | FAIL round 1 on turn spacing (`review-6-1.md`), fixed by `16-fix`; PASS round 2 (`review-6-2.md`); PASS round 3 after ticket 17 (`review-6-3.md`) |

The docs worker ran; its drift list is `.scratch/v1/drift.md`. `CHANGELOG.md` has an Unreleased section.

### Fix tickets (all `Status: done`, in `.scratch/v1/issues/`)

| # | Ticket | What changed |
|---|---|---|
| 1 | `10-fix-watch-survives-leaving-chat` | Leaving the chat ends the watch and stops the service. |
| 2 | `01-fix-ipv6-gateway-url` | IPv6 gateway URLs no longer crash the Gateways form. |
| 3 | `03-fix-auth-state-surfacing` | Gateways row shows 'Signed out, sign in again' and 'Gateway auth unavailable, retrying'. |
| 4 | `08-fix-clarify-answers-and-coverage` | Clarify card answers every question. |
| 5 | `09-fix-expensive-model-confirm` | Expensive-model switch has Confirm and Cancel. |
| 6 | `11-fix-download-checksum-and-install-failure` | Updater download checksum is tested; install failures show in Settings. |
| 7 | `05c-fix-offline-line-only-when-offline` | Grey Offline line only while Offline. |
| 8 | `03-fix-missing-auth-tests` | Extra tests for auth. |
| 9 | `05b-fix-untested-friendly-error-and-reconnect-refresh` | Extra tests for friendly errors and reconnect refresh. |
| 10 | `07-fix-untested-reload-paths` | Extra tests for the reload paths. |
| 11 | `06-fix-dedupe-history-mapping` | History-to-items mapping exists once in `ChatController`. |
| 12 | `05-fix-flaky-livelink-test` | Flaky LiveLinkTest race fixed. |

### Not run on a device

- Leaving the chat during a running turn (no Done notification comes after leaving, by design of the chosen rule).
- The Gateways row auth messages.
- Approval and clarify cards (approvals are off on the WorkLab gateway).
- The expensive-model Confirm.
- The updater and install failure messages.
- Markdown code card.
- Markdown table.
- Reasoning row.
- Link tap.
- Whole-reply Copy.

### Unchecked

- CHANGELOG says slash suggestions are filtered as you type; the old handoff said suggestions run only when
  tapped. Unchecked.
- Slash dispatch types `send`, `skill`, `prefill` and `alias` show nothing.

### Non-blocking reviewer smells (no ticket)

- Anonymous state wrapper in `AuthedHttp`.
- The 'Signed out — sign in again' string is repeated in `AuthedHttp` and `SignInController`.
- `SignInState` and `AuthState` both exist.
- Raw `e.message` in `ModelsController.fail` and `SlashCommands`.
- Fully qualified names in `UpdateController` and `MainActivity`.

### Next steps

1. Design ticket for the chat UI (phone feedback: minimalist, "okay, not great enough").
2. Phase 3 = slash commands.
3. Then the docs worker.
4. Then ask the user about tagging v0.1.1 (the fixes are not in 0.1.0).
- Raising the Markdown library above 0.33.0 needs a Compose BOM bump first (planner decision).

## Working agreement with the user

- One ticket at a time. After each: verify (`git log --stat -1`, `./build.sh test`, `assembleDebug`, parse
  `android/app/build/test-results/testDebugUnitTest/*.xml`, read the screen-part diff, grep for `Log.`/`println`),
  set Status, update this file, push, send the debug APK on Telegram, then **report and wait for the user's go**.
- Commit the ticket's `Status: in-progress` before dispatching (the launcher inspects the tree).
- Dev worker: `python -u <hermes-dev-team>/kit/run-worker.py dev "ticket: .scratch/v1/issues/<file>.md"`
  (Windows: `C:/home/project/hermes-dev-team/kit`), `terminal(background=true, heartbeat=120)`. Never two workers at once.
- If a worker skips part of a ticket, do not mark it done: write a follow-up ticket and have the user approve it.
- Phone findings become follow-up tickets (trace each symptom to a cause in code first).
- After fix tickets, run reviewer round 2 per batch (same prompt, `round: 2`); PASS marks the batch's tickets `done`.
- No Co-Authored-By trailers. Never push to main or merge. Push only `feature/v1` (and tags with the user's go).
- Tell the user plainly what is untested. Do not write "works" in this file for something the user did not run.

## Gateway facts learned on the real WorkLab gateway (0.21.5)

- `session.resume` must send ONLY `session_id`: extra params (`inline_images`, `omit_messages`) are rejected
  with an invalid-params error even though PROTOCOL.md lists them. Send only what is documented and needed.
- `GET /api/sessions/{id}/messages` rows carry the body in `content` (string or list of parts), not `text`.
  `SessionsRepository.rowText` handles both; keep fake-gateway replies in tests in the real shape.
- The phone reaches WorkLab (`http://100.65.118.116:9119`) over Tailscale, a VPN network. In airplane mode the VPN
  stays up, so online = a network with INTERNET and NOT_VPN (`NetworkTracker`), never `activeNetwork != null`.
- Approvals are switched off on WorkLab, so the approval cards were never seen on a device.

## Releases

- Source repo `LastNyx/hermes-mobile-client` is **private**; releases go to the **public**
  `LastNyx/hermes-mobile-client-releases` (only README, CHANGELOG and release assets; GitHub's auto "Source code"
  zips contain just those two files).
- Release = push a tag `v*`: `.github/workflows/release.yml` runs tests, builds a signed APK, publishes
  `HermesClient.apk` + `HermesClient.apk.sha256` with `gh release create`. v0.1.0 run took 6m39s.
- Secrets in the source repo: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
  `ANDROID_KEY_PASSWORD`, `RELEASES_TOKEN` (fine-grained, releases repo only, has an expiry).
- Keystore: `C:/home/keys/` (outside git, made by `scripts/make-release-key.sh`). Certificate SHA-256
  `63:36:9F:BF:...:1F:30:B4`. **The user must keep a backup outside both PCs**; losing it means every user reinstalls.
- Phone installs of debug builds and release builds have different keys: switching needs one uninstall.
- After each release update the releases repo's `CHANGELOG.md` (and README "Latest" line), commit and push there.
- Updater only ever ran against fixtures; an end-to-end update needs a second release (v0.1.1).
- Releases CI warns that `actions/checkout@v4` and `setup-java@v4` are on a deprecated Node version (harmless now).

## Known gaps and not device-tested

- Chat UI renders Markdown (Phase 2 done) but still looks minimalist per phone feedback; the design ticket is next
  (ask the user what to mirror from Desktop). Notification wording may be revised later.
- Reasoning events are shown while streaming; reasoning in stored history is not read.
- Multi-select clarify questions are answered as one value because PROTOCOL.md has no multi-answer shape.
- Reasoning level is not read from the session on open.
- Markdown library gotchas: 0.38.1 crashed the chat on Android 11 (`NoSuchMethodError` `shouldExecute`) because the app's
  Compose is 1.7.6 (BOM 2024.12.01). 0.35.0 crashed on a `BasicText` TextAutoSize overload. Unit tests and
  `assembleDebug` do not catch this. Always run `kit/linkcheck.py <apk>` and open a chat with a reply on a device.
- Phone checkpoints: 1 (sign-in) PASSED; 2 (chat) PASSED with a text-only UI; 3 at the end.
- The "Not run on a device" list is under "Where we are" above; nothing in it has been run on a device.

## APK delivery

After every ticket that passes, build the debug APK (`android/app/build/outputs/apk/debug/app-debug.apk`) and send
it to the user on Telegram with the Bot API `sendDocument` (curl `-F document=@<apk>`, bot token and the first
`TELEGRAM_ALLOWED_USERS` id from the Hermes `.env` with `tr -d '\r'`, `--max-time` under 600) and confirm `ok:true`
with the file size. `hermes send` with `MEDIA:` times out on a 16 MB APK. Native Windows `curl` cannot write to
MSYS `/tmp`: use `C:/` paths. Once the in-app updater is proven (after v0.1.1), release APKs replace this.

## Tooling gotchas (Windows git-bash)

- Foreground `terminal` calls above 600 s are auto-promoted to background; a trailing `grep` with no match makes
  the whole command exit 1.
- `$TMPDIR` is `/tmp` in bash (invisible to native tools); Hermes scratch is
  `C:/Users/User/AppData/Local/hermes/cache/scratch`.
- `apksigner.bat` does not run through `cmd //c` here; use
  `C:/home/tools/jdk-17/bin/java.exe -jar C:/home/tools/android-sdk/build-tools/35.0.0/lib/apksigner.jar verify ...`.
- `gh` is at `/c/Program Files/GitHub CLI/gh.exe` (not on PATH), logged in as LastNyx with repo + workflow scopes.
- Python heredocs that write Kotlin: `\\n` inside the heredoc becomes a real newline in the file; patch the
  generated file instead of trusting the heredoc.
