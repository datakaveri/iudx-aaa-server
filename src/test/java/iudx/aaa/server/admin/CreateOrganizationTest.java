package iudx.aaa.server.admin;

import static iudx.aaa.server.apiserver.util.Urn.*;
import static iudx.aaa.server.registration.Utils.SQL_CREATE_ADMIN_SERVER;
import static iudx.aaa.server.registration.Utils.SQL_DELETE_BULK_ORG;
import static iudx.aaa.server.registration.Utils.SQL_DELETE_SERVERS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.CreateOrgRequest;
import iudx.aaa.server.apiserver.RoleStatus;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.policy.PolicyService;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

@ExtendWith({VertxExtension.class})
public class CreateOrganizationTest {
  private static Logger LOGGER = LogManager.getLogger(CreateOrganizationTest.class);

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

  private static List<UUID> orgIds = new ArrayList<UUID>();

  private static final String UUID_REGEX =
      "^[0-9a-f]{8}\\b-[0-9a-f]{4}\\b-[0-9a-f]{4}\\b-[0-9a-f]{4}\\b-[0-9a-f]{12}$";

  private static final String DUMMY_SERVER =
      "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";
  private static final String DUMMY_AUTH_SERVER =
      "auth" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + "iudx.io";


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
    JsonObject options = new JsonObject().put(Constants.CONFIG_AUTH_URL, DUMMY_AUTH_SERVER);

    Map<Roles, RoleStatus> rolesA = new HashMap<Roles, RoleStatus>();
    rolesA.put(Roles.ADMIN, RoleStatus.APPROVED);

    Map<Roles, RoleStatus> rolesB = new HashMap<Roles, RoleStatus>();
    rolesB.put(Roles.CONSUMER, RoleStatus.APPROVED);

    adminAuthUser = Utils.createFakeUser(pool, Constants.NIL_UUID, "", rolesA, false);
    adminOtherUser = Utils.createFakeUser(pool, Constants.NIL_UUID, "", rolesA, false);
    consumerUser = Utils.createFakeUser(pool, Constants.NIL_UUID, "", rolesB, false);

    CompositeFuture.all(adminAuthUser, adminOtherUser, consumerUser).compose(res -> {
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
    List<JsonObject> users =
        List.of(adminAuthUser.result(), adminOtherUser.result(), consumerUser.result());
    Tuple createdOrgs = Tuple.of(orgIds.toArray(UUID[]::new));

    pool.withConnection(conn -> conn.preparedQuery(SQL_DELETE_SERVERS).execute(servers)
        .compose(succ -> conn.preparedQuery(SQL_DELETE_BULK_ORG).execute(createdOrgs))
        .compose(success -> Utils.deleteFakeUser(pool, users))).onComplete(x -> {
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

    JsonObject json = new JsonObject().put("url", "foo.bar").put("name", "bar");
    CreateOrgRequest request = new CreateOrgRequest(json);
    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.ADMIN)).build();

    adminService.createOrganization(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getInteger("status"), 404);
          assertEquals(URN_MISSING_INFO.toString(), response.getString("type"));
          assertEquals(Constants.ERR_TITLE_NO_USER_PROFILE, response.getString("title"));
          assertEquals(Constants.ERR_DETAIL_NO_USER_PROFILE, response.getString("detail"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test not auth admin")
  void notAuthAdmin(VertxTestContext testContext) {
    JsonObject userJson = adminOtherUser.result();

    JsonObject json = new JsonObject().put("url", "foo.bar").put("name", "bar");
    CreateOrgRequest request = new CreateOrgRequest(json);
    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId")).roles(List.of(Roles.ADMIN))
        .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

    adminService.createOrganization(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getInteger("status"), 401);
          assertEquals(URN_INVALID_ROLE.toString(), response.getString("type"));
          assertEquals(Constants.ERR_TITLE_NOT_AUTH_ADMIN, response.getString("title"));
          assertEquals(Constants.ERR_DETAIL_NOT_AUTH_ADMIN, response.getString("detail"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test not an admin")
  void notAdmin(VertxTestContext testContext) {
    JsonObject userJson = consumerUser.result();

    JsonObject json = new JsonObject().put("url", "foo.bar").put("name", "bar");
    CreateOrgRequest request = new CreateOrgRequest(json);
    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId")).roles(List.of(Roles.CONSUMER))
        .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

    adminService.createOrganization(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getInteger("status"), 401);
          assertEquals(URN_INVALID_ROLE.toString(), response.getString("type"));
          assertEquals(Constants.ERR_TITLE_NOT_AUTH_ADMIN, response.getString("title"));
          assertEquals(Constants.ERR_DETAIL_NOT_AUTH_ADMIN, response.getString("detail"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test invalid domains")
  void invalidDomains(VertxTestContext testContext) {
    JsonObject userJson = adminAuthUser.result();
    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId")).roles(List.of(Roles.ADMIN))
        .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

    Checkpoint protocolInUrl = testContext.checkpoint();
    Checkpoint pathInUrl = testContext.checkpoint();
    Checkpoint badDomain = testContext.checkpoint();
    Checkpoint specialChars = testContext.checkpoint();

    JsonObject json = new JsonObject().put("url", "https://example.com").put("name", "bar");
    CreateOrgRequest request = new CreateOrgRequest(json);
    adminService.createOrganization(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getInteger("status"), 400);
          assertEquals(response.getString("type"), URN_INVALID_INPUT.toString());
          assertEquals(response.getString("title"), Constants.ERR_TITLE_INVALID_DOMAIN);
          assertEquals(response.getString("detail"), Constants.ERR_DETAIL_INVALID_DOMAIN);
          protocolInUrl.flag();
        })));

    json.clear().put("url", "example.com/path/1/2").put("name", "bar");
    request = new CreateOrgRequest(json);
    adminService.createOrganization(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getInteger("status"), 400);
          assertEquals(response.getString("type"), URN_INVALID_INPUT.toString());
          assertEquals(response.getString("title"), Constants.ERR_TITLE_INVALID_DOMAIN);
          assertEquals(response.getString("detail"), Constants.ERR_DETAIL_INVALID_DOMAIN);
          pathInUrl.flag();
        })));

    json.clear().put("url", "example.abcd").put("name", "bar");
    request = new CreateOrgRequest(json);
    adminService.createOrganization(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getInteger("status"), 400);
          assertEquals(response.getString("type"), URN_INVALID_INPUT.toString());
          assertEquals(response.getString("title"), Constants.ERR_TITLE_INVALID_DOMAIN);
          assertEquals(response.getString("detail"), Constants.ERR_DETAIL_INVALID_DOMAIN);
          badDomain.flag();
        })));

    json.clear().put("url", "example#@.abcd").put("name", "bar");
    request = new CreateOrgRequest(json);
    adminService.createOrganization(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getInteger("status"), 400);
          assertEquals(response.getString("type"), URN_INVALID_INPUT.toString());
          assertEquals(response.getString("title"), Constants.ERR_TITLE_INVALID_DOMAIN);
          assertEquals(response.getString("detail"), Constants.ERR_DETAIL_INVALID_DOMAIN);
          specialChars.flag();
        })));
  }

  @Test
  @DisplayName("Test successful creation")
  void successfulCreation(VertxTestContext testContext) {
    JsonObject userJson = adminAuthUser.result();

    String name = RandomStringUtils.randomAlphabetic(10);
    String url = name + ".com";

    JsonObject json = new JsonObject().put("url", url).put("name", name);
    CreateOrgRequest request = new CreateOrgRequest(json);
    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId")).roles(List.of(Roles.ADMIN))
        .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

    adminService.createOrganization(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getInteger("status"), 201);
          assertEquals(response.getString("type"), URN_SUCCESS.toString());
          assertEquals(response.getString("title"), Constants.SUCC_TITLE_CREATED_ORG);
          JsonObject result = response.getJsonObject("results");
          assertEquals(result.getString("name"), name);
          assertEquals(result.getString("url"), url.toLowerCase());
          assertTrue(result.getString("id").matches(UUID_REGEX));
          orgIds.add(UUID.fromString(result.getString("id")));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test existing org - same domain")
  void existingOrg(VertxTestContext testContext) {
    JsonObject userJson = adminAuthUser.result();

    String name = RandomStringUtils.randomAlphabetic(10);
    String url = name + ".com";

    JsonObject json = new JsonObject().put("url", url).put("name", name);
    CreateOrgRequest request = new CreateOrgRequest(json);
    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId")).roles(List.of(Roles.ADMIN))
        .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

    Promise<Void> p1 = Promise.promise();

    adminService.createOrganization(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getInteger("status"), 201);
          assertEquals(response.getString("type"), URN_SUCCESS.toString());
          assertEquals(response.getString("title"), Constants.SUCC_TITLE_CREATED_ORG);
          JsonObject result = response.getJsonObject("results");
          assertEquals(result.getString("name"), name);
          assertEquals(result.getString("url"), url.toLowerCase());
          assertTrue(result.getString("id").matches(UUID_REGEX));
          orgIds.add(UUID.fromString(result.getString("id")));
          p1.complete();
        })));

    p1.future().onSuccess(succ -> {
      adminService.createOrganization(request, user,
          testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(response.getInteger("status"), 409);
            assertEquals(response.getString("type"), URN_ALREADY_EXISTS.toString());
            assertEquals(response.getString("title"), Constants.ERR_TITLE_DOMAIN_EXISTS);
            assertEquals(response.getString("detail"), Constants.ERR_DETAIL_DOMAIN_EXISTS);
            testContext.completeNow();
          })));
    });
  }

  @Test
  @DisplayName("Test subdomain and existing domain w/ subdomain")
  void orgSubdomain(VertxTestContext testContext) {
    JsonObject userJson = adminAuthUser.result();

    String name = RandomStringUtils.randomAlphabetic(10);
    String mainDomain = name + ".com";
    String url = "mail." + mainDomain;

    JsonObject json = new JsonObject().put("url", url).put("name", name);
    CreateOrgRequest request = new CreateOrgRequest(json);

    json.clear().put("url", "subdomain." + url).put("name", name);
    CreateOrgRequest requestSub = new CreateOrgRequest(json);

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId")).roles(List.of(Roles.ADMIN))
        .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

    Promise<Void> p1 = Promise.promise();

    adminService.createOrganization(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getInteger("status"), 201);
          assertEquals(response.getString("type"), URN_SUCCESS.toString());
          assertEquals(response.getString("title"), Constants.SUCC_TITLE_CREATED_ORG);
          JsonObject result = response.getJsonObject("results");
          assertEquals(result.getString("name"), name);
          assertEquals(result.getString("url"), mainDomain.toLowerCase());
          assertTrue(result.getString("id").matches(UUID_REGEX));
          orgIds.add(UUID.fromString(result.getString("id")));
          p1.complete();
        })));

    p1.future().onSuccess(succ -> {
      adminService.createOrganization(requestSub, user,
          testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(response.getInteger("status"), 409);
            assertEquals(response.getString("type"), URN_ALREADY_EXISTS.toString());
            assertEquals(response.getString("title"), Constants.ERR_TITLE_DOMAIN_EXISTS);
            assertEquals(response.getString("detail"), Constants.ERR_DETAIL_DOMAIN_EXISTS);
            testContext.completeNow();
          })));
    });
  }
}
