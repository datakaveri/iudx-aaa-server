package iudx.aaa.server.admin;

import static iudx.aaa.server.apiserver.util.Urn.*;
import static iudx.aaa.server.admin.Constants.CONFIG_AUTH_URL;
import static iudx.aaa.server.admin.Constants.ERR_DETAIL_NOT_AUTH_ADMIN;
import static iudx.aaa.server.admin.Constants.ERR_DETAIL_NO_USER_PROFILE;
import static iudx.aaa.server.admin.Constants.ERR_TITLE_INVALID_USER;
import static iudx.aaa.server.admin.Constants.ERR_TITLE_NOT_AUTH_ADMIN;
import static iudx.aaa.server.admin.Constants.ERR_TITLE_NO_USER_PROFILE;
import static iudx.aaa.server.admin.Constants.NIL_UUID;
import static iudx.aaa.server.admin.Constants.RESP_STATUS;
import static iudx.aaa.server.admin.Constants.SUCC_TITLE_PROV_STATUS_UPDATE;
import static iudx.aaa.server.registration.Utils.SQL_CREATE_ADMIN_SERVER;
import static iudx.aaa.server.registration.Utils.SQL_CREATE_ORG;
import static iudx.aaa.server.registration.Utils.SQL_DELETE_ORG;
import static iudx.aaa.server.registration.Utils.SQL_DELETE_SERVERS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import io.vertx.core.CompositeFuture;
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
import iudx.aaa.server.apiserver.ProviderUpdateRequest;
import iudx.aaa.server.apiserver.RoleStatus;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.policy.PolicyService;
import iudx.aaa.server.registration.KcAdmin;
import iudx.aaa.server.registration.Utils;
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

@ExtendWith({VertxExtension.class})
public class UpdateProviderRegistrationStatusTest {
  private static Logger LOGGER = LogManager.getLogger(UpdateProviderRegistrationStatusTest.class);

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
  private static AdminService adminService;
  private static Vertx vertxObj;

  private static KcAdmin kc = Mockito.mock(KcAdmin.class);
  private static PolicyService policyService = Mockito.mock(PolicyService.class);
  private static Future<JsonObject> adminAuthUser;
  private static Future<JsonObject> adminOtherUser;
  private static Future<JsonObject> consumerUser;

  private static final String DUMMY_SERVER =
      "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";
  private static final String DUMMY_AUTH_SERVER =
      "auth" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + "iudx.io";

  static String name = RandomStringUtils.randomAlphabetic(10).toLowerCase();
  static String url = name + ".com";
  static Promise<UUID> orgId;

  static Future<JsonObject> providerPending1;
  static Future<JsonObject> providerPending2;
  static Future<JsonObject> providerPending3;
  static Future<JsonObject> providerRejected;
  static Future<JsonObject> providerApproved;

  static Future<UUID> orgIdFut;

  @BeforeAll
  @DisplayName("Deploying Verticle")
  static void startVertx(Vertx vertx, VertxTestContext testContext) {
    config = new Configuration();
    vertxObj = vertx;
    JsonObject dbConfig = config.configLoader(3, vertx);

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

    /* Pool options */
    if (poolOptions == null) {
      poolOptions = new PoolOptions().setMaxSize(poolSize);
    }

    pool = PgPool.pool(vertx, connectOptions, poolOptions);

    /* Do not take test config, use generated config */
    JsonObject options = new JsonObject().put(CONFIG_AUTH_URL, DUMMY_AUTH_SERVER);

    Map<Roles, RoleStatus> rolesA = new HashMap<Roles, RoleStatus>();
    rolesA.put(Roles.ADMIN, RoleStatus.APPROVED);

    Map<Roles, RoleStatus> rolesB = new HashMap<Roles, RoleStatus>();
    rolesB.put(Roles.CONSUMER, RoleStatus.APPROVED);

    adminAuthUser = Utils.createFakeUser(pool, NIL_UUID, "", rolesA, false);
    adminOtherUser = Utils.createFakeUser(pool, NIL_UUID, "", rolesA, false);
    consumerUser = Utils.createFakeUser(pool, NIL_UUID, "", rolesB, false);

    orgIdFut = pool.withConnection(conn -> conn.preparedQuery(SQL_CREATE_ORG)
        .execute(Tuple.of(name, url)).map(row -> row.iterator().next().getUUID("id")));

    providerApproved = orgIdFut.compose(id -> Utils.createFakeUser(pool, id.toString(), url,
        Map.of(Roles.PROVIDER, RoleStatus.APPROVED), true));

    providerRejected = orgIdFut.compose(id -> Utils.createFakeUser(pool, id.toString(), url,
        Map.of(Roles.PROVIDER, RoleStatus.REJECTED), true));

    providerPending1 = orgIdFut.compose(id -> Utils.createFakeUser(pool, id.toString(), url,
        Map.of(Roles.PROVIDER, RoleStatus.PENDING), true));

    providerPending2 = orgIdFut.compose(id -> Utils.createFakeUser(pool, id.toString(), url,
        Map.of(Roles.PROVIDER, RoleStatus.PENDING), true));

    providerPending3 = orgIdFut.compose(id -> Utils.createFakeUser(pool, id.toString(), url,
        Map.of(Roles.PROVIDER, RoleStatus.PENDING), true));

    @SuppressWarnings("rawtypes")
    List<Future> list = List.of(adminAuthUser, adminOtherUser, consumerUser, providerApproved,
        providerPending1, providerPending2, providerPending3, providerRejected);

    CompositeFuture.all(list).compose(res -> {
      JsonObject admin1 = (JsonObject) res.list().get(0);
      UUID uid1 = UUID.fromString(admin1.getString("userId"));

      JsonObject admin2 = (JsonObject) res.list().get(1);
      UUID uid2 = UUID.fromString(admin2.getString("userId"));
      List<Tuple> tup = List.of(Tuple.of("Auth Server", uid1, DUMMY_AUTH_SERVER),
          Tuple.of("Other Server", uid2, DUMMY_SERVER));

      return pool
          .withConnection(conn -> conn.preparedQuery(SQL_CREATE_ADMIN_SERVER).executeBatch(tup));
    }).onSuccess(res -> {
      adminService = new AdminServiceImpl(pool, kc, policyService, options);
      testContext.completeNow();
    }).onFailure(err -> testContext.failNow(err.getMessage()));
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing....");
    Tuple servers = Tuple.of(List.of(DUMMY_AUTH_SERVER, DUMMY_SERVER).toArray());
    List<JsonObject> users = List.of(adminAuthUser.result(), adminOtherUser.result(),
        consumerUser.result(), providerPending1.result(), providerPending2.result(),
        providerPending3.result(), providerRejected.result(), providerApproved.result());

    pool.withConnection(conn -> conn.preparedQuery(SQL_DELETE_SERVERS).execute(servers)
        .compose(success -> Utils.deleteFakeUser(pool, users))
        .compose(succ -> conn.preparedQuery(SQL_DELETE_ORG).execute(Tuple.of(orgIdFut.result()))))
        .onComplete(x -> {
          if (x.failed()) {
            LOGGER.warn(x.cause().getMessage());
          }
          vertxObj.close(testContext.succeeding(response -> testContext.completeNow()));
        });
  }

  @Test
  @DisplayName("Test no user profile")
  void noUserProfile(VertxTestContext testContext) {
    JsonObject userJson = adminAuthUser.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.ADMIN)).build();

    JsonObject providerJson = providerPending1.result();

    JsonArray req = new JsonArray().add(
        new JsonObject().put("userId", providerJson.getString("userId")).put("status", "approved"));
    List<ProviderUpdateRequest> request = ProviderUpdateRequest.jsonArrayToList(req);

    adminService.updateProviderRegistrationStatus(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getInteger("status"), 404);
          assertEquals(URN_MISSING_INFO.toString(), response.getString("type"));
          assertEquals(ERR_TITLE_NO_USER_PROFILE, response.getString("title"));
          assertEquals(ERR_DETAIL_NO_USER_PROFILE, response.getString("detail"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test not auth admin")
  void notAuthAdmin(VertxTestContext testContext) {
    JsonObject userJson = adminOtherUser.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId")).roles(List.of(Roles.ADMIN))
        .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

    JsonObject providerJson = providerPending1.result();

    JsonArray req = new JsonArray().add(
        new JsonObject().put("userId", providerJson.getString("userId")).put("status", "approved"));
    List<ProviderUpdateRequest> request = ProviderUpdateRequest.jsonArrayToList(req);

    adminService.updateProviderRegistrationStatus(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getInteger("status"), 401);
          assertEquals(URN_INVALID_ROLE.toString(), response.getString("type"));
          assertEquals(ERR_TITLE_NOT_AUTH_ADMIN, response.getString("title"));
          assertEquals(ERR_DETAIL_NOT_AUTH_ADMIN, response.getString("detail"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test not an admin")
  void notAdmin(VertxTestContext testContext) {
    JsonObject userJson = consumerUser.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId")).roles(List.of(Roles.CONSUMER))
        .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

    JsonObject providerJson = providerPending1.result();

    JsonArray req = new JsonArray().add(
        new JsonObject().put("userId", providerJson.getString("userId")).put("status", "approved"));
    List<ProviderUpdateRequest> request = ProviderUpdateRequest.jsonArrayToList(req);

    adminService.updateProviderRegistrationStatus(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getInteger("status"), 401);
          assertEquals(URN_INVALID_ROLE.toString(), response.getString("type"));
          assertEquals(ERR_TITLE_NOT_AUTH_ADMIN, response.getString("title"));
          assertEquals(ERR_DETAIL_NOT_AUTH_ADMIN, response.getString("detail"));
          testContext.completeNow();
        })));
  }

  /*
   * NOTE: We mock KcAdmin getDetails function as well as the Map<String, JsonObject> returned by
   * said function. This is because we don't know the values already existing in the table. Hence,
   * we mock the Map.get(<String>) function to return an empty JsonObject, but if it is the expected
   * userId String, we return the expected output.
   * 
   * We additionally mock PolicyService.setDefaultProviderPolicies, by completing the promise/Async
   * Handler with an empty JsonObject
   */

  @Test
  @DisplayName("Test approve provider")
  void approveprovider(VertxTestContext testContext) {
    JsonObject userJson = adminAuthUser.result();
    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId")).roles(List.of(Roles.ADMIN))
        .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

    JsonObject providerJson = providerPending1.result();
    JsonObject details = new JsonObject().put("email", providerJson.getString("email")).put("name",
        new JsonObject().put("firstName", providerJson.getString("firstName")).put("lastName",
            providerJson.getString("lastName")));

    JsonArray req = new JsonArray().add(
        new JsonObject().put("userId", providerJson.getString("userId")).put("status", "approved"));
    List<ProviderUpdateRequest> request = ProviderUpdateRequest.jsonArrayToList(req);

    /** MOCKS -> mock PolicyService, kc.getDetails and kc.approveProvider **/
    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(3);
      p.complete(new JsonObject().put("type", URN_SUCCESS.toString()));
      return i.getMock();
    }).when(policyService).createPolicy(any(), any(),any(),any());

    @SuppressWarnings("unchecked")
    Map<String, JsonObject> resp = Mockito.mock(Map.class);
    Mockito.when(kc.getDetails(any())).thenReturn(Future.succeededFuture(resp));
    Mockito.when(resp.get(anyString())).thenReturn(new JsonObject());
    Mockito.when(resp.get(providerJson.getString("keycloakId"))).thenReturn(details);

    Mockito.when(kc.approveProvider(any())).thenReturn(Future.succeededFuture());
    /***********************************************************************/

    adminService.updateProviderRegistrationStatus(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getString("type"), URN_SUCCESS.toString());
          assertEquals(response.getString("title"), SUCC_TITLE_PROV_STATUS_UPDATE);
          JsonArray res = response.getJsonArray("results");
          assertTrue(res.size() > 0);

          res.forEach(i -> {
            JsonObject j = (JsonObject) i;
            if (j.getString("userId").equals(providerJson.getString("userId"))) {

              assertEquals(j.getString(RESP_STATUS), RoleStatus.APPROVED.name().toLowerCase());

              assertEquals(j.getString("email"), providerJson.getString("email"));
              assertEquals(j.getString("userId"), providerJson.getString("userId"));
              testContext.completeNow();
            }
          });
        })));
  }

  @Test
  @DisplayName("Test fail already approved")
  void alreadyApproved(VertxTestContext testContext) {
    JsonObject providerJson = providerApproved.result();
    JsonObject details = new JsonObject().put("email", providerJson.getString("email")).put("name",
        new JsonObject().put("firstName", providerJson.getString("firstName")).put("lastName",
            providerJson.getString("lastName")));

    JsonObject userJson = adminAuthUser.result();
    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId")).roles(List.of(Roles.ADMIN))
        .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

    JsonArray req = new JsonArray().add(
        new JsonObject().put("userId", providerJson.getString("userId")).put("status", "approved"));
    List<ProviderUpdateRequest> request = ProviderUpdateRequest.jsonArrayToList(req);

    /** MOCKS -> mock PolicyService, kc.getDetails and kc.approveProvider **/
    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(3);
      p.complete(new JsonObject().put("type", URN_SUCCESS.toString()));
      return i.getMock();
    }).when(policyService).createPolicy(any(),any(),any(),any());

    @SuppressWarnings("unchecked")
    Map<String, JsonObject> resp = Mockito.mock(Map.class);
    Mockito.when(kc.getDetails(any())).thenReturn(Future.succeededFuture(resp));
    Mockito.when(resp.get(anyString())).thenReturn(new JsonObject());
    Mockito.when(resp.get(providerJson.getString("keycloakId"))).thenReturn(details);

    Mockito.when(kc.approveProvider(any())).thenReturn(Future.succeededFuture());
    /***********************************************************************/

    adminService.updateProviderRegistrationStatus(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getString("type"), URN_INVALID_INPUT.toString());
          assertEquals(response.getString("title"), ERR_TITLE_INVALID_USER);
          assertEquals(response.getString("detail"), providerJson.getString("userId"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test reject provider and approve again")
  void rejectProvider(VertxTestContext testContext) {
    JsonObject userJson = adminAuthUser.result();
    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId")).roles(List.of(Roles.ADMIN))
        .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

    JsonObject providerJson = providerPending2.result();
    JsonObject details = new JsonObject().put("email", providerJson.getString("email")).put("name",
        new JsonObject().put("firstName", providerJson.getString("firstName")).put("lastName",
            providerJson.getString("lastName")));

    JsonArray req = new JsonArray().add(
        new JsonObject().put("userId", providerJson.getString("userId")).put("status", "rejected"));
    List<ProviderUpdateRequest> request = ProviderUpdateRequest.jsonArrayToList(req);

    /** MOCKS -> mock PolicyService, kc.getDetails and kc.approveProvider **/
    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(3);
      p.complete(new JsonObject().put("type", URN_SUCCESS.toString()));
      return i.getMock();
    }).when(policyService).createPolicy(any(),any(),any(),any());

    @SuppressWarnings("unchecked")
    Map<String, JsonObject> resp = Mockito.mock(Map.class);
    Mockito.when(kc.getDetails(any())).thenReturn(Future.succeededFuture(resp));
    Mockito.when(resp.get(anyString())).thenReturn(new JsonObject());
    Mockito.when(resp.get(providerJson.getString("keycloakId"))).thenReturn(details);

    Mockito.when(kc.approveProvider(any())).thenReturn(Future.succeededFuture());
    /***********************************************************************/

    Checkpoint rejected = testContext.checkpoint();
    Checkpoint fail = testContext.checkpoint();

    adminService.updateProviderRegistrationStatus(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getString("type"), URN_SUCCESS.toString());
          assertEquals(response.getString("title"), SUCC_TITLE_PROV_STATUS_UPDATE);
          JsonArray res = response.getJsonArray("results");
          assertTrue(res.size() > 0);

          res.forEach(i -> {
            JsonObject j = (JsonObject) i;
            if (j.getString("userId").equals(providerJson.getString("userId"))) {

              assertEquals(j.getString(RESP_STATUS), RoleStatus.REJECTED.name().toLowerCase());

              assertEquals(j.getString("email"), providerJson.getString("email"));
              assertEquals(j.getString("userId"), providerJson.getString("userId"));
              rejected.flag();
            }
          });

          adminService.updateProviderRegistrationStatus(request, user,
              testContext.succeeding(r -> testContext.verify(() -> {
                assertEquals(r.getString("type"), URN_INVALID_INPUT.toString());
                assertEquals(r.getString("title"), ERR_TITLE_INVALID_USER);
                assertEquals(r.getString("detail"), providerJson.getString("userId"));
                fail.flag();
              })));
        })));
  }

  @Test
  @DisplayName("Test createPolicy fail and transaction rollback")
  /*
   * Testing if a failure in createPolicy rolls back the transaction. We attempt to approve the
   * provider, but fail createPolicy. We then attempt to successfully reject the provider
   */
  void createPolicyFailtransaction(VertxTestContext testContext) {
    JsonObject userJson = adminAuthUser.result();
    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId")).roles(List.of(Roles.ADMIN))
        .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

    JsonObject providerJson = providerPending3.result();
    JsonObject details = new JsonObject().put("email", providerJson.getString("email")).put("name",
        new JsonObject().put("firstName", providerJson.getString("firstName")).put("lastName",
            providerJson.getString("lastName")));

    JsonArray req = new JsonArray().add(
        new JsonObject().put("userId", providerJson.getString("userId")).put("status", "approved"));
    List<ProviderUpdateRequest> request = ProviderUpdateRequest.jsonArrayToList(req);

    /** MOCKS -> mock PolicyService, kc.getDetails and kc.approveProvider **/
    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(3);
      p.fail("Failed to set admin ");
      return i.getMock();
    }).when(policyService).createPolicy(any(),any(),any(),any());

    @SuppressWarnings("unchecked")
    Map<String, JsonObject> resp = Mockito.mock(Map.class);
    Mockito.when(kc.getDetails(any())).thenReturn(Future.succeededFuture(resp));
    Mockito.when(resp.get(anyString())).thenReturn(new JsonObject());
    Mockito.when(resp.get(providerJson.getString("keycloakId"))).thenReturn(details);

    Mockito.when(kc.approveProvider(any())).thenReturn(Future.succeededFuture());
    /***********************************************************************/

    Checkpoint failed = testContext.checkpoint();
    Checkpoint rejectedSuccess = testContext.checkpoint();

    adminService.updateProviderRegistrationStatus(request, user, testContext.failing(response -> {
      failed.flag();

      /* change request to rejected */
      JsonArray j = new JsonArray().add(new JsonObject()
          .put("userId", providerJson.getString("userId")).put("status", "rejected"));
      List<ProviderUpdateRequest> newRequest = ProviderUpdateRequest.jsonArrayToList(j);

      adminService.updateProviderRegistrationStatus(newRequest, user,
          testContext.succeeding(r -> testContext.verify(() -> {
            assertEquals(r.getString("type"), URN_SUCCESS.toString());
            assertEquals(r.getString("title"), SUCC_TITLE_PROV_STATUS_UPDATE);
            JsonArray res = r.getJsonArray("results");
            assertTrue(res.size() > 0);
            rejectedSuccess.flag();
          })));
    }));
  }
}
