package de.thm.codecracker.game;

import de.thm.codecracker.game.model.Guess;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
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
 * Manages the {@code /ws/game} WebSocket: one connection per player, scoped
 * to one Game.
 *
 * <p>Like {@link de.thm.codecracker.lobby.LobbyWebSocketController}, the
 * JWT travels as a {@code token} query parameter (a WS handshake cannot
 * carry an {@code Authorization} header) and is validated before the
 * upgrade completes. The Game ID travels as a {@code gameId} query
 * parameter, and the caller must actually be a player in that Game —
 * checked before upgrading, so a stranger can't open a socket for a game
 * they're not part of.</p>
 *
 * <p>Once connected, this controller subscribes the socket to two EventBus
 * addresses published by {@link GameService}: the public
 * {@code "game:" + gameId} address (round-started, round-ended, etc.) and
 * this player's own private {@code "game-guess:" + userId} address (their
 * guess feedback). Both consumers are unregistered when the socket
 * closes.</p>
 */
public class GameWebSocketController {
  private static final Logger LOGGER = LoggerFactory.getLogger(GameWebSocketController.class);
  private static final String GAME_PATH = "/ws/game";

  private final JWTAuth jwtAuth;
  private final GameRepository gameRepository;
  private final GameService gameService;
  private final Vertx vertx;

  /**
   * Creates a new Game WebSocket controller.
   *
   * @param vertx          used to subscribe to the EventBus addresses published by {@link GameService}
   * @param jwtAuth        used to validate the token supplied at connection time
   * @param gameRepository used to check that the caller is actually a player in the requested Game
   * @param gameService     used to execute submit-guess/forfeit commands
   */
  public GameWebSocketController(
    Vertx vertx,
    JWTAuth jwtAuth,
    GameRepository gameRepository,
    GameService gameService
  ) {
    this.vertx = vertx;
    this.jwtAuth = jwtAuth;
    this.gameRepository = gameRepository;
    this.gameService = gameService;
  }

  /**
   * Registers the Game WebSocket route on the given router.
   *
   * @param router the Vert.x router used to register the route
   */
  public void registerRoutes(Router router) {
    router.get(GAME_PATH).handler(this::upgradeConnection);
  }

  private void upgradeConnection(RoutingContext ctx) {
    String token = ctx.request().getParam("token");
    String gameIdParam = ctx.request().getParam("gameId");

    if (token == null || token.isBlank() || gameIdParam == null) {
      ctx.response().setStatusCode(401).end();
      return;
    }

    Long gameId;
    try {
      gameId = Long.parseLong(gameIdParam);
    } catch (NumberFormatException exception) {
      ctx.response().setStatusCode(400).end();
      return;
    }

    jwtAuth.authenticate(new TokenCredentials(token))
      .onSuccess(user -> verifyMembershipAndUpgrade(ctx, user, gameId))
      .onFailure(cause -> ctx.response().setStatusCode(401).end());
  }

  private void verifyMembershipAndUpgrade(RoutingContext ctx, User authenticatedUser, Long gameId) {
    Long userId = authenticatedUser.principal().getLong("uid");

    var player = gameRepository.findPlayer(gameId, userId).await();
    if (player == null) {
      ctx.response().setStatusCode(403).end();
      return;
    }

    try {
      ServerWebSocket socket = ctx.request().toWebSocket().await();
      handleConnection(gameId, userId, socket);
    } catch (Exception exception) {
      ctx.fail(exception);
    }
  }

  private void handleConnection(Long gameId, Long userId, ServerWebSocket socket) {
    MessageConsumer<String> publicConsumer = vertx.eventBus().<String>consumer(
      "game:" + gameId, message -> writeIfOpen(socket, message.body())
    );

    MessageConsumer<String> privateConsumer = vertx.eventBus().<String>consumer(
      "game-guess:" + userId, message -> writeIfOpen(socket, message.body())
    );

    socket.textMessageHandler(message -> handleCommand(gameId, userId, socket, message));

    socket.closeHandler(unused -> {
      publicConsumer.unregister();
      privateConsumer.unregister();
    });

    socket.exceptionHandler(error -> {
      LOGGER.warn("Game WebSocket error for user {} in game {}", userId, gameId, error);
      publicConsumer.unregister();
      privateConsumer.unregister();
    });
  }

  private void handleCommand(Long gameId, Long userId, ServerWebSocket socket, String message) {
    String requestId = null;

    try {
      JsonObject command = new JsonObject(message);
      requestId = command.getString("requestId");
      String type = requiredString(command, "type");
      JsonObject payload = command.getJsonObject("payload", new JsonObject());

      Object result = switch (type) {
        case "submit-guess" -> submitGuess(gameId, userId, payload);
        case "forfeit" -> forfeit(gameId, userId);
        default -> throw new IllegalArgumentException("Unknown command: " + type);
      };

      sendResponse(socket, requestId, result);
    } catch (GameNotFoundException | InvalidGuessException | IllegalArgumentException exception) {
      sendError(socket, requestId, exception.getMessage());
    } catch (Exception exception) {
      LOGGER.error("Unexpected error while handling Game WebSocket command", exception);
      sendError(socket, requestId, "Unexpected server error");
    }
  }

  private JsonObject submitGuess(Long gameId, Long userId, JsonObject payload) {
    String code = payload.getString("code");
    Guess guess = gameService.submitGuess(gameId, userId, code).await();
    return gameService.guessToJson(guess);
  }

  private JsonObject forfeit(Long gameId, Long userId) {
    gameService.forfeit(gameId, userId).await();
    return new JsonObject().put("forfeited", true);
  }

  private String requiredString(JsonObject object, String fieldName) {
    String value = object.getString(fieldName);

    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " is required");
    }

    return value;
  }

  private void writeIfOpen(ServerWebSocket socket, String message) {
    if (!socket.isClosed()) {
      socket.writeTextMessage(message);
    }
  }

  private void sendResponse(ServerWebSocket socket, String requestId, Object payload) {
    writeIfOpen(socket, new JsonObject()
      .put("type", "response")
      .put("requestId", requestId)
      .put("payload", payload)
      .encode());
  }

  private void sendError(ServerWebSocket socket, String requestId, String message) {
    writeIfOpen(socket, new JsonObject()
      .put("type", "response")
      .put("requestId", requestId)
      .put("error", message == null ? "Command failed" : message)
      .encode());
  }
}
