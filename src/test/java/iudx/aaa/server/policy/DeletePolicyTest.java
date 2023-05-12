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
import iudx.aaa.server.apd.ApdService;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.registration.RegistrationService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static iudx.aaa.server.policy.Constants.DELETE_USR_POLICY;
import static iudx.aaa.server.policy.Constants.DELETE_APD_POLICY;
import static iudx.aaa.server.policy.Constants.ID_NOT_PRESENT;
import static iudx.aaa.server.policy.Constants.NO_USER;
import static iudx.aaa.server.policy.Constants.INVALID_ROLE;
import static iudx.aaa.server.policy.Constants.SUCC_TITLE_POLICY_DEL;
import static iudx.aaa.server.policy.TestRequest.DelFail;
import static iudx.aaa.server.policy.TestRequest.DelPolFail;
import static iudx.aaa.server.policy.TestRequest.DelegateUser;
import static iudx.aaa.server.policy.TestRequest.DelegateUserFail;
import static iudx.aaa.server.policy.TestRequest.INSERT_EXPIRED_USER_POL;
import static iudx.aaa.server.policy.TestRequest.INSERT_USER_POL;
import static iudx.aaa.server.policy.TestRequest.INSERT_APD_POL;
import static iudx.aaa.server.policy.TestRequest.ProviderUser;
import static iudx.aaa.server.policy.TestRequest.ResExistFail;
import static iudx.aaa.server.policy.TestRequest.ResOwnFail;
import static iudx.aaa.server.policy.TestRequest.allRolesUser;
import static iudx.aaa.server.policy.TestRequest.allRolesUser2;
import static iudx.aaa.server.policy.TestRequest.allRolesUser3;
import static iudx.aaa.server.policy.TestRequest.successProvider;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestMethodOrder(OrderAnnotation.class)
@ExtendWith({VertxExtension.class, MockitoExtension.class})
public class DeletePolicyTest {
  private static final Logger LOGGER = LogManager.getLogger(VerifyPolicyTest.class);
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
  private static ApdService apdService = Mockito.mock(ApdService.class);
  private static EmailClient emailClient = Mockito.mock(EmailClient.class);

  private static RegistrationService registrationService;
  private static CatalogueClient catalogueClient;
  private static Vertx vertxObj;
  private static JsonObject authOptions;
  private static JsonObject catOptions;
  
  private static UUID userPolicyId = UUID.randomUUID();
  private static UUID expiredUserPolicyId = UUID.randomUUID();
  private static UUID apdPolicyId = UUID.randomUUID();

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

    /* We pass the UUID id when inserting the policy into the DB itself */
    pgclient
        .withConnection(conn -> conn.preparedQuery(INSERT_USER_POL).execute(Tuple.of(userPolicyId))
            .compose(x -> conn.preparedQuery(INSERT_APD_POL).execute(Tuple.of(apdPolicyId)))
            .compose(x -> conn.preparedQuery(INSERT_EXPIRED_USER_POL).execute(Tuple.of(expiredUserPolicyId))))
        .onSuccess(obj -> {
          policyService = new PolicyServiceImpl(pgclient, registrationService, apdService,
              catalogueClient, authOptions, catOptions,emailClient);
          testContext.completeNow();
        }).onFailure(err -> testContext.failNow(err.getMessage()));
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing....");
    List<UUID> policyidList = new ArrayList<>();
    policyidList.add(userPolicyId);
    policyidList.add(apdPolicyId);
    Tuple policyTup = Tuple.of(policyidList.toArray(UUID[]::new));
    
    pgclient.withConnection(conn -> conn.preparedQuery(DELETE_USR_POLICY).execute(policyTup)
        .compose(x -> conn.preparedQuery(DELETE_APD_POLICY).execute(policyTup)).onComplete(
            ar -> vertxObj.close(testContext.succeeding(response -> testContext.completeNow()))));
  }

  @Order(1)
  @Test
  @DisplayName("Testing Failure(ID not present))")
  void resExistFailure(VertxTestContext testContext) {

    policyService.deletePolicy(
        ResExistFail,
        allRolesUser,
        new JsonObject(),
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      JsonObject result = response;
                      assertEquals(ID_NOT_PRESENT, result.getString("title"));
                      testContext.completeNow();
                    })));
  }

  @Order(2)
  @Test
  @DisplayName("Testing Failure(user does not own resource)")
  void resOwnFailure(VertxTestContext testContext) {

    policyService.deletePolicy(
        ResOwnFail,
        ProviderUser,
        new JsonObject(),
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      JsonObject result = response;
                      assertEquals(ID_NOT_PRESENT, result.getString("title"));
                      testContext.completeNow();
                    })));
  }

  @Order(3)
  @Test
  @DisplayName("Testing Failure (delegate policy not present)")
  void delPolicyFailure(VertxTestContext testContext) {
      JsonObject providerHeader = new JsonObject().put("providerId","a13eb955-c691-4fd3-b200-f18bc78810b5");
    policyService.deletePolicy(
        DelPolFail,
        DelegateUser,
            providerHeader,
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      JsonObject result = response;
                      assertEquals(ID_NOT_PRESENT, result.getString("title"));
                      testContext.completeNow();
                    })));
  }

  @Order(4)
  @Test
  @DisplayName("Testing Failure (not a delegate for owner)")
  void delFailure(VertxTestContext testContext) {
      JsonObject providerHeader = new JsonObject().put("providerId","a13eb955-c691-4fd3-b200-f18bc78810b5");
    policyService.deletePolicy(
        DelFail,
        DelegateUserFail,
            providerHeader,
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      JsonObject result = response;
                      assertEquals(ID_NOT_PRESENT, result.getString("title"));
                      testContext.completeNow();
                    })));
  }

  @Order(5)
  @Test
  @DisplayName("Testing successful deletion of user policy")
  void SuccessDelUserPol(VertxTestContext testContext) {
    JsonObject obj = new JsonObject().put("id", userPolicyId.toString());
    JsonArray req = new JsonArray().add(obj);
    policyService.deletePolicy(
        req,
        successProvider,
        new JsonObject(),
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      JsonObject result = response;
                      assertEquals(SUCC_TITLE_POLICY_DEL, result.getString("title"));
                      testContext.completeNow();
                    })));
  }

  @Order(6)
  @Test
  @DisplayName("Testing failure when user policy already deleted and apd policy active ")
  void FailDelApdPol(VertxTestContext testContext) {
    JsonArray req = new JsonArray().add(new JsonObject().put("id", userPolicyId.toString()))
        .add(new JsonObject().put("id", apdPolicyId.toString()));
    policyService.deletePolicy(
        req,
        successProvider,
        new JsonObject(),
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      JsonObject result = response;
                      assertEquals(ID_NOT_PRESENT, result.getString("title"));
                      testContext.completeNow();
                    })));
  }

  @Order(7)
  @Test
  @DisplayName("Testing successful delete of apd policy and expired user policy")
  void SuccessDelApdPol(VertxTestContext testContext) {
    JsonArray req = new JsonArray().add(new JsonObject().put("id", expiredUserPolicyId.toString()))
        .add(new JsonObject().put("id", apdPolicyId.toString()));
    policyService.deletePolicy(
        req,
        successProvider,
        new JsonObject(),
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      JsonObject result = response;
                      assertEquals(SUCC_TITLE_POLICY_DEL, result.getString("title"));
                      testContext.completeNow();
                    })));
  }

  @Order(8)
  @Test
  @DisplayName("Testing Failure(ID is NIL_UUID(No USER) present))")
  void failCaseNil_User(VertxTestContext testContext) {

      policyService.deletePolicy(
              ResExistFail,
              allRolesUser2,
              new JsonObject(),
              testContext.succeeding(
                      response ->
                              testContext.verify(
                                      () -> {
                                          JsonObject result = response;
                                          assertEquals(NO_USER, result.getString("detail"));
                                          assertEquals("urn:dx:as:MissingInformation", result.getString("title"));
                                          assertEquals(401, result.getInteger("status"));
                                          testContext.completeNow();
                                      })));

    }

    @Order(9)
    @Test
    @DisplayName("Testing Failure because Consumer trying to Delete the Policy")
    void deletePolicyFaliureByConsumer(VertxTestContext testContext) {

        policyService.deletePolicy(
                ResExistFail,
                allRolesUser3,
                new JsonObject(),
                testContext.succeeding(
                        response ->
                                testContext.verify(
                                        () -> {
                                            JsonObject result = response;
                                            assertEquals("urn:dx:as:InvalidRole", result.getString("type"));
                                            assertEquals(INVALID_ROLE, result.getString("detail"));
                                            assertEquals(INVALID_ROLE, result.getString("title"));
                                            assertEquals(401, result.getInteger("status"));
                                            testContext.completeNow();
                                        })));
    }
}
