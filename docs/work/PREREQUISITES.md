# Prerequisites for a new machine

What a PC needs to continue this project (Windows with git-bash, or Arch Linux). Nothing here is a
secret; secrets are listed only by name and where they live.

## Always needed

| Need | Why | Arch | Windows |
|---|---|---|---|
| git | clone, push the feature branch | `pacman -S git` | Git for Windows (git-bash) |
| curl, unzip, tar | `scripts/setup-toolchain.sh` downloads the toolchain | `pacman -S curl unzip tar` | ship with git-bash |
| bash | `build.sh` and the scripts are bash | already there | git-bash |
| x64 CPU | the toolchain script fetches x64 builds | | |

JDK 17 and the Android SDK are **not** installed from packages: `scripts/setup-toolchain.sh` puts them in
`~/tools` (Linux) or `C:/home/tools` (Windows) without admin or PATH edits.

```bash
git clone https://github.com/LastNyx/hermes-mobile-client.git && cd hermes-mobile-client
git checkout feature/v1
scripts/setup-toolchain.sh
./build.sh test            # must pass before anything else
```

## To continue the ticket workflow (planner + dev workers)

| Need | Why |
|---|---|
| Hermes Agent with the `orchestrate` skill | the planner role |
| `claude` CLI, logged in | dev, reviewer and docs workers are `claude -p` runs |
| Python 3 | `kit/run-worker.py` |
| `hermes-dev-team` repo cloned next to this one (`../hermes-dev-team`) | provides `kit/run-worker.py`; paths in HANDOFF use `C:/home/project/hermes-dev-team/kit` on Windows, `~/project/hermes-dev-team/kit` on Linux |
| `.team.json` (committed) | test command and worker models |

Dispatch: `python -u <hermes-dev-team>/kit/run-worker.py dev "ticket: .scratch/v1/issues/<NN>-<slug>.md"`

## To publish a release

Releases are built by GitHub Actions (`.github/workflows/release.yml`) when a `v*` tag is pushed, and
published as a GitHub Release of this repo.

- **Nothing to install on the new machine for CI.** The signing key is held in GitHub repo secrets in
  `LastNyx/hermes-mobile-client`: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
  `ANDROID_KEY_PASSWORD`. Publishing uses the workflow's own `GITHUB_TOKEN`.
- Tag from anywhere with push access: `git tag v0.1.0 && git push origin v0.1.0`.
- `gh` (GitHub CLI, `pacman -S github-cli`, then `gh auth login`) is useful for secrets and release checks.
- Tickets and worker notes are in the private repo `LastNyx/hermes-mobile-scratch`; on a new machine run
  `python3 <hermes-dev-team>/kit/scratch.py init --clone https://github.com/LastNyx/hermes-mobile-scratch.git`.

## The release keystore (only for local signed builds)

CI does not need it on your PC. You need it only to build a release-signed APK locally.

- Created with `scripts/make-release-key.sh`, stored **outside git** (Windows: `C:/home/keys/`).
- Files: `hermes-client-release.jks` and `keystore.properties` (passwords inside).
- Certificate SHA-256: `63:36:9F:BF:59:78:A9:0E:91:9E:0B:ED:C1:7A:0D:BC:00:AC:E2:51:9E:19:15:4B:95:4A:96:66:AA:1F:30:B4`.
  Check a copy with `keytool -list -v -keystore hermes-client-release.jks`.
- Copy it between machines over an encrypted channel (not git, not chat). Then, to sign locally, copy
  `keystore.properties` to `android/keystore.properties` (gitignored); `./build.sh` then builds a signed
  release APK instead of the debug one.
- **Keep a backup outside both PCs.** Android only installs an update signed with the same key, so losing it
  means every user must uninstall and reinstall.

## To send the debug APK to Telegram after each ticket

HANDOFF's APK rule uses the Telegram Bot API `sendDocument` with `TELEGRAM_BOT_TOKEN` and the first id in
`TELEGRAM_ALLOWED_USERS`, both read from the Hermes `.env` on that machine (never from the repo). A new machine
needs Hermes installed with its Telegram gateway configured, or those two variables set by hand.

## To install on a phone from the PC (optional)

`adb` comes with the SDK platform-tools that the setup script installs. `./build.sh install` runs
`adb install -r` for a connected phone with USB debugging on.
