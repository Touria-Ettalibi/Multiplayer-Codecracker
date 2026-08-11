package de.thm.codecracker.lobby;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * Handles the REST endpoint used to load the initial lobby state.
 *
 * <p>Mirrors the Todo template's convention: no initial data is pushed
 * when a WebSocket connects, so a client loads the current snapshot once
 * over REST and then applies {@code player-joined}/{@code player-left}
 * events from the WebSocket from that point onward.</p>
 */
public class LobbyController {
  private final LobbyRegistry lobbyRegistry;

  /**
   * Creates a new Lobby controller.
   *
   * @param lobbyRegistry the registry of currently connected Users
   */
  public LobbyController(LobbyRegistry lobbyRegistry) {
    this.lobbyRegistry = lobbyRegistry;
  }

  /**
   * Registers the lobby snapshot route on the given router.
   *
   * @param router the Vert.x router used to register the route
   */
  public void registerRoutes(Router router) {
    router.get("/api/lobby").handler(this::findAll);
  }

  private void findAll(RoutingContext ctx) {
    JsonArray users = new JsonArray(lobbyRegistry.snapshot().stream()
      .map(user -> new JsonObject()
        .put("id", user.id())
        .put("username", user.username()))
      .toList());

    ctx.response()
      .putHeader("content-type", "application/json")
      .end(users.encode());
  }
}
