package iudx.aaa.server.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static iudx.aaa.server.admin.Constants.POLICY_SERVICE_ADDRESS;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.impl.Arguments;
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
import iudx.aaa.server.policy.PolicyServiceImpl;
import iudx.aaa.server.registration.KcAdmin;
import iudx.aaa.server.registration.Utils;
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
import org.junit.jupiter.api.Disabled;
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

  private static List<UUID> orgIds = new ArrayList<UUID>();

  private static final String UUID_REGEX =
      "^[0-9a-f]{8}\\b-[0-9a-f]{4}\\b-[0-9a-f]{4}\\b-[0-9a-f]{4}\\b-[0-9a-f]{12}$";

  private static final String DUMMY_SERVER =
      "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";
  private static final String DUMMY_AUTH_SERVER =
      "auth" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + "iudx.io";

  /* SQL queries for creating and deleting required data */
  private static final String SQL_CREATE_ADMIN_SERVER =
      "INSERT INTO test.resource_server (name, owner_id, url, created_at, updated_at) "
          + "VALUES ($1::text, $2::uuid, $3::text, NOW(), NOW())";

  private static final String SQL_DELETE_SERVERS =
      "DELETE FROM test.resource_server WHERE url = ANY ($1::text[])";

  private static final String SQL_CREATE_ORG =
      "INSERT INTO test.organizations (name, url, created_at, updated_at) "
          + "VALUES ($1:: text, $2::text, NOW(), NOW()) RETURNING id";

  private static final String SQL_DELETE_ORG = "DELETE FROM test.organizations WHERE id = $1::uuid";

  static String name = RandomStringUtils.randomAlphabetic(10).toLowerCase();
  static String url = name + ".com";
  static Promise<UUID> orgId;

  static Future<JsonObject> providerPending1;
  static Future<JsonObject> providerPending2;
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
    databaseUserName = dbConfig.getString("databaseUserName");
    databasePassword = dbConfig.getString("databasePassword");
    poolSize = Integer.parseInt(dbConfig.getString("poolSize"));

    /* Set Connection Object */
    if (connectOptions == null) {
      connectOptions = new PgConnectOptions().setPort(databasePort).setHost(databaseIP)
          .setDatabase(databaseName).setUser(databaseUserName).setPassword(databasePassword);
    }

    /* Pool options */
    if (poolOptions == null) {
      poolOptions = new PoolOptions().setMaxSize(poolSize);
    }

    pool = PgPool.pool(vertx, connectOptions, poolOptions);

    /* Do not take test config, use generated config */
    JsonObject options = new JsonObject().put(Constants.CONFIG_AUTH_URL, DUMMY_AUTH_SERVER);

    Map<Roles, RoleStatus> rolesA = new HashMap<Roles, RoleStatus>();
    rolesA.put(Roles.ADMIN, RoleStatus.APPROVED);

    Map<Roles, RoleStatus> rolesB = new HashMap<Roles, RoleStatus>();
    rolesB.put(Roles.CONSUMER, RoleStatus.APPROVED);

    adminAuthUser = Utils.createFakeUser(pool, Constants.NIL_UUID, "", rolesA, false);
    adminOtherUser = Utils.createFakeUser(pool, Constants.NIL_UUID, "", rolesA, false);
    consumerUser = Utils.createFakeUser(pool, Constants.NIL_UUID, "", rolesB, false);

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

    @SuppressWarnings("rawtypes")
    List<Future> list = List.of(adminAuthUser, adminOtherUser, consumerUser, providerApproved,
        providerPending1, providerPending2, providerRejected);

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
        providerRejected.result(), providerApproved.result());

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
          assertEquals(Constants.URN_MISSING_INFO, response.getString("type"));
          assertEquals(Constants.ERR_TITLE_NO_USER_PROFILE, response.getString("title"));
          assertEquals(Constants.ERR_DETAIL_NO_USER_PROFILE, response.getString("detail"));
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
          assertEquals(Constants.URN_INVALID_ROLE, response.getString("type"));
          assertEquals(Constants.ERR_TITLE_NOT_AUTH_ADMIN, response.getString("title"));
          assertEquals(Constants.ERR_DETAIL_NOT_AUTH_ADMIN, response.getString("detail"));
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
          assertEquals(Constants.URN_INVALID_ROLE, response.getString("type"));
          assertEquals(Constants.ERR_TITLE_NOT_AUTH_ADMIN, response.getString("title"));
          assertEquals(Constants.ERR_DETAIL_NOT_AUTH_ADMIN, response.getString("detail"));
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
      Promise<JsonObject> p = i.getArgument(1);
      p.complete(new JsonObject());
      return i.getMock();
    }).when(policyService).setDefaultProviderPolicies(any(), any());

    @SuppressWarnings("unchecked")
    Map<String, JsonObject> resp = Mockito.mock(Map.class);
    Mockito.when(kc.getDetails(any())).thenReturn(Future.succeededFuture(resp));
    Mockito.when(resp.get(anyString())).thenReturn(new JsonObject());
    Mockito.when(resp.get(providerJson.getString("keycloakId"))).thenReturn(details);

    Mockito.when(kc.approveProvider(any())).thenReturn(Future.succeededFuture());
    /***********************************************************************/

    adminService.updateProviderRegistrationStatus(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getString("type"), Constants.URN_SUCCESS);
          assertEquals(response.getString("title"), Constants.SUCC_TITLE_PROV_STATUS_UPDATE);
          JsonArray res = response.getJsonArray("results");
          assertTrue(res.size() > 0);

          res.forEach(i -> {
            JsonObject j = (JsonObject) i;
            if (j.getString("userId").equals(providerJson.getString("userId"))) {

              assertEquals(j.getString(Constants.RESP_STATUS), RoleStatus.APPROVED.name());

              assertEquals(j.getString("email"), providerJson.getString("email"));
              assertEquals(j.getString("userId"), providerJson.getString("userId"));
              testContext.completeNow();
            }
          });
        })));
  }

  @Test
  @DisplayName("Test duplicates")
  void duplicateuserIds(VertxTestContext testContext) {
    JsonObject providerJson = providerApproved.result();
    JsonObject details = new JsonObject().put("email", providerJson.getString("email")).put("name",
        new JsonObject().put("firstName", providerJson.getString("firstName")).put("lastName",
            providerJson.getString("lastName")));

    JsonObject userJson = adminAuthUser.result();
    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId")).roles(List.of(Roles.ADMIN))
        .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

    JsonObject j =
        new JsonObject().put("userId", providerJson.getString("userId")).put("status", "approved");
    JsonArray req = new JsonArray().add(j).add(j);
    List<ProviderUpdateRequest> request = ProviderUpdateRequest.jsonArrayToList(req);

    /** MOCKS -> mock PolicyService, kc.getDetails and kc.approveProvider **/
    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(1);
      p.complete(new JsonObject());
      return i.getMock();
    }).when(policyService).setDefaultProviderPolicies(any(), any());

    @SuppressWarnings("unchecked")
    Map<String, JsonObject> resp = Mockito.mock(Map.class);
    Mockito.when(kc.getDetails(any())).thenReturn(Future.succeededFuture(resp));
    Mockito.when(resp.get(anyString())).thenReturn(new JsonObject());
    Mockito.when(resp.get(providerJson.getString("keycloakId"))).thenReturn(details);

    Mockito.when(kc.approveProvider(any())).thenReturn(Future.succeededFuture());
    /***********************************************************************/

    adminService.updateProviderRegistrationStatus(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getString("type"), Constants.URN_INVALID_INPUT);
          assertEquals(response.getString("title"), Constants.ERR_TITLE_DUPLICATES);
          assertEquals(response.getString("detail"), Constants.ERR_DETAIL_DUPLICATES);
          testContext.completeNow();
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
      Promise<JsonObject> p = i.getArgument(1);
      p.complete(new JsonObject());
      return i.getMock();
    }).when(policyService).setDefaultProviderPolicies(any(), any());

    @SuppressWarnings("unchecked")
    Map<String, JsonObject> resp = Mockito.mock(Map.class);
    Mockito.when(kc.getDetails(any())).thenReturn(Future.succeededFuture(resp));
    Mockito.when(resp.get(anyString())).thenReturn(new JsonObject());
    Mockito.when(resp.get(providerJson.getString("keycloakId"))).thenReturn(details);

    Mockito.when(kc.approveProvider(any())).thenReturn(Future.succeededFuture());
    /***********************************************************************/

    adminService.updateProviderRegistrationStatus(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getString("type"), Constants.URN_INVALID_INPUT);
          assertEquals(response.getString("title"), Constants.ERR_TITLE_INVALID_USER);
          assertEquals(response.getString("detail"), Constants.ERR_DETAIL_INVALID_USER + "0");
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
      Promise<JsonObject> p = i.getArgument(1);
      p.complete(new JsonObject());
      return i.getMock();
    }).when(policyService).setDefaultProviderPolicies(any(), any());

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
          assertEquals(response.getString("type"), Constants.URN_SUCCESS);
          assertEquals(response.getString("title"), Constants.SUCC_TITLE_PROV_STATUS_UPDATE);
          JsonArray res = response.getJsonArray("results");
          assertTrue(res.size() > 0);

          res.forEach(i -> {
            JsonObject j = (JsonObject) i;
            if (j.getString("userId").equals(providerJson.getString("userId"))) {

              assertEquals(j.getString(Constants.RESP_STATUS), RoleStatus.REJECTED.name());

              assertEquals(j.getString("email"), providerJson.getString("email"));
              assertEquals(j.getString("userId"), providerJson.getString("userId"));
              rejected.flag();
            }
          });

          adminService.updateProviderRegistrationStatus(request, user,
              testContext.succeeding(r -> testContext.verify(() -> {
                assertEquals(r.getString("type"), Constants.URN_INVALID_INPUT);
                assertEquals(r.getString("title"), Constants.ERR_TITLE_INVALID_USER);
                assertEquals(r.getString("detail"), Constants.ERR_DETAIL_INVALID_USER + "0");
                fail.flag();
              })));
        })));
  }
}
