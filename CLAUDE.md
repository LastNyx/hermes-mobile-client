# hermes-mobile-client

Android app (Kotlin, Jetpack Compose, OkHttp) that talks **directly** to a Hermes gateway
(`hermes serve`): RFC 8252 native sign-in, bearer tokens for REST, JSON-RPC over `/api/ws`.
No bridge, no server-side component in this repo.

- Design and every settled decision: `docs/DESIGN.md`. Do not reopen a decision; follow it.
- Wire protocol (the only source of truth for endpoints, methods, events): `docs/PROTOCOL.md`.
  Never invent a method or field that is not in it. If the ticket needs one that is missing, FAIL
  with the reason instead of guessing.
- Package `io.github.lastnyx.hermesclient`, under `android/app/src/main/java/io/github/lastnyx/hermesclient/`.
  `data/` = network, auth, storage, protocol (pure Kotlin where possible, so it is JVM-testable).
  `ui/` = Compose screens. Android-only glue (Activity, Service, Keystore) stays thin.
- Tests: JVM unit tests in `android/app/src/test/...`, run with `./build.sh test` from the repo root.
  Use OkHttp `MockWebServer` as the fake gateway (it supports WebSocket upgrades). `org.json` is on
  the test classpath because Android's stub `org.json` is empty on the JVM.
- Dependencies are fixed in `android/gradle/libs.versions.toml`; build files are protected. If a
  ticket truly needs a new library, FAIL with `needs-dependency: <coordinates>`.
- No secrets in logs. Tokens never go into `Log.*`, exceptions, or UI text.
- Kotlin style: small files, no comments that restate code, explain *why* where it is not obvious.
