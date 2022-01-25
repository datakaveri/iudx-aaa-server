package iudx.aaa.server.registration;

import static iudx.aaa.server.apiserver.util.Urn.*;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_NO_USER_PROFILE;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_USER_NOT_KC;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_NO_USER_PROFILE;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_USER_NOT_KC;
import static iudx.aaa.server.registration.Constants.RESP_CLIENT_ARR;
import static iudx.aaa.server.registration.Constants.RESP_CLIENT_ID;
import static iudx.aaa.server.registration.Constants.RESP_EMAIL;
import static iudx.aaa.server.registration.Constants.RESP_ORG;
import static iudx.aaa.server.registration.Constants.RESP_PHONE;
import static iudx.aaa.server.registration.Constants.SUCC_TITLE_USER_READ;
import static iudx.aaa.server.registration.Utils.SQL_CREATE_ORG;
import static iudx.aaa.server.registration.Utils.SQL_DELETE_ORG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.RoleStatus;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;
import iudx.aaa.server.configuration.Configuration;
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
public class ListUserTest {
  private static Logger LOGGER = LogManager.getLogger(ListUserTest.class);

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
  private static final String UUID_REGEX =
      "^[0-9a-f]{8}\\b-[0-9a-f]{4}\\b-[0-9a-f]{4}\\b-[0-9a-f]{4}\\b-[0-9a-f]{12}$";

  static String name = RandomStringUtils.randomAlphabetic(10).toLowerCase();
  static String url = name + ".com";
  static Promise<UUID> orgId;

  static Future<JsonObject> userWithOrg;
  static Future<JsonObject> userNoOrg;
  static Future<UUID> orgIdFut;

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

      connectOptions = new PgConnectOptions().setPort(databasePort).setHost(databaseIP)
          .setDatabase(databaseName).setUser(databaseUserName).setPassword(databasePassword)
          .setProperties(schemaProp);
    }

    if (poolOptions == null) {
      poolOptions = new PoolOptions().setMaxSize(poolSize);
    }

    pool = PgPool.pool(vertx, connectOptions, poolOptions);

    /*
     * create fake organization, and create 2 mock users. One user has an organization + phone
     * number other does not
     */

    orgIdFut = pool.withConnection(conn -> conn.preparedQuery(SQL_CREATE_ORG)
        .execute(Tuple.of(name, url)).map(row -> row.iterator().next().getUUID("id")));

    Map<Roles, RoleStatus> rolesA = new HashMap<Roles, RoleStatus>();
    rolesA.put(Roles.DELEGATE, RoleStatus.APPROVED);
    rolesA.put(Roles.PROVIDER, RoleStatus.APPROVED);

    Map<Roles, RoleStatus> rolesB = new HashMap<Roles, RoleStatus>();
    rolesB.put(Roles.CONSUMER, RoleStatus.APPROVED);
    rolesB.put(Roles.ADMIN, RoleStatus.APPROVED);

    userWithOrg =
        orgIdFut.compose(id -> Utils.createFakeUser(pool, id.toString(), url, rolesA, true));
    userNoOrg = Utils.createFakeUser(pool, Constants.NIL_UUID, "", rolesB, false);

    CompositeFuture.all(userWithOrg, userNoOrg).onSuccess(res -> {
      registrationService = new RegistrationServiceImpl(pool, kc);
      testContext.completeNow();
    }).onFailure(err -> testContext.failNow(err.getMessage()));
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing and resetting DB");

    Utils.deleteFakeUser(pool, List.of(userNoOrg.result(), userWithOrg.result()))
        .compose(success -> pool.withConnection(
            conn -> conn.preparedQuery(SQL_DELETE_ORG).execute(Tuple.of(orgIdFut.result()))))
        .onComplete(x -> {
          if (x.failed()) {
            LOGGER.warn(x.cause().getMessage());
          }
          vertxObj.close(testContext.succeeding(response -> testContext.completeNow()));
        });
  }

  @Test
  @DisplayName("Test user not registered")
  void userDoesNotExist(VertxTestContext testContext) {

    JsonObject userJson = userWithOrg.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

    Mockito.when(kc.getEmailId(any()))
        .thenReturn(Future.succeededFuture(userJson.getString("email")));

    registrationService.listUser(user, new JsonObject(), new JsonObject(),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(404, response.getInteger("status"));
          assertEquals(ERR_TITLE_NO_USER_PROFILE, response.getString("title"));
          assertEquals(ERR_DETAIL_NO_USER_PROFILE, response.getString("detail"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test keycloak failure")
  void keycloakFailed(VertxTestContext testContext) {

    JsonObject userJson = userWithOrg.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

    Mockito.when(kc.getEmailId(any())).thenReturn(Future.failedFuture("fail"));

    registrationService.listUser(user, new JsonObject(), new JsonObject(),
        testContext.failing(response -> testContext.verify(() -> {
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test email not on keycloak - should never happen")
  void userEmailFail(VertxTestContext testContext) {

    JsonObject userJson = userWithOrg.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

    Mockito.when(kc.getEmailId(any())).thenReturn(Future.succeededFuture(""));

    registrationService.listUser(user, new JsonObject(), new JsonObject(),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(400, response.getInteger("status"));
          assertEquals(ERR_TITLE_USER_NOT_KC, response.getString("title"));
          assertEquals(ERR_DETAIL_USER_NOT_KC, response.getString("detail"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test list user with organization, phone and provider, delegate roles")
  void listWithOrg(VertxTestContext testContext) {

    JsonObject userJson = userWithOrg.result();
    List<Roles> roles = List.of(Roles.DELEGATE, Roles.PROVIDER);
    List<String> rolesString =
        List.of(Roles.DELEGATE.name().toLowerCase(), Roles.PROVIDER.name().toLowerCase());

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId")).roles(roles)
        .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

    Mockito.when(kc.getEmailId(any()))
        .thenReturn(Future.succeededFuture(userJson.getString("email")));

    registrationService.listUser(user, new JsonObject(), new JsonObject(),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(200, response.getInteger("status"));
          assertEquals(SUCC_TITLE_USER_READ, response.getString("title"));
          assertEquals(URN_SUCCESS.toString(), response.getString("type"));

          JsonObject result = response.getJsonObject("results");

          JsonObject name = result.getJsonObject("name");
          assertEquals(name.getString("firstName"), userJson.getString("firstName"));
          assertEquals(name.getString("lastName"), userJson.getString("lastName"));

          List<String> returnedRoles = result.getJsonArray("roles").getList();
          assertTrue(returnedRoles.containsAll(rolesString));

          JsonArray clients = result.getJsonArray(RESP_CLIENT_ARR);
          JsonObject defaultClient = clients.getJsonObject(0);
          assertTrue(clients.size() > 0);
          assertEquals(defaultClient.getString(RESP_CLIENT_ID), userJson.getString("clientId"));

          JsonObject org = result.getJsonObject(RESP_ORG);
          assertEquals(org.getString("url"), userJson.getString("url"));

          assertEquals(result.getString(RESP_PHONE), userJson.getString("phone"));
          assertEquals(result.getString(RESP_EMAIL), userJson.getString("email"));
          assertEquals(result.getString("userId"), userJson.getString("userId"));
          assertEquals(result.getString("keycloakId"), userJson.getString("keycloakId"));

          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test list user with no organization, no phone, consumer, admin role")
  void userConsumer(VertxTestContext testContext) {

    JsonObject userJson = userNoOrg.result();
    List<Roles> roles = List.of(Roles.CONSUMER, Roles.ADMIN);
    List<String> rolesString =
        List.of(Roles.CONSUMER.name().toLowerCase(), Roles.ADMIN.name().toLowerCase());

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId")).roles(roles)
        .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

    Mockito.when(kc.getEmailId(any()))
        .thenReturn(Future.succeededFuture(userJson.getString("email")));

    registrationService.listUser(user, new JsonObject(), new JsonObject(),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(200, response.getInteger("status"));
          assertEquals(SUCC_TITLE_USER_READ, response.getString("title"));
          assertEquals(URN_SUCCESS.toString(), response.getString("type"));

          JsonObject result = response.getJsonObject("results");

          JsonObject name = result.getJsonObject("name");
          assertEquals(name.getString("firstName"), userJson.getString("firstName"));
          assertEquals(name.getString("lastName"), userJson.getString("lastName"));

          List<String> returnedRoles = result.getJsonArray("roles").getList();
          assertTrue(returnedRoles.containsAll(rolesString));

          JsonArray clients = result.getJsonArray(RESP_CLIENT_ARR);
          JsonObject defaultClient = clients.getJsonObject(0);
          assertTrue(clients.size() > 0);
          assertEquals(defaultClient.getString(RESP_CLIENT_ID), userJson.getString("clientId"));

          assertFalse(result.containsKey(RESP_PHONE));
          assertFalse(result.containsKey(RESP_ORG));

          assertEquals(result.getString(RESP_EMAIL), userJson.getString("email"));
          assertEquals(result.getString("userId"), userJson.getString("userId"));
          assertEquals(result.getString("keycloakId"), userJson.getString("keycloakId"));

          testContext.completeNow();
        })));
  }


  @Test
  @DisplayName("Test list user with no approved roles e.g. pending provider")
  void listNoApprovedRoles(VertxTestContext testContext) {

    JsonObject userJson = userWithOrg.result();
    List<Roles> roles = List.of();

    /* Add empty list of roles when creating user object */
    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId")).roles(roles)
        .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

    Mockito.when(kc.getEmailId(any()))
        .thenReturn(Future.succeededFuture(userJson.getString("email")));

    registrationService.listUser(user, new JsonObject(), new JsonObject(),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(200, response.getInteger("status"));
          assertEquals(SUCC_TITLE_USER_READ, response.getString("title"));
          assertEquals(URN_SUCCESS.toString(), response.getString("type"));

          JsonObject result = response.getJsonObject("results");

          JsonObject name = result.getJsonObject("name");
          assertEquals(name.getString("firstName"), userJson.getString("firstName"));
          assertEquals(name.getString("lastName"), userJson.getString("lastName"));

          assertTrue(result.getJsonArray("roles").isEmpty());

          JsonArray clients = result.getJsonArray(RESP_CLIENT_ARR);
          JsonObject defaultClient = clients.getJsonObject(0);
          assertTrue(clients.size() > 0);
          assertEquals(defaultClient.getString(RESP_CLIENT_ID), userJson.getString("clientId"));

          JsonObject org = result.getJsonObject(RESP_ORG);
          assertEquals(org.getString("url"), userJson.getString("url"));

          assertEquals(result.getString(RESP_PHONE), userJson.getString("phone"));
          assertEquals(result.getString(RESP_EMAIL), userJson.getString("email"));
          assertEquals(result.getString("userId"), userJson.getString("userId"));
          assertEquals(result.getString("keycloakId"), userJson.getString("keycloakId"));

          testContext.completeNow();
        })));
  }
}
