package de.thm.codecracker.todo;

import de.thm.codecracker.todo.model.Todo;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import java.util.List;

/**
 * Provides business logic for Todo operations.
 *
 * <p>The service validates input, coordinates database access through the
 * {@link TodoRepository}, and publishes Todo changes on the Vert.x EventBus.</p>
 */
public class TodoService {
  private static final int MAX_TEXT_LENGTH = 100;

  private final TodoRepository todoRepository;
  private final Vertx vertx;

  /**
   * Creates a new Todo service.
   *
   * @param todoRepository the repository used for Todo persistence
   * @param vertx          the Vert.x instance used to access the EventBus
   */
  public TodoService(TodoRepository todoRepository, Vertx vertx) {
    this.todoRepository = todoRepository;
    this.vertx = vertx;
  }

  /**
   * Creates a new Todo and publishes a creation event.
   *
   * @param text the Todo text
   * @return a future containing the created Todo
   * @throws IllegalArgumentException if the text is invalid
   */
  public Future<Todo> create(String text) {
    validateText(text);

    Todo todo = todoRepository.create(text).await();

    publishTodoEvent("created", JsonObject.mapFrom(todo));

    return Future.succeededFuture(todo);
  }

  /**
   * Finds a Todo by its ID.
   *
   * @param id the Todo ID
   * @return a future containing the Todo
   * @throws IllegalArgumentException  if the ID is invalid
   * @throws TodoNotFoundException     if the Todo does not exist
   */
  public Future<Todo> findById(Long id) {
    validateId(id);

    Todo todo = todoRepository.findById(id).await();

    if (todo == null) {
      throw new TodoNotFoundException();
    }

    return Future.succeededFuture(todo);
  }

  /**
   * Returns all Todos.
   *
   * @return a future containing all Todos
   */
  public Future<List<Todo>> findAll() {
    List<Todo> todos = todoRepository.findAll().await();

    return Future.succeededFuture(todos);
  }

  /**
   * Updates an existing Todo and publishes an update event.
   *
   * <p>If no completion state is provided, the current value is preserved.</p>
   *
   * @param id   the Todo ID
   * @param text the new Todo text
   * @param done the new completion state, or {@code null} to keep the current value
   * @return a future containing the updated Todo
   * @throws IllegalArgumentException if the input is invalid
   * @throws TodoNotFoundException    if the Todo does not exist
   */
  public Future<Todo> update(Long id, String text, Boolean done) {
    validateId(id);
    validateText(text);

    Todo existingTodo = todoRepository.findById(id).await();

    if (existingTodo == null) {
      throw new TodoNotFoundException();
    }

    boolean updatedDone = done != null ? done : existingTodo.done();

    Todo updatedTodo = todoRepository.update(
      id,
      text,
      updatedDone
    ).await();

    publishTodoEvent("updated", JsonObject.mapFrom(updatedTodo));

    return Future.succeededFuture(updatedTodo);
  }

  /**
   * Deletes a Todo by its ID and publishes a deletion event.
   *
   * @param id the Todo ID
   * @return a succeeded future when the Todo was deleted
   * @throws IllegalArgumentException if the ID is invalid
   * @throws TodoNotFoundException    if the Todo does not exist
   */
  public Future<Void> deleteById(Long id) {
    validateId(id);

    boolean deleted = todoRepository.deleteById(id).await();

    if (!deleted) {
      throw new TodoNotFoundException();
    }

    publishTodoEvent("deleted", new JsonObject().put("id", id));

    return Future.succeededFuture();
  }

  /**
   * Publishes a Todo event on the Vert.x EventBus.
   *
   * @param type    the event type
   * @param payload the event payload
   */
  private void publishTodoEvent(String type, JsonObject payload) {
    var message = new JsonObject()
      .put("type", type)
      .put("payload", payload);

    vertx.eventBus().publish("todos", message);
  }

  /**
   * Validates a Todo ID.
   *
   * @param id the Todo ID
   * @throws IllegalArgumentException if the ID is {@code null} or not positive
   */
  private void validateId(Long id) {
    if (id == null || id <= 0) {
      throw new IllegalArgumentException("Invalid todo id");
    }
  }

  /**
   * Validates that a Todo text is present and within the allowed length.
   *
   * @param text the Todo text
   * @throws IllegalArgumentException if the text is empty or too long
   */
  private void validateText(String text) {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("Todo text must not be empty");
    }

    if (text.length() > MAX_TEXT_LENGTH) {
      throw new IllegalArgumentException(
        "Todo text must not exceed 100 characters"
      );
    }
  }
}
