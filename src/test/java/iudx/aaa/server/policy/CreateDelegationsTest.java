package iudx.aaa.server.policy;

import io.vertx.core.CompositeFuture;
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
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apd.ApdService;
import iudx.aaa.server.apiserver.CreateDelegationRequest;
import iudx.aaa.server.apiserver.RoleStatus;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.registration.RegistrationServiceImpl;
import iudx.aaa.server.registration.Utils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static iudx.aaa.server.apiserver.util.Urn.URN_ALREADY_EXISTS;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_INPUT;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_ROLE;
import static iudx.aaa.server.apiserver.util.Urn.URN_SUCCESS;
import static iudx.aaa.server.policy.Constants.*;
import static iudx.aaa.server.registration.Utils.SQL_DELETE_ANY_POLICIES;
import static iudx.aaa.server.registration.Utils.SQL_DELETE_DELEGATE;
import static iudx.aaa.server.registration.Utils.SQL_DELETE_ORG;
import static iudx.aaa.server.registration.Utils.SQL_DELETE_SERVERS;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
public class CreateDelegationsTest {
  static String name = RandomStringUtils.randomAlphabetic(10).toLowerCase();
  // Database Properties
  static String url = name + ".com";
  static Future<UUID> orgIdFut;
  private static Logger LOGGER =
      LogManager.getLogger(iudx.aaa.server.policy.CreateDelegationsTest.class);
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
  private static JsonObject authOptions;
  private static JsonObject catalogueOptions;
  private static Future<JsonObject> adminUser;
  private static Future<JsonObject> providerUser;
  private static Future<JsonObject> authDelUser;
  private static Future<JsonObject> otherDelUser;
  private static Future<JsonObject> consumerUser;
  private static JsonObject catOptions;
  private static UUID authSerId;
  private static String authServerURL;
  private static UUID otherSerId;
  private static String otherServerURL;
  private static UUID fakeCatSerId;
  private static String fakeCatSerUrl;
  private static Vertx vertxObj;
  private static PolicyService policyService;
  private static ApdService apdService = Mockito.mock(ApdService.class);
  private static EmailClient emailClient = Mockito.mock(EmailClient.class);
  private static RegistrationServiceImpl registrationService =
      Mockito.mock(RegistrationServiceImpl.class);
  private static CatalogueClient catalogueClient = Mockito.mock(CatalogueClient.class);

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
    authOptions = dbConfig.getJsonObject("authOptions");
    catalogueOptions = dbConfig.getJsonObject("catalogueOptions");
    catOptions = dbConfig.getJsonObject("catOptions");
    authServerURL = "auth" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";
    otherServerURL = "other" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";
    fakeCatSerUrl  = "cat" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";
    authOptions.put("authServerUrl", authServerURL);
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

    // create org
    orgIdFut =
        pgclient.withConnection(
            conn ->
                conn.preparedQuery(Utils.SQL_CREATE_ORG)
                    .execute(Tuple.of(name, url))
                    .map(row -> row.iterator().next().getUUID("id")));

    // create users with diff roles
    adminUser =
        orgIdFut.compose(
            orgId ->
                Utils.createFakeUser(
                    pgclient,
                    orgId.toString(),
                    "",
                    Map.of(Roles.ADMIN, RoleStatus.APPROVED),
                    false));
    providerUser =
        orgIdFut.compose(
            orgId ->
                Utils.createFakeUser(
                    pgclient,
                    orgId.toString(),
                    "",
                    Map.of(Roles.PROVIDER, RoleStatus.APPROVED),
                    false));
    authDelUser =
        orgIdFut.compose(
            orgId ->
                Utils.createFakeUser(
                    pgclient,
                    orgId.toString(),
                    "",
                    Map.of(Roles.DELEGATE, RoleStatus.APPROVED),
                    false));
    otherDelUser =
        orgIdFut.compose(
            orgId ->
                Utils.createFakeUser(
                    pgclient,
                    orgId.toString(),
                    "",
                    Map.of(Roles.DELEGATE, RoleStatus.APPROVED),
                    false));

    consumerUser =
        orgIdFut.compose(
            orgId ->
                Utils.createFakeUser(
                    pgclient,
                    orgId.toString(),
                    "",
                    Map.of(Roles.CONSUMER, RoleStatus.APPROVED),
                    false));

    CompositeFuture.all(adminUser, providerUser, authDelUser, otherDelUser, consumerUser)
        .onSuccess(
            succ -> {
              // create Auth server and other server
              authSerId = UUID.randomUUID();
              otherSerId = UUID.randomUUID();
              fakeCatSerId = UUID.randomUUID();
              Utils.createFakeResourceServer(pgclient, adminUser.result(), authSerId, authServerURL)
                  .compose(
                      comp ->
                          Utils.createFakeResourceServer(
                              pgclient, adminUser.result(), otherSerId, otherServerURL))
                  .compose(
                      comp ->
                          Utils.createFakeResourceServer(
                              pgclient, adminUser.result(),fakeCatSerId, fakeCatSerUrl))
                  .compose(
                      pol ->
                          Utils.createFakePolicy(
                              pgclient,
                              UUID.fromString(providerUser.result().getString("userId")),
                              Constants.itemTypes.RESOURCE_SERVER,
                              UUID.fromString(adminUser.result().getString("userId")),
                              authSerId))
                  .compose(
                      otherPol ->
                          Utils.createFakePolicy(
                              pgclient,
                              UUID.fromString(authDelUser.result().getString("userId")),
                              Constants.itemTypes.RESOURCE_SERVER,
                              UUID.fromString(adminUser.result().getString("userId")),
                              authSerId))
                  .compose(
                      proPol ->
                          Utils.createFakePolicy(
                              pgclient,
                              UUID.fromString(authDelUser.result().getString("userId")),
                              Constants.itemTypes.RESOURCE_SERVER,
                              UUID.fromString(adminUser.result().getString("userId")),
                              otherSerId))
                  .compose(
                      proPol ->
                          Utils.createFakePolicy(
                              pgclient,
                              UUID.fromString(providerUser.result().getString("userId")),
                              Constants.itemTypes.RESOURCE_SERVER,
                              UUID.fromString(adminUser.result().getString("userId")),
                              otherSerId))
                  .compose(
                      del ->
                          Utils.createDelegation(
                              pgclient,
                              UUID.fromString(authDelUser.result().getString("userId")),
                              UUID.fromString(providerUser.result().getString("userId")),
                              authSerId))
                  .compose(
                      del ->
                          Utils.createDelegation(
                              pgclient,
                              UUID.fromString(authDelUser.result().getString("userId")),
                              UUID.fromString(providerUser.result().getString("userId")),
                              otherSerId))
                  .onSuccess(
                      success -> {
                        policyService =
                            new PolicyServiceImpl(
                                pgclient,
                                registrationService,
                                apdService,
                                catalogueClient,
                                authOptions,
                                catOptions,emailClient);
                        testContext.completeNow();
                      })
                  .onFailure(
                      failure -> {
                        testContext.failNow("failed " + failure.toString());
                      });
            });
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing....");
    Tuple servers = Tuple.of(List.of(authServerURL, otherServerURL).toArray());
    UUID adminId = UUID.fromString(adminUser.result().getString("userId"));
    UUID providerId = UUID.fromString(providerUser.result().getString("userId"));
    UUID delegateId = UUID.fromString(authDelUser.result().getString("userId"));
    Tuple policyOwners = Tuple.of(List.of(adminId, providerId, delegateId).toArray(UUID[]::new));

    List<JsonObject> users =
        List.of(
            providerUser.result(), authDelUser.result(), consumerUser.result(), adminUser.result());
    pgclient
        .withConnection(
            conn ->
                conn.preparedQuery(SQL_DELETE_DELEGATE)
                    .execute(policyOwners)
                    .compose(
                        del ->
                            conn.preparedQuery(SQL_DELETE_SERVERS)
                                .execute(servers)
                                .compose(
                                    bb ->
                                        conn.preparedQuery(SQL_DELETE_ANY_POLICIES)
                                            .execute(policyOwners))
                                .compose(aa -> Utils.deleteFakeUser(pgclient, users))
                                .compose(
                                    succ ->
                                        conn.preparedQuery(SQL_DELETE_ORG)
                                            .execute(Tuple.of(orgIdFut.result())))))
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

  @Order(1)
  @Test
  @DisplayName("failure- user calling api does not have provider role/ is not an auth delegate")
  void userNotADelegate(VertxTestContext testContext) {

    JsonObject userJson = consumerUser.result();
    User user =
        new User.UserBuilder()
            .keycloakId(userJson.getString("keycloakId"))
            .userId(userJson.getString("userId"))
            .name(userJson.getString("firstName"), userJson.getString("lastName"))
            .roles(List.of(Roles.CONSUMER))
            .build();

    List<CreateDelegationRequest> req = new ArrayList<>();
    policyService.createDelegation(
        req,
        user,
        new JsonObject(),
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      assertEquals(URN_INVALID_ROLE.toString(), response.getString("type"));
                      assertEquals(ERR_TITLE_INVALID_ROLES, response.getString("title"));
                      assertEquals(401, response.getInteger("status"));
                      testContext.completeNow();
                    })));
  }
  @Order(2)
  @Test
  @DisplayName("resServer not present failure")
  void InvalidServer(VertxTestContext testContext) {
    JsonObject userJson = providerUser.result();
    User user =
        new User.UserBuilder()
            .keycloakId(userJson.getString("keycloakId"))
            .userId(userJson.getString("userId"))
            .name(userJson.getString("firstName"), userJson.getString("lastName"))
            .roles(List.of(Roles.PROVIDER))
            .build();

    JsonObject obj =
        new JsonObject()
            .put("userId", authDelUser.result().getString("userId"))
            .put("resSerId", RandomStringUtils.randomAlphabetic(10).toLowerCase());

    List<CreateDelegationRequest> req =
        CreateDelegationRequest.jsonArrayToList(new JsonArray().add(obj));
    policyService.createDelegation(
        req,
        user,
        new JsonObject(),
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
                      assertEquals(SERVER_NOT_PRESENT, response.getString("title"));
                      assertEquals(400, response.getInteger("status"));
                      testContext.completeNow();
                    })));
  }
  @Order(3)
  @Test
  @DisplayName("success - create delegation as provider for other server and check duplicate fail ")
  void SuccessDel(VertxTestContext testContext) {
    JsonObject userJson = providerUser.result();
    User user =
        new User.UserBuilder()
            .keycloakId(userJson.getString("keycloakId"))
            .userId(userJson.getString("userId"))
            .name(userJson.getString("firstName"), userJson.getString("lastName"))
            .roles(List.of(Roles.PROVIDER))
            .build();

    JsonObject obj =
        new JsonObject()
            .put("userId", otherDelUser.result().getString("userId"))
            .put("resSerId", otherServerURL);
    List<CreateDelegationRequest> req =
        CreateDelegationRequest.jsonArrayToList(new JsonArray().add(obj));
      Tuple policyOwners =
          Tuple.of(
              List.of(UUID.fromString(providerUser.result().getString("userId")))
                  .toArray(UUID[]::new));
    policyService.createDelegation(
        req,
        user,
        new JsonObject(),
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      assertEquals(URN_SUCCESS.toString(), response.getString("type"));
                      assertEquals("added delegations", response.getString("title"));
                      assertEquals(200, response.getInteger("status"));
                      policyService.createDelegation(
          req,
          user,
          new JsonObject(),
          testContext.succeeding(
              resp ->
                  testContext.verify(
                      () -> {
                          assertEquals(URN_ALREADY_EXISTS.toString(), resp.getString("type"));
                          assertEquals(DUPLICATE_DELEGATION, resp.getString("title"));
                          assertEquals(409, resp.getInteger("status"));
                          pgclient
                          .withConnection(conn -> conn.preparedQuery(SQL_DELETE_DELEGATE)
                              .execute(policyOwners))
                          .onSuccess(succ -> testContext.completeNow()).onFailure(
                              fail -> testContext.failNow("Could not delete data"));
                          })));
  })));}
    @Order(4)
  @Test
  @DisplayName("failure - create delegation that already exists")
  void DuplicateDelegation(VertxTestContext testContext) {
    JsonObject userJson = providerUser.result();
    User user =
        new User.UserBuilder()
            .keycloakId(userJson.getString("keycloakId"))
            .userId(userJson.getString("userId"))
            .name(userJson.getString("firstName"), userJson.getString("lastName"))
            .roles(List.of(Roles.PROVIDER))
            .build();

    JsonObject obj =
        new JsonObject()
            .put("userId", authDelUser.result().getString("userId"))
            .put("resSerId", authServerURL);
    List<CreateDelegationRequest> req =
        CreateDelegationRequest.jsonArrayToList(new JsonArray().add(obj));
    policyService.createDelegation(
        req,
        user,
        new JsonObject(),
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      assertEquals(URN_ALREADY_EXISTS.toString(), response.getString("type"));
                      assertEquals(DUPLICATE_DELEGATION, response.getString("title"));
                      assertEquals(409, response.getInteger("status"));
                      testContext.completeNow();
                    })));
  }
  @Order(5)
  @Test
  @DisplayName("AuthDelegate trying to create auth delegate failure")
  void AuthDelFailure(VertxTestContext testContext) {
    JsonObject userJson = authDelUser.result();
    User user =
        new User.UserBuilder()
            .keycloakId(userJson.getString("keycloakId"))
            .userId(userJson.getString("userId"))
            .name(userJson.getString("firstName"), userJson.getString("lastName"))
            .roles(List.of(Roles.DELEGATE))
            .build();

    JsonObject obj =
        new JsonObject()
            .put("userId", otherDelUser.result().getString("userId"))
            .put("resSerId", authServerURL);
    List<CreateDelegationRequest> req =
        CreateDelegationRequest.jsonArrayToList(new JsonArray().add(obj));
    policyService.createDelegation(
        req,
        user,
        new JsonObject().put("providerId", providerUser.result().getString("userId")),
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
                      assertEquals(ERR_TITLE_AUTH_DELE_CREATE, response.getString("title"));
                      assertEquals(403, response.getInteger("status"));
                      testContext.completeNow();
                    })));
  }
  @Order(6)
  @Test
  @DisplayName("failure- user for whom does not have delegate role")
  void userRoleFailure(VertxTestContext testContext) {
      Checkpoint checkAdmin = testContext.checkpoint();
     Checkpoint checkCansumer = testContext.checkpoint();

      // check for admin
      JsonObject userJson = providerUser.result();
      User provideruser =
              new User.UserBuilder()
                      .keycloakId(userJson.getString("keycloakId"))
                      .userId(userJson.getString("userId"))
                      .name(userJson.getString("firstName"), userJson.getString("lastName"))
                      .roles(List.of(Roles.PROVIDER))
                      .build();

      JsonObject obj =
              new JsonObject()
                      .put("userId", adminUser.result().getString("userId"))
                      .put("resSerId", authServerURL);
      List<CreateDelegationRequest> req =
              CreateDelegationRequest.jsonArrayToList(new JsonArray().add(obj));
      policyService.createDelegation(
              req,
              provideruser,
              new JsonObject(),
              testContext.succeeding(
                      response ->
                              testContext.verify(
                                      () -> {
                                          assertEquals(URN_INVALID_ROLE.toString(), response.getString("type"));
                                          assertEquals(NOT_DELEGATE, response.getString("title"));
                                          assertEquals(400, response.getInteger("status"));
                                          checkAdmin.flag();
                                      })));
      // check for consumer
     JsonObject conObj =
              new JsonObject()
                      .put("userId", consumerUser.result().getString("userId"))
                      .put("resSerId", authServerURL);
      List<CreateDelegationRequest> conReq =
              CreateDelegationRequest.jsonArrayToList(new JsonArray().add(conObj));
      policyService.createDelegation(
              conReq,
              provideruser,
              new JsonObject(),
              testContext.succeeding(
                      response ->
                              testContext.verify(
                                      () -> {
                                          assertEquals(URN_INVALID_ROLE.toString(), response.getString("type"));
                                          assertEquals(NOT_DELEGATE, response.getString("title"));
                                          assertEquals(400, response.getInteger("status"));
                                          checkCansumer.flag();
                                      })));
  }
  @Order(7)
  @Test
  @DisplayName("failure- user calling api does not have auth server policy")
  void authPolicyFailure(VertxTestContext testContext) {
      JsonObject userJson = otherDelUser.result();
      User user =
              new User.UserBuilder()
                      .keycloakId(userJson.getString("keycloakId"))
                      .userId(userJson.getString("userId"))
                      .name(userJson.getString("firstName"), userJson.getString("lastName"))
                      .roles(List.of(Roles.PROVIDER))
                      .build();

      JsonObject obj =
              new JsonObject()
                      .put("userId", otherDelUser.result().getString("userId"))
                      .put("resSerId", authServerURL);
      List<CreateDelegationRequest> req =
              CreateDelegationRequest.jsonArrayToList(new JsonArray().add(obj));
      policyService.createDelegation(
              req,
              user,
              new JsonObject(),
              testContext.succeeding(
                      response ->
                              testContext.verify(
                                      () -> {
                                         assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
                                          assertEquals(NO_AUTH_POLICY, response.getString("title"));
                                          assertEquals(NO_AUTH_POLICY,response.getString("detail"));
                                          assertEquals(403, response.getInteger("status"));
                                          testContext.completeNow();
                                      })));
  }
    @Order(8)
  @Test
  @DisplayName("Success - creating a delegate for multiple servers as auth server delegate")
  void successAsAuthDel(VertxTestContext testContext) {
    JsonObject userJson = authDelUser.result();
    User user =
        new User.UserBuilder()
            .keycloakId(userJson.getString("keycloakId"))
            .userId(userJson.getString("userId"))
            .name(userJson.getString("firstName"), userJson.getString("lastName"))
            .roles(List.of(Roles.DELEGATE))
            .build();

    JsonObject otherSerReq =
        new JsonObject()
            .put("userId", otherDelUser.result().getString("userId"))
            .put("resSerId", otherServerURL);

      JsonObject catSerReq =
          new JsonObject()
              .put("userId", otherDelUser.result().getString("userId"))
              .put("resSerId", fakeCatSerUrl);
    List<CreateDelegationRequest> req =
        CreateDelegationRequest.jsonArrayToList(new JsonArray().add(otherSerReq).add(catSerReq));

    Tuple policyOwners =
        Tuple.of(
            List.of(UUID.fromString(providerUser.result().getString("userId")))
                .toArray(UUID[]::new));
    policyService.createDelegation(
        req,
        user,
        new JsonObject().put("providerId", providerUser.result().getString("userId")),
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                      assertEquals(URN_SUCCESS.toString(), response.getString("type"));
                      assertEquals("added delegations", response.getString("title"));
                      assertEquals(200, response.getInteger("status"));
                      //repeated test so delegation must be deleted within the test
                      pgclient
                      .withConnection(conn -> conn.preparedQuery(SQL_DELETE_DELEGATE)
                          .execute(policyOwners))
                      .onSuccess(succ -> testContext.completeNow()).onFailure(
                          fail -> testContext.failNow("Could not delete data"));
                    })));
  }

    @Order(9)
    @Test
    @DisplayName("Success - multiple request as provider")
    void successMulAsProvider(VertxTestContext testContext) {
        JsonObject userJson = providerUser.result();
        User user =
                new User.UserBuilder()
                        .keycloakId(userJson.getString("keycloakId"))
                        .userId(userJson.getString("userId"))
                        .name(userJson.getString("firstName"), userJson.getString("lastName"))
                        .roles(List.of(Roles.PROVIDER))
                        .build();

        JsonObject otherSerReq =
                new JsonObject()
                        .put("userId", otherDelUser.result().getString("userId"))
                        .put("resSerId", otherServerURL);

        JsonObject catSerReq =
                new JsonObject()
                        .put("userId", otherDelUser.result().getString("userId"))
                        .put("resSerId", fakeCatSerUrl);
        List<CreateDelegationRequest> req =
                CreateDelegationRequest.jsonArrayToList(new JsonArray().add(otherSerReq).add(catSerReq));

        Tuple policyOwners =
                Tuple.of(
                        List.of(UUID.fromString(providerUser.result().getString("userId")))
                                .toArray(UUID[]::new));
        policyService.createDelegation(
                req,
                user,
                new JsonObject(),
                testContext.succeeding(
                        response ->
                                testContext.verify(
                                        () -> {
                                            assertEquals(URN_SUCCESS.toString(), response.getString("type"));
                                            assertEquals("added delegations", response.getString("title"));
                                            assertEquals(200, response.getInteger("status"));
                                            pgclient
                                            .withConnection(conn -> conn.preparedQuery(SQL_DELETE_DELEGATE)
                                                .execute(policyOwners))
                                            .onSuccess(succ -> testContext.completeNow()).onFailure(
                                                fail -> testContext.failNow("Could not delete data"));
                                        })));
    }
}
