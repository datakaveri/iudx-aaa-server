package iudx.aaa.server.policy;

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
import iudx.aaa.server.apiserver.DeletePolicyNotificationRequest;
import iudx.aaa.server.apiserver.NotifRequestStatus;
import iudx.aaa.server.apiserver.RoleStatus;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.registration.RegistrationService;
import iudx.aaa.server.registration.Utils;
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

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_INPUT;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_ROLE;
import static iudx.aaa.server.apiserver.util.Urn.URN_SUCCESS;
import static iudx.aaa.server.policy.Constants.*;
import static iudx.aaa.server.registration.Utils.SQL_CREATE_NOTIFICATION;
import static iudx.aaa.server.registration.Utils.SQL_DELETE_ORG;
import static iudx.aaa.server.registration.Utils.SQL_DELETE_SERVERS;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
public class DeletePolicyNotificationTest {
  private static final String DUMMY_SERVER =
      "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";
  static String name = RandomStringUtils.randomAlphabetic(10).toLowerCase();

  // Database Properties
  static String url = name + ".com";
  static Promise<UUID> orgId;
  static Future<JsonObject> adminUser;
  static Future<JsonObject> providerUser;
  static Future<JsonObject> consumerUserOne;
  static Future<JsonObject> consumerUserTwo;
  static Future<UUID> orgIdFut;
  static UUID otherSerId;
  private static Logger LOGGER =
      LogManager.getLogger(iudx.aaa.server.policy.DeletePolicyNotificationTest.class);
  private static Configuration config;
  private static String databaseIP;
  private static int databasePort;
  private static String databaseName;
  ;
  private static String databaseSchema;
  private static String databaseUserName;
  private static String databasePassword;
  private static int poolSize;
  private static PgPool pool;

  // not used, using constant
  private static PoolOptions poolOptions;
  private static PgConnectOptions connectOptions;
  private static PolicyService policyService;
  private static ApdService apdService = Mockito.mock(ApdService.class);
  private static RegistrationService registrationService = Mockito.mock(RegistrationService.class);
  private static JsonObject authOptions;
  private static JsonObject catOptions;
  private static Vertx vertxObj;
  private static CatalogueClient catalogueClient = Mockito.mock(CatalogueClient.class);
  private static EmailClient emailClient = Mockito.mock(EmailClient.class);


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

    // Pool options
    if (poolOptions == null) {
      poolOptions = new PoolOptions().setMaxSize(poolSize);
    }

    // Create the client pool
    pool = PgPool.pool(vertx, connectOptions, poolOptions);

    orgIdFut =
        pool.withConnection(
            conn ->
                conn.preparedQuery(Utils.SQL_CREATE_ORG)
                    .execute(Tuple.of(name, url))
                    .map(row -> row.iterator().next().getUUID("id")));

    // create users with diff roles
    adminUser =
        orgIdFut.compose(
            orgId ->
                Utils.createFakeUser(
                    pool, orgId.toString(), "", Map.of(Roles.ADMIN, RoleStatus.APPROVED), false));
    providerUser =
        orgIdFut.compose(
            orgId ->
                Utils.createFakeUser(
                    pool,
                    orgId.toString(),
                    "",
                    Map.of(Roles.PROVIDER, RoleStatus.APPROVED),
                    false));
    consumerUserOne =
        orgIdFut.compose(
            orgId ->
                Utils.createFakeUser(
                    pool,
                    orgId.toString(),
                    "",
                    Map.of(Roles.CONSUMER, RoleStatus.APPROVED),
                    false));

    consumerUserTwo =
        orgIdFut.compose(
            orgId ->
                Utils.createFakeUser(
                    pool,
                    orgId.toString(),
                    "",
                    Map.of(Roles.CONSUMER, RoleStatus.APPROVED),
                    false));

    CompositeFuture.all(adminUser, providerUser, consumerUserOne, consumerUserTwo)
        .onSuccess(
            succ -> {
              // create Auth server and other server
              otherSerId = UUID.randomUUID();
              Utils.createFakeResourceServer(pool, adminUser.result(), otherSerId, DUMMY_SERVER)
                  .onSuccess(
                      r -> {
                        policyService =
                            new PolicyServiceImpl(
                                pool,
                                registrationService,
                                apdService,
                                catalogueClient,
                                authOptions,
                                catOptions,emailClient);
                        testContext.completeNow();
                      });
            });
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing....");
    Tuple servers = Tuple.of(List.of(DUMMY_SERVER).toArray());
    List<JsonObject> users =
        List.of(
            adminUser.result(),
            providerUser.result(),
            consumerUserOne.result(),
            consumerUserTwo.result());

    pool.withConnection(
            conn ->
                conn.preparedQuery(SQL_DELETE_SERVERS)
                    .execute(servers)
                    .compose(success -> Utils.deleteFakeUser(pool, users))
                    .compose(
                        succ ->
                            conn.preparedQuery(SQL_DELETE_ORG)
                                .execute(Tuple.of(orgIdFut.result()))))
        .onComplete(
            x -> {
              if (x.failed()) {
                LOGGER.warn(x.cause().getMessage());
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
  Future<UUID> createNotification(
      UUID itemId,
      String itemType,
      NotifRequestStatus status,
      String expiryInterval,
      JsonObject constraints) {

    UUID requestId = UUID.randomUUID();

    UUID providerId = UUID.fromString(providerUser.result().getString("userId"));
    UUID consumerId = UUID.fromString(consumerUserOne.result().getString("userId"));

    Interval intervalToStore = null;
    try {
      Duration duration = DatatypeFactory.newInstance().newDuration(expiryInterval);
      intervalToStore =
          Interval.of(
              duration.getYears(),
              duration.getMonths(),
              duration.getDays(),
              duration.getHours(),
              duration.getMinutes(),
              duration.getSeconds());
    } catch (DatatypeConfigurationException e) {
      return Future.failedFuture("Bad expiry duration");
    }

    Tuple tup =
        Tuple.of(
            requestId,
            consumerId,
            itemId,
            itemType,
            providerId,
            status,
            intervalToStore,
            constraints);

    return pool.withConnection(conn -> conn.preparedQuery(SQL_CREATE_NOTIFICATION).execute(tup))
        .map(succ -> requestId)
        .onSuccess(id -> Future.succeededFuture(id));
  }

  /**
   * Get user details for a list of users
   *
   * @param userIds the list of String user IDs
   * @return JSON object containing user details as how registrationService sends it
   */
  JsonObject fetchUserDetail(List<String> userIds) {
    String providerId = providerUser.result().getString("userId");
    String consumerOneId = consumerUserOne.result().getString("userId");
    String consumerTwoId = consumerUserTwo.result().getString("userId");

    Map<String, JsonObject> map =
        Map.of(
            providerId,
            providerUser.result(),
            consumerOneId,
            consumerUserOne.result(),
            consumerTwoId,
            consumerUserTwo.result());

    JsonObject response = new JsonObject();
    userIds.forEach(
        id -> {
          JsonObject userJson = map.get(id);

          JsonObject details =
              new JsonObject()
                  .put("email", userJson.getString("email"))
                  .put(
                      "name",
                      new JsonObject()
                          .put("firstName", userJson.getString("firstName"))
                          .put("lastName", userJson.getString("lastName")));
          response.put(id, details);
        });

    return response;
  }

  @Test
  @DisplayName("Failure - Test not consumer user calling API")
  void failNotConsumerUser(VertxTestContext testContext) {

    JsonObject userJson = providerUser.result();
    User user =
        new UserBuilder()
            .keycloakId(userJson.getString("keycloakId"))
            .userId(userJson.getString("userId"))
            .name(userJson.getString("firstName"), userJson.getString("lastName"))
            .roles(List.of(Roles.PROVIDER))
            .build();

    /* putting a random UUID as request ID here since it's not important to exist for this test */
    JsonArray req = new JsonArray().add(new JsonObject().put("id", UUID.randomUUID().toString()));

    List<DeletePolicyNotificationRequest> request =
        DeletePolicyNotificationRequest.jsonArrayToList(req);

    policyService.deletePolicyNotification(
        request,
        user,
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      assertEquals(URN_INVALID_ROLE.toString(), response.getString(TYPE));
                      assertEquals(ERR_TITLE_INVALID_ROLES, response.getString("title"));
                      assertEquals(401, response.getInteger("status"));
                      testContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Failure - Test random consumer trying to withdraw notification not owned by them")
  void failConsumerDoesNotOwnRequest(VertxTestContext testContext) {

    UUID itemId = UUID.randomUUID();

    createNotification(
            itemId, "RESOURCE_GROUP", NotifRequestStatus.PENDING, "P1Y", new JsonObject())
        .onSuccess(
            id -> {
              String requestId = id.toString();

              JsonObject userJson = consumerUserTwo.result();

              User user =
                  new UserBuilder()
                      .keycloakId(userJson.getString("keycloakId"))
                      .userId(userJson.getString("userId"))
                      .name(userJson.getString("firstName"), userJson.getString("lastName"))
                      .roles(List.of(Roles.CONSUMER))
                      .build();

              JsonArray req = new JsonArray().add(new JsonObject().put("id", requestId));

              List<DeletePolicyNotificationRequest> request =
                  DeletePolicyNotificationRequest.jsonArrayToList(req);

              policyService.deletePolicyNotification(
                  request,
                  user,
                  testContext.succeeding(
                      response ->
                          testContext.verify(
                              () -> {
                                assertEquals(
                                    URN_INVALID_INPUT.toString(), response.getString(TYPE));
                                assertEquals(
                                    REQ_ID_ALREADY_NOT_EXISTS, response.getString("title"));
                                assertEquals(400, response.getInteger("status"));
                                testContext.completeNow();
                              })));
            });
  }

  @Test
  @DisplayName("Failure - Test consumer trying to withdraw notification that is not pending")
  void failNotPendingNotification(VertxTestContext testContext) {
      UUID itemId = UUID.randomUUID();

      createNotification(
              itemId, "RESOURCE_GROUP", NotifRequestStatus.APPROVED, "P1Y", new JsonObject())
              .onSuccess(
                      id -> {
                          String requestId = id.toString();

                          JsonObject userJson = consumerUserOne.result();

                          User user =
                                  new UserBuilder()
                                          .keycloakId(userJson.getString("keycloakId"))
                                          .userId(userJson.getString("userId"))
                                          .name(userJson.getString("firstName"), userJson.getString("lastName"))
                                          .roles(List.of(Roles.CONSUMER))
                                          .build();

                          JsonArray req = new JsonArray().add(new JsonObject().put("id", requestId));

                          List<DeletePolicyNotificationRequest> request =
                                  DeletePolicyNotificationRequest.jsonArrayToList(req);

                          policyService.deletePolicyNotification(
                                  request,
                                  user,
                                  testContext.succeeding(
                                          response ->
                                                  testContext.verify(
                                                          () -> {
                                                              assertEquals(
                                                                      URN_INVALID_INPUT.toString(), response.getString(TYPE));
                                                              assertEquals(
                                                                      REQ_ID_ALREADY_NOT_EXISTS, response.getString("title"));
                                                              assertEquals(400, response.getInteger("status"));
                                                              testContext.completeNow();
                                                          })));
                      });
  }

  @Test
  @DisplayName("Failure - Test consumer trying to delete notification ID does not exist")
  void failIdNotPresent(VertxTestContext testContext) {
      UUID itemId = UUID.randomUUID();

      createNotification(
              itemId, "RESOURCE_GROUP", NotifRequestStatus.PENDING, "P1Y", new JsonObject())
              .onSuccess(
                      id -> {

                          JsonObject userJson = consumerUserOne.result();

                          User user =
                                  new UserBuilder()
                                          .keycloakId(userJson.getString("keycloakId"))
                                          .userId(userJson.getString("userId"))
                                          .name(userJson.getString("firstName"), userJson.getString("lastName"))
                                          .roles(List.of(Roles.CONSUMER))
                                          .build();

                          JsonArray req = new JsonArray().add(new JsonObject().put("id", UUID.randomUUID().toString()));

                          List<DeletePolicyNotificationRequest> request =
                                  DeletePolicyNotificationRequest.jsonArrayToList(req);

                          policyService.deletePolicyNotification(
                                  request,
                                  user,
                                  testContext.succeeding(
                                          response ->
                                                  testContext.verify(
                                                          () -> {
                                                              assertEquals(
                                                                      URN_INVALID_INPUT.toString(), response.getString(TYPE));
                                                              assertEquals(
                                                                      REQ_ID_ALREADY_NOT_EXISTS, response.getString("title"));
                                                              assertEquals(400, response.getInteger("status"));
                                                              testContext.completeNow();
                                                          })));
                      });

  }

  @Test
  @DisplayName("Success- Test Deleting request for consumer")
  void successDeletion(VertxTestContext testContext) {
      UUID itemId = UUID.randomUUID();

      createNotification(
              itemId, "RESOURCE_GROUP", NotifRequestStatus.PENDING, "P1Y", new JsonObject())
              .onSuccess(
                      id -> {
                          String requestId = id.toString();

                          JsonObject userJson = consumerUserOne.result();

                          User user =
                                  new UserBuilder()
                                          .keycloakId(userJson.getString("keycloakId"))
                                          .userId(userJson.getString("userId"))
                                          .name(userJson.getString("firstName"), userJson.getString("lastName"))
                                          .roles(List.of(Roles.CONSUMER))
                                          .build();

                          /* Mock registration service user details */
                          Mockito.doAnswer(
                                  i -> {
                                      List<String> userIds = i.getArgument(0);
                                      Promise<JsonObject> promise = i.getArgument(1);
                                      promise.complete(fetchUserDetail(userIds));
                                      return i.getMock();
                                  })
                                  .when(registrationService)
                                  .getUserDetails(Mockito.any(), Mockito.any());

                          JsonArray req = new JsonArray().add(new JsonObject().put("id", requestId));

                          List<DeletePolicyNotificationRequest> request =
                                  DeletePolicyNotificationRequest.jsonArrayToList(req);

                          policyService.deletePolicyNotification(
                                  request,
                                  user,
                                  testContext.succeeding(
                                          response ->
                                                  testContext.verify(
                                                          () -> {
                                                              assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
                                                              assertEquals(DELETE_NOTIF_REQ, response.getString("title"));
                                                              assertEquals(200, response.getInteger("status"));
                                                              testContext.completeNow();
                                                          })));
                      });

  }

  @Test
  @DisplayName("Test Delete successfully deleted id")
  void deleteSuccessfullyDeleted(VertxTestContext testContext) {

    UUID itemId = UUID.randomUUID();

    createNotification(
            itemId, "RESOURCE_GROUP", NotifRequestStatus.PENDING, "P1Y", new JsonObject())
        .onSuccess(
            id -> {
              String requestId = id.toString();

              JsonObject userJson = consumerUserOne.result();

              User user =
                  new UserBuilder()
                      .keycloakId(userJson.getString("keycloakId"))
                      .userId(userJson.getString("userId"))
                      .name(userJson.getString("firstName"), userJson.getString("lastName"))
                      .roles(List.of(Roles.CONSUMER))
                      .build();

              /* Mock registration service user details */
              Mockito.doAnswer(
                      i -> {
                        List<String> userIds = i.getArgument(0);
                        Promise<JsonObject> promise = i.getArgument(1);
                        promise.complete(fetchUserDetail(userIds));
                        return i.getMock();
                      })
                  .when(registrationService)
                  .getUserDetails(Mockito.any(), Mockito.any());

              JsonArray req = new JsonArray().add(new JsonObject().put("id", requestId));

              List<DeletePolicyNotificationRequest> request =
                  DeletePolicyNotificationRequest.jsonArrayToList(req);

              policyService.deletePolicyNotification(
                  request,
                  user,
                  testContext.succeeding(
                      response ->
                          testContext.verify(
                              () -> {
                                assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
                                assertEquals(DELETE_NOTIF_REQ, response.getString("title"));
                                assertEquals(200, response.getInteger("status"));
                                policyService.deletePolicyNotification(
                                    request,
                                    user,
                                    testContext.succeeding(
                                        resp ->
                                            testContext.verify(
                                                () -> {
                                                  assertEquals(
                                                      URN_INVALID_INPUT.toString(),
                                                      resp.getString(TYPE));
                                                  assertEquals(
                                                      REQ_ID_ALREADY_NOT_EXISTS,
                                                      resp.getString("title"));
                                                  assertEquals(400, resp.getInteger("status"));
                                                  testContext.completeNow();
                                                })));
                              })));
            });
  }

    @Test
    @DisplayName("Success- Test Delete multiple Pending Notification requests for a consumer")
    void multipleIdSuccess(VertxTestContext testContext) {
        UUID itemId1 = UUID.randomUUID();
        UUID itemId2 = UUID.randomUUID();

        createNotification(
                itemId1, "RESOURCE_GROUP", NotifRequestStatus.PENDING, "P1Y", new JsonObject())
                .onSuccess(
                        id1 -> {
                            createNotification(
                                    itemId2, "RESOURCE_GROUP", NotifRequestStatus.PENDING, "P1Y", new JsonObject())
                                    .onSuccess(
                                            id2 -> {
                                                JsonObject userJson = consumerUserOne.result();

                                                User user =
                                                        new UserBuilder()
                                                                .keycloakId(userJson.getString("keycloakId"))
                                                                .userId(userJson.getString("userId"))
                                                                .name(userJson.getString("firstName"), userJson.getString("lastName"))
                                                                .roles(List.of(Roles.CONSUMER))
                                                                .build();

                                                /* Mock registration service user details */
                                                Mockito.doAnswer(
                                                        i -> {
                                                            List<String> userIds = i.getArgument(0);
                                                            Promise<JsonObject> promise = i.getArgument(1);
                                                            promise.complete(fetchUserDetail(userIds));
                                                            return i.getMock();
                                                        })
                                                        .when(registrationService)
                                                        .getUserDetails(Mockito.any(), Mockito.any());

                                                JsonArray req = new JsonArray().add(new JsonObject().put("id", id1.toString()))
                                                        .add(new JsonObject().put("id", id2.toString()));


                                                List<DeletePolicyNotificationRequest> request =
                                                        DeletePolicyNotificationRequest.jsonArrayToList(req);

                                                policyService.deletePolicyNotification(
                                                        request,
                                                        user,
                                                        testContext.succeeding(
                                                                response ->
                                                                        testContext.verify(
                                                                                () -> {
                                                                                    assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
                                                                                    assertEquals(DELETE_NOTIF_REQ, response.getString("title"));
                                                                                    assertEquals(200, response.getInteger("status"));
                                                                                    testContext.completeNow();
                                                                                })));
                                            });
                        });
    }
}
