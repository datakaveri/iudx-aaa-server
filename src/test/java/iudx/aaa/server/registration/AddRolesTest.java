package iudx.aaa.server.registration;

import static iudx.aaa.server.apiserver.util.Urn.URN_ALREADY_EXISTS;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_INPUT;
import static iudx.aaa.server.apiserver.util.Urn.URN_SUCCESS;
import static iudx.aaa.server.registration.Constants.CONFIG_COS_URL;
import static iudx.aaa.server.registration.Constants.CONFIG_OMITTED_SERVERS;
import static iudx.aaa.server.registration.Constants.ERR_CONTEXT_EXISTING_ROLE_FOR_RS;
import static iudx.aaa.server.registration.Constants.ERR_CONTEXT_NOT_FOUND_RS_URLS;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_CONSUMER_FOR_RS_EXISTS;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_PENDING_REJECTED_PROVIDER_RS_REG_EXISTS;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_PROVIDER_FOR_RS_EXISTS;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_RS_NO_EXIST;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_USER_NOT_KC;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_PENDING_REJECTED_PROVIDER_RS_REG_EXISTS;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_ROLE_FOR_RS_EXISTS;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_RS_NO_EXIST;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_USER_NOT_KC;
import static iudx.aaa.server.registration.Constants.PROVIDER_PENDING_MESG;
import static iudx.aaa.server.registration.Constants.RESP_CLIENT_ARR;
import static iudx.aaa.server.registration.Constants.RESP_CLIENT_ID;
import static iudx.aaa.server.registration.Constants.RESP_PHONE;
import static iudx.aaa.server.registration.Constants.SUCC_TITLE_ADDED_ROLES;
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
import iudx.aaa.server.apiserver.AddRolesRequest;
import iudx.aaa.server.apiserver.RoleStatus;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.token.TokenService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

/** Unit tests for adding provider and consumer roles. */
@ExtendWith(VertxExtension.class)
public class AddRolesTest {
  private static Logger LOGGER = LogManager.getLogger(AddRolesTest.class);

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

  private static final String DUMMY_SERVER_ONE =
      "dummyone" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";
  private static final String DUMMY_SERVER_TWO =
      "dummytwo" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + "iudx.io";

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

    CompositeFuture.all(
            utils.createFakeResourceServer(
                DUMMY_SERVER_ONE, new UserBuilder().userId(UUID.randomUUID()).build()),
            utils.createFakeResourceServer(
                DUMMY_SERVER_TWO, new UserBuilder().userId(UUID.randomUUID()).build()))
        .onSuccess(
            succ -> {
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
              vertxObj.close(testContext.succeeding(response -> testContext.completeNow()));
            });
  }

  @Test
  @DisplayName("Test success for new user w/ consumer roles (with phone number)")
  void createConsumerSuccess(VertxTestContext testContext) {
    String email = RandomStringUtils.randomAlphabetic(5).toLowerCase() + "@gmail.com";
    String userId = UUID.randomUUID().toString();

    Mockito.when(kc.getEmailId(userId)).thenReturn(Future.succeededFuture(email));
    String phone = "9989989980";

    JsonObject jsonReq =
        new JsonObject()
            .put(
                Roles.CONSUMER.toString().toLowerCase(),
                new JsonArray(List.of(DUMMY_SERVER_ONE, DUMMY_SERVER_TWO)))
            .put("phone", phone);

    AddRolesRequest request = new AddRolesRequest(jsonReq);

    User user = new UserBuilder().userId(userId).name("aa", "bb").build();

    registrationService
        .addRoles(request, user)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(200, response.getInteger("status"));
                          JsonObject result = response.getJsonObject("results");

                          assertEquals(URN_SUCCESS.toString(), response.getString("type"));
                          assertEquals(SUCC_TITLE_ADDED_ROLES, response.getString("title"));

                          assertTrue(
                              result
                                  .getJsonArray("roles")
                                  .getList()
                                  .contains(Roles.CONSUMER.name().toLowerCase()));

                          assertEquals(result.getString("email"), email);
                          assertTrue(result.getString("userId").equals(userId));
                          assertEquals(result.getJsonObject("name").getString("firstName"), "aa");
                          assertEquals(result.getJsonObject("name").getString("lastName"), "bb");
                          assertEquals(result.getString("phone"), phone);

                          assertTrue(
                              result
                                  .getJsonObject("rolesToRsMapping")
                                  .containsKey(Roles.CONSUMER.toString().toLowerCase()));
                          JsonArray rsForConsumerRole =
                              result
                                  .getJsonObject("rolesToRsMapping")
                                  .getJsonArray(Roles.CONSUMER.toString().toLowerCase());
                          assertTrue(rsForConsumerRole.contains(DUMMY_SERVER_ONE));
                          assertTrue(rsForConsumerRole.contains(DUMMY_SERVER_TWO));

                          assertFalse(result.containsKey(RESP_CLIENT_ARR));

                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Test success for new user w/ provider roles (no phone number, has user info)")
  void createProviderSuccess(VertxTestContext testContext) {
    String email = RandomStringUtils.randomAlphabetic(5).toLowerCase() + "@gmail.com";
    String userId = UUID.randomUUID().toString();

    Mockito.when(kc.getEmailId(userId)).thenReturn(Future.succeededFuture(email));

    JsonObject jsonReq =
        new JsonObject()
            .put(
                Roles.PROVIDER.toString().toLowerCase(),
                new JsonArray(List.of(DUMMY_SERVER_ONE, DUMMY_SERVER_TWO)))
            .put("userInfo", new JsonObject().put("orgAddress", "nowhere"));

    AddRolesRequest request = new AddRolesRequest(jsonReq);

    User user = new UserBuilder().userId(userId).name("aa", "bb").build();

    registrationService
        .addRoles(request, user)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(200, response.getInteger("status"));
                          JsonObject result = response.getJsonObject("results");

                          assertEquals(URN_SUCCESS.toString(), response.getString("type"));
                          assertEquals(
                              SUCC_TITLE_ADDED_ROLES + PROVIDER_PENDING_MESG,
                              response.getString("title"));

                          assertTrue(result.getJsonArray("roles").isEmpty());

                          assertEquals(result.getString("email"), email);
                          assertTrue(result.getString("userId").equals(userId));
                          assertEquals(result.getJsonObject("name").getString("firstName"), "aa");
                          assertEquals(result.getJsonObject("name").getString("lastName"), "bb");
                          assertFalse(result.containsKey(RESP_PHONE));

                          assertTrue(result.getJsonObject("rolesToRsMapping").isEmpty());

                          assertFalse(result.containsKey(RESP_CLIENT_ARR));

                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName(
      "Test success for adding provider + consumer roles for one server, when already has those roles with another server")
  void addRolesForServerWhenAlreadyHasForAnotherServer(VertxTestContext testContext) {

    User user = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();
    user.setRoles(List.of(Roles.CONSUMER, Roles.PROVIDER));
    user.setRolesToRsMapping(
        Map.of(
            Roles.CONSUMER.toString(),
            new JsonArray().add(DUMMY_SERVER_ONE),
            Roles.PROVIDER.toString(),
            new JsonArray().add(DUMMY_SERVER_ONE)));

    utils
        .createFakeUser(user, false, false)
        .onSuccess(
            res -> {
              Mockito.when(kc.getEmailId(user.getUserId()))
                  .thenReturn(Future.succeededFuture(utils.getDetails(user).email));

              JsonObject jsonReq =
                  new JsonObject()
                      .put(
                          Roles.PROVIDER.toString().toLowerCase(),
                          new JsonArray(List.of(DUMMY_SERVER_TWO)))
                      .put(
                          Roles.CONSUMER.toString().toLowerCase(),
                          new JsonArray(List.of(DUMMY_SERVER_TWO)))
                      .put("userInfo", new JsonObject().put("orgAddress", "nowhere"));

              AddRolesRequest request = new AddRolesRequest(jsonReq);

              registrationService
                  .addRoles(request, user)
                  .onComplete(
                      testContext.succeeding(
                          response ->
                              testContext.verify(
                                  () -> {
                                    assertEquals(200, response.getInteger("status"));
                                    JsonObject result = response.getJsonObject("results");

                                    assertEquals(
                                        URN_SUCCESS.toString(), response.getString("type"));
                                    assertEquals(
                                        SUCC_TITLE_ADDED_ROLES + PROVIDER_PENDING_MESG,
                                        response.getString("title"));

                                    assertEquals(
                                        result.getString("email"), utils.getDetails(user).email);
                                    assertTrue(result.getString("userId").equals(user.getUserId()));
                                    assertEquals(
                                        result.getJsonObject("name").getString("firstName"), "aa");
                                    assertEquals(
                                        result.getJsonObject("name").getString("lastName"), "bb");

                                    assertFalse(result.containsKey(RESP_PHONE));

                                    assertTrue(
                                        result
                                            .getJsonArray("roles")
                                            .getList()
                                            .contains(Roles.CONSUMER.name().toLowerCase()));
                                    assertTrue(
                                        result
                                            .getJsonArray("roles")
                                            .getList()
                                            .contains(Roles.PROVIDER.name().toLowerCase()));

                                    assertTrue(
                                        result
                                            .getJsonObject("rolesToRsMapping")
                                            .containsKey(Roles.CONSUMER.toString().toLowerCase()));
                                    JsonArray rsForConsumerRole =
                                        result
                                            .getJsonObject("rolesToRsMapping")
                                            .getJsonArray(Roles.CONSUMER.toString().toLowerCase());
                                    assertTrue(rsForConsumerRole.contains(DUMMY_SERVER_ONE));
                                    assertTrue(rsForConsumerRole.contains(DUMMY_SERVER_TWO));

                                    assertTrue(
                                        result
                                            .getJsonObject("rolesToRsMapping")
                                            .containsKey(Roles.PROVIDER.toString().toLowerCase()));
                                    JsonArray rsForProviderRole =
                                        result
                                            .getJsonObject("rolesToRsMapping")
                                            .getJsonArray(Roles.PROVIDER.toString().toLowerCase());
                                    assertTrue(rsForProviderRole.contains(DUMMY_SERVER_ONE));

                                    // does not have the provider role for the new server yet
                                    assertFalse(rsForProviderRole.contains(DUMMY_SERVER_TWO));

                                    assertFalse(result.containsKey(RESP_CLIENT_ARR));

                                    testContext.completeNow();
                                  })));
            })
        .onFailure(res -> testContext.failNow("failed"));
  }

  @Test
  @DisplayName("Test requested RS does not exist")
  void rsDoesNotExist(VertxTestContext testContext) {
    String email = RandomStringUtils.randomAlphabetic(5).toLowerCase() + "@gmail.com";
    String userId = UUID.randomUUID().toString();

    Mockito.when(kc.getEmailId(userId)).thenReturn(Future.succeededFuture(email));

    String badRs = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";

    JsonObject jsonReq =
        new JsonObject()
            .put(Roles.PROVIDER.toString().toLowerCase(), new JsonArray(List.of(badRs)))
            .put(Roles.CONSUMER.toString().toLowerCase(), new JsonArray(List.of(DUMMY_SERVER_TWO)));

    AddRolesRequest request = new AddRolesRequest(jsonReq);

    User user = new UserBuilder().userId(userId).name("aa", "bb").build();

    registrationService
        .addRoles(request, user)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(400, response.getInteger("status"));
                          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
                          assertEquals(ERR_TITLE_RS_NO_EXIST, response.getString("title"));
                          assertEquals(ERR_DETAIL_RS_NO_EXIST, response.getString("detail"));

                          assertTrue(
                              response
                                  .getJsonObject("context")
                                  .getJsonArray(ERR_CONTEXT_NOT_FOUND_RS_URLS)
                                  .contains(badRs));

                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Test already have approved role for the given RS")
  void alreadyHasRole(VertxTestContext testContext) {
    String email = RandomStringUtils.randomAlphabetic(5).toLowerCase() + "@gmail.com";
    String userId = UUID.randomUUID().toString();

    Mockito.when(kc.getEmailId(userId)).thenReturn(Future.succeededFuture(email));

    // if we set the user object in this way, it means that the user has the provider and consumer
    // roles for DUMMY_SERVER
    User user = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();
    user.setRoles(List.of(Roles.CONSUMER, Roles.PROVIDER));
    user.setRolesToRsMapping(
        Map.of(
            Roles.CONSUMER.toString(),
            new JsonArray().add(DUMMY_SERVER_ONE),
            Roles.PROVIDER.toString(),
            new JsonArray().add(DUMMY_SERVER_ONE)));

    Checkpoint failsConsumer = testContext.checkpoint();
    Checkpoint failsProvider = testContext.checkpoint();

    JsonObject jsonConsReq =
        new JsonObject()
            .put(
                Roles.CONSUMER.toString().toLowerCase(),
                new JsonArray(List.of(DUMMY_SERVER_ONE, DUMMY_SERVER_TWO)));

    AddRolesRequest consRequest = new AddRolesRequest(jsonConsReq);

    registrationService
        .addRoles(consRequest, user)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(409, response.getInteger("status"));
                          assertEquals(URN_ALREADY_EXISTS.toString(), response.getString("type"));
                          assertEquals(ERR_TITLE_ROLE_FOR_RS_EXISTS, response.getString("title"));
                          assertEquals(
                              ERR_DETAIL_CONSUMER_FOR_RS_EXISTS, response.getString("detail"));

                          assertTrue(
                              response
                                  .getJsonObject("context")
                                  .getJsonArray(ERR_CONTEXT_EXISTING_ROLE_FOR_RS)
                                  .contains(DUMMY_SERVER_ONE));

                          failsConsumer.flag();
                        })));

    JsonObject jsonProvReq =
        new JsonObject()
            .put(
                Roles.PROVIDER.toString().toLowerCase(),
                new JsonArray(List.of(DUMMY_SERVER_ONE, DUMMY_SERVER_TWO)));

    AddRolesRequest provRequest = new AddRolesRequest(jsonProvReq);

    registrationService
        .addRoles(provRequest, user)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(409, response.getInteger("status"));
                          assertEquals(URN_ALREADY_EXISTS.toString(), response.getString("type"));
                          assertEquals(ERR_TITLE_ROLE_FOR_RS_EXISTS, response.getString("title"));
                          assertEquals(
                              ERR_DETAIL_PROVIDER_FOR_RS_EXISTS, response.getString("detail"));

                          assertTrue(
                              response
                                  .getJsonObject("context")
                                  .getJsonArray(ERR_CONTEXT_EXISTING_ROLE_FOR_RS)
                                  .contains(DUMMY_SERVER_ONE));
                          failsProvider.flag();
                        })));
  }

  @Test
  @DisplayName(
      "User with pending provider registration cannot register for provider role for same server")
  void pendingOrRejectedProviderCannotGetAgain(VertxTestContext testContext) {

    User user = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();

    utils
        .createFakeUser(user, false, false)
        .compose(
            res ->
                utils.addProviderStatusRole(
                    user, DUMMY_SERVER_ONE, RoleStatus.PENDING, UUID.randomUUID()))
        .onSuccess(
            res -> {
              Mockito.when(kc.getEmailId(user.getUserId()))
                  .thenReturn(Future.succeededFuture(utils.getDetails(user).email));

              JsonObject jsonReq =
                  new JsonObject()
                      .put(
                          Roles.PROVIDER.toString().toLowerCase(),
                          new JsonArray(List.of(DUMMY_SERVER_ONE)))
                      .put(
                          Roles.CONSUMER.toString().toLowerCase(),
                          new JsonArray(List.of(DUMMY_SERVER_ONE)))
                      .put("userInfo", new JsonObject().put("orgAddress", "nowhere"));

              AddRolesRequest request = new AddRolesRequest(jsonReq);

              registrationService
                  .addRoles(request, user)
                  .onComplete(
                      testContext.succeeding(
                          response ->
                              testContext.verify(
                                  () -> {
                                    assertEquals(403, response.getInteger("status"));
                                    assertEquals(
                                        URN_INVALID_INPUT.toString(), response.getString("type"));
                                    assertEquals(
                                        ERR_TITLE_PENDING_REJECTED_PROVIDER_RS_REG_EXISTS,
                                        response.getString("title"));
                                    assertEquals(
                                        ERR_DETAIL_PENDING_REJECTED_PROVIDER_RS_REG_EXISTS,
                                        response.getString("detail"));

                                    assertTrue(
                                        response
                                            .getJsonObject("context")
                                            .getJsonArray(
                                                RoleStatus.PENDING.toString().toLowerCase())
                                            .contains(DUMMY_SERVER_ONE));

                                    testContext.completeNow();
                                  })));
            });
  }

  @Test
  @DisplayName(
      "Test success for multiple roles and multiple RS for existing user (like an admin) with client creds")
  void multipleRolesAndRs(VertxTestContext testContext) {

    User user = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();

    user.setRoles(List.of(Roles.ADMIN));
    user.setRolesToRsMapping(Map.of(Roles.ADMIN.toString(), new JsonArray().add(DUMMY_SERVER_ONE)));

    utils
        .createFakeUser(user, false, false)
        .compose(res -> utils.createClientCreds(user))
        .onSuccess(
            res -> {
              Mockito.when(kc.getEmailId(user.getUserId()))
                  .thenReturn(Future.succeededFuture(utils.getDetails(user).email));

              JsonObject jsonReq =
                  new JsonObject()
                      .put(
                          Roles.PROVIDER.toString().toLowerCase(),
                          new JsonArray(List.of(DUMMY_SERVER_ONE, DUMMY_SERVER_TWO)))
                      .put(
                          Roles.CONSUMER.toString().toLowerCase(),
                          new JsonArray(List.of(DUMMY_SERVER_ONE, DUMMY_SERVER_TWO)));

              AddRolesRequest request = new AddRolesRequest(jsonReq);

              registrationService
                  .addRoles(request, user)
                  .onComplete(
                      testContext.succeeding(
                          response ->
                              testContext.verify(
                                  () -> {
                                    assertEquals(200, response.getInteger("status"));
                                    JsonObject result = response.getJsonObject("results");

                                    assertEquals(
                                        URN_SUCCESS.toString(), response.getString("type"));
                                    assertEquals(
                                        SUCC_TITLE_ADDED_ROLES + PROVIDER_PENDING_MESG,
                                        response.getString("title"));

                                    assertEquals(
                                        result.getString("email"), utils.getDetails(user).email);
                                    assertTrue(result.getString("userId").equals(user.getUserId()));
                                    assertEquals(
                                        result.getJsonObject("name").getString("firstName"), "aa");
                                    assertEquals(
                                        result.getJsonObject("name").getString("lastName"), "bb");

                                    assertFalse(result.containsKey(RESP_PHONE));

                                    assertTrue(
                                        result
                                            .getJsonArray("roles")
                                            .getList()
                                            .contains(Roles.CONSUMER.name().toLowerCase()));
                                    assertTrue(
                                        result
                                            .getJsonArray("roles")
                                            .getList()
                                            .contains(Roles.ADMIN.name().toLowerCase()));

                                    assertFalse(
                                        result
                                            .getJsonArray("roles")
                                            .getList()
                                            .contains(Roles.PROVIDER.name().toLowerCase()));

                                    assertTrue(
                                        result
                                            .getJsonObject("rolesToRsMapping")
                                            .containsKey(Roles.CONSUMER.toString().toLowerCase()));
                                    JsonArray rsForConsumerRole =
                                        result
                                            .getJsonObject("rolesToRsMapping")
                                            .getJsonArray(Roles.CONSUMER.toString().toLowerCase());
                                    assertTrue(rsForConsumerRole.contains(DUMMY_SERVER_ONE));
                                    assertTrue(rsForConsumerRole.contains(DUMMY_SERVER_TWO));

                                    assertFalse(
                                        result
                                            .getJsonObject("rolesToRsMapping")
                                            .containsKey(Roles.PROVIDER.toString().toLowerCase()));

                                    assertTrue(
                                        result
                                            .getJsonObject("rolesToRsMapping")
                                            .containsKey(Roles.ADMIN.toString().toLowerCase()));
                                    JsonArray rsForAdminRole =
                                        result
                                            .getJsonObject("rolesToRsMapping")
                                            .getJsonArray(Roles.ADMIN.toString().toLowerCase());
                                    assertTrue(rsForAdminRole.contains(DUMMY_SERVER_ONE));
                                    assertFalse(rsForAdminRole.contains(DUMMY_SERVER_TWO));

                                    assertTrue(result.containsKey(RESP_CLIENT_ARR));
                                    assertTrue(
                                        result
                                            .getJsonArray(RESP_CLIENT_ARR)
                                            .getJsonObject(0)
                                            .getString(RESP_CLIENT_ID)
                                            .equals(utils.getDetails(user).clientId));

                                    testContext.completeNow();
                                  })));
            })
        .onFailure(res -> testContext.failNow("failed"));
  }

  @Test
  @DisplayName("Test email/user ID not found on Keycloak - should never happen")
  void userNotFoundOnKc(VertxTestContext testContext) {

    String userId = UUID.randomUUID().toString();

    Mockito.when(kc.getEmailId(userId)).thenReturn(Future.succeededFuture(""));

    JsonObject jsonReq =
        new JsonObject()
            .put(
                Roles.PROVIDER.toString().toLowerCase(),
                new JsonArray(List.of(DUMMY_SERVER_ONE, DUMMY_SERVER_TWO)))
            .put("userInfo", new JsonObject().put("orgAddress", "nowhere"));

    AddRolesRequest request = new AddRolesRequest(jsonReq);

    User user = new UserBuilder().userId(userId).name("aa", "bb").build();

    registrationService
        .addRoles(request, user)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
                          assertEquals(400, response.getInteger("status"));
                          assertEquals(ERR_DETAIL_USER_NOT_KC, response.getString("detail"));
                          assertEquals(ERR_TITLE_USER_NOT_KC, response.getString("title"));
                          testContext.completeNow();
                        })));
  }
}
