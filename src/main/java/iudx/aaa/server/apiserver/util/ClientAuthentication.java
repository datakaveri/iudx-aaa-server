package iudx.aaa.server.apiserver.util;

import static iudx.aaa.server.apiserver.util.Constants.CLIENT_ID;
import static iudx.aaa.server.apiserver.util.Constants.CLIENT_SECRET;
import static iudx.aaa.server.apiserver.util.Constants.INTERNAL_SVR_ERR;
import static iudx.aaa.server.apiserver.util.Constants.OBTAINED_USER_ID;
import static iudx.aaa.server.apiserver.util.Constants.MISSING_TOKEN_CLIENT;
import static iudx.aaa.server.apiserver.util.Constants.SQL_GET_DETAILS_BY_CLIENT_ID;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_INPUT;
import static iudx.aaa.server.apiserver.util.Urn.URN_MISSING_AUTH_TOKEN;
import static iudx.aaa.server.token.Constants.INVALID_CLIENT_ID_SEC;
import static iudx.aaa.server.token.Constants.LOG_DB_ERROR;
import static iudx.aaa.server.token.Constants.LOG_UNAUTHORIZED;
import static iudx.aaa.server.token.Constants.LOG_USER_SECRET;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.Response.ResponseBuilder;
import java.security.MessageDigest;
import java.util.Objects;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Handles client ID - client secret authentication, specifically for the get token API. Validates
 * that client ID exists and the corresponding client secret matches. If validation successful, add
 * user ID obtained from client ID lookup to the routing context.
 */
public class ClientAuthentication implements Handler<RoutingContext>{

  private static final Logger LOGGER = LogManager.getLogger(ClientAuthentication.class);
  private PgPool pgPool;
  
  public ClientAuthentication(PgPool pgPool) {
    this.pgPool = pgPool;
  }
  
  @Override
  public void handle(RoutingContext routingContext) {
    
    MultiMap headers = routingContext.request().headers();
    
    if (Objects.nonNull(routingContext.get(OBTAINED_USER_ID))) {
      routingContext.next();
      return;
    }
    
    if (headers.contains(CLIENT_ID) && headers.contains(CLIENT_SECRET)) {
      String clientId = headers.get(CLIENT_ID);
      String clientSecret = headers.get(CLIENT_SECRET);

      if (clientId != null && !clientId.isBlank()) {

        pgPool.withConnection(connection -> connection.preparedQuery(SQL_GET_DETAILS_BY_CLIENT_ID)
            .execute(Tuple.of(clientId))
            .map(rows -> rows.rowCount() > 0 ? rows.iterator().next().toJson() : new JsonObject()))
            .onComplete(dbHandler -> {
              if (dbHandler.failed()) {

                LOGGER.error(LOG_DB_ERROR + dbHandler.cause());
                Response rs = new ResponseBuilder().title(INTERNAL_SVR_ERR).status(500)
                    .detail(INTERNAL_SVR_ERR).build();
                routingContext.fail(new Throwable(rs.toJsonString()));
                return;
              } else if (dbHandler.succeeded()) {
                if (dbHandler.result().isEmpty()) {
                  Response rs = new ResponseBuilder().status(401).type(URN_INVALID_INPUT)
                      .title(INVALID_CLIENT_ID_SEC).detail(INVALID_CLIENT_ID_SEC).build();
                  routingContext.fail(new Throwable(rs.toJsonString()));
                  return;
                }
              }

          JsonObject result = dbHandler.result();
          String dbClientSecret = result.getString("client_secret");

          /* Validating clientSecret hash */
          boolean valid = false;
          try {
            byte[] requestSecretHashed = DigestUtils.sha512(clientSecret);
            byte[] dbSecretHashed = Hex.decodeHex(dbClientSecret.toCharArray());

            valid = MessageDigest.isEqual(dbSecretHashed, requestSecretHashed);
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

          routingContext.put(OBTAINED_USER_ID, result.getString("user_id")).next();
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
}