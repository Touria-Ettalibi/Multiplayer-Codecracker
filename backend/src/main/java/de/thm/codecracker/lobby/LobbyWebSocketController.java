package de.thm.codecracker.lobby;

import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages lobby WebSocket connections.
 *
 * <p>Browsers cannot set an {@code Authorization} header during a WebSocket
 * handshake, so the JWT travels as a {@code token} query parameter instead
 * (e.g. {@code wss://host/ws/lobby?token=...}). The token is validated
 * <strong>before</strong> the HTTP connection is upgraded: an invalid or
 * expired token gets a plain {@code 401} response and the upgrade never
 * happens, satisfying "close WebSocket connection on invalid/expired
 * token".</p>
 */
public class LobbyWebSocketController {
  private static final Logger LOGGER = LoggerFactory.getLogger(LobbyWebSocketController.class);
  private static final String LOBBY_PATH = "/ws/lobby";

  private final JWTAuth jwtAuth;
  private final LobbyRegistry lobbyRegistry;
  private final LobbyBroadcaster lobbyBroadcaster;

  /**
   * Creates a new Lobby WebSocket controller.
   *
   * @param jwtAuth          used to validate the token supplied at connection time
   * @param lobbyRegistry    the registry of currently connected Users
   * @param lobbyBroadcaster used to notify every connected client of joins/leaves
   */
  public LobbyWebSocketController(JWTAuth jwtAuth, LobbyRegistry lobbyRegistry, LobbyBroadcaster lobbyBroadcaster) {
    this.jwtAuth = jwtAuth;
    this.lobbyRegistry = lobbyRegistry;
    this.lobbyBroadcaster = lobbyBroadcaster;
  }

  /**
   * Registers the lobby WebSocket route on the given router.
   *
   * @param router the Vert.x router used to register the route
   */
  public void registerRoutes(Router router) {
    router.get(LOBBY_PATH).handler(this::upgradeConnection);
  }

  private void upgradeConnection(RoutingContext ctx) {
    String token = ctx.request().getParam("token");

    if (token == null || token.isBlank()) {
      ctx.response().setStatusCode(401).end();
      return;
    }

    jwtAuth.authenticate(new TokenCredentials(token))
      .onSuccess(user -> completeUpgrade(ctx, user))
      .onFailure(cause -> ctx.response().setStatusCode(401).end());
  }

  private void completeUpgrade(RoutingContext ctx, User authenticatedUser) {
    ConnectedUser user = new ConnectedUser(
      authenticatedUser.principal().getLong("uid"),
      authenticatedUser.principal().getString("sub")
    );

    try {
      ServerWebSocket socket = ctx.request().toWebSocket().await();
      handleConnection(user, socket);
    } catch (Exception exception) {
      ctx.fail(exception);
    }
  }

  private void handleConnection(ConnectedUser user, ServerWebSocket socket) {
    boolean isFirstConnection = lobbyRegistry.connect(user, socket);

    if (isFirstConnection) {
      lobbyBroadcaster.broadcast("player-joined", toJson(user));
    }

    socket.closeHandler(unused -> onDisconnect(user, socket));
    socket.exceptionHandler(error -> {
      LOGGER.warn("Lobby WebSocket error for user {}", user.username(), error);
      onDisconnect(user, socket);
    });
  }

  private void onDisconnect(ConnectedUser user, ServerWebSocket socket) {
    boolean wasLastConnection = lobbyRegistry.disconnect(user.id(), socket);

    if (wasLastConnection) {
      lobbyBroadcaster.broadcast("player-left", toJson(user));
    }
  }

  private JsonObject toJson(ConnectedUser user) {
    return new JsonObject()
      .put("id", user.id())
      .put("username", user.username());
  }
}
