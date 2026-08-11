package de.thm.codecracker.invite;

import java.util.List;

import de.thm.codecracker.game.GameService;
import de.thm.codecracker.game.model.Game;
import de.thm.codecracker.lobby.ConnectedUser;
import de.thm.codecracker.lobby.LobbyBroadcaster;
import de.thm.codecracker.lobby.LobbyRegistry;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements the "any lobby player can start a game" flow described in
 * the assignment: starting a game invites everyone currently in the
 * lobby, gives them 30 seconds to confirm or decline, and — if at least
 * two end up ready — hands off to {@link GameService#startGame} for the
 * actual Game. Not required by the base assignment, but simultaneous
 * games aren't handled here: only one invite/game can be active across
 * the whole lobby at a time, matching "Die Lobby ist während des Spiels
 * nicht verfügbar."
 *
 * <p>All state lives in {@link #currentSession}, in memory only — see
 * {@link InviteSession}'s Javadoc for why. Every read or write of it is
 * synchronized on {@link #lock}; Vert.x route handlers here may run on
 * virtual threads per the project's threading model, so unlike the
 * single-event-loop-thread code elsewhere in this codebase, concurrent
 * calls into this class are a real possibility (e.g. two players clicking
 * "Bereit" at the same moment).</p>
 */
public class GameInviteService {
  private static final Logger LOGGER = LoggerFactory.getLogger(GameInviteService.class);

  private static final long RESPONSE_WINDOW_MILLIS = 30_000;
  private static final int MIN_READY_PLAYERS = 2;

  private final Vertx vertx;
  private final LobbyRegistry lobbyRegistry;
  private final LobbyBroadcaster lobbyBroadcaster;
  private final GameService gameService;

  private final Object lock = new Object();

  /** {@code null} whenever no invite is pending and no game is running. */
  private InviteSession currentSession;

  public GameInviteService(
    Vertx vertx,
    LobbyRegistry lobbyRegistry,
    LobbyBroadcaster lobbyBroadcaster,
    GameService gameService
  ) {
    this.vertx = vertx;
    this.lobbyRegistry = lobbyRegistry;
    this.lobbyBroadcaster = lobbyBroadcaster;
    this.gameService = gameService;
  }

  /**
   * Starts a new invite round, inviting every User currently connected to
   * the lobby (including the initiator, who is recorded as ready
   * immediately — starting a game is itself a confirmation of readiness).
   *
   * @param initiatorUserId the initiating User's ID
   * @return the snapshot to send back to the initiator's REST call
   * @throws InviteConflictException if an invite or game is already
   *                                   active, the initiator isn't
   *                                   currently connected to the lobby
   *                                   socket, or fewer than two Users are
   *                                   online in the first place
   */
  public JsonObject startInvite(Long initiatorUserId) {
    synchronized (lock) {
      if (currentSession != null) {
        throw new InviteConflictException("A game invite or game is already active");
      }

      List<ConnectedUser> online = lobbyRegistry.snapshot();
      ConnectedUser initiator = online.stream()
        .filter(user -> user.id().equals(initiatorUserId))
        .findFirst()
        .orElseThrow(() -> new InviteConflictException(
          "You must be connected to the lobby (open the dashboard) to start a game"));

      if (online.size() < MIN_READY_PLAYERS) {
        throw new InviteConflictException("At least two Users must be online to start a game");
      }

      long deadlineAt = System.currentTimeMillis() + RESPONSE_WINDOW_MILLIS;
      long timerId = vertx.setTimer(RESPONSE_WINDOW_MILLIS, fired -> concludeInvite());

      currentSession = new InviteSession(initiator, online, deadlineAt, timerId);

      lobbyBroadcaster.broadcast("game-invite-started", inviteStartedJson(currentSession));

      return snapshotJson();
    }
  }

  /**
   * Records one invited User's response. If every invited User has now
   * responded, concludes the invite immediately instead of waiting out
   * the rest of the 30s window.
   *
   * @param userId the responding User's ID
   * @param ready  {@code true} for "Bereit", {@code false} for "Ablehnen"
   * @throws InviteConflictException if there is no pending invite (e.g. it
   *                                   already turned into a running game)
   * @throws NotInvitedException     if the caller wasn't part of this
   *                                   invite — most likely because they
   *                                   connected to the lobby after it started
   */
  public void respond(Long userId, boolean ready) {
    boolean shouldConcludeNow;

    synchronized (lock) {
      if (currentSession == null || currentSession.gameId() != null) {
        throw new InviteConflictException("There is no pending game invite to respond to");
      }

      if (!currentSession.isInvited(userId)) {
        throw new NotInvitedException("You were not invited to this round");
      }

      currentSession.recordResponse(userId, ready);

      ConnectedUser user = currentSession.invitedUsers().get(userId);
      lobbyBroadcaster.broadcast("game-invite-status", new JsonObject()
        .put("id", user.id())
        .put("username", user.username())
        .put("status", ready ? "ready" : "declined"));

      shouldConcludeNow = currentSession.everyoneHasResponded();
    }

    if (shouldConcludeNow) {
      concludeInvite();
    }
  }

  /**
   * Returns the current lobby-wide phase, for a client that just loaded
   * the dashboard (or refreshed it) to catch up without waiting for the
   * next WebSocket event.
   *
   * @return {@code {"phase": "NONE"}}, an invite-pending snapshot, or a
   *         game-running snapshot — see {@link #snapshotJson()}
   */
  public JsonObject currentSnapshot() {
    synchronized (lock) {
      return snapshotJson();
    }
  }

  /**
   * Concludes the current invite: either because the 30s timer fired, or
   * because every invited User responded early. Safe to call from either
   * trigger — only proceeds if a session is still actually pending.
   */
  private void concludeInvite() {
    InviteSession session;
    List<Long> readyUserIds;

    synchronized (lock) {
      session = currentSession;
      if (session == null || session.gameId() != null) {
        return; // already concluded via the other trigger, or no longer relevant
      }

      // Cancel the pending timer now: harmless no-op if it already fired
      // (that's what got us here), but necessary if we're concluding
      // early because everyone responded before the 30s were up.
      vertx.cancelTimer(session.timerId());

      readyUserIds = session.readyUserIds();

      if (readyUserIds.size() < MIN_READY_PLAYERS) {
        currentSession = null;
        lobbyBroadcaster.broadcast("game-invite-cancelled", new JsonObject()
          .put("reason", "not-enough-ready"));
        return;
      }
    }

    // Deliberately outside the lock: startGame() blocks on database I/O
    // (via .await()), which shouldn't happen while holding a lock other
    // requests (e.g. a late "Ablehnen" click) might be waiting on.
    Game game;
    try {
      game = gameService.startGame(readyUserIds).await();
    } catch (Exception exception) {
      LOGGER.error("Failed to start game from invite session", exception);
      synchronized (lock) {
        if (currentSession == session) {
          currentSession = null;
        }
      }
      lobbyBroadcaster.broadcast("game-invite-cancelled", new JsonObject()
        .put("reason", "start-failed"));
      return;
    }

    synchronized (lock) {
      session.setGameId(game.id());
    }

    lobbyBroadcaster.broadcast("game-started", new JsonObject()
      .put("gameId", game.id())
      .put("playerUserIds", new JsonArray(readyUserIds)));

    var gameEndedConsumer = vertx.eventBus().<String>consumer("game:" + game.id());
    gameEndedConsumer.handler(message -> {
      JsonObject event = new JsonObject(message.body());
      if ("game-ended".equals(event.getString("type"))) {
        gameEndedConsumer.unregister();
        onGameEnded(session, game.id());
      }
    });
  }

  private void onGameEnded(InviteSession session, Long gameId) {
    synchronized (lock) {
      if (currentSession != session) {
        return;
      }
      currentSession = null;
    }

    lobbyBroadcaster.broadcast("lobby-game-ended", new JsonObject().put("gameId", gameId));
  }

  private JsonObject inviteStartedJson(InviteSession session) {
    return new JsonObject()
      .put("initiator", new JsonObject()
        .put("id", session.initiator().id())
        .put("username", session.initiator().username()))
      .put("deadlineAt", session.deadlineAt())
      .put("players", session.playersWithStatus());
  }

  /** Must be called while holding {@link #lock}. */
  private JsonObject snapshotJson() {
    if (currentSession == null) {
      return new JsonObject().put("phase", "NONE");
    }

    if (currentSession.gameId() != null) {
      return new JsonObject()
        .put("phase", "GAME_RUNNING")
        .put("gameId", currentSession.gameId())
        .put("playerUserIds", new JsonArray(currentSession.readyUserIds()));
    }

    return new JsonObject()
      .put("phase", "INVITE_PENDING")
      .put("initiator", new JsonObject()
        .put("id", currentSession.initiator().id())
        .put("username", currentSession.initiator().username()))
      .put("deadlineAt", currentSession.deadlineAt())
      .put("players", currentSession.playersWithStatus());
  }
}
