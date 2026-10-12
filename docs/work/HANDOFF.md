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
| Feature branch | `feature/v1` (tickets in `.scratch/v1/issues/`, 12 tickets incl. 02b) |
| Last done ticket | 02b (review, 0b8621d) |
| Next ticket | 03 signed-in requests, refresh, logout (in progress) |
| Phone checkpoints | after 03 (sign-in on WorkLab), after 06 (chat), at the end |
| Notes | Toolchain + skeleton verified on Windows: `./build.sh test` and `assembleDebug` pass. |
