package de.thm.codecracker.lobby;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.vertx.core.http.ServerWebSocket;

/**
 * Tracks which Users are currently connected to the lobby.
 *
 * <p>This state is deliberately kept in memory only, not in MariaDB: it
 * describes which WebSocket connections are open right now, which only
 * makes sense for as long as this backend process is running. A User can
 * have more than one open connection (e.g. two browser tabs); they only
 * count as "joined"/"left" the lobby on the first connection and the last
 * disconnection, respectively.</p>
 */
public class LobbyRegistry {
  private final Map<Long, ConnectedUser> onlineUsers = new ConcurrentHashMap<>();
  private final Map<Long, Set<ServerWebSocket>> connectionsByUserId = new ConcurrentHashMap<>();

  /**
   * Registers a new connection for a User.
   *
   * @param user   the connecting User
   * @param socket the newly opened WebSocket
   * @return {@code true} if this is the User's first open connection
   *         (i.e. they just joined the lobby), {@code false} if they
   *         already had another connection open (e.g. a second tab)
   */
  public boolean connect(ConnectedUser user, ServerWebSocket socket) {
    Set<ServerWebSocket> sockets = connectionsByUserId.computeIfAbsent(
      user.id(), unused -> ConcurrentHashMap.newKeySet()
    );

    boolean isFirstConnection = sockets.isEmpty();
    sockets.add(socket);
    onlineUsers.put(user.id(), user);

    return isFirstConnection;
  }

  /**
   * Removes a connection for a User.
   *
   * @param userId the User's ID
   * @param socket the WebSocket that closed
   * @return {@code true} if this was the User's last open connection
   *         (i.e. they just left the lobby), {@code false} if another
   *         connection (e.g. another tab) is still open
   */
  public boolean disconnect(Long userId, ServerWebSocket socket) {
    Set<ServerWebSocket> sockets = connectionsByUserId.get(userId);

    if (sockets == null) {
      return false;
    }

    sockets.remove(socket);

    if (sockets.isEmpty()) {
      connectionsByUserId.remove(userId);
      onlineUsers.remove(userId);
      return true;
    }

    return false;
  }

  /**
   * Returns a snapshot of every currently connected User.
   *
   * @return the currently connected Users
   */
  public List<ConnectedUser> snapshot() {
    return List.copyOf(onlineUsers.values());
  }

  /**
   * Returns every currently open lobby WebSocket, across all connected Users.
   *
   * <p>Used to broadcast join/leave events to everyone currently in the lobby.</p>
   *
   * @return all currently open sockets
   */
  public Set<ServerWebSocket> allSockets() {
    return connectionsByUserId.values().stream()
      .flatMap(Set::stream)
      .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }
}
