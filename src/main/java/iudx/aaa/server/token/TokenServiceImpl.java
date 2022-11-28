package iudx.aaa.server.token;

import io.vertx.core.CompositeFuture;
import io.vertx.core.json.Json;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.util.ComposeException;
import iudx.aaa.server.apiserver.Response.ResponseBuilder;
import iudx.aaa.server.policy.PolicyService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static iudx.aaa.server.apd.Constants.APD_CONSTRAINTS;
import static iudx.aaa.server.apiserver.util.Urn.*;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_NO_USER_PROFILE;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_NO_USER_PROFILE;
import static iudx.aaa.server.registration.Constants.NIL_UUID;
import static iudx.aaa.server.token.Constants.*;

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
  private IudxJwtTokenGenerator jwtTokenProvider;

  public TokenServiceImpl(PgPool pgPool, PolicyService policyService, JWTAuth provider,
      TokenRevokeService revokeService) {
    this.pgPool = pgPool;
    this.policyService = policyService;
    this.provider = provider;
    this.revokeService = revokeService;
    this.jwtTokenProvider=new IudxJwtTokenGenerator(this.provider);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public TokenService createToken(RequestToken requestToken, User user, Handler<AsyncResult<JsonObject>> handler) {
    LOGGER.debug(REQ_RECEIVED);

    String role = StringUtils.upperCase(requestToken.getRole());
    List<String> roles = user.getRoles().stream().map(r -> r.name()).collect(Collectors.toList());
    
    String itemType = requestToken.getItemType();
    JsonObject request = JsonObject.mapFrom(requestToken);
    request.put(USER_ID, user.getUserId());

    isValidUserId(user, handler);
    
    verifyUserRole(role, roles, handler);

    //update check to not include role check
    if (RESOURCE_SVR.equals(itemType)) {
      issueResourceServerToken(requestToken, user, role, request, handler);
    } else {
      issueTokenForServer(request, handler);
    }
    
    return this;
  }

  private void issueTokenForServer(JsonObject request, Handler<AsyncResult<JsonObject>> handler) {
    Promise<JsonObject> policyHandler = Promise.promise();
    policyService.verifyPolicy(request, policyHandler);
    policyHandler.future().onSuccess(result -> {

      request.mergeIn(result, true);

      if (request.getString(STATUS).equals(SUCCESS)) {
       
        JsonObject jwt = jwtTokenProvider.getJwt(request);
      Response resp = new ResponseBuilder().status(200).type(URN_SUCCESS).title(TOKEN_SUCCESS)
          .objectResults(jwt).build();
      
      handler.handle(Future.succeededFuture(resp.toJson()));
      } else if (request.getString(STATUS).equals(APD_INTERACTION)) {
        
        JsonObject apdJwt = jwtTokenProvider.getApdJwt(request);
        /* Add context to the error response containing the APD token */
        Response resp = new ResponseBuilder().status(403).type(URN_MISSING_INFO)
            .title(ERR_TITLE_APD_INTERACT_REQUIRED).detail(ERR_DETAIL_APD_INTERACT_REQUIRED)
            .errorContext(apdJwt).build();
        
      handler.handle(Future.succeededFuture(resp.toJson()));
      }

      LOGGER.info(LOG_TOKEN_SUCC);

    }).onFailure(fail -> {
      if (fail instanceof ComposeException) {
        ComposeException exp = (ComposeException) fail;
        handler.handle(Future.succeededFuture(exp.getResponse().toJson()));
        return;
      }
      LOGGER.error(fail.getMessage());
      handler.handle(Future.failedFuture("Internal error"));
    });
  }

  private void issueResourceServerToken(RequestToken requestToken, User user, String role,
      JsonObject request, Handler<AsyncResult<JsonObject>> handler) {
    Future<Void> checkIdenToken = validateForIdentityToken(requestToken.getItemId(), role, user);

    checkIdenToken.onSuccess(owner -> {
      request.put(URL, requestToken.getItemId());
      JsonObject jwt = jwtTokenProvider.getJwt(request);
      
      LOGGER.info(LOG_TOKEN_SUCC);
      
      Response resp = new ResponseBuilder().status(200).type(URN_SUCCESS).title(TOKEN_SUCCESS)
          .objectResults(jwt).build();
      handler.handle(Future.succeededFuture(resp.toJson()));
      return;
    }).onFailure(fail -> {
      if (fail instanceof ComposeException) {
        ComposeException exp = (ComposeException) fail;
        handler.handle(Future.succeededFuture(exp.getResponse().toJson()));
        return;
      }
      LOGGER.error(fail.getMessage());
      handler.handle(Future.failedFuture("Internal error"));
    });
  }

  private void verifyUserRole(String role, List<String> roles,
      Handler<AsyncResult<JsonObject>> handler) {
    /* Verify the user role */
    if (!roles.contains(role)) {
      LOGGER.error(LOG_UNAUTHORIZED + INVALID_ROLE);
      Response resp = new ResponseBuilder().status(400).type(URN_INVALID_ROLE).title(INVALID_ROLE)
          .detail(INVALID_ROLE).build();
      handler.handle(Future.succeededFuture(resp.toJson()));
    }
    return;
  }

  private void isValidUserId(User user, Handler<AsyncResult<JsonObject>> handler) {
    /* Checking if the userId is valid */
    if (user.getUserId().equals(NIL_UUID)) {
      Response r = new ResponseBuilder().status(404).type(URN_MISSING_INFO)
          .title(ERR_TITLE_NO_USER_PROFILE).detail(ERR_DETAIL_NO_USER_PROFILE).build();
      handler.handle(Future.succeededFuture(r.toJson()));
      
    }
    return;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public TokenService revokeToken(RevokeToken revokeToken, User user,
      Handler<AsyncResult<JsonObject>> handler) {

    LOGGER.debug(REQ_RECEIVED);
    
    isValidUserId(user, handler);

    checkForRole(user, handler);

    String rsUrl = revokeToken.getRsUrl().toLowerCase();

    /* Check if the user is trying to revoke tokens on auth */
    if (rsUrl.equals(CLAIM_ISSUER)) {
      Response resp = new ResponseBuilder().status(400).type(URN_INVALID_INPUT)
          .title(CANNOT_REVOKE_ON_AUTH).detail(CANNOT_REVOKE_ON_AUTH).build();
      handler.handle(Future.succeededFuture(resp.toJson()));
      return this;
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
        
        JsonObject revokePayload =
            new JsonObject().put(USER_ID, user.getUserId()).put(RS_URL, revokeToken.getRsUrl());

        /* Here, we get the special admin token that is presented to other servers for token
         * revocation. The 'sub' field is the auth server domain instead of a UUID user ID.
         * The 'iss' field is the auth server domain as usual and 'aud' is the requested 
         * resource server domain. The rest of the field are not important, so they are
         * either null or blank. 
         */ 
        
        JsonObject adminTokenReq = new JsonObject().put(USER_ID, CLAIM_ISSUER).put(URL, rsUrl)
            .put(ROLE, "").put(ITEM_TYPE, "").put(ITEM_ID, "");
        String adminToken = jwtTokenProvider.getJwt(adminTokenReq).getString(ACCESS_TOKEN);
        
        revokeService.httpRevokeRequest(revokePayload, adminToken, result -> {
          if (result.succeeded()) {
            LOGGER.info(LOG_REVOKE_REQ);
            Response resp = new ResponseBuilder().status(200).type(URN_SUCCESS).title(TOKEN_REVOKED)
                .arrayResults(new JsonArray()).build();
            handler.handle(Future.succeededFuture(resp.toJson()));
            return;
          } else {
            LOGGER.error("Fail: {}; {}", FAILED_REVOKE, result.cause());
            Response resp = new ResponseBuilder().status(400).type(URN_INVALID_INPUT)
                .title(FAILED_REVOKE).detail(FAILED_REVOKE).build();
            handler.handle(Future.succeededFuture(resp.toJson()));
            return;
          }
        });
      }
    });

    return this;
  }

  private void checkForRole(User user, Handler<AsyncResult<JsonObject>> handler) {
    /* Verify the user has some roles */
    if (user.getRoles().isEmpty()) {
      Response resp = new ResponseBuilder().status(400).type(URN_INVALID_ROLE).title(INVALID_ROLE)
          .detail(INVALID_ROLE).build();
      handler.handle(Future.succeededFuture(resp.toJson()));
    }
    return;
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
      Response resp = new ResponseBuilder().status(400).type(URN_MISSING_INFO).title(MISSING_TOKEN)
          .detail(MISSING_TOKEN).build();
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

      Response resp = new ResponseBuilder().status(200).type(URN_SUCCESS).title(TOKEN_AUTHENTICATED)
          .objectResults(accessTokenJwt).build();
      handler.handle(Future.succeededFuture(resp.toJson()));
      return;

    });
    return this;
  }
    
  
  /**
   * {@inheritDoc}
   */
  @Override
  public TokenService getAuthServerToken(String audienceUrl,
      Handler<AsyncResult<JsonObject>> handler) {
    JsonObject adminTokenReq = new JsonObject().put(USER_ID, CLAIM_ISSUER).put(URL, audienceUrl)
        .put(ROLE, "").put(ITEM_TYPE, "").put(ITEM_ID, "");
    handler.handle(Future.succeededFuture(jwtTokenProvider.getJwt(adminTokenReq)));
    return this;
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
        connection -> connection.preparedQuery(query).execute(tuple)).onComplete(handler -> {
          if (handler.succeeded()) {
            JsonArray jsonResult = new JsonArray();
            for (Row each : handler.result()) {
              jsonResult.add(each.toJson());
            }
            promise.complete(jsonResult);
          } else if (handler.failed()) {
            promise.fail(handler.cause());
          }
        });
    return promise.future();
  }

  /**
   * Perform checks for identity token-based flow. 
   * 
   * @param url the server URL passed as the itemId in the request
   * @param role the role requested by the user
   * @param user the User object
   * @return void Future, succeeds if checks pass, fails with a ComposeException if they do not
   */
  private Future<Void> validateForIdentityToken(String url, String role, User user) {

    Promise<Void> promise = Promise.promise();

    Future<JsonArray> resServer = pgSelelctQuery(CHECK_RS_EXISTS_BY_URL, Tuple.of(url));
    Future<JsonArray> apd = pgSelelctQuery(CHECK_APD_EXISTS_BY_URL, Tuple.of(url));

    Future<Void> checkUrlExists = CompositeFuture.all(resServer, apd).compose(res -> {
      if (resServer.result().isEmpty() && apd.result().isEmpty()) {
        return Future.failedFuture(
            new ComposeException(400, URN_INVALID_INPUT, INVALID_RS_URL, INVALID_RS_URL));
      }
      return Future.succeededFuture();
    });

    Future<JsonArray> checkIfAdminOrTrustee = checkUrlExists.compose(res -> {
      UUID userId = UUID.fromString(user.getUserId());

      if (role.equalsIgnoreCase(ADMIN)) {
        return pgSelelctQuery(GET_RS, Tuple.of(url, userId));
      } else if (role.equalsIgnoreCase(TRUSTEE)) {
        return pgSelelctQuery(CHECK_APD_OWNER, Tuple.of(url, userId));
      } else {
        // skip ownership query if requested role not admin or trustee
        return Future.succeededFuture(new JsonArray());
      }
    });

    checkIfAdminOrTrustee.compose(result -> {
      // skip ownership query if requested role not admin or trustee
      if (!(role.equalsIgnoreCase(ADMIN) || role.equalsIgnoreCase(TRUSTEE))) {
        return Future.succeededFuture();
      }

      if (result.isEmpty()) {
        return Future
            .failedFuture(new ComposeException(403, URN_INVALID_INPUT, ERR_ADMIN, ERR_ADMIN));
      }
      return Future.succeededFuture();
    }).onSuccess(succ -> {
      promise.complete();
    }).onFailure(err -> {
      promise.fail(err);
    });

    return promise.future();
  }
}
