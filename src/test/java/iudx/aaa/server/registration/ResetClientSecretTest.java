package iudx.aaa.server.registration;

import static iudx.aaa.server.apiserver.util.Urn.*;
import static iudx.aaa.server.registration.Constants.CLIENT_SECRET_BYTES;
import static iudx.aaa.server.registration.Constants.CONFIG_COS_URL;
import static iudx.aaa.server.registration.Constants.CONFIG_OMITTED_SERVERS;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_INVALID_CLI_ID;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_NO_APPROVED_ROLES;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_INVALID_CLI_ID;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_NO_APPROVED_ROLES;
import static iudx.aaa.server.registration.Constants.RESP_CLIENT_ARR;
import static iudx.aaa.server.registration.Constants.RESP_CLIENT_ID;
import static iudx.aaa.server.registration.Constants.RESP_CLIENT_SC;
import static iudx.aaa.server.registration.Constants.RESP_EMAIL;
import static iudx.aaa.server.registration.Constants.SUCC_TITLE_REGEN_CLIENT_SECRET;
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
import iudx.aaa.server.apiserver.ResetClientSecretRequest;
import iudx.aaa.server.apiserver.RevokeToken;
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

/** Unit tests for resetting client credentials. */
@ExtendWith(VertxExtension.class)
public class ResetClientSecretTest {
  private static Logger LOGGER = LogManager.getLogger(ResetClientSecretTest.class);

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

  private static Utils utils;

  private static final String DUMMY_SERVER_1 =
      "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

  private static final int CLIENT_SECRET_HEX_LEN = CLIENT_SECRET_BYTES * 2;
  private static final String CLIENT_SECRET_REGEX = "^[0-9a-f]{" + CLIENT_SECRET_HEX_LEN + "}$";

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

    options
        .put(CONFIG_OMITTED_SERVERS, dbConfig.getJsonArray(CONFIG_OMITTED_SERVERS))
        .put(CONFIG_COS_URL, dbConfig.getString(CONFIG_COS_URL));
    pool = PgPool.pool(vertx, connectOptions, poolOptions);

    utils = new Utils(pool);

    utils
        .createFakeResourceServer(
            DUMMY_SERVER_1, new UserBuilder().userId(UUID.randomUUID()).build())
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
  @DisplayName("Test user does not have any roles")
  void userDoesNotHaveRoles(VertxTestContext testContext) {
    User user = new UserBuilder().userId(UUID.randomUUID()).name("Foo", "Bar").build();

    JsonObject req = new JsonObject().put("clientId", UUID.randomUUID().toString());

    ResetClientSecretRequest request = new ResetClientSecretRequest(req);

    Mockito.when(kc.getEmailId(any())).thenReturn(Future.succeededFuture("email@gmail.com"));

    registrationService
        .resetClientSecret(request, user)
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
  @DisplayName("[Regen Client Secret] Successfully regen client secret")
  void clientRegenSuccess(VertxTestContext testContext) {

    User user =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .roles(List.of(Roles.CONSUMER))
            .rolesToRsMapping(
                Map.of(Roles.CONSUMER.toString(), new JsonArray().add(DUMMY_SERVER_1)))
            .name("aa", "bb")
            .build();
    Future<Void> created =
        utils.createFakeUser(user, false, false).compose(res -> utils.createClientCreds(user));

    created.onSuccess(
        userJson -> {
          JsonObject req = new JsonObject().put("clientId", utils.getDetails(user).clientId);

          ResetClientSecretRequest request = new ResetClientSecretRequest(req);

          Mockito.when(kc.getEmailId(any()))
              .thenReturn(Future.succeededFuture(utils.getDetails(user).email));

          Mockito.doReturn(
                  Future.succeededFuture(new JsonObject().put("type", URN_SUCCESS.toString())))
              .when(tokenService)
              .revokeToken(any(), any());

          registrationService
              .resetClientSecret(request, user)
              .onComplete(
                  testContext.succeeding(
                      response ->
                          testContext.verify(
                              () -> {
                                assertEquals(200, response.getInteger("status"));
                                assertEquals(
                                    SUCC_TITLE_REGEN_CLIENT_SECRET, response.getString("title"));
                                assertEquals(URN_SUCCESS.toString(), response.getString("type"));

                                JsonObject result = response.getJsonObject("results");

                                JsonObject name = result.getJsonObject("name");
                                assertEquals(
                                    name.getString("firstName"), user.getName().get("firstName"));
                                assertEquals(
                                    name.getString("lastName"), user.getName().get("lastName"));

                                @SuppressWarnings("unchecked")
                                List<String> returnedRoles = result.getJsonArray("roles").getList();
                                List<String> rolesString =
                                    List.of(Roles.CONSUMER.name().toLowerCase());
                                assertTrue(
                                    returnedRoles.containsAll(rolesString)
                                        && rolesString.containsAll(returnedRoles));

                                JsonArray clients = result.getJsonArray(RESP_CLIENT_ARR);
                                JsonObject defaultClient = clients.getJsonObject(0);
                                assertTrue(clients.size() > 0);
                                assertEquals(
                                    defaultClient.getString(RESP_CLIENT_ID),
                                    utils.getDetails(user).clientId);
                                assertTrue(defaultClient.containsKey(RESP_CLIENT_SC));
                                String newClientSec = defaultClient.getString(RESP_CLIENT_SC);
                                assertTrue(newClientSec.matches(CLIENT_SECRET_REGEX));
                                String oldClientSec = utils.getDetails(user).clientSecret;
                                assertFalse(oldClientSec.equals(newClientSec));

                                assertEquals(
                                    result.getString(RESP_EMAIL), utils.getDetails(user).email);
                                assertEquals(result.getString("userId"), user.getUserId());

                                testContext.completeNow();
                              })));
        });
  }

  @Test
  @DisplayName("[Regen Client Secret] Success when some token revoke to a server fails")
  void clientRegenSuccess2(VertxTestContext testContext) {

    User user =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .roles(List.of(Roles.CONSUMER))
            .rolesToRsMapping(
                Map.of(Roles.CONSUMER.toString(), new JsonArray().add(DUMMY_SERVER_1)))
            .name("aa", "bb")
            .build();
    Future<Void> created =
        utils.createFakeUser(user, false, false).compose(res -> utils.createClientCreds(user));

    created.onSuccess(
        userJson -> {
          JsonObject req = new JsonObject().put("clientId", utils.getDetails(user).clientId);

          ResetClientSecretRequest request = new ResetClientSecretRequest(req);

          Mockito.when(kc.getEmailId(any()))
              .thenReturn(Future.succeededFuture(utils.getDetails(user).email));

          Mockito.doAnswer(
                  i -> {
                    RevokeToken r = i.getArgument(0);
                    if (r.getRsUrl().equals(DUMMY_SERVER_1)) {
                      return Future.succeededFuture(
                          new JsonObject().put("type", URN_INVALID_INPUT.toString()));
                    } else {
                      return Future.succeededFuture(
                          new JsonObject().put("type", URN_SUCCESS.toString()));
                    }
                  })
              .when(tokenService)
              .revokeToken(any(), any());

          registrationService
              .resetClientSecret(request, user)
              .onComplete(
                  testContext.succeeding(
                      response ->
                          testContext.verify(
                              () -> {
                                assertEquals(200, response.getInteger("status"));
                                assertEquals(
                                    SUCC_TITLE_REGEN_CLIENT_SECRET, response.getString("title"));
                                assertEquals(URN_SUCCESS.toString(), response.getString("type"));

                                JsonObject result = response.getJsonObject("results");

                                JsonObject name = result.getJsonObject("name");
                                assertEquals(
                                    name.getString("firstName"), user.getName().get("firstName"));
                                assertEquals(
                                    name.getString("lastName"), user.getName().get("lastName"));

                                @SuppressWarnings("unchecked")
                                List<String> returnedRoles = result.getJsonArray("roles").getList();
                                List<String> rolesString =
                                    List.of(Roles.CONSUMER.name().toLowerCase());
                                assertTrue(
                                    returnedRoles.containsAll(rolesString)
                                        && rolesString.containsAll(returnedRoles));

                                JsonArray clients = result.getJsonArray(RESP_CLIENT_ARR);
                                JsonObject defaultClient = clients.getJsonObject(0);
                                assertTrue(clients.size() > 0);
                                assertEquals(
                                    defaultClient.getString(RESP_CLIENT_ID),
                                    utils.getDetails(user).clientId);
                                assertTrue(defaultClient.containsKey(RESP_CLIENT_SC));
                                String newClientSec = defaultClient.getString(RESP_CLIENT_SC);
                                assertTrue(newClientSec.matches(CLIENT_SECRET_REGEX));
                                String oldClientSec = utils.getDetails(user).clientSecret;
                                assertFalse(oldClientSec.equals(newClientSec));

                                assertEquals(
                                    result.getString(RESP_EMAIL), utils.getDetails(user).email);
                                assertEquals(result.getString("userId"), user.getUserId());

                                testContext.completeNow();
                              })));
        });
  }

  @Test
  @DisplayName("[Regen Client Secret] Fail when token revoke fails due to internal error")
  void clientRegenFail(VertxTestContext testContext) {

    User user =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .roles(List.of(Roles.CONSUMER))
            .rolesToRsMapping(
                Map.of(Roles.CONSUMER.toString(), new JsonArray().add(DUMMY_SERVER_1)))
            .name("aa", "bb")
            .build();
    Future<Void> created =
        utils.createFakeUser(user, false, false).compose(res -> utils.createClientCreds(user));

    created.onSuccess(
        userJson -> {
          JsonObject req = new JsonObject().put("clientId", utils.getDetails(user).clientId);

          ResetClientSecretRequest request = new ResetClientSecretRequest(req);

          Mockito.when(kc.getEmailId(any()))
              .thenReturn(Future.succeededFuture(utils.getDetails(user).email));

          /* Revocation to DUMMY_SERVER_1 fails with an internal error */
          Mockito.doAnswer(
                  i -> {
                    RevokeToken r = i.getArgument(0);
                    if (r.getRsUrl().equals(DUMMY_SERVER_1)) {
                      return Future.failedFuture("Internal error");
                    } else {
                      return Future.succeededFuture(
                          new JsonObject().put("type", URN_SUCCESS.toString()));
                    }
                  })
              .when(tokenService)
              .revokeToken(any(), any());

          registrationService
              .resetClientSecret(request, user)
              .onComplete(
                  testContext.failing(
                      response -> testContext.verify(() -> testContext.completeNow())));
        });
  }

  @Test
  @DisplayName("[Regen Client Secret] Client ID not found")
  void clientSecretRegenClientIdNotFound(VertxTestContext testContext) {

    User user =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .roles(List.of(Roles.CONSUMER))
            .rolesToRsMapping(
                Map.of(Roles.CONSUMER.toString(), new JsonArray().add(DUMMY_SERVER_1)))
            .name("aa", "bb")
            .build();

    Future<Void> created =
        utils.createFakeUser(user, false, false).compose(res -> utils.createClientCreds(user));

    created.onSuccess(
        userJson -> {
          Mockito.when(kc.getEmailId(any()))
              .thenReturn(Future.succeededFuture(utils.getDetails(user).email));

          JsonObject req = new JsonObject().put("clientId", UUID.randomUUID().toString());

          ResetClientSecretRequest request = new ResetClientSecretRequest(req);

          registrationService
              .resetClientSecret(request, user)
              .onComplete(
                  testContext.succeeding(
                      response ->
                          testContext.verify(
                              () -> {
                                assertEquals(404, response.getInteger("status"));
                                assertEquals(ERR_TITLE_INVALID_CLI_ID, response.getString("title"));
                                assertEquals(
                                    URN_INVALID_INPUT.toString(), response.getString("type"));
                                assertEquals(
                                    ERR_DETAIL_INVALID_CLI_ID, response.getString("detail"));
                                testContext.completeNow();
                              })));
        });
  }
}
