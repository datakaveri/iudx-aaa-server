package iudx.aaa.server.apiserver.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.Response.ResponseBuilder;
import iudx.aaa.server.apiserver.User;
import static iudx.aaa.server.apiserver.util.Constants.*;
import static iudx.aaa.server.token.Constants.LOG_DB_ERROR;

public class ProviderAuthentication implements Handler<RoutingContext> {
  
  private static final Logger LOGGER = LogManager.getLogger(ProviderAuthentication.class);
  private PgPool pgPool;
  private String authServerDomain;
  
  public ProviderAuthentication(PgPool pgPool, String domain) {
    this.pgPool = pgPool;
    this.authServerDomain = domain;
  }

  @Override
  public void handle(RoutingContext routingContext) {
    HttpServerRequest request = routingContext.request();
    MultiMap headers = request.headers();
    User user = routingContext.get(USER);

    if (!headers.contains(HEADER_PROVIDER_ID)) {
      LOGGER.debug("Info: {}; {}", ERR_PROVDERID, "null");
      routingContext.next();
      return;
    }
    
    String providerId = headers.get(HEADER_PROVIDER_ID);
    
    if(providerId.isBlank()) {
      LOGGER.error("Fail: {}; {}", ERR_PROVDERID, "empty/null");
      Response rs = new ResponseBuilder().status(401).type(URN_MISSING_AUTH_TOKEN)
          .title(INVALID_PROVERID).detail(INVALID_PROVERID).build();
      routingContext.fail(new Throwable(rs.toJsonString()));
    }
    
    Tuple tuple = Tuple.of(authServerDomain,user.getUserId(),providerId);
    pgQueryHandler(GET_DELEGATE, tuple).onComplete(dbHandler ->{
      if (dbHandler.failed()) {
        LOGGER.error(LOG_DB_ERROR + dbHandler.cause());
        Response rs = new ResponseBuilder().title(INTERNAL_SVR_ERR).status(500)
            .detail(dbHandler.cause().getLocalizedMessage()).build();
        routingContext.fail(new Throwable(rs.toJsonString()));
      } else if (dbHandler.succeeded()) {
        JsonObject results = dbHandler.result();
        if (results.isEmpty()) {
          Response rs = new ResponseBuilder().status(401).type(URN_MISSING_AUTH_TOKEN)
              .title(ERR_DELEGATE).detail(ERR_DELEGATE).build();
          routingContext.fail(new Throwable(rs.toJsonString()));
        }else {
          LOGGER.debug("Success: Provider/delegate authenticated");
        }
      }

      JsonObject data = new JsonObject().put(HEADER_PROVIDER_ID, providerId);
      routingContext.put(DATA, data).next();
    });
  }
  
  /**
   * Handles database queries.
   * 
   * @param sql query
   * @param if of the element
   * @return Future promise which is JsonObject
   */
  public Future<JsonObject> pgQueryHandler(String query, Tuple tuple) {

    Promise<JsonObject> promise = Promise.promise();
    pgPool.withConnection(connection -> connection.preparedQuery(query).execute(tuple)
        .map(rows -> rows.rowCount() > 0 ? rows.iterator().next().toJson() : new JsonObject())
        .onComplete(handler -> {
          if (handler.succeeded()) {
            JsonObject details = handler.result();
            promise.complete(details);
          } else if (handler.failed()) {
            LOGGER.error(LOG_DB_ERROR+ handler.cause());
            promise.fail(INTERNAL_SVR_ERR);
          }
        }));
    return promise.future();
  }
}
