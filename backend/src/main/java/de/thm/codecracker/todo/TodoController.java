package de.thm.codecracker.todo;

import java.util.List;

import de.thm.codecracker.todo.model.Todo;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles HTTP requests for Todo operations.
 *
 * <p>The controller maps REST requests to the {@link TodoService}, converts
 * domain objects into JSON responses, and translates exceptions into
 * appropriate HTTP error responses.</p>
 */
public class TodoController {
  private static final Logger LOGGER = LoggerFactory.getLogger(TodoController.class);

  private final TodoService todoService;

  /**
   * Creates a new Todo controller.
   *
   * @param todoService the service used to perform Todo operations
   */
  public TodoController(TodoService todoService) {
    this.todoService = todoService;
  }

  /**
   * Registers all Todo REST API routes on the given router.
   *
   * @param router the Vert.x router used to register the routes
   */
  public void registerRoutes(Router router) {
    router.post("/api/todos").handler(this::create);
    router.get("/api/todos").handler(this::findAll);
    router.get("/api/todos/:id").handler(this::findById);
    router.put("/api/todos/:id").handler(this::update);
    router.delete("/api/todos/:id").handler(this::deleteById);
  }

  /**
   * Handles the request to create a new Todo.
   *
   * @param ctx the current routing context
   */
  private void create(RoutingContext ctx) {
    try {
      JsonObject request = ctx.body().asJsonObject();
      Todo todo = todoService.create(request.getString("text")).await();

      ctx.response()
        .setStatusCode(201)
        .putHeader("content-type", "application/json")
        .end(JsonObject.mapFrom(todo).encode());
    } catch (IllegalArgumentException exception) {
      sendError(ctx, 400, exception.getMessage());
    } catch (Exception exception) {
      sendUnexpectedError(ctx, exception);
    }
  }

  /**
   * Handles the request to return all Todos.
   *
   * @param ctx the current routing context
   */
  private void findAll(RoutingContext ctx) {
    try {
      List<Todo> todos = todoService.findAll().await();

      JsonArray response = new JsonArray(todos.stream()
        .map(JsonObject::mapFrom)
        .toList());

      ctx.response()
        .putHeader("content-type", "application/json")
        .end(response.encode());
    } catch (Exception exception) {
      sendUnexpectedError(ctx, exception);
    }
  }

  /**
   * Handles the request to return a Todo by its ID.
   *
   * @param ctx the current routing context containing the Todo ID
   */
  private void findById(RoutingContext ctx) {
    try {
      Long id = parseId(ctx);
      Todo todo = todoService.findById(id).await();

      ctx.response()
        .putHeader("content-type", "application/json")
        .end(JsonObject.mapFrom(todo).encode());
    } catch (TodoNotFoundException exception) {
      sendError(ctx, 404, exception.getMessage());
    } catch (IllegalArgumentException exception) {
      sendError(ctx, 400, exception.getMessage());
    } catch (Exception exception) {
      sendUnexpectedError(ctx, exception);
    }
  }

  /**
   * Handles the request to update an existing Todo.
   *
   * @param ctx the current routing context containing the Todo ID and request body
   */
  private void update(RoutingContext ctx) {
    try {
      Long id = parseId(ctx);
      JsonObject request = ctx.body().asJsonObject();
      Todo todo = todoService.update(
        id,
        request.getString("text"),
        request.getBoolean("done")
      ).await();

      ctx.response()
        .putHeader("content-type", "application/json")
        .end(JsonObject.mapFrom(todo).encode());
    } catch (TodoNotFoundException exception) {
      sendError(ctx, 404, exception.getMessage());
    } catch (IllegalArgumentException exception) {
      sendError(ctx, 400, exception.getMessage());
    } catch (Exception exception) {
      sendUnexpectedError(ctx, exception);
    }
  }

  /**
   * Handles the request to delete a Todo by its ID.
   *
   * @param ctx the current routing context containing the Todo ID
   */
  private void deleteById(RoutingContext ctx) {
    try {
      Long id = parseId(ctx);
      todoService.deleteById(id).await();

      ctx.response()
        .setStatusCode(204)
        .end();
    } catch (TodoNotFoundException exception) {
      sendError(ctx, 404, exception.getMessage());
    } catch (IllegalArgumentException exception) {
      sendError(ctx, 400, exception.getMessage());
    } catch (Exception exception) {
      sendUnexpectedError(ctx, exception);
    }
  }

  /**
   * Parses the Todo ID from the current request path.
   *
   * @param ctx the current routing context
   * @return the parsed Todo ID
   * @throws IllegalArgumentException if the path parameter is not a valid number
   */
  private Long parseId(RoutingContext ctx) {
    try {
      return Long.parseLong(ctx.pathParam("id"));
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Invalid todo id");
    }
  }

  /**
   * Sends an error response with the given HTTP status code and message.
   *
   * @param ctx        the current routing context
   * @param statusCode the HTTP status code
   * @param message    the error message returned as JSON
   */
  private void sendError(RoutingContext ctx, int statusCode, String message) {
    ctx.response()
      .setStatusCode(statusCode)
      .putHeader("content-type", "application/json")
      .end(new JsonObject()
        .put("error", message)
        .encode());
  }

  /**
   * Logs an unexpected failure and sends a generic response to the client.
   *
   * @param ctx       the current routing context
   * @param exception the unexpected failure
   */
  private void sendUnexpectedError(RoutingContext ctx, Exception exception) {
    LOGGER.error(
      "Unexpected error while handling {} {}",
      ctx.request().method(),
      ctx.request().path(),
      exception
    );
    sendError(ctx, 500, "Unexpected server error");
  }
}
