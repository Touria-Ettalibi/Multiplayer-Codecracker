package de.thm.codecracker.lobby;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks which authenticated users are currently connected to the lobby and
 * broadcasts join/leave events in real time.
 *
 * <p>Browsers cannot send custom headers during a WebSocket handshake, so the
 * JWT is passed as a {@code ?token=} query parameter instead of the
 * {@code Authorization} header used by the REST endpoints. The token is
 * validated manually before the connection is upgraded.</p>
 */
public class LobbyWebSocketController {
  private static final Logger LOGGER = LoggerFactory.getLogger(LobbyWebSocketController.class);
  private static final String LOBBY_PATH = "/ws/lobby";

  private final JWTAuth jwtAuth;

  /** Every currently connected socket, mapped to that connection's JWT claims. */
  private final Map<ServerWebSocket, JsonObject> connections = new ConcurrentHashMap<>();

  /**
   * Creates a new Lobby WebSocket controller.
   *
   * @param jwtAuth the JWT provider used to validate the connection token
   */
  public LobbyWebSocketController(JWTAuth jwtAuth) {
    this.jwtAuth = jwtAuth;
  }

  /**
   * Registers the Lobby WebSocket route on the given router.
   *
   * @param router the Vert.x router used to register the route
   */
  public void registerRoutes(Router router) {
    router.get(LOBBY_PATH).handler(this::upgradeConnection);
  }

  /**
   * Validates the connection token and upgrades the request to a WebSocket
   * if it is valid, or rejects the request otherwise.
   *
   * @param ctx the current routing context
   */
  private void upgradeConnection(RoutingContext ctx) {
    String token = ctx.request().getParam("token");

    if (token == null || token.isBlank()) {
      ctx.response().setStatusCode(401).end();
      return;
    }

    try {
      JsonObject principal = jwtAuth.authenticate(new TokenCredentials(token))
        .await()
        .principal();

      ServerWebSocket socket = ctx.request().toWebSocket().await();
      handleConnection(socket, principal);
    } catch (Exception exception) {
      ctx.response().setStatusCode(401).end();
    }
  }

  /**
   * Registers a newly connected client, sends it the current online list,
   * and notifies every other client that this user has joined.
   *
   * @param socket    the newly opened WebSocket
   * @param principal the JWT claims identifying the connected user
   */
  private void handleConnection(ServerWebSocket socket, JsonObject principal) {
    connections.put(socket, principal);

    LOGGER.info("User {} joined the lobby", principal.getString("sub"));

    sendOnlineList(socket);
    broadcast(event("join", principal));

    socket.closeHandler(unused -> handleDisconnect(socket));
    socket.exceptionHandler(error -> handleDisconnect(socket));
  }

  /**
   * Removes a disconnected client and notifies every remaining client that
   * this user has left.
   *
   * @param socket the WebSocket that closed or errored
   */
  private void handleDisconnect(ServerWebSocket socket) {
    JsonObject principal = connections.remove(socket);

    if (principal == null) {
      return;
    }

    LOGGER.info("User {} left the lobby", principal.getString("sub"));

    broadcast(event("leave", principal));
  }

  /**
   * Sends the current list of distinct online users to a single client.
   *
   * <p>The same user connected from multiple tabs/devices is only counted
   * once.</p>
   *
   * @param socket the client to send the snapshot to
   */
  private void sendOnlineList(ServerWebSocket socket) {
    Map<Object, JsonObject> distinctUsers = new LinkedHashMap<>();

    for (JsonObject principal : connections.values()) {
      distinctUsers.putIfAbsent(principal.getValue("uid"), toUserSummary(principal));
    }

    JsonObject message = new JsonObject()
      .put("type", "online-list")
      .put("users", new JsonArray(new ArrayList<>(distinctUsers.values())));

    socket.writeTextMessage(message.encode());
  }

  /**
   * Builds a join or leave event for a single user.
   *
   * @param type      either {@code "join"} or {@code "leave"}
   * @param principal the JWT claims identifying the user
   * @return the event as JSON
   */
  private JsonObject event(String type, JsonObject principal) {
    return new JsonObject()
      .put("type", type)
      .put("user", toUserSummary(principal));
  }

  /**
   * Extracts the public fields of a user from their JWT claims.
   *
   * @param principal the JWT claims
   * @return the public user summary
   */
  private JsonObject toUserSummary(JsonObject principal) {
    return new JsonObject()
      .put("id", principal.getValue("uid"))
      .put("username", principal.getString("sub"));
  }

  /**
   * Sends a message to every currently connected lobby client.
   *
   * @param message the JSON message to broadcast
   */
  private void broadcast(JsonObject message) {
    for (ServerWebSocket socket : connections.keySet()) {
      if (!socket.isClosed()) {
        socket.writeTextMessage(message.encode());
      }
    }
  }
}
