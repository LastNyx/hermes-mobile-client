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
| Feature branch | (set when tickets are cut) |
| Last done ticket | none |
| Next ticket | none |
| Notes | Toolchain + skeleton verified on Windows: `./build.sh test` and `assembleDebug` pass. |
