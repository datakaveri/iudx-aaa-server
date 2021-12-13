package iudx.aaa.server.registration;

import static iudx.aaa.server.registration.Utils.SQL_CREATE_ORG;
import static iudx.aaa.server.registration.Utils.SQL_DELETE_ORG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

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
import iudx.aaa.server.apiserver.RoleStatus;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.configuration.Configuration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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
public class GetUserDetailsTest {
  private static Logger LOGGER = LogManager.getLogger(GetUserDetailsTest.class);

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

  static String name = RandomStringUtils.randomAlphabetic(10).toLowerCase();
  static String url = name + ".com";
  static Promise<UUID> orgId;

  static Future<JsonObject> userWithOrg;
  static Future<JsonObject> userNoOrg;
  static Future<UUID> orgIdFut;

  /* for converting getUserDetails's JsonObject to map */ 
  Function<JsonObject, Map<String, JsonObject>> jsonObjectToMap = (obj) -> {
    return obj.stream().collect(
        Collectors.toMap(val -> (String) val.getKey(), val -> (JsonObject) val.getValue()));
  };

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
  @DisplayName("Test invalid UUID sent")
  void invalidUuid(VertxTestContext testContext) {
    JsonObject userJson = userNoOrg.result();
    JsonObject details = new JsonObject().put("email", userJson.getString("email")).put("name",
        new JsonObject().put("firstName", userJson.getString("firstName")).put("lastName",
            userJson.getString("lastName")));

    /* when both checkpoints are flagged, the context auto completes */
    Checkpoint nulls = testContext.checkpoint();
    Checkpoint string = testContext.checkpoint();

    Map<String, JsonObject> resp = new HashMap<String, JsonObject>();
    resp.put(userJson.getString("keycloakId"), details);
    Mockito.when(kc.getDetails(any())).thenReturn(Future.succeededFuture(resp));

    registrationService.getUserDetails(List.of("12345678"),
        testContext.failing(response -> testContext.verify(() -> {
          assertEquals("Invalid UUID", response.getMessage());
          string.flag();
        })));

    List<String> list = new ArrayList<String>();
    list.add(null);
    list.add(userJson.getString("userId"));

    registrationService.getUserDetails(list,
        testContext.failing(response -> testContext.verify(() -> {
          assertEquals("Invalid UUID", response.getMessage());
          nulls.flag();
        })));
  }

  @Test
  @DisplayName("Test empty list")
  void emptyList(VertxTestContext testContext) {
    JsonObject userJson = userNoOrg.result();
    JsonObject details = new JsonObject().put("email", userJson.getString("email")).put("name",
        new JsonObject().put("firstName", userJson.getString("firstName")).put("lastName",
            userJson.getString("lastName")));

    Map<String, JsonObject> resp = new HashMap<String, JsonObject>();
    resp.put(userJson.getString("keycloakId"), details);
    Mockito.when(kc.getDetails(any())).thenReturn(Future.succeededFuture(resp));

    registrationService.getUserDetails(new ArrayList<String>(),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertTrue(response.isEmpty());
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test user ID not in DB")
  void nonExistentUser(VertxTestContext testContext) {
    JsonObject userJson = userNoOrg.result();
    JsonObject details = new JsonObject().put("email", userJson.getString("email")).put("name",
        new JsonObject().put("firstName", userJson.getString("firstName")).put("lastName",
            userJson.getString("lastName")));

    Map<String, JsonObject> resp = new HashMap<String, JsonObject>();
    resp.put(userJson.getString("keycloakId"), details);
    Mockito.when(kc.getDetails(any())).thenReturn(Future.succeededFuture(resp));

    registrationService.getUserDetails(List.of(UUID.randomUUID().toString()),
        testContext.failing(response -> testContext.verify(() -> {
          assertEquals("Invalid user ID", response.getMessage());
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test keycloak failure")
  void userEmailFail(VertxTestContext testContext) {
    JsonObject userJson = userNoOrg.result();
    Mockito.when(kc.getDetails(any())).thenReturn(Future.failedFuture("fail"));

    registrationService.getUserDetails(List.of(userJson.getString("userId")),
        testContext.failing(response -> testContext.verify(() -> {
          assertEquals("Internal error", response.getMessage());
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test successful get")
  void successfulGet(VertxTestContext testContext) {
    JsonObject userJson1 = userNoOrg.result();
    JsonObject details1 = new JsonObject().put("email", userJson1.getString("email")).put("name",
        new JsonObject().put("firstName", userJson1.getString("firstName")).put("lastName",
            userJson1.getString("lastName")));

    JsonObject userJson2 = userWithOrg.result();
    JsonObject details2 = new JsonObject().put("email", userJson2.getString("email")).put("name",
        new JsonObject().put("firstName", userJson2.getString("firstName")).put("lastName",
            userJson2.getString("lastName")));

    Map<String, JsonObject> resp = new HashMap<String, JsonObject>();
    resp.put(userJson1.getString("keycloakId"), details1);
    resp.put(userJson2.getString("keycloakId"), details2);

    Mockito.when(kc.getDetails(any())).thenReturn(Future.succeededFuture(resp));

    registrationService.getUserDetails(
        List.of(userJson1.getString("userId"), userJson2.getString("userId")),
        testContext.succeeding(jsonResult -> testContext.verify(() -> {
          
          Map<String, JsonObject> response = jsonObjectToMap.apply(jsonResult);
          
          JsonObject one = response.get(userJson1.getString("userId"));
          assertNotNull(one);
          assertEquals(one.getString("email"), userJson1.getString("email"));
          JsonObject name1 = one.getJsonObject("name");
          assertNotNull(name1);
          assertEquals(name1.getString("firstName"), userJson1.getString("firstName"));
          assertEquals(name1.getString("lastName"), userJson1.getString("lastName"));

          JsonObject two = response.get(userJson2.getString("userId"));
          assertNotNull(two);
          assertEquals(two.getString("email"), userJson2.getString("email"));
          JsonObject name2 = two.getJsonObject("name");
          assertNotNull(name2);
          assertEquals(name2.getString("firstName"), userJson2.getString("firstName"));
          assertEquals(name2.getString("lastName"), userJson2.getString("lastName"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test duplicate user IDs")
  void duplicateIds(VertxTestContext testContext) {
    JsonObject userJson = userNoOrg.result();
    JsonObject details = new JsonObject().put("email", userJson.getString("email")).put("name",
        new JsonObject().put("firstName", userJson.getString("firstName")).put("lastName",
            userJson.getString("lastName")));

    Map<String, JsonObject> resp = new HashMap<String, JsonObject>();
    resp.put(userJson.getString("keycloakId"), details);

    Mockito.when(kc.getDetails(any())).thenReturn(Future.succeededFuture(resp));

    registrationService.getUserDetails(
        List.of(userJson.getString("userId"), userJson.getString("userId")),
        testContext.succeeding(jsonResult -> testContext.verify(() -> {
          
          Map<String, JsonObject> response = jsonObjectToMap.apply(jsonResult);
          
          assertTrue(response.size() == 1);
          JsonObject obj = response.get(userJson.getString("userId"));
          assertNotNull(obj);
          assertEquals(obj.getString("email"), userJson.getString("email"));
          JsonObject name = obj.getJsonObject("name");
          assertNotNull(name);
          assertEquals(name.getString("firstName"), userJson.getString("firstName"));
          assertEquals(name.getString("lastName"), userJson.getString("lastName"));

          testContext.completeNow();
        })));
  }
}
