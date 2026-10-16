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
Built (tickets 23-26) and phone-checked by the user on 2026-10-14.
- [x] Autocomplete while typing (`complete.slash`), catalog from `commands.catalog`.
- [x] Enter on a slash command runs `slash.exec`, falls back to `command.dispatch`.
- [x] Handle every dispatch type: `exec`/`plugin` show output, `alias` re-runs, `send`/`skill` submit, `prefill` fills the composer.
- [x] Reasoning level read from the session on open.

## Phase 4: close out
- [x] Docs worker, drift report (2026-10-14).
- [x] Release decision (2026-10-14): no release. The user runs the debug build for himself; v0.1.0 stays the only tag.

## Phase 5: research tickets 57-109 plus 82, 83, 68 (2026-10-15 to 2026-10-16)
Tickets came from the research rounds (`.scratch/research-*.md`). Batches are in `.scratch/v1/batches.md`; every one below passed the reviewer. Phone checks are by the user on a Realme RMX1921 (Android 11).
- [x] Batch 17 (57-60, 98): history latest page, tool row, dismissed processes, answer result, id-less keys. Review rounds: 2.
- [x] Batch 18 (61-63, 65, 99, 100): failed Stop, updater install status, event-gap catch-up, drafts. Rounds: 3. Phone-checked 2026-10-16.
- [x] Batch 19 (64, 66, 67, 69 and fixes): rotation, friendly errors and timeouts, Keystore failure, token-delta batching. Rounds: 2. Phone-checked.
- [x] Batch 20 (70-73): remembered Markdown, long-text clip, running-steps collapse, usage line. Rounds: 1. Phone-checked.
- [x] Batch 21 (74, 94-96 and fixes): notices banner, process output, approval and notification hardening. Rounds: 2. Phone-checked.
- [x] Batch 22 (97, 78): plain connection wording, share into the app. Rounds: 2. Phone-checked.
- [x] Batch 23 (75-77): edit any prompt, Regenerate, Branch from here. Rounds: 1. Phone-checked.
- [x] Batch 24 (79-81): attachments. Rounds: 2. Not tried on a phone (the user rarely uses attachments).
- [x] Batch 25 (84, 85): gateway projects, new chat in a project. Rounds: 2. Not tried on a phone yet.
- [x] Batch 28 (101-103): Watch service crash on a failed offline send, stale Stop error, rotation between layouts. Rounds: 1. Phone-checked.
- [x] Batch 29 (104, 105): re-attach to the running session after a reconnect. Rounds: 2. Phone-checked.
- [x] Batch 26 (91-93 and two fixes): subagent cards, subagent list and Stop, checkpoints sheet. Rounds: 2. Checkpoints sheet seen on the phone ("off on this gateway" message); the Subagents panel and checkpoint list not seen (no delegating chat, checkpoints off).
- [x] Batch 30 (106-109): chat title above the model chip on the phone layout, empty and opening states, `ChatScreen.kt` spacing constants and file split (891 to 568 lines). Rounds: 2. Header and the unchanged look checked on the phone; empty states not seen.
- [x] Batch 27 (86-90 and two fixes): Scheduled jobs (list, Run now, Pause, Resume, create, edit, delete), Insights, Capabilities switches, profile picker, one shared REST helper. Rounds: 2. Insights and the empty Scheduled jobs page seen on the phone; job rows, Capabilities and the profile picker not opened.
- [x] Batch 32 (82, 83 and a fix): agent images inline with a full-screen zoom viewer and Save/Share, linked gateway files in a plain text viewer. Rounds: 2. Route check on WorkLab 0.22.2 done by the user in a signed-in browser (all four routes exist). Chat looks unchanged on the phone; images and the viewers not seen (no chat with a file or picture).
- [x] 68 (session takeover investigation): note 3.7 in `docs/PROTOCOL.md`. Phone test by the user: no takeover, no reaping; a message from the other client does not show live in either direction (gateway behaviour, cause not proven). No app change.

## Backlog (user decision 2026-10-14)
Goal: usable almost like Hermes Desktop, essential usage only. Herald is a feature reference, not a plan to follow. Picked by the user: session tools, send-while-running, notification actions. Order is the planner's; each one gets its own ticket batch after a protocol check.
1. Session list like Desktop (user added 2026-10-14: grouping, ordering, what is shown). Checked in Desktop's source (`apps/desktop/src/store/layout.ts`, `app/chat/sidebar/filter-menu.tsx`, `store/session-dot-state.ts`):
   - Desktop groups by `date` (default; Today / Yesterday / Last week dividers), `project`, `status`, and `profile` (only when showing all profiles). Pinned is its own section on top; cron runs are kept out of recents.
   - Desktop orders by `updated` (default), `created`, `status`, `tokens`, `cost` and `manual` (drag).
   - Status buckets, loudest first: needs-input, working, unread, draft, idle. A status filter uses them.
   - Row details the user can switch on: updated time, preview (both on by default), tokens, cost, PR, profile. There is also an archived list and a project filter.
   - Phone scope (planner pick, essential only): group by date (default), status or project; order by updated (default), created or status; pinned on top; Archived page; status filter; row shows preview and updated time. Left out: tokens, cost, PR, profile, manual drag, card rows.
   - Protocol facts: rows carry `pinned`, `archived`, `unread`, `is_active`, `source`, `message_count`, `last_active`, `cwd`, `git_branch`; `order` (`recent`|`created`), `archived` (`exclude`|`only`|`include`), `exclude_sources`, `min_messages` are query options; there is no grouping parameter, so the phone groups the rows itself. Unchecked: how Desktop derives needs-input, working and draft from a row (the REST row has `is_active`, `unread` and `message_count` only); read `session-dot-state.ts` before the status ticket.
   - (built, tickets 33-36) Pin and archive (with Undo) via `PATCH` (`pinned`, `archived`). Already exist: rename, delete, search.
2. (built, tickets 45-47 and 48, 51-52 fixes) Send-while-running: redirect, queue, retry and edit. Original list: queue, steer, stop and send, undo and retry. PROTOCOL has `prompt.submit` (`queued`), `session.steer`, `session.redirect`, `session.interrupt`, `session.undo`.
3. (built, tickets 49-50; buttons did not show on the Realme, see HANDOFF) Notification actions: approve or answer from the notification. Needs the watch service to answer server requests over the live link.
4. (built, tickets 53-56, user request 2026-10-14) Background processes panel: list, output, Stop, Dismiss.
5. (built, tickets 79-81, not yet tried on a phone) Attachments: paperclip, thumbnails, upload, history shows `[image]` and a paperclip line.
Not built: offline cache, voice, app lock, dictation.
