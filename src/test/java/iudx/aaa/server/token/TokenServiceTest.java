package iudx.aaa.server.token;

import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_AUTH_TOKEN;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_INPUT;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_ROLE;
import static iudx.aaa.server.apiserver.util.Urn.URN_MISSING_INFO;
import static iudx.aaa.server.apiserver.util.Urn.URN_SUCCESS;
import static iudx.aaa.server.policy.Constants.CAT_ID;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_NO_APPROVED_ROLES;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_NO_APPROVED_ROLES;
import static iudx.aaa.server.token.Constants.ACCESS_DENIED;
import static iudx.aaa.server.token.Constants.ACCESS_TOKEN;
import static iudx.aaa.server.token.Constants.APD_TOKEN;
import static iudx.aaa.server.token.Constants.AUD;
import static iudx.aaa.server.token.Constants.CLAIM_ISSUER;
import static iudx.aaa.server.token.Constants.CONS;
import static iudx.aaa.server.token.Constants.CONSTRAINTS;
import static iudx.aaa.server.token.Constants.CREATE_TOKEN_DID;
import static iudx.aaa.server.token.Constants.CREATE_TOKEN_DRL;
import static iudx.aaa.server.token.Constants.CREATE_TOKEN_RG;
import static iudx.aaa.server.token.Constants.DENY;
import static iudx.aaa.server.token.Constants.DID;
import static iudx.aaa.server.token.Constants.DRL;
import static iudx.aaa.server.token.Constants.ERR_COS_ADMIN_NO_RS;
import static iudx.aaa.server.token.Constants.ERR_DETAIL_APD_INTERACT_REQUIRED;
import static iudx.aaa.server.token.Constants.ERR_DETAIL_DELEGATION_INFO_MISSING;
import static iudx.aaa.server.token.Constants.ERR_DETAIL_INVALID_COS_URL;
import static iudx.aaa.server.token.Constants.ERR_DETAIL_INVALID_ROLE_FOR_COS;
import static iudx.aaa.server.token.Constants.ERR_DETAIL_INVALID_RS;
import static iudx.aaa.server.token.Constants.ERR_DETAIL_NO_RES_GRP_TOKEN;
import static iudx.aaa.server.token.Constants.ERR_DETAIL_ROLE_NOT_OWNED;
import static iudx.aaa.server.token.Constants.ERR_DOES_NOT_HAVE_ROLE_FOR_RS;
import static iudx.aaa.server.token.Constants.ERR_TITLE_APD_INTERACT_REQUIRED;
import static iudx.aaa.server.token.Constants.ERR_TITLE_DELEGATION_INFO_MISSING;
import static iudx.aaa.server.token.Constants.ERR_TITLE_INVALID_COS_URL;
import static iudx.aaa.server.token.Constants.ERR_TITLE_INVALID_ROLE_FOR_COS;
import static iudx.aaa.server.token.Constants.ERR_TITLE_INVALID_RS;
import static iudx.aaa.server.token.Constants.ERR_TITLE_NO_RES_GRP_TOKEN;
import static iudx.aaa.server.token.Constants.ERR_TITLE_ROLE_NOT_OWNED;
import static iudx.aaa.server.token.Constants.EXP;
import static iudx.aaa.server.token.Constants.IID;
import static iudx.aaa.server.token.Constants.INTROSPECT_USERINFO;
import static iudx.aaa.server.token.Constants.ISS;
import static iudx.aaa.server.token.Constants.ITEM_ID;
import static iudx.aaa.server.token.Constants.ITEM_TYPE;
import static iudx.aaa.server.token.Constants.LINK;
import static iudx.aaa.server.token.Constants.PG_CONNECTION_TIMEOUT;
import static iudx.aaa.server.token.Constants.RESOURCE_SVR;
import static iudx.aaa.server.token.Constants.RG;
import static iudx.aaa.server.token.Constants.ROLE;
import static iudx.aaa.server.token.Constants.RS_URL;
import static iudx.aaa.server.token.Constants.SESSION_ID;
import static iudx.aaa.server.token.Constants.SID;
import static iudx.aaa.server.token.Constants.STATUS;
import static iudx.aaa.server.token.Constants.SUB;
import static iudx.aaa.server.token.Constants.SUCCESS;
import static iudx.aaa.server.token.Constants.TYPE;
import static iudx.aaa.server.token.Constants.URL;
import static iudx.aaa.server.token.Constants.USER_ID;
import static iudx.aaa.server.token.RequestPayload.expiredTipPayload;
import static iudx.aaa.server.token.RequestPayload.mapToInspctToken;
import static iudx.aaa.server.token.RequestPayload.mapToRevToken;
import static iudx.aaa.server.token.RequestPayload.randomToken;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.KeyStoreOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import iudx.aaa.server.apiserver.ApdStatus;
import iudx.aaa.server.apiserver.DelegationInformation;
import iudx.aaa.server.apiserver.IntrospectToken;
import iudx.aaa.server.apiserver.RequestToken;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.policy.MockRegistrationFactory;
import iudx.aaa.server.policy.PolicyService;
import iudx.aaa.server.registration.RegistrationService;
import iudx.aaa.server.registration.Utils;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for token creation, introspection, revocation. */
@ExtendWith({VertxExtension.class, MockitoExtension.class})
public class TokenServiceTest {
  private static Logger LOGGER = LogManager.getLogger(TokenServiceTest.class);

  private static Configuration config;

  /* Database Properties */
  private static String databaseIP;
  private static int databasePort;
  private static String databaseName;
  private static String databaseSchema;
  private static String databaseUserName;
  private static String databasePassword;
  private static int poolSize;
  private static PoolOptions poolOptions;
  private static PgConnectOptions connectOptions;
  private static PgPool pgPool;
  private static TokenServiceImpl tokenServiceImplObj;
  private static TokenService tokenService;
  private static Vertx vertxObj;
  private static String keystorePath;
  private static String keystorePassword;
  private static JWTAuth provider;
  private static PolicyService policyService;
  private static RegistrationService registrationService;
  private static MockRegistrationFactory mockRegistrationFactory;
  private static MockPolicyFactory mockPolicy;
  private static TokenRevokeService httpWebClient;
  private static MockHttpWebClient mockHttpWebClient;

  private static final String DUMMY_COS_URL =
      "cos" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + "iudx.io";

  private static final String DUMMY_SERVER =
      "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

  private static final String DUMMY_ACTIVE_APD =
      "apd" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

  private static final String DUMMY_INACTIVE_APD =
      "apd" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

  private static String RESOURCE_GROUP = UUID.randomUUID().toString();
  private static String RESOURCE_ITEM = UUID.randomUUID().toString();

  // no need to register these users
  private static User normalUser = new UserBuilder().userId(UUID.randomUUID()).build();

  private static Utils utils;

  @BeforeAll
  @DisplayName("Deploying Verticle")
  static void startVertx(Vertx vertx, VertxTestContext testContext) {

    config = new Configuration();
    vertxObj = vertx;
    JsonObject dbConfig = config.configLoader(2, vertx);

    /* Read the configuration and set the postgres client properties. */
    LOGGER.debug("Info : Reading config file");

    databaseIP = dbConfig.getString("databaseIP");
    databasePort = Integer.parseInt(dbConfig.getString("databasePort"));
    databaseName = dbConfig.getString("databaseName");
    databaseSchema = dbConfig.getString("databaseSchema");
    databaseUserName = dbConfig.getString("databaseUserName");
    databasePassword = dbConfig.getString("databasePassword");
    poolSize = Integer.parseInt(dbConfig.getString("poolSize"));

    keystorePath = dbConfig.getString("keystorePath");
    keystorePassword = dbConfig.getString("keystorePassword");
    String issuer = DUMMY_COS_URL;

    if (issuer != null && !issuer.isBlank()) {
      CLAIM_ISSUER = issuer;
    } else {
      LOGGER.fatal("Fail: authServerDomain not set");
      throw new IllegalStateException("authServerDomain not set");
    }

    /* Set Connection Object and schema */
    if (connectOptions == null) {
      Map<String, String> schemaProp = Map.of("search_path", databaseSchema);

      connectOptions =
          new PgConnectOptions()
              .setPort(databasePort)
              .setHost(databaseIP)
              .setDatabase(databaseName)
              .setUser(databaseUserName)
              .setPassword(databasePassword)
              .setConnectTimeout(PG_CONNECTION_TIMEOUT)
              .setProperties(schemaProp);
    }

    /* Pool options */
    if (poolOptions == null) {
      poolOptions = new PoolOptions().setMaxSize(poolSize);
    }

    /* Initializing the services */
    provider = jwtInitConfig(vertx);
    pgPool = PgPool.pool(vertx, connectOptions, poolOptions);

    utils = new Utils(pgPool);

    Future<Void> create =
        utils
            .createFakeResourceServer(
                DUMMY_SERVER, new UserBuilder().userId(UUID.randomUUID()).build())
            .compose(res -> utils.createFakeUser(normalUser, false, false))
            .compose(
                res ->
                    utils.createFakeApd(
                        DUMMY_ACTIVE_APD,
                        new UserBuilder().userId(UUID.randomUUID()).build(),
                        ApdStatus.ACTIVE))
            .compose(
                res ->
                    utils.createFakeApd(
                        DUMMY_INACTIVE_APD,
                        new UserBuilder().userId(UUID.randomUUID()).build(),
                        ApdStatus.INACTIVE));

    create.onSuccess(
        r -> {
          mockPolicy = new MockPolicyFactory();
          mockHttpWebClient = new MockHttpWebClient();
          httpWebClient = mockHttpWebClient.getMockHttpWebClient();

          policyService = mockPolicy.getInstance();
          mockRegistrationFactory = new MockRegistrationFactory();
          registrationService = mockRegistrationFactory.getInstance();
          tokenServiceImplObj =
              new TokenServiceImpl(
                  pgPool, policyService, registrationService, provider, httpWebClient);
          tokenService = tokenServiceImplObj;

          testContext.completeNow();
        });
  }

  /* Initializing JwtProvider */
  public static JWTAuth jwtInitConfig(Vertx vertx) {
    JWTAuthOptions config = new JWTAuthOptions();
    config.setKeyStore(new KeyStoreOptions().setPath(keystorePath).setPassword(keystorePassword));

    JWTAuth provider = JWTAuth.create(vertx, config);
    return provider;
  }

  private static JsonObject getJwtPayload(String jwt) {
    String payload = jwt.split("\\.")[1];
    byte[] bytes = Base64.getUrlDecoder().decode(payload);
    return new JsonObject(new String(bytes, StandardCharsets.UTF_8));
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing....");
    utils
        .deleteFakeResourceServer()
        .compose(res -> utils.deleteFakeApd())
        .compose(res -> utils.deleteFakeUser())
        .onComplete(
            x -> {
              if (x.failed()) {
                LOGGER.warn(x.cause().getMessage());
              }
              vertxObj.close(testContext.succeeding(response -> testContext.completeNow()));
            });
  }

  @Test
  @DisplayName("Create Token - No approved roles - Fail")
  void createTokenNoUserProfile(VertxTestContext testContext) {

    JsonObject jsonReq =
        new JsonObject()
            .put("itemId", RESOURCE_ITEM)
            .put("itemType", "resource")
            .put("role", "consumer");
    RequestToken request = new RequestToken(jsonReq);

    tokenService
        .createToken(request, null, normalUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_MISSING_INFO.toString(), response.getString(TYPE));
                          assertEquals(ERR_TITLE_NO_APPROVED_ROLES, response.getString("title"));
                          assertEquals(ERR_DETAIL_NO_APPROVED_ROLES, response.getString("detail"));
                          assertEquals(404, response.getInteger("status"));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Create Token - Delegate must supply delegate info - Fail")
  void createTokenDelegateNeedsDelegInfo(VertxTestContext testContext) {

    User consumerUser = new User(normalUser.toJson());
    consumerUser.setRoles(List.of(Roles.DELEGATE));
    consumerUser.setRolesToRsMapping(
        Map.of(Roles.DELEGATE.toString(), new JsonArray().add(DUMMY_SERVER)));

    JsonObject jsonReq =
        new JsonObject()
            .put("itemId", RESOURCE_ITEM)
            .put("itemType", "resource")
            .put("role", "delegate");

    RequestToken request = new RequestToken(jsonReq);
    mockPolicy.setResponse("valid");
    tokenService
        .createToken(request, null, consumerUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_MISSING_INFO.toString(), response.getString(TYPE));
                          assertEquals(
                              ERR_TITLE_DELEGATION_INFO_MISSING, response.getString("title"));
                          assertEquals(
                              ERR_DETAIL_DELEGATION_INFO_MISSING, response.getString("detail"));
                          assertEquals(400, response.getInteger("status"));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Create Token - User does not have the requested role - Fail")
  void createTokenUserNotHaveRequestedRole(VertxTestContext testContext) {

    User consumerUser = new User(normalUser.toJson());
    consumerUser.setRoles(List.of(Roles.CONSUMER));
    consumerUser.setRolesToRsMapping(
        Map.of(Roles.CONSUMER.toString(), new JsonArray().add(DUMMY_SERVER)));

    JsonObject jsonReq =
        new JsonObject()
            .put("itemId", RESOURCE_ITEM)
            .put("itemType", "resource")
            .put("role", "delegate");

    RequestToken request = new RequestToken(jsonReq);
    mockPolicy.setResponse("valid");
    tokenService
        .createToken(request, null, consumerUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_INVALID_ROLE.toString(), response.getString(TYPE));
                          assertEquals(ERR_TITLE_ROLE_NOT_OWNED, response.getString("title"));
                          assertEquals(ERR_DETAIL_ROLE_NOT_OWNED, response.getString("detail"));
                          assertEquals(403, response.getInteger("status"));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName(
      "Create Token - Request for Resource Group token fails for admin, cos_admin, provider, consumer, delegate")
  void createTokenRsGrpFails(VertxTestContext testContext) {

    List<Roles> roles = List.of(Roles.ADMIN, Roles.COS_ADMIN, Roles.PROVIDER, Roles.CONSUMER);

    User user = new User(normalUser.toJson());
    user.setRoles(roles);
    user.setRolesToRsMapping(
        Map.of(
            Roles.CONSUMER.toString(),
            new JsonArray().add("some-rs.url"),
            Roles.PROVIDER.toString(),
            new JsonArray().add("some-rs.url"),
            Roles.ADMIN.toString(),
            new JsonArray().add("some-rs.url")));

    Map<Roles, Checkpoint> checks =
        roles.stream().collect(Collectors.toMap(role -> role, role -> testContext.checkpoint()));
    Checkpoint delegateCheck = testContext.checkpoint();
    roles.forEach(
        role -> {
          JsonObject jsonReq =
              new JsonObject()
                  .put("itemId", RESOURCE_GROUP)
                  .put("itemType", "resource_group")
                  .put("role", role.toString().toLowerCase());

          RequestToken request = new RequestToken(jsonReq);
          tokenService
              .createToken(request, null, user)
              .onComplete(
                  testContext.succeeding(
                      response ->
                          testContext.verify(
                              () -> {
                                assertEquals(
                                    URN_INVALID_INPUT.toString(), response.getString(TYPE));
                                assertEquals(
                                    ERR_TITLE_NO_RES_GRP_TOKEN, response.getString("title"));
                                assertEquals(
                                    ERR_DETAIL_NO_RES_GRP_TOKEN, response.getString("detail"));
                                assertEquals(403, response.getInteger("status"));
                                checks.get(role).flag();
                              })));
        });

    User delegateUser = new User(normalUser.toJson());
    delegateUser.setRoles(List.of(Roles.DELEGATE));
    delegateUser.setRolesToRsMapping(
        Map.of(Roles.DELEGATE.toString(), new JsonArray().add("some-rs.url")));

    DelegationInformation delegConsInfo =
        new DelegationInformation(
            UUID.randomUUID(),
            UUID.fromString(normalUser.getUserId()),
            Roles.CONSUMER,
            DUMMY_SERVER);

    JsonObject jsonReq =
        new JsonObject()
            .put("itemId", RESOURCE_GROUP)
            .put("itemType", "resource_group")
            .put("role", "delegate");

    RequestToken request = new RequestToken(jsonReq);
    tokenService
        .createToken(request, delegConsInfo, delegateUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_INVALID_INPUT.toString(), response.getString(TYPE));
                          assertEquals(ERR_TITLE_NO_RES_GRP_TOKEN, response.getString("title"));
                          assertEquals(ERR_DETAIL_NO_RES_GRP_TOKEN, response.getString("detail"));
                          assertEquals(403, response.getInteger("status"));
                          delegateCheck.flag();
                        })));
  }

  @Test
  @DisplayName("Consumer getting Resource token - Success")
  void createTokenConsResItemSuccess(VertxTestContext testContext) {

    User consumerUser = new User(normalUser.toJson());
    consumerUser.setRoles(List.of(Roles.CONSUMER));
    consumerUser.setRolesToRsMapping(
        Map.of(Roles.CONSUMER.toString(), new JsonArray().add(DUMMY_SERVER)));

    JsonObject jsonReq =
        new JsonObject()
            .put("itemId", RESOURCE_ITEM)
            .put("itemType", "resource")
            .put("role", "consumer");
    RequestToken request = new RequestToken(jsonReq);

    JsonObject policyResponse =
        new JsonObject()
            .put(STATUS, SUCCESS)
            .put(CAT_ID, RESOURCE_ITEM)
            .put(CREATE_TOKEN_RG, RESOURCE_GROUP)
            .put(CONSTRAINTS, new JsonObject().put("access", new JsonArray().add("sub").add("api")))
            .put(URL, DUMMY_SERVER);
    mockPolicy.setResponse(policyResponse);

    tokenService
        .createToken(request, null, consumerUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_SUCCESS.toString(), response.getString("type"));
                          JsonObject payload =
                              getJwtPayload(
                                  response.getJsonObject("results").getString(ACCESS_TOKEN));
                          assertEquals(payload.getString(SUB), consumerUser.getUserId());
                          assertEquals(payload.getString(ISS), DUMMY_COS_URL);
                          assertEquals(payload.getString(AUD), DUMMY_SERVER);
                          assertEquals(payload.getString(IID), "ri:" + RESOURCE_ITEM);
                          assertEquals(payload.getString(RG), RESOURCE_GROUP);
                          assertEquals(
                              payload.getString(ROLE), Roles.CONSUMER.toString().toLowerCase());
                          assertFalse(payload.getJsonObject(CONS).isEmpty());
                          assertNotNull(payload.getString(EXP));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Provider getting Resource token - Success")
  void createTokenProvResItemSuccess(VertxTestContext testContext) {

    User providerUser = new User(normalUser.toJson());
    providerUser.setRoles(List.of(Roles.PROVIDER));
    providerUser.setRolesToRsMapping(
        Map.of(Roles.PROVIDER.toString(), new JsonArray().add(DUMMY_SERVER)));

    JsonObject jsonReq =
        new JsonObject()
            .put("itemId", RESOURCE_ITEM)
            .put("itemType", "resource")
            .put("role", "provider");
    RequestToken request = new RequestToken(jsonReq);

    JsonObject policyResponse =
        new JsonObject()
            .put(STATUS, SUCCESS)
            .put(CAT_ID, RESOURCE_ITEM)
            .put(CREATE_TOKEN_RG, RESOURCE_GROUP)
            .put(CONSTRAINTS, new JsonObject().put("access", new JsonArray().add("sub").add("api")))
            .put(URL, DUMMY_SERVER);
    mockPolicy.setResponse(policyResponse);

    tokenService
        .createToken(request, null, providerUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_SUCCESS.toString(), response.getString("type"));
                          JsonObject payload =
                              getJwtPayload(
                                  response.getJsonObject("results").getString(ACCESS_TOKEN));
                          assertEquals(payload.getString(SUB), providerUser.getUserId());
                          assertEquals(payload.getString(ISS), DUMMY_COS_URL);
                          assertEquals(payload.getString(AUD), DUMMY_SERVER);
                          assertEquals(payload.getString(IID), "ri:" + RESOURCE_ITEM);
                          assertEquals(payload.getString(RG), RESOURCE_GROUP);
                          assertEquals(
                              payload.getString(ROLE), Roles.PROVIDER.toString().toLowerCase());
                          assertFalse(payload.getJsonObject(CONS).isEmpty());
                          assertNotNull(payload.getString(EXP));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Delegate getting Resource token as consumer delegate - Success")
  void createTokenConsDelegateResourceSuccess(VertxTestContext testContext) {

    User delegateUser = new User(normalUser.toJson());
    delegateUser.setRoles(List.of(Roles.DELEGATE));
    delegateUser.setRolesToRsMapping(
        Map.of(Roles.DELEGATE.toString(), new JsonArray().add(DUMMY_SERVER)));

    JsonObject delegJsonReq =
        new JsonObject()
            .put("itemId", RESOURCE_ITEM)
            .put("itemType", "resource")
            .put("role", "delegate");

    RequestToken request = new RequestToken(delegJsonReq);
    DelegationInformation delegConsInfo =
        new DelegationInformation(
            UUID.randomUUID(),
            UUID.fromString(normalUser.getUserId()),
            Roles.CONSUMER,
            DUMMY_SERVER);

    JsonObject policyResponse =
        new JsonObject()
            .put(STATUS, SUCCESS)
            .put(CAT_ID, RESOURCE_ITEM)
            .put(CREATE_TOKEN_RG, RESOURCE_GROUP)
            .put(CONSTRAINTS, new JsonObject().put("access", new JsonArray().add("sub").add("api")))
            .put(CREATE_TOKEN_DID, normalUser.getUserId())
            .put(CREATE_TOKEN_DRL, Roles.CONSUMER.toString())
            .put(URL, DUMMY_SERVER);
    mockPolicy.setResponse(policyResponse);

    tokenService
        .createToken(request, delegConsInfo, delegateUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_SUCCESS.toString(), response.getString("type"));
                          JsonObject payload =
                              getJwtPayload(
                                  response.getJsonObject("results").getString(ACCESS_TOKEN));
                          assertEquals(payload.getString(SUB), delegateUser.getUserId());
                          assertEquals(payload.getString(ISS), DUMMY_COS_URL);
                          assertEquals(payload.getString(AUD), DUMMY_SERVER);
                          assertEquals(payload.getString(IID), "ri:" + RESOURCE_ITEM);
                          assertEquals(payload.getString(RG), RESOURCE_GROUP);
                          assertEquals(
                              payload.getString(ROLE), Roles.DELEGATE.toString().toLowerCase());
                          assertEquals(payload.getString(DID), normalUser.getUserId());
                          assertEquals(
                              payload.getString(DRL), Roles.CONSUMER.toString().toLowerCase());
                          assertFalse(payload.getJsonObject(CONS).isEmpty());
                          assertNotNull(payload.getString(EXP));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Delegate getting Resource token as provider delegate - Success")
  void createTokenProvDelegateResourceSuccess(VertxTestContext testContext) {

    User delegateUser = new User(normalUser.toJson());
    delegateUser.setRoles(List.of(Roles.DELEGATE));
    delegateUser.setRolesToRsMapping(
        Map.of(Roles.DELEGATE.toString(), new JsonArray().add(DUMMY_SERVER)));

    JsonObject delegJsonReq =
        new JsonObject()
            .put("itemId", RESOURCE_ITEM)
            .put("itemType", "resource")
            .put("role", "delegate");

    RequestToken request = new RequestToken(delegJsonReq);
    DelegationInformation delegProvInfo =
        new DelegationInformation(
            UUID.randomUUID(),
            UUID.fromString(normalUser.getUserId()),
            Roles.PROVIDER,
            DUMMY_SERVER);

    JsonObject policyResponse =
        new JsonObject()
            .put(STATUS, SUCCESS)
            .put(CAT_ID, RESOURCE_ITEM)
            .put(CREATE_TOKEN_RG, RESOURCE_GROUP)
            .put(CONSTRAINTS, new JsonObject())
            .put(CREATE_TOKEN_DID, normalUser.getUserId())
            .put(CREATE_TOKEN_DRL, Roles.PROVIDER.toString())
            .put(URL, DUMMY_SERVER);
    mockPolicy.setResponse(policyResponse);

    tokenService
        .createToken(request, delegProvInfo, delegateUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_SUCCESS.toString(), response.getString("type"));
                          JsonObject payload =
                              getJwtPayload(
                                  response.getJsonObject("results").getString(ACCESS_TOKEN));
                          assertEquals(payload.getString(SUB), delegateUser.getUserId());
                          assertEquals(payload.getString(ISS), DUMMY_COS_URL);
                          assertEquals(payload.getString(AUD), DUMMY_SERVER);
                          assertEquals(payload.getString(IID), "ri:" + RESOURCE_ITEM);
                          assertEquals(payload.getString(RG), RESOURCE_GROUP);
                          assertEquals(
                              payload.getString(ROLE), Roles.DELEGATE.toString().toLowerCase());
                          assertEquals(payload.getString(DID), normalUser.getUserId());
                          assertEquals(
                              payload.getString(DRL), Roles.PROVIDER.toString().toLowerCase());
                          assertTrue(payload.getJsonObject(CONS).isEmpty());
                          assertNotNull(payload.getString(EXP));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Create Token - Request for COS token fails for admin, provider, consumer, delegate")
  void createTokenCosTokenFailsWrongRole(VertxTestContext testContext) {

    List<Roles> roles = List.of(Roles.ADMIN, Roles.PROVIDER, Roles.CONSUMER);

    User user = new User(normalUser.toJson());
    user.setRoles(roles);
    user.setRolesToRsMapping(
        Map.of(
            Roles.CONSUMER.toString(),
            new JsonArray().add("some-rs.url"),
            Roles.PROVIDER.toString(),
            new JsonArray().add("some-rs.url"),
            Roles.ADMIN.toString(),
            new JsonArray().add("some-rs.url")));

    Map<Roles, Checkpoint> checks =
        roles.stream().collect(Collectors.toMap(role -> role, role -> testContext.checkpoint()));
    Checkpoint delegateCheck = testContext.checkpoint();
    roles.forEach(
        role -> {
          JsonObject jsonReq =
              new JsonObject()
                  .put("itemId", DUMMY_COS_URL)
                  .put("itemType", "cos")
                  .put("role", role.toString().toLowerCase());

          RequestToken request = new RequestToken(jsonReq);
          tokenService
              .createToken(request, null, user)
              .onComplete(
                  testContext.succeeding(
                      response ->
                          testContext.verify(
                              () -> {
                                assertEquals(URN_INVALID_ROLE.toString(), response.getString(TYPE));
                                assertEquals(
                                    ERR_TITLE_INVALID_ROLE_FOR_COS, response.getString("title"));
                                assertEquals(
                                    ERR_DETAIL_INVALID_ROLE_FOR_COS, response.getString("detail"));
                                assertEquals(403, response.getInteger("status"));
                                checks.get(role).flag();
                              })));
        });

    User delegateUser = new User(normalUser.toJson());
    delegateUser.setRoles(List.of(Roles.DELEGATE));
    delegateUser.setRolesToRsMapping(
        Map.of(Roles.DELEGATE.toString(), new JsonArray().add("some-rs.url")));

    DelegationInformation delegConsInfo =
        new DelegationInformation(
            UUID.randomUUID(),
            UUID.fromString(normalUser.getUserId()),
            Roles.CONSUMER,
            DUMMY_SERVER);

    JsonObject jsonReq =
        new JsonObject()
            .put("itemId", DUMMY_COS_URL)
            .put("itemType", "cos")
            .put("role", "delegate");

    RequestToken request = new RequestToken(jsonReq);
    tokenService
        .createToken(request, delegConsInfo, delegateUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_INVALID_ROLE.toString(), response.getString(TYPE));
                          assertEquals(ERR_TITLE_INVALID_ROLE_FOR_COS, response.getString("title"));
                          assertEquals(
                              ERR_DETAIL_INVALID_ROLE_FOR_COS, response.getString("detail"));
                          assertEquals(403, response.getInteger("status"));
                          delegateCheck.flag();
                        })));
  }

  @Test
  @DisplayName("Create Token - Request for COS token invalid COS URL")
  void createTokenCosTokenFailsInvalidCosUrl(VertxTestContext testContext) {

    User cosAdminUser = new User(normalUser.toJson());
    cosAdminUser.setRoles(List.of(Roles.COS_ADMIN));

    JsonObject jsonReq =
        new JsonObject()
            .put("itemId", RandomStringUtils.randomAlphabetic(10) + ".com")
            .put("itemType", "cos")
            .put("role", "cos_admin");

    RequestToken request = new RequestToken(jsonReq);
    tokenService
        .createToken(request, null, cosAdminUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_INVALID_INPUT.toString(), response.getString(TYPE));
                          assertEquals(ERR_TITLE_INVALID_COS_URL, response.getString("title"));
                          assertEquals(ERR_DETAIL_INVALID_COS_URL, response.getString("detail"));
                          assertEquals(403, response.getInteger("status"));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("COS Admin getting COS token - Success")
  void createTokenCosTokenSuccess(VertxTestContext testContext) {

    User cosAdminUser = new User(normalUser.toJson());
    cosAdminUser.setRoles(List.of(Roles.COS_ADMIN));

    JsonObject jsonReq =
        new JsonObject()
            .put("itemId", DUMMY_COS_URL)
            .put("itemType", "cos")
            .put("role", "cos_admin");
    RequestToken request = new RequestToken(jsonReq);

    tokenService
        .createToken(request, null, cosAdminUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_SUCCESS.toString(), response.getString("type"));
                          JsonObject payload =
                              getJwtPayload(
                                  response.getJsonObject("results").getString(ACCESS_TOKEN));
                          assertEquals(payload.getString(SUB), cosAdminUser.getUserId());
                          assertEquals(payload.getString(ISS), DUMMY_COS_URL);
                          assertEquals(payload.getString(AUD), DUMMY_COS_URL);
                          assertEquals(payload.getString(IID), "cos:" + DUMMY_COS_URL);
                          assertEquals(
                              payload.getString(ROLE), Roles.COS_ADMIN.toString().toLowerCase());
                          assertTrue(payload.getJsonObject(CONS).isEmpty());
                          assertNotNull(payload.getString(EXP));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Consumer getting APD token for a resource item - Success")
  void getApdTokenConsumerSuccess(VertxTestContext testContext) {

    User consumerUser = new User(normalUser.toJson());
    consumerUser.setRoles(List.of(Roles.CONSUMER));
    consumerUser.setRolesToRsMapping(
        Map.of(Roles.CONSUMER.toString(), new JsonArray().add(DUMMY_SERVER)));

    JsonObject jsonReq =
        new JsonObject()
            .put("itemId", RESOURCE_ITEM)
            .put("itemType", "resource")
            .put("role", "consumer");
    RequestToken request = new RequestToken(jsonReq);

    mockPolicy.setResponse("apd-interaction", DUMMY_SERVER + "/apd-interact", DUMMY_SERVER);

    tokenService
        .createToken(request, null, consumerUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_MISSING_INFO.toString(), response.getString("type"));
                          assertEquals(
                              ERR_TITLE_APD_INTERACT_REQUIRED.toString(),
                              response.getString("title"));
                          assertEquals(
                              ERR_DETAIL_APD_INTERACT_REQUIRED.toString(),
                              response.getString("detail"));

                          JsonObject apdToken =
                              getJwtPayload(response.getJsonObject("context").getString(APD_TOKEN));
                          assertEquals(apdToken.getString(SUB), consumerUser.getUserId());
                          assertEquals(apdToken.getString(ISS), DUMMY_COS_URL);
                          assertEquals(apdToken.getString(AUD), DUMMY_SERVER);
                          assertTrue(apdToken.containsKey(SID));
                          assertEquals(apdToken.getString(LINK), DUMMY_SERVER + "/apd-interact");
                          assertNotNull(apdToken.getString(EXP));
                          assertEquals(
                              response.getJsonObject("context").getString(LINK),
                              DUMMY_SERVER + "/apd-interact");
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("COS Admin cannot get Resource Server token")
  void createTokenCosAdminResServFail(VertxTestContext testContext) {

    User cosAdminUser = new User(normalUser.toJson());
    cosAdminUser.setRoles(List.of(Roles.COS_ADMIN));

    JsonObject jsonReq =
        new JsonObject()
            .put("itemId", DUMMY_SERVER)
            .put("itemType", "resource_server")
            .put("role", "cos_admin");
    RequestToken request = new RequestToken(jsonReq);

    tokenService
        .createToken(request, null, cosAdminUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_INVALID_INPUT.toString(), response.getString(TYPE));
                          assertEquals(ERR_COS_ADMIN_NO_RS, response.getString("title"));
                          assertEquals(ERR_COS_ADMIN_NO_RS, response.getString("detail"));
                          assertEquals(400, response.getInteger("status"));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Consumer getting Resource Server token - Success")
  void createTokenConsumerResServSuccess(VertxTestContext testContext) {

    User consumerUser = new User(normalUser.toJson());
    consumerUser.setRoles(List.of(Roles.CONSUMER));
    consumerUser.setRolesToRsMapping(
        Map.of(Roles.CONSUMER.toString(), new JsonArray().add(DUMMY_SERVER)));

    JsonObject jsonReq =
        new JsonObject()
            .put("itemId", DUMMY_SERVER)
            .put("itemType", "resource_server")
            .put("role", "consumer");
    RequestToken request = new RequestToken(jsonReq);

    tokenService
        .createToken(request, null, consumerUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_SUCCESS.toString(), response.getString("type"));
                          JsonObject payload =
                              getJwtPayload(
                                  response.getJsonObject("results").getString(ACCESS_TOKEN));
                          assertEquals(payload.getString(SUB), consumerUser.getUserId());
                          assertEquals(payload.getString(ISS), DUMMY_COS_URL);
                          assertEquals(payload.getString(AUD), DUMMY_SERVER);
                          assertEquals(payload.getString(IID), "rs:" + DUMMY_SERVER);
                          assertEquals(
                              payload.getString(ROLE), Roles.CONSUMER.toString().toLowerCase());
                          assertTrue(payload.getJsonObject(CONS).isEmpty());
                          assertNotNull(payload.getString(EXP));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Provider getting Resource Server token - Success")
  void createTokenProviderResServSuccess(VertxTestContext testContext) {

    User providerUser = new User(normalUser.toJson());
    providerUser.setRoles(List.of(Roles.PROVIDER));
    providerUser.setRolesToRsMapping(
        Map.of(Roles.PROVIDER.toString(), new JsonArray().add(DUMMY_SERVER)));

    JsonObject jsonReq =
        new JsonObject()
            .put("itemId", DUMMY_SERVER)
            .put("itemType", "resource_server")
            .put("role", "provider");
    RequestToken request = new RequestToken(jsonReq);

    tokenService
        .createToken(request, null, providerUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_SUCCESS.toString(), response.getString("type"));
                          JsonObject payload =
                              getJwtPayload(
                                  response.getJsonObject("results").getString(ACCESS_TOKEN));
                          assertEquals(payload.getString(SUB), providerUser.getUserId());
                          assertEquals(payload.getString(ISS), DUMMY_COS_URL);
                          assertEquals(payload.getString(AUD), DUMMY_SERVER);
                          assertEquals(payload.getString(IID), "rs:" + DUMMY_SERVER);
                          assertEquals(
                              payload.getString(ROLE), Roles.PROVIDER.toString().toLowerCase());
                          assertTrue(payload.getJsonObject(CONS).isEmpty());
                          assertNotNull(payload.getString(EXP));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Admin getting Resource Server token - Success")
  void createTokenAdminResServSuccess(VertxTestContext testContext) {

    User adminUser = new User(normalUser.toJson());
    adminUser.setRoles(List.of(Roles.ADMIN));
    adminUser.setRolesToRsMapping(
        Map.of(Roles.ADMIN.toString(), new JsonArray().add(DUMMY_SERVER)));

    JsonObject jsonReq =
        new JsonObject()
            .put("itemId", DUMMY_SERVER)
            .put("itemType", "resource_server")
            .put("role", "admin");
    RequestToken request = new RequestToken(jsonReq);

    tokenService
        .createToken(request, null, adminUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_SUCCESS.toString(), response.getString("type"));
                          JsonObject payload =
                              getJwtPayload(
                                  response.getJsonObject("results").getString(ACCESS_TOKEN));
                          assertEquals(payload.getString(SUB), adminUser.getUserId());
                          assertEquals(payload.getString(ISS), DUMMY_COS_URL);
                          assertEquals(payload.getString(AUD), DUMMY_SERVER);
                          assertEquals(payload.getString(IID), "rs:" + DUMMY_SERVER);
                          assertEquals(
                              payload.getString(ROLE), Roles.ADMIN.toString().toLowerCase());
                          assertTrue(payload.getJsonObject(CONS).isEmpty());
                          assertNotNull(payload.getString(EXP));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Delegate getting Resource Server token - Success")
  void createTokenDelegateResServSuccess(VertxTestContext testContext) {

    User delegateUser = new User(normalUser.toJson());
    delegateUser.setRoles(List.of(Roles.DELEGATE));
    delegateUser.setRolesToRsMapping(
        Map.of(Roles.DELEGATE.toString(), new JsonArray().add(DUMMY_SERVER)));

    JsonObject delegJsonReq =
        new JsonObject()
            .put("itemId", DUMMY_SERVER)
            .put("itemType", "resource_server")
            .put("role", "delegate");

    RequestToken request = new RequestToken(delegJsonReq);
    Checkpoint consumerDelegateRsToken = testContext.checkpoint();
    Checkpoint providerDelegateRsToken = testContext.checkpoint();

    DelegationInformation delegConsInfo =
        new DelegationInformation(
            UUID.randomUUID(),
            UUID.fromString(normalUser.getUserId()),
            Roles.CONSUMER,
            DUMMY_SERVER);

    DelegationInformation delegProvInfo =
        new DelegationInformation(
            UUID.randomUUID(),
            UUID.fromString(normalUser.getUserId()),
            Roles.PROVIDER,
            DUMMY_SERVER);

    tokenService
        .createToken(request, delegConsInfo, delegateUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_SUCCESS.toString(), response.getString("type"));
                          JsonObject payload =
                              getJwtPayload(
                                  response.getJsonObject("results").getString(ACCESS_TOKEN));
                          assertEquals(payload.getString(SUB), delegateUser.getUserId());
                          assertEquals(payload.getString(ISS), DUMMY_COS_URL);
                          assertEquals(payload.getString(AUD), DUMMY_SERVER);
                          assertEquals(payload.getString(IID), "rs:" + DUMMY_SERVER);
                          assertEquals(
                              payload.getString(ROLE), Roles.DELEGATE.toString().toLowerCase());
                          assertEquals(payload.getString(DID), normalUser.getUserId());
                          assertEquals(
                              payload.getString(DRL), Roles.CONSUMER.toString().toLowerCase());
                          assertTrue(payload.getJsonObject(CONS).isEmpty());
                          assertNotNull(payload.getString(EXP));
                          consumerDelegateRsToken.flag();
                        })));

    tokenService
        .createToken(request, delegProvInfo, delegateUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_SUCCESS.toString(), response.getString("type"));
                          JsonObject payload =
                              getJwtPayload(
                                  response.getJsonObject("results").getString(ACCESS_TOKEN));
                          assertEquals(payload.getString(SUB), delegateUser.getUserId());
                          assertEquals(payload.getString(ISS), DUMMY_COS_URL);
                          assertEquals(payload.getString(AUD), DUMMY_SERVER);
                          assertEquals(payload.getString(IID), "rs:" + DUMMY_SERVER);
                          assertEquals(
                              payload.getString(ROLE), Roles.DELEGATE.toString().toLowerCase());
                          assertEquals(payload.getString(DID), normalUser.getUserId());
                          assertEquals(
                              payload.getString(DRL), Roles.PROVIDER.toString().toLowerCase());
                          assertTrue(payload.getJsonObject(CONS).isEmpty());
                          assertNotNull(payload.getString(EXP));
                          providerDelegateRsToken.flag();
                        })));
  }

  @Test
  @DisplayName("Consumer, provider, admin cannot get Resource Server token for non-existent RS")
  void createTokenResServNoRoleForRsThatDoesNotExist(VertxTestContext testContext) {

    List<Roles> rolesToTest = List.of(Roles.PROVIDER, Roles.CONSUMER, Roles.ADMIN);

    User user = new User(normalUser.toJson());
    user.setRoles(rolesToTest);
    user.setRolesToRsMapping(
        Map.of(
            Roles.CONSUMER.toString(),
            new JsonArray().add(DUMMY_SERVER),
            Roles.PROVIDER.toString(),
            new JsonArray().add(DUMMY_SERVER),
            Roles.ADMIN.toString(),
            new JsonArray().add(DUMMY_SERVER)));

    Map<Roles, Checkpoint> checks =
        rolesToTest.stream()
            .collect(Collectors.toMap(role -> role, role -> testContext.checkpoint()));

    rolesToTest.forEach(
        role -> {
          JsonObject jsonReq =
              new JsonObject()
                  .put("itemId", RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com")
                  .put("itemType", "resource_server")
                  .put("role", role.toString().toLowerCase());

          RequestToken request = new RequestToken(jsonReq);
          tokenService
              .createToken(request, null, user)
              .onComplete(
                  testContext.succeeding(
                      response ->
                          testContext.verify(
                              () -> {
                                assertEquals(
                                    URN_INVALID_INPUT.toString(), response.getString(TYPE));
                                assertEquals(ERR_TITLE_INVALID_RS, response.getString("title"));
                                assertEquals(ERR_DETAIL_INVALID_RS, response.getString("detail"));
                                assertEquals(400, response.getInteger("status"));
                                checks.get(role).flag();
                              })));
        });
  }

  @Test
  @DisplayName("Consumer, provider, admin cannot get Resource Server token if no role for RS")
  void createTokenResServNoRoleForResServ(VertxTestContext testContext) {

    List<Roles> rolesToTest = List.of(Roles.PROVIDER, Roles.CONSUMER, Roles.ADMIN);

    User user = new User(normalUser.toJson());
    user.setRoles(rolesToTest);
    user.setRolesToRsMapping(
        Map.of(
            Roles.CONSUMER.toString(),
            new JsonArray().add("some-rs.url"),
            Roles.PROVIDER.toString(),
            new JsonArray().add("some-rs.url"),
            Roles.ADMIN.toString(),
            new JsonArray().add("some-rs.url")));

    Map<Roles, Checkpoint> checks =
        rolesToTest.stream()
            .collect(Collectors.toMap(role -> role, role -> testContext.checkpoint()));

    rolesToTest.forEach(
        role -> {
          JsonObject jsonReq =
              new JsonObject()
                  .put("itemId", DUMMY_SERVER)
                  .put("itemType", "resource_server")
                  .put("role", role.toString().toLowerCase());

          RequestToken request = new RequestToken(jsonReq);
          tokenService
              .createToken(request, null, user)
              .onComplete(
                  testContext.succeeding(
                      response ->
                          testContext.verify(
                              () -> {
                                assertEquals(
                                    URN_INVALID_INPUT.toString(), response.getString(TYPE));
                                assertEquals(ACCESS_DENIED, response.getString("title"));
                                assertEquals(
                                    ERR_DOES_NOT_HAVE_ROLE_FOR_RS, response.getString("detail"));
                                assertEquals(403, response.getInteger("status"));
                                checks.get(role).flag();
                              })));
        });
  }

  @Test
  @DisplayName(
      "Delegate cannot get Resource Server token if delegated RS URL is not the requested RS URL")
  void createTokenDelegateRsUrlNotMatchDelegatedRsUrl(VertxTestContext testContext) {

    // the delegate has delegations for both `delegatedRsUrl` and DUMMY_SERVER, hence the roles to
    // RS map
    // has both. The token request was for DUMMY_SERVER, but the delegation info (via the
    // delegationId)
    // is for `delegatedRsUrl`
    String delegatedRsUrl = "some-rs.url";
    User delegateUser = new User(normalUser.toJson());
    delegateUser.setRoles(List.of(Roles.DELEGATE));
    delegateUser.setRolesToRsMapping(
        Map.of(Roles.DELEGATE.toString(), new JsonArray().add(DUMMY_SERVER).add(delegatedRsUrl)));

    JsonObject delegJsonReq =
        new JsonObject()
            .put("itemId", DUMMY_SERVER)
            .put("itemType", "resource_server")
            .put("role", "delegate");

    RequestToken request = new RequestToken(delegJsonReq);

    Checkpoint consDelegFail = testContext.checkpoint();
    Checkpoint provDelegFail = testContext.checkpoint();

    DelegationInformation consDelegInfo =
        new DelegationInformation(
            UUID.randomUUID(),
            UUID.fromString(normalUser.getUserId()),
            Roles.CONSUMER,
            delegatedRsUrl);

    DelegationInformation provDelegInfo =
        new DelegationInformation(
            UUID.randomUUID(),
            UUID.fromString(normalUser.getUserId()),
            Roles.PROVIDER,
            delegatedRsUrl);

    tokenService
        .createToken(request, consDelegInfo, delegateUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_INVALID_INPUT.toString(), response.getString(TYPE));
                          assertEquals(ACCESS_DENIED, response.getString("title"));
                          assertEquals(ERR_DOES_NOT_HAVE_ROLE_FOR_RS, response.getString("detail"));
                          assertEquals(403, response.getInteger("status"));
                          consDelegFail.flag();
                        })));

    tokenService
        .createToken(request, provDelegInfo, delegateUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_INVALID_INPUT.toString(), response.getString(TYPE));
                          assertEquals(ACCESS_DENIED, response.getString("title"));
                          assertEquals(ERR_DOES_NOT_HAVE_ROLE_FOR_RS, response.getString("detail"));
                          assertEquals(403, response.getInteger("status"));
                          provDelegFail.flag();
                        })));
  }

  @Test
  @DisplayName("Delegate cannot get Resource Server token if RS does not exist")
  void createTokenDelegateRsNotExist(VertxTestContext testContext) {

    User delegateUser = new User(normalUser.toJson());
    delegateUser.setRoles(List.of(Roles.DELEGATE));
    delegateUser.setRolesToRsMapping(
        Map.of(Roles.DELEGATE.toString(), new JsonArray().add(DUMMY_SERVER)));

    JsonObject delegJsonReq =
        new JsonObject()
            .put("itemId", RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com")
            .put("itemType", "resource_server")
            .put("role", "delegate");

    RequestToken request = new RequestToken(delegJsonReq);

    Checkpoint consDelegFail = testContext.checkpoint();
    Checkpoint provDelegFail = testContext.checkpoint();

    DelegationInformation consDelegInfo =
        new DelegationInformation(
            UUID.randomUUID(),
            UUID.fromString(normalUser.getUserId()),
            Roles.CONSUMER,
            DUMMY_SERVER);

    DelegationInformation provDelegInfo =
        new DelegationInformation(
            UUID.randomUUID(),
            UUID.fromString(normalUser.getUserId()),
            Roles.PROVIDER,
            DUMMY_SERVER);

    tokenService
        .createToken(request, consDelegInfo, delegateUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_INVALID_INPUT.toString(), response.getString(TYPE));
                          assertEquals(ERR_TITLE_INVALID_RS, response.getString("title"));
                          assertEquals(ERR_DETAIL_INVALID_RS, response.getString("detail"));
                          assertEquals(400, response.getInteger("status"));
                          consDelegFail.flag();
                        })));

    tokenService
        .createToken(request, provDelegInfo, delegateUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_INVALID_INPUT.toString(), response.getString(TYPE));
                          assertEquals(ERR_TITLE_INVALID_RS, response.getString("title"));
                          assertEquals(ERR_DETAIL_INVALID_RS, response.getString("detail"));
                          assertEquals(400, response.getInteger("status"));
                          provDelegFail.flag();
                        })));
  }

  @Test
  @DisplayName("createToken invalid policy [Fail]")
  void createTokenFailedInvalidPolicy(VertxTestContext testContext) {

    User consumerUser = new User(normalUser.toJson());
    consumerUser.setRoles(List.of(Roles.CONSUMER));
    consumerUser.setRolesToRsMapping(
        Map.of(Roles.CONSUMER.toString(), new JsonArray().add(DUMMY_SERVER)));

    JsonObject jsonReq =
        new JsonObject()
            .put("itemId", RESOURCE_ITEM)
            .put("itemType", "resource")
            .put("role", "consumer");
    RequestToken request = new RequestToken(jsonReq);

    mockPolicy.setResponse("invalid");
    tokenService
        .createToken(request, null, consumerUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_INVALID_INPUT.toString(), response.getString(TYPE));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("revokeToken [Success - revoke for RS]")
  void revokeTokenSuccessRs(VertxTestContext testContext) {
    User consumerUser = new User(normalUser.toJson());
    consumerUser.setRoles(List.of(Roles.CONSUMER));
    consumerUser.setRolesToRsMapping(
        Map.of(Roles.CONSUMER.toString(), new JsonArray().add(DUMMY_SERVER)));

    JsonObject request = new JsonObject().put(RS_URL, DUMMY_SERVER);

    mockHttpWebClient.setResponse("valid");
    tokenService
        .revokeToken(mapToRevToken(request), consumerUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("revokeToken [Success - revoke for RS]")
  void revokeTokenSuccessApd(VertxTestContext testContext) {
    User consumerUser = new User(normalUser.toJson());
    consumerUser.setRoles(List.of(Roles.CONSUMER));
    consumerUser.setRolesToRsMapping(
        Map.of(Roles.CONSUMER.toString(), new JsonArray().add(DUMMY_SERVER)));

    JsonObject request = new JsonObject().put(RS_URL, DUMMY_ACTIVE_APD);

    mockHttpWebClient.setResponse("valid");
    tokenService
        .revokeToken(mapToRevToken(request), consumerUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("revokeToken [Failed-01 Failure in RS]")
  void revokeTokenFailed01(VertxTestContext testContext) {

    User consumerUser = new User(normalUser.toJson());
    consumerUser.setRoles(List.of(Roles.CONSUMER));
    consumerUser.setRolesToRsMapping(
        Map.of(Roles.CONSUMER.toString(), new JsonArray().add(DUMMY_SERVER)));

    JsonObject request = new JsonObject().put(RS_URL, DUMMY_SERVER);
    mockHttpWebClient.setResponse("invalid");
    tokenService
        .revokeToken(mapToRevToken(request), consumerUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_INVALID_INPUT.toString(), response.getString(TYPE));
                          assertEquals(400, response.getInteger(STATUS));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("revokeToken [Failed-02 no roles]")
  void revokeTokenFailed02(VertxTestContext testContext) {

    mockHttpWebClient.setResponse("valid");

    JsonObject request = new JsonObject().put(RS_URL, DUMMY_SERVER);
    tokenService
        .revokeToken(mapToRevToken(request), normalUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_MISSING_INFO.toString(), response.getString(TYPE));
                          assertEquals(ERR_TITLE_NO_APPROVED_ROLES, response.getString("title"));
                          assertEquals(ERR_DETAIL_NO_APPROVED_ROLES, response.getString("detail"));
                          assertEquals(404, response.getInteger("status"));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("revokeToken [Failed-04 invalidUrl]")
  void revokeTokenFailed04(VertxTestContext testContext) {
    User consumerUser = new User(normalUser.toJson());
    consumerUser.setRoles(List.of(Roles.CONSUMER));
    consumerUser.setRolesToRsMapping(
        Map.of(Roles.CONSUMER.toString(), new JsonArray().add(DUMMY_SERVER)));

    JsonObject request =
        new JsonObject().put(RS_URL, RandomStringUtils.randomAlphabetic(10) + ".com");

    mockHttpWebClient.setResponse("valid");
    tokenService
        .revokeToken(mapToRevToken(request), consumerUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_INVALID_INPUT.toString(), response.getString(TYPE));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("revokeToken [Failed-04 invalidUrl]")
  void revokeTokenFailedApdInactive(VertxTestContext testContext) {
    User consumerUser = new User(normalUser.toJson());
    consumerUser.setRoles(List.of(Roles.CONSUMER));
    consumerUser.setRolesToRsMapping(
        Map.of(Roles.CONSUMER.toString(), new JsonArray().add(DUMMY_SERVER)));

    JsonObject request = new JsonObject().put(RS_URL, DUMMY_INACTIVE_APD);

    mockHttpWebClient.setResponse("valid");
    tokenService
        .revokeToken(mapToRevToken(request), consumerUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_INVALID_INPUT.toString(), response.getString(TYPE));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("revokeToken [Failed-05 authUrl]")
  void revokeTokenFailed05(VertxTestContext testContext) {
    User consumerUser = new User(normalUser.toJson());
    consumerUser.setRoles(List.of(Roles.CONSUMER));
    consumerUser.setRolesToRsMapping(
        Map.of(Roles.CONSUMER.toString(), new JsonArray().add(DUMMY_SERVER)));

    JsonObject request = new JsonObject().put(RS_URL, DUMMY_COS_URL);

    mockHttpWebClient.setResponse("valid");
    tokenService
        .revokeToken(mapToRevToken(request), consumerUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_INVALID_INPUT.toString(), response.getString(TYPE));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("validateToken [Success]")
  void validateTokenSuccess(VertxTestContext testContext) {
    JsonObject tokenRequest =
        new JsonObject()
            .put(ITEM_TYPE, "resource_group")
            .put(ITEM_ID, RESOURCE_GROUP)
            .put(USER_ID, normalUser.getUserId())
            .put(URL, DUMMY_SERVER)
            .put(ROLE, Roles.CONSUMER.toString().toLowerCase());
    JsonObject token = tokenServiceImplObj.getJwt(tokenRequest);
    token.remove("expiry");
    token.remove("server");

    tokenService
        .validateToken(mapToInspctToken(token))
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
                          JsonObject payload = response.getJsonObject("results");
                          assertEquals(payload.getString(SUB), normalUser.getUserId());
                          assertEquals(payload.getString(ISS), DUMMY_COS_URL);
                          assertEquals(payload.getString(SUB), normalUser.getUserId());
                          assertEquals(payload.getString(AUD), DUMMY_SERVER);
                          assertEquals(payload.getString(IID), "rg:" + RESOURCE_GROUP);
                          assertEquals(
                              payload.getString(ROLE), Roles.CONSUMER.toString().toLowerCase());
                          assertTrue(payload.getJsonObject(CONS).isEmpty());
                          assertNotNull(payload.getString(EXP));

                          assertTrue(!payload.containsKey(INTROSPECT_USERINFO));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("validateToken resource server token and user info present [Success]")
  void validateResourceServerTokenSuccess(VertxTestContext testContext) {
    JsonObject userDetailsResp =
        new JsonObject().put(normalUser.getUserId(), utils.getKcAdminJson(normalUser));
    mockRegistrationFactory.setResponse(userDetailsResp);

    JsonObject tokenRequest =
        new JsonObject()
            .put(ITEM_TYPE, RESOURCE_SVR)
            .put(ITEM_ID, DUMMY_SERVER)
            .put(USER_ID, normalUser.getUserId())
            .put(URL, DUMMY_SERVER)
            .put(ROLE, Roles.CONSUMER.toString().toLowerCase());
    JsonObject token = tokenServiceImplObj.getJwt(tokenRequest);
    token.remove("expiry");
    token.remove("server");

    tokenService
        .validateToken(mapToInspctToken(token))
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
                          JsonObject payload = response.getJsonObject("results");
                          assertEquals(payload.getString(SUB), normalUser.getUserId());
                          assertEquals(payload.getString(ISS), DUMMY_COS_URL);
                          assertEquals(payload.getString(SUB), normalUser.getUserId());
                          assertEquals(payload.getString(AUD), DUMMY_SERVER);
                          assertEquals(payload.getString(IID), "rs:" + DUMMY_SERVER);
                          assertEquals(
                              payload.getString(ROLE), Roles.CONSUMER.toString().toLowerCase());
                          assertTrue(payload.getJsonObject(CONS).isEmpty());
                          assertNotNull(payload.getString(EXP));

                          assertTrue(
                              payload.getJsonObject(INTROSPECT_USERINFO).containsKey("email"));
                          assertTrue(
                              payload.getJsonObject(INTROSPECT_USERINFO).containsKey("name"));
                          testContext.completeNow();
                        })));
  }

  @DisplayName("validateToken resource server token - registration service fails [Fail]")
  void validateResourceServerTokenRegServiceFails(VertxTestContext testContext) {

    mockRegistrationFactory.setResponse("invalid");

    JsonObject tokenRequest =
        new JsonObject()
            .put(ITEM_TYPE, RESOURCE_SVR)
            .put(ITEM_ID, DUMMY_SERVER)
            .put(USER_ID, normalUser.getUserId())
            .put(URL, DUMMY_SERVER)
            .put(ROLE, Roles.CONSUMER.toString().toLowerCase());
    JsonObject token = tokenServiceImplObj.getJwt(tokenRequest);
    token.remove("expiry");
    token.remove("server");

    tokenService
        .validateToken(mapToInspctToken(token))
        .onComplete(
            testContext.failing(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals("Internal error", response.getMessage());
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("validateToken [Failed-02 invalidToken]")
  void validateTokenFailed02(VertxTestContext testContext) {

    JsonObject tokenRequest =
        new JsonObject()
            .put(ITEM_TYPE, "resourceGroup")
            .put(ITEM_ID, RESOURCE_GROUP)
            .put(USER_ID, normalUser.getUserId())
            .put(URL, DUMMY_SERVER)
            .put(ROLE, Roles.CONSUMER.toString().toLowerCase());
    JsonObject token = tokenServiceImplObj.getJwt(tokenRequest);

    /* add extra data to token */
    JsonObject invalidToken =
        new JsonObject().put("accessToken", token.getString("accessToken") + "abc");

    tokenService
        .validateToken(mapToInspctToken(invalidToken))
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_INVALID_AUTH_TOKEN.toString(), response.getString(TYPE));
                          assertTrue(
                              response
                                  .getJsonArray("results")
                                  .getJsonObject(0)
                                  .getString(STATUS)
                                  .equals(DENY));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("validateToken [Failed-03 expiredToken]")
  void validateTokenFailed03(VertxTestContext testContext) {

    tokenService
        .validateToken(mapToInspctToken(expiredTipPayload))
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_INVALID_AUTH_TOKEN.toString(), response.getString(TYPE));
                          assertTrue(
                              response
                                  .getJsonArray("results")
                                  .getJsonObject(0)
                                  .getString(STATUS)
                                  .equals(DENY));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("validateToken [Failed-04 missingToken]")
  void validateTokenFailed04(VertxTestContext testContext) {

    IntrospectToken introspect = new IntrospectToken();

    tokenService
        .validateToken(introspect)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_MISSING_INFO.toString(), response.getString("type"));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("validateToken [Failed-05 randomToken]")
  void validateTokenFailed05(VertxTestContext testContext) {

    tokenService
        .validateToken(mapToInspctToken(randomToken))
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_INVALID_AUTH_TOKEN.toString(), response.getString(TYPE));
                          assertTrue(
                              response
                                  .getJsonArray("results")
                                  .getJsonObject(0)
                                  .getString(STATUS)
                                  .equals(DENY));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("validateToken success [APD token]")
  void validateTokenFailed06(VertxTestContext testContext) {

    String sessId = UUID.randomUUID().toString();
    JsonObject apdTokenRequest =
        new JsonObject()
            .put(URL, DUMMY_SERVER)
            .put(SESSION_ID, sessId)
            .put(USER_ID, normalUser.getUserId())
            .put(LINK, DUMMY_SERVER + "/apd");
    JsonObject token = tokenServiceImplObj.getApdJwt(apdTokenRequest);
    token.remove("expiry");
    token.remove("server");
    token.remove("link");

    /* The JWT response has the key `apdToken`, introspect expects `accessToken`*/
    token.put(ACCESS_TOKEN, token.remove(APD_TOKEN));

    tokenService
        .validateToken(mapToInspctToken(token))
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
                          JsonObject payload = response.getJsonObject("results");
                          assertEquals(payload.getString(SUB), normalUser.getUserId());
                          assertEquals(payload.getString(ISS), DUMMY_COS_URL);
                          assertEquals(payload.getString(SUB), normalUser.getUserId());
                          assertEquals(payload.getString(AUD), DUMMY_SERVER);
                          assertEquals(payload.getString(SID), sessId);
                          assertEquals(payload.getString(LINK), DUMMY_SERVER + "/apd");
                          assertNotNull(payload.getString(EXP));

                          assertTrue(!payload.containsKey(CONS));
                          assertTrue(!payload.containsKey(ROLE));
                          assertTrue(!payload.containsKey(IID));
                          assertTrue(!payload.containsKey(INTROSPECT_USERINFO));

                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("validateToken success [special admin token]")
  void validateSpecialAdminToken(VertxTestContext testContext) {

    JsonObject adminTokenReq =
        new JsonObject()
            .put(USER_ID, CLAIM_ISSUER)
            .put(URL, DUMMY_SERVER)
            .put(ROLE, "")
            .put(ITEM_TYPE, "")
            .put(ITEM_ID, "");
    JsonObject token = tokenServiceImplObj.getJwt(adminTokenReq);
    token.remove("expiry");
    token.remove("server");

    tokenService
        .validateToken(mapToInspctToken(token))
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
                          JsonObject payload = response.getJsonObject("results");
                          assertEquals(payload.getString(SUB), DUMMY_COS_URL);
                          assertEquals(payload.getString(ISS), DUMMY_COS_URL);
                          assertEquals(payload.getString(AUD), DUMMY_SERVER);
                          assertEquals(payload.getString(IID), "null:");
                          assertEquals(payload.getString(ROLE), "");
                          assertTrue(payload.getJsonObject(CONS).isEmpty());
                          assertNotNull(payload.getString(EXP));
                          assertTrue(!payload.containsKey(INTROSPECT_USERINFO));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Test Auth Server Token flow")
  void authServerToken(VertxTestContext testContext) {

    tokenService
        .getAuthServerToken(DUMMY_SERVER)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          JsonObject payload = getJwtPayload(response.getString(ACCESS_TOKEN));
                          assertEquals(payload.getString(SUB), DUMMY_COS_URL);
                          assertEquals(payload.getString(ISS), DUMMY_COS_URL);
                          assertEquals(payload.getString(AUD), DUMMY_SERVER);
                          assertNotNull(payload.getString(EXP));
                          testContext.completeNow();
                        })));
  }
}
