package de.thm.codecracker;

import de.thm.codecracker.config.AppConfig;
import de.thm.codecracker.config.DatabaseConfig;
import de.thm.codecracker.todo.TodoController;
import de.thm.codecracker.todo.TodoRepository;
import de.thm.codecracker.todo.TodoService;
import de.thm.codecracker.todo.TodoWebSocketController;
import io.vertx.core.AbstractVerticle;
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

    var todoRepository = new TodoRepository(pool);
    var todoService = new TodoService(todoRepository, vertx);
    var todoController = new TodoController(todoService);

    var todoWebSocketController = new TodoWebSocketController(vertx, todoService);
    todoWebSocketController.registerEventBusConsumer();

    Router router = Router.router(vertx);
    router.route().handler(LoggerHandler.create(LoggerFormat.DEFAULT));
    router.route().handler(BodyHandler.create());

    todoController.registerRoutes(router);
    todoWebSocketController.registerRoutes(router);

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
