package iudx.aaa.server.registration;

import static iudx.aaa.server.apiserver.util.Urn.*;
import static iudx.aaa.server.registration.Constants.CONFIG_AUTH_URL;
import static iudx.aaa.server.registration.Constants.CONFIG_OMITTED_SERVERS;
import static iudx.aaa.server.registration.Constants.SUCC_TITLE_RS_READ;
import static iudx.aaa.server.registration.Utils.SQL_CREATE_ADMIN_SERVER;
import static iudx.aaa.server.registration.Utils.SQL_DELETE_SERVERS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
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
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.policy.Constants.roles;
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
public class ListResourceServerTest {

  private static Logger LOGGER = LogManager.getLogger(ListResourceServerTest.class);

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

  private static final String DUMMY_SERVER_ONE =
      "dummyone" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";
  private static final String DUMMY_SERVER_TWO =
      "dummytwo" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + "iudx.io";

  private static Future<JsonObject> adminOneUser;
  private static Future<JsonObject> adminTwoUser;

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

    options.put(CONFIG_AUTH_URL, dbConfig.getString(CONFIG_AUTH_URL)).put(CONFIG_OMITTED_SERVERS,
        dbConfig.getJsonArray(CONFIG_OMITTED_SERVERS));

    adminOneUser = Utils.createFakeUser(pool, Constants.NIL_UUID, "",
        Map.of(Roles.ADMIN, RoleStatus.APPROVED), false);
    adminTwoUser = Utils.createFakeUser(pool, Constants.NIL_UUID, "",
        Map.of(Roles.ADMIN, RoleStatus.APPROVED), false);

    /* create fake resource servers */
    CompositeFuture.all(adminOneUser, adminTwoUser).compose(res -> {
      JsonObject admin1 = (JsonObject) res.list().get(0);
      UUID uid1 = UUID.fromString(admin1.getString("userId"));

      JsonObject admin2 = (JsonObject) res.list().get(1);
      UUID uid2 = UUID.fromString(admin2.getString("userId"));
      List<Tuple> tup = List.of(Tuple.of("Dummy Server One", uid1, DUMMY_SERVER_ONE),
          Tuple.of("Dummy Server Two", uid2, DUMMY_SERVER_TWO));

      return pool
          .withConnection(conn -> conn.preparedQuery(SQL_CREATE_ADMIN_SERVER).executeBatch(tup));
    }).onSuccess(succ -> {
      registrationService =
          new RegistrationServiceImpl(pool, kc, tokenService, policyService, options);
      testContext.completeNow();
    }).onFailure(err -> testContext.failNow(err.getMessage()));
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing and resetting DB");
    pool.withConnection(conn -> conn.preparedQuery(SQL_DELETE_SERVERS)
        .execute(Tuple.of(List.of(DUMMY_SERVER_TWO, DUMMY_SERVER_ONE).toArray()))).onComplete(x -> {
          vertxObj.close(testContext.succeeding(response -> testContext.completeNow()));
        });
  }

  /* TODO add test if empty JSON details returned if org table empty. */
  @Test
  @DisplayName("Get added test resource servers")
  void getAddedResourceServer(VertxTestContext testContext) {

    Mockito.doAnswer(i -> {
      List<String> ids = i.getArgument(0);
      Map<String, JsonObject> resp = new HashMap<String, JsonObject>();

      // put an empty JSON object by default - since there may be many RS's on the test server
      // and we can't mock info for each of them
      ids.forEach(id -> {
        resp.put(id, new JsonObject());
      });

      JsonObject userJson1 = adminOneUser.result();
      JsonObject details1 = new JsonObject().put("email", userJson1.getString("email")).put("name",
          new JsonObject().put("firstName", userJson1.getString("firstName")).put("lastName",
              userJson1.getString("lastName")));

      JsonObject userJson2 = adminTwoUser.result();
      JsonObject details2 = new JsonObject().put("email", userJson2.getString("email")).put("name",
          new JsonObject().put("firstName", userJson2.getString("firstName")).put("lastName",
              userJson2.getString("lastName")));

      resp.replace(adminOneUser.result().getString("userId"), details1);
      resp.replace(adminTwoUser.result().getString("userId"), details2);

      return Future.succeededFuture(resp);
    }).when(kc).getDetails(Mockito.anyList());

    registrationService
        .listResourceServer(testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(SUCC_TITLE_RS_READ, response.getString("title"));
          assertEquals(URN_SUCCESS.toString(), response.getString("type"));

          @SuppressWarnings("unchecked")
          List<JsonObject> list = response.getJsonArray("results").getList();

          Boolean oneExists = list.stream().anyMatch(obj -> {
            return (obj.getString("name").equals("Dummy Server One")
                && obj.getString("url").equals(DUMMY_SERVER_ONE) && obj.getJsonObject("owner")
                    .getString("id").equals(adminOneUser.result().getString("userId")));
          });

          Boolean twoExists = list.stream().anyMatch(obj -> {
            return (obj.getString("name").equals("Dummy Server Two")
                && obj.getString("url").equals(DUMMY_SERVER_TWO) && obj.getJsonObject("owner")
                    .getString("id").equals(adminTwoUser.result().getString("userId")));
          });

          assertTrue(oneExists);
          assertTrue(twoExists);
          testContext.completeNow();
        })));
  }
}
