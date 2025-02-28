package iudx.aaa.server.apd;

import static iudx.aaa.server.apd.Constants.APD_CONSTRAINTS;
import static iudx.aaa.server.apd.Constants.APD_NOT_ACTIVE;
import static iudx.aaa.server.apd.Constants.APD_RESP_DETAIL;
import static iudx.aaa.server.apd.Constants.APD_RESP_LINK;
import static iudx.aaa.server.apd.Constants.APD_RESP_SESSIONID;
import static iudx.aaa.server.apd.Constants.APD_RESP_TYPE;
import static iudx.aaa.server.apd.Constants.APD_URN_ALLOW;
import static iudx.aaa.server.apd.Constants.APD_URN_DENY;
import static iudx.aaa.server.apd.Constants.APD_URN_DENY_NEEDS_INT;
import static iudx.aaa.server.apd.Constants.CREATE_TOKEN_APD_INTERAC;
import static iudx.aaa.server.apd.Constants.CREATE_TOKEN_CAT_ID;
import static iudx.aaa.server.apd.Constants.CREATE_TOKEN_CONSTRAINTS;
import static iudx.aaa.server.apd.Constants.CREATE_TOKEN_LINK;
import static iudx.aaa.server.apd.Constants.CREATE_TOKEN_SESSIONID;
import static iudx.aaa.server.apd.Constants.CREATE_TOKEN_STATUS;
import static iudx.aaa.server.apd.Constants.CREATE_TOKEN_SUCCESS;
import static iudx.aaa.server.apd.Constants.CREATE_TOKEN_URL;
import static iudx.aaa.server.apd.Constants.ERR_DETAIL_APD_NOT_REGISTERED;
import static iudx.aaa.server.apd.Constants.ERR_DETAIL_APD_NOT_RESPOND;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_APD_EVAL_FAILED;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_APD_NOT_REGISTERED;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_APD_NOT_RESPOND;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_INPUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import iudx.aaa.server.apiserver.ApdStatus;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.Response.ResponseBuilder;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;
import iudx.aaa.server.apiserver.util.ComposeException;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.policy.PolicyService;
import iudx.aaa.server.registration.RegistrationService;
import iudx.aaa.server.registration.Utils;
import iudx.aaa.server.token.TokenService;
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

/** Unit tests for call APD service that {@link PolicyService} calls to verify resource access. */
@ExtendWith({VertxExtension.class})
public class CallApdTest {
  private static Logger LOGGER = LogManager.getLogger(CallApdTest.class);

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

  private static final String DUMMY_SERVER =
      "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + "iudx.io";

  private static final String INACTIVE_APD =
      "apd" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";
  private static final String ACTIVE_APD =
      "apd" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

  private static User consumer =
      new UserBuilder()
          .userId(UUID.randomUUID())
          .roles(List.of(Roles.CONSUMER))
          .rolesToRsMapping(Map.of(Roles.CONSUMER.toString(), new JsonArray().add(DUMMY_SERVER)))
          .name("aa", "bb")
          .build();

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
            .createFakeResourceServer(
                DUMMY_SERVER, new UserBuilder().userId(UUID.randomUUID()).build())
            .compose(res -> utils.createFakeUser(consumer, false, false))
            .compose(
                res ->
                    utils.createFakeApd(
                        ACTIVE_APD,
                        new UserBuilder().userId(UUID.randomUUID()).build(),
                        ApdStatus.ACTIVE))
            .compose(
                res ->
                    utils.createFakeApd(
                        INACTIVE_APD,
                        new UserBuilder().userId(UUID.randomUUID()).build(),
                        ApdStatus.INACTIVE));

    create
        .onSuccess(
            res -> {
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
  @DisplayName("APD not registered at this COS - should never happen???")
  void apdUrlNotExist(VertxTestContext testContext) {

    UUID userId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();

    JsonObject apdContext =
        new JsonObject()
            .put("userId", userId.toString())
            .put("ownerId", ownerId.toString())
            .put("apdUrl", RandomStringUtils.randomAlphabetic(10) + ".com")
            .put("itemId", RandomStringUtils.randomAlphabetic(20).toLowerCase())
            .put("itemType", RandomStringUtils.randomAlphabetic(10).toLowerCase())
            .put("resSerUrl", RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".com")
            .put("context", new JsonObject());

    apdService
        .callApd(apdContext)
        .onComplete(
            testContext.failing(
                response ->
                    testContext.verify(
                        () -> {
                          ComposeException exp = (ComposeException) response;
                          Response errResp = exp.getResponse();
                          assertEquals(errResp.getType(), URN_INVALID_INPUT.toString());
                          assertEquals(errResp.getTitle(), ERR_TITLE_APD_NOT_REGISTERED);
                          assertEquals(errResp.getDetail(), ERR_DETAIL_APD_NOT_REGISTERED);
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Registration service (get user details) failing")
  void regServiceFail(VertxTestContext testContext) {

    Mockito.doAnswer(
            i -> {
              return Future.failedFuture("Fail");
            })
        .when(registrationService)
        .getUserDetails(any());

    UUID userId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();

    JsonObject apdContext =
        new JsonObject()
            .put("userId", userId.toString())
            .put("ownerId", ownerId.toString())
            .put("apdUrl", ACTIVE_APD)
            .put("itemId", RandomStringUtils.randomAlphabetic(20).toLowerCase())
            .put("itemType", RandomStringUtils.randomAlphabetic(10).toLowerCase())
            .put("resSerUrl", RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".com")
            .put("context", new JsonObject());

    apdService
        .callApd(apdContext)
        .onComplete(
            testContext.failing(
                response ->
                    testContext.verify(
                        () -> {
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("Token service (get auth server token) failing")
  void tokenServiceFail(VertxTestContext testContext) {

    Mockito.doReturn(Future.failedFuture("Fail")).when(tokenService).getAuthServerToken(any());

    UUID userId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();

    JsonObject apdContext =
        new JsonObject()
            .put("userId", userId.toString())
            .put("ownerId", ownerId.toString())
            .put("apdUrl", ACTIVE_APD)
            .put("itemId", RandomStringUtils.randomAlphabetic(20).toLowerCase())
            .put("itemType", RandomStringUtils.randomAlphabetic(10).toLowerCase())
            .put("resSerUrl", RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".com")
            .put("context", new JsonObject());

    apdService
        .callApd(apdContext)
        .onComplete(
            testContext.failing(
                response ->
                    testContext.verify(
                        () -> {
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("ApdWebClient failing - incorrect/invalid response sent by APD")
  void apdWebClientFails(VertxTestContext testContext) {
    Mockito.doAnswer(
            i -> {
              List<String> ids = i.getArgument(0);
              JsonObject resp = new JsonObject();
              for (String id : ids) {
                resp.put(id, new JsonObject());
              }
              return Future.succeededFuture(resp);
            })
        .when(registrationService)
        .getUserDetails(any());

    Mockito.doAnswer(
            i -> {
              JsonObject resp =
                  new JsonObject().put("accessToken", RandomStringUtils.randomAlphabetic(30));
              return Future.succeededFuture(resp);
            })
        .when(tokenService)
        .getAuthServerToken(any());

    Response failureResponse =
        new ResponseBuilder()
            .type(URN_INVALID_INPUT)
            .title(ERR_TITLE_APD_NOT_RESPOND)
            .detail(ERR_DETAIL_APD_NOT_RESPOND)
            .status(400)
            .build();

    Mockito.when(apdWebClient.callVerifyApdEndpoint(any(), any(), any()))
        .thenReturn(Future.failedFuture(new ComposeException(failureResponse)));

    UUID userId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();

    JsonObject apdContext =
        new JsonObject()
            .put("userId", userId.toString())
            .put("ownerId", ownerId.toString())
            .put("apdUrl", INACTIVE_APD)
            .put("resource", RandomStringUtils.randomAlphabetic(20).toLowerCase())
            .put("itemType", RandomStringUtils.randomAlphabetic(10).toLowerCase())
            .put("resSerUrl", RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".com")
            .put("context", new JsonObject());

    apdService
        .callApd(apdContext)
        .onComplete(
            testContext.failing(
                response ->
                    testContext.verify(
                        () -> {
                          ComposeException exp = (ComposeException) response;
                          Response errResp = exp.getResponse();
                          assertEquals(errResp.getType(), URN_INVALID_INPUT.toString());
                          assertEquals(errResp.getTitle(), ERR_TITLE_APD_EVAL_FAILED);
                          assertEquals(
                              errResp.getDetail(), ERR_DETAIL_APD_NOT_RESPOND + APD_NOT_ACTIVE);
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("ApdWebClient success - APD allow")
  void apdWebClientSuccessAllow(VertxTestContext testContext) {
    Mockito.doAnswer(
            i -> {
              List<String> ids = i.getArgument(0);
              JsonObject resp = new JsonObject();
              for (String id : ids) {
                resp.put(id, new JsonObject());
              }
              return Future.succeededFuture(resp);
            })
        .when(registrationService)
        .getUserDetails(any());

    Mockito.doAnswer(
            i -> {
              JsonObject resp =
                  new JsonObject().put("accessToken", RandomStringUtils.randomAlphabetic(30));
              return Future.succeededFuture(resp);
            })
        .when(tokenService)
        .getAuthServerToken(any());

    JsonObject webClientResp = new JsonObject().put(APD_RESP_TYPE, APD_URN_ALLOW);

    Mockito.when(apdWebClient.callVerifyApdEndpoint(any(), any(), any()))
        .thenReturn(Future.succeededFuture(webClientResp));

    UUID userId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    String itemId = RandomStringUtils.randomAlphabetic(20).toLowerCase();
    String itemType = RandomStringUtils.randomAlphabetic(10).toLowerCase();
    String resSerUrl = RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".com";

    JsonObject apdContext =
        new JsonObject()
            .put("userId", userId.toString())
            .put("ownerId", ownerId.toString())
            .put("apdUrl", ACTIVE_APD)
            .put("itemId", itemId)
            .put("itemType", itemType)
            .put("resSerUrl", resSerUrl)
            .put("context", new JsonObject());

    apdService
        .callApd(apdContext)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(
                              response.getString(CREATE_TOKEN_STATUS), CREATE_TOKEN_SUCCESS);
                          assertEquals(response.getString(CREATE_TOKEN_CAT_ID), itemId);
                          assertEquals(response.getString(CREATE_TOKEN_URL), resSerUrl);
                          assertEquals(
                              response.getJsonObject(CREATE_TOKEN_CONSTRAINTS), new JsonObject());
                          testContext.completeNow();
                        })));
  }

  // add test  for apd response with constraints
  @Test
  @DisplayName("ApdWebClient success - APD allow with constraints")
  void apdWebClientSuccessAllowWConstraints(VertxTestContext testContext) {

    JsonObject apdConstraints = new JsonObject().put("access", true);

    Mockito.doAnswer(
            i -> {
              List<String> ids = i.getArgument(0);
              JsonObject resp = new JsonObject();
              for (String id : ids) {
                resp.put(id, new JsonObject());
              }
              return Future.succeededFuture(resp);
            })
        .when(registrationService)
        .getUserDetails(any());

    Mockito.doAnswer(
            i -> {
              JsonObject resp =
                  new JsonObject().put("accessToken", RandomStringUtils.randomAlphabetic(30));
              return Future.succeededFuture(resp);
            })
        .when(tokenService)
        .getAuthServerToken(any());

    JsonObject webClientResp =
        new JsonObject().put(APD_RESP_TYPE, APD_URN_ALLOW).put(APD_CONSTRAINTS, apdConstraints);

    Mockito.when(apdWebClient.callVerifyApdEndpoint(any(), any(), any()))
        .thenReturn(Future.succeededFuture(webClientResp));

    UUID userId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    String itemId = RandomStringUtils.randomAlphabetic(20).toLowerCase();
    String itemType = RandomStringUtils.randomAlphabetic(10).toLowerCase();
    String resSerUrl = RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".com";

    JsonObject apdContext =
        new JsonObject()
            .put("userId", userId.toString())
            .put("ownerId", ownerId.toString())
            .put("apdUrl", ACTIVE_APD)
            .put("itemType", itemType)
            .put("itemId", itemId)
            .put("itemType", itemType)
            .put("resSerUrl", resSerUrl)
            .put("context", new JsonObject());

    apdService
        .callApd(apdContext)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(
                              response.getString(CREATE_TOKEN_STATUS), CREATE_TOKEN_SUCCESS);
                          assertEquals(response.getString(CREATE_TOKEN_CAT_ID), itemId);
                          assertEquals(response.getString(CREATE_TOKEN_URL), resSerUrl);
                          assertEquals(
                              response.getJsonObject(CREATE_TOKEN_CONSTRAINTS), apdConstraints);
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("ApdWebClient success - APD deny")
  void apdWebClientSuccessDeny(VertxTestContext testContext) {
    Mockito.doAnswer(
            i -> {
              List<String> ids = i.getArgument(0);
              JsonObject resp = new JsonObject();
              for (String id : ids) {
                resp.put(id, new JsonObject());
              }
              return Future.succeededFuture(resp);
            })
        .when(registrationService)
        .getUserDetails(any());

    Mockito.doAnswer(
            i -> {
              JsonObject resp =
                  new JsonObject().put("accessToken", RandomStringUtils.randomAlphabetic(30));
              return Future.succeededFuture(resp);
            })
        .when(tokenService)
        .getAuthServerToken(any());

    JsonObject webClientResp =
        new JsonObject().put(APD_RESP_TYPE, APD_URN_DENY).put(APD_RESP_DETAIL, "Not allowed");

    Mockito.when(apdWebClient.callVerifyApdEndpoint(any(), any(), any()))
        .thenReturn(Future.succeededFuture(webClientResp));

    UUID userId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    String itemId = RandomStringUtils.randomAlphabetic(20).toLowerCase();
    String itemType = RandomStringUtils.randomAlphabetic(10).toLowerCase();
    String resSerUrl = RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".com";

    JsonObject apdContext =
        new JsonObject()
            .put("userId", userId.toString())
            .put("ownerId", ownerId.toString())
            .put("apdUrl", ACTIVE_APD)
            .put("itemId", itemId)
            .put("itemType", itemType)
            .put("resSerUrl", resSerUrl)
            .put("context", new JsonObject());

    apdService
        .callApd(apdContext)
        .onComplete(
            testContext.failing(
                response ->
                    testContext.verify(
                        () -> {
                          ComposeException exp = (ComposeException) response;
                          Response errResp = exp.getResponse();
                          assertEquals(errResp.getType(), URN_INVALID_INPUT.toString());
                          assertEquals(errResp.getTitle(), ERR_TITLE_APD_EVAL_FAILED);
                          assertEquals(errResp.getDetail(), "Not allowed");
                          testContext.completeNow();
                        })));
  }

  @Test
  @DisplayName("ApdWebClient success - APD deny and needs interaction")
  void apdWebClientSuccessDenySessionId(VertxTestContext testContext) {
    Mockito.doAnswer(
            i -> {
              List<String> ids = i.getArgument(0);
              JsonObject resp = new JsonObject();
              for (String id : ids) {
                resp.put(id, new JsonObject());
              }
              return Future.succeededFuture(resp);
            })
        .when(registrationService)
        .getUserDetails(any());

    Mockito.doAnswer(
            i -> {
              JsonObject resp =
                  new JsonObject().put("accessToken", RandomStringUtils.randomAlphabetic(30));
              return Future.succeededFuture(resp);
            })
        .when(tokenService)
        .getAuthServerToken(any());

    String sessionId = UUID.randomUUID().toString();
    JsonObject webClientResp =
        new JsonObject()
            .put(APD_RESP_TYPE, APD_URN_DENY_NEEDS_INT)
            .put(APD_RESP_DETAIL, "Needs interaction")
            .put(APD_RESP_SESSIONID, sessionId)
            .put(APD_RESP_LINK, ACTIVE_APD + "/apd");

    Mockito.when(apdWebClient.callVerifyApdEndpoint(any(), any(), any()))
        .thenReturn(Future.succeededFuture(webClientResp));

    UUID userId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    String itemId = RandomStringUtils.randomAlphabetic(20).toLowerCase();
    String itemType = RandomStringUtils.randomAlphabetic(10).toLowerCase();
    String resSerUrl = RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".com";

    JsonObject apdContext =
        new JsonObject()
            .put("userId", userId.toString())
            .put("ownerId", ownerId.toString())
            .put("apdUrl", ACTIVE_APD)
            .put("itemId", itemId)
            .put("itemType", itemType)
            .put("resSerUrl", resSerUrl)
            .put("context", new JsonObject());

    apdService
        .callApd(apdContext)
        .onComplete(
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(
                              response.getString(CREATE_TOKEN_STATUS), CREATE_TOKEN_APD_INTERAC);
                          assertEquals(response.getString(CREATE_TOKEN_LINK), ACTIVE_APD + "/apd");
                          assertEquals(response.getString(CREATE_TOKEN_SESSIONID), sessionId);
                          assertEquals(response.getString(CREATE_TOKEN_URL), ACTIVE_APD);
                          testContext.completeNow();
                        })));
  }
}
