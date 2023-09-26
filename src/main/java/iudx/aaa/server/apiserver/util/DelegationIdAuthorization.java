package iudx.aaa.server.apiserver.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.DelegationInformation;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.Response.ResponseBuilder;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import static iudx.aaa.server.apiserver.util.Constants.*;
import static iudx.aaa.server.apiserver.util.Urn.*;
import static iudx.aaa.server.token.Constants.LOG_DB_ERROR;
import java.util.UUID;

/**
 * Validates the <em>delegationId</em> header based on the incoming user and puts all information
 * about the delegation as {@link DelegationInformation} object onto the {@link RoutingContext}.
 */
public class DelegationIdAuthorization implements Handler<RoutingContext> {
  
  private static final Logger LOGGER = LogManager.getLogger(DelegationIdAuthorization.class);
  private PgPool pgPool;
  
  public DelegationIdAuthorization(PgPool pgPool) {
    this.pgPool = pgPool;
  }

  @Override
  public void handle(RoutingContext routingContext) {
    HttpServerRequest request = routingContext.request();
    MultiMap headers = request.headers();
    User user = routingContext.get(USER);

    if (!headers.contains(HEADER_DELEGATION_ID)) {
      LOGGER.debug("Info: {}; {}", ERR_PROVDERID, "null");
      routingContext.next();
      return;
    }

    if(!user.getRoles().contains(Roles.DELEGATE))
    {
      Response rs = new ResponseBuilder().status(401).type(URN_INVALID_INPUT)
          .title(INVALID_DELEGATION_ID).detail(INVALID_DELEGATION_ID).build();
      routingContext.fail(new Throwable(rs.toJsonString()));
      return;
    }
    
    String delegationIdStr = headers.get(HEADER_DELEGATION_ID);
    
    if(delegationIdStr.isBlank()) {
      LOGGER.error("Fail: {}; {}", ERR_PROVDERID, "empty/null");
      Response rs = new ResponseBuilder().status(401).type(URN_INVALID_INPUT)
          .title(INVALID_DELEGATION_ID).detail(INVALID_DELEGATION_ID).build();
      routingContext.fail(new Throwable(rs.toJsonString()));
      return;
    }

    UUID delegationId = UUID.fromString(delegationIdStr);
    
    Tuple tuple = Tuple.of(delegationId, user.getUserId());
    
    pgPool
        .withConnection(
            conn -> conn.preparedQuery(SQL_GET_DELEGATION_BY_USER_AND_DELEG_ID).execute(tuple))
        .onFailure(fail -> {
          LOGGER.error(LOG_DB_ERROR + fail.getLocalizedMessage());
          Response rs = new ResponseBuilder().title(INTERNAL_SVR_ERR).status(500)
              .detail(INTERNAL_SVR_ERR).build();
          routingContext.fail(new Throwable(rs.toJsonString()));
        }).onSuccess(rows -> {
          if (rows.rowCount() == 0) {
            Response rs = new ResponseBuilder().status(401).type(URN_INVALID_INPUT)
                .title(ERR_DELEGATE).detail(ERR_DELEGATE).build();
            routingContext.fail(new Throwable(rs.toJsonString()));
          }
          
          JsonObject data = rows.iterator().next().toJson();
          DelegationInformation delegInfo = new DelegationInformation(data);

          routingContext.put(DELEGATION_INFO, delegInfo).next();
        });
  }
}
