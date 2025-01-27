package iudx.aaa.server.apd;

import static iudx.aaa.server.apd.Constants.ERR_TITLE_INVALID_REQUEST_ID;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_NO_APPROVED_ROLES;
import static iudx.aaa.server.apd.Constants.RESP_APD_ID;
import static iudx.aaa.server.apd.Constants.RESP_APD_NAME;
import static iudx.aaa.server.apd.Constants.RESP_APD_OWNER;
import static iudx.aaa.server.apd.Constants.RESP_APD_STATUS;
import static iudx.aaa.server.apd.Constants.RESP_APD_URL;
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
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;
import iudx.aaa.server.apiserver.util.ComposeException;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.registration.RegistrationService;
import iudx.aaa.server.registration.Utils;
import iudx.aaa.server.token.TokenService;
import java.util.ArrayList;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

/** Unit tests for APD listing. */
@ExtendWith({VertxExtension.class})
@TestMethodOrder(OrderAnnotation.class)
public class ListApdTest {

  private static Logger LOGGER = LogManager.getLogger(ListApdTest.class);
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

  @DisplayName("Test incorrect apdID")
  void noApd(VertxTestContext testContext) {

    String randUuid = UUID.randomUUID().toString();
    apdService
        .getApdDetails(List.of(), List.of(randUuid))
        .onComplete(
            testContext.succeeding(
                response -> {
                  testContext.verify(
                      () -> {
                        assertEquals(response.getInteger("status"), 400);
                        assertEquals(response.getString("title"), "Invalid request");
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  @DisplayName("Test incorrect apdUrl")
  void noUrl(VertxTestContext testContext) {

    String randString = RandomStringUtils.randomAlphabetic(7).toLowerCase();
    apdService
        .getApdDetails(List.of(randString), List.of())
        .onComplete(
            testContext.failing(
                response -> {
                  testContext.verify(
                      () -> {
                        if (response instanceof ComposeException) {
                          ComposeException e = (ComposeException) response;
                          JsonObject result = e.getResponse().toJson();
                          assertEquals(result.getInteger("status"), 400);
                          assertEquals(result.getString("title"), ERR_TITLE_INVALID_REQUEST_ID);
                        }
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  @DisplayName("Test incorrect input both apdUrl and apdID present")
  void invalidInput(VertxTestContext testContext) {

    String randUuid = UUID.randomUUID().toString();
    String randString = RandomStringUtils.randomAlphabetic(7).toLowerCase();
    apdService
        .getApdDetails(List.of(randString), List.of(randUuid))
        .onComplete(
            testContext.failing(
                response -> {
                  testContext.verify(
                      () -> {
                        assertEquals(response.getMessage(), "internal server error");
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  @DisplayName("Test incorrect input both apdUrl and apdID empty")
  void emptyInput(VertxTestContext testContext) {

    apdService
        .getApdDetails(List.of(), List.of())
        .onComplete(
            testContext.failing(
                response -> {
                  testContext.verify(
                      () -> {
                        assertEquals(response.getMessage(), "internal server error");
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  @DisplayName("Test multipleSuccess - apdID")
  void multipleSuccessApdId(VertxTestContext testContext) {

    List<String> request = new ArrayList<String>();

    String activeApdAId = utils.apdMap.get(ACTIVE_A).toString();
    String inActiveApdBId = utils.apdMap.get(INACTIVE_B).toString();

    request.add(activeApdAId);
    request.add(inActiveApdBId);

    Mockito.doAnswer(
            i -> {
              JsonObject response = new JsonObject();

              response
                  .put(trusteeAUser.getUserId(), utils.getKcAdminJson(trusteeAUser))
                  .put(trusteeBUser.getUserId(), utils.getKcAdminJson(trusteeBUser));

              return Future.succeededFuture(response);
            })
        .when(registrationService)
        .getUserDetails(Mockito.any());

    apdService
        .getApdDetails(List.of(), request)
        .onComplete(
            testContext.succeeding(
                response -> {
                  testContext.verify(
                      () -> {
                        JsonObject respOne = response.getJsonObject(activeApdAId);
                        JsonObject respTwo = response.getJsonObject(inActiveApdBId);

                        assertEquals(
                            respOne.getString(RESP_APD_STATUS),
                            ApdStatus.ACTIVE.toString().toLowerCase());
                        assertEquals(
                            respTwo.getString(RESP_APD_STATUS),
                            ApdStatus.INACTIVE.toString().toLowerCase());

                        assertEquals(
                            respOne.getJsonObject(RESP_APD_OWNER).getString("email"),
                            utils.getDetails(trusteeAUser).email);
                        assertEquals(
                            respTwo.getJsonObject(RESP_APD_OWNER).getString("email"),
                            utils.getDetails(trusteeBUser).email);
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  @DisplayName("Test multipleSuccess - apdUrl")
  void multipleSuccessApdUrl(VertxTestContext testContext) {

    List<String> request = new ArrayList<String>();

    request.add(ACTIVE_A);
    request.add(INACTIVE_B);

    Mockito.doAnswer(
            i -> {
              JsonObject response = new JsonObject();

              response
                  .put(trusteeAUser.getUserId(), utils.getKcAdminJson(trusteeAUser))
                  .put(trusteeBUser.getUserId(), utils.getKcAdminJson(trusteeBUser));

              return Future.succeededFuture(response);
            })
        .when(registrationService)
        .getUserDetails(Mockito.any());

    apdService
        .getApdDetails(request, List.of())
        .onComplete(
            testContext.succeeding(
                response -> {
                  testContext.verify(
                      () -> {
                        JsonObject respOne = response.getJsonObject(ACTIVE_A);
                        JsonObject respTwo = response.getJsonObject(INACTIVE_B);

                        assertEquals(
                            respOne.getString(RESP_APD_STATUS),
                            ApdStatus.ACTIVE.toString().toLowerCase());
                        assertEquals(
                            respTwo.getString(RESP_APD_STATUS),
                            ApdStatus.INACTIVE.toString().toLowerCase());

                        assertEquals(
                            respOne.getJsonObject(RESP_APD_OWNER).getString("email"),
                            utils.getDetails(trusteeAUser).email);
                        assertEquals(
                            respTwo.getJsonObject(RESP_APD_OWNER).getString("email"),
                            utils.getDetails(trusteeBUser).email);
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  @DisplayName("List APD - Test no roles")
  void ListInvalidUser(VertxTestContext testContext) {

    UUID uid1 = UUID.randomUUID();
    User user = new UserBuilder().userId(uid1).build();

    apdService
        .listApd(user)
        .onComplete(
            testContext.succeeding(
                response -> {
                  testContext.verify(
                      () -> {
                        assertEquals(response.getString("title"), ERR_TITLE_NO_APPROVED_ROLES);
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  @DisplayName("List APD - COS Admin")
  void ListCosAdmin(VertxTestContext testContext) {

    String activeApdAId = utils.apdMap.get(ACTIVE_A).toString();
    String activeApdBId = utils.apdMap.get(ACTIVE_B).toString();
    String inActiveApdAId = utils.apdMap.get(INACTIVE_A).toString();
    String inActiveApdBId = utils.apdMap.get(INACTIVE_B).toString();

    Mockito.doAnswer(
            i -> {
              List<String> userIds = i.getArgument(0);
              JsonObject response = new JsonObject();

              userIds.forEach(
                  id -> {
                    response.put(id, new JsonObject());
                  });

              response
                  .put(trusteeAUser.getUserId(), utils.getKcAdminJson(trusteeAUser))
                  .put(trusteeBUser.getUserId(), utils.getKcAdminJson(trusteeBUser));

              return Future.succeededFuture(response);
            })
        .when(registrationService)
        .getUserDetails(Mockito.any());

    Checkpoint checkActiveA = testContext.checkpoint();
    Checkpoint checkActiveB = testContext.checkpoint();
    Checkpoint checkInActiveA = testContext.checkpoint();
    Checkpoint checkInActiveB = testContext.checkpoint();

    apdService
        .listApd(cosAdmin)
        .onComplete(
            testContext.succeeding(
                response -> {
                  testContext.verify(
                      () -> {
                        JsonArray responseArr = response.getJsonArray("results");

                        for (int i = 0; i < responseArr.size(); i++) {
                          JsonObject obj = responseArr.getJsonObject(i);

                          if (obj.getString(RESP_APD_URL).equals(ACTIVE_A.toLowerCase())) {
                            assertEquals(
                                obj.getString(RESP_APD_STATUS),
                                ApdStatus.ACTIVE.toString().toLowerCase());
                            assertEquals(obj.getString(RESP_APD_ID), activeApdAId);
                            assertEquals(obj.getString(RESP_APD_NAME), ACTIVE_A + "name");
                            JsonObject owner = obj.getJsonObject(RESP_APD_OWNER);

                            assertEquals(owner.getString("id"), trusteeAUser.getUserId());
                            assertEquals(
                                owner.getString("email"), utils.getDetails(trusteeAUser).email);
                            checkActiveA.flag();
                          }

                          if (obj.getString(RESP_APD_URL).equals(ACTIVE_B.toLowerCase())) {
                            assertEquals(
                                obj.getString(RESP_APD_STATUS),
                                ApdStatus.ACTIVE.toString().toLowerCase());
                            assertEquals(obj.getString(RESP_APD_ID), activeApdBId);
                            assertEquals(obj.getString(RESP_APD_NAME), ACTIVE_B + "name");
                            JsonObject owner = obj.getJsonObject(RESP_APD_OWNER);

                            assertEquals(owner.getString("id"), trusteeBUser.getUserId());
                            assertEquals(
                                owner.getString("email"), utils.getDetails(trusteeBUser).email);
                            checkActiveB.flag();
                          }

                          if (obj.getString(RESP_APD_URL).equals(INACTIVE_A.toLowerCase())) {
                            assertEquals(
                                obj.getString(RESP_APD_STATUS),
                                ApdStatus.INACTIVE.toString().toLowerCase());
                            assertEquals(obj.getString(RESP_APD_ID), inActiveApdAId);
                            assertEquals(obj.getString(RESP_APD_NAME), INACTIVE_A + "name");
                            JsonObject owner = obj.getJsonObject(RESP_APD_OWNER);

                            assertEquals(owner.getString("id"), trusteeAUser.getUserId());
                            assertEquals(
                                owner.getString("email"), utils.getDetails(trusteeAUser).email);
                            checkInActiveA.flag();
                          }

                          if (obj.getString(RESP_APD_URL).equals(INACTIVE_B.toLowerCase())) {
                            assertEquals(
                                obj.getString(RESP_APD_STATUS),
                                ApdStatus.INACTIVE.toString().toLowerCase());
                            assertEquals(obj.getString(RESP_APD_ID), inActiveApdBId);
                            assertEquals(obj.getString(RESP_APD_NAME), INACTIVE_B + "name");
                            JsonObject owner = obj.getJsonObject(RESP_APD_OWNER);

                            assertEquals(owner.getString("id"), trusteeBUser.getUserId());
                            assertEquals(
                                owner.getString("email"), utils.getDetails(trusteeBUser).email);
                            checkInActiveB.flag();
                          }
                        }
                      });
                }));
  }

  @Test
  @DisplayName("List APD - provider/consumer/admin")
  void ListApdAsProviderConsumerAdmin(VertxTestContext testContext) {

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

    String activeApdAId = utils.apdMap.get(ACTIVE_A).toString();
    String activeApdBId = utils.apdMap.get(ACTIVE_B).toString();

    Mockito.doAnswer(
            i -> {
              List<String> userIds = i.getArgument(0);
              JsonObject response = new JsonObject();

              userIds.forEach(
                  id -> {
                    response.put(id, new JsonObject());
                  });

              response
                  .put(trusteeAUser.getUserId(), utils.getKcAdminJson(trusteeAUser))
                  .put(trusteeBUser.getUserId(), utils.getKcAdminJson(trusteeBUser));

              return Future.succeededFuture(response);
            })
        .when(registrationService)
        .getUserDetails(Mockito.any());

    Checkpoint checkActiveA = testContext.checkpoint();
    Checkpoint checkActiveB = testContext.checkpoint();

    apdService
        .listApd(provConsAdminUser)
        .onComplete(
            testContext.succeeding(
                response -> {
                  testContext.verify(
                      () -> {
                        JsonArray responseArr = response.getJsonArray("results");

                        for (int i = 0; i < responseArr.size(); i++) {
                          JsonObject obj = responseArr.getJsonObject(i);

                          assertTrue(
                              !obj.getString(RESP_APD_STATUS)
                                  .equals(ApdStatus.INACTIVE.toString().toLowerCase()));

                          assertTrue(
                              !(obj.getString(RESP_APD_URL).equals(INACTIVE_A)
                                  && obj.getString(RESP_APD_URL).equals(INACTIVE_B)));

                          if (obj.getString(RESP_APD_URL).equals(ACTIVE_A.toLowerCase())) {
                            assertEquals(
                                obj.getString(RESP_APD_STATUS),
                                ApdStatus.ACTIVE.toString().toLowerCase());
                            assertEquals(obj.getString(RESP_APD_ID), activeApdAId);
                            assertEquals(obj.getString(RESP_APD_NAME), ACTIVE_A + "name");
                            JsonObject owner = obj.getJsonObject(RESP_APD_OWNER);

                            assertEquals(owner.getString("id"), trusteeAUser.getUserId());
                            assertEquals(
                                owner.getString("email"), utils.getDetails(trusteeAUser).email);
                            checkActiveA.flag();
                          }

                          if (obj.getString(RESP_APD_URL).equals(ACTIVE_B.toLowerCase())) {
                            assertEquals(
                                obj.getString(RESP_APD_STATUS),
                                ApdStatus.ACTIVE.toString().toLowerCase());
                            assertEquals(obj.getString(RESP_APD_ID), activeApdBId);
                            assertEquals(obj.getString(RESP_APD_NAME), ACTIVE_B + "name");
                            JsonObject owner = obj.getJsonObject(RESP_APD_OWNER);

                            assertEquals(owner.getString("id"), trusteeBUser.getUserId());
                            assertEquals(
                                owner.getString("email"), utils.getDetails(trusteeBUser).email);
                            checkActiveB.flag();
                          }
                        }
                      });
                }));
  }

  @Test
  @DisplayName("List APD - trustee - sees only active")
  void ListApdAsTrustee(VertxTestContext testContext) {

    String activeApdAId = utils.apdMap.get(ACTIVE_A).toString();
    String activeApdBId = utils.apdMap.get(ACTIVE_B).toString();

    Mockito.doAnswer(
            i -> {
              List<String> userIds = i.getArgument(0);
              JsonObject response = new JsonObject();

              userIds.forEach(
                  id -> {
                    response.put(id, new JsonObject());
                  });

              response
                  .put(trusteeAUser.getUserId(), utils.getKcAdminJson(trusteeAUser))
                  .put(trusteeBUser.getUserId(), utils.getKcAdminJson(trusteeBUser));

              return Future.succeededFuture(response);
            })
        .when(registrationService)
        .getUserDetails(Mockito.any());

    Checkpoint checkActiveA = testContext.checkpoint();
    Checkpoint checkActiveB = testContext.checkpoint();

    apdService
        .listApd(trusteeAUser)
        .onComplete(
            testContext.succeeding(
                response -> {
                  testContext.verify(
                      () -> {
                        JsonArray responseArr = response.getJsonArray("results");

                        for (int i = 0; i < responseArr.size(); i++) {
                          JsonObject obj = responseArr.getJsonObject(i);

                          assertTrue(
                              !obj.getString(RESP_APD_STATUS)
                                  .equals(ApdStatus.INACTIVE.toString().toLowerCase()));
                          assertTrue(
                              !(obj.getString(RESP_APD_URL).equals(INACTIVE_A)
                                  && obj.getString(RESP_APD_URL).equals(INACTIVE_B)));

                          if (obj.getString(RESP_APD_URL).equals(ACTIVE_A.toLowerCase())) {
                            assertEquals(
                                obj.getString(RESP_APD_STATUS),
                                ApdStatus.ACTIVE.toString().toLowerCase());
                            assertEquals(obj.getString(RESP_APD_ID), activeApdAId);
                            assertEquals(obj.getString(RESP_APD_NAME), ACTIVE_A + "name");
                            JsonObject owner = obj.getJsonObject(RESP_APD_OWNER);

                            assertEquals(owner.getString("id"), trusteeAUser.getUserId());
                            assertEquals(
                                owner.getString("email"), utils.getDetails(trusteeAUser).email);
                            checkActiveA.flag();
                          }

                          if (obj.getString(RESP_APD_URL).equals(ACTIVE_B.toLowerCase())) {
                            assertEquals(
                                obj.getString(RESP_APD_STATUS),
                                ApdStatus.ACTIVE.toString().toLowerCase());
                            assertEquals(obj.getString(RESP_APD_ID), activeApdBId);
                            assertEquals(obj.getString(RESP_APD_NAME), ACTIVE_B + "name");
                            JsonObject owner = obj.getJsonObject(RESP_APD_OWNER);

                            assertEquals(owner.getString("id"), trusteeBUser.getUserId());
                            assertEquals(
                                owner.getString("email"), utils.getDetails(trusteeBUser).email);
                            checkActiveB.flag();
                          }
                        }
                      });
                }));
  }
}
