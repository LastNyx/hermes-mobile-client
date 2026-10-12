# Hermes Gateway Client Protocol (for the Android client)

Extracted from the Hermes source (`hermes-agent` checkout, version observed live: `0.21.5`, release `2026.9.24`).
Every name, field and code below is copied from source. Citations are `path:line` relative to the
`hermes-agent` repo root. Anything not read directly from source is marked **(inferred)**.

The server is `hermes serve` / the dashboard backend (FastAPI + uvicorn). The chat protocol is the
**tui_gateway JSON-RPC** spoken over a WebSocket at `/api/ws`. The Hermes Desktop app (Electron,
`apps/desktop`, `apps/shared`) is the reference client and is cited where its behaviour is the contract.

---

## 1. Overview, base URL and prefix handling

- **Base URL**: the user enters something like `http://100.65.118.116:9119` or `https://host/hermes`.
  Every path in this document is relative to the base URL **including any path prefix**.
  The Desktop builds every URL as `${protocol}//${host}${pathname-without-trailing-slash}${path}`
  (`apps/desktop/electron/native-oauth.ts:119-154`, `apps/desktop/electron/connection-config.ts:83-97`).
  Do the same: strip trailing `/` from the base path and append `/api/...`, `/auth/...`.
- **WebSocket URL**: same rule, scheme `https:`→`wss:`, `http:`→`ws:`:
  `ws(s)://<host><prefix>/api/ws?ticket=<ticket>` (`connection-config.ts:91-97`).
- **Reverse-proxy prefix**: the server learns its mount prefix from `X-Forwarded-Prefix` (set by the proxy,
  not by you). It is used for cookie `Path`, redirect `Location`s and `login_url`
  (`hermes_cli/dashboard_auth/prefix.py:61-81`). A client never sends this header **(inferred: it is a
  proxy-to-backend header)**; just keep the prefix in your base URL.
- **Two server auth modes** (`hermes_cli/dashboard_auth/middleware.py:1-9`, `hermes_cli/web_server_chat.py:250-374`):
  - **Gated** (`auth_required: true` in `/api/status`): OAuth/password providers, bearer tokens. This is the
    mode of any remotely reachable gateway and the one this doc targets.
  - **Loopback / `--insecure`** (`auth_required: false`): a static process token. REST uses header
    `X-Hermes-Session-Token: <token>` (or legacy `Authorization: Bearer <token>`), WS uses `?token=<token>`
    (`hermes_cli/web_server.py:387,457-468`). The token is not discoverable remotely; not covered further.
- Discover the mode with the public `GET /api/status` → `auth_required`, `auth_providers`, `auth_flows`
  (`hermes_cli/web_routers/status.py:367-389`). Live sample (this PC's gateway):
  `"auth_required":true,"auth_providers":["basic"],"auth_flows":["cookie","native_pkce"]`.

### Public (no-auth) paths
Exact-match `/api/*` allowlist (`hermes_cli/dashboard_auth/public_paths.py:9-27`):
`/api/health`, `/api/status`, `/api/config/defaults`, `/api/config/schema`, `/api/model/info`,
`/api/dashboard/themes`, `/api/dashboard/plugins`, `/api/cron/fire`.
Prefix allowlist (`middleware.py:39-43`): `/auth/login`, `/auth/callback`, `/auth/native/authorize`,
`/auth/native/token`, `/auth/native/refresh`, `/auth/password-login`, `/auth/logout`, `/login`,
`/api/auth/providers`, `/api/mcp/oauth/callback/`, static assets.
Everything else under `/api/` requires auth.

---

## 2. Authentication (gated mode)

### 2.1 Provider discovery — `GET /api/auth/providers` (public)
`hermes_cli/dashboard_auth/routes.py:203-212`
```json
{"providers":[{"name":"basic","display_name":"Username & Password","supports_password":true}]}
```
- `503 {"detail":"no auth providers registered"}` when none.
- Live gateway: only `basic` (a password provider). `supports_password:true` means the browser leg of the
  native flow lands on the server-rendered `/login` form.

`GET /api/status` → `auth_flows` contains `"native_pkce"` when at least one interactive session provider
exists (`status.py:381-384`). The Desktop uses the native flow iff `native_pkce` is present
(`apps/desktop/electron/native-oauth.ts:76-111`).

### 2.2 Native-app login (RFC 8252: system browser + loopback + PKCE S256)
Server store: `hermes_cli/dashboard_auth/native_flow.py`. Routes: `routes.py:236-335, 567-617`.
Desktop implementation: `apps/desktop/electron/native-oauth.ts`, `native-oauth-login.ts`.

**Step 1: client prepares PKCE + state** (`native-oauth.ts:60-70`)
- `code_verifier` = base64url (no padding) of 32 random bytes.
- `code_challenge` = base64url-nopad( SHA-256( ASCII(code_verifier) ) ). Server check:
  `native_flow.py:72-75` (`_s256`), compared constant-time at `native_flow.py:156`.
- `state` = base64url of 24 random bytes (client CSRF value; echoed back verbatim).

**Step 2: client starts a loopback HTTP listener**
- Must be `http://127.0.0.1:<port>/<path>` or `http://[::1]:<port>/<path>`. **`localhost` is rejected.**
  Desktop binds `127.0.0.1` on an ephemeral port with path `/callback` (`native-oauth-login.ts:173-182`).
- Server validation (`routes.py:242-276`, `_validate_loopback_redirect_uri`): ASCII only, no `\`, no control
  chars/space, scheme exactly `http`, no `#fragment`, no userinfo, host must be `127.0.0.1` or `::1`,
  netloc must be canonical (`127.0.0.1:PORT` / `[::1]:PORT`), empty path normalised to `/`.
- Errors (all HTTP 400, JSON `{"detail": "..."}`, FastAPI `HTTPException`):
  `redirect_uri required` · `native redirect_uri must use a canonical loopback URL` ·
  `native redirect_uri must be http:// on the loopback interface` · `native redirect_uri must not contain a fragment` ·
  `native redirect_uri must not contain userinfo` ·
  `native redirect_uri host must be a loopback IP literal (127.0.0.1 / ::1)` ·
  `native redirect_uri must use a canonical loopback authority`.
- **Android note (inferred)**: an Android app can run such a listener (e.g. a socket on 127.0.0.1) while the
  user is in a Custom Tab / browser; Android custom-scheme or https App-Link redirect URIs are NOT accepted
  by this server.

**Step 3: open the system browser at**
```
GET {base}/auth/native/authorize?code_challenge=<cc>&code_challenge_method=S256
    &redirect_uri=<urlencoded loopback uri>&state=<state>[&provider=<name>]
```
(`routes.py:289-335`; Desktop builder `native-oauth.ts:119-138`)
- `code_challenge_method` must be `S256` (case-insensitive) else 400 `code_challenge_method must be S256`;
  missing challenge → 400 `code_challenge required`.
- `provider` optional: omitted and exactly one session provider → auto-selected; several → an HTML chooser
  page; unknown → 404 `Unknown provider: '<x>'`; token-only provider → 400 `Provider does not support native login: '<x>'`.
- Store full / per-IP cap (8 pending per IP, 256 global) → 503 with
  `native-flow authorization store at capacity` or `too many pending native authorizations from this address`
  (`native_flow.py:30-33,101-119`).
- On success the server stores a pending entry (TTL **600 s**, `native_flow.py:28`) keyed by an opaque
  `broker_state` carried in its own `hermes_session_pkce` cookie (browser-side only), then:
  - password provider (e.g. `basic`): `302 → {prefix}/login` (the HTML login form);
  - OAuth provider: `302 → IdP authorize URL`, which returns to `/auth/callback`.

**Step 4: user signs in inside the browser** (no client involvement)
- The `/login` form JS POSTs `{prefix}/auth/password-login` with JSON
  `{"provider":"basic","username":"…","password":"…","next":""}` (`login_page.py:404-432`, `routes.py:439-493`).
  Responses: `200 {"ok":true,"next":"<url>"}` (then the page navigates to `next`); `401 Invalid credentials`;
  `404 Unknown provider`; `429 Too many login attempts. Try again shortly.` (10 attempts / 60 s per IP,
  `routes.py:386-388`); `503 Provider unreachable: …`; `400 This native sign-in was started for a different provider; …`.
- In the native flow `next` is the loopback redirect, so the browser is sent to your listener.
- **Do not** POST `/auth/password-login` yourself from the app: it only completes a native flow when the
  browser's `hermes_session_pkce` cookie is present, and without it sets cookies instead of minting a code
  (`routes.py:467-493`). Collecting passwords in-app also defeats the design. **(inferred recommendation)**

**Step 5: browser hits your loopback listener**
```
GET http://127.0.0.1:<port>/callback?code=<gw_code>&state=<your state>
```
(`routes.py:119-133`: `redirect_uri + ("&" if "?" in redirect_uri else "?") + urlencode({code, state})`.)
- Verify `state` equals what you sent; reject otherwise (`native-oauth.ts:162-187`). An `error`/
  `error_description` query pair is also handled by the Desktop parser.
- `gw_code` TTL is **120 s** and single-use (`native_flow.py:29,129-158`).
- If the pending entry expired before the user finished: the browser shows 400
  `Native login expired or unknown; restart sign-in.` (`routes.py:54,126-129`) and your listener never fires.
  Use your own timeout (≥ 600 s) **(inferred)**.
- Respond to the browser with a small HTML "you can close this tab" page **(inferred; Desktop does similar)**.

**Step 6: redeem the code** — `POST {base}/auth/native/token` (public) (`routes.py:569-586`)
```json
{"code":"<gw_code>","code_verifier":"<code_verifier>"}
```
200 response = `_bearer_payload` (`routes.py:111-116`):
```json
{"access_token":"…","refresh_token":"…","token_type":"Bearer",
 "expires_at":1760000000,"provider":"basic","user_id":"…"}
```
- `expires_at` = unix seconds of the access token's `exp` (`base.py:18`).
- Any failure (unknown/expired/replayed code, wrong verifier) → **400** `{"detail":"Invalid or expired authorization code."}`.
  The code is consumed even on a wrong verifier (`native_flow.py:145-158`) → restart the flow.
- No cookies are set on this path.
- `basic` provider lifetimes (`plugins/dashboard_auth/basic/__init__.py:31-33,173-177`): access token
  default **12 h** (`session_ttl_seconds` / `HERMES_DASHBOARD_BASIC_AUTH_TTL_SECONDS`), refresh token **30 days**.
  Other providers (e.g. Nous Portal) may use ~15 min ATs and rotating, reuse-detected RTs
  (`apps/desktop/electron/connection-config.ts:26-36`).

### 2.3 Authenticating REST requests
- Header: `Authorization: Bearer <access_token>` (scheme case-insensitive; `request_utils.py:29-34`).
- The gate verifies the bearer against the session providers; **the gate never refreshes a bearer for you**
  (`middleware.py:120-132,169-182`). Refresh is the client's job via `/auth/native/refresh`.
- **401 body** (all `/api/*` routes, `middleware.py:72-87`):
  ```json
  {"error":"session_expired","detail":"Unauthorized","reason":"invalid_or_expired_session","login_url":"/login"}
  ```
  - `error` is `"session_expired"` when `reason == "invalid_or_expired_session"` (what a presented-but-invalid
    bearer always gets, `middleware.py:182`), else `"unauthenticated"` (e.g. `reason:"no_cookie"` when no
    credential at all). Live sample: `{"error":"unauthenticated","detail":"Unauthorized","reason":"no_cookie","login_url":"/login"}`.
  - `login_url` is for browsers; a native client ignores it.
- **503 body** `{"detail":"Auth provider '<name>' unreachable"}` (`request_utils.py:52-54`): transient IdP /
  store outage. **Do not log out, do not refresh; retry with backoff.**
- Route-level 401 from handlers (`routes.py:517-521`) is plain `{"detail":"Unauthorized"}`.
- Token-route 401 (`token_auth.py:96`): `{"error":"unauthenticated","detail":"Unauthorized"}` (service tokens; not used by a mobile app).

**When to refresh** (Desktop rules, `native-oauth.ts:237-253`, `oauth-rest-request.ts:94-186`):
1. Proactively: refresh when `now >= expires_at - 60` (also when `expires_at` unknown/0).
2. Reactively: on a 401 whose JSON body has `error ∈ {"unauthenticated","session_expired"}` and a non-empty
   `reason`, force ONE refresh and retry **only if the request is idempotent (GET/HEAD) or replay-safe
   (`POST /api/auth/ws-ticket`)**. Never blindly replay a non-idempotent POST. A 403 is never a refresh trigger.
3. Serialize refreshes: one in flight; concurrent callers await it. The server coalesces identical-RT
   refreshes (30 s success cache, `refresh_singleflight.py:25-30`) but rotating-RT providers revoke the whole
   session on RT reuse, so never send an old RT after you received a rotated one.

### 2.4 Refresh — `POST {base}/auth/native/refresh` (public) (`routes.py:589-617`)
```json
{"refresh_token":"<rt>","provider":"basic"}
```
(`provider` optional hint, default `""`.)
- **200** → same `_bearer_payload` as the token endpoint. **Always replace BOTH stored tokens** (RT may rotate).
- **401** `{"error":"session_expired","detail":"Refresh token expired or invalid; start a new sign-in."}`
  → every provider rejected the RT: wipe tokens, run the native login again.
- **503** `{"detail":"Auth provider '<name>' unreachable"}` → nothing rotated, keep tokens, retry later.
- **400** `{"detail":"refresh_token required"}` for an empty token.

### 2.5 Identity — `GET /api/auth/me` (auth required) (`routes.py:524-553`)
```json
{"user_id":"…","email":"…","display_name":"…","org_id":"","provider":"basic","expires_at":1760000000}
```
Loopback mode returns `{"user_id":"local","email":"","display_name":"Local","org_id":"","provider":"loopback","expires_at":0}`.
401 `{"detail":"Unauthorized"}` (or the gate envelope above).

### 2.6 Logout — `POST {base}/auth/logout` (`routes.py:496-512`)
Cookie-oriented: reads the RT from **cookies** only, best-effort revokes it on every provider, clears cookies,
answers `302 → {prefix}/login`. A bearer-only native client has nothing to send; logging out = delete the
stored tokens locally. (`basic` tokens are stateless; revoke is a no-op, `basic/__init__.py:166`.)
Optionally POST `/auth/logout` and ignore the 302 **(inferred)**.

### 2.7 Cookies (for reference only; a native client should not use them)
`hermes_cli/dashboard_auth/cookies.py:1-60`: `hermes_session_at`, `hermes_session_rt`,
`hermes_session_provider`, `hermes_session_pkce`, `hermes_sso_attempt`, with `__Host-` (HTTPS, Path=/) or
`__Secure-` (HTTPS behind prefix) name prefixes, HttpOnly, SameSite=Lax.

---

## 3. WebSocket `/api/ws`

### 3.1 Credentials for the upgrade (gated mode)
Checked before accept by `_ws_auth_reason` (`hermes_cli/web_server_chat.py:250-367`), in this order:
1. `?internal=` (server-spawned children only; ignore).
2. **Ticket via subprotocol**: `Sec-WebSocket-Protocol: hermes-gateway-v1, hermes-gateway-ticket.<ticket>`
   (exactly one ticket protocol AND `hermes-gateway-v1` must be present, else rejected; the server selects
   `hermes-gateway-v1` on accept) (`web_server_chat.py:207-222,299-316`).
3. **Ticket via query**: `?ticket=<ticket>` (what the Desktop uses).
4. **Bearer via query**: `?token=<access_token>` — the user's provider access token is accepted directly
   (verified by the same `verify_session` seam as REST; `web_server_chat.py:322-365`). The legacy
   process `_SESSION_TOKEN` is rejected in gated mode.

**`Authorization: Bearer` on the upgrade request is NOT read** by the WS gate (only query params and
`Sec-WebSocket-Protocol` are consulted). Use a ticket (recommended: short-lived, nothing long-lived in URLs or
proxy logs) or `?token=`.

**Mint a ticket** — `POST {base}/api/auth/ws-ticket` with `Authorization: Bearer <at>`, empty body
(`routes.py:556-564`, `ws_tickets.py`):
```json
{"ticket":"<base64url 32 bytes>","ttl_seconds":30}
```
- Single-use, 30 s TTL: mint immediately before every connect/reconnect.
- 401 → refresh token and retry once (replay-safe, `oauth-rest-request.ts:129-186`).

### 3.2 Close codes and HTTP-level refusals
Pre-accept gate for `/api/ws` (`hermes_cli/web_routers/chat_ws.py:161-173,701-719`):

| Code | When | Client action |
|---|---|---|
| `4401` | credential rejected (`no_credential`, `ticket_invalid` (missing/expired/used), `token_invalid`, `token_unavailable` (IdP outage)) | Mint a fresh ticket (refresh AT if needed) and reconnect; if ticket mint itself 401s after refresh → re-login. |
| `4403` | embedded chat disabled on server, or Host/Origin mismatch (DNS-rebinding guard, `web_server_chat.py:167-194`) | Do not loop; show config error. Don't send a browser-style `Origin` that differs from the host (inferred: OkHttp sends no Origin by default, which passes). |
| `1011` | server closed a live socket: send deadline exceeded (peer not draining for 30 s) or fan-out overflow (`tui_gateway/ws.py:206-214,232-249`) | Reconnect + replay (§3.6). |
| `1000`/other | normal close / network loss | Reconnect with backoff + replay. |

The reason string for 4401 is `auth: <reason>` on the `/api/console`/`/api/pty` gates (`chat_ws.py:144`);
`/api/ws` closes with the bare code (`chat_ws.py:168`).

### 3.3 Framing
- One JSON-RPC 2.0 object per WebSocket **text** message, both directions. The server
  `json.dumps` each frame individually (`tui_gateway/transport.py:83-95`) and strips/ignores blank inbound
  messages (`ws.py:408-410`). Do not batch multiple objects per message (no JSON-RPC batch arrays).
- Lone UTF-16 surrogates are replaced with U+FFFD server-side (`ws.py:48-57`).
- Inbound parse error → `{"jsonrpc":"2.0","error":{"code":-32700,"message":"parse error"},"id":null}` (`ws.py:412-419`).
- Three frame kinds arrive from the server:
  1. **Response**: `{"jsonrpc":"2.0","id":<your id>,"result":{…}}` or `{"jsonrpc":"2.0","id":…,"error":{"code":int,"message":str[,"data":…]}}` (`server.py:881-887`).
  2. **Event notification**: `{"jsonrpc":"2.0","method":"event","params":{"type":"<name>","session_id":"<sid>","payload":{…},"seq":<int>}}`
     (`server.py:701-704`; `payload` omitted for payload-less events; `seq` added for events with a non-empty
     `session_id`, `event_replay.py:67-91`).
  3. **Server→client request**: `{"jsonrpc":"2.0","id":"srq-<12 hex>","method":"<approval|clarify|…>","params":{"session_id":"…",…}}`
     (`tui_gateway/server_requests.py:82-84`). You must answer with a response frame carrying the same `id`.
- Client request ids: use integers or strings that never start with `srq-` (Desktop uses `"r1","r2",…`;
  `json-rpc-gateway.ts:161`). Server ids are strings `srq-…` (`server_requests.py:9-10`).
- Requests are dispatched one at a time in arrival order per connection, but "long" handlers
  (`session.resume`, `session.list`, `slash.exec`, `model.options`, `command.dispatch`, … full list
  `tui_gateway/server.py:181-199`) run on a pool and answer later — **responses may arrive out of order; match by id**.

### 3.4 Connection handshake
1. Server accepts and immediately sends `gateway.ready` (`ws.py:365-370`):
   ```json
   {"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{
     "skin":{…},"change_events":true,"heartbeat":true,"replay_epoch":"<32 hex>"}}}
   ```
   (no `session_id`, no `seq`). `skin` = theme object (`contracts/events.py:35-49`), safe to ignore.
2. Client MUST send once per connection, after `gateway.ready`
   (`apps/shared/src/json-rpc-channel.ts:489-518`, contract `tui_gateway/contracts/liveness.py:29-48`):
   ```json
   {"jsonrpc":"2.0","id":"r1","method":"client.capabilities","params":{"server_requests":true}}
   ```
   → `{"server_requests":["clarify","approval","sudo","secret",…],"declines_not_shown":true}`.
   **Without this, every approval/clarify/sudo/secret request fails fast for your connection** — approvals
   are withdrawn ("the attached client cannot answer approval requests (update the Hermes app)") and clarify
   returns undelivered (`server_requests.py:30-34,149-154`, `server.py:833-842`).
3. Optional: `gateway.capabilities` → `{"per_session_exclusive_submit":bool}`; `ping` → `{"pong":true}`.

### 3.5 Keepalive
- Only when `gateway.ready.payload.heartbeat === true`: every **15 s** send
  `{"jsonrpc":"2.0","id":"heartbeat-<n>","method":"gateway.ping","params":{}}` → `{"jsonrpc":"2.0","result":{"ok":true},"id":"heartbeat-<n>"}`.
  `gateway.ping` is answered directly on the read loop even while handlers are busy (`ws.py:420-424`).
- Liveness = **any** inbound frame (streamed deltas count). If a ping was sent and nothing at all arrived for
  **45 s**, drop the socket and reconnect (`json-rpc-channel.ts:154-155,526-572`, `json-rpc-gateway.ts:177-182`).
- Connect timeout 15 s (`json-rpc-gateway.ts:58`). Default RPC timeout 120 s; `approval.respond` 300 s
  (`json-rpc-gateway.ts:40-49`).

### 3.6 Reconnect and replay
- **Backoff** (`apps/shared/src/reconnect-backoff.ts`): full jitter, delay = random(0, min(15000, 300·2^attempt)) ms;
  reset the attempt counter only after a socket stayed open ≥ 5 s.
- **Session survival**: on disconnect the server detaches your sessions and parks them; an orphaned session is
  interrupted/reaped after a grace of **20 s** (`HERMES_TUI_WS_ORPHAN_REAP_GRACE_S` /
  `dashboard.ws_orphan_reap_grace_s`), but a still-active running turn is only interrupted after **600 s** of
  no activity (`tui_gateway/server.py:131-154`). Reattach quickly. Any session-scoped RPC on an unknown runtime
  id returns error **4001 `session not found`** → call `session.resume` with the **stored** id
  (`server.py:1229-1245`).
- **Event replay** (`tui_gateway/event_replay.py`, `methods_session.py:2334-2348`, client `json-rpc-gateway.ts:422-698`):
  - Every session event carries `params.seq` (monotonic per `session_id`, per process). Keep `lastSeenSeq[sid]`.
  - Ring per session: 512 events / 4 MiB; 64 sessions; 64 MiB process total.
  - `gateway.ready.payload.replay_epoch` identifies the server process. If it differs from the epoch you held
    → backend restarted → **clear all watermarks**, re-fetch state via `session.resume`/REST.
  - After reconnect (same epoch), for each session you were watching:
    `session.events.since {"session_id":sid,"last_seen":lastSeenSeq[sid]}` →
    `{"events":[<params objects>],"latest_seq":int,"truncated":bool,"count":int,"epoch":str,"open_requests":[…]}`.
    Each element of `events` is the event's `params` object (`{type, session_id, payload, seq}`), not the envelope.
    - `truncated:true` → there is a gap: do not apply; set watermark to `latest_seq` and reload the transcript
      (`session.resume` or `GET /api/sessions/{id}/messages`).
    - Otherwise dispatch events with `seq > lastSeen` in order. Hold live frames for that sid that arrive while
      the replay call is in flight, then flush them seq-gated (drop `seq <= watermark`).
  - `open_requests` (also on `session.resume`/`session.activate`): `[{"id":"srq-…","method":"approval","params":{…}}]` —
    re-show these prompts exactly as if just received (`server_requests.py:15-18,86-92`).

---

## 4. JSON-RPC methods (client → server)

Common rules (`tui_gateway/contracts/base.py:11-20`, `rpc_dispatch.py`):
- `params` must be an object; **unknown keys are rejected with error 4000** (params models are `extra="forbid"`).
- Most session methods accept optional `profile: string` (named Hermes profile); omit for default.
- `session_id` in session methods = **runtime** id returned by `session.create`/`session.resume`, except where
  noted as STORED id (the DB key, = `stored_session_id` / REST `id`).

### 4.1 Error codes
| Code | Meaning | Source |
|---|---|---|
| -32700 | parse error | `ws.py:417` |
| -32600 | invalid request (not object / no method) | `server.py:905-913` |
| -32601 | unknown method (version skew) | `rpc_dispatch.py:22-24` |
| -32602 | invalid params (not an object; bad `last_seen`) | `server.py:913`, `methods_session.py:2341` |
| -32603 | internal error (dispatch crash / serialization) | `ws.py:333`, `transport.py:93` |
| -32000 | handler error: … (pool handler raised) | `rpc_dispatch.py:79` |
| 4000 | params failed contract validation (unknown/mistyped key) | `rpc_dispatch.py:28-31` |
| 4001 | session not found (stale runtime id → `session.resume` the stored id); also other "not found/no session" | `server.py:1245` |
| 4002 | bad value (`text is required`, `unknown reasoning value: …`, `model value required`, `unknown config key: …`) | `methods_config_set.py`, `methods_session_interrupt.py:106` |
| 4006 | `request_id required` | `methods_prompt.py:1197` |
| 4009 | `session busy` (truncating submit while running) | `methods_prompt.py:765` |
| 4010 | agent does not support steer/redirect | `methods_session_interrupt.py:127` |
| 4064 | profile unavailable | `rpc_dispatch.py:35-36` |
| 4090 | active-session limit reached (`data.reason`) | `methods_prompt.py:719-723` |
| 4404 | (client→server answer) "no window shows this session" decline | `server_requests.py:110-114` |
| 5000 | steer/redirect failed: … | `methods_session_interrupt.py:88` |
| 5019 | compute-host interrupt failed | `methods_session_interrupt.py:63` |
| 5032 | agent still starting / agent init failed / build timed out | `server.py:921-925,962-967` |
| 5035 | `backend is retiring; reconnect to continue` | `rpc_dispatch.py:13,68` |

### 4.2 Core methods
Contracts: `tui_gateway/contracts/{sessions,prompt_voice,liveness,tools_commands,config_free_tier_control,profiles_vault_complete_foreign_subagents}.py`.

| Method | Params (types; `?` = optional) | Result | Use |
|---|---|---|---|
| `client.capabilities` | `server_requests: bool` | `{server_requests: string[], declines_not_shown: bool}` | Once per connection (§3.4). |
| `gateway.ping` | `{}` | `{ok: true}` | Heartbeat (§3.5). |
| `ping` | `{}` | `{pong: true}` | Liveness probe. |
| `session.list` | `profile?, title?: str, limit?: int, include_hidden?: bool=false` | `{sessions: SessionListRow[]}` | Stored sessions, newest first. |
| `session.most_recent` | `profile?` | `{session_id: str\|null, title?, started_at?, source?}` | |
| `session.active_list` | `profile?, current_session_id?` | `{sessions: SessionActiveItem[]}` | Live sessions in this process. |
| `session.create` | `profile?, cols?: int, source?: str, cwd?: str, cwd_explicit?: bool, messages?: SeedMessage[], parent_session_id?, title?, model?, provider?, reasoning_effort?, fast?: bool, service_tier?, close_on_disconnect: bool=false, hidden: bool=false, room_plumbing: bool=false, follow_profile_config: bool=false, idempotency_key?: str` | `{session_id, stored_session_id, message_count, messages: TranscriptMessage[], info: SessionLiveInfo}` | New chat. Agent builds after reply; DB row appears on first prompt. Use `idempotency_key` for safe retries. |
| `session.resume` | `session_id` (**STORED** id or exact title), `profile?, cols?, source?, lazy=false, defer_history=false, omit_messages=false, eager_build=false, close_on_disconnect=false, inline_images=true` | `LiveSessionSnapshot` (§4.4) | Open an existing chat; reply's `session_id` is the new runtime id. Mobile: `inline_images:false` to avoid base64 image blobs. |
| `session.activate` | `session_id` (runtime), `profile?, cols?, omit_messages=false` | `LiveSessionSnapshot` | Re-attach to an already-live session. |
| `session.close` | `session_id` | `{closed: bool, messages: string[]}` | Tear down live session (stored row stays). |
| `session.history` | `session_id` | `{count, messages: TranscriptMessage[]}` | Durable transcript. |
| `session.title` | `session_id, title?` | `{title, session_key?, pending?}` | Read (omit `title`) or set. |
| `session.delete` | `session_id` (STORED) | `{deleted: str}` | Refused while live here. |
| `session.archive` | `session_id?` or `session_key?`, `archived=true, profile?` | `{archived, session_key}` | |
| `session.usage` | `session_id` | `Usage + {credits_lines?: string[]}` | Tokens/cost. |
| `session.status` | `session_id` | `{output: str}` | Rendered `/status`. |
| `session.events.since` | `session_id, last_seen?: int` | §3.6 | Replay. |
| `prompt.submit` | `session_id, text: string, display_kind?: "hidden", queued?: bool, surface?, title_preview?, interrupted?, voice_context?, voice_turn?, truncate_before_row_id?: int, truncate_before_message_id?, truncate_before_user_ordinal?, confirm_truncate?, confirm_empty_truncate?, rebind_survivor_row_ids?: int[]` | `{status: "streaming"\|"queued"\|"steered"\|"redirected", user_row_id?: int, survivor_user_row_ids?, survivor_row_id_map?, turn_isolation?, voice_stopped?}` | Send a user turn. Idle → `streaming`. Busy → handled per server `display.busy_input_mode` (`interrupt` default = redirect or interrupt+queue; `queue`; `steer`) (`session_auto_continue.py:404-458`). `queued:true` forces queue. |
| `session.steer` | `session_id, text: str` | `{status: "queued"\|"redirected"\|"rejected", text}` | Inject guidance into the running turn without interrupting. `rejected` when idle → send as a normal `prompt.submit` instead (`methods_session_interrupt.py:100-143`). Accepted status is `"queued"`. |
| `session.redirect` | `session_id, text` | `{status: "redirected"\|"queued"\|"rejected", text}` | Redirect the active turn. |
| `session.interrupt` | `session_id, expected_hosted_task_id?` | `{status: "interrupted"\|"not_interrupted", interrupted?, turn_isolation?}` | Stop. Turn then ends with `message.complete` (`status:"interrupted"`) **(inferred from TurnStatus)**; pending approvals get `approval.cancelled`. |
| `session.undo` | `session_id, intent?: "retry"\|"undo"` | `{removed: int}` | Drop last user turn (idle only). |
| `session.compress` | `session_id, focus_topic?` | see contract | Manual compaction. |
| `approval.respond` | `session_id, choice?: "once"\|"session"\|"always"\|"deny" (default "deny"), all?: bool, request_id?: str` | `{resolved: int}` | Answer an approval by RPC (alternative to answering the server request). Idempotent; Desktop timeout 300 s and re-sends once on timeout (`apps/desktop/src/store/prompts.ts:377-405`). |
| `approval.pending` | `session_id` | `{approvals: PendingApproval[]}` | Replay pending approvals. |
| `approval.received` | `session_id, request_id: str` | `{acknowledged: bool}` | Tell server the card is visible (starts its timeout clock). |
| `clarify.lock` | `request_id, question_id, answer: any, profile?` | `{status: "ok"\|"expired", remaining?: string[]}` | Lock one answer of a multi-question clarify; last lock resolves it. |
| `request.answer` | `id: str, result: object, profile?` | `{status: "ok"\|"expired"}` | Answer an open server request by RPC (e.g. from `approval.pending`/`open_requests` without the original frame). |
| `model.options` | `profile?, session_id?, explicit_only=false, include_unconfigured=false, refresh=false` | `{providers: ModelOptionProvider[], model: str, provider: str}` | Model picker inventory. |
| `config.get` | `key: str, session_id?, cwd?, profile?` | `ConfigGetResult` | `key:"reasoning"` → `{value, display}`; `key:"provider"` → `{model, provider, providers[]}`. |
| `config.set` | `key: str, value: any, session_id?, scope?: "session"\|"global"\|"once", confirm_expensive_model=false, profile?` | `{key, value, warning?, confirm_required?, confirm_message?, scope?, deferred?, info?: SessionLiveInfo, …}` | Set model / reasoning / yolo / fast etc. (§4.3). |
| `commands.catalog` | `session_id?, profile?` | `{pairs: [name, desc][], sub: {cmd: string[]}, canon: {alias: canonical}, commands: {name: {argument_mode?: "options"\|"text"\|"mixed", desktop?, desktop_subcommands?}}, categories: [{name, pairs}], skills: {name: {usage, origin}}, skill_count, warning}` | List slash commands + skills. |
| `complete.slash` | `text?, session_id?, profile?` | `{items: [{text, display, meta, kind?}], replace_from?: int}` | Autocomplete for a `/…` token. |
| `slash.exec` | `session_id, command: str` (without leading `/`, e.g. `"status"`, `"goal pause"`) | `{output?, warning?, type?, target?, message?, notice?, display?, name?, status?}` | Execute a slash command. If `type` present, treat like `command.dispatch` result. |
| `command.dispatch` | `name: str, arg?: str, session_id?, profile?` | `{type: "exec"\|"alias"\|"plugin"\|"send"\|"skill"\|"prefill", output?, target?, message?, notice?, display?, name?, status?}` | Fallback when `slash.exec` errors (Desktop order, `use-prompt-actions/slash.ts:420-470`). `exec`/`plugin`: show `output`; `alias`: re-run `target`; `send`/`skill`: submit `message` via `prompt.submit` (render `display`, never `message`); `prefill`: put `message` in the composer **(inferred from contract doc + Desktop)**. |
| `image.attach_bytes` | `session_id, content_base64?\|data?, filename?, ext?` | `{attached, path?, count?, name?, width?, height?, token_estimate?, …}` | Upload an image for the next turn (remote clients). |
| `file.attach` | `session_id, path?, data_url?, name?` | `{attached, name, path, ref_path, ref_text, uploaded}` | Non-image file; insert `ref_text` into prompt **(inferred)**. |

### 4.3 Model and reasoning (exact Desktop usage)
- **Set model** (`apps/desktop/src/app/session/hooks/use-model-controls.ts:336-344`):
  `config.set {"session_id":sid,"key":"model","value":"<model> --provider <provider>[ --session]"}`.
  Flags parsed by `parse_model_switch_args` (`hermes_cli/model_switch.py:592-617`): `--provider X`, `--session`,
  `--global`, `--once`, `--reasoning <level>`. Without `--session` the Desktop's primary chat also persists the
  default; secondary chats use `--session`.
  - Result `confirm_required:true` + `confirm_message` → ask user, resend with `confirm_expensive_model:true`.
  - `deferred:true` → switch applies at next turn; wait for `session.info`.
  - Model switch requires a live session: `4001 config.set model requires a live session…`.
- **Set reasoning effort** (`apps/desktop/src/lib/reasoning-slash.ts`, server `methods_config_set.py:307-345`):
  `config.set {"session_id":sid,"key":"reasoning","value":"high"[,"scope":"session"|"global"]}`.
  Levels: `minimal, low, medium, high, xhigh, max, ultra` (`hermes_constants.py:996`), plus `none|false|disabled`
  (= off). Display words also accepted: `show|on`, `hide|off`, `full|all`, `clamp|collapse|short`.
  With a session and no `scope:"global"`, the effort is session-scoped. Unknown → `4002 unknown reasoning value: …`.
  Read current: `config.get {"key":"reasoning","session_id":sid}` → `{value, display}`.
- Current model/effort for a session: `SessionLiveInfo.model`, `.provider`, `.reasoning_effort` (from
  `session.create`/`resume` `info` and every `session.info` event).

### 4.4 Shared result shapes (`tui_gateway/contracts/common.py`, `sessions.py`)
`SessionListRow`: `{id, resolved_id?, title="", preview="", started_at: float=0, message_count=0, live_message_count?, source="", _lineage_root_id?}`.

`SessionActiveItem`: `{current, id, last_active, message_count, model, preview, session_key, started_at, status: "idle"|"starting"|"waiting"|"working"|"streaming"|"resuming", title}`.

`LiveSessionSnapshot` (resume/activate): `{session_id, message_count, messages: TranscriptMessage[], info: SessionLiveInfo, stored_session_id?, resumed?, session_key?, messages_omitted?, hydrating?, running?, turn_started_at?, started_at?, status?, inflight?: {assistant, streaming, user, display_kind?, error?, status?, …}, queued?: {user}, pending_approval?: PendingApproval, open_requests?: [{id, method, params}], todo_state?: {todos, revision}, auto_continue?: {attempt, interrupted_at}, pending_connection?}`.
Use `inflight` to rebuild a turn that was streaming when you reconnected.

`TranscriptMessage`: `{role, text?, content?, tool_call_id?, timestamp?, row_id?: int, display_kind?, display_metadata?, name?, context?, args?, labels?, reasoning?}` + extra keys.

`SessionLiveInfo` (selected): `{model, provider, reasoning_effort, reasoning_effort_wire, service_tier, fast, yolo, approval_mode="manual", tools: {toolset: string[]}, skills, cwd, branch?, project?, running, turn_started_at?, title, stored_session_id, version, usage?: Usage, profile_name?, mcp_servers: [], credential_warning?, lazy?}` (open model; more keys may appear).

`Usage`: `{model, input, output, reasoning, prompt, completion, total, calls, compressions?, context_used?, context_max?, context_percent?, context_source?, context_estimated?, cache_hit_pct?, cache_read?, cache_write?, avg_latency_s?, avg_tps?, active_subagents?, cost_usd?, cost_status?}`.

`PendingApproval`: `{request_id?, command?, description?, pattern_key?, pattern_keys?, allow_permanent?, allow_session?, smart_denied?, choices?: string[], tool_name?}`.

`ModelOptionProvider` (selected): `{slug, name, models: string[], total_models?, is_current?, authenticated?, auth_type?, featured_models?, capabilities?: {model: {...}}, pricing?, warning?, unavailable_models?}` (`config_free_tier_control.py:536-568`).

---

## 5. Server → client traffic

### 5.1 Server→client requests (must be answered)
Frame: `{"jsonrpc":"2.0","id":"srq-…","method":M,"params":{"session_id":sid,…}}`. Answer:
`{"jsonrpc":"2.0","id":"srq-…","result":{…}}`. For a method you don't implement answer
`{"jsonrpc":"2.0","id":"srq-…","error":{"code":-32601,"message":"no handler for server request: M"}}` so the agent
doesn't wait out its deadline (`json-rpc-channel.ts:407`). Contracts: `tui_gateway/contracts/server_requests.py`.

| Method | Params | Result to send |
|---|---|---|
| `approval` | `request_id: str, command: str (redacted), description: str, choices: ("once"\|"session"\|"always"\|"deny")[], allow_permanent?, allow_session?, smart_denied?, tool_name?, gateway_session_id?` + extra keys | `{"choice":"once"\|"session"\|"always"\|"deny","all"?:bool}` |
| `clarify` | `questions: [{qid, question, choices?: string[]\|null, multi_select: bool}]`, `answers?: {qid: str\|null}` (only on reconnect replay: already-locked answers) | `{"answers":{"<qid>":"<answer>"\|null}}`; a result without `answers` = cancel all. Timeout default 300 s (`server.py:1408-1415`). |
| `sudo` | `command: str` | `{"value":"<password>"}` (`""` = declined) |
| `secret` | `env_var, prompt, metadata?` | `{"value":"…"}` |
| `vault.unlock_prompt` / `vault.save_login` / `vault.code` | see contract | `{"value":"…"}` |
| `setup_choose` | `kind, question, options?, multi_select, preselected?` | `{picked?, label?, said?}` |
| `terminal.read`, `preview.read`, `preview.act`, `window.read`, `tour` | Desktop-window bridges | Mobile: answer error `{"code":4404,"message":"not shown"}` (decline; server settles once all clients decline, `server_requests.py:233-262`) |

Approval choices are computed server-side: `["once"]` + `"session"` (unless smart-denied or `allow_session:false`)
+ `"always"` (unless `allow_permanent:false`) + `"deny"` (`server.py:765-778`). Only offer what `choices` lists.

### 5.2 Event notifications
Frame: `{"jsonrpc":"2.0","method":"event","params":{"type":T,"session_id":sid,"payload":{…},"seq":n}}`.
Contracts: `tui_gateway/contracts/events.py` (cited by line). Unknown event types: ignore.

| Event | Payload | Meaning |
|---|---|---|
| `gateway.ready` | `{skin, change_events: bool, replay_epoch: str, heartbeat?: bool}` | First frame (§3.4). `events.py:51-61` |
| `message.start` | (none) | A turn began streaming. `:113` |
| `message.delta` | `{text: str, rendered?: str, verbose?: bool}` | Assistant text chunk (append `text`). Coalesced ~33 ms server-side (`ws.py:77-83`). `:116-126` |
| `reasoning.delta` | same | Reasoning/thinking chunk. `:127` |
| `thinking.delta` | same | Legacy thinking chunk. `:129` |
| `reasoning.available` | same | Whole reasoning block (non-streaming providers). `:128` |
| `message.interim` | `{text, already_streamed: bool}` | Interim commentary beside tool calls, sealed as its own segment. `:132-140` |
| `tool.generating` | `{name}` | Model is emitting a tool call's arguments. `:302-308` |
| `tool.start` | `{tool_id, name, context?, args?: object, args_text?, preview?, labels?}` | Tool call began. `:266-280` |
| `tool.complete` | `{tool_id, name, args?, duration_s?, result?: any, summary?, result_text?, inline_diff?, todos?, revision?, labels?}` | Tool finished; match by `tool_id`. `:283-299` |
| `tool.output_risk` | `{tool_id, name, risk, findings: string[], redacted}` | Risky tool output. `:311-321` |
| `todo.updated` | `{todos: any[], revision: int}` | Full todo snapshot. `:324-331` |
| `message.complete` | `{text, usage?: Usage, status?: "complete"\|"error"\|"interrupted", reasoning?, warning?, error?, recoverable?, error_surface?: {layer, code, retryable, provider?, model?, resets_at?}, billing?, failure_reason?, rendered?, partial?, response_reused?, persisted_turn?: {row_ids, complete, user_row_id?, final_assistant_row_id?}}` | **Turn ended** (success, error or interrupt). `text` is the final reply; if `response_reused:true` it adds nothing new. `:189-213` |
| `status.update` | `{kind: str, text: str}` | Transient status line (`status`, `lifecycle`, `compacting`, `goal`, `loop`, `heartbeat`, `process`, …). `:216-223` |
| `session.usage` | `{usage: Usage}` | Mid-turn usage tick. `:226-232` |
| `session.info` | `SessionLiveInfo` | Session settings changed (model/reasoning/running…). `:374-375` |
| `session.title` | `{session_id, title}` | Auto-title (`session_id` here is the stored key). `:235-242` |
| `session.resume_progress` | `{phase, status: "loading"\|"complete"\|"failed", message_count?, message?}` | Deferred resume hydration. `:384-393` |
| `session.reclaimed` | `{session_id, stored_session_id, reason}` (`idle_timeout`\|`lru_evict`\|`ws_orphan_reap`) | Live session was reaped; resume by stored id to continue. `:396-404` |
| `approval.cancelled` | `{session_id, stored_session_id, reason, cancelled_count, request_ids: string[]}` | Pending approvals dropped (interrupt/reap) — clear cards. `:407-424` |
| `request.cancel` | `{id, method, reason: "timeout"\|"interrupted"\|"shutdown"\|"resolved"\|"session_closed"}` | Withdraw an open server request (approval/clarify card). `server_requests.py:252-267` |
| `error` | `{message}` | Session-level failure outside a turn (agent init, model switch, resume). `:92-98` |
| `notice` | `{message}` | Informational one-liner. `:101-107` |
| `notification.show` | `{text, level: info\|warn\|error\|success, kind: sticky\|ttl\|agent, ttl_ms?, key?, id?}` | Toast/status notice (replace by `key`). `:337-353` |
| `notification.clear` | `{key}` | Withdraw notice. `:349-354` |
| `background.complete` / `btw.complete` | `{task_id, text, question?}` | Side-agent answers. `:448-457` |
| `subagent.spawn_requested`/`.start`/`.progress`/`.thinking`/`.tool`/`.complete` | `{goal, task_count, task_index, subagent_id?, status?, summary?, text?, tool_name?, …}` | Delegated child agents. `:483-521` |
| `sessions.changed`, `cron.changed`, `platforms.changed`, `projects.changed` | `{}` | Refetch lists (session-less broadcast). `:741-746` |
| `voice.*`, `wake.detected`, `preview.*`, `layout.apply`, `pane.reveal`, `tip.show`, `pet.*`, `moa.*`, `browser.*`, `skin.changed`, `setup.ready`, `free_tier.challenge`, `billing.step_up.verification`, `message.reaction`, `reaction`, `review.summary`, `agent.terminal.output`, `terminal.close`, `session.control.update` | see `events.py` | Desktop/voice features; safe to ignore on mobile. |

**Turn lifecycle summary**: `prompt.submit` reply `{status:"streaming"}` → `message.start` → interleaved
`message.delta` / `reasoning.delta` / `tool.generating` / `tool.start` / `tool.complete` / `message.interim` /
`status.update` / `session.usage` (and possibly an `approval` or `clarify` server request) → `message.complete`.
`SessionLiveInfo.running` and `session.info` events track busy state. Errors inside a turn arrive as
`message.complete` with `status:"error"` + `error`/`error_surface`; errors outside a turn as `error` events or
as JSON-RPC error responses. If agent build is slow, a `notification.show` with `key:"agent-build-slow"` appears
(`server.py:933-971`).

---

## 6. Worked example (one turn with an approval)

`→` client sends, `←` server sends. Ids/values illustrative; names and shapes are real.

```text
# 0. auth (REST)
POST /api/auth/ws-ticket   Authorization: Bearer eyJ…        ← 200 {"ticket":"Q2x…","ttl_seconds":30}
WS   wss://host/hermes/api/ws?ticket=Q2x…

← {"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{"skin":{},"change_events":true,"heartbeat":true,"replay_epoch":"9f2c…"}}}
→ {"jsonrpc":"2.0","id":"r1","method":"client.capabilities","params":{"server_requests":true}}
← {"jsonrpc":"2.0","id":"r1","result":{"server_requests":["clarify","approval","sudo","secret", "…"],"declines_not_shown":true}}

# 1. create a session
→ {"jsonrpc":"2.0","id":"r2","method":"session.create","params":{"idempotency_key":"a1b2"}}
← {"jsonrpc":"2.0","id":"r2","result":{"session_id":"s_7f3","stored_session_id":"20261012_105500_ab12","message_count":0,"messages":[],"info":{"model":"…","provider":"…","reasoning_effort":"medium","running":false,"…":"…"}}}

# 2. submit a prompt
→ {"jsonrpc":"2.0","id":"r3","method":"prompt.submit","params":{"session_id":"s_7f3","text":"delete build/ and rebuild"}}
← {"jsonrpc":"2.0","id":"r3","result":{"status":"streaming","user_row_id":101}}
← {"jsonrpc":"2.0","method":"event","params":{"type":"message.start","session_id":"s_7f3","seq":1}}
← {"jsonrpc":"2.0","method":"event","params":{"type":"message.delta","session_id":"s_7f3","payload":{"text":"I'll clean "},"seq":2}}
← {"jsonrpc":"2.0","method":"event","params":{"type":"message.delta","session_id":"s_7f3","payload":{"text":"the build dir."},"seq":3}}
← {"jsonrpc":"2.0","method":"event","params":{"type":"tool.generating","session_id":"s_7f3","payload":{"name":"terminal"},"seq":4}}
← {"jsonrpc":"2.0","method":"event","params":{"type":"tool.start","session_id":"s_7f3","payload":{"tool_id":"call_1","name":"terminal","args":{"command":"rm -rf build"},"preview":"rm -rf build"},"seq":5}}

# 3. approval request (server -> client JSON-RPC request)
← {"jsonrpc":"2.0","id":"srq-4be1c09a2f13","method":"approval","params":{"session_id":"s_7f3","request_id":"ap_9","command":"rm -rf build","description":"recursive delete","choices":["once","session","always","deny"],"tool_name":"terminal"}}
→ {"jsonrpc":"2.0","id":"r4","method":"approval.received","params":{"session_id":"s_7f3","request_id":"ap_9"}}     (optional)
← {"jsonrpc":"2.0","id":"r4","result":{"acknowledged":true}}

# 4. user taps "Allow once" — answer the request frame itself
→ {"jsonrpc":"2.0","id":"srq-4be1c09a2f13","result":{"choice":"once"}}
#    (equivalent alternative: method approval.respond {"session_id":"s_7f3","choice":"once","request_id":"ap_9"} -> {"resolved":1})

# 5. tool runs, turn completes
← {"jsonrpc":"2.0","method":"event","params":{"type":"tool.complete","session_id":"s_7f3","payload":{"tool_id":"call_1","name":"terminal","duration_s":0.4,"summary":"exit 0","result_text":"…"},"seq":6}}
← {"jsonrpc":"2.0","method":"event","params":{"type":"message.delta","session_id":"s_7f3","payload":{"text":"Done — build/ removed and rebuilt."},"seq":7}}
← {"jsonrpc":"2.0","method":"event","params":{"type":"message.complete","session_id":"s_7f3","payload":{"text":"I'll clean the build dir.\n…Done — build/ removed and rebuilt.","status":"complete","usage":{"model":"…","input":1200,"output":85,"total":1285,"calls":2}},"seq":8}}

# Heartbeat meanwhile (every 15 s)
→ {"jsonrpc":"2.0","id":"heartbeat-1","method":"gateway.ping","params":{}}
← {"jsonrpc":"2.0","result":{"ok":true},"id":"heartbeat-1"}

# Steer while running / stop
→ {"jsonrpc":"2.0","id":"r5","method":"session.steer","params":{"session_id":"s_7f3","text":"use make clean instead"}}
← {"jsonrpc":"2.0","id":"r5","result":{"status":"queued","text":"use make clean instead"}}
→ {"jsonrpc":"2.0","id":"r6","method":"session.interrupt","params":{"session_id":"s_7f3"}}
← {"jsonrpc":"2.0","id":"r6","result":{"status":"interrupted"}}
```
The exact text distribution between `message.delta` and `message.complete.text`, the `tool_id` format and the
`request_id` format above are **(inferred)**; field names are from source.

---

## 7. REST endpoints a mobile client needs

All require `Authorization: Bearer <at>` unless marked public. Errors are FastAPI `{"detail": …}` with the status
shown, plus the gate's 401/503 envelopes (§2.3).

### `GET /api/health` (public)
Minimal liveness probe (`hermes_cli/web_routers/status.py:116`). Shape not needed by clients **(not extracted)**.

### `GET /api/status` (public) — `status.py:466-566`
Selected keys: `version`, `release_date`, `config_version`, `latest_config_version`, `gateway_running`,
`gateway_state`, `gateway_platforms` (`{platform: {state, error_code, error_message, updated_at, …}}`),
`active_agents`, `active_sessions`, `gateway_busy`, `gateway_drainable`, `auth_required`, `auth_providers`,
`auth_flows`, `nous_session_valid`, `install_id?`, `components` (`{gateway, dashboard, storage, platforms}` each
with `status: "ok"|"degraded"`), `overall: "ok"|"degraded"`, `memory`, `disk`, `profiles: string[]`,
`parked_profiles`, `gateway_mode`. Optional query `?profile=`.

### `GET /api/sessions` — `hermes_cli/web_routers/sessions.py:177-271`
Query: `limit` (0–100, default 20), `offset` (default 0), `min_messages` (int), `archived` = `exclude`|`only`|`include`
(default `exclude`), `order` = `created`|`recent` (default `created`; use `recent` for a chat list), `source`,
`sources` (csv), `exclude_sources` (csv; Desktop excludes `cron` for recents), `cwd_prefix`, `full` (bool), `profile`.
Response:
```json
{"sessions":[StoredSessionRow…],"total":123,"limit":20,"offset":0,"storage":{}}
```
`StoredSessionRow` (`contracts/common.py:101-133` + `sessions.py:227-235`): `id, title, preview, source, model,
started_at, ended_at, last_active, message_count, tool_call_count, input_tokens, output_tokens, is_active,
cwd, git_branch, git_repo_root, parent_session_id, pinned: bool, unread?, archived: bool, actual_cost_usd?,
estimated_cost_usd?, _lineage_root_id?, _lineage_ids?, continuation_kind?, profile, is_default_profile`.
`system_prompt`/`model_config` only with `full=1`. Errors: 400 bad `archived`/`order`; 503 store busy/corrupt.
`storage` is `{"<profile>":"corrupt"}` when the DB is unreadable (empty list ≠ no sessions).

### `GET /api/sessions/search?q=…&limit=20` — `sessions.py:284-461`
`limit` clamped 1–100; also `profile`, `source`, `sources`, `exclude_sources`. Empty `q` → `{"results":[]}`.
```json
{"results":[{"session_id":"<lineage tip id — resume this>","id":"…","lineage_root":"…","snippet":"…","role":"user|assistant|null",
  "source":"…","model":"…","session_started":1760000000.0,"last_active":…,"title":"…","started_at":…,"ended_at":…,
  "is_active":false,"message_count":12,"tool_call_count":3,"input_tokens":…,"output_tokens":…,"preview":"…",
  "parent_session_id":null,"profile":"default","is_default_profile":true,"archived":false}]}
```
Matches session ids first, then FTS5 message content (prefix-wildcarded), then titles; deduped by compression lineage.

### `GET /api/sessions/{session_id}` — `sessions.py:562-582`
Full stored row (`StoredSessionRow` superset) + `profile`, `is_default_profile`, `scheduler_owned?`. 404 `{"detail": …}` if unknown.

### `GET /api/sessions/{session_id}/messages` — `sessions.py:723-767`
Query: `limit` (≤500; omitted → latest 500), `offset`, `order` = `oldest`|`latest`, `include_compacted` (bool),
`inline_images` (bool, default true — set `false` on mobile), `profile`.
```json
{"session_id":"<resolved resume id>","profile":"default","messages":[TranscriptMessage…],
 "pagination":{"limit":500,"offset":0,"order":"latest","returned":42}}
```
Includes compression-ancestor messages (matches `session.resume`).

### `PATCH /api/sessions/{session_id}` — `sessions.py:892-922`, body model `hermes_cli/web_models.py:268-275`
Body: `{"title"?: str, "archived"?: bool, "hidden"?: bool, "pinned"?: bool, "unread"?: bool, "profile"?: str}` →
`{"ok":true,"title":"…", "<flag>": bool…}`. 400 if nothing to update or invalid title.

### `DELETE /api/sessions/{session_id}` — `sessions.py:830-850`
→ `{"ok":true}` or `{"ok":true,"already_absent":true}`; 409 if the session is actively being written.

### `GET /api/model/info` (public) — `hermes_cli/web_routers/models.py:90-135`
`{"model","provider","auto_context_length","config_context_length","effective_context_length","capabilities":{…}}` for the configured default model.

### `GET /api/model/options` — `models.py:138-167`
Query `profile`, `refresh`, `include_unconfigured`, `explicit_only`. Same shape as the `model.options` RPC:
`{"providers":[ModelOptionProvider…],"model":"…","provider":"…"}`. 503 `Restart required: …` on code skew.

### `POST /api/model/set` — `models.py:376-…`
Writes the **default** model in config for new sessions (not the live chat; use `config.set key=model` for that).
Body `ModelAssignment` (`hermes_cli/web_models.py:105-123`): `{"scope": "main"|"auxiliary", "provider": str,
"model": str, "task": str = "", "reasoning_effort"?: str|null (auxiliary only), "base_url": str = "",
"api_key": str = "", "confirm_expensive_model": bool = false, "profile"?: str}`. 400 `scope must be 'main' or 'auxiliary'`.
May return `{"ok":false,"confirm_required":true,"confirm_message":"…",…}`.

---

## 8. Client checklist

1. `GET /api/status` → require `auth_required` and `"native_pkce" ∈ auth_flows`.
2. Native PKCE login (§2.2) → store `access_token`, `refresh_token`, `expires_at`, `provider` encrypted.
3. Before each REST call / ticket mint: refresh if `now >= expires_at - 60`; on structured 401 refresh once and
   retry only idempotent calls; on refresh 401 `session_expired` → re-login; on 503 → back off, keep tokens.
4. Mint `/api/auth/ws-ticket` → connect `/api/ws?ticket=` → wait `gateway.ready` → `client.capabilities` →
   start 15 s `gateway.ping` if `heartbeat:true`.
5. Track `seq` per session and `replay_epoch`; on reconnect call `session.events.since` per watched session;
   on `4001` call `session.resume` with the stored id; re-show `open_requests`.
6. Answer every server request (`approval`, `clarify`, `sudo`, `secret`) or reply `-32601` / `4404`.

---

## 9. Not determined from source

- Exact JSON of `GET /api/health` (not read).
- Exact `tool_id` / approval `request_id` string formats (generated in agent/tool code not inspected).
- Whether `message.complete.text` always repeats the full streamed text (contract only says final text;
  `response_reused` marks re-delivery).
- `session.interrupt` → final event ordering (interrupted turns are expected to end with `message.complete`
  `status:"interrupted"` per `TurnStatus`, but the emitter path was not traced).
- Behaviour of non-`basic` OAuth providers' token lifetimes (provider plugins other than `basic` not read).
- No live end-to-end verification was performed (no login, no POST, per instructions); only the public
  `/api/status`, `/api/auth/providers` and the unauthenticated 401 body of `/api/sessions` were checked live.
