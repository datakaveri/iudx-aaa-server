package iudx.aaa.server.admin;

import static iudx.aaa.server.apiserver.util.Urn.*;
import static iudx.aaa.server.registration.Utils.SQL_DELETE_RESOURCE_SERVER;
import static iudx.aaa.server.registration.Utils.SQL_DELETE_ROLES_OF_RS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import iudx.aaa.server.apiserver.CreateRsRequest;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;
import iudx.aaa.server.apiserver.util.ComposeException;
import iudx.aaa.server.apiserver.util.Urn;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.registration.KcAdmin;
import iudx.aaa.server.registration.RegistrationService;
import iudx.aaa.server.registration.Utils;
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

/** Unit tests for creating resource servers. */
@ExtendWith({VertxExtension.class})
public class CreateResourceServerTest {
  private static Logger LOGGER = LogManager.getLogger(CreateResourceServerTest.class);

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
  private static RegistrationService registrationService = Mockito.mock(RegistrationService.class);

  private static Utils utils;

  private static final String DUMMY_SERVER =
      "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

  /**
   * Dummy COS Admin user. This user does not need to be registered, just needs to have the COS
   * admin role in user object.
   */
  private static User cosAdminUser = new UserBuilder().roles(List.of(Roles.COS_ADMIN)).build();

  /** Admin of the DUMMY_SERVER. Will also be the admin of all created RSs. */
  private static User adminUser =
      new UserBuilder()
          .userId(UUID.randomUUID())
          .roles(List.of(Roles.ADMIN))
          .rolesToRsMapping(Map.of(Roles.ADMIN.toString(), new JsonArray().add(DUMMY_SERVER)))
          .name("aa", "bb")
          .build();

  /** Test user for checking if user w/ consumer role has new consumer role added for new RS. */
  private static User roleTestUser =
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

  private static List<UUID> createdRsIds = new ArrayList<UUID>();

  private static final String SQL_CHECK_APPROVED_ROLES_FOR_RS =
      "SELECT roles.id, role FROM roles"
          + " JOIN resource_server ON roles.resource_server_id = resource_server.id"
          + " WHERE user_id = $1::uuid AND url = $2::text AND status = 'APPROVED'";

  private static final String UUID_REGEX =
      "^[0-9a-f]{8}\\b-[0-9a-f]{4}\\b-[0-9a-f]{4}\\b-[0-9a-f]{4}\\b-[0-9a-f]{12}$";

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

    utils
        .createFakeResourceServer(DUMMY_SERVER, adminUser)
        .compose(succ -> utils.createFakeUser(roleTestUser, false, false))
        .onSuccess(
            res -> {
              adminService = new AdminServiceImpl(pool, kc, registrationService);
              testContext.completeNow();
            })
        .onFailure(fail -> testContext.failNow(fail.getMessage()));
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing....");
    List<UUID> serverIds = new ArrayList<UUID>(createdRsIds);
    Tuple servers = Tuple.of(serverIds.toArray(UUID[]::new));

    pool.withTransaction(
            conn ->
                conn.preparedQuery(SQL_DELETE_ROLES_OF_RS)
                    .execute(servers)
                    .compose(res -> conn.preparedQuery(SQL_DELETE_RESOURCE_SERVER).execute(servers))
                    .compose(success -> utils.deleteFakeResourceServer())
                    .compose(res -> utils.deleteFakeUser()))
        .onComplete(
            x -> {
              if (x.failed()) {
                LOGGER.warn(x.cause().getMessage());
              }
              vertxObj.close(testContext.succeeding(response -> testContext.completeNow()));
            });
  }

  @Test
  @DisplayName("Test not COS admin")
  void notAuthAdmin(VertxTestContext testContext) {

    JsonObject json =
        new JsonObject().put("url", "foo.bar").put("name", "bar").put("owner", "email@email.com");
    CreateRsRequest request = new CreateRsRequest(json);

    User user = new UserBuilder().userId(UUID.randomUUID()).roles(List.of(Roles.ADMIN)).build();

    adminService
        .createResourceServer(request, user)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(response.getInteger("status"), 401);
                          assertEquals(URN_INVALID_ROLE.toString(), response.getString("type"));
                          assertEquals(
                              Constants.ERR_TITLE_NO_COS_ADMIN_ROLE, response.getString("title"));
                          assertEquals(
                              Constants.ERR_DETAIL_NO_COS_ADMIN_ROLE, response.getString("detail"));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Test invalid domains")
  void invalidDomains(VertxTestContext testContext) {
    Checkpoint protocolInUrl = testContext.checkpoint();
    Checkpoint pathInUrl = testContext.checkpoint();
    Checkpoint specialChars = testContext.checkpoint();

    JsonObject json =
        new JsonObject()
            .put("url", "https://example.com")
            .put("name", "bar")
            .put("owner", "email@email.com");
    CreateRsRequest request = new CreateRsRequest(json);

    adminService
        .createResourceServer(request, cosAdminUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(response.getInteger("status"), 400);
                          assertEquals(response.getString("type"), URN_INVALID_INPUT.toString());
                          assertEquals(
                              response.getString("title"), Constants.ERR_TITLE_INVALID_DOMAIN);
                          assertEquals(
                              response.getString("detail"), Constants.ERR_DETAIL_INVALID_DOMAIN);
                          protocolInUrl.flag();
                        })));

    json.clear()
        .put("url", "example.com/path/1/2")
        .put("name", "bar")
        .put("owner", "email@email.com");
    request = new CreateRsRequest(json);
    adminService
        .createResourceServer(request, cosAdminUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(response.getInteger("status"), 400);
                          assertEquals(response.getString("type"), URN_INVALID_INPUT.toString());
                          assertEquals(
                              response.getString("title"), Constants.ERR_TITLE_INVALID_DOMAIN);
                          assertEquals(
                              response.getString("detail"), Constants.ERR_DETAIL_INVALID_DOMAIN);
                          pathInUrl.flag();
                        })));

    json.clear().put("url", "example#@.abcd").put("name", "bar").put("owner", "email@email.com");
    request = new CreateRsRequest(json);
    adminService
        .createResourceServer(request, cosAdminUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(response.getInteger("status"), 400);
                          assertEquals(response.getString("type"), URN_INVALID_INPUT.toString());
                          assertEquals(
                              response.getString("title"), Constants.ERR_TITLE_INVALID_DOMAIN);
                          assertEquals(
                              response.getString("detail"), Constants.ERR_DETAIL_INVALID_DOMAIN);
                          specialChars.flag();
                        })));
  }

  @Test
  @DisplayName("Test owner email does not exist on Keycloak")
  void ownerEmailNotFound(VertxTestContext testContext) {

    String name = RandomStringUtils.randomAlphabetic(10);
    String url = name + ".com";
    String ownerEmail = RandomStringUtils.randomAlphabetic(10) + "@gmail.com";

    JsonObject json = new JsonObject().put("url", url).put("name", name).put("owner", ownerEmail);
    CreateRsRequest request = new CreateRsRequest(json);

    Mockito.doAnswer(
            i -> {
              Set<String> emails = i.getArgument(0);
              return Future.failedFuture(
                  new ComposeException(
                      400, Urn.URN_INVALID_INPUT, "Email not exist", emails.toString()));
            })
        .when(registrationService)
        .findUserByEmail(Mockito.anySet());

    adminService
        .createResourceServer(request, cosAdminUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(response.getInteger("status"), 400);
                          assertEquals(response.getString("type"), URN_INVALID_INPUT.toString());
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Test successful creation")
  void successfulCreation(VertxTestContext testContext) {
    String name = RandomStringUtils.randomAlphabetic(10);
    String url = name + ".com";
    String ownerEmail = utils.getDetails(adminUser).email;

    JsonObject json = new JsonObject().put("url", url).put("name", name).put("owner", ownerEmail);
    CreateRsRequest request = new CreateRsRequest(json);

    Mockito.doAnswer(
            i -> {
              Set<String> emails = i.getArgument(0);
              String email = new ArrayList<String>(emails).get(0);

              JsonObject resp = utils.getKcAdminJson(adminUser);

              return Future.succeededFuture(new JsonObject().put(email, resp));
            })
        .when(registrationService)
        .findUserByEmail(Mockito.anySet());

    adminService
        .createResourceServer(request, cosAdminUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(response.getInteger("status"), 201);
                          assertEquals(response.getString("type"), URN_SUCCESS.toString());
                          assertEquals(
                              response.getString("title"), Constants.SUCC_TITLE_CREATED_RS);
                          JsonObject result = response.getJsonObject("results");
                          assertEquals(result.getString("name"), name);
                          assertEquals(result.getString("url"), url.toLowerCase());

                          assertTrue(result.containsKey("owner"));
                          assertTrue(result.getJsonObject("owner").containsKey("email"));
                          assertTrue(result.getJsonObject("owner").containsKey("id"));
                          assertTrue(result.getJsonObject("owner").containsKey("name"));

                          assertTrue(result.getString("id").matches(UUID_REGEX));
                          createdRsIds.add(UUID.fromString(result.getString("id")));
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Test existing resource server - same domain")
  void existingRs(VertxTestContext testContext) {

    String name = RandomStringUtils.randomAlphabetic(10);
    String url = name + ".com";
    String ownerEmail = utils.getDetails(adminUser).email;

    JsonObject json = new JsonObject().put("url", url).put("name", name).put("owner", ownerEmail);
    CreateRsRequest request = new CreateRsRequest(json);

    Promise<Void> p1 = Promise.promise();

    Mockito.doAnswer(
            i -> {
              Set<String> emails = i.getArgument(0);
              String email = new ArrayList<String>(emails).get(0);

              JsonObject resp = utils.getKcAdminJson(adminUser);

              return Future.succeededFuture(new JsonObject().put(email, resp));
            })
        .when(registrationService)
        .findUserByEmail(Mockito.anySet());

    adminService
        .createResourceServer(request, cosAdminUser)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(response.getInteger("status"), 201);
                          assertEquals(response.getString("type"), URN_SUCCESS.toString());
                          assertEquals(
                              response.getString("title"), Constants.SUCC_TITLE_CREATED_RS);
                          JsonObject result = response.getJsonObject("results");
                          assertEquals(result.getString("name"), name);
                          assertEquals(result.getString("url"), url.toLowerCase());

                          assertTrue(result.containsKey("owner"));
                          assertTrue(result.getJsonObject("owner").containsKey("email"));
                          assertTrue(result.getJsonObject("owner").containsKey("id"));
                          assertTrue(result.getJsonObject("owner").containsKey("name"));

                          assertTrue(result.getString("id").matches(UUID_REGEX));
                          createdRsIds.add(UUID.fromString(result.getString("id")));
                          p1.complete();
                        })));

    p1.future()
        .onSuccess(
            succ -> {
              adminService
                  .createResourceServer(request, cosAdminUser)
                  .onComplete(
                      testContext.succeeding(
                          response ->
                              testContext.verify(
                                  () -> {
                                    assertEquals(response.getInteger("status"), 409);
                                    assertEquals(
                                        response.getString("type"), URN_ALREADY_EXISTS.toString());
                                    assertEquals(
                                        response.getString("title"),
                                        Constants.ERR_TITLE_DOMAIN_EXISTS);
                                    assertEquals(
                                        response.getString("detail"),
                                        Constants.ERR_DETAIL_DOMAIN_EXISTS);
                                    testContext.completeNow();
                                  })));
            });
  }

  @Test
  @DisplayName("Test find user by email in RegistrationService failing")
  void regServiceFails(VertxTestContext testContext) {

    String name = RandomStringUtils.randomAlphabetic(10);
    String url = name + ".com";
    String ownerEmail = utils.getDetails(adminUser).email;

    JsonObject json = new JsonObject().put("url", url).put("name", name).put("owner", ownerEmail);
    CreateRsRequest request = new CreateRsRequest(json);

    Promise<Void> p1 = Promise.promise();

    Mockito.doAnswer(
            i -> {
              return Future.failedFuture("Internal error!");
            })
        .when(registrationService)
        .findUserByEmail(Mockito.anySet());

    adminService
        .createResourceServer(request, cosAdminUser)
        .onComplete(
            testContext.failing(
                response ->
                    testContext.verify(
                        () -> {
                          p1.complete();
                        })));

    p1.future()
        .onSuccess(
            succ -> {
              Mockito.doAnswer(
                      i -> {
                        Set<String> emails = i.getArgument(0);
                        String email = new ArrayList<String>(emails).get(0);

                        /*
                         * Need a real user for having the insert into the RS table, hence we use consumer User's ID
                         */
                        JsonObject resp = utils.getKcAdminJson(adminUser);

                        return Future.succeededFuture(new JsonObject().put(email, resp));
                      })
                  .when(registrationService)
                  .findUserByEmail(Mockito.anySet());

              adminService
                  .createResourceServer(request, cosAdminUser)
                  .onComplete(
                      testContext.succeeding(
                          response ->
                              testContext.verify(
                                  () -> {
                                    assertEquals(response.getInteger("status"), 201);
                                    assertEquals(
                                        response.getString("type"), URN_SUCCESS.toString());
                                    assertEquals(
                                        response.getString("title"),
                                        Constants.SUCC_TITLE_CREATED_RS);
                                    JsonObject result = response.getJsonObject("results");
                                    assertEquals(result.getString("name"), name);
                                    assertEquals(result.getString("url"), url.toLowerCase());

                                    assertTrue(result.containsKey("owner"));
                                    assertTrue(result.getJsonObject("owner").containsKey("email"));
                                    assertTrue(result.getJsonObject("owner").containsKey("id"));
                                    assertTrue(result.getJsonObject("owner").containsKey("name"));

                                    assertTrue(result.getString("id").matches(UUID_REGEX));
                                    createdRsIds.add(UUID.fromString(result.getString("id")));
                                    testContext.completeNow();
                                  })));
            });
  }

  @Test
  @DisplayName("Test user w/ consumer role gets consumer role for newly added RS")
  void consumerGetsConsumerRoleForNewRs(VertxTestContext testContext) {
    String name = RandomStringUtils.randomAlphabetic(10);
    String url = name + ".com";
    String ownerEmail = utils.getDetails(adminUser).email;

    Checkpoint userCurrentlyHasNoRolesForNewRs = testContext.checkpoint();
    Checkpoint userHasNewConsumerRoleForNewRs = testContext.checkpoint();
    JsonObject json = new JsonObject().put("url", url).put("name", name).put("owner", ownerEmail);
    CreateRsRequest request = new CreateRsRequest(json);

    Mockito.doAnswer(
            i -> {
              Set<String> emails = i.getArgument(0);
              String email = new ArrayList<String>(emails).get(0);

              JsonObject resp = utils.getKcAdminJson(adminUser);

              return Future.succeededFuture(new JsonObject().put(email, resp));
            })
        .when(registrationService)
        .findUserByEmail(Mockito.anySet());

    Future<Void> roleCheckBeforeCreation =
        pool.withConnection(
                conn ->
                    conn.preparedQuery(SQL_CHECK_APPROVED_ROLES_FOR_RS)
                        .execute(Tuple.of(roleTestUser.getUserId(), url.toLowerCase())))
            .compose(
                rows -> {
                  if (rows.rowCount() == 0) {
                    userCurrentlyHasNoRolesForNewRs.flag();
                  }
                  return Future.succeededFuture();
                });

    roleCheckBeforeCreation.map(
        res ->
            adminService
                .createResourceServer(request, cosAdminUser)
                .onComplete(
                    testContext.succeeding(
                        response ->
                            testContext.verify(
                                () -> {
                                  assertEquals(response.getInteger("status"), 201);
                                  assertEquals(response.getString("type"), URN_SUCCESS.toString());
                                  assertEquals(
                                      response.getString("title"), Constants.SUCC_TITLE_CREATED_RS);
                                  JsonObject result = response.getJsonObject("results");
                                  createdRsIds.add(UUID.fromString(result.getString("id")));

                                  pool.withConnection(
                                          conn ->
                                              conn.preparedQuery(SQL_CHECK_APPROVED_ROLES_FOR_RS)
                                                  .execute(
                                                      Tuple.of(
                                                          roleTestUser.getUserId(),
                                                          url.toLowerCase())))
                                      .onSuccess(
                                          rows -> {
                                            if (rows.rowCount() == 1
                                                && rows.iterator()
                                                    .next()
                                                    .get(Roles.class, "role")
                                                    .equals(Roles.CONSUMER)) {
                                              userHasNewConsumerRoleForNewRs.flag();
                                            }
                                          });
                                }))));
  }
}
