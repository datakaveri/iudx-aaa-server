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
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.RevokeToken;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.Response.ResponseBuilder;
import iudx.aaa.server.policy.PolicyService;
import static iudx.aaa.server.token.Constants.*;
import java.nio.charset.StandardCharsets;

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
    LOGGER.debug(REQ_RECEIVED);

    String clientId = requestToken.getClientId();
    String clientSecret = requestToken.getClientSecret();
    String role = StringUtils.upperCase(requestToken.getRole());
    
    JsonObject request=JsonObject.mapFrom(requestToken);
    Tuple tuple = Tuple.of(clientId);
    
    /* Get and verify the clientId from DB*/
    pgSelelctQuery(GET_USER,tuple).onComplete(dbHandler -> {
      if (dbHandler.failed()) {
        LOGGER.error(LOG_DB_ERROR + dbHandler.cause());
        handler.handle(Future.failedFuture(INTERNAL_SVR_ERR));

      } else if (dbHandler.succeeded()) {
        if (dbHandler.result().size() != 1) {
          LOGGER.error(LOG_UNAUTHORIZED + INVALID_CLIENT_ID_SEC);
          Response resp = new ResponseBuilder().status(400).type(URN_INVALID_INPUT)
              .title(INVALID_CLIENT_ID_SEC).detail(INVALID_CLIENT_ID_SEC).build();
          handler.handle(Future.succeededFuture(resp.toJson()));
          return;
        }

        JsonObject result = dbHandler.result().getJsonObject(0);
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
          handler.handle(Future.succeededFuture(resp.toJson()));
          return;
        }

        if (valid == Boolean.FALSE) {
          LOGGER.error(LOG_UNAUTHORIZED + INVALID_CLIENT_ID_SEC);
          Response resp = new ResponseBuilder().status(401).type(URN_INVALID_INPUT)
              .title(INVALID_CLIENT_ID_SEC).detail(INVALID_CLIENT_ID_SEC).build();
          handler.handle(Future.succeededFuture(resp.toJson()));
          return;
        }

        /* Verify the user role */
        if (!Roles.exists(role)) {
          LOGGER.error(LOG_UNAUTHORIZED + INVALID_ROLE);
          Response resp = new ResponseBuilder().status(400).type(URN_INVALID_ROLE)
              .title(INVALID_ROLE).detail(INVALID_ROLE).build();
          handler.handle(Future.succeededFuture(resp.toJson()));
          return;
        }

        request.put(USER_ID, result.getString("user_id"));
        policyService.verifyPolicy(request, policyHandler -> {
          if (policyHandler.succeeded()) {

            request.mergeIn(policyHandler.result(), true);
            JsonObject jwt = getJwt(request);

            LOGGER.info(LOG_TOKEN_SUCC);
            Response resp = new ResponseBuilder().status(200).type(URN_SUCCESS).title(TOKEN_SUCCESS)
                .arrayResults(new JsonArray().add(jwt)).build();
            handler.handle(Future.succeededFuture(resp.toJson()));

          } else if (policyHandler.failed()) {
            LOGGER.error(LOG_UNAUTHORIZED + INVALID_POLICY);
            Response resp = new ResponseBuilder().status(400).type(URN_INVALID_INPUT)
                .title(INVALID_POLICY).detail(INVALID_POLICY).build();
            handler.handle(Future.succeededFuture(resp.toJson()));
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

    LOGGER.debug(REQ_RECEIVED);

    String userId = user.getUserId();
    String clientId = revokeToken.getClientId();
    String rsUrl = revokeToken.getRsUrl();

    if (userId == null || userId.isBlank()) {
      LOGGER.error("Fail: " + INVALID_USERID);
      Response resp = new ResponseBuilder().status(400).type(URN_MISSING_INFO).title(INVALID_USERID)
          .detail(INVALID_USERID).build();
      handler.handle(Future.succeededFuture(resp.toJson()));
      return this;
    }

    Tuple clientTuple = Tuple.of(userId);
    pgSelelctQuery(GET_CLIENT, clientTuple).onSuccess(mapper -> {
      if (mapper.size() != 1) {
        LOGGER.error("Fail: " + INVALID_USER_CLIENT);
        Response resp = new ResponseBuilder().status(400).type(URN_INVALID_INPUT)
            .title(INVALID_USER_CLIENT).detail(INVALID_USER_CLIENT).build();
        handler.handle(Future.succeededFuture(resp.toJson()));
        return;
      }

      JsonObject dbClientRow = mapper.getJsonObject(0);
      String dbClientId = dbClientRow.getString("client_id");

      if (!dbClientId.equals(clientId)) {
        LOGGER.error("Fail: " + INVALID_CLIENT);
        Response resp = new ResponseBuilder().status(400).type(URN_INVALID_INPUT)
            .title(INVALID_CLIENT).detail(INVALID_CLIENT).build();
        handler.handle(Future.succeededFuture(resp.toJson()));
        return;
      }

      Tuple tuple = Tuple.of(rsUrl);
      pgSelelctQuery(GET_URL, tuple).onComplete(dbHandler -> {
        if (dbHandler.failed()) {
          LOGGER.error(LOG_DB_ERROR + dbHandler.cause());
          handler.handle(Future.failedFuture(INTERNAL_SVR_ERR));
          return;
        }

        if (dbHandler.succeeded()) {
          JsonObject dbExistsRow = dbHandler.result().getJsonObject(0);
          boolean flag = dbExistsRow.getBoolean(EXISTS);

          if (flag == Boolean.FALSE) {
            LOGGER.error("Fail: " + INVALID_RS_URL);
            Response resp = new ResponseBuilder().status(400).type(URN_INVALID_INPUT)
                .title(INVALID_RS_URL).detail(INVALID_RS_URL).build();
            handler.handle(Future.succeededFuture(resp.toJson()));
            return;
          }

          LOGGER.debug("Info: ResourceServer URL validated");
          JsonObject revokePayload = JsonObject.mapFrom(revokeToken);

          revokeService.httpRevokeRequest(revokePayload, httpClient -> {
            if (httpClient.succeeded()) {
              LOGGER.info(LOG_REVOKE_REQ);
              Response resp = new ResponseBuilder().status(200).type(URN_SUCCESS)
                  .title(TOKEN_REVOKED).arrayResults(new JsonArray()).build();
              handler.handle(Future.succeededFuture(resp.toJson()));
              return;
            } else {
              LOGGER.error("Fail: {}; {}", FAILED_REVOKE, httpClient.cause());
              Response resp = new ResponseBuilder().status(400).type(URN_INVALID_INPUT)
                  .title(FAILED_REVOKE).detail(FAILED_REVOKE).build();
              handler.handle(Future.succeededFuture(resp.toJson()));
              return;
            }
          });
        }
      });
    }).onFailure(failureHandler -> {
      LOGGER.error(LOG_DB_ERROR + failureHandler.getMessage());
      handler.handle(Future.failedFuture(INTERNAL_SVR_ERR));
    });

    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public TokenService validateToken(IntrospectToken introspectToken,
      Handler<AsyncResult<JsonObject>> handler) {

    LOGGER.debug(REQ_RECEIVED);

    String accessToken = introspectToken.getAccessToken();
    if (accessToken == null || accessToken.isBlank()) {
      LOGGER.error(LOG_PARSE_TOKEN);
      Response resp = new ResponseBuilder().status(400).type(URN_MISSING_INFO)
          .title(MISSING_TOKEN).detail(MISSING_TOKEN).build();
      handler.handle(Future.succeededFuture(resp.toJson()));
      return this;
    }

    TokenCredentials authInfo = new TokenCredentials(accessToken);
    provider.authenticate(authInfo).onFailure(jwtError -> {
      LOGGER.error("Fail: {}; {}", TOKEN_FAILED, jwtError.getLocalizedMessage());
      Response resp =
          new ResponseBuilder().status(401).type(URN_INVALID_AUTH_TOKEN).title(TOKEN_FAILED)
              .arrayResults(new JsonArray().add(new JsonObject().put(STATUS, DENY))).build();
      handler.handle(Future.succeededFuture(resp.toJson()));
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
          LOGGER.error(LOG_DB_ERROR + dbHandler.cause().getMessage());
          handler.handle(Future.failedFuture(INTERNAL_SVR_ERR));
        } else if (dbHandler.succeeded()) {

          if (dbHandler.result().size() != 1) {
            LOGGER.error(LOG_TOKEN_AUTH + INVALID_CLIENT);
            Response resp = new ResponseBuilder().status(400).type(URN_INVALID_AUTH_TOKEN)
                .title(TOKEN_FAILED).detail(INVALID_CLIENT).build();
            handler.handle(Future.succeededFuture(resp.toJson()));
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
              request.mergeIn(accessTokenJwt);

              Response resp = new ResponseBuilder().status(200).type(URN_SUCCESS)
                  .title(TOKEN_AUTHENTICATED).arrayResults(new JsonArray().add(request)).build();
              LOGGER.info("Info: {}; {}", POLICY_SUCCESS, TOKEN_AUTHENTICATED);
              handler.handle(Future.succeededFuture(resp.toJson()));
              
            } else if (policyHandler.failed()) {
              LOGGER.error("Fail: {}; {}", INVALID_POLICY, policyHandler.cause().getMessage());
              Response resp = new ResponseBuilder().status(400).type(URN_INVALID_INPUT)
                  .title(INVALID_POLICY).detail(INVALID_POLICY).build();
              handler.handle(Future.succeededFuture(resp.toJson()));
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
  private JsonObject getJwt(JsonObject request) {
    
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

    JsonObject tokenResp = new JsonObject();
    tokenResp.put(ACCESS_TOKEN, token).put("expiry", expiry).put("server",
        request.getString(AUDIENCE));
    return tokenResp;
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
