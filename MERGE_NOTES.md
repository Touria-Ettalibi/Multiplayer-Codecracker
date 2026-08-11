# Merge notes: feature/Lobby-online-player-list-UI + feature/Game

This folder is `feature/Game` with the teammate's Lobby-UI branch merged in.
No git history was available for either branch (both were plain zip
exports), so this was a manual merge — do this for real with:

```bash
git checkout feature/Game
git checkout -b merge/game-and-lobby-ui
git merge feature/Lobby-online-player-list-UI
# resolve using the notes below, then commit
```

## Why a manual merge was needed at all

Both branches touched `LobbyWebSocketController` and the dashboard, but for
different reasons:

- **feature/Game** rewrote `LobbyWebSocketController` to use a new
  `LobbyRegistry`/`ConnectedUser` pair (multi-tab-aware: a user only
  triggers `player-joined`/`player-left` on their *first*/*last* open tab,
  not every tab). It also added a `GET /api/lobby` REST snapshot endpoint
  (`LobbyController`) so a page refresh doesn't have to wait on the socket,
  matching the existing Todo/Game convention. This was needed because the
  Game feature has to know who's actually online (for the eventual
  invite/ready-check flow).
- **feature/Lobby-online-player-list-UI** built the actual online-players
  card UI, but against the *old* protocol (`online-list`/`join`/`leave`
  events with a `user` field, no REST snapshot) — it branched off before
  the registry rework landed.

So the two branches' online-list code is **not just duplicated, it's
incompatible**: the teammate's `lobby-websocket.ts` would silently show an
empty/stale list against the current backend, since it listens for
`online-list`/`join`/`leave` events that the server no longer sends.

## What was kept from each side

**Backend:** entirely from `feature/Game`, unchanged. It's a strict
superset (Auth, User management, Todo template, the reworked Lobby
backend, and the full Game feature).

**Frontend:**
- `lobby-api.ts` + `lobby-socket.ts` (from `feature/Game`) — kept, because
  these already speak the current backend protocol.
- `lobby-websocket.ts` (from the teammate's branch) — **dropped**, it's the
  incompatible old-protocol client. Nothing else referenced it.
- `dashboard.html` layout (the "Online" card with the status badge and
  `online-players-list`) — taken from the teammate's branch; it's simply
  better UI than the placeholder `<ul id="lobbyList">` that `feature/Game`
  had thrown together just to unblock its own testing. Added an
  `#lobbyError` slot into that card for the REST-fetch-failed case, which
  the old markup didn't have.
- `dashboard.ts` — rewritten from scratch, combining: the correct
  data source (`fetchLobby()` + `connectLobbySocket()` from
  `feature/Game`), rendered into the teammate's markup/empty-state
  ("Niemand online."), plus the "Du" badge for the current user from
  `feature/Game`'s version, plus a connection-status badge
  (connecting/connected/disconnected/unreachable) matching the UX the
  teammate designed for `#lobby-status`.
- `lobby-socket.ts` gained one small, backward-compatible addition: an
  optional `onStatusChange` callback on `connectLobbySocket(...)`, so
  `dashboard.ts` can drive that status badge. No existing caller passed a
  second argument before, so this doesn't break anything.
- `nav.ts`, `vite.config.ts`, `mariadb/mariadb_init/00-init.sql`,
  `README.md` heading, `header.hbs` — trivial, non-conflicting differences
  (a `game.html` build entry, a working local dev bcrypt hash instead of a
  placeholder, a generic README title instead of a branch-specific one).
  Kept `feature/Game`'s versions; nothing lost.
- Every other shared file (`auth-storage.ts`, `login.ts`, `require-auth.ts`,
  `UserController.java`, etc.) only differed by CRLF line endings /
  trailing newline between the two branches — no real changes, so nothing
  to merge there.

## Still open (out of scope for this merge)

The teammate's original `dashboard.ts` had a note that hiding the lobby
view during an active game depends on a "current game" signal that didn't
exist yet. That's still true after this merge: `GameController` only
exposes `GET /api/games/{id}` for a game you already know the id of (plus
the temporary `/api/games/dev-start`), not a "which game is user X
currently in" lookup. That's explicitly deferred to `feature/game-invite`
per `GameController`'s own Javadoc — carried the same note forward in
`dashboard.ts`.

## Verified

- `cd frontend && npm ci && npm run build` — builds clean, no TS errors.
- Backend was not recompiled here (no Maven Central access in this
  environment) — it's unchanged from `feature/Game`, so it should build
  exactly as it did before.
