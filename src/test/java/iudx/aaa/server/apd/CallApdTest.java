package iudx.aaa.server.apd;

import static iudx.aaa.server.apd.Constants.APD_NOT_ACTIVE;
import static iudx.aaa.server.apd.Constants.APD_RESP_DETAIL;
import static iudx.aaa.server.apd.Constants.APD_RESP_LINK;
import static iudx.aaa.server.apd.Constants.APD_RESP_SESSIONID;
import static iudx.aaa.server.apd.Constants.APD_RESP_TYPE;
import static iudx.aaa.server.apd.Constants.APD_URN_ALLOW;
import static iudx.aaa.server.apd.Constants.APD_URN_DENY;
import static iudx.aaa.server.apd.Constants.APD_URN_DENY_NEEDS_INT;
import static iudx.aaa.server.apd.Constants.CONFIG_AUTH_URL;
import static iudx.aaa.server.apd.Constants.CREATE_TOKEN_APD_INTERAC;
import static iudx.aaa.server.apd.Constants.CREATE_TOKEN_CAT_ID;
import static iudx.aaa.server.apd.Constants.CREATE_TOKEN_CONSTRAINTS;
import static iudx.aaa.server.apd.Constants.CREATE_TOKEN_LINK;
import static iudx.aaa.server.apd.Constants.CREATE_TOKEN_SESSIONID;
import static iudx.aaa.server.apd.Constants.CREATE_TOKEN_STATUS;
import static iudx.aaa.server.apd.Constants.CREATE_TOKEN_SUCCESS;
import static iudx.aaa.server.apd.Constants.CREATE_TOKEN_URL;
import static iudx.aaa.server.apd.Constants.ERR_DETAIL_APD_NOT_RESPOND;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_APD_NOT_RESPOND;
import static iudx.aaa.server.apd.Constants.ERR_TITLE_POLICY_EVAL_FAILED;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_INPUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import iudx.aaa.server.apiserver.ApdStatus;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.Response.ResponseBuilder;
import iudx.aaa.server.apiserver.RoleStatus;
import iudx.aaa.server.apiserver.Roles;
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
  private static PolicyService policyService = Mockito.mock(PolicyService.class);
  private static TokenService tokenService = Mockito.mock(TokenService.class);

  private static final String DUMMY_AUTH_SERVER =
      "auth" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + "iudx.io";
  private static Future<JsonObject> trusteeUser;
  private static Future<JsonObject> otherUser;

  static String name = RandomStringUtils.randomAlphabetic(10).toLowerCase();
  static String url = name + ".com";
  static Future<UUID> orgIdFut;

  private static final UUID PENDING_APD_ID = UUID.randomUUID();
  private static final UUID ACTIVE_APD_ID = UUID.randomUUID();

  private static final String PENDING_APD = RandomStringUtils.randomAlphabetic(5).toLowerCase();
  private static final String ACTIVE_APD = RandomStringUtils.randomAlphabetic(5).toLowerCase();

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
        Map.of(Roles.CONSUMER, RoleStatus.APPROVED), false));

    CompositeFuture.all(trusteeUser, otherUser).compose(s -> {
      UUID trusteeId = UUID.fromString(trusteeUser.result().getString("userId"));
      List<Tuple> apdTup = List.of(
          Tuple.of(PENDING_APD_ID, PENDING_APD, PENDING_APD + ".com", trusteeId, ApdStatus.PENDING),
          Tuple.of(ACTIVE_APD_ID, ACTIVE_APD, ACTIVE_APD + ".com", trusteeId, ApdStatus.ACTIVE));

      return pool
          .withConnection(conn -> conn.preparedQuery(Utils.SQL_CREATE_APD).executeBatch(apdTup));
    }).onSuccess(res -> {
      apdService = new ApdServiceImpl(pool, apdWebClient, registrationService, policyService,
          tokenService, options);
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
  @DisplayName("APD ID not found in DB - should never happen")
  void apdIdNotExist(VertxTestContext testContext) {

    UUID userId = UUID.randomUUID();
    UUID providerId = UUID.randomUUID();

    JsonObject apdContext = new JsonObject().put("userId", userId.toString())
        .put("providerId", providerId.toString()).put("apdId", UUID.randomUUID().toString())
        .put("resource", RandomStringUtils.randomAlphabetic(20).toLowerCase())
        .put("resSerUrl", RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".com")
        .put("constraints", new JsonObject())
        .put("userClass", RandomStringUtils.randomAlphabetic(5).toLowerCase());

    apdService.callApd(apdContext, testContext.failing(response -> testContext.verify(() -> {
      testContext.completeNow();
    })));
  }

  @Test
  @DisplayName("Registration service (get user details) failing")
  void regServiceFail(VertxTestContext testContext) {

    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(1);
      p.fail("Fail");
      return i.getMock();
    }).when(registrationService).getUserDetails(any(), any());

    UUID userId = UUID.randomUUID();
    UUID providerId = UUID.randomUUID();

    JsonObject apdContext = new JsonObject().put("userId", userId.toString())
        .put("providerId", providerId.toString()).put("apdId", ACTIVE_APD_ID.toString())
        .put("resource", RandomStringUtils.randomAlphabetic(20).toLowerCase())
        .put("resSerUrl", RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".com")
        .put("constraints", new JsonObject())
        .put("userClass", RandomStringUtils.randomAlphabetic(5).toLowerCase());

    apdService.callApd(apdContext, testContext.failing(response -> testContext.verify(() -> {
      testContext.completeNow();
    })));
  }


  @Test
  @DisplayName("Token service (get auth server token) failing")
  void tokenServiceFail(VertxTestContext testContext) {

    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(1);
      p.fail("Fail");
      return i.getMock();
    }).when(tokenService).getAuthServerToken(any(), any());

    UUID userId = UUID.randomUUID();
    UUID providerId = UUID.randomUUID();

    JsonObject apdContext = new JsonObject().put("userId", userId.toString())
        .put("providerId", providerId.toString()).put("apdId", ACTIVE_APD_ID.toString())
        .put("resource", RandomStringUtils.randomAlphabetic(20).toLowerCase())
        .put("resSerUrl", RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".com")
        .put("constraints", new JsonObject())
        .put("userClass", RandomStringUtils.randomAlphabetic(5).toLowerCase());

    apdService.callApd(apdContext, testContext.failing(response -> testContext.verify(() -> {
      testContext.completeNow();
    })));
  }

  @Test
  @DisplayName("ApdWebClient failing - incorrect/invalid response sent by APD")
  void apdWebClientFails(VertxTestContext testContext) {
    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(1);
      List<String> ids = i.getArgument(0);
      JsonObject resp = new JsonObject();
      for (String id : ids) {
        resp.put(id, new JsonObject());
      }
      p.complete(resp);
      return i.getMock();
    }).when(registrationService).getUserDetails(any(), any());

    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(1);
      JsonObject resp = new JsonObject().put("accessToken", RandomStringUtils.randomAlphabetic(30));
      p.complete(resp);
      return i.getMock();
    }).when(tokenService).getAuthServerToken(any(), any());

    Response failureResponse = new ResponseBuilder().type(URN_INVALID_INPUT)
        .title(ERR_TITLE_APD_NOT_RESPOND).detail(ERR_DETAIL_APD_NOT_RESPOND).status(400).build();

    Mockito.when(apdWebClient.callVerifyApdEndpoint(any(), any(), any()))
        .thenReturn(Future.failedFuture(new ComposeException(failureResponse)));

    UUID userId = UUID.randomUUID();
    UUID providerId = UUID.randomUUID();

    JsonObject apdContext = new JsonObject().put("userId", userId.toString())
        .put("providerId", providerId.toString()).put("apdId", PENDING_APD_ID.toString())
        .put("resource", RandomStringUtils.randomAlphabetic(20).toLowerCase())
        .put("resSerUrl", RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".com")
        .put("constraints", new JsonObject())
        .put("userClass", RandomStringUtils.randomAlphabetic(5).toLowerCase());

    apdService.callApd(apdContext, testContext.failing(response -> testContext.verify(() -> {
      ComposeException exp = (ComposeException) response;
      Response errResp = exp.getResponse();
      assertEquals(errResp.getType(), URN_INVALID_INPUT.toString());
      assertEquals(errResp.getTitle(), ERR_TITLE_POLICY_EVAL_FAILED);
      assertEquals(errResp.getDetail(), ERR_DETAIL_APD_NOT_RESPOND + APD_NOT_ACTIVE);
      testContext.completeNow();
    })));
  }

  @Test
  @DisplayName("ApdWebClient success - APD allow")
  void apdWebClientSuccessAllow(VertxTestContext testContext) {
    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(1);
      List<String> ids = i.getArgument(0);
      JsonObject resp = new JsonObject();
      for (String id : ids) {
        resp.put(id, new JsonObject());
      }
      p.complete(resp);
      return i.getMock();
    }).when(registrationService).getUserDetails(any(), any());

    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(1);
      JsonObject resp = new JsonObject().put("accessToken", RandomStringUtils.randomAlphabetic(30));
      p.complete(resp);
      return i.getMock();
    }).when(tokenService).getAuthServerToken(any(), any());

    JsonObject webClientResp = new JsonObject().put(APD_RESP_TYPE, APD_URN_ALLOW);

    Mockito.when(apdWebClient.callVerifyApdEndpoint(any(), any(), any()))
        .thenReturn(Future.succeededFuture(webClientResp));

    UUID userId = UUID.randomUUID();
    UUID providerId = UUID.randomUUID();
    String resource = RandomStringUtils.randomAlphabetic(20).toLowerCase();
    String resSerUrl = RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".com";
    String userClass = RandomStringUtils.randomAlphabetic(5).toLowerCase();

    JsonObject apdContext = new JsonObject().put("userId", userId.toString())
        .put("providerId", providerId.toString()).put("apdId", ACTIVE_APD_ID.toString())
        .put("resource", resource).put("resSerUrl", resSerUrl).put("constraints", new JsonObject())
        .put("userClass", userClass);

    apdService.callApd(apdContext, testContext.succeeding(response -> testContext.verify(() -> {
      assertEquals(response.getString(CREATE_TOKEN_STATUS), CREATE_TOKEN_SUCCESS);
      assertEquals(response.getString(CREATE_TOKEN_CAT_ID), resource);
      assertEquals(response.getString(CREATE_TOKEN_URL), resSerUrl);
      assertEquals(response.getJsonObject(CREATE_TOKEN_CONSTRAINTS), new JsonObject());
      testContext.completeNow();
    })));
  }

  @Test
  @DisplayName("ApdWebClient success - APD deny")
  void apdWebClientSuccessDeny(VertxTestContext testContext) {
    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(1);
      List<String> ids = i.getArgument(0);
      JsonObject resp = new JsonObject();
      for (String id : ids) {
        resp.put(id, new JsonObject());
      }
      p.complete(resp);
      return i.getMock();
    }).when(registrationService).getUserDetails(any(), any());

    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(1);
      JsonObject resp = new JsonObject().put("accessToken", RandomStringUtils.randomAlphabetic(30));
      p.complete(resp);
      return i.getMock();
    }).when(tokenService).getAuthServerToken(any(), any());

    JsonObject webClientResp =
        new JsonObject().put(APD_RESP_TYPE, APD_URN_DENY).put(APD_RESP_DETAIL, "Not allowed");

    Mockito.when(apdWebClient.callVerifyApdEndpoint(any(), any(), any()))
        .thenReturn(Future.succeededFuture(webClientResp));

    UUID userId = UUID.randomUUID();
    UUID providerId = UUID.randomUUID();
    String resource = RandomStringUtils.randomAlphabetic(20).toLowerCase();
    String resSerUrl = RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".com";
    String userClass = RandomStringUtils.randomAlphabetic(5).toLowerCase();

    JsonObject apdContext = new JsonObject().put("userId", userId.toString())
        .put("providerId", providerId.toString()).put("apdId", ACTIVE_APD_ID.toString())
        .put("resource", resource).put("resSerUrl", resSerUrl).put("constraints", new JsonObject())
        .put("userClass", userClass);

    apdService.callApd(apdContext, testContext.failing(response -> testContext.verify(() -> {
      ComposeException exp = (ComposeException) response;
      Response errResp = exp.getResponse();
      assertEquals(errResp.getType(), URN_INVALID_INPUT.toString());
      assertEquals(errResp.getTitle(), ERR_TITLE_POLICY_EVAL_FAILED);
      assertEquals(errResp.getDetail(), "Not allowed");
      testContext.completeNow();
    })));
  }

  @Test
  @DisplayName("ApdWebClient success - APD deny and needs interaction")
  void apdWebClientSuccessDenySessionId(VertxTestContext testContext) {
    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(1);
      List<String> ids = i.getArgument(0);
      JsonObject resp = new JsonObject();
      for (String id : ids) {
        resp.put(id, new JsonObject());
      }
      p.complete(resp);
      return i.getMock();
    }).when(registrationService).getUserDetails(any(), any());

    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(1);
      JsonObject resp = new JsonObject().put("accessToken", RandomStringUtils.randomAlphabetic(30));
      p.complete(resp);
      return i.getMock();
    }).when(tokenService).getAuthServerToken(any(), any());

    String sessionId = UUID.randomUUID().toString();
    JsonObject webClientResp =
        new JsonObject().put(APD_RESP_TYPE, APD_URN_DENY_NEEDS_INT).put(APD_RESP_DETAIL, "Needs interaction")
            .put(APD_RESP_SESSIONID, sessionId).put(APD_RESP_LINK, ACTIVE_APD + ".com/apd");

    Mockito.when(apdWebClient.callVerifyApdEndpoint(any(), any(), any()))
        .thenReturn(Future.succeededFuture(webClientResp));

    UUID userId = UUID.randomUUID();
    UUID providerId = UUID.randomUUID();
    String resource = RandomStringUtils.randomAlphabetic(20).toLowerCase();
    String resSerUrl = RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".com";
    String userClass = RandomStringUtils.randomAlphabetic(5).toLowerCase();

    JsonObject apdContext = new JsonObject().put("userId", userId.toString())
        .put("providerId", providerId.toString()).put("apdId", ACTIVE_APD_ID.toString())
        .put("resource", resource).put("resSerUrl", resSerUrl).put("constraints", new JsonObject())
        .put("userClass", userClass);

    apdService.callApd(apdContext, testContext.succeeding(response -> testContext.verify(() -> {
      assertEquals(response.getString(CREATE_TOKEN_STATUS), CREATE_TOKEN_APD_INTERAC);
      assertEquals(response.getString(CREATE_TOKEN_LINK), ACTIVE_APD + ".com/apd");
      assertEquals(response.getString(CREATE_TOKEN_SESSIONID), sessionId);
      assertEquals(response.getString(CREATE_TOKEN_URL), ACTIVE_APD + ".com");
      testContext.completeNow();
    })));
  }
}
