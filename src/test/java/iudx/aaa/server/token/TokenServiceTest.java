package iudx.aaa.server.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.KeyStoreOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import iudx.aaa.server.registration.Utils;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.policy.MockRegistrationFactory;
import iudx.aaa.server.policy.PolicyService;
import org.mockito.junit.jupiter.MockitoExtension;
import static iudx.aaa.server.token.RequestPayload.*;
import static iudx.aaa.server.token.Constants.*;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
public class TokenServiceTest {
  private static Logger LOGGER = LogManager.getLogger(TokenServiceTest.class);

  private static Configuration config;

  /* Database Properties */
  private static String databaseIP;
  private static int databasePort;
  private static String databaseName;
  private static String databaseUserName;
  private static String databasePassword;
  private static int poolSize;
  private static PoolOptions poolOptions;
  private static PgConnectOptions connectOptions;
  private static PgPool pgPool;
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
    databaseUserName = dbConfig.getString("databaseUserName");
    databasePassword = dbConfig.getString("databasePassword");
    poolSize = Integer.parseInt(dbConfig.getString("poolSize"));

    keystorePath = dbConfig.getString("keystorePath");
    keystorePassword = dbConfig.getString("keystorePassword");
    String issuer = dbConfig.getString("authServerDomain", "");

    if (issuer != null && !issuer.isBlank()) {
      CLAIM_ISSUER = issuer;
    } else {
      LOGGER.fatal("Fail: authServerDomain not set");
      throw new IllegalStateException("authServerDomain not set");
    }

    /* Set Connection Object */
    if (connectOptions == null) {
      connectOptions = new PgConnectOptions().setPort(databasePort).setHost(databaseIP)
          .setDatabase(databaseName).setUser(databaseUserName).setPassword(databasePassword)
          .setConnectTimeout(PG_CONNECTION_TIMEOUT);
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
      tokenService = new TokenServiceImpl(pgPool, policyService, provider, httpWebClient);

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

    mockPolicy.setResponse("valid");
    tokenService.createToken(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_SUCCESS, response.getString("type"));
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

    mockPolicy.setResponse("valid");
    tokenService.createToken(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_SUCCESS, response.getString("type"));
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

    JsonObject jsonReq = new JsonObject().put("itemId", DUMMY_SERVER).put("itemType", "resource_server")
        .put("role", "consumer");
    RequestToken request = new RequestToken(jsonReq);

    mockPolicy.setResponse("valid");
    tokenService.createToken(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_SUCCESS, response.getString("type"));
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

    JsonObject jsonReq = new JsonObject().put("itemId", DUMMY_SERVER).put("itemType", "resource_server")
        .put("role", "admin");
    RequestToken request = new RequestToken(jsonReq);

    mockPolicy.setResponse("valid");
    tokenService.createToken(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_SUCCESS, response.getString("type"));
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

    JsonObject jsonReq = new JsonObject().put("itemId", "abc.123.com").put("itemType", "resource_server")
        .put("role", "admin");
    RequestToken request = new RequestToken(jsonReq);

    mockPolicy.setResponse("valid");
    tokenService.createToken(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_INVALID_INPUT, response.getString("type"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("create token as FAKE admin for resource server [Fail]")
  void createTokenConsumerResServFail(VertxTestContext testContext) {

    JsonObject userJson = consumer.result();
    
    /* We *artificially* add the admin role to the consumer user when creating the
     * user object */
    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.ADMIN)).build();

    JsonObject jsonReq = new JsonObject().put("itemId", DUMMY_SERVER).put("itemType", "resource_server")
        .put("role", "admin");
    RequestToken request = new RequestToken(jsonReq);

    mockPolicy.setResponse("valid");
    tokenService.createToken(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_INVALID_INPUT, response.getString("type"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("createToken [Failed-01 invalidPolicy]")
  void createTokenFailed01(VertxTestContext testContext) {

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
          assertEquals(URN_INVALID_INPUT, response.getString(TYPE));
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
    tokenService.createToken(mapToReqToken(undefinedRole), clientFlowUser(),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_INVALID_ROLE, response.getString(TYPE));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("revokeToken [Success]")
  void revokeTokenSuccess(VertxTestContext testContext) {

    mockHttpWebClient.setResponse("valid");
    tokenService.revokeToken(mapToRevToken(revokeTokenValidPayload),
        user("32a4b979-4f4a-4c44-b0c3-2fe109952b5f"),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_SUCCESS, response.getString(TYPE));
          // assertTrue(response.containsKey("accessToken"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("revokeToken [Failed-01 Failure in KeyClock or RS]")
  void revokeTokenFailed01(VertxTestContext testContext) {

    mockHttpWebClient.setResponse("invalid");
    tokenService.revokeToken(mapToRevToken(revokeTokenValidPayload),
        user("32a4b979-4f4a-4c44-b0c3-2fe109952b5f"),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_INVALID_INPUT, response.getString(TYPE));
          testContext.completeNow();
        })));
  }

  @Test
  @Disabled
  @DisplayName("revokeToken [Failed-02 nullUserId]")
  void revokeTokenFailed02(VertxTestContext testContext) {

    mockHttpWebClient.setResponse("valid");
    User.UserBuilder userBuilder = new UserBuilder();
    User user = new User(userBuilder);

    tokenService.revokeToken(mapToRevToken(revokeTokenValidPayload), user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_INVALID_INPUT, response.getString(TYPE));
          testContext.completeNow();
        })));
  }

  @Test
  @Disabled
  @DisplayName("revokeToken [Failed-03 invalidUserId]")
  void revokeTokenFailed03(VertxTestContext testContext) {

    mockHttpWebClient.setResponse("valid");
    tokenService.revokeToken(mapToRevToken(revokeTokenValidPayload),
        user("32a4b979-4f4a-4c44-b0c3-2fe109952b53"),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_INVALID_INPUT, response.getString(TYPE));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("revokeToken [Failed-04 invalidUrl]")
  void revokeTokenFailed04(VertxTestContext testContext) {

    mockHttpWebClient.setResponse("valid");
    tokenService.revokeToken(mapToRevToken(revokeTokenInvalidUrl),
        user("32a4b979-4f4a-4c44-b0c3-2fe109952b5f"),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_INVALID_INPUT, response.getString(TYPE));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("revokeToken [Failed-05 invalidClientId]")
  void revokeTokenFailed05(VertxTestContext testContext) {

    mockHttpWebClient.setResponse("valid");
    tokenService.revokeToken(mapToRevToken(revokeTokenInvalidClientId),
        user("32a4b979-4f4a-4c44-b0c3-2fe109952b5f"),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_INVALID_INPUT, response.getString(TYPE));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("validateToken [Success]")
  void validateTokenSuccess(VertxTestContext testContext) {

    mockPolicy.setResponse("valid");
    tokenService.validateToken(mapToInspctToken(validTipPayload),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_SUCCESS, response.getString(TYPE));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("validateToken [Failed-01 invalidPolicy]")
  void validateTokenFailed01(VertxTestContext testContext) {

    mockPolicy.setResponse("invalid");
    tokenService.validateToken(mapToInspctToken(validTipPayload),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_INVALID_INPUT, response.getString(TYPE));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("validateToken [Failed-02 invalidToken]")
  void validateTokenFailed02(VertxTestContext testContext) {

    mockPolicy.setResponse("valid");
    tokenService.validateToken(mapToInspctToken(invalidTipPayload),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_INVALID_AUTH_TOKEN, response.getString(TYPE));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("validateToken [Failed-03 expiredToken]")
  void validateTokenFailed03(VertxTestContext testContext) {

    mockPolicy.setResponse("valid");
    tokenService.validateToken(mapToInspctToken(expiredTipPayload),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_INVALID_AUTH_TOKEN, response.getString(TYPE));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("validateToken [Failed-04 missingToken]")
  void validateTokenFailed04(VertxTestContext testContext) {

    mockPolicy.setResponse("valid");
    IntrospectToken introspect = new IntrospectToken();

    tokenService.validateToken(introspect,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_MISSING_INFO, response.getString("type"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("validateToken [Failed-05 randomToken]")
  void validateTokenFailed05(VertxTestContext testContext) {

    tokenService.validateToken(mapToInspctToken(randomToken),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_INVALID_AUTH_TOKEN, response.getString(TYPE));
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
          assertEquals(URN_INVALID_AUTH_TOKEN, response.getString(TYPE));
          testContext.completeNow();
        })));
  }
}
