package de.thm.codecracker.invite;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.thm.codecracker.lobby.ConnectedUser;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Tracks one lobby-wide "someone wants to start a game" round: who was
 * invited, how each of them has responded so far, and — once the 30s
 * window closes with at least two players ready — which {@code Game} it
 * turned into.
 *
 * <p>Deliberately in-memory only, never persisted, following the same
 * reasoning as {@link de.thm.codecracker.lobby.LobbyRegistry}: this state
 * only means anything while this backend process and these WebSocket
 * connections are alive. It stops existing the moment it either turns
 * into a real, persisted {@code Game} (via {@code GameService#startGame})
 * or gets cancelled — there is nothing here worth surviving a restart.</p>
 *
 * <p>Not thread-safe by itself — {@link de.thm.codecracker.invite.GameInviteService}
 * is responsible for synchronizing all access to a session's mutable state.</p>
 */
class InviteSession {
  private final ConnectedUser initiator;

  /** Every User invited when this session started, keyed by user ID, in invite order. */
  private final Map<Long, ConnectedUser> invitedUsers;

  /** Responses recorded so far: {@code true} = ready, {@code false} = declined. */
  private final Map<Long, Boolean> responses = new LinkedHashMap<>();

  private final long deadlineAt;
  private final long timerId;

  /** Set once the 30s window has closed and a real Game has been created; {@code null} until then. */
  private Long gameId;

  InviteSession(ConnectedUser initiator, List<ConnectedUser> invitedUsers, long deadlineAt, long timerId) {
    this.initiator = initiator;
    this.invitedUsers = new LinkedHashMap<>();
    for (ConnectedUser user : invitedUsers) {
      this.invitedUsers.put(user.id(), user);
    }
    // The initiator confirms readiness implicitly by starting the game.
    this.responses.put(initiator.id(), true);
    this.deadlineAt = deadlineAt;
    this.timerId = timerId;
  }

  ConnectedUser initiator() {
    return initiator;
  }

  Map<Long, ConnectedUser> invitedUsers() {
    return invitedUsers;
  }

  boolean isInvited(Long userId) {
    return invitedUsers.containsKey(userId);
  }

  void recordResponse(Long userId, boolean ready) {
    responses.put(userId, ready);
  }

  /**
   * @return {@code true} once every invited User has either accepted or
   *         declined, meaning the session can conclude before its deadline
   */
  boolean everyoneHasResponded() {
    return responses.size() >= invitedUsers.size();
  }

  List<Long> readyUserIds() {
    return responses.entrySet().stream()
      .filter(Map.Entry::getValue)
      .map(Map.Entry::getKey)
      .toList();
  }

  long deadlineAt() {
    return deadlineAt;
  }

  long timerId() {
    return timerId;
  }

  Long gameId() {
    return gameId;
  }

  void setGameId(Long gameId) {
    this.gameId = gameId;
  }

  /**
   * Builds the {@code players} array shared by every invite-related event
   * and the REST snapshot: every invited User plus their current status.
   *
   * @return the players, each as {@code {id, username, status}} where
   *         status is {@code "ready"}, {@code "declined"}, or {@code "pending"}
   */
  JsonArray playersWithStatus() {
    return new JsonArray(invitedUsers.values().stream()
      .map(user -> {
        Boolean response = responses.get(user.id());
        String status = response == null ? "pending" : (response ? "ready" : "declined");
        return new JsonObject()
          .put("id", user.id())
          .put("username", user.username())
          .put("status", status);
      })
      .toList());
  }
}
