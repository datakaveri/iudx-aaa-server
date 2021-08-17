package iudx.aaa.server.apiserver.util;

import static iudx.aaa.server.apiserver.util.Constants.CLIENT_ID;
import static iudx.aaa.server.apiserver.util.Constants.CLIENT_SECRET;
import static iudx.aaa.server.apiserver.util.Constants.ID;
import static iudx.aaa.server.apiserver.util.Constants.INTERNAL_SVR_ERR;
import static iudx.aaa.server.apiserver.util.Constants.KID;
import static iudx.aaa.server.apiserver.util.Constants.MISSING_TOKEN_CLIENT;
import static iudx.aaa.server.apiserver.util.Constants.ROLES;
import static iudx.aaa.server.apiserver.util.Constants.SQL_GET_KID_ROLES;
import static iudx.aaa.server.apiserver.util.Constants.TOKEN_FAILED;
import static iudx.aaa.server.apiserver.util.Constants.URN_MISSING_AUTH_TOKEN;
import static iudx.aaa.server.apiserver.util.Constants.USER;
import static iudx.aaa.server.token.Constants.INVALID_CLIENT_ID_SEC;
import static iudx.aaa.server.token.Constants.LOG_DB_ERROR;
import static iudx.aaa.server.token.Constants.LOG_UNAUTHORIZED;
import static iudx.aaa.server.token.Constants.LOG_USER_SECRET;
import static iudx.aaa.server.token.Constants.URN_INVALID_INPUT;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.crypto.generators.OpenBSDBCrypt;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.authentication.Credentials;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthenticationHandler;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.Response.ResponseBuilder;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;

public class ClientAuthentication implements AuthenticationHandler{

  private static final Logger LOGGER = LogManager.getLogger(ClientAuthentication.class);
  private PgPool pgPool;
  
  public ClientAuthentication(PgPool pgPool) {
    this.pgPool = pgPool;
  }
  
  @Override
  public void handle(RoutingContext routingContext) {
    
    MultiMap headers = routingContext.request().headers();
    User.UserBuilder user = new UserBuilder();
    
    if (Objects.nonNull(routingContext.get(USER))) {
      routingContext.next();
      return;
    }
    
    if (headers.contains(CLIENT_ID) && headers.contains(CLIENT_SECRET)) {
      String clientId = headers.get(CLIENT_ID);
      String clientSecret = headers.get(CLIENT_SECRET);

      if (clientId != null && !clientId.isBlank()) {

        pgSelectUser(SQL_GET_KID_ROLES, clientId).onComplete(dbHandler -> {
          if (dbHandler.failed()) {
            LOGGER.error(LOG_DB_ERROR + dbHandler.cause());
            Response rs = new ResponseBuilder().title(INTERNAL_SVR_ERR).status(500)
                .detail(dbHandler.cause().getLocalizedMessage()).build();
            routingContext.fail(new Throwable(rs.toJsonString()));
            return;
          } else if (dbHandler.succeeded()) {
            if (dbHandler.result().isEmpty()) {
              Response rs = new ResponseBuilder().status(401).type(URN_MISSING_AUTH_TOKEN)
                  .title(TOKEN_FAILED).detail(TOKEN_FAILED).build();
              routingContext.fail(new Throwable(rs.toJsonString()));
              routingContext.end();
            }
          }

          JsonObject result = dbHandler.result();
          String dbClientSecret = result.getString("client_secret");

          /* Validating clientSecret hash */
          boolean valid = false;
          try {
            valid = OpenBSDBCrypt.checkPassword(dbClientSecret,
                clientSecret.getBytes(StandardCharsets.UTF_8));
          } catch (Exception e) {
            LOGGER.error(LOG_USER_SECRET + e.getLocalizedMessage());
            Response resp = new ResponseBuilder().status(400).type(URN_INVALID_INPUT)
                .title(INVALID_CLIENT_ID_SEC).detail(INVALID_CLIENT_ID_SEC).build();
            routingContext.fail(new Throwable(resp.toJson().toString()));
            return;
          }

          if (valid == Boolean.FALSE) {
            LOGGER.error(LOG_UNAUTHORIZED + INVALID_CLIENT_ID_SEC);
            Response resp = new ResponseBuilder().status(401).type(URN_INVALID_INPUT)
                .title(INVALID_CLIENT_ID_SEC).detail(INVALID_CLIENT_ID_SEC).build();
            routingContext.fail(new Throwable(resp.toJson().toString()));
            return;
          }

          LOGGER.info("Info: client authenticated");
          user.keycloakId(result.getString(KID)).userId(result.getString(ID))
              .roles(processRoles(result.getJsonArray(ROLES)));

          routingContext.put(CLIENT_ID, clientId);
          routingContext.put(USER, user.build()).next();
        });
      } else  {
        LOGGER.error("Fail: {}; {}", INVALID_CLIENT_ID_SEC, "null clientId/token");
        Response rs = new ResponseBuilder().status(401).type(URN_MISSING_AUTH_TOKEN)
            .title(INVALID_CLIENT_ID_SEC).detail(INVALID_CLIENT_ID_SEC).build();
        routingContext.fail(new Throwable(rs.toJsonString()));
      }
    } else {
      LOGGER.error("Fail: {}; {}", MISSING_TOKEN_CLIENT, "null clientId/token");
      Response rs = new ResponseBuilder().status(401).type(URN_MISSING_AUTH_TOKEN)
          .title(MISSING_TOKEN_CLIENT).detail(MISSING_TOKEN_CLIENT).build();
      routingContext.fail(new Throwable(rs.toJsonString()));
    }
  }
  
  /**
   * Handles database queries.
   * 
   * @param sql query
   * @param if of the element
   * @return Future promise which is JsonObject
   */
  public Future<JsonObject> pgSelectUser(String query, String id) {

    Promise<JsonObject> promise = Promise.promise();
    pgPool.withConnection(connection -> connection.preparedQuery(query).execute(Tuple.of(id))
        .map(rows -> rows.rowCount() > 0 ? rows.iterator().next().toJson() : new JsonObject())
        .onComplete(handler -> {
          if (handler.succeeded()) {
            JsonObject details = handler.result();
            promise.complete(details);
          } else if (handler.failed()) {
            promise.fail(handler.cause());
          }
        }));
    return promise.future();
  }
  
  /**
   * Creates Roles enum.
   * 
   * @param role
   * @return List having Roles
   */
  public List<Roles> processRoles(JsonArray role) {
    List<Roles> roles = role.stream().filter(a -> Roles.exists(a.toString()))
        .map(a -> Roles.valueOf(a.toString())).collect(Collectors.toList());

    return roles;
  }

  @Override
  public void parseCredentials(RoutingContext context, Handler<AsyncResult<Credentials>> handler) {
    handler.handle(Future.succeededFuture());
  }
}
