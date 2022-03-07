package iudx.aaa.server.apd;

import static iudx.aaa.server.apd.Constants.CONFIG_AUTH_URL;
import static iudx.aaa.server.apd.Constants.ERR_DETAIL_APD_NOT_RESPOND;
import static iudx.aaa.server.apd.Constants.ERR_DETAIL_EXISTING_DOMAIN;
import static iudx.aaa.server.apd.Constants.ERR_DETAIL_INVALID_DOMAIN;
import static iudx.aaa.server.apd.Constants.ERR_DETAIL_NOT_TRUSTEE;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_APD_NOT_RESPOND;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_EXISTING_DOMAIN;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_INVALID_DOMAIN;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_NOT_TRUSTEE;
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
import static org.mockito.ArgumentMatchers.any;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
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
import iudx.aaa.server.apiserver.CreateApdRequest;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.Response.ResponseBuilder;
import iudx.aaa.server.apiserver.RoleStatus;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.policy.PolicyService;
import iudx.aaa.server.registration.ComposeException;
import iudx.aaa.server.registration.RegistrationService;
import iudx.aaa.server.registration.Utils;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
  private static PolicyService policyService = Mockito.mock(PolicyService.class);

  private static final String DUMMY_AUTH_SERVER =
      "auth" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + "iudx.io";
  private static Future<JsonObject> trusteeUser;
  private static Future<JsonObject> otherUser;

  static String name = RandomStringUtils.randomAlphabetic(10).toLowerCase();
  static String url = name + ".com";
  static Future<UUID> orgIdFut;

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
          new PgConnectOptions().setPort(databasePort).setHost(databaseIP).setDatabase(databaseName)
              .setUser(databaseUserName).setPassword(databasePassword).setProperties(schemaProp);
    }

    /* Pool options */
    if (poolOptions == null) {
      poolOptions = new PoolOptions().setMaxSize(poolSize);
    }

    pool = PgPool.pool(vertx, connectOptions, poolOptions);

    /* Do not take test config, use generated config */
    JsonObject options = new JsonObject().put(CONFIG_AUTH_URL, DUMMY_AUTH_SERVER);

    orgIdFut = pool.withConnection(conn -> conn.preparedQuery(Utils.SQL_CREATE_ORG)
        .execute(Tuple.of(name, url)).map(row -> row.iterator().next().getUUID("id")));
    trusteeUser = orgIdFut.compose(orgId -> Utils.createFakeUser(pool, orgId.toString(), "",
        Map.of(Roles.TRUSTEE, RoleStatus.APPROVED), false));
    otherUser = orgIdFut.compose(orgId -> Utils.createFakeUser(pool, orgId.toString(), "",
        Map.of(Roles.PROVIDER, RoleStatus.APPROVED), false));

    CompositeFuture.all(trusteeUser, otherUser).onSuccess(succ -> {
      apdService =
          new ApdServiceImpl(pool, apdWebClient, registrationService, policyService, options);
      testContext.completeNow();
    });
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing....");
    List<JsonObject> users = List.of(trusteeUser.result(), otherUser.result());

    Utils.deleteFakeUser(pool, users)
        .compose(succ -> pool.withConnection(
            conn -> conn.preparedQuery(Utils.SQL_DELETE_ORG).execute(Tuple.of(orgIdFut.result()))))
        .onComplete(x -> {
          if (x.failed()) {
            LOGGER.warn(x.cause().getMessage());
          }
          vertxObj.close(testContext.succeeding(response -> testContext.completeNow()));
        });
  }

  @Test
  @DisplayName("Test user calling does not have trustee role")
  void notTrustee(VertxTestContext testContext) {
    JsonObject userJson = otherUser.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .roles(List.of(Roles.PROVIDER, Roles.CONSUMER, Roles.ADMIN, Roles.DELEGATE))
        .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

    JsonObject jsonRequest = new JsonObject().put("name", "something").put("url", "something.com");

    CreateApdRequest request = new CreateApdRequest(jsonRequest);

    apdService.createApd(request, user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getInteger("status"), 403);
          assertEquals(URN_INVALID_ROLE.toString(), response.getString("type"));
          assertEquals(ERR_TITLE_NOT_TRUSTEE, response.getString("title"));
          assertEquals(ERR_DETAIL_NOT_TRUSTEE, response.getString("detail"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test various invalid domains")
  void invalidDomain(VertxTestContext testContext) {
    JsonObject userJson = trusteeUser.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId")).roles(List.of(Roles.TRUSTEE))
        .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

    Checkpoint test1 = testContext.checkpoint();
    JsonObject jsonRequest =
        new JsonObject().put("name", "something").put("url", "https://something.com");
    apdService.createApd(new CreateApdRequest(jsonRequest), user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getInteger("status"), 400);
          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
          assertEquals(ERR_TITLE_INVALID_DOMAIN, response.getString("title"));
          assertEquals(ERR_DETAIL_INVALID_DOMAIN, response.getString("detail"));
          test1.flag();
        })));

    Checkpoint test2 = testContext.checkpoint();
    jsonRequest.clear().put("name", "something").put("url", "something.com:8080");
    apdService.createApd(new CreateApdRequest(jsonRequest), user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getInteger("status"), 400);
          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
          assertEquals(ERR_TITLE_INVALID_DOMAIN, response.getString("title"));
          assertEquals(ERR_DETAIL_INVALID_DOMAIN, response.getString("detail"));
          test2.flag();
        })));

    Checkpoint test3 = testContext.checkpoint();
    jsonRequest.clear().put("name", "something").put("url", "#*(@)(84jndjhda.com");
    apdService.createApd(new CreateApdRequest(jsonRequest), user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getInteger("status"), 400);
          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
          assertEquals(ERR_TITLE_INVALID_DOMAIN, response.getString("title"));
          assertEquals(ERR_DETAIL_INVALID_DOMAIN, response.getString("detail"));
          test3.flag();
        })));

    Checkpoint test4 = testContext.checkpoint();
    jsonRequest.clear().put("name", "something").put("url", "something.com/api/readuserclass");
    apdService.createApd(new CreateApdRequest(jsonRequest), user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getInteger("status"), 400);
          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
          assertEquals(ERR_TITLE_INVALID_DOMAIN, response.getString("title"));
          assertEquals(ERR_DETAIL_INVALID_DOMAIN, response.getString("detail"));
          test4.flag();
        })));

    Checkpoint test5 = testContext.checkpoint();
    jsonRequest.clear().put("name", "something").put("url", "something.com?id=1234");
    apdService.createApd(new CreateApdRequest(jsonRequest), user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getInteger("status"), 400);
          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
          assertEquals(ERR_TITLE_INVALID_DOMAIN, response.getString("title"));
          assertEquals(ERR_DETAIL_INVALID_DOMAIN, response.getString("detail"));
          test5.flag();
        })));
  }

  @Test
  @DisplayName("Test APD not responding")
  void apdNotResponding(VertxTestContext testContext) {
    JsonObject userJson = trusteeUser.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId")).roles(List.of(Roles.TRUSTEE))
        .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

    String name = RandomStringUtils.randomAlphabetic(5).toLowerCase();
    String url = name + ".com";

    JsonObject jsonRequest = new JsonObject().put("name", name).put("url", url);

    Response failureResponse = new ResponseBuilder().type(URN_INVALID_INPUT)
        .title(ERR_TITLE_APD_NOT_RESPOND).detail(ERR_DETAIL_APD_NOT_RESPOND).status(400).build();
    Mockito.when(apdWebClient.checkApdExists(url))
        .thenReturn(Future.failedFuture(new ComposeException(failureResponse)));

    apdService.createApd(new CreateApdRequest(jsonRequest), user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getInteger("status"), 400);
          assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
          assertEquals(ERR_TITLE_APD_NOT_RESPOND, response.getString("title"));
          assertEquals(ERR_DETAIL_APD_NOT_RESPOND, response.getString("detail"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test successful APD registration")
  void successfulApdReg(VertxTestContext testContext) {
    JsonObject userJson = trusteeUser.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId")).roles(List.of(Roles.TRUSTEE))
        .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

    String name = RandomStringUtils.randomAlphabetic(5).toLowerCase();
    String url = name + ".com";

    JsonObject jsonRequest = new JsonObject().put("name", name).put("url", url);
    Mockito.when(apdWebClient.checkApdExists(url)).thenReturn(Future.succeededFuture(true));

    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(1);
      JsonObject userDets = new JsonObject().put("email", userJson.getString("email")).put("name",
          new JsonObject().put("firstName", userJson.getString("firstName")).put("lastName",
              userJson.getString("lastName")));
      p.complete(new JsonObject().put(userJson.getString("userId"), userDets));
      return i.getMock();
    }).when(registrationService).getUserDetails(any(), any());

    apdService.createApd(new CreateApdRequest(jsonRequest), user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getInteger("status"), 200);
          assertEquals(URN_SUCCESS.toString(), response.getString("type"));
          assertEquals(SUCC_TITLE_REGISTERED_APD, response.getString("title"));

          JsonObject result = response.getJsonObject("results");
          assertEquals(name, result.getString(RESP_APD_NAME));
          assertEquals(url, result.getString(RESP_APD_URL));
          assertEquals("pending", result.getString(RESP_APD_STATUS));
          assertTrue(result.containsKey(RESP_APD_ID));
          assertTrue(result.containsKey(RESP_APD_OWNER));

          JsonObject ownerDets = result.getJsonObject(RESP_APD_OWNER);
          assertEquals(userJson.getString("userId"), ownerDets.getString(RESP_OWNER_USER_ID));
          assertEquals(userJson.getString("firstName"),
              ownerDets.getJsonObject("name").getString("firstName"));
          assertEquals(userJson.getString("lastName"),
              ownerDets.getJsonObject("name").getString("lastName"));
          assertEquals(userJson.getString("email"), ownerDets.getString("email"));

          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test existing url")
  void existingUrl(VertxTestContext testContext) {
    JsonObject userJson = trusteeUser.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId")).roles(List.of(Roles.TRUSTEE))
        .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

    String name = RandomStringUtils.randomAlphabetic(5).toLowerCase();
    String url = name + ".com";

    JsonObject jsonRequest = new JsonObject().put("name", name).put("url", url);
    Mockito.when(apdWebClient.checkApdExists(url)).thenReturn(Future.succeededFuture(true));

    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(1);
      JsonObject userDets = new JsonObject().put("email", userJson.getString("email")).put("name",
          new JsonObject().put("firstName", userJson.getString("firstName")).put("lastName",
              userJson.getString("lastName")));
      p.complete(new JsonObject().put(userJson.getString("userId"), userDets));
      return i.getMock();
    }).when(registrationService).getUserDetails(any(), any());

    Checkpoint created = testContext.checkpoint();
    Checkpoint existing = testContext.checkpoint();

    apdService.createApd(new CreateApdRequest(jsonRequest), user,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(response.getInteger("status"), 200);
          assertEquals(URN_SUCCESS.toString(), response.getString("type"));
          assertEquals(SUCC_TITLE_REGISTERED_APD, response.getString("title"));
          created.flag();

          apdService.createApd(new CreateApdRequest(jsonRequest), user,
              testContext.succeeding(fail -> testContext.verify(() -> {
                assertEquals(fail.getInteger("status"), 409);
                assertEquals(URN_ALREADY_EXISTS.toString(), fail.getString("type"));
                assertEquals(ERR_TITLE_EXISTING_DOMAIN, fail.getString("title"));
                assertEquals(ERR_DETAIL_EXISTING_DOMAIN, fail.getString("detail"));
                existing.flag();
              })));
        })));
  }

  @Test
  @DisplayName("Test APD web client fails (internal error)")
  void apdWebClientFails(VertxTestContext testContext) {
    JsonObject userJson = trusteeUser.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId")).roles(List.of(Roles.TRUSTEE))
        .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

    String name = RandomStringUtils.randomAlphabetic(5).toLowerCase();
    String url = name + ".com";

    JsonObject jsonRequest = new JsonObject().put("name", name).put("url", url);
    Mockito.when(apdWebClient.checkApdExists(url)).thenReturn(Future.failedFuture("Fail"));

    apdService.createApd(new CreateApdRequest(jsonRequest), user,
        testContext.failing(fail -> testContext.verify(() -> {
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Test failing registration service - insert apd transaction should rollback")
  void failingRegService(VertxTestContext testContext) {
    JsonObject userJson = trusteeUser.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId")).roles(List.of(Roles.TRUSTEE))
        .name(userJson.getString("firstName"), userJson.getString("lastName")).build();

    String name = RandomStringUtils.randomAlphabetic(5).toLowerCase();
    String url = name + ".com";

    JsonObject jsonRequest = new JsonObject().put("name", name).put("url", url);
    Mockito.when(apdWebClient.checkApdExists(url)).thenReturn(Future.succeededFuture(true));

    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(1);
      p.fail("Fail");
      return i.getMock();
    }).when(registrationService).getUserDetails(any(), any());

    Checkpoint regFailed = testContext.checkpoint();
    Checkpoint success = testContext.checkpoint();

    apdService.createApd(new CreateApdRequest(jsonRequest), user,
        testContext.failing(fail -> testContext.verify(() -> {
          testContext.completeNow();
          regFailed.flag();

          Mockito.doAnswer(i -> {
            Promise<JsonObject> p = i.getArgument(1);
            JsonObject userDets = new JsonObject().put("email", userJson.getString("email"))
                .put("name", new JsonObject().put("firstName", userJson.getString("firstName"))
                    .put("lastName", userJson.getString("lastName")));
            p.complete(new JsonObject().put(userJson.getString("userId"), userDets));
            return i.getMock();
          }).when(registrationService).getUserDetails(any(), any());

          apdService.createApd(new CreateApdRequest(jsonRequest), user,
              testContext.succeeding(response -> testContext.verify(() -> {
                assertEquals(response.getInteger("status"), 200);
                assertEquals(URN_SUCCESS.toString(), response.getString("type"));
                assertEquals(SUCC_TITLE_REGISTERED_APD, response.getString("title"));
                success.flag();
              })));
        })));
  }
}
