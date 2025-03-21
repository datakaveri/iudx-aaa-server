package iudx.aaa.server.policy;

import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_INPUT;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_ROLE;
import static iudx.aaa.server.apiserver.util.Urn.URN_SUCCESS;
import static iudx.aaa.server.policy.Constants.ERR_DETAIL_DEL_DELEGATE_ROLES;
import static iudx.aaa.server.policy.Constants.ERR_TITLE_INVALID_ID;
import static iudx.aaa.server.policy.Constants.ERR_TITLE_INVALID_ROLES;
import static iudx.aaa.server.policy.Constants.SUCC_TITLE_DELETE_DELE;
import static iudx.aaa.server.policy.Constants.TYPE;
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
import iudx.aaa.server.apd.ApdService;
import iudx.aaa.server.apiserver.DelegationStatus;
import iudx.aaa.server.apiserver.DeleteDelegationRequest;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.registration.RegistrationService;
import iudx.aaa.server.registration.Utils;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for delegation deletion. */
@ExtendWith({VertxExtension.class, MockitoExtension.class})
public class DeleteDelegationTest {
  private static Logger LOGGER =
      LogManager.getLogger(iudx.aaa.server.policy.DeleteDelegationTest.class);

  private static Configuration config;

  // Database Properties

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
  private static PolicyService policyService;
  private static ApdService apdService = Mockito.mock(ApdService.class);
  private static RegistrationService registrationService = Mockito.mock(RegistrationService.class);

  private static Vertx vertxObj;
  private static CatalogueClient catalogueClient = Mockito.mock(CatalogueClient.class);

  private static Utils utils;

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

    utils = new Utils(pool);

    policyService = new PolicyServiceImpl(pool, registrationService, apdService, catalogueClient);
    testContext.completeNow();
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing....");

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
  @DisplayName("Failure - user calling API does not have provider role/consumer role")
  void userNotProviderConsumerRole(VertxTestContext testContext) {

    final String DUMMY_SERVER =
        "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

    User dummyUser =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .roles(List.of(Roles.ADMIN, Roles.COS_ADMIN, Roles.TRUSTEE, Roles.DELEGATE))
            .rolesToRsMapping(
                Map.of(
                    Roles.DELEGATE.toString(),
                    new JsonArray().add(DUMMY_SERVER),
                    Roles.TRUSTEE.toString(),
                    new JsonArray().add("some-apd.url"),
                    Roles.ADMIN.toString(),
                    new JsonArray().add(DUMMY_SERVER)))
            .build();

    List<DeleteDelegationRequest> req = new ArrayList<DeleteDelegationRequest>();

    policyService
        .deleteDelegation(req, dummyUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_INVALID_ROLE.toString(), response.getString("type"));
                          assertEquals(ERR_TITLE_INVALID_ROLES, response.getString("title"));
                          assertEquals(ERR_DETAIL_DEL_DELEGATE_ROLES, response.getString("detail"));
                          assertEquals(401, response.getInteger("status"));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Invalid delegation ID")
  void invalidDelegationId(VertxTestContext testContext) {

    final String DUMMY_SERVER =
        "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

    User consumerUser =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .name("aa", "bb")
            .roles(List.of(Roles.CONSUMER))
            .rolesToRsMapping(Map.of(Roles.CONSUMER.toString(), new JsonArray().add(DUMMY_SERVER)))
            .build();

    UUID validDelegationId = UUID.randomUUID();

    Future<Void> create =
        utils
            .createFakeResourceServer(
                DUMMY_SERVER, new UserBuilder().userId(UUID.randomUUID()).build())
            .compose(res -> utils.createFakeUser(consumerUser, false, false))
            .compose(
                res ->
                    utils.createFakeDelegation(
                        validDelegationId,
                        consumerUser,
                        new UserBuilder().userId(UUID.randomUUID()).build(),
                        DUMMY_SERVER,
                        Roles.CONSUMER,
                        DelegationStatus.ACTIVE));

    create
        .onSuccess(
            res -> {
              UUID inValidDelegationId = UUID.randomUUID();

              JsonArray req =
                  new JsonArray()
                      .add(new JsonObject().put("id", validDelegationId.toString()))
                      .add(new JsonObject().put("id", inValidDelegationId.toString()));
              List<DeleteDelegationRequest> request = DeleteDelegationRequest.jsonArrayToList(req);

              policyService
                  .deleteDelegation(request, consumerUser)
                  .onComplete(
                      testContext.succeeding(
                          response ->
                              testContext.verify(
                                  () -> {
                                    assertEquals(
                                        URN_INVALID_INPUT.toString(), response.getString(TYPE));
                                    assertEquals(ERR_TITLE_INVALID_ID, response.getString("title"));
                                    assertEquals(
                                        inValidDelegationId.toString(),
                                        response.getString("detail"));
                                    assertEquals(400, response.getInteger("status"));
                                    testContext.completeNow();
                                  })));
            })
        .onFailure(fail -> testContext.failNow(fail.getMessage()));
  }

  @Test
  @DisplayName("Deleted delegation ID")
  void deletedDelegationId(VertxTestContext testContext) {
    final String DUMMY_SERVER =
        "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

    User consumerUser =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .name("aa", "bb")
            .roles(List.of(Roles.CONSUMER))
            .rolesToRsMapping(Map.of(Roles.CONSUMER.toString(), new JsonArray().add(DUMMY_SERVER)))
            .build();

    UUID activeDelegationId = UUID.randomUUID();
    UUID deletedDelegationId = UUID.randomUUID();

    Future<Void> create =
        utils
            .createFakeResourceServer(
                DUMMY_SERVER, new UserBuilder().userId(UUID.randomUUID()).build())
            .compose(res -> utils.createFakeUser(consumerUser, false, false))
            .compose(
                res ->
                    utils.createFakeDelegation(
                        activeDelegationId,
                        consumerUser,
                        new UserBuilder().userId(UUID.randomUUID()).build(),
                        DUMMY_SERVER,
                        Roles.CONSUMER,
                        DelegationStatus.ACTIVE))
            .compose(
                res ->
                    utils.createFakeDelegation(
                        deletedDelegationId,
                        consumerUser,
                        new UserBuilder().userId(UUID.randomUUID()).build(),
                        DUMMY_SERVER,
                        Roles.CONSUMER,
                        DelegationStatus.DELETED));

    create
        .onSuccess(
            res -> {
              JsonArray req =
                  new JsonArray()
                      .add(new JsonObject().put("id", activeDelegationId.toString()))
                      .add(new JsonObject().put("id", deletedDelegationId.toString()));
              List<DeleteDelegationRequest> request = DeleteDelegationRequest.jsonArrayToList(req);

              policyService
                  .deleteDelegation(request, consumerUser)
                  .onComplete(
                      testContext.succeeding(
                          response ->
                              testContext.verify(
                                  () -> {
                                    assertEquals(
                                        URN_INVALID_INPUT.toString(), response.getString(TYPE));
                                    assertEquals(ERR_TITLE_INVALID_ID, response.getString("title"));
                                    assertEquals(
                                        deletedDelegationId.toString(),
                                        response.getString("detail"));
                                    assertEquals(400, response.getInteger("status"));
                                    testContext.completeNow();
                                  })));
            })
        .onFailure(fail -> testContext.failNow(fail.getMessage()));
  }

  @Test
  @DisplayName("Delete delegation as a consumer")
  void deleteDelegationAsConsumer(VertxTestContext testContext) {
    final String DUMMY_SERVER =
        "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

    User consumerUser =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .name("aa", "bb")
            .roles(List.of(Roles.CONSUMER))
            .rolesToRsMapping(Map.of(Roles.CONSUMER.toString(), new JsonArray().add(DUMMY_SERVER)))
            .build();

    UUID consumersDelegationId = UUID.randomUUID();

    Future<Void> create =
        utils
            .createFakeResourceServer(
                DUMMY_SERVER, new UserBuilder().userId(UUID.randomUUID()).build())
            .compose(res -> utils.createFakeUser(consumerUser, false, false))
            .compose(
                res ->
                    utils.createFakeDelegation(
                        consumersDelegationId,
                        consumerUser,
                        new UserBuilder().userId(UUID.randomUUID()).build(),
                        DUMMY_SERVER,
                        Roles.CONSUMER,
                        DelegationStatus.ACTIVE));

    create
        .onSuccess(
            res -> {
              JsonArray req =
                  new JsonArray().add(new JsonObject().put("id", consumersDelegationId.toString()));
              List<DeleteDelegationRequest> request = DeleteDelegationRequest.jsonArrayToList(req);

              policyService
                  .deleteDelegation(request, consumerUser)
                  .onComplete(
                      testContext.succeeding(
                          response ->
                              testContext.verify(
                                  () -> {
                                    assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
                                    assertEquals(
                                        SUCC_TITLE_DELETE_DELE, response.getString("title"));
                                    assertEquals(200, response.getInteger("status"));
                                    testContext.completeNow();
                                  })));
            })
        .onFailure(fail -> testContext.failNow(fail.getMessage()));
  }

  @Test
  @DisplayName("Delete delegation as a provider")
  void deleteDelegationAsProvider(VertxTestContext testContext) {
    final String DUMMY_SERVER =
        "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

    User providerUser =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .name("aa", "bb")
            .roles(List.of(Roles.PROVIDER))
            .rolesToRsMapping(Map.of(Roles.PROVIDER.toString(), new JsonArray().add(DUMMY_SERVER)))
            .build();

    UUID providersDelegationId = UUID.randomUUID();

    Future<Void> create =
        utils
            .createFakeResourceServer(
                DUMMY_SERVER, new UserBuilder().userId(UUID.randomUUID()).build())
            .compose(res -> utils.createFakeUser(providerUser, false, false))
            .compose(
                res ->
                    utils.createFakeDelegation(
                        providersDelegationId,
                        providerUser,
                        new UserBuilder().userId(UUID.randomUUID()).build(),
                        DUMMY_SERVER,
                        Roles.PROVIDER,
                        DelegationStatus.ACTIVE));

    create
        .onSuccess(
            res -> {
              JsonArray req =
                  new JsonArray().add(new JsonObject().put("id", providersDelegationId.toString()));
              List<DeleteDelegationRequest> request = DeleteDelegationRequest.jsonArrayToList(req);

              policyService
                  .deleteDelegation(request, providerUser)
                  .onComplete(
                      testContext.succeeding(
                          response ->
                              testContext.verify(
                                  () -> {
                                    assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
                                    assertEquals(
                                        SUCC_TITLE_DELETE_DELE, response.getString("title"));
                                    assertEquals(200, response.getInteger("status"));
                                    testContext.completeNow();
                                  })));
            })
        .onFailure(fail -> testContext.failNow(fail.getMessage()));
  }

  @Test
  @DisplayName("Delete delegation and fail deleting again")
  void deleteAndDeleteAgain(VertxTestContext testContext) {
    final String DUMMY_SERVER =
        "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

    User providerUser =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .name("aa", "bb")
            .roles(List.of(Roles.PROVIDER))
            .rolesToRsMapping(Map.of(Roles.PROVIDER.toString(), new JsonArray().add(DUMMY_SERVER)))
            .build();

    UUID providersDelegationId = UUID.randomUUID();

    Checkpoint deleted = testContext.checkpoint();
    Checkpoint cannotDelete = testContext.checkpoint();

    Future<Void> create =
        utils
            .createFakeResourceServer(
                DUMMY_SERVER, new UserBuilder().userId(UUID.randomUUID()).build())
            .compose(res -> utils.createFakeUser(providerUser, false, false))
            .compose(
                res ->
                    utils.createFakeDelegation(
                        providersDelegationId,
                        providerUser,
                        new UserBuilder().userId(UUID.randomUUID()).build(),
                        DUMMY_SERVER,
                        Roles.PROVIDER,
                        DelegationStatus.ACTIVE));

    create
        .onSuccess(
            res -> {
              JsonArray req =
                  new JsonArray().add(new JsonObject().put("id", providersDelegationId.toString()));
              List<DeleteDelegationRequest> request = DeleteDelegationRequest.jsonArrayToList(req);

              policyService
                  .deleteDelegation(request, providerUser)
                  .onComplete(
                      testContext.succeeding(
                          response ->
                              testContext.verify(
                                  () -> {
                                    assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
                                    assertEquals(
                                        SUCC_TITLE_DELETE_DELE, response.getString("title"));
                                    assertEquals(200, response.getInteger("status"));
                                    deleted.flag();

                                    policyService
                                        .deleteDelegation(request, providerUser)
                                        .onComplete(
                                            testContext.succeeding(
                                                resp ->
                                                    testContext.verify(
                                                        () -> {
                                                          assertEquals(
                                                              URN_INVALID_INPUT.toString(),
                                                              resp.getString(TYPE));
                                                          assertEquals(
                                                              ERR_TITLE_INVALID_ID,
                                                              resp.getString("title"));
                                                          assertEquals(
                                                              providersDelegationId.toString(),
                                                              resp.getString("detail"));
                                                          assertEquals(
                                                              400, resp.getInteger("status"));
                                                          cannotDelete.flag();
                                                        })));
                                  })));
            })
        .onFailure(fail -> testContext.failNow(fail.getMessage()));
  }

  @Test
  @DisplayName("Multiple delegation IDs deletion")
  void multipleDeletes(VertxTestContext testContext) {
    final String DUMMY_SERVER_ONE =
        "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

    final String DUMMY_SERVER_TWO =
        "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

    User consProvUser =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .name("aa", "bb")
            .roles(List.of(Roles.CONSUMER, Roles.PROVIDER))
            .rolesToRsMapping(
                Map.of(
                    Roles.PROVIDER.toString(),
                    new JsonArray().add(DUMMY_SERVER_ONE),
                    Roles.CONSUMER.toString(),
                    new JsonArray().add(DUMMY_SERVER_TWO)))
            .build();

    UUID providerDelegationId = UUID.randomUUID();
    UUID consumerDelegationId = UUID.randomUUID();

    Future<Void> create =
        utils
            .createFakeResourceServer(
                DUMMY_SERVER_ONE, new UserBuilder().userId(UUID.randomUUID()).build())
            .compose(
                res ->
                    utils.createFakeResourceServer(
                        DUMMY_SERVER_TWO, new UserBuilder().userId(UUID.randomUUID()).build()))
            .compose(res -> utils.createFakeUser(consProvUser, false, false))
            .compose(
                res ->
                    utils.createFakeDelegation(
                        providerDelegationId,
                        consProvUser,
                        new UserBuilder().userId(UUID.randomUUID()).build(),
                        DUMMY_SERVER_ONE,
                        Roles.PROVIDER,
                        DelegationStatus.ACTIVE))
            .compose(
                res ->
                    utils.createFakeDelegation(
                        consumerDelegationId,
                        consProvUser,
                        new UserBuilder().userId(UUID.randomUUID()).build(),
                        DUMMY_SERVER_TWO,
                        Roles.CONSUMER,
                        DelegationStatus.ACTIVE));

    create
        .onSuccess(
            res -> {
              JsonArray req =
                  new JsonArray()
                      .add(new JsonObject().put("id", consumerDelegationId.toString()))
                      .add(new JsonObject().put("id", providerDelegationId.toString()));
              List<DeleteDelegationRequest> request = DeleteDelegationRequest.jsonArrayToList(req);

              policyService
                  .deleteDelegation(request, consProvUser)
                  .onComplete(
                      testContext.succeeding(
                          response ->
                              testContext.verify(
                                  () -> {
                                    assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
                                    assertEquals(
                                        SUCC_TITLE_DELETE_DELE, response.getString("title"));
                                    assertEquals(200, response.getInteger("status"));
                                    testContext.completeNow();
                                  })));
            })
        .onFailure(fail -> testContext.failNow(fail.getMessage()));
  }
}
