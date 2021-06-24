package iudx.aaa.server.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.KeyStoreOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import iudx.aaa.server.apiserver.IntrospectToken;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;
import iudx.aaa.server.configuration.Configuration;
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

    mockPolicy = new MockPolicyFactory();
    mockHttpWebClient = new MockHttpWebClient();
    httpWebClient = mockHttpWebClient.getMockHttpWebClient();

    policyService = mockPolicy.getInstance();
    tokenService = new TokenServiceImpl(pgPool, policyService, provider, httpWebClient);

    testContext.completeNow();
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
    vertxObj.close(testContext.succeeding(response -> testContext.completeNow()));
  }

  @Test
  @DisplayName("createToken [Success]")
  void createTokenSuccess(VertxTestContext testContext) {

    mockPolicy.setResponse("valid");
    tokenService.createToken(mapToReqToken(validPayload), roleList,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals("success", response.getString("status"));
          assertTrue(response.containsKey("accessToken"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("createToken [Failed-01 invalidPolicy]")
  void createTokenFailed01(VertxTestContext testContext) {

    mockPolicy.setResponse("invalid");
    tokenService.createToken(mapToReqToken(validPayload), roleList,
        testContext.failing(response -> testContext.verify(() -> {
          JsonObject result = new JsonObject(response.getLocalizedMessage());
          assertEquals("failed", result.getString("status"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("createToken [Failed-02 invalidClientSecret]")
  void createTokenFailed02(VertxTestContext testContext) {

    mockPolicy.setResponse("valid");
    tokenService.createToken(mapToReqToken(invalidClientSecret), roleList,
        testContext.failing(response -> testContext.verify(() -> {
          JsonObject result = new JsonObject(response.getLocalizedMessage());
          assertEquals("failed", result.getString("status"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("createToken [Failed-03 invalidClientId]")
  void createTokenFailed03(VertxTestContext testContext) {

    mockPolicy.setResponse("valid");
    tokenService.createToken(mapToReqToken(invalidClientId), roleList,
        testContext.failing(response -> testContext.verify(() -> {
          JsonObject result = new JsonObject(response.getLocalizedMessage());
          assertEquals("failed", result.getString("status"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("createToken [Failed-04 undefinedRole]")
  void createTokenFailed04(VertxTestContext testContext) {

    mockPolicy.setResponse("valid");
    tokenService.createToken(mapToReqToken(undefinedRole), roleList,
        testContext.failing(response -> testContext.verify(() -> {
          JsonObject result = new JsonObject(response.getLocalizedMessage());
          assertEquals("failed", result.getString("status"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("revokeToken [Success]")
  void revokeTokenSuccess(VertxTestContext testContext) {

    mockHttpWebClient.setResponse("valid");
    tokenService.revokeToken(mapToRevToken(revokeTokenValidPayload), user("32a4b979-4f4a-4c44-b0c3-2fe109952b5f"),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals("success", response.getString("status"));
          // assertTrue(response.containsKey("accessToken"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("revokeToken [Failed-01 Failure in KeyClock or RS]")
  void revokeTokenFailed01(VertxTestContext testContext) {

    mockHttpWebClient.setResponse("invalid");
    tokenService.revokeToken(mapToRevToken(revokeTokenValidPayload), user("32a4b979-4f4a-4c44-b0c3-2fe109952b5f"),
        testContext.failing(response -> testContext.verify(() -> {
          JsonObject result = new JsonObject(response.getLocalizedMessage());
          assertEquals("failed", result.getString("status"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("revokeToken [Failed-02 nullUserId]")
  void revokeTokenFailed02(VertxTestContext testContext) {

    mockHttpWebClient.setResponse("valid");
    User.UserBuilder userBuilder = new UserBuilder();
    User user = new User(userBuilder);
    
    tokenService.revokeToken(mapToRevToken(revokeTokenValidPayload), user,
        testContext.failing(response -> testContext.verify(() -> {
          JsonObject result = new JsonObject(response.getLocalizedMessage());
          assertEquals("failed", result.getString("status"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("revokeToken [Failed-03 invalidUserId]")
  void revokeTokenFailed03(VertxTestContext testContext) {

    mockHttpWebClient.setResponse("valid");
    tokenService.revokeToken(mapToRevToken(revokeTokenValidPayload), user("32a4b979-4f4a-4c44-b0c3-2fe109952b53"),
        testContext.failing(response -> testContext.verify(() -> {
          JsonObject result = new JsonObject(response.getLocalizedMessage());
          assertEquals("failed", result.getString("status"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("revokeToken [Failed-04 invalidUrl]")
  void revokeTokenFailed04(VertxTestContext testContext) {

    mockHttpWebClient.setResponse("valid");
    tokenService.revokeToken(mapToRevToken(revokeTokenInvalidUrl), user("32a4b979-4f4a-4c44-b0c3-2fe109952b5f"),
        testContext.failing(response -> testContext.verify(() -> {
          JsonObject result = new JsonObject(response.getLocalizedMessage());
          assertEquals("failed", result.getString("status"));
          testContext.completeNow();
        })));
  }
  
  @Test
  @DisplayName("revokeToken [Failed-05 invalidClientId]")
  void revokeTokenFailed05(VertxTestContext testContext) {

    mockHttpWebClient.setResponse("valid");
    tokenService.revokeToken(mapToRevToken(revokeTokenInvalidClientId), user("32a4b979-4f4a-4c44-b0c3-2fe109952b5f"),
        testContext.failing(response -> testContext.verify(() -> {
          JsonObject result = new JsonObject(response.getLocalizedMessage());
          assertEquals("failed", result.getString("status"));
          testContext.completeNow();
        })));
  }
  
  @Test
  @DisplayName("validateToken [Success]")
  void validateTokenSuccess(VertxTestContext testContext) {
    
    mockPolicy.setResponse("valid");
    tokenService.validateToken(mapToInspctToken(validTipPayload), testContext.succeeding(response -> testContext.verify(() -> {
      assertEquals("allow", response.getString("status"));
      testContext.completeNow();
    })));
  }
  
  @Test
  @DisplayName("validateToken [Failed-01 invalidPolicy]")
  void validateTokenFailed01(VertxTestContext testContext) {

    mockPolicy.setResponse("invalid");
    tokenService.validateToken(mapToInspctToken(validTipPayload),
        testContext.failing(response -> testContext.verify(() -> {
          JsonObject result = new JsonObject(response.getLocalizedMessage());
          assertEquals("deny", result.getString("status"));
          testContext.completeNow();
        })));
  }
  
  @Test
  @DisplayName("validateToken [Failed-02 invalidToken]")
  void validateTokenFailed02(VertxTestContext testContext) {

    mockPolicy.setResponse("valid");
    tokenService.validateToken(mapToInspctToken(invalidTipPayload),
        testContext.failing(response -> testContext.verify(() -> {
          JsonObject result = new JsonObject(response.getLocalizedMessage());
          assertEquals("deny", result.getString("status"));
          testContext.completeNow();
        })));
  }
  
  @Test
  @DisplayName("validateToken [Failed-03 expiredToken]")
  void validateTokenFailed03(VertxTestContext testContext) {

    mockPolicy.setResponse("valid");
    tokenService.validateToken(mapToInspctToken(expiredTipPayload),
        testContext.failing(response -> testContext.verify(() -> {
          JsonObject result = new JsonObject(response.getLocalizedMessage());
          assertEquals("deny", result.getString("status"));
          testContext.completeNow();
        })));
  }
  
  @Test
  @DisplayName("validateToken [Failed-04 missingToken]")
  void validateTokenFailed04(VertxTestContext testContext) {

    mockPolicy.setResponse("valid");
    IntrospectToken introspect = new IntrospectToken();
    
    tokenService.validateToken(introspect,
        testContext.failing(response -> testContext.verify(() -> {
          JsonObject result = new JsonObject(response.getLocalizedMessage());
          assertEquals("failed", result.getString("status"));
          testContext.completeNow();
        })));
  }
}
