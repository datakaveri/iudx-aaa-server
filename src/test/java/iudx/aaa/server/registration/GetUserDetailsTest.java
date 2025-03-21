package iudx.aaa.server.registration;

import static iudx.aaa.server.registration.Constants.CONFIG_COS_URL;
import static iudx.aaa.server.registration.Constants.CONFIG_OMITTED_SERVERS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

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
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.token.TokenService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

/** Unit tests for getting user details. */
@ExtendWith(VertxExtension.class)
public class GetUserDetailsTest {
  private static Logger LOGGER = LogManager.getLogger(GetUserDetailsTest.class);

  private static Configuration config;

  /* Database Properties */
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
  private static RegistrationService registrationService;
  private static Vertx vertxObj;

  private static KcAdmin kc = Mockito.mock(KcAdmin.class);
  private static TokenService tokenService = Mockito.mock(TokenService.class);
  private static JsonObject options = new JsonObject();

  private static final String DUMMY_SERVER =
      "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

  static User userOne =
      new UserBuilder()
          .userId(UUID.randomUUID())
          .roles(List.of(Roles.CONSUMER, Roles.PROVIDER))
          .rolesToRsMapping(
              Map.of(
                  Roles.CONSUMER.toString(),
                  new JsonArray().add(DUMMY_SERVER),
                  Roles.PROVIDER.toString(),
                  new JsonArray().add(DUMMY_SERVER)))
          .name("aa", "bb")
          .build();

  static User userTwo =
      new UserBuilder()
          .userId(UUID.randomUUID())
          .roles(List.of(Roles.CONSUMER, Roles.PROVIDER))
          .rolesToRsMapping(
              Map.of(
                  Roles.CONSUMER.toString(),
                  new JsonArray().add(DUMMY_SERVER),
                  Roles.PROVIDER.toString(),
                  new JsonArray().add(DUMMY_SERVER)))
          .name("bb", "cc")
          .build();

  private static Utils utils;

  /*
   * for converting getUserDetails's JsonObject output to a Map to make it easier to assert values
   */
  Function<JsonObject, Map<String, JsonObject>> jsonObjectToMap =
      (obj) -> {
        return obj.stream()
            .collect(
                Collectors.toMap(val -> (String) val.getKey(), val -> (JsonObject) val.getValue()));
      };

  @BeforeAll
  @DisplayName("Deploying Verticle")
  static void startVertx(Vertx vertx, VertxTestContext testContext) {
    Configuration config = new Configuration();
    vertxObj = vertx;
    JsonObject dbConfig = config.configLoader(1, vertx);

    /* Read the configuration and set the postgres client properties. */
    LOGGER.debug("Info : Reading config file");

    databaseIP = dbConfig.getString("databaseIP");
    databasePort = Integer.parseInt(dbConfig.getString("databasePort"));
    databaseName = dbConfig.getString("databaseName");
    databaseSchema = dbConfig.getString("databaseSchema");
    databaseUserName = dbConfig.getString("databaseUserName");
    databasePassword = dbConfig.getString("databasePassword");
    poolSize = Integer.parseInt(dbConfig.getString("poolSize"));

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

    if (poolOptions == null) {
      poolOptions = new PoolOptions().setMaxSize(poolSize);
    }

    pool = PgPool.pool(vertx, connectOptions, poolOptions);

    options
        .put(CONFIG_COS_URL, dbConfig.getString(CONFIG_COS_URL))
        .put(CONFIG_OMITTED_SERVERS, dbConfig.getJsonArray(CONFIG_OMITTED_SERVERS));

    utils = new Utils(pool);

    utils
        .createFakeResourceServer(DUMMY_SERVER, new UserBuilder().userId(UUID.randomUUID()).build())
        .compose(res -> utils.createFakeUser(userOne, false, false))
        .compose(sss -> utils.createFakeUser(userTwo, false, false))
        .onSuccess(
            res -> {
              registrationService = new RegistrationServiceImpl(pool, kc, tokenService, options);
              testContext.completeNow();
            })
        .onFailure(err -> testContext.failNow(err.getMessage()));
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing and resetting DB");
    utils
        .deleteFakeUser()
        .compose(res -> utils.deleteFakeResourceServer())
        .onComplete(
            x -> {
              if (x.failed()) {
                LOGGER.warn(x.cause().getMessage());
              }
              vertxObj.close(testContext.succeeding(response -> testContext.completeNow()));
            });
  }

  @Test
  @DisplayName("Test invalid UUID sent")
  void invalidUuid(VertxTestContext testContext) {

    /* when both checkpoints are flagged, the context auto completes */
    Checkpoint nulls = testContext.checkpoint();
    Checkpoint string = testContext.checkpoint();

    JsonObject userOneDetails = utils.getKcAdminJson(userOne);
    Map<String, JsonObject> resp = new HashMap<String, JsonObject>();
    resp.put(userOneDetails.getString("keycloakId"), userOneDetails);
    Mockito.when(kc.getDetails(any())).thenReturn(Future.succeededFuture(resp));

    registrationService
        .getUserDetails(List.of("12345678"))
        .onComplete(
            testContext.failing(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals("Invalid UUID", response.getMessage());
                          string.flag();
                        })));

    List<String> list = new ArrayList<String>();
    list.add(null);
    list.add(userOne.getUserId());

    registrationService
        .getUserDetails(list)
        .onComplete(
            testContext.failing(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals("Invalid UUID", response.getMessage());
                          nulls.flag();
                        })));
  }

  @Test
  @DisplayName("Test empty list")
  void emptyList(VertxTestContext testContext) {
    JsonObject userOneDetails = utils.getKcAdminJson(userOne);
    Map<String, JsonObject> resp = new HashMap<String, JsonObject>();
    resp.put(userOneDetails.getString("keycloakId"), userOneDetails);
    Mockito.when(kc.getDetails(any())).thenReturn(Future.succeededFuture(resp));

    registrationService
        .getUserDetails(new ArrayList<String>())
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertTrue(response.isEmpty());
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Test user does not exist on Keycloak")
  void nonExistentUser(VertxTestContext testContext) {

    String nonExistentUserId = UUID.randomUUID().toString();

    // kc.getDetails returns empty JSON object if a user is not found on KC
    Map<String, JsonObject> resp = new HashMap<String, JsonObject>();
    resp.put(nonExistentUserId, new JsonObject());

    Mockito.when(kc.getDetails(any())).thenReturn(Future.succeededFuture(resp));

    registrationService
        .getUserDetails(List.of(UUID.randomUUID().toString()))
        .onComplete(
            testContext.succeeding(
                jsonResult ->
                    testContext.verify(
                        () -> {
                          Map<String, JsonObject> response = jsonObjectToMap.apply(jsonResult);

                          assertTrue(response.get(nonExistentUserId).isEmpty());
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Test keycloak failure")
  void userEmailFail(VertxTestContext testContext) {
    Mockito.when(kc.getDetails(any())).thenReturn(Future.failedFuture("fail"));

    registrationService
        .getUserDetails(List.of(userOne.getUserId()))
        .onComplete(
            testContext.failing(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals("Internal error", response.getMessage());
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Test successful get")
  void successfulGet(VertxTestContext testContext) {
    Map<String, JsonObject> resp = new HashMap<String, JsonObject>();

    JsonObject userOneDetails = utils.getKcAdminJson(userOne);
    resp.put(userOneDetails.getString("keycloakId"), userOneDetails);

    JsonObject userTwoDetails = utils.getKcAdminJson(userTwo);
    resp.put(userTwoDetails.getString("keycloakId"), userTwoDetails);

    Mockito.when(kc.getDetails(any())).thenReturn(Future.succeededFuture(resp));

    registrationService
        .getUserDetails(List.of(userOne.getUserId(), userTwo.getUserId()))
        .onComplete(
            testContext.succeeding(
                jsonResult ->
                    testContext.verify(
                        () -> {
                          Map<String, JsonObject> response = jsonObjectToMap.apply(jsonResult);

                          JsonObject one = response.get(userOne.getUserId());
                          assertNotNull(one);
                          assertEquals(one.getString("email"), userOneDetails.getString("email"));
                          JsonObject name1 = one.getJsonObject("name");
                          assertNotNull(name1);
                          assertEquals(
                              name1.getString("firstName"), userOne.getName().get("firstName"));
                          assertEquals(
                              name1.getString("lastName"), userOne.getName().get("lastName"));

                          JsonObject two = response.get(userTwo.getUserId());
                          assertNotNull(two);
                          assertEquals(two.getString("email"), userTwoDetails.getString("email"));
                          JsonObject name2 = two.getJsonObject("name");
                          assertNotNull(name2);
                          assertEquals(
                              name2.getString("firstName"), userTwo.getName().get("firstName"));
                          assertEquals(
                              name2.getString("lastName"), userTwo.getName().get("lastName"));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Test duplicate user IDs")
  void duplicateIds(VertxTestContext testContext) {
    JsonObject userOneDetails = utils.getKcAdminJson(userOne);
    Map<String, JsonObject> resp = new HashMap<String, JsonObject>();
    resp.put(userOneDetails.getString("keycloakId"), userOneDetails);
    Mockito.when(kc.getDetails(any())).thenReturn(Future.succeededFuture(resp));

    registrationService
        .getUserDetails(List.of(userOne.getUserId(), userOne.getUserId()))
        .onComplete(
            testContext.succeeding(
                jsonResult ->
                    testContext.verify(
                        () -> {
                          Map<String, JsonObject> response = jsonObjectToMap.apply(jsonResult);

                          assertTrue(response.size() == 1);
                          JsonObject obj = response.get(userOne.getUserId());
                          assertNotNull(obj);
                          assertEquals(obj.getString("email"), userOneDetails.getString("email"));
                          JsonObject name = obj.getJsonObject("name");
                          assertNotNull(name);
                          assertEquals(
                              name.getString("firstName"), userOne.getName().get("firstName"));
                          assertEquals(
                              name.getString("lastName"), userOne.getName().get("lastName"));

                          testContext.completeNow();
                        })));
  }
}
