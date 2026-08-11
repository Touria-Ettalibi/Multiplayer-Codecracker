package de.thm.codecracker;

import de.thm.codecracker.auth.AuthController;
import de.thm.codecracker.auth.AuthService;
import de.thm.codecracker.auth.RoleHandler;
import de.thm.codecracker.auth.UserRepository;
import de.thm.codecracker.config.AppConfig;
import de.thm.codecracker.config.DatabaseConfig;
import de.thm.codecracker.lobby.LobbyWebSocketController;
import de.thm.codecracker.user.UserController;
import de.thm.codecracker.user.UserService;
import io.vertx.core.AbstractVerticle;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.ext.web.handler.LoggerFormat;
import io.vertx.ext.web.handler.LoggerHandler;
import io.vertx.ext.web.handler.StaticHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainVerticle extends AbstractVerticle {
  private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class);

  @Override
  public void start() {
    var config = AppConfig.fromEnvironment();

    var pool = DatabaseConfig.createPool(vertx, config);

    JWTAuth jwtAuth = JWTAuth.create(vertx, new JWTAuthOptions()
      .addPubSecKey(new PubSecKeyOptions()
        .setAlgorithm("HS256")
        .setBuffer(config.jwtSecret())));

    var userRepository = new UserRepository(pool);

    var authService = new AuthService(userRepository, jwtAuth);
    var authController = new AuthController(authService);

    var userService = new UserService(userRepository);
    var userController = new UserController(userService);

    var lobbyWebSocketController = new LobbyWebSocketController(jwtAuth);

    Router router = Router.router(vertx);
    router.route().handler(LoggerHandler.create(LoggerFormat.DEFAULT));
    router.route().handler(BodyHandler.create());

    // Public auth routes: registration and login must stay reachable
    // without a token, since they are how a token is obtained in the first place.
    authController.registerRoutes(router);

    // Everything else under /api/* requires a valid, non-expired JWT.
    // JWTAuthHandler itself replies 401 for a missing/invalid/expired token,
    // and populates ctx.user() with the token's claims on success.
    JWTAuthHandler jwtAuthHandler = JWTAuthHandler.create(jwtAuth);
    router.route("/api/*").handler(jwtAuthHandler);

    // Admin-only routes: each one is chained behind RoleHandler.requireRole,
    // which runs after the JWT handler above (so ctx.user() is already
    // populated) and responds 403 if the caller's role claim isn't ADMIN.
    userController.registerRoutes(router, RoleHandler.requireRole(RoleHandler.ADMIN));

    // Lobby WebSocket: validates its own token manually (browsers can't send
    // custom headers on a WS handshake), so it sits outside the /api/* JWT
    // middleware above rather than depending on it.
    lobbyWebSocketController.registerRoutes(router);

    router.get("/").handler(ctx ->
      ctx.response()
        .setStatusCode(302)
        .putHeader("Location", "/index.html")
        .end()
    );

    router.route().handler(StaticHandler.create("webroot"));

    var server = vertx.createHttpServer();
    server.requestHandler(router);
    server.listen(config.httpPort()).await();

    LOGGER.info("Server started on port {}", config.httpPort());
  }
}
