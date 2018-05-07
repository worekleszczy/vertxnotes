package worekleszczy.verticles;

import com.google.common.collect.ImmutableMap;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class DbVerticle extends AbstractVerticle {

  public static final String USER_INSERT = "mongo.database.users.insert";
  public static final String USER_GET = "mongo.database.users.get";
  public static final String NOTE_INSERT = "mongo.database.note.insert";
  public static final String NOTE_GET = "mongo.database.note.get";
  public static final String GET_USER_NOTES = "mongo.database.notes.get";
  public static final String UPDATE_NOTE = "mongo.database.note.update";
  public static final String DELETE_NOTE = "mongo.database.note.delete";

  public static final String SUCCESSFUL = "SUCCESSFUL";

  private MongoClient mongo;

  private String dbname = "notes";
  private String usersCollection = "users";
  private String notesCollection = "notes";

  private static final String UNKNOWN_ERROR = "Unknown database error";
  private static final String NAME_CONFLICT = "Resource with same name exists in system";
  private static final String NO_RESOURCE = "There is no such resource";

  public DbVerticle() {

  }

  @Override
  public void start(Future<Void> fut) {
    JsonObject config = new JsonObject(ImmutableMap
      .<String, Object>builder()
      .put("host", "127.0.0.1")
      .put("port", 27017)
      .put("db_name", dbname)
      .build());
    mongo = MongoClient.createShared(vertx, config);

    EventBus eventBus = vertx.eventBus();

    eventBus.consumer(USER_INSERT, this::insertUser);
    eventBus.consumer(USER_GET, this::getUser);
    eventBus.consumer(NOTE_INSERT, this::insertNote);
    eventBus.consumer(NOTE_GET, this::getNote);
    eventBus.consumer(GET_USER_NOTES, this::getUserNotes);
    eventBus.consumer(UPDATE_NOTE, this::updateNote);
    eventBus.consumer(DELETE_NOTE, this::deleteNote);

    fut.complete();
  }

  private void insertUser(final Message<JsonObject> message) {
    JsonObject body = message.body();

    ifNotExists(usersCollection, new JsonObject().put("username", body.getString("username")),
      () -> mongo.insert(usersCollection, body, res -> {
        if (res.succeeded()) {
          message.reply(res.result());
        } else {
          message.fail(500, UNKNOWN_ERROR);
        }
      }),
      () -> message.fail(409, NAME_CONFLICT)
    );
  }

  private void getUser(final Message<JsonObject> message) {
    JsonObject query = message.body();

    mongo.findOne(usersCollection, query, null, response -> {
      if (response.succeeded()) {
        message.reply(response.result());
      } else {
        message.fail(500, UNKNOWN_ERROR);
      }
    });
  }

  private void insertNote(final Message<JsonObject> message) {

    JsonObject body = message.body();
    JsonObject query = new JsonObject()
      .put("name", body.getString("name"))
      .put("owner", body.getString("owner"));

    ifNotExists(notesCollection, query,
      () -> mongo.insert(notesCollection, body, result -> {
        if (result.succeeded()) {
          message.reply(result.result());
        } else
          message.fail(500, UNKNOWN_ERROR);
      }),
      () -> message.fail(409, NAME_CONFLICT)
    );
  }

  private void getUserNotes(final Message<String> message) {
    String userId = message.body();

    mongo.find(notesCollection, new JsonObject().put("owner", userId), result -> {
      if (result.succeeded()) {
        List<JsonObject> notes = result.result();
        notes.stream().map(note -> {
          Object noteID = note.remove("_id");
          try {
            JsonObject objectID = (JsonObject) noteID;
            note.put("id", objectID.getString("$oid"));
          } catch (ClassCastException ex) {
            note.put("id", noteID);
          } finally {
            return note;
          }
        }).collect(Collectors.toList());

        message.reply(new JsonArray(notes));
      } else {
        message.fail(500, UNKNOWN_ERROR);
      }
    });
  }

  private void getSharedNotes(final Message<String> message) {

  }


  private void getNote(final Message<String> message) {
    String noteID = message.body();

    mongo.findOne(notesCollection, new JsonObject().put("_id", noteID), null, response -> {
      if (response.succeeded()) {
        if (Objects.isNull(response.result())) {
          message.fail(404, NO_RESOURCE);
        } else {
          message.reply(response.result());
        }
      } else {
        message.fail(500, UNKNOWN_ERROR);
      }
    });
  }

  private void updateNote(final Message<JsonObject> message) {
    JsonObject body = message.body();
    JsonObject query = body.getJsonObject("query");
    JsonObject newData = body.getJsonObject("data");

    mongo.findOneAndUpdate(notesCollection, query, new JsonObject().put("$set", newData), result -> {
      if (result.succeeded() && Objects.nonNull(result.result())) {
        message.reply(SUCCESSFUL);
      } else if (result.succeeded() && Objects.isNull(result.result())) {
        message.fail(404, NO_RESOURCE);
      } else {
        result.cause().printStackTrace();
        message.fail(500, UNKNOWN_ERROR);
      }
    });
  }

  private void deleteNote(final Message<String> message) {
    String noteID = message.body();

    mongo.removeDocument(notesCollection, new JsonObject().put("_id", noteID), result -> {
      if (result.succeeded()) {
        if (result.result().getRemovedCount() >= 1)
          message.reply(SUCCESSFUL);
        else {
          message.fail(404, NO_RESOURCE);
        }
      } else {
        message.fail(500, UNKNOWN_ERROR);
      }
    });
  }

  private void ifNotExists(String collection, JsonObject query, final Runnable task, final Runnable orElse) {
    mongo.findOne(collection, query, null, result -> {
      if (Objects.isNull(result.result())) {
        task.run();
      } else {
        orElse.run();
      }
    });
  }
}
