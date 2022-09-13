package iudx.aaa.server.policy;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import iudx.aaa.server.apd.ApdService;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.registration.RegistrationServiceImpl;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
public class CreateApdPolicyTest {
  // Database Properties
  private static Logger LOGGER =
      LogManager.getLogger(iudx.aaa.server.policy.CreateApdPolicyTest.class);
  private static Configuration config;
  private static String databaseIP;
  private static int databasePort;
  private static String databaseName;
  private static String databaseSchema;
  private static String databaseUserName;
  private static String databasePassword;
  private static int poolSize;
  private static PgPool pgclient;
  private static PoolOptions poolOptions;
  private static PgConnectOptions connectOptions;
  private static JsonObject authOptions;
  private static JsonObject catalogueOptions;
  private static JsonObject catOptions;
  private static String authServerURL;
  private static Vertx vertxObj;
  private static PolicyService policyService;
  private static ApdService apdService = Mockito.mock(ApdService.class);
  private static RegistrationServiceImpl registrationService =
      Mockito.mock(RegistrationServiceImpl.class);
  private static CatalogueClient catalogueClient = Mockito.mock(CatalogueClient.class);

  @BeforeAll
  @DisplayName("Deploying Verticle")
  static void startVertx(Vertx vertx, VertxTestContext testContext) {

    config = new Configuration();
    vertxObj = vertx;
    JsonObject dbConfig = config.configLoader(0, vertx);

    // Read the configuration and set the postgres client properties.

    LOGGER.debug("Info : Reading config file");

    databaseIP = dbConfig.getString("databaseIP");
    databasePort = Integer.parseInt(dbConfig.getString("databasePort"));
    databaseName = dbConfig.getString("databaseName");
    databaseSchema = dbConfig.getString("databaseSchema");
    databaseUserName = dbConfig.getString("databaseUserName");
    databasePassword = dbConfig.getString("databasePassword");
    poolSize = Integer.parseInt(dbConfig.getString("poolSize"));
    authOptions = dbConfig.getJsonObject("authOptions");
    catalogueOptions = dbConfig.getJsonObject("catalogueOptions");
    catOptions = dbConfig.getJsonObject("catOptions");
    authServerURL = "auth" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";
    authOptions.put("authServerUrl", authServerURL);
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
              .setProperties(schemaProp);
    }

    // Pool options

    if (poolOptions == null) {
      poolOptions = new PoolOptions().setMaxSize(poolSize);
    }

    // Create the client pool

    pgclient = PgPool.pool(vertx, connectOptions, poolOptions);

                        policyService =
                            new PolicyServiceImpl(
                                pgclient,
                                registrationService,
                                apdService,
                                catalogueClient,
                                authOptions,
                                catOptions);
                        testContext.completeNow();

  }

  @AfterAll
  public static void finish(VertxTestContext testContext){
      LOGGER.info("Finishing....");
      testContext.completeNow();
  }


  @Test
  @DisplayName("Testing  APD policy creation as incorrect user (itemType apd)")
  void invalidApdPolicyRole(VertxTestContext testContext) {
    testContext.completeNow();
  }


  @Test
  @DisplayName("Testing invalid APD policy creation - apd does not exist ")
  void invalidApdId(VertxTestContext testContext) {
    testContext.completeNow();}


  @Test
  @DisplayName("Testing apd Policy table - incorrect apd ID ")
  void invalidAPDPolicyApdId(VertxTestContext testContext) {  testContext.completeNow();
  }
  @Test
  @DisplayName("Testing apd Policy table - incorrect resource ID ")
  void invalidAPDPolicyItemId(VertxTestContext testContext) {
    testContext.completeNow();}

}
