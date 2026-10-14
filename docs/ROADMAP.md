# Roadmap (pre-release work, before v0.1.1)

Reference for feature behaviour: [Giton22/herald](https://github.com/Giton22/herald) (`docs/ROADMAP.md`,
`docs/hermes-protocol-research.md`). Read it for how a feature works; do not copy code, and do not add
anything `docs/PROTOCOL.md` does not list. Settled decisions stay in `docs/DESIGN.md`.

Order = build order. Each phase ends with a reviewer pass and a phone check by the user.

## Phase 0: baseline
- [x] Toolchain installed on this PC, `./build.sh test` green on `feature/v1` (22 tests).
- [x] `05-fix-flaky-livelink-test`: the test failed once on a cold run (race reading the first client frame). Dispatch first.
- [x] `build.sh` and `setup-toolchain.sh` made executable (the launcher runs `./build.sh test`).

## Phase 1: fix tickets (already written, `.scratch/v1/issues/*-fix-*.md`)
Real bugs first, then gaps, then tests. Tests that cover final code come after the code they cover.
1. `10-fix-watch-survives-leaving-chat`
2. `01-fix-ipv6-gateway-url`
3. `03-fix-auth-state-surfacing`
4. `08-fix-clarify-answers-and-coverage`
5. `09-fix-expensive-model-confirm`
6. `11-fix-download-checksum-and-install-failure`
7. `05c-fix-offline-line-only-when-offline`
8. `03-fix-missing-auth-tests`
9. `05b-fix-untested-friendly-error-and-reconnect-refresh`
10. `07-fix-untested-reload-paths`

Then reviewer round 2 on batches 1 to 4 (same bases as `batches.md`). Round 3 is never dispatched without asking.

## Phase 2: Markdown chat (Herald: Chat, Markdown/Code)
Gap today: replies are plain text with basic code blocks.
- [x] GFM rendering: headings, lists, tables, links (web and mail only), inline code.
- [x] Code blocks with language label and copy button; copy whole reply.
- [x] Reasoning and tool rows collapsible (check what ticket 06 already did before cutting tickets).
- Needs a Markdown library: `libs.versions.toml` is protected, so the planner adds it with the user's yes.
- Syntax highlighting and TeX math are later, not here.

## Phase 2b: design polish (done)
Batches 7 to 9 (`.scratch/v1/batches.md`). All three batches PASS; the user is satisfied with the design "for now".
- Batch 7: ticket 18 chat design polish, 18-fix title default. Base `b1ab414`. FAIL round 1 (style: "New chat" default repeated), PASS round 2 (`review-7-2.md`).
- Batch 8: tickets 19 text size, 20 compact chat, 21 consistent screens, fixes 19-fix, 20-fix, 21-fix, 20-fix-imports. Base `6ac4980`. FAIL round 1 (layering, spacing, indent), FAIL round 2 (imports), PASS round 3 (`review-8-3.md`).
- Batch 9: ticket 22 model picker, 22-fix. Base `b88a0c5`. FAIL round 1 (missing test, imports), PASS round 2 (`review-9-2.md`).

## Phase 3: slash commands (DESIGN Q3 scope; Herald: Slash commands)
Gap today: suggestions only run when tapped, Enter sends `/cmd` as plain text, and `command.dispatch` types
`send`/`skill`/`prefill`/`alias` show nothing.
- [ ] Autocomplete while typing (`complete.slash`), catalog from `commands.catalog`.
- [ ] Enter on a slash command runs `slash.exec`, falls back to `command.dispatch`.
- [ ] Handle every dispatch type: `exec`/`plugin` show output, `alias` re-runs, `send`/`skill` submit, `prefill` fills the composer.
- [x] Reasoning level read from the session on open.

### Starting notes for the next session
Read from the code on 2026-10-13. No Phase 3 tickets are written yet.
- Works now: `SlashCommands.load()` calls `commands.catalog` when a chat opens. Suggestions filter locally as you type (`slash.filter(draft)`, first 6); tapping one runs it. `run()` calls `slash.exec` and, on an RPC error other than 4001, falls back to `command.dispatch`. The result shows as a note using `output`, `warning` or `notice`. The reasoning level is read on open with `config.get` (key `reasoning`, ticket 22).
- Missing: `complete.slash` is unused; (stale, fixed by tickets 23-26: Send runs `/cmd` as a command, output card cut at 12 lines); error text from `SlashCommands` and `ModelsController.fail` is raw `e.message`.
- Where the code is: `SlashCommands`; `ChatScreen.kt` (the composer, with the round Send/Steer/Stop button and the 44 dp field); `ModelsController`.
- Before cutting tickets, read `docs/PROTOCOL.md` lines ~383-386 (`commands.catalog`, `complete.slash`, `slash.exec`, `command.dispatch`) and Herald's `docs/hermes-protocol-research.md` (https://github.com/Giton22/herald). Herald's notes have not been read yet. Do not add any method or field that PROTOCOL.md does not list.

## Phase 4: close out
- [x] Docs worker, drift report (2026-10-14).
- [x] Release decision (2026-10-14): no release. The user runs the debug build for himself; v0.1.0 stays the only tag.

## Backlog (user decision 2026-10-14)
Goal: usable almost like Hermes Desktop, essential usage only. Herald is a feature reference, not a plan to follow. Picked by the user: session tools, send-while-running, notification actions. Order is the planner's; each one gets its own ticket batch after a protocol check.
1. Session tools: pin and archive (rename, delete, search exist). PROTOCOL has `session.archive`, `session.delete`, `session.title` and the REST PATCH with `pinned`, `archived`, `unread`.
2. Send-while-running: queue, steer, stop and send, undo and retry. PROTOCOL has `prompt.submit` (`queued`), `session.steer`, `session.redirect`, `session.interrupt`, `session.undo`.
3. Notification actions: approve or answer from the notification. Needs the watch service to answer server requests over the live link.
Not scheduled (no decision yet): attachments, offline cache, cron and capabilities, voice.
