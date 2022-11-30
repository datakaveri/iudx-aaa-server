package iudx.aaa.server.token;

import static iudx.aaa.server.apiserver.util.Urn.*;
import static iudx.aaa.server.token.Constants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static iudx.aaa.server.token.RequestPayload.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import iudx.aaa.server.apiserver.RoleStatus;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.policy.PolicyService;
import iudx.aaa.server.registration.Utils;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
public class IudxTokenGeneratorTest {
  
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
  private static IudxJwtTokenGenerator jwtTokenGenerator;
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
      jwtTokenGenerator=new IudxJwtTokenGenerator(provider);
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

  // TODO : these test cases will be moved to new class, IudxJwtTokenGeneratorTest.class according
  // to refactoring
  @Test
  @DisplayName("validateToken [Success]")
  void validateTokenSuccess(VertxTestContext testContext) {
    JsonObject tokenRequest = new JsonObject()
        .put(ITEM_TYPE, "resource_group")
          .put(ITEM_ID, RESOURCE_GROUP)
          .put(USER_ID, consumer.result().getString("userId"))
          .put(URL, DUMMY_SERVER)
          .put(ROLE, Roles.CONSUMER.toString().toLowerCase());
    JsonObject token = jwtTokenGenerator.getJwt(tokenRequest);
    token.remove("expiry");
    token.remove("server");

    tokenService
        .validateToken(mapToInspctToken(token),
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
    JsonObject tokenRequest = new JsonObject()
        .put(ITEM_TYPE, RESOURCE_SVR)
          .put(ITEM_ID, DUMMY_SERVER)
          .put(USER_ID, consumer.result().getString("userId"))
          .put(URL, DUMMY_SERVER)
          .put(ROLE, Roles.CONSUMER.toString().toLowerCase());
    JsonObject token = jwtTokenGenerator.getJwt(tokenRequest);
    token.remove("expiry");
    token.remove("server");

    tokenService
        .validateToken(mapToInspctToken(token),
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
  @DisplayName("validateToken [Failed-02 invalidToken]")
  void validateTokenFailed02(VertxTestContext testContext) {

    JsonObject tokenRequest = new JsonObject()
        .put(ITEM_TYPE, "resourceGroup")
          .put(ITEM_ID, RESOURCE_GROUP)
          .put(USER_ID, consumer.result().getString("userId"))
          .put(URL, DUMMY_SERVER)
          .put(ROLE, Roles.CONSUMER.toString().toLowerCase());
    JsonObject token = jwtTokenGenerator.getJwt(tokenRequest);

    /* add extra data to token */
    JsonObject invalidToken =
        new JsonObject().put("accessToken", token.getString("accessToken") + "abc");

    tokenService
        .validateToken(mapToInspctToken(invalidToken),
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

    tokenService
        .validateToken(mapToInspctToken(expiredTipPayload),
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

    tokenService
        .validateToken(introspect, testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_MISSING_INFO.toString(), response.getString("type"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("validateToken [Failed-05 randomToken]")
  void validateTokenFailed05(VertxTestContext testContext) {

    tokenService
        .validateToken(mapToInspctToken(randomToken),
            testContext.succeeding(response -> testContext.verify(() -> {
              assertEquals(URN_INVALID_AUTH_TOKEN.toString(), response.getString(TYPE));
              assertTrue(
                  response.getJsonArray("results").getJsonObject(0).getString(STATUS).equals(DENY));
              testContext.completeNow();
            })));
  }

  @Test
  @DisplayName("validateToken success [APD token]")
  void validateTokenFailed06(VertxTestContext testContext) {

    String sessId = UUID.randomUUID().toString();
    JsonObject apdTokenRequest = new JsonObject()
        .put(URL, DUMMY_SERVER)
          .put(SESSION_ID, sessId)
          .put(USER_ID, consumer.result().getString("userId"))
          .put(LINK, DUMMY_SERVER + "/apd");
    JsonObject token = jwtTokenGenerator.getApdJwt(apdTokenRequest);
    token.remove("expiry");
    token.remove("server");
    token.remove("link");

    /* The JWT response has the key `apdToken`, introspect expects `accessToken` */
    token.put(ACCESS_TOKEN, token.remove(APD_TOKEN));

    tokenService
        .validateToken(mapToInspctToken(token),
            testContext.succeeding(response -> testContext.verify(() -> {
              assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
              JsonObject payload = response.getJsonObject("results");
              assertEquals(payload.getString(ISS), DUMMY_AUTH_SERVER);
              assertEquals(payload.getString(SUB), consumer.result().getString("userId"));
              assertEquals(payload.getString(AUD), DUMMY_SERVER);
              assertEquals(payload.getString(SID), sessId);
              assertEquals(payload.getString(LINK), DUMMY_SERVER + "/apd");
              assertNotNull(payload.getString(EXP));

              assertTrue(!payload.containsKey(CONS));
              assertTrue(!payload.containsKey(ROLE));
              assertTrue(!payload.containsKey(IID));

              testContext.completeNow();
            })));
  }

  @Test
  @DisplayName("validateToken success [special admin token]")
  void validateSpecialAdminToken(VertxTestContext testContext) {

    JsonObject adminTokenReq = new JsonObject()
        .put(USER_ID, CLAIM_ISSUER)
          .put(URL, DUMMY_SERVER)
          .put(ROLE, "")
          .put(ITEM_TYPE, "")
          .put(ITEM_ID, "");
    JsonObject token = jwtTokenGenerator.getJwt(adminTokenReq);
    token.remove("expiry");
    token.remove("server");

    tokenService
        .validateToken(mapToInspctToken(token),
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
