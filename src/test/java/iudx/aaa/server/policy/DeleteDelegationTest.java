
package iudx.aaa.server.policy;

import static iudx.aaa.server.policy.Constants.AUTH_SERVER_URL;
import static iudx.aaa.server.policy.Constants.ERR_DETAIL_DEL_DELEGATE_ROLES;
import static iudx.aaa.server.policy.Constants.ERR_TITLE_AUTH_DELE_DELETE;
import static iudx.aaa.server.policy.Constants.ERR_TITLE_INVALID_ID;
import static iudx.aaa.server.policy.Constants.ERR_TITLE_INVALID_ROLES;
import static iudx.aaa.server.policy.Constants.NIL_UUID;
import static iudx.aaa.server.policy.Constants.SUCC_TITLE_DELETE_DELE;
import static iudx.aaa.server.policy.Constants.TYPE;
import static iudx.aaa.server.policy.Constants.URN_INVALID_INPUT;
import static iudx.aaa.server.policy.Constants.URN_INVALID_ROLE;
import static iudx.aaa.server.policy.Constants.URN_SUCCESS;
import static iudx.aaa.server.registration.Utils.SQL_CREATE_ADMIN_SERVER;
import static iudx.aaa.server.registration.Utils.SQL_CREATE_DELEG;
import static iudx.aaa.server.registration.Utils.SQL_CREATE_ORG;
import static iudx.aaa.server.registration.Utils.SQL_DELETE_ORG;
import static iudx.aaa.server.registration.Utils.SQL_DELETE_SERVERS;
import static iudx.aaa.server.registration.Utils.SQL_GET_DELEG_IDS;
import static iudx.aaa.server.registration.Utils.SQL_GET_SERVER_IDS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vertx.core.CompositeFuture;
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
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.DeleteDelegationRequest;
import iudx.aaa.server.apiserver.RoleStatus;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.User.UserBuilder;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.policy.Constants.status;
import iudx.aaa.server.registration.RegistrationService;
import iudx.aaa.server.registration.Utils;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
@TestMethodOrder(OrderAnnotation.class)
public class DeleteDelegationTest {
  private static Logger LOGGER =
      LogManager.getLogger(iudx.aaa.server.policy.DeleteDelegationTest.class);

  private static Configuration config;

  // Database Properties

  private static String databaseIP;
  private static int databasePort;
  private static String databaseName;
  private static String databaseUserName;
  private static String databasePassword;
  private static int poolSize;
  private static PgPool pool;
  private static PoolOptions poolOptions;
  private static PgConnectOptions connectOptions;
  private static PolicyService policyService;
  private static RegistrationService registrationService;
  private static JsonObject catalogueOptions;
  private static JsonObject authOptions;
  private static JsonObject catOptions;

  private static Vertx vertxObj;
  private static MockRegistrationFactory mockRegistrationFactory;
  private static CatalogueClient catalogueClient = Mockito.mock(CatalogueClient.class);

  // not used, using constant
  private static final String DUMMY_AUTH_SERVER =
      "auth" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + "iudx.io";

  private static final String DUMMY_SERVER =
      "dummy" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";

  /* SQL queries for creating and deleting required data */
  static String name = RandomStringUtils.randomAlphabetic(10).toLowerCase();
  static String url = name + ".com";

  static Future<JsonObject> delegate;
  static Future<JsonObject> providerAdmin;
  static Future<JsonObject> consumer;

  static Future<UUID> orgIdFut;
  static Promise<Map<String, UUID>> delegationId = Promise.promise();

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
    databaseUserName = dbConfig.getString("databaseUserName");
    databasePassword = dbConfig.getString("databasePassword");
    poolSize = Integer.parseInt(dbConfig.getString("poolSize"));
    authOptions = dbConfig.getJsonObject("authOptions");
    catOptions = dbConfig.getJsonObject("catOptions");
    
    /*
     * Injecting authServerUrl into 'authOptions' and 'catOptions' from config().'authServerDomain'
     * TODO - make this uniform
     */
    authOptions.put("authServerUrl", dbConfig.getString("authServerDomain"));
    catOptions.put("authServerUrl", dbConfig.getString("authServerDomain"));

    // Set Connection Object
    if (connectOptions == null) {
      connectOptions = new PgConnectOptions().setPort(databasePort).setHost(databaseIP)
          .setDatabase(databaseName).setUser(databaseUserName).setPassword(databasePassword);
    }

    // Pool options
    if (poolOptions == null) {
      poolOptions = new PoolOptions().setMaxSize(poolSize);
    }

    // Create the client pool
    pool = PgPool.pool(vertx, connectOptions, poolOptions);

    orgIdFut = pool.withConnection(conn -> conn.preparedQuery(SQL_CREATE_ORG)
        .execute(Tuple.of(name, url)).map(row -> row.iterator().next().getUUID("id")));

    providerAdmin = orgIdFut.compose(id -> Utils.createFakeUser(pool, id.toString(), url,
        Map.of(Roles.PROVIDER, RoleStatus.APPROVED, Roles.ADMIN, RoleStatus.APPROVED), true));

    delegate = orgIdFut.compose(id -> Utils.createFakeUser(pool, id.toString(), url,
        Map.of(Roles.DELEGATE, RoleStatus.APPROVED), true));

    consumer = orgIdFut.compose(id -> Utils.createFakeUser(pool, NIL_UUID, "",
        Map.of(Roles.CONSUMER, RoleStatus.APPROVED), true));

    /*
     * 1. create organization 2. create 3 users 3. create 1 resource server with providerAdmin as
     * admin 4. create 2 delegations, one for provider -> delegate on authsrv, one for other server
     * to delegate 5. AS delegate/consumer, must not be able to delete 6. AS auth delegate must be
     * able to delete, assert cannot be deleted again 7. AS auth delegate, must not be able to
     * delete auth deleg but provider must be able to
     */

    mockRegistrationFactory = new MockRegistrationFactory();
    CompositeFuture.all(orgIdFut, providerAdmin, delegate, consumer).onSuccess(res -> {

      UUID apId = UUID.fromString(providerAdmin.result().getString("userId"));
      UUID deleId = UUID.fromString(delegate.result().getString("userId"));

      List<Tuple> servers = List.of(Tuple.of("Other Server", apId, DUMMY_SERVER));
      Tuple getServId = Tuple.of(List.of(AUTH_SERVER_URL, DUMMY_SERVER).toArray());

      Collector<Row, ?, Map<String, UUID>> serverIds =
          Collectors.toMap(row -> row.getString("url"), row -> row.getUUID("id"));

      /* Get map of resource server Id - delegation ID to know which to delete */
      Collector<Row, ?, Map<String, UUID>> srvIdDelegId =
          Collectors.toMap(row -> row.getString("url"), row -> row.getUUID("id"));

      pool.withConnection(conn -> conn.preparedQuery(SQL_CREATE_ADMIN_SERVER).executeBatch(servers)
          .compose(succ -> conn.preparedQuery(SQL_GET_SERVER_IDS).collecting(serverIds)
              .execute(getServId).map(r -> r.value()))
          .map(i -> {

            return List.of(Tuple.of(apId, deleId, i.get(DUMMY_SERVER), status.ACTIVE.toString()),
                Tuple.of(apId, deleId, i.get(AUTH_SERVER_URL), status.ACTIVE.toString()));

          }).compose(j -> conn.preparedQuery(SQL_CREATE_DELEG).executeBatch(j))
          .compose(k -> conn.preparedQuery(SQL_GET_DELEG_IDS).collecting(srvIdDelegId)
              .execute(getServId.addUUID(apId)))
          .map(val -> val.value()).onSuccess(s -> {
            delegationId.complete(s);
            registrationService = mockRegistrationFactory.getInstance();
            policyService = new PolicyServiceImpl(pool, registrationService, catalogueClient,
                authOptions, catOptions);
            testContext.completeNow();
          }));
    });
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing....");
    Tuple servers = Tuple.of(List.of(DUMMY_SERVER).toArray());
    List<JsonObject> users = List.of(providerAdmin.result(), delegate.result(), consumer.result());

    pool.withConnection(conn -> conn.preparedQuery(SQL_DELETE_SERVERS).execute(servers)
        .compose(success -> Utils.deleteFakeUser(pool, users))
        .compose(succ -> conn.preparedQuery(SQL_DELETE_ORG).execute(Tuple.of(orgIdFut.result()))))
        .onComplete(x -> {
          if (x.failed()) {
            LOGGER.warn(x.cause().getMessage());
          }
          vertxObj.close(testContext.succeeding(response -> testContext.completeNow()));
        });
  }

  /* These tests require ordering -> provider deletes the auth delegate delegation at the end */

  @Test
  @DisplayName("Invalid delegation ID")
  @Order(1)
  void listDelegationAsProvider(VertxTestContext testContext) {

    JsonObject userJson = providerAdmin.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.PROVIDER, Roles.ADMIN)).build();

    String randUuid = UUID.randomUUID().toString();
    String otherDelegId = delegationId.future().result().get(DUMMY_SERVER).toString();
    JsonArray req = new JsonArray().add(new JsonObject().put("id", randUuid))
        .add(new JsonObject().put("id", otherDelegId));

    List<DeleteDelegationRequest> request = DeleteDelegationRequest.jsonArrayToList(req);

    policyService.deleteDelegation(request, user, new JsonObject(),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_INVALID_INPUT, response.getString(TYPE));
          assertEquals(ERR_TITLE_INVALID_ID, response.getString("title"));
          assertEquals(randUuid, response.getString("detail"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Fail delete auth delegation as auth delegate")
  @Order(2)
  void deleteAuthDelegationAsAuthDelegate(VertxTestContext testContext) {

    JsonObject userJson = delegate.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.DELEGATE)).build();

    String authDelegId = delegationId.future().result().get(AUTH_SERVER_URL).toString();
    JsonArray req = new JsonArray().add(new JsonObject().put("id", authDelegId));
    List<DeleteDelegationRequest> request = DeleteDelegationRequest.jsonArrayToList(req);

    JsonObject providerDetails =
        new JsonObject().put("providerId", providerAdmin.result().getString("userId"));

    policyService.deleteDelegation(request, user, providerDetails,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_INVALID_INPUT, response.getString(TYPE));
          assertEquals(ERR_TITLE_AUTH_DELE_DELETE, response.getString("title"));
          assertEquals(authDelegId, response.getString("detail"));
          assertEquals(403, response.getInteger("status"));
          testContext.completeNow();
        })));
  }

  @Test
  @DisplayName("Delete dummy server delegation as auth delegate, then cannot delete as provider")
  @Order(3)
  void deleteDelegationAsDelegate(VertxTestContext testContext) {

    JsonObject userJson = delegate.result();

    User user = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.DELEGATE)).build();

    JsonObject provJson = providerAdmin.result();
    User providerUser = new UserBuilder().keycloakId(provJson.getString("keycloakId"))
        .userId(provJson.getString("userId"))
        .name(provJson.getString("firstName"), provJson.getString("lastName"))
        .roles(List.of(Roles.PROVIDER, Roles.ADMIN)).build();

    String delegId = delegationId.future().result().get(DUMMY_SERVER).toString();
    JsonArray req = new JsonArray().add(new JsonObject().put("id", delegId));
    List<DeleteDelegationRequest> request = DeleteDelegationRequest.jsonArrayToList(req);

    Checkpoint deleted = testContext.checkpoint();
    Checkpoint cannotDelete = testContext.checkpoint();
    Promise<Void> p = Promise.promise();

    JsonObject providerDetails =
        new JsonObject().put("providerId", providerAdmin.result().getString("userId"));

    policyService.deleteDelegation(request, user, providerDetails,
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_SUCCESS, response.getString(TYPE));
          assertEquals(SUCC_TITLE_DELETE_DELE, response.getString("title"));
          assertEquals(200, response.getInteger("status"));
          deleted.flag();
          p.complete();
        })));

    p.future().onSuccess(x -> {
      policyService.deleteDelegation(request, providerUser, new JsonObject(),
          testContext.succeeding(response -> testContext.verify(() -> {
            assertEquals(URN_INVALID_INPUT, response.getString(TYPE));
            assertEquals(ERR_TITLE_INVALID_ID, response.getString("title"));
            assertEquals(delegId, response.getString("detail"));
            assertEquals(400, response.getInteger("status"));
            cannotDelete.flag();
          })));
    });
  }

  @Test
  @DisplayName("Fail at get delegations as consumer/delegate")
  @Order(4)
  void delDelegationAsConsumerDele(VertxTestContext testContext) {

    JsonObject userJson = consumer.result();

    User userC = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(userJson.getString("userId"))
        .name(userJson.getString("firstName"), userJson.getString("lastName"))
        .roles(List.of(Roles.CONSUMER)).build();

    JsonObject deleJson = delegate.result();

    User userD = new UserBuilder().keycloakId(userJson.getString("keycloakId"))
        .userId(deleJson.getString("userId"))
        .name(deleJson.getString("firstName"), deleJson.getString("lastName"))
        .roles(List.of(Roles.DELEGATE)).build();

    Checkpoint consFail = testContext.checkpoint();
    Checkpoint deleFail = testContext.checkpoint();

    String authDelegId = delegationId.future().result().get(AUTH_SERVER_URL).toString();
    JsonArray req = new JsonArray().add(new JsonObject().put("id", authDelegId));
    List<DeleteDelegationRequest> request = DeleteDelegationRequest.jsonArrayToList(req);

    policyService.deleteDelegation(request, userC, new JsonObject(),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_INVALID_ROLE, response.getString(TYPE));
          assertEquals(ERR_DETAIL_DEL_DELEGATE_ROLES, response.getString("detail"));
          assertEquals(ERR_TITLE_INVALID_ROLES, response.getString("title"));
          assertEquals(401, response.getInteger("status"));
          consFail.flag();
        })));

    policyService.deleteDelegation(request, userD, new JsonObject(),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_INVALID_ROLE, response.getString(TYPE));
          assertEquals(ERR_DETAIL_DEL_DELEGATE_ROLES, response.getString("detail"));
          assertEquals(ERR_TITLE_INVALID_ROLES, response.getString("title"));
          assertEquals(401, response.getInteger("status"));
          deleFail.flag();
        })));
  }

  @Test
  @DisplayName("Delete auth delegation as provider")
  @Order(5)
  void deleteAuthDelegationAsProvider(VertxTestContext testContext) {

    JsonObject provJson = providerAdmin.result();
    User providerUser = new UserBuilder().keycloakId(provJson.getString("keycloakId"))
        .userId(provJson.getString("userId"))
        .name(provJson.getString("firstName"), provJson.getString("lastName"))
        .roles(List.of(Roles.PROVIDER, Roles.ADMIN)).build();

    String delegId = delegationId.future().result().get(AUTH_SERVER_URL).toString();
    JsonArray req = new JsonArray().add(new JsonObject().put("id", delegId));
    List<DeleteDelegationRequest> request = DeleteDelegationRequest.jsonArrayToList(req);

    policyService.deleteDelegation(request, providerUser, new JsonObject(),
        testContext.succeeding(response -> testContext.verify(() -> {
          assertEquals(URN_SUCCESS, response.getString(TYPE));
          assertEquals(SUCC_TITLE_DELETE_DELE, response.getString("title"));
          assertEquals(200, response.getInteger("status"));
          testContext.completeNow();
        })));
  }
}

