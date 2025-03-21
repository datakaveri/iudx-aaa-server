package iudx.aaa.server.registration;

import static iudx.aaa.server.apiserver.util.Urn.*;
import static iudx.aaa.server.registration.Constants.CONFIG_COS_URL;
import static iudx.aaa.server.registration.Constants.CONFIG_OMITTED_SERVERS;
import static iudx.aaa.server.registration.Constants.SUCC_TITLE_RS_READ;
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
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;
import iudx.aaa.server.configuration.Configuration;
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

/** Unit tests for listing resource servers. */
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
  private static JsonObject options = new JsonObject();

  private static final String DUMMY_SERVER_ONE =
      "dummyone" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";
  private static final String DUMMY_SERVER_TWO =
      "dummytwo" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + "iudx.io";

  private static User adminOneUser =
      new UserBuilder()
          .userId(UUID.randomUUID())
          .roles(List.of(Roles.ADMIN))
          .rolesToRsMapping(Map.of(Roles.ADMIN.toString(), new JsonArray().add(DUMMY_SERVER_ONE)))
          .name("aa", "bb")
          .build();

  private static User adminTwoUser =
      new UserBuilder()
          .userId(UUID.randomUUID())
          .roles(List.of(Roles.ADMIN))
          .rolesToRsMapping(Map.of(Roles.ADMIN.toString(), new JsonArray().add(DUMMY_SERVER_ONE)))
          .name("aa", "bb")
          .build();

  private static Utils utils;

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
          new PgConnectOptions()
              .setPort(databasePort)
              .setHost(databaseIP)
              .setDatabase(databaseName)
              .setUser(databaseUserName)
              .setPassword(databasePassword)
              .setProperties(schemaProp);
    }

    if (poolOptions == null) {
      poolOptions = new PoolOptions().setMaxSize(poolSize);
    }

    pool = PgPool.pool(vertx, connectOptions, poolOptions);

    options
        .put(CONFIG_COS_URL, dbConfig.getString(CONFIG_COS_URL))
        .put(CONFIG_OMITTED_SERVERS, dbConfig.getJsonArray(CONFIG_OMITTED_SERVERS));

    utils = new Utils(pool);

    /* create fake resource servers */
    CompositeFuture.all(
            utils.createFakeResourceServer(DUMMY_SERVER_ONE, adminOneUser),
            utils.createFakeResourceServer(DUMMY_SERVER_TWO, adminTwoUser))
        .onSuccess(
            succ -> {
              registrationService = new RegistrationServiceImpl(pool, kc, tokenService, options);
              testContext.completeNow();
            })
        .onFailure(err -> testContext.failNow(err.getMessage()));
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing and resetting DB");
    utils
        .deleteFakeResourceServer()
        .onComplete(
            x -> {
              vertxObj.close(testContext.succeeding(response -> testContext.completeNow()));
            });
  }

  /* TODO add test if empty JSON details returned if org table empty. */
  @Test
  @DisplayName("Get added test resource servers")
  void getAddedResourceServer(VertxTestContext testContext) {

    Mockito.doAnswer(
            i -> {
              List<String> ids = i.getArgument(0);
              Map<String, JsonObject> resp = new HashMap<String, JsonObject>();

              // put an empty JSON object by default - since there may be many RS's on the test
              // server
              // and we can't mock info for each of them
              ids.forEach(
                  id -> {
                    resp.put(id, new JsonObject());
                  });

              resp.replace(adminOneUser.getUserId(), utils.getKcAdminJson(adminOneUser));
              resp.replace(adminTwoUser.getUserId(), utils.getKcAdminJson(adminTwoUser));

              return Future.succeededFuture(resp);
            })
        .when(kc)
        .getDetails(Mockito.anyList());

    registrationService
        .listResourceServer()
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(SUCC_TITLE_RS_READ, response.getString("title"));
                          assertEquals(URN_SUCCESS.toString(), response.getString("type"));

                          @SuppressWarnings("unchecked")
                          List<JsonObject> list = response.getJsonArray("results").getList();

                          Boolean oneExists =
                              list.stream()
                                  .anyMatch(
                                      obj -> {
                                        return (obj.getString("url").equals(DUMMY_SERVER_ONE)
                                            && obj.getJsonObject("owner")
                                                .getString("id")
                                                .equals(adminOneUser.getUserId()));
                                      });

                          Boolean twoExists =
                              list.stream()
                                  .anyMatch(
                                      obj -> {
                                        return (obj.getString("url").equals(DUMMY_SERVER_TWO)
                                            && obj.getJsonObject("owner")
                                                .getString("id")
                                                .equals(adminTwoUser.getUserId()));
                                      });

                          assertTrue(oneExists);
                          assertTrue(twoExists);
                          testContext.completeNow();
                        })));
  }
}
