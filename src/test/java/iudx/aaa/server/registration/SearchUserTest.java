package iudx.aaa.server.registration;

import static iudx.aaa.server.apiserver.util.Urn.*;
import static iudx.aaa.server.registration.Constants.CONFIG_AUTH_URL;
import static iudx.aaa.server.registration.Constants.CONFIG_OMITTED_SERVERS;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_NO_USER_PROFILE;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_SEARCH_USR_INVALID_ROLE;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_USER_NOT_FOUND;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_NO_USER_PROFILE;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_SEARCH_USR_INVALID_ROLE;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_USER_NOT_FOUND;
import static iudx.aaa.server.registration.Constants.RESP_ORG;
import static iudx.aaa.server.registration.Constants.SUCC_TITLE_USER_FOUND;
import static iudx.aaa.server.policy.Constants.NO_AUTH_POLICY;
import static iudx.aaa.server.policy.Constants.NO_AUTH_ADMIN_POLICY;
import static iudx.aaa.server.registration.Utils.SQL_CREATE_ORG;
import static iudx.aaa.server.registration.Utils.SQL_DELETE_ORG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
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
import iudx.aaa.server.apiserver.util.ComposeException;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.policy.PolicyService;
import iudx.aaa.server.token.TokenService;
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
public class SearchUserTest {
  private static Logger LOGGER = LogManager.getLogger(SearchUserTest.class);

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
  private static PolicyService policyService = Mockito.mock(PolicyService.class);
  private static JsonObject options = new JsonObject();
  
  private static final String UUID_REGEX =
      "^[0-9a-f]{8}\\b-[0-9a-f]{4}\\b-[0-9a-f]{4}\\b-[0-9a-f]{4}\\b-[0-9a-f]{12}$";

  static String name = RandomStringUtils.randomAlphabetic(10).toLowerCase();
  static String url = name + ".com";
  static Promise<UUID> orgId;

  static Future<JsonObject> providerDeleg;
  static Future<JsonObject> consumerAdmin;
  static Future<JsonObject> trustee;
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

    options.put(CONFIG_AUTH_URL, dbConfig.getString(CONFIG_AUTH_URL)).put(CONFIG_OMITTED_SERVERS,
        dbConfig.getJsonArray(CONFIG_OMITTED_SERVERS));
    /*
     * create fake organization, and create 3 mock users. One user has an organization + phone
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

    Map<Roles, RoleStatus> rolesC = new HashMap<Roles, RoleStatus>();
    rolesC.put(Roles.TRUSTEE, RoleStatus.APPROVED);

    providerDeleg =
        orgIdFut.compose(id -> Utils.createFakeUser(pool, id.toString(), url, rolesA, true));
    consumerAdmin = Utils.createFakeUser(pool, Constants.NIL_UUID, "", rolesB, false);
    trustee = Utils.createFakeUser(pool, Constants.NIL_UUID, "", rolesC, false);

    CompositeFuture.all(providerDeleg, consumerAdmin, trustee).onSuccess(res -> {
      registrationService =
          new RegistrationServiceImpl(pool, kc, tokenService, policyService, options);
      testContext.completeNow();
    }).onFailure(err -> testContext.failNow(err.getMessage()));
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing and resetting DB");

    Utils
        .deleteFakeUser(pool,
            List.of(consumerAdmin.result(), providerDeleg.result(), trustee.result()))
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

    JsonObject userJson = providerDeleg.result();

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
  @DisplayName("Test search for the consumer as provider")
  void searchConsumerAsProvider(VertxTestContext testContext) {
    JsonObject userJson = providerDeleg.result();
    List<Roles> roles = List.of(Roles.DELEGATE, Roles.PROVIDER);

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId")).roles(roles)
        .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

    JsonObject consumerUser = consumerAdmin.result();
    JsonObject kcResult = new JsonObject().put("keycloakId", consumerUser.getString("keycloakId"))
        .put("email", consumerUser.getString("email"))
        .put("name", new JsonObject().put("firstName", consumerUser.getString("firstName"))
            .put("lastName", consumerUser.getString("lastName")));

    Mockito.when(kc.findUserByEmail(consumerUser.getString("email")))
        .thenReturn(Future.succeededFuture(kcResult));

    JsonObject searchUser = new JsonObject().put("email", consumerUser.getString("email"))
        .put("role", Roles.CONSUMER.toString().toLowerCase());

    registrationService.listUser(user, searchUser, new JsonObject(),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(200, response.getInteger("status"));
          assertEquals(SUCC_TITLE_USER_FOUND, response.getString("title"));
          assertEquals(URN_SUCCESS.toString(), response.getString("type"));

          JsonObject result = response.getJsonObject("results");

          JsonObject name = result.getJsonObject("name");
          assertEquals(name.getString("firstName"), consumerUser.getString("firstName"));
          assertEquals(name.getString("lastName"), consumerUser.getString("lastName"));

          assertTrue(result.getJsonObject(RESP_ORG) == null);
          assertEquals(result.getString("userId"), consumerUser.getString("userId"));

          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test search for the delegate as admin")
  void searchDelegateAsAdmin(VertxTestContext testContext) {
    JsonObject userJson = consumerAdmin.result();
    List<Roles> roles = List.of(Roles.CONSUMER, Roles.ADMIN);

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId")).roles(roles)
        .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

    JsonObject delegateUser = providerDeleg.result();
    JsonObject kcResult = new JsonObject().put("keycloakId", delegateUser.getString("keycloakId"))
        .put("email", delegateUser.getString("email"))
        .put("name", new JsonObject().put("firstName", delegateUser.getString("firstName"))
            .put("lastName", delegateUser.getString("lastName")));

    Mockito.when(kc.findUserByEmail(delegateUser.getString("email")))
        .thenReturn(Future.succeededFuture(kcResult));

    JsonObject searchUser = new JsonObject().put("email", delegateUser.getString("email"))
        .put("role", Roles.DELEGATE.toString().toLowerCase());

    registrationService.listUser(user, searchUser, new JsonObject(),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(200, response.getInteger("status"));
          assertEquals(SUCC_TITLE_USER_FOUND, response.getString("title"));
          assertEquals(URN_SUCCESS.toString(), response.getString("type"));

          JsonObject result = response.getJsonObject("results");

          JsonObject name = result.getJsonObject("name");
          assertEquals(name.getString("firstName"), delegateUser.getString("firstName"));
          assertEquals(name.getString("lastName"), delegateUser.getString("lastName"));

          JsonObject org = result.getJsonObject(RESP_ORG);
          assertEquals(org.getString("url"), delegateUser.getString("url"));
          assertEquals(result.getString("userId"), delegateUser.getString("userId"));

          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test search for the consumer as auth delegate")
  void searchConsumerAsDelegate(VertxTestContext testContext) {
    JsonObject userJson = providerDeleg.result();

    /*
     * NOTE We explicitly omit the Provider role from the User object to tes out the auth Delegate
     * flow. Also note that the user is not really an auth delegate. We simply add the
     * authDelegateDetails object with a providerId (the same user's ID itself since they have both
     * provider and delegate roles) in it as we do not check the content of the object in the
     * method, we only see if it's not empty and if user has delegate role.
     */
    List<Roles> roles = List.of(Roles.DELEGATE);

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId")).roles(roles)
        .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

    JsonObject consumerUser = consumerAdmin.result();
    JsonObject kcResult = new JsonObject().put("keycloakId", consumerUser.getString("keycloakId"))
        .put("email", consumerUser.getString("email"))
        .put("name", new JsonObject().put("firstName", consumerUser.getString("firstName"))
            .put("lastName", consumerUser.getString("lastName")));

    Mockito.when(kc.findUserByEmail(any())).thenReturn(Future.succeededFuture(kcResult));

    JsonObject searchUser = new JsonObject().put("email", consumerUser.getString("email"))
        .put("role", Roles.CONSUMER.toString().toLowerCase());

    JsonObject authDelegateDetails =
        new JsonObject().put("providerId", userJson.getString("userId"));

    registrationService.listUser(user, searchUser, authDelegateDetails,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(200, response.getInteger("status"));
          assertEquals(SUCC_TITLE_USER_FOUND, response.getString("title"));
          assertEquals(URN_SUCCESS.toString(), response.getString("type"));

          JsonObject result = response.getJsonObject("results");

          JsonObject name = result.getJsonObject("name");
          assertEquals(name.getString("firstName"), consumerUser.getString("firstName"));
          assertEquals(name.getString("lastName"), consumerUser.getString("lastName"));

          assertTrue(result.getJsonObject(RESP_ORG) == null);
          assertEquals(result.getString("userId"), consumerUser.getString("userId"));

          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test search as not auth delegate")
  void searchNotAuthDeleg(VertxTestContext testContext) {
    JsonObject userJson = providerDeleg.result();
    /* We omit provider role again. */

    List<Roles> roles = List.of(Roles.DELEGATE);

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId")).roles(roles)
        .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

    JsonObject consumerUser = consumerAdmin.result();
    JsonObject searchUser = new JsonObject().put("email", consumerUser.getString("email"))
        .put("role", Roles.CONSUMER.toString().toLowerCase());

    registrationService.listUser(user, searchUser, new JsonObject(),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(401, response.getInteger("status"));
          assertEquals(ERR_TITLE_SEARCH_USR_INVALID_ROLE, response.getString("title"));
          assertEquals(ERR_DETAIL_SEARCH_USR_INVALID_ROLE, response.getString("detail"));
          assertEquals(URN_INVALID_ROLE.toString(), response.getString("type"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test search as consumer")
  void searchAsConsumer(VertxTestContext testContext) {

    JsonObject userJson = consumerAdmin.result();
    /* We omit the admin role. */
    List<Roles> roles = List.of(Roles.CONSUMER);

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId")).roles(roles)
        .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

    JsonObject delegateUser = providerDeleg.result();
    JsonObject searchUser = new JsonObject().put("email", delegateUser.getString("email"))
        .put("role", Roles.DELEGATE.toString().toLowerCase());

    registrationService.listUser(user, searchUser, new JsonObject(),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(401, response.getInteger("status"));
          assertEquals(ERR_TITLE_SEARCH_USR_INVALID_ROLE, response.getString("title"));
          assertEquals(ERR_DETAIL_SEARCH_USR_INVALID_ROLE, response.getString("detail"));
          assertEquals(URN_INVALID_ROLE.toString(), response.getString("type"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test search - user not on keycloak")
  void searchNotOnKc(VertxTestContext testContext) {
    JsonObject userJson = providerDeleg.result();
    List<Roles> roles = List.of(Roles.DELEGATE, Roles.PROVIDER);

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId")).roles(roles)
        .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

    String email = "email@email.com";
    Mockito.when(kc.findUserByEmail(email)).thenReturn(Future.succeededFuture(new JsonObject()));

    JsonObject searchUser =
        new JsonObject().put("email", email).put("role", Roles.CONSUMER.toString().toLowerCase());

    registrationService.listUser(user, searchUser, new JsonObject(),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(404, response.getInteger("status"));
          assertEquals(ERR_TITLE_USER_NOT_FOUND, response.getString("title"));
          assertEquals(ERR_DETAIL_USER_NOT_FOUND, response.getString("detail"));
          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test search - user does not have a user profile")
  void searchNoUserProfile(VertxTestContext testContext) {
    JsonObject userJson = providerDeleg.result();
    List<Roles> roles = List.of(Roles.DELEGATE, Roles.PROVIDER);

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId")).roles(roles)
        .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

    String email = "email@email.com";
    JsonObject kcResult = new JsonObject().put("keycloakId", UUID.randomUUID().toString())
        .put("email", email).put("name",
            new JsonObject().put("firstName", RandomStringUtils.randomAlphabetic(10).toLowerCase())
                .put("lastName", RandomStringUtils.randomAlphabetic(10).toLowerCase()));

    Mockito.when(kc.findUserByEmail(email)).thenReturn(Future.succeededFuture(kcResult));

    JsonObject searchUser =
        new JsonObject().put("email", email).put("role", Roles.CONSUMER.toString().toLowerCase());

    registrationService.listUser(user, searchUser, new JsonObject(),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(404, response.getInteger("status"));
          assertEquals(ERR_TITLE_USER_NOT_FOUND, response.getString("title"));
          assertEquals(ERR_DETAIL_USER_NOT_FOUND, response.getString("detail"));
          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test search - user does not have requested role")
  void searchNoRole(VertxTestContext testContext) {
    JsonObject userJson = providerDeleg.result();
    List<Roles> roles = List.of(Roles.DELEGATE, Roles.PROVIDER);

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId")).roles(roles)
        .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

    JsonObject consumerUser = consumerAdmin.result();
    JsonObject kcResult = new JsonObject().put("keycloakId", consumerUser.getString("keycloakId"))
        .put("email", consumerUser.getString("email"))
        .put("name", new JsonObject().put("firstName", consumerUser.getString("firstName"))
            .put("lastName", consumerUser.getString("lastName")));

    Mockito.when(kc.findUserByEmail(consumerUser.getString("email")))
        .thenReturn(Future.succeededFuture(kcResult));

    JsonObject searchUser = new JsonObject().put("email", consumerUser.getString("email"))
        .put("role", Roles.DELEGATE.toString().toLowerCase());

    registrationService.listUser(user, searchUser, new JsonObject(),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(404, response.getInteger("status"));
          assertEquals(ERR_TITLE_USER_NOT_FOUND, response.getString("title"));
          assertEquals(ERR_DETAIL_USER_NOT_FOUND, response.getString("detail"));
          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test search - trustee does not have auth admin policy")
  void searchTrusteeNoAuthAdminPolicy(VertxTestContext testContext) {
    JsonObject userJson = trustee.result();
    List<Roles> roles = List.of(Roles.TRUSTEE);

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId")).roles(roles)
        .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

    Mockito.doAnswer(i -> {
      Promise<Void> p = i.getArgument(1);
      p.fail(new ComposeException(403, URN_INVALID_INPUT, NO_AUTH_POLICY, NO_AUTH_ADMIN_POLICY));
      return i.getMock();
    }).when(policyService).checkAuthPolicy(Mockito.eq(userJson.getString("userId")), any());

    JsonObject consumerUser = consumerAdmin.result();

    JsonObject searchUser = new JsonObject().put("email", consumerUser.getString("email"))
        .put("role", Roles.CONSUMER.toString().toLowerCase());
    
    registrationService.listUser(user, searchUser, new JsonObject(),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(403, response.getInteger("status"));
          assertEquals(NO_AUTH_POLICY, response.getString("title"));
          assertEquals(NO_AUTH_ADMIN_POLICY, response.getString("detail"));
          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test search - trustee finds consumer successfully")
  void searchTrusteeFindConsumer(VertxTestContext testContext) {
    JsonObject userJson = trustee.result();
    List<Roles> roles = List.of(Roles.TRUSTEE);

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId")).roles(roles)
        .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

    JsonObject consumerUser = consumerAdmin.result();

    Mockito.doAnswer(i -> {
      Promise<Void> p = i.getArgument(1);
      p.complete();
      return i.getMock();
    }).when(policyService).checkAuthPolicy(Mockito.eq(userJson.getString("userId")), any());

    JsonObject kcResult = new JsonObject().put("keycloakId", consumerUser.getString("keycloakId"))
        .put("email", consumerUser.getString("email"))
        .put("name", new JsonObject().put("firstName", consumerUser.getString("firstName"))
            .put("lastName", consumerUser.getString("lastName")));

    Mockito.when(kc.findUserByEmail(consumerUser.getString("email")))
        .thenReturn(Future.succeededFuture(kcResult));

    JsonObject searchUser = new JsonObject().put("email", consumerUser.getString("email"))
        .put("role", Roles.CONSUMER.toString().toLowerCase());
    
    registrationService.listUser(user, searchUser, new JsonObject(),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(200, response.getInteger("status"));
          assertEquals(SUCC_TITLE_USER_FOUND, response.getString("title"));
          assertEquals(URN_SUCCESS.toString(), response.getString("type"));

          JsonObject result = response.getJsonObject("results");

          JsonObject name = result.getJsonObject("name");
          assertEquals(name.getString("firstName"), consumerUser.getString("firstName"));
          assertEquals(name.getString("lastName"), consumerUser.getString("lastName"));

          assertTrue(result.getJsonObject(RESP_ORG) == null);
          assertEquals(result.getString("userId"), consumerUser.getString("userId"));

          testContext.completeNow();
        })));
  }
}
