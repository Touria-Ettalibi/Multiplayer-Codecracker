package de.thm.codecracker.game;

import java.util.List;

import de.thm.codecracker.game.GameRepository.RoundGuess;
import de.thm.codecracker.game.model.Game;
import de.thm.codecracker.game.model.GamePlayer;
import de.thm.codecracker.game.model.Guess;
import de.thm.codecracker.game.model.Round;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Handles the REST endpoint used to load (or reload) the state of a single Game.
 *
 * <p>Mirrors the same "no initial data over the socket, fetch a snapshot
 * over REST first" convention already used for the Todo and Lobby
 * features. This is what lets a page refresh mid-game recover the current
 * round number, remaining time, and the caller's own guess history,
 * instead of waiting on the next server-pushed WebSocket event.</p>
 *
 * <p>Only ever returns the calling User's <em>own</em> guess history —
 * never another player's — since the game rules require that feedback
 * stays private to the player who submitted it.</p>
 */
public class GameController {
  private static final Logger LOGGER = LoggerFactory.getLogger(GameController.class);

  private final GameRepository gameRepository;
  private final GameService gameService;

  /**
   * Creates a new Game controller.
   *
   * @param gameRepository the repository used to read Game state
   * @param gameService    used only by the temporary {@code /api/games/dev-start}
   *                        endpoint — see its Javadoc below
   */
  public GameController(GameRepository gameRepository, GameService gameService) {
    this.gameRepository = gameRepository;
    this.gameService = gameService;
  }

  /**
   * Registers the Game snapshot route on the given router.
   *
   * @param router the Vert.x router used to register the route
   */
  public void registerRoutes(Router router) {
    router.get("/api/games/:id").handler(this::findById);
    router.post("/api/games/dev-start").handler(this::devStart);
  }

  /**
   * TEMPORARY, dev-only stand-in for the real invite/ready-check flow.
   *
   * <p><strong>Delete this endpoint once {@code feature/game-invite} is
   * merged.</strong> It exists only so the game page can be built and
   * tested end-to-end before that flow is ready: it directly calls
   * {@link GameService#startGame}, the same method the real invite flow
   * is expected to call once it has decided who is playing. There is
   * intentionally no admin/role restriction here beyond being logged in —
   * tightening that is pointless work on a route that is going away.</p>
   *
   * <p>Request body: {@code {"userIds": [1, 2]}}. Response: the created
   * Game's {@code id}, for the caller to redirect to
   * {@code /pages/game.html?id=<id>}.</p>
   *
   * @param ctx the current routing context
   */
  private void devStart(RoutingContext ctx) {
    try {
      JsonObject request = ctx.body().asJsonObject();
      List<Long> userIds = request.getJsonArray("userIds").stream()
        .map(id -> ((Number) id).longValue())
        .toList();

      Game game = gameService.startGame(userIds).await();

      ctx.response()
        .setStatusCode(201)
        .putHeader("content-type", "application/json")
        .end(new JsonObject().put("id", game.id()).encode());
    } catch (IllegalArgumentException exception) {
      sendError(ctx, 400, exception.getMessage());
    } catch (Exception exception) {
      LOGGER.error("Unexpected error while starting dev game", exception);
      sendError(ctx, 500, "Unexpected server error");
    }
  }

  private void findById(RoutingContext ctx) {
    try {
      Long gameId = parseId(ctx);
      Long callerUserId = ctx.user().principal().getLong("uid");

      Game game = gameRepository.findGameById(gameId).await();
      if (game == null) {
        sendError(ctx, 404, "Game not found");
        return;
      }

      GamePlayer caller = gameRepository.findPlayer(gameId, callerUserId).await();
      if (caller == null) {
        // Not a participant in this game — treat it the same as "doesn't
        // exist" rather than confirming details about a game they're not part of.
        sendError(ctx, 404, "Game not found");
        return;
      }

      JsonObject response = toSnapshotJson(game, caller, callerUserId);

      ctx.response()
        .putHeader("content-type", "application/json")
        .end(response.encode());
    } catch (IllegalArgumentException exception) {
      sendError(ctx, 400, exception.getMessage());
    } catch (Exception exception) {
      LOGGER.error("Unexpected error while loading game snapshot", exception);
      sendError(ctx, 500, "Unexpected server error");
    }
  }

  private JsonObject toSnapshotJson(Game game, GamePlayer caller, Long callerUserId) {
    Round currentRound = gameRepository.findCurrentRound(game.id()).await();
    List<RoundGuess> history = gameRepository.findGuessHistory(game.id(), callerUserId).await();

    JsonObject response = new JsonObject()
      .put("id", game.id())
      .put("status", game.status())
      .put("maxRounds", game.maxRounds())
      .put("roundTimeLimitSeconds", game.roundTimeLimitSeconds())
      .put("myResult", caller.result())
      .put("history", historyToJson(history));

    if (currentRound != null) {
      response
        .put("roundNumber", currentRound.roundNumber())
        .put("roundStartedAt", currentRound.startedAt())
        .put("roundEnded", currentRound.endedAt() != null);
    }

    if (Game.STATUS_FINISHED.equals(game.status())) {
      List<GamePlayer> allPlayers = gameRepository.findPlayersByGameId(game.id()).await();
      response.put("results", resultsToJson(allPlayers));
    }

    return response;
  }

  private JsonArray historyToJson(List<RoundGuess> history) {
    return new JsonArray(history.stream()
      .map(entry -> {
        Guess guess = entry.guess();
        return new JsonObject()
          .put("roundNumber", entry.roundNumber())
          .put("guessedCode", guess.guessedCode())
          .put("correctPosition", guess.correctPositionCount())
          .put("correctColor", guess.correctColorCount())
          .put("isValid", guess.isValid());
      })
      .toList());
  }

  private JsonArray resultsToJson(List<GamePlayer> players) {
    return new JsonArray(players.stream()
      .map(player -> {
        int totalPoints = gameRepository.findTotalPoints(player.userId()).await();
        return new JsonObject()
          .put("userId", player.userId())
          .put("username", player.username())
          .put("result", player.result())
          .put("pointsThisGame", player.points())
          .put("totalPoints", totalPoints);
      })
      .toList());
  }

  private Long parseId(RoutingContext ctx) {
    try {
      return Long.parseLong(ctx.pathParam("id"));
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Invalid game id");
    }
  }

  private void sendError(RoutingContext ctx, int statusCode, String message) {
    ctx.response()
      .setStatusCode(statusCode)
      .putHeader("content-type", "application/json")
      .end(new JsonObject().put("error", message).encode());
  }
}
