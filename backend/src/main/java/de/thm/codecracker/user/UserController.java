package de.thm.codecracker.user;

import java.util.List;

import de.thm.codecracker.auth.UsernameAlreadyExistsException;
import de.thm.codecracker.auth.model.User;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles HTTP requests for admin user management.
 *
 * <p>Every route registered here is admin-only. The caller of
 * {@link #registerRoutes} must supply the role-check handler (see
 * {@link de.thm.codecracker.auth.RoleHandler#requireRole}) explicitly for
 * each route, rather than relying on a wildcard path mount, so there is no
 * ambiguity about which exact routes are protected.</p>
 */
public class UserController {
  private static final Logger LOGGER = LoggerFactory.getLogger(UserController.class);

  private final UserService userService;

  /**
   * Creates a new User management controller.
   *
   * @param userService the service used to perform User management operations
   */
  public UserController(UserService userService) {
    this.userService = userService;
  }

  /**
   * Registers all admin User-management REST API routes on the given router,
   * each behind the given role-check handler.
   *
   * @param router      the Vert.x router used to register the routes
   * @param adminOnly   a handler that calls {@code ctx.next()} only for admins
   *                    (must run after a JWT-auth handler already populated {@code ctx.user()})
   */
  public void registerRoutes(Router router, Handler<RoutingContext> adminOnly) {
    router.get("/api/users").handler(adminOnly).handler(this::findAll);
    router.get("/api/users/:id").handler(adminOnly).handler(this::findById);
    router.post("/api/users").handler(adminOnly).handler(this::create);
    router.put("/api/users/:id").handler(adminOnly).handler(this::update);
    router.delete("/api/users/:id").handler(adminOnly).handler(this::deleteById);
  }

  /**
   * Handles the request to return all Users, optionally filtered by a search term.
   *
   * @param ctx the current routing context
   */
  private void findAll(RoutingContext ctx) {
    try {
      String search = ctx.request().getParam("search");
      List<User> users = userService.findAll(search).await();

      JsonArray response = new JsonArray(users.stream()
        .map(this::toPublicJson)
        .toList());

      ctx.response()
        .putHeader("content-type", "application/json")
        .end(response.encode());
    } catch (Exception exception) {
      sendUnexpectedError(ctx, exception);
    }
  }

  /**
   * Handles the request to return a User by its ID.
   *
   * @param ctx the current routing context containing the User ID
   */
  private void findById(RoutingContext ctx) {
    try {
      Long id = parseId(ctx);
      User user = userService.findById(id).await();

      ctx.response()
        .putHeader("content-type", "application/json")
        .end(toPublicJson(user).encode());
    } catch (UserNotFoundException exception) {
      sendError(ctx, 404, exception.getMessage());
    } catch (IllegalArgumentException exception) {
      sendError(ctx, 400, exception.getMessage());
    } catch (Exception exception) {
      sendUnexpectedError(ctx, exception);
    }
  }

  /**
   * Handles the request to create a new User.
   *
   * @param ctx the current routing context
   */
  private void create(RoutingContext ctx) {
    try {
      JsonObject request = ctx.body().asJsonObject();
      User user = userService.create(
        request.getString("username"),
        request.getString("password"),
        request.getString("role")
      ).await();

      ctx.response()
        .setStatusCode(201)
        .putHeader("content-type", "application/json")
        .end(toPublicJson(user).encode());
    } catch (UsernameAlreadyExistsException exception) {
      sendError(ctx, 409, exception.getMessage());
    } catch (IllegalArgumentException exception) {
      sendError(ctx, 400, exception.getMessage());
    } catch (Exception exception) {
      sendUnexpectedError(ctx, exception);
    }
  }

  /**
   * Handles the request to update an existing User's username, role, and
   * optionally its password.
   *
   * @param ctx the current routing context containing the User ID and request body
   */
  private void update(RoutingContext ctx) {
    try {
      Long id = parseId(ctx);
      JsonObject request = ctx.body().asJsonObject();
      User user = userService.update(
        id,
        request.getString("username"),
        request.getString("role"),
        request.getString("password")
      ).await();

      ctx.response()
        .putHeader("content-type", "application/json")
        .end(toPublicJson(user).encode());
    } catch (UserNotFoundException exception) {
      sendError(ctx, 404, exception.getMessage());
    } catch (UsernameAlreadyExistsException exception) {
      sendError(ctx, 409, exception.getMessage());
    } catch (LastAdminException exception) {
      sendError(ctx, 409, exception.getMessage());
    } catch (IllegalArgumentException exception) {
      sendError(ctx, 400, exception.getMessage());
    } catch (Exception exception) {
      sendUnexpectedError(ctx, exception);
    }
  }

  /**
   * Handles the request to delete a User by its ID.
   *
   * @param ctx the current routing context containing the User ID
   */
  private void deleteById(RoutingContext ctx) {
    try {
      Long id = parseId(ctx);
      userService.deleteById(id).await();

      ctx.response()
        .setStatusCode(204)
        .end();
    } catch (UserNotFoundException exception) {
      sendError(ctx, 404, exception.getMessage());
    } catch (LastAdminException exception) {
      sendError(ctx, 409, exception.getMessage());
    } catch (IllegalArgumentException exception) {
      sendError(ctx, 400, exception.getMessage());
    } catch (Exception exception) {
      sendUnexpectedError(ctx, exception);
    }
  }

  /**
   * Parses the User ID from the current request path.
   *
   * @param ctx the current routing context
   * @return the parsed User ID
   * @throws IllegalArgumentException if the path parameter is not a valid number
   */
  private Long parseId(RoutingContext ctx) {
    try {
      return Long.parseLong(ctx.pathParam("id"));
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Invalid user id");
    }
  }

  /**
   * Converts a User into its public JSON representation, omitting the password hash.
   *
   * @param user the User to convert
   * @return the public JSON representation
   */
  private JsonObject toPublicJson(User user) {
    return new JsonObject()
      .put("id", user.id())
      .put("username", user.username())
      .put("role", user.role())
      .put("createdAt", user.createdAt());
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