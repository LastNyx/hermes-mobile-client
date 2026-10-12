# Handoff

Where the build stands (as of 2026-10-12), so work can continue on another PC (Windows or Arch Linux).
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

- All 11 feature tickets (plus 02b, 04b, 05b, 05c) are built and in `review`. None is `done`: a ticket
  becomes `done` only when its reviewer batch passes.
- **v0.1.0 is published** (tag `v0.1.0`, built by CI, signed with the release key). The user installed the
  release build on the phone ("mobile nice"); which parts were exercised on the minified build is not recorded.
  v0.1.0 does NOT contain any of the fixes below.
- **Reviewer round 1 ran on all 4 batches and every batch FAILED.** Reviews: `.scratch/v1/review-<b>-1.md`.
  10 fix tickets exist, all `todo`, **none dispatched** (the user said: review everything first, no fixes yet).
- Next step: the user picks the order and says go; dispatch fix tickets one at a time; then reviewer round 2;
  then the docs worker; then tag `v0.1.1` (ask the user: the fixes are not in 0.1.0).

### Review batches (`.scratch/v1/batches.md`)

| Batch | Tickets | Base | Verdict |
|---|---|---|---|
| 1 | 01, 02, 02b, 03 | `16a46fc` (merge-base with main) | FAIL 3 spec, 1 standards |
| 2 | 04, 04b, 05, 05b | `beb9368` | FAIL 2 spec, 2 standards |
| 3 | 05c, 06, 07, 08 | `37aae4a` | FAIL 4 spec, 2 standards |
| 4 | 09, 10, 11 | `f573778` | FAIL 3 spec, 3 standards |

Bases for batches 2 to 4 were chosen by commit order, not recorded as each batch passed (the skill's rule),
so a reviewer may have seen a little out-of-scope diff. Round 2 should reuse the same bases. Round 3 is never
dispatched without asking the user.

### Fix tickets (all `Status: todo`, in `.scratch/v1/issues/`)

Suggested order (the user has not chosen one yet):

| # | Ticket | What | Kind |
|---|---|---|---|
| 1 | `10-fix-watch-survives-leaving-chat` | Watch service and its notifications stop working once the chat screen is left (Back to the list): the `ChatController` scope dies with `ChatHost`, so no Done/approval notification and the service never stops. Also `WatchHub` keeps the link after the chat is gone. | real bug |
| 2 | `01-fix-ipv6-gateway-url` | `GatewayUrl.normalise` doubles brackets on IPv6 hosts and `warnsCleartext` throws, so the Gateways form crashes. Confirmed in code. | real bug |
| 3 | `03-fix-auth-state-surfacing` | "Signed out, sign in again" / "Gateway auth unavailable" never reach the Gateways row; `SignInController` and `AuthedHttp` keep two sources of truth, so Expired can outlive a fresh sign-in. Has a required screen part. | real bug |
| 4 | `08-fix-clarify-answers-and-coverage` | Clarify card answers only the first question; approval withdrawal untested. | gap + tests |
| 5 | `09-fix-expensive-model-confirm` | Expensive-model confirm is a dead end: the picker shows the message in red but cannot confirm and resend. | gap + tests |
| 6 | `11-fix-download-checksum-and-install-failure` | `Updater.download` checksum enforcement untested; `InstallStatusReceiver` ignores failure statuses so Settings sticks on "Installing...". | gap + tests |
| 7 | `05c-fix-offline-line-only-when-offline` | Grey "Offline" line also shows while Connecting, hiding a real can't-reach error. | small |
| 8 | `03-fix-missing-auth-tests` | Tests for 02, 02b, 03 criteria that were built but never tested. Do after #2 and #3 so they cover final code. | tests |
| 9 | `05b-fix-untested-friendly-error-and-reconnect-refresh` | Tests for the friendly-error mapping and refresh-on-Connected (moves the rule into a testable unit). | tests |
| 10 | `07-fix-untested-reload-paths` | Tests for the different-epoch and truncated reload after reconnect. | tests |

Reviewer smells with no ticket: duplicated history-to-items mapping in `ChatController`; inline fully-qualified
names and unordered imports; raw `e.message` shown in the model picker and slash UI (should be plain sentences);
the ping id `heartbeat-<ms>` vs `r<n>` (harmless, matches PROTOCOL).

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

- Chat UI is plain text with basic code blocks; no Markdown rendering; no polish ticket yet (ask the user what to
  mirror from Desktop). Notification wording may be revised later.
- Slash suggestions run only when tapped; Enter sends `/cmd` as a normal message. `command.dispatch` types
  `send`/`skill`/`prefill`/`alias` show nothing. Reasoning level is not read from the session on open.
- Not run on a device: approvals, expensive-model confirm, reconnect catch-up with a slow turn (the user's 1-300
  test finished too fast), the background watch beyond the simple case, the updater, R8 shrinking beyond what the
  user tried.
- Phone checkpoints: 1 (sign-in) PASSED; 2 (chat) PASSED with a text-only UI; 3 at the end.

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
