package iudx.aaa.server.policy;

import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_ROLE;
import static iudx.aaa.server.apiserver.util.Urn.URN_SUCCESS;
import static iudx.aaa.server.policy.Constants.RESP_DELEG_EMAILS;
import static iudx.aaa.server.policy.Constants.SUCC_TITLE_DELEG_EMAILS;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_NOT_TRUSTEE;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_NOT_TRUSTEE;
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
import iudx.aaa.server.apd.ApdService;
import iudx.aaa.server.apiserver.DelegationStatus;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;
import iudx.aaa.server.configuration.Configuration;
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
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for getting delegate emails by trustee. */
@ExtendWith({VertxExtension.class, MockitoExtension.class})
public class GetDelegateEmailsTest {
  private static Logger LOGGER =
      LogManager.getLogger(iudx.aaa.server.policy.GetDelegateEmailsTest.class);

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

  private static RegistrationService registrationService;

  private static Vertx vertxObj;
  private static MockRegistrationFactory mockRegistrationFactory = new MockRegistrationFactory();
  private static CatalogueClient catalogueClient = Mockito.mock(CatalogueClient.class);

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

    Future<Void> create =
        utils
            .createFakeResourceServer(
                DUMMY_SERVER, new UserBuilder().userId(UUID.randomUUID()).build())
            .compose(
                res ->
                    utils.createFakeResourceServer(
                        DUMMY_SERVER_THAT_NO_ONE_HAS_ROLES_FOR,
                        new UserBuilder().userId(UUID.randomUUID()).build()));

    create
        .onSuccess(
            res -> {
              registrationService = mockRegistrationFactory.getInstance();
              policyService =
                  new PolicyServiceImpl(pool, registrationService, apdService, catalogueClient);
              testContext.completeNow();
            })
        .onFailure(err -> testContext.failNow(err.getMessage()));
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing....");
    utils
        .deleteFakeResourceServer()
        .compose(res -> utils.deleteFakeDelegation())
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

    User dummyUser =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .roles(
                List.of(
                    Roles.PROVIDER, Roles.CONSUMER, Roles.COS_ADMIN, Roles.ADMIN, Roles.DELEGATE))
            .rolesToRsMapping(
                Map.of(
                    Roles.PROVIDER.toString(),
                    new JsonArray(List.of(DUMMY_SERVER)),
                    Roles.DELEGATE.toString(),
                    new JsonArray(List.of(DUMMY_SERVER)),
                    Roles.CONSUMER.toString(),
                    new JsonArray(List.of(DUMMY_SERVER)),
                    Roles.ADMIN.toString(),
                    new JsonArray(List.of(DUMMY_SERVER))))
            .name("aa", "bb")
            .build();

    policyService
        .getDelegateEmails(dummyUser, UUID.randomUUID().toString(), Roles.CONSUMER, DUMMY_SERVER)
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
  @DisplayName("User ID does not exist on COS")
  void userIdDoesNotExist(VertxTestContext testContext) {

    policyService
        .getDelegateEmails(trusteeUser, UUID.randomUUID().toString(), Roles.CONSUMER, DUMMY_SERVER)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(200, response.getInteger("status"));
                          assertEquals(URN_SUCCESS.toString(), response.getString("type"));
                          assertEquals(SUCC_TITLE_DELEG_EMAILS, response.getString("title"));
                          assertTrue(
                              response
                                  .getJsonObject("results")
                                  .getJsonArray(RESP_DELEG_EMAILS)
                                  .isEmpty());
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("User ID AND RS does not exist on COS")
  void userIdAndRsDoesNotExist(VertxTestContext testContext) {

    policyService
        .getDelegateEmails(
            trusteeUser,
            UUID.randomUUID().toString(),
            Roles.CONSUMER,
            RandomStringUtils.randomAlphabetic(10).toLowerCase())
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(200, response.getInteger("status"));
                          assertEquals(URN_SUCCESS.toString(), response.getString("type"));
                          assertEquals(SUCC_TITLE_DELEG_EMAILS, response.getString("title"));
                          assertTrue(
                              response
                                  .getJsonObject("results")
                                  .getJsonArray(RESP_DELEG_EMAILS)
                                  .isEmpty());
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("User ID exists, RS does not")
  void userIdExistsRsDoesNotExist(VertxTestContext testContext) {

    User user =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .roles(List.of(Roles.CONSUMER))
            .rolesToRsMapping(
                Map.of(Roles.CONSUMER.toString(), new JsonArray(List.of(DUMMY_SERVER))))
            .name("aa", "bb")
            .build();

    Future<Void> create = utils.createFakeUser(user, false, false);

    create
        .onSuccess(
            res -> {
              policyService
                  .getDelegateEmails(
                      trusteeUser,
                      user.getUserId(),
                      Roles.CONSUMER,
                      RandomStringUtils.randomAlphabetic(10).toLowerCase())
                  .onComplete(
                      testContext.succeeding(
                          response ->
                              testContext.verify(
                                  () -> {
                                    assertEquals(200, response.getInteger("status"));
                                    assertEquals(
                                        URN_SUCCESS.toString(), response.getString("type"));
                                    assertEquals(
                                        SUCC_TITLE_DELEG_EMAILS, response.getString("title"));
                                    assertTrue(
                                        response
                                            .getJsonObject("results")
                                            .getJsonArray(RESP_DELEG_EMAILS)
                                            .isEmpty());
                                    testContext.completeNow();
                                  })));
            })
        .onFailure(fail -> testContext.failNow(fail.getMessage()));
  }

  @Test
  @DisplayName("User does not have role")
  void userDoesNotHaveRole(VertxTestContext testContext) {

    User user =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .roles(List.of(Roles.CONSUMER))
            .rolesToRsMapping(
                Map.of(Roles.CONSUMER.toString(), new JsonArray(List.of(DUMMY_SERVER))))
            .name("aa", "bb")
            .build();

    Future<Void> create = utils.createFakeUser(user, false, false);

    create
        .onSuccess(
            res -> {
              policyService
                  .getDelegateEmails(trusteeUser, user.getUserId(), Roles.PROVIDER, DUMMY_SERVER)
                  .onComplete(
                      testContext.succeeding(
                          response ->
                              testContext.verify(
                                  () -> {
                                    assertEquals(200, response.getInteger("status"));
                                    assertEquals(
                                        URN_SUCCESS.toString(), response.getString("type"));
                                    assertEquals(
                                        SUCC_TITLE_DELEG_EMAILS, response.getString("title"));
                                    assertTrue(
                                        response
                                            .getJsonObject("results")
                                            .getJsonArray(RESP_DELEG_EMAILS)
                                            .isEmpty());
                                    testContext.completeNow();
                                  })));
            })
        .onFailure(fail -> testContext.failNow(fail.getMessage()));
  }

  @Test
  @DisplayName("User does not have role for RS")
  void userHasNoRolesForRs(VertxTestContext testContext) {

    User user =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .roles(List.of(Roles.CONSUMER))
            .rolesToRsMapping(
                Map.of(Roles.CONSUMER.toString(), new JsonArray(List.of(DUMMY_SERVER))))
            .name("aa", "bb")
            .build();

    Future<Void> create = utils.createFakeUser(user, false, false);

    create
        .onSuccess(
            res -> {
              policyService
                  .getDelegateEmails(
                      trusteeUser,
                      user.getUserId(),
                      Roles.CONSUMER,
                      DUMMY_SERVER_THAT_NO_ONE_HAS_ROLES_FOR)
                  .onComplete(
                      testContext.succeeding(
                          response ->
                              testContext.verify(
                                  () -> {
                                    assertEquals(200, response.getInteger("status"));
                                    assertEquals(
                                        URN_SUCCESS.toString(), response.getString("type"));
                                    assertEquals(
                                        SUCC_TITLE_DELEG_EMAILS, response.getString("title"));
                                    assertTrue(
                                        response
                                            .getJsonObject("results")
                                            .getJsonArray(RESP_DELEG_EMAILS)
                                            .isEmpty());
                                    testContext.completeNow();
                                  })));
            })
        .onFailure(fail -> testContext.failNow(fail.getMessage()));
  }

  @Test
  @DisplayName("User has role for RS but does not have any delegations")
  void userHasRoleButNoDelegs(VertxTestContext testContext) {

    User user =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .roles(List.of(Roles.CONSUMER))
            .rolesToRsMapping(
                Map.of(Roles.CONSUMER.toString(), new JsonArray(List.of(DUMMY_SERVER))))
            .name("aa", "bb")
            .build();

    Future<Void> create = utils.createFakeUser(user, false, false);

    create
        .onSuccess(
            res -> {
              policyService
                  .getDelegateEmails(trusteeUser, user.getUserId(), Roles.CONSUMER, DUMMY_SERVER)
                  .onComplete(
                      testContext.succeeding(
                          response ->
                              testContext.verify(
                                  () -> {
                                    assertEquals(200, response.getInteger("status"));
                                    assertEquals(
                                        URN_SUCCESS.toString(), response.getString("type"));
                                    assertEquals(
                                        SUCC_TITLE_DELEG_EMAILS, response.getString("title"));
                                    assertTrue(
                                        response
                                            .getJsonObject("results")
                                            .getJsonArray(RESP_DELEG_EMAILS)
                                            .isEmpty());
                                    testContext.completeNow();
                                  })));
            })
        .onFailure(fail -> testContext.failNow(fail.getMessage()));
  }

  @Test
  @DisplayName("User has deleted delegations - no emails sent")
  void userHasDeletedDelegs(VertxTestContext testContext) {

    User user =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .roles(List.of(Roles.CONSUMER))
            .rolesToRsMapping(
                Map.of(Roles.CONSUMER.toString(), new JsonArray(List.of(DUMMY_SERVER))))
            .name("aa", "bb")
            .build();

    User delegateUser =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .name("aa", "bb")
            .roles(List.of(Roles.DELEGATE))
            .rolesToRsMapping(Map.of(Roles.DELEGATE.toString(), new JsonArray().add(DUMMY_SERVER)))
            .build();

    Future<Void> create =
        utils
            .createFakeUser(user, false, false)
            .compose(res -> utils.createFakeUser(delegateUser, false, false))
            .compose(
                res ->
                    utils.createFakeDelegation(
                        UUID.randomUUID(),
                        user,
                        delegateUser,
                        DUMMY_SERVER,
                        Roles.CONSUMER,
                        DelegationStatus.DELETED));

    create
        .onSuccess(
            res -> {
              policyService
                  .getDelegateEmails(trusteeUser, user.getUserId(), Roles.CONSUMER, DUMMY_SERVER)
                  .onComplete(
                      testContext.succeeding(
                          response ->
                              testContext.verify(
                                  () -> {
                                    assertEquals(200, response.getInteger("status"));
                                    assertEquals(
                                        URN_SUCCESS.toString(), response.getString("type"));
                                    assertEquals(
                                        SUCC_TITLE_DELEG_EMAILS, response.getString("title"));
                                    assertTrue(
                                        response
                                            .getJsonObject("results")
                                            .getJsonArray(RESP_DELEG_EMAILS)
                                            .isEmpty());
                                    testContext.completeNow();
                                  })));
            })
        .onFailure(fail -> testContext.failNow(fail.getMessage()));
  }

  @Test
  @DisplayName("Get consumer delegate emails")
  void getConsumerDelegEmails(VertxTestContext testContext) {

    User user =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .roles(List.of(Roles.CONSUMER))
            .rolesToRsMapping(
                Map.of(Roles.CONSUMER.toString(), new JsonArray(List.of(DUMMY_SERVER))))
            .name("aa", "bb")
            .build();

    User delegateUserOne =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .name("aa", "bb")
            .roles(List.of(Roles.DELEGATE))
            .rolesToRsMapping(Map.of(Roles.DELEGATE.toString(), new JsonArray().add(DUMMY_SERVER)))
            .build();

    User delegateUserTwo =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .name("aa", "bb")
            .roles(List.of(Roles.DELEGATE))
            .rolesToRsMapping(Map.of(Roles.DELEGATE.toString(), new JsonArray().add(DUMMY_SERVER)))
            .build();

    Future<Void> create =
        utils
            .createFakeUser(user, false, false)
            .compose(res -> utils.createFakeUser(delegateUserOne, false, false))
            .compose(
                res ->
                    utils.createFakeDelegation(
                        UUID.randomUUID(),
                        user,
                        delegateUserOne,
                        DUMMY_SERVER,
                        Roles.CONSUMER,
                        DelegationStatus.ACTIVE))
            .compose(
                res ->
                    utils.createFakeDelegation(
                        UUID.randomUUID(),
                        user,
                        delegateUserTwo,
                        DUMMY_SERVER,
                        Roles.CONSUMER,
                        DelegationStatus.ACTIVE));

    create
        .onSuccess(
            res -> {
              JsonObject regServiceResponse =
                  new JsonObject()
                      .put(delegateUserOne.getUserId(), utils.getKcAdminJson(delegateUserOne))
                      .put(delegateUserTwo.getUserId(), utils.getKcAdminJson(delegateUserTwo));

              mockRegistrationFactory.setResponse(regServiceResponse);

              policyService
                  .getDelegateEmails(trusteeUser, user.getUserId(), Roles.CONSUMER, DUMMY_SERVER)
                  .onComplete(
                      testContext.succeeding(
                          response ->
                              testContext.verify(
                                  () -> {
                                    assertEquals(200, response.getInteger("status"));
                                    assertEquals(
                                        URN_SUCCESS.toString(), response.getString("type"));
                                    assertEquals(
                                        SUCC_TITLE_DELEG_EMAILS, response.getString("title"));

                                    JsonArray sentEmails =
                                        response
                                            .getJsonObject("results")
                                            .getJsonArray(RESP_DELEG_EMAILS);

                                    assertEquals(sentEmails.size(), 2);

                                    assertTrue(
                                        sentEmails.contains(
                                            utils.getDetails(delegateUserOne).email));
                                    assertTrue(
                                        sentEmails.contains(
                                            utils.getDetails(delegateUserTwo).email));
                                    testContext.completeNow();
                                  })));
            })
        .onFailure(fail -> testContext.failNow(fail.getMessage()));
  }

  @Test
  @DisplayName("Get provider delegate emails")
  void getProviderDelegEmails(VertxTestContext testContext) {

    User user =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .roles(List.of(Roles.PROVIDER))
            .rolesToRsMapping(
                Map.of(Roles.PROVIDER.toString(), new JsonArray(List.of(DUMMY_SERVER))))
            .name("aa", "bb")
            .build();

    User delegateUserOne =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .name("aa", "bb")
            .roles(List.of(Roles.DELEGATE))
            .rolesToRsMapping(Map.of(Roles.DELEGATE.toString(), new JsonArray().add(DUMMY_SERVER)))
            .build();

    User delegateUserTwo =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .name("aa", "bb")
            .roles(List.of(Roles.DELEGATE))
            .rolesToRsMapping(Map.of(Roles.DELEGATE.toString(), new JsonArray().add(DUMMY_SERVER)))
            .build();

    Future<Void> create =
        utils
            .createFakeUser(user, false, false)
            .compose(res -> utils.createFakeUser(delegateUserOne, false, false))
            .compose(
                res ->
                    utils.createFakeDelegation(
                        UUID.randomUUID(),
                        user,
                        delegateUserOne,
                        DUMMY_SERVER,
                        Roles.PROVIDER,
                        DelegationStatus.ACTIVE))
            .compose(
                res ->
                    utils.createFakeDelegation(
                        UUID.randomUUID(),
                        user,
                        delegateUserTwo,
                        DUMMY_SERVER,
                        Roles.PROVIDER,
                        DelegationStatus.ACTIVE));

    create
        .onSuccess(
            res -> {
              JsonObject regServiceResponse =
                  new JsonObject()
                      .put(delegateUserOne.getUserId(), utils.getKcAdminJson(delegateUserOne))
                      .put(delegateUserTwo.getUserId(), utils.getKcAdminJson(delegateUserTwo));

              mockRegistrationFactory.setResponse(regServiceResponse);

              policyService
                  .getDelegateEmails(trusteeUser, user.getUserId(), Roles.PROVIDER, DUMMY_SERVER)
                  .onComplete(
                      testContext.succeeding(
                          response ->
                              testContext.verify(
                                  () -> {
                                    assertEquals(200, response.getInteger("status"));
                                    assertEquals(
                                        URN_SUCCESS.toString(), response.getString("type"));
                                    assertEquals(
                                        SUCC_TITLE_DELEG_EMAILS, response.getString("title"));

                                    JsonArray sentEmails =
                                        response
                                            .getJsonObject("results")
                                            .getJsonArray(RESP_DELEG_EMAILS);

                                    assertEquals(sentEmails.size(), 2);

                                    assertTrue(
                                        sentEmails.contains(
                                            utils.getDetails(delegateUserOne).email));
                                    assertTrue(
                                        sentEmails.contains(
                                            utils.getDetails(delegateUserTwo).email));
                                    testContext.completeNow();
                                  })));
            })
        .onFailure(fail -> testContext.failNow(fail.getMessage()));
  }

  @Test
  @DisplayName("User has both provider-delegates and consumer-delegates")
  void bothConsumerAndProviderDelegs(VertxTestContext testContext) {

    User user =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .roles(List.of(Roles.PROVIDER, Roles.CONSUMER))
            .rolesToRsMapping(
                Map.of(
                    Roles.CONSUMER.toString(),
                    new JsonArray(List.of(DUMMY_SERVER)),
                    Roles.PROVIDER.toString(),
                    new JsonArray(List.of(DUMMY_SERVER))))
            .name("aa", "bb")
            .build();

    User consumerDelegateUser =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .name("aa", "bb")
            .roles(List.of(Roles.DELEGATE))
            .rolesToRsMapping(Map.of(Roles.DELEGATE.toString(), new JsonArray().add(DUMMY_SERVER)))
            .build();

    User providerDelegateUser =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .name("aa", "bb")
            .roles(List.of(Roles.DELEGATE))
            .rolesToRsMapping(Map.of(Roles.DELEGATE.toString(), new JsonArray().add(DUMMY_SERVER)))
            .build();

    Checkpoint getProviderDelegateEmail = testContext.checkpoint();
    Checkpoint getConsumerDelegateEmail = testContext.checkpoint();

    Future<Void> create =
        utils
            .createFakeUser(user, false, false)
            .compose(res -> utils.createFakeUser(consumerDelegateUser, false, false))
            .compose(
                res ->
                    utils.createFakeDelegation(
                        UUID.randomUUID(),
                        user,
                        consumerDelegateUser,
                        DUMMY_SERVER,
                        Roles.CONSUMER,
                        DelegationStatus.ACTIVE))
            .compose(
                res ->
                    utils.createFakeDelegation(
                        UUID.randomUUID(),
                        user,
                        providerDelegateUser,
                        DUMMY_SERVER,
                        Roles.PROVIDER,
                        DelegationStatus.ACTIVE));

    create
        .onSuccess(
            res -> {
              JsonObject regServiceResponse =
                  new JsonObject()
                      .put(
                          consumerDelegateUser.getUserId(),
                          utils.getKcAdminJson(consumerDelegateUser))
                      .put(
                          providerDelegateUser.getUserId(),
                          utils.getKcAdminJson(providerDelegateUser));

              mockRegistrationFactory.setResponse(regServiceResponse);

              policyService
                  .getDelegateEmails(trusteeUser, user.getUserId(), Roles.CONSUMER, DUMMY_SERVER)
                  .onComplete(
                      testContext.succeeding(
                          response ->
                              testContext.verify(
                                  () -> {
                                    assertEquals(200, response.getInteger("status"));
                                    assertEquals(
                                        URN_SUCCESS.toString(), response.getString("type"));
                                    assertEquals(
                                        SUCC_TITLE_DELEG_EMAILS, response.getString("title"));

                                    JsonArray sentEmails =
                                        response
                                            .getJsonObject("results")
                                            .getJsonArray(RESP_DELEG_EMAILS);

                                    assertEquals(sentEmails.size(), 1);

                                    assertTrue(
                                        sentEmails.contains(
                                            utils.getDetails(consumerDelegateUser).email));
                                    getConsumerDelegateEmail.flag();
                                  })));

              policyService
                  .getDelegateEmails(trusteeUser, user.getUserId(), Roles.PROVIDER, DUMMY_SERVER)
                  .onComplete(
                      testContext.succeeding(
                          response ->
                              testContext.verify(
                                  () -> {
                                    assertEquals(200, response.getInteger("status"));
                                    assertEquals(
                                        URN_SUCCESS.toString(), response.getString("type"));
                                    assertEquals(
                                        SUCC_TITLE_DELEG_EMAILS, response.getString("title"));

                                    JsonArray sentEmails =
                                        response
                                            .getJsonObject("results")
                                            .getJsonArray(RESP_DELEG_EMAILS);

                                    assertEquals(sentEmails.size(), 1);

                                    assertTrue(
                                        sentEmails.contains(
                                            utils.getDetails(providerDelegateUser).email));
                                    getProviderDelegateEmail.flag();
                                  })));
            })
        .onFailure(fail -> testContext.failNow(fail.getMessage()));
  }
}
