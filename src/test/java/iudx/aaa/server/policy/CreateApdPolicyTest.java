package iudx.aaa.server.policy;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apd.ApdService;
import iudx.aaa.server.apiserver.CreatePolicyRequest;
import iudx.aaa.server.apiserver.ResourceObj;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.RoleStatus;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.User;
import iudx.aaa.server.apiserver.util.ComposeException;
import iudx.aaa.server.apiserver.util.Urn;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.registration.RegistrationServiceImpl;
import iudx.aaa.server.registration.Utils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static iudx.aaa.server.apd.Constants.ERR_TITLE_INVALID_REQUEST_ID;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_INPUT;
import static iudx.aaa.server.apiserver.util.Urn.URN_INVALID_ROLE;
import static iudx.aaa.server.apiserver.util.Urn.URN_SUCCESS;
import static iudx.aaa.server.policy.Constants.ID;
import static iudx.aaa.server.policy.Constants.INVALID_ROLE;
import static iudx.aaa.server.policy.Constants.ITEMNOTFOUND;
import static iudx.aaa.server.policy.Constants.NO_AUTH_TRUSTEE_POLICY;
import static iudx.aaa.server.policy.Constants.STATUS;
import static iudx.aaa.server.policy.Constants.URL;
import static iudx.aaa.server.registration.Utils.SQL_CREATE_APD;
import static iudx.aaa.server.registration.Utils.SQL_DELETE_ANY_POLICIES;
import static iudx.aaa.server.registration.Utils.SQL_DELETE_APD;
import static iudx.aaa.server.registration.Utils.SQL_DELETE_DELEGATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
public class CreateApdPolicyTest {
  static String name = RandomStringUtils.randomAlphabetic(10).toLowerCase();
  // Database Properties
  static String url = name + ".com";
  static Future<UUID> orgIdFut;
  private static Logger LOGGER =
      LogManager.getLogger(iudx.aaa.server.policy.CreateUserPolicyTest.class);
  private static Configuration config;
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
  private static JsonObject authOptions;
  private static JsonObject catalogueOptions;
  private static Future<JsonObject> adminUser;
  private static Future<JsonObject> providerUser;
  private static Future<JsonObject> authDelUser;
  private static Future<JsonObject> consumerUser;
  private static Future<JsonObject> trusteeUser;
  private static JsonObject catOptions;
  private static UUID authSerId;
  private static String authServerURL;
  private static UUID otherSerId;
  private static String otherServerURL;
  private static UUID apdId;
  private static UUID resourceGrpID;
  private static String apdURL;
  private static Vertx vertxObj;
  private static PolicyService policyService;
  private static ApdService apdService = Mockito.mock(ApdService.class);
  private static RegistrationServiceImpl registrationService =
      Mockito.mock(RegistrationServiceImpl.class);
  private static CatalogueClient catalogueClient = Mockito.mock(CatalogueClient.class);
  private static EmailClient emailClient = Mockito.mock(EmailClient.class);


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
    catalogueOptions = dbConfig.getJsonObject("catalogueOptions");
    catOptions = dbConfig.getJsonObject("catOptions");
    authServerURL = "auth" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";
    otherServerURL = "other" + RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";
    apdURL = "apd."+ RandomStringUtils.randomAlphabetic(5).toLowerCase() + ".iudx.io";
    authOptions.put("authServerUrl", authServerURL);
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

    // Pool options

    if (poolOptions == null) {
      poolOptions = new PoolOptions().setMaxSize(poolSize);
    }

    // Create the client pool

    pgclient = PgPool.pool(vertx, connectOptions, poolOptions);

    // create org
    orgIdFut =
        pgclient.withConnection(
            conn ->
                conn.preparedQuery(Utils.SQL_CREATE_ORG)
                    .execute(Tuple.of(name, url))
                    .map(row -> row.iterator().next().getUUID("id")));

    // create users with diff roles
    adminUser =
        orgIdFut.compose(
            orgId ->
                Utils.createFakeUser(
                    pgclient,
                    orgId.toString(),
                    "",
                    Map.of(Roles.ADMIN, RoleStatus.APPROVED),
                    false));
    providerUser =
        orgIdFut.compose(
            orgId ->
                Utils.createFakeUser(
                    pgclient,
                    orgId.toString(),
                    "",
                    Map.of(Roles.ADMIN, RoleStatus.APPROVED),
                    false));

    trusteeUser =
        orgIdFut.compose(
            orgId ->
                Utils.createFakeUser(
                    pgclient,
                    orgId.toString(),
                    "",
                    Map.of(Roles.TRUSTEE, RoleStatus.APPROVED),
                    false));

    authDelUser =
        orgIdFut.compose(
            orgId ->
                Utils.createFakeUser(
                    pgclient,
                    orgId.toString(),
                    "",
                    Map.of(Roles.DELEGATE, RoleStatus.APPROVED),
                    false));
    consumerUser =
        orgIdFut.compose(
            orgId ->
                Utils.createFakeUser(
                    pgclient,
                    orgId.toString(),
                    "",
                    Map.of(Roles.CONSUMER, RoleStatus.APPROVED),
                    false));

    CompositeFuture.all(adminUser, providerUser, authDelUser, consumerUser,trusteeUser)
        .onSuccess(
            succ -> {
              // create all servers
              authSerId = UUID.randomUUID();
              otherSerId = UUID.randomUUID();
              resourceGrpID = UUID.randomUUID();
              apdId = UUID.randomUUID();
              //create APD with  id,name,url,owner_id(admin user),status,created_at,updated_at
              Tuple apdTuple = Tuple.of(apdId,RandomStringUtils.randomAlphabetic(5),apdURL,
              UUID.fromString(trusteeUser.result().getString("userId")), Constants.status.ACTIVE);
              pgclient.withConnection(conn -> conn.preparedQuery(SQL_CREATE_APD).execute(apdTuple))
              .compose(ar->
                  Utils.createFakeResourceServer(pgclient, adminUser.result(), authSerId, authServerURL))
                  .compose(
                      comp ->
                          Utils.createFakeResourceServer(
                              pgclient, adminUser.result(), otherSerId, otherServerURL))
                  .compose(
                      comp ->
                          Utils.createFakeResourceGroup(
                              pgclient,
                              providerUser.result(),
                              otherSerId,
                              resourceGrpID,
                              // putting some random value in cat ID 
                              RandomStringUtils.randomAlphabetic(10)))
                  .compose(
                      pol ->
                          // policy for provider user by admin user for all servers
                          Utils.createFakePolicy(
                              pgclient,
                              UUID.fromString(providerUser.result().getString("userId")),
                              Constants.itemTypes.RESOURCE_SERVER,
                              UUID.fromString(adminUser.result().getString("userId")),
                              authSerId))
                  .compose(
                      otherPol ->
                          Utils.createFakePolicy(
                              pgclient,
                              UUID.fromString(providerUser.result().getString("userId")),
                              Constants.itemTypes.RESOURCE_SERVER,
                              UUID.fromString(adminUser.result().getString("userId")),
                              otherSerId))
                  .compose(
                      proPol ->
                          Utils.createFakePolicy(
                              pgclient,
                              UUID.fromString(authDelUser.result().getString("userId")),
                              Constants.itemTypes.RESOURCE_SERVER,
                              UUID.fromString(adminUser.result().getString("userId")),
                              authSerId))
                  // policy for authDelUser by providerUser
                  .compose(
                      proPol ->
                          Utils.createFakePolicy(
                              pgclient,
                              UUID.fromString(authDelUser.result().getString("userId")),
                              Constants.itemTypes.RESOURCE_GROUP,
                              UUID.fromString(providerUser.result().getString("userId")),
                              otherSerId))
                  // delegation for authDelUser for auth servers
                  .compose(
                      del ->
                          Utils.createDelegation(
                              pgclient,
                              UUID.fromString(authDelUser.result().getString("userId")),
                              UUID.fromString(providerUser.result().getString("userId")),
                              authSerId))
                  .compose(
                      proPol ->
                          Utils.createFakePolicy(
                              pgclient,
                              UUID.fromString(providerUser.result().getString("userId")),
                              Constants.itemTypes.RESOURCE_SERVER,
                              UUID.fromString(trusteeUser.result().getString("userId")),
                              apdId))

                  .onSuccess(
                      success -> {
                        policyService =
                            new PolicyServiceImpl(
                                pgclient,
                                registrationService,
                                apdService,
                                catalogueClient,
                                authOptions,
                                catOptions,emailClient);
                        testContext.completeNow();
                      })
                  .onFailure(
                      failure -> {
                        testContext.failNow("failed " + failure.toString());
                      });
            });
  }

  @AfterAll
  public static void finish(VertxTestContext testContext){
    LOGGER.info("Finishing....");
    Tuple servers = Tuple.of(List.of(authServerURL, otherServerURL).toArray());
    UUID adminId = UUID.fromString(adminUser.result().getString("userId"));
    UUID providerId = UUID.fromString(providerUser.result().getString("userId"));
    UUID delegateId = UUID.fromString(authDelUser.result().getString("userId"));
    UUID trusteeId = UUID.fromString(authDelUser.result().getString("userId"));
    Tuple policyOwners = Tuple.of(List.of(adminId, providerId, delegateId,trusteeId).toArray(UUID[]::new));

    List<JsonObject> users =
        List.of(
            providerUser.result(), authDelUser.result(), consumerUser.result(), adminUser.result(),
            trusteeUser.result());
        pgclient.withConnection(
        conn -> conn.preparedQuery(SQL_DELETE_APD).execute(policyOwners))
        .compose(resGrp -> Utils.deleteFakeResourceServer(pgclient, users))
        .compose(
            ar ->
                pgclient.withConnection(
                    conn -> conn.preparedQuery(SQL_DELETE_DELEGATE).execute(policyOwners)))
        .compose(
            bb ->
                pgclient.withConnection(
                    conn -> conn.preparedQuery(SQL_DELETE_ANY_POLICIES).execute(policyOwners)))
        .onComplete(
            x -> {
              if (x.failed()) {
                LOGGER.warn(x.cause().getMessage());
                LOGGER.error(
                    "Data is NOT deleted after this test since apd table"
                        + " does not allow deletes and therefore cascade delete fails");
              }
              vertxObj.close(testContext.succeeding(response -> testContext.completeNow()));
            });
  }


  @Test
  @DisplayName("Testing  APD policy creation as incorrect user")
  void invalidApdPolicyRole(VertxTestContext testContext){

    JsonObject userJson = consumerUser.result();
    User user =
        new User.UserBuilder()
            .keycloakId(userJson.getString("keycloakId"))
            .userId(userJson.getString("userId"))
            .name(userJson.getString("firstName"), userJson.getString("lastName"))
            .roles(List.of(Roles.CONSUMER))
            .build();
    JsonObject obj = new JsonObject();
    obj.put("itemId","").put("itemType","RESOURCE_GROUP").put("apdId",apdURL)
        .put("userClass","").put("constraints",new JsonObject());
    List<CreatePolicyRequest> req =
        CreatePolicyRequest.jsonArrayToList(new JsonArray().add(obj));
    policyService.createPolicy(
        req,
        user,
        new JsonObject(),
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                     assertEquals(URN_INVALID_ROLE.toString(), response.getString("type"));
                     assertEquals(INVALID_ROLE, response.getString("title"));
                     assertEquals(403, response.getInteger("status"));
                      testContext.completeNow();
                    })));
  }


  @Test
  @DisplayName("Testing invalid APD policy creation - apd does not exist ")
  void invalidApdId(VertxTestContext testContext) {

    JsonObject userJson = providerUser.result();
    User user =
        new User.UserBuilder()
            .keycloakId(userJson.getString("keycloakId"))
            .userId(userJson.getString("userId"))
            .name(userJson.getString("firstName"), userJson.getString("lastName"))
            .roles(List.of(Roles.PROVIDER))
            .build();
    
    UUID randomItemId =UUID.randomUUID();

    JsonObject validCatItem =
        new JsonObject()
            .put("cat_id", "")
            .put("itemType", "resource_group")
            .put("owner_id", providerUser.result().getString("userId"))
            .put("id", randomItemId)
            .put("resource_group_id", UUID.randomUUID())
            .put("resource_server_id", otherSerId.toString());

    ResourceObj resourceObj = new ResourceObj(validCatItem);
    Map<String, ResourceObj> resp = new HashMap<>();
    resp.put(resourceObj.getId().toString(), resourceObj);
    Mockito.when(catalogueClient.checkReqItems(any())).thenReturn(Future.succeededFuture(resp));

    String randomAPD = RandomStringUtils.randomAlphabetic(5);
    Mockito.doAnswer(i -> {
      Promise<JsonObject> p = i.getArgument(2);
      List<String> urls = i.getArgument(0);
      p.fail(  new ComposeException(
          400,
          URN_INVALID_INPUT,
          ERR_TITLE_INVALID_REQUEST_ID,
          urls.toString()));
      return i.getMock();
    }).when(apdService).getApdDetails(any(), any(), any());


    JsonObject obj = new JsonObject();
    obj.put("itemId",randomItemId.toString()).put("itemType","RESOURCE_GROUP").put("apdId",randomAPD)
        .put("userClass","").put("constraints",new JsonObject());
    List<CreatePolicyRequest> req =
        CreatePolicyRequest.jsonArrayToList(new JsonArray().add(obj));
    policyService.createPolicy(
        req,
        user,
        new JsonObject(),
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                     assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
                     assertEquals(ERR_TITLE_INVALID_REQUEST_ID, response.getString("title"));
                     assertEquals(400, response.getInteger("status"));
                      testContext.completeNow();
                    })));
  }


  @Test
  @DisplayName("Testing apd Policy table - incorrect resource ID ")
  void invalidAPDPolicyItemId(VertxTestContext testContext)
    {

      JsonObject userJson = providerUser.result();
      User user =
          new User.UserBuilder()
              .keycloakId(userJson.getString("keycloakId"))
              .userId(userJson.getString("userId"))
              .name(userJson.getString("firstName"), userJson.getString("lastName"))
              .roles(List.of(Roles.PROVIDER))
              .build();
      UUID randomItemId = UUID.randomUUID();

      Response r =
          new Response.ResponseBuilder()
              .type(Urn.URN_INVALID_INPUT.toString())
              .title(ITEMNOTFOUND)
              .detail(randomItemId.toString())
              .status(400)
              .build();
      ComposeException exception = new ComposeException(r);
      Mockito.when(catalogueClient.checkReqItems(any())).thenReturn(Future.failedFuture(exception));

      String randomAPD = RandomStringUtils.randomAlphabetic(5);
      Mockito.doAnswer(i -> {
        Promise<JsonObject> p = i.getArgument(2);
        JsonObject result = new JsonObject();
        List<String> ids = i.getArgument(0);
        for (String x : ids) {
          result.put(x, new JsonObject().put(URL, "<apd-url-placeholder>"));
        }
        p.complete(result);
        return i.getMock();
      }).when(apdService).getApdDetails(any(), any(), any());


      JsonObject obj = new JsonObject();
      obj.put("itemId",randomItemId.toString()).put("itemType","RESOURCE_GROUP").put("apdId",randomAPD)
          .put("userClass","").put("constraints",new JsonObject());
      List<CreatePolicyRequest> req =
          CreatePolicyRequest.jsonArrayToList(new JsonArray().add(obj));
      policyService.createPolicy(
          req,
          user,
          new JsonObject(),
          testContext.succeeding(
              response ->
                  testContext.verify(
                      () -> {
                       assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
                       assertEquals(ITEMNOTFOUND, response.getString("title"));
                       assertEquals(400, response.getInteger("status"));
                        testContext.completeNow();
                      })));
    }

  @Test
  @DisplayName("Testing apd Policy table - user does not have policy by trustee ")
  void noTrusteePolicy(VertxTestContext testContext)
  {

    JsonObject userJson = authDelUser.result();
    User user =
        new User.UserBuilder()
            .keycloakId(userJson.getString("keycloakId"))
            .userId(userJson.getString("userId"))
            .name(userJson.getString("firstName"), userJson.getString("lastName"))
            .roles(List.of(Roles.PROVIDER))
            .build();


    JsonObject validCatItem =
        new JsonObject()
            .put("cat_id", "")
            .put("itemType", "resource_group")
            .put("owner_id", providerUser.result().getString("userId"))
            .put("id",resourceGrpID)
            .put("resource_group_id", resourceGrpID)
            .put("resource_server_id", otherSerId.toString());

    ResourceObj resourceObj = new ResourceObj(validCatItem);
    Map<String, ResourceObj> resp = new HashMap<>();
    resp.put(resourceObj.getId().toString(), resourceObj);
    Mockito.when(catalogueClient.checkReqItems(any())).thenReturn(Future.succeededFuture(resp));

    String randomAPD = RandomStringUtils.randomAlphabetic(5);
    Mockito.doAnswer(
            i -> {
              Promise<JsonObject> p = i.getArgument(2);
              JsonObject result = new JsonObject();
              List<String> ids = i.getArgument(0);
              for (String x : ids) {
                result.put(
                    x,
                    new JsonObject()
                        .put(URL, "<apd-url-placeholder>")
                        .put(STATUS, "active")
                        .put(STATUS, "active")
                        .put(STATUS, "active")
                        .put(ID,apdId)
                );
              }
              p.complete(result);
              return i.getMock();
            })
        .when(apdService)
        .getApdDetails(any(), any(), any());

    JsonObject obj = new JsonObject();
    obj.put("itemId",resourceGrpID.toString()).put("itemType","RESOURCE_GROUP").put("apdId",randomAPD)
        .put("userClass","").put("constraints",new JsonObject());
    List<CreatePolicyRequest> req =
        CreatePolicyRequest.jsonArrayToList(new JsonArray().add(obj));
    policyService.createPolicy(
        req,
        user,
        new JsonObject(),
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                    assertEquals(URN_INVALID_INPUT.toString(), response.getString("type"));
                    assertEquals(NO_AUTH_TRUSTEE_POLICY, response.getString("title"));
                    assertEquals(403, response.getInteger("status"));
                      testContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Testing apd Policy table - user does not have policy by trustee ")
  void successApdPolicyCreation(VertxTestContext testContext)
  {

    JsonObject userJson = providerUser.result();
    User user =
        new User.UserBuilder()
            .keycloakId(userJson.getString("keycloakId"))
            .userId(userJson.getString("userId"))
            .name(userJson.getString("firstName"), userJson.getString("lastName"))
            .roles(List.of(Roles.PROVIDER))
            .build();


    JsonObject validCatItem =
        new JsonObject()
            .put("cat_id", "")
            .put("itemType", "resource_group")
            .put("owner_id", providerUser.result().getString("userId"))
            .put("id",resourceGrpID)
            .put("resource_group_id", resourceGrpID)
            .put("resource_server_id", otherSerId.toString());

    ResourceObj resourceObj = new ResourceObj(validCatItem);
    Map<String, ResourceObj> resp = new HashMap<>();
    resp.put(resourceObj.getId().toString(), resourceObj);
    Mockito.when(catalogueClient.checkReqItems(any())).thenReturn(Future.succeededFuture(resp));

    String randomAPD = RandomStringUtils.randomAlphabetic(5);
    Mockito.doAnswer(
        i -> {
          Promise<JsonObject> p = i.getArgument(2);
          JsonObject result = new JsonObject();
          List<String> ids = i.getArgument(0);
          for (String x : ids) {
            result.put(
                x,
                new JsonObject()
                    .put(URL, "<apd-url-placeholder>")
                    .put(STATUS, "active")
                    .put(STATUS, "active")
                    .put(STATUS, "active")
                    .put(ID,apdId)
            );
          }
          p.complete(result);
          return i.getMock();
        })
        .when(apdService)
        .getApdDetails(any(), any(), any());

    JsonObject obj = new JsonObject();
    obj.put("itemId",resourceGrpID.toString()).put("itemType","RESOURCE_GROUP").put("apdId",randomAPD)
        .put("userClass","").put("constraints",new JsonObject());
    List<CreatePolicyRequest> req =
        CreatePolicyRequest.jsonArrayToList(new JsonArray().add(obj));
    policyService.createPolicy(
        req,
        user,
        new JsonObject(),
        testContext.succeeding(
            response ->
                testContext.verify(
                    () -> {
                    assertEquals(URN_SUCCESS.toString(), response.getString("type"));
                    assertEquals("added policies", response.getString("title"));
                    assertEquals(200, response.getInteger("status"));
                      testContext.completeNow();
                    })));
  }

}
