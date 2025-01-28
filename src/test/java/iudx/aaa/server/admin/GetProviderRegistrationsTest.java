package iudx.aaa.server.admin;

import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_ROLE;
import static iudx.aaa.server.apiserver.util.Urn.URN_SUCCESS;
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
import iudx.aaa.server.apiserver.RoleStatus;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;
import iudx.aaa.server.configuration.Configuration;
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

/** Unit tests for get provider registrations. */
@ExtendWith({VertxExtension.class})
public class GetProviderRegistrationsTest {
  private static Logger LOGGER = LogManager.getLogger(GetProviderRegistrationsTest.class);

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

    adminService
        .getProviderRegistrations(RoleStatus.PENDING, user)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(response.getInteger("status"), 401);
                          assertEquals(URN_INVALID_ROLE.toString(), response.getString("type"));
                          assertEquals(Constants.ERR_TITLE_NOT_ADMIN, response.getString("title"));
                          assertEquals(
                              Constants.ERR_DETAIL_NOT_ADMIN, response.getString("detail"));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Test no registrations in any state")
  void noRegsInAnyState(VertxTestContext testContext) {

    String SERVER_URL = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";

    User adminUser = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();
    adminUser.setRoles(List.of(Roles.ADMIN));
    adminUser.setRolesToRsMapping(Map.of(Roles.ADMIN.toString(), new JsonArray().add(SERVER_URL)));

    Future<Void> setup = utils.createFakeResourceServer(SERVER_URL, adminUser);

    Checkpoint pendingEmpty = testContext.checkpoint();
    Checkpoint rejectedEmpty = testContext.checkpoint();
    Checkpoint approvedEmpty = testContext.checkpoint();

    setup.onSuccess(
        succ -> {
          adminService
              .getProviderRegistrations(RoleStatus.PENDING, adminUser)
              .onComplete(
                  testContext.succeeding(
                      response ->
                          testContext.verify(
                              () -> {
                                assertEquals(response.getInteger("status"), 200);
                                assertEquals(response.getString("type"), URN_SUCCESS.toString());
                                assertEquals(
                                    response.getString("title"),
                                    Constants.SUCC_TITLE_PROVIDER_REGS);
                                JsonArray res = response.getJsonArray("results");
                                assertTrue(res.isEmpty());
                                pendingEmpty.flag();
                              })));

          adminService
              .getProviderRegistrations(RoleStatus.REJECTED, adminUser)
              .onComplete(
                  testContext.succeeding(
                      response ->
                          testContext.verify(
                              () -> {
                                assertEquals(response.getInteger("status"), 200);
                                assertEquals(response.getString("type"), URN_SUCCESS.toString());
                                assertEquals(
                                    response.getString("title"),
                                    Constants.SUCC_TITLE_PROVIDER_REGS);
                                JsonArray res = response.getJsonArray("results");
                                assertTrue(res.isEmpty());
                                rejectedEmpty.flag();
                              })));

          adminService
              .getProviderRegistrations(RoleStatus.APPROVED, adminUser)
              .onComplete(
                  testContext.succeeding(
                      response ->
                          testContext.verify(
                              () -> {
                                assertEquals(response.getInteger("status"), 200);
                                assertEquals(response.getString("type"), URN_SUCCESS.toString());
                                assertEquals(
                                    response.getString("title"),
                                    Constants.SUCC_TITLE_PROVIDER_REGS);
                                JsonArray res = response.getJsonArray("results");
                                assertTrue(res.isEmpty());
                                approvedEmpty.flag();
                              })));
        });
  }

  @Test
  @DisplayName("Test get pending registration")
  void pendingReg(VertxTestContext testContext) {

    String SERVER_URL = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";

    User adminUser = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();
    adminUser.setRoles(List.of(Roles.ADMIN));
    adminUser.setRolesToRsMapping(Map.of(Roles.ADMIN.toString(), new JsonArray().add(SERVER_URL)));

    User providerA = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();
    User providerB = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();

    UUID providerAPendingId = UUID.randomUUID();
    UUID providerBPendingId = UUID.randomUUID();

    Checkpoint sawProviderA = testContext.checkpoint();
    Checkpoint sawProviderB = testContext.checkpoint();

    Future<Void> setup =
        utils
            .createFakeResourceServer(SERVER_URL, adminUser)
            .compose(res -> utils.createFakeUser(providerA, false, true))
            .compose(res -> utils.createFakeUser(providerB, false, true))
            .compose(
                res ->
                    utils.addProviderStatusRole(
                        providerA, SERVER_URL, RoleStatus.PENDING, providerAPendingId))
            .compose(
                res ->
                    utils.addProviderStatusRole(
                        providerB, SERVER_URL, RoleStatus.PENDING, providerBPendingId));

    setup.onSuccess(
        succ -> {
          Map<String, JsonObject> mockKcResp =
              Map.of(
                  providerA.getUserId(),
                  utils.getKcAdminJson(providerA),
                  providerB.getUserId(),
                  utils.getKcAdminJson(providerB));
          Mockito.when(kc.getDetails(any())).thenReturn(Future.succeededFuture(mockKcResp));

          adminService
              .getProviderRegistrations(RoleStatus.PENDING, adminUser)
              .onComplete(
                  testContext.succeeding(
                      response ->
                          testContext.verify(
                              () -> {
                                assertEquals(response.getInteger("status"), 200);
                                assertEquals(response.getString("type"), URN_SUCCESS.toString());
                                assertEquals(
                                    response.getString("title"),
                                    Constants.SUCC_TITLE_PROVIDER_REGS);
                                JsonArray res = response.getJsonArray("results");
                                assertTrue(res.size() == 2);

                                res.forEach(
                                    i -> {
                                      JsonObject j = (JsonObject) i;

                                      assertEquals(
                                          j.getString(Constants.RESP_STATUS),
                                          RoleStatus.PENDING.name().toLowerCase());
                                      assertEquals(j.getString("rsUrl"), SERVER_URL);

                                      if (j.getString("id").equals(providerAPendingId.toString())) {
                                        assertEquals(
                                            j.getString("email"),
                                            utils.getDetails(providerA).email);
                                        assertEquals(j.getString("userId"), providerA.getUserId());
                                        assertEquals(
                                            j.getJsonObject("userInfo"),
                                            utils.getDetails(providerA).userInfo);
                                        sawProviderA.flag();
                                      }

                                      if (j.getString("id").equals(providerBPendingId.toString())) {
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
  @DisplayName("Test get approved registration")
  void approvedReg(VertxTestContext testContext) {

    String SERVER_URL = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";

    User adminUser = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();
    adminUser.setRoles(List.of(Roles.ADMIN));
    adminUser.setRolesToRsMapping(Map.of(Roles.ADMIN.toString(), new JsonArray().add(SERVER_URL)));

    User providerA = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();
    User providerB = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();

    UUID providerAPendingId = UUID.randomUUID();
    UUID providerBPendingId = UUID.randomUUID();

    Checkpoint sawProviderA = testContext.checkpoint();
    Checkpoint sawProviderB = testContext.checkpoint();

    Future<Void> setup =
        utils
            .createFakeResourceServer(SERVER_URL, adminUser)
            .compose(res -> utils.createFakeUser(providerA, false, true))
            .compose(res -> utils.createFakeUser(providerB, false, true))
            .compose(
                res ->
                    utils.addProviderStatusRole(
                        providerA, SERVER_URL, RoleStatus.APPROVED, providerAPendingId))
            .compose(
                res ->
                    utils.addProviderStatusRole(
                        providerB, SERVER_URL, RoleStatus.APPROVED, providerBPendingId));

    setup.onSuccess(
        succ -> {
          Map<String, JsonObject> mockKcResp =
              Map.of(
                  providerA.getUserId(),
                  utils.getKcAdminJson(providerA),
                  providerB.getUserId(),
                  utils.getKcAdminJson(providerB));
          Mockito.when(kc.getDetails(any())).thenReturn(Future.succeededFuture(mockKcResp));

          adminService
              .getProviderRegistrations(RoleStatus.APPROVED, adminUser)
              .onComplete(
                  testContext.succeeding(
                      response ->
                          testContext.verify(
                              () -> {
                                assertEquals(response.getInteger("status"), 200);
                                assertEquals(response.getString("type"), URN_SUCCESS.toString());
                                assertEquals(
                                    response.getString("title"),
                                    Constants.SUCC_TITLE_PROVIDER_REGS);
                                JsonArray res = response.getJsonArray("results");
                                assertTrue(res.size() == 2);

                                res.forEach(
                                    i -> {
                                      JsonObject j = (JsonObject) i;
                                      assertEquals(
                                          j.getString(Constants.RESP_STATUS),
                                          RoleStatus.APPROVED.name().toLowerCase());
                                      assertEquals(j.getString("rsUrl"), SERVER_URL);

                                      if (j.getString("id").equals(providerAPendingId.toString())) {
                                        assertEquals(
                                            j.getString("email"),
                                            utils.getDetails(providerA).email);
                                        assertEquals(j.getString("userId"), providerA.getUserId());
                                        assertEquals(
                                            j.getJsonObject("userInfo"),
                                            utils.getDetails(providerA).userInfo);
                                        sawProviderA.flag();
                                      }

                                      if (j.getString("id").equals(providerBPendingId.toString())) {
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
  @DisplayName("Test get rejected registration")
  void rejectedReg(VertxTestContext testContext) {

    String SERVER_URL = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";

    User adminUser = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();
    adminUser.setRoles(List.of(Roles.ADMIN));
    adminUser.setRolesToRsMapping(Map.of(Roles.ADMIN.toString(), new JsonArray().add(SERVER_URL)));

    User providerA = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();
    User providerB = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();

    UUID providerAPendingId = UUID.randomUUID();
    UUID providerBPendingId = UUID.randomUUID();

    Checkpoint sawProviderA = testContext.checkpoint();
    Checkpoint sawProviderB = testContext.checkpoint();

    Future<Void> setup =
        utils
            .createFakeResourceServer(SERVER_URL, adminUser)
            .compose(res -> utils.createFakeUser(providerA, false, true))
            .compose(res -> utils.createFakeUser(providerB, false, true))
            .compose(
                res ->
                    utils.addProviderStatusRole(
                        providerA, SERVER_URL, RoleStatus.REJECTED, providerAPendingId))
            .compose(
                res ->
                    utils.addProviderStatusRole(
                        providerB, SERVER_URL, RoleStatus.REJECTED, providerBPendingId));

    setup.onSuccess(
        succ -> {
          Map<String, JsonObject> mockKcResp =
              Map.of(
                  providerA.getUserId(),
                  utils.getKcAdminJson(providerA),
                  providerB.getUserId(),
                  utils.getKcAdminJson(providerB));
          Mockito.when(kc.getDetails(any())).thenReturn(Future.succeededFuture(mockKcResp));

          adminService
              .getProviderRegistrations(RoleStatus.REJECTED, adminUser)
              .onComplete(
                  testContext.succeeding(
                      response ->
                          testContext.verify(
                              () -> {
                                assertEquals(response.getInteger("status"), 200);
                                assertEquals(response.getString("type"), URN_SUCCESS.toString());
                                assertEquals(
                                    response.getString("title"),
                                    Constants.SUCC_TITLE_PROVIDER_REGS);
                                JsonArray res = response.getJsonArray("results");
                                assertTrue(res.size() == 2);

                                res.forEach(
                                    i -> {
                                      JsonObject j = (JsonObject) i;
                                      assertEquals(
                                          j.getString(Constants.RESP_STATUS),
                                          RoleStatus.REJECTED.name().toLowerCase());
                                      assertEquals(j.getString("rsUrl"), SERVER_URL);

                                      if (j.getString("id").equals(providerAPendingId.toString())) {
                                        assertEquals(
                                            j.getString("email"),
                                            utils.getDetails(providerA).email);
                                        assertEquals(j.getString("userId"), providerA.getUserId());
                                        assertEquals(
                                            j.getJsonObject("userInfo"),
                                            utils.getDetails(providerA).userInfo);
                                        sawProviderA.flag();
                                      }

                                      if (j.getString("id").equals(providerBPendingId.toString())) {
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
  @DisplayName(
      "Test admin of multiple RS viewing only pending registrations across all their owned servers")
  void adminOfMultipleRs(VertxTestContext testContext) {

    String SERVER_URL_ONE = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";
    String SERVER_URL_TWO = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";

    User adminUser = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();
    adminUser.setRoles(List.of(Roles.ADMIN));
    adminUser.setRolesToRsMapping(
        Map.of(Roles.ADMIN.toString(), new JsonArray(List.of(SERVER_URL_ONE, SERVER_URL_TWO))));

    User providerA = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();
    User providerB = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();
    User providerC = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();
    User providerD = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();

    UUID providerAPendingIdOnServerOne = UUID.randomUUID();
    UUID providerBPendingIdOnServerTwo = UUID.randomUUID();

    UUID providerCApprovedIdOnServerTwo = UUID.randomUUID();
    UUID providerDRejectedIdOnServerOne = UUID.randomUUID();

    Checkpoint sawProviderA = testContext.checkpoint();
    Checkpoint sawProviderB = testContext.checkpoint();

    Future<Void> setup =
        utils
            .createFakeResourceServer(SERVER_URL_ONE, adminUser)
            .compose(res -> utils.createFakeResourceServer(SERVER_URL_TWO, adminUser))
            .compose(res -> utils.createFakeUser(providerA, false, true))
            .compose(res -> utils.createFakeUser(providerB, false, true))
            .compose(res -> utils.createFakeUser(providerC, false, true))
            .compose(res -> utils.createFakeUser(providerD, false, true))
            .compose(
                res ->
                    utils.addProviderStatusRole(
                        providerA,
                        SERVER_URL_ONE,
                        RoleStatus.PENDING,
                        providerAPendingIdOnServerOne))
            .compose(
                res ->
                    utils.addProviderStatusRole(
                        providerB,
                        SERVER_URL_TWO,
                        RoleStatus.PENDING,
                        providerBPendingIdOnServerTwo))
            .compose(
                res ->
                    utils.addProviderStatusRole(
                        providerC,
                        SERVER_URL_TWO,
                        RoleStatus.APPROVED,
                        providerCApprovedIdOnServerTwo))
            .compose(
                res ->
                    utils.addProviderStatusRole(
                        providerD,
                        SERVER_URL_ONE,
                        RoleStatus.REJECTED,
                        providerDRejectedIdOnServerOne));

    setup.onSuccess(
        succ -> {
          Map<String, JsonObject> mockKcResp =
              Map.of(
                  providerA.getUserId(),
                  utils.getKcAdminJson(providerA),
                  providerB.getUserId(),
                  utils.getKcAdminJson(providerB));
          Mockito.when(kc.getDetails(any())).thenReturn(Future.succeededFuture(mockKcResp));

          adminService
              .getProviderRegistrations(RoleStatus.PENDING, adminUser)
              .onComplete(
                  testContext.succeeding(
                      response ->
                          testContext.verify(
                              () -> {
                                assertEquals(response.getInteger("status"), 200);
                                assertEquals(response.getString("type"), URN_SUCCESS.toString());
                                assertEquals(
                                    response.getString("title"),
                                    Constants.SUCC_TITLE_PROVIDER_REGS);
                                JsonArray res = response.getJsonArray("results");
                                assertTrue(res.size() == 2);

                                res.forEach(
                                    i -> {
                                      JsonObject j = (JsonObject) i;
                                      assertEquals(
                                          j.getString(Constants.RESP_STATUS),
                                          RoleStatus.PENDING.name().toLowerCase());

                                      if (j.getString("id")
                                          .equals(providerAPendingIdOnServerOne.toString())) {
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
                                          .equals(providerBPendingIdOnServerTwo.toString())) {
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
  @DisplayName("Test admin getting registrations only associated with them")
  void adminGettingOnlyRegAssociatedWithThem(VertxTestContext testContext) {
    String SERVER_URL_ONE = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";
    String SERVER_URL_TWO = RandomStringUtils.randomAlphabetic(10).toLowerCase() + ".com";

    User adminOfOne = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();
    adminOfOne.setRoles(List.of(Roles.ADMIN));
    adminOfOne.setRolesToRsMapping(
        Map.of(Roles.ADMIN.toString(), new JsonArray(List.of(SERVER_URL_ONE))));

    User adminOfTwo = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();
    adminOfTwo.setRoles(List.of(Roles.ADMIN));
    adminOfTwo.setRolesToRsMapping(
        Map.of(Roles.ADMIN.toString(), new JsonArray(List.of(SERVER_URL_TWO))));

    User providerA = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();
    User providerB = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();
    User providerC = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();
    User providerD = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();

    UUID providerAPendingIdOnServerOne = UUID.randomUUID();
    UUID providerBPendingIdOnServerOne = UUID.randomUUID();

    UUID providerCPendingIdOnServerTwo = UUID.randomUUID();
    UUID providerDPendingIdOnServerTwo = UUID.randomUUID();

    Checkpoint sawProviderA = testContext.checkpoint();
    Checkpoint sawProviderB = testContext.checkpoint();

    Future<Void> setup =
        utils
            .createFakeResourceServer(SERVER_URL_ONE, adminOfOne)
            .compose(res -> utils.createFakeResourceServer(SERVER_URL_TWO, adminOfTwo))
            .compose(res -> utils.createFakeUser(providerA, false, true))
            .compose(res -> utils.createFakeUser(providerB, false, true))
            .compose(res -> utils.createFakeUser(providerC, false, true))
            .compose(res -> utils.createFakeUser(providerD, false, true))
            .compose(
                res ->
                    utils.addProviderStatusRole(
                        providerA,
                        SERVER_URL_ONE,
                        RoleStatus.PENDING,
                        providerAPendingIdOnServerOne))
            .compose(
                res ->
                    utils.addProviderStatusRole(
                        providerB,
                        SERVER_URL_ONE,
                        RoleStatus.PENDING,
                        providerBPendingIdOnServerOne))
            .compose(
                res ->
                    utils.addProviderStatusRole(
                        providerC,
                        SERVER_URL_TWO,
                        RoleStatus.APPROVED,
                        providerCPendingIdOnServerTwo))
            .compose(
                res ->
                    utils.addProviderStatusRole(
                        providerD,
                        SERVER_URL_TWO,
                        RoleStatus.REJECTED,
                        providerDPendingIdOnServerTwo));

    setup.onSuccess(
        succ -> {
          Map<String, JsonObject> mockKcResp =
              Map.of(
                  providerA.getUserId(),
                  utils.getKcAdminJson(providerA),
                  providerB.getUserId(),
                  utils.getKcAdminJson(providerB));
          Mockito.when(kc.getDetails(any())).thenReturn(Future.succeededFuture(mockKcResp));

          adminService
              .getProviderRegistrations(RoleStatus.PENDING, adminOfOne)
              .onComplete(
                  testContext.succeeding(
                      response ->
                          testContext.verify(
                              () -> {
                                assertEquals(response.getInteger("status"), 200);
                                assertEquals(response.getString("type"), URN_SUCCESS.toString());
                                assertEquals(
                                    response.getString("title"),
                                    Constants.SUCC_TITLE_PROVIDER_REGS);
                                JsonArray res = response.getJsonArray("results");
                                assertTrue(res.size() == 2);

                                res.forEach(
                                    i -> {
                                      JsonObject j = (JsonObject) i;
                                      assertEquals(
                                          j.getString(Constants.RESP_STATUS),
                                          RoleStatus.PENDING.name().toLowerCase());

                                      if (j.getString("id")
                                          .equals(providerAPendingIdOnServerOne.toString())) {
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
                                          .equals(providerBPendingIdOnServerOne.toString())) {
                                        assertEquals(j.getString("rsUrl"), SERVER_URL_ONE);
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
}
