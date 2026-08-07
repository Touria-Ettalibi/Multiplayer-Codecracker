package de.thm.codecracker.todo;

import de.thm.codecracker.todo.model.Todo;
import io.vertx.core.Vertx;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Todo WebSocket connections, commands, responses and events.
 *
 * <p>Commands from clients are delegated to the same {@link TodoService} used
 * by the REST API. Completed changes arrive through the Vert.x EventBus and
 * are then broadcast to every connected client.</p>
 */
public class TodoWebSocketController {
  private static final Logger LOGGER = LoggerFactory.getLogger(TodoWebSocketController.class);
  private static final String TODO_PATH = "/ws/todos";
  private static final String TODO_EVENT_ADDRESS = "todos";

  private final Vertx vertx;
  private final TodoService todoService;
  private final Set<ServerWebSocket> sockets = ConcurrentHashMap.newKeySet();

  /**
   * Creates a new WebSocket controller.
   *
   * @param vertx       the Vert.x instance used to access the EventBus
   * @param todoService the shared business logic for Todo commands
   */
  public TodoWebSocketController(Vertx vertx, TodoService todoService) {
    this.vertx = vertx;
    this.todoService = todoService;
  }

  /**
   * Registers the Todo WebSocket route on the given router.
   *
   * @param router the Vert.x router used to register the route
   */
  public void registerRoutes(Router router) {
    router.get(TODO_PATH).handler(this::upgradeConnection);
  }

  /**
   * Registers the consumer that forwards Todo events to connected clients.
   */
  public void registerEventBusConsumer() {
    vertx.eventBus().consumer(TODO_EVENT_ADDRESS, message ->
      broadcast((JsonObject) message.body())
    );
  }

  private void upgradeConnection(RoutingContext context) {
    try {
      ServerWebSocket socket = context.request().toWebSocket().await();
      handleConnection(socket);
    } catch (Exception exception) {
      context.fail(exception);
    }
  }

  private void handleConnection(ServerWebSocket socket) {
    sockets.add(socket);
    socket.textMessageHandler(message -> handleTodoCommand(socket, message));
    socket.closeHandler(unused -> sockets.remove(socket));
    socket.exceptionHandler(error -> sockets.remove(socket));
  }

  /**
   * Executes one Todo command received from a WebSocket client.
   *
   * <p>Every command carries a request ID. The response repeats this ID so
   * the client can match the asynchronous response to its request.</p>
   *
   * @param socket  the client that sent the command
   * @param message the command encoded as JSON
   */
  private void handleTodoCommand(ServerWebSocket socket, String message) {
    String requestId = null;

    try {
      JsonObject command = new JsonObject(message);
      requestId = requiredString(command, "requestId");
      String type = requiredString(command, "type");
      JsonObject payload = command.getJsonObject("payload", new JsonObject());

      Object result = switch (type) {
        case "find-all" -> findAllTodos();
        case "create" -> createTodo(payload);
        case "update" -> updateTodo(payload);
        case "delete" -> deleteTodo(payload);
        default -> throw new IllegalArgumentException("Unknown command: " + type);
      };

      sendResponse(socket, requestId, result);
    } catch (TodoNotFoundException | IllegalArgumentException exception) {
      sendError(socket, requestId, exception.getMessage());
    } catch (Exception exception) {
      LOGGER.error("Unexpected error while handling Todo WebSocket command", exception);
      sendError(socket, requestId, "Unexpected server error");
    }
  }

  private JsonArray findAllTodos() {
    return todosAsJson(todoService.findAll().await());
  }

  private JsonObject createTodo(JsonObject payload) {
    Todo todo = todoService.create(payload.getString("text")).await();
    return JsonObject.mapFrom(todo);
  }

  private JsonObject updateTodo(JsonObject payload) {
    Todo todo = todoService.update(
      requiredId(payload),
      payload.getString("text"),
      payload.getBoolean("done")
    ).await();

    return JsonObject.mapFrom(todo);
  }

  private JsonObject deleteTodo(JsonObject payload) {
    long id = requiredId(payload);
    todoService.deleteById(id).await();
    return new JsonObject().put("id", id);
  }

  private JsonArray todosAsJson(List<Todo> todos) {
    return new JsonArray(todos.stream()
      .map(JsonObject::mapFrom)
      .toList());
  }

  private String requiredString(JsonObject object, String fieldName) {
    String value = object.getString(fieldName);

    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " is required");
    }

    return value;
  }

  private long requiredId(JsonObject payload) {
    Number id = payload.getNumber("id");

    if (id == null) {
      throw new IllegalArgumentException("id is required");
    }

    return id.longValue();
  }

  private void sendResponse(ServerWebSocket socket, String requestId, Object payload) {
    socket.writeTextMessage(new JsonObject()
      .put("type", "response")
      .put("requestId", requestId)
      .put("payload", payload)
      .encode());
  }

  private void sendError(ServerWebSocket socket, String requestId, String message) {
    socket.writeTextMessage(new JsonObject()
      .put("type", "response")
      .put("requestId", requestId)
      .put("error", message == null ? "Command failed" : message)
      .encode());
  }

  /**
   * Sends a message to all connected Todo clients.
   *
   * @param message the JSON message to send
   */
  private void broadcast(JsonObject message) {
    for (var socket : sockets) {
      if (!socket.isClosed()) {
        socket.writeTextMessage(message.encode());
      }
    }
  }
}
