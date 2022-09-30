package iudx.aaa.server.policy;

import io.vertx.core.CompositeFuture;
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
import iudx.aaa.server.apiserver.CreatePolicyRequest;
import iudx.aaa.server.apiserver.ResourceObj;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.RoleStatus;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.util.ComposeException;
import iudx.aaa.server.apiserver.util.Urn;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.registration.RegistrationServiceImpl;
import iudx.aaa.server.registration.Utils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
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

import static iudx.aaa.server.apiserver.util.Urn.URN_ALREADY_EXISTS;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_INPUT;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_ROLE;
import static iudx.aaa.server.apiserver.util.Urn.URN_SUCCESS;
import static iudx.aaa.server.policy.Constants.DUPLICATE;
import static iudx.aaa.server.policy.Constants.DUPLICATE_POLICY;
import static iudx.aaa.server.policy.Constants.INCORRECT_ITEM_TYPE;
import static iudx.aaa.server.policy.Constants.INVALID_ROLE;
import static iudx.aaa.server.policy.Constants.INVALID_USER;
import static iudx.aaa.server.policy.Constants.ITEMNOTFOUND;
import static iudx.aaa.server.policy.Constants.STATUS;
import static iudx.aaa.server.policy.Constants.TITLE;
import static iudx.aaa.server.policy.Constants.TYPE;
import static iudx.aaa.server.policy.Constants.UNAUTHORIZED;
import static iudx.aaa.server.policy.Constants.VALIDATE_EXPIRY_FAIL;
import static iudx.aaa.server.registration.Utils.SQL_DELETE_ANY_POLICIES;
import static iudx.aaa.server.registration.Utils.SQL_DELETE_DELEGATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
public class CreateUserPolicyTest {
  static String name = RandomStringUtils.randomAlphabetic(10).toLowerCase();
  // Database Properties
  static String url = name + ".com";
  static Future<UUID> orgIdFut;
  private static Logger LOGGER =
      LogManager.getLogger(iudx.aaa.server.policy.CreateUserPolicyTest.class);
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
  private static Future<JsonObject> adminUser;
  private static Future<JsonObject> providerUser;
  private static Future<JsonObject> authDelUser;
  private static Future<JsonObject> delUser;
  private static Future<JsonObject> consumerUser;
  private static JsonObject catOptions;
  private static UUID authSerId;
  private static String authServerURL;
  private static UUID otherSerId;
  private static String otherServerURL;
  private static UUID fakeCatSerId;
  private static String fakeCatSerUrl;
  private static UUID resourceGrpID;
  private static UUID resourceIdOne;
  private static UUID resourceIdTwo;
  private static String resourceGrp;
  private static String resourceOne;
  private static String resourceTwo;
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
    otherServerURL = "other" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";
    fakeCatSerUrl = "cat" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";
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

    // create org
    orgIdFut =
        pgclient.withConnection(
            conn ->
                conn.preparedQuery(Utils.SQL_CREATE_ORG)
                    .execute(Tuple.of(name, url))
                    .map(row -> row.iterator().next().getUUID("id")));

    // create users with diff roles
    adminUser =
        orgIdFut.compose(
            orgId ->
                Utils.createFakeUser(
                    pgclient,
                    orgId.toString(),
                    "",
                    Map.of(Roles.ADMIN, RoleStatus.APPROVED),
                    false));
    providerUser =
        orgIdFut.compose(
            orgId ->
                Utils.createFakeUser(
                    pgclient,
                    orgId.toString(),
                    "",
                    Map.of(Roles.ADMIN, RoleStatus.APPROVED),
                    false));

    authDelUser =
        orgIdFut.compose(
            orgId ->
                Utils.createFakeUser(
                    pgclient,
                    orgId.toString(),
                    "",
                    Map.of(Roles.DELEGATE, RoleStatus.APPROVED),
                    false));
    delUser =
        orgIdFut.compose(
            orgId ->
                Utils.createFakeUser(
                    pgclient,
                    orgId.toString(),
                    "",
                    Map.of(Roles.DELEGATE, RoleStatus.APPROVED),
                    false));
    consumerUser =
        orgIdFut.compose(
            orgId ->
                Utils.createFakeUser(
                    pgclient,
                    orgId.toString(),
                    "",
                    Map.of(Roles.CONSUMER, RoleStatus.APPROVED),
                    false));

    CompositeFuture.all(adminUser, providerUser, authDelUser, delUser, consumerUser)
        .onSuccess(
            succ -> {
              // create all servers
              authSerId = UUID.randomUUID();
              otherSerId = UUID.randomUUID();
              fakeCatSerId = UUID.randomUUID();
              resourceGrpID = UUID.randomUUID();
              resourceIdOne = UUID.randomUUID();
              resourceIdTwo = UUID.randomUUID();
              resourceGrp = "emailsha/xyz/" + otherServerURL + "/rsg";
              resourceOne = resourceGrp + "/ri1";
              resourceTwo = resourceGrp + "/ri2";
              Utils.createFakeResourceServer(pgclient, adminUser.result(), authSerId, authServerURL)
                  .compose(
                      comp ->
                          Utils.createFakeResourceServer(
                              pgclient, adminUser.result(), otherSerId, otherServerURL))
                  .compose(
                      comp ->
                          Utils.createFakeResourceServer(
                              pgclient, adminUser.result(), fakeCatSerId, fakeCatSerUrl))
                  .compose(
                      comp ->
                          Utils.createFakeResourceGroup(
                              pgclient,
                              providerUser.result(),
                              otherSerId,
                              resourceGrpID,
                              resourceGrp))
                  .compose(
                      comp ->
                          Utils.createFakeResource(
                              pgclient,
                              providerUser.result(),
                              resourceIdOne,
                              otherSerId,
                              resourceGrpID,
                              resourceOne))
                  .compose(
                      comp ->
                          Utils.createFakeResource(
                              pgclient,
                              providerUser.result(),
                              resourceIdTwo,
                              otherSerId,
                              resourceGrpID,
                              resourceTwo))
                  .compose(
                      pol ->
                          // policy for provider user by admin user for all servers
                          Utils.createFakePolicy(
                              pgclient,
                              UUID.fromString(providerUser.result().getString("userId")),
                              Constants.itemTypes.RESOURCE_SERVER,
                              UUID.fromString(adminUser.result().getString("userId")),
                              authSerId))
                  .compose(
                      otherPol ->
                          Utils.createFakePolicy(
                              pgclient,
                              UUID.fromString(providerUser.result().getString("userId")),
                              Constants.itemTypes.RESOURCE_SERVER,
                              UUID.fromString(adminUser.result().getString("userId")),
                              otherSerId))
                  .compose(
                      proPol ->
                          Utils.createFakePolicy(
                              pgclient,
                              UUID.fromString(providerUser.result().getString("userId")),
                              Constants.itemTypes.RESOURCE_SERVER,
                              UUID.fromString(adminUser.result().getString("userId")),
                              fakeCatSerId))
                  .compose(
                      proPol ->
                          Utils.createFakePolicy(
                              pgclient,
                              UUID.fromString(authDelUser.result().getString("userId")),
                              Constants.itemTypes.RESOURCE_SERVER,
                              UUID.fromString(adminUser.result().getString("userId")),
                              authSerId))
                  // policy for authDelUser by providerUser
                  .compose(
                      proPol ->
                          Utils.createFakePolicy(
                              pgclient,
                              UUID.fromString(authDelUser.result().getString("userId")),
                              Constants.itemTypes.RESOURCE_GROUP,
                              UUID.fromString(providerUser.result().getString("userId")),
                              otherSerId))
                  // delegation for authDelUser for two servers
                  .compose(
                      del ->
                          Utils.createDelegation(
                              pgclient,
                              UUID.fromString(authDelUser.result().getString("userId")),
                              UUID.fromString(providerUser.result().getString("userId")),
                              authSerId))
                  .compose(
                      del ->
                          Utils.createDelegation(
                              pgclient,
                              UUID.fromString(authDelUser.result().getString("userId")),
                              UUID.fromString(providerUser.result().getString("userId")),
                              otherSerId))
                  .onSuccess(
                      success -> {
                        policyService =
                            new PolicyServiceImpl(
                                pgclient,
                                registrationService,
                                apdService,
                                catalogueClient,
                                authOptions,
                                catOptions);
                        testContext.completeNow();
                      })
                  .onFailure(
                      failure -> {
                        testContext.failNow("failed " + failure.toString());
                      });
            });
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing....");
    Tuple servers = Tuple.of(List.of(authServerURL, otherServerURL).toArray());
    UUID adminId = UUID.fromString(adminUser.result().getString("userId"));
    UUID providerId = UUID.fromString(providerUser.result().getString("userId"));
    UUID delegateId = UUID.fromString(authDelUser.result().getString("userId"));
    Tuple policyOwners = Tuple.of(List.of(adminId, providerId, delegateId).toArray(UUID[]::new));

    List<JsonObject> users =
        List.of(
            providerUser.result(), authDelUser.result(), consumerUser.result(), adminUser.result());
    Utils.deleteFakeResource(pgclient, users)
        .compose(succ -> Utils.deleteFakeResourceGrp(pgclient, users))
        .compose(resGrp -> Utils.deleteFakeResourceServer(pgclient, users))
        .compose(
            ar ->
                pgclient.withConnection(
                    conn -> conn.preparedQuery(SQL_DELETE_DELEGATE).execute(policyOwners)))
        .compose(
            bb ->
                pgclient.withConnection(
                    conn -> conn.preparedQuery(SQL_DELETE_ANY_POLICIES).execute(policyOwners)))
        .onComplete(
            x -> {
              if (x.failed()) {
                LOGGER.warn(x.cause().getMessage());
                LOGGER.error(
                    "Data is NOT deleted after this test since policy table"
                        + " does not allow deletes and therefore cascade delete fails");
              }
              vertxObj.close(testContext.succeeding(response -> testContext.completeNow()));
            });
  }

  @Test
  @DisplayName("Failure - non admin user trying to set resource server policy")
  void roleFailure(VertxTestContext testContext) {

    JsonObject userJson = consumerUser.result();
    User user =
        new User.UserBuilder()
            .keycloakId(userJson.getString("keycloakId"))
            .userId(userJson.getString("userId"))
            .name(userJson.getString("firstName"), userJson.getString("lastName"))
            .roles(List.of(Roles.CONSUMER))
            .build();

    List<CreatePolicyRequest> req =
        CreatePolicyRequest.jsonArrayToList(new JsonArray().add(new JsonObject()));
    policyService.createPolicy(
        req,
        user,
        new JsonObject(),
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      assertEquals(URN_INVALID_ROLE.toString(), response.getString("type"));
                      assertEquals(INVALID_ROLE, response.getString("title"));
                      assertEquals(403, response.getInteger("status"));
                      testContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Failure- Item does not exist")
  void itemFailure(VertxTestContext testContext) {

    JsonObject userJson = providerUser.result();
    User user =
        new User.UserBuilder()
            .keycloakId(userJson.getString("keycloakId"))
            .userId(userJson.getString("userId"))
            .name(userJson.getString("firstName"), userJson.getString("lastName"))
            .roles(List.of(Roles.PROVIDER))
            .build();
    String invalidCatId =
        "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/invalidRes";
    Response r =
        new Response.ResponseBuilder()
            .type(Urn.URN_INVALID_INPUT.toString())
            .title(ITEMNOTFOUND)
            .detail(invalidCatId)
            .status(400)
            .build();
    ComposeException exception = new ComposeException(r);
    Mockito.when(catalogueClient.checkReqItems(any())).thenReturn(Future.failedFuture(exception));

    JsonObject invalidReqItem =
        new JsonObject()
            .put("userId", consumerUser.result().getString("userId"))
            .put("itemId", invalidCatId)
            .put("itemType", "resource_group")
            .put("expiryTime", "")
            .put("constraints", new JsonObject());
    List<CreatePolicyRequest> itemFailure =
        CreatePolicyRequest.jsonArrayToList(new JsonArray().add(invalidReqItem));
    policyService.createPolicy(
        itemFailure,
        user,
        new JsonObject(),
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      assertEquals(URN_INVALID_INPUT.toString(), response.getString(TYPE));
                      assertEquals(ITEMNOTFOUND, response.getString(TITLE));
                      assertEquals(400, response.getInteger(STATUS));
                      testContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Failure - policy already exists")
  void duplicateFailure(VertxTestContext testContext) {

    JsonObject userJson = adminUser.result();
    User user =
        new User.UserBuilder()
            .keycloakId(userJson.getString("keycloakId"))
            .userId(userJson.getString("userId"))
            .name(userJson.getString("firstName"), userJson.getString("lastName"))
            .roles(List.of(Roles.ADMIN))
            .build();

    JsonObject duplicateReq =
        new JsonObject()
            .put("userId", providerUser.result().getString("userId"))
            .put("itemId", otherServerURL)
            .put("itemType", "resource_server")
            .put("expiryTime", "")
            .put("constraints", new JsonObject());
    List<CreatePolicyRequest> duplicateItemFailure =
        CreatePolicyRequest.jsonArrayToList(new JsonArray().add(duplicateReq));

    policyService.createPolicy(
        duplicateItemFailure,
        user,
        new JsonObject(),
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      assertEquals(URN_ALREADY_EXISTS.toString(), response.getString(TYPE));
                      assertEquals(DUPLICATE_POLICY, response.getString(TITLE));
                      assertEquals(409, response.getInteger(STATUS));
                      testContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Failure - user does not exist")
  void userFailure(VertxTestContext testContext) {

    JsonObject userJson = providerUser.result();
    User user =
        new User.UserBuilder()
            .keycloakId(userJson.getString("keycloakId"))
            .userId(userJson.getString("userId"))
            .name(userJson.getString("firstName"), userJson.getString("lastName"))
            .roles(List.of(Roles.PROVIDER))
            .build();

    JsonObject userFailReq =
        new JsonObject()
            .put("userId", UUID.randomUUID())
            .put("itemId", resourceGrp)
            .put("itemType", "resource_group")
            .put("expiryTime", "")
            .put("constraints", new JsonObject());
    List<CreatePolicyRequest> userFailReqItem =
        CreatePolicyRequest.jsonArrayToList(new JsonArray().add(userFailReq));

    JsonObject validCatItem =
        new JsonObject()
            .put("cat_id", resourceOne)
            .put("itemType", "resource")
            .put("owner_id", providerUser.result().getString("userId"))
            .put("id", resourceIdOne.toString())
            .put("resource_group_id", resourceGrpID.toString())
            .put("resource_server_id", otherSerId.toString());

    ResourceObj resourceObj = new ResourceObj(validCatItem);
    Map<String, ResourceObj> resp = new HashMap<>();
    resp.put(resourceObj.getCatId(), resourceObj);
    Mockito.when(catalogueClient.checkReqItems(any())).thenReturn(Future.succeededFuture(resp));
    policyService.createPolicy(
        userFailReqItem,
        user,
        new JsonObject(),
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      assertEquals(URN_INVALID_INPUT.toString(), response.getString(TYPE));
                      assertEquals(INVALID_USER, response.getString(TITLE));
                      assertEquals(400, response.getInteger(STATUS));
                      testContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Failure - expiry is in the past")
  void policyExpFailure(VertxTestContext testContext) {

    JsonObject userJson = providerUser.result();
    User user =
        new User.UserBuilder()
            .keycloakId(userJson.getString("keycloakId"))
            .userId(userJson.getString("userId"))
            .name(userJson.getString("firstName"), userJson.getString("lastName"))
            .roles(List.of(Roles.PROVIDER))
            .build();

    JsonObject polExpFail =
        new JsonObject()
            .put("userId", consumerUser.result().getString("userId"))
            .put("itemId", resourceGrp)
            .put("itemType", "resource_group")
            .put("expiryTime", "2010-01-01T01:01:01")
            .put("constraints", new JsonObject());
    List<CreatePolicyRequest> userFailReqItem =
        CreatePolicyRequest.jsonArrayToList(new JsonArray().add(polExpFail));

    JsonObject validCatItem =
        new JsonObject()
            .put("cat_id", resourceOne)
            .put("itemType", "resource")
            .put("owner_id", providerUser.result().getString("userId"))
            .put("id", resourceIdOne.toString())
            .put("resource_group_id", resourceGrpID.toString())
            .put("resource_server_id", otherSerId.toString());

    ResourceObj resourceObj = new ResourceObj(validCatItem);
    Map<String, ResourceObj> resp = new HashMap<>();
    resp.put(resourceObj.getCatId(), resourceObj);
    Mockito.when(catalogueClient.checkReqItems(any())).thenReturn(Future.succeededFuture(resp));
    policyService.createPolicy(
        userFailReqItem,
        user,
        new JsonObject(),
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      assertEquals(URN_INVALID_INPUT.toString(), response.getString(TYPE));
                      assertEquals(VALIDATE_EXPIRY_FAIL, response.getString(TITLE));
                      assertEquals(400, response.getInteger(STATUS));
                      testContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Failure - provider user not the owner")
  void UnauthorizedPolicyCreation(VertxTestContext testContext) {
    JsonObject userJson = authDelUser.result();
    User invalidProvider =
        new User.UserBuilder()
            .keycloakId(userJson.getString("keycloakId"))
            .userId(userJson.getString("userId"))
            .name(userJson.getString("firstName"), userJson.getString("lastName"))
            .roles(List.of(Roles.PROVIDER))
            .build();

    JsonObject validCatItem =
        new JsonObject()
            .put("cat_id", resourceGrp)
            .put("itemType", "resource_group")
            .put("owner_id", providerUser.result().getString("userId"))
            .put("id", resourceGrpID.toString())
            .put("resource_server_id", otherSerId);

    ResourceObj resourceObj = new ResourceObj(validCatItem);
    Map<String, ResourceObj> resp = new HashMap<>();
    resp.put(resourceObj.getCatId(), resourceObj);
    Mockito.when(catalogueClient.checkReqItems(any())).thenReturn(Future.succeededFuture(resp));

    JsonObject reqItem =
        new JsonObject()
            .put("userId", consumerUser.result().getString("userId"))
            .put("itemId", resourceGrp)
            .put("itemType", "resource_group")
            .put("expiryTime", "")
            .put("constraints", new JsonObject());
    List<CreatePolicyRequest> validReq =
        CreatePolicyRequest.jsonArrayToList(new JsonArray().add(reqItem));

    policyService.createPolicy(
        validReq,
        invalidProvider,
        new JsonObject(),
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      assertEquals(URN_INVALID_INPUT.toString(), response.getString(TYPE));
                      assertEquals(UNAUTHORIZED, response.getString(TITLE));
                      assertEquals(403, response.getInteger(STATUS));
                      testContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Failure - delegate's provider is not the owner")
  void UnauthorizedDelegate(VertxTestContext testContext) {
    JsonObject userJson = authDelUser.result();
    User invalidProvider =
        new User.UserBuilder()
            .keycloakId(userJson.getString("keycloakId"))
            .userId(userJson.getString("userId"))
            .name(userJson.getString("firstName"), userJson.getString("lastName"))
            .roles(List.of(Roles.DELEGATE))
            .build();

    JsonObject validCatItem =
        new JsonObject()
            .put("cat_id", resourceGrp)
            .put("itemType", "resource_group")
            .put("owner_id", providerUser.result().getString("userId"))
            .put("id", resourceGrpID.toString())
            .put("resource_server_id", otherSerId);

    ResourceObj resourceObj = new ResourceObj(validCatItem);
    Map<String, ResourceObj> resp = new HashMap<>();
    resp.put(resourceObj.getCatId(), resourceObj);
    Mockito.when(catalogueClient.checkReqItems(any())).thenReturn(Future.succeededFuture(resp));

    JsonObject reqItem =
        new JsonObject()
            .put("userId", consumerUser.result().getString("userId"))
            .put("itemId", resourceGrp)
            .put("itemType", "resource_group")
            .put("expiryTime", "")
            .put("constraints", new JsonObject());
    List<CreatePolicyRequest> validReq =
        CreatePolicyRequest.jsonArrayToList(new JsonArray().add(reqItem));

    policyService.createPolicy(
        validReq,
        invalidProvider,
        new JsonObject().put("providerId", adminUser.result().getString("userId")),
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      assertEquals(URN_INVALID_INPUT.toString(), response.getString(TYPE));
                      assertEquals(UNAUTHORIZED, response.getString(TITLE));
                      assertEquals(403, response.getInteger(STATUS));
                      testContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Duplicate IDs in request array")
  void failDuplicateIds(VertxTestContext testContext) {
    {
      JsonObject userJson = providerUser.result();
      User user =
          new User.UserBuilder()
              .keycloakId(userJson.getString("keycloakId"))
              .userId(userJson.getString("userId"))
              .name(userJson.getString("firstName"), userJson.getString("lastName"))
              .roles(List.of(Roles.PROVIDER))
              .build();

      JsonObject validReq =
          new JsonObject()
              .put("userId", consumerUser.result().getString("userId"))
              .put("itemId", resourceTwo)
              .put("itemType", "resource")
              .put("expiryTime", "")
              .put("constraints", new JsonObject());
      List<CreatePolicyRequest> validCreatePolicyRequest =
          CreatePolicyRequest.jsonArrayToList(new JsonArray().add(validReq).add(validReq));

      policyService.createPolicy(
          validCreatePolicyRequest,
          user,
          new JsonObject(),
          testContext.succeeding(
              response ->
                  testContext.verify(
                      () -> {
                        assertEquals(URN_INVALID_INPUT.toString(), response.getString(TYPE));
                        assertEquals(DUPLICATE, response.getString(TITLE));
                        assertEquals(400, response.getInteger(STATUS));
                        testContext.completeNow();
                      })));
    }
  }

  @Test
  @DisplayName("Success - policy creation for consumer")
  void successPolicy(VertxTestContext testContext) {

    JsonObject userJson = providerUser.result();
    User user =
        new User.UserBuilder()
            .keycloakId(userJson.getString("keycloakId"))
            .userId(userJson.getString("userId"))
            .name(userJson.getString("firstName"), userJson.getString("lastName"))
            .roles(List.of(Roles.PROVIDER))
            .build();

    JsonObject validCatItem =
        new JsonObject()
            .put("cat_id", resourceOne)
            .put("itemType", "resource")
            .put("owner_id", providerUser.result().getString("userId"))
            .put("id", resourceIdOne.toString())
            .put("resource_group_id", resourceGrpID.toString())
            .put("resource_server_id", otherSerId.toString());

    ResourceObj resourceObj = new ResourceObj(validCatItem);
    Map<String, ResourceObj> resp = new HashMap<>();
    resp.put(resourceObj.getCatId(), resourceObj);
    Mockito.when(catalogueClient.checkReqItems(any())).thenReturn(Future.succeededFuture(resp));

    JsonObject validReq =
        new JsonObject()
            .put("userId", consumerUser.result().getString("userId"))
            .put("itemId", resourceOne)
            .put("itemType", "resource")
            .put("expiryTime", "")
            .put("constraints", new JsonObject());
    List<CreatePolicyRequest> validCreatePolicyRequest =
        CreatePolicyRequest.jsonArrayToList(new JsonArray().add(validReq));

    policyService.createPolicy(
        validCreatePolicyRequest,
        user,
        new JsonObject(),
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
                      assertEquals("added policies", response.getString(TITLE));
                      assertEquals(200, response.getInteger(STATUS));
                      testContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Testing Failure for RES_Grp(Item does not exist))")
  void itemFailure4Resgp(VertxTestContext testContext) {
    String itemId =
        RandomStringUtils.randomAlphabetic(2)
            + "/"
            + RandomStringUtils.randomAlphabetic(2)
            + "/"
            + RandomStringUtils.randomAlphabetic(2);

      JsonObject userJson = providerUser.result();
      User user =
          new User.UserBuilder()
              .keycloakId(userJson.getString("keycloakId"))
              .userId(userJson.getString("userId"))
              .name(userJson.getString("firstName"), userJson.getString("lastName"))
              .roles(List.of(Roles.PROVIDER))
              .build();

    UUID userId = UUID.randomUUID();
    JsonObject invalidReqItem4Resgrp =
        new JsonObject()
            .put("userId", userId)
            .put("itemId", itemId)
            .put("itemType", "resource_group")
            .put("expiryTime", "")
            .put("constraints", new JsonObject());

    List<CreatePolicyRequest> ItemfailureResGroup =
        CreatePolicyRequest.jsonArrayToList(new JsonArray().add(invalidReqItem4Resgrp));
    policyService.createPolicy(
        ItemfailureResGroup,
        user,
        new JsonObject(),
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      JsonObject result = response;
                      assertEquals(URN_INVALID_INPUT.toString(), result.getString(TYPE));
                      assertEquals(INCORRECT_ITEM_TYPE, result.getString(TITLE));
                      assertEquals(INCORRECT_ITEM_TYPE, result.getString("detail"));
                      assertEquals(400, result.getInteger(STATUS));
                      testContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Testing Failure for RES_Id(Item does not exist))")
  void itemFailure4ResId(VertxTestContext testContext) {
    String itemId =
        RandomStringUtils.randomAlphabetic(2)
            + "/"
            + RandomStringUtils.randomAlphabetic(2)
            + "/"
            + RandomStringUtils.randomAlphabetic(2);

      JsonObject userJson = providerUser.result();
      User user =
          new User.UserBuilder()
              .keycloakId(userJson.getString("keycloakId"))
              .userId(userJson.getString("userId"))
              .name(userJson.getString("firstName"), userJson.getString("lastName"))
              .roles(List.of(Roles.PROVIDER))
              .build();

    UUID userId = UUID.randomUUID();
    JsonObject invalidReqItem4Res =
        new JsonObject()
            .put("userId", userId)
            .put("itemId", itemId)
            .put("itemType", "resource")
            .put("expiryTime", "")
            .put("constraints", new JsonObject());

    List<CreatePolicyRequest> ItemfailureRes =
        CreatePolicyRequest.jsonArrayToList(new JsonArray().add(invalidReqItem4Res));
    policyService.createPolicy(
        ItemfailureRes,
        user,
        new JsonObject(),
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      JsonObject result = response;
                      assertEquals(URN_INVALID_INPUT.toString(), result.getString(TYPE));
                      assertEquals(INCORRECT_ITEM_TYPE, result.getString(TITLE));
                      assertEquals(INCORRECT_ITEM_TYPE, result.getString("detail"));
                      assertEquals(400, result.getInteger(STATUS));
                      testContext.completeNow();
                    })));
  }
}
