package iudx.aaa.server.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import java.util.List;
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
import iudx.aaa.server.apiserver.RegistrationRequest;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;
import iudx.aaa.server.configuration.Configuration;

@ExtendWith(VertxExtension.class)
public class CreateUserTest {
  private static Logger LOGGER = LogManager.getLogger(CreateUserTest.class);

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
  private static RegistrationService registrationService;
  private static Vertx vertxObj;

  private static KcAdmin kc = Mockito.mock(KcAdmin.class);
  private static final String UUID_REGEX =
      "^[0-9a-f]{8}\\b-[0-9a-f]{4}\\b-[0-9a-f]{4}\\b-[0-9a-f]{4}\\b-[0-9a-f]{12}$";

  /* SQL queries for creating and deleting required data */
  private static final String SQL_CREATE_ORG =
      "INSERT INTO test.organizations (name, url, created_at, updated_at) "
          + "VALUES ($1:: text, $2::text, NOW(), NOW()) RETURNING id";
  private static final String SQL_DELETE_USRS =
      "DELETE FROM test.users WHERE organization_id = $1::uuid";
  private static final String SQL_DELETE_ORG = "DELETE FROM test.organizations WHERE id = $1::uuid";
  private static final String SQL_DELETE_CONSUMERS =
      "DELETE FROM test.users WHERE email_hash LIKE $1::text || '%'";

  static String name = RandomStringUtils.randomAlphabetic(10).toLowerCase();
  static String url = name + ".com";
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
    databaseUserName = dbConfig.getString("databaseUserName");
    databasePassword = dbConfig.getString("databasePassword");
    poolSize = Integer.parseInt(dbConfig.getString("poolSize"));

    if (connectOptions == null) {
      connectOptions = new PgConnectOptions().setPort(databasePort).setHost(databaseIP)
          .setDatabase(databaseName).setUser(databaseUserName).setPassword(databasePassword);
    }

    if (poolOptions == null) {
      poolOptions = new PoolOptions().setMaxSize(poolSize);
    }

    pool = PgPool.pool(vertx, connectOptions, poolOptions);

    /* create fake organization */
    orgIdFut =
        pool.withConnection(conn -> conn.preparedQuery(SQL_CREATE_ORG).execute(Tuple.of(name, url))
            .map(row -> row.iterator().next().getUUID("id"))).onSuccess(err -> {
              registrationService = new RegistrationServiceImpl(pool, kc);
              testContext.completeNow();
            }).onFailure(err -> testContext.failNow(err.getMessage()));
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing and resetting DB");
    pool.withConnection(
        conn -> conn.preparedQuery(SQL_DELETE_USRS).execute(Tuple.of(orgIdFut.result()))
            .compose(res -> conn.preparedQuery(SQL_DELETE_ORG).execute(Tuple.of(orgIdFut.result())))
            .compose(success -> conn.preparedQuery(SQL_DELETE_CONSUMERS).execute(Tuple.of(url))))
        .onComplete(x -> {
          vertxObj.close(testContext.succeeding(response -> testContext.completeNow()));
        });
  }

  @Test
  @DisplayName("JSON body validation")
  void validations(VertxTestContext testContext) {

    JsonObject empty = new JsonObject();
    assertThrows(IllegalArgumentException.class, () -> new RegistrationRequest(empty));

    JsonObject invalidRole = new JsonObject().put("roles", new JsonArray().add("hello"));
    assertThrows(IllegalArgumentException.class, () -> new RegistrationRequest(invalidRole));

    JsonObject adminRole = new JsonObject().put("roles", new JsonArray().add("admin"));
    assertThrows(IllegalArgumentException.class, () -> new RegistrationRequest(adminRole));

    JsonObject stringRole = new JsonObject().put("roles", "admin");
    assertThrows(IllegalArgumentException.class, () -> new RegistrationRequest(stringRole));

    JsonObject badRoleArr =
        new JsonObject().put("roles", new JsonArray().add(1).add(new JsonObject()));
    assertThrows(IllegalArgumentException.class, () -> new RegistrationRequest(badRoleArr));

    JsonObject invalidPhone =
        new JsonObject().put("roles", new JsonArray().add("consumer")).put("phone", "665544");
    assertThrows(IllegalArgumentException.class, () -> new RegistrationRequest(invalidPhone));

    JsonObject numPhone =
        new JsonObject().put("roles", new JsonArray().add("consumer")).put("phone", 9845598);
    assertThrows(IllegalArgumentException.class, () -> new RegistrationRequest(numPhone));

    JsonObject orgNum =
        new JsonObject().put("roles", new JsonArray().add("provider")).put("orgId", 1234);
    assertThrows(IllegalArgumentException.class, () -> new RegistrationRequest(orgNum));

    JsonObject orgArr = new JsonObject().put("roles", new JsonArray().add("provider")).put("orgId",
        new JsonArray());
    assertThrows(IllegalArgumentException.class, () -> new RegistrationRequest(orgArr));

    JsonObject orgInvalid = new JsonObject().put("roles", new JsonArray().add("provider"))
        .put("orgId", "107f8479-e767-4760-ac5f-d4518ebe3a8");
    assertThrows(IllegalArgumentException.class, () -> new RegistrationRequest(orgInvalid));
    testContext.completeNow();
  }


  @Test
  @DisplayName("Test successful consumer registration")
  void createConsumerSuccess(VertxTestContext testContext) {

    String email = RandomStringUtils.randomAlphabetic(5).toLowerCase() + "@" + url;
    String keycloakId = UUID.randomUUID().toString();

    Mockito.when(kc.modifyRoles(any(), any())).thenReturn(Future.succeededFuture());
    Mockito.when(kc.getEmailId(any())).thenReturn(Future.succeededFuture(email));

    JsonObject jsonReq =
        new JsonObject().put("roles", new JsonArray().add("Consumer")).put("phone", "9989989980");
    RegistrationRequest request = new RegistrationRequest(jsonReq);

    User user = new UserBuilder().keycloakId(keycloakId).name("Foo", "Bar").build();

    registrationService.createUser(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(201, response.getInteger("status"));
          JsonObject result = response.getJsonObject("results");

          assertEquals(Constants.URN_SUCCESS, response.getString("type"));
          assertEquals(Constants.SUCC_TITLE_CREATED_USER, response.getString("title"));
          assertTrue(result.getJsonArray("roles").getList().contains(Roles.CONSUMER.name()));
          assertEquals(result.getString("email"), email);
          assertEquals(result.getString("keycloakId"), keycloakId);
          assertTrue(result.getString("userId").matches(UUID_REGEX));
          assertEquals(result.getJsonObject("name").getString("firstName"), "Foo");
          assertEquals(result.getJsonObject("name").getString("lastName"), "Bar");
          assertEquals(result.getString("phone"), "9989989980");
          assertTrue(!result.containsKey("organization"));

          JsonObject client = result.getJsonArray("clients").getJsonObject(0);
          assertTrue(client.getString("clientId").matches(UUID_REGEX));
          assertTrue(client.getString("clientSecret").matches(UUID_REGEX));
          assertEquals(client.getString("client"), Constants.DEFAULT_CLIENT);

          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test successful provider registration")
  void createProviderSuccess(VertxTestContext testContext) {

    String orgId = orgIdFut.result().toString();
    String email = RandomStringUtils.randomAlphabetic(5).toLowerCase() + "@" + url;
    String keycloakId = UUID.randomUUID().toString();

    Mockito.when(kc.modifyRoles(any(), any())).thenReturn(Future.succeededFuture());
    Mockito.when(kc.getEmailId(any())).thenReturn(Future.succeededFuture(email));

    JsonObject jsonReq = new JsonObject().put("roles", new JsonArray().add("provider"))
        .put("orgId", orgId).put("phone", "9989989980");
    RegistrationRequest request = new RegistrationRequest(jsonReq);

    User user = new UserBuilder().keycloakId(keycloakId).name("Foo", "Bar").build();

    registrationService.createUser(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(201, response.getInteger("status"));
          JsonObject result = response.getJsonObject("results");

          assertEquals(Constants.URN_SUCCESS, response.getString("type"));
          assertEquals(Constants.SUCC_TITLE_CREATED_USER + Constants.PROVIDER_PENDING_MESG,
              response.getString("title"));
          assertTrue(result.getJsonArray("roles").size() == 0);
          assertEquals(result.getString("email"), email);
          assertEquals(result.getString("keycloakId"), keycloakId);
          assertTrue(result.getString("userId").matches(UUID_REGEX));
          assertEquals(result.getJsonObject("name").getString("firstName"), "Foo");
          assertEquals(result.getJsonObject("name").getString("lastName"), "Bar");
          assertEquals(result.getString("phone"), "9989989980");
          assertEquals(result.getJsonObject("organization").getString("url"), url);

          JsonObject client = result.getJsonArray("clients").getJsonObject(0);
          assertTrue(client.getString("clientId").matches(UUID_REGEX));
          assertTrue(client.getString("clientSecret").matches(UUID_REGEX));
          assertEquals(client.getString("client"), Constants.DEFAULT_CLIENT);

          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test successful delegate registration")
  void createDelegateSuccess(VertxTestContext testContext) {

    String orgId = orgIdFut.result().toString();
    String email = RandomStringUtils.randomAlphabetic(5).toLowerCase() + "@" + url;
    String keycloakId = UUID.randomUUID().toString();

    Mockito.when(kc.modifyRoles(any(), any())).thenReturn(Future.succeededFuture());
    Mockito.when(kc.getEmailId(any())).thenReturn(Future.succeededFuture(email));

    JsonObject jsonReq = new JsonObject().put("roles", new JsonArray().add("delegate"))
        .put("orgId", orgId);
    RegistrationRequest request = new RegistrationRequest(jsonReq);

    User user = new UserBuilder().keycloakId(keycloakId).name("Foo", "Bar").build();

    registrationService.createUser(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(201, response.getInteger("status"));

          JsonObject result = response.getJsonObject("results");

          assertEquals(Constants.URN_SUCCESS, response.getString("type"));
          assertEquals(Constants.SUCC_TITLE_CREATED_USER, response.getString("title"));
          assertEquals(result.getJsonArray("roles"), new JsonArray().add(Roles.DELEGATE.name()));
          assertEquals(result.getString("email"), email);
          assertEquals(result.getString("keycloakId"), keycloakId);
          assertTrue(result.getString("userId").matches(UUID_REGEX));
          assertEquals(result.getJsonObject("name").getString("firstName"), "Foo");
          assertEquals(result.getJsonObject("name").getString("lastName"), "Bar");
          assertTrue(!result.containsKey("phone"));
          assertEquals(result.getJsonObject("organization").getString("url"), url);

          JsonObject client = result.getJsonArray("clients").getJsonObject(0);
          assertTrue(client.getString("clientId").matches(UUID_REGEX));
          assertTrue(client.getString("clientSecret").matches(UUID_REGEX));
          assertEquals(client.getString("client"), Constants.DEFAULT_CLIENT);

          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test successful registration of all roles")
  void allRolesRegister(VertxTestContext testContext) {

    String orgId = orgIdFut.result().toString();
    String email = RandomStringUtils.randomAlphabetic(5).toLowerCase() + "@" + url;
    String keycloakId = UUID.randomUUID().toString();

    Mockito.when(kc.modifyRoles(any(), any())).thenReturn(Future.succeededFuture());
    Mockito.when(kc.getEmailId(any())).thenReturn(Future.succeededFuture(email));

    JsonObject jsonReq = new JsonObject()
        .put("roles", new JsonArray().add("delegate").add("provider").add("consumer"))
        .put("orgId", orgId).put("phone", "9989989980");
    RegistrationRequest request = new RegistrationRequest(jsonReq);

    User user = new UserBuilder().keycloakId(keycloakId).name("Foo", "Bar").build();

    registrationService.createUser(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(201, response.getInteger("status"));

          JsonObject result = response.getJsonObject("results");
          
          assertEquals(Constants.URN_SUCCESS, response.getString("type"));
          assertEquals(Constants.SUCC_TITLE_CREATED_USER + Constants.PROVIDER_PENDING_MESG,
              response.getString("title"));
          List<String> roles = result.getJsonArray("roles").getList();
          assertTrue(roles.containsAll(List.of(Roles.DELEGATE.name(), Roles.CONSUMER.name())));

          assertEquals(result.getString("email"), email);
          assertEquals(result.getString("keycloakId"), keycloakId);
          assertTrue(result.getString("userId").matches(UUID_REGEX));
          assertEquals(result.getJsonObject("name").getString("firstName"), "Foo");
          assertEquals(result.getJsonObject("name").getString("lastName"), "Bar");
          assertEquals(result.getString("phone"), "9989989980");
          assertEquals(result.getJsonObject("organization").getString("url"), url);

          JsonObject client = result.getJsonArray("clients").getJsonObject(0);
          assertTrue(client.getString("clientId").matches(UUID_REGEX));
          assertTrue(client.getString("clientSecret").matches(UUID_REGEX));
          assertEquals(client.getString("client"), Constants.DEFAULT_CLIENT);

          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Testing Invalid organization")
  void invalidOrg(VertxTestContext testContext) {
    String orgId = UUID.randomUUID().toString();
    String email = RandomStringUtils.randomAlphabetic(5).toLowerCase() + "@" + url;
    String keycloakId = UUID.randomUUID().toString();

    Mockito.when(kc.modifyRoles(any(), any())).thenReturn(Future.succeededFuture());
    Mockito.when(kc.getEmailId(any())).thenReturn(Future.succeededFuture(email));

    JsonObject jsonReq =
        new JsonObject().put("roles", new JsonArray().add("provider")).put("orgId", orgId);
    RegistrationRequest request = new RegistrationRequest(jsonReq);

    User user = new UserBuilder().keycloakId(keycloakId).name("Foo", "Bar").build();

    registrationService.createUser(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(Constants.URN_INVALID_INPUT, response.getString("type"));
          assertEquals(400, response.getInteger("status"));
          assertEquals(Constants.ERR_DETAIL_ORG_NO_EXIST, response.getString("detail"));
          assertEquals(Constants.ERR_TITLE_ORG_NO_EXIST, response.getString("title"));

          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Testing no organization ID for provider/delegate reg")
  void noOrgForProviderReg(VertxTestContext testContext) {
    String email = RandomStringUtils.randomAlphabetic(5).toLowerCase() + "@" + url;
    String keycloakId = UUID.randomUUID().toString();

    Mockito.when(kc.modifyRoles(any(), any())).thenReturn(Future.succeededFuture());
    Mockito.when(kc.getEmailId(any())).thenReturn(Future.succeededFuture(email));

    JsonObject jsonReq =
        new JsonObject().put("roles", new JsonArray().add("provider").add("delegate"));
    RegistrationRequest request = new RegistrationRequest(jsonReq);

    User user = new UserBuilder().keycloakId(keycloakId).name("Foo", "Bar").build();

    registrationService.createUser(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(Constants.URN_MISSING_INFO, response.getString("type"));
          assertEquals(400, response.getInteger("status"));
          assertEquals(Constants.ERR_DETAIL_ORG_ID_REQUIRED, response.getString("detail"));
          assertEquals(Constants.ERR_TITLE_ORG_ID_REQUIRED, response.getString("title"));

          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test org-domain mismatch")
  void orgDomainNotMatch(VertxTestContext testContext) {

    String orgId = orgIdFut.result().toString();
    String keycloakId = UUID.randomUUID().toString();

    Mockito.when(kc.modifyRoles(any(), any())).thenReturn(Future.succeededFuture());
    Mockito.when(kc.getEmailId(any())).thenReturn(Future.succeededFuture("random@email.com"));

    JsonObject jsonReq =
        new JsonObject().put("roles", new JsonArray().add("provider")).put("orgId", orgId);
    RegistrationRequest request = new RegistrationRequest(jsonReq);

    User user = new UserBuilder().keycloakId(keycloakId).name("Foo", "Bar").build();
    registrationService.createUser(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(Constants.URN_INVALID_INPUT, response.getString("type"));
          assertEquals(400, response.getInteger("status"));
          assertEquals(Constants.ERR_DETAIL_ORG_NO_MATCH, response.getString("detail"));
          assertEquals(Constants.ERR_TITLE_ORG_NO_MATCH, response.getString("title"));

          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test existing user profile")
  void existingUserProfile(VertxTestContext testContext) {

    String orgId = orgIdFut.result().toString();
    String email = RandomStringUtils.randomAlphabetic(5).toLowerCase() + "@" + url;
    String keycloakId = UUID.randomUUID().toString();

    Mockito.when(kc.modifyRoles(any(), any())).thenReturn(Future.succeededFuture());
    Mockito.when(kc.getEmailId(any())).thenReturn(Future.succeededFuture(email));

    JsonObject jsonReq =
        new JsonObject().put("roles", new JsonArray().add("provider")).put("orgId", orgId);
    RegistrationRequest request = new RegistrationRequest(jsonReq);

    User user = new UserBuilder().keycloakId(keycloakId).name("Foo", "Bar").build();

    Promise<Void> completeReg = Promise.promise();
    registrationService.createUser(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(Constants.URN_SUCCESS, response.getString("type"));
          assertEquals(201, response.getInteger("status"));
          completeReg.complete();
        })));

    completeReg.future().onSuccess(i -> {

      registrationService.createUser(request, user,
          testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(Constants.URN_ALREADY_EXISTS, response.getString("type"));
            assertEquals(409, response.getInteger("status"));
            assertEquals(Constants.ERR_DETAIL_USER_EXISTS, response.getString("detail"));
            assertEquals(Constants.ERR_TITLE_USER_EXISTS, response.getString("title"));
            testContext.completeNow();
          })));
    });
  }

  @Test
  @DisplayName("Test email/user ID not found on Keycloak - should never happen")
  void userNotFoundOnKc(VertxTestContext testContext) {

    String orgId = orgIdFut.result().toString();
    String keycloakId = UUID.randomUUID().toString();

    Mockito.when(kc.modifyRoles(any(), any())).thenReturn(Future.succeededFuture());
    Mockito.when(kc.getEmailId(any())).thenReturn(Future.succeededFuture(""));

    JsonObject jsonReq =
        new JsonObject().put("roles", new JsonArray().add("provider")).put("orgId", orgId);
    RegistrationRequest request = new RegistrationRequest(jsonReq);

    User user = new UserBuilder().keycloakId(keycloakId).name("Foo", "Bar").build();

    registrationService.createUser(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(Constants.URN_INVALID_INPUT, response.getString("type"));
          assertEquals(400, response.getInteger("status"));
          assertEquals(Constants.ERR_DETAIL_USER_NOT_KC, response.getString("detail"));
          assertEquals(Constants.ERR_TITLE_USER_NOT_KC, response.getString("title"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test transaction rollback if Keycloak fails")
  void modifyRolesFail(VertxTestContext testContext) {

    String email = RandomStringUtils.randomAlphabetic(5).toLowerCase() + "@" + url;
    String keycloakId = UUID.randomUUID().toString();

    Mockito.when(kc.getEmailId(any())).thenReturn(Future.succeededFuture(email));
    Mockito.when(kc.modifyRoles(any(), any())).thenReturn(Future.failedFuture("fail"));

    JsonObject jsonReq = new JsonObject().put("roles", new JsonArray().add("consumer"));
    RegistrationRequest request = new RegistrationRequest(jsonReq);

    User user = new UserBuilder().keycloakId(keycloakId).name("Foo", "Bar").build();

    Promise<Void> failed = Promise.promise();
    registrationService.createUser(request, user,
        testContext.failing(response -> testContext.verify(() -> {
          failed.complete();
        })));

    failed.future().onSuccess(i -> {
      Mockito.when(kc.modifyRoles(any(), any())).thenReturn(Future.succeededFuture());

      registrationService.createUser(request, user,
          testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(Constants.URN_SUCCESS, response.getString("type"));
            assertEquals(201, response.getInteger("status"));
            testContext.completeNow();
          })));
    });
  }
}
