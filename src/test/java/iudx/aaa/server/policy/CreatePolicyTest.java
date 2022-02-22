package iudx.aaa.server.policy;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import iudx.aaa.server.apiserver.ResourceObj;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.util.Urn;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static iudx.aaa.server.apiserver.util.Urn.URN_ALREADY_EXISTS;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_INPUT;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_ROLE;
import static iudx.aaa.server.policy.Constants.DETAIL;
import static iudx.aaa.server.policy.Constants.ITEMNOTFOUND;
import static iudx.aaa.server.policy.Constants.STATUS;
import static iudx.aaa.server.policy.Constants.TITLE;
import static iudx.aaa.server.policy.Constants.TYPE;
import static iudx.aaa.server.policy.Constants.UNAUTHORIZED;
import static iudx.aaa.server.policy.TestRequest.INSERT_REQ;
import static iudx.aaa.server.policy.TestRequest.MultipleProvider;
import static iudx.aaa.server.policy.TestRequest.ProviderUserCreate;
import static iudx.aaa.server.policy.TestRequest.consumerUser;
import static iudx.aaa.server.policy.TestRequest.duplicate;
import static iudx.aaa.server.policy.TestRequest.invalidDelUser;
import static iudx.aaa.server.policy.TestRequest.invalidProvider;
import static iudx.aaa.server.policy.TestRequest.invalidProviderItem;
import static iudx.aaa.server.policy.TestRequest.itemFailure;
import static iudx.aaa.server.policy.TestRequest.roleFailureReq;
import static iudx.aaa.server.policy.TestRequest.unAuthDel;
import static iudx.aaa.server.policy.TestRequest.unAuthProvider;
import static iudx.aaa.server.policy.TestRequest.validCatItem;
import static iudx.aaa.server.policy.TestRequest.validDelUser;
import static iudx.aaa.server.policy.TestRequest.validDelegateItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
public class CreatePolicyTest {
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
  private static Vertx vertxObj;
  private static JsonObject authOptions;
  private static JsonObject catOptions;
  private static RegistrationService registrationService;
  private static MockRegistrationFactory mockRegistrationFactory;
  private static CatalogueClient catClient;

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
                  catClient = Mockito.mock(CatalogueClient.class);
                  mockRegistrationFactory = new MockRegistrationFactory();
                  registrationService = mockRegistrationFactory.getInstance();
                  policyService =
                      new PolicyServiceImpl(
                          pgclient, registrationService, catClient, authOptions, catOptions);
                  testContext.completeNow();
                })
            .onFailure(err -> testContext.failNow(err.getMessage()));
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing....");
    vertxObj.close(testContext.succeeding(response -> testContext.completeNow()));
  }

  @Test
  @DisplayName("Testing Failure(Role is consumer))")
  void roleFailure(VertxTestContext testContext) {
    policyService.createPolicy(
        roleFailureReq,
        consumerUser,
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      JsonObject result = response;
                      assertEquals(URN_INVALID_ROLE.toString(), result.getString("type"));
                      testContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Testing Failure(Item does not exist))")
  void itemFailure(VertxTestContext testContext) {
    JsonObject resp =
        new JsonObject()
            .put(TYPE, URN_INVALID_INPUT)
            .put(
                DETAIL,
                "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/file.iudx.io/surat-itms-realtime-information2")
            .put(TITLE, ITEMNOTFOUND)
            .put(STATUS, 400);
    Response r =
        new Response.ResponseBuilder()
            .type(Urn.URN_INVALID_INPUT.toString())
            .title(ITEMNOTFOUND)
            .detail("")
            .status(400)
            .build();
    ComposeException exception = new ComposeException(r);
    Mockito.when(catClient.checkReqItems(any())).thenReturn(Future.failedFuture(exception));
    policyService.createPolicy(
        itemFailure,
        ProviderUserCreate,
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      JsonObject result = response;
                      assertEquals(URN_INVALID_INPUT.toString(), result.getString(TYPE));
                      testContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Testing Duplicate policy")
  void duplicateFailure(VertxTestContext testContext) {
    Map<String, ResourceObj> resp = new HashMap<>();
    ResourceObj resourceObj = new ResourceObj(validCatItem);
    resp.put(resourceObj.getCatId(), resourceObj);
    Mockito.when(catClient.checkReqItems(any())).thenReturn(Future.succeededFuture(resp));
    policyService.createPolicy(
        duplicate,
        ProviderUserCreate,
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      JsonObject result = response;
                      assertEquals(URN_ALREADY_EXISTS.toString(), result.getString(TYPE));
                      testContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Testing unauthorized policy creation - provider")
  // provider role calling create policy is not the owner
  void UnauthorizedPolicyCreation(VertxTestContext testContext) {
    Map<String, ResourceObj> resp = new HashMap<>();
    ResourceObj resourceObj = new ResourceObj(validCatItem);
    resp.put(resourceObj.getCatId(), resourceObj);
    Mockito.when(catClient.checkReqItems(any())).thenReturn(Future.succeededFuture(resp));
    policyService.createPolicy(
        duplicate,
        invalidProvider,
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      JsonObject result = response;
                      assertEquals(UNAUTHORIZED, result.getString(TITLE));
                      testContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Testing unauthorized policy creation - delegate not assigned")
  // delegate role calling create policy and not a delegate to the owner
  void UnauthorizedDelegate(VertxTestContext testContext) {
    Map<String, ResourceObj> resp = new HashMap<>();
    ResourceObj resourceObj = new ResourceObj(validDelegateItem);
    resp.put(resourceObj.getCatId(), resourceObj);
    Mockito.when(catClient.checkReqItems(any())).thenReturn(Future.succeededFuture(resp));
    policyService.createPolicy(
        unAuthDel,
        invalidDelUser,
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      JsonObject result = response;
                      assertEquals(UNAUTHORIZED, result.getString(TITLE));
                      testContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Testing unauthorized policy creation - delegate not assigned")
  // delegate role user calling create policy is a delegate to the provider. provider is not the
  // owner
  void UnauthorizedProvider(VertxTestContext testContext) {
    Map<String, ResourceObj> resp = new HashMap<>();
    ResourceObj resourceObj = new ResourceObj(invalidProviderItem);
    resp.put(resourceObj.getCatId(), resourceObj);
    Mockito.when(catClient.checkReqItems(any())).thenReturn(Future.succeededFuture(resp));
    policyService.createPolicy(
        unAuthProvider,
        validDelUser,
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      JsonObject result = response;
                      assertEquals(UNAUTHORIZED, result.getString(TITLE));
                      testContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Testing unauthorized policy creation - provider does not own some of the resources")
  // Test for provider owns one resource but not the other
  void UnauthorizedMultipleProvider(VertxTestContext testContext) {
    Map<String, ResourceObj> resp = new HashMap<>();
    ResourceObj validResourceObj = new ResourceObj(validDelegateItem);
    ResourceObj invalidResourceObj = new ResourceObj(invalidProviderItem);
    resp.put(validResourceObj.getCatId(), validResourceObj);
    resp.put(invalidResourceObj.getCatId(), invalidResourceObj);
    Mockito.when(catClient.checkReqItems(any())).thenReturn(Future.succeededFuture(resp));
    policyService.createPolicy(
        MultipleProvider,
        ProviderUserCreate,
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      JsonObject result = response;
                      assertEquals(UNAUTHORIZED, result.getString(TITLE));
                      testContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Testing unauthorized policy creation - Not Delegate for some of the resources")
  // test for delegate to one resource owner but not the other
  void UnauthorizedMultipleDelegate(VertxTestContext testContext) {
    Map<String, ResourceObj> resp = new HashMap<>();
    ResourceObj validResourceObj = new ResourceObj(validDelegateItem);
    ResourceObj invalidResourceObj = new ResourceObj(invalidProviderItem);
    resp.put(validResourceObj.getCatId(), validResourceObj);
    resp.put(invalidResourceObj.getCatId(), invalidResourceObj);
    Mockito.when(catClient.checkReqItems(any())).thenReturn(Future.succeededFuture(resp));
    policyService.createPolicy(
        MultipleProvider,
        validDelUser,
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      JsonObject result = response;
                      assertEquals(UNAUTHORIZED, result.getString(TITLE));
                      testContext.completeNow();
                    })));
  }
}
