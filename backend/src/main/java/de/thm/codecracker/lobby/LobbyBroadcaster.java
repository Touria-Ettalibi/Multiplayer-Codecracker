package de.thm.codecracker.lobby;

import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;

/**
 * Sends a JSON {@code {"type": ..., "payload": ...}} message to every
 * currently connected lobby WebSocket client.
 *
 * <p>Extracted out of {@link LobbyWebSocketController} so other lobby-wide
 * senders — currently just the game invite flow — can reuse it with only a
 * {@link LobbyRegistry} reference, instead of depending on the WebSocket
 * controller itself.</p>
 */
public class LobbyBroadcaster {
  private final LobbyRegistry lobbyRegistry;

  /**
   * Creates a new Lobby broadcaster.
   *
   * @param lobbyRegistry the registry of currently connected Users
   */
  public LobbyBroadcaster(LobbyRegistry lobbyRegistry) {
    this.lobbyRegistry = lobbyRegistry;
  }

  /**
   * Sends an event to every currently open lobby socket, including every
   * open tab belonging to any one User.
   *
   * @param type    the event type, e.g. {@code "player-joined"} or
   *                {@code "game-invite-started"}
   * @param payload the event's payload
   */
  public void broadcast(String type, JsonObject payload) {
    JsonObject message = new JsonObject()
      .put("type", type)
      .put("payload", payload);

    String encoded = message.encode();

    for (ServerWebSocket socket : lobbyRegistry.allSockets()) {
      if (!socket.isClosed()) {
        socket.writeTextMessage(encoded);
      }
    }
  }
}
