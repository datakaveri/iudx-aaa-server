package iudx.aaa.server.registration;

import static iudx.aaa.server.apiserver.util.Urn.*;
import static iudx.aaa.server.registration.Constants.CONFIG_COS_URL;
import static iudx.aaa.server.registration.Constants.CONFIG_OMITTED_SERVERS;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_NO_APPROVED_ROLES;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_USER_NOT_KC;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_NO_APPROVED_ROLES;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_USER_NOT_KC;
import static iudx.aaa.server.registration.Constants.RESP_CLIENT_ARR;
import static iudx.aaa.server.registration.Constants.RESP_CLIENT_ID;
import static iudx.aaa.server.registration.Constants.RESP_EMAIL;
import static iudx.aaa.server.registration.Constants.RESP_PHONE;
import static iudx.aaa.server.registration.Constants.SUCC_TITLE_USER_READ;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

/** Unit tests for listing user roles. */
@ExtendWith(VertxExtension.class)
public class ListUserRolesTest {
  private static Logger LOGGER = LogManager.getLogger(ListUserRolesTest.class);

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

  private static Utils utils;

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

    utils = new Utils(pool);

    options
        .put(CONFIG_COS_URL, dbConfig.getString(CONFIG_COS_URL))
        .put(CONFIG_OMITTED_SERVERS, dbConfig.getJsonArray(CONFIG_OMITTED_SERVERS));

    Future<Void> create =
        utils.createFakeResourceServer(
            DUMMY_SERVER, new UserBuilder().userId(UUID.randomUUID()).build());

    create
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
        .deleteFakeResourceServer()
        .compose(res -> utils.deleteFakeUser())
        .onComplete(
            x -> {
              if (x.failed()) {
                LOGGER.warn(x.cause().getMessage());
              }
              vertxObj.close(testContext.succeeding(response -> testContext.completeNow()));
            });
  }

  @Test
  @DisplayName("Test user with no approved roles")
  void userDoesNotExist(VertxTestContext testContext) {

    User noRolesUser = new UserBuilder().userId(UUID.randomUUID()).build();

    registrationService
        .listUser(noRolesUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(404, response.getInteger("status"));
                          assertEquals(ERR_TITLE_NO_APPROVED_ROLES, response.getString("title"));
                          assertEquals(ERR_DETAIL_NO_APPROVED_ROLES, response.getString("detail"));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Test keycloak failure")
  void keycloakFailed(VertxTestContext testContext) {

    User user = new UserBuilder().userId(UUID.randomUUID()).build();
    user.setRoles(List.of(Roles.CONSUMER));
    user.setRolesToRsMapping(Map.of(Roles.CONSUMER.toString(), new JsonArray().add(DUMMY_SERVER)));

    utils
        .createFakeUser(user, true, true)
        .compose(res -> utils.createClientCreds(user))
        .onSuccess(
            succ -> {
              Mockito.when(kc.getEmailId(any())).thenReturn(Future.failedFuture("fail"));

              registrationService
                  .listUser(user)
                  .onComplete(
                      testContext.failing(
                          response ->
                              testContext.verify(
                                  () -> {
                                    testContext.completeNow();
                                  })));
            });
  }

  @Test
  @DisplayName("Test email not on keycloak - should never happen")
  void userEmailFail(VertxTestContext testContext) {

    User user = new UserBuilder().userId(UUID.randomUUID()).build();
    user.setRoles(List.of(Roles.CONSUMER));
    user.setRolesToRsMapping(Map.of(Roles.CONSUMER.toString(), new JsonArray().add(DUMMY_SERVER)));

    utils
        .createFakeUser(user, true, true)
        .compose(res -> utils.createClientCreds(user))
        .onSuccess(
            succ -> {
              Mockito.when(kc.getEmailId(any())).thenReturn(Future.succeededFuture(""));

              registrationService
                  .listUser(user)
                  .onComplete(
                      testContext.succeeding(
                          response ->
                              testContext.verify(
                                  () -> {
                                    assertEquals(400, response.getInteger("status"));
                                    assertEquals(
                                        ERR_TITLE_USER_NOT_KC, response.getString("title"));
                                    assertEquals(
                                        ERR_DETAIL_USER_NOT_KC, response.getString("detail"));
                                    testContext.completeNow();
                                  })));
            });
  }

  @Test
  @DisplayName("Test list user with phone and client credentials")
  void listWithPhoneAndClientCreds(VertxTestContext testContext) {

    User user =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .name("aa", "bb")
            .roles(List.of(Roles.CONSUMER))
            .rolesToRsMapping(Map.of(Roles.CONSUMER.toString(), new JsonArray().add(DUMMY_SERVER)))
            .build();

    utils
        .createFakeUser(user, true, true)
        .compose(res -> utils.createClientCreds(user))
        .onSuccess(
            succ -> {
              Mockito.when(kc.getEmailId(any()))
                  .thenReturn(Future.succeededFuture(utils.getDetails(user).email));

              registrationService
                  .listUser(user)
                  .onComplete(
                      testContext.succeeding(
                          response ->
                              testContext.verify(
                                  () -> {
                                    assertEquals(200, response.getInteger("status"));
                                    assertEquals(SUCC_TITLE_USER_READ, response.getString("title"));
                                    assertEquals(
                                        URN_SUCCESS.toString(), response.getString("type"));

                                    JsonObject result = response.getJsonObject("results");

                                    JsonObject name = result.getJsonObject("name");
                                    assertEquals(
                                        name.getString("firstName"),
                                        user.getName().get("firstName"));
                                    assertEquals(
                                        name.getString("lastName"), user.getName().get("lastName"));

                                    @SuppressWarnings("unchecked")
                                    List<String> returnedRoles =
                                        result.getJsonArray("roles").getList();
                                    assertTrue(
                                        returnedRoles.containsAll(
                                            List.of(Roles.CONSUMER.toString().toLowerCase())));

                                    assertTrue(
                                        result
                                            .getJsonObject("rolesToRsMapping")
                                            .containsKey(Roles.CONSUMER.toString().toLowerCase()));
                                    JsonArray rsUrls =
                                        result
                                            .getJsonObject("rolesToRsMapping")
                                            .getJsonArray(Roles.CONSUMER.toString().toLowerCase());
                                    assertTrue(rsUrls.contains(DUMMY_SERVER));

                                    assertTrue(result.containsKey(RESP_CLIENT_ARR));
                                    JsonArray clients = result.getJsonArray(RESP_CLIENT_ARR);
                                    JsonObject defaultClient = clients.getJsonObject(0);
                                    assertTrue(clients.size() > 0);
                                    assertEquals(
                                        defaultClient.getString(RESP_CLIENT_ID),
                                        utils.getDetails(user).clientId);

                                    assertEquals(
                                        result.getString(RESP_PHONE), utils.getDetails(user).phone);
                                    assertEquals(
                                        result.getString(RESP_EMAIL), utils.getDetails(user).email);
                                    assertEquals(result.getString("userId"), user.getUserId());

                                    testContext.completeNow();
                                  })));
            });
  }

  @Test
  @DisplayName("Test list user withOUT phone and client credentials")
  void listWithNoPhoneNoClientCreds(VertxTestContext testContext) {

    User user =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .name("aa", "bb")
            .roles(List.of(Roles.CONSUMER))
            .rolesToRsMapping(Map.of(Roles.CONSUMER.toString(), new JsonArray().add(DUMMY_SERVER)))
            .build();

    utils
        .createFakeUser(user, false, false)
        .onSuccess(
            succ -> {
              Mockito.when(kc.getEmailId(any()))
                  .thenReturn(Future.succeededFuture(utils.getDetails(user).email));

              registrationService
                  .listUser(user)
                  .onComplete(
                      testContext.succeeding(
                          response ->
                              testContext.verify(
                                  () -> {
                                    assertEquals(200, response.getInteger("status"));
                                    assertEquals(SUCC_TITLE_USER_READ, response.getString("title"));
                                    assertEquals(
                                        URN_SUCCESS.toString(), response.getString("type"));

                                    JsonObject result = response.getJsonObject("results");

                                    JsonObject name = result.getJsonObject("name");
                                    assertEquals(
                                        name.getString("firstName"),
                                        user.getName().get("firstName"));
                                    assertEquals(
                                        name.getString("lastName"), user.getName().get("lastName"));

                                    @SuppressWarnings("unchecked")
                                    List<String> returnedRoles =
                                        result.getJsonArray("roles").getList();
                                    assertTrue(
                                        returnedRoles.containsAll(
                                            List.of(Roles.CONSUMER.toString().toLowerCase())));

                                    assertTrue(
                                        result
                                            .getJsonObject("rolesToRsMapping")
                                            .containsKey(Roles.CONSUMER.toString().toLowerCase()));
                                    JsonArray rsUrls =
                                        result
                                            .getJsonObject("rolesToRsMapping")
                                            .getJsonArray(Roles.CONSUMER.toString().toLowerCase());
                                    assertTrue(rsUrls.contains(DUMMY_SERVER));

                                    assertFalse(result.containsKey(RESP_CLIENT_ARR));
                                    assertFalse(result.containsKey(RESP_PHONE));

                                    assertEquals(
                                        result.getString(RESP_EMAIL), utils.getDetails(user).email);
                                    assertEquals(result.getString("userId"), user.getUserId());

                                    testContext.completeNow();
                                  })));
            });
  }

  @Test
  @DisplayName("Test list user with all roles")
  void userConsumer(VertxTestContext testContext) {

    List<Roles> allRoles = new ArrayList<Roles>(Roles.allRoles);
    Map<String, JsonArray> roleToRsMap =
        allRoles.stream()
            .collect(
                Collectors.toMap(role -> role.toString(), i -> new JsonArray().add(DUMMY_SERVER)));
    // remove COS_ADMIN from role-rs map as that never happens
    roleToRsMap.remove(Roles.COS_ADMIN.toString());

    User user =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .name("aa", "bb")
            .roles(allRoles)
            .rolesToRsMapping(roleToRsMap)
            .build();

    utils
        .createFakeUser(user, false, false)
        .onSuccess(
            succ -> {
              Mockito.when(kc.getEmailId(any()))
                  .thenReturn(Future.succeededFuture(utils.getDetails(user).email));

              registrationService
                  .listUser(user)
                  .onComplete(
                      testContext.succeeding(
                          response ->
                              testContext.verify(
                                  () -> {
                                    assertEquals(200, response.getInteger("status"));
                                    assertEquals(SUCC_TITLE_USER_READ, response.getString("title"));
                                    assertEquals(
                                        URN_SUCCESS.toString(), response.getString("type"));

                                    JsonObject result = response.getJsonObject("results");

                                    JsonObject name = result.getJsonObject("name");
                                    assertEquals(
                                        name.getString("firstName"),
                                        user.getName().get("firstName"));
                                    assertEquals(
                                        name.getString("lastName"), user.getName().get("lastName"));

                                    @SuppressWarnings("unchecked")
                                    List<String> returnedRoles =
                                        result.getJsonArray("roles").getList();
                                    assertTrue(
                                        returnedRoles.containsAll(
                                            Roles.allRoles.stream()
                                                .map(i -> i.toString().toLowerCase())
                                                .collect(Collectors.toList())));

                                    assertTrue(result.containsKey("rolesToRsMapping"));
                                    JsonObject rolesToRs = result.getJsonObject("rolesToRsMapping");

                                    Set<Roles> rolesThatHaveMapping =
                                        new HashSet<Roles>(Roles.allRoles);
                                    rolesThatHaveMapping.remove(Roles.COS_ADMIN);

                                    rolesThatHaveMapping.forEach(
                                        role -> {
                                          assertTrue(
                                              rolesToRs
                                                  .getJsonArray(role.toString().toLowerCase())
                                                  .contains(DUMMY_SERVER));
                                        });

                                    assertFalse(result.containsKey(RESP_CLIENT_ARR));
                                    assertFalse(result.containsKey(RESP_PHONE));

                                    assertEquals(
                                        result.getString(RESP_EMAIL), utils.getDetails(user).email);
                                    assertEquals(result.getString("userId"), user.getUserId());

                                    testContext.completeNow();
                                  })));
            });
  }

  @Test
  @DisplayName("Test COS Admin may not have an entry in DB, but can still call lisy")
  void listCosAdmin(VertxTestContext testContext) {

    User cosAdminUser =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .name("aa", "bb")
            .roles(List.of(Roles.COS_ADMIN))
            .build();

    String cosAdminEmail = RandomStringUtils.randomAlphabetic(10) + "@gmail.com";

    Mockito.when(kc.getEmailId(any())).thenReturn(Future.succeededFuture(cosAdminEmail));

    registrationService
        .listUser(cosAdminUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(200, response.getInteger("status"));
                          assertEquals(SUCC_TITLE_USER_READ, response.getString("title"));
                          assertEquals(URN_SUCCESS.toString(), response.getString("type"));

                          JsonObject result = response.getJsonObject("results");

                          JsonObject name = result.getJsonObject("name");
                          assertEquals(
                              name.getString("firstName"), cosAdminUser.getName().get("firstName"));
                          assertEquals(
                              name.getString("lastName"), cosAdminUser.getName().get("lastName"));

                          @SuppressWarnings("unchecked")
                          List<String> returnedRoles = result.getJsonArray("roles").getList();
                          assertTrue(
                              returnedRoles.containsAll(
                                  List.of(Roles.COS_ADMIN.toString().toLowerCase())));

                          assertTrue(result.getJsonObject("rolesToRsMapping").isEmpty());

                          assertFalse(result.containsKey(RESP_CLIENT_ARR));
                          assertFalse(result.containsKey(RESP_PHONE));

                          assertEquals(result.getString(RESP_EMAIL), cosAdminEmail);
                          assertEquals(result.getString("userId"), cosAdminUser.getUserId());

                          testContext.completeNow();
                        })));
  }
}
