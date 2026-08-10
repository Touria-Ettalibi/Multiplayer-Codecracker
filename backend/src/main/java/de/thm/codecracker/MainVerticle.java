package de.thm.codecracker;

import de.thm.codecracker.auth.AuthController;
import de.thm.codecracker.auth.AuthService;
import de.thm.codecracker.auth.UserRepository;
import de.thm.codecracker.config.AppConfig;
import de.thm.codecracker.config.DatabaseConfig;
import io.vertx.core.AbstractVerticle;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
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

    Router router = Router.router(vertx);
    router.route().handler(LoggerHandler.create(LoggerFormat.DEFAULT));
    router.route().handler(BodyHandler.create());

    authController.registerRoutes(router);

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
