package iudx.aaa.server.policy;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.registration.RegistrationService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static iudx.aaa.server.policy.Constants.*;
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
  private static RegistrationService registrationService;
  private static CatalogueClient catalogueClient;
  private static JsonObject catalogueOptions;
  private static JsonObject authOptions;
  private static JsonObject catOptions;

  private static Vertx vertxObj;

  @BeforeAll
  @DisplayName("Deploying Verticle")
  static void startVertx(Vertx vertx, VertxTestContext testContext) {
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
    authOptions = dbConfig.getJsonObject("authOptions");
    catOptions = dbConfig.getJsonObject("catOptions");


      /* Set Connection Object */
    if (connectOptions == null) {
      connectOptions =
          new PgConnectOptions()
              .setPort(databasePort)
              .setHost(databaseIP)
              .setDatabase(databaseName)
              .setUser(databaseUserName)
              .setPassword(databasePassword);
    }

    /* Pool options */
    if (poolOptions == null) {
      poolOptions = new PoolOptions().setMaxSize(poolSize);
    }

    /* Create the client pool */
    pgclient = PgPool.pool(vertx, connectOptions, poolOptions);

    policyService = new PolicyServiceImpl(pgclient, registrationService, catalogueClient,authOptions,catOptions);

    testContext.completeNow();
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing....");
    vertxObj.close(testContext.succeeding(response -> testContext.completeNow()));
  }

  @Test
  @DisplayName("role does not match - failure")
  void roleFailure(VertxTestContext testContext) {
    policyService.verifyPolicy(
        roleFailure,
        testContext.failing(
            response ->
                testContext.verify(
                    () -> {
                      assertEquals(ROLE_NOT_FOUND, response.getLocalizedMessage());
                      testContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Testing Successful Policy verification consumer")
  void consumerPolicySuccess(VertxTestContext testContext) {
    policyService.verifyPolicy(
        consumerVerification,
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      assertEquals(SUCCESS, response.getString(STATUS));
                      testContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("no match for email hash")
  void InvalidProviderUser(VertxTestContext testContext) {
    policyService.verifyPolicy(
        providerUserFailure,
        testContext.failing(
            response ->
                testContext.verify(
                    () -> {
                      assertEquals(NO_USER, response.getLocalizedMessage());
                      testContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("no policy by catalogue admin")
  void NoAdminPolicy(VertxTestContext testContext) {
    policyService.verifyPolicy(
        NoCataloguePolicy,
        testContext.failing(
            response ->
                testContext.verify(
                    () -> {
                      assertEquals(NO_ADMIN_POLICY, response.getLocalizedMessage());
                      testContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Successful provider catalogue policy verification")
  void ProviderCatPolicySuccess(VertxTestContext testContext) {
    policyService.verifyPolicy(
        validProviderCat,
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      assertEquals(SUCCESS, response.getString(STATUS));
                      testContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Successful provider  policy verification")
  void ProviderPolicySuccess(VertxTestContext testContext) {
    policyService.verifyPolicy(
        validProviderVerification,
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      assertEquals(SUCCESS, response.getString(STATUS));
                      testContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("not a delegate for the resource owner")
  void InvalidDelegate(VertxTestContext testContext) {
    policyService.verifyPolicy(
        invalidDelegate,
        testContext.failing(
            response ->
                testContext.verify(
                    () -> {
                      assertEquals(UNAUTHORIZED_DELEGATE, response.getLocalizedMessage());
                      testContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("not policy for user by cat admin")
  void noCatAdminPolicy(VertxTestContext testContext) {
    policyService.verifyPolicy(
        NoCatalogueProviderPolicy,
        testContext.failing(
            response ->
                testContext.verify(
                    () -> {
                      assertEquals(NO_ADMIN_POLICY, response.getLocalizedMessage());
                      testContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("testing successful delegate verification")
  void validCatVerification(VertxTestContext testContext) {
    policyService.verifyPolicy(
        validDelegateVerification,
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                        System.out.println("response" + response);
                      assertEquals(SUCCESS, response.getString(STATUS));
                      testContext.completeNow();
                    })));
  }
}
