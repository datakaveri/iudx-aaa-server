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
import static iudx.aaa.server.apd.Constants.RESP_APD_OWNER;
import static iudx.aaa.server.apd.Constants.RESP_APD_STATUS;
import static iudx.aaa.server.apd.Constants.RESP_APD_URL;
import static iudx.aaa.server.apd.Constants.RESP_OWNER_USER_ID;
import static iudx.aaa.server.apd.Constants.SUCC_TITLE_UPDATED_APD;
import static iudx.aaa.server.apiserver.util.Urn.*;
import static iudx.aaa.server.registration.Utils.SQL_CREATE_ADMIN_SERVER;
import static iudx.aaa.server.registration.Utils.SQL_CREATE_APD;
import static iudx.aaa.server.registration.Utils.SQL_DELETE_SERVERS;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import iudx.aaa.server.apiserver.ApdStatus;
import iudx.aaa.server.apiserver.ApdUpdateRequest;
import iudx.aaa.server.apiserver.CreatePolicyRequest;
import iudx.aaa.server.apiserver.RoleStatus;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.policy.PolicyService;
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
  private static PolicyService policyService = Mockito.mock(PolicyService.class);
  private static TokenService tokenService = Mockito.mock(TokenService.class);

  private static final String DUMMY_SERVER =
      "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";
  private static final String DUMMY_AUTH_SERVER =
      "auth" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + "iudx.io";

  private static final UUID PENDING_A_ID = UUID.randomUUID();
  private static final UUID ACTIVE_A_ID = UUID.randomUUID();
  private static final UUID INACTIVE_A_ID = UUID.randomUUID();

  private static final UUID PENDING_B_ID = UUID.randomUUID();
  private static final UUID ACTIVE_B_ID = UUID.randomUUID();
  private static final UUID INACTIVE_B_ID = UUID.randomUUID();

  private static final String PENDING_A = RandomStringUtils.randomAlphabetic(5).toLowerCase();
  private static final String ACTIVE_A = RandomStringUtils.randomAlphabetic(5).toLowerCase();
  private static final String INACTIVE_A = RandomStringUtils.randomAlphabetic(5).toLowerCase();

  private static final String PENDING_B = RandomStringUtils.randomAlphabetic(5).toLowerCase();
  private static final String ACTIVE_B = RandomStringUtils.randomAlphabetic(5).toLowerCase();
  private static final String INACTIVE_B = RandomStringUtils.randomAlphabetic(5).toLowerCase();

  private static Future<JsonObject> trusteeUserA;
  private static Future<JsonObject> trusteeUserB;
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
    trusteeUserA = orgIdFut.compose(orgId -> Utils.createFakeUser(pool, orgId.toString(), "",
        Map.of(Roles.TRUSTEE, RoleStatus.APPROVED), false));
    trusteeUserB = orgIdFut.compose(orgId -> Utils.createFakeUser(pool, orgId.toString(), "",
        Map.of(Roles.TRUSTEE, RoleStatus.APPROVED), false));
    authAdmin = orgIdFut.compose(orgId -> Utils.createFakeUser(pool, orgId.toString(), "",
        Map.of(Roles.ADMIN, RoleStatus.APPROVED), false));
    otherAdmin = orgIdFut.compose(orgId -> Utils.createFakeUser(pool, orgId.toString(), "",
        Map.of(Roles.ADMIN, RoleStatus.APPROVED), false));

    CompositeFuture.all(trusteeUserA, trusteeUserB, authAdmin, otherAdmin).compose(succ -> {
      // create servers for admins
      JsonObject admin1 = authAdmin.result();
      UUID uid1 = UUID.fromString(admin1.getString("userId"));

      JsonObject admin2 = otherAdmin.result();
      UUID uid2 = UUID.fromString(admin2.getString("userId"));
      List<Tuple> tup = List.of(Tuple.of("Auth Server", uid1, DUMMY_AUTH_SERVER),
          Tuple.of("Other Server", uid2, DUMMY_SERVER));

      /*
       * To test the different APD states, we create 3 APDs each for the 2 trustees. Slightly
       * different from other tests, we also create the UUID APD IDs and insert into the DB instead
       * of relying on the auto-create in DB
       */
      UUID trusteeIdA = UUID.fromString(trusteeUserA.result().getString("userId"));
      UUID trusteeIdB = UUID.fromString(trusteeUserB.result().getString("userId"));

      List<Tuple> apdTup = List.of(
          Tuple.of(PENDING_A_ID, PENDING_A, PENDING_A + ".com", trusteeIdA, ApdStatus.PENDING),
          Tuple.of(ACTIVE_A_ID, ACTIVE_A, ACTIVE_A + ".com", trusteeIdA, ApdStatus.ACTIVE),
          Tuple.of(INACTIVE_A_ID, INACTIVE_A, INACTIVE_A + ".com", trusteeIdA, ApdStatus.INACTIVE),
          Tuple.of(PENDING_B_ID, PENDING_B, PENDING_B + ".com", trusteeIdB, ApdStatus.PENDING),
          Tuple.of(ACTIVE_B_ID, ACTIVE_B, ACTIVE_B + ".com", trusteeIdB, ApdStatus.ACTIVE),
          Tuple.of(INACTIVE_B_ID, INACTIVE_B, INACTIVE_B + ".com", trusteeIdB, ApdStatus.INACTIVE));

      return pool.withConnection(conn -> conn.preparedQuery(SQL_CREATE_ADMIN_SERVER)
          .executeBatch(tup).compose(x -> conn.preparedQuery(SQL_CREATE_APD).executeBatch(apdTup)));
    }).onSuccess(x -> {
      apdService = new ApdServiceImpl(pool, apdWebClient, registrationService, policyService,
          tokenService, options);
      testContext.completeNow();
    });
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing....");
    Tuple servers = Tuple.of(List.of(DUMMY_AUTH_SERVER, DUMMY_SERVER).toArray());
    List<JsonObject> users = List.of(trusteeUserA.result(), otherAdmin.result(),
        trusteeUserB.result(), authAdmin.result());

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
   * We make use of ordering for these tests since there are only 6 APDs created. Some tests for
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
    JsonObject userJson = authAdmin.result();

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
    JsonObject userJson = trusteeUserA.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.TRUSTEE)).build();

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
    JsonObject userJson = trusteeUserA.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.TRUSTEE)).build();

    JsonArray req = new JsonArray()
        .add(new JsonObject().put("apdId", PENDING_A_ID.toString()).put("status", "pending"))
        .add(new JsonObject().put("apdId", PENDING_A_ID.toString()).put("status", "active"));
    List<ApdUpdateRequest> request = ApdUpdateRequest.jsonArrayToList(req);

    apdService.updateApd(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getInteger("status"), 400);
          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
          assertEquals(ERR_TITLE_DUPLICATE_REQ, response.getString("title"));
          assertEquals(PENDING_A_ID.toString(), response.getString("detail"));
          testContext.completeNow();
        })));
  }

  @Order(6)
  @Test
  @DisplayName("Test trusteeB trying to change status of APD owned by trusteeA")
  void wrongTrustee(VertxTestContext testContext) {
    JsonObject userJson = trusteeUserB.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.TRUSTEE)).build();

    JsonArray req = new JsonArray()
        .add(new JsonObject().put("apdId", ACTIVE_B_ID.toString()).put("status", "inactive"))
        .add(new JsonObject().put("apdId", INACTIVE_A_ID.toString()).put("status", "pending"));
    List<ApdUpdateRequest> request = ApdUpdateRequest.jsonArrayToList(req);

    apdService.updateApd(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getInteger("status"), 400);
          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
          assertEquals(ERR_TITLE_INVALID_APDID, response.getString("title"));
          assertEquals(INACTIVE_A_ID.toString(), response.getString("detail"));
          testContext.completeNow();
        })));
  }

  @Order(7)
  @Test
  @DisplayName("Test trustee changing to existing state")
  void existingStateTrustee(VertxTestContext testContext) {
    JsonObject userJson = trusteeUserB.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.TRUSTEE)).build();

    JsonArray req = new JsonArray()
        .add(new JsonObject().put("apdId", PENDING_B_ID.toString()).put("status", "pending"))
        .add(new JsonObject().put("apdId", INACTIVE_B_ID.toString()).put("status", "pending"));
    List<ApdUpdateRequest> request = ApdUpdateRequest.jsonArrayToList(req);

    apdService.updateApd(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getInteger("status"), 403);
          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
          assertEquals(ERR_TITLE_CANT_CHANGE_APD_STATUS, response.getString("title"));
          assertEquals(PENDING_B_ID.toString(), response.getString("detail"));
          testContext.completeNow();
        })));
  }

  @Order(8)
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
        .add(new JsonObject().put("apdId", INACTIVE_B_ID.toString()).put("status", "pending"));
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

  @Order(9)
  @Test
  @DisplayName("Test trusteeA changing active -> inactive")
  void trusteeActiveToInactive(VertxTestContext testContext) {
    JsonObject userJson = trusteeUserA.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.TRUSTEE)).build();

    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(1);
      JsonObject userDets = new JsonObject().put("email", userJson.getString("email")).put("name",
          new JsonObject().put("firstName", userJson.getString("firstName")).put("lastName",
              userJson.getString("lastName")));
      p.complete(new JsonObject().put(userJson.getString("userId"), userDets));
      return i.getMock();
    }).when(registrationService).getUserDetails(any(), any());

    JsonArray req = new JsonArray()
        .add(new JsonObject().put("apdId", ACTIVE_A_ID.toString()).put("status", "inactive"));
    List<ApdUpdateRequest> request = ApdUpdateRequest.jsonArrayToList(req);

    apdService.updateApd(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getInteger("status"), 200);
          assertEquals(URN_SUCCESS.toString(), response.getString("type"));
          assertEquals(SUCC_TITLE_UPDATED_APD, response.getString("title"));
          JsonObject result = response.getJsonArray("results").getJsonObject(0);
          assertEquals(ACTIVE_A, result.getString(RESP_APD_NAME));
          assertEquals(ACTIVE_A + ".com", result.getString(RESP_APD_URL));
          assertEquals("inactive", result.getString(RESP_APD_STATUS));
          assertTrue(result.containsKey(RESP_APD_ID));
          assertTrue(result.containsKey(RESP_APD_OWNER));

          JsonObject ownerDets = result.getJsonObject(RESP_APD_OWNER);
          assertEquals(userJson.getString("userId"), ownerDets.getString(RESP_OWNER_USER_ID));
          assertEquals(userJson.getString("firstName"),
              ownerDets.getJsonObject("name").getString("firstName"));
          assertEquals(userJson.getString("lastName"),
              ownerDets.getJsonObject("name").getString("lastName"));
          assertEquals(userJson.getString("email"), ownerDets.getString("email"));
          testContext.completeNow();
        })));
  }

  @Order(10)
  @Test
  @DisplayName("Test trusteeB changing inactive -> pending")
  void trusteeInactiveToPending(VertxTestContext testContext) {
    JsonObject userJson = trusteeUserB.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.TRUSTEE)).build();

    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(1);
      JsonObject userDets = new JsonObject().put("email", userJson.getString("email")).put("name",
          new JsonObject().put("firstName", userJson.getString("firstName")).put("lastName",
              userJson.getString("lastName")));
      p.complete(new JsonObject().put(userJson.getString("userId"), userDets));
      return i.getMock();
    }).when(registrationService).getUserDetails(any(), any());

    JsonArray req = new JsonArray()
        .add(new JsonObject().put("apdId", INACTIVE_B_ID.toString()).put("status", "pending"));
    List<ApdUpdateRequest> request = ApdUpdateRequest.jsonArrayToList(req);

    apdService.updateApd(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getInteger("status"), 200);
          assertEquals(URN_SUCCESS.toString(), response.getString("type"));
          assertEquals(SUCC_TITLE_UPDATED_APD, response.getString("title"));
          JsonObject result = response.getJsonArray("results").getJsonObject(0);
          assertEquals(INACTIVE_B, result.getString(RESP_APD_NAME));
          assertEquals(INACTIVE_B + ".com", result.getString(RESP_APD_URL));
          assertEquals("pending", result.getString(RESP_APD_STATUS));
          assertTrue(result.containsKey(RESP_APD_ID));
          assertTrue(result.containsKey(RESP_APD_OWNER));

          JsonObject ownerDets = result.getJsonObject(RESP_APD_OWNER);
          assertEquals(userJson.getString("userId"), ownerDets.getString(RESP_OWNER_USER_ID));
          assertEquals(userJson.getString("firstName"),
              ownerDets.getJsonObject("name").getString("firstName"));
          assertEquals(userJson.getString("lastName"),
              ownerDets.getJsonObject("name").getString("lastName"));
          assertEquals(userJson.getString("email"), ownerDets.getString("email"));
          testContext.completeNow();
        })));
  }

  @Order(11)
  @Test
  @DisplayName("Test admin changes trusteeA inactive -> active")
  void adminInactiveToActive(VertxTestContext testContext) {
    JsonObject userJson = authAdmin.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.ADMIN)).build();

    JsonObject trusteeDetails = trusteeUserA.result();

    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(1);
      JsonObject userDets = new JsonObject().put("email", trusteeDetails.getString("email"))
          .put("name", new JsonObject().put("firstName", trusteeDetails.getString("firstName"))
              .put("lastName", trusteeDetails.getString("lastName")));
      p.complete(new JsonObject().put(trusteeDetails.getString("userId"), userDets));
      return i.getMock();
    }).when(registrationService).getUserDetails(any(), any());

    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(3);
      p.complete(new JsonObject().put("type", URN_SUCCESS.toString()));
      return i.getMock();
    }).when(policyService).createPolicy(any(), any(), any(), any());

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
          assertTrue(result.containsKey(RESP_APD_OWNER));

          JsonObject ownerDets = result.getJsonObject(RESP_APD_OWNER);
          assertEquals(trusteeDetails.getString("userId"), ownerDets.getString(RESP_OWNER_USER_ID));
          assertEquals(trusteeDetails.getString("firstName"),
              ownerDets.getJsonObject("name").getString("firstName"));
          assertEquals(trusteeDetails.getString("lastName"),
              ownerDets.getJsonObject("name").getString("lastName"));
          assertEquals(trusteeDetails.getString("email"), ownerDets.getString("email"));
          testContext.completeNow();
        })));
  }

  @Order(12)
  @Test
  @DisplayName("Multiple requests - test failing policy service and transaction rollback")
  void polServiceFailing(VertxTestContext testContext) {
    JsonObject userJson = authAdmin.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.ADMIN)).build();

    JsonObject trusteeAdets = trusteeUserA.result();
    JsonObject trusteeBdets = trusteeUserB.result();

    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(3);
      @SuppressWarnings("unchecked")
      CreatePolicyRequest obj = ((List<CreatePolicyRequest>) i.getArgument(0)).get(0);

      if (obj.getUserId().equals(trusteeAdets.getString("userId"))) {
        p.complete(new JsonObject().put("type", URN_SUCCESS.toString()));
      } else if (obj.getUserId().equals(trusteeBdets.getString("userId"))) {
        p.fail("Fail");
      }
      return i.getMock();
    }).when(policyService).createPolicy(any(), any(), any(), any());

    JsonArray req = new JsonArray()
        .add(new JsonObject().put("apdId", PENDING_A_ID.toString()).put("status", "active"))
        .add(new JsonObject().put("apdId", PENDING_B_ID.toString()).put("status", "active"));
    List<ApdUpdateRequest> request = ApdUpdateRequest.jsonArrayToList(req);

    apdService.updateApd(request, user, testContext.failing(response -> testContext.verify(() -> {
      testContext.completeNow();
    })));
  }

  @Order(13)
  @Test
  @DisplayName("Multiple requests - Test not recognized URN sent by policy service and transaction rollback")
  void polServiceBadUrn(VertxTestContext testContext) {
    JsonObject userJson = authAdmin.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.ADMIN)).build();

    JsonObject trusteeAdets = trusteeUserA.result();
    JsonObject trusteeBdets = trusteeUserB.result();

    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(3);
      @SuppressWarnings("unchecked")
      CreatePolicyRequest obj = ((List<CreatePolicyRequest>) i.getArgument(0)).get(0);

      if (obj.getUserId().equals(trusteeAdets.getString("userId"))) {
        p.complete(new JsonObject().put("type", URN_SUCCESS.toString()));
      } else if (obj.getUserId().equals(trusteeBdets.getString("userId"))) {
        p.complete(new JsonObject().put("type", URN_INVALID_INPUT.toString()));
      }
      return i.getMock();
    }).when(policyService).createPolicy(any(), any(), any(), any());

    JsonArray req = new JsonArray()
        .add(new JsonObject().put("apdId", PENDING_A_ID.toString()).put("status", "active"))
        .add(new JsonObject().put("apdId", PENDING_B_ID.toString()).put("status", "active"));
    List<ApdUpdateRequest> request = ApdUpdateRequest.jsonArrayToList(req);

    apdService.updateApd(request, user, testContext.failing(response -> testContext.verify(() -> {
      testContext.completeNow();
    })));
  }

  @Order(14)
  @Test
  @DisplayName("Multiple requests - Test registration service fail and transaction rollback")
  void regServiceFail(VertxTestContext testContext) {
    JsonObject userJson = authAdmin.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.ADMIN)).build();

    JsonObject trusteeAdets = trusteeUserA.result();
    JsonObject trusteeBdets = trusteeUserB.result();

    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(3);
      @SuppressWarnings("unchecked")
      CreatePolicyRequest obj = ((List<CreatePolicyRequest>) i.getArgument(0)).get(0);

      if (obj.getUserId().equals(trusteeAdets.getString("userId"))) {
        p.complete(new JsonObject().put("type", URN_SUCCESS.toString()));
      } else if (obj.getUserId().equals(trusteeBdets.getString("userId"))) {
        p.complete(new JsonObject().put("type", URN_ALREADY_EXISTS.toString()));
      }
      return i.getMock();
    }).when(policyService).createPolicy(any(), any(), any(), any());

    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(1);
      p.fail("Fail");
      return i.getMock();
    }).when(registrationService).getUserDetails(any(), any());

    JsonArray req = new JsonArray()
        .add(new JsonObject().put("apdId", PENDING_A_ID.toString()).put("status", "active"))
        .add(new JsonObject().put("apdId", PENDING_B_ID.toString()).put("status", "active"));
    List<ApdUpdateRequest> request = ApdUpdateRequest.jsonArrayToList(req);

    apdService.updateApd(request, user, testContext.failing(response -> testContext.verify(() -> {
      testContext.completeNow();
    })));
  }

  @Order(15)
  @Test
  @DisplayName("Multiple requests - Test success admin setting pending -> active")
  void mutipleReqSuccess(VertxTestContext testContext) {
    JsonObject userJson = authAdmin.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.ADMIN)).build();

    JsonObject trusteeAdets = trusteeUserA.result();
    JsonObject trusteeBdets = trusteeUserB.result();

    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(3);
      @SuppressWarnings("unchecked")
      CreatePolicyRequest obj = ((List<CreatePolicyRequest>) i.getArgument(0)).get(0);

      if (obj.getUserId().equals(trusteeAdets.getString("userId"))) {
        p.complete(new JsonObject().put("type", URN_SUCCESS.toString()));
      } else if (obj.getUserId().equals(trusteeBdets.getString("userId"))) {
        p.complete(new JsonObject().put("type", URN_ALREADY_EXISTS.toString()));
      }
      return i.getMock();
    }).when(policyService).createPolicy(any(), any(), any(), any());

    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(1);
      JsonObject trusteeA = new JsonObject().put("email", trusteeAdets.getString("email"))
          .put("name", new JsonObject().put("firstName", trusteeAdets.getString("firstName"))
              .put("lastName", trusteeAdets.getString("lastName")));

      JsonObject trusteeB = new JsonObject().put("email", trusteeBdets.getString("email"))
          .put("name", new JsonObject().put("firstName", trusteeBdets.getString("firstName"))
              .put("lastName", trusteeBdets.getString("lastName")));

      p.complete(new JsonObject().put(trusteeAdets.getString("userId"), trusteeA)
          .put(trusteeBdets.getString("userId"), trusteeB));
      return i.getMock();
    }).when(registrationService).getUserDetails(any(), any());

    JsonArray req = new JsonArray()
        .add(new JsonObject().put("apdId", PENDING_A_ID.toString()).put("status", "active"))
        .add(new JsonObject().put("apdId", PENDING_B_ID.toString()).put("status", "active"));
    List<ApdUpdateRequest> request = ApdUpdateRequest.jsonArrayToList(req);

    apdService.updateApd(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {

          assertEquals(response.getInteger("status"), 200);
          assertEquals(URN_SUCCESS.toString(), response.getString("type"));
          assertEquals(SUCC_TITLE_UPDATED_APD, response.getString("title"));

          JsonObject resultA = response.getJsonArray("results").getJsonObject(0);
          JsonObject resultB = response.getJsonArray("results").getJsonObject(1);

          if (!resultA.getString(RESP_APD_NAME).equals(PENDING_A)) {
            resultA = response.getJsonArray("results").getJsonObject(1);
            resultB = response.getJsonArray("results").getJsonObject(0);
          }

          assertEquals(PENDING_A, resultA.getString(RESP_APD_NAME));
          assertEquals(PENDING_A + ".com", resultA.getString(RESP_APD_URL));
          assertEquals("active", resultA.getString(RESP_APD_STATUS));
          assertTrue(resultA.containsKey(RESP_APD_ID));
          assertTrue(resultA.containsKey(RESP_APD_OWNER));

          JsonObject ownerDetsA = resultA.getJsonObject(RESP_APD_OWNER);
          assertEquals(trusteeAdets.getString("userId"), ownerDetsA.getString(RESP_OWNER_USER_ID));
          assertEquals(trusteeAdets.getString("firstName"),
              ownerDetsA.getJsonObject("name").getString("firstName"));
          assertEquals(trusteeAdets.getString("lastName"),
              ownerDetsA.getJsonObject("name").getString("lastName"));
          assertEquals(trusteeAdets.getString("email"), ownerDetsA.getString("email"));

          assertEquals(PENDING_B, resultB.getString(RESP_APD_NAME));
          assertEquals(PENDING_B + ".com", resultB.getString(RESP_APD_URL));
          assertEquals("active", resultB.getString(RESP_APD_STATUS));
          assertTrue(resultB.containsKey(RESP_APD_ID));
          assertTrue(resultB.containsKey(RESP_APD_OWNER));

          JsonObject ownerDetsB = resultB.getJsonObject(RESP_APD_OWNER);
          assertEquals(trusteeBdets.getString("userId"), ownerDetsB.getString(RESP_OWNER_USER_ID));
          assertEquals(trusteeBdets.getString("firstName"),
              ownerDetsB.getJsonObject("name").getString("firstName"));
          assertEquals(trusteeBdets.getString("lastName"),
              ownerDetsB.getJsonObject("name").getString("lastName"));
          assertEquals(trusteeBdets.getString("email"), ownerDetsB.getString("email"));

          testContext.completeNow();
        })));
  }

  @Order(16)
  @Test
  @DisplayName("Test admin changes trusteeB active -> inactive")
  void adminActiveToInactive(VertxTestContext testContext) {
    JsonObject userJson = authAdmin.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.ADMIN)).build();

    JsonObject trusteeDetails = trusteeUserB.result();

    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(1);
      JsonObject userDets = new JsonObject().put("email", trusteeDetails.getString("email"))
          .put("name", new JsonObject().put("firstName", trusteeDetails.getString("firstName"))
              .put("lastName", trusteeDetails.getString("lastName")));
      p.complete(new JsonObject().put(trusteeDetails.getString("userId"), userDets));
      return i.getMock();
    }).when(registrationService).getUserDetails(any(), any());

    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(3);
      p.complete(new JsonObject().put("type", URN_SUCCESS.toString()));
      return i.getMock();
    }).when(policyService).createPolicy(any(), any(), any(), any());

    JsonArray req = new JsonArray()
        .add(new JsonObject().put("apdId", ACTIVE_B_ID.toString()).put("status", "inactive"));
    List<ApdUpdateRequest> request = ApdUpdateRequest.jsonArrayToList(req);

    apdService.updateApd(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getInteger("status"), 200);
          assertEquals(URN_SUCCESS.toString(), response.getString("type"));
          assertEquals(SUCC_TITLE_UPDATED_APD, response.getString("title"));
          JsonObject result = response.getJsonArray("results").getJsonObject(0);
          assertEquals(ACTIVE_B, result.getString(RESP_APD_NAME));
          assertEquals(ACTIVE_B + ".com", result.getString(RESP_APD_URL));
          assertEquals("inactive", result.getString(RESP_APD_STATUS));
          assertTrue(result.containsKey(RESP_APD_ID));
          assertTrue(result.containsKey(RESP_APD_OWNER));

          JsonObject ownerDets = result.getJsonObject(RESP_APD_OWNER);
          assertEquals(trusteeDetails.getString("userId"), ownerDets.getString(RESP_OWNER_USER_ID));
          assertEquals(trusteeDetails.getString("firstName"),
              ownerDets.getJsonObject("name").getString("firstName"));
          assertEquals(trusteeDetails.getString("lastName"),
              ownerDets.getJsonObject("name").getString("lastName"));
          assertEquals(trusteeDetails.getString("email"), ownerDets.getString("email"));
          testContext.completeNow();
        })));
  }
}
