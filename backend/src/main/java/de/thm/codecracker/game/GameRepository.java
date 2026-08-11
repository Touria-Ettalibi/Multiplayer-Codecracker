package de.thm.codecracker.game;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import de.thm.codecracker.game.model.Game;
import de.thm.codecracker.game.model.GamePlayer;
import de.thm.codecracker.game.model.Guess;
import de.thm.codecracker.game.model.Round;
import io.vertx.core.Future;
import io.vertx.mysqlclient.MySQLClient;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

/**
 * Provides database access for Games, their players, rounds, and guesses.
 *
 * <p>Follows the same convention as {@link de.thm.codecracker.auth.UserRepository}:
 * parameterized queries only, and every timestamp is converted to Unix epoch
 * milliseconds when mapped out of a row.</p>
 */
public class GameRepository {
  private final Pool pool;

  /**
   * Creates a new Game repository.
   *
   * @param pool the SQL connection pool used for database access
   */
  public GameRepository(Pool pool) {
    this.pool = pool;
  }

  /**
   * Creates a new Game, already {@code IN_PROGRESS}, along with its players
   * and first round, in one connection.
   *
   * <p>There is deliberately no separate {@code WAITING} state created
   * here: by the time this is called, the invite/ready-check flow has
   * already decided who is playing, so the Game starts life directly as
   * {@code IN_PROGRESS} with round 1 already open.</p>
   *
   * @param secretCode            the freshly generated secret code
   * @param maxRounds             the maximum number of rounds
   * @param roundTimeLimitSeconds the time limit per round, in seconds
   * @param playerUserIds         the IDs of the Users who confirmed readiness
   * @return a future containing the created Game
   */
  public Future<Game> createGame(
    String secretCode,
    int maxRounds,
    int roundTimeLimitSeconds,
    List<Long> playerUserIds
  ) {
    String insertGameSql = """
      INSERT INTO games (status, secret_code, max_rounds, round_time_limit_seconds, started_at)
      VALUES ('IN_PROGRESS', ?, ?, ?, NOW())
      """;

    RowSet<Row> gameResult = pool.preparedQuery(insertGameSql)
      .execute(Tuple.of(secretCode, maxRounds, roundTimeLimitSeconds))
      .await();

    Long gameId = gameResult.property(MySQLClient.LAST_INSERTED_ID);

    String insertPlayerSql = """
      INSERT INTO game_players (game_id, user_id, ready, result, points)
      VALUES (?, ?, TRUE, 'PENDING', 0)
      """;

    for (Long userId : playerUserIds) {
      pool.preparedQuery(insertPlayerSql)
        .execute(Tuple.of(gameId, userId))
        .await();
    }

    insertRound(gameId, 1).await();

    return findGameById(gameId);
  }

  /**
   * Finds a Game by its ID.
   *
   * @param id the Game's ID
   * @return a future containing the Game, or {@code null} if it does not exist
   */
  public Future<Game> findGameById(Long id) {
    String sql = """
      SELECT id, status, secret_code, max_rounds, round_time_limit_seconds,
             started_at, ended_at, created_at
      FROM games
      WHERE id = ?
      """;

    RowSet<Row> rows = pool.preparedQuery(sql)
      .execute(Tuple.of(id))
      .await();

    return Future.succeededFuture(firstOrNull(rows, this::toGame));
  }

  /**
   * Marks a Game as finished.
   *
   * @param id the Game's ID
   */
  public Future<Void> finishGame(Long id) {
    String sql = "UPDATE games SET status = 'FINISHED', ended_at = NOW() WHERE id = ?";

    pool.preparedQuery(sql).execute(Tuple.of(id)).await();

    return Future.succeededFuture();
  }

  /**
   * Finds every player of a Game, joined with their username.
   *
   * @param gameId the Game's ID
   * @return a future containing the Game's players
   */
  public Future<List<GamePlayer>> findPlayersByGameId(Long gameId) {
    String sql = """
      SELECT gp.id, gp.game_id, gp.user_id, u.username, gp.ready, gp.result, gp.points, gp.joined_at
      FROM game_players gp
      JOIN users u ON u.id = gp.user_id
      WHERE gp.game_id = ?
      ORDER BY gp.id
      """;

    RowSet<Row> rows = pool.preparedQuery(sql)
      .execute(Tuple.of(gameId))
      .await();

    List<GamePlayer> players = rows.stream()
      .map(this::toGamePlayer)
      .toList();

    return Future.succeededFuture(players);
  }

  /**
   * Finds one player's participation in a Game.
   *
   * @param gameId the Game's ID
   * @param userId the User's ID
   * @return a future containing the GamePlayer, or {@code null} if the User is not in this Game
   */
  public Future<GamePlayer> findPlayer(Long gameId, Long userId) {
    String sql = """
      SELECT gp.id, gp.game_id, gp.user_id, u.username, gp.ready, gp.result, gp.points, gp.joined_at
      FROM game_players gp
      JOIN users u ON u.id = gp.user_id
      WHERE gp.game_id = ? AND gp.user_id = ?
      """;

    RowSet<Row> rows = pool.preparedQuery(sql)
      .execute(Tuple.of(gameId, userId))
      .await();

    return Future.succeededFuture(firstOrNull(rows, this::toGamePlayer));
  }

  /**
   * Updates a player's result and adds to their points for this Game.
   *
   * @param gameId      the Game's ID
   * @param userId      the User's ID
   * @param result      the new result ({@code WIN}/{@code DRAW}/{@code LOSS})
   * @param pointsDelta the points to add (may be negative, e.g. for a loss)
   */
  public Future<Void> updatePlayerResult(Long gameId, Long userId, String result, int pointsDelta) {
    String sql = """
      UPDATE game_players
      SET result = ?, points = points + ?
      WHERE game_id = ? AND user_id = ?
      """;

    pool.preparedQuery(sql)
      .execute(Tuple.of(result, pointsDelta, gameId, userId))
      .await();

    return Future.succeededFuture();
  }

  /**
   * Creates a new Round.
   *
   * @param gameId      the Game's ID
   * @param roundNumber the 1-based round number
   * @return a future containing the created Round
   */
  public Future<Round> insertRound(Long gameId, int roundNumber) {
    String sql = """
      INSERT INTO rounds (game_id, round_number, started_at)
      VALUES (?, ?, NOW())
      """;

    RowSet<Row> result = pool.preparedQuery(sql)
      .execute(Tuple.of(gameId, roundNumber))
      .await();

    Long roundId = result.property(MySQLClient.LAST_INSERTED_ID);

    return findRoundById(roundId);
  }

  /**
   * Finds a Round by its ID.
   *
   * @param id the Round's ID
   * @return a future containing the Round, or {@code null} if it does not exist
   */
  public Future<Round> findRoundById(Long id) {
    String sql = """
      SELECT id, game_id, round_number, started_at, ended_at
      FROM rounds
      WHERE id = ?
      """;

    RowSet<Row> rows = pool.preparedQuery(sql)
      .execute(Tuple.of(id))
      .await();

    return Future.succeededFuture(firstOrNull(rows, this::toRound));
  }

  /**
   * Finds the most recent (current) Round of a Game.
   *
   * @param gameId the Game's ID
   * @return a future containing the current Round, or {@code null} if the Game has no rounds yet
   */
  public Future<Round> findCurrentRound(Long gameId) {
    String sql = """
      SELECT id, game_id, round_number, started_at, ended_at
      FROM rounds
      WHERE game_id = ?
      ORDER BY round_number DESC
      LIMIT 1
      """;

    RowSet<Row> rows = pool.preparedQuery(sql)
      .execute(Tuple.of(gameId))
      .await();

    return Future.succeededFuture(firstOrNull(rows, this::toRound));
  }

  /**
   * Marks a Round as ended, but only if it has not already been ended.
   *
   * <p>This is the guard that makes it safe for two different triggers —
   * "every active player just submitted" and "the round-timeout timer
   * fired" — to race against each other for the same round: only one of
   * them will ever see {@code true} come back from this call.</p>
   *
   * @param id the Round's ID
   * @return a future containing {@code true} if this call is the one that
   *         closed the round, {@code false} if it was already closed
   */
  public Future<Boolean> endRound(Long id) {
    String sql = "UPDATE rounds SET ended_at = NOW() WHERE id = ? AND ended_at IS NULL";

    RowSet<Row> result = pool.preparedQuery(sql).execute(Tuple.of(id)).await();

    return Future.succeededFuture(result.rowCount() > 0);
  }

  /**
   * Records one User's guess for a Round.
   *
   * @param roundId              the Round's ID
   * @param userId               the guessing User's ID
   * @param guessedCode          the submitted code, or {@code null} if nothing was submitted
   * @param correctPositionCount how many colors are correct and in the correct position,
   *                             or {@code null} if the guess is invalid
   * @param correctColorCount    how many further colors are correct but misplaced,
   *                             or {@code null} if the guess is invalid
   * @param isValid              whether this counts as a real attempt
   * @return a future containing the created Guess
   */
  public Future<Guess> insertGuess(
    Long roundId,
    Long userId,
    String guessedCode,
    Integer correctPositionCount,
    Integer correctColorCount,
    boolean isValid
  ) {
    String sql = """
      INSERT INTO guesses (round_id, user_id, guessed_code, correct_position_count, correct_color_count, is_valid)
      VALUES (?, ?, ?, ?, ?, ?)
      """;

    RowSet<Row> result = pool.preparedQuery(sql)
      .execute(Tuple.of(roundId, userId, guessedCode, correctPositionCount, correctColorCount, isValid))
      .await();

    Long guessId = result.property(MySQLClient.LAST_INSERTED_ID);

    return findGuessById(guessId);
  }

  /**
   * Finds a Guess by its ID.
   *
   * @param id the Guess's ID
   * @return a future containing the Guess, or {@code null} if it does not exist
   */
  public Future<Guess> findGuessById(Long id) {
    String sql = """
      SELECT id, round_id, user_id, guessed_code, correct_position_count, correct_color_count, is_valid, submitted_at
      FROM guesses
      WHERE id = ?
      """;

    RowSet<Row> rows = pool.preparedQuery(sql)
      .execute(Tuple.of(id))
      .await();

    return Future.succeededFuture(firstOrNull(rows, this::toGuess));
  }

  /**
   * Finds a User's guess for a specific Round, if they already submitted one.
   *
   * @param roundId the Round's ID
   * @param userId  the User's ID
   * @return a future containing the Guess, or {@code null} if the User has not submitted yet
   */
  public Future<Guess> findGuess(Long roundId, Long userId) {
    String sql = """
      SELECT id, round_id, user_id, guessed_code, correct_position_count, correct_color_count, is_valid, submitted_at
      FROM guesses
      WHERE round_id = ? AND user_id = ?
      """;

    RowSet<Row> rows = pool.preparedQuery(sql)
      .execute(Tuple.of(roundId, userId))
      .await();

    return Future.succeededFuture(firstOrNull(rows, this::toGuess));
  }

  /**
   * Finds every guess submitted in a Round.
   *
   * @param roundId the Round's ID
   * @return a future containing the Round's guesses
   */
  public Future<List<Guess>> findGuessesByRoundId(Long roundId) {
    String sql = """
      SELECT id, round_id, user_id, guessed_code, correct_position_count, correct_color_count, is_valid, submitted_at
      FROM guesses
      WHERE round_id = ?
      """;

    RowSet<Row> rows = pool.preparedQuery(sql)
      .execute(Tuple.of(roundId))
      .await();

    List<Guess> guesses = rows.stream()
      .map(this::toGuess)
      .toList();

    return Future.succeededFuture(guesses);
  }

  /**
   * Finds one User's full guess history for a Game, oldest round first —
   * the data behind the "Codehistorie" panel.
   *
   * @param gameId the Game's ID
   * @param userId the User's ID
   * @return a future containing the User's guesses across every round of this Game,
   *         each paired with its round number
   */
  public Future<List<RoundGuess>> findGuessHistory(Long gameId, Long userId) {
    String sql = """
      SELECT r.round_number, g.id, g.round_id, g.user_id, g.guessed_code,
             g.correct_position_count, g.correct_color_count, g.is_valid, g.submitted_at
      FROM guesses g
      JOIN rounds r ON r.id = g.round_id
      WHERE r.game_id = ? AND g.user_id = ?
      ORDER BY r.round_number
      """;

    RowSet<Row> rows = pool.preparedQuery(sql)
      .execute(Tuple.of(gameId, userId))
      .await();

    List<RoundGuess> history = rows.stream()
      .map(row -> new RoundGuess(row.getInteger("round_number"), toGuess(row)))
      .toList();

    return Future.succeededFuture(history);
  }

  /**
   * Sums a User's points across every Game they've played, floored at 0 —
   * the "Gesamtpunkte" total shown on the game-over screen and the Highscore.
   *
   * @param userId the User's ID
   * @return a future containing the User's total points, never negative
   */
  public Future<Integer> findTotalPoints(Long userId) {
    String sql = "SELECT COALESCE(SUM(points), 0) AS total FROM game_players WHERE user_id = ?";

    RowSet<Row> rows = pool.preparedQuery(sql)
      .execute(Tuple.of(userId))
      .await();

    int total = rows.iterator().next().getInteger("total");
    return Future.succeededFuture(Math.max(0, total));
  }

  /**
   * Pairs a Guess with the round number it was submitted in, for history views.
   *
   * @param roundNumber the 1-based round number
   * @param guess       the Guess submitted in that round
   */
  public record RoundGuess(int roundNumber, Guess guess) {
  }

  private Game toGame(Row row) {
    return new Game(
      row.getLong("id"),
      row.getString("status"),
      row.getString("secret_code"),
      row.getInteger("max_rounds"),
      row.getInteger("round_time_limit_seconds"),
      toEpochMillisOrNull(row.getLocalDateTime("started_at")),
      toEpochMillisOrNull(row.getLocalDateTime("ended_at")),
      toEpochMillis(row.getLocalDateTime("created_at"))
    );
  }

  private GamePlayer toGamePlayer(Row row) {
    return new GamePlayer(
      row.getLong("id"),
      row.getLong("game_id"),
      row.getLong("user_id"),
      row.getString("username"),
      row.getBoolean("ready"),
      row.getString("result"),
      row.getInteger("points"),
      toEpochMillis(row.getLocalDateTime("joined_at"))
    );
  }

  private Round toRound(Row row) {
    return new Round(
      row.getLong("id"),
      row.getLong("game_id"),
      row.getInteger("round_number"),
      toEpochMillis(row.getLocalDateTime("started_at")),
      toEpochMillisOrNull(row.getLocalDateTime("ended_at"))
    );
  }

  private Guess toGuess(Row row) {
    return new Guess(
      row.getLong("id"),
      row.getLong("round_id"),
      row.getLong("user_id"),
      row.getString("guessed_code"),
      row.getInteger("correct_position_count"),
      row.getInteger("correct_color_count"),
      row.getBoolean("is_valid"),
      toEpochMillis(row.getLocalDateTime("submitted_at"))
    );
  }

  private <T> T firstOrNull(RowSet<Row> rows, java.util.function.Function<Row, T> mapper) {
    var iterator = rows.iterator();
    return iterator.hasNext() ? mapper.apply(iterator.next()) : null;
  }

  /**
   * Converts a local date and time to Unix epoch milliseconds in UTC.
   *
   * @param dateTime the local date and time
   * @return the Unix timestamp in milliseconds
   */
  private long toEpochMillis(LocalDateTime dateTime) {
    return dateTime
      .toInstant(ZoneOffset.UTC)
      .toEpochMilli();
  }

  /**
   * Same as {@link #toEpochMillis}, but tolerant of a {@code null} column
   * (e.g. {@code ended_at} before a Game/Round has ended).
   *
   * @param dateTime the local date and time, or {@code null}
   * @return the Unix timestamp in milliseconds, or {@code null}
   */
  private Long toEpochMillisOrNull(LocalDateTime dateTime) {
    return dateTime == null ? null : toEpochMillis(dateTime);
  }
}
