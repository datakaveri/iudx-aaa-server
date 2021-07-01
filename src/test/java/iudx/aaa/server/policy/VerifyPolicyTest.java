package iudx.aaa.server.policy;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import iudx.aaa.server.configuration.Configuration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static iudx.aaa.server.policy.TestRequest.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
public class VerifyPolicyTest {
    private static Logger LOGGER = LogManager.getLogger(VerifyPolicyTest.class);

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
    private static PolicyService policyService;
    private static Vertx vertxObj;

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

        policyService = new PolicyServiceImpl(pgclient);

        testContext.completeNow();

    }

    @AfterAll
    public static void finish(VertxTestContext testContext) {
        LOGGER.info("Finishing....");
        vertxObj.close(testContext.succeeding(response -> testContext.completeNow()));
    }

    @Test
    @DisplayName("Testing Successful Policy verification")
    void verifyPolicySuccess(VertxTestContext testContext) {
        policyService.verifyPolicy(validVerifyPolicy,
                testContext.succeeding(response -> testContext.verify(() -> {
                    assertEquals("success", response.getString("status"));
                    testContext.completeNow();
                })));
    }

    @Test
    @DisplayName("Testing Failure in  policy verification(no matching role)")
    void verifyPolicyRoleFailure(VertxTestContext testContext) {
       policyService.verifyPolicy(policyRoleFailure,
                testContext.failing(response -> testContext.verify(() -> {
                    String result = response.getLocalizedMessage();
                    assertEquals("role not found", result);
                    testContext.completeNow();
                })));
    }

    @Test
    @DisplayName("Testing Failure in  policy verification(no policy defined)")
    void verifyPolicyFailure(VertxTestContext testContext) {
        policyService.verifyPolicy(policyFailure,
                testContext.failing(response -> testContext.verify(() -> {
                    String result = response.getLocalizedMessage();
                    assertEquals("policy not found", result);
                    testContext.completeNow();
                })));
    }
}
