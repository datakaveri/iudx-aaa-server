package iudx.aaa.server.apd;

import static iudx.aaa.server.apd.Constants.CONFIG_AUTH_URL;
import static iudx.aaa.server.apd.Constants.ERR_DETAIL_NO_ROLES_PUT;
import static iudx.aaa.server.apd.Constants.ERR_DETAIL_NO_USER_PROFILE;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_CANT_CHANGE_APD_STATUS;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_DUPLICATE_REQ;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_INVALID_APDID;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_NO_ROLES_PUT;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_NO_USER_PROFILE;
import static iudx.aaa.server.apd.Constants.RESP_APD_ID;
import static iudx.aaa.server.apd.Constants.RESP_APD_NAME;
import static iudx.aaa.server.apd.Constants.RESP_APD_STATUS;
import static iudx.aaa.server.apd.Constants.RESP_APD_URL;
import static iudx.aaa.server.apd.Constants.SUCC_TITLE_UPDATED_APD;
import static iudx.aaa.server.apiserver.util.Urn.*;
import static iudx.aaa.server.registration.Utils.SQL_CREATE_ADMIN_SERVER;
import static iudx.aaa.server.registration.Utils.SQL_CREATE_APD;
import static iudx.aaa.server.registration.Utils.SQL_DELETE_SERVERS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.ApdStatus;
import iudx.aaa.server.apiserver.ApdUpdateRequest;
import iudx.aaa.server.apiserver.RoleStatus;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.registration.RegistrationService;
import iudx.aaa.server.registration.Utils;
import iudx.aaa.server.token.TokenService;
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
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

@ExtendWith({VertxExtension.class})
@TestMethodOrder(OrderAnnotation.class)
public class UpdateApdTest {

  private static Logger LOGGER = LogManager.getLogger(UpdateApdTest.class);

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

  private static final String DUMMY_SERVER =
      "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";
  private static final String DUMMY_AUTH_SERVER =
      "auth" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + "iudx.io";

  private static final UUID ACTIVE_A_ID = UUID.randomUUID();
  private static final UUID INACTIVE_A_ID = UUID.randomUUID();

  private static final UUID ACTIVE_B_ID = UUID.randomUUID();
  private static final UUID INACTIVE_B_ID = UUID.randomUUID();

  private static final String ACTIVE_A = RandomStringUtils.randomAlphabetic(5).toLowerCase();
  private static final String INACTIVE_A = RandomStringUtils.randomAlphabetic(5).toLowerCase();

  private static final String ACTIVE_B = RandomStringUtils.randomAlphabetic(5).toLowerCase();
  private static final String INACTIVE_B = RandomStringUtils.randomAlphabetic(5).toLowerCase();

  private static Future<JsonObject> normalUser;
  private static Future<JsonObject> authAdmin;
  private static Future<JsonObject> otherAdmin;

  static String name = RandomStringUtils.randomAlphabetic(10).toLowerCase();
  static String url = name + ".com";
  static Future<UUID> orgIdFut;

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
          new PgConnectOptions().setPort(databasePort).setHost(databaseIP).setDatabase(databaseName)
              .setUser(databaseUserName).setPassword(databasePassword).setProperties(schemaProp);
    }

    /* Pool options */
    if (poolOptions == null) {
      poolOptions = new PoolOptions().setMaxSize(poolSize);
    }

    pool = PgPool.pool(vertx, connectOptions, poolOptions);

    /* Do not take test config, use generated config */
    JsonObject options = new JsonObject().put(CONFIG_AUTH_URL, DUMMY_AUTH_SERVER);

    orgIdFut = pool.withConnection(conn -> conn.preparedQuery(Utils.SQL_CREATE_ORG)
        .execute(Tuple.of(name, url)).map(row -> row.iterator().next().getUUID("id")));
    normalUser = orgIdFut.compose(orgId -> Utils.createFakeUser(pool, orgId.toString(), "",
        Map.of(Roles.CONSUMER, RoleStatus.APPROVED), false));
    authAdmin = orgIdFut.compose(orgId -> Utils.createFakeUser(pool, orgId.toString(), "",
        Map.of(Roles.ADMIN, RoleStatus.APPROVED), false));
    otherAdmin = orgIdFut.compose(orgId -> Utils.createFakeUser(pool, orgId.toString(), "",
        Map.of(Roles.ADMIN, RoleStatus.APPROVED), false));

    CompositeFuture.all(normalUser, authAdmin, otherAdmin).compose(succ -> {
      // create servers for admins
      JsonObject admin1 = authAdmin.result();
      UUID uid1 = UUID.fromString(admin1.getString("userId"));

      JsonObject admin2 = otherAdmin.result();
      UUID uid2 = UUID.fromString(admin2.getString("userId"));
      List<Tuple> tup = List.of(Tuple.of("Auth Server", uid1, DUMMY_AUTH_SERVER),
          Tuple.of("Other Server", uid2, DUMMY_SERVER));

      /*
       * To test the different APD states, we create 4 APDs. Slightly
       * different from other tests, we also create the UUID APD IDs and insert into the DB instead
       * of relying on the auto-create in DB
       */

      List<Tuple> apdTup = List.of(
          Tuple.of(ACTIVE_A_ID, ACTIVE_A, ACTIVE_A + ".com", ApdStatus.ACTIVE),
          Tuple.of(INACTIVE_A_ID, INACTIVE_A, INACTIVE_A + ".com", ApdStatus.INACTIVE),
          Tuple.of(ACTIVE_B_ID, ACTIVE_B, ACTIVE_B + ".com", ApdStatus.ACTIVE),
          Tuple.of(INACTIVE_B_ID, INACTIVE_B, INACTIVE_B + ".com", ApdStatus.INACTIVE));

      return pool.withConnection(conn -> conn.preparedQuery(SQL_CREATE_ADMIN_SERVER)
          .executeBatch(tup).compose(x -> conn.preparedQuery(SQL_CREATE_APD).executeBatch(apdTup)));
    }).onSuccess(x -> {
      apdService = new ApdServiceImpl(pool, apdWebClient, registrationService, tokenService, options);
      testContext.completeNow();
    });
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing....");
    Tuple servers = Tuple.of(List.of(DUMMY_AUTH_SERVER, DUMMY_SERVER).toArray());
    List<JsonObject> users =
        List.of(normalUser.result(), otherAdmin.result(), authAdmin.result());

    pool.withConnection(conn -> conn.preparedQuery(SQL_DELETE_SERVERS).execute(servers)
        .compose(success -> Utils.deleteFakeUser(pool, users)).compose(
            succ -> conn.preparedQuery(Utils.SQL_DELETE_ORG).execute(Tuple.of(orgIdFut.result()))))
        .onComplete(x -> {
          if (x.failed()) {
            LOGGER.warn(x.cause().getMessage());
          }
          vertxObj.close(testContext.succeeding(response -> testContext.completeNow()));
        });
  }

  /*
   * We make use of ordering for these tests since there are only 4 APDs created. Some tests for
   * 400s and 403s that fail due to APD status at that time may succeed if the test for 200 runs
   * first (which would change the expected status).
   */

  @Order(1)
  @Test
  @DisplayName("Test no user profile")
  void noUserProfile(VertxTestContext testContext) {
    JsonObject userJson = authAdmin.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.ADMIN)).build();

    apdService.updateApd(List.of(), user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getInteger("status"), 404);
          assertEquals(URN_MISSING_INFO.toString(), response.getString("type"));
          assertEquals(ERR_TITLE_NO_USER_PROFILE, response.getString("title"));
          assertEquals(ERR_DETAIL_NO_USER_PROFILE, response.getString("detail"));
          testContext.completeNow();
        })));
  }

  @Order(2)
  @Test
  @DisplayName("Test invalid roles")
  void invalidRoles(VertxTestContext testContext) {
    JsonObject userJson = normalUser.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.CONSUMER, Roles.PROVIDER, Roles.DELEGATE)).build();

    apdService.updateApd(List.of(), user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getInteger("status"), 403);
          assertEquals(URN_INVALID_ROLE.toString(), response.getString("type"));
          assertEquals(ERR_TITLE_NO_ROLES_PUT, response.getString("title"));
          assertEquals(ERR_DETAIL_NO_ROLES_PUT, response.getString("detail"));
          testContext.completeNow();
        })));
  }

  @Order(3)
  @Test
  @DisplayName("Test not auth admin")
  void notAuthAdmin(VertxTestContext testContext) {
    JsonObject userJson = otherAdmin.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.ADMIN)).build();

    apdService.updateApd(List.of(), user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getInteger("status"), 403);
          assertEquals(URN_INVALID_ROLE.toString(), response.getString("type"));
          assertEquals(ERR_TITLE_NO_ROLES_PUT, response.getString("title"));
          assertEquals(ERR_DETAIL_NO_ROLES_PUT, response.getString("detail"));
          testContext.completeNow();
        })));
  }

  @Order(4)
  @Test
  @DisplayName("Test non-existent apd IDs")
  void nonExistentApdId(VertxTestContext testContext) {
    JsonObject userJson = authAdmin.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.ADMIN)).build();

    String randUuid = UUID.randomUUID().toString();
    JsonArray req = new JsonArray()
        .add(new JsonObject().put("apdId", ACTIVE_A_ID.toString()).put("status", "inactive"))
        .add(new JsonObject().put("apdId", randUuid).put("status", "active"));
    List<ApdUpdateRequest> request = ApdUpdateRequest.jsonArrayToList(req);

    apdService.updateApd(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getInteger("status"), 400);
          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
          assertEquals(ERR_TITLE_INVALID_APDID, response.getString("title"));
          assertEquals(randUuid, response.getString("detail"));
          testContext.completeNow();
        })));
  }

  @Order(5)
  @Test
  @DisplayName("Test duplicate apd IDs")
  void duplicateApdIds(VertxTestContext testContext) {
    JsonObject userJson = authAdmin.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.ADMIN)).build();

    JsonArray req = new JsonArray()
        .add(new JsonObject().put("apdId", INACTIVE_A_ID.toString()).put("status", "active"))
        .add(new JsonObject().put("apdId", INACTIVE_A_ID.toString()).put("status", "inactive"));
    List<ApdUpdateRequest> request = ApdUpdateRequest.jsonArrayToList(req);

    apdService.updateApd(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getInteger("status"), 400);
          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
          assertEquals(ERR_TITLE_DUPLICATE_REQ, response.getString("title"));
          assertEquals(INACTIVE_A_ID.toString(), response.getString("detail"));
          testContext.completeNow();
        })));
  }

  @Order(6)
  @Test
  @DisplayName("Test admin changing to existing state")
  void existingStateAdmin(VertxTestContext testContext) {
    JsonObject userJson = authAdmin.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.ADMIN)).build();

    JsonArray req = new JsonArray()
        .add(new JsonObject().put("apdId", ACTIVE_B_ID.toString()).put("status", "active"))
        .add(new JsonObject().put("apdId", INACTIVE_B_ID.toString()).put("status", "active"));
    List<ApdUpdateRequest> request = ApdUpdateRequest.jsonArrayToList(req);

    apdService.updateApd(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getInteger("status"), 403);
          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
          assertEquals(ERR_TITLE_CANT_CHANGE_APD_STATUS, response.getString("title"));
          assertEquals(ACTIVE_B_ID.toString(), response.getString("detail"));
          testContext.completeNow();
        })));
  }

  @Order(7)
  @Test
  @DisplayName("Test invalid state change for admin")
  void invalidStateAdmin(VertxTestContext testContext) {
    JsonObject userJson = authAdmin.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.ADMIN)).build();

    JsonArray req = new JsonArray()
        .add(new JsonObject().put("apdId", ACTIVE_B_ID.toString()).put("status", "inactive"))
        .add(new JsonObject().put("apdId", INACTIVE_B_ID.toString()).put("status", "inactive"));
    List<ApdUpdateRequest> request = ApdUpdateRequest.jsonArrayToList(req);

    apdService.updateApd(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getInteger("status"), 403);
          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
          assertEquals(ERR_TITLE_CANT_CHANGE_APD_STATUS, response.getString("title"));
          assertEquals(INACTIVE_B_ID.toString(), response.getString("detail"));
          testContext.completeNow();
        })));
  }

  @Order(8)
  @Test
  @DisplayName("Test admin changes trusteeA inactive -> active")
  void adminInactiveToActive(VertxTestContext testContext) {
    JsonObject userJson = authAdmin.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.ADMIN)).build();

    JsonArray req = new JsonArray()
        .add(new JsonObject().put("apdId", INACTIVE_A_ID.toString()).put("status", "active"));
    List<ApdUpdateRequest> request = ApdUpdateRequest.jsonArrayToList(req);

    apdService.updateApd(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getInteger("status"), 200);
          assertEquals(URN_SUCCESS.toString(), response.getString("type"));
          assertEquals(SUCC_TITLE_UPDATED_APD, response.getString("title"));
          JsonObject result = response.getJsonArray("results").getJsonObject(0);
          assertEquals(INACTIVE_A, result.getString(RESP_APD_NAME));
          assertEquals(INACTIVE_A + ".com", result.getString(RESP_APD_URL));
          assertEquals("active", result.getString(RESP_APD_STATUS));
          assertTrue(result.containsKey(RESP_APD_ID));

          testContext.completeNow();
        })));
  }

  @Order(9)
  @Test
  @DisplayName("Multiple requests - Test success admin setting active -> inactive, inactive -> active")
  void mutipleReqSuccess(VertxTestContext testContext) {
    JsonObject userJson = authAdmin.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.ADMIN)).build();

    JsonArray req = new JsonArray()
        .add(new JsonObject().put("apdId", ACTIVE_A_ID.toString()).put("status", "inactive"))
        .add(new JsonObject().put("apdId", INACTIVE_B_ID.toString()).put("status", "active"));
    List<ApdUpdateRequest> request = ApdUpdateRequest.jsonArrayToList(req);

    apdService.updateApd(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {

          assertEquals(response.getInteger("status"), 200);
          assertEquals(URN_SUCCESS.toString(), response.getString("type"));
          assertEquals(SUCC_TITLE_UPDATED_APD, response.getString("title"));

          JsonObject resultA = response.getJsonArray("results").getJsonObject(0);
          JsonObject resultB = response.getJsonArray("results").getJsonObject(1);

          if (!resultA.getString(RESP_APD_NAME).equals(ACTIVE_A)) {
            resultA = response.getJsonArray("results").getJsonObject(1);
            resultB = response.getJsonArray("results").getJsonObject(0);
          }

          assertEquals(ACTIVE_A, resultA.getString(RESP_APD_NAME));
          assertEquals(ACTIVE_A + ".com", resultA.getString(RESP_APD_URL));
          assertEquals("inactive", resultA.getString(RESP_APD_STATUS));
          assertTrue(resultA.containsKey(RESP_APD_ID));

          assertEquals(INACTIVE_B, resultB.getString(RESP_APD_NAME));
          assertEquals(INACTIVE_B + ".com", resultB.getString(RESP_APD_URL));
          assertEquals("active", resultB.getString(RESP_APD_STATUS));
          assertTrue(resultB.containsKey(RESP_APD_ID));

          testContext.completeNow();
        })));
  }

}
