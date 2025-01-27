package iudx.aaa.server.policy;

import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_INPUT;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_ROLE;
import static iudx.aaa.server.policy.Constants.ERR_CONTEXT_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE;
import static iudx.aaa.server.policy.Constants.ERR_DETAIL_CREATE_DELEGATE_ROLES;
import static iudx.aaa.server.policy.Constants.ERR_DETAIL_DUPLICATE_DELEGATION;
import static iudx.aaa.server.policy.Constants.ERR_DETAIL_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE;
import static iudx.aaa.server.policy.Constants.ERR_TITLE_DUPLICATE_DELEGATION;
import static iudx.aaa.server.policy.Constants.ERR_TITLE_INVALID_ROLES;
import static iudx.aaa.server.policy.Constants.ERR_TITLE_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE;
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
import iudx.aaa.server.apiserver.CreateDelegationRequest;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;
import iudx.aaa.server.apiserver.util.ComposeException;
import iudx.aaa.server.apiserver.util.Urn;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.registration.RegistrationServiceImpl;
import iudx.aaa.server.registration.Utils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

/** Unit tests for delegation creation. */
@ExtendWith({VertxExtension.class, MockitoExtension.class})
public class CreateDelegationTest {

  // Database Properties
  private static Logger LOGGER =
      LogManager.getLogger(iudx.aaa.server.policy.CreateDelegationTest.class);
  private static Configuration config;
  private static String databaseIP;
  private static int databasePort;
  private static String databaseName;
  private static String databaseSchema;
  private static String databaseUserName;
  private static String databasePassword;
  private static int poolSize;
  private static PgPool pgclient;
  private static PoolOptions poolOptions;
  private static PgConnectOptions connectOptions;
  private static PolicyService policyService;
  private static ApdService apdService = Mockito.mock(ApdService.class);
  private static RegistrationServiceImpl registrationService =
      Mockito.mock(RegistrationServiceImpl.class);
  private static CatalogueClient catalogueClient = Mockito.mock(CatalogueClient.class);
  private static Vertx vertxObj;

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

    pgclient = PgPool.pool(vertx, connectOptions, poolOptions);

    utils = new Utils(pgclient);

    policyService =
        new PolicyServiceImpl(pgclient, registrationService, apdService, catalogueClient);
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
                LOGGER.error(
                    "Data is NOT deleted after this test since policy table"
                        + " does not allow deletes and therefore cascade delete fails");
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

    List<CreateDelegationRequest> req = new ArrayList<>();
    policyService
        .createDelegation(req, dummyUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_INVALID_ROLE.toString(), response.getString("type"));
                          assertEquals(ERR_TITLE_INVALID_ROLES, response.getString("title"));
                          assertEquals(
                              ERR_DETAIL_CREATE_DELEGATE_ROLES, response.getString("detail"));
                          assertEquals(401, response.getInteger("status"));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Resource server not present failure")
  void InvalidServer(VertxTestContext testContext) {
    final String DUMMY_SERVER =
        "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

    final String FAKE_SERVER =
        "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

    User consumerUser =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .name("aa", "bb")
            .roles(List.of(Roles.CONSUMER))
            .rolesToRsMapping(Map.of(Roles.CONSUMER.toString(), new JsonArray().add(DUMMY_SERVER)))
            .build();

    String delegateEmail = RandomStringUtils.randomAlphabetic(10).toLowerCase() + "@gmail.com";

    Future<Void> create =
        utils
            .createFakeResourceServer(
                DUMMY_SERVER, new UserBuilder().userId(UUID.randomUUID()).build())
            .compose(res -> utils.createFakeUser(consumerUser, false, false));

    create
        .onSuccess(
            succ -> {
              JsonObject obj =
                  new JsonObject()
                      .put("userEmail", delegateEmail)
                      .put("role", Roles.CONSUMER.toString().toLowerCase())
                      .put("resSerUrl", FAKE_SERVER);

              List<CreateDelegationRequest> req =
                  CreateDelegationRequest.jsonArrayToList(new JsonArray().add(obj));

              policyService
                  .createDelegation(req, consumerUser)
                  .onComplete(
                      testContext.succeeding(
                          response ->
                              testContext.verify(
                                  () -> {
                                    assertEquals(
                                        URN_INVALID_INPUT.toString(), response.getString("type"));
                                    assertEquals(
                                        ERR_TITLE_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE,
                                        response.getString("title"));
                                    assertEquals(
                                        ERR_DETAIL_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE,
                                        response.getString("detail"));
                                    assertEquals(
                                        new JsonArray(List.of(FAKE_SERVER)),
                                        response
                                            .getJsonObject("context")
                                            .getJsonArray(
                                                ERR_CONTEXT_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE));
                                    assertEquals(400, response.getInteger("status"));
                                    testContext.completeNow();
                                  })));
            })
        .onFailure(fail -> testContext.failNow(fail.getMessage()));
  }

  @Test
  @DisplayName("Delegator does not have role for delegation being set")
  void aa(VertxTestContext testContext) {
    final String DUMMY_SERVER =
        "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

    User consumerUser =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .name("aa", "bb")
            .roles(List.of(Roles.CONSUMER))
            .rolesToRsMapping(Map.of(Roles.CONSUMER.toString(), new JsonArray().add(DUMMY_SERVER)))
            .build();

    User providerUser =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .name("aa", "bb")
            .roles(List.of(Roles.PROVIDER))
            .rolesToRsMapping(Map.of(Roles.PROVIDER.toString(), new JsonArray().add(DUMMY_SERVER)))
            .build();

    String delegateEmail = RandomStringUtils.randomAlphabetic(10).toLowerCase() + "@gmail.com";

    Checkpoint providerDelegByConsFails = testContext.checkpoint();
    Checkpoint consumerDelegByProvFails = testContext.checkpoint();

    Future<Void> create =
        utils
            .createFakeResourceServer(
                DUMMY_SERVER, new UserBuilder().userId(UUID.randomUUID()).build())
            .compose(res -> utils.createFakeUser(consumerUser, false, false))
            .compose(res -> utils.createFakeUser(providerUser, false, false));

    create
        .onSuccess(
            succ -> {
              JsonObject providerDelegObj =
                  new JsonObject()
                      .put("userEmail", delegateEmail)
                      .put("role", Roles.PROVIDER.toString().toLowerCase())
                      .put("resSerUrl", DUMMY_SERVER);

              List<CreateDelegationRequest> providerDelegReq =
                  CreateDelegationRequest.jsonArrayToList(new JsonArray().add(providerDelegObj));

              policyService
                  .createDelegation(providerDelegReq, consumerUser)
                  .onComplete(
                      testContext.succeeding(
                          response ->
                              testContext.verify(
                                  () -> {
                                    assertEquals(
                                        URN_INVALID_INPUT.toString(), response.getString("type"));
                                    assertEquals(
                                        ERR_TITLE_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE,
                                        response.getString("title"));
                                    assertEquals(
                                        ERR_DETAIL_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE,
                                        response.getString("detail"));
                                    assertEquals(
                                        new JsonArray(List.of(DUMMY_SERVER)),
                                        response
                                            .getJsonObject("context")
                                            .getJsonArray(
                                                ERR_CONTEXT_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE));
                                    assertEquals(400, response.getInteger("status"));
                                    providerDelegByConsFails.flag();
                                  })));

              JsonObject consumerDelegObj =
                  new JsonObject()
                      .put("userEmail", delegateEmail)
                      .put("role", Roles.CONSUMER.toString().toLowerCase())
                      .put("resSerUrl", DUMMY_SERVER);

              List<CreateDelegationRequest> consumerDelegReq =
                  CreateDelegationRequest.jsonArrayToList(new JsonArray().add(consumerDelegObj));

              policyService
                  .createDelegation(consumerDelegReq, providerUser)
                  .onComplete(
                      testContext.succeeding(
                          response ->
                              testContext.verify(
                                  () -> {
                                    assertEquals(
                                        URN_INVALID_INPUT.toString(), response.getString("type"));
                                    assertEquals(
                                        ERR_TITLE_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE,
                                        response.getString("title"));
                                    assertEquals(
                                        ERR_DETAIL_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE,
                                        response.getString("detail"));
                                    assertEquals(
                                        new JsonArray(List.of(DUMMY_SERVER)),
                                        response
                                            .getJsonObject("context")
                                            .getJsonArray(
                                                ERR_CONTEXT_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE));
                                    assertEquals(400, response.getInteger("status"));
                                    consumerDelegByProvFails.flag();
                                  })));
            })
        .onFailure(fail -> testContext.failNow(fail.getMessage()));
  }

  @Test
  @DisplayName("Delegator does not have role for RS for delegation being set")
  void bb(VertxTestContext testContext) {
    final String DUMMY_SERVER_ONE =
        "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

    final String DUMMY_SERVER_TWO =
        "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

    User consumerUser =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .name("aa", "bb")
            .roles(List.of(Roles.CONSUMER))
            .rolesToRsMapping(
                Map.of(Roles.CONSUMER.toString(), new JsonArray().add(DUMMY_SERVER_ONE)))
            .build();

    User providerUser =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .name("aa", "bb")
            .roles(List.of(Roles.PROVIDER))
            .rolesToRsMapping(
                Map.of(Roles.PROVIDER.toString(), new JsonArray().add(DUMMY_SERVER_ONE)))
            .build();

    String delegateEmail = RandomStringUtils.randomAlphabetic(10).toLowerCase() + "@gmail.com";

    Checkpoint providerDelegFails = testContext.checkpoint();
    Checkpoint consumerDelegFails = testContext.checkpoint();

    Future<Void> create =
        utils
            .createFakeResourceServer(
                DUMMY_SERVER_ONE, new UserBuilder().userId(UUID.randomUUID()).build())
            .compose(
                res ->
                    utils.createFakeResourceServer(
                        DUMMY_SERVER_TWO, new UserBuilder().userId(UUID.randomUUID()).build()))
            .compose(res -> utils.createFakeUser(consumerUser, false, false))
            .compose(res -> utils.createFakeUser(providerUser, false, false));

    create
        .onSuccess(
            succ -> {
              JsonObject providerDelegObj =
                  new JsonObject()
                      .put("userEmail", delegateEmail)
                      .put("role", Roles.PROVIDER.toString().toLowerCase())
                      .put("resSerUrl", DUMMY_SERVER_TWO);

              List<CreateDelegationRequest> providerDelegReq =
                  CreateDelegationRequest.jsonArrayToList(new JsonArray().add(providerDelegObj));

              policyService
                  .createDelegation(providerDelegReq, providerUser)
                  .onComplete(
                      testContext.succeeding(
                          response ->
                              testContext.verify(
                                  () -> {
                                    assertEquals(
                                        URN_INVALID_INPUT.toString(), response.getString("type"));
                                    assertEquals(
                                        ERR_TITLE_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE,
                                        response.getString("title"));
                                    assertEquals(
                                        ERR_DETAIL_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE,
                                        response.getString("detail"));
                                    assertEquals(
                                        new JsonArray(List.of(DUMMY_SERVER_TWO)),
                                        response
                                            .getJsonObject("context")
                                            .getJsonArray(
                                                ERR_CONTEXT_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE));
                                    assertEquals(400, response.getInteger("status"));
                                    providerDelegFails.flag();
                                  })));

              JsonObject consumerDelegObj =
                  new JsonObject()
                      .put("userEmail", delegateEmail)
                      .put("role", Roles.CONSUMER.toString().toLowerCase())
                      .put("resSerUrl", DUMMY_SERVER_TWO);

              List<CreateDelegationRequest> consumerDelegReq =
                  CreateDelegationRequest.jsonArrayToList(new JsonArray().add(consumerDelegObj));

              policyService
                  .createDelegation(consumerDelegReq, consumerUser)
                  .onComplete(
                      testContext.succeeding(
                          response ->
                              testContext.verify(
                                  () -> {
                                    assertEquals(
                                        URN_INVALID_INPUT.toString(), response.getString("type"));
                                    assertEquals(
                                        ERR_TITLE_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE,
                                        response.getString("title"));
                                    assertEquals(
                                        ERR_DETAIL_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE,
                                        response.getString("detail"));
                                    assertEquals(
                                        new JsonArray(List.of(DUMMY_SERVER_TWO)),
                                        response
                                            .getJsonObject("context")
                                            .getJsonArray(
                                                ERR_CONTEXT_RS_NOT_EXIST_OR_USER_NO_HAVE_ROLE));
                                    assertEquals(400, response.getInteger("status"));
                                    consumerDelegFails.flag();
                                  })));
            })
        .onFailure(fail -> testContext.failNow(fail.getMessage()));
  }

  @Test
  @DisplayName("Delegate not found on UAC Keycloak")
  void cc(VertxTestContext testContext) {
    final String DUMMY_SERVER =
        "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

    User providerUser =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .name("aa", "bb")
            .roles(List.of(Roles.PROVIDER))
            .rolesToRsMapping(Map.of(Roles.PROVIDER.toString(), new JsonArray().add(DUMMY_SERVER)))
            .build();

    String notFoundEmail = RandomStringUtils.randomAlphabetic(10).toLowerCase() + "@gmail.com";

    Future<Void> create =
        utils
            .createFakeResourceServer(
                DUMMY_SERVER, new UserBuilder().userId(UUID.randomUUID()).build())
            .compose(res -> utils.createFakeUser(providerUser, false, false));

    create
        .onSuccess(
            succ -> {
              Mockito.doAnswer(
                      i -> {
                        Set<String> emails = i.getArgument(0);
                        return Future.failedFuture(
                            new ComposeException(
                                400, Urn.URN_INVALID_INPUT, "Email not exist", emails.toString()));
                      })
                  .when(registrationService)
                  .findUserByEmail(Mockito.eq(Set.of(notFoundEmail)));

              JsonObject providerDelegObj =
                  new JsonObject()
                      .put("userEmail", notFoundEmail)
                      .put("role", Roles.PROVIDER.toString().toLowerCase())
                      .put("resSerUrl", DUMMY_SERVER);

              List<CreateDelegationRequest> providerDelegReq =
                  CreateDelegationRequest.jsonArrayToList(new JsonArray().add(providerDelegObj));

              policyService
                  .createDelegation(providerDelegReq, providerUser)
                  .onComplete(
                      testContext.succeeding(
                          response ->
                              testContext.verify(
                                  () -> {
                                    assertEquals(response.getInteger("status"), 400);
                                    assertEquals(
                                        response.getString("type"), URN_INVALID_INPUT.toString());
                                    testContext.completeNow();
                                  })));
            })
        .onFailure(fail -> testContext.failNow(fail.getMessage()));
  }

  @Test
  @DisplayName("Success provider delegation")
  void providerDelegation(VertxTestContext testContext) {
    final String DUMMY_SERVER =
        "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

    User providerUser =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .name("aa", "bb")
            .roles(List.of(Roles.PROVIDER))
            .rolesToRsMapping(Map.of(Roles.PROVIDER.toString(), new JsonArray().add(DUMMY_SERVER)))
            .build();

    User delegateUser = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();

    Future<Void> create =
        utils
            .createFakeResourceServer(
                DUMMY_SERVER, new UserBuilder().userId(UUID.randomUUID()).build())
            .compose(res -> utils.createFakeUser(providerUser, false, false))
            .compose(res -> utils.createFakeUser(delegateUser, false, false));

    create
        .onSuccess(
            succ -> {
              Mockito.doAnswer(
                      i -> {
                        Set<String> emails = i.getArgument(0);

                        String delegateEmail = new ArrayList<String>(emails).get(0);

                        JsonObject resp = utils.getKcAdminJson(delegateUser);

                        return Future.succeededFuture(new JsonObject().put(delegateEmail, resp));
                      })
                  .when(registrationService)
                  .findUserByEmail(Mockito.eq(Set.of(utils.getDetails(delegateUser).email)));

              JsonObject providerDelegObj =
                  new JsonObject()
                      .put("userEmail", utils.getDetails(delegateUser).email)
                      .put("role", Roles.PROVIDER.toString().toLowerCase())
                      .put("resSerUrl", DUMMY_SERVER);

              List<CreateDelegationRequest> providerDelegReq =
                  CreateDelegationRequest.jsonArrayToList(new JsonArray().add(providerDelegObj));

              policyService
                  .createDelegation(providerDelegReq, providerUser)
                  .onComplete(
                      testContext.succeeding(
                          response ->
                              testContext.verify(
                                  () -> {
                                    assertEquals(
                                        Urn.URN_SUCCESS.toString(), response.getString("type"));
                                    assertEquals(201, response.getInteger("status"));
                                    testContext.completeNow();
                                  })));
            })
        .onFailure(fail -> testContext.failNow(fail.getMessage()));
  }

  @Test
  @DisplayName("Success consumer delegation")
  void consumerDelegation(VertxTestContext testContext) {
    final String DUMMY_SERVER =
        "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

    User consumerUser =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .name("aa", "bb")
            .roles(List.of(Roles.CONSUMER))
            .rolesToRsMapping(Map.of(Roles.CONSUMER.toString(), new JsonArray().add(DUMMY_SERVER)))
            .build();

    User delegateUser = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();

    Future<Void> create =
        utils
            .createFakeResourceServer(
                DUMMY_SERVER, new UserBuilder().userId(UUID.randomUUID()).build())
            .compose(res -> utils.createFakeUser(consumerUser, false, false))
            .compose(res -> utils.createFakeUser(delegateUser, false, false));

    create
        .onSuccess(
            succ -> {
              Mockito.doAnswer(
                      i -> {
                        Set<String> emails = i.getArgument(0);

                        String delegateEmail = new ArrayList<String>(emails).get(0);

                        JsonObject resp = utils.getKcAdminJson(delegateUser);

                        return Future.succeededFuture(new JsonObject().put(delegateEmail, resp));
                      })
                  .when(registrationService)
                  .findUserByEmail(Mockito.eq(Set.of(utils.getDetails(delegateUser).email)));

              JsonObject consumerDelegObj =
                  new JsonObject()
                      .put("userEmail", utils.getDetails(delegateUser).email)
                      .put("role", Roles.CONSUMER.toString().toLowerCase())
                      .put("resSerUrl", DUMMY_SERVER);

              List<CreateDelegationRequest> consumerDelegReq =
                  CreateDelegationRequest.jsonArrayToList(new JsonArray().add(consumerDelegObj));

              policyService
                  .createDelegation(consumerDelegReq, consumerUser)
                  .onComplete(
                      testContext.succeeding(
                          response ->
                              testContext.verify(
                                  () -> {
                                    assertEquals(
                                        Urn.URN_SUCCESS.toString(), response.getString("type"));
                                    assertEquals(201, response.getInteger("status"));
                                    testContext.completeNow();
                                  })));
            })
        .onFailure(fail -> testContext.failNow(fail.getMessage()));
  }

  @Test
  @DisplayName("failure - create delegation that already exists")
  void DuplicateDelegation(VertxTestContext testContext) {
    final String DUMMY_SERVER =
        "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

    User consumerUser =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .name("aa", "bb")
            .roles(List.of(Roles.CONSUMER))
            .rolesToRsMapping(Map.of(Roles.CONSUMER.toString(), new JsonArray().add(DUMMY_SERVER)))
            .build();

    User delegateUser = new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();

    Future<Void> create =
        utils
            .createFakeResourceServer(
                DUMMY_SERVER, new UserBuilder().userId(UUID.randomUUID()).build())
            .compose(res -> utils.createFakeUser(consumerUser, false, false))
            .compose(res -> utils.createFakeUser(delegateUser, false, false));

    Checkpoint created = testContext.checkpoint();
    Checkpoint alreadyExists = testContext.checkpoint();

    create
        .onSuccess(
            succ -> {
              Mockito.doAnswer(
                      i -> {
                        Set<String> emails = i.getArgument(0);

                        String delegateEmail = new ArrayList<String>(emails).get(0);

                        JsonObject resp = utils.getKcAdminJson(delegateUser);

                        return Future.succeededFuture(new JsonObject().put(delegateEmail, resp));
                      })
                  .when(registrationService)
                  .findUserByEmail(Mockito.eq(Set.of(utils.getDetails(delegateUser).email)));

              JsonObject consumerDelegObj =
                  new JsonObject()
                      .put("userEmail", utils.getDetails(delegateUser).email)
                      .put("role", Roles.CONSUMER.toString().toLowerCase())
                      .put("resSerUrl", DUMMY_SERVER);

              List<CreateDelegationRequest> consumerDelegReq =
                  CreateDelegationRequest.jsonArrayToList(new JsonArray().add(consumerDelegObj));

              policyService
                  .createDelegation(consumerDelegReq, consumerUser)
                  .onComplete(
                      testContext.succeeding(
                          response ->
                              testContext.verify(
                                  () -> {
                                    assertEquals(
                                        Urn.URN_SUCCESS.toString(), response.getString("type"));
                                    assertEquals(201, response.getInteger("status"));
                                    created.flag();

                                    policyService
                                        .createDelegation(consumerDelegReq, consumerUser)
                                        .onComplete(
                                            testContext.succeeding(
                                                resp ->
                                                    testContext.verify(
                                                        () -> {
                                                          assertEquals(
                                                              Urn.URN_ALREADY_EXISTS.toString(),
                                                              resp.getString("type"));
                                                          assertEquals(
                                                              ERR_TITLE_DUPLICATE_DELEGATION,
                                                              resp.getString("title"));
                                                          assertEquals(
                                                              ERR_DETAIL_DUPLICATE_DELEGATION,
                                                              resp.getString("detail"));
                                                          assertEquals(
                                                              409, resp.getInteger("status"));
                                                          alreadyExists.flag();
                                                        })));
                                  })));
            })
        .onFailure(fail -> testContext.failNow(fail.getMessage()));
  }

  @Test
  @DisplayName("Success multiple delegations for different delegates")
  void successMultiple(VertxTestContext testContext) {
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

    User consumerDelegateUser =
        new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();
    User providerDelegateUser =
        new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();

    Future<Void> create =
        utils
            .createFakeResourceServer(
                DUMMY_SERVER_ONE, new UserBuilder().userId(UUID.randomUUID()).build())
            .compose(
                res ->
                    utils.createFakeResourceServer(
                        DUMMY_SERVER_TWO, new UserBuilder().userId(UUID.randomUUID()).build()))
            .compose(res -> utils.createFakeUser(consProvUser, false, false))
            .compose(res -> utils.createFakeUser(consumerDelegateUser, false, false))
            .compose(res -> utils.createFakeUser(providerDelegateUser, false, false));

    create
        .onSuccess(
            succ -> {
              JsonObject userDetails =
                  new JsonObject()
                      .put(
                          utils.getDetails(providerDelegateUser).email,
                          utils.getKcAdminJson(providerDelegateUser))
                      .put(
                          utils.getDetails(consumerDelegateUser).email,
                          utils.getKcAdminJson(consumerDelegateUser));

              Mockito.when(
                      registrationService.findUserByEmail(
                          Mockito.eq(
                              Set.of(
                                  utils.getDetails(providerDelegateUser).email,
                                  utils.getDetails(consumerDelegateUser).email))))
                  .thenReturn(Future.succeededFuture(userDetails));

              JsonObject providerDelegObj =
                  new JsonObject()
                      .put("userEmail", utils.getDetails(providerDelegateUser).email)
                      .put("role", Roles.PROVIDER.toString().toLowerCase())
                      .put("resSerUrl", DUMMY_SERVER_ONE);

              JsonObject consumerDelegObj =
                  new JsonObject()
                      .put("userEmail", utils.getDetails(consumerDelegateUser).email)
                      .put("role", Roles.CONSUMER.toString().toLowerCase())
                      .put("resSerUrl", DUMMY_SERVER_TWO);

              List<CreateDelegationRequest> req =
                  CreateDelegationRequest.jsonArrayToList(
                      new JsonArray().add(providerDelegObj).add(consumerDelegObj));

              policyService
                  .createDelegation(req, consProvUser)
                  .onComplete(
                      testContext.succeeding(
                          response ->
                              testContext.verify(
                                  () -> {
                                    assertEquals(
                                        Urn.URN_SUCCESS.toString(), response.getString("type"));
                                    assertEquals(201, response.getInteger("status"));
                                    testContext.completeNow();
                                  })));
            })
        .onFailure(fail -> testContext.failNow(fail.getMessage()));
  }
}
