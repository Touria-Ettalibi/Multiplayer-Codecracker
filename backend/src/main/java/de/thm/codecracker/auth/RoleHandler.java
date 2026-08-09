package de.thm.codecracker.auth;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * Provides role-based authorization for routes already protected by
 * {@link io.vertx.ext.web.handler.JWTAuthHandler}.
 *
 * <p>Must run after the JWT handler, since it reads the role claim from
 * {@link RoutingContext#user()}, which the JWT handler populates once a
 * token has been successfully validated.</p>
 */
public final class RoleHandler {
  public static final String ADMIN = "ADMIN";
  public static final String USER = "USER";

  private RoleHandler() {
  }

  /**
   * Returns a route handler that only calls {@code ctx.next()} if the
   * authenticated User has the given role, and responds with {@code 403}
   * otherwise.
   *
   * @param requiredRole the role required to access the route
   * @return a Vert.x route handler enforcing the role
   */
  public static io.vertx.core.Handler<RoutingContext> requireRole(String requiredRole) {
    return ctx -> {
      String role = currentRole(ctx);

      if (!requiredRole.equals(role)) {
        ctx.response()
          .setStatusCode(403)
          .putHeader("content-type", "application/json")
          .end(new JsonObject().put("error", "Forbidden").encode());
        return;
      }

      ctx.next();
    };
  }

  /**
   * Reads the role claim of the currently authenticated User.
   *
   * @param ctx the current routing context
   * @return the role, or {@code null} if no User is authenticated
   */
  private static String currentRole(RoutingContext ctx) {
    if (ctx.user() == null) {
      return null;
    }

    return ctx.user().principal().getString("role");
  }
}