package iudx.aaa.server.token;

import static iudx.aaa.server.apiserver.util.Urn.*;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_NO_APPROVED_ROLES;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_NO_APPROVED_ROLES;
import static iudx.aaa.server.token.Constants.*;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.DelegationInformation;
import iudx.aaa.server.apiserver.IntrospectToken;
import iudx.aaa.server.apiserver.ItemType;
import iudx.aaa.server.apiserver.RequestToken;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.Response.ResponseBuilder;
import iudx.aaa.server.apiserver.RevokeToken;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.util.ComposeException;
import iudx.aaa.server.policy.PolicyService;
import iudx.aaa.server.registration.RegistrationService;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The Token Service Implementation.
 *
 * <h1>Token Service Implementation</h1>
 *
 * <p>The Token Service implementation in the IUDX AAA Server implements the definitions of the
 * {@link iudx.aaa.server.token.TokenService}.
 *
 * @version 1.0
 * @since 2020-12-15
 */
public class TokenServiceImpl implements TokenService {

  private static final Logger LOGGER = LogManager.getLogger(TokenServiceImpl.class);

  private PgPool pgPool;
  private JWTAuth provider;
  private PolicyService policyService;
  private RegistrationService registrationService;
  private TokenRevokeService revokeService;

  public TokenServiceImpl(
      PgPool pgPool,
      PolicyService policyService,
      RegistrationService registrationService,
      JWTAuth provider,
      TokenRevokeService revokeService) {
    this.pgPool = pgPool;
    this.policyService = policyService;
    this.registrationService = registrationService;
    this.provider = provider;
    this.revokeService = revokeService;
  }

  /** {@inheritDoc} */
  @Override
  public Future<JsonObject> createToken(
      RequestToken request, DelegationInformation delegationInfo, User user) {
    LOGGER.debug(REQ_RECEIVED);

    Promise<JsonObject> promiseHandler = Promise.promise();

    Roles role = request.getRole();
    ItemType itemType = request.getItemType();

    JsonObject jsonRequest = request.toJson();

    /* manually putting user_id as it's required by getJwt */
    jsonRequest.put(USER_ID, user.getUserId());

    /* Checking if the user has any roles */
    if (user.getRoles().isEmpty()) {
      Response r =
          new ResponseBuilder()
              .status(404)
              .type(URN_MISSING_INFO)
              .title(ERR_TITLE_NO_APPROVED_ROLES)
              .detail(ERR_DETAIL_NO_APPROVED_ROLES)
              .build();
      promiseHandler.complete(r.toJson());
      return promiseHandler.future();
    }

    /* Verify that the user has the requested role - the resource server check is later */
    if (!user.getRoles().contains(role)) {
      Response resp =
          new ResponseBuilder()
              .status(403)
              .type(URN_INVALID_ROLE)
              .title(ERR_TITLE_ROLE_NOT_OWNED)
              .detail(ERR_DETAIL_ROLE_NOT_OWNED)
              .build();
      promiseHandler.complete(resp.toJson());
      return promiseHandler.future();
    }

    if (role.equals(Roles.DELEGATE) && delegationInfo == null) {
      Response resp =
          new ResponseBuilder()
              .status(400)
              .type(URN_MISSING_INFO)
              .title(ERR_TITLE_DELEGATION_INFO_MISSING)
              .detail(ERR_DETAIL_DELEGATION_INFO_MISSING)
              .build();
      promiseHandler.complete(resp.toJson());
      return promiseHandler.future();
    }

    if (itemType.equals(ItemType.RESOURCE_GROUP)) {
      Response r =
          new ResponseBuilder()
              .status(403)
              .type(URN_INVALID_INPUT)
              .title(ERR_TITLE_NO_RES_GRP_TOKEN)
              .detail(ERR_DETAIL_NO_RES_GRP_TOKEN)
              .build();
      promiseHandler.complete(r.toJson());
      return promiseHandler.future();
    } else if (itemType.equals(ItemType.COS)) {
      if (!request.getRole().equals(Roles.COS_ADMIN)) {
        Response r =
            new ResponseBuilder()
                .status(403)
                .type(URN_INVALID_ROLE)
                .title(ERR_TITLE_INVALID_ROLE_FOR_COS)
                .detail(ERR_DETAIL_INVALID_ROLE_FOR_COS)
                .build();
        promiseHandler.complete(r.toJson());
        return promiseHandler.future();
      }

      if (!request.getItemId().equals(CLAIM_ISSUER)) {
        Response r =
            new ResponseBuilder()
                .status(403)
                .type(URN_INVALID_INPUT)
                .title(ERR_TITLE_INVALID_COS_URL)
                .detail(ERR_DETAIL_INVALID_COS_URL)
                .build();
        promiseHandler.complete(r.toJson());
        return promiseHandler.future();
      }

      jsonRequest.put(URL, request.getItemId());
      JsonObject jwt = getJwt(jsonRequest);

      LOGGER.info(LOG_TOKEN_SUCC);

      Response resp =
          new ResponseBuilder()
              .status(200)
              .type(URN_SUCCESS)
              .title(TOKEN_SUCCESS)
              .objectResults(jwt)
              .build();
      promiseHandler.complete(resp.toJson());
    } else if (itemType.equals(ItemType.RESOURCE_SERVER)) {
      Future<JsonObject> checkIdenToken =
          validateForIdentityToken(request.getItemId(), role, delegationInfo, user);

      checkIdenToken
          .onSuccess(
              result -> {
                jsonRequest.mergeIn(result, true);
                JsonObject jwt = getJwt(jsonRequest);

                LOGGER.info(LOG_TOKEN_SUCC);

                Response resp =
                    new ResponseBuilder()
                        .status(200)
                        .type(URN_SUCCESS)
                        .title(TOKEN_SUCCESS)
                        .objectResults(jwt)
                        .build();
                promiseHandler.complete(resp.toJson());
                return;
              })
          .onFailure(
              fail -> {
                if (fail instanceof ComposeException) {
                  ComposeException exp = (ComposeException) fail;
                  promiseHandler.complete(exp.getResponse().toJson());
                  return;
                }
                LOGGER.error(fail.getMessage());
                promiseHandler.fail("Internal error");
              });
    } else if (itemType.equals(ItemType.RESOURCE)) {

      policyService
          .verifyResourceAccess(request, delegationInfo, user)
          .onSuccess(
              result -> {
                jsonRequest.mergeIn(result, true);

                if (jsonRequest.getString(STATUS).equals(SUCCESS)) {

                  JsonObject jwt = getJwt(jsonRequest);
                  Response resp =
                      new ResponseBuilder()
                          .status(200)
                          .type(URN_SUCCESS)
                          .title(TOKEN_SUCCESS)
                          .objectResults(jwt)
                          .build();

                  promiseHandler.complete(resp.toJson());
                } else if (jsonRequest.getString(STATUS).equals(APD_INTERACTION)) {

                  JsonObject apdJwt = getApdJwt(jsonRequest);
                  /* Add context to the error response containing the APD token */
                  Response resp =
                      new ResponseBuilder()
                          .status(403)
                          .type(URN_MISSING_INFO)
                          .title(ERR_TITLE_APD_INTERACT_REQUIRED)
                          .detail(ERR_DETAIL_APD_INTERACT_REQUIRED)
                          .errorContext(apdJwt)
                          .build();

                  promiseHandler.complete(resp.toJson());
                }

                LOGGER.info(LOG_TOKEN_SUCC);
              })
          .onFailure(
              fail -> {
                if (fail instanceof ComposeException) {
                  ComposeException exp = (ComposeException) fail;
                  promiseHandler.complete(exp.getResponse().toJson());
                  return;
                }
                LOGGER.error(fail.getMessage());
                promiseHandler.fail("Internal error");
              });
    }

    return promiseHandler.future();
  }

  /** {@inheritDoc} */
  @Override
  public Future<JsonObject> revokeToken(RevokeToken revokeToken, User user) {

    LOGGER.debug(REQ_RECEIVED);
    Promise<JsonObject> promiseHandler = Promise.promise();

    /* Verify the user has some roles */
    if (user.getRoles().isEmpty()) {
      Response r =
          new ResponseBuilder()
              .status(404)
              .type(URN_MISSING_INFO)
              .title(ERR_TITLE_NO_APPROVED_ROLES)
              .detail(ERR_DETAIL_NO_APPROVED_ROLES)
              .build();
      promiseHandler.complete(r.toJson());
      return promiseHandler.future();
    }

    String rsUrl = revokeToken.getRsUrl();

    /* Check if the user is trying to revoke tokens on auth */
    if (rsUrl.equals(CLAIM_ISSUER)) {
      Response resp =
          new ResponseBuilder()
              .status(400)
              .type(URN_INVALID_INPUT)
              .title(CANNOT_REVOKE_ON_AUTH)
              .detail(CANNOT_REVOKE_ON_AUTH)
              .build();
      promiseHandler.complete(resp.toJson());
      return promiseHandler.future();
    }

    Tuple tuple = Tuple.of(rsUrl);

    pgSelelctQuery(GET_URL, tuple)
        .onComplete(
            dbHandler -> {
              if (dbHandler.failed()) {
                LOGGER.error(LOG_DB_ERROR, dbHandler.cause());
                promiseHandler.fail(INTERNAL_SVR_ERR);
                return;
              }

              if (dbHandler.succeeded()) {
                JsonObject dbExistsRow = dbHandler.result().getJsonObject(0);
                boolean flag = dbExistsRow.getBoolean(EXISTS);

                if (flag == Boolean.FALSE) {
                  LOGGER.error("Fail: {}", ERR_TITLE_INVALID_RS);
                  Response resp =
                      new ResponseBuilder()
                          .status(400)
                          .type(URN_INVALID_INPUT)
                          .title(ERR_TITLE_INVALID_RS_APD_REVOKE)
                          .detail(ERR_DETAIL_INVALID_RS_APD_REVOKE)
                          .build();
                  promiseHandler.complete(resp.toJson());
                  return;
                }
                LOGGER.debug("Info: ResourceServer URL validated");

                JsonObject revokePayload =
                    new JsonObject()
                        .put(USER_ID, user.getUserId())
                        .put(RS_URL, revokeToken.getRsUrl());

                /* Here, we get the special admin token that is presented to other servers for token
                 * revocation. The 'sub' field is the auth server domain instead of a UUID user ID.
                 * The 'iss' field is the auth server domain as usual and 'aud' is the requested
                 * resource server domain. The rest of the field are not important, so they are
                 * either null or blank.
                 */

                JsonObject adminTokenReq =
                    new JsonObject()
                        .put(USER_ID, CLAIM_ISSUER)
                        .put(URL, rsUrl)
                        .put(ROLE, "")
                        .put(ITEM_TYPE, "")
                        .put(ITEM_ID, "");
                String adminToken = getJwt(adminTokenReq).getString(ACCESS_TOKEN);

                revokeService
                    .httpRevokeRequest(revokePayload, adminToken)
                    .onComplete(
                        result -> {
                          if (result.succeeded()) {
                            LOGGER.info(LOG_REVOKE_REQ);
                            Response resp =
                                new ResponseBuilder()
                                    .status(200)
                                    .type(URN_SUCCESS)
                                    .title(TOKEN_REVOKED)
                                    .arrayResults(new JsonArray())
                                    .build();
                            promiseHandler.complete(resp.toJson());
                            return;
                          } else {
                            LOGGER.error("Fail: {}; {}", FAILED_REVOKE, result.cause());
                            Response resp =
                                new ResponseBuilder()
                                    .status(400)
                                    .type(URN_INVALID_INPUT)
                                    .title(FAILED_REVOKE)
                                    .detail(FAILED_REVOKE)
                                    .build();
                            promiseHandler.complete(resp.toJson());
                            return;
                          }
                        });
              }
            });

    return promiseHandler.future();
  }

  /** {@inheritDoc} */
  @Override
  public Future<JsonObject> validateToken(IntrospectToken introspectToken) {

    LOGGER.debug(REQ_RECEIVED);
    Promise<JsonObject> promiseHandler = Promise.promise();

    String accessToken = introspectToken.getAccessToken();
    if (accessToken == null || accessToken.isBlank()) {
      LOGGER.error(LOG_PARSE_TOKEN);
      Response resp =
          new ResponseBuilder()
              .status(400)
              .type(URN_MISSING_INFO)
              .title(MISSING_TOKEN)
              .detail(MISSING_TOKEN)
              .build();
      promiseHandler.complete(resp.toJson());
      return promiseHandler.future();
    }

    TokenCredentials authInfo = new TokenCredentials(accessToken);

    /**
     * The `.authenticate` method returns a succeeded future if the JWT is valid and a failed future
     * if not. Here, if it returns a failed future, we catch it using `recover` and create a
     * ComposeException so that the normal compose chain can work.
     */
    Future<JsonObject> decodedToken =
        provider
            .authenticate(authInfo)
            .recover(
                jwtError -> {
                  LOGGER.error("Fail: {}; {}", TOKEN_FAILED, jwtError.getLocalizedMessage());
                  Response resp =
                      new ResponseBuilder()
                          .status(401)
                          .type(URN_INVALID_AUTH_TOKEN)
                          .title(TOKEN_FAILED)
                          .arrayResults(new JsonArray().add(new JsonObject().put(STATUS, DENY)))
                          .build();
                  return Future.failedFuture(new ComposeException(resp));
                })
            .compose(
                jwtDetails -> {
                  JsonObject accessTokenJwt = jwtDetails.attributes().getJsonObject(ACCESS_TOKEN);
                  return Future.succeededFuture(accessTokenJwt);
                });

    decodedToken
        .compose(tokenJson -> addUserInfoToIntrospect(tokenJson))
        .onSuccess(
            res -> {
              Response resp =
                  new ResponseBuilder()
                      .status(200)
                      .type(URN_SUCCESS)
                      .title(TOKEN_AUTHENTICATED)
                      .objectResults(res)
                      .build();
              promiseHandler.complete(resp.toJson());
            })
        .onFailure(
            fail -> {
              if (fail instanceof ComposeException) {
                ComposeException exp = (ComposeException) fail;
                promiseHandler.complete(exp.getResponse().toJson());
                return;
              }
              LOGGER.error(fail.getMessage());
              promiseHandler.fail("Internal error");
            });

    return promiseHandler.future();
  }

  /**
   * <b>Optionally</b> adds user details of the user who issued a token (<tt>sub</tt> field in the
   * JWT) to the introspection result. The details are added as a JSON object with the
   * <tt>userInfo</tt> key. If user details is not applicable for the token, then the same decoded
   * token is returned as is.
   *
   * @param decodedToken the decoded JWT token
   * @return a Future of JsonObject. The response is the same decoded JWT token with or without the
   *     <tt>userInfo</tt> key
   */
  private Future<JsonObject> addUserInfoToIntrospect(JsonObject decodedToken) {
    Promise<JsonObject> promise = Promise.promise();
    String tokenIid = decodedToken.getString(IID, "");
    String[] iidParts = tokenIid.split(":");

    /* We only send userinfo for identity tokens now. Checking if `iid` is like 'rs:server.url' */
    if (iidParts.length != 2 || !iidParts[0].equals(ITEM_TYPE_MAP.inverse().get(RESOURCE_SVR))) {
      promise.complete(decodedToken);
      return promise.future();
    }

    String userId = decodedToken.getString(SUB);

    registrationService
        .getUserDetails(List.of(userId))
        .onSuccess(
            userInfo -> {
              decodedToken.put(INTROSPECT_USERINFO, userInfo.getJsonObject(userId));
              promise.complete(decodedToken);
            })
        .onFailure(fail -> promise.fail(fail));

    return promise.future();
  }

  /**
   * Generates the JWT token using the request data.
   *
   * @param request
   * @return jwtToken
   */
  public JsonObject getJwt(JsonObject request) {

    JWTOptions options =
        new JWTOptions()
            .setAlgorithm(JWT_ALGORITHM)
            .setHeader(new JsonObject().put(ISS, CLAIM_ISSUER));
    long timestamp = System.currentTimeMillis() / 1000;
    long expiry = timestamp + CLAIM_EXPIRY;
    String itemType = request.getString(ITEM_TYPE).toLowerCase();
    String iid = ITEM_TYPE_MAP.inverse().get(itemType) + ":" + request.getString(ITEM_ID);
    String audience = request.getString(URL);

    /* Populate the token claims */
    JsonObject claims = new JsonObject();

    claims
        .put(SUB, request.getString(USER_ID))
        .put(ISS, CLAIM_ISSUER)
        .put(AUD, audience)
        .put(EXP, expiry)
        .put(IAT, timestamp)
        .put(IID, iid)
        .put(ROLE, request.getString(ROLE).toLowerCase())
        .put(CONS, request.getJsonObject(CONSTRAINTS, new JsonObject()));

    if (request.containsKey(CREATE_TOKEN_RG)) {
      claims.put(RG, request.getString(CREATE_TOKEN_RG));
    }
    if (request.containsKey(CREATE_TOKEN_DID)) {
      claims.put(DID, request.getString(CREATE_TOKEN_DID));
    }
    if (request.containsKey(CREATE_TOKEN_DRL)) {
      claims.put(DRL, request.getString(CREATE_TOKEN_DRL).toLowerCase());
    }

    String token = provider.generateToken(claims, options);

    JsonObject tokenResp = new JsonObject();
    tokenResp.put(ACCESS_TOKEN, token).put("expiry", expiry).put("server", audience);
    return tokenResp;
  }

  /**
   * Generates the JWT token used for APD interaction using the request data.
   *
   * @param request a JSON object containing
   *     <ul>
   *       <li><em>url</em> : The URL of the APD to be called. This is placed in the <em>aud</em>
   *           field
   *       <li><em>userId</em> : The user ID of the user requesting access
   *       <li><em>sessionId</em> : The sessionId sent by the APD
   *       <li><em>link</em> : The link to visit sent by the APD
   *     </ul>
   *
   * @return jwtToken a JSON object containing the <i>accessToken</i>, expiry and server (audience)
   */
  public JsonObject getApdJwt(JsonObject request) {

    JWTOptions options =
        new JWTOptions()
            .setAlgorithm(JWT_ALGORITHM)
            .setHeader(new JsonObject().put(ISS, CLAIM_ISSUER));
    long timestamp = System.currentTimeMillis() / 1000;
    long expiry = timestamp + CLAIM_EXPIRY;
    String sessionId = request.getString(SESSION_ID);
    String link = request.getString(LINK);
    String audience = request.getString(URL);

    /* Populate the token claims */
    JsonObject claims = new JsonObject();
    claims
        .put(SUB, request.getString(USER_ID))
        .put(ISS, CLAIM_ISSUER)
        .put(AUD, audience)
        .put(EXP, expiry)
        .put(IAT, timestamp)
        .put(SID, sessionId)
        .put(LINK, link);

    String token = provider.generateToken(claims, options);

    JsonObject tokenResp = new JsonObject();
    tokenResp.put(APD_TOKEN, token).put("expiry", expiry).put("server", audience).put(LINK, link);
    return tokenResp;
  }

  /** {@inheritDoc} */
  @Override
  public Future<JsonObject> getAuthServerToken(String audienceUrl) {
    JsonObject adminTokenReq =
        new JsonObject()
            .put(USER_ID, CLAIM_ISSUER)
            .put(URL, audienceUrl)
            .put(ROLE, "")
            .put(ITEM_TYPE, "")
            .put(ITEM_ID, "");
    return Future.succeededFuture(getJwt(adminTokenReq));
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
    pgPool
        .withConnection(connection -> connection.preparedQuery(query).execute(tuple))
        .onComplete(
            handler -> {
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
  private Future<JsonObject> validateForIdentityToken(
      String url, Roles role, DelegationInformation delegInfo, User user) {

    Promise<JsonObject> promise = Promise.promise();

    if (role.equals(Roles.COS_ADMIN)) {
      return Future.failedFuture(
          new ComposeException(400, URN_INVALID_INPUT, ERR_COS_ADMIN_NO_RS, ERR_COS_ADMIN_NO_RS));
    }

    Future<JsonArray> resServer = pgSelelctQuery(CHECK_RS_EXISTS_BY_URL, Tuple.of(url));

    Future<Void> checkUrlExists =
        resServer.compose(
            res -> {
              if (res.isEmpty()) {
                return Future.failedFuture(
                    new ComposeException(
                        400, URN_INVALID_INPUT, ERR_TITLE_INVALID_RS, ERR_DETAIL_INVALID_RS));
              }

              return Future.succeededFuture();
            });

    checkUrlExists
        .compose(
            res -> {
              /*
               * For delegates, we only need to check if the delegated URL is the same as requested URL
               * because a delegation can be made only if the delegator has a role for that resource server
               */
              if (role.equals(Roles.DELEGATE)) {
                if (delegInfo.getDelegatedRsUrl().equals(url)) {
                  JsonObject result =
                      new JsonObject()
                          .put(URL, url)
                          .put(CREATE_TOKEN_DID, delegInfo.getDelegatorUserId())
                          .put(CREATE_TOKEN_DRL, delegInfo.getDelegatedRole().toString());
                  return Future.succeededFuture(result);
                }
                return Future.failedFuture(
                    new ComposeException(
                        403, URN_INVALID_INPUT, ACCESS_DENIED, ERR_DOES_NOT_HAVE_ROLE_FOR_RS));
              }

              if (user.getResServersForRole(role).contains(url)) {
                JsonObject result = new JsonObject().put(URL, url);
                return Future.succeededFuture(result);
              }

              return Future.failedFuture(
                  new ComposeException(
                      403, URN_INVALID_INPUT, ACCESS_DENIED, ERR_DOES_NOT_HAVE_ROLE_FOR_RS));
            })
        .onSuccess(succ -> promise.complete(succ))
        .onFailure(fail -> promise.fail(fail));

    return promise.future();
  }
}
