package de.thm.codecracker.auth;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import de.thm.codecracker.auth.model.User;
import io.vertx.core.Future;
import io.vertx.mysqlclient.MySQLClient;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

/**
 * Provides database access for User operations.
 *
 * <p>The repository executes SQL queries using the Vert.x SQL client and maps
 * database rows to {@link User} domain objects.</p>
 */
public class UserRepository {
  private final Pool pool;

  /**
   * Creates a new User repository.
   *
   * @param pool the SQL connection pool used for database access
   */
  public UserRepository(Pool pool) {
    this.pool = pool;
  }

  /**
   * Inserts a new User and returns the persisted domain object.
   *
   * @param username     the username
   * @param passwordHash the bcrypt hash of the password
   * @param role         the user's role
   * @return a future containing the created User
   */
  public Future<User> create(String username, String passwordHash, String role) {
    String sql = """
      INSERT INTO users (username, password_hash, role)
      VALUES (?, ?, ?)
      """;

    RowSet<Row> result = pool.preparedQuery(sql)
      .execute(Tuple.of(username, passwordHash, role))
      .await();

    Long id = result.property(MySQLClient.LAST_INSERTED_ID);
    User user = findById(id).await();

    return Future.succeededFuture(user);
  }

  /**
   * Finds a User by its ID.
   *
   * @param id the user ID
   * @return a future containing the User, or {@code null} if it does not exist
   */
  public Future<User> findById(Long id) {
    String sql = """
      SELECT id, username, password_hash, role, created_at
      FROM users
      WHERE id = ?
      """;

    RowSet<Row> rows = pool.preparedQuery(sql)
      .execute(Tuple.of(id))
      .await();

    return Future.succeededFuture(firstOrNull(rows));
  }

  /**
   * Finds a User by its username.
   *
   * @param username the username
   * @return a future containing the User, or {@code null} if it does not exist
   */
  public Future<User> findByUsername(String username) {
    String sql = """
      SELECT id, username, password_hash, role, created_at
      FROM users
      WHERE username = ?
      """;

    RowSet<Row> rows = pool.preparedQuery(sql)
      .execute(Tuple.of(username))
      .await();

    return Future.succeededFuture(firstOrNull(rows));
  }

  /**
   * Checks whether a User with the given username already exists.
   *
   * @param username the username
   * @return a future containing {@code true} if the username is taken
   */
  public Future<Boolean> existsByUsername(String username) {
    return findByUsername(username).map(user -> user != null);
  }

  /**
   * Returns the first User from the given rows.
   *
   * @param rows the database rows
   * @return the first User, or {@code null} if no row exists
   */
  private User firstOrNull(RowSet<Row> rows) {
    Row row = rows.iterator().hasNext() ? rows.iterator().next() : null;

    if (row == null) {
      return null;
    }

    return toUser(row);
  }

  /**
   * Maps a database row to a User domain object.
   *
   * @param row the database row
   * @return the mapped User
   */
  private User toUser(Row row) {
    return new User(
      row.getLong("id"),
      row.getString("username"),
      row.getString("password_hash"),
      row.getString("role"),
      toEpochMillis(row.getLocalDateTime("created_at"))
    );
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
}
