package iudx.aaa.server.token;

import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_AUTH_TOKEN;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_INPUT;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_ROLE;
import static iudx.aaa.server.apiserver.util.Urn.URN_MISSING_INFO;
import static iudx.aaa.server.apiserver.util.Urn.URN_SUCCESS;
import static iudx.aaa.server.token.Constants.ACCESS_TOKEN;
import static iudx.aaa.server.token.Constants.APD_TOKEN;
import static iudx.aaa.server.token.Constants.AUD;
import static iudx.aaa.server.token.Constants.CLAIM_ISSUER;
import static iudx.aaa.server.token.Constants.CONS;
import static iudx.aaa.server.token.Constants.DENY;
import static iudx.aaa.server.token.Constants.ERR_DETAIL_APD_INTERACT_REQUIRED;
import static iudx.aaa.server.token.Constants.ERR_TITLE_APD_INTERACT_REQUIRED;
import static iudx.aaa.server.token.Constants.EXP;
import static iudx.aaa.server.token.Constants.IID;
import static iudx.aaa.server.token.Constants.ISS;
import static iudx.aaa.server.token.Constants.ITEM_ID;
import static iudx.aaa.server.token.Constants.ITEM_TYPE;
import static iudx.aaa.server.token.Constants.LINK;
import static iudx.aaa.server.token.Constants.PG_CONNECTION_TIMEOUT;
import static iudx.aaa.server.token.Constants.RESOURCE_SVR;
import static iudx.aaa.server.token.Constants.ROLE;
import static iudx.aaa.server.token.Constants.RS_URL;
import static iudx.aaa.server.token.Constants.SID;
import static iudx.aaa.server.token.Constants.STATUS;
import static iudx.aaa.server.token.Constants.SUB;
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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collector;
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
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.KeyStoreOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.IntrospectToken;
import iudx.aaa.server.apiserver.RequestToken;
import iudx.aaa.server.apiserver.RoleStatus;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.policy.PolicyService;
import iudx.aaa.server.registration.Utils;

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
  private static MockPolicyFactory mockPolicy;
  private static TokenRevokeService httpWebClient;
  private static MockHttpWebClient mockHttpWebClient;

  private static final String DUMMY_AUTH_SERVER =
      "auth" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + "iudx.io";

  private static final String DUMMY_SERVER =
      "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

  static String RESOURCE_GROUP =
      "iisc.ac.in/da39a3ee5e6b4b0d3255bfef95601890afd80709/" + DUMMY_SERVER + "/resourcegroup";
  static String RESOURCE_ITEM = RESOURCE_GROUP + "/resource";

  public static final String NIL_UUID = "00000000-0000-0000-0000-000000000000";
  static String name = RandomStringUtils.randomAlphabetic(10).toLowerCase();
  static String url = name + ".com";
  static Promise<UUID> orgId;

  static Future<JsonObject> delegate;
  static Future<JsonObject> providerAdmin;
  static Future<JsonObject> consumer;

  static Future<UUID> orgIdFut;

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
    String issuer = DUMMY_AUTH_SERVER;

    if (issuer != null && !issuer.isBlank()) {
      CLAIM_ISSUER = issuer;
    } else {
      LOGGER.fatal("Fail: authServerDomain not set");
      throw new IllegalStateException("authServerDomain not set");
    }

    /* Set Connection Object and schema */
    if (connectOptions == null) {
      Map<String, String> schemaProp = Map.of("search_path", databaseSchema);

      connectOptions = new PgConnectOptions().setPort(databasePort).setHost(databaseIP)
          .setDatabase(databaseName).setUser(databaseUserName).setPassword(databasePassword)
          .setConnectTimeout(PG_CONNECTION_TIMEOUT).setProperties(schemaProp);
    }

    /* Pool options */
    if (poolOptions == null) {
      poolOptions = new PoolOptions().setMaxSize(poolSize);
    }

    /* Initializing the services */
    provider = jwtInitConfig(vertx);
    pgPool = PgPool.pool(vertx, connectOptions, poolOptions);
    // httpWebClient = new HttpWebClient(vertx, keycloakOptions);

    orgIdFut = pgPool.withConnection(conn -> conn.preparedQuery(Utils.SQL_CREATE_ORG)
        .execute(Tuple.of(name, url)).map(row -> row.iterator().next().getUUID("id")));

    providerAdmin = orgIdFut.compose(id -> Utils.createFakeUser(pgPool, id.toString(), url,
        Map.of(Roles.PROVIDER, RoleStatus.APPROVED, Roles.ADMIN, RoleStatus.APPROVED), true));

    delegate = orgIdFut.compose(id -> Utils.createFakeUser(pgPool, id.toString(), url,
        Map.of(Roles.DELEGATE, RoleStatus.APPROVED), true));

    consumer = orgIdFut.compose(id -> Utils.createFakeUser(pgPool, NIL_UUID, "",
        Map.of(Roles.CONSUMER, RoleStatus.APPROVED), true));

    /*
     * 1. create organization 2. create 3 users 3. create 1 resource server with providerAdmin as
     * admin 4. create 3 delegations, one for provider -> delegate on authsrv, one for other server
     * to delegate, and a deleted delegation 5. AS provider, must view 2 delegations 6. AS delegate
     * must view 2 delegations 7. AS auth delegate, must view one delegation 8. A consumer must not
     * be able to call the API at all
     */

    CompositeFuture.all(orgIdFut, providerAdmin, delegate, consumer).compose(res -> {

      UUID apId = UUID.fromString(providerAdmin.result().getString("userId"));
      UUID deleId = UUID.fromString(delegate.result().getString("userId"));

      List<Tuple> servers = List.of(Tuple.of("Other Server", apId, DUMMY_SERVER));
      Tuple getServId = Tuple.of(List.of(DUMMY_AUTH_SERVER, DUMMY_SERVER).toArray());

      Collector<Row, ?, Map<String, UUID>> serverIds =
          Collectors.toMap(row -> row.getString("url"), row -> row.getUUID("id"));

      return pgPool.withConnection(
          conn -> conn.preparedQuery(Utils.SQL_CREATE_ADMIN_SERVER).executeBatch(servers));
      /*
       * .compose(succ -> conn.preparedQuery(SQL_GET_SERVER_IDS)
       * .collecting(serverIds).execute(getServId).map(r -> r.value())) .map(i -> {
       * 
       * return List.of(Tuple.of(apId, deleId, i.get(DUMMY_SERVER), status.ACTIVE.toString()),
       * Tuple.of(apId, deleId, i.get(DUMMY_SERVER), status.DELETED.toString()), Tuple.of(apId,
       * deleId, i.get(AUTH_SERVER_URL), status.ACTIVE.toString()));
       */
      // }).compose(j -> conn.preparedQuery(SQL_CREATE_DELEG).executeBatch(j)));

    }).onSuccess(r -> {

      mockPolicy = new MockPolicyFactory();
      mockHttpWebClient = new MockHttpWebClient();
      httpWebClient = mockHttpWebClient.getMockHttpWebClient();

      policyService = mockPolicy.getInstance();
      tokenServiceImplObj = new TokenServiceImpl(pgPool, policyService, provider, httpWebClient);
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
    Tuple servers = Tuple.of(List.of(DUMMY_SERVER, DUMMY_AUTH_SERVER).toArray());
    List<JsonObject> users = List.of(providerAdmin.result(), delegate.result(), consumer.result());

    pgPool
        .withConnection(conn -> conn.preparedQuery(Utils.SQL_DELETE_SERVERS).execute(servers)
            .compose(success -> Utils.deleteFakeUser(pgPool, users)).compose(succ -> conn
                .preparedQuery(Utils.SQL_DELETE_ORG).execute(Tuple.of(orgIdFut.result()))))
        .onComplete(x -> {
          if (x.failed()) {
            LOGGER.warn(x.cause().getMessage());
          }
          vertxObj.close(testContext.succeeding(response -> testContext.completeNow()));
        });
  }

  @Test
  @DisplayName("create token as consumer (resource group) [Success]")
  void createTokenRsGrpSuccess(VertxTestContext testContext) {

    JsonObject userJson = consumer.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.CONSUMER)).build();

    JsonObject jsonReq = new JsonObject().put("itemId", RESOURCE_GROUP)
        .put("itemType", "resource_group").put("role", "consumer");
    RequestToken request = new RequestToken(jsonReq);

    mockPolicy.setResponse("valid", RESOURCE_GROUP, DUMMY_SERVER);
    tokenService.createToken(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_SUCCESS.toString(), response.getString("type"));
          JsonObject payload =
              getJwtPayload(response.getJsonObject("results").getString(ACCESS_TOKEN));
          assertEquals(payload.getString(ISS), DUMMY_AUTH_SERVER);
          assertEquals(payload.getString(AUD), DUMMY_SERVER);
          assertEquals(payload.getString(IID), "rg:" + RESOURCE_GROUP);
          assertEquals(payload.getString(ROLE), Roles.CONSUMER.toString().toLowerCase());
          assertFalse(payload.getJsonObject(CONS).isEmpty());
          assertNotNull(payload.getString(EXP));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("create token as consumer (resource item) [Success]")
  void createTokenResItemSuccess(VertxTestContext testContext) {

    JsonObject userJson = consumer.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.CONSUMER)).build();

    JsonObject jsonReq = new JsonObject().put("itemId", RESOURCE_ITEM).put("itemType", "resource")
        .put("role", "consumer");
    RequestToken request = new RequestToken(jsonReq);

    mockPolicy.setResponse("valid", RESOURCE_ITEM, DUMMY_SERVER);
    tokenService.createToken(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_SUCCESS.toString(), response.getString("type"));
          JsonObject payload =
              getJwtPayload(response.getJsonObject("results").getString(ACCESS_TOKEN));
          assertEquals(payload.getString(ISS), DUMMY_AUTH_SERVER);
          assertEquals(payload.getString(AUD), DUMMY_SERVER);
          assertEquals(payload.getString(IID), "ri:" + RESOURCE_ITEM);
          assertEquals(payload.getString(ROLE), Roles.CONSUMER.toString().toLowerCase());
          assertFalse(payload.getJsonObject(CONS).isEmpty());
          assertNotNull(payload.getString(EXP));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Get APD token as consumer [Success]")
  void getApdTokenConsumerSuccess(VertxTestContext testContext) {

    JsonObject userJson = consumer.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.CONSUMER)).build();

    JsonObject jsonReq = new JsonObject().put("itemId", RESOURCE_ITEM).put("itemType", "resource")
        .put("role", "consumer");
    RequestToken request = new RequestToken(jsonReq);

    mockPolicy.setResponse("apd-interaction", DUMMY_SERVER + "/apd-interact", DUMMY_SERVER);
    tokenService.createToken(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_MISSING_INFO.toString(), response.getString("type"));
          assertEquals(ERR_TITLE_APD_INTERACT_REQUIRED.toString(), response.getString("title"));
          assertEquals(ERR_DETAIL_APD_INTERACT_REQUIRED.toString(), response.getString("detail"));

          JsonObject apdToken =
              getJwtPayload(response.getJsonObject("context").getString(APD_TOKEN));
          assertEquals(apdToken.getString(ISS), DUMMY_AUTH_SERVER);
          assertEquals(apdToken.getString(AUD), DUMMY_SERVER);
          assertTrue(apdToken.containsKey(SID));
          assertEquals(apdToken.getString(LINK), DUMMY_SERVER + "/apd-interact");
          assertNotNull(apdToken.getString(EXP));
          assertEquals(response.getJsonObject("context").getString(LINK),
              DUMMY_SERVER + "/apd-interact");
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("create token as consumer (resource server) [Success]")
  void createTokenResServSuccess(VertxTestContext testContext) {

    JsonObject userJson = consumer.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.CONSUMER)).build();

    JsonObject jsonReq = new JsonObject().put("itemId", DUMMY_SERVER)
        .put("itemType", "resource_server").put("role", "consumer");
    RequestToken request = new RequestToken(jsonReq);

    tokenService.createToken(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_SUCCESS.toString(), response.getString("type"));
          JsonObject payload =
              getJwtPayload(response.getJsonObject("results").getString(ACCESS_TOKEN));
          assertEquals(payload.getString(ISS), DUMMY_AUTH_SERVER);
          assertEquals(payload.getString(AUD), DUMMY_SERVER);
          assertEquals(payload.getString(IID), "rs:" + DUMMY_SERVER);
          assertEquals(payload.getString(ROLE), Roles.CONSUMER.toString().toLowerCase());
          assertTrue(payload.getJsonObject(CONS).isEmpty());
          assertNotNull(payload.getString(EXP));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("create token as admin for resource server [Success]")
  void createTokenAdminSuccess(VertxTestContext testContext) {

    JsonObject userJson = providerAdmin.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.ADMIN, Roles.PROVIDER)).build();

    JsonObject jsonReq = new JsonObject().put("itemId", DUMMY_SERVER)
        .put("itemType", "resource_server").put("role", "admin");
    RequestToken request = new RequestToken(jsonReq);

    mockPolicy.setResponse("valid");
    tokenService.createToken(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_SUCCESS.toString(), response.getString("type"));
          JsonObject payload =
              getJwtPayload(response.getJsonObject("results").getString(ACCESS_TOKEN));
          assertEquals(payload.getString(ISS), DUMMY_AUTH_SERVER);
          assertEquals(payload.getString(AUD), DUMMY_SERVER);
          assertEquals(payload.getString(IID), "rs:" + DUMMY_SERVER);
          assertEquals(payload.getString(ROLE), Roles.ADMIN.toString().toLowerCase());
          assertTrue(payload.getJsonObject(CONS).isEmpty());
          assertNotNull(payload.getString(EXP));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("create token as admin for not found resource server [Fail]")
  void createTokenAdminResServFail(VertxTestContext testContext) {

    JsonObject userJson = providerAdmin.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.ADMIN, Roles.PROVIDER)).build();

    JsonObject jsonReq = new JsonObject().put("itemId", "abc.123.com")
        .put("itemType", "resource_server").put("role", "admin");
    RequestToken request = new RequestToken(jsonReq);

    mockPolicy.setResponse("valid");
    tokenService.createToken(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("create token as FAKE admin for resource server [Fail]")
  void createTokenConsumerResServFail(VertxTestContext testContext) {

    JsonObject userJson = consumer.result();

    /*
     * We *artificially* add the admin role to the consumer user when creating the user object
     */
    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.ADMIN)).build();

    JsonObject jsonReq = new JsonObject().put("itemId", DUMMY_SERVER)
        .put("itemType", "resource_server").put("role", "admin");
    RequestToken request = new RequestToken(jsonReq);

    mockPolicy.setResponse("valid");
    tokenService.createToken(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("createToken invalid policy [Fail]")
  void createTokenFailedInvalidPolicy(VertxTestContext testContext) {

    JsonObject userJson = consumer.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.CONSUMER)).build();

    JsonObject jsonReq = new JsonObject().put("itemId", RESOURCE_ITEM).put("itemType", "resource")
        .put("role", "consumer");
    RequestToken request = new RequestToken(jsonReq);

    mockPolicy.setResponse("invalid");
    tokenService.createToken(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_INVALID_INPUT.toString(), response.getString(TYPE));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("createToken no user profile [Fail]")
  void createTokenNoUserProfile(VertxTestContext testContext) {

    JsonObject userJson = consumer.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId")).userId(NIL_UUID)
        .name(userJson.getString("firstName"), userJson.getString("lastName")).roles(List.of())
        .build();

    JsonObject jsonReq = new JsonObject().put("itemId", RESOURCE_ITEM).put("itemType", "resource")
        .put("role", "consumer");
    RequestToken request = new RequestToken(jsonReq);

    tokenService.createToken(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_MISSING_INFO.toString(), response.getString(TYPE));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("createToken user does not have requested role [Fail]")
  void createTokenUserNotHaveRequestedRole(VertxTestContext testContext) {

    JsonObject userJson = consumer.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.CONSUMER)).build();

    JsonObject jsonReq = new JsonObject().put("itemId", RESOURCE_ITEM).put("itemType", "resource")
        .put("role", "delegate");

    RequestToken request = new RequestToken(jsonReq);
    mockPolicy.setResponse("valid");
    tokenService.createToken(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_INVALID_ROLE.toString(), response.getString(TYPE));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("revokeToken [Success]")
  void revokeTokenSuccess(VertxTestContext testContext) {
    JsonObject userJson = consumer.result();
    
    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.CONSUMER)).build();

    JsonObject request = new JsonObject().put(RS_URL, DUMMY_SERVER);
    
    mockHttpWebClient.setResponse("valid");
    tokenService.revokeToken(mapToRevToken(request), user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("revokeToken [Failed-01 Failure in RS]")
  void revokeTokenFailed01(VertxTestContext testContext) {

    JsonObject userJson = consumer.result();
    
    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.CONSUMER)).build();

    JsonObject request = new JsonObject().put(RS_URL, DUMMY_SERVER);
    mockHttpWebClient.setResponse("invalid");
    tokenService.revokeToken(mapToRevToken(request), user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_INVALID_INPUT.toString(), response.getString(TYPE));
          assertEquals(400, response.getInteger(STATUS));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("revokeToken [Failed-02 no roles]")
  void revokeTokenFailed02(VertxTestContext testContext) {

    mockHttpWebClient.setResponse("valid");
    
    JsonObject userJson = consumer.result();
    
    /* We purposefully add no roles to the user object */
    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of()).build();

    JsonObject request = new JsonObject().put(RS_URL, DUMMY_SERVER);
    tokenService.revokeToken(mapToRevToken(request), user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_INVALID_ROLE.toString(), response.getString(TYPE));
          assertEquals(400, response.getInteger(STATUS));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("revokeToken [Failed-03 nilUUID userId]")
  void revokeTokenFailed03(VertxTestContext testContext) {

    JsonObject userJson = consumer.result();
    
    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(NIL_UUID.toString())
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.CONSUMER)).build();

    JsonObject request = new JsonObject().put(RS_URL, DUMMY_SERVER);

    mockHttpWebClient.setResponse("valid");
    tokenService.revokeToken(mapToRevToken(request), user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_MISSING_INFO.toString(), response.getString(TYPE));
          assertEquals(404, response.getInteger(STATUS));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("revokeToken [Failed-04 invalidUrl]")
  void revokeTokenFailed04(VertxTestContext testContext) {
    JsonObject userJson = consumer.result();
    
    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.CONSUMER)).build();

    JsonObject request = new JsonObject().put(RS_URL, "abcd.com");

    mockHttpWebClient.setResponse("valid");
    tokenService.revokeToken(mapToRevToken(request), user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_INVALID_INPUT.toString(), response.getString(TYPE));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("revokeToken [Failed-05 authUrl]")
  void revokeTokenFailed05(VertxTestContext testContext) {
    JsonObject userJson = consumer.result();
    
    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.CONSUMER)).build();

    JsonObject request = new JsonObject().put(RS_URL, DUMMY_AUTH_SERVER);

    mockHttpWebClient.setResponse("valid");
    tokenService.revokeToken(mapToRevToken(request), user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_INVALID_INPUT.toString(), response.getString(TYPE));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("validateToken [Success]")
  void validateTokenSuccess(VertxTestContext testContext) {
    JsonObject tokenRequest = new JsonObject().put(ITEM_TYPE, "resource_group")
        .put(ITEM_ID, RESOURCE_GROUP).put(USER_ID, consumer.result().getString("userId"))
        .put(URL, DUMMY_SERVER).put(ROLE, Roles.CONSUMER.toString().toLowerCase());
    JsonObject token = tokenServiceImplObj.getJwt(tokenRequest);
    token.remove("expiry");
    token.remove("server");

    mockPolicy.setResponse("valid");
    tokenService.validateToken(mapToInspctToken(token),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
          JsonObject payload = response.getJsonObject("results");
          assertEquals(payload.getString(ISS), DUMMY_AUTH_SERVER);
          assertEquals(payload.getString(SUB), consumer.result().getString("userId"));
          assertEquals(payload.getString(AUD), DUMMY_SERVER);
          assertEquals(payload.getString(IID), "rg:" + RESOURCE_GROUP);
          assertEquals(payload.getString(ROLE), Roles.CONSUMER.toString().toLowerCase());
          assertTrue(payload.getJsonObject(CONS).isEmpty());
          assertNotNull(payload.getString(EXP));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("validateToken resource server token [Success]")
  void validateResourceServerTokenSuccess(VertxTestContext testContext) {
    JsonObject tokenRequest = new JsonObject().put(ITEM_TYPE, RESOURCE_SVR)
        .put(ITEM_ID, DUMMY_SERVER).put(USER_ID, consumer.result().getString("userId"))
        .put(URL, DUMMY_SERVER).put(ROLE, Roles.CONSUMER.toString().toLowerCase());
    JsonObject token = tokenServiceImplObj.getJwt(tokenRequest);
    token.remove("expiry");
    token.remove("server");

    mockPolicy.setResponse("valid");
    tokenService.validateToken(mapToInspctToken(token),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
          JsonObject payload = response.getJsonObject("results");
          assertEquals(payload.getString(ISS), DUMMY_AUTH_SERVER);
          assertEquals(payload.getString(SUB), consumer.result().getString("userId"));
          assertEquals(payload.getString(AUD), DUMMY_SERVER);
          assertEquals(payload.getString(IID), "rs:" + DUMMY_SERVER);
          assertEquals(payload.getString(ROLE), Roles.CONSUMER.toString().toLowerCase());
          assertTrue(payload.getJsonObject(CONS).isEmpty());
          assertNotNull(payload.getString(EXP));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("validateToken [Failed-01 invalidPolicy]")
  void validateTokenFailed01(VertxTestContext testContext) {

    JsonObject tokenRequest = new JsonObject().put(ITEM_TYPE, "resourceGroup")
        .put(ITEM_ID, RESOURCE_GROUP).put(USER_ID, consumer.result().getString("userId"))
        .put(URL, DUMMY_SERVER).put(ROLE, Roles.CONSUMER.toString().toLowerCase());
    JsonObject token = tokenServiceImplObj.getJwt(tokenRequest);
    token.remove("expiry");
    token.remove("server");

    mockPolicy.setResponse("invalid");
    tokenService.validateToken(mapToInspctToken(token),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_INVALID_INPUT.toString(), response.getString(TYPE));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("validateToken [Failed-02 invalidToken]")
  void validateTokenFailed02(VertxTestContext testContext) {

    JsonObject tokenRequest = new JsonObject().put(ITEM_TYPE, "resourceGroup")
        .put(ITEM_ID, RESOURCE_GROUP).put(USER_ID, consumer.result().getString("userId"))
        .put(URL, DUMMY_SERVER).put(ROLE, Roles.CONSUMER.toString().toLowerCase());
    JsonObject token = tokenServiceImplObj.getJwt(tokenRequest);

    /* add extra data to token */
    JsonObject invalidToken =
        new JsonObject().put("accessToken", token.getString("accessToken") + "abc");

    tokenService.validateToken(mapToInspctToken(invalidToken),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_INVALID_AUTH_TOKEN.toString(), response.getString(TYPE));
          assertTrue(
              response.getJsonArray("results").getJsonObject(0).getString(STATUS).equals(DENY));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("validateToken [Failed-03 expiredToken]")
  void validateTokenFailed03(VertxTestContext testContext) {

    tokenService.validateToken(mapToInspctToken(expiredTipPayload),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_INVALID_AUTH_TOKEN.toString(), response.getString(TYPE));
          assertTrue(
              response.getJsonArray("results").getJsonObject(0).getString(STATUS).equals(DENY));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("validateToken [Failed-04 missingToken]")
  void validateTokenFailed04(VertxTestContext testContext) {

    IntrospectToken introspect = new IntrospectToken();

    tokenService.validateToken(introspect,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_MISSING_INFO.toString(), response.getString("type"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("validateToken [Failed-05 randomToken]")
  void validateTokenFailed05(VertxTestContext testContext) {

    tokenService.validateToken(mapToInspctToken(randomToken),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_INVALID_AUTH_TOKEN.toString(), response.getString(TYPE));
          assertTrue(
              response.getJsonArray("results").getJsonObject(0).getString(STATUS).equals(DENY));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("validateToken [Failed-06 user does not exist - should never happen]")
  void validateTokenFailed06(VertxTestContext testContext) {

    JsonObject tokenRequest = new JsonObject().put(Constants.ITEM_TYPE, "resourceGroup")
        .put(Constants.ITEM_ID, RESOURCE_GROUP).put(Constants.USER_ID, UUID.randomUUID().toString())
        .put(Constants.ROLE, Roles.CONSUMER.toString().toLowerCase());
    JsonObject token = tokenServiceImplObj.getJwt(tokenRequest);
    token.remove("expiry");
    token.remove("server");

    tokenService.validateToken(mapToInspctToken(token),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_INVALID_AUTH_TOKEN.toString(), response.getString(TYPE));
          testContext.completeNow();
        })));
  }
  @Test
  @DisplayName("validateToken success [special admin token]")
  void validateSpecialAdminToken(VertxTestContext testContext) {

        JsonObject adminTokenReq = new JsonObject().put(USER_ID, CLAIM_ISSUER).put(URL, DUMMY_SERVER)
            .put(ROLE, "").put(ITEM_TYPE, "").put(ITEM_ID, "");
    JsonObject token = tokenServiceImplObj.getJwt(adminTokenReq);
    token.remove("expiry");
    token.remove("server");

    tokenService.validateToken(mapToInspctToken(token),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
          JsonObject payload = response.getJsonObject("results");
          assertEquals(payload.getString(ISS), DUMMY_AUTH_SERVER);
          assertEquals(payload.getString(SUB), DUMMY_AUTH_SERVER);
          assertEquals(payload.getString(AUD), DUMMY_SERVER);
          assertEquals(payload.getString(IID), "null:");
          assertEquals(payload.getString(ROLE), "");
          assertTrue(payload.getJsonObject(CONS).isEmpty());
          assertNotNull(payload.getString(EXP));
          testContext.completeNow();
        })));
  }
}
