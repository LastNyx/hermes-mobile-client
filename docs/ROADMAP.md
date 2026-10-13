# Roadmap (pre-release work, before v0.1.1)

Reference for feature behaviour: [Giton22/herald](https://github.com/Giton22/herald) (`docs/ROADMAP.md`,
`docs/hermes-protocol-research.md`). Read it for how a feature works; do not copy code, and do not add
anything `docs/PROTOCOL.md` does not list. Settled decisions stay in `docs/DESIGN.md`.

Order = build order. Each phase ends with a reviewer pass and a phone check by the user.

## Phase 0: baseline
- [x] Toolchain installed on this PC, `./build.sh test` green on `feature/v1` (22 tests).
- [ ] `05-fix-flaky-livelink-test`: the test failed once on a cold run (race reading the first client frame). Dispatch first.
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
- [ ] GFM rendering: headings, lists, tables, links (web and mail only), inline code.
- [ ] Code blocks with language label and copy button; copy whole reply.
- [ ] Reasoning and tool rows collapsible (check what ticket 06 already did before cutting tickets).
- Needs a Markdown library: `libs.versions.toml` is protected, so the planner adds it with the user's yes.
- Syntax highlighting and TeX math are later, not here.

## Phase 3: slash commands (DESIGN Q3 scope; Herald: Slash commands)
Gap today: suggestions only run when tapped, Enter sends `/cmd` as plain text, and `command.dispatch` types
`send`/`skill`/`prefill`/`alias` show nothing.
- [ ] Autocomplete while typing (`complete.slash`), catalog from `commands.catalog`.
- [ ] Enter on a slash command runs `slash.exec`, falls back to `command.dispatch`.
- [ ] Handle every dispatch type: `exec`/`plugin` show output, `alias` re-runs, `send`/`skill` submit, `prefill` fills the composer.
- [ ] Reasoning level read from the session on open.

## Phase 4: close out
- [ ] Docs worker, drift report.
- [ ] Ask the user: tag `v0.1.1` (includes phases 1 to 3), or keep working first.

## Backlog (not scheduled; the user picks after Phase 3)
Ordered by what Herald shows as the biggest gap.
1. Attachments: photos, files, PDFs; media the agent sends back.
2. Session tools: rename, pin, archive, delete, search.
3. Send-while-running modes (queue, stop and send) and edit/regenerate/branch.
4. Offline cache of recent chats (encrypted).
5. Cron jobs, capabilities (skills/toolsets/MCP), insights.
6. Voice: dictation, then voice chat.
7. Notification actions: approve or answer from the notification.
