package iudx.aaa.server.tip;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.policy.PolicyService;
import iudx.aaa.server.postgres.client.PostgresClient;
import static iudx.aaa.server.token.Constants.*;

/**
 * The TIP Service Implementation.
 * <h1>TIP Service Implementation</h1>
 * <p>
 * The TIP Service implementation in the IUDX AAA Server implements the definitions of the
 * {@link iudx.aaa.server.tip.TIPService}.
 * </p>
 * 
 * @version 1.0
 * @since 2020-12-15
 */

public class TIPServiceImpl implements TIPService {

  private static final Logger LOGGER = LogManager.getLogger(TIPServiceImpl.class);

  private PostgresClient pgClient;
  private PolicyService policyService;
  private JWTAuth provider;

  public TIPServiceImpl(PostgresClient pgClient, PolicyService policyService, JWTAuth provider) {
    this.pgClient = pgClient;
    this.policyService = policyService;
    this.provider = provider;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public TIPService validateToken(JsonObject request, Handler<AsyncResult<JsonObject>> handler) {
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");
    
    if (request.containsKey(ACCESS_TOKEN)) {
      TokenCredentials authInfo = new TokenCredentials(request.getString(ACCESS_TOKEN));
      provider.authenticate(authInfo).onSuccess(jwtDetails -> {

        JsonObject accessTokenJwt =
            jwtDetails.attributes().getJsonObject(ACCESS_TOKEN);

        String clientId = accessTokenJwt.getString(SUB);
        String role = accessTokenJwt.getString(ROLE);
        String itemId = accessTokenJwt.getString(ITEM_ID);
        String itemType = accessTokenJwt.getString(ITEM_TYPE);

        Tuple tuple = Tuple.of(clientId);
        pgClient.selectQuery(GET_USER, tuple, dbHandler -> {
          if (dbHandler.succeeded()) {
            if (dbHandler.result().size() == 1) {
              
              request.clear();
              JsonObject result = dbHandler.result().getJsonObject(0);
              request.put("userId", result.getString(USER_ID))
                     .put("clientId", clientId)
                     .put("role", role)
                     .put("itemId", itemId)
                     .put("itemType", itemType);
              
              policyService.verifyPolicy(request, policyHandler -> {
                if (policyHandler.succeeded()) {
                  request.clear();
                  request.put(STATUS, ALLOW);
                  request.mergeIn(accessTokenJwt);

                  LOGGER.info("Info: Policy evaluation succeeded; Token authenticated");
                  handler.handle(Future.succeededFuture(request));
                } else if (policyHandler.failed()) {
                  LOGGER.error("Fail: Policy evaluation failed; "
                      + policyHandler.cause().getLocalizedMessage());
                  handler.handle(
                      Future.failedFuture(new JsonObject().put(STATUS, DENY).toString()));
                }
              });
            } else {
              LOGGER.error("Fail: Invalid accessToken- clientId");
              handler
                  .handle(Future.failedFuture(new JsonObject().put(STATUS, DENY).toString()));
            }
          } else {
            LOGGER.error("Fail: Databse query; " + dbHandler.cause().getMessage());
            handler.handle(Future.failedFuture(new JsonObject().put(STATUS, FAILED)
                .put(DESC, dbHandler.cause().getLocalizedMessage()).toString()));
          }
        });
      }).onFailure(jwtError -> {
        LOGGER.error("Fail: Token authentication failed; " + jwtError.getLocalizedMessage());
        handler.handle(Future.failedFuture(new JsonObject().put(STATUS, DENY).toString()));
      });
    } else {
      LOGGER.error("Fail: Unable to parse accessToken from request");
      handler.handle(Future.failedFuture(
          new JsonObject().put(STATUS, FAILED).put(DESC, "missing accessToken").toString()));
    }
    return this;
  }
}
