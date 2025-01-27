package iudx.aaa.server.registration;

import static iudx.aaa.server.registration.Constants.CONFIG_COS_URL;
import static iudx.aaa.server.registration.Constants.CONFIG_OMITTED_SERVERS;
import static iudx.aaa.server.registration.Constants.ERR_CONTEXT_NOT_FOUND_EMAILS;
import static iudx.aaa.server.registration.Constants.ERR_DETAIL_EMAILS_NOT_AT_UAC_KEYCLOAK;
import static iudx.aaa.server.registration.Constants.ERR_TITLE_EMAILS_NOT_AT_UAC_KEYCLOAK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import io.vertx.core.Future;
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
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;
import iudx.aaa.server.apiserver.util.ComposeException;
import iudx.aaa.server.apiserver.util.Urn;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.token.TokenService;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

/** Unit tests for finding user by email. */
@ExtendWith(VertxExtension.class)
public class FindUserByEmailTest {
  private static Logger LOGGER = LogManager.getLogger(FindUserByEmailTest.class);

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

  private static final String DUMMY_SERVER =
      "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

  static User user =
      new UserBuilder()
          .userId(UUID.randomUUID())
          .roles(List.of(Roles.CONSUMER, Roles.PROVIDER))
          .rolesToRsMapping(
              Map.of(
                  Roles.CONSUMER.toString(),
                  new JsonArray().add(DUMMY_SERVER),
                  Roles.PROVIDER.toString(),
                  new JsonArray().add(DUMMY_SERVER)))
          .name("aa", "bb")
          .build();

  private static Utils utils;

  /*
   * for converting getUserDetails's JsonObject output to a Map to make it easier to assert values
   */
  Function<JsonObject, Map<String, JsonObject>> jsonObjectToMap =
      (obj) -> {
        return obj.stream()
            .collect(
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

    utils
        .createFakeResourceServer(DUMMY_SERVER, new UserBuilder().userId(UUID.randomUUID()).build())
        .compose(sss -> utils.createFakeUser(user, false, false))
        .onSuccess(
            res -> {
              registrationService = new RegistrationServiceImpl(pool, kc, tokenService, options);
              testContext.completeNow();
            })
        .onFailure(err -> testContext.failNow(err.getMessage()));
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing and resetting DB");
    utils
        .deleteFakeUser()
        .compose(res -> utils.deleteFakeResourceServer())
        .onComplete(
            x -> {
              if (x.failed()) {
                LOGGER.warn(x.cause().getMessage());
              }
              vertxObj.close(testContext.succeeding(response -> testContext.completeNow()));
            });
  }

  @Test
  @DisplayName("Test empty set")
  void emptyList(VertxTestContext testContext) {
    JsonObject userDetails = utils.getKcAdminJson(user);
    Mockito.when(kc.findUserByEmail(any())).thenReturn(Future.succeededFuture(userDetails));

    registrationService
        .findUserByEmail(Set.of())
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertTrue(response.isEmpty());
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Test email does not exist at UAC keycloak")
  void nonExistentUser(VertxTestContext testContext) {

    String notRegdEmail = RandomStringUtils.randomAlphabetic(10) + "@gmail.com";

    Mockito.when(kc.findUserByEmail(notRegdEmail))
        .thenReturn(Future.succeededFuture(new JsonObject()));
    Mockito.when(kc.findUserByEmail(utils.getDetails(user).email))
        .thenReturn(Future.succeededFuture(utils.getKcAdminJson(user)));

    registrationService
        .findUserByEmail(Set.of(utils.getDetails(user).email, notRegdEmail))
        .onComplete(
            testContext.failing(
                err ->
                    testContext.verify(
                        () -> {
                          assertTrue(err instanceof ComposeException);
                          ComposeException composeErr = (ComposeException) err;
                          JsonObject jsonResult = composeErr.getResponse().toJson();

                          assertEquals(jsonResult.getInteger("status"), 400);
                          assertEquals(
                              jsonResult.getString("type"), Urn.URN_INVALID_INPUT.toString());
                          assertEquals(
                              jsonResult.getString("title"), ERR_TITLE_EMAILS_NOT_AT_UAC_KEYCLOAK);
                          assertEquals(
                              jsonResult.getString("detail"),
                              ERR_DETAIL_EMAILS_NOT_AT_UAC_KEYCLOAK);

                          JsonArray missingEmails =
                              jsonResult
                                  .getJsonObject("context")
                                  .getJsonArray(ERR_CONTEXT_NOT_FOUND_EMAILS);
                          assertTrue(missingEmails.contains(notRegdEmail));

                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Test keycloak failure")
  void userEmailFail(VertxTestContext testContext) {
    Mockito.when(kc.findUserByEmail(any())).thenReturn(Future.failedFuture("fail"));

    registrationService
        .findUserByEmail(Set.of(utils.getDetails(user).email))
        .onComplete(
            testContext.failing(
                response ->
                    testContext.verify(
                        () -> {
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Test getting email of user already in DB")
  void findingUserAlreadyOnDb(VertxTestContext testContext) {
    Mockito.when(kc.findUserByEmail(utils.getDetails(user).email))
        .thenReturn(Future.succeededFuture(utils.getKcAdminJson(user)));

    Checkpoint userExistsInDb = testContext.checkpoint();
    Checkpoint dataObtained = testContext.checkpoint();

    pool.withConnection(
            conn ->
                conn.preparedQuery("SELECT id FROM users WHERE id = $1::UUID")
                    .execute(Tuple.of(user.getUserId())))
        .compose(
            row -> {
              if (row.rowCount() == 1) {
                userExistsInDb.flag();
              } else {
                testContext.failNow("failed");
              }
              return Future.succeededFuture();
            })
        .map(
            next ->
                registrationService
                    .findUserByEmail(Set.of(utils.getDetails(user).email))
                    .onComplete(
                        testContext.succeeding(
                            response ->
                                testContext.verify(
                                    () -> {
                                      assertTrue(
                                          response.containsKey(utils.getDetails(user).email));
                                      JsonObject obj =
                                          response.getJsonObject(utils.getDetails(user).email);

                                      assertEquals(
                                          obj.getString("email"), utils.getDetails(user).email);
                                      assertEquals(
                                          obj.getJsonObject("name").getString("firstName"),
                                          user.getName().get("firstName"));
                                      assertEquals(
                                          obj.getJsonObject("name").getString("lastName"),
                                          user.getName().get("lastName"));
                                      assertEquals(obj.getString("keycloakId"), user.getUserId());

                                      dataObtained.flag();
                                    }))));
  }

  @Test
  @DisplayName("Test getting email of user not in DB and gets inserted")
  void findingUserNotInDbAndGetsInserted(VertxTestContext testContext) {
    String newUserEmail = RandomStringUtils.randomAlphabetic(10) + "@gmail.com";
    String newUserId = UUID.randomUUID().toString();
    String newUserFirstName = "aa";
    String newUserLastName = "bb";

    String existingUserEmail = utils.getDetails(user).email;

    Mockito.when(kc.findUserByEmail(newUserEmail))
        .thenReturn(
            Future.succeededFuture(
                new JsonObject()
                    .put("keycloakId", newUserId)
                    .put("email", newUserEmail)
                    .put(
                        "name",
                        new JsonObject()
                            .put("firstName", newUserFirstName)
                            .put("lastName", newUserLastName))));

    Mockito.when(kc.findUserByEmail(existingUserEmail))
        .thenReturn(Future.succeededFuture(utils.getKcAdminJson(user)));

    Checkpoint newUserNotInDb = testContext.checkpoint();
    Checkpoint newUserInDb = testContext.checkpoint();

    pool.withConnection(
            conn ->
                conn.preparedQuery("SELECT id FROM users WHERE id = $1::UUID OR id = $2::UUID")
                    .execute(Tuple.of(user.getUserId(), newUserId)))
        .compose(
            row -> {
              if (row.rowCount() == 1) {
                newUserNotInDb.flag();
              } else {
                testContext.failNow("failed");
              }
              return Future.succeededFuture();
            })
        .map(
            next ->
                registrationService
                    .findUserByEmail(Set.of(existingUserEmail, newUserEmail))
                    .onComplete(
                        testContext.succeeding(
                            response ->
                                testContext.verify(
                                    () -> {
                                      assertTrue(response.containsKey(existingUserEmail));
                                      JsonObject obj1 = response.getJsonObject(existingUserEmail);

                                      assertEquals(obj1.getString("email"), existingUserEmail);
                                      assertEquals(
                                          obj1.getJsonObject("name").getString("firstName"),
                                          user.getName().get("firstName"));
                                      assertEquals(
                                          obj1.getJsonObject("name").getString("lastName"),
                                          user.getName().get("lastName"));
                                      assertEquals(obj1.getString("keycloakId"), user.getUserId());

                                      assertTrue(response.containsKey(newUserEmail));
                                      JsonObject obj2 = response.getJsonObject(newUserEmail);

                                      assertEquals(obj2.getString("email"), newUserEmail);
                                      assertEquals(
                                          obj2.getJsonObject("name").getString("firstName"),
                                          newUserFirstName);
                                      assertEquals(
                                          obj2.getJsonObject("name").getString("lastName"),
                                          newUserLastName);
                                      assertEquals(obj2.getString("keycloakId"), newUserId);

                                      pool.withConnection(
                                              conn ->
                                                  conn.preparedQuery(
                                                          "SELECT id FROM users WHERE id = $1::UUID OR id = $2::UUID")
                                                      .execute(
                                                          Tuple.of(user.getUserId(), newUserId)))
                                          .onSuccess(
                                              rows -> {
                                                if (rows.rowCount() == 2) {
                                                  newUserInDb.flag();
                                                } else {
                                                  testContext.failNow("failed");
                                                }
                                              })
                                          .onFailure(
                                              fail -> testContext.failNow(fail.getMessage()));
                                    }))));
  }
}
