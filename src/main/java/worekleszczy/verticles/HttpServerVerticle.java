package worekleszczy.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.KeyStoreOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CookieHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import worekleszczy.verticles.dto.Note;
import worekleszczy.verticles.dto.rest.JsonMessage;

import static worekleszczy.verticles.DbVerticle.*;

import java.util.Objects;

public class HttpServerVerticle extends AbstractVerticle {

  private Router router;
  private JWTAuth jwtProvider;

  public HttpServerVerticle() {
  }

  @Override
  public void start(Future<Void> init) {
    JWTAuthOptions config = new JWTAuthOptions()
      .setKeyStore(new KeyStoreOptions()
        .setPath("keystore.jceks")
        .setPassword("secret"));
    try {
      jwtProvider = JWTAuth.create(vertx, config);
    } catch (Exception e) {
      e.printStackTrace();
    }

    router = Router.router(this.vertx);


    router.route().handler(SessionHandler.create(LocalSessionStore.create(this.vertx)))
      .handler(CookieHandler.create());
    router.post().handler(this::appJson);
    router.post().handler(BodyHandler.create());
    router.post("/register").handler(this::register);
    router.post("/signin").handler(this::signin);
    router.route().handler(JWTAuthHandler.create(jwtProvider));
    router.post("/note/:name").handler(this::addNote);
    router.get("/note/:note").handler(this::getNote);
    router.get("/notes/:user").handler(this::getUserNotes);

    this.vertx.createHttpServer().requestHandler(router::accept)
      .listen(8080, complete -> {
        if (complete.succeeded()) {
          init.complete();
          System.out.println("Started listening on port 8080");
        } else {
          System.out.println("Failed on httpServer initialization");
        }
      });
  }

  private void register(RoutingContext context) {

    JsonObject body = context.getBodyAsJson();
    String username = body.getString("username");
    String password = body.getString("password");
    this.vertx.eventBus()
      .send(DbVerticle.USER_INSERT, new JsonObject()
        .put("username", username)
        .put("password", password)
        .put("type", "user"), result -> {
        JsonObject resBody = (JsonObject) result.result().body();
        String status = resBody.getString("status");
        if (result.succeeded() && status.equals(SUCCESSFUL)) {
          String id = resBody.getString("result");
          String token = genToken(id, "user");
          context.response().setStatusCode(201).end(JsonMessage.token(token));
        } else if (status.equals(CONFLICT)) {
          JsonObject response = (JsonObject) result.result().body();
          context.response().setStatusCode(409).end(JsonMessage.error("User already exists"));
        } else {
          context.response().setStatusCode(500).end(JsonMessage.error("Unknown error occurred"));
        }
      });

  }

  private void signin(RoutingContext context) {
    final JsonObject requestBody = context.getBodyAsJson();
    final String username = requestBody.getString("username");
    final String password = requestBody.getString("password");

    if (Objects.nonNull(username) && Objects.nonNull(password)) {
      this.vertx.eventBus().send(DbVerticle.USER_GET,
        new JsonObject().put("username", username),
        result -> {
          JsonObject resBody = (JsonObject) result.result().body();
          String status = resBody.getString("status");

          if (status.equals(SUCCESSFUL)) {
            JsonObject user = resBody.getJsonObject("result");
            if (Objects.nonNull(user) && hash(requestBody.getString("password")).equals(user.getString("password"))) {
              String token = genToken(user.getString("_id"), "user");
              context.response().setStatusCode(201).end(JsonMessage.token(token));
            } else
              context.response().setStatusCode(401).end(JsonMessage.error("Wrong username or password"));
          } else {
            context.response().setStatusCode(500).end(JsonMessage.error("Unknown error occurred"));
          }

        });
    } else {
      context.response().setStatusCode(422).end(Json.encode(JsonMessage.error("Fields username and password are required")));
    }
  }

  private void addNote(RoutingContext context) {
    JsonObject user = context.user().principal();
    String name = context.pathParam("name");
    Note processingNote = new Note(name, user.getString("id"));
    this.vertx.eventBus().send(DbVerticle.NOTE_INSERT,
      JsonObject.mapFrom(processingNote), response -> {
        if (response.result().body() == null)
          context.response().setStatusCode(201).end(JsonMessage.resource(processingNote.get_id()));
        else {
          context.response().end(JsonMessage.error("Note exits or sth else"));
        }
      });

  }

  private void getNote(RoutingContext context) {
    final String noteId = context.pathParam("note");

    this.vertx.eventBus().send(DbVerticle.NOTE_GET, noteId, response -> {
      JsonObject body = (JsonObject) response.result().body();
      if (response.succeeded() && Objects.nonNull(body)) {
        body.remove("_id");
        context.response().setStatusCode(200).end(body.encode());
      } else {
        context.response().setStatusCode(404).end();
      }
    });
  }

  private void getUserNotes(RoutingContext context) {

    final String userId = context.pathParam("user");
    System.out.println(context.user().principal());
    if (context.user().principal().getString("id").equals(userId))
      this.vertx.eventBus().send(DbVerticle.GET_USER_NOTES, userId, result -> {
        JsonArray notes = (JsonArray) result.result().body();
        if (result.succeeded() && Objects.nonNull(notes)) {
          context.response().setStatusCode(200).end(notes.encode());
        } else {
          context.response().setStatusCode(400).end();
        }
      });
    else {
      context.response().setStatusCode(403).end();
    }
  }

  private void appJson(RoutingContext context) {
    context.response().putHeader("Content-Type", "application/json; charset=UTF-8");
    context.next();
  }

  private String hash(String password) {
    return password;
  }

  private String genToken(String id, String role) {
    return jwtProvider.generateToken(new JsonObject().put("id", id).put("role", role));
  }
}
