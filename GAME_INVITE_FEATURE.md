# feature/game-invite — implemented as a starting point

This adds the lobby-initiated "Spiel starten" → 30s ready-check → Game
flow that was missing (see mockups: Dashboard, Dashboard - Running Game).
It's real, wired-up code — not just a sketch — and the frontend build is
green. It has **not** been run against the live backend/DB, so treat it as
a strong starting point to test and refine, not a finished PR.

## The shape of it

One new backend package, `de.thm.codecracker.invite`:

- **`InviteSession`** (package-private) — one round's in-memory state:
  initiator, invited users, per-user ready/declined responses, the 30s
  deadline, the pending Vert.x timer ID, and (once decided) the resulting
  `gameId`. Never persisted — same reasoning as `LobbyRegistry`: this only
  means anything while these WebSocket connections and this process are
  alive. `GameRepository.createGame`'s own Javadoc already says as much:
  *"There is deliberately no separate WAITING state created here: by the
  time this is called, the invite/ready-check flow has already decided
  who is playing."* This is that flow — it hands off to
  `GameService.startGame(...)` exactly like that comment expects, so
  nothing in the Game/DB layer needed to change.

- **`GameInviteService`** — the orchestrator. One `currentSession` field,
  guarded by a lock (routes may run on virtual threads per the project's
  concurrency model, so concurrent "Bereit" clicks are real). Three
  entry points:
  - `startInvite(initiatorUserId)` — only allowed from `NONE`; snapshots
    everyone currently in `LobbyRegistry`, marks the initiator ready
    immediately (starting a game is itself a confirmation), schedules a
    30s `vertx.setTimer`, broadcasts `game-invite-started`.
  - `respond(userId, ready)` — records a response, broadcasts
    `game-invite-status`; if that was the last outstanding response,
    concludes immediately instead of waiting out the rest of the timer.
  - `currentSnapshot()` — REST-facing state for a client that just loaded
    or refreshed the dashboard.
  - Conclusion (`concludeInvite`, private): fewer than 2 ready → clears
    the session, broadcasts `game-invite-cancelled`. Otherwise calls
    `gameService.startGame(readyUserIds)`, broadcasts `game-started`, and
    registers a one-shot EventBus consumer on `"game:" + gameId` to catch
    that specific game's own `"game-ended"` event (published by
    `GameService` already) and then broadcasts a lobby-wide
    `lobby-game-ended` so onlookers' "Spiel starten" button re-enables.

- **`GameInviteController`** — REST only:
  - `GET /api/lobby/invite` → `{"phase": "NONE"}` |
    `{"phase": "INVITE_PENDING", initiator, deadlineAt, players}` |
    `{"phase": "GAME_RUNNING", gameId, playerUserIds}`
  - `POST /api/lobby/invite/start` → 201 + snapshot, or 409 if an
    invite/game is already active or fewer than 2 users are online
  - `POST /api/lobby/invite/respond` `{"ready": true|false}` → 204, or
    403 if the caller wasn't invited, 409 if there's nothing pending

- **`LobbyBroadcaster`** (new, in the existing `lobby` package) — the
  broadcast loop that used to live privately inside
  `LobbyWebSocketController` (for `player-joined`/`player-left`), pulled
  out so `GameInviteService` can reuse it without depending on the
  WebSocket controller. `LobbyWebSocketController` itself is otherwise
  unchanged behaviorally.

No new WebSocket endpoint: every invite-flow event rides the *existing*
`/ws/lobby` socket, alongside `player-joined`/`player-left`:

| type | payload |
|---|---|
| `game-invite-started` | `{initiator: {id,username}, deadlineAt, players: [{id,username,status}]}` |
| `game-invite-status` | `{id, username, status: "ready"\|"declined"}` |
| `game-invite-cancelled` | `{reason: "not-enough-ready"\|"start-failed"}` |
| `game-started` | `{gameId, playerUserIds}` |
| `lobby-game-ended` | `{gameId}` |

## Frontend

- `lobby-invite-api.ts` (new) — `fetchInvitePhase`, `startInvite`,
  `respondToInvite`.
- `lobby-socket.ts` — `LobbyEvent` extended with the five event types
  above; `LobbyUser` is now imported from `lobby-api.ts` instead of being
  redefined.
- `dashboard.ts` — rewritten. Combines the online-players list with the
  invite banner (initiator + Bereit/Ablehnen), the running-game banner,
  and the Spiel-starten button's enabled state, all driven by one `phase`
  value that starts from the REST snapshot and then only ever moves via
  WebSocket events — no polling.
- `dashboard.html` — added the markup for all of the above; kept the
  existing `#online-players-list`/`#lobby-status` ids from the merge.

## What's deliberately still open

- **Round/time for onlookers.** The base requirements say a non-playing
  user should see *"die aktuelle Runde"* of a running game on the
  dashboard (README: Dashboard/GUI section), but `GameController`
  currently 404s `GET /api/games/{id}` for anyone who isn't a player in
  that game, and `GameWebSocketController` similarly checks membership
  before upgrading. This implementation only shows onlookers *that* a
  game is running (`GAME_RUNNING` + who's in it), not its round number or
  remaining time — that needs either a small public/summary variant of
  those two membership checks, or piggybacking round-started events
  through `GameInviteService`'s existing EventBus subscription and
  broadcasting them lobby-wide too. Left out here to keep this change to
  the invite flow itself.
- **Not tested against a live server/DB.** No Maven Central access in the
  environment this was written in, so the Java side is unverified beyond
  careful reading + a brace-balance check. Frontend TypeScript compiles
  and `vite build` succeeds cleanly.
- **Highscore card** (top-5-of-last-10-days) is a separate, also-missing
  base requirement, unrelated to this feature — visible in every
  dashboard mockup but out of scope here.
