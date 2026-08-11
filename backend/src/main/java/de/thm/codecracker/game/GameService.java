package de.thm.codecracker.game;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import de.thm.codecracker.game.model.Game;
import de.thm.codecracker.game.model.GamePlayer;
import de.thm.codecracker.game.model.Guess;
import de.thm.codecracker.game.model.Round;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements the Codecracker game rules on top of {@link GameRepository}.
 *
 * <p>Follows the same "Service publishes to the Vert.x EventBus, a
 * WebSocket controller just subscribes and forwards" pattern already used
 * by {@link de.thm.codecracker.todo.TodoService}. Two kinds of events are
 * published:</p>
 * <ul>
 *   <li><b>Public</b>, on address {@code "game:" + gameId} — round-started,
 *       round-ended, player-forfeited, game-ended. Everyone connected to
 *       that Game receives these.</li>
 *   <li><b>Private</b>, on address {@code "game-guess:" + userId} — the
 *       feedback for one player's own guess. Only that player's socket
 *       subscribes to their own address, so guess feedback never leaks to
 *       opponents.</li>
 * </ul>
 *
 * <p>Round timing: each round schedules exactly one {@link Vertx#setTimer}
 * call. If every active player submits before it fires, the round is
 * concluded early and the pending timer is cancelled. If a player never
 * submits at all (closed tab, crashed client), the timer is the only thing
 * that guarantees the round still ends. Ending a round is guarded by an
 * atomic {@code UPDATE ... WHERE ended_at IS NULL} in the repository, so
 * the "everyone just submitted" path and the "timer fired" path can never
 * both conclude the same round.</p>
 */
public class GameService {
  private static final Logger LOGGER = LoggerFactory.getLogger(GameService.class);

  /**
   * Fixed at 4 to match the base configuration in the assignment. A future
   * difficulty option (more colors, configurable length) would need a
   * column on {@code games} and to thread that value through here instead
   * of this constant — {@link GuessEvaluator} itself already supports any
   * length and color set, so that extension would not require touching it.
   */
  static final int CODE_LENGTH = 4;
  static final Set<Character> ALLOWED_COLORS = Set.of('R', 'B', 'G', 'Y');

  private static final int DEFAULT_MAX_ROUNDS = 10;
  private static final int DEFAULT_ROUND_TIME_LIMIT_SECONDS = 30;

  private static final int POINTS_WIN = 3;
  private static final int POINTS_DRAW = 1;
  private static final int POINTS_LOSS = -1;

  private final Vertx vertx;
  private final GameRepository gameRepository;
  private final SecureRandom random = new SecureRandom();

  /** One pending round-timeout timer per currently active Game. */
  private final Map<Long, Long> roundTimersByGameId = new ConcurrentHashMap<>();

  /**
   * Creates a new Game service.
   *
   * @param vertx          used to schedule round-timeout timers and publish EventBus events
   * @param gameRepository the repository used for Game persistence
   */
  public GameService(Vertx vertx, GameRepository gameRepository) {
    this.vertx = vertx;
    this.gameRepository = gameRepository;
  }

  /**
   * Starts a new Game for the given players: generates the secret code,
   * persists the Game/players/first Round, and schedules the first round's
   * timeout.
   *
   * <p>This is the hand-off point from the invite/ready-check flow: once
   * it has decided who is playing, it calls this method and gets back the
   * Game those players should be redirected to.</p>
   *
   * @param playerUserIds the IDs of the Users who confirmed readiness
   * @return a future containing the created, already {@code IN_PROGRESS} Game
   * @throws IllegalArgumentException if fewer than two players are given
   */
  public Future<Game> startGame(List<Long> playerUserIds) {
    if (playerUserIds == null || playerUserIds.size() < 2) {
      throw new IllegalArgumentException("A game needs at least two players");
    }

    String secretCode = generateSecretCode();
    Game game = gameRepository.createGame(
      secretCode, DEFAULT_MAX_ROUNDS, DEFAULT_ROUND_TIME_LIMIT_SECONDS, playerUserIds
    ).await();

    scheduleRoundTimer(game.id(), game.roundTimeLimitSeconds());

    return Future.succeededFuture(game);
  }

  /**
   * Submits one player's guess for the current round.
   *
   * @param gameId  the Game's ID
   * @param userId  the guessing User's ID
   * @param rawCode the submitted code, e.g. {@code "RBGY"}; may be incomplete
   *                or {@code null} (an auto-submit of an empty selection at timeout)
   * @return a future containing the persisted Guess (feedback included)
   * @throws GameNotFoundException if the Game does not exist
   * @throws InvalidGuessException if the Game/round is not active, the User is
   *                                not an active player in this Game, or they
   *                                already submitted a guess for this round
   */
  public Future<Guess> submitGuess(Long gameId, Long userId, String rawCode) {
    Game game = requireInProgressGame(gameId);
    requireActivePlayer(game, userId);

    Round round = gameRepository.findCurrentRound(gameId).await();
    if (round == null || round.endedAt() != null) {
      throw new InvalidGuessException("This round has already ended");
    }

    Guess existing = gameRepository.findGuess(round.id(), userId).await();
    if (existing != null) {
      throw new InvalidGuessException("You have already submitted a guess for this round");
    }

    boolean isValid = isValidCode(rawCode);
    GuessEvaluator.Feedback feedback = isValid
      ? GuessEvaluator.evaluate(game.secretCode(), rawCode)
      : null;

    Guess guess = gameRepository.insertGuess(
      round.id(),
      userId,
      isValid ? rawCode : null,
      feedback == null ? null : feedback.correctPosition(),
      feedback == null ? null : feedback.correctColor(),
      isValid
    ).await();

    publishPrivateGuessResult(userId, guess);

    if (allActivePlayersHaveSubmitted(gameId, round.id())) {
      concludeRound(gameId);
    }

    return Future.succeededFuture(guess);
  }

  /**
   * Forfeits a Game for one player: immediately records a {@code LOSS} for
   * them, removing them from future rounds (per the README: forfeiting
   * counts as losing).
   *
   * @param gameId the Game's ID
   * @param userId the forfeiting User's ID
   * @throws GameNotFoundException if the Game does not exist
   * @throws InvalidGuessException if the Game is not active or the User is
   *                                not an active player in it
   */
  public Future<Void> forfeit(Long gameId, Long userId) {
    Game game = requireInProgressGame(gameId);
    GamePlayer player = requireActivePlayer(game, userId);

    gameRepository.updatePlayerResult(gameId, userId, GamePlayer.RESULT_LOSS, POINTS_LOSS).await();

    publishPublicEvent(gameId, "player-forfeited", new JsonObject()
      .put("userId", player.userId())
      .put("username", player.username()));

    List<GamePlayer> stillActive = gameRepository.findPlayersByGameId(gameId).await().stream()
      .filter(GamePlayer::isActive)
      .toList();

    if (stillActive.isEmpty()) {
      // Everyone has forfeited or already finished — nothing left to play for.
      cancelScheduledTimer(gameId);
      gameRepository.finishGame(gameId).await();
      publishPublicEvent(gameId, "game-ended", new JsonObject().put("results", new JsonArray()));
      return Future.succeededFuture();
    }

    Round round = gameRepository.findCurrentRound(gameId).await();
    if (round != null && round.endedAt() == null && allActivePlayersHaveSubmitted(gameId, round.id())) {
      concludeRound(gameId);
    }

    return Future.succeededFuture();
  }

  /**
   * Concludes the current round of a Game: evaluates who (if anyone) guessed
   * the secret code exactly, then either finishes the Game or advances to
   * the next round.
   *
   * <p>Called from two places: {@link #submitGuess} when it detects every
   * active player has now submitted (early conclusion), and the scheduled
   * round-timeout timer (forced conclusion regardless of who submitted).
   * The {@code UPDATE ... WHERE ended_at IS NULL} guard in
   * {@link GameRepository#endRound} makes it safe for both to race — only
   * one of them will actually proceed past that point for a given round.</p>
   *
   * @param gameId the Game's ID
   */
  private void concludeRound(Long gameId) {
    Game game = gameRepository.findGameById(gameId).await();
    if (game == null || !Game.STATUS_IN_PROGRESS.equals(game.status())) {
      return; // already finished by another path
    }

    Round round = gameRepository.findCurrentRound(gameId).await();
    if (round == null || round.endedAt() != null) {
      return;
    }

    boolean closedNow = gameRepository.endRound(round.id()).await();
    if (!closedNow) {
      return; // the other trigger already concluded this exact round
    }

    cancelScheduledTimer(gameId);

    List<GamePlayer> activePlayers = gameRepository.findPlayersByGameId(gameId).await().stream()
      .filter(GamePlayer::isActive)
      .toList();

    List<Guess> guesses = gameRepository.findGuessesByRoundId(round.id()).await();

    Set<Long> winnerUserIds = guesses.stream()
      .filter(guess -> guess.isValid() && Objects.equals(guess.correctPositionCount(), CODE_LENGTH))
      .map(Guess::userId)
      .collect(Collectors.toSet());

    publishPublicEvent(gameId, "round-ended", new JsonObject().put("roundNumber", round.roundNumber()));

    boolean noOneLeftToPlay = activePlayers.isEmpty();
    boolean lastRoundReached = round.roundNumber() >= game.maxRounds();

    if (!winnerUserIds.isEmpty() || lastRoundReached || noOneLeftToPlay) {
      finalizeGame(gameId, activePlayers, winnerUserIds);
    } else {
      startNextRound(gameId, round.roundNumber() + 1, game.roundTimeLimitSeconds());
    }
  }

  /**
   * Assigns final results and points to every still-active player, marks
   * the Game as finished, and publishes the {@code "game-ended"} event
   * with the full results table for the game-over screen.
   *
   * @param gameId         the Game's ID
   * @param activePlayers  players who had not already forfeited
   * @param winnerUserIds  IDs of players who guessed the secret code exactly
   *                        this round (empty if nobody did)
   */
  private void finalizeGame(Long gameId, List<GamePlayer> activePlayers, Set<Long> winnerUserIds) {
    String resultForWinners = winnerUserIds.size() == 1 ? GamePlayer.RESULT_WIN : GamePlayer.RESULT_DRAW;
    int pointsForWinners = winnerUserIds.size() == 1 ? POINTS_WIN : POINTS_DRAW;

    for (GamePlayer player : activePlayers) {
      boolean isWinner = winnerUserIds.contains(player.userId());
      String result = isWinner ? resultForWinners : GamePlayer.RESULT_LOSS;
      int points = isWinner ? pointsForWinners : POINTS_LOSS;

      gameRepository.updatePlayerResult(gameId, player.userId(), result, points).await();
    }

    gameRepository.finishGame(gameId).await();

    List<GamePlayer> finalPlayers = gameRepository.findPlayersByGameId(gameId).await();
    JsonArray results = new JsonArray(finalPlayers.stream()
      .map(this::toResultRowJson)
      .toList());

    publishPublicEvent(gameId, "game-ended", new JsonObject().put("results", results));
  }

  /**
   * Creates the next round and schedules its timeout, then publishes
   * {@code "round-started"} so connected clients can reset their countdown.
   *
   * @param gameId                the Game's ID
   * @param nextRoundNumber       the 1-based number of the round being started
   * @param roundTimeLimitSeconds the time limit for the new round
   */
  private void startNextRound(Long gameId, int nextRoundNumber, int roundTimeLimitSeconds) {
    Round round = gameRepository.insertRound(gameId, nextRoundNumber).await();

    publishPublicEvent(gameId, "round-started", new JsonObject()
      .put("roundNumber", round.roundNumber())
      .put("roundStartedAt", round.startedAt())
      .put("roundTimeLimitSeconds", roundTimeLimitSeconds));

    scheduleRoundTimer(gameId, roundTimeLimitSeconds);
  }

  private boolean allActivePlayersHaveSubmitted(Long gameId, Long roundId) {
    Set<Long> activeUserIds = gameRepository.findPlayersByGameId(gameId).await().stream()
      .filter(GamePlayer::isActive)
      .map(GamePlayer::userId)
      .collect(Collectors.toSet());

    Set<Long> submittedUserIds = gameRepository.findGuessesByRoundId(roundId).await().stream()
      .map(Guess::userId)
      .collect(Collectors.toSet());

    return activeUserIds.stream().allMatch(submittedUserIds::contains);
  }

  private void scheduleRoundTimer(Long gameId, int roundTimeLimitSeconds) {
    long timerId = vertx.setTimer(roundTimeLimitSeconds * 1000L, firedTimerId -> {
      try {
        concludeRound(gameId);
      } catch (Exception exception) {
        LOGGER.error("Unexpected error while concluding round on timeout for game {}", gameId, exception);
      }
    });

    roundTimersByGameId.put(gameId, timerId);
  }

  private void cancelScheduledTimer(Long gameId) {
    Long timerId = roundTimersByGameId.remove(gameId);
    if (timerId != null) {
      vertx.cancelTimer(timerId);
    }
  }

  private Game requireInProgressGame(Long gameId) {
    Game game = gameRepository.findGameById(gameId).await();

    if (game == null) {
      throw new GameNotFoundException();
    }

    if (!Game.STATUS_IN_PROGRESS.equals(game.status())) {
      throw new InvalidGuessException("This game is not in progress");
    }

    return game;
  }

  private GamePlayer requireActivePlayer(Game game, Long userId) {
    GamePlayer player = gameRepository.findPlayer(game.id(), userId).await();

    if (player == null) {
      throw new InvalidGuessException("You are not a player in this game");
    }

    if (!player.isActive()) {
      throw new InvalidGuessException("You have already forfeited this game");
    }

    return player;
  }

  private boolean isValidCode(String code) {
    if (code == null || code.length() != CODE_LENGTH) {
      return false;
    }

    return code.chars().allMatch(c -> ALLOWED_COLORS.contains((char) c));
  }

  private String generateSecretCode() {
    char[] colors = new char[ALLOWED_COLORS.size()];
    int index = 0;
    for (char color : ALLOWED_COLORS) {
      colors[index++] = color;
    }

    StringBuilder code = new StringBuilder(CODE_LENGTH);
    for (int i = 0; i < CODE_LENGTH; i++) {
      code.append(colors[random.nextInt(colors.length)]);
    }

    return code.toString();
  }

  private void publishPublicEvent(Long gameId, String type, Object payload) {
    vertx.eventBus().publish("game:" + gameId, new JsonObject()
      .put("type", type)
      .put("payload", payload)
      .encode());
  }

  private void publishPrivateGuessResult(Long userId, Guess guess) {
    vertx.eventBus().publish("game-guess:" + userId, new JsonObject()
      .put("type", "guess-result")
      .put("payload", guessToJson(guess))
      .encode());
  }

  /**
   * Converts a Guess into the JSON shape sent to its own submitter.
   *
   * @param guess the Guess to convert
   * @return the JSON representation
   */
  JsonObject guessToJson(Guess guess) {
    return new JsonObject()
      .put("isValid", guess.isValid())
      .put("guessedCode", guess.guessedCode())
      .put("correctPosition", guess.correctPositionCount())
      .put("correctColor", guess.correctColorCount())
      .put("submittedAt", guess.submittedAt());
  }

  /**
   * Converts a final GamePlayer into one row of the game-over results table,
   * including their all-time total points (floored at 0) across every Game.
   *
   * @param player the GamePlayer to convert
   * @return the JSON representation
   */
  private JsonObject toResultRowJson(GamePlayer player) {
    int totalPoints = gameRepository.findTotalPoints(player.userId()).await();

    return new JsonObject()
      .put("userId", player.userId())
      .put("username", player.username())
      .put("result", player.result())
      .put("pointsThisGame", player.points())
      .put("totalPoints", totalPoints);
  }
}
