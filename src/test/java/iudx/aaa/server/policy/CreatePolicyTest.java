package iudx.aaa.server.policy;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import iudx.aaa.server.apd.ApdService;
import iudx.aaa.server.apiserver.ResourceObj;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.util.ComposeException;
import iudx.aaa.server.apiserver.util.Urn;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.registration.RegistrationService;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static iudx.aaa.server.apd.Constants.ERR_TITLE_INVALID_REQUEST_ID;
import static iudx.aaa.server.apiserver.util.Urn.URN_ALREADY_EXISTS;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_INPUT;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_ROLE;
import static iudx.aaa.server.policy.Constants.ITEMNOTFOUND;
import static iudx.aaa.server.policy.Constants.NIL_UUID;
import static iudx.aaa.server.policy.Constants.TITLE;
import static iudx.aaa.server.policy.Constants.TYPE;
import static iudx.aaa.server.policy.Constants.UNAUTHORIZED;
import static iudx.aaa.server.policy.Constants.URL;
import static iudx.aaa.server.policy.TestRequest.MultipleProvider;
import static iudx.aaa.server.policy.TestRequest.consumerUser;
import static iudx.aaa.server.policy.TestRequest.duplicate;
import static iudx.aaa.server.policy.TestRequest.invalidApdPolicyId;
import static iudx.aaa.server.policy.TestRequest.invalidApdPolicyItemType;
import static iudx.aaa.server.policy.TestRequest.invalidApdTrustee;
import static iudx.aaa.server.policy.TestRequest.invalidDelUser;
import static iudx.aaa.server.policy.TestRequest.invalidProvider;
import static iudx.aaa.server.policy.TestRequest.invalidProviderItem;
import static iudx.aaa.server.policy.TestRequest.invalidTrustee;
import static iudx.aaa.server.policy.TestRequest.invalidTrusteeItem;
import static iudx.aaa.server.policy.TestRequest.itemFailure;
import static iudx.aaa.server.policy.TestRequest.validReq;
import static iudx.aaa.server.policy.TestRequest.unAuthProvider;
import static iudx.aaa.server.policy.TestRequest.validCatItem;
import static iudx.aaa.server.policy.TestRequest.validDelUser;
import static iudx.aaa.server.policy.TestRequest.validDelegateItem;
import static iudx.aaa.server.policy.TestRequest.validProvider;
import static iudx.aaa.server.policy.TestRequest.validTrusteeUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
public class CreatePolicyTest {
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
  private static Vertx vertxObj;
  private static JsonObject authOptions;
  private static JsonObject catOptions;
  private static RegistrationService registrationService;
  private static ApdService apdService = Mockito.mock(ApdService.class);
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


                  catClient = Mockito.mock(CatalogueClient.class);
                  mockRegistrationFactory = new MockRegistrationFactory();
                  registrationService = mockRegistrationFactory.getInstance();
                  policyService = new PolicyServiceImpl(pgclient, registrationService, apdService,
                      catClient, authOptions, catOptions);
                  testContext.completeNow();
          ;
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing....");
    vertxObj.close(testContext.succeeding(response -> testContext.completeNow()));
  }

  /*policies needed
        user 1 is the admin for all servers and is the provider for itemid1/catid1
         1. user1 creates policy for user 2 for authserver,resserver and catserver as admin
         2. user1 creates policy for user 2 for item1 as provider
         3. user1 creates delegation for user 2 on authserver,resserver and catserver as provider
         4. user2 creates delegation for user3 on any server
         5. user1 creates a policy for user 3 for authserver as an admin
         6. user3 must not be any kind of delegate to user1
         7. user3 must not have any active policies on item1
         8. user1 creates policy for user3 for resource Serve

         For createPolicyTest
         userId1 844e251b-574b-46e6-9247-f76f1f70a637 (all roles)
         userId2 d1262b13-1cbe-4b66-a9b2-96df86437683 (provider/delegate)
         userId3 a13eb955-c691-4fd3-b200-f18bc78810b5 (all roles - remove admin)
         item1  iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/testing-insert-rsg
*/

  @Test
  @DisplayName("Testing Failure(Role is consumer))")
  void roleFailure(VertxTestContext testContext) {
    policyService.createPolicy(
            validReq,
        consumerUser,
            new JsonObject(),
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      JsonObject result = response;
                      assertEquals(URN_INVALID_ROLE.toString(), result.getString(TYPE));
                      testContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Testing Failure(Item does not exist))")
  void itemFailure(VertxTestContext testContext) {

    Response r =
        new Response.ResponseBuilder()
            .type(Urn.URN_INVALID_INPUT.toString())
            .title(ITEMNOTFOUND)
            .detail("iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/invalidCatId")
            .status(400)
            .build();
    ComposeException exception = new ComposeException(r);
    Mockito.when(catClient.checkReqItems(any())).thenReturn(Future.failedFuture(exception));
    policyService.createPolicy(
        itemFailure,
            validProvider,
            new JsonObject(),
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
            validProvider,
            new JsonObject(),
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
            validReq,
        invalidProvider,
            new JsonObject(),
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
    ResourceObj resourceObj = new ResourceObj(validCatItem);
    JsonObject providerHeader = new JsonObject().put("providerId","a13eb955-c691-4fd3-b200-f18bc78810b5");
    resp.put(resourceObj.getCatId(), resourceObj);
    Mockito.when(catClient.checkReqItems(any())).thenReturn(Future.succeededFuture(resp));
    policyService.createPolicy(
            validReq,
        invalidDelUser,
            providerHeader,
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
  @DisplayName("Testing unauthorized policy creation - invalid delegate")
  // delegate role user calling create policy is a delegate to the provider. provider is not the
  // owner
  void UnauthorizedProvider(VertxTestContext testContext) {
    Map<String, ResourceObj> resp = new HashMap<>();
    ResourceObj resourceObj = new ResourceObj(invalidProviderItem);
    resp.put(resourceObj.getCatId(), resourceObj);
    JsonObject providerHeader = new JsonObject().put("providerId","844e251b-574b-46e6-9247-f76f1f70a637");
    Mockito.when(catClient.checkReqItems(any())).thenReturn(Future.succeededFuture(resp));
    policyService.createPolicy(
        unAuthProvider,
        validDelUser,
            providerHeader,
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
            validProvider,
            new JsonObject(),
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
            new JsonObject(),
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
    @DisplayName("Testing  APD policy creation as incorrect user (itemType apd)")
        // test for delegate to one resource owner but not the other
    void invalidApdPolicyRole(VertxTestContext testContext) {

        policyService.createPolicy(
                invalidTrustee,
                validDelUser,
                new JsonObject(),
                testContext.succeeding(
                        response ->
                                testContext.verify(
                                        () -> {
                                            JsonObject result = response;
                                            assertEquals(URN_INVALID_ROLE.toString(), result.getString(TYPE));
                                            testContext.completeNow();
                                        })));
    }


    @Test
    @DisplayName("Testing invalid APD policy creation - apd does not exist ")
        // test for delegate to one resource owner but not the other
    void invalidApdId(VertxTestContext testContext) {

        Mockito.doAnswer(i -> {
            Promise<JsonObject> p = i.getArgument(2);
            Response r =
                    new Response.ResponseBuilder()
                            .type(URN_INVALID_INPUT)
                            .title(ERR_TITLE_INVALID_REQUEST_ID)
                            .detail("authdev.iudx.ip")
                            .status(400)
                            .build();
            ComposeException exception = new ComposeException(r);
            p.fail(exception);
            return i.getMock();
        }).when(apdService).getApdDetails(any(), any(), any());

        policyService.createPolicy(
                invalidApdTrustee,
                validTrusteeUser,
                new JsonObject(),
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
    @DisplayName("Testing apd Policy table - incorrect apd ID ")
        // test for delegate to one resource owner but not the other
    void invalidAPDPolicyId(VertxTestContext testContext) {

        Mockito.doAnswer(i -> {
            Promise<JsonObject> p = i.getArgument(2);
            Response r =
                    new Response.ResponseBuilder()
                            .type(URN_INVALID_INPUT)
                            .title(ERR_TITLE_INVALID_REQUEST_ID)
                            .detail("authdev.iudx.ip")
                            .status(400)
                            .build();
            ComposeException exception = new ComposeException(r);
            p.fail(exception);
            return i.getMock();
        }).when(apdService).getApdDetails(any(), any(), any());

        policyService.createPolicy(
                invalidApdPolicyId,
                validProvider,
                new JsonObject(),
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
    @DisplayName("Testing apd Policy table - incorrect apd ID ")
        // test for delegate to one resource owner but not the other
    void invalidAPDPolicyApdId(VertxTestContext testContext) {

        Mockito.doAnswer(i -> {
            Promise<JsonObject> p = i.getArgument(2);
            Response r =
                    new Response.ResponseBuilder()
                            .type(URN_INVALID_INPUT)
                            .title(ERR_TITLE_INVALID_REQUEST_ID)
                            .detail("authdev.iudx.ip")
                            .status(400)
                            .build();
            ComposeException exception = new ComposeException(r);
            p.fail(exception);
            return i.getMock();
        }).when(apdService).getApdDetails(any(), any(), any());

        policyService.createPolicy(
                invalidApdPolicyId,
                validProvider,
                new JsonObject(),
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
    @DisplayName("Testing apd Policy table - incorrect resource ID ")
        // test for delegate to one resource owner but not the other
    void invalidAPDPolicyItemId(VertxTestContext testContext) {

    Mockito.doAnswer(
            i -> {
              Promise<JsonObject> p = i.getArgument(2);
              List<String> urls = i.getArgument(0);
              JsonObject response = new JsonObject();
              urls.forEach(
                  url -> {
                    JsonObject apdResponse = new JsonObject();
                    apdResponse.put("owner",new JsonObject().put("id",NIL_UUID)
                            .put("name",new JsonObject()).put("email","abc@xyz.com"));
                    apdResponse.put("url", url);
                    apdResponse.put("status", "ACTIVE");
                    apdResponse.put("name", "someName");
                    apdResponse.put("id", NIL_UUID);
                    response.put(url,apdResponse);
                  });
             p.complete(response);
            return i.getMock();
            })
        .when(apdService)
        .getApdDetails(any(), any(), any());

        Response r =
                new Response.ResponseBuilder()
                        .type(Urn.URN_INVALID_INPUT.toString())
                        .title(ITEMNOTFOUND)
                        .detail("iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/invalidRes")
                        .status(400)
                        .build();
        ComposeException exception = new ComposeException(r);
        Mockito.when(catClient.checkReqItems(any())).thenReturn(Future.failedFuture(exception));

        policyService.createPolicy(
                invalidApdPolicyItemType,
                validProvider,
                new JsonObject(),
                testContext.succeeding(
                        response ->
                                testContext.verify(
                                        () -> {

                                            JsonObject result = response;
                                            assertEquals(URN_INVALID_INPUT.toString(), result.getString(TYPE));
                                            assertEquals("Item does not exist", result.getString(TITLE));
                                            testContext.completeNow();
                                        })));
    }

}
