package iudx.aaa.server.policy;

import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_ROLE;
import static iudx.aaa.server.apiserver.util.Urn.URN_SUCCESS;
import static iudx.aaa.server.policy.Constants.CONSTRAINTS;
import static iudx.aaa.server.policy.Constants.ERR_DETAIL_LIST_DELEGATE_ROLES;
import static iudx.aaa.server.policy.Constants.ERR_TITLE_INVALID_ROLES;
import static iudx.aaa.server.policy.Constants.ID;
import static iudx.aaa.server.policy.Constants.ITEMID;
import static iudx.aaa.server.policy.Constants.ITEMTYPE;
import static iudx.aaa.server.policy.Constants.NIL_UUID;
import static iudx.aaa.server.policy.Constants.OWNER_DETAILS;
import static iudx.aaa.server.policy.Constants.RESULTS;
import static iudx.aaa.server.policy.Constants.STATUS;
import static iudx.aaa.server.policy.Constants.SUCC_LIST_NOTIF_REQ;
import static iudx.aaa.server.policy.Constants.TYPE;
import static iudx.aaa.server.policy.Constants.USER_DETAILS;
import static iudx.aaa.server.registration.Utils.SQL_CREATE_NOTIFICATION;
import static iudx.aaa.server.registration.Utils.SQL_CREATE_ORG;
import static iudx.aaa.server.registration.Utils.SQL_DELETE_ORG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.pgclient.data.Interval;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apd.ApdService;
import iudx.aaa.server.apiserver.NotifRequestStatus;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
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

@ExtendWith({VertxExtension.class, MockitoExtension.class})
public class ListPolicyNotificationTest {

  /*-
   * - 2 providers, A and B. Each have 1 resource and 1 resource group 
   * - one consumer
   * - one consumer who is also has a delegate role and can therefore
   * serve as auth delegate
   * - 4 notification requests
   * 
   * TODO: update tests when Withdrawn status comes
   * This table covers all scenarios:
   * +---------+--------------+---------------+----------+
   * | Request | Consumer     | Item          | Status   |
   * +---------+--------------+---------------+----------+
   * | 1       | Consumer     | ProviderA-RG  | Pending  |
   * | 2       | Consumer     | ProviderB-R   | Approved |
   * | 3       | DelegateCons | ProviderA-R   | Rejected |
   * | 4       | DelegateCons | ProviderB-RSG | Pending  |
   * +---------+--------------+---------------+----------+
   * 
   */
  private static Logger LOGGER =
      LogManager.getLogger(iudx.aaa.server.policy.ListPolicyNotificationTest.class);

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
  private static PolicyService policyService;
  private static ApdService apdService = Mockito.mock(ApdService.class);
  private static EmailClient emailClient = Mockito.mock(EmailClient.class);

  private static RegistrationService registrationService = Mockito.mock(RegistrationService.class);
  private static JsonObject authOptions;
  private static JsonObject catOptions;

  private static Vertx vertxObj;
  private static CatalogueClient catalogueClient = Mockito.mock(CatalogueClient.class);

  static String name = RandomStringUtils.randomAlphabetic(10).toLowerCase();
  static String url = name + ".com";
  static Promise<UUID> orgId;

  static Future<JsonObject> providerA;
  static Future<JsonObject> providerB;
  static Future<JsonObject> consumer;
  static Future<JsonObject> delegateConsumer;

  static UUID request1 = UUID.randomUUID();
  static UUID request2 = UUID.randomUUID();
  static UUID request3 = UUID.randomUUID();
  static UUID request4 = UUID.randomUUID();

  static UUID providerARsgItemId = UUID.randomUUID();
  static UUID providerBRsgItemId = UUID.randomUUID();
  static UUID providerAResItemId = UUID.randomUUID();
  static UUID providerBResItemId = UUID.randomUUID();

  static String providerARsgCatId = RandomStringUtils.randomAlphabetic(10).toLowerCase();
  static String providerBRsgCatId = RandomStringUtils.randomAlphabetic(10).toLowerCase();
  static String providerAResCatId = RandomStringUtils.randomAlphabetic(10).toLowerCase();
  static String providerBResCatId = RandomStringUtils.randomAlphabetic(10).toLowerCase();

  Map<UUID, String> itemIdToCatId =
      Map.of(providerARsgItemId, providerARsgCatId, providerBRsgItemId, providerBRsgCatId,
          providerAResItemId, providerAResCatId, providerBResItemId, providerBResCatId);

  Map<String, JsonObject> userDetails = new HashMap<String, JsonObject>();

  static Future<UUID> orgIdFut;
  static final String EXPIRY_DURATION = "P1Y5D";

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

    providerA = orgIdFut.compose(id -> Utils.createFakeUser(pool, id.toString(), url,
        Map.of(Roles.PROVIDER, RoleStatus.APPROVED), true));

    providerB = orgIdFut.compose(id -> Utils.createFakeUser(pool, id.toString(), url,
        Map.of(Roles.PROVIDER, RoleStatus.APPROVED), true));

    delegateConsumer = orgIdFut.compose(id -> Utils.createFakeUser(pool, id.toString(), url,
        Map.of(Roles.DELEGATE, RoleStatus.APPROVED, Roles.CONSUMER, RoleStatus.APPROVED), true));

    consumer = orgIdFut.compose(id -> Utils.createFakeUser(pool, NIL_UUID, "",
        Map.of(Roles.CONSUMER, RoleStatus.APPROVED), true));

    CompositeFuture.all(orgIdFut, providerA, providerB, delegateConsumer, consumer).compose(res -> {

      Interval expiryInterval = null;
      try {
        Duration duration = DatatypeFactory.newInstance().newDuration(EXPIRY_DURATION);
        expiryInterval = Interval.of(duration.getYears(), duration.getMonths(), duration.getDays(),
            duration.getHours(), duration.getMinutes(), duration.getSeconds());
      } catch (DatatypeConfigurationException e) {
        // should not happen
        testContext.failNow("Error");
      }

      JsonObject constraints = new JsonObject();
      UUID providerAId = UUID.fromString(providerA.result().getString("userId"));
      UUID providerBId = UUID.fromString(providerB.result().getString("userId"));
      UUID consumerId = UUID.fromString(consumer.result().getString("userId"));
      UUID deleConsId = UUID.fromString(delegateConsumer.result().getString("userId"));

      Tuple req1Tup = Tuple.of(request1, consumerId, providerARsgItemId, "RESOURCE_GROUP",
          providerAId, NotifRequestStatus.PENDING, expiryInterval, constraints);

      Tuple req2Tup = Tuple.of(request2, consumerId, providerBResItemId, "RESOURCE", providerBId,
          NotifRequestStatus.APPROVED, expiryInterval, constraints);

      Tuple req3Tup = Tuple.of(request3, deleConsId, providerAResItemId, "RESOURCE", providerAId,
          NotifRequestStatus.REJECTED, expiryInterval, constraints);

      Tuple req4Tup = Tuple.of(request4, deleConsId, providerBRsgItemId, "RESOURCE_GROUP",
          providerBId, NotifRequestStatus.PENDING, expiryInterval, constraints);

      List<Tuple> requests = List.of(req1Tup, req2Tup, req3Tup, req4Tup);

      return pool.withConnection(
          conn -> conn.preparedQuery(SQL_CREATE_NOTIFICATION).executeBatch(requests));
    }).onSuccess(r -> {

      policyService = new PolicyServiceImpl(pool, registrationService, apdService, catalogueClient,
          authOptions, catOptions,emailClient);
      testContext.completeNow();
    }).onFailure(handler -> handler.printStackTrace());
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing....");
    List<JsonObject> users = List.of(providerA.result(), providerB.result(),
        delegateConsumer.result(), consumer.result());

    pool.withConnection(conn -> Utils.deleteFakeUser(pool, users)
        .compose(succ -> conn.preparedQuery(SQL_DELETE_ORG).execute(Tuple.of(orgIdFut.result()))))
        .onComplete(x -> {
          if (x.failed()) {
            LOGGER.warn(x.cause().getMessage());
          }
          vertxObj.close(testContext.succeeding(response -> testContext.completeNow()));
        });
  }


  /**
   * Get user details for a list of users
   * 
   * @param userIds the list of String user IDs
   * @return JSON object containing user details as how registrationService sends it
   */
  JsonObject fetchUserDetail(List<String> userIds) {
    String providerAId = providerA.result().getString("userId");
    String providerBId = providerB.result().getString("userId");
    String consumerId = consumer.result().getString("userId");
    String deleConsId = delegateConsumer.result().getString("userId");

    Map<String, JsonObject> map = Map.of(providerAId, providerA.result(), providerBId,
        providerB.result(), consumerId, consumer.result(), deleConsId, delegateConsumer.result());

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

  @Test
  @DisplayName("Test not registered user failing to call API")
  void failNotRegisteredUser(VertxTestContext testContext) {

    JsonObject userJson = consumer.result();
    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId")).userId(NIL_UUID)
        .name(userJson.getString("firstName"), userJson.getString("lastName")).roles(List.of())
        .build();

    policyService.listPolicyNotification(user, new JsonObject(),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_INVALID_ROLE.toString(), response.getString(TYPE));
          assertEquals(ERR_DETAIL_LIST_DELEGATE_ROLES, response.getString("detail"));
          assertEquals(ERR_TITLE_INVALID_ROLES, response.getString("title"));
          assertEquals(401, response.getInteger("status"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test admin/trustee calling API")
  void failDisallowedRoles(VertxTestContext testContext) {
    // same as the create notification tests here
    Checkpoint checkAdmin = testContext.checkpoint();
    Checkpoint checkTrustee = testContext.checkpoint();
    JsonObject admin = consumer.result();
    String randomUserId = UUID.randomUUID().toString();
    User userAdmin = new UserBuilder().keycloakId(admin.getString("keycloakId")).userId(randomUserId)
            .name(admin.getString("firstName"), admin.getString("lastName"))
            .roles(List.of(Roles.ADMIN)).build();

    policyService.listPolicyNotification(userAdmin, new JsonObject(),
            testContext.succeeding(response -> testContext.verify(() -> {
              assertEquals(URN_INVALID_ROLE.toString(), response.getString(TYPE));
              assertEquals(ERR_DETAIL_LIST_DELEGATE_ROLES, response.getString("detail"));
              assertEquals(ERR_TITLE_INVALID_ROLES, response.getString("title"));
              assertEquals(401, response.getInteger("status"));
              checkAdmin.flag();
            })));

    JsonObject trustee = consumer.result();
    User trusteeUser = new UserBuilder().keycloakId(trustee.getString("keycloakId")).userId(randomUserId)
            .name(trustee.getString("firstName"), trustee.getString("lastName"))
            .roles(List.of(Roles.TRUSTEE)).build();

    policyService.listPolicyNotification(trusteeUser, new JsonObject(),
            testContext.succeeding(response -> testContext.verify(() -> {
              assertEquals(URN_INVALID_ROLE.toString(), response.getString(TYPE));
              assertEquals(ERR_DETAIL_LIST_DELEGATE_ROLES, response.getString("detail"));
              assertEquals(ERR_TITLE_INVALID_ROLES, response.getString("title"));
              assertEquals(401, response.getInteger("status"));
              checkTrustee.flag();
            })));

  }

  @Test
  @DisplayName("Test consumer having no notifications calling API")
  void consumerCallNoNotifs(VertxTestContext testContext) {

    JsonObject userJson = consumer.result();
    /*
     * We pass a random User ID when creating this user object. This is not completely correct, i.e
     * we should actually create a consumer that has no notifications in the setup code. But it
     * should work here since there's only a query being made (and no insertions take place so
     * there's no foreign key constraints)
     */
    String randomUserId = UUID.randomUUID().toString();
    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId")).userId(randomUserId)
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.CONSUMER)).build();

    policyService.listPolicyNotification(user, new JsonObject(),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
          assertEquals(SUCC_LIST_NOTIF_REQ, response.getString("title"));
          assertEquals(200, response.getInteger("status"));
          assertTrue(response.containsKey(RESULTS));

          JsonArray results = response.getJsonArray(RESULTS);
          assertTrue(results.isEmpty());
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test consumer user (pending, approved) calling API")
  void consumerListNotif(VertxTestContext testContext) {
    JsonObject userJson = consumer.result();
    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.CONSUMER)).build();

    /* Mock catalogue client to get correct list of cat IDs */
    Mockito.doAnswer(i -> {
      Set<UUID> itemIds = i.getArgument(0);
      Map<UUID, String> resMap =
          itemIds.stream().collect(Collectors.toMap(id -> id, id -> itemIdToCatId.get(id)));
      return Future.succeededFuture(resMap);
    }).when(catalogueClient).getCatIds(Mockito.any(), Mockito.any());

    /* Mock registration service details */
    Mockito.doAnswer(i -> {
      List<String> userIds = i.getArgument(0);
      Promise<JsonObject> promise = i.getArgument(1);

      promise.complete(fetchUserDetail(userIds));
      return i.getMock();
    }).when(registrationService).getUserDetails(Mockito.any(), Mockito.any());

    Checkpoint request1Cp = testContext.checkpoint();
    Checkpoint request2Cp = testContext.checkpoint();

    policyService.listPolicyNotification(user, new JsonObject(),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
          assertEquals(SUCC_LIST_NOTIF_REQ, response.getString("title"));
          assertEquals(200, response.getInteger("status"));
          assertTrue(response.containsKey(RESULTS));

          JsonArray results = response.getJsonArray(RESULTS);
          assertTrue(results.size() == 2);

          results.forEach(object -> {
            JsonObject j = (JsonObject) object;

            if (j.getString("requestId").equals(request1.toString())) {
              assertEquals(j.getString(ITEMID), providerARsgCatId);
              assertEquals(j.getString(STATUS),
                  NotifRequestStatus.PENDING.toString().toLowerCase());
              assertEquals(j.getString(ITEMTYPE), "resource_group");
              assertEquals(j.getJsonObject(OWNER_DETAILS).getString(ID),
                  providerA.result().getString("userId"));
              assertEquals(j.getJsonObject(USER_DETAILS).getString(ID),
                  consumer.result().getString("userId"));
              assertEquals(j.getString("expiryDuration"), EXPIRY_DURATION);
              assertEquals(j.getJsonObject(CONSTRAINTS), new JsonObject());
              request1Cp.flag();
            }

            if (j.getString("requestId").equals(request2.toString())) {
              assertEquals(j.getString(ITEMID), providerBResCatId);
              assertEquals(j.getString(STATUS),
                  NotifRequestStatus.APPROVED.toString().toLowerCase());
              assertEquals(j.getString(ITEMTYPE), "resource");
              assertEquals(j.getJsonObject(OWNER_DETAILS).getString(ID),
                  providerB.result().getString("userId"));
              assertEquals(j.getJsonObject(USER_DETAILS).getString(ID),
                  consumer.result().getString("userId"));
              assertEquals(j.getString("expiryDuration"), EXPIRY_DURATION);
              assertEquals(j.getJsonObject(CONSTRAINTS), new JsonObject());
              request2Cp.flag();
            }
          });
        })));
  }

  @Test
  @DisplayName("Test providerA (pending, rejected) calling API")
  void providerAListNotif(VertxTestContext testContext) {
    JsonObject userJson = providerA.result();
    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.PROVIDER)).build();

    // add mocks
    // must get requests 1 and 3

    /* Mock catalogue client to get correct list of cat IDs */
    Mockito.doAnswer(i -> {
      Set<UUID> itemIds = i.getArgument(0);
      Map<UUID, String> resMap =
              itemIds.stream().collect(Collectors.toMap(id -> id, id -> itemIdToCatId.get(id)));
      return Future.succeededFuture(resMap);
    }).when(catalogueClient).getCatIds(Mockito.any(), Mockito.any());

    /* Mock registration service details */
    Mockito.doAnswer(i -> {
      List<String> userIds = i.getArgument(0);
      Promise<JsonObject> promise = i.getArgument(1);

      promise.complete(fetchUserDetail(userIds));
      return i.getMock();
    }).when(registrationService).getUserDetails(Mockito.any(), Mockito.any());

    Checkpoint request1Cp = testContext.checkpoint();
    Checkpoint request3Cp = testContext.checkpoint();

    policyService.listPolicyNotification(user, new JsonObject(),
            testContext.succeeding(response -> testContext.verify(() -> {
              assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
              assertEquals(SUCC_LIST_NOTIF_REQ, response.getString("title"));
              assertEquals(200, response.getInteger("status"));
              assertTrue(response.containsKey(RESULTS));

              JsonArray results = response.getJsonArray(RESULTS);
              assertTrue(results.size() == 2);

              results.forEach(object -> {
                JsonObject j = (JsonObject) object;

                if (j.getString("requestId").equals(request1.toString())) {
                  assertEquals(j.getString(ITEMID), providerARsgCatId);
                  assertEquals(j.getString(STATUS),
                          NotifRequestStatus.PENDING.toString().toLowerCase());
                  assertEquals(j.getString(ITEMTYPE), "resource_group");
                  assertEquals(j.getJsonObject(OWNER_DETAILS).getString(ID),
                          providerA.result().getString("userId"));
                  assertEquals(j.getJsonObject(USER_DETAILS).getString(ID),
                          consumer.result().getString("userId"));
                  assertEquals(j.getString("expiryDuration"), EXPIRY_DURATION);
                  assertEquals(j.getJsonObject(CONSTRAINTS), new JsonObject());
                  request1Cp.flag();
                }

                if (j.getString("requestId").equals(request3.toString())) {
                  assertEquals(j.getString(ITEMID), providerAResCatId);
                  assertEquals(j.getString(STATUS),
                          NotifRequestStatus.REJECTED.toString().toLowerCase());
                  assertEquals(j.getString(ITEMTYPE), "resource");
                  assertEquals(j.getJsonObject(OWNER_DETAILS).getString(ID),
                          providerA.result().getString("userId"));
                  assertEquals(j.getJsonObject(USER_DETAILS).getString(ID),
                          delegateConsumer.result().getString("userId"));
                  assertEquals(j.getString("expiryDuration"), EXPIRY_DURATION);
                  assertEquals(j.getJsonObject(CONSTRAINTS), new JsonObject());
                  request3Cp.flag();
                }
              });
            })));

  }

  @Test
  @DisplayName("Test providerB (pending, approved) calling API")
  void providerBListNotif(VertxTestContext testContext) {
    JsonObject userJson = providerB.result();
    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.PROVIDER)).build();

    // add mocks
    // must get requests 2 and 4

    /* Mock catalogue client to get correct list of cat IDs */
    Mockito.doAnswer(i -> {
      Set<UUID> itemIds = i.getArgument(0);
      Map<UUID, String> resMap =
              itemIds.stream().collect(Collectors.toMap(id -> id, id -> itemIdToCatId.get(id)));
      return Future.succeededFuture(resMap);
    }).when(catalogueClient).getCatIds(Mockito.any(), Mockito.any());

    /* Mock registration service details */
    Mockito.doAnswer(i -> {
      List<String> userIds = i.getArgument(0);
      Promise<JsonObject> promise = i.getArgument(1);

      promise.complete(fetchUserDetail(userIds));
      return i.getMock();
    }).when(registrationService).getUserDetails(Mockito.any(), Mockito.any());

    Checkpoint request2Cp = testContext.checkpoint();
    Checkpoint request4Cp = testContext.checkpoint();

    policyService.listPolicyNotification(user, new JsonObject(),
            testContext.succeeding(response -> testContext.verify(() -> {
              assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
              assertEquals(SUCC_LIST_NOTIF_REQ, response.getString("title"));
              assertEquals(200, response.getInteger("status"));
              assertTrue(response.containsKey(RESULTS));

              JsonArray results = response.getJsonArray(RESULTS);
              assertTrue(results.size() == 2);

              results.forEach(object -> {
                JsonObject j = (JsonObject) object;

                if (j.getString("requestId").equals(request4.toString())) {
                  assertEquals(j.getString(ITEMID), providerBRsgCatId);
                  assertEquals(j.getString(STATUS),
                          NotifRequestStatus.PENDING.toString().toLowerCase());
                  assertEquals(j.getString(ITEMTYPE), "resource_group");
                  assertEquals(j.getJsonObject(OWNER_DETAILS).getString(ID),
                          providerB.result().getString("userId"));
                  assertEquals(j.getJsonObject(USER_DETAILS).getString(ID),
                          delegateConsumer.result().getString("userId"));
                  assertEquals(j.getString("expiryDuration"), EXPIRY_DURATION);
                  assertEquals(j.getJsonObject(CONSTRAINTS), new JsonObject());
                  request4Cp.flag();
                }

                if (j.getString("requestId").equals(request2.toString())) {
                  assertEquals(j.getString(ITEMID), providerBResCatId);
                  assertEquals(j.getString(STATUS),
                          NotifRequestStatus.APPROVED.toString().toLowerCase());
                  assertEquals(j.getString(ITEMTYPE), "resource");
                  assertEquals(j.getJsonObject(OWNER_DETAILS).getString(ID),
                          providerB.result().getString("userId"));
                  assertEquals(j.getJsonObject(USER_DETAILS).getString(ID),
                          consumer.result().getString("userId"));
                  assertEquals(j.getString("expiryDuration"), EXPIRY_DURATION);
                  assertEquals(j.getJsonObject(CONSTRAINTS), new JsonObject());
                  request2Cp.flag();
                }
              });
            })));
  }

  @Test
  @DisplayName("Test delegate-consumer (pending, rejected) calling API")
  void deleConsListNotif(VertxTestContext testContext) {
    JsonObject userJson = delegateConsumer.result();
    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.DELEGATE, Roles.CONSUMER)).build();

    // add mocks
    // must get requests 3 and 4

    /* Mock catalogue client to get correct list of cat IDs */
    Mockito.doAnswer(i -> {
      Set<UUID> itemIds = i.getArgument(0);
      Map<UUID, String> resMap =
              itemIds.stream().collect(Collectors.toMap(id -> id, id -> itemIdToCatId.get(id)));
      return Future.succeededFuture(resMap);
    }).when(catalogueClient).getCatIds(Mockito.any(), Mockito.any());

    /* Mock registration service details */
    Mockito.doAnswer(i -> {
      List<String> userIds = i.getArgument(0);
      Promise<JsonObject> promise = i.getArgument(1);

      promise.complete(fetchUserDetail(userIds));
      return i.getMock();
    }).when(registrationService).getUserDetails(Mockito.any(), Mockito.any());

    Checkpoint request3Cp = testContext.checkpoint();
    Checkpoint request4Cp = testContext.checkpoint();

    policyService.listPolicyNotification(user, new JsonObject(),
            testContext.succeeding(response -> testContext.verify(() -> {
              assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
              assertEquals(SUCC_LIST_NOTIF_REQ, response.getString("title"));
              assertEquals(200, response.getInteger("status"));
              assertTrue(response.containsKey(RESULTS));

              JsonArray results = response.getJsonArray(RESULTS);
              assertTrue(results.size() == 2);

              results.forEach(object -> {
                JsonObject j = (JsonObject) object;

                if (j.getString("requestId").equals(request4.toString())) {
                  assertEquals(j.getString(ITEMID), providerBRsgCatId);
                  assertEquals(j.getString(STATUS),
                          NotifRequestStatus.PENDING.toString().toLowerCase());
                  assertEquals(j.getString(ITEMTYPE), "resource_group");
                  assertEquals(j.getJsonObject(OWNER_DETAILS).getString(ID),
                          providerB.result().getString("userId"));
                  assertEquals(j.getJsonObject(USER_DETAILS).getString(ID),
                          delegateConsumer.result().getString("userId"));
                  assertEquals(j.getString("expiryDuration"), EXPIRY_DURATION);
                  assertEquals(j.getJsonObject(CONSTRAINTS), new JsonObject());
                  request4Cp.flag();
                }

                if (j.getString("requestId").equals(request3.toString())) {
                  assertEquals(j.getString(ITEMID), providerAResCatId);
                  assertEquals(j.getString(STATUS),
                          NotifRequestStatus.REJECTED.toString().toLowerCase());
                  assertEquals(j.getString(ITEMTYPE), "resource");
                  assertEquals(j.getJsonObject(OWNER_DETAILS).getString(ID),
                          providerA.result().getString("userId"));
                  assertEquals(j.getJsonObject(USER_DETAILS).getString(ID),
                          delegateConsumer.result().getString("userId"));
                  assertEquals(j.getString("expiryDuration"), EXPIRY_DURATION);
                  assertEquals(j.getJsonObject(CONSTRAINTS), new JsonObject());
                  request3Cp.flag();
                }
              });
            })));
  }

  @Test
  @DisplayName("Test delegate-consumer (pending, rejected) calling API as auth delegate for provider A")
  void deleConsListNotifAsAuthDelegate(VertxTestContext testContext) {
    JsonObject userJson = delegateConsumer.result();
    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.DELEGATE, Roles.CONSUMER)).build();

    JsonObject providerDetails =
        new JsonObject().put("providerId", providerA.result().getString("userId"));
    // add this as the 2nd argument of listPolicyNotification
    // e.g. policyService.listPolicyNotification(user, providerDetails ...

    // add mocks
    // must get requests 1 and 3

    /* Mock catalogue client to get correct list of cat IDs */
    Mockito.doAnswer(i -> {
      Set<UUID> itemIds = i.getArgument(0);
      Map<UUID, String> resMap =
              itemIds.stream().collect(Collectors.toMap(id -> id, id -> itemIdToCatId.get(id)));
      return Future.succeededFuture(resMap);
    }).when(catalogueClient).getCatIds(Mockito.any(), Mockito.any());

    /* Mock registration service details */
    Mockito.doAnswer(i -> {
      List<String> userIds = i.getArgument(0);
      Promise<JsonObject> promise = i.getArgument(1);

      promise.complete(fetchUserDetail(userIds));
      return i.getMock();
    }).when(registrationService).getUserDetails(Mockito.any(), Mockito.any());

    Checkpoint request1Cp = testContext.checkpoint();
    Checkpoint request3Cp = testContext.checkpoint();

    policyService.listPolicyNotification(user, providerDetails,
            testContext.succeeding(response -> testContext.verify(() -> {
              assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
              assertEquals(SUCC_LIST_NOTIF_REQ, response.getString("title"));
              assertEquals(200, response.getInteger("status"));
              assertTrue(response.containsKey(RESULTS));

              JsonArray results = response.getJsonArray(RESULTS);
              assertTrue(results.size() == 2);

              results.forEach(object -> {
                JsonObject j = (JsonObject) object;

                if (j.getString("requestId").equals(request1.toString())) {
                  assertEquals(j.getString(ITEMID), providerARsgCatId);
                  assertEquals(j.getString(STATUS),
                          NotifRequestStatus.PENDING.toString().toLowerCase());
                  assertEquals(j.getString(ITEMTYPE), "resource_group");
                  assertEquals(j.getJsonObject(OWNER_DETAILS).getString(ID),
                          providerA.result().getString("userId"));
                  assertEquals(j.getJsonObject(USER_DETAILS).getString(ID),
                          consumer.result().getString("userId"));
                  assertEquals(j.getString("expiryDuration"), EXPIRY_DURATION);
                  assertEquals(j.getJsonObject(CONSTRAINTS), new JsonObject());
                  request1Cp.flag();
                }

                if (j.getString("requestId").equals(request3.toString())) {
                  assertEquals(j.getString(ITEMID), providerAResCatId);
                  assertEquals(j.getString(STATUS),
                          NotifRequestStatus.REJECTED.toString().toLowerCase());
                  assertEquals(j.getString(ITEMTYPE), "resource");
                  assertEquals(j.getJsonObject(OWNER_DETAILS).getString(ID),
                          providerA.result().getString("userId"));
                  assertEquals(j.getJsonObject(USER_DETAILS).getString(ID),
                          delegateConsumer.result().getString("userId"));
                  assertEquals(j.getString("expiryDuration"), EXPIRY_DURATION);
                  assertEquals(j.getJsonObject(CONSTRAINTS), new JsonObject());
                  request3Cp.flag();
                }
              });
            })));
  }
}
