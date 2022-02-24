package iudx.aaa.server.policy;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Tuple;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static iudx.aaa.server.policy.Constants.DELETE_POLICY;
import static iudx.aaa.server.policy.Constants.ID_NOT_PRESENT;
import static iudx.aaa.server.policy.Constants.INVALID_DELEGATE;
import static iudx.aaa.server.policy.Constants.ITEMNOTFOUND;
import static iudx.aaa.server.policy.Constants.SUCC_TITLE_POLICY_DEL;
import static iudx.aaa.server.policy.TestRequest.DelFail;
import static iudx.aaa.server.policy.TestRequest.DelPolFail;
import static iudx.aaa.server.policy.TestRequest.DelegateUser;
import static iudx.aaa.server.policy.TestRequest.DelegateUserFail;
import static iudx.aaa.server.policy.TestRequest.INSERT_REQ;
import static iudx.aaa.server.policy.TestRequest.ProviderUser;
import static iudx.aaa.server.policy.TestRequest.ResExistFail;
import static iudx.aaa.server.policy.TestRequest.ResOwnFail;
import static iudx.aaa.server.policy.TestRequest.allRolesUser;
import static iudx.aaa.server.policy.TestRequest.successProvider;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
public class DeletePolicyTest {
  private static final Logger LOGGER = LogManager.getLogger(VerifyPolicyTest.class);
  static Future<UUID> policyId;
  private static Configuration config;
  /* Database Properties */
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
  private static PolicyService policyService;
  private static RegistrationService registrationService;
  private static CatalogueClient catalogueClient;
  private static Vertx vertxObj;
  private static JsonObject authOptions;
  private static JsonObject catOptions;

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
    databaseSchema = dbConfig.getString("databaseSchema");
    databaseUserName = dbConfig.getString("databaseUserName");
    databasePassword = dbConfig.getString("databasePassword");
    poolSize = Integer.parseInt(dbConfig.getString("poolSize"));
    authOptions = dbConfig.getJsonObject("authOptions");
    catOptions = dbConfig.getJsonObject("catOptions");

    /*
     * Injecting authServerUrl into 'authOptions' from config().'authServerDomain'
     * TODO - make this uniform
     */
    authOptions.put("authServerUrl", dbConfig.getString("authServerDomain"));

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

    /* Pool options */
    if (poolOptions == null) {
      poolOptions = new PoolOptions().setMaxSize(poolSize);
    }

    /* Create the client pool */
    pgclient = PgPool.pool(vertx, connectOptions, poolOptions);

    policyId =
        pgclient
            .withConnection(
                conn ->
                    conn.preparedQuery(INSERT_REQ)
                        .execute()
                        .map(row -> row.iterator().next().getUUID("id")))
            .onSuccess(
                obj -> {
                  policyService =
                      new PolicyServiceImpl(
                          pgclient, registrationService, catalogueClient, authOptions, catOptions);
                  testContext.completeNow();
                })
            .onFailure(err -> testContext.failNow(err.getMessage()));
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing....");
    List<UUID> policyidList =new ArrayList<>();
    policyidList.add(policyId.result());
      pgclient
              .withConnection(
                      conn ->
                              conn.preparedQuery(DELETE_POLICY)
                                      .execute(Tuple.of(Constants.status.DELETED, Constants.status.ACTIVE)
                                              .addArrayOfUUID(policyidList.toArray(UUID[]::new)))
                                      .map(row -> row.iterator().next().getUUID(policyId.result().toString()))
      .onComplete(ar->
    vertxObj.close(testContext.succeeding(response -> testContext.completeNow()))));
  }

  @Test
  @DisplayName("Testing Failure(ID not present))")
  void resExistFailure(VertxTestContext testContext) {

    policyService.deletePolicy(
        ResExistFail,
        allRolesUser,
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      JsonObject result = response;
                      assertEquals(ID_NOT_PRESENT, result.getString("title"));
                      testContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Testing Failure(user does not own resource)")
  void resOwnFailure(VertxTestContext testContext) {

    policyService.deletePolicy(
        ResOwnFail,
        ProviderUser,
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      JsonObject result = response;
                      assertEquals(ITEMNOTFOUND, result.getString("title"));
                      testContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Testing Failure (delegate policy not present)")
  void delPolicyFailure(VertxTestContext testContext) {

    policyService.deletePolicy(
        DelPolFail,
        DelegateUser,
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      JsonObject result = response;
                      assertEquals(ITEMNOTFOUND, result.getString("title"));
                      testContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Testing Failure (not a delegate for owner)")
  void delFailure(VertxTestContext testContext) {

    policyService.deletePolicy(
        DelFail,
        DelegateUserFail,
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      JsonObject result = response;
                      assertEquals(ITEMNOTFOUND, result.getString("title"));
                      testContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Testing successful deletion ")
  void SuccessDel(VertxTestContext testContext) {
    JsonObject obj = new JsonObject().put("id", policyId.result().toString());
    JsonArray req = new JsonArray().add(obj);
    policyService.deletePolicy(
        req,
        successProvider,
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      JsonObject result = response;
                      assertEquals(SUCC_TITLE_POLICY_DEL, result.getString("title"));
                      testContext.completeNow();
                    })));
  }
}
