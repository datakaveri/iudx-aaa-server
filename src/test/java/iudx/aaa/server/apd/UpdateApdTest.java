package iudx.aaa.server.apd;

import static iudx.aaa.server.apd.Constants.ERR_DETAIL_NO_COS_ADMIN_ROLE;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_CANT_CHANGE_APD_STATUS;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_DUPLICATE_REQ;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_INVALID_APDID;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_NO_COS_ADMIN_ROLE;
import static iudx.aaa.server.apd.Constants.RESP_APD_ID;
import static iudx.aaa.server.apd.Constants.RESP_APD_NAME;
import static iudx.aaa.server.apd.Constants.RESP_APD_STATUS;
import static iudx.aaa.server.apd.Constants.RESP_APD_URL;
import static iudx.aaa.server.apd.Constants.SUCC_TITLE_UPDATED_APD;
import static iudx.aaa.server.apiserver.util.Urn.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import iudx.aaa.server.apiserver.ApdStatus;
import iudx.aaa.server.apiserver.ApdUpdateRequest;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.registration.RegistrationService;
import iudx.aaa.server.registration.Utils;
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
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

/** Unit tests for APD status update. */
@ExtendWith({VertxExtension.class})
@TestMethodOrder(OrderAnnotation.class)
public class UpdateApdTest {

  private static Logger LOGGER = LogManager.getLogger(UpdateApdTest.class);

  private static Configuration config;

  private static String databaseIP;
  private static int databasePort;
  private static String databaseName;
  private static String databaseSchema;
  private static String databaseUserName;
  private static String databasePassword;
  private static int poolSize;
  private static Vertx vertxObj;
  private static ApdService apdService;

  private static PgPool pool;
  private static PoolOptions poolOptions;
  private static PgConnectOptions connectOptions;
  private static ApdWebClient apdWebClient = Mockito.mock(ApdWebClient.class);
  private static RegistrationService registrationService = Mockito.mock(RegistrationService.class);
  private static TokenService tokenService = Mockito.mock(TokenService.class);

  private static final String ACTIVE_A =
      "apd" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

  private static final String ACTIVE_B =
      "apd" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

  private static final String INACTIVE_A =
      "apd" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

  private static final String INACTIVE_B =
      "apd" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

  private static User normalUser = new UserBuilder().userId(UUID.randomUUID()).build();

  private static User trusteeAUser =
      new UserBuilder()
          .userId(UUID.randomUUID())
          .roles(List.of(Roles.TRUSTEE))
          .rolesToRsMapping(
              Map.of(Roles.TRUSTEE.toString(), new JsonArray(List.of(ACTIVE_A, INACTIVE_A))))
          .name("aa", "bb")
          .build();

  private static User trusteeBUser =
      new UserBuilder()
          .userId(UUID.randomUUID())
          .roles(List.of(Roles.TRUSTEE))
          .rolesToRsMapping(
              Map.of(Roles.TRUSTEE.toString(), new JsonArray(List.of(ACTIVE_B, INACTIVE_B))))
          .name("aa", "bb")
          .build();

  private static User cosAdmin =
      new UserBuilder().userId(UUID.randomUUID()).roles(List.of(Roles.COS_ADMIN)).build();

  private static Utils utils;

  @BeforeAll
  @DisplayName("Deploying Verticle")
  static void startVertx(Vertx vertx, VertxTestContext testContext) {
    config = new Configuration();
    vertxObj = vertx;
    JsonObject dbConfig = config.configLoader(4, vertx);

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

    /* Pool options */
    if (poolOptions == null) {
      poolOptions = new PoolOptions().setMaxSize(poolSize);
    }

    pool = PgPool.pool(vertx, connectOptions, poolOptions);

    utils = new Utils(pool);

    Future<Void> create =
        utils
            .createFakeApd(ACTIVE_A, trusteeAUser, ApdStatus.ACTIVE)
            .compose(res -> utils.createFakeApd(ACTIVE_B, trusteeBUser, ApdStatus.ACTIVE))
            .compose(res -> utils.createFakeApd(INACTIVE_A, trusteeAUser, ApdStatus.INACTIVE))
            .compose(res -> utils.createFakeApd(INACTIVE_B, trusteeBUser, ApdStatus.INACTIVE))
            .compose(res -> utils.createFakeUser(normalUser, false, false));

    create
        .onSuccess(
            x -> {
              apdService =
                  new ApdServiceImpl(pool, apdWebClient, registrationService, tokenService);
              testContext.completeNow();
            })
        .onFailure(
            x -> {
              testContext.failNow("Failed");
            });
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing....");
    utils
        .deleteFakeApd()
        .compose(res -> utils.deleteFakeUser())
        .compose(res -> utils.deleteFakeResourceServer())
        .onComplete(
            x -> {
              if (x.failed()) {
                LOGGER.warn(x.cause().getMessage());
              }
              vertxObj.close(testContext.succeeding(response -> testContext.completeNow()));
            });
  }

  /*
   * We make use of ordering for these tests since there are only 4 APDs created. Some tests for
   * 400s and 403s that fail due to APD status at that time may succeed if the test for 200 runs
   * first (which would change the expected status).
   */

  @Order(1)
  @Test
  @DisplayName("Test invalid roles")
  void invalidRoles(VertxTestContext testContext) {
    Checkpoint trusteeNotAllowed = testContext.checkpoint();
    Checkpoint adminProvConsNotAllowed = testContext.checkpoint();

    User provConsAdminUser = new User(normalUser.toJson());
    provConsAdminUser.setRoles(List.of(Roles.CONSUMER, Roles.PROVIDER, Roles.ADMIN));
    provConsAdminUser.setRolesToRsMapping(
        Map.of(
            Roles.CONSUMER.toString(),
            new JsonArray().add("some-url.com"),
            Roles.PROVIDER.toString(),
            new JsonArray().add("some-url.com"),
            Roles.ADMIN.toString(),
            new JsonArray().add("some-url.com")));

    apdService
        .updateApd(List.of(), provConsAdminUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(response.getInteger("status"), 401);
                          assertEquals(URN_INVALID_ROLE.toString(), response.getString("type"));
                          assertEquals(ERR_TITLE_NO_COS_ADMIN_ROLE, response.getString("title"));
                          assertEquals(ERR_DETAIL_NO_COS_ADMIN_ROLE, response.getString("detail"));
                          adminProvConsNotAllowed.flag();
                        })));

    apdService
        .updateApd(List.of(), trusteeAUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(response.getInteger("status"), 401);
                          assertEquals(URN_INVALID_ROLE.toString(), response.getString("type"));
                          assertEquals(ERR_TITLE_NO_COS_ADMIN_ROLE, response.getString("title"));
                          assertEquals(ERR_DETAIL_NO_COS_ADMIN_ROLE, response.getString("detail"));
                          trusteeNotAllowed.flag();
                        })));
  }

  @Order(2)
  @Test
  @DisplayName("Test non-existent apd IDs")
  void nonExistentApdId(VertxTestContext testContext) {

    String randUuid = UUID.randomUUID().toString();
    String activeAId = utils.apdMap.get(ACTIVE_A).toString();

    JsonArray req =
        new JsonArray()
            .add(new JsonObject().put("id", activeAId).put("status", "inactive"))
            .add(new JsonObject().put("id", randUuid).put("status", "active"));
    List<ApdUpdateRequest> request = ApdUpdateRequest.jsonArrayToList(req);

    apdService
        .updateApd(request, cosAdmin)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(response.getInteger("status"), 400);
                          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
                          assertEquals(ERR_TITLE_INVALID_APDID, response.getString("title"));
                          assertEquals(randUuid, response.getString("detail"));
                          testContext.completeNow();
                        })));
  }

  @Order(3)
  @Test
  @DisplayName("Test duplicate apd IDs")
  void duplicateApdIds(VertxTestContext testContext) {

    String inActiveAId = utils.apdMap.get(INACTIVE_A).toString();
    JsonArray req =
        new JsonArray()
            .add(new JsonObject().put("id", inActiveAId).put("status", "active"))
            .add(new JsonObject().put("id", inActiveAId).put("status", "inactive"));
    List<ApdUpdateRequest> request = ApdUpdateRequest.jsonArrayToList(req);

    apdService
        .updateApd(request, cosAdmin)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(response.getInteger("status"), 400);
                          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
                          assertEquals(ERR_TITLE_DUPLICATE_REQ, response.getString("title"));
                          assertEquals(inActiveAId, response.getString("detail"));
                          testContext.completeNow();
                        })));
  }

  @Order(4)
  @Test
  @DisplayName("Test admin changing to existing state")
  void existingStateAdmin(VertxTestContext testContext) {

    String activeBId = utils.apdMap.get(ACTIVE_B).toString();
    String inActiveBId = utils.apdMap.get(INACTIVE_B).toString();
    JsonArray req =
        new JsonArray()
            .add(new JsonObject().put("id", activeBId).put("status", "active"))
            .add(new JsonObject().put("id", inActiveBId).put("status", "active"));
    List<ApdUpdateRequest> request = ApdUpdateRequest.jsonArrayToList(req);

    apdService
        .updateApd(request, cosAdmin)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(response.getInteger("status"), 403);
                          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
                          assertEquals(
                              ERR_TITLE_CANT_CHANGE_APD_STATUS, response.getString("title"));
                          assertEquals(activeBId, response.getString("detail"));
                          testContext.completeNow();
                        })));
  }

  @Order(5)
  @Test
  @DisplayName("Test invalid state change for admin")
  void invalidStateAdmin(VertxTestContext testContext) {

    String activeBId = utils.apdMap.get(ACTIVE_B).toString();
    String inActiveBId = utils.apdMap.get(INACTIVE_B).toString();

    JsonArray req =
        new JsonArray()
            .add(new JsonObject().put("id", activeBId).put("status", "inactive"))
            .add(new JsonObject().put("id", inActiveBId).put("status", "inactive"));
    List<ApdUpdateRequest> request = ApdUpdateRequest.jsonArrayToList(req);

    apdService
        .updateApd(request, cosAdmin)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(response.getInteger("status"), 403);
                          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
                          assertEquals(
                              ERR_TITLE_CANT_CHANGE_APD_STATUS, response.getString("title"));
                          assertEquals(inActiveBId, response.getString("detail"));
                          testContext.completeNow();
                        })));
  }

  @Order(6)
  @Test
  @DisplayName("Test admin changes inactive A APD : inactive -> active")
  void adminInactiveToActive(VertxTestContext testContext) {

    String inActiveAId = utils.apdMap.get(INACTIVE_A).toString();
    JsonArray req =
        new JsonArray().add(new JsonObject().put("id", inActiveAId).put("status", "active"));
    List<ApdUpdateRequest> request = ApdUpdateRequest.jsonArrayToList(req);

    apdService
        .updateApd(request, cosAdmin)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(response.getInteger("status"), 200);
                          assertEquals(URN_SUCCESS.toString(), response.getString("type"));
                          assertEquals(SUCC_TITLE_UPDATED_APD, response.getString("title"));
                          JsonObject result = response.getJsonArray("results").getJsonObject(0);
                          assertEquals(INACTIVE_A + "name", result.getString(RESP_APD_NAME));
                          assertEquals(INACTIVE_A, result.getString(RESP_APD_URL));
                          assertEquals(
                              ApdStatus.ACTIVE.toString().toLowerCase(),
                              result.getString(RESP_APD_STATUS));
                          assertTrue(result.containsKey(RESP_APD_ID));

                          testContext.completeNow();
                        })));
  }

  @Order(7)
  @Test
  @DisplayName(
      "Multiple requests - Test success admin setting active -> inactive, inactive -> active")
  void mutipleReqSuccess(VertxTestContext testContext) {

    String activeAId = utils.apdMap.get(ACTIVE_A).toString();
    String inActiveBId = utils.apdMap.get(INACTIVE_B).toString();
    JsonArray req =
        new JsonArray()
            .add(new JsonObject().put("id", activeAId).put("status", "inactive"))
            .add(new JsonObject().put("id", inActiveBId).put("status", "active"));
    List<ApdUpdateRequest> request = ApdUpdateRequest.jsonArrayToList(req);

    apdService
        .updateApd(request, cosAdmin)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(response.getInteger("status"), 200);
                          assertEquals(URN_SUCCESS.toString(), response.getString("type"));
                          assertEquals(SUCC_TITLE_UPDATED_APD, response.getString("title"));

                          JsonObject resultA = response.getJsonArray("results").getJsonObject(0);
                          JsonObject resultB = response.getJsonArray("results").getJsonObject(1);

                          if (!resultA.getString(RESP_APD_URL).equals(ACTIVE_A)) {
                            resultA = response.getJsonArray("results").getJsonObject(1);
                            resultB = response.getJsonArray("results").getJsonObject(0);
                          }

                          assertEquals(ACTIVE_A + "name", resultA.getString(RESP_APD_NAME));
                          assertEquals(ACTIVE_A, resultA.getString(RESP_APD_URL));
                          assertEquals(
                              ApdStatus.INACTIVE.toString().toLowerCase(),
                              resultA.getString(RESP_APD_STATUS));
                          assertTrue(resultA.containsKey(RESP_APD_ID));

                          assertEquals(INACTIVE_B + "name", resultB.getString(RESP_APD_NAME));
                          assertEquals(INACTIVE_B, resultB.getString(RESP_APD_URL));
                          assertEquals(
                              ApdStatus.ACTIVE.toString().toLowerCase(),
                              resultB.getString(RESP_APD_STATUS));
                          assertTrue(resultB.containsKey(RESP_APD_ID));

                          testContext.completeNow();
                        })));
  }
}
