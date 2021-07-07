package iudx.aaa.server.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
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
import iudx.aaa.server.registration.KcAdmin;

@ExtendWith({VertxExtension.class})
public class AdminServiceTest {
  private static Logger LOGGER = LogManager.getLogger(AdminServiceTest.class);

  private static Configuration config;

  /* Database Properties */
  private static String databaseIP;
  private static int databasePort;
  private static String databaseName;
  private static String databaseUserName;
  private static String databasePassword;
  private static int poolSize;
  private static PgPool pool;
  private static PoolOptions poolOptions;
  private static PgConnectOptions connectOptions;
  private static AdminService adminService;
  private static Vertx vertxObj;

  private static KcAdmin kc = Mockito.mock(KcAdmin.class);
  @BeforeAll
  @DisplayName("Deploying Verticle")
  static void startVertx(Vertx vertx,
      VertxTestContext testContext) {
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
    pool = PgPool.pool(vertx, connectOptions, poolOptions);
    adminService = new AdminServiceImpl(pool, kc);

    testContext.completeNow();
  }

  @AfterEach
  public void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing....");
    vertxObj.close(testContext.succeeding(response -> testContext.completeNow()));
  }
}
