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
import io.vertx.sqlclient.PoolOptions;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.policy.PolicyService;
import iudx.aaa.server.postgres.client.PostgresClient;
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
  private static PostgresClient pgClient;
  private static TokenService tokenService;
  private static Vertx vertxObj;
  private static String keystorePath;
  private static String keystorePassword;
  private static JWTAuth provider;
  private static PolicyService policyService;
  private static MockPolicyFactory mockPolicy;

  @BeforeAll
  @DisplayName("Deploying Verticle")
  static void startVertx(Vertx vertx, VertxTestContext testContext) {
    
    config = new Configuration();
    vertxObj = vertx;
    JsonObject dbConfig = config.configLoader(3, vertx);

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
    String issuer = dbConfig.getString("authServerDomain","");
    
    if(issuer != null && !issuer.isBlank()) {
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
    pgClient = new PostgresClient(vertx, connectOptions, poolOptions);
    
    mockPolicy = new MockPolicyFactory();
    policyService = mockPolicy.getInstance();
    tokenService = new TokenServiceImpl(pgClient, policyService, provider, null);

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
  @DisplayName("createToken [Success]")
  void createTokenSuccess(VertxTestContext testContext) {
    
    mockPolicy.setResponse("valid");
    tokenService.createToken(validPayload.copy(),
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
    tokenService.createToken(validPayload.copy(),
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
    tokenService.createToken(invalidClientSecret,
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
    tokenService.createToken(invalidClientId,
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
    tokenService.createToken(undefinedRole,
        testContext.failing(response -> testContext.verify(() -> {
          JsonObject result = new JsonObject(response.getLocalizedMessage());
          assertEquals("failed", result.getString("status"));
          testContext.completeNow();
        })));
  }
}
