package worekleszczy.verticles;

import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Bytes;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import jdk.nashorn.internal.ir.CallNode;
import org.bson.BsonBinary;
import org.bson.BsonBinarySubType;
import org.bson.types.ObjectId;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class DbVerticle extends AbstractVerticle {

  public static final String USER_INSERT = "mongo.database.users.insert";
  public static final String USER_GET = "mongo.database.users.get";
  public static final String NOTE_INSERT = "mongo.database.note.insert";
  public static final String NOTE_GET = "mongo.database.note.get";
  public static final String GET_USER_NOTES = "mongo.database.notes.get";

  public static final String SUCCESSFUL = "SUCCESSFUL";
  public static final String ERROR = "ERROR";
  public static final String CONFLICT = "CONFLICT";

  private MongoClient mongo;

  private String dbname = "notes";
  private String usersCollection = "users";
  private String notesCollection = "notes";

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

    fut.complete();
  }

  private void insertUser(final Message<Object> message) {
    JsonObject body = (JsonObject) message.body();

      ifNotExists(usersCollection, new JsonObject().put("username", body.getString("username")),
        () -> {
          mongo.insert(usersCollection, body, res -> {
            JsonObject response = new JsonObject();
            if (res.succeeded()) {
              response.put("status", SUCCESSFUL).put("result", res.result());
              message.reply(response);
            }
            else {
              message.reply(response.put("status", ERROR));
            }
          });
        }, () -> {
          message.reply(new JsonObject().put("status", CONFLICT));
        });
  }

  private void getUser(final Message<Object> message) {
      JsonObject query = (JsonObject) message.body();

      mongo.findOne(usersCollection, query, null, response -> {
        JsonObject resJson = new JsonObject();
        if (response.succeeded()) {
          resJson.put("status", SUCCESSFUL)
            .put("result", response.result());
          message.reply(resJson);
        }
        else {
          resJson.put("status", ERROR);
          message.reply(resJson);
        }
      });
  }

  private void insertNote(final Message<Object> message) {

    JsonObject body = (JsonObject) message.body();
    JsonObject query = new JsonObject().put("name", body.getString("name"));

    ifNotExists(notesCollection, query, () -> {
      mongo.insert(notesCollection, body, result -> {
        if(result.succeeded()) {
          message.reply(result.result());
        }
        else
          result.cause().printStackTrace();
      });
    }, () -> {
      message.reply(new JsonObject().put("Error", "Note already exists"));
    });
  }

  private void getUserNotes(final Message<String> message) {
    String userId = message.body();

    mongo.find(notesCollection, new JsonObject().put("owner", userId), result -> {
      if(result.succeeded()) {
        List<JsonObject> notes = result.result();
        message.reply(new JsonArray(notes));
      } else {
        message.reply(new JsonObject().put("Error", "Unknown error occurred"));
      }
    });
  }

  private void getSharedNotes(final Message<String> message) {

  }


    private void getNote(final Message<String> message) {
    String id = message.body();

    mongo.findOne(notesCollection, new JsonObject().put("_id", id), null, response -> {
      if (response.succeeded()) {
        message.reply(response.result());
      }
      else {
        // logging should happen here
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
