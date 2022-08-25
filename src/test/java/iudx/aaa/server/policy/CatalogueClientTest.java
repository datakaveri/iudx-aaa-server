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
import iudx.aaa.server.apiserver.ResourceObj;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.RoleStatus;
import iudx.aaa.server.apiserver.Roles;
import iudx.aaa.server.apiserver.util.ComposeException;
import iudx.aaa.server.apiserver.util.Urn;
import iudx.aaa.server.configuration.Configuration;
import iudx.aaa.server.registration.Utils;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static iudx.aaa.server.policy.Constants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
public class CatalogueClientTest {

  static String name = RandomStringUtils.randomAlphabetic(10).toLowerCase();

  // Database Properties
  static String url = name + ".com";
  static Future<UUID> orgIdFut;
  private static Logger LOGGER =
      LogManager.getLogger(iudx.aaa.server.policy.CatalogueClientTest.class);
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
  private static CatalogueClient catalogueClient;
  private static CatalogueClient spyCatalogueClient;
  private static Future<JsonObject> adminUser;
  private static Future<JsonObject> providerUser;
  private static UUID resourceSerID;
  private static UUID resourceGrpID;
  private static UUID resourceIdOne;
  private static String resourceServerUrl;
  private static String resourceGrp;
  private static String resourceOne;
  private static Vertx vertxObj;

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

    orgIdFut =
        pgclient.withConnection(
            conn ->
                conn.preparedQuery(Utils.SQL_CREATE_ORG)
                    .execute(Tuple.of(name, url))
                    .map(row -> row.iterator().next().getUUID("id")));
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
                    Map.of(Roles.PROVIDER, RoleStatus.APPROVED),
                    false));

    CompositeFuture.all(adminUser, providerUser)
        .onSuccess(
            succ -> {
              resourceGrpID = UUID.randomUUID();
              resourceSerID = UUID.randomUUID();
              resourceIdOne = UUID.randomUUID();
              resourceServerUrl = "rs.server.com";
              resourceGrp = "emailsha/xyz/"+ resourceServerUrl+"/rsg";
              resourceOne = resourceGrp + "/ri1";
              Utils.createFakeResourceServer(
                      pgclient, adminUser.result(), resourceSerID, resourceServerUrl)
                  .compose(
                      resServer ->
                          Utils.createFakeResourceGroup(
                              pgclient,
                              providerUser.result(),
                              resourceSerID,
                              resourceGrpID,
                              resourceGrp))
                  .compose(
                      resGrp ->
                          Utils.createFakeResource(
                              pgclient,
                              providerUser.result(),
                              resourceIdOne,
                              resourceSerID,
                              resourceGrpID,
                              resourceOne))
                  .onSuccess(
                      success -> {
                        catalogueClient = new CatalogueClient(vertxObj, pgclient, catalogueOptions);
                        spyCatalogueClient = Mockito.spy(catalogueClient);
                        testContext.completeNow();
                      })
                  .onFailure(
                      failure -> {
                        testContext.failNow("failed " + failure.toString());
                      });
            });
  }

  @AfterAll
  public static void finish(VertxTestContext testContext) {
    LOGGER.info("Finishing....");
    List<JsonObject> users = List.of(adminUser.result(), providerUser.result());
    Utils.deleteFakeResource(pgclient, users)
        .compose(succ -> Utils.deleteFakeResourceGrp(pgclient, users))
        .compose(resGrp -> Utils.deleteFakeResourceServer(pgclient, users))
        .compose(resSer -> Utils.deleteFakeUser(pgclient, users))
        .compose(
            user ->
                pgclient.withConnection(
                    conn ->
                        conn.preparedQuery(Utils.SQL_DELETE_ORG)
                            .execute(Tuple.of(orgIdFut.result()))))
        .onComplete(
            x -> {
              if (x.failed()) {
                LOGGER.warn(x.cause().getMessage());
              }
              vertxObj.close(testContext.succeeding(response -> testContext.completeNow()));
            });
  }

  @Test
  @DisplayName("Test resource does not exist")
  void InvalidRes(VertxTestContext testContext) {
    String item =
        RandomStringUtils.randomAlphabetic(2)
            + "/"
            + RandomStringUtils.randomAlphabetic(2)
            + "/"
            + RandomStringUtils.randomAlphabetic(2)
            + "/"
            + RandomStringUtils.randomAlphabetic(2)
            + "/"
            + RandomStringUtils.randomAlphabetic(2);
    List<String> resItems = new ArrayList<>();
    resItems.add(item);
    Map<String, List<String>> catItem = new HashMap<>();
    catItem.put(RES, resItems);
    Response r =
        new Response.ResponseBuilder()
            .type(Urn.URN_INVALID_INPUT.toString())
            .title(ITEMNOTFOUND)
            .detail(item)
            .status(400)
            .build();
    ComposeException e = new ComposeException(r);

    Mockito.doReturn(Future.failedFuture(e)).when(spyCatalogueClient).fetchItem(Mockito.any());

    spyCatalogueClient
        .checkReqItems(catItem)
        .onFailure(
            fail -> {
              JsonObject response = new JsonObject();
              if (fail instanceof ComposeException) {
                ComposeException exp = (ComposeException) fail;
                response = exp.getResponse().toJson();
                assertEquals(response.getString(TITLE), ITEMNOTFOUND);
                assertEquals(response.getInteger(STATUS), 400);
                assertEquals(response.getString(TYPE), Urn.URN_INVALID_INPUT.toString());
              }
              testContext.completeNow();
            });
  }

  @Test
  @DisplayName("Test resource group does not exist")
  void InvalidResGrp(VertxTestContext testContext) {
      String item =
              RandomStringUtils.randomAlphabetic(2)
                      + "/"
                      + RandomStringUtils.randomAlphabetic(2)
                      + "/"
                      + RandomStringUtils.randomAlphabetic(2)
                      + "/"
                      + RandomStringUtils.randomAlphabetic(2);
      List<String> resItems = new ArrayList<>();
      resItems.add(item);
      Map<String, List<String>> catItem = new HashMap<>();
      catItem.put(RES_GRP, resItems);
      Response r =
              new Response.ResponseBuilder()
                      .type(Urn.URN_INVALID_INPUT.toString())
                      .title(ITEMNOTFOUND)
                      .detail(item)
                      .status(400)
                      .build();
      ComposeException e = new ComposeException(r);

      Mockito.doReturn(Future.failedFuture(e)).when(spyCatalogueClient).fetchItem(Mockito.any());

      spyCatalogueClient
              .checkReqItems(catItem)
              .onFailure(
                      fail -> {
                          JsonObject response = new JsonObject();
                          if (fail instanceof ComposeException) {
                              ComposeException exp = (ComposeException) fail;
                              response = exp.getResponse().toJson();
                              assertEquals(response.getString(TITLE), ITEMNOTFOUND);
                              assertEquals(response.getInteger(STATUS), 400);
                              assertEquals(response.getString(TYPE), Urn.URN_INVALID_INPUT.toString());
                          }
                          testContext.completeNow();
                      });
  }

  @Test
  @DisplayName("Test get res details from cat server")
  void getItemfromCatServerSucc(VertxTestContext testContext) {
      String resourceGrp =
              RandomStringUtils.randomAlphabetic(2)
                      + "/"
                      + RandomStringUtils.randomAlphabetic(2)
                      + "/"
                      + RandomStringUtils.randomAlphabetic(2)
                      + "/"
                      + RandomStringUtils.randomAlphabetic(2);
      String resource = resourceGrp + "/" + RandomStringUtils.randomAlphabetic(2);
      List<String> resItems = new ArrayList<>();
      resItems.add(resource);
      // request
      Map<String, List<String>> catItem = new HashMap<>();
      catItem.put(RES, resItems);

      // mock response
      JsonObject resItem = new JsonObject();
      JsonObject resGrpItem = new JsonObject();
      JsonArray resTypeArray = new JsonArray();
      JsonArray resGrpTypeArray = new JsonArray();
      resTypeArray.add(IUDX_RES);
      resGrpTypeArray.add(IUDX_RES_GRP);
      String emailId = providerUser.result().getString("email");
      String hash = DigestUtils.sha1Hex(emailId.getBytes());
      String emailHash = emailId.split("@")[1] + '/' + hash;
      resItem
              .put(TYPE, resTypeArray)
              .put(PROVIDER, emailHash)
              .put(RESOURCE_SERVER, emailHash + "/" + resourceServerUrl)
              .put(RESOURCE_GROUP, resourceGrp);

      resGrpItem
              .put(TYPE, resGrpTypeArray)
              .put(PROVIDER, emailHash)
              .put(RESOURCE_SERVER, emailHash + "/" + resourceServerUrl);

      // mock
      Mockito.doAnswer(
                      i -> {
                          Promise<JsonObject> p = Promise.promise();
                          String item = i.getArgument(0);
                          String[] itemSplit = item.split("/");
                          if (itemSplit.length < 5) p.complete(resGrpItem.put(ID, item));
                          else p.complete(resItem.put(ID, item));
                          return p.future();
                      })
              .when(spyCatalogueClient)
              .fetchItem(any());

      spyCatalogueClient
              .checkReqItems(catItem)
              .onSuccess(
                      succ -> {
                          Map<String, ResourceObj> resp = succ;
                          ResourceObj respObj = resp.get(resource);
                          assertEquals(respObj.getCatId(), resource);
                          assertEquals(respObj.getItemType(), "resource");
                          assertEquals(
                                  respObj.getOwnerId(), UUID.fromString(providerUser.result().getString("userId")));
                          assertEquals(respObj.getResServerID(), resourceSerID);
                          testContext.completeNow();
                      });
  }
  @Test
  @DisplayName("Test resource group details from cat server")
  void getGrpfromCatServerSucc(VertxTestContext testContext) {
      String resourceGrp =
              RandomStringUtils.randomAlphabetic(2)
                      + "/"
                      + RandomStringUtils.randomAlphabetic(2)
                      + "/"
                      + RandomStringUtils.randomAlphabetic(2)
                      + "/"
                      + RandomStringUtils.randomAlphabetic(2);
      String resource = resourceGrp + "/" + RandomStringUtils.randomAlphabetic(2);
      List<String> resItems = new ArrayList<>();
      resItems.add(resourceGrp);
      // request
      Map<String, List<String>> catItem = new HashMap<>();
      catItem.put(RES_GRP, resItems);

      // mock response
      JsonObject resGrpItem = new JsonObject();
      JsonArray resGrpTypeArray = new JsonArray();
      resGrpTypeArray.add(IUDX_RES_GRP);
      String emailId = providerUser.result().getString("email");
      String hash = DigestUtils.sha1Hex(emailId.getBytes());
      String emailHash = emailId.split("@")[1] + '/' + hash;

      resGrpItem
              .put(TYPE, resGrpTypeArray)
              .put(PROVIDER, emailHash)
              .put(RESOURCE_SERVER, emailHash + "/" + resourceServerUrl);

      // mock
      Mockito.doAnswer(
                      i -> {
                          Promise<JsonObject> p = Promise.promise();
                          String item = i.getArgument(0);
                          p.complete(resGrpItem.put(ID, item));
                          return p.future();
                      })
              .when(spyCatalogueClient)
              .fetchItem(any());

      spyCatalogueClient
              .checkReqItems(catItem)
              .onSuccess(
                      succ -> {
                          Map<String, ResourceObj> resp = succ;
                          ResourceObj respObj = resp.get(resourceGrp);
                          assertEquals(respObj.getCatId(), resourceGrp);
                          assertEquals(respObj.getItemType(), "resource_group");
                          assertEquals(
                                  respObj.getOwnerId(), UUID.fromString(providerUser.result().getString("userId")));
                          assertEquals(respObj.getResServerID(), resourceSerID);
                          testContext.completeNow();
                      });
  }

    @Test
    @DisplayName("Test resource exists on DB")
    void succResExistsinDB(VertxTestContext testContext) {
        List<String> resItems = new ArrayList<>();
        resItems.add(resourceOne);
        // request
        Map<String, List<String>> catItem = new HashMap<>();
        catItem.put(RES, resItems);
        catalogueClient.checkReqItems(catItem)
                .onSuccess(succ -> {
                    Map<String, ResourceObj> resp = succ;
                    ResourceObj respObj = resp.get(resourceOne);
                    assertEquals(respObj.getCatId(), resourceOne);
                    assertEquals(respObj.getItemType(), "resource");
                    assertEquals(
                            respObj.getOwnerId(), UUID.fromString(providerUser.result().getString("userId")));
                    assertEquals(respObj.getResServerID(), resourceSerID);
                    testContext.completeNow();
                });
    }

    @Test
    @DisplayName("Test resource group exists on DB")
    void succResGrpExistsinDB(VertxTestContext testContext) {
        List<String> resItems = new ArrayList<>();
        resItems.add(resourceGrp);
        // request
        Map<String, List<String>> catItem = new HashMap<>();
        catItem.put(RES_GRP, resItems);
        spyCatalogueClient
                .checkReqItems(catItem)
                .onSuccess(succ -> {
                    Map<String, ResourceObj> resp = succ;
                    ResourceObj respObj = resp.get(resourceGrp);
                    assertEquals(respObj.getCatId(), resourceGrp);
                    assertEquals(respObj.getItemType(), "resource_group");
                    assertEquals(
                            respObj.getOwnerId(), UUID.fromString(providerUser.result().getString("userId")));
                    assertEquals(respObj.getResServerID(), resourceSerID);
                    testContext.completeNow();
                });
    }

    @Test
    @DisplayName("Test resource group details not available in cat server")
    void getGrpfromCatServerFail(VertxTestContext testContext) {
        String resourceGrp =
                RandomStringUtils.randomAlphabetic(2)
                        + "/"
                        + RandomStringUtils.randomAlphabetic(2)
                        + "/"
                        + RandomStringUtils.randomAlphabetic(2)
                        + "/"
                        + RandomStringUtils.randomAlphabetic(2);
        List<String> resItems = new ArrayList<>();
        resItems.add(resourceGrp);
        // request
        Map<String, List<String>> catItem = new HashMap<>();
        catItem.put(RES_GRP, resItems);

        // mock response
        JsonObject resGrpItem = new JsonObject();
        JsonArray resGrpTypeArray = new JsonArray();
        resGrpTypeArray.add(IUDX_RES_GRP);
        String emailId = providerUser.result().getString("email");
        String hash = DigestUtils.sha1Hex(emailId.getBytes());
        String emailHash = emailId.split("@")[1] + '/' + hash;
        JsonArray resTypeArray = new JsonArray();

        resTypeArray.add(IUDX_RES);
        resGrpItem
                .put(TYPE, resGrpTypeArray)
                .put(PROVIDER, "emailHash")
                .put(RESOURCE_SERVER, emailHash + "/" + resourceServerUrl);


        // mock
        Mockito.doAnswer(
                        i -> {
                            Promise<JsonObject> p = Promise.promise();
                            String item = i.getArgument(0);
                            p.complete(resGrpItem.put(ID, item));
                            return p.future();
                        })
                .when(spyCatalogueClient)
                .fetchItem(any());
        spyCatalogueClient
                .checkReqItems(catItem)
                .onFailure(
                        failure -> {
                            JsonObject response = new JsonObject();
                            if (failure instanceof ComposeException) {
                                ComposeException exp = (ComposeException) failure;
                                response = exp.getResponse().toJson();
                                assertEquals(response.getString(TITLE), PROVIDER_NOT_REGISTERED);
                                assertEquals(response.getInteger(STATUS), 403);
                                assertEquals(response.getString(TYPE), Urn.URN_INVALID_INPUT.toString());
                            }
                            testContext.completeNow();
                        });
    }

    @Test
    @DisplayName("Test Get Cat IDs of resource")
    void getCatIDForRes(VertxTestContext testContext) {
        Set<UUID> itemIds = new HashSet<>();
        itemIds.add(resourceIdOne);
        spyCatalogueClient.getCatIds(itemIds, itemTypes.RESOURCE)
                .onSuccess(
                        succ -> {
                            Map<UUID, String> resp = new HashMap<>(succ);
                            assertTrue(resp.containsKey(resourceIdOne));
                            assertEquals(resp.get(resourceIdOne),resourceOne);
                            testContext.completeNow();
                        });
    }

    @Test
    @DisplayName("Test Get Cat IDs of resource")
    void getCatIDForRes1(VertxTestContext testContext) {
        Set<UUID> itemIds = new HashSet<>();
        itemIds.add(resourceGrpID);
        spyCatalogueClient.getCatIds(itemIds, itemTypes.RESOURCE_GROUP)
                .onSuccess(
                        succ -> {
                            Map<UUID, String> resp = new HashMap<>(succ);
                            assertTrue(resp.containsKey(resourceGrpID));
                            assertEquals(resp.get(resourceGrpID),resourceGrp);
                            testContext.completeNow();
                        });
    }
}
