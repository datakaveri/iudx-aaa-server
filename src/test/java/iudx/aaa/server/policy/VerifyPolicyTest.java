package iudx.aaa.server.policy;

import io.vertx.core.Vertx;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import iudx.aaa.server.apd.ApdService;
import iudx.aaa.server.apiserver.util.ComposeException;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.registration.RegistrationService;
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

import static iudx.aaa.server.policy.Constants.INVALID_ROLE;
import static iudx.aaa.server.policy.Constants.INCORRECT_ITEM_ID;
import static iudx.aaa.server.policy.Constants.NO_ADMIN_POLICY;
import static iudx.aaa.server.policy.Constants.INCORRECT_ITEM_TYPE;
import static iudx.aaa.server.policy.Constants.REGISTRATION_SERVICE_ADDRESS;
import static iudx.aaa.server.policy.Constants.STATUS;
import static iudx.aaa.server.policy.Constants.SUCCESS;
import static iudx.aaa.server.policy.Constants.UNAUTHORIZED_DELEGATE;
import static iudx.aaa.server.policy.TestRequest.apdResourceGrpPolicy;
import static iudx.aaa.server.policy.TestRequest.NoCataloguePolicy;
import static iudx.aaa.server.policy.TestRequest.NoCatalogueProviderPolicy;
import static iudx.aaa.server.policy.TestRequest.consumerVerification;
import static iudx.aaa.server.policy.TestRequest.invalidDelegate;
import static iudx.aaa.server.policy.TestRequest.invalidItemId;
import static iudx.aaa.server.policy.TestRequest.invalidItemId2;
import static iudx.aaa.server.policy.TestRequest.invalidItemId3;
import static iudx.aaa.server.policy.TestRequest.roleFailure;
import static iudx.aaa.server.policy.TestRequest.roleFailure2;
import static iudx.aaa.server.policy.TestRequest.roleFailure3;
import static iudx.aaa.server.policy.TestRequest.validDelegateVerification;
import static iudx.aaa.server.policy.TestRequest.validProviderCat;
import static iudx.aaa.server.policy.TestRequest.validProviderVerification;
import static iudx.aaa.server.token.Constants.INVALID_POLICY;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
public class VerifyPolicyTest {
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
    databaseSchema = dbConfig.getString("databaseSchema");
    databaseUserName = dbConfig.getString("databaseUserName");
    databasePassword = dbConfig.getString("databasePassword");
    poolSize = Integer.parseInt(dbConfig.getString("poolSize"));
    catalogueOptions = dbConfig.getJsonObject("catalogueOptions");
    catalogueOptions.put("domain", dbConfig.getString("domain"));
    catalogueOptions.put("resURL", dbConfig.getJsonObject("resOptions").getString("resURL"));
    authOptions = dbConfig.getJsonObject("authOptions");
    catOptions = dbConfig.getJsonObject("catOptions");

    /*
     * Injecting authServerUrl into 'authOptions' and 'catalogueOptions' from config().'authServerDomain'
     * TODO - make this uniform
     */
    authOptions.put("authServerUrl", dbConfig.getString("authServerDomain"));
    catalogueOptions.put("authServerUrl", dbConfig.getString("authServerDomain"));

    // get options for catalogue client

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

    PgPool pool = PgPool.pool(vertx, connectOptions, poolOptions);
    registrationService = RegistrationService.createProxy(vertx, REGISTRATION_SERVICE_ADDRESS);
    catalogueClient = new CatalogueClient(vertx, pool, catalogueOptions);
    policyService = new PolicyServiceImpl(pool, registrationService, apdService, catalogueClient,
        authOptions, catOptions);
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
                      ComposeException exp = (ComposeException) response;
                      assertEquals(INVALID_ROLE, exp.getResponse().getDetail());
                      testContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Invalid item ID - sending URL instead of resource")
  void invalidItemId(VertxTestContext testContext) {
    policyService.verifyPolicy(
        invalidItemId,
        testContext.failing(
            response ->
                testContext.verify(
                    () -> {
                      ComposeException exp = (ComposeException) response;
                      assertEquals(INCORRECT_ITEM_ID, exp.getResponse().getDetail());
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
  @DisplayName("Testing Successful APD Policy verification consumer")
  void consumerApdPolicySuccess(VertxTestContext testContext) {
    
    /*
     * Since verifyPolicy does not check the response of callApd, we just send an empty JSON object
     * as the mocked response for callApd here.
     */
    Mockito.doAnswer(i -> { 
      Promise<JsonObject> promise = i.getArgument(1);
      promise.complete(new JsonObject());
      return i.getMock();
    }).when(apdService).callApd(any(), any());
    
    policyService.verifyPolicy(
        apdResourceGrpPolicy,
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
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
                      ComposeException exp = (ComposeException) response;
                      assertEquals(NO_ADMIN_POLICY, exp.getResponse().getDetail());
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
                      ComposeException exp = (ComposeException) response;
                      assertEquals(UNAUTHORIZED_DELEGATE, exp.getResponse().getDetail());
                      testContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("no policy for user by cat admin")
  void noCatAdminPolicy(VertxTestContext testContext) {
    policyService.verifyPolicy(
        NoCatalogueProviderPolicy,
        testContext.failing(
            response ->
                testContext.verify(
                    () -> {
                      ComposeException exp = (ComposeException) response;
                      assertEquals(UNAUTHORIZED_DELEGATE, exp.getResponse().getDetail());
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
                                          System.out.println("response + " + response);
                                          assertEquals(SUCCESS, response.getString(STATUS));
                                          testContext.completeNow();
                                      })));
  }
    @Test
    @DisplayName("role does not match - failure")
    void roleFailure2(VertxTestContext testContext) {
        policyService.verifyPolicy(
                roleFailure3,
                testContext.failing(
                        response ->
                                testContext.verify(
                                        () -> {
                                            ComposeException exp = (ComposeException) response;
                                            assertEquals(INVALID_ROLE, exp.getResponse().getDetail());
                                            assertEquals(INVALID_POLICY, exp.getResponse().getTitle());
                                            assertEquals(403, exp.getResponse().getStatus());
                                            testContext.completeNow();
                                        })));
    }

    @Test
    @DisplayName("role does not match - failure")
    void roleFailure3(VertxTestContext testContext) {
        policyService.verifyPolicy(
                roleFailure2,
                testContext.failing(
                        response ->
                                testContext.verify(
                                        () -> {
                                            ComposeException exp = (ComposeException) response;
                                            assertEquals(INCORRECT_ITEM_TYPE, exp.getResponse().getDetail());
                                            assertEquals(INVALID_POLICY, exp.getResponse().getTitle());
                                            assertEquals(403, exp.getResponse().getStatus());
                                            testContext.completeNow();
                                        })));
    }

    @Test
    @DisplayName("Resource Group !=4 Error")
    void invalidItemId2(VertxTestContext testContext) {
        policyService.verifyPolicy(
                invalidItemId2,
                testContext.failing(
                        response ->
                                testContext.verify(
                                        () -> {
                                            ComposeException exp = (ComposeException) response;
                                            assertEquals(INVALID_POLICY, exp.getResponse().getTitle());
                                            assertEquals(403, exp.getResponse().getStatus());
                                            assertEquals("incorrect item type", exp.getResponse().getDetail());
                                            testContext.completeNow();
                                        })));
    }

    @Test
    @DisplayName("Resource <=4 Error")
    void invalidItemId3(VertxTestContext testContext) {
        policyService.verifyPolicy(
                invalidItemId3,
                testContext.failing(
                        response ->
                                testContext.verify(
                                        () -> {
                                            ComposeException exp = (ComposeException) response;
                                            assertEquals(INVALID_POLICY, exp.getResponse().getTitle());
                                            assertEquals(403, exp.getResponse().getStatus());
                                            assertEquals("incorrect item type", exp.getResponse().getDetail());
                                            testContext.completeNow();
                                        })));
    }

}
