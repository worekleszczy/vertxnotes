package worekleszczy.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.KeyStoreOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CookieHandler;
import io.vertx.ext.web.handler.JWTAuthHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import worekleszczy.verticles.dto.Note;
import worekleszczy.verticles.dto.rest.JsonMessage;

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
    router.route().handler(this::crossOrigin);
    router.options().handler(this::optionsCORS);
    router.route().handler(SessionHandler.create(LocalSessionStore.create(this.vertx)))
      .handler(CookieHandler.create());
    router.post().handler(this::appJson);
    router.post().handler(BodyHandler.create());
    router.patch().handler(BodyHandler.create());
    router.post().handler(this::jsonTypeRequest);
    router.post("/register").handler(this::register);
    router.post("/signin").handler(this::signin);
    router.route().handler(JWTAuthHandler.create(jwtProvider));
    router.post("/note").handler(this::addNote);
    router.patch("/note/:noteid").handler(this::updateNote);
    router.delete("/note/:noteid").handler(this::deleteNote);
    router.get("/note/:noteid").handler(this::getNote);
    router.get("/notes/").handler(this::getUserNotes);

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
      .<String>send(DbVerticle.USER_INSERT, new JsonObject()
        .put("username", username)
        .put("password", password)
        .put("type", "user"), result -> {

        Message<String> message = result.result();
        if (result.succeeded()) {
          String token = genToken(message.body(), "user");
          context.response().setStatusCode(201).end(JsonMessage.token(token));
        } else {
          ReplyException cause = (ReplyException) result.cause();
          context.response().setStatusCode(cause.failureCode()).end(JsonMessage.error(cause.getMessage()));
        }
      });
  }

  private void signin(RoutingContext context) {

    final JsonObject requestBody = context.getBodyAsJson();
    final String username = requestBody.getString("username");
    final String password = requestBody.getString("password");

    if (Objects.nonNull(username) && Objects.nonNull(password)) {
      this.vertx.eventBus()
        .<JsonObject>send(DbVerticle.USER_GET,
          new JsonObject().put("username", username),
          result -> {
            if (result.succeeded()) {
              JsonObject user = result.result().body();
              if (Objects.nonNull(user) && hash(requestBody.getString("password")).equals(user.getString("password"))) {
                String token = genToken(user.getString("_id"), "user");
                context.response().setStatusCode(201).end(JsonMessage.token(token));
              } else {
                context.response().setStatusCode(401).end(JsonMessage.error("Wrong username or password"));
              }
            } else {
              ReplyException cause = (ReplyException) result.cause();
              context.response().setStatusCode(cause.failureCode()).end(JsonMessage.error("Unknown error occurred"));
            }

          });
    } else {
      context.response().setStatusCode(422).end(Json.encode(JsonMessage.error("Fields username and password are required")));
    }
  }

  private void addNote(RoutingContext context) {

    String userID = context.user().principal().getString("id");
    JsonObject body = context.getBodyAsJson();

    if (!body.containsKey("name") || !body.containsKey("data")) {
      context.response()
        .setStatusCode(422)
        .end(JsonMessage.error("Fields \"name\" and \"data\" are required"));
      return;
    }

    Note processingNote = new Note(body.getString("name"), userID, body.getString("data"));
    this.vertx.eventBus().send(DbVerticle.NOTE_INSERT,
      JsonObject.mapFrom(processingNote), result -> {
        if(result.succeeded()) {
          context.response().setStatusCode(201).end(JsonMessage.resource(processingNote.get_id()));
        }
        else {
          ReplyException cause = (ReplyException) result.cause();
          context.response()
            .setStatusCode(cause.failureCode())
            .end(JsonMessage.error(cause.getMessage()));;
        }
      });

  }

  private void getNote(RoutingContext context) {
    final String noteID = context.pathParam("noteid");

    this.vertx.eventBus().<JsonObject>send(DbVerticle.NOTE_GET, noteID, result -> {

      if (result.succeeded()) {
        JsonObject body = result.result().body();
        context.response().setStatusCode(200).end(body.encode());
      } else {
        ReplyException cause = (ReplyException) result.cause();
        context.response()
          .setStatusCode(cause.failureCode())
          .end(JsonMessage.error(cause.getMessage()));
      }
    });
  }

  private void getUserNotes(RoutingContext context) {
    System.out.println("GetUserNotes");
    final String userID = context.user().principal().getString("id");

    this.vertx.eventBus().send(DbVerticle.GET_USER_NOTES, userID, result -> {
      JsonArray notes = (JsonArray) result.result().body();

      if (Objects.isNull(notes))
        notes = new JsonArray();

      if (result.succeeded()) {
        context.response().setStatusCode(200).end(notes.encode());
      } else {
        ReplyException cause = (ReplyException) result.cause();
        context.response()
          .setStatusCode(cause.failureCode())
          .end(JsonMessage.error(cause.getMessage()));
      }
    });
  }

  private void updateNote(RoutingContext context) {

    final String userID = context.user().principal().getString("id");
    final String noteID = context.pathParam("noteid");
    final JsonObject body = context.getBodyAsJson();

    final JsonObject query = new JsonObject().put("_id", noteID).put("owner", userID);
    System.out.println(query);
    this.vertx.eventBus().send(DbVerticle.UPDATE_NOTE, new JsonObject()
        .put("query", query)
        .put("data", body), result -> {
        if (result.succeeded()) {
          context.response().setStatusCode(204).end();
        } else {
          ReplyException cause = (ReplyException) result.cause();
          context.response()
            .setStatusCode(cause.failureCode())
            .end(JsonMessage.error(cause.getMessage()));
        }
      }
    );
  }

  private void deleteNote(RoutingContext context) {
    final String noteID = context.pathParam("noteid");

    this.vertx.eventBus().send(DbVerticle.DELETE_NOTE, noteID, result -> {
        if (result.succeeded()) {
          context.response().setStatusCode(204).end();
        } else {
          ReplyException cause = (ReplyException) result.cause();
          context.response()
            .setStatusCode(cause.failureCode())
            .end(JsonMessage.error(cause.getMessage()));
        }
      }
    );
  }

  private void appJson(RoutingContext context) {
    context.response().putHeader("Content-Type", "application/json; charset=UTF-8");
    System.out.println("Connection");
    context.next();
  }
  private void crossOrigin(RoutingContext context) {
    context.response().putHeader("Access-Control-Allow-Origin", "*");
    context.next();
  }

  private void optionsCORS(RoutingContext context) {
    context.response().setStatusCode(200)
      .putHeader("Access-Control-Allow-Headers","Authorization, Content-Type")
      .putHeader("Access-Control-Allow-Methods","PATCH, DELETE")
      .end();
  }
  private void jsonTypeRequest(RoutingContext context) {
    try {
      context.getBodyAsJson();
      context.next();
    } catch (DecodeException e) {
      context.response().setStatusCode(400).end();
    }
  }

  private String hash(String password) {
    return password;
  }

  private String genToken(String id, String role) {
    return jwtProvider.generateToken(new JsonObject().put("id", id).put("role", role));
  }
}
