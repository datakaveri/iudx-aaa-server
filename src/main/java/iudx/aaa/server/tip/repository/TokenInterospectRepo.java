package iudx.aaa.server.tip.repository;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;
import org.jooq.DSLContext;
import org.jooq.Query;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import iudx.aaa.server.postgres.client.PostgresClient;

public class TokenInterospectRepo {

  private PostgresClient pgClient;

  public TokenInterospectRepo(PostgresClient pgClient) {
    this.pgClient = pgClient;
  }


  private String getQuery(String token) {
    DSLContext create = DSL.using(SQLDialect.POSTGRES);
    Query query = create.select(field("*"))
        .from(table("TOKEN"))
        .join(table("ACCESS"))
        .on(field("TOKEN.ID").eq(field("ACCESS.TOKEN_ID")))
        .where(field("TOKEN.ID").eq(token));

    return query.toString();

  }

  public Future<RowSet<Row>> getTokenAccessDetails(String token) {
    Promise<RowSet<Row>> promise = Promise.promise();
    String query = getQuery(token);
    pgClient.executeAsync(query).onComplete(handler -> {
      if (handler.succeeded()) {
        promise.complete(handler.result());
      } else {
        promise.fail("DB query failed"+handler.cause());
      }
    });
    return promise.future();
  }

}
