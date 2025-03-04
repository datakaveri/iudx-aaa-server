package iudx.aaa.server.database.postgres.service;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import iudx.aaa.server.common.models.Query;

@VertxGen
@ProxyGen
public interface PostgresService {
  Future<JsonObject> executeQuery(final String query);

  /**
   * The executeCountQuery implements a count of records operation on the database.
   *
   * @param query which is a String
   * @return PostgresService which is a service
   */
  Future<JsonObject> executeCountQuery(final String query);

  @GenIgnore
  static PostgresService createProxy(Vertx vertx, String address) {
    return new PostgresServiceVertxEBProxy(vertx, address);
  }
}
