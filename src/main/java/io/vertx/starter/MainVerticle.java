package io.vertx.starter;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import worekleszczy.verticles.DbVerticle;
import worekleszczy.verticles.HttpServerVerticle;

public class MainVerticle extends AbstractVerticle {

  @Override
  public void start(Future<Void> mainFuture) {
    Future<String> dbFuture = Future.future();
    Future<String> routerFuture = Future.future();

    vertx.deployVerticle(new DbVerticle(), dbFuture.completer());
    vertx.deployVerticle(new HttpServerVerticle(), routerFuture.completer());

    EventBus bus = vertx.eventBus();

    CompositeFuture.all(dbFuture, routerFuture).setHandler(req -> {

    });

  }

}
