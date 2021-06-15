package iudx.aaa.server.postgres.client;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;
import static iudx.aaa.server.token.Constants.*;

public class PostgresClient {
  private static final Logger LOGGER = LogManager.getLogger(PostgresClient.class);

  private PgPool pgPool;

  public PostgresClient(Vertx vertx, PgConnectOptions pgConnectOptions,
      PoolOptions connectionPoolOptions) {
    this.pgPool = PgPool.pool(vertx, pgConnectOptions, connectionPoolOptions);  
  }

  /**
   * Handles the select User query from user_client.
   * 
   * @param tuple
   * @param handler
   * @return JsonArray Handler
   */
  public PostgresClient selectUserQuery(Tuple tuple, Handler<AsyncResult<JsonArray>> handler) {

    Future<RowSet<Row>> future = executeAsync(GET_CLIENT, tuple);
    future.onComplete(resultHandler -> {
      if (resultHandler.succeeded()) {
        /* Converts the SQL RowSet to JsonArray. */
        JsonArray jsonResult = new JsonArray();
        for (Row each : resultHandler.result()) {
          jsonResult.add(each.toJson());
        }
        handler.handle(Future.succeededFuture(jsonResult));
      } else if (resultHandler.failed()) {
        handler.handle(Future.failedFuture(resultHandler.cause()));
      }
    });
    return this;
  }

  public Future<RowSet<Row>> executeAsync(String preparedQuerySQL, Tuple tuple) {
    LOGGER.debug("Info : PostgresQLClient#executeAsync() started");
    Promise<RowSet<Row>> promise = Promise.promise();
    pgPool.getConnection(connectionHandler -> {
      if (connectionHandler.succeeded()) {
        SqlConnection pgConnection = connectionHandler.result();
        pgConnection.preparedQuery(preparedQuerySQL).execute(tuple, handler -> {
          if (handler.succeeded()) {
            pgConnection.close();
            promise.complete(handler.result());
          } else {
            pgConnection.close();
            LOGGER.fatal("Fail : " + handler.cause());
            promise.fail(handler.cause());
          }
        });
      } else {
        LOGGER.fatal("Fail: Database Connection failed; "+ connectionHandler.cause().getMessage());
        promise.fail(connectionHandler.cause());
      }
    });
    
    return promise.future();
  }
}
