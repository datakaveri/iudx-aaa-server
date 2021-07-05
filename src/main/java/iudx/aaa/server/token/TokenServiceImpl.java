package iudx.aaa.server.token;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.crypto.generators.OpenBSDBCrypt;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.IntrospectToken;
import iudx.aaa.server.apiserver.RequestToken;
import iudx.aaa.server.apiserver.RevokeToken;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.policy.PolicyService;
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

  private PgPool pgPool;
  private JWTAuth provider;
  private PolicyService policyService;
  private TokenRevokeService revokeService;

  public TokenServiceImpl(PgPool pgPool, PolicyService policyService, JWTAuth provider,
      TokenRevokeService revokeService) {
    this.pgPool = pgPool;
    this.policyService = policyService;
    this.provider = provider;
    this.revokeService = revokeService;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public TokenService createToken(RequestToken requestToken, Handler<AsyncResult<JsonObject>> handler) {
    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    String clientId = requestToken.getClientId();
    String clientSecret = requestToken.getClientSecret();
    String role = StringUtils.upperCase(requestToken.getRole());
    
    JsonObject request=JsonObject.mapFrom(requestToken);
    Tuple tuple = Tuple.of(clientId);
    
    pgSelelctQuery(GET_USER,tuple).onComplete(dbHandler -> {
      if (dbHandler.failed()) {
        LOGGER.error("Fail: Databse query; " + dbHandler.cause());
        handler.handle(Future.failedFuture(
            new JsonObject().put(STATUS, FAILED).put(DESC, "Internal server error").toString()));

      } else if (dbHandler.succeeded()) {

        if (dbHandler.result().size() != 1) {
          LOGGER.error("Fail: Unauthorized access; Invalid clientId/clientSecret");
          handler.handle(Future.failedFuture(new JsonObject().put(STATUS, FAILED)
              .put(DESC, "Invalid clientId/clientSecret").toString()));
          return;
        }

        JsonObject result = dbHandler.result().getJsonObject(0);
        String dbClientSecret = result.getString("client_secret");

        boolean valid = false;
        try {
          valid = OpenBSDBCrypt.checkPassword(dbClientSecret,
              clientSecret.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
          LOGGER.error("Fail: Invalid clientSecret format; " + e.getLocalizedMessage());
          handler.handle(Future.failedFuture(
              new JsonObject().put(STATUS, FAILED).put(DESC, "Unauthorized user").toString()));
          return;
        }

        if (valid == Boolean.FALSE) {
          LOGGER.error("Fail: Unauthorized access; Invalid clientId/clientSecret");
          handler.handle(Future.failedFuture(new JsonObject().put(STATUS, FAILED)
              .put(DESC, "Invalid clientId/clientSecret").toString()));
          return;
        }

        if (!Roles.exists(role)) {
          LOGGER.error("Fail: Unauthorized access; Role not defined");
          handler.handle(Future.failedFuture(
              new JsonObject().put(STATUS, FAILED).put(DESC, "Unauthorized user").toString()));
          return;
        }

        request.put(USER_ID, result.getString("user_id"));
        policyService.verifyPolicy(request, policyHandler -> {
          if (policyHandler.succeeded()) {

            request.mergeIn(policyHandler.result(), true);
            String jwt = getJwt(request);

            LOGGER.info("Info: Policy evaluation succeeded; JWT generated & signed");
            handler.handle(Future
                .succeededFuture(new JsonObject().put(STATUS, SUCCESS).put(ACCESS_TOKEN, jwt)));

          } else if (policyHandler.failed()) {
            LOGGER.error("Fail: Unauthorized access; Policy evaluation failed");
            handler.handle(Future.failedFuture(
                new JsonObject().put(STATUS, FAILED).put(DESC, "Unauthorized user").toString()));
          }
        });
      }
    });
    
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public TokenService revokeToken(RevokeToken revokeToken, User user,
      Handler<AsyncResult<JsonObject>> handler) {

    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    String userId = user.getUserId();
    String clientId = revokeToken.getClientId();
    String rsUrl = revokeToken.getRsUrl();

    if (userId == null || userId.isBlank()) {
      LOGGER.error("Fail: Empty/null userId");
      handler.handle(Future.failedFuture(
          new JsonObject().put(STATUS, FAILED).put(DESC, "Empty/null userId").toString()));
      return this;
    }

    Tuple clientTuple = Tuple.of(userId);
    pgSelelctQuery(GET_CLIENT, clientTuple).onSuccess(mapper -> {
      if (mapper.size() != 1) {
        LOGGER.error("Fail: Incorrect resourceServer URL");
        handler.handle(Future.failedFuture(new JsonObject().put(STATUS, FAILED)
            .put(DESC, "Incorrect resourceServer URL").toString()));
        return;
      }

      JsonObject dbClientRow = mapper.getJsonObject(0);
      String dbClientId = dbClientRow.getString("client_id");

      if (!dbClientId.equals(clientId)) {
        LOGGER.error("Fail: Incorrect userId/clientId");
        handler.handle(Future.failedFuture(
            new JsonObject().put(STATUS, FAILED).put(DESC, "Invalid userId/clientId").toString()));
        return;
      }

      Tuple tuple = Tuple.of(rsUrl);
      pgSelelctQuery(GET_URL, tuple).onComplete(dbHandler -> {
        if (dbHandler.failed()) {
          LOGGER.error("Fail: Databse query; " + dbHandler.cause());
          handler.handle(Future.failedFuture(
              new JsonObject().put(STATUS, FAILED).put(DESC, "Internal server error").toString()));
          return;
        }

        if (dbHandler.succeeded()) {
          JsonObject dbExistsRow = dbHandler.result().getJsonObject(0);
          boolean flag = dbExistsRow.getBoolean(EXISTS);

          if (flag == Boolean.FALSE) {
            LOGGER.error("Fail: Incorrect resourceServer; exists: " + flag);
            handler.handle(Future.failedFuture(new JsonObject().put(STATUS, FAILED)
                .put(DESC, "Invalid resourceServer").toString()));
            return;
          }

          LOGGER.debug("Info: ResourceServer URL validated");
          JsonObject revokePayload = JsonObject.mapFrom(revokeToken);

          revokeService.httpRevokeRequest(revokePayload, httpClient -> {
            if (httpClient.succeeded()) {
              handler.handle(Future.succeededFuture(new JsonObject().put(STATUS, SUCCESS)));
              return;
            } else {
              System.out.println(httpClient.cause());
             handler.handle(Future.failedFuture(new JsonObject().put(STATUS, FAILED)
                  .put(DESC, "revoke request failed").toString()));
              return;
            }
          });
        }
      });
    }).onFailure(failureHandler -> {
      LOGGER.error("Fail: Databse query; " + failureHandler.getMessage());
      handler.handle(Future.failedFuture(
          new JsonObject().put(STATUS, FAILED).put(DESC, "Internal server error").toString()));
    });
    
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public TokenService validateToken(IntrospectToken introspectToken,
      Handler<AsyncResult<JsonObject>> handler) {

    LOGGER.debug("Info : " + LOGGER.getName() + " : Request received");

    String accessToken = introspectToken.getAccessToken();
    if (accessToken == null || accessToken.isBlank()) {
      LOGGER.error("Fail: Unable to parse accessToken");
      handler.handle(Future.failedFuture(
          new JsonObject().put(STATUS, FAILED).put(DESC, "missing accessToken").toString()));
      return this;
    }

    TokenCredentials authInfo = new TokenCredentials(accessToken);
    provider.authenticate(authInfo).onFailure(jwtError -> {
      LOGGER.error("Fail: Token authentication failed; " + jwtError.getLocalizedMessage());
      handler.handle(Future.failedFuture(new JsonObject().put(STATUS, DENY).toString()));
    }).onSuccess(jwtDetails -> {

      JsonObject accessTokenJwt = jwtDetails.attributes().getJsonObject(ACCESS_TOKEN);
      String clientId = accessTokenJwt.getString(SUB);
      String role = accessTokenJwt.getString(ROLE);
      
      String[] item = accessTokenJwt.getString(IID).split(":");
      String itemId = item[1];
      String itemType = ITEM_TYPE_MAP.get(item[0]);

      Tuple tuple = Tuple.of(clientId);
      pgSelelctQuery(GET_USER, tuple).onComplete(dbHandler -> {

        if (dbHandler.failed()) {
          LOGGER.error("Fail: Databse query; " + dbHandler.cause().getMessage());
          handler.handle(Future.failedFuture(new JsonObject().put(STATUS, FAILED)
              .put(DESC, dbHandler.cause().getLocalizedMessage()).toString()));
        } else if (dbHandler.succeeded()) {

          if (dbHandler.result().size() != 1) {
            LOGGER.error("Fail: Invalid accessToken- clientId");
            handler.handle(Future.failedFuture(new JsonObject().put(STATUS, DENY).toString()));
            return;
          }

          JsonObject result = dbHandler.result().getJsonObject(0);
          JsonObject request = new JsonObject();
          request.put(USER_ID, result.getString("user_id"))
                 .put(CLIENT_ID, clientId)
                 .put(ROLE, role)
                 .put(ITEM_ID, itemId)
                 .put(ITEM_TYPE, itemType);

          policyService.verifyPolicy(request, policyHandler -> {
            if (policyHandler.succeeded()) {
              request.clear();
              request.put(STATUS, ALLOW);
              request.mergeIn(accessTokenJwt);

              LOGGER.info("Info: Policy evaluation succeeded; Token authenticated");
              handler.handle(Future.succeededFuture(request));
            } else if (policyHandler.failed()) {
              LOGGER.error(
                  "Fail: Policy evaluation failed; " + policyHandler.cause().getLocalizedMessage());
              handler.handle(Future.failedFuture(new JsonObject().put(STATUS, DENY).toString()));
            }
          });
        }
      });
    });
    return this;
  }
    
  /**
   * Generates the JWT token using the request data.
   * 
   * @param request
   * @return jwtToken
   */
  private String getJwt(JsonObject request) {
    //String uuid = UUID.randomUUID().toString();
    JWTOptions options = new JWTOptions().setAlgorithm(JWT_ALGORITHM);
    
    long timestamp = System.currentTimeMillis() / 1000;
    long expiry = timestamp + CLAIM_EXPIRY;
    String itemType = request.getString(ITEM_TYPE);
    String iid = ITEM_TYPE_MAP.inverse().get(itemType)+":"+request.getString(ITEM_ID);
    
    /* Populate the token claims */
    JsonObject claims = new JsonObject();
    claims.put(SUB, request.getString(CLIENT_ID))
          .put(ISS, CLAIM_ISSUER)
          .put(AUD, request.getString(AUDIENCE))
          .put(EXP, expiry)
          .put(IAT, timestamp)
          .put(IID, iid)
          .put(ROLE, request.getString(ROLE))
          .put(CONS, request.getJsonObject(CONSTRAINTS));
    
    String token = provider.generateToken(claims, options);
    return token;
  }
  
  /**
   * Handles the PostgreSQL query.
   * 
   * @param query which is SQL
   * @param tuple which contains fields
   * @return future associated with Promise
   */
  Future<JsonArray> pgSelelctQuery(String query, Tuple tuple) {

    Promise<JsonArray> promise = Promise.promise();
    pgPool.withConnection(
        connection -> connection.preparedQuery(query).execute(tuple).onComplete(handler -> {
          if (handler.succeeded()) {
            JsonArray jsonResult = new JsonArray();
            for (Row each : handler.result()) {
              jsonResult.add(each.toJson());
            }
            promise.complete(jsonResult);
          } else if (handler.failed()) {
            promise.fail(handler.cause());
          }
        }));
    return promise.future();
  }
}
