package iudx.aaa.server.registration;

import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_INPUT;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_ROLE;
import static iudx.aaa.server.apiserver.util.Urn.URN_SUCCESS;
import static iudx.aaa.server.registration.Constants.CONFIG_COS_URL;
import static iudx.aaa.server.registration.Constants.CONFIG_OMITTED_SERVERS;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_NOT_TRUSTEE;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_USER_NOT_FOUND;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_NOT_TRUSTEE;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_USER_NOT_FOUND;
import static iudx.aaa.server.registration.Constants.SUCC_TITLE_USER_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

/** Unit tests for searching for user by trustee. */
@ExtendWith(VertxExtension.class)
public class SearchUserTest {
  private static Logger LOGGER = LogManager.getLogger(SearchUserTest.class);

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

  private static final String DUMMY_SERVER_THAT_NO_ONE_HAS_ROLES_FOR =
      "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

  // no need to register both APD and trustee user
  private static final String DUMMY_APD =
      "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

  private static User trusteeUser =
      new UserBuilder()
          .userId(UUID.randomUUID())
          .roles(List.of(Roles.TRUSTEE))
          .rolesToRsMapping(Map.of(Roles.TRUSTEE.toString(), new JsonArray(List.of(DUMMY_APD))))
          .name("aa", "bb")
          .build();

  private static User providerUser =
      new UserBuilder()
          .userId(UUID.randomUUID())
          .roles(List.of(Roles.PROVIDER))
          .rolesToRsMapping(Map.of(Roles.PROVIDER.toString(), new JsonArray(List.of(DUMMY_SERVER))))
          .name("aa", "bb")
          .build();

  private static User consumerUser =
      new UserBuilder()
          .userId(UUID.randomUUID())
          .roles(List.of(Roles.CONSUMER))
          .rolesToRsMapping(Map.of(Roles.CONSUMER.toString(), new JsonArray(List.of(DUMMY_SERVER))))
          .name("aa", "bb")
          .build();

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

    options
        .put(CONFIG_COS_URL, dbConfig.getString(CONFIG_COS_URL))
        .put(CONFIG_OMITTED_SERVERS, dbConfig.getJsonArray(CONFIG_OMITTED_SERVERS));

    utils = new Utils(pool);

    Future<Void> create =
        utils
            .createFakeResourceServer(
                DUMMY_SERVER, new UserBuilder().userId(UUID.randomUUID()).build())
            .compose(
                res ->
                    utils.createFakeResourceServer(
                        DUMMY_SERVER_THAT_NO_ONE_HAS_ROLES_FOR,
                        new UserBuilder().userId(UUID.randomUUID()).build()))
            .compose(res -> utils.createFakeUser(consumerUser, false, false))
            .compose(res -> utils.createFakeUser(providerUser, false, false));

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
  @DisplayName("Test user does not have trustee role")
  void userDoesNothaveTrusteeRole(VertxTestContext testContext) {

    registrationService
        .searchUser(consumerUser, UUID.randomUUID().toString(), Roles.CONSUMER, DUMMY_SERVER)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(401, response.getInteger("status"));
                          assertEquals(URN_INVALID_ROLE.toString(), response.getString("type"));
                          assertEquals(ERR_TITLE_NOT_TRUSTEE, response.getString("title"));
                          assertEquals(ERR_DETAIL_NOT_TRUSTEE, response.getString("detail"));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Search by email - Successful search for consumer")
  void searchByEmailForConsumerSuccess(VertxTestContext testContext) {

    String consumerUserEmail = utils.getDetails(consumerUser).email;

    Mockito.when(kc.findUserByEmail(consumerUserEmail))
        .thenReturn(Future.succeededFuture(utils.getKcAdminJson(consumerUser)));

    registrationService
        .searchUser(trusteeUser, consumerUserEmail, Roles.CONSUMER, DUMMY_SERVER)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(200, response.getInteger("status"));
                          assertEquals(SUCC_TITLE_USER_FOUND, response.getString("title"));
                          assertEquals(URN_SUCCESS.toString(), response.getString("type"));

                          JsonObject result = response.getJsonObject("results");

                          JsonObject name = result.getJsonObject("name");
                          assertEquals(
                              name.getString("firstName"), consumerUser.getName().get("firstName"));
                          assertEquals(
                              name.getString("lastName"), consumerUser.getName().get("lastName"));

                          assertEquals(result.getString("userId"), consumerUser.getUserId());
                          assertEquals(result.getString("email"), consumerUserEmail);

                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Search by email - Successful search for provider")
  void searchByEmailForProviderSuccess(VertxTestContext testContext) {

    String providerUserEmail = utils.getDetails(providerUser).email;

    Mockito.when(kc.findUserByEmail(providerUserEmail))
        .thenReturn(Future.succeededFuture(utils.getKcAdminJson(providerUser)));

    registrationService
        .searchUser(trusteeUser, providerUserEmail, Roles.PROVIDER, DUMMY_SERVER)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(200, response.getInteger("status"));
                          assertEquals(SUCC_TITLE_USER_FOUND, response.getString("title"));
                          assertEquals(URN_SUCCESS.toString(), response.getString("type"));

                          JsonObject result = response.getJsonObject("results");

                          JsonObject name = result.getJsonObject("name");
                          assertEquals(
                              name.getString("firstName"), providerUser.getName().get("firstName"));
                          assertEquals(
                              name.getString("lastName"), providerUser.getName().get("lastName"));

                          assertEquals(result.getString("userId"), providerUser.getUserId());
                          assertEquals(result.getString("email"), providerUserEmail);

                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Search by email - User exists on Keycloak, but does not have role")
  void searchByEmailUserDoesNotHaveRole(VertxTestContext testContext) {

    String providerUserEmail = utils.getDetails(providerUser).email;

    Mockito.when(kc.findUserByEmail(providerUserEmail))
        .thenReturn(Future.succeededFuture(utils.getKcAdminJson(providerUser)));

    registrationService
        .searchUser(trusteeUser, providerUserEmail, Roles.CONSUMER, DUMMY_SERVER)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(404, response.getInteger("status"));
                          assertEquals(ERR_TITLE_USER_NOT_FOUND, response.getString("title"));
                          assertEquals(ERR_DETAIL_USER_NOT_FOUND, response.getString("detail"));
                          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Search by email - User exists on Keycloak but not registered on COS")
  void searchByEmailUserExistOnKeycloakNotRegdOnCos(VertxTestContext testContext) {

    String randUserEmail = RandomStringUtils.randomAlphabetic(10) + "@gmail.com";

    Mockito.when(kc.findUserByEmail(randUserEmail))
        .thenReturn(
            Future.succeededFuture(
                new JsonObject()
                    .put("keycloakId", UUID.randomUUID().toString())
                    .put("email", randUserEmail)
                    .put(
                        "name",
                        new JsonObject().put("firstName", "rand").put("lastName", "rand"))));

    registrationService
        .searchUser(trusteeUser, randUserEmail, Roles.CONSUMER, DUMMY_SERVER)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(404, response.getInteger("status"));
                          assertEquals(ERR_TITLE_USER_NOT_FOUND, response.getString("title"));
                          assertEquals(ERR_DETAIL_USER_NOT_FOUND, response.getString("detail"));
                          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Search by email - User exists on Keycloak, has role but not for requested RS")
  void searchByEmailUserDoesNotHaveRoleForRs(VertxTestContext testContext) {

    String providerUserEmail = utils.getDetails(providerUser).email;

    Mockito.when(kc.findUserByEmail(providerUserEmail))
        .thenReturn(Future.succeededFuture(utils.getKcAdminJson(providerUser)));

    registrationService
        .searchUser(
            trusteeUser, providerUserEmail, Roles.PROVIDER, DUMMY_SERVER_THAT_NO_ONE_HAS_ROLES_FOR)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(404, response.getInteger("status"));
                          assertEquals(ERR_TITLE_USER_NOT_FOUND, response.getString("title"));
                          assertEquals(ERR_DETAIL_USER_NOT_FOUND, response.getString("detail"));
                          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Search by email - User exists on Keycloak, but RS does not exist")
  void searchByEmailUserRsDoesNotExist(VertxTestContext testContext) {

    String providerUserEmail = utils.getDetails(providerUser).email;

    Mockito.when(kc.findUserByEmail(providerUserEmail))
        .thenReturn(Future.succeededFuture(utils.getKcAdminJson(providerUser)));

    registrationService
        .searchUser(
            trusteeUser,
            providerUserEmail,
            Roles.PROVIDER,
            RandomStringUtils.randomAlphabetic(10) + ".com")
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(404, response.getInteger("status"));
                          assertEquals(ERR_TITLE_USER_NOT_FOUND, response.getString("title"));
                          assertEquals(ERR_DETAIL_USER_NOT_FOUND, response.getString("detail"));
                          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Search by email - User does not exist on Keycloak")
  void searchByEmailUserNotOnKeycloak(VertxTestContext testContext) {

    String providerUserEmail = utils.getDetails(providerUser).email;

    Mockito.when(kc.findUserByEmail(providerUserEmail))
        .thenReturn(Future.succeededFuture(new JsonObject()));

    registrationService
        .searchUser(trusteeUser, providerUserEmail, Roles.PROVIDER, DUMMY_SERVER)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(404, response.getInteger("status"));
                          assertEquals(ERR_TITLE_USER_NOT_FOUND, response.getString("title"));
                          assertEquals(ERR_DETAIL_USER_NOT_FOUND, response.getString("detail"));
                          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Search by UUID - Successful search for consumer")
  void searchByUuidForConsumerSuccess(VertxTestContext testContext) {

    String consumerUserId = consumerUser.getUserId();

    Mockito.when(kc.getDetails(List.of(consumerUserId)))
        .thenReturn(
            Future.succeededFuture(Map.of(consumerUserId, utils.getKcAdminJson(consumerUser))));

    registrationService
        .searchUser(trusteeUser, consumerUserId, Roles.CONSUMER, DUMMY_SERVER)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(200, response.getInteger("status"));
                          assertEquals(SUCC_TITLE_USER_FOUND, response.getString("title"));
                          assertEquals(URN_SUCCESS.toString(), response.getString("type"));

                          JsonObject result = response.getJsonObject("results");

                          JsonObject name = result.getJsonObject("name");
                          assertEquals(
                              name.getString("firstName"), consumerUser.getName().get("firstName"));
                          assertEquals(
                              name.getString("lastName"), consumerUser.getName().get("lastName"));

                          assertEquals(result.getString("userId"), consumerUserId);
                          assertEquals(
                              result.getString("email"), utils.getDetails(consumerUser).email);

                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Search by UUID - Successful search for provider")
  void searchByUuidForProviderSuccess(VertxTestContext testContext) {

    String providerUserId = providerUser.getUserId();

    Mockito.when(kc.getDetails(List.of(providerUserId)))
        .thenReturn(
            Future.succeededFuture(Map.of(providerUserId, utils.getKcAdminJson(providerUser))));

    registrationService
        .searchUser(trusteeUser, providerUserId, Roles.PROVIDER, DUMMY_SERVER)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(200, response.getInteger("status"));
                          assertEquals(SUCC_TITLE_USER_FOUND, response.getString("title"));
                          assertEquals(URN_SUCCESS.toString(), response.getString("type"));

                          JsonObject result = response.getJsonObject("results");

                          JsonObject name = result.getJsonObject("name");
                          assertEquals(
                              name.getString("firstName"), providerUser.getName().get("firstName"));
                          assertEquals(
                              name.getString("lastName"), providerUser.getName().get("lastName"));

                          assertEquals(result.getString("userId"), providerUserId);
                          assertEquals(
                              result.getString("email"), utils.getDetails(providerUser).email);

                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Search by UUID - User exists on Keycloak but not registered on COS")
  void searchByUuidUserExistOnKeycloakNotRegdOnCos(VertxTestContext testContext) {

    String randUserId = UUID.randomUUID().toString();

    Mockito.when(kc.getDetails(List.of(randUserId)))
        .thenReturn(
            Future.succeededFuture(
                Map.of(
                    randUserId,
                    new JsonObject()
                        .put("keycloakId", randUserId)
                        .put("email", RandomStringUtils.randomAlphabetic(10) + "@gmail.com")
                        .put(
                            "name",
                            new JsonObject().put("firstName", "rand").put("lastName", "rand")))));

    registrationService
        .searchUser(trusteeUser, randUserId, Roles.CONSUMER, DUMMY_SERVER)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(404, response.getInteger("status"));
                          assertEquals(ERR_TITLE_USER_NOT_FOUND, response.getString("title"));
                          assertEquals(ERR_DETAIL_USER_NOT_FOUND, response.getString("detail"));
                          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Search by UUID - User exists on Keycloak, but does not have role")
  void searchByUuidUserDoesNotHaveRole(VertxTestContext testContext) {

    String providerUserId = providerUser.getUserId();

    Mockito.when(kc.getDetails(List.of(providerUserId)))
        .thenReturn(
            Future.succeededFuture(Map.of(providerUserId, utils.getKcAdminJson(providerUser))));

    registrationService
        .searchUser(trusteeUser, providerUserId, Roles.CONSUMER, DUMMY_SERVER)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(404, response.getInteger("status"));
                          assertEquals(ERR_TITLE_USER_NOT_FOUND, response.getString("title"));
                          assertEquals(ERR_DETAIL_USER_NOT_FOUND, response.getString("detail"));
                          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Search by UUID - User exists on Keycloak, has role but not for requested RS")
  void searchByUuidUserDoesNotHaveRoleForRs(VertxTestContext testContext) {

    String providerUserId = providerUser.getUserId();

    Mockito.when(kc.getDetails(List.of(providerUserId)))
        .thenReturn(
            Future.succeededFuture(Map.of(providerUserId, utils.getKcAdminJson(providerUser))));

    registrationService
        .searchUser(
            trusteeUser, providerUserId, Roles.PROVIDER, DUMMY_SERVER_THAT_NO_ONE_HAS_ROLES_FOR)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(404, response.getInteger("status"));
                          assertEquals(ERR_TITLE_USER_NOT_FOUND, response.getString("title"));
                          assertEquals(ERR_DETAIL_USER_NOT_FOUND, response.getString("detail"));
                          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Search by UUID - User exists on Keycloak, but RS does not exist")
  void searchByUuidUserRsDoesNotExist(VertxTestContext testContext) {

    String providerUserId = providerUser.getUserId();

    Mockito.when(kc.getDetails(List.of(providerUserId)))
        .thenReturn(
            Future.succeededFuture(Map.of(providerUserId, utils.getKcAdminJson(providerUser))));

    registrationService
        .searchUser(
            trusteeUser,
            providerUserId,
            Roles.PROVIDER,
            RandomStringUtils.randomAlphabetic(10) + ".com")
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(404, response.getInteger("status"));
                          assertEquals(ERR_TITLE_USER_NOT_FOUND, response.getString("title"));
                          assertEquals(ERR_DETAIL_USER_NOT_FOUND, response.getString("detail"));
                          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Search by UUID - User does not exist on Keycloak")
  void searchByUuidUserNotOnKeycloak(VertxTestContext testContext) {

    String providerUserId = providerUser.getUserId();

    Mockito.when(kc.getDetails(List.of(providerUserId)))
        .thenReturn(Future.succeededFuture(Map.of(providerUserId, new JsonObject())));

    registrationService
        .searchUser(trusteeUser, providerUserId, Roles.PROVIDER, DUMMY_SERVER)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(404, response.getInteger("status"));
                          assertEquals(ERR_TITLE_USER_NOT_FOUND, response.getString("title"));
                          assertEquals(ERR_DETAIL_USER_NOT_FOUND, response.getString("detail"));
                          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("When searchString does not match UUID regex, test if email search done by default")
  void emailSearchDoneIfSearchStringNotUuid(VertxTestContext testContext) {

    String notAUuid = RandomStringUtils.randomAlphanumeric(20);

    // this is called only if the email flow is taken
    Mockito.when(kc.findUserByEmail(notAUuid)).thenReturn(Future.succeededFuture(new JsonObject()));

    registrationService
        .searchUser(trusteeUser, notAUuid, Roles.PROVIDER, DUMMY_SERVER)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(404, response.getInteger("status"));
                          assertEquals(ERR_TITLE_USER_NOT_FOUND, response.getString("title"));
                          assertEquals(ERR_DETAIL_USER_NOT_FOUND, response.getString("detail"));
                          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Pending or rejected providers cannot be found")
  void pendingOrRejectedProvidersCannotBeFound(VertxTestContext testContext) {

    Checkpoint pendingProviderNotFound = testContext.checkpoint();
    Checkpoint rejectedProviderNotFound = testContext.checkpoint();

    String pendingUserId = UUID.randomUUID().toString();
    User pendingUser = new UserBuilder().userId(pendingUserId).name("aa", "bb").build();

    utils
        .createFakeUser(pendingUser, false, false)
        .compose(
            res ->
                utils.addProviderStatusRole(
                    pendingUser, DUMMY_SERVER, RoleStatus.PENDING, UUID.randomUUID()))
        .onSuccess(
            res -> {
              Mockito.when(kc.getDetails(List.of(pendingUserId)))
                  .thenReturn(
                      Future.succeededFuture(
                          Map.of(pendingUserId, utils.getKcAdminJson(pendingUser))));

              registrationService
                  .searchUser(trusteeUser, pendingUserId, Roles.PROVIDER, DUMMY_SERVER)
                  .onComplete(
                      testContext.succeeding(
                          response ->
                              testContext.verify(
                                  () -> {
                                    assertEquals(404, response.getInteger("status"));
                                    assertEquals(
                                        ERR_TITLE_USER_NOT_FOUND, response.getString("title"));
                                    assertEquals(
                                        ERR_DETAIL_USER_NOT_FOUND, response.getString("detail"));
                                    assertEquals(
                                        URN_INVALID_INPUT.toString(), response.getString("type"));
                                    pendingProviderNotFound.flag();
                                  })));
            });

    String rejectedUserId = UUID.randomUUID().toString();
    User rejectedUser = new UserBuilder().userId(rejectedUserId).name("aa", "bb").build();

    utils
        .createFakeUser(rejectedUser, false, false)
        .compose(
            res ->
                utils.addProviderStatusRole(
                    rejectedUser, DUMMY_SERVER, RoleStatus.REJECTED, UUID.randomUUID()))
        .onSuccess(
            res -> {
              Mockito.when(kc.getDetails(List.of(rejectedUserId)))
                  .thenReturn(
                      Future.succeededFuture(
                          Map.of(rejectedUserId, utils.getKcAdminJson(pendingUser))));

              registrationService
                  .searchUser(trusteeUser, rejectedUserId, Roles.PROVIDER, DUMMY_SERVER)
                  .onComplete(
                      testContext.succeeding(
                          response ->
                              testContext.verify(
                                  () -> {
                                    assertEquals(404, response.getInteger("status"));
                                    assertEquals(
                                        ERR_TITLE_USER_NOT_FOUND, response.getString("title"));
                                    assertEquals(
                                        ERR_DETAIL_USER_NOT_FOUND, response.getString("detail"));
                                    assertEquals(
                                        URN_INVALID_INPUT.toString(), response.getString("type"));
                                    rejectedProviderNotFound.flag();
                                  })));
            });
  }
}
