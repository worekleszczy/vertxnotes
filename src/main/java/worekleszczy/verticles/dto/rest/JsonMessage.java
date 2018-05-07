package worekleszczy.verticles.dto.rest;

import io.vertx.core.json.Json;

public class JsonMessage {
  public static String error(String message) {
    return Json.encode(new Error(message));
  }

  public static String token(String token) {
    return Json.encode(new Token(token));
  }

  public static String resource(String id) {
    return Json.encode(new ResourceID(id));
  }
}
