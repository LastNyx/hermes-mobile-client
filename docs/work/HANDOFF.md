# Handoff

Where the build stands (as of 2026-10-16), so work can continue on another PC (Windows or Arch Linux).
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

- Branch `feature/v1`, pushed to origin (batch 27 build is `dcbda1f`; docs commits come after it). Nothing merged to main, nothing tagged since
  v0.1.0. User decision: no release; the user runs the debug build.
- Every ticket is `Status: done` except 31 (wontfix). `./build.sh test`: 319 tests, 0 failures. Every batch up to 32 passed the reviewer
  (`.scratch/v1/review-*.md`, batches in `.scratch/v1/batches.md`). Nothing is blocked.
- Roadmap: `docs/ROADMAP.md` lists every phase and batch. Dev lessons: `docs/dev-notes.md` (34 lines, approved by the user 2026-10-16).
- Markdown library is `com.mikepenz:multiplatform-markdown-renderer-m3` **0.33.0** (`docs/work/MARKDOWN-LIB.md`); raising it needs a Compose BOM bump first.
- Test device: Realme RMX1921, Android 11, USB debugging on, `adb` in `~/tools/android-sdk/platform-tools`. The debug build of 14:45 on 2026-10-16
  (batch 32, all batches) is installed. Toolchain in `~/tools`; dev-team kit at `~/Dokumen/projects/hermes-dev-team/kit` (run with `python3 -u`).
- Reference app for behaviour: `Giton22/herald` (read-only reference, no code copied). Desktop source on this PC: `/opt/hermes-agent/apps/desktop/src`.

### Built and phone-checked by the user (2026-10-16 unless noted)
Edit any message, Regenerate, Branch from here; rotation in both layouts; reconnect re-attach; offline send without a crash; drafts; plain errors and
connection wording; long-chat smoothness, clipped long text and the "N earlier steps" line; usage line; share into the app; approval, question and
notification hardening; notices banner; process output. Earlier (2026-10-14): slash commands, session list, send-while-running, notification actions
(buttons did not show on the Realme, accepted), background processes.

### Built, NOT tried on a phone (or only partly)
- Attachments (79-81): the user rarely uses them. Limits: 6 per message, photos 25 MB, files 10 MB; PDFs need `pdftoppm` on the gateway host.
- Projects (84-85): Group by Project, new chat in a project, New project. Needs a gateway with `projects.tree`; older ones fall back to folder names.
- Subagents (91-92): panel above the composer, list on open, Stop per child. Not seen: needs a chat where the agent delegates.
- Checkpoints (93): list, diff, Restore. WorkLab has checkpoints off, so only the "off" message was seen. Restore rewrites a working tree: do not tap it on real data.
- Scheduled jobs (86-87): the page opened and showed "No scheduled jobs." Not seen: rows, Run now, Pause, Resume, create, edit, delete. The edit call is a PUT; the worker flagged a possible 405 on WorkLab.
- Capabilities (89) and the profile picker (90): not opened. Switches change which tools the agent has; the profile is sent as a `profile` query or body key.
- Empty and opening states (107): not seen (needs an empty list or a slow open).
- Insights (88): seen on the phone, loads real data. Plain layout.
- Agent images and the file viewer (82-83): the chat looks unchanged, but no inline image, zoom viewer, Save/Share or text viewer was seen (no chat had a file or picture). To try: ask the agent to make a PNG in its images folder, or to reply with a link to a text file. Images outside the gateway's media folders are refused with a short sentence by design.
- Unchecked: the updater's install confirmation (needs a newer release); the wide layout draft switch (99).

### Non-blocking reviewer smells (no ticket)
- A successful send clears the whole attachment tray, so files picked while it was in flight are lost.
- `chatCwd` of a new project chat is not saved across rotation (reopens as a plain chat).
- A file path with a space is cut at the space in the chat-list preview.
- `reloadHistory` flushes pending deltas twice; the thumbnail and `AttachmentReader` decode code overlap; chat test rigs are duplicated.
- Raw `e.message` is shown by `SlashRun` for RPC code 4004 only (deliberate).

### Next steps
1. Phone-check the "NOT tried" list above, ideally against a gateway with checkpoints on and a scheduled job to look at (read-only).
2. Session sharing (68, closed): a message from Desktop does not show live on the phone and vice versa; it appears after a delay. Gateway behaviour, cause not proven (`docs/PROTOCOL.md` 3.7). A possible later ticket: refresh history on the open chat when another client's reply completes.
3. Possible UI work from the audit: Insights as cards, the "Connected" status line sits left of the title, the empty Scheduled jobs text sits top left. Animation was skipped on purpose.
4. Not scheduled: offline cache, app lock (needs `androidx.biometric`, a build-file change), dictation, notification toggles, voice.
5. Merging `feature/v1` to main and any release are the user's call. Do not tag, merge or push to main.

## Session 2026-10-16: batches 18 to 32

- One ticket at a time, then reviewer rounds per batch (usually 1 to 3). Batch bases are in `.scratch/v1/batches.md`. A worker FAIL never happened this session.
- Planner task P1 is done: `docs/PROTOCOL.md` section 4.6 (edit, branch, attach, projects, subagents, checkpoints) and a REST block in section 7 were written from
  `/opt/hermes-agent` on this PC. That install has no git history, so it is NOT matched to the research checkout (`dce1e9b375`), and none of it was checked on WorkLab.
  Keep params minimal; WorkLab has rejected a param the source accepts.
- Phone check found three real bugs (fixed in batch 28 and 29): a crash from stopping `WatchService` before `startForeground` (101), the chat rebuilt when
  rotating between the narrow and wide layouts (103), and a chat that never re-attached to its running session after a reconnect (104).
- Dev workers may not edit `CONTEXT.md` (glossary): the planner adds glossary rows (Project was added by hand).
- Cost at list price: about $54.7 for 171 worker runs (a subscription seat bills differently).
- Batches 26, 27 and 30 were driven while the planner looked at the phone over adb (screenshots, read-only taps only). A screenshot found the title squeezed to "Chec..." by the model chip (106).
- Route check for 82 and 83: unauthenticated `/api/*` answers 401 even for a made-up path, so a token-less curl proves nothing. The user opened four URLs in a signed-in browser instead: `/api/media` answered "Path outside media roots" and the other three "Path not found", so all four exist. WorkLab now reports 0.22.2.
- Insights note: the total cost uses `total_actual_cost` when it is above 0, but the gateway's `by_model` rows only carry `estimated_cost`, so the model rows can add up to more than the total. That is the gateway, not an app bug.
- The docs worker ran: 16 CHANGELOG entries, drift list in `.scratch/v1/drift.md` (12 new-doc terms not in the glossary, 3 renamed-term candidates).
- Install steps used: `./build.sh assemble`, `python3 -I tools/linkcheck.py <apk>`, `adb install -r`, then compare `lastUpdateTime` with the APK mtime.

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

- After a dropped socket the gateway parks the session on a drop sink; events are discarded until `session.resume`, `session.activate` or `prompt.submit`
  re-binds the new socket. `session.events.since` only replays. The app calls `session.activate` (session id only) on every reconnect, then catch-up.
- Edit sends a REST history row `id` as `truncate_before_row_id`; it worked on the user's phone for an old message, a just-sent one and the first one.

- `session.resume` must send ONLY `session_id`: extra params (`inline_images`, `omit_messages`) are rejected
  with an invalid-params error even though PROTOCOL.md lists them. Send only what is documented and needed.
- `GET /api/sessions/{id}/messages` rows carry the body in `content` (string or list of parts), not `text`.
  `SessionsRepository.rowText` handles both; keep fake-gateway replies in tests in the real shape.
- The phone reaches WorkLab (`http://100.65.118.116:9119`) over Tailscale, a VPN network. In airplane mode the VPN
  stays up, so online = a network with INTERNET and NOT_VPN (`NetworkTracker`), never `activeNetwork != null`.
- Approvals are switched off on WorkLab; the approval card changes (ticket 95) were only exercised through notifications and the mock gateway.

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

- Notification buttons (tickets 49, 50: Allow once, Deny, and the Reply box for a single-question clarify) did not show on the user's Realme RMX1921 (Android 11). The notification itself and its tap-to-open work. Cause not found; the user said it is fine as it is (2026-10-14). If revisited, start with `adb shell dumpsys notification` while one is showing, to see whether the actions are on the notification or only hidden by the device's styling. The buttons work only while the chat screen is open and the watch runs.
- Design polish (Phase 2b) is done; the user is satisfied with it "for now". Notification wording may be revised later.
- Reasoning events are shown while streaming; reasoning in stored history is not read.
- Multi-select clarify questions are answered as one value because PROTOCOL.md has no multi-answer shape.
- Markdown library gotchas: 0.38.1 crashed the chat on Android 11 (`NoSuchMethodError` `shouldExecute`) because the app's
  Compose is 1.7.6 (BOM 2024.12.01). 0.35.0 crashed on a `BasicText` TextAutoSize overload. Unit tests and
  `assembleDebug` do not catch this. Always run `kit/linkcheck.py <apk>` and open a chat with a reply on a device.
- What is and is not phone-checked is listed under "Where we are" above.

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
