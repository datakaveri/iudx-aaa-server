package iudx.aaa.server.admin;

import static iudx.aaa.server.admin.Constants.ERR_DETAIL_NOT_ADMIN;
import static iudx.aaa.server.admin.Constants.ERR_TITLE_DUPLICATE_REQ;
import static iudx.aaa.server.admin.Constants.ERR_TITLE_INVALID_PROV_REG_ID;
import static iudx.aaa.server.admin.Constants.ERR_TITLE_NOT_ADMIN;
import static iudx.aaa.server.admin.Constants.RESP_STATUS;
import static iudx.aaa.server.admin.Constants.SUCC_TITLE_PROV_STATUS_UPDATE;
import static iudx.aaa.server.apiserver.util.Urn.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import iudx.aaa.server.apiserver.ProviderUpdateRequest;
import iudx.aaa.server.apiserver.RoleStatus;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.policy.PolicyService;
import iudx.aaa.server.registration.KcAdmin;
import iudx.aaa.server.registration.RegistrationService;
import iudx.aaa.server.registration.Utils;
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

/** Unit tests for update provider registration. */
@ExtendWith({VertxExtension.class})
public class UpdateProviderRegistrationStatusTest {
  private static Logger LOGGER = LogManager.getLogger(UpdateProviderRegistrationStatusTest.class);

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
  private static AdminService adminService;
  private static Vertx vertxObj;

  private static KcAdmin kc = Mockito.mock(KcAdmin.class);
  private static PolicyService policyService = Mockito.mock(PolicyService.class);
  private static RegistrationService registrationService = Mockito.mock(RegistrationService.class);

  private static Utils utils;

  @BeforeAll
  @DisplayName("Deploying Verticle")
  static void startVertx(Vertx vertx, VertxTestContext testContext) {
    config = new Configuration();
    vertxObj = vertx;
    JsonObject dbConfig = config.configLoader(3, vertx);

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

    adminService = new AdminServiceImpl(pool, kc, registrationService);
    testContext.completeNow();
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing....");
    utils
        .deleteFakeResourceServer()
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
  @DisplayName("Test not an admin")
  void notAdmin(VertxTestContext testContext) {

    User user = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();

    UUID id = UUID.randomUUID();

    JsonArray req =
        new JsonArray().add(new JsonObject().put("id", id.toString()).put("status", "approved"));

    List<ProviderUpdateRequest> request = ProviderUpdateRequest.jsonArrayToList(req);

    adminService
        .updateProviderRegistrationStatus(request, user)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(response.getInteger("status"), 401);
                          assertEquals(URN_INVALID_ROLE.toString(), response.getString("type"));
                          assertEquals(ERR_TITLE_NOT_ADMIN, response.getString("title"));
                          assertEquals(ERR_DETAIL_NOT_ADMIN, response.getString("detail"));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Test duplicate IDs in request")
  void duplicateIds(VertxTestContext testContext) {

    // using fake user of a non-existent RS and fake IDs for this test
    User fakeAdminUser = new UserBuilder().userId(UUID.randomUUID()).build();
    fakeAdminUser.setRoles(List.of(Roles.ADMIN));
    fakeAdminUser.setRolesToRsMapping(
        Map.of(
            Roles.ADMIN.toString(),
            new JsonArray().add(RandomStringUtils.randomAlphabetic(10) + ".com")));

    UUID duplicateId = UUID.randomUUID();

    JsonArray req =
        new JsonArray()
            .add(new JsonObject().put("id", duplicateId.toString()).put("status", "approved"))
            .add(new JsonObject().put("id", duplicateId.toString()).put("status", "rejected"))
            .add(
                new JsonObject().put("id", UUID.randomUUID().toString()).put("status", "rejected"));

    List<ProviderUpdateRequest> request = ProviderUpdateRequest.jsonArrayToList(req);

    adminService
        .updateProviderRegistrationStatus(request, fakeAdminUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(response.getInteger("status"), 400);
                          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
                          assertEquals(ERR_TITLE_DUPLICATE_REQ, response.getString("title"));
                          assertEquals(duplicateId.toString(), response.getString("detail"));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Test approve provider")
  void approveProvider(VertxTestContext testContext) {
    String SERVER_URL = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";

    User adminUser = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();
    adminUser.setRoles(List.of(Roles.ADMIN));
    adminUser.setRolesToRsMapping(Map.of(Roles.ADMIN.toString(), new JsonArray().add(SERVER_URL)));

    User providerA = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();

    UUID providerAPendingId = UUID.randomUUID();

    Future<Void> setup =
        utils
            .createFakeResourceServer(SERVER_URL, adminUser)
            .compose(res -> utils.createFakeUser(providerA, false, true))
            .compose(
                res ->
                    utils.addProviderStatusRole(
                        providerA, SERVER_URL, RoleStatus.PENDING, providerAPendingId));

    setup.onSuccess(
        succ -> {
          Map<String, JsonObject> mockKcResp =
              Map.of(providerA.getUserId(), utils.getKcAdminJson(providerA));
          Mockito.when(kc.getDetails(any())).thenReturn(Future.succeededFuture(mockKcResp));

          JsonArray req =
              new JsonArray()
                  .add(
                      new JsonObject()
                          .put("id", providerAPendingId.toString())
                          .put("status", "approved"));
          List<ProviderUpdateRequest> request = ProviderUpdateRequest.jsonArrayToList(req);

          adminService
              .updateProviderRegistrationStatus(request, adminUser)
              .onComplete(
                  testContext.succeeding(
                      response ->
                          testContext.verify(
                              () -> {
                                assertEquals(response.getInteger("status"), 200);
                                assertEquals(response.getString("type"), URN_SUCCESS.toString());
                                assertEquals(
                                    response.getString("title"), SUCC_TITLE_PROV_STATUS_UPDATE);
                                JsonArray res = response.getJsonArray("results");
                                assertTrue(res.size() == 1);

                                JsonObject j = res.getJsonObject(0);
                                assertEquals(j.getString("id"), providerAPendingId.toString());
                                assertEquals(
                                    j.getString(RESP_STATUS),
                                    RoleStatus.APPROVED.name().toLowerCase());

                                assertEquals(
                                    j.getString("email"), utils.getDetails(providerA).email);
                                assertEquals(j.getString("userId"), providerA.getUserId());
                                assertEquals(
                                    j.getJsonObject("userInfo"),
                                    utils.getDetails(providerA).userInfo);
                                testContext.completeNow();
                              })));
        });
  }

  @Test
  @DisplayName("Test reject provider")
  void rejectProvider(VertxTestContext testContext) {
    String SERVER_URL = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";

    User adminUser = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();
    adminUser.setRoles(List.of(Roles.ADMIN));
    adminUser.setRolesToRsMapping(Map.of(Roles.ADMIN.toString(), new JsonArray().add(SERVER_URL)));

    User providerA = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();

    UUID providerAPendingId = UUID.randomUUID();

    Future<Void> setup =
        utils
            .createFakeResourceServer(SERVER_URL, adminUser)
            .compose(res -> utils.createFakeUser(providerA, false, true))
            .compose(
                res ->
                    utils.addProviderStatusRole(
                        providerA, SERVER_URL, RoleStatus.PENDING, providerAPendingId));

    setup.onSuccess(
        succ -> {
          Map<String, JsonObject> mockKcResp =
              Map.of(providerA.getUserId(), utils.getKcAdminJson(providerA));
          Mockito.when(kc.getDetails(any())).thenReturn(Future.succeededFuture(mockKcResp));

          JsonArray req =
              new JsonArray()
                  .add(
                      new JsonObject()
                          .put("id", providerAPendingId.toString())
                          .put("status", "rejected"));
          List<ProviderUpdateRequest> request = ProviderUpdateRequest.jsonArrayToList(req);

          adminService
              .updateProviderRegistrationStatus(request, adminUser)
              .onComplete(
                  testContext.succeeding(
                      response ->
                          testContext.verify(
                              () -> {
                                assertEquals(response.getInteger("status"), 200);
                                assertEquals(response.getString("type"), URN_SUCCESS.toString());
                                assertEquals(
                                    response.getString("title"), SUCC_TITLE_PROV_STATUS_UPDATE);
                                JsonArray res = response.getJsonArray("results");
                                assertTrue(res.size() == 1);

                                JsonObject j = res.getJsonObject(0);
                                assertEquals(j.getString("id"), providerAPendingId.toString());
                                assertEquals(
                                    j.getString(RESP_STATUS),
                                    RoleStatus.REJECTED.name().toLowerCase());

                                assertEquals(
                                    j.getString("email"), utils.getDetails(providerA).email);
                                assertEquals(j.getString("userId"), providerA.getUserId());
                                assertEquals(
                                    j.getJsonObject("userInfo"),
                                    utils.getDetails(providerA).userInfo);
                                testContext.completeNow();
                              })));
        });
  }

  @Test
  @DisplayName("Test non-existent ID")
  void notExistentId(VertxTestContext testContext) {
    String SERVER_URL = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";

    User adminUser = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();
    adminUser.setRoles(List.of(Roles.ADMIN));
    adminUser.setRolesToRsMapping(Map.of(Roles.ADMIN.toString(), new JsonArray().add(SERVER_URL)));

    User providerA = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();

    UUID providerAPendingId = UUID.randomUUID();

    Future<Void> setup =
        utils
            .createFakeResourceServer(SERVER_URL, adminUser)
            .compose(res -> utils.createFakeUser(providerA, false, true))
            .compose(
                res ->
                    utils.addProviderStatusRole(
                        providerA, SERVER_URL, RoleStatus.PENDING, providerAPendingId));

    UUID nonExistentId = UUID.randomUUID();

    setup.onSuccess(
        succ -> {
          Map<String, JsonObject> mockKcResp =
              Map.of(providerA.getUserId(), utils.getKcAdminJson(providerA));
          Mockito.when(kc.getDetails(any())).thenReturn(Future.succeededFuture(mockKcResp));

          JsonArray req =
              new JsonArray()
                  .add(
                      new JsonObject()
                          .put("id", providerAPendingId.toString())
                          .put("status", "approved"))
                  .add(
                      new JsonObject()
                          .put("id", nonExistentId.toString())
                          .put("status", "approved"));
          List<ProviderUpdateRequest> request = ProviderUpdateRequest.jsonArrayToList(req);

          adminService
              .updateProviderRegistrationStatus(request, adminUser)
              .onComplete(
                  testContext.succeeding(
                      response ->
                          testContext.verify(
                              () -> {
                                assertEquals(response.getInteger("status"), 400);
                                assertEquals(
                                    response.getString("type"), URN_INVALID_INPUT.toString());
                                assertEquals(
                                    response.getString("title"), ERR_TITLE_INVALID_PROV_REG_ID);
                                assertEquals(
                                    response.getString("detail"), nonExistentId.toString());
                                testContext.completeNow();
                              })));
        });
  }

  @Test
  @DisplayName("Test ID that does not belong to the admin")
  void idDoesNotBelongToAdmin(VertxTestContext testContext) {
    String SERVER_URL_ONE = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";
    String SERVER_URL_TWO = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";

    User adminOfOne = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();
    adminOfOne.setRoles(List.of(Roles.ADMIN));
    adminOfOne.setRolesToRsMapping(
        Map.of(Roles.ADMIN.toString(), new JsonArray().add(SERVER_URL_ONE)));

    User providerA = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();

    UUID providerAPendingIdOnOne = UUID.randomUUID();
    UUID providerAPendingIdOnTwo = UUID.randomUUID();

    Future<Void> setup =
        utils
            .createFakeResourceServer(SERVER_URL_ONE, adminOfOne)
            .compose(
                res ->
                    utils.createFakeResourceServer(
                        SERVER_URL_TWO, new UserBuilder().userId(UUID.randomUUID()).build()))
            .compose(res -> utils.createFakeUser(providerA, false, true))
            .compose(
                res ->
                    utils.addProviderStatusRole(
                        providerA, SERVER_URL_ONE, RoleStatus.PENDING, providerAPendingIdOnOne))
            .compose(
                res ->
                    utils.addProviderStatusRole(
                        providerA, SERVER_URL_TWO, RoleStatus.PENDING, providerAPendingIdOnTwo));

    setup.onSuccess(
        succ -> {
          Map<String, JsonObject> mockKcResp =
              Map.of(providerA.getUserId(), utils.getKcAdminJson(providerA));
          Mockito.when(kc.getDetails(any())).thenReturn(Future.succeededFuture(mockKcResp));

          JsonArray req =
              new JsonArray()
                  .add(
                      new JsonObject()
                          .put("id", providerAPendingIdOnOne.toString())
                          .put("status", "rejected"))
                  .add(
                      new JsonObject()
                          .put("id", providerAPendingIdOnTwo.toString())
                          .put("status", "rejected"));
          List<ProviderUpdateRequest> request = ProviderUpdateRequest.jsonArrayToList(req);

          adminService
              .updateProviderRegistrationStatus(request, adminOfOne)
              .onComplete(
                  testContext.succeeding(
                      response ->
                          testContext.verify(
                              () -> {
                                assertEquals(response.getInteger("status"), 400);
                                assertEquals(
                                    response.getString("type"), URN_INVALID_INPUT.toString());
                                assertEquals(
                                    response.getString("title"), ERR_TITLE_INVALID_PROV_REG_ID);
                                assertEquals(
                                    response.getString("detail"),
                                    providerAPendingIdOnTwo.toString());
                                testContext.completeNow();
                              })));
        });
  }

  @Test
  @DisplayName(
      "Test both approve and reject in same request for different servers owned by the admin")
  void approveAndRejectTogetherOnDifferentServers(VertxTestContext testContext) {
    String SERVER_URL_ONE = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";
    String SERVER_URL_TWO = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";

    User adminUser = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();
    adminUser.setRoles(List.of(Roles.ADMIN));
    adminUser.setRolesToRsMapping(
        Map.of(Roles.ADMIN.toString(), new JsonArray(List.of(SERVER_URL_ONE, SERVER_URL_TWO))));

    User providerA = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();
    User providerB = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();

    UUID providerAPendingIdOnOne = UUID.randomUUID();
    UUID providerBPendingIdOnTwo = UUID.randomUUID();

    Future<Void> setup =
        utils
            .createFakeResourceServer(SERVER_URL_ONE, adminUser)
            .compose(res -> utils.createFakeResourceServer(SERVER_URL_TWO, adminUser))
            .compose(res -> utils.createFakeUser(providerA, false, true))
            .compose(res -> utils.createFakeUser(providerB, false, true))
            .compose(
                res ->
                    utils.addProviderStatusRole(
                        providerA, SERVER_URL_ONE, RoleStatus.PENDING, providerAPendingIdOnOne))
            .compose(
                res ->
                    utils.addProviderStatusRole(
                        providerB, SERVER_URL_TWO, RoleStatus.PENDING, providerBPendingIdOnTwo));

    Checkpoint sawProviderA = testContext.checkpoint();
    Checkpoint sawProviderB = testContext.checkpoint();

    setup.onSuccess(
        succ -> {
          Map<String, JsonObject> mockKcResp =
              Map.of(
                  providerA.getUserId(),
                  utils.getKcAdminJson(providerA),
                  providerB.getUserId(),
                  utils.getKcAdminJson(providerB));
          Mockito.when(kc.getDetails(any())).thenReturn(Future.succeededFuture(mockKcResp));

          JsonArray req =
              new JsonArray()
                  .add(
                      new JsonObject()
                          .put("id", providerAPendingIdOnOne.toString())
                          .put("status", "approved"))
                  .add(
                      new JsonObject()
                          .put("id", providerBPendingIdOnTwo.toString())
                          .put("status", "rejected"));
          List<ProviderUpdateRequest> request = ProviderUpdateRequest.jsonArrayToList(req);

          adminService
              .updateProviderRegistrationStatus(request, adminUser)
              .onComplete(
                  testContext.succeeding(
                      response ->
                          testContext.verify(
                              () -> {
                                assertEquals(response.getInteger("status"), 200);
                                assertEquals(response.getString("type"), URN_SUCCESS.toString());
                                assertEquals(
                                    response.getString("title"), SUCC_TITLE_PROV_STATUS_UPDATE);
                                JsonArray res = response.getJsonArray("results");
                                assertTrue(res.size() == 2);

                                res.forEach(
                                    i -> {
                                      JsonObject j = (JsonObject) i;

                                      if (j.getString("id")
                                          .equals(providerAPendingIdOnOne.toString())) {
                                        assertEquals(
                                            j.getString(Constants.RESP_STATUS),
                                            RoleStatus.APPROVED.name().toLowerCase());
                                        assertEquals(j.getString("rsUrl"), SERVER_URL_ONE);
                                        assertEquals(
                                            j.getString("email"),
                                            utils.getDetails(providerA).email);
                                        assertEquals(j.getString("userId"), providerA.getUserId());
                                        assertEquals(
                                            j.getJsonObject("userInfo"),
                                            utils.getDetails(providerA).userInfo);
                                        sawProviderA.flag();
                                      }

                                      if (j.getString("id")
                                          .equals(providerBPendingIdOnTwo.toString())) {
                                        assertEquals(
                                            j.getString(Constants.RESP_STATUS),
                                            RoleStatus.REJECTED.name().toLowerCase());
                                        assertEquals(j.getString("rsUrl"), SERVER_URL_TWO);
                                        assertEquals(
                                            j.getString("email"),
                                            utils.getDetails(providerB).email);
                                        assertEquals(j.getString("userId"), providerB.getUserId());
                                        assertEquals(
                                            j.getJsonObject("userInfo"),
                                            utils.getDetails(providerB).userInfo);
                                        sawProviderB.flag();
                                      }
                                    });
                              })));
        });
  }

  @Test
  @DisplayName("Test approve provider and approve again")
  void approveProviderAndApproveAgain(VertxTestContext testContext) {
    String SERVER_URL = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";

    User adminUser = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();
    adminUser.setRoles(List.of(Roles.ADMIN));
    adminUser.setRolesToRsMapping(Map.of(Roles.ADMIN.toString(), new JsonArray().add(SERVER_URL)));

    User providerA = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();

    UUID providerAPendingId = UUID.randomUUID();

    Future<Void> setup =
        utils
            .createFakeResourceServer(SERVER_URL, adminUser)
            .compose(res -> utils.createFakeUser(providerA, false, true))
            .compose(
                res ->
                    utils.addProviderStatusRole(
                        providerA, SERVER_URL, RoleStatus.PENDING, providerAPendingId));

    Checkpoint approveSuccessfully = testContext.checkpoint();
    Checkpoint failApprove = testContext.checkpoint();

    setup.onSuccess(
        succ -> {
          Map<String, JsonObject> mockKcResp =
              Map.of(providerA.getUserId(), utils.getKcAdminJson(providerA));
          Mockito.when(kc.getDetails(any())).thenReturn(Future.succeededFuture(mockKcResp));

          JsonArray req =
              new JsonArray()
                  .add(
                      new JsonObject()
                          .put("id", providerAPendingId.toString())
                          .put("status", "approved"));
          List<ProviderUpdateRequest> request = ProviderUpdateRequest.jsonArrayToList(req);

          adminService
              .updateProviderRegistrationStatus(request, adminUser)
              .onComplete(
                  testContext.succeeding(
                      response ->
                          testContext.verify(
                              () -> {
                                assertEquals(response.getInteger("status"), 200);
                                assertEquals(response.getString("type"), URN_SUCCESS.toString());
                                assertEquals(
                                    response.getString("title"), SUCC_TITLE_PROV_STATUS_UPDATE);
                                JsonArray res = response.getJsonArray("results");
                                assertTrue(res.size() == 1);

                                JsonObject j = res.getJsonObject(0);
                                assertEquals(j.getString("id"), providerAPendingId.toString());
                                assertEquals(
                                    j.getString(RESP_STATUS),
                                    RoleStatus.APPROVED.name().toLowerCase());

                                assertEquals(
                                    j.getString("email"), utils.getDetails(providerA).email);
                                assertEquals(j.getString("userId"), providerA.getUserId());
                                assertEquals(
                                    j.getJsonObject("userInfo"),
                                    utils.getDetails(providerA).userInfo);

                                approveSuccessfully.flag();

                                JsonArray badReq =
                                    new JsonArray()
                                        .add(
                                            new JsonObject()
                                                .put("id", providerAPendingId.toString())
                                                .put("status", "approved"));
                                List<ProviderUpdateRequest> badRequest =
                                    ProviderUpdateRequest.jsonArrayToList(badReq);

                                adminService
                                    .updateProviderRegistrationStatus(badRequest, adminUser)
                                    .onComplete(
                                        testContext.succeeding(
                                            resp ->
                                                testContext.verify(
                                                    () -> {
                                                      assertEquals(resp.getInteger("status"), 400);
                                                      assertEquals(
                                                          resp.getString("type"),
                                                          URN_INVALID_INPUT.toString());
                                                      assertEquals(
                                                          resp.getString("title"),
                                                          ERR_TITLE_INVALID_PROV_REG_ID);
                                                      assertEquals(
                                                          resp.getString("detail"),
                                                          providerAPendingId.toString());

                                                      failApprove.flag();
                                                    })));
                              })));
        });
  }

  @Test
  @DisplayName("Test reject provider and approve again")
  void rejectProviderThenApprove(VertxTestContext testContext) {
    String SERVER_URL = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";

    User adminUser = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();
    adminUser.setRoles(List.of(Roles.ADMIN));
    adminUser.setRolesToRsMapping(Map.of(Roles.ADMIN.toString(), new JsonArray().add(SERVER_URL)));

    User providerA = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();

    UUID providerAPendingId = UUID.randomUUID();

    Future<Void> setup =
        utils
            .createFakeResourceServer(SERVER_URL, adminUser)
            .compose(res -> utils.createFakeUser(providerA, false, true))
            .compose(
                res ->
                    utils.addProviderStatusRole(
                        providerA, SERVER_URL, RoleStatus.PENDING, providerAPendingId));

    Checkpoint rejectSuccessfully = testContext.checkpoint();
    Checkpoint failApprove = testContext.checkpoint();

    setup.onSuccess(
        succ -> {
          Map<String, JsonObject> mockKcResp =
              Map.of(providerA.getUserId(), utils.getKcAdminJson(providerA));
          Mockito.when(kc.getDetails(any())).thenReturn(Future.succeededFuture(mockKcResp));

          JsonArray req =
              new JsonArray()
                  .add(
                      new JsonObject()
                          .put("id", providerAPendingId.toString())
                          .put("status", "rejected"));
          List<ProviderUpdateRequest> request = ProviderUpdateRequest.jsonArrayToList(req);

          adminService
              .updateProviderRegistrationStatus(request, adminUser)
              .onComplete(
                  testContext.succeeding(
                      response ->
                          testContext.verify(
                              () -> {
                                assertEquals(response.getInteger("status"), 200);
                                assertEquals(response.getString("type"), URN_SUCCESS.toString());
                                assertEquals(
                                    response.getString("title"), SUCC_TITLE_PROV_STATUS_UPDATE);
                                JsonArray res = response.getJsonArray("results");
                                assertTrue(res.size() == 1);

                                JsonObject j = res.getJsonObject(0);
                                assertEquals(j.getString("id"), providerAPendingId.toString());
                                assertEquals(
                                    j.getString(RESP_STATUS),
                                    RoleStatus.REJECTED.name().toLowerCase());

                                assertEquals(
                                    j.getString("email"), utils.getDetails(providerA).email);
                                assertEquals(j.getString("userId"), providerA.getUserId());
                                assertEquals(
                                    j.getJsonObject("userInfo"),
                                    utils.getDetails(providerA).userInfo);

                                rejectSuccessfully.flag();

                                JsonArray badReq =
                                    new JsonArray()
                                        .add(
                                            new JsonObject()
                                                .put("id", providerAPendingId.toString())
                                                .put("status", "approved"));
                                List<ProviderUpdateRequest> badRequest =
                                    ProviderUpdateRequest.jsonArrayToList(badReq);

                                adminService
                                    .updateProviderRegistrationStatus(badRequest, adminUser)
                                    .onComplete(
                                        testContext.succeeding(
                                            resp ->
                                                testContext.verify(
                                                    () -> {
                                                      assertEquals(resp.getInteger("status"), 400);
                                                      assertEquals(
                                                          resp.getString("type"),
                                                          URN_INVALID_INPUT.toString());
                                                      assertEquals(
                                                          resp.getString("title"),
                                                          ERR_TITLE_INVALID_PROV_REG_ID);
                                                      assertEquals(
                                                          resp.getString("detail"),
                                                          providerAPendingId.toString());

                                                      failApprove.flag();
                                                    })));
                              })));
        });
  }

  @Test
  @DisplayName("Test Keycloak fails to get details + rollback ")
  void keycloakFailRollback(VertxTestContext testContext) {
    String SERVER_URL = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";

    User adminUser = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();
    adminUser.setRoles(List.of(Roles.ADMIN));
    adminUser.setRolesToRsMapping(Map.of(Roles.ADMIN.toString(), new JsonArray().add(SERVER_URL)));

    User providerA = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();

    UUID providerAPendingId = UUID.randomUUID();

    Future<Void> setup =
        utils
            .createFakeResourceServer(SERVER_URL, adminUser)
            .compose(res -> utils.createFakeUser(providerA, false, true))
            .compose(
                res ->
                    utils.addProviderStatusRole(
                        providerA, SERVER_URL, RoleStatus.PENDING, providerAPendingId));

    Checkpoint keycloakFailed = testContext.checkpoint();
    Checkpoint rollBackSuccess = testContext.checkpoint();

    setup.onSuccess(
        succ -> {

          // bad KC behaviour
          Mockito.when(kc.getDetails(any())).thenReturn(Future.failedFuture("fail"));

          JsonArray req =
              new JsonArray()
                  .add(
                      new JsonObject()
                          .put("id", providerAPendingId.toString())
                          .put("status", "approved"));
          List<ProviderUpdateRequest> request = ProviderUpdateRequest.jsonArrayToList(req);

          adminService
              .updateProviderRegistrationStatus(request, adminUser)
              .onComplete(
                  testContext.failing(
                      err ->
                          testContext.verify(
                              () -> {
                                keycloakFailed.flag();

                                // mock correct KC behaviour
                                Map<String, JsonObject> mockKcResp =
                                    Map.of(providerA.getUserId(), utils.getKcAdminJson(providerA));
                                Mockito.when(kc.getDetails(any()))
                                    .thenReturn(Future.succeededFuture(mockKcResp));

                                // request should succeed because of rollback
                                adminService
                                    .updateProviderRegistrationStatus(request, adminUser)
                                    .onComplete(
                                        testContext.succeeding(
                                            response ->
                                                testContext.verify(
                                                    () -> {
                                                      assertEquals(
                                                          response.getInteger("status"), 200);
                                                      assertEquals(
                                                          response.getString("type"),
                                                          URN_SUCCESS.toString());
                                                      assertEquals(
                                                          response.getString("title"),
                                                          SUCC_TITLE_PROV_STATUS_UPDATE);
                                                      JsonArray res =
                                                          response.getJsonArray("results");
                                                      assertTrue(res.size() == 1);

                                                      JsonObject j = res.getJsonObject(0);
                                                      assertEquals(
                                                          j.getString("id"),
                                                          providerAPendingId.toString());
                                                      assertEquals(
                                                          j.getString(RESP_STATUS),
                                                          RoleStatus.APPROVED.name().toLowerCase());

                                                      assertEquals(
                                                          j.getString("email"),
                                                          utils.getDetails(providerA).email);
                                                      assertEquals(
                                                          j.getString("userId"),
                                                          providerA.getUserId());
                                                      assertEquals(
                                                          j.getJsonObject("userInfo"),
                                                          utils.getDetails(providerA).userInfo);

                                                      rollBackSuccess.flag();
                                                    })));
                              })));
        });
  }
}
