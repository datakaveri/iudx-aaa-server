package iudx.aaa.server.registration;

import static iudx.aaa.server.apiserver.util.Urn.*;
import static iudx.aaa.server.registration.Constants.CLIENT_SECRET_BYTES;
import static iudx.aaa.server.registration.Constants.CONFIG_AUTH_URL;
import static iudx.aaa.server.registration.Constants.CONFIG_OMITTED_SERVERS;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_INVALID_CLI_ID;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_NO_USER_PROFILE;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_ORG_ID_REQUIRED;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_ORG_NO_EXIST;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_ORG_NO_MATCH;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_ROLE_EXISTS;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_USER_NOT_KC;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_INVALID_CLI_ID;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_NO_USER_PROFILE;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_ORG_ID_REQUIRED;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_ORG_NO_EXIST;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_ORG_NO_MATCH;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_ROLE_EXISTS;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_USER_NOT_KC;
import static iudx.aaa.server.registration.Constants.NIL_UUID;
import static iudx.aaa.server.registration.Constants.RESP_CLIENT_ARR;
import static iudx.aaa.server.registration.Constants.RESP_CLIENT_ID;
import static iudx.aaa.server.registration.Constants.RESP_CLIENT_SC;
import static iudx.aaa.server.registration.Constants.RESP_EMAIL;
import static iudx.aaa.server.registration.Constants.RESP_ORG;
import static iudx.aaa.server.registration.Constants.RESP_PHONE;
import static iudx.aaa.server.registration.Constants.SUCC_TITLE_REGEN_CLIENT_SECRET;
import static iudx.aaa.server.registration.Constants.SUCC_TITLE_UPDATED_USER_ROLES;
import static iudx.aaa.server.registration.Utils.SQL_CREATE_ADMIN_SERVER;
import static iudx.aaa.server.registration.Utils.SQL_CREATE_ORG;
import static iudx.aaa.server.registration.Utils.SQL_DELETE_ORG;
import static iudx.aaa.server.registration.Utils.SQL_DELETE_SERVERS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import io.vertx.core.Future;
import io.vertx.core.Promise;
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
import iudx.aaa.server.apiserver.RevokeToken;
import iudx.aaa.server.apiserver.RoleStatus;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.UpdateProfileRequest;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.token.TokenService;
import java.util.ArrayList;
import java.util.HashMap;
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

@ExtendWith(VertxExtension.class)
public class UpdateUserTest {
  private static Logger LOGGER = LogManager.getLogger(UpdateUserTest.class);

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

  static String name = RandomStringUtils.randomAlphabetic(10).toLowerCase();
  static String url = name + ".com";
  static Promise<UUID> orgId;
  static Future<JsonObject> adminUser;

  private static final String DUMMY_AUTH_SERVER =
      "auth" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + "iudx.io";

  private static final String DUMMY_SERVER_1 =
      "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

  private static final String DUMMY_SERVER_2 =
      "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

  static Future<UUID> orgIdFut;
  static List<JsonObject> createdUsers = new ArrayList<JsonObject>();
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
          new PgConnectOptions().setPort(databasePort).setHost(databaseIP).setDatabase(databaseName)
              .setUser(databaseUserName).setPassword(databasePassword).setProperties(schemaProp);
    }

    if (poolOptions == null) {
      poolOptions = new PoolOptions().setMaxSize(poolSize);
    }

    pool = PgPool.pool(vertx, connectOptions, poolOptions);

    /*
     * We inject the DUMMY_AUTH_SERVER url in the config. The servers that need to be omitted can be
     * in config-test itself
     */
    options.put(CONFIG_AUTH_URL, DUMMY_AUTH_SERVER).put(CONFIG_OMITTED_SERVERS,
        dbConfig.getJsonArray(CONFIG_OMITTED_SERVERS));

    /* create fake organization for registration */
    orgIdFut = pool.withConnection(conn -> conn.preparedQuery(SQL_CREATE_ORG)
        .execute(Tuple.of(name, url)).map(row -> row.iterator().next().getUUID("id")));

    /*
     * we create a fake auth and 2 dummy servers to test out the revocation logic, so as to not
     * affect any real servers in test. We also need to create an admin user since we need a valid
     * user ID when inserting into the resource_server table.
     */
    adminUser = orgIdFut.compose(id -> Utils.createFakeUser(pool, id.toString(), url,
        Map.of(Roles.ADMIN, RoleStatus.APPROVED), true));

    adminUser.compose(adminDetails -> {
      UUID adUid = UUID.fromString(adminDetails.getString("userId"));

      createdUsers.add(adminDetails);

      List<Tuple> servers = List.of(Tuple.of("Auth Server", adUid, DUMMY_AUTH_SERVER),
          Tuple.of("Other Server One", adUid, DUMMY_SERVER_1),
          Tuple.of("Other Server Two", adUid, DUMMY_SERVER_2));

      return pool.withConnection(
          conn -> conn.preparedQuery(SQL_CREATE_ADMIN_SERVER).executeBatch(servers));
    }).onSuccess(res -> {
      registrationService = new RegistrationServiceImpl(pool, kc, tokenService, options);
      testContext.completeNow();
    }).onFailure(err -> testContext.failNow(err.getMessage()));
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing and resetting DB");

    Tuple servers = Tuple.of(List.of(DUMMY_AUTH_SERVER, DUMMY_SERVER_1, DUMMY_SERVER_2).toArray());

    pool.withConnection(conn -> conn.preparedQuery(SQL_DELETE_SERVERS).execute(servers)
        .compose(success -> Utils.deleteFakeUser(pool, createdUsers))
        .compose(succ -> conn.preparedQuery(SQL_DELETE_ORG).execute(Tuple.of(orgIdFut.result()))))
        .onComplete(x -> {
          if (x.failed()) {
            LOGGER.warn(x.cause().getMessage());
          }
          vertxObj.close(testContext.succeeding(response -> testContext.completeNow()));
        });
  }

  @Test
  @DisplayName("Test user profile not exists")
  void userDoesNotExist(VertxTestContext testContext) {
    User user = new UserBuilder().keycloakId(UUID.randomUUID()).name("Foo", "Bar").build();

    JsonObject req = new JsonObject().put("orgId", orgIdFut.result().toString()).put("roles",
        new JsonArray().add("delegate"));

    UpdateProfileRequest request = new UpdateProfileRequest(req);

    Mockito.when(kc.getEmailId(any())).thenReturn(Future.succeededFuture("email@gmail.com"));

    registrationService.updateUser(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(404, response.getInteger("status"));
          assertEquals(ERR_TITLE_NO_USER_PROFILE, response.getString("title"));
          assertEquals(ERR_DETAIL_NO_USER_PROFILE, response.getString("detail"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("[Update roles] Test no org ID when consumer requesting delegate")
  void consumerNoOrgId(VertxTestContext testContext) {
    JsonObject req = new JsonObject().put("roles", new JsonArray().add("delegate"));

    UpdateProfileRequest request = new UpdateProfileRequest(req);

    List<Roles> roles = List.of(Roles.CONSUMER);
    Map<Roles, RoleStatus> cons = new HashMap<Roles, RoleStatus>();
    cons.put(Roles.CONSUMER, RoleStatus.APPROVED);

    Future<JsonObject> consumer = Utils.createFakeUser(pool, NIL_UUID, "", cons, false);

    consumer.onSuccess(userJson -> {
      createdUsers.add(userJson);

      User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
          .userId(userJson.getString("userId")).roles(roles)
          .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

      Mockito.when(kc.getEmailId(any()))
          .thenReturn(Future.succeededFuture(userJson.getString("email")));

      registrationService.updateUser(request, user,
          testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(400, response.getInteger("status"));
            assertEquals(ERR_TITLE_ORG_ID_REQUIRED, response.getString("title"));
            assertEquals(URN_MISSING_INFO.toString(), response.getString("type"));
            assertEquals(ERR_DETAIL_ORG_ID_REQUIRED, response.getString("detail"));
            testContext.completeNow();
          })));
    });
  }

  @Test
  @DisplayName("[Update roles] Test invalid org Id when getting delegate")
  void consumerInvalidOrg(VertxTestContext testContext) {
    JsonObject req = new JsonObject().put("roles", new JsonArray().add("delegate")).put("orgId",
        UUID.randomUUID().toString());

    UpdateProfileRequest request = new UpdateProfileRequest(req);

    List<Roles> roles = List.of(Roles.CONSUMER);
    Map<Roles, RoleStatus> cons = new HashMap<Roles, RoleStatus>();
    cons.put(Roles.CONSUMER, RoleStatus.APPROVED);

    Future<JsonObject> consumer = Utils.createFakeUser(pool, NIL_UUID, "", cons, false);

    consumer.onSuccess(userJson -> {
      createdUsers.add(userJson);

      User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
          .userId(userJson.getString("userId")).roles(roles)
          .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

      Mockito.when(kc.getEmailId(any()))
          .thenReturn(Future.succeededFuture(userJson.getString("email")));

      registrationService.updateUser(request, user,
          testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(400, response.getInteger("status"));
            assertEquals(ERR_TITLE_ORG_NO_EXIST, response.getString("title"));
            assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
            assertEquals(ERR_DETAIL_ORG_NO_EXIST, response.getString("detail"));
            testContext.completeNow();
          })));
    });
  }

  @Test
  @DisplayName("[Update roles] Test email not found on keycloak - should never happen")
  void emailNotFoundOnKeycloak(VertxTestContext testContext) {
    JsonObject req = new JsonObject().put("roles", new JsonArray().add("delegate")).put("orgId",
        UUID.randomUUID().toString());

    UpdateProfileRequest request = new UpdateProfileRequest(req);

    List<Roles> roles = List.of(Roles.CONSUMER);
    Map<Roles, RoleStatus> cons = new HashMap<Roles, RoleStatus>();
    cons.put(Roles.CONSUMER, RoleStatus.APPROVED);

    Future<JsonObject> consumer = Utils.createFakeUser(pool, NIL_UUID, "", cons, false);

    consumer.onSuccess(userJson -> {
      createdUsers.add(userJson);

      User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
          .userId(userJson.getString("userId")).roles(roles)
          .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

      Mockito.when(kc.getEmailId(any()))
          .thenReturn(Future.succeededFuture(""));

      registrationService.updateUser(request, user,
          testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(400, response.getInteger("status"));
            assertEquals(ERR_TITLE_USER_NOT_KC, response.getString("title"));
            assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
            assertEquals(ERR_DETAIL_USER_NOT_KC, response.getString("detail"));
            testContext.completeNow();
          })));
    });
  }

  @Test
  @DisplayName("[Update roles] Test consumer with gmail email cannot become delegate")
  void consumerDomainMismatch(VertxTestContext testContext) {

    JsonObject req = new JsonObject().put("roles", new JsonArray().add("delegate")).put("orgId",
        orgIdFut.result().toString());

    UpdateProfileRequest request = new UpdateProfileRequest(req);

    List<Roles> roles = List.of(Roles.CONSUMER);
    Map<Roles, RoleStatus> cons = new HashMap<Roles, RoleStatus>();
    cons.put(Roles.CONSUMER, RoleStatus.APPROVED);

    /* Create gmail consumer */
    Future<JsonObject> consumer = Utils.createFakeUser(pool, NIL_UUID, "", cons, false);

    consumer.onSuccess(userJson -> {
      createdUsers.add(userJson);

      User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
          .userId(userJson.getString("userId")).roles(roles)
          .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

      Mockito.when(kc.getEmailId(any()))
          .thenReturn(Future.succeededFuture(userJson.getString("email")));

      registrationService.updateUser(request, user,
          testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(400, response.getInteger("status"));
            assertEquals(ERR_TITLE_ORG_NO_MATCH, response.getString("title"));
            assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
            assertEquals(ERR_DETAIL_ORG_NO_MATCH, response.getString("detail"));
            testContext.completeNow();
          })));
    });
  }

  @Test
  @DisplayName("[Update roles] Test consumer get delegate roles")
  void consumerAddProvDele(VertxTestContext testContext) {

    JsonObject req = new JsonObject().put("roles", new JsonArray().add("delegate")).put("orgId",
        orgIdFut.result().toString());

    UpdateProfileRequest request = new UpdateProfileRequest(req);

    List<Roles> roles = List.of(Roles.CONSUMER);
    Map<Roles, RoleStatus> cons = new HashMap<Roles, RoleStatus>();
    cons.put(Roles.CONSUMER, RoleStatus.APPROVED);

    /* Create consumer with email of created org domain */
    Future<JsonObject> consumer = Utils.createFakeUser(pool, NIL_UUID, url, cons, false);

    consumer.onSuccess(userJson -> {
      createdUsers.add(userJson);

      User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
          .userId(userJson.getString("userId")).roles(roles)
          .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

      Mockito.when(kc.getEmailId(any()))
          .thenReturn(Future.succeededFuture(userJson.getString("email")));
      Mockito.when(kc.modifyRoles(any(), any())).thenReturn(Future.succeededFuture());

      registrationService.updateUser(request, user,
          testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(200, response.getInteger("status"));
            assertEquals(SUCC_TITLE_UPDATED_USER_ROLES, response.getString("title"));
            assertEquals(URN_SUCCESS.toString(), response.getString("type"));

            JsonObject result = response.getJsonObject("results");

            JsonObject name = result.getJsonObject("name");
            assertEquals(name.getString("firstName"), userJson.getString("firstName"));
            assertEquals(name.getString("lastName"), userJson.getString("lastName"));

            @SuppressWarnings("unchecked")
            List<String> returnedRoles = result.getJsonArray("roles").getList();
            List<String> rolesString =
                List.of(Roles.CONSUMER.name().toLowerCase(), Roles.DELEGATE.name().toLowerCase());
            assertTrue(
                returnedRoles.containsAll(rolesString) && rolesString.containsAll(returnedRoles));

            JsonArray clients = result.getJsonArray(RESP_CLIENT_ARR);
            JsonObject defaultClient = clients.getJsonObject(0);
            assertTrue(clients.size() > 0);
            assertEquals(defaultClient.getString(RESP_CLIENT_ID), userJson.getString("clientId"));

            JsonObject org = result.getJsonObject(RESP_ORG);
            assertEquals(org.getString("url"), userJson.getString("url"));

            assertEquals(result.getString(RESP_EMAIL), userJson.getString("email"));
            assertEquals(result.getString("userId"), userJson.getString("userId"));
            assertEquals(result.getString("keycloakId"), userJson.getString("keycloakId"));

            testContext.completeNow();
          })));
    });
  }

  @Test
  @DisplayName("[Update roles] Test existing role request")
  void existingRoles(VertxTestContext testContext) {

    JsonObject req = new JsonObject().put("roles", new JsonArray().add("consumer").add("delegate"))
        .put("orgId", orgIdFut.result().toString());

    UpdateProfileRequest request = new UpdateProfileRequest(req);

    List<Roles> roles = List.of(Roles.DELEGATE, Roles.PROVIDER);
    Map<Roles, RoleStatus> cons = new HashMap<Roles, RoleStatus>();
    cons.put(Roles.PROVIDER, RoleStatus.PENDING);
    cons.put(Roles.DELEGATE, RoleStatus.APPROVED);

    Future<JsonObject> provDele =
        Utils.createFakeUser(pool, orgIdFut.result().toString(), url, cons, false);

    provDele.onSuccess(userJson -> {
      createdUsers.add(userJson);

      User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
          .userId(userJson.getString("userId")).roles(roles)
          .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

      Mockito.when(kc.getEmailId(any()))
          .thenReturn(Future.succeededFuture(userJson.getString("email")));
      Mockito.when(kc.modifyRoles(any(), any())).thenReturn(Future.succeededFuture());

      registrationService.updateUser(request, user,
          testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(409, response.getInteger("status"));
            assertEquals(ERR_TITLE_ROLE_EXISTS, response.getString("title"));
            assertTrue(response.getString("detail").contains(ERR_DETAIL_ROLE_EXISTS));
            assertTrue(response.getString("detail").contains("delegate"));
            assertFalse(response.getString("detail").contains("provider"));
            assertEquals(URN_ALREADY_EXISTS.toString(), response.getString("type"));

            testContext.completeNow();
          })));
    });
  }

  @Test
  @DisplayName("[Update roles] Test pending provider getting consumer and delegate roles - with and without orgId")
  void providerGettingConsDele(VertxTestContext testContext) {

    JsonObject req = new JsonObject().put("roles", new JsonArray().add("consumer").add("delegate"));

    /* Request without orgId */
    UpdateProfileRequest badRequest = new UpdateProfileRequest(req);

    /* Request with orgId */
    UpdateProfileRequest correctRequest =
        new UpdateProfileRequest(req.put("orgId", orgIdFut.result().toString()));

    Map<Roles, RoleStatus> prov = new HashMap<Roles, RoleStatus>();
    prov.put(Roles.PROVIDER, RoleStatus.PENDING);

    Checkpoint noOrgIdFail = testContext.checkpoint();
    Checkpoint withOrgIdSuccess = testContext.checkpoint();

    Future<JsonObject> provider =
        Utils.createFakeUser(pool, orgIdFut.result().toString(), url, prov, true);

    provider.onSuccess(userJson -> {
      createdUsers.add(userJson);

      /* Since provider is pending, do not add to user object */
      User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
          .userId(userJson.getString("userId"))
          .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

      Mockito.when(kc.getEmailId(any()))
          .thenReturn(Future.succeededFuture(userJson.getString("email")));
      Mockito.when(kc.modifyRoles(any(), any())).thenReturn(Future.succeededFuture());

      registrationService.updateUser(badRequest, user,
          testContext.succeeding(resp -> testContext.verify(() -> {

            assertEquals(400, resp.getInteger("status"));
            assertEquals(ERR_TITLE_ORG_ID_REQUIRED, resp.getString("title"));
            assertEquals(URN_MISSING_INFO.toString(), resp.getString("type"));
            assertEquals(ERR_DETAIL_ORG_ID_REQUIRED, resp.getString("detail"));
            noOrgIdFail.flag();



            registrationService.updateUser(correctRequest, user,
                testContext.succeeding(response -> testContext.verify(() -> {
                  assertEquals(200, response.getInteger("status"));
                  assertEquals(SUCC_TITLE_UPDATED_USER_ROLES, response.getString("title"));
                  assertEquals(URN_SUCCESS.toString(), response.getString("type"));

                  JsonObject result = response.getJsonObject("results");

                  JsonObject name = result.getJsonObject("name");
                  assertEquals(name.getString("firstName"), userJson.getString("firstName"));
                  assertEquals(name.getString("lastName"), userJson.getString("lastName"));

                  @SuppressWarnings("unchecked")
                  List<String> returnedRoles = result.getJsonArray("roles").getList();
                  List<String> rolesString = List.of(Roles.CONSUMER.name().toLowerCase(),
                      Roles.DELEGATE.name().toLowerCase());
                  assertTrue(returnedRoles.containsAll(rolesString)
                      && rolesString.containsAll(returnedRoles));

                  JsonArray clients = result.getJsonArray(RESP_CLIENT_ARR);
                  JsonObject defaultClient = clients.getJsonObject(0);
                  assertTrue(clients.size() > 0);
                  assertEquals(defaultClient.getString(RESP_CLIENT_ID),
                      userJson.getString("clientId"));

                  JsonObject org = result.getJsonObject(RESP_ORG);
                  assertEquals(org.getString("url"), userJson.getString("url"));

                  assertEquals(result.getString(RESP_EMAIL), userJson.getString("email"));
                  assertEquals(result.getString(RESP_PHONE), userJson.getString("phone"));
                  assertEquals(result.getString("userId"), userJson.getString("userId"));
                  assertEquals(result.getString("keycloakId"), userJson.getString("keycloakId"));

                  withOrgIdSuccess.flag();
                })));
          })));
    });
  }

  @Test
  @DisplayName("[Update roles] Test provider getting consumer with keycloak transaction failure ")
  void keycloakFailure(VertxTestContext testContext) {

    JsonObject req = new JsonObject().put("roles", new JsonArray().add("consumer"));

    UpdateProfileRequest request = new UpdateProfileRequest(req);

    List<Roles> roles = List.of(Roles.PROVIDER);
    Map<Roles, RoleStatus> prov = new HashMap<Roles, RoleStatus>();
    prov.put(Roles.PROVIDER, RoleStatus.APPROVED);

    Future<JsonObject> consumer =
        Utils.createFakeUser(pool, orgIdFut.result().toString(), url, prov, true);

    consumer.onSuccess(userJson -> {
      createdUsers.add(userJson);

      User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
          .userId(userJson.getString("userId")).roles(roles)
          .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

      Mockito.when(kc.getEmailId(any()))
          .thenReturn(Future.succeededFuture(userJson.getString("email")));
      Mockito.when(kc.modifyRoles(any(), any())).thenReturn(Future.failedFuture("fail"));

      Promise<Void> failed = Promise.promise();
      registrationService.updateUser(request, user,
          testContext.failing(response -> testContext.verify(() -> {
            failed.complete();
          })));

      failed.future().onSuccess(res -> {
        Mockito.when(kc.modifyRoles(any(), any())).thenReturn(Future.succeededFuture());

        registrationService.updateUser(request, user,
            testContext.succeeding(response -> testContext.verify(() -> {
              assertEquals(200, response.getInteger("status"));
              assertEquals(SUCC_TITLE_UPDATED_USER_ROLES, response.getString("title"));
              assertEquals(URN_SUCCESS.toString(), response.getString("type"));

              JsonObject result = response.getJsonObject("results");

              JsonObject name = result.getJsonObject("name");
              assertEquals(name.getString("firstName"), userJson.getString("firstName"));
              assertEquals(name.getString("lastName"), userJson.getString("lastName"));

              @SuppressWarnings("unchecked")
              List<String> returnedRoles = result.getJsonArray("roles").getList();
              List<String> rolesString =
                  List.of(Roles.CONSUMER.name().toLowerCase(), Roles.PROVIDER.name().toLowerCase());
              assertTrue(
                  returnedRoles.containsAll(rolesString) && rolesString.containsAll(returnedRoles));

              JsonArray clients = result.getJsonArray(RESP_CLIENT_ARR);
              JsonObject defaultClient = clients.getJsonObject(0);
              assertTrue(clients.size() > 0);
              assertEquals(defaultClient.getString(RESP_CLIENT_ID), userJson.getString("clientId"));

              JsonObject org = result.getJsonObject(RESP_ORG);
              assertEquals(org.getString("url"), userJson.getString("url"));

              assertEquals(result.getString(RESP_EMAIL), userJson.getString("email"));
              assertEquals(result.getString(RESP_PHONE), userJson.getString("phone"));
              assertEquals(result.getString("userId"), userJson.getString("userId"));
              assertEquals(result.getString("keycloakId"), userJson.getString("keycloakId"));

              testContext.completeNow();
            })));
      });
    });
  }

  @Test
  @DisplayName("[Regen Client Secret] Successfully regen client secret")
  void clientRegenSuccess(VertxTestContext testContext) {

    List<Roles> roles = List.of(Roles.DELEGATE);
    Map<Roles, RoleStatus> cons = new HashMap<Roles, RoleStatus>();
    cons.put(Roles.DELEGATE, RoleStatus.APPROVED);

    Future<JsonObject> provDele =
        Utils.createFakeUser(pool, orgIdFut.result().toString(), url, cons, false);

    provDele.onSuccess(userJson -> {
      createdUsers.add(userJson);
      JsonObject req = new JsonObject().put("clientId", userJson.getString("clientId"));

      UpdateProfileRequest request = new UpdateProfileRequest(req);

      User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
          .userId(userJson.getString("userId")).roles(roles)
          .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

      Mockito.when(kc.getEmailId(any()))
          .thenReturn(Future.succeededFuture(userJson.getString("email")));

      Mockito.doAnswer(i -> {
        Promise<JsonObject> p = i.getArgument(2);
        p.complete(new JsonObject().put("type", URN_SUCCESS.toString()));
        return i.getMock();
      }).when(tokenService).revokeToken(any(), any(), any());

      registrationService.updateUser(request, user,
          testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(200, response.getInteger("status"));
            assertEquals(SUCC_TITLE_REGEN_CLIENT_SECRET, response.getString("title"));
            assertEquals(URN_SUCCESS.toString(), response.getString("type"));

            JsonObject result = response.getJsonObject("results");

            JsonObject name = result.getJsonObject("name");
            assertEquals(name.getString("firstName"), userJson.getString("firstName"));
            assertEquals(name.getString("lastName"), userJson.getString("lastName"));

            @SuppressWarnings("unchecked")
            List<String> returnedRoles = result.getJsonArray("roles").getList();
            List<String> rolesString = List.of(Roles.DELEGATE.name().toLowerCase());
            assertTrue(
                returnedRoles.containsAll(rolesString) && rolesString.containsAll(returnedRoles));

            JsonArray clients = result.getJsonArray(RESP_CLIENT_ARR);
            JsonObject defaultClient = clients.getJsonObject(0);
            assertTrue(clients.size() > 0);
            assertEquals(defaultClient.getString(RESP_CLIENT_ID), userJson.getString("clientId"));
            assertTrue(defaultClient.containsKey(RESP_CLIENT_SC));
            String newClientSec = defaultClient.getString(RESP_CLIENT_SC);
            assertTrue(newClientSec.matches(CLIENT_SECRET_REGEX));
            String oldClientSec = userJson.getString("clientSecret");
            assertFalse(oldClientSec.equals(newClientSec));

            JsonObject org = result.getJsonObject(RESP_ORG);
            assertEquals(org.getString("url"), userJson.getString("url"));

            assertEquals(result.getString(RESP_EMAIL), userJson.getString("email"));
            assertEquals(result.getString("userId"), userJson.getString("userId"));
            assertEquals(result.getString("keycloakId"), userJson.getString("keycloakId"));

            testContext.completeNow();
          })));
    });
  }

  @Test
  @DisplayName("[Regen Client Secret] Success when some token revoke to a server fails")
  void clientRegenSuccess2(VertxTestContext testContext) {

    List<Roles> roles = List.of(Roles.DELEGATE);
    Map<Roles, RoleStatus> cons = new HashMap<Roles, RoleStatus>();
    cons.put(Roles.DELEGATE, RoleStatus.APPROVED);

    Future<JsonObject> provDele =
        Utils.createFakeUser(pool, orgIdFut.result().toString(), url, cons, false);

    provDele.onSuccess(userJson -> {
      createdUsers.add(userJson);
      JsonObject req = new JsonObject().put("clientId", userJson.getString("clientId"));

      UpdateProfileRequest request = new UpdateProfileRequest(req);

      User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
          .userId(userJson.getString("userId")).roles(roles)
          .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

      Mockito.when(kc.getEmailId(any()))
          .thenReturn(Future.succeededFuture(userJson.getString("email")));

      Mockito.doAnswer(i -> {
        Promise<JsonObject> p = i.getArgument(2);
        RevokeToken r = i.getArgument(0);
        if (r.getRsUrl().equals(DUMMY_SERVER_2)) {
          p.complete(new JsonObject().put("type", URN_INVALID_INPUT.toString()));
        } else {
          p.complete(new JsonObject().put("type", URN_SUCCESS.toString()));
        }
        return i.getMock();
      }).when(tokenService).revokeToken(any(), any(), any());

      registrationService.updateUser(request, user,
          testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(200, response.getInteger("status"));
            assertEquals(SUCC_TITLE_REGEN_CLIENT_SECRET, response.getString("title"));
            assertEquals(URN_SUCCESS.toString(), response.getString("type"));

            JsonObject result = response.getJsonObject("results");

            JsonObject name = result.getJsonObject("name");
            assertEquals(name.getString("firstName"), userJson.getString("firstName"));
            assertEquals(name.getString("lastName"), userJson.getString("lastName"));

            @SuppressWarnings("unchecked")
            List<String> returnedRoles = result.getJsonArray("roles").getList();
            List<String> rolesString = List.of(Roles.DELEGATE.name().toLowerCase());
            assertTrue(
                returnedRoles.containsAll(rolesString) && rolesString.containsAll(returnedRoles));

            JsonArray clients = result.getJsonArray(RESP_CLIENT_ARR);
            JsonObject defaultClient = clients.getJsonObject(0);
            assertTrue(clients.size() > 0);
            assertEquals(defaultClient.getString(RESP_CLIENT_ID), userJson.getString("clientId"));
            assertTrue(defaultClient.containsKey(RESP_CLIENT_SC));
            String newClientSec = defaultClient.getString(RESP_CLIENT_SC);
            assertTrue(newClientSec.matches(CLIENT_SECRET_REGEX));
            String oldClientSec = userJson.getString("clientSecret");
            assertFalse(oldClientSec.equals(newClientSec));

            JsonObject org = result.getJsonObject(RESP_ORG);
            assertEquals(org.getString("url"), userJson.getString("url"));

            assertEquals(result.getString(RESP_EMAIL), userJson.getString("email"));
            assertEquals(result.getString("userId"), userJson.getString("userId"));
            assertEquals(result.getString("keycloakId"), userJson.getString("keycloakId"));

            testContext.completeNow();
          })));
    });
  }

  @Test
  @DisplayName("[Regen Client Secret] Fail when token revoke fails due to internal error")
  void clientRegenFail(VertxTestContext testContext) {

    List<Roles> roles = List.of(Roles.DELEGATE);
    Map<Roles, RoleStatus> cons = new HashMap<Roles, RoleStatus>();
    cons.put(Roles.DELEGATE, RoleStatus.APPROVED);

    Future<JsonObject> provDele =
        Utils.createFakeUser(pool, orgIdFut.result().toString(), url, cons, false);

    provDele.onSuccess(userJson -> {
      createdUsers.add(userJson);
      JsonObject req = new JsonObject().put("clientId", userJson.getString("clientId"));

      UpdateProfileRequest request = new UpdateProfileRequest(req);

      User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
          .userId(userJson.getString("userId")).roles(roles)
          .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

      Mockito.when(kc.getEmailId(any()))
          .thenReturn(Future.succeededFuture(userJson.getString("email")));

      /* Revocation to DUMMY_SERVER_2 fails with an internal error */
      Mockito.doAnswer(i -> {
        Promise<JsonObject> p = i.getArgument(2);
        RevokeToken r = i.getArgument(0);
        if (r.getRsUrl().equals(DUMMY_SERVER_2)) {
          p.fail("Internal error");
        } else {
          p.complete(new JsonObject().put("type", URN_SUCCESS.toString()));
        }
        return i.getMock();
      }).when(tokenService).revokeToken(any(), any(), any());

      registrationService.updateUser(request, user,
          testContext.failing(response -> testContext.verify(() -> testContext.completeNow())));
    });
  }

  @Test
  @DisplayName("[Regen Client Secret] Client ID not found")
  void clientSecretRegenClientIdNotFound(VertxTestContext testContext) {
    JsonObject req = new JsonObject().put("clientId", UUID.randomUUID().toString());

    UpdateProfileRequest request = new UpdateProfileRequest(req);

    List<Roles> roles = List.of(Roles.CONSUMER);
    Map<Roles, RoleStatus> cons = new HashMap<Roles, RoleStatus>();
    cons.put(Roles.CONSUMER, RoleStatus.APPROVED);

    Future<JsonObject> consumer = Utils.createFakeUser(pool, NIL_UUID, "", cons, false);

    consumer.onSuccess(userJson -> {
      createdUsers.add(userJson);
      
      Mockito.when(kc.getEmailId(any()))
          .thenReturn(Future.succeededFuture(userJson.getString("email")));

      User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
          .userId(userJson.getString("userId")).roles(roles)
          .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

      registrationService.updateUser(request, user,
          testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(404, response.getInteger("status"));
            assertEquals(ERR_TITLE_INVALID_CLI_ID, response.getString("title"));
            assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
            assertEquals(ERR_DETAIL_INVALID_CLI_ID, response.getString("detail"));
            testContext.completeNow();
          })));
    });
  }
}
