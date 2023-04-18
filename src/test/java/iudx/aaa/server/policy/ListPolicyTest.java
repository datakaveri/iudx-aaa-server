
package iudx.aaa.server.policy;

import static iudx.aaa.server.apiserver.util.Urn.*;
import static iudx.aaa.server.policy.Constants.*;
import static iudx.aaa.server.policy.TestRequest.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.Set;

import iudx.aaa.server.apiserver.User;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import iudx.aaa.server.apd.ApdService;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.registration.RegistrationService;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
public class ListPolicyTest {
  private static Logger LOGGER = LogManager.getLogger(iudx.aaa.server.policy.ListPolicyTest.class);

  private static Configuration config;

  // Database Properties

  private static String databaseIP;
  private static int databasePort;
  private static String databaseName;
  private static String databaseSchema;
  private static String databaseUserName;
  private static String databasePassword;
  private static int poolSize;
  private static PgPool pgclient;
  private static PoolOptions poolOptions;
  private static PgConnectOptions connectOptions;
  private static PolicyService policyService;
  private static ApdService apdService = Mockito.mock(ApdService.class);
  private static RegistrationService registrationService;
  private static CatalogueClient catalogueClient = Mockito.mock(CatalogueClient.class);
  private static EmailClient emailClient = Mockito.mock(EmailClient.class);

  private static JsonObject authOptions;
  private static JsonObject catOptions;

  private static Vertx vertxObj;
  private static MockRegistrationFactory mockRegistrationFactory;



  @BeforeAll
  @DisplayName("Deploying Verticle")
  static void startVertx(Vertx vertx, VertxTestContext testContext) {

    config = new Configuration();
    vertxObj = vertx;
    JsonObject dbConfig = config.configLoader(0, vertx);

    // Read the configuration and set the postgres client properties.

    LOGGER.debug("Info : Reading config file");

    databaseIP = dbConfig.getString("databaseIP");
    databasePort = Integer.parseInt(dbConfig.getString("databasePort"));
    databaseName = dbConfig.getString("databaseName");
    databaseSchema = dbConfig.getString("databaseSchema");
    databaseUserName = dbConfig.getString("databaseUserName");
    databasePassword = dbConfig.getString("databasePassword");
    poolSize = Integer.parseInt(dbConfig.getString("poolSize"));
    authOptions = dbConfig.getJsonObject("authOptions");
    catOptions = dbConfig.getJsonObject("catOptions");

    /*
     * Injecting authServerUrl into 'authOptions' from config().'authServerDomain' TODO - make this
     * uniform
     */
    authOptions.put("authServerUrl", dbConfig.getString("authServerDomain"));

    /* Set Connection Object and schema */
    if (connectOptions == null) {
      Map<String, String> schemaProp = Map.of("search_path", databaseSchema);

      connectOptions =
          new PgConnectOptions().setPort(databasePort).setHost(databaseIP).setDatabase(databaseName)
              .setUser(databaseUserName).setPassword(databasePassword).setProperties(schemaProp);
    }

    // Pool options

    if (poolOptions == null) {
      poolOptions = new PoolOptions().setMaxSize(poolSize);
    }

    // Create the client pool

    pgclient = PgPool.pool(vertx, connectOptions, poolOptions);

    mockRegistrationFactory = new MockRegistrationFactory();
    registrationService = mockRegistrationFactory.getInstance();
    policyService = new PolicyServiceImpl(pgclient, registrationService, apdService,
        catalogueClient, authOptions, catOptions,emailClient);
    testContext.completeNow();
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing....");
    vertxObj.close(testContext.succeeding(response -> testContext.completeNow()));
  }

  @Test
  @DisplayName("ListPolicy Success provider")
  void listPolicySuccessProvider(VertxTestContext testContext) {

    Mockito.doAnswer(i -> {
      Map<UUID, String> result = new HashMap<UUID, String>();
      Set<UUID> ids = i.getArgument(0);
      for (UUID x : ids) {
        result.put(x, "<cat-id-placeholder>");
      }
      return Future.succeededFuture(result);
    }).when(catalogueClient).getCatIds(any(), any());

    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(2);
      JsonObject result = new JsonObject();
      List<String> ids = i.getArgument(1);
      for (String x : ids) {
        result.put(x, new JsonObject().put(URL, "<apd-url-placeholder>"));
      }
      p.complete(result);
      return i.getMock();
    }).when(apdService).getApdDetails(any(), any(), any());

    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(1);
      JsonObject result = new JsonObject();
      List<String> ids = i.getArgument(0);
      for (String x : ids) {
        result.put(x, new JsonObject());
      }
      p.complete(result);
      return i.getMock();
    }).when(registrationService).getUserDetails(any(), any());

    policyService.listPolicy(validListPolicyProvider, new JsonObject(),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
          testContext.completeNow();
        })));
  }


  @Test
  @DisplayName("ListPolicy Success consumer")
  void listPolicySuccessConsumer(VertxTestContext testContext) {

    Mockito.doAnswer(i -> {
      Map<UUID, String> result = new HashMap<UUID, String>();
      Set<UUID> ids = i.getArgument(0);
      for (UUID x : ids) {
        result.put(x, "<cat-id-placeholder>");
      }
      return Future.succeededFuture(result);
    }).when(catalogueClient).getCatIds(any(), any());

    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(2);
      JsonObject result = new JsonObject();
      List<String> ids = i.getArgument(1);
      for (String x : ids) {
        result.put(x, new JsonObject().put(URL, "<apd-url-placeholder>"));
      }
      p.complete(result);
      return i.getMock();
    }).when(apdService).getApdDetails(any(), any(), any());

    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(1);
      JsonObject result = new JsonObject();
      List<String> ids = i.getArgument(0);
      for (String x : ids) {
        result.put(x, new JsonObject());
      }
      p.complete(result);
      return i.getMock();
    }).when(registrationService).getUserDetails(any(), any());

    policyService.listPolicy(validListPolicyConsumer, new JsonObject(),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("ListPolicy Success no policy")
  void listPolicySuccess(VertxTestContext testContext) {

    policyService.listPolicy(invalidListPolicy, new JsonObject(),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_SUCCESS.toString(), response.getString(TYPE));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("ListPolicy failure")
  void listPolicyFailure(VertxTestContext testContext) {

    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(1);
      p.fail("Failure");
      return i.getMock();
    }).when(registrationService).getUserDetails(any(), any());
    policyService.listPolicy(validListPolicyProvider, new JsonObject(),
        testContext.failing(response -> testContext.verify(() -> {
          assertEquals(INTERNALERROR, response.getMessage());
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("ListPolicy failure due to NIL_UUID")
  void listPolicyNILUUID(VertxTestContext testContext){

    User nilUUID =
            new User(new JsonObject().put("userId", "00000000-0000-0000-0000-000000000000"));

    policyService.listPolicy(nilUUID, new JsonObject(),
            testContext.succeeding(response -> testContext.verify(() -> {
              assertEquals(URN_MISSING_INFO.toString(), response.getString("type"));
              assertEquals(URN_MISSING_INFO.toString(), response.getString("title"));
              assertEquals(NO_USER.toString(), response.getString("detail"));
              assertEquals(404, response.getInteger("status"));
            })));
    testContext.completeNow();
  }
}

