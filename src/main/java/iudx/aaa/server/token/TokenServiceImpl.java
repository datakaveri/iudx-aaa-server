package iudx.aaa.server.token;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.crypto.generators.OpenBSDBCrypt;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.policy.PolicyService;
import iudx.aaa.server.postgres.client.PostgresClient;
import static iudx.aaa.server.token.Constants.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The Token Service Implementation.
 * <h1>Token Service Implementation</h1>
 * <p>
 * The Token Service implementation in the IUDX AAA Server implements the definitions of the
 * {@link iudx.aaa.server.token.TokenService}.
 * </p>
 * 
 * @version 1.0
 * @since 2020-12-15
 */

public class TokenServiceImpl implements TokenService {

  private static final Logger LOGGER = LogManager.getLogger(TokenServiceImpl.class);

  private PostgresClient pgClient;
  private JWTAuth provider;
  private PolicyService policyService;
  private HttpWebClient httpWebClient;

  public TokenServiceImpl(PostgresClient pgClient, PolicyService policyService, JWTAuth provider,
      HttpWebClient httpWebClient) {
    this.pgClient = pgClient;
    this.policyService = policyService;
    this.provider = provider;
    this.httpWebClient = httpWebClient;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public TokenService createToken(JsonObject request, Handler<AsyncResult<JsonObject>> handler) {
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    String clientId = request.getString("clientId");
    String clientSecret = request.getString("clientSecret");
    String role = request.getString("role");
    JsonArray roleArray = request.getJsonArray("roleList");

    Tuple tuple = Tuple.of(clientId);

    pgClient.selectQuery(GET_USER, tuple, dbHandler -> {
      if (dbHandler.succeeded()) {
        if (dbHandler.result().size() == 1) {
          JsonObject result = dbHandler.result().getJsonObject(0);
          String dbClientSecret = result.getString("client_secret");

          boolean valid = false;
          try {
            valid = OpenBSDBCrypt.checkPassword(dbClientSecret,
                clientSecret.getBytes(StandardCharsets.UTF_8));
          } catch (Exception e) {
            LOGGER.error("Fail: Invalid clientSecret format; " + e.getLocalizedMessage());
            handler.handle(Future.failedFuture(new JsonObject().put("status", "failed")
                .put("desc", "Unauthorized user").toString()));
            return;
          }

          if (valid == Boolean.TRUE) {
            if (roleArray.contains(role)) {
              request.put("userId", result.getString("user_id"));

              policyService.verifyPolicy(request, policyHandler -> {
                if (policyHandler.succeeded()) {

                  request.mergeIn(policyHandler.result(), true);
                  String jwt = getJwt(request);
                  
                  LOGGER.info("Info: Policy evaluation succeeded; JWT generated & signed");
                  handler.handle(Future
                      .succeededFuture(new JsonObject().put("status", "success").put("accessToken", jwt)));

                } else if (policyHandler.failed()) {
                  LOGGER.error("Fail: Unauthorized access; Policy evaluation failed");
                  handler.handle(Future.failedFuture(new JsonObject().put("status", "failed")
                      .put("desc", "Unauthorized user").toString()));
                }
              });
            } else {
              LOGGER.error("Fail: Unauthorized access; Role not defined");
              handler.handle(Future.failedFuture(new JsonObject().put("status", "failed")
                  .put("desc", "Unauthorized user").toString()));
            }
          } else {
            LOGGER.error("Fail: Unauthorized access; Invalid clientId/clientSecret");
            handler.handle(Future.failedFuture(new JsonObject().put("status", "failed")
                .put("desc", "Invalid clientId/clientSecret").toString()));
          }
        } else {
          LOGGER.error("Fail: Unauthorized access; Invalid clientId/clientSecret");
          handler.handle(Future.failedFuture(new JsonObject().put("status", "failed")
              .put("desc", "Invalid clientId/clientSecret").toString()));
        }

      } else {
        LOGGER.error("Fail: Databse query; " + dbHandler.cause().getMessage());
        handler.handle(Future.failedFuture(new JsonObject().put("status", "failed")
            .put("desc", "Internal server error").toString()));
      }
    });

    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public TokenService revokeToken(JsonObject request, Handler<AsyncResult<JsonObject>> handler) {
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    String userId = request.getString("userId");
    String clientId = request.getString("clientId");
    String rsUrl = request.getString("rsUrl");

    if (userId != null && !userId.isBlank()) {

      Tuple clientTuple = Tuple.of(userId);
      pgClient.selectQuery(GET_CLIENT, clientTuple, dbHandler -> {
        if (dbHandler.succeeded()) {

          if (dbHandler.result().size() == 1) {
            JsonObject dbClientRow = dbHandler.result().getJsonObject(0);
            String dbClientId = dbClientRow.getString("client_id");

            if (dbClientId.equals(clientId)) {
              Tuple rsUrlTuple = Tuple.of(rsUrl);

              pgClient.selectQuery(GET_URL, rsUrlTuple, selectHandler -> {
                if (selectHandler.succeeded()) {
                  JsonObject dbExistsRow = selectHandler.result().getJsonObject(0);
                  boolean flag = dbExistsRow.getBoolean("exists");
                  if (flag == Boolean.TRUE) {
                    LOGGER.debug("Info: ResourceServer URL validated");

                    JsonObject revokePayload = new JsonObject();
                    revokePayload.put("user-id", clientId).put("current-token-duration",
                        CLAIM_EXPIRY);
                    request.put("body", revokePayload);
                    httpWebClient.httpRevokeRequest(request, httpClient -> {
                      if (httpClient.succeeded()) {
                        System.out.println(httpClient.result());
                        handler.handle(
                            Future.succeededFuture(new JsonObject().put("status", "success")));
                      } else {
                        System.out.println(httpClient.cause());
                        handler.handle(Future.failedFuture(new JsonObject().put("status", "failed")
                            .put("desc", "revoke request failed").toString()));
                      }
                    });
                  } else {
                    LOGGER.error("Fail: Incorrect resourceServer; exists: " + flag);
                    handler.handle(Future.failedFuture(new JsonObject().put("status", "failed")
                        .put("desc", "Invalid resourceServer").toString()));
                  }
                } else {
                  LOGGER.error("Fail: Databse query; " + selectHandler.cause());
                  handler.handle(Future.failedFuture(new JsonObject().put("status", "failed")
                      .put("desc", "Internal server error").toString()));
                }
              });

            } else {
              LOGGER.error("Fail: Incorrect userId/clientId");
              handler.handle(Future.failedFuture(new JsonObject().put("status", "failed")
                  .put("desc", "Invalid userId/clientId").toString()));
            }
          } else {
            LOGGER.error("Fail: Incorrect resourceServer URL");
            handler.handle(Future.failedFuture(new JsonObject().put("status", "failed")
                .put("desc", "Incorrect resourceServer URL").toString()));

          }
        } else {
          LOGGER.error("Fail: Databse query; " + dbHandler.cause());
          handler.handle(Future.failedFuture(new JsonObject().put("status", "failed")
              .put("desc", "Internal server error").toString()));
        }
      });
    } else {
      LOGGER.error("Fail: Empty/null userId");
      handler.handle(Future.failedFuture(
          new JsonObject().put("status", "failed").put("desc", "Empty/null userId").toString()));
    }
    return this;
  }

  @Override
  public TokenService listToken(JsonObject request, Handler<AsyncResult<JsonObject>> handler) {
    // TODO Auto-generated method stub
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    JsonObject response = new JsonObject();
    response.put("status", "success");
    handler.handle(Future.succeededFuture(response));
    return this;
  }
    
  /**
   * Generates the JWT token using the request data.
   * 
   * @param request
   * @return jwtToken
   */
  private String getJwt(JsonObject request) {
    String uuid = UUID.randomUUID().toString();
    JWTOptions options = new JWTOptions().setAlgorithm(JWT_ALGORITHM);
    
    long timestamp = System.currentTimeMillis() / 1000;
    long expiry = timestamp + CLAIM_EXPIRY;
    
    /* Populate the token claims */
    JsonObject claims = new JsonObject();
    claims.put("sub", request.getString("clientId"))
          .put("iss", CLAIM_ISSUER)
          .put("aud", request.getString("audience"))
          .put("exp", expiry)
          .put("nbf", timestamp)
          .put("iat", timestamp)
          .put("jti", uuid)
          .put("item_id", request.getString("itemId"))
          .put("item_type", request.getString("itemType"))
          .put("role", request.getString("role"))
          .put("constraints", request.getJsonObject("constraints"));
    
    String token = provider.generateToken(claims, options);
    return token;
  }
}
