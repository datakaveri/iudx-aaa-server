package iudx.aaa.server.policy;

import static iudx.aaa.server.apiserver.util.Urn.URN_ALREADY_EXISTS;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_INPUT;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_ROLE;
import static iudx.aaa.server.apiserver.util.Urn.URN_SUCCESS;
import static iudx.aaa.server.policy.Constants.CONSTRAINTS;
import static iudx.aaa.server.policy.Constants.DUPLICATE;
import static iudx.aaa.server.policy.Constants.DUPLICATE_POLICY;
import static iudx.aaa.server.policy.Constants.ERR_DETAIL_LIST_DELEGATE_ROLES;
import static iudx.aaa.server.policy.Constants.ERR_TITLE_INVALID_ROLES;
import static iudx.aaa.server.policy.Constants.ID;
import static iudx.aaa.server.policy.Constants.ITEMID;
import static iudx.aaa.server.policy.Constants.ITEMTYPE;
import static iudx.aaa.server.policy.Constants.NIL_UUID;
import static iudx.aaa.server.policy.Constants.OWNER_DETAILS;
import static iudx.aaa.server.policy.Constants.REQ_ID_ALREADY_NOT_EXISTS;
import static iudx.aaa.server.policy.Constants.RES;
import static iudx.aaa.server.policy.Constants.RESULTS;
import static iudx.aaa.server.policy.Constants.RES_GRP;
import static iudx.aaa.server.policy.Constants.STATUS;
import static iudx.aaa.server.policy.Constants.SUCC_NOTIF_REQ;
import static iudx.aaa.server.policy.Constants.SUCC_UPDATE_NOTIF_REQ;
import static iudx.aaa.server.policy.Constants.TITLE;
import static iudx.aaa.server.policy.Constants.TYPE;
import static iudx.aaa.server.policy.Constants.USER_DETAILS;
import static iudx.aaa.server.registration.Utils.SQL_CREATE_ADMIN_SERVER;
import static iudx.aaa.server.registration.Utils.SQL_CREATE_NOTIFICATION;
import static iudx.aaa.server.registration.Utils.SQL_CREATE_ORG;
import static iudx.aaa.server.registration.Utils.SQL_CREATE_VALID_AUTH_POLICY;
import static iudx.aaa.server.registration.Utils.SQL_DELETE_ANY_POLICIES;
import static iudx.aaa.server.registration.Utils.SQL_DELETE_ORG;
import static iudx.aaa.server.registration.Utils.SQL_DELETE_SERVERS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.pgclient.data.Interval;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apd.ApdService;
import iudx.aaa.server.apiserver.CreatePolicyRequest;
import iudx.aaa.server.apiserver.NotifRequestStatus;
import iudx.aaa.server.apiserver.ResourceObj;
import iudx.aaa.server.apiserver.Response.ResponseBuilder;
import iudx.aaa.server.apiserver.RoleStatus;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.UpdatePolicyNotification;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.registration.RegistrationService;
import iudx.aaa.server.registration.Utils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.Hours;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
public class UpdatePolicyNotificationTest {

  private static Logger LOGGER =
      LogManager.getLogger(iudx.aaa.server.policy.UpdatePolicyNotificationTest.class);

  private static Configuration config;

  // Database Properties

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
  private static ApdService apdService = Mockito.mock(ApdService.class);
  private static RegistrationService registrationService = Mockito.mock(RegistrationService.class);
  private static JsonObject authOptions;
  private static JsonObject catOptions;

  /*
   * We make use of Mockito spies in this test, since the createPolicy method of the policyService
   * is called and we cannot mock the policyService object (since the update notif API is also part
   * of policyService). Argument captors are used to verify that the calls made to the createPolicy
   * method have the correct arguments.
   * 
   * We cannot mock out createPolicy in these tests since the update notification code depends on
   * the policy being created and existing in the DB. Update notification queries the policy table
   * for the policy ID after a successful approval. Hence, the actual createPolicy must be called.
   * 
   * As a result, we need to create an auth server entry in the resource_server table and add an
   * auth policy set for the provider and delegate by the auth server admin (without this,
   * createPolicy will fail). We also need to mock the CatalogueClient.checkReqItems, since it's
   * used in createPolicy
   */

  @Captor
  ArgumentCaptor<List<CreatePolicyRequest>> policyRequest;
  @Captor
  ArgumentCaptor<JsonObject> authDelegateJson;

  private static PolicyService policyService;

  private static Vertx vertxObj;
  private static CatalogueClient catalogueClient = Mockito.mock(CatalogueClient.class);

  static String name = RandomStringUtils.randomAlphabetic(10).toLowerCase();
  static String url = name + ".com";
  static Promise<UUID> orgId;

  static Future<JsonObject> provider;
  static Future<JsonObject> consumer;
  static Future<JsonObject> delegate;
  static Future<JsonObject> admin;

  static Future<UUID> orgIdFut;
  private static final String DUMMY_AUTH_SERVER =
      "auth" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + "iudx.io";

  /* item details data structures */
  static Map<UUID, String> itemIdToCatId = new HashMap<UUID, String>();
  static Map<String, ResourceObj> catIdToResObj = new HashMap<String, ResourceObj>();

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

    authOptions.put("authServerUrl", DUMMY_AUTH_SERVER);

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

    provider = orgIdFut.compose(id -> Utils.createFakeUser(pool, id.toString(), url,
        Map.of(Roles.PROVIDER, RoleStatus.APPROVED), true));

    delegate = orgIdFut.compose(id -> Utils.createFakeUser(pool, id.toString(), url,
        Map.of(Roles.DELEGATE, RoleStatus.APPROVED), true));

    consumer = orgIdFut.compose(id -> Utils.createFakeUser(pool, NIL_UUID, "",
        Map.of(Roles.CONSUMER, RoleStatus.APPROVED), true));

    admin = orgIdFut.compose(id -> Utils.createFakeUser(pool, NIL_UUID, "",
        Map.of(Roles.ADMIN, RoleStatus.APPROVED), true));

    CompositeFuture.all(orgIdFut, provider, delegate, consumer, admin).compose(r -> {
      UUID adminId = UUID.fromString(admin.result().getString("userId"));
      UUID providerId = UUID.fromString(provider.result().getString("userId"));
      UUID delegateId = UUID.fromString(delegate.result().getString("userId"));

      Tuple authTup = Tuple.of("Auth Server", adminId, DUMMY_AUTH_SERVER);
      Tuple authPolicyProvider = Tuple.of(providerId, DUMMY_AUTH_SERVER);
      Tuple authPolicyDelegate = Tuple.of(delegateId, DUMMY_AUTH_SERVER);

      return pool.withConnection(conn -> conn.preparedQuery(SQL_CREATE_ADMIN_SERVER)
          .execute(authTup).compose(succ -> conn.preparedQuery(SQL_CREATE_VALID_AUTH_POLICY)
              .executeBatch(List.of(authPolicyProvider, authPolicyDelegate))));
    }).onSuccess(res -> {

      policyService = new PolicyServiceImpl(pool, registrationService, apdService, catalogueClient,
          authOptions, catOptions);

      testContext.completeNow();
    }).onFailure(handler -> handler.printStackTrace());
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing....");
    Tuple servers = Tuple.of(List.of(DUMMY_AUTH_SERVER).toArray());
    UUID adminId = UUID.fromString(admin.result().getString("userId"));
    UUID providerId = UUID.fromString(provider.result().getString("userId"));
    UUID delegateId = UUID.fromString(delegate.result().getString("userId"));
    Tuple policyOwners = Tuple.of(List.of(adminId, providerId, delegateId).toArray(UUID[]::new));

    List<JsonObject> users =
        List.of(provider.result(), delegate.result(), consumer.result(), admin.result());

    pool.withConnection(conn -> conn.preparedQuery(SQL_DELETE_SERVERS).execute(servers)
        .compose(bb -> conn.preparedQuery(SQL_DELETE_ANY_POLICIES).execute(policyOwners))
        .compose(aa -> Utils.deleteFakeUser(pool, users))
        .compose(succ -> conn.preparedQuery(SQL_DELETE_ORG).execute(Tuple.of(orgIdFut.result()))))
        .onComplete(x -> {
          if (x.failed()) {
            LOGGER.warn(x.cause().getMessage());
            LOGGER.error(
                "Data is NOT deleted after this test since policy table"
                + " does not allow deletes and therefore cascade delete fails");
          }
          vertxObj.close(testContext.succeeding(response -> testContext.completeNow()));
        });
  }

  /**
   * Create notification requests in DB as desired.
   * 
   * @param itemId the UUID item ID
   * @param itemType the item type ('RESOURCE'/'RESOURCE_GROUP')
   * @param status status of request
   * @param expiryInterval interval in ISO8601 interval format
   * @param constraints JSON object of constraints
   * @return the request ID of the created request
   */
  Future<UUID> createNotification(UUID itemId, String itemType, NotifRequestStatus status,
      String expiryInterval, JsonObject constraints) {

    UUID requestId = UUID.randomUUID();

    UUID providerId = UUID.fromString(provider.result().getString("userId"));
    UUID consumerId = UUID.fromString(consumer.result().getString("userId"));

    Interval intervalToStore = null;
    try {
      Duration duration = DatatypeFactory.newInstance().newDuration(expiryInterval);
      intervalToStore = Interval.of(duration.getYears(), duration.getMonths(), duration.getDays(),
          duration.getHours(), duration.getMinutes(), duration.getSeconds());
    } catch (DatatypeConfigurationException e) {
      return Future.failedFuture("Bad expiry duration");
    }

    Tuple tup = Tuple.of(requestId, consumerId, itemId, itemType, providerId, status,
        intervalToStore, constraints);

    return pool.withConnection(conn -> conn.preparedQuery(SQL_CREATE_NOTIFICATION).execute(tup))
        .map(succ -> requestId).onSuccess(id -> Future.succeededFuture(id));
  }

  /**
   * Get user details for a list of users
   * 
   * @param userIds the list of String user IDs
   * @return JSON object containing user details as how registrationService sends it
   */
  JsonObject fetchUserDetail(List<String> userIds) {
    String providerId = provider.result().getString("userId");
    String consumerId = consumer.result().getString("userId");
    String delegateId = delegate.result().getString("userId");

    Map<String, JsonObject> map = Map.of(providerId, provider.result(), consumerId,
        consumer.result(), delegateId, delegate.result());

    JsonObject response = new JsonObject();
    userIds.forEach(id -> {
      JsonObject userJson = map.get(id);

      JsonObject details = new JsonObject().put("email", userJson.getString("email")).put("name",
          new JsonObject().put("firstName", userJson.getString("firstName")).put("lastName",
              userJson.getString("lastName")));
      response.put(id, details);
    });

    return response;
  }

  /**
   * Creates a random valid resource group owned by the provider user. The resource group is added
   * to the item maps for CatalogueClient mocks.
   */
  Supplier<UUID> randResourceGroup = () -> {
    UUID itemId = UUID.randomUUID();
    String emailId = provider.result().getString("email");
    String catId = String.format("%s/%s/rs.%s/%s", url, DigestUtils.sha1Hex(emailId.getBytes()),
        url, RandomStringUtils.randomAlphabetic(10).toLowerCase());

    ResourceObj obj = new ResourceObj("resource_group", itemId, catId,
        UUID.fromString(provider.result().getString("userId")), UUID.randomUUID(), null);
    itemIdToCatId.put(itemId, catId);
    catIdToResObj.put(catId, obj);
    return itemId;
  };

  /**
   * Creates a random valid resource owned by the provider user. The resource is added to the item
   * maps for CatalogueClient mocks.
   */
  Supplier<UUID> randResource = () -> {
    UUID itemId = UUID.randomUUID();
    String emailId = provider.result().getString("email");
    String catId = String.format("%s/%s/rs.%s/%s/%s", url, DigestUtils.sha1Hex(emailId.getBytes()),
        url, RandomStringUtils.randomAlphabetic(10).toLowerCase(),
        RandomStringUtils.randomAlphabetic(10).toLowerCase());

    ResourceObj obj = new ResourceObj("resource", itemId, catId,
        UUID.fromString(provider.result().getString("userId")), UUID.randomUUID(),
        UUID.randomUUID());
    itemIdToCatId.put(itemId, catId);
    catIdToResObj.put(catId, obj);
    return itemId;
  };

  @Test
  @DisplayName("Test not registered user failing to call API")
  void failNotRegisteredUser(VertxTestContext testContext) {

    JsonObject userJson = provider.result();
    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId")).userId(NIL_UUID)
        .name(userJson.getString("firstName"), userJson.getString("lastName")).roles(List.of())
        .build();

    /* putting a random UUID as request ID here since it's not important to exist for this test */
    JsonArray req =
        new JsonArray().add(new JsonObject().put("requestId", UUID.randomUUID().toString())
            .put(STATUS, NotifRequestStatus.APPROVED.toString().toLowerCase()));

    List<UpdatePolicyNotification> request = UpdatePolicyNotification.jsonArrayToList(req);

    policyService.updatePolicyNotification(request, user, new JsonObject(),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_INVALID_ROLE.toString(), response.getString(TYPE));
          assertEquals(ERR_DETAIL_LIST_DELEGATE_ROLES, response.getString("detail"));
          assertEquals(ERR_TITLE_INVALID_ROLES, response.getString("title"));
          assertEquals(401, response.getInteger("status"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test admin/trustee/consumer calling API")
  void failDisallowedRoles(VertxTestContext testContext) {
    // creake fake request
    // try with different users with checkpoints
    testContext.completeNow();
  }

  @DisplayName("Test non-existent request")
  void failNonExistentRequest(VertxTestContext testContext) {
    // make request with 'requestId' as UUID.randomUUID().toString()
    testContext.completeNow();
  }

  @Test
  @DisplayName("Test duplicate request IDs")
  void failDuplicateReqIds(VertxTestContext testContext) {

    UUID itemId = randResourceGroup.get();

    createNotification(itemId, "RESOURCE_GROUP", NotifRequestStatus.PENDING, "P1Y",
        new JsonObject()).onSuccess(id -> {
          String requestId = id.toString();
          JsonObject userJson = provider.result();
          User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
              .userId(userJson.getString("userId"))
              .name(userJson.getString("firstName"), userJson.getString("lastName"))
              .roles(List.of(Roles.PROVIDER)).build();

          JsonArray req = new JsonArray()
              .add(new JsonObject().put("requestId", requestId).put(STATUS,
                  NotifRequestStatus.APPROVED.toString().toLowerCase()))
              .add(new JsonObject().put("requestId", requestId).put(STATUS,
                  NotifRequestStatus.REJECTED.toString().toLowerCase()));

          List<UpdatePolicyNotification> request = UpdatePolicyNotification.jsonArrayToList(req);

          policyService.updatePolicyNotification(request, user, new JsonObject(),
              testContext.succeeding(response -> testContext.verify(() -> {
                assertEquals(URN_INVALID_INPUT.toString(), response.getString(TYPE));
                assertEquals(SUCC_NOTIF_REQ, response.getString(TITLE));
                assertEquals(DUPLICATE, response.getString("detail"));
                assertEquals(400, response.getInteger("status"));
                testContext.completeNow();
              })));
        });
  }

  @Test
  @DisplayName("Test random provider trying to reject notification not owned by them")
  void failProviderDoesNotOwnRequest(VertxTestContext testContext) {

    UUID itemId = randResourceGroup.get();
    PolicyService spiedPolicyService = Mockito.spy(policyService);

    createNotification(itemId, "RESOURCE_GROUP", NotifRequestStatus.PENDING, "P1Y",
        new JsonObject()).onSuccess(id -> {
          String requestId = id.toString();

          /*
           * NOTE: we take the delegate user and add a fake userId, and the provider role.
           */
          JsonObject userJson = delegate.result();

          User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
              .userId(UUID.randomUUID().toString())
              .name(userJson.getString("firstName"), userJson.getString("lastName"))
              .roles(List.of(Roles.PROVIDER)).build();

          JsonArray req = new JsonArray().add(new JsonObject().put("requestId", requestId)
              .put(STATUS, NotifRequestStatus.APPROVED.toString().toLowerCase()));

          List<UpdatePolicyNotification> request = UpdatePolicyNotification.jsonArrayToList(req);

          spiedPolicyService.updatePolicyNotification(request, user, new JsonObject(),
              testContext.succeeding(response -> testContext.verify(() -> {
                assertEquals(URN_INVALID_INPUT.toString(), response.getString(TYPE));
                assertEquals(SUCC_NOTIF_REQ, response.getString(TITLE));
                assertEquals(REQ_ID_ALREADY_NOT_EXISTS, response.getString("detail"));
                assertEquals(404, response.getInteger("status"));

                testContext.completeNow();
              })));
        });
  }

  @Test
  @DisplayName("Test successful rejection")
  void successRejection(VertxTestContext testContext) {

    UUID itemId = randResourceGroup.get();
    PolicyService spiedPolicyService = Mockito.spy(policyService);

    /* Mock catalogue client for UpdateNotification response */
    Mockito.doAnswer(i -> {
      Set<UUID> itemIds = i.getArgument(0);
      Map<UUID, String> map = new HashMap<UUID, String>();
      itemIds.forEach(id -> map.put(id, itemIdToCatId.get(id)));
      return Future.succeededFuture(map);
    }).when(catalogueClient).getCatIds(Mockito.any(), Mockito.any());

    /* Mock registration service user details */
    Mockito.doAnswer(i -> {
      List<String> userIds = i.getArgument(0);
      Promise<JsonObject> promise = i.getArgument(1);
      promise.complete(fetchUserDetail(userIds));
      return i.getMock();
    }).when(registrationService).getUserDetails(Mockito.any(), Mockito.any());

    createNotification(itemId, "RESOURCE_GROUP", NotifRequestStatus.PENDING, "P1Y",
        new JsonObject()).onSuccess(id -> {
          String requestId = id.toString();

          JsonObject userJson = provider.result();
          User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
              .userId(userJson.getString("userId"))
              .name(userJson.getString("firstName"), userJson.getString("lastName"))
              .roles(List.of(Roles.PROVIDER)).build();

          JsonArray req = new JsonArray().add(new JsonObject().put("requestId", requestId)
              .put(STATUS, NotifRequestStatus.REJECTED.toString().toLowerCase()));

          List<UpdatePolicyNotification> request = UpdatePolicyNotification.jsonArrayToList(req);

          spiedPolicyService.updatePolicyNotification(request, user, new JsonObject(),
              testContext.succeeding(response -> testContext.verify(() -> {
                assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
                assertEquals(SUCC_UPDATE_NOTIF_REQ, response.getString(TITLE));
                assertEquals(200, response.getInteger("status"));
                assertTrue(response.containsKey(RESULTS));

                JsonArray results = response.getJsonArray(RESULTS);
                assertTrue(results.size() == 1);

                JsonObject j = results.getJsonObject(0);

                assertEquals(j.getString(ITEMID), itemIdToCatId.get(itemId));
                assertEquals(j.getString(STATUS),
                    NotifRequestStatus.REJECTED.toString().toLowerCase());
                assertEquals(j.getString(ITEMTYPE), "resource_group");
                assertEquals(j.getJsonObject(OWNER_DETAILS).getString(ID),
                    provider.result().getString("userId"));
                assertEquals(j.getJsonObject(USER_DETAILS).getString(ID),
                    consumer.result().getString("userId"));
                assertEquals(j.getString("expiryDuration"), "P1Y");
                assertEquals(j.getJsonObject(CONSTRAINTS), new JsonObject());

                testContext.completeNow();
              })));
        });
  }

  @Test
  @DisplayName("Test successful approved")
  void successApproved(VertxTestContext testContext) {

    PolicyService spiedPolicyService = Mockito.spy(policyService);

    UUID itemId = randResourceGroup.get();
    JsonObject constraints = new JsonObject();
    String expiryDuration = "P1Y";

    JsonObject userJson = provider.result();
    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.PROVIDER)).build();

    /* Mock catalogue client for UpdateNotification response */
    Mockito.doAnswer(i -> {
      Set<UUID> itemIds = i.getArgument(0);
      Map<UUID, String> map = new HashMap<UUID, String>();
      itemIds.forEach(id -> map.put(id, itemIdToCatId.get(id)));
      return Future.succeededFuture(map);
    }).when(catalogueClient).getCatIds(Mockito.any(), Mockito.any());

    /* Mock catalogue client for create policy */
    Mockito.doAnswer(i -> {
      Map<String, List<String>> itemList = i.getArgument(0);
      Map<String, ResourceObj> map = new HashMap<String, ResourceObj>();
      String resourceGroup = itemList.get(RES_GRP).get(0);
      map.put(resourceGroup, catIdToResObj.get(resourceGroup));
      return Future.succeededFuture(map);
    }).when(catalogueClient).checkReqItems(Mockito.any());

    /* Mock registration service user details */
    Mockito.doAnswer(i -> {
      List<String> userIds = i.getArgument(0);
      Promise<JsonObject> promise = i.getArgument(1);
      promise.complete(fetchUserDetail(userIds));
      return i.getMock();
    }).when(registrationService).getUserDetails(Mockito.any(), Mockito.any());

    createNotification(itemId, "RESOURCE_GROUP", NotifRequestStatus.PENDING, expiryDuration,
        constraints).onSuccess(id -> {

          String requestId = id.toString();

          JsonArray req = new JsonArray().add(new JsonObject().put("requestId", requestId)
              .put(STATUS, NotifRequestStatus.APPROVED.toString().toLowerCase()));

          List<UpdatePolicyNotification> request = UpdatePolicyNotification.jsonArrayToList(req);

          spiedPolicyService.updatePolicyNotification(request, user, new JsonObject(),
              testContext.succeeding(response -> testContext.verify(() -> {

                /* Verifying that create policy was called with expected request */
                Mockito.verify(spiedPolicyService).createPolicy(policyRequest.capture(),
                    Mockito.any(), Mockito.any(), Mockito.any());
                CreatePolicyRequest polReq = policyRequest.getValue().get(0);

                assertTrue(polReq.getItemType().equalsIgnoreCase("RESOURCE_GROUP"));
                assertEquals(polReq.getItemId(), itemIdToCatId.get(itemId));
                assertEquals(polReq.getUserId(), consumer.result().getString("userId"));
                assertEquals(polReq.getConstraints(), constraints);

                DateTime now = DateTime.now();
                DateTime expiry = DateTime.parse(polReq.getExpiryTime());
                int noOfHours = Hours.hoursBetween(now, expiry).getHours();
                /* subtract 1 since we are already in the first hour */
                assertEquals(noOfHours, (365 * 24) - 1);
                /* ************************************************************* */

                assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
                assertEquals(SUCC_UPDATE_NOTIF_REQ, response.getString(TITLE));
                assertEquals(200, response.getInteger("status"));
                assertTrue(response.containsKey(RESULTS));

                JsonArray results = response.getJsonArray(RESULTS);
                assertTrue(results.size() == 1);

                JsonObject j = results.getJsonObject(0);

                assertEquals(j.getString(ITEMID), itemIdToCatId.get(itemId));
                assertEquals(j.getString(STATUS),
                    NotifRequestStatus.APPROVED.toString().toLowerCase());
                assertEquals(j.getString(ITEMTYPE), "resource_group");
                assertEquals(j.getJsonObject(OWNER_DETAILS).getString(ID),
                    provider.result().getString("userId"));
                assertEquals(j.getJsonObject(USER_DETAILS).getString(ID),
                    consumer.result().getString("userId"));
                assertEquals(j.getString("expiryDuration"), expiryDuration);
                assertEquals(j.getJsonObject(CONSTRAINTS), constraints);

                testContext.completeNow();
              })));
        });
  }

  @Test
  @DisplayName("Test approving after rejecting a request")
  void failApprovingAlreadyRejectedRequest(VertxTestContext testContext) {
    // same as the createNotificationTest existing test scenario
    // copy the test rejected request code
    // make a request to set approved after rejecting successfully
    testContext.completeNow();
  }

  @Test
  @DisplayName("Test rejecting after approving a request")
  void failRejectingAlreadyApprovedRequest(VertxTestContext testContext) {
    // same as the createNotificationTest existing test scenario
    // copy the test approved request code
    // make a request to set rejected after approving successfully
    testContext.completeNow();
  }

  @Test
  @DisplayName("Test provider updating expiry duration and constraints in approved request")
  void checkExpiryAndConstraintsUpdateInApprovedReq(VertxTestContext testContext) {
    UUID itemId = randResource.get();
    PolicyService spiedPolicyService = Mockito.spy(policyService);

    JsonObject constraints = new JsonObject();
    String expiryDuration = "P1Y";

    String updatedExpiryDuration = "P1Y20D"; // one year and 20 days
    JsonObject updatedConstraints =
        new JsonObject().put("access", new JsonArray().add("api").add("sub"));

    JsonObject userJson = provider.result();
    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.PROVIDER)).build();

    /* Mock catalogue client for UpdateNotification response */
    Mockito.doAnswer(i -> {
      Set<UUID> itemIds = i.getArgument(0);
      Map<UUID, String> map = new HashMap<UUID, String>();
      itemIds.forEach(id -> map.put(id, itemIdToCatId.get(id)));
      return Future.succeededFuture(map);
    }).when(catalogueClient).getCatIds(Mockito.any(), Mockito.any());

    /* Mock catalogue client for create policy */
    Mockito.doAnswer(i -> {
      Map<String, List<String>> itemList = i.getArgument(0);
      Map<String, ResourceObj> map = new HashMap<String, ResourceObj>();
      String resource = itemList.get(RES).get(0);
      map.put(resource, catIdToResObj.get(resource));
      return Future.succeededFuture(map);
    }).when(catalogueClient).checkReqItems(Mockito.any());

    /* Mock registration service user details */
    Mockito.doAnswer(i -> {
      List<String> userIds = i.getArgument(0);
      Promise<JsonObject> promise = i.getArgument(1);
      promise.complete(fetchUserDetail(userIds));
      return i.getMock();
    }).when(registrationService).getUserDetails(Mockito.any(), Mockito.any());

    createNotification(itemId, "RESOURCE", NotifRequestStatus.PENDING, expiryDuration, constraints)
        .onSuccess(id -> {

          String requestId = id.toString();

          JsonArray req = new JsonArray().add(new JsonObject().put("requestId", requestId)
              .put(STATUS, NotifRequestStatus.APPROVED.toString().toLowerCase())
              .put("expiryDuration", updatedExpiryDuration).put(CONSTRAINTS, updatedConstraints));

          List<UpdatePolicyNotification> request = UpdatePolicyNotification.jsonArrayToList(req);

          spiedPolicyService.updatePolicyNotification(request, user, new JsonObject(),
              testContext.succeeding(response -> testContext.verify(() -> {

                /* Verifying that create policy was called with updated expiry, cons */
                Mockito.verify(spiedPolicyService).createPolicy(policyRequest.capture(),
                    Mockito.any(), Mockito.any(), Mockito.any());
                CreatePolicyRequest polReq = policyRequest.getValue().get(0);

                assertTrue(polReq.getItemType().equalsIgnoreCase("RESOURCE"));
                assertEquals(polReq.getItemId(), itemIdToCatId.get(itemId));
                assertEquals(polReq.getUserId(), consumer.result().getString("userId"));

                assertEquals(polReq.getConstraints(), updatedConstraints);

                DateTime now = DateTime.now();
                DateTime expiry = DateTime.parse(polReq.getExpiryTime());
                int noOfHours = Hours.hoursBetween(now, expiry).getHours();
                /* subtract 1 since we are already in the first hour */
                assertEquals(noOfHours, ((365 + 20) * 24) - 1);
                /* ************************************************************* */

                assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
                assertEquals(SUCC_UPDATE_NOTIF_REQ, response.getString(TITLE));
                assertEquals(200, response.getInteger("status"));
                assertTrue(response.containsKey(RESULTS));

                JsonArray results = response.getJsonArray(RESULTS);
                assertTrue(results.size() == 1);

                JsonObject j = results.getJsonObject(0);

                assertEquals(j.getString(ITEMID), itemIdToCatId.get(itemId));
                assertEquals(j.getString(STATUS),
                    NotifRequestStatus.APPROVED.toString().toLowerCase());
                assertEquals(j.getString(ITEMTYPE), "resource");
                assertEquals(j.getJsonObject(OWNER_DETAILS).getString(ID),
                    provider.result().getString("userId"));
                assertEquals(j.getJsonObject(USER_DETAILS).getString(ID),
                    consumer.result().getString("userId"));
                assertEquals(j.getString("expiryDuration"), updatedExpiryDuration);
                assertEquals(j.getJsonObject(CONSTRAINTS), updatedConstraints);

                testContext.completeNow();
              })));
        });
  }

  @Test
  @DisplayName("Test create policy not ending in success (policy not created) and rollback ")
  void policyNotCreatedAfterApprove(VertxTestContext testContext) {
    UUID itemId = randResource.get();
    JsonObject constraints = new JsonObject();
    String expiryDuration = "P1Y";
    PolicyService spiedPolicyService = Mockito.spy(policyService);

    JsonObject userJson = provider.result();
    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.PROVIDER)).build();

    /* Mock policy service to send non-200 OK response */
    Mockito.doAnswer(i -> {
      Promise<JsonObject> promise = i.getArgument(3);

      JsonObject resp = new ResponseBuilder().status(409).type(URN_ALREADY_EXISTS)
          .title(DUPLICATE_POLICY).detail(DUPLICATE_POLICY).build().toJson();

      promise.complete(resp);
      return i.getMock();
    }).when(spiedPolicyService).createPolicy(Mockito.any(), Mockito.any(), Mockito.any(),
        Mockito.any());

    /* Mock registration service user details */
    Mockito.doAnswer(i -> {
      List<String> userIds = i.getArgument(0);
      Promise<JsonObject> promise = i.getArgument(1);
      promise.complete(fetchUserDetail(userIds));
      return i.getMock();
    }).when(registrationService).getUserDetails(Mockito.any(), Mockito.any());

    createNotification(itemId, "RESOURCE", NotifRequestStatus.PENDING, expiryDuration, constraints)
        .onSuccess(id -> {

          String requestId = id.toString();

          JsonArray req = new JsonArray().add(new JsonObject().put("requestId", requestId)
              .put(STATUS, NotifRequestStatus.APPROVED.toString().toLowerCase()));

          List<UpdatePolicyNotification> request = UpdatePolicyNotification.jsonArrayToList(req);

          spiedPolicyService.updatePolicyNotification(request, user, new JsonObject(),
              testContext.succeeding(response -> testContext.verify(() -> {

                assertEquals(URN_INVALID_INPUT.toString(), response.getString(TYPE));
                assertEquals(SUCC_NOTIF_REQ, response.getString(TITLE));
                assertEquals(400, response.getInteger("status"));
                assertEquals(DUPLICATE_POLICY, response.getString("detail"));

                /*
                 * We should be able to reject the request now, as the request should still be in
                 * pending state
                 */
                JsonArray updatedReq =
                    new JsonArray().add(new JsonObject().put("requestId", requestId).put(STATUS,
                        NotifRequestStatus.REJECTED.toString().toLowerCase()));

                List<UpdatePolicyNotification> updatedRequest =
                    UpdatePolicyNotification.jsonArrayToList(updatedReq);

                spiedPolicyService.updatePolicyNotification(updatedRequest, user, new JsonObject(),
                    testContext.succeeding(resp -> testContext.verify(() -> {
                      assertEquals(URN_SUCCESS.toString(), resp.getString(TYPE));
                      assertEquals(SUCC_UPDATE_NOTIF_REQ, resp.getString(TITLE));
                      assertEquals(200, resp.getInteger("status"));
                      assertTrue(resp.containsKey(RESULTS));

                      JsonArray results = resp.getJsonArray(RESULTS);

                      JsonObject j = results.getJsonObject(0);

                      assertEquals(j.getString(STATUS),
                          NotifRequestStatus.REJECTED.toString().toLowerCase());

                      assertEquals(j.getString("expiryDuration"), "P1Y");
                      assertEquals(j.getJsonObject(CONSTRAINTS), new JsonObject());
                      testContext.completeNow();
                    })));
              })));
        });
  }

  @Test
  @DisplayName("Test auth delegate approving request")
  void authDelegateApproving(VertxTestContext testContext) {
    UUID itemId = randResource.get();
    PolicyService spiedPolicyService = Mockito.spy(policyService);

    JsonObject constraints = new JsonObject();
    String expiryDuration = "P1Y";

    JsonObject userJson = delegate.result();
    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.DELEGATE)).build();

    JsonObject providerDetails =
        new JsonObject().put("providerId", provider.result().getString("userId"));

    /* Mock catalogue client for UpdateNotification response */
    Mockito.doAnswer(i -> {
      Set<UUID> itemIds = i.getArgument(0);
      Map<UUID, String> map = new HashMap<UUID, String>();
      itemIds.forEach(id -> map.put(id, itemIdToCatId.get(id)));
      return Future.succeededFuture(map);
    }).when(catalogueClient).getCatIds(Mockito.any(), Mockito.any());

    /* Mock catalogue client for create policy */
    Mockito.doAnswer(i -> {
      Map<String, List<String>> itemList = i.getArgument(0);
      Map<String, ResourceObj> map = new HashMap<String, ResourceObj>();
      String resource = itemList.get(RES).get(0);
      map.put(resource, catIdToResObj.get(resource));
      return Future.succeededFuture(map);
    }).when(catalogueClient).checkReqItems(Mockito.any());

    /* Mock registration service user details */
    Mockito.doAnswer(i -> {
      List<String> userIds = i.getArgument(0);
      Promise<JsonObject> promise = i.getArgument(1);
      promise.complete(fetchUserDetail(userIds));
      return i.getMock();
    }).when(registrationService).getUserDetails(Mockito.any(), Mockito.any());

    createNotification(itemId, "RESOURCE", NotifRequestStatus.PENDING, expiryDuration, constraints)
        .onSuccess(id -> {

          String requestId = id.toString();

          JsonArray req = new JsonArray().add(new JsonObject().put("requestId", requestId)
              .put(STATUS, NotifRequestStatus.APPROVED.toString().toLowerCase())
              .put("expiryDuration", expiryDuration).put(CONSTRAINTS, constraints));

          List<UpdatePolicyNotification> request = UpdatePolicyNotification.jsonArrayToList(req);

          // passing provider details in data param
          spiedPolicyService.updatePolicyNotification(request, user, providerDetails,
              testContext.succeeding(response -> testContext.verify(() -> {

                /* Verifying that create policy was called with updated expiry, cons */
                Mockito.verify(spiedPolicyService).createPolicy(policyRequest.capture(),
                    Mockito.any(), authDelegateJson.capture(), Mockito.any());
                CreatePolicyRequest polReq = policyRequest.getValue().get(0);

                assertTrue(polReq.getItemType().equalsIgnoreCase("RESOURCE"));
                assertEquals(polReq.getItemId(), itemIdToCatId.get(itemId));
                assertEquals(polReq.getUserId(), consumer.result().getString("userId"));

                assertEquals(polReq.getConstraints(), constraints);

                DateTime now = DateTime.now();
                DateTime expiry = DateTime.parse(polReq.getExpiryTime());
                int noOfHours = Hours.hoursBetween(now, expiry).getHours();
                /* subtract 1 since we are already in the first hour */
                assertEquals(noOfHours, (365 * 24) - 1);

                assertEquals(authDelegateJson.getValue(), providerDetails);
                /* ************************************************************* */

                assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
                assertEquals(SUCC_UPDATE_NOTIF_REQ, response.getString(TITLE));
                assertEquals(200, response.getInteger("status"));
                assertTrue(response.containsKey(RESULTS));

                JsonArray results = response.getJsonArray(RESULTS);
                assertTrue(results.size() == 1);

                JsonObject j = results.getJsonObject(0);

                assertEquals(j.getString(ITEMID), itemIdToCatId.get(itemId));
                assertEquals(j.getString(STATUS),
                    NotifRequestStatus.APPROVED.toString().toLowerCase());
                assertEquals(j.getString(ITEMTYPE), "resource");
                assertEquals(j.getJsonObject(OWNER_DETAILS).getString(ID),
                    provider.result().getString("userId"));
                assertEquals(j.getJsonObject(USER_DETAILS).getString(ID),
                    consumer.result().getString("userId"));
                assertEquals(j.getString("expiryDuration"), expiryDuration);
                assertEquals(j.getJsonObject(CONSTRAINTS), constraints);

                testContext.completeNow();
              })));
        });
  }

  @Test
  @DisplayName("Test auth delegate rejecting request")
  void authDelegateRejecting(VertxTestContext testContext) {
    testContext.completeNow();
  }

  @Test
  @DisplayName("Test policyService.createPolicy failing and rollback")
  void createPolicyFailing(VertxTestContext testContext) {
    // same as policyNotCreatedAfterApprove but mock object is as follows
    /*- 
    
    Mockito.doAnswer(i -> {
      Promise<JsonObject> promise = i.getArgument(3);
      promise.fail("Create policy hard failure");
      return i.getMock();
    }).when(spiedPolicyService).createPolicy(Mockito.any(), Mockito.any(), Mockito.any(),
        Mockito.any());
        
        */
    testContext.completeNow();
  }

  // optional test
  @Test
  @DisplayName("Test registration service failing")
  void registrationServiceFailing(VertxTestContext testContext) {
    testContext.completeNow();
  }

}
