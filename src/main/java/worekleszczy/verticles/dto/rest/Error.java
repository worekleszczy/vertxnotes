package worekleszczy.verticles.dto.rest;

public class Error {
  final private String error;
  public Error(String error) {
    this.error = error;
  }

  public String getError() {
    return error;
  }
}
