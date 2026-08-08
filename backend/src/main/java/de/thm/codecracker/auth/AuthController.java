package de.thm.codecracker.auth;

import de.thm.codecracker.auth.model.User;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles HTTP requests for registration and login.
 *
 * <p>The controller maps REST requests to the {@link AuthService}, converts
 * domain objects into JSON responses, and translates exceptions into
 * appropriate HTTP error responses.</p>
 */
public class AuthController {
  private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

  private final AuthService authService;

  /**
   * Creates a new Auth controller.
   *
   * @param authService the service used to perform registration and login
   */
  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  /**
   * Registers all Auth REST API routes on the given router.
   *
   * @param router the Vert.x router used to register the routes
   */
  public void registerRoutes(Router router) {
    router.post("/api/auth/register").handler(this::register);
    router.post("/api/auth/login").handler(this::login);
  }

  /**
   * Handles the request to register a new User.
   *
   * @param ctx the current routing context
   */
  private void register(RoutingContext ctx) {
    try {
      JsonObject request = ctx.body().asJsonObject();
      User user = authService.register(
        request.getString("username"),
        request.getString("password")
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
   * Handles the request to log in and issues a JWT on success.
   *
   * @param ctx the current routing context
   */
  private void login(RoutingContext ctx) {
    try {
      JsonObject request = ctx.body().asJsonObject();
      String token = authService.login(
        request.getString("username"),
        request.getString("password")
      ).await();

      ctx.response()
        .putHeader("content-type", "application/json")
        .end(new JsonObject().put("token", token).encode());
    } catch (InvalidCredentialsException exception) {
      sendError(ctx, 401, exception.getMessage());
    } catch (Exception exception) {
      sendUnexpectedError(ctx, exception);
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
