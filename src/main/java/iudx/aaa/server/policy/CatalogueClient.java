package iudx.aaa.server.policy;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.SqlResult;
import io.vertx.sqlclient.Tuple;
import iudx.aaa.server.apiserver.ItemType;
import iudx.aaa.server.apiserver.ResourceObj;
import iudx.aaa.server.apiserver.ResourceObj2;
import iudx.aaa.server.apiserver.Response;
import iudx.aaa.server.apiserver.ResourceObj2.ResourceObjBuilder;
import iudx.aaa.server.apiserver.util.ComposeException;
import iudx.aaa.server.apiserver.util.Urn;
import iudx.aaa.server.policy.Constants.itemTypes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static iudx.aaa.server.policy.Constants.CAT_ID;
import static iudx.aaa.server.policy.Constants.CHECK_RESOURCE_EXIST;
import static iudx.aaa.server.policy.Constants.CHECK_RESOURCE_EXIST_JOIN;
import static iudx.aaa.server.policy.Constants.EMAIL_HASH;
import static iudx.aaa.server.policy.Constants.ERR_NOT_VALID_RESOURCE;
import static iudx.aaa.server.policy.Constants.GET_PROVIDER_ID;
import static iudx.aaa.server.policy.Constants.GET_RES_CAT_IDS;
import static iudx.aaa.server.policy.Constants.GET_RES_DETAILS;
import static iudx.aaa.server.policy.Constants.GET_RES_GRP_CAT_IDS;
import static iudx.aaa.server.policy.Constants.GET_RES_GRP_DETAILS;
import static iudx.aaa.server.policy.Constants.GET_RES_SER_DETAIL;
import static iudx.aaa.server.policy.Constants.GET_RES_SER_ID;
import static iudx.aaa.server.policy.Constants.ID;
import static iudx.aaa.server.policy.Constants.INSERT_RES;
import static iudx.aaa.server.policy.Constants.INSERT_RES_GRP;
import static iudx.aaa.server.policy.Constants.INTERNALERROR;
import static iudx.aaa.server.policy.Constants.ITEMNOTFOUND;
import static iudx.aaa.server.policy.Constants.ITEMTYPE;
import static iudx.aaa.server.policy.Constants.IUDX_RES;
import static iudx.aaa.server.policy.Constants.IUDX_RES_GRP;
import static iudx.aaa.server.policy.Constants.PROVIDER;
import static iudx.aaa.server.policy.Constants.PROVIDER_ID;
import static iudx.aaa.server.policy.Constants.PROVIDER_NOT_REGISTERED;
import static iudx.aaa.server.policy.Constants.RES;
import static iudx.aaa.server.policy.Constants.RESOURCE_GROUP;
import static iudx.aaa.server.policy.Constants.RESOURCE_GROUP_TABLE;
import static iudx.aaa.server.policy.Constants.RESOURCE_SERVER;
import static iudx.aaa.server.policy.Constants.RESOURCE_SERVER_ID;
import static iudx.aaa.server.policy.Constants.RESOURCE_TABLE;
import static iudx.aaa.server.policy.Constants.RESULTS;
import static iudx.aaa.server.policy.Constants.RES_GRP;
import static iudx.aaa.server.policy.Constants.TYPE;
import static iudx.aaa.server.policy.Constants.URL;
import static iudx.aaa.server.policy.Constants.*;

public class CatalogueClient {
  private static final Logger LOGGER = LogManager.getLogger(CatalogueClient.class);

  public static final String CAT_ITEM_ENDPOINT = "/item";
  public static final String CAT_RELATION_ENDPOINT = "/relationship";

  public static final String CAT_REL_QUERY_PARAM = "rel";
  public static final String CAT_REL_QUERY_VAL_ALL = "all";

  public static final String CAT_RESP_TYPE_KEY = "type";
  public static final String CAT_RESP_APD_KEY = "apdURL";
  public static final String CAT_RESP_ACCESS_POLICY_KEY = "accessPolicy";
  public static final String CAT_RESP_RES_SERVER_URL_KEY = "resourceServerURL";
  public static final String CAT_RESP_RES_GROUP_KEY = "resourceGroup";
  public static final String CAT_RESP_PROVIDER_USER_ID_KEY = "providerUserId";
  public static final String CAT_SUCCESS_URN = "urn:dx:cat:Success";

  public static final String CAT_RESP_RESOURCE_TYPE = "iudx:Resource";
  public static final String CAT_RESP_PROVIDER_TYPE = "iudx:Provider";
  public static final String CAT_RESP_RES_SERVER_TYPE = "iudx:ResourceServer";
  
  private final PgPool pool;
  private final WebClient client;
  private final String catHost;
  private final Integer catPort;
  private final String catItemPath;
  private final String authUrl;
  private final String resUrl;
  private final String domain;

  public CatalogueClient(Vertx vertx, PgPool pool, JsonObject options) {

    this.pool = pool;
    WebClientOptions clientOptions =
        new WebClientOptions().setSsl(true).setVerifyHost(true).setTrustAll(false);

    this.client = WebClient.create(vertx, clientOptions);
    this.catHost = options.getString("catServerHost");
    this.catPort = Integer.parseInt(options.getString("catServerPort"));
    this.catItemPath = options.getString("catServerItemPath");
    this.authUrl = options.getString("authServerUrl");
    this.resUrl = options.getString("resURL");
    this.domain = options.getString("domain");
  }

  /**
   * checks if item present in db, fetch if not present from catalog
   *
   * @param request - map of item type to list of item ids
   * @return Map<String, CatObj> -> map of cat_id to itemid
   */
  public Future<Map<String, ResourceObj>> checkReqItems(Map<String, List<String>> request) {
    Promise<Map<String, ResourceObj>> p = Promise.promise();
    
    /* Check if the item is not a UUID */
    List<String> items = request.entrySet().stream().flatMap(i -> i.getValue().stream())
        .collect(Collectors.toList());
    Iterator<String> itr = items.iterator();
    
    while(itr.hasNext()) {
      String strItemId = itr.next();  
    
      try {
        UUID.fromString(strItemId);
      }
      catch(IllegalArgumentException e) {
        Response r =
            new Response.ResponseBuilder()
            .type(Urn.URN_INVALID_INPUT.toString())
            .title("Invalid Item ID format")
            .detail(strItemId)
            .status(400)
            .build();
        p.fail(new ComposeException(r));
        
        return p.future();
      }
    }
    
    Future<Map<String, List<String>>> resources = checkResExist(request);

    Future<List<JsonObject>> fetchItem =
        resources.compose(
            obj -> {
              if (obj.size() == 0) return Future.succeededFuture(new ArrayList<JsonObject>());
              else return fetch(obj);
            });

    Future<Boolean> insertItems =
        fetchItem.compose(
            toInsert -> {
              if (toInsert.size() == 0) return Future.succeededFuture(true);
              else return insertItemToDb(toInsert);
            });

    Future<Map<String, ResourceObj>> resDetails =
        insertItems.compose(
            obj -> {
              if (request.containsKey(RES)) return getResDetails(request.get(RES), RESOURCE_TABLE);
              return Future.succeededFuture(new HashMap<>());
            });

    Future<Map<String, ResourceObj>> resGrpDetails =
        insertItems.compose(
            obj -> {
              if (request.containsKey(RES_GRP))
                return getResDetails(request.get(RES_GRP), RESOURCE_GROUP_TABLE);
              return Future.succeededFuture(new HashMap<>());
            });

    Map<String, ResourceObj> result = new HashMap<>();

    CompositeFuture.all(resDetails, resGrpDetails)
        .onSuccess(
            success -> {
              if (!resDetails.result().isEmpty()) result.putAll(resDetails.result());
              if (!resGrpDetails.result().isEmpty()) result.putAll(resGrpDetails.result());
              p.complete(result);
            })
        .onFailure(failHandler -> p.fail(failHandler));

    return p.future();
  }

  /**
   * checks if resource item/ resource groups present in db
   *
   * @param request - map of item type to list of item ids
   * @return Map<String, List<String>> -> map of item type to list of item ids that are not present
   *     in db
   */

  // method to check if resource present in db. returns, map of resType,List<CatId>(not present)
  private Future<Map<String, List<String>>> checkResExist(Map<String, List<String>> request) {
    Promise<Map<String, List<String>>> p = Promise.promise();

    Future<List<String>> resGrp;
    Future<List<String>> resItem;
    if (request.containsKey(RES_GRP)) {

      List<String> resGrpIds = request.get(RES_GRP);
      resGrp = checkRes(resGrpIds, Constants.itemTypes.RESOURCE_GROUP.toString().toLowerCase());
    } else resGrp = Future.succeededFuture(new ArrayList<String>());

    if (request.containsKey(RES)) {
      List<String> resIds = request.get(RES);
      resItem = checkRes(resIds, Constants.itemTypes.RESOURCE.toString().toLowerCase());
    } else resItem = Future.succeededFuture(new ArrayList<String>());

    CompositeFuture.all(resGrp, resItem)
        .onSuccess(
            obj -> {
              Map<String, List<String>> resp = new HashMap<>();
              if (!resGrp.result().isEmpty()) resp.put(RES_GRP, resGrp.result());

              if (!resItem.result().isEmpty()) resp.put(RES, resItem.result());
              p.complete(resp);
            })
        .onFailure(fail -> p.fail(INTERNALERROR));
    return p.future();
  }

  /**
   * Verify if resource are present in db
   *
   * @param resources - list of resource groups to be checked
   * @return List<String> -> List of cat_id not present
   */
  private Future<List<String>> checkRes(List<String> resources, String itemType) {
    Promise<List<String>> p = Promise.promise();

    Collector<Row, ?, List<String>> catIdCollector =
        Collectors.mapping(row -> row.getUUID(ID).toString(), Collectors.toList());

    try {

      if (resources.isEmpty()) {
        p.complete(new ArrayList<>());
        return p.future();
      }

      pool.withConnection(
          conn ->
              conn.preparedQuery(CHECK_RESOURCE_EXIST + itemType + CHECK_RESOURCE_EXIST_JOIN)
                  .collecting(catIdCollector)
                  .execute(Tuple.of(resources.stream().map(x -> UUID.fromString(x)).toArray(UUID[]::new))))
                  .onFailure(
                      obj -> {
                        LOGGER.error("checkRes db fail :: " + obj.getLocalizedMessage());
                        p.fail(INTERNALERROR);
                      })
                  .onSuccess(
                      success -> {
                        List<String> validItems = success.value();
                        List<String> invalid =
                            resources.stream()
                                .filter(item -> !validItems.contains(item))
                                .collect(Collectors.toList());
                        p.complete(invalid);
                      });
    } catch (Exception e) {
      LOGGER.error("Fail checkRes : " + e.toString());
      p.fail(INTERNALERROR);
    }
    return p.future();
  }

  /**
   * get list of items from catalogue server
   *
   * @param request - map of resource type to list of itemid
   * @return List<JsonObject> - List of jsonobjects from the catalogue server
   */
  private Future<List<JsonObject>> fetch(Map<String, List<String>> request) {
    Promise<List<JsonObject>> p = Promise.promise();
    Response r =
        new Response.ResponseBuilder()
        .type(Urn.URN_INVALID_INPUT.toString())
        .title(ITEMNOTFOUND)
        .detail("Getting items from cat is disabled")
        .status(400)
        .build();
    p.fail(new ComposeException(r));
    
    return p.future();
    /*
    List<String> resIds = new ArrayList<>();
    if (request.containsKey(RES)) {
      resIds.addAll(request.get(RES));

      // get resGrpId from resID
      resIds.addAll(
          resIds.stream()
              .map(e -> e.split("/"))
              .map(obj -> obj[0] + "/" + obj[1] + "/" + obj[2] + "/" + obj[3])
              .collect(Collectors.toList()));
    }

    if (request.containsKey(RES_GRP)) resIds.addAll(request.get(RES_GRP));

    List<String> distinctRes = resIds.stream().distinct().collect(Collectors.toList());

    List<Future> fetchFutures =
        distinctRes.stream().map(this::fetchItem).collect(Collectors.toList());

    CompositeFuture.all(fetchFutures)
        .onSuccess(
            successHandler -> {
              p.complete(
                  fetchFutures.stream()
                      .map(x -> (JsonObject) x.result())
                      .collect(Collectors.toList()));
            })
        .onFailure(
            failureHandler -> {
              p.fail(failureHandler);
            });
    return p.future();
    */
  }

  /**
   * get item from catalogue server
   *
   * @param id - cat_id of item to be fetched from catalogue
   * @return JsonObject - result from the catalogue server
   */
  Future<JsonObject> fetchItem(String id) {
    Promise<JsonObject> p = Promise.promise();
    client
        .get(catPort, catHost, catItemPath)
        .addQueryParam(ID, id)
        .send()
        .onFailure(
            ar -> {
              LOGGER.error("fetchItem error : " + ar.getCause());
              p.fail(INTERNALERROR);
            })
        .onSuccess(
            obj -> {
              JsonObject res = obj.bodyAsJsonObject();
              if (obj.statusCode() == 200) {
                if (res.getString(TYPE).equals(CAT_SUCCESS_URN)) {
                  p.complete(obj.bodyAsJsonObject().getJsonArray(RESULTS).getJsonObject(0));
                }
              } else {
                if (obj.statusCode() == 404) {
                  Response r =
                      new Response.ResponseBuilder()
                          .type(Urn.URN_INVALID_INPUT.toString())
                          .title(ITEMNOTFOUND)
                          .detail(id)
                          .status(400)
                          .build();
                  p.fail(new ComposeException(r));
                } else {
                  LOGGER.error("failed fetchItem: " + res);
                  p.fail(INTERNALERROR);
                }
              }
            });
    return p.future();
  }

  /**
   * Insert item into resource/resource_group tables
   *
   * @param request - List of responses from catalogue server
   * @return Boolean - true if insertion successful
   */
  private Future<Boolean> insertItemToDb(List<JsonObject> request) {
    Promise<Boolean> p = Promise.promise();
    // stream list of Json request to get list of resource groups,id,.resource server
    List<JsonObject> resGrps =
        request.stream()
            .filter(obj -> obj.getJsonArray(TYPE).contains(IUDX_RES_GRP))
            .collect(Collectors.toList());

    List<String> emailSHA =
        resGrps.stream().map(e -> e.getString(PROVIDER)).collect(Collectors.toList());
    // stream resGrps to get list of ResourceServers, parse to get url of server, check for
    // file/video, use to get resource server id

    // TODO
    // changing video and file server (.iudx.org.in) to resource server

    ListIterator<JsonObject> obj = resGrps.listIterator();
    Map<String, String> resUrlMap = new HashMap<>();
    while (obj.hasNext()) {

      String server = obj.next().getString(RESOURCE_SERVER);
      String url = server.split("/")[2];
      String[] urlSplit = url.split("\\.", 2);
      if (!urlSplit[0].equals("catalogue") && urlSplit[1].equals(domain)) url = resUrl;
      resUrlMap.put(server, url);
    }
    Collector<Row, ?, Map<String, UUID>> collector =
        Collectors.toMap(row -> row.getString(URL), row -> row.getUUID(ID));
    Future<Map<String, UUID>> resSerId =
        pool.withConnection(
            conn ->
                conn.preparedQuery(GET_RES_SER_ID)
                    .collecting(collector)
                    .execute(Tuple.of(resUrlMap.values().toArray()))
                    .map(SqlResult::value));

    Collector<Row, ?, Map<String, UUID>> providerId =
        Collectors.toMap(row -> row.getString(EMAIL_HASH), row -> row.getUUID(ID));

    Future<Map<String, UUID>> emailHash =
        pool.withConnection(
            conn ->
                conn.preparedQuery(GET_PROVIDER_ID)
                    .collecting(providerId)
                    .execute(Tuple.of(emailSHA.toArray()))
                    .map(SqlResult::value));

    Future<List<Tuple>> item =
        CompositeFuture.all(resSerId, emailHash)
            .compose(
                ar -> {
                  if (emailHash.result() == null || emailHash.result().isEmpty()) {
                    Response r =
                        new Response.ResponseBuilder()
                            .type(Urn.URN_INVALID_INPUT.toString())
                            .title(PROVIDER_NOT_REGISTERED)
                            .detail(PROVIDER_NOT_REGISTERED)
                            .status(403)
                            .build();
                    return Future.failedFuture(new ComposeException(r));
                  }

                  List<Tuple> tuples = new ArrayList<>();
                  for (JsonObject jsonObject : resGrps) {
                    String id = jsonObject.getString(ID);
                    UUID pid = emailHash.result().get(jsonObject.getString(PROVIDER));
                    String resServer = jsonObject.getString(RESOURCE_SERVER);
                    String url = resUrlMap.get(resServer);
                    UUID serverId = resSerId.result().get(url);
                    tuples.add(Tuple.of(id, pid, serverId));
                  }
                  return Future.succeededFuture(tuples);
                });
    // create tuple for resource group insertion // not if empty
    Future<Void> resGrpEntry =
        item.compose(
            success -> {
              if (success.size() == 0) {
                LOGGER.error("failed resGrpEntry: " + "No res groups to enter");
                return Future.failedFuture(INTERNALERROR);
              }
              return pool.withTransaction(
                  conn -> conn.preparedQuery(INSERT_RES_GRP).executeBatch(success).mapEmpty());
            });

    List<JsonObject> res =
        request.stream()
            .filter(arr -> arr.getJsonArray(TYPE).contains(IUDX_RES))
            .collect(Collectors.toList());

    if (res.size() == 0) {
      resGrpEntry
          .onSuccess(ar -> p.complete(true))
          .onFailure(
              failureHandler -> {
                p.fail(failureHandler.getLocalizedMessage());
              });
    } else {
      List<String> resGrpId =
          res.stream().map(e -> e.getString(RESOURCE_GROUP)).collect(Collectors.toList());
      Future<Map<String, JsonObject>> resourceItemDetails =
          resGrpEntry.compose(
              ar -> {
                Collector<Row, ?, Map<String, JsonObject>> mapCollector =
                    Collectors.toMap(
                        row -> row.getString(CAT_ID),
                        row ->
                            new JsonObject()
                                .put(ID, row.getUUID(ID))
                                .put(PROVIDER_ID, row.getUUID(PROVIDER_ID))
                                .put(RESOURCE_SERVER_ID, row.getUUID(RESOURCE_SERVER_ID)));

                // get map of resid , jsonobj by using data from resource_group table
                return pool.withConnection(
                    conn ->
                        conn.preparedQuery(GET_RES_SER_DETAIL)
                            .collecting(mapCollector)
                            .execute(Tuple.of(resGrpId.toArray()))
                            .map(map -> map.value()));
              });

      Future<List<Tuple>> resources =
          resourceItemDetails.compose(
              x -> {
                List<Tuple> tuples = new ArrayList<>();
                for (JsonObject jsonObject : res) {
                  String id = jsonObject.getString(RESOURCE_GROUP);
                  String cat_id = jsonObject.getString(ID);
                  UUID pId = UUID.fromString(x.get(id).getString(PROVIDER_ID));
                  UUID rSerId = UUID.fromString(x.get(id).getString(RESOURCE_SERVER_ID));
                  UUID rId = UUID.fromString(x.get(id).getString(ID));
                  tuples.add(Tuple.of(cat_id, pId, rSerId, rId));
                }
                return Future.succeededFuture(tuples);
              });

      resources
          .compose(
              success -> {
                return pool.withTransaction(
                    conn -> conn.preparedQuery(INSERT_RES).executeBatch(success).mapEmpty());
              })
          .onFailure(
              failureHandler -> {
                p.fail(failureHandler.getLocalizedMessage());
              })
          .onSuccess(success -> p.complete(true));
    }
    return p.future();
  }

  /**
   * get itemid for list of cat_ids
   *
   * @param resList - list of resource_group items
   * @return Map<String, UUID> -> map of cat_id to itemid
   */
  public Future<Map<String, ResourceObj>> getResDetails(List<String> resList, String itemType) {
    Promise<Map<String, ResourceObj>> p = Promise.promise();

    Collector<Row, ?, List<JsonObject>> ResDetailCollector =
        Collectors.mapping(Row::toJson, Collectors.toList());

    try {

      if (resList.isEmpty()) {
        p.complete(new HashMap<>());
        return p.future();
      }

      String query = "";
      if (itemType.equals(RESOURCE_GROUP_TABLE)) {
        query = GET_RES_GRP_DETAILS;
      } else if ((itemType.equals(RESOURCE_TABLE))) {
        query = GET_RES_DETAILS;
      }

      String finalQuery = query;
      pool.withConnection(
          conn ->
              conn.preparedQuery(finalQuery)
                  .collecting(ResDetailCollector)
                  .execute(Tuple.of(resList.stream().map(x -> UUID.fromString(x)).toArray(UUID[]::new)))
                  .onFailure(
                      obj -> {
                        LOGGER.error("getResGrpDetails db fail :: " + obj.getLocalizedMessage());
                        p.fail(INTERNALERROR);
                      })
                  .onSuccess(
                      success -> {
                        List<JsonObject> resDetailsList = success.value();
                        resDetailsList.forEach(resource -> resource.put(ITEMTYPE, itemType));

                        Map<String, ResourceObj> resDetailMap = new HashMap<>();
                        resDetailsList.forEach(
                            resource ->
                                resDetailMap.put(
                                    resource.getString(ID), new ResourceObj(resource)));
                        p.complete(resDetailMap);
                      }));

    } catch (Exception e) {
      LOGGER.error("Fail: " + e.toString());
      p.fail(INTERNALERROR);
    }
    return p.future();
  }
  
  /**
   * Get Cat IDs of resource/resource group given item IDs.
   * 
   * @param itemIds Set of item IDs in UUID
   * @param itemType itemTypes enum accepting either RESOURCE or RESOURCE_GROUP
   * @return a future of map, mapping item ID to Cat ID
   */
  public Future<Map<UUID, String>> getCatIds(Set<UUID> itemIds, itemTypes itemType)
  {
    Promise<Map<UUID, String>> promise = Promise.promise();

    if (itemIds.isEmpty()) {
      promise.complete(new HashMap<UUID, String>());
      return promise.future();
    }

    String query;
    if (itemType.equals(itemTypes.RESOURCE_GROUP)) {
      query = GET_RES_GRP_CAT_IDS;
    } else if ((itemType.equals(itemTypes.RESOURCE))) {
      query = GET_RES_CAT_IDS;
    } else {
      promise.fail("Invalid resource type passed");
      return promise.future();
    }

    Collector<Row, ?, Map<UUID, String>> collector =
        Collectors.toMap(row -> row.getUUID(ID), row -> row.getString(CAT_ID));

    pool.withConnection(conn -> conn.preparedQuery(query).collecting(collector)
        .execute(Tuple.of(itemIds.toArray(UUID[]::new))).map(res -> res.value()))
        .onSuccess(resp -> {
          promise.complete(resp);
        }).onFailure(err -> {
          err.printStackTrace();
          LOGGER.error("Fail: " + err.toString());
          promise.fail(INTERNALERROR);
        });
    return promise.future();
  }

  /**
   * Checks if given resource ID is a valid resource, gets all info about the resource and puts it
   * into a {@link ResourceObj2} object.
   * 
   * @param itemId a UUID representing a resource
   * @return a Future of {@link ResourceObj2} object containing all info if successful
   */
  public Future<ResourceObj2> getResourceDetails(UUID itemId) {
    Promise<ResourceObj2> promise = Promise.promise();

    ResourceObjBuilder builder = new ResourceObjBuilder();
    
    Future<JsonArray> catExistenceResponse =
        client.get(catPort, catHost, catItemPath + CAT_ITEM_ENDPOINT)
            .addQueryParam(ID, itemId.toString()).send().compose(res -> {
              if (res.statusCode() == 200
                  && res.bodyAsJsonObject().getString(TYPE).equals(CAT_SUCCESS_URN)) {
                return Future.succeededFuture(res.bodyAsJsonObject().getJsonArray(RESULTS));
              } else if (res.statusCode() == 404) {
                Response r = new Response.ResponseBuilder().type(Urn.URN_INVALID_INPUT.toString())
                    .title(ITEMNOTFOUND).detail(itemId.toString()).status(400).build();
                return Future.failedFuture(new ComposeException(r));
              } else {
                LOGGER.error("failed fetchItem: " + res);
                return Future.failedFuture(INTERNALERROR);
              }
            });

    Future<JsonObject> itemValidation = catExistenceResponse.compose(resArr -> {
      if (resArr.isEmpty()) {
        LOGGER.error("Failed Catalogue item check : Results array empty");
        return Future.failedFuture(INTERNALERROR);
      }

      JsonObject body = resArr.getJsonObject(0);

      JsonArray itemTypes = body.getJsonArray(CAT_RESP_TYPE_KEY);

      if (!itemTypes.contains(CAT_RESP_RESOURCE_TYPE)) {
        Response r = new Response.ResponseBuilder().type(Urn.URN_INVALID_INPUT.toString())
            .title(ERR_NOT_VALID_RESOURCE).detail(itemId.toString()).status(400).build();
        return Future.failedFuture(new ComposeException(r));
      }

      if (!body.containsKey(CAT_RESP_APD_KEY)) {
        LOGGER.error("Failed Catalogue item check : Resource {} does not have `apd` key",
            itemId.toString());
        return Future.failedFuture(INTERNALERROR);
      }

      if (!body.containsKey(CAT_RESP_ACCESS_POLICY_KEY)) {
        LOGGER.error("Failed Catalogue item check : Resource {} does not have `accessPolicy` key",
            itemId.toString());
        return Future.failedFuture(INTERNALERROR);
      }

      if (!(body.containsKey(CAT_RESP_RES_GROUP_KEY)
          && body.getString(CAT_RESP_RES_GROUP_KEY).matches(UUID_REGEX))) {
        LOGGER.error(
            "Failed Catalogue item check : Resource {} does not have `resourceGroup` key or is not UUID",
            itemId.toString());
        return Future.failedFuture(INTERNALERROR);
      }

      builder.id(itemId);
      builder.apdUrl(body.getString(CAT_RESP_APD_KEY));
      builder.resGrpId(UUID.fromString(body.getString(CAT_RESP_RES_GROUP_KEY)));
      builder.accessType(body.getString(CAT_RESP_ACCESS_POLICY_KEY));
      builder.itemType(ItemType.RESOURCE);

      return Future.succeededFuture();
    });

    Future<JsonArray> catRelationResponse = itemValidation
        .compose(itemExists -> client.get(catPort, catHost, catItemPath + CAT_RELATION_ENDPOINT)
            .addQueryParam(ID, itemId.toString())
            .addQueryParam(CAT_REL_QUERY_PARAM, CAT_REL_QUERY_VAL_ALL).send().compose(res -> {
              if (res.statusCode() == 200
                  && res.bodyAsJsonObject().getString(TYPE).equals(CAT_SUCCESS_URN)) {
                return Future.succeededFuture(res.bodyAsJsonObject().getJsonArray(RESULTS));
              } else {
                LOGGER.error("Failed Catalogue relation check : {} {}", res.statusCode(),
                    res.bodyAsJsonObject().toString());
                return Future.failedFuture(INTERNALERROR);
              }
            }));

    Future<ResourceObj2> relationValidation = catRelationResponse.compose(resArr -> {
      if (resArr.isEmpty()) {
        LOGGER.error("Failed Catalogue relation check : Results array empty");
        return Future.failedFuture(INTERNALERROR);
      }

      String providerUserId = "";
      String resourceServerUrl = "";

      for (int i = 0; i < resArr.size(); i++) {
        JsonObject json = resArr.getJsonObject(i);
        if (json.getJsonArray(CAT_RESP_TYPE_KEY).contains(CAT_RESP_RES_SERVER_TYPE)) {
          resourceServerUrl = json.getString(CAT_RESP_RES_SERVER_URL_KEY, "");
        } else if (json.getJsonArray(CAT_RESP_TYPE_KEY).contains(CAT_RESP_PROVIDER_TYPE)) {
          providerUserId = json.getString(CAT_RESP_PROVIDER_USER_ID_KEY, "");
        }
      }

      if (providerUserId.isEmpty() || resourceServerUrl.isEmpty()) {
        LOGGER.error("Failed Catalogue relation check : relationship API - provider {}, rsURL {}",
            providerUserId, resourceServerUrl);
        return Future.failedFuture(INTERNALERROR);
      }

      // getting res group ID from item API
      builder.ownerId(UUID.fromString(providerUserId));
      builder.resServerUrl(resourceServerUrl);

      return Future.succeededFuture(builder.build());
    });
    
    relationValidation.onSuccess(res -> {
      promise.complete(res);
    }).onFailure(fail -> promise.fail(fail));

    return promise.future();
  }
}
