package worekleszczy.verticles.dto.rest;

public class Token {
  final private String token;

  public Token(String token) {
    this.token = token;
  }

  public String getToken() {
    return token;
  }
}
