package de.thm.codecracker.auth;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

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
   * Checks whether a different User already uses the given username.
   *
   * <p>Used when editing a User, so that a User keeping their own username
   * is not mistaken for a conflict.</p>
   *
   * @param username the username to check
   * @param id       the ID of the User being edited
   * @return a future containing {@code true} if another User already has this username
   */
  public Future<Boolean> existsByUsernameExcludingId(String username, Long id) {
    String sql = """
      SELECT id, username, password_hash, role, created_at
      FROM users
      WHERE username = ? AND id <> ?
      """;

    RowSet<Row> rows = pool.preparedQuery(sql)
      .execute(Tuple.of(username, id))
      .await();

    return Future.succeededFuture(rows.iterator().hasNext());
  }

  /**
   * Finds all Users, optionally filtered by a search term matched against the username.
   *
   * @param search a case-insensitive substring to match against usernames,
   *                or {@code null}/blank to return every User
   * @return a future containing all matching Users ordered by ID
   */
  public Future<List<User>> findAll(String search) {
    String pattern = (search == null || search.isBlank()) ? null : "%" + search.trim() + "%";

    String sql = """
      SELECT id, username, password_hash, role, created_at
      FROM users
      WHERE ? IS NULL OR username LIKE ?
      ORDER BY id
      """;

    RowSet<Row> rows = pool.preparedQuery(sql)
      .execute(Tuple.of(pattern, pattern))
      .await();

    List<User> users = rows.stream()
      .map(this::toUser)
      .toList();

    return Future.succeededFuture(users);
  }

  /**
   * Updates a User's username and role.
   *
   * @param id       the user ID
   * @param username the new username
   * @param role     the new role
   * @return a future containing the updated User, or {@code null} if it does not exist
   */
  public Future<User> update(Long id, String username, String role) {
    String sql = """
      UPDATE users
      SET username = ?, role = ?
      WHERE id = ?
      """;

    RowSet<Row> result = pool.preparedQuery(sql)
      .execute(Tuple.of(username, role, id))
      .await();

    if (result.rowCount() == 0) {
      return Future.succeededFuture(null);
    }

    return findById(id);
  }

  /**
   * Updates a User's password hash.
   *
   * @param id           the user ID
   * @param passwordHash the new bcrypt hash
   * @return a future containing {@code true} if a User was updated
   */
  public Future<Boolean> updatePasswordHash(Long id, String passwordHash) {
    String sql = """
      UPDATE users
      SET password_hash = ?
      WHERE id = ?
      """;

    RowSet<Row> result = pool.preparedQuery(sql)
      .execute(Tuple.of(passwordHash, id))
      .await();

    return Future.succeededFuture(result.rowCount() > 0);
  }

  /**
   * Deletes a User by its ID.
   *
   * @param id the user ID
   * @return a future containing {@code true} if a User was deleted
   */
  public Future<Boolean> deleteById(Long id) {
    String sql = "DELETE FROM users WHERE id = ?";

    RowSet<Row> result = pool.preparedQuery(sql)
      .execute(Tuple.of(id))
      .await();

    return Future.succeededFuture(result.rowCount() > 0);
  }

  /**
   * Counts how many Users currently have the {@code ADMIN} role.
   *
   * @return a future containing the number of admin Users
   */
  public Future<Long> countByRole(String role) {
    String sql = "SELECT COUNT(*) AS total FROM users WHERE role = ?";

    RowSet<Row> rows = pool.preparedQuery(sql)
      .execute(Tuple.of(role))
      .await();

    return Future.succeededFuture(rows.iterator().next().getLong("total"));
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
