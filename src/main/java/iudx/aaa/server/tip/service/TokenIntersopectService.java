package iudx.aaa.server.tip.service;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import iudx.aaa.server.tip.repository.TokenInterospectRepo;

public class TokenIntersopectService {

  private TokenInterospectRepo repository;

  public TokenIntersopectService(TokenInterospectRepo repository) {
    this.repository = repository;
  }


  public Future<JsonObject> validateAccess(String token) {
    Promise<JsonObject> promise = Promise.promise();
    repository.getTokenAccessDetails(token).onComplete(handler -> {
      if (handler.succeeded()) {
        RowSet<Row> result=handler.result();
        for(Row row:result) {
          System.out.println(row.getString("id"));
          //run token validation/access validation logic here.
        }
      } else {
        promise.fail("cause"+handler.cause());
      }
    });
    return promise.future();
  }
}
