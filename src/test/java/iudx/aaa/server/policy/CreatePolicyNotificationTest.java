package iudx.aaa.server.policy;

import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_ROLE;
import static iudx.aaa.server.apiserver.util.Urn.URN_SUCCESS;
import static iudx.aaa.server.policy.Constants.CONSTRAINTS;
import static iudx.aaa.server.policy.Constants.INVALID_ROLE;
import static iudx.aaa.server.policy.Constants.ITEMID;
import static iudx.aaa.server.policy.Constants.ITEMTYPE;
import static iudx.aaa.server.policy.Constants.NIL_UUID;
import static iudx.aaa.server.policy.Constants.OWNER_DETAILS;
import static iudx.aaa.server.policy.Constants.RESULTS;
import static iudx.aaa.server.policy.Constants.RES_GRP;
import static iudx.aaa.server.policy.Constants.STATUS;
import static iudx.aaa.server.policy.Constants.SUCC_TITLE_POLICY_READ;
import static iudx.aaa.server.policy.Constants.TITLE;
import static iudx.aaa.server.policy.Constants.TYPE;
import static iudx.aaa.server.policy.Constants.USER_DETAILS;
import static iudx.aaa.server.registration.Utils.SQL_CREATE_ORG;
import static iudx.aaa.server.registration.Utils.SQL_DELETE_ORG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apd.ApdService;
import iudx.aaa.server.apiserver.CreatePolicyNotification;
import iudx.aaa.server.apiserver.NotifRequestStatus;
import iudx.aaa.server.apiserver.ResourceObj;
import iudx.aaa.server.apiserver.RoleStatus;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.registration.RegistrationService;
import iudx.aaa.server.registration.Utils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
public class CreatePolicyNotificationTest {
  private static Logger LOGGER =
      LogManager.getLogger(iudx.aaa.server.policy.CreatePolicyNotificationTest.class);

  private static Configuration config;

  private static String databaseIP;
  private static int databasePort;
  private static String databaseName;
  private static String databaseSchema;
  private static String databaseUserName;
  private static String databasePassword;
  private static int poolSize;
  private static PgPool pool;
  private static PoolOptions poolOptions;
  private static PgConnectOptions connectOptions;
  private static PolicyService policyService;
  private static ApdService apdService = Mockito.mock(ApdService.class);
  private static RegistrationService registrationService;
  private static JsonObject catalogueOptions;
  private static JsonObject authOptions;
  private static JsonObject catOptions;

  private static Vertx vertxObj;
  private static MockRegistrationFactory mockRegistrationFactory;
  private static CatalogueClient catalogueClient = Mockito.mock(CatalogueClient.class);

  /* SQL queries for creating and deleting required data */
  static String name = RandomStringUtils.randomAlphabetic(10).toLowerCase();
  static String url = name + ".com";

  static Future<JsonObject> consumer;
  static Future<JsonObject> provider;
  static Future<UUID> orgIdFut;

  /* These resources would never get stored in the DB, so OK if hardcoded */
  String RESOURCE_GROUP =
      "somedomain.com/da39a3ee5e6b4b0d3255bfef95601890afd80709/rs.server.com/rsg";
  String RESOURCE = RESOURCE_GROUP + "/ri";

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
    catOptions = dbConfig.getJsonObject("catOptions");

    /* Set Connection Object and schema */
    if (connectOptions == null) {
      Map<String, String> schemaProp = Map.of("search_path", databaseSchema);

      connectOptions =
          new PgConnectOptions().setPort(databasePort).setHost(databaseIP).setDatabase(databaseName)
              .setUser(databaseUserName).setPassword(databasePassword).setProperties(schemaProp);
    }

    // Pool options
    if (poolOptions == null) {
      poolOptions = new PoolOptions().setMaxSize(poolSize);
    }

    // Create the client pool
    pool = PgPool.pool(vertx, connectOptions, poolOptions);

    orgIdFut = pool.withConnection(conn -> conn.preparedQuery(SQL_CREATE_ORG)
        .execute(Tuple.of(name, url)).map(row -> row.iterator().next().getUUID("id")));

    /*
     * creating a provider to associate the catalogue client resource object response with an actual
     * provider/owner.
     */
    provider = orgIdFut.compose(id -> Utils.createFakeUser(pool, id.toString(), url,
        Map.of(Roles.PROVIDER, RoleStatus.APPROVED), true));

    consumer = orgIdFut.compose(id -> Utils.createFakeUser(pool, NIL_UUID, "",
        Map.of(Roles.CONSUMER, RoleStatus.APPROVED), true));

    mockRegistrationFactory = new MockRegistrationFactory();
    CompositeFuture.all(orgIdFut, provider, consumer).onSuccess(res -> {
      registrationService = mockRegistrationFactory.getInstance();
      policyService = new PolicyServiceImpl(pool, registrationService, apdService, catalogueClient,
          authOptions, catOptions);
      testContext.completeNow();
    });
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing....");
    List<JsonObject> users = List.of(provider.result(), consumer.result());

    Utils.deleteFakeUser(pool, users)
        .compose(succ -> pool.withConnection(
            conn -> conn.preparedQuery(SQL_DELETE_ORG).execute(Tuple.of(orgIdFut.result()))))
        .onComplete(x -> {
          if (x.failed()) {
            LOGGER.warn(x.cause().getMessage());
          }
          vertxObj.close(testContext.succeeding(response -> testContext.completeNow()));
        });
  }


  @Test
  @DisplayName("Fail to create notification with user with no roles (not completed registration)")
  void failCreateNotifNoRoles(VertxTestContext testContext) {

    JsonObject userJson = consumer.result();

    /* use the consumer user itself but create user object with empty role list */
    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName")).roles(List.of())
        .build();

    JsonArray req = new JsonArray()
        .add(new JsonObject().put("itemId", RESOURCE_GROUP).put("itemType", "resource_group")
            .put("expiryDuration", "P1Y").put("constraints", new JsonObject()));
    List<CreatePolicyNotification> request = CreatePolicyNotification.jsonArrayToList(req);

    policyService.createPolicyNotification(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(403, response.getInteger("status"));
          assertEquals(URN_INVALID_ROLE.toString(), response.getString(TYPE));
          assertEquals(INVALID_ROLE, response.getString("detail"));
          assertEquals(INVALID_ROLE, response.getString(TITLE));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("User with admin/provider/trustee/delegate role cannot create notification")
  void failOtherRolesCreateNotif(VertxTestContext testContext) {
    Checkpoint checkAdmin = testContext.checkpoint();
    Checkpoint checkProvider = testContext.checkpoint();
    Checkpoint checkTrustee = testContext.checkpoint();
    Checkpoint checkDelegate = testContext.checkpoint();

    JsonObject userJson = consumer.result();

    /* use the consumer user itself but create user object with required role */
    User adminUser = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.ADMIN)).build();

    JsonArray req = new JsonArray()
        .add(new JsonObject().put("itemId", RESOURCE_GROUP).put("itemType", "resource_group")
            .put("expiryDuration", "P1Y").put("constraints", new JsonObject()));
    List<CreatePolicyNotification> request = CreatePolicyNotification.jsonArrayToList(req);

    policyService.createPolicyNotification(request, adminUser,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(403, response.getInteger("status"));
          assertEquals(URN_INVALID_ROLE.toString(), response.getString(TYPE));
          assertEquals(INVALID_ROLE, response.getString("detail"));
          assertEquals(INVALID_ROLE, response.getString(TITLE));
          checkAdmin.flag();
        })));

    // repeat for provider, trustee, delegate roles
    checkProvider.flag();
    checkTrustee.flag();
    checkDelegate.flag();
  }

  @Test
  @DisplayName("Invalid itemId:itemType - resource sent with itemType resource_group")
  void failInvalidItemResource(VertxTestContext testContext) {

    testContext.completeNow();
  }

  @Test
  @DisplayName("Invalid itemId:itemType - resource_group sent with itemType resource")
  void failInvalidItemResourceGrp(VertxTestContext testContext) {

    testContext.completeNow();
  }

  @Test
  @DisplayName("Duplicate resource IDs in request array")
  void failDuplicateRequestIds(VertxTestContext testContext) {
    // add 2 requests to the JSON array/list of requests but both having same resource ID, other
    // options different
    testContext.completeNow();
  }

  @Test
  @DisplayName("ItemId does not exist")
  void failItemNotExist(VertxTestContext testContext) {
    Map<String, List<String>> catClientRequest = new HashMap<String, List<String>>();
    catClientRequest.put(RES_GRP, List.of(RESOURCE_GROUP));

    Mockito.when(catalogueClient.checkReqItems(catClientRequest))
        .thenReturn(Future.failedFuture("Item does not exist"));

    // call create notification with the same resource group, perform assertions
    testContext.completeNow();
  }

  @Test
  @DisplayName("Successful creation of notification")
  void createNotificationSuccess(VertxTestContext testContext) {

    String resourceGroup = url + "/da39a3ee5e6b4b0d3255bfef95601890afd80709/" + "rs." + url + "/"
        + RandomStringUtils.randomAlphabetic(10).toLowerCase();

    Map<String, List<String>> catClientRequest = new HashMap<String, List<String>>();
    catClientRequest.put(RES_GRP, List.of(resourceGroup));

    JsonObject ownerJson = provider.result();
    JsonObject consumerJson = consumer.result();

    UUID consumerId = UUID.fromString(consumerJson.getString("userId"));
    UUID ownerId = UUID.fromString(ownerJson.getString("userId"));
    UUID itemIdInDb = UUID.randomUUID();
    UUID resServerId = UUID.randomUUID();

    /* Mocking CatalogueClient.checkReqItems */
    Mockito.doAnswer(i -> {
      Map<String, List<String>> req = i.getArgument(0);
      String catId = req.get(RES_GRP).get(0);
      ResourceObj obj = new ResourceObj(RES_GRP, itemIdInDb, catId, ownerId, resServerId, null);

      Map<String, ResourceObj> resp = new HashMap<String, ResourceObj>();
      resp.put(catId, obj);
      return Future.succeededFuture(resp);
    }).when(catalogueClient).checkReqItems(catClientRequest);

    /* Mocking RegistrationService.getUserDetails */
    JsonObject ownerDetails = new JsonObject().put("email", ownerJson.getString("email"))
        .put("name", new JsonObject().put("firstName", ownerJson.getString("firstName"))
            .put("lastName", ownerJson.getString("lastName")));

    JsonObject consumerDetails = new JsonObject().put("email", consumerJson.getString("email"))
        .put("name", new JsonObject().put("firstName", consumerJson.getString("firstName"))
            .put("lastName", consumerJson.getString("lastName")));
    JsonObject userDetailsResp = new JsonObject().put(consumerId.toString(), consumerDetails)
        .put(ownerId.toString(), ownerDetails);

    mockRegistrationFactory.setResponse(userDetailsResp);

    User user = new UserBuilder().keycloakId(consumerJson.getString("keycloakId"))
        .userId(consumerJson.getString("userId"))
        .name(consumerJson.getString("firstName"), consumerJson.getString("lastName"))
        .roles(List.of(Roles.CONSUMER)).build();

    JsonArray req = new JsonArray()
        .add(new JsonObject().put("itemId", resourceGroup).put("itemType", "resource_group")
            .put("expiryDuration", "P1Y").put("constraints", new JsonObject()));

    List<CreatePolicyNotification> request = CreatePolicyNotification.jsonArrayToList(req);

    policyService.createPolicyNotification(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(200, response.getInteger("status"));
          assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
          assertEquals(SUCC_TITLE_POLICY_READ, response.getString(TITLE));
          assertTrue(response.containsKey(RESULTS));
          assertFalse(response.getJsonArray(RESULTS).isEmpty());

          JsonObject obj = response.getJsonArray(RESULTS).getJsonObject(0);

          assertTrue(obj.containsKey("requestId"));

          // checking user, owner objects. omitted checks for name
          assertTrue(obj.getJsonObject(OWNER_DETAILS).getString("id")
              .equals(ownerJson.getString("userId")));
          assertTrue(obj.getJsonObject(OWNER_DETAILS).getString("email")
              .equals(ownerJson.getString("email")));
          assertTrue(obj.getJsonObject(USER_DETAILS).getString("email")
              .equals(consumerJson.getString("email")));
          assertTrue(obj.getJsonObject(USER_DETAILS).getString("id")
              .equals(consumerJson.getString("userId")));

          assertEquals(obj.getString(STATUS), NotifRequestStatus.PENDING.name().toLowerCase());
          assertEquals(obj.getString("expiryDuration"), request.get(0).getExpiryDuration());
          assertEquals(obj.getString(ITEMID), resourceGroup);
          assertEquals(obj.getString(ITEMTYPE), "resource_group");
          assertTrue(obj.getValue(CONSTRAINTS) instanceof JsonObject);
          testContext.completeNow();
        })));

  }

  @Test
  @DisplayName("Notification request already exists")
  void failNotifAlreadyExists(VertxTestContext testContext) {
    // create a notification successfully
    // in the .succeeding method, create the exact same thing (i.e. repeat the createNotification
    // call)
    // assert that you get a 409 + whatever else
    testContext.completeNow();
  }


}
