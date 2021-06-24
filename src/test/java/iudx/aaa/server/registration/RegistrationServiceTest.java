package iudx.aaa.server.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.postgres.client.PostgresClient;

@ExtendWith(VertxExtension.class)
public class RegistrationServiceTest {
  private static Logger LOGGER = LogManager.getLogger(RegistrationServiceTest.class);

  private static Configuration config;

  /* Database Properties */
  private static String databaseIP;
  private static int databasePort;
  private static String databaseName;
  private static String databaseUserName;
  private static String databasePassword;
  private static int poolSize;
  private static PgPool pgclient;
  private static PoolOptions poolOptions;
  private static PgConnectOptions connectOptions;
  private static PostgresClient pgClient;
  private static RegistrationService registrationService;
  private static Vertx vertxObj;

  @BeforeAll
  @DisplayName("Deploying Verticle")
  static void startVertx(Vertx vertx,
      VertxTestContext testContext) {
    config = new Configuration();
    vertxObj = vertx;
    JsonObject dbConfig = config.configLoader(1, vertx);

    /* Read the configuration and set the postgres client properties. */
    LOGGER.debug("Info : Reading config file");

    databaseIP = dbConfig.getString("databaseIP");
    databasePort = Integer.parseInt(dbConfig.getString("databasePort"));
    databaseName = dbConfig.getString("databaseName");
    databaseUserName = dbConfig.getString("databaseUserName");
    databasePassword = dbConfig.getString("databasePassword");
    poolSize = Integer.parseInt(dbConfig.getString("poolSize"));

    /* Set Connection Object */
    if (connectOptions == null) {
      connectOptions = new PgConnectOptions().setPort(databasePort).setHost(databaseIP)
          .setDatabase(databaseName).setUser(databaseUserName).setPassword(databasePassword);
    }

    /* Pool options */
    if (poolOptions == null) {
      poolOptions = new PoolOptions().setMaxSize(poolSize);
    }

    /* Create the client pool */
    pgclient = PgPool.pool(vertx, connectOptions, poolOptions);

    pgClient = new PostgresClient(vertx, connectOptions, poolOptions);

    registrationService = new RegistrationServiceImpl(pgClient);

    testContext.completeNow();

  }

  @AfterEach
  public void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing....");
    vertxObj.close(testContext.succeeding(response -> testContext.completeNow()));
  }

  @Test
  @DisplayName("Testing Successful user registration")
  void createUserSuccess(VertxTestContext testContext) {
    JsonObject request = new JsonObject().put("email", "email");

    registrationService.createUser(request,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals("success", response.getString("status"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Testing Failure in user registration")
  void createUserFailure(VertxTestContext testContext) {
    JsonObject request = new JsonObject().put("email", "email");

    registrationService.createUser(request,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals("failed", response.getString("status"));
          testContext.completeNow();
        })));
  }

}
