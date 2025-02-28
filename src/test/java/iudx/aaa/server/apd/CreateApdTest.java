package iudx.aaa.server.apd;

import static iudx.aaa.server.apd.Constants.ERR_DETAIL_EXISTING_DOMAIN;
import static iudx.aaa.server.apd.Constants.ERR_DETAIL_INVALID_DOMAIN;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_EXISTING_DOMAIN;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_INVALID_DOMAIN;
import static iudx.aaa.server.apd.Constants.RESP_APD_ID;
import static iudx.aaa.server.apd.Constants.RESP_APD_NAME;
import static iudx.aaa.server.apd.Constants.RESP_APD_OWNER;
import static iudx.aaa.server.apd.Constants.RESP_APD_STATUS;
import static iudx.aaa.server.apd.Constants.RESP_APD_URL;
import static iudx.aaa.server.apd.Constants.RESP_OWNER_USER_ID;
import static iudx.aaa.server.apd.Constants.SUCC_TITLE_REGISTERED_APD;
import static iudx.aaa.server.apiserver.util.Urn.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import iudx.aaa.server.apiserver.CreateApdRequest;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;
import iudx.aaa.server.apiserver.util.ComposeException;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.registration.RegistrationService;
import iudx.aaa.server.registration.Utils;
import iudx.aaa.server.token.TokenService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

/** Unit tests for APD creation. */
@ExtendWith({VertxExtension.class})
public class CreateApdTest {
  private static Logger LOGGER = LogManager.getLogger(CreateApdTest.class);

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
  private static TokenService tokenService = Mockito.mock(TokenService.class);

  private static final String DUMMY_AUTH_SERVER =
      "auth" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + "iudx.io";
  private static final String DUMMY_SERVER =
      "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

  private static User normalUser = new UserBuilder().userId(UUID.randomUUID()).build();

  private static User trusteeUser =
      new UserBuilder().userId(UUID.randomUUID()).name("aa", "bb").build();

  private static User cosAdmin =
      new UserBuilder().userId(UUID.randomUUID()).roles(List.of(Roles.COS_ADMIN)).build();

  private static Utils utils;

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
          new PgConnectOptions()
              .setPort(databasePort)
              .setHost(databaseIP)
              .setDatabase(databaseName)
              .setUser(databaseUserName)
              .setPassword(databasePassword)
              .setProperties(schemaProp);
    }

    /* Pool options */
    if (poolOptions == null) {
      poolOptions = new PoolOptions().setMaxSize(poolSize);
    }

    pool = PgPool.pool(vertx, connectOptions, poolOptions);

    utils = new Utils(pool);

    Future<Void> create =
        utils
            .createFakeUser(normalUser, false, false)
            .compose(res -> utils.createFakeUser(trusteeUser, false, false));

    create
        .onSuccess(
            succ -> {
              apdService =
                  new ApdServiceImpl(pool, apdWebClient, registrationService, tokenService);
              testContext.completeNow();
            })
        .onFailure(fail -> testContext.failNow(fail.getMessage()));
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing....");

    utils
        .deleteFakeApd()
        .compose(res -> utils.deleteFakeUser())
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
  @DisplayName("Test user calling does not have COS admin role")
  void notAdmin(VertxTestContext testContext) {
    User provConsAdminUser = new User(normalUser.toJson());

    provConsAdminUser.setRoles(List.of(Roles.CONSUMER, Roles.PROVIDER, Roles.ADMIN, Roles.TRUSTEE));
    provConsAdminUser.setRolesToRsMapping(
        Map.of(
            Roles.CONSUMER.toString(),
            new JsonArray().add("some-url.com"),
            Roles.PROVIDER.toString(),
            new JsonArray().add("some-url.com"),
            Roles.ADMIN.toString(),
            new JsonArray().add("some-url.com")));

    JsonObject jsonRequest =
        new JsonObject()
            .put("name", "something")
            .put("url", "something.com")
            .put("owner", utils.getDetails(trusteeUser).email);

    CreateApdRequest request = new CreateApdRequest(jsonRequest);

    apdService
        .createApd(request, provConsAdminUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(response.getInteger("status"), 401);
                          assertEquals(URN_INVALID_ROLE.toString(), response.getString("type"));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Test various invalid domains")
  void invalidDomain(VertxTestContext testContext) {
    // Checkpoints for each test case
    Checkpoint test1 = testContext.checkpoint();
    Checkpoint test2 = testContext.checkpoint();
    Checkpoint test3 = testContext.checkpoint();
    Checkpoint test4 = testContext.checkpoint();
    Checkpoint test5 = testContext.checkpoint();

    // Test Case 1
    JsonObject jsonRequest1 =
        new JsonObject()
            .put("name", "something")
            .put("url", "https://something.com")
            .put("owner", utils.getDetails(trusteeUser).email);

    apdService
        .createApd(new CreateApdRequest(jsonRequest1), cosAdmin)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(response.getInteger("status"), 400);
                          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
                          assertEquals(ERR_TITLE_INVALID_DOMAIN, response.getString("title"));
                          assertEquals(ERR_DETAIL_INVALID_DOMAIN, response.getString("detail"));
                          test1.flag(); // Flag the completion of test1
                        })));

    // Test Case 2
    JsonObject jsonRequest2 =
        new JsonObject()
            .put("name", "something")
            .put("url", "something.com:8080")
            .put("owner", utils.getDetails(trusteeUser).email);

    apdService
        .createApd(new CreateApdRequest(jsonRequest2), cosAdmin)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(response.getInteger("status"), 400);
                          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
                          assertEquals(ERR_TITLE_INVALID_DOMAIN, response.getString("title"));
                          assertEquals(ERR_DETAIL_INVALID_DOMAIN, response.getString("detail"));
                          test2.flag(); // Flag the completion of test2
                        })));

    // Test Case 3
    JsonObject jsonRequest3 =
        new JsonObject()
            .put("name", "something")
            .put("url", "#*(@)(84jndjhda.com")
            .put("owner", utils.getDetails(trusteeUser).email);

    apdService
        .createApd(new CreateApdRequest(jsonRequest3), cosAdmin)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(response.getInteger("status"), 400);
                          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
                          assertEquals(ERR_TITLE_INVALID_DOMAIN, response.getString("title"));
                          assertEquals(ERR_DETAIL_INVALID_DOMAIN, response.getString("detail"));
                          test3.flag(); // Flag the completion of test3
                        })));

    // Test Case 4
    JsonObject jsonRequest4 =
        new JsonObject()
            .put("name", "something")
            .put("url", "something.com/api/readuserclass")
            .put("owner", utils.getDetails(trusteeUser).email);

    apdService
        .createApd(new CreateApdRequest(jsonRequest4), cosAdmin)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(response.getInteger("status"), 400);
                          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
                          assertEquals(ERR_TITLE_INVALID_DOMAIN, response.getString("title"));
                          assertEquals(ERR_DETAIL_INVALID_DOMAIN, response.getString("detail"));
                          test4.flag(); // Flag the completion of test4
                        })));

    // Test Case 5
    JsonObject jsonRequest5 =
        new JsonObject()
            .put("name", "something")
            .put("url", "something.com?id=1234")
            .put("owner", utils.getDetails(trusteeUser).email);

    apdService
        .createApd(new CreateApdRequest(jsonRequest5), cosAdmin)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(response.getInteger("status"), 400);
                          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
                          assertEquals(ERR_TITLE_INVALID_DOMAIN, response.getString("title"));
                          assertEquals(ERR_DETAIL_INVALID_DOMAIN, response.getString("detail"));
                          test5.flag(); // Flag the completion of test5
                        })));
  }

  @Test
  @DisplayName("Test successful APD registration")
  void successfulApdReg(VertxTestContext testContext) {
    String name = RandomStringUtils.randomAlphabetic(5).toLowerCase();
    String url = name + ".com";

    JsonObject jsonRequest =
        new JsonObject()
            .put("name", name)
            .put("url", url)
            .put("owner", utils.getDetails(trusteeUser).email);

    Mockito.doAnswer(
            i -> {
              Set<String> emails = i.getArgument(0);
              String email = new ArrayList<String>(emails).get(0);

              JsonObject resp = utils.getKcAdminJson(trusteeUser);

              return Future.succeededFuture(new JsonObject().put(email, resp));
            })
        .when(registrationService)
        .findUserByEmail(Mockito.anySet());

    apdService
        .createApd(new CreateApdRequest(jsonRequest), cosAdmin)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(response.getInteger("status"), 201);
                          assertEquals(URN_SUCCESS.toString(), response.getString("type"));
                          assertEquals(SUCC_TITLE_REGISTERED_APD, response.getString("title"));

                          JsonObject result = response.getJsonObject("results");
                          assertEquals(name, result.getString(RESP_APD_NAME));
                          assertEquals(url, result.getString(RESP_APD_URL));
                          assertEquals("active", result.getString(RESP_APD_STATUS));
                          assertTrue(result.containsKey(RESP_APD_ID));

                          assertTrue(result.containsKey(RESP_APD_OWNER));

                          JsonObject ownerDets = result.getJsonObject(RESP_APD_OWNER);
                          assertEquals(
                              trusteeUser.getUserId(), ownerDets.getString(RESP_OWNER_USER_ID));
                          assertEquals(
                              trusteeUser.getName().get("firstName"),
                              ownerDets.getJsonObject("name").getString("firstName"));
                          assertEquals(
                              trusteeUser.getName().get("lastName"),
                              ownerDets.getJsonObject("name").getString("lastName"));
                          assertEquals(
                              utils.getDetails(trusteeUser).email, ownerDets.getString("email"));

                          // add to apdmap for deletion
                          utils.apdMap.put(url, UUID.fromString(result.getString(RESP_APD_ID)));

                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Test existing url")
  void existingUrl(VertxTestContext testContext) {
    String name = RandomStringUtils.randomAlphabetic(5).toLowerCase();
    String url = name + ".com";

    JsonObject jsonRequest =
        new JsonObject()
            .put("name", name)
            .put("url", url)
            .put("owner", utils.getDetails(trusteeUser).email);

    Mockito.doAnswer(
            i -> {
              Set<String> emails = i.getArgument(0);
              String email = new ArrayList<String>(emails).get(0);

              JsonObject resp = utils.getKcAdminJson(trusteeUser);

              return Future.succeededFuture(new JsonObject().put(email, resp));
            })
        .when(registrationService)
        .findUserByEmail(Mockito.anySet());

    Checkpoint created = testContext.checkpoint();
    Checkpoint existing = testContext.checkpoint();

    apdService
        .createApd(new CreateApdRequest(jsonRequest), cosAdmin)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(response.getInteger("status"), 201);
                          assertEquals(URN_SUCCESS.toString(), response.getString("type"));
                          assertEquals(SUCC_TITLE_REGISTERED_APD, response.getString("title"));
                          created.flag();

                          // add to apdmap for deletion
                          JsonObject result = response.getJsonObject("results");
                          utils.apdMap.put(url, UUID.fromString(result.getString(RESP_APD_ID)));

                          apdService
                              .createApd(new CreateApdRequest(jsonRequest), cosAdmin)
                              .onComplete(
                                  testContext.succeeding(
                                      fail ->
                                          testContext.verify(
                                              () -> {
                                                assertEquals(fail.getInteger("status"), 409);
                                                assertEquals(
                                                    URN_ALREADY_EXISTS.toString(),
                                                    fail.getString("type"));
                                                assertEquals(
                                                    ERR_TITLE_EXISTING_DOMAIN,
                                                    fail.getString("title"));
                                                assertEquals(
                                                    ERR_DETAIL_EXISTING_DOMAIN,
                                                    fail.getString("detail"));
                                                existing.flag();
                                              })));
                        })));
  }

  @Test
  @DisplayName("Test owner email not registered on COS")
  void emailNotRegisteredOnCos(VertxTestContext testContext) {
    String name = RandomStringUtils.randomAlphabetic(5).toLowerCase();
    String url = name + ".com";

    JsonObject jsonRequest =
        new JsonObject()
            .put("name", name)
            .put("url", url)
            .put("owner", utils.getDetails(trusteeUser).email);

    Mockito.doAnswer(
            i -> {
              Set<String> emails = i.getArgument(0);

              return Future.failedFuture(
                  new ComposeException(
                      400, URN_MISSING_INFO, "Some emails don't exist", emails.toString()));
            })
        .when(registrationService)
        .findUserByEmail(Mockito.anySet());

    apdService
        .createApd(new CreateApdRequest(jsonRequest), cosAdmin)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(response.getInteger("status"), 400);
                          assertEquals(URN_MISSING_INFO.toString(), response.getString("type"));
                          assertTrue(
                              response
                                  .getString("detail")
                                  .contains(utils.getDetails(trusteeUser).email));
                          testContext.completeNow();
                        })));
  }
}
