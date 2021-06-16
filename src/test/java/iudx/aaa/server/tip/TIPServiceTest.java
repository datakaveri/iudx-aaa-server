package iudx.aaa.server.tip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.KeyStoreOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.PoolOptions;
import org.apache.logging.log4j.Logger;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.policy.PolicyService;
import iudx.aaa.server.postgres.client.PostgresClient;
import iudx.aaa.server.token.MockPolicyFactory;
import static iudx.aaa.server.token.Constants.*;
import static iudx.aaa.server.tip.RequestPayload.*;

@ExtendWith({VertxExtension.class})
public class TIPServiceTest {
  private static Logger LOGGER = LogManager.getLogger(TIPServiceTest.class);

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
  private static PostgresClient pgClient;
  private static TIPService tipService;
  private static Vertx vertxObj;
  private static String keystorePath;
  private static String keystorePassword;
  private static JWTAuth provider;
  private static PolicyService policyService;
  private static MockPolicyFactory mockPolicy;

  @BeforeAll
  @DisplayName("Deploying Verticle")
  static void startVertx(Vertx vertx,
      VertxTestContext testContext) {
    config = new Configuration();
    vertxObj = vertx;
    JsonObject dbConfig = config.configLoader(0, vertx);

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
    pgClient = new PostgresClient(vertx, connectOptions, poolOptions);
    
    mockPolicy = new MockPolicyFactory();
    policyService = mockPolicy.getInstance();
    tipService = new TIPServiceImpl(pgClient, policyService, provider);

    testContext.completeNow();

  }
  
  /* Initializing JwtProvider */
  public static JWTAuth jwtInitConfig(Vertx vertx) {
    JWTAuthOptions config = new JWTAuthOptions();
    config.setKeyStore(
        new KeyStoreOptions()
          .setPath(keystorePath)
          .setPassword(keystorePassword));

    JWTAuth provider = JWTAuth.create(vertx, config);
    return provider;
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing....");
    vertxObj.close(testContext.succeeding(response -> testContext.completeNow()));
  }

  @Test
  @DisplayName("validateToken [Success]")
  void validateTokenSuccess(VertxTestContext testContext) {
    
    mockPolicy.setResponse("valid");
    tipService.validateToken(validPayload.copy(), testContext.succeeding(response -> testContext.verify(() -> {
      assertEquals("allow", response.getString("status"));
      testContext.completeNow();
    })));
  }
  
  @Test
  @DisplayName("validateToken [Failed-01 invalidPolicy]")
  void validateTokenFailed01(VertxTestContext testContext) {

    mockPolicy.setResponse("invalid");
    tipService.validateToken(validPayload.copy(),
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
    tipService.validateToken(invalidPayload,
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
    tipService.validateToken(expiredPayload,
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
    tipService.validateToken(new JsonObject(),
        testContext.failing(response -> testContext.verify(() -> {
          JsonObject result = new JsonObject(response.getLocalizedMessage());
          assertEquals("failed", result.getString("status"));
          testContext.completeNow();
        })));
  }
}
