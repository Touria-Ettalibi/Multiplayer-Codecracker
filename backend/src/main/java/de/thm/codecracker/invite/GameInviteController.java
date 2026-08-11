package de.thm.codecracker.invite;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the REST endpoints for the game invite / ready-check flow:
 * starting an invite, responding to one, and loading the current
 * lobby-wide phase (for a client that just opened or refreshed the
 * dashboard). Mirrors the same conventions as {@link
 * de.thm.codecracker.lobby.LobbyController} and {@link
 * de.thm.codecracker.game.GameController}.
 *
 * <p>Real-time updates (a new invite starting, someone responding, the
 * game actually starting) are pushed separately over the existing lobby
 * WebSocket — these endpoints only cover the request/response actions
 * themselves and the page-refresh snapshot.</p>
 */
public class GameInviteController {
  private static final Logger LOGGER = LoggerFactory.getLogger(GameInviteController.class);

  private final GameInviteService gameInviteService;

  public GameInviteController(GameInviteService gameInviteService) {
    this.gameInviteService = gameInviteService;
  }

  /**
   * Registers the game invite routes on the given router.
   *
   * @param router the Vert.x router used to register the routes
   */
  public void registerRoutes(Router router) {
    router.get("/api/lobby/invite").handler(this::getCurrentPhase);
    router.post("/api/lobby/invite/start").handler(this::start);
    router.post("/api/lobby/invite/respond").handler(this::respond);
  }

  private void getCurrentPhase(RoutingContext ctx) {
    ctx.response()
      .putHeader("content-type", "application/json")
      .end(gameInviteService.currentSnapshot().encode());
  }

  private void start(RoutingContext ctx) {
    try {
      Long initiatorUserId = ctx.user().principal().getLong("uid");
      JsonObject snapshot = gameInviteService.startInvite(initiatorUserId);

      ctx.response()
        .setStatusCode(201)
        .putHeader("content-type", "application/json")
        .end(snapshot.encode());
    } catch (InviteConflictException exception) {
      sendError(ctx, 409, exception.getMessage());
    } catch (Exception exception) {
      LOGGER.error("Unexpected error while starting a game invite", exception);
      sendError(ctx, 500, "Unexpected server error");
    }
  }

  private void respond(RoutingContext ctx) {
    try {
      Long userId = ctx.user().principal().getLong("uid");
      JsonObject body = ctx.body().asJsonObject();

      if (body == null || !body.containsKey("ready")) {
        sendError(ctx, 400, "Request body must include a boolean 'ready' field");
        return;
      }

      gameInviteService.respond(userId, body.getBoolean("ready"));
      ctx.response().setStatusCode(204).end();
    } catch (NotInvitedException exception) {
      sendError(ctx, 403, exception.getMessage());
    } catch (InviteConflictException exception) {
      sendError(ctx, 409, exception.getMessage());
    } catch (Exception exception) {
      LOGGER.error("Unexpected error while responding to a game invite", exception);
      sendError(ctx, 500, "Unexpected server error");
    }
  }

  private void sendError(RoutingContext ctx, int statusCode, String message) {
    ctx.response()
      .setStatusCode(statusCode)
      .putHeader("content-type", "application/json")
      .end(new JsonObject().put("error", message).encode());
  }
}
