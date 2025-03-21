package iudx.aaa.server.policy;

import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_ROLE;
import static iudx.aaa.server.apiserver.util.Urn.URN_SUCCESS;
import static iudx.aaa.server.policy.Constants.ERR_DETAIL_LIST_DELEGATE_ROLES;
import static iudx.aaa.server.policy.Constants.ERR_TITLE_INVALID_ROLES;
import static iudx.aaa.server.policy.Constants.RESULTS;
import static iudx.aaa.server.policy.Constants.SUCC_TITLE_LIST_DELEGS;
import static iudx.aaa.server.policy.Constants.TYPE;
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

/** Unit tests for listing delegations. */
@ExtendWith({VertxExtension.class, MockitoExtension.class})
public class ListDelegationTest {
  private static Logger LOGGER =
      LogManager.getLogger(iudx.aaa.server.policy.ListDelegationTest.class);

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

  private static User consumerUser =
      new UserBuilder()
          .userId(UUID.randomUUID())
          .name("aa", "bb")
          .roles(List.of(Roles.CONSUMER))
          .rolesToRsMapping(Map.of(Roles.CONSUMER.toString(), new JsonArray().add(DUMMY_SERVER)))
          .build();

  private static User delegateUser =
      new UserBuilder()
          .userId(UUID.randomUUID())
          .name("aa", "bb")
          .roles(List.of(Roles.DELEGATE))
          .rolesToRsMapping(Map.of(Roles.DELEGATE.toString(), new JsonArray().add(DUMMY_SERVER)))
          .build();

  private static User providerUser =
      new UserBuilder()
          .userId(UUID.randomUUID())
          .name("aa", "bb")
          .roles(List.of(Roles.PROVIDER))
          .rolesToRsMapping(Map.of(Roles.PROVIDER.toString(), new JsonArray().add(DUMMY_SERVER)))
          .build();

  private static final UUID CONSUMER_DELEGATION_ID = UUID.randomUUID();
  private static final UUID PROVIDER_DELEGATION_ID = UUID.randomUUID();

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

    // create 1 active consumer and provider delegation each, and one inactive each
    Future<Void> create =
        utils
            .createFakeResourceServer(
                DUMMY_SERVER, new UserBuilder().userId(UUID.randomUUID()).build())
            .compose(res -> utils.createFakeUser(consumerUser, false, false))
            .compose(res -> utils.createFakeUser(providerUser, false, false))
            .compose(
                res ->
                    utils.createFakeDelegation(
                        CONSUMER_DELEGATION_ID,
                        consumerUser,
                        delegateUser,
                        DUMMY_SERVER,
                        Roles.CONSUMER,
                        DelegationStatus.ACTIVE))
            .compose(
                res ->
                    utils.createFakeDelegation(
                        PROVIDER_DELEGATION_ID,
                        providerUser,
                        delegateUser,
                        DUMMY_SERVER,
                        Roles.PROVIDER,
                        DelegationStatus.ACTIVE))
            .compose(
                res ->
                    utils.createFakeDelegation(
                        UUID.randomUUID(),
                        consumerUser,
                        delegateUser,
                        DUMMY_SERVER,
                        Roles.CONSUMER,
                        DelegationStatus.DELETED))
            .compose(
                res ->
                    utils.createFakeDelegation(
                        UUID.randomUUID(),
                        providerUser,
                        delegateUser,
                        DUMMY_SERVER,
                        Roles.PROVIDER,
                        DelegationStatus.DELETED));

    create
        .onSuccess(
            r -> {
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
  @DisplayName("Get delegations as provider")
  void listDelegationAsProvider(VertxTestContext testContext) {

    JsonObject regServiceResponse =
        new JsonObject()
            .put(providerUser.getUserId(), utils.getKcAdminJson(providerUser))
            .put(delegateUser.getUserId(), utils.getKcAdminJson(delegateUser));

    mockRegistrationFactory.setResponse(regServiceResponse);

    policyService
        .listDelegation(providerUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
                          assertEquals(SUCC_TITLE_LIST_DELEGS, response.getString("title"));
                          assertEquals(200, response.getInteger("status"));
                          JsonArray resp = response.getJsonArray(RESULTS);

                          assertTrue(resp.size() == 1);
                          JsonObject j = resp.getJsonObject(0);

                          assertTrue(
                              j.getJsonObject("owner")
                                  .getString("id")
                                  .equals(providerUser.getUserId()));
                          assertTrue(
                              j.getJsonObject("owner")
                                  .getString("email")
                                  .equals(utils.getDetails(providerUser).email));

                          assertTrue(
                              j.getJsonObject("user")
                                  .getString("id")
                                  .equals(delegateUser.getUserId()));
                          assertTrue(
                              j.getJsonObject("user")
                                  .getString("email")
                                  .equals(utils.getDetails(delegateUser).email));

                          assertEquals(j.getString("url"), DUMMY_SERVER);
                          assertEquals(
                              j.getString("role"), Roles.PROVIDER.toString().toLowerCase());
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Get delegations as consumer")
  void listDelegationAsConsumer(VertxTestContext testContext) {

    JsonObject regServiceResponse =
        new JsonObject()
            .put(consumerUser.getUserId(), utils.getKcAdminJson(consumerUser))
            .put(delegateUser.getUserId(), utils.getKcAdminJson(delegateUser));
    mockRegistrationFactory.setResponse(regServiceResponse);

    policyService
        .listDelegation(consumerUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
                          assertEquals(SUCC_TITLE_LIST_DELEGS, response.getString("title"));
                          assertEquals(200, response.getInteger("status"));
                          JsonArray resp = response.getJsonArray(RESULTS);

                          assertTrue(resp.size() == 1);
                          JsonObject j = resp.getJsonObject(0);

                          assertTrue(
                              j.getJsonObject("owner")
                                  .getString("id")
                                  .equals(consumerUser.getUserId()));
                          assertTrue(
                              j.getJsonObject("owner")
                                  .getString("email")
                                  .equals(utils.getDetails(consumerUser).email));

                          assertTrue(
                              j.getJsonObject("user")
                                  .getString("id")
                                  .equals(delegateUser.getUserId()));
                          assertTrue(
                              j.getJsonObject("user")
                                  .getString("email")
                                  .equals(utils.getDetails(delegateUser).email));

                          assertEquals(j.getString("url"), DUMMY_SERVER);
                          assertEquals(
                              j.getString("role"), Roles.CONSUMER.toString().toLowerCase());
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Get delegations as delegate")
  void listDelegationAsDelegate(VertxTestContext testContext) {

    JsonObject regServiceResponse =
        new JsonObject()
            .put(consumerUser.getUserId(), utils.getKcAdminJson(consumerUser))
            .put(delegateUser.getUserId(), utils.getKcAdminJson(delegateUser))
            .put(providerUser.getUserId(), utils.getKcAdminJson(providerUser));
    mockRegistrationFactory.setResponse(regServiceResponse);

    Checkpoint sawProviderDelegation = testContext.checkpoint();
    Checkpoint sawConsumerDelegation = testContext.checkpoint();

    policyService
        .listDelegation(delegateUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
                          assertEquals(SUCC_TITLE_LIST_DELEGS, response.getString("title"));
                          assertEquals(200, response.getInteger("status"));
                          JsonArray resp = response.getJsonArray(RESULTS);

                          assertTrue(resp.size() == 2);

                          resp.forEach(
                              object -> {
                                JsonObject j = (JsonObject) object;

                                if (j.getString("id").equals(CONSUMER_DELEGATION_ID.toString())) {
                                  assertTrue(
                                      j.getJsonObject("owner")
                                          .getString("id")
                                          .equals(consumerUser.getUserId()));
                                  assertTrue(
                                      j.getJsonObject("owner")
                                          .getString("email")
                                          .equals(utils.getDetails(consumerUser).email));

                                  assertTrue(
                                      j.getJsonObject("user")
                                          .getString("id")
                                          .equals(delegateUser.getUserId()));
                                  assertTrue(
                                      j.getJsonObject("user")
                                          .getString("email")
                                          .equals(utils.getDetails(delegateUser).email));

                                  assertEquals(j.getString("url"), DUMMY_SERVER);
                                  assertEquals(
                                      j.getString("role"), Roles.CONSUMER.toString().toLowerCase());
                                  sawConsumerDelegation.flag();
                                }

                                if (j.getString("id").equals(PROVIDER_DELEGATION_ID.toString())) {
                                  assertTrue(
                                      j.getJsonObject("owner")
                                          .getString("id")
                                          .equals(providerUser.getUserId()));
                                  assertTrue(
                                      j.getJsonObject("owner")
                                          .getString("email")
                                          .equals(utils.getDetails(providerUser).email));

                                  assertTrue(
                                      j.getJsonObject("user")
                                          .getString("id")
                                          .equals(delegateUser.getUserId()));
                                  assertTrue(
                                      j.getJsonObject("user")
                                          .getString("email")
                                          .equals(utils.getDetails(delegateUser).email));

                                  assertEquals(j.getString("url"), DUMMY_SERVER);
                                  assertEquals(
                                      j.getString("role"), Roles.PROVIDER.toString().toLowerCase());
                                  sawProviderDelegation.flag();
                                }
                              });
                        })));
  }

  @Test
  @DisplayName("Fail at get delegations as admin/COS admin/trustee/")
  void cannotListWithCertainRoles(VertxTestContext testContext) {

    User dummyUser =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .roles(List.of(Roles.ADMIN, Roles.COS_ADMIN, Roles.TRUSTEE))
            .rolesToRsMapping(
                Map.of(
                    Roles.DELEGATE.toString(),
                    new JsonArray().add(DUMMY_SERVER),
                    Roles.TRUSTEE.toString(),
                    new JsonArray().add("some-apd.url"),
                    Roles.ADMIN.toString(),
                    new JsonArray().add(DUMMY_SERVER)))
            .build();

    policyService
        .listDelegation(dummyUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(URN_INVALID_ROLE.toString(), response.getString(TYPE));
                          assertEquals(
                              ERR_DETAIL_LIST_DELEGATE_ROLES, response.getString("detail"));
                          assertEquals(ERR_TITLE_INVALID_ROLES, response.getString("title"));
                          assertEquals(401, response.getInteger("status"));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName(
      "User has consumer, provider and delegate roles and can see delegations for them and created by them")
  void listDelegationAsAllValidRoles(VertxTestContext testContext) {
    // creating new users for this test instead of using the globally created ones
    User consumer =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .name("aa", "bb")
            .roles(List.of(Roles.CONSUMER))
            .rolesToRsMapping(Map.of(Roles.CONSUMER.toString(), new JsonArray().add(DUMMY_SERVER)))
            .build();

    User delegate =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .name("aa", "bb")
            .roles(List.of(Roles.DELEGATE))
            .rolesToRsMapping(Map.of(Roles.DELEGATE.toString(), new JsonArray().add(DUMMY_SERVER)))
            .build();

    User provider =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .name("aa", "bb")
            .roles(List.of(Roles.PROVIDER))
            .rolesToRsMapping(Map.of(Roles.PROVIDER.toString(), new JsonArray().add(DUMMY_SERVER)))
            .build();

    User allRoles =
        new UserBuilder()
            .userId(UUID.randomUUID())
            .name("aa", "bb")
            .roles(List.of(Roles.PROVIDER, Roles.CONSUMER, Roles.DELEGATE))
            .rolesToRsMapping(
                Map.of(
                    Roles.PROVIDER.toString(),
                    new JsonArray().add(DUMMY_SERVER),
                    Roles.DELEGATE.toString(),
                    new JsonArray().add(DUMMY_SERVER),
                    Roles.CONSUMER.toString(),
                    new JsonArray().add(DUMMY_SERVER)))
            .build();

    UUID CONSUMER_TO_ALLROLES = UUID.randomUUID();
    UUID PROVIDER_TO_ALLROLES = UUID.randomUUID();
    UUID ALLROLES_TO_DELEGATE_CONSUMER = UUID.randomUUID();
    UUID ALLROLES_TO_DELEGATE_PROVIDER = UUID.randomUUID();

    Checkpoint sawProviderDelegationFORAllRoles = testContext.checkpoint();
    Checkpoint sawConsumerDelegationFORAllRoles = testContext.checkpoint();

    Checkpoint sawProviderDelegationBYAllRoles = testContext.checkpoint();
    Checkpoint sawConsumerDelegationBYAllRoles = testContext.checkpoint();

    Future<Void> createData =
        utils
            .createFakeUser(consumer, false, false)
            .compose(res -> utils.createFakeUser(provider, false, false))
            .compose(res -> utils.createFakeUser(delegate, false, false))
            .compose(res -> utils.createFakeUser(allRoles, false, false))
            .compose(
                res ->
                    utils.createFakeDelegation(
                        CONSUMER_TO_ALLROLES,
                        consumer,
                        allRoles,
                        DUMMY_SERVER,
                        Roles.CONSUMER,
                        DelegationStatus.ACTIVE))
            .compose(
                res ->
                    utils.createFakeDelegation(
                        PROVIDER_TO_ALLROLES,
                        provider,
                        allRoles,
                        DUMMY_SERVER,
                        Roles.PROVIDER,
                        DelegationStatus.ACTIVE))
            .compose(
                res ->
                    utils.createFakeDelegation(
                        ALLROLES_TO_DELEGATE_CONSUMER,
                        allRoles,
                        delegate,
                        DUMMY_SERVER,
                        Roles.CONSUMER,
                        DelegationStatus.ACTIVE))
            .compose(
                res ->
                    utils.createFakeDelegation(
                        ALLROLES_TO_DELEGATE_PROVIDER,
                        allRoles,
                        delegate,
                        DUMMY_SERVER,
                        Roles.PROVIDER,
                        DelegationStatus.ACTIVE));

    createData.onSuccess(
        succ -> {
          JsonObject regServiceResponse =
              new JsonObject()
                  .put(consumer.getUserId(), utils.getKcAdminJson(consumer))
                  .put(delegate.getUserId(), utils.getKcAdminJson(delegate))
                  .put(allRoles.getUserId(), utils.getKcAdminJson(allRoles))
                  .put(provider.getUserId(), utils.getKcAdminJson(provider));

          mockRegistrationFactory.setResponse(regServiceResponse);

          policyService
              .listDelegation(allRoles)
              .onComplete(
                  testContext.succeeding(
                      response ->
                          testContext.verify(
                              () -> {
                                assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
                                assertEquals(SUCC_TITLE_LIST_DELEGS, response.getString("title"));
                                assertEquals(200, response.getInteger("status"));
                                JsonArray resp = response.getJsonArray(RESULTS);

                                assertTrue(resp.size() == 4);

                                resp.forEach(
                                    object -> {
                                      JsonObject j = (JsonObject) object;
                                      assertEquals(j.getString("url"), DUMMY_SERVER);

                                      if (j.getString("id")
                                          .equals(CONSUMER_TO_ALLROLES.toString())) {
                                        assertTrue(
                                            j.getJsonObject("owner")
                                                .getString("id")
                                                .equals(consumer.getUserId()));

                                        assertTrue(
                                            j.getJsonObject("user")
                                                .getString("id")
                                                .equals(allRoles.getUserId()));

                                        assertEquals(
                                            j.getString("role"),
                                            Roles.CONSUMER.toString().toLowerCase());
                                        sawConsumerDelegationFORAllRoles.flag();
                                      }

                                      if (j.getString("id")
                                          .equals(PROVIDER_TO_ALLROLES.toString())) {
                                        assertTrue(
                                            j.getJsonObject("owner")
                                                .getString("id")
                                                .equals(provider.getUserId()));

                                        assertTrue(
                                            j.getJsonObject("user")
                                                .getString("id")
                                                .equals(allRoles.getUserId()));

                                        assertEquals(j.getString("url"), DUMMY_SERVER);
                                        assertEquals(
                                            j.getString("role"),
                                            Roles.PROVIDER.toString().toLowerCase());
                                        sawProviderDelegationFORAllRoles.flag();
                                      }

                                      if (j.getString("id")
                                          .equals(ALLROLES_TO_DELEGATE_CONSUMER.toString())) {
                                        assertTrue(
                                            j.getJsonObject("owner")
                                                .getString("id")
                                                .equals(allRoles.getUserId()));

                                        assertTrue(
                                            j.getJsonObject("user")
                                                .getString("id")
                                                .equals(delegate.getUserId()));

                                        assertEquals(j.getString("url"), DUMMY_SERVER);
                                        assertEquals(
                                            j.getString("role"),
                                            Roles.CONSUMER.toString().toLowerCase());
                                        sawConsumerDelegationBYAllRoles.flag();
                                      }

                                      if (j.getString("id")
                                          .equals(ALLROLES_TO_DELEGATE_PROVIDER.toString())) {
                                        assertTrue(
                                            j.getJsonObject("owner")
                                                .getString("id")
                                                .equals(allRoles.getUserId()));

                                        assertTrue(
                                            j.getJsonObject("user")
                                                .getString("id")
                                                .equals(delegate.getUserId()));

                                        assertEquals(j.getString("url"), DUMMY_SERVER);
                                        assertEquals(
                                            j.getString("role"),
                                            Roles.PROVIDER.toString().toLowerCase());
                                        sawProviderDelegationBYAllRoles.flag();
                                      }
                                    });
                              })));
        });
  }
}
