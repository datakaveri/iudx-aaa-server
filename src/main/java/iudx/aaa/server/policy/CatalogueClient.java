package iudx.aaa.server.policy;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.SqlResult;
import io.vertx.sqlclient.Tuple;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static iudx.aaa.server.policy.Constants.BAD_REQUEST;
import static iudx.aaa.server.policy.Constants.CAT_ID;
import static iudx.aaa.server.policy.Constants.CHECKRESGRP;
import static iudx.aaa.server.policy.Constants.CHECK_AUTH_POLICY;
import static iudx.aaa.server.policy.Constants.CHECK_DELEGATION;
import static iudx.aaa.server.policy.Constants.CHECK_RES_SER;
import static iudx.aaa.server.policy.Constants.EMAIL_HASH;
import static iudx.aaa.server.policy.Constants.GET_PROVIDER_ID;
import static iudx.aaa.server.policy.Constants.GET_RES_DETAILS;
import static iudx.aaa.server.policy.Constants.GET_RES_GRP_DETAILS;
import static iudx.aaa.server.policy.Constants.GET_RES_GRP_OWNER;
import static iudx.aaa.server.policy.Constants.GET_RES_OWNERS;
import static iudx.aaa.server.policy.Constants.GET_RES_SER_DETAIL;
import static iudx.aaa.server.policy.Constants.GET_RES_SER_ID;
import static iudx.aaa.server.policy.Constants.ID;
import static iudx.aaa.server.policy.Constants.INSERT_RES;
import static iudx.aaa.server.policy.Constants.INSERT_RES_GRP;
import static iudx.aaa.server.policy.Constants.INTERNALERROR;
import static iudx.aaa.server.policy.Constants.ITEMNOTFOUND;
import static iudx.aaa.server.policy.Constants.IUDX_RES;
import static iudx.aaa.server.policy.Constants.IUDX_RES_GRP;
import static iudx.aaa.server.policy.Constants.NO_AUTH_POLICY;
import static iudx.aaa.server.policy.Constants.PROVIDER;
import static iudx.aaa.server.policy.Constants.PROVIDER_ID;
import static iudx.aaa.server.policy.Constants.PROVIDER_NOT_REGISTERED;
import static iudx.aaa.server.policy.Constants.RES;
import static iudx.aaa.server.policy.Constants.RESOURCE_GROUP;
import static iudx.aaa.server.policy.Constants.RESOURCE_SERVER;
import static iudx.aaa.server.policy.Constants.RESOURCE_SERVER_ID;
import static iudx.aaa.server.policy.Constants.RESULTS;
import static iudx.aaa.server.policy.Constants.RES_GRP;
import static iudx.aaa.server.policy.Constants.RES_GRP_OWNER;
import static iudx.aaa.server.policy.Constants.RES_OWNER;
import static iudx.aaa.server.policy.Constants.RES_SERVER;
import static iudx.aaa.server.policy.Constants.SERVER_NOT_PRESENT;
import static iudx.aaa.server.policy.Constants.STATUS;
import static iudx.aaa.server.policy.Constants.TYPE;
import static iudx.aaa.server.policy.Constants.URL;
import static iudx.aaa.server.policy.Constants.status;

public class CatalogueClient {
  private static final Logger LOGGER = LogManager.getLogger(CatalogueClient.class);

  private final PgPool pool;
  private WebClient client;
  private String catHost;
  private Integer catPort;
  private String catItemPath;
  private String authUrl;
  private String resUrl;
  private String domain;

  public CatalogueClient(Vertx vertx, PgPool pool, JsonObject options) {

    this.pool = pool;
    WebClientOptions clientOptions =
        new WebClientOptions().setSsl(true).setVerifyHost(false).setTrustAll(true);

    this.client = WebClient.create(vertx, clientOptions);
    this.catHost = options.getString("catServerHost");
    this.catPort = Integer.parseInt(options.getString("catServerPort"));
    this.catItemPath = Constants.CAT_ITEM_PATH;
    this.authUrl = options.getString("authServerUrl");
    this.resUrl = options.getString("resURL");
    this.domain = options.getString("domain");
  }

  /**
   * checks if item present in db, fetch if not present from catalog
   *
   * @param request - map of item type to list of item ids
   * @param userId - the userId of the policySetter
   * @return Map<String, UUID> -> map of cat_id to itemid
   */
  public Future<Map<String, UUID>> checkReqItems(Map<String, List<String>> request, String userId) {
    Promise<Map<String, UUID>> p = Promise.promise();

    if (request.containsKey(RES_SERVER)) {

      List<String> servers = request.get(RES_SERVER);

      if (servers.isEmpty()) {
        p.fail(BAD_REQUEST);
        return p.future();
      }

      Future<Map<String, UUID>> checkSer = checkResSer(servers, userId);
      checkSer
          .onSuccess(
              obj -> {
                servers.removeAll(obj.keySet());
                if (!servers.isEmpty()) p.fail(SERVER_NOT_PRESENT + servers.toString());
                else p.complete(obj);
              })
          .onFailure(failHandler -> p.fail(failHandler.getLocalizedMessage()));
    } else {

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

      Future<Map<String, UUID>> resDetails =
          insertItems.compose(
              obj -> {
                if (request.containsKey(RES)) return getResDetails(request.get(RES));
                return Future.succeededFuture(new HashMap<>());
              });

      Future<Map<String, UUID>> resGrpDetails =
          insertItems.compose(
              obj -> {
                if (request.containsKey(RES_GRP)) return getResGrpDetails(request.get(RES_GRP));
                return Future.succeededFuture(new HashMap<>());
              });

      Map<String, UUID> result = new HashMap<>();

      CompositeFuture.all(resDetails, resGrpDetails)
          .onSuccess(
              success -> {
                if (!resDetails.result().isEmpty()) result.putAll(resDetails.result());
                if (!resGrpDetails.result().isEmpty()) result.putAll(resGrpDetails.result());
                p.complete(result);
              })
          .onFailure(failHandler -> p.fail(failHandler.getLocalizedMessage()));
    }

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
  public Future<Map<String, List<String>>> checkResExist(Map<String, List<String>> request) {
    Promise<Map<String, List<String>>> p = Promise.promise();

    Future<List<String>> resGrp;
    Future<List<String>> resItem;
    if (request.containsKey(RES_GRP)) {

      List<String> resGrpIds = request.get(RES_GRP);
      resGrp = checkResGrp(resGrpIds);
    } else resGrp = Future.succeededFuture(new ArrayList<String>());

    if (request.containsKey(RES)) {
      List<String> resIds = request.get(RES);
      resItem = checkResource(resIds);
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
   * Verify if resource groups are present in db
   *
   * @param resGrpList - list of resource groups to be checked
   * @return List<String> -> List of cat_id not present
   */
  public Future<List<String>> checkResGrp(List<String> resGrpList) {
    Promise<List<String>> p = Promise.promise();

    Collector<Row, ?, List<String>> catIdCollector =
        Collectors.mapping(row -> row.getString(CAT_ID), Collectors.toList());

    try {

      if (resGrpList.isEmpty()) {
        p.complete(new ArrayList<>());
        return p.future();
      }

      pool.withConnection(
          conn ->
              conn.preparedQuery(CHECKRESGRP)
                  .collecting(catIdCollector)
                  .execute(Tuple.of(resGrpList.toArray(String[]::new)))
                  .onFailure(
                      obj -> {
                        LOGGER.error("checkResGrp db fail :: " + obj.getLocalizedMessage());
                        p.fail(INTERNALERROR);
                      })
                  .onSuccess(
                      success -> {
                        List<String> validItems = success.value();
                        List<String> invalid =
                            resGrpList.stream()
                                .filter(item -> !validItems.contains(item))
                                .collect(Collectors.toList());
                        p.complete(invalid);
                      }));
    } catch (Exception e) {
      LOGGER.error("Fail checkResGrp : " + e.toString());
      p.fail(INTERNALERROR);
    }
    return p.future();
  }

  /**
   * Verify if resource items are present in db
   *
   * @param resourceList - list of resource groups to be checked
   * @return List<String> -> List of cat_id not present
   */
  public Future<List<String>> checkResource(List<String> resourceList) {
    Promise<List<String>> p = Promise.promise();

    Collector<Row, ?, List<String>> catIdCollector =
        Collectors.mapping(row -> row.getString("cat_id"), Collectors.toList());

    try {

      if (resourceList.isEmpty()) {
        p.complete(new ArrayList<>());
        return p.future();
      }

      pool.withConnection(
          conn ->
              conn.preparedQuery(GET_RES_DETAILS)
                  .collecting(catIdCollector)
                  .execute(Tuple.of(resourceList.toArray(String[]::new)))
                  .onFailure(
                      obj -> {
                        LOGGER.error("checkResource db fail :: " + obj.getLocalizedMessage());
                        p.fail("internal error");
                      })
                  .onSuccess(
                      success -> {
                        List<String> validItems = success.value();

                        List<String> invalid =
                            resourceList.stream()
                                .filter(item -> !validItems.contains(item))
                                .collect(Collectors.toList());

                        p.complete(invalid);
                      }));
    } catch (Exception e) {
      LOGGER.error("Fail: " + e.toString());
      p.fail("internal error");
    }
    return p.future();
  }

  /**
   * Verify if server present in db
   *
   * @param req - list of servers
   * @param userId - the userId of the policySetter
   * @return Map<String, UUID> -> map of cat_id to itemid if present, fail if not present
   */
  public Future<Map<String, UUID>> checkResSer(List<String> req, String userId) {
    Promise<Map<String, UUID>> p = Promise.promise();

    if (req.isEmpty()) {
      p.complete(new HashMap<>());
      return p.future();
    }

    Collector<Row, ?, Map<String, UUID>> nameCollector =
        Collectors.toMap(row -> row.getString(CAT_ID), row -> row.getUUID(ID));

    pool.withConnection(
        conn ->
            conn.preparedQuery(CHECK_RES_SER)
                .collecting(nameCollector)
                .execute(
                    Tuple.of(UUID.fromString(userId)).addArrayOfString(req.toArray(String[]::new)))
                .onFailure(
                    obj -> {
                      LOGGER.error("checkResSer db fail :: " + obj.getLocalizedMessage());
                      p.fail(INTERNALERROR);
                    })
                .onSuccess(
                    success -> {
                      Map<String, UUID> servers = success.value();
                      if (servers.isEmpty()) p.fail(SERVER_NOT_PRESENT + req.get(0));
                      else p.complete(servers);
                    }));
    return p.future();
  }

  /**
   * get itemid for list of cat_ids
   *
   * @param resourceList - list of resource items
   * @return Map<String, UUID> -> map of cat_id to itemid
   */
  public Future<Map<String, UUID>> getResDetails(List<String> resourceList) {
    Promise<Map<String, UUID>> p = Promise.promise();

    Collector<Row, ?, Map<String, UUID>> catIdCollector =
        Collectors.toMap(row -> row.getString(CAT_ID), row -> row.getUUID(ID));

    try {
      if (resourceList.isEmpty()) {
        p.complete(new HashMap<>());
        return p.future();
      } else {
        pool.withConnection(
            conn ->
                conn.preparedQuery(GET_RES_DETAILS)
                    .collecting(catIdCollector)
                    .execute(Tuple.of(resourceList.toArray(String[]::new)))
                    .onFailure(
                        obj -> {
                          LOGGER.error("checkResource db fail :: " + obj.getLocalizedMessage());
                          p.fail(INTERNALERROR);
                        })
                    .onSuccess(
                        success -> {
                          p.complete(success.value());
                        }));
      }
    } catch (Exception e) {
      LOGGER.error("Fail getResDetails: ");
      p.fail(INTERNALERROR);
    }

    return p.future();
  }

  /**
   * get itemid for list of cat_ids
   *
   * @param resGrpList - list of resource_group items
   * @return Map<String, UUID> -> map of cat_id to itemid
   */
  public Future<Map<String, UUID>> getResGrpDetails(List<String> resGrpList) {
    Promise<Map<String, UUID>> p = Promise.promise();

    Collector<Row, ?, Map<String, UUID>> catIdCollector =
        Collectors.toMap(row -> row.getString("cat_id"), row -> row.getUUID("id"));

    try {

      if (resGrpList.isEmpty()) {
        p.complete(new HashMap<>());
        return p.future();
      }

        pool.withConnection(
          conn ->
              conn.preparedQuery(GET_RES_GRP_DETAILS)
                  .collecting(catIdCollector)
                  .execute(Tuple.of(resGrpList.toArray(String[]::new)))
                  .onFailure(
                      obj -> {
                        LOGGER.error("getResGrpDetails db fail :: " + obj.getLocalizedMessage());
                        p.fail("internal error");
                      })
                  .onSuccess(
                      success -> {
                        p.complete(success.value());
                      }));

    } catch (Exception e) {
      LOGGER.error("Fail: " + e.toString());
      p.fail("internal error");
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
  }

  /**
   * get item from catalogue server
   *
   * @param id - cat_id of item to be fetched from catalogue
   * @return JsonObject - result from the catalogue server
   */
  private Future<JsonObject> fetchItem(String id) {
    Promise<JsonObject> p = Promise.promise();
    client
        .get(catPort, catHost, catItemPath)
        .addQueryParam(ID, id)
        .send()
        .onFailure(
            ar -> {
              ar.printStackTrace();
              p.fail(INTERNALERROR);
            })
        .onSuccess(
            obj -> {
              JsonObject res = obj.bodyAsJsonObject();
              if (obj.statusCode() == 200) {
                if (res.getString(STATUS).equals(status.SUCCESS.toString().toLowerCase()))
                  p.complete(obj.bodyAsJsonObject().getJsonArray(RESULTS).getJsonObject(0));
              } else {
                if (obj.statusCode() == 404) p.fail(ITEMNOTFOUND + id);
                else {
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
                  if (emailHash.result() == null || emailHash.result().isEmpty())
                    return Future.failedFuture(PROVIDER_NOT_REGISTERED);

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
        resGrpEntry.onSuccess(
                ar -> p.complete(true)
                ).onFailure(
                failureHandler -> {
                    failureHandler.printStackTrace();
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
                failureHandler.printStackTrace();
                p.fail(failureHandler.getLocalizedMessage());
              })
          .onSuccess(success -> p.complete(true));
    }
    return p.future();
  }

  /**
   * checks if for user owns the resource
   *
   * @param req - map of resource_type to list of resource id
   * @param userId - userId of user
   * @return Boolean - active policy exits true , else false
   */
  public Future<List<UUID>> checkOwner(Map<String, List<String>> req, String userId) {
    Promise<List<UUID>> p = Promise.promise();

    Future<List<UUID>> resGrp;
    Future<List<UUID>> resItem;
    if (req.containsKey(RES_GRP)) {

      List<String> resGrpIds = req.get(RES_GRP);
      resGrp = resGrpOwner(resGrpIds, userId);
    } else resGrp = Future.succeededFuture(new ArrayList<>());

    if (req.containsKey(RES)) {
      List<String> resIds = req.get(RES);
      resItem = resOwner(resIds, userId);
    } else resItem = Future.succeededFuture(new ArrayList<>());

    CompositeFuture.all(resGrp, resItem)
        .onSuccess(
            obj -> {
              List<UUID> resp = new ArrayList<>();
              if (!resGrp.result().isEmpty()) resp.addAll(resGrp.result());

              if (!resItem.result().isEmpty()) resp.addAll(resItem.result());

              p.complete(resp);
            })
        .onFailure(fail -> p.fail(INTERNALERROR));
    // return map of res_id,iowners in place of user id
    // check if userid is delegate for any of these owners
    // compose
    // check if user id is an active delegate of all of the owner ids for resource
    // return list of not delegate
    return p.future();
  }

  /**
   * checks if for user owns the resource_groups
   *
   * @param ids - List of resource group id
   * @param userId - userId of user
   * @return List<UUID> - List of owner_ids
   */
  public Future<List<UUID>> resGrpOwner(List<String> ids, String userId) {
    Promise<List<UUID>> p = Promise.promise();

    Collector<Row, ?, List<UUID>> providerIdCollector =
        Collectors.mapping(row -> row.getUUID(PROVIDER_ID), Collectors.toList());

    pool.withConnection(
        conn ->
            conn.preparedQuery(RES_GRP_OWNER)
                .collecting(providerIdCollector)
                .execute(
                    Tuple.of(UUID.fromString(userId)).addArrayOfString(ids.toArray(String[]::new)))
                .onFailure(
                    obj -> {
                      LOGGER.error("resGrpOwner db fail :: " + obj.getLocalizedMessage());
                      obj.printStackTrace();
                      p.fail(INTERNALERROR);
                    })
                .onSuccess(
                    success -> {
                      p.complete(success.value());
                    }));

    return p.future();
  }

  /**
   * checks if for user owns the resource_groups
   *
   * @param ids - List of resource id
   * @param userId - userId of user
   * @return List<UUID> - List of owner_ids
   */
  public Future<List<UUID>> resOwner(List<String> ids, String userId) {
    Promise<List<UUID>> p = Promise.promise();

    Collector<Row, ?, List<UUID>> providerIdCollector =
        Collectors.mapping(row -> row.getUUID(PROVIDER_ID), Collectors.toList());

    pool.withConnection(
        conn ->
            conn.preparedQuery(RES_OWNER)
                .collecting(providerIdCollector)
                .execute(
                    Tuple.of(UUID.fromString(userId)).addArrayOfString(ids.toArray(String[]::new)))
                .onFailure(
                    obj -> {
                      LOGGER.error("resOwner db fail :: " + obj.getLocalizedMessage());
                      p.fail(INTERNALERROR);
                    })
                .onSuccess(
                    success -> {
                      p.complete(success.value());
                    }));

    return p.future();
  }

  /**
   * checks if for user is a delegate for any of a list of users
   *
   * @param provider_ids - List of Providers that the user should be a delegate of
   * @param userId - userId of user
   * @return List<UUID> - List of owner_ids
   */
  public Future<List<UUID>> checkDelegate(List<UUID> provider_ids, String userId) {
    Promise<List<UUID>> p = Promise.promise();
    Collector<Row, ?, List<UUID>> idCollector =
        Collectors.mapping(row -> row.getUUID(ID), Collectors.toList());

    try {
      pool.withConnection(
          conn ->
              conn.preparedQuery(CHECK_DELEGATION)
                  .collecting(idCollector)
                  .execute(
                      Tuple.of(authUrl, UUID.fromString(userId))
                          .addArrayOfUUID(provider_ids.toArray(UUID[]::new)))
                  .onSuccess(obj -> p.complete(obj.value()))
                  .onFailure(
                      obj -> {
                        LOGGER.error("checkDelegate db fail :: " + obj.getLocalizedMessage());
                        p.fail(INTERNALERROR);
                      }));
    } catch (Exception e) {
      LOGGER.error("Fail checkDelegate:" + e.toString());
      p.fail(INTERNALERROR);
    }
    return p.future();
  }

  /**
   * checks if resource item/ resource groups present in db
   *
   * @param request - map of item type to list of item ids
   * @return Map<String, UUID> -> map of cat_id to owner_id
   */
  public Future<Map<String, UUID>> getOwnerId(Map<String, List<String>> request) {
    Promise<Map<String, UUID>> p = Promise.promise();

    Future<Map<String, UUID>> resGrp;
    Future<Map<String, UUID>> resItem;
    /* if(request.containsKey(RES_SERVER))
    {
        List<String> resSerIds = request.get(RES_SERVER);
        Future<Map<String,UUID>> resSer = getResGrpOwners(resSerIds);
     } else {*/
    if (request.containsKey(RES_GRP)) {

      List<String> resGrpIds = request.get(RES_GRP);
      resGrp = getResGrpOwners(resGrpIds);
    } else resGrp = Future.succeededFuture(new HashMap<>());

    if (request.containsKey(RES)) {
      List<String> resIds = request.get(RES);
      resItem = getResOwners(resIds);
    } else resItem = Future.succeededFuture(new HashMap<>());

    CompositeFuture.all(resGrp, resItem)
        .onSuccess(
            obj -> {
              Map<String, UUID> resp = new HashMap<>();
              if (!resGrp.result().isEmpty()) resp.putAll(resGrp.result());

              if (!resItem.result().isEmpty()) resp.putAll(resItem.result());

              p.complete(resp);
            })
        .onFailure(fail -> p.fail(INTERNALERROR));
    // }
    return p.future();
  }

  /**
   * get owner_id for resource_group items
   *
   * @param resGrpId - List of resource group cat_ids
   * @return Map<String, UUID> -> map of cat_id to owner_id
   */
  public Future<Map<String, UUID>> getResGrpOwners(List<String> resGrpId) {
    Promise<Map<String, UUID>> p = Promise.promise();

    Collector<Row, ?, Map<String, UUID>> ownerCollector =
        Collectors.toMap(row -> row.getString(CAT_ID), row -> row.getUUID(PROVIDER_ID));
    pool.withConnection(
        conn ->
            conn.preparedQuery(GET_RES_GRP_OWNER)
                .collecting(ownerCollector)
                .execute(Tuple.of(resGrpId.toArray()))
                .onFailure(
                    obj -> {
                      LOGGER.error("getResGrpOwners db fail :: " + obj.getLocalizedMessage());
                      p.fail(INTERNALERROR);
                    })
                .onSuccess(
                    success -> {
                      p.complete(success.value());
                    }));

    return p.future();
  }

  /**
   * get owner_id for resource items
   *
   * @param resId - List of resource cat_ids
   * @return Map<String, UUID> -> map of cat_id to owner_id
   */
  public Future<Map<String, UUID>> getResOwners(List<String> resId) {
    {
      Promise<Map<String, UUID>> p = Promise.promise();

      Collector<Row, ?, Map<String, UUID>> ownerCollector =
          Collectors.toMap(row -> row.getString(CAT_ID), row -> row.getUUID(PROVIDER_ID));
      pool.withConnection(
          conn ->
              conn.preparedQuery(GET_RES_OWNERS)
                  .collecting(ownerCollector)
                  .execute(Tuple.of(resId.toArray()))
                  .onFailure(
                      obj -> {
                        LOGGER.error("getResOwners db fail :: " + obj.getLocalizedMessage());
                        p.fail(INTERNALERROR);
                      })
                  .onSuccess(
                      success -> {
                        p.complete(success.value());
                      }));

      return p.future();
    }
  }

  /**
   * checks if there is a policy for user by auth admin
   *
   * @param userId - userId of user
   * @return Boolean - active policy exits true , else false
   */
  public Future<Boolean> checkAuthPolicy(String userId) {
    Promise<Boolean> p = Promise.promise();

    pool.withConnection(
        conn ->
            conn.preparedQuery(CHECK_AUTH_POLICY)
                .execute(Tuple.of(userId, authUrl, status.ACTIVE))
                .onFailure(
                    obj -> {
                      LOGGER.error("checkAuthPolicy db fail :: " + obj.getLocalizedMessage());
                      p.fail(INTERNALERROR);
                    })
                .onSuccess(
                    obj -> {
                      if (obj.rowCount() > 0) p.complete(true);
                      else p.fail(NO_AUTH_POLICY);
                    }));

    return p.future();
  }
}
