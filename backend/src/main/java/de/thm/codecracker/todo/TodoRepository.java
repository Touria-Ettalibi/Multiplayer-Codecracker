package de.thm.codecracker.todo;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import de.thm.codecracker.todo.model.Todo;
import io.vertx.core.Future;
import io.vertx.mysqlclient.MySQLClient;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

/**
 * Provides database access for Todo operations.
 *
 * <p>The repository executes SQL queries using the Vert.x SQL client and maps
 * database rows to {@link Todo} domain objects.</p>
 */
public class TodoRepository {
  private final Pool pool;

  /**
   * Creates a new Todo repository.
   *
   * @param pool the SQL connection pool used for database access
   */
  public TodoRepository(Pool pool) {
    this.pool = pool;
  }

  /**
   * Inserts a new Todo and returns the persisted domain object.
   *
   * @param text the Todo text
   * @return a future containing the created Todo
   */
  public Future<Todo> create(String text) {
    String sql = """
      INSERT INTO todos (text, done)
      VALUES (?, false)
      """;

    RowSet<Row> result = pool.preparedQuery(sql)
      .execute(Tuple.of(text))
      .await();

    Long id = result.property(MySQLClient.LAST_INSERTED_ID);
    Todo todo = findById(id).await();

    return Future.succeededFuture(todo);
  }

  /**
   * Finds a Todo by its ID.
   *
   * @param id the Todo ID
   * @return a future containing the Todo, or {@code null} if it does not exist
   */
  public Future<Todo> findById(Long id) {
    String sql = """
      SELECT id, text, done, created_at, updated_at
      FROM todos
      WHERE id = ?
      """;

    RowSet<Row> rows = pool.preparedQuery(sql)
      .execute(Tuple.of(id))
      .await();

    return Future.succeededFuture(firstOrNull(rows));
  }

  /**
   * Returns all Todos ordered by their ID.
   *
   * @return a future containing all Todos
   */
  public Future<List<Todo>> findAll() {
    String sql = """
      SELECT id, text, done, created_at, updated_at
      FROM todos
      ORDER BY id
      """;

    RowSet<Row> rows = pool.preparedQuery(sql)
      .execute()
      .await();

    return Future.succeededFuture(toList(rows));
  }

  /**
   * Updates an existing Todo and returns its current state.
   *
   * @param id   the Todo ID
   * @param text the new Todo text
   * @param done the new completion state
   * @return a future containing the updated Todo
   */
  public Future<Todo> update(Long id, String text, boolean done) {
    String sql = """
      UPDATE todos
      SET text = ?, done = ?
      WHERE id = ?
      """;

    pool.preparedQuery(sql)
      .execute(Tuple.of(text, done, id))
      .await();

    Todo todo = findById(id).await();

    return Future.succeededFuture(todo);
  }

  /**
   * Deletes a Todo by its ID.
   *
   * @param id the Todo ID
   * @return a future containing {@code true} if a Todo was deleted,
   * otherwise {@code false}
   */
  public Future<Boolean> deleteById(Long id) {
    String sql = """
      DELETE FROM todos
      WHERE id = ?
      """;

    RowSet<Row> result = pool.preparedQuery(sql)
      .execute(Tuple.of(id))
      .await();

    return Future.succeededFuture(result.rowCount() > 0);
  }

  /**
   * Returns the first Todo from the given rows.
   *
   * @param rows the database rows
   * @return the first Todo, or {@code null} if no row exists
   */
  private Todo firstOrNull(RowSet<Row> rows) {
    Row row = rows.iterator().hasNext() ? rows.iterator().next() : null;

    if (row == null) {
      return null;
    }

    return toTodo(row);
  }

  /**
   * Converts database rows into a list of Todos.
   *
   * @param rows the database rows
   * @return the mapped Todos
   */
  private List<Todo> toList(RowSet<Row> rows) {
    List<Todo> todos = new ArrayList<>();

    for (Row row : rows) {
      todos.add(toTodo(row));
    }

    return todos;
  }

  /**
   * Maps a database row to a Todo domain object.
   *
   * @param row the database row
   * @return the mapped Todo
   */
  private Todo toTodo(Row row) {
    return new Todo(
      row.getLong("id"),
      row.getString("text"),
      row.getBoolean("done"),
      toEpochMillis(row.getLocalDateTime("created_at")),
      toEpochMillis(row.getLocalDateTime("updated_at"))
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
